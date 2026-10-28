package com.nxredux.mobile.cheats.db

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

sealed interface CheatDbState {
    /** The manifest has not been read yet. */
    data object Unknown : CheatDbState
    data object NotInstalled : CheatDbState
    data class Installed(val manifest: CheatDbManifest) : CheatDbState
    /** [previous]: what to show again if this fails or is cancelled (null = nothing was installed). [source]: the archive downloading. */
    data class Downloading(val done: Long, val total: Long, val previous: CheatDbManifest?, val source: CheatDbSource = CheatDbSource.LIBRETRO) : CheatDbState
    data class Installing(val tag: String, val index: Int, val count: Int, val previous: CheatDbManifest?) : CheatDbState
    data class Checking(val previous: CheatDbManifest) : CheatDbState
    data class Removing(val previous: CheatDbManifest) : CheatDbState
}

/** One-shot results for the UI: a [Notice] goes to the pill, a [Failure] to the Problem dialog. */
sealed interface CheatDbMessage {
    data class Notice(val text: String) : CheatDbMessage
    data class Failure(val text: String) : CheatDbMessage
}

/**
 * The Cheats & Patches tool's state machine (nx-redux `cheatdb.c`): download each source's archive ([CheatDatabase.sourcesFor]),
 * install the folders for [tags], keep one manifest; check for updates by Last-Modified; remove what the manifest lists. Every
 * action is ignored while [busy] except [cancel]. Lives in a process-level [scope] so a download outlives
 * the screen that started it.
 */
class CheatDbController(
    private val platform: CheatDbPlatform,
    private val tags: Set<String>,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow<CheatDbState>(CheatDbState.Unknown)
    val state: StateFlow<CheatDbState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<CheatDbMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<CheatDbMessage> = _messages.asSharedFlow()
    private var job: Job? = null
    /** The running install has returned: its files are on disk and only the manifest that lists them is left to write. */
    private var committing = false

    val busy: Boolean
        get() = _state.value.let { it is CheatDbState.Downloading || it is CheatDbState.Installing || it is CheatDbState.Checking || it is CheatDbState.Removing }

    private val installedManifest: CheatDbManifest? get() = (_state.value as? CheatDbState.Installed)?.manifest
    /** The table rows an install writes, per source; a manifest listing other rows is reinstalled by the next update check. */
    private val tableSources: Map<CheatDbSource, Map<String, List<String>>> = CheatDatabase.sourcesFor(tags)
    private val tableTags: List<String> = CheatDatabase.rowsFor(tags)

    /** Reads the manifest into [state]; a missing, unparsable or empty manifest means not installed. */
    fun refresh() {
        if (busy) return
        scope.launch {
            val manifest = platform.readManifest()?.let(CheatDbManifest::parse)?.takeIf { it.installed }
            // A job may have started while the manifest was read; its state wins.
            if (busy) return@launch
            _state.value = manifest?.let { CheatDbState.Installed(it) } ?: CheatDbState.NotInstalled
        }
    }

    /** Downloads and installs the database; over an existing install this replaces it. */
    fun download() = startDownload(installedManifest, "$NAME installed")

    /**
     * Compares each source's Last-Modified with its installed stamp, and the installed rows with the table; downloads
     * (every source) when any differs.
     */
    fun checkForUpdates() {
        if (busy) return
        val current = installedManifest ?: return
        // Busy before launching, so a second tap can't slip in before the coroutine starts.
        _state.value = CheatDbState.Checking(current)
        job = scope.launch {
            val remotes = tableSources.keys.associateWith { source ->
                try {
                    platform.remoteStamp(source)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null   // unreachable either way; never leave the state stuck on Checking
                }
            }
            _state.value = CheatDbState.Installed(current)
            val same = remotes.all { (source, remote) -> remote == current.stampOf(source) && remote != CheatDbManifest.UNKNOWN_STAMP }
            when {
                remotes.values.any { it == null } -> emit(CheatDbMessage.Failure("Could not check for updates (Wi-Fi?)"))
                same && current.tags == tableTags -> emit(CheatDbMessage.Notice("$NAME are up to date"))
                else -> startDownload(current, "$NAME updated")
            }
        }
    }

    /** Deletes exactly the files the manifest lists, then the manifest. */
    fun remove() {
        if (busy) return
        val current = installedManifest ?: return
        _state.value = CheatDbState.Removing(current)
        job = scope.launch {
            try {
                platform.deleteInstalled(current.files)
                _state.value = CheatDbState.NotInstalled
                emit(CheatDbMessage.Notice("$NAME removed"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: CheatDbException) {
                restore(current); emit(CheatDbMessage.Failure(e.kind.message))
            } catch (e: Exception) {
                restore(current); emit(CheatDbMessage.Failure("$NAME failed: ${e::class.simpleName}"))
            }
        }
    }

    /**
     * Stops a download, install or update check and shows the previous state again. Removing can't be cancelled, and
     * neither can an install whose files are already written (its manifest is being saved, so Remove finds them).
     */
    fun cancel() {
        if (committing) return
        val previous = when (val s = _state.value) {
            is CheatDbState.Downloading -> s.previous
            is CheatDbState.Installing -> s.previous
            is CheatDbState.Checking -> s.previous
            else -> return
        }
        job?.cancel()
        job = null
        _state.value = previous?.let { CheatDbState.Installed(it) } ?: CheatDbState.NotInstalled
    }

    private fun startDownload(previous: CheatDbManifest?, verb: String) {
        if (busy) return
        if (platform.freeBytes() < CheatDatabase.NEEDED_FREE_BYTES) { emit(CheatDbMessage.Failure(CheatDbException.Kind.NO_SPACE.message)); return }
        _state.value = CheatDbState.Downloading(0, -1, previous)
        job = scope.launch {
            // Each source's archive, once downloaded; all of them are installed in one call, so a failure takes back all.
            val archives = ArrayList<CheatDbArchive>()
            try {
                try {
                    for (source in tableSources.keys) {
                        archives += withProgress { report ->
                            platform.download(source) { done, total -> report(CheatDbState.Downloading(done, total, previous, source)) }
                        }
                    }
                    val files = withProgress { report ->
                        platform.install(archives.map { CheatDbInstall(it, tableSources.getValue(it.source)) }) { tag, i, n ->
                            report(CheatDbState.Installing(tag, i, n, previous))
                        }
                    }
                    val stamps = archives.associate { it.source to (it.stamp ?: CheatDbManifest.UNKNOWN_STAMP) }
                    val manifest = CheatDbManifest(
                        stamps[CheatDbSource.LIBRETRO] ?: CheatDbManifest.UNKNOWN_STAMP, now(), files, tableTags, stamps[CheatDbSource.PCSX2],
                    )
                    // The platform took back what a cancelled install wrote; from here the files stay, so the manifest
                    // listing them is written whatever happens to the job (else they would be orphans Remove never sees).
                    committing = true
                    try {
                        withContext(NonCancellable) {
                            platform.writeManifest(manifest.render())
                            // What the previous install wrote and this one didn't (a folder the table dropped): the new
                            // manifest no longer lists it, so Remove would never find it.
                            val stale = previous?.files.orEmpty() - files.toSet()
                            if (stale.isNotEmpty()) {
                                try { platform.deleteFiles(stale) } catch (e: Exception) { /* best effort: the install stands */ }
                            }
                        }
                    } finally {
                        committing = false
                    }
                    _state.value = CheatDbState.Installed(manifest)
                    emit(CheatDbMessage.Notice("$verb: ${manifest.fileCount} files for ${manifest.systems} systems"))
                } finally {
                    // Also on cancellation: the archives must not linger in the cache.
                    withContext(NonCancellable) { archives.forEach { platform.discard(it) } }
                }
            } catch (e: CancellationException) {
                throw e   // cancel() already restored the state
            } catch (e: CheatDbException) {
                restore(previous); emit(CheatDbMessage.Failure(e.kind.message))
            } catch (e: Exception) {
                restore(previous); emit(CheatDbMessage.Failure("$NAME failed: ${e::class.simpleName}"))
            }
        }
    }

    /**
     * Runs [block] with a progress reporter whose updates reach [state] one by one. The platform calls its
     * progress callbacks without suspending, and a StateFlow keeps only the latest value, so each update is
     * queued and applied by a forwarder that yields after every write, giving collectors a turn.
     */
    private suspend fun <T> withProgress(block: suspend (report: (CheatDbState) -> Unit) -> T): T = coroutineScope {
        val updates = Channel<CheatDbState>(Channel.UNLIMITED)
        launch { for (s in updates) { _state.value = s; yield() } }
        try {
            block { updates.trySend(it) }
        } finally {
            updates.close()   // the forwarder drains what is queued, then ends; coroutineScope waits for it
        }
    }

    private fun restore(previous: CheatDbManifest?) {
        _state.value = previous?.let { CheatDbState.Installed(it) } ?: CheatDbState.NotInstalled
    }

    private fun emit(message: CheatDbMessage) { _messages.tryEmit(message) }

    private companion object {
        /** The tool's name in its messages. */
        const val NAME = "Cheats & Patches"
    }
}
