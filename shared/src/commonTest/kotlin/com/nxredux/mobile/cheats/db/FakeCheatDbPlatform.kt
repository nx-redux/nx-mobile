package com.nxredux.mobile.cheats.db

import kotlinx.coroutines.CompletableDeferred

/** Scriptable platform for controller and AppState tests: set the fields, then read the recorded calls. */
class FakeCheatDbPlatform : CheatDbPlatform {
    var free: Long = 1L shl 30
    var remote: String? = "stamp-2"
    /** PCSX2's patches.zip's stamps (the libretro ones are [remote] and [downloadStamp]). */
    var patchesRemote: String? = "patches-2"
    var patchesDownloadStamp: String? = "patches-2"
    /** GitHub's newest `cht` commit; null = the API did not answer. */
    var commit: CheatDbCommit? = null
    var downloadStamp: String? = "stamp-2"
    var downloadError: CheatDbException? = null
    var installError: CheatDbException? = null
    var installedFiles: (Map<String, List<String>>) -> List<String> = { folders -> folders.keys.map { "Cheats/$it/a.cht" } }
    /** The files a PCSX2 install writes for its rows. */
    var installedPatches: (Map<String, List<String>>) -> List<String> = { folders -> folders.keys.map { "Cheats/$it/Patches/a.pnach" } }
    /** When set, downloading this source throws it. */
    var failSource: CheatDbSource? = null
    /** When set, installing this source's archive fails ([CheatDbInstall.optional]: skipped, else the call throws CORRUPT). */
    var failInstallSource: CheatDbSource? = null
    var manifestText: String? = null
    /** When set, `download` / `install` suspend on it until the test completes or cancels them. */
    var downloadGate: CompletableDeferred<Unit>? = null
    var installGate: CompletableDeferred<Unit>? = null
    /** When set, `readManifest` suspends on it, so a test can start a job while a refresh is in flight. */
    var readGate: CompletableDeferred<Unit>? = null
    /** When set, `writeManifest` suspends on it before storing the text. */
    var manifestGate: CompletableDeferred<Unit>? = null
    var reportedTotal: Long = 1000L

    val discarded = ArrayList<String>()
    val deleted = ArrayList<List<String>>()
    val pruned = ArrayList<List<String>>()
    var remoteCalls = 0
    val downloads = ArrayList<CheatDbSource>()
    val installs = ArrayList<List<CheatDbSource>>()
    /** The sources installs were asked to treat as best effort. */
    val optional = ArrayList<CheatDbSource>()

    override fun freeBytes(): Long = free
    override suspend fun remoteStamp(source: CheatDbSource): String? {
        remoteCalls++
        return if (source == CheatDbSource.PCSX2) patchesRemote else remote
    }
    override suspend fun latestChtCommit(): CheatDbCommit? = commit
    override suspend fun download(source: CheatDbSource, onProgress: (Long, Long) -> Unit): CheatDbArchive {
        downloads += source
        downloadError?.let { throw it }
        if (source == failSource) throw CheatDbException(CheatDbException.Kind.NETWORK)
        onProgress(100L, reportedTotal)
        downloadGate?.await()
        onProgress(reportedTotal.coerceAtLeast(200L), reportedTotal)
        return if (source == CheatDbSource.PCSX2) CheatDbArchive("/cache/patches.zip", patchesDownloadStamp, source)
        else CheatDbArchive("/cache/cheats.zip", downloadStamp, source)
    }
    override suspend fun install(installs: List<CheatDbInstall>, onProgress: (String, Int, Int) -> Unit): CheatDbInstallResult {
        installError?.let { throw it }
        this.installs += installs.map { it.archive.source }
        this.optional += installs.filter { it.optional }.map { it.archive.source }
        val tags = installs.flatMap { it.folders.keys }
        tags.forEachIndexed { i, tag -> onProgress(tag, i + 1, tags.size); if (i == 0) installGate?.await() }
        // A failing source: an optional one is skipped (its files taken back), a required one fails the whole call.
        val failed = installs.filter { it.archive.source == failInstallSource }
        if (failed.any { !it.optional }) throw CheatDbException(CheatDbException.Kind.CORRUPT)
        val files = (installs - failed.toSet()).flatMap { if (it.archive.source == CheatDbSource.PCSX2) installedPatches(it.folders) else installedFiles(it.folders) }
        return CheatDbInstallResult(files, failed.map { it.archive.source })
    }
    override suspend fun discard(archive: CheatDbArchive) { discarded += archive.path }
    override suspend fun readManifest(): String? { readGate?.await(); return manifestText }
    override suspend fun writeManifest(text: String) { manifestGate?.await(); manifestText = text }
    override suspend fun deleteInstalled(files: List<String>) { deleted += files; manifestText = null }
    override suspend fun deleteFiles(files: List<String>) { pruned += files }
}
