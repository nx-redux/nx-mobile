package com.nxredux.mobile.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the updater needs from the app: the feed, the download, the install gate and the stored check times. */
interface UpdatePlatform {
    /** The dev flavor, which installs the `-dev` APK. */
    val dev: Boolean
    val installedCode: Long
    /** The installed versionName, for `Up to date (<version>)` when the feed's release is older than the install. */
    val installedVersion: String
    /** The feed's response body; throws when offline or on any HTTP status but 200. */
    suspend fun fetchLatest(): String
    suspend fun download(release: AppRelease, onProgress: (done: Long, total: Long) -> Unit): UpdateFile
    /** The file's lowercase hex SHA-256. */
    suspend fun sha256(file: UpdateFile): String
    fun discard(file: UpdateFile)
    /**
     * Why the app can't be replaced now (a game as [UpdateController.GAME_REASON], Device Sync, artwork, achievements,
     * Cheats & Patches), in the Global Constraints order; null when free.
     */
    fun busyReason(): String?
    /** Whether the app may install packages (`canRequestPackageInstalls()`). */
    fun canInstall(): Boolean
    /** Opens the system screen that allows installs from this app. */
    fun askInstallPermission()
    /**
     * Installs [file]; [clear] says, when the confirm screen is due, whether nothing holds the install any more (a game
     * started while the APK was copied in). Not clear: the session waits for [resumeInstall] on a free main menu.
     */
    fun install(file: UpdateFile, clear: () -> Boolean, onResult: (UpdateInstallResult) -> Unit)
    /**
     * The main menu showed while [install]'s session has no answer (the confirm screen left by Home, or its launch
     * blocked): reopens the confirm screen when the session still waits on it. False when the session is gone, which
     * this abandons; no result follows for it then.
     */
    fun resumeInstall(): Boolean
    /** Keeps the process alive with a notification showing [text] while [active]; for the download only. */
    fun keepAlive(active: Boolean, text: String?)
    /** A plain notification saying what a downloaded install waits for; null removes it. */
    fun showWaiting(reason: String?)
    /** Epoch millis of the last good / failed check, 0 for never; persisted. */
    var lastSuccess: Long
    var lastFailure: Long
    /** The version the user answered Not now for; persisted. */
    var silenced: String?
    /**
     * The version Update was pressed for, until the update ends here or a check answers; persisted, so a process that died
     * meanwhile (the install-permission screen, low memory) checks on its first main menu and asks again.
     */
    var pendingUpdate: String?
}

/** A downloaded APK. */
class UpdateFile(val path: String)

enum class UpdateInstallResult { STARTED, CANCELLED, FAILED }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val done: Long, val total: Long) : UpdateState
    /** Downloaded and verified; [reason] says what the install waits for. */
    data class Waiting(val release: AppRelease, val file: UpdateFile, val reason: String) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    /** [text]: "Couldn't reach GitHub" or "Update failed"; [release]: what Update downloads again, null after a failed check. */
    data class Failed(val text: String, val release: AppRelease?) : UpdateState
}

/**
 * The in-app updater: checks the GitHub feed (on the main menu once a day, or from About), asks about a newer release,
 * downloads and verifies it, waits until nothing would be lost by replacing the app, then hands it to the installer.
 * Runs its work in a process-level [scope] so a download outlives the screen that started it.
 */
class UpdateController(
    private val platform: UpdatePlatform,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _popup = MutableStateFlow<AppRelease?>(null)
    /** The release the main menu should ask about, or null. */
    val popup: StateFlow<AppRelease?> = _popup.asStateFlow()

    /** The host's reason to hold the install ("Close Netplay to install"); "" when off the main menu (wait, showing the platform's reason); null when free. */
    var hostBusy: () -> String? = { null }

    private var job: Job? = null
    /** The Waiting poll; its own field because it starts inside the download [job], before that launch returns. */
    private var poll: Job? = null
    /** The downloaded APK until the install ends, so a cancel or a failed install can delete it. */
    private var file: UpdateFile? = null
    /** The Waiting state is for the install permission, which only the next [onMainMenu] re-checks. */
    private var awaitingPermission = false
    /** This download already opened the install-permission screen; the user is asked once per download. */
    private var asked = false
    /** Update asked for the install permission before downloading; the next [onMainMenu] downloads once it is allowed. */
    private var permissionFor: AppRelease? = null
    /** [installNow] was pressed: the host's "" (off the main menu) no longer holds this install; until the install ends. */
    private var here = false

    private val busy: Boolean
        get() = _state.value.let { it is UpdateState.Checking || it is UpdateState.Downloading || it is UpdateState.Waiting || it is UpdateState.Installing }

    /** The main menu showed: checks the feed when due, retries a waiting install, or resumes or drops an unanswered one. */
    fun onMainMenu() {
        val s = _state.value
        if (s is UpdateState.Installing) {
            if (waitReason() != null) return   // a game or a job came up since the commit: the confirm screen waits for a free menu
            if (!platform.resumeInstall()) { dropFile(); finish(UpdateState.Available(s.release)) }
            return
        }
        if (s is UpdateState.Waiting) {
            if (!awaitingPermission) { poll?.cancel(); gate(s.release, s.file); return }
            if (platform.canInstall()) { awaitingPermission = false; gate(s.release, s.file); return }
            // Refused again: the user decides when to try, from About's Update to row.
            awaitingPermission = false
            dropFile()
            finish(UpdateState.Available(s.release))
            return
        }
        permissionFor?.let { r ->
            permissionFor = null
            if (s != UpdateState.Available(r)) return@let
            if (platform.canInstall()) download(r) else platform.pendingUpdate = null   // refused: About's Update to row asks again
            return
        }
        if (busy) return
        if (platform.pendingUpdate == null && !UpdatePolicy.checkDue(now(), platform.lastSuccess, platform.lastFailure)) return
        check(automatic = true)
    }

    /** About's Check for updates: ignores the interval and the silenced version. */
    fun checkNow() {
        if (busy) return
        check(automatic = false)
    }

    /** The popup's Update or About's Update to: downloads the release. */
    fun update() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        } ?: return
        _popup.value = null
        asked = false
        permissionFor = null
        platform.pendingUpdate = release.version
        if (!platform.canInstall()) {
            // Asked before the download, so a restart while the user is on that screen costs no download.
            asked = true
            permissionFor = release
            _state.value = UpdateState.Available(release)
            platform.askInstallPermission()
            return
        }
        download(release)
    }

    private fun download(release: AppRelease) {
        _state.value = UpdateState.Downloading(release, 0L, release.apkSize)
        platform.keepAlive(true, downloadingText(0L, release.apkSize))
        job = scope.launch {
            try {
                var shown = 0
                val downloaded = platform.download(release) { done, total ->
                    _state.value = UpdateState.Downloading(release, done, total)
                    val percent = percent(done, total)
                    if (percent != shown) { shown = percent; platform.keepAlive(true, downloadingText(done, total)) }
                }
                file = downloaded
                if (release.sha256 != null && platform.sha256(downloaded) != release.sha256) {
                    dropFile()
                    finish(UpdateState.Failed("Update failed", release))
                    return@launch
                }
                gate(release, downloaded)
            } catch (e: CancellationException) {
                throw e   // cancel() already restored the state
            } catch (e: Exception) {
                dropFile()
                finish(UpdateState.Failed("Update failed", release))
            }
        }
    }

    /**
     * About's A on "Ready to install": installs now, as the main menu would. Only the host's "" may be waived (About can't
     * launch a game); a real reason, or the platform's own "Ready to install" (the app hidden), keeps the wait.
     */
    fun installNow() {
        val s = _state.value as? UpdateState.Waiting ?: return
        if (awaitingPermission || s.reason != READY_REASON || platform.busyReason() != null || hostBusy() != "") return
        poll?.cancel()
        here = true
        gate(s.release, s.file)
    }

    /** The popup's Not now: silences this version until a newer one. */
    fun notNow() {
        val release = _popup.value ?: return
        platform.silenced = release.version
        _popup.value = null
    }

    /** The notification's Cancel: stops a download or a waiting install and offers the release again. */
    fun cancel() {
        val release = when (val s = _state.value) {
            is UpdateState.Downloading -> s.release
            is UpdateState.Waiting -> s.release
            else -> return
        }
        job?.cancel()
        poll?.cancel()
        job = null
        poll = null
        awaitingPermission = false
        dropFile()
        finish(UpdateState.Available(release))
    }

    private fun check(automatic: Boolean) {
        _state.value = UpdateState.Checking
        job = scope.launch {
            val latest = try {
                readLatestRelease(platform.fetchLatest(), platform.dev)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LatestRelease.Unusable   // offline, rate-limited or a portal page: all the same to the user
            }
            if (latest == LatestRelease.Unusable) {
                // An update left by a dead process retries on the failure backoff, not every menu.
                if (platform.pendingUpdate != null) { platform.pendingUpdate = null; platform.lastSuccess = 0L }
                platform.lastFailure = now()
                _state.value = UpdateState.Failed("Couldn't reach GitHub", null)
                return@launch
            }
            platform.lastSuccess = now()
            platform.pendingUpdate = null
            // GitHub answered; a release without this build's APK has nothing newer to offer.
            val release = (latest as? LatestRelease.Found)?.release
            if (release == null) {
                _state.value = UpdateState.UpToDate(platform.installedVersion)
                return@launch
            }
            if (release.versionCode > platform.installedCode) {
                _state.value = UpdateState.Available(release)
                if (automatic && UpdatePolicy.shouldPopup(release, platform.installedCode, platform.silenced)) _popup.value = release
            } else {
                val version = if (release.versionCode == platform.installedCode) release.version else platform.installedVersion
                _state.value = UpdateState.UpToDate(version)
            }
        }
    }

    /**
     * Installs [downloaded] when the host and the platform are free and installs are allowed; otherwise waits, polling the
     * busy reasons every 2 s so a quit game or a finished sync starts the install without another menu event.
     */
    private fun gate(release: AppRelease, downloaded: UpdateFile) {
        val reason = waitReason()
        if (reason != null) {
            waitFor(release, downloaded, reason)
            poll = scope.launch {
                while (true) {
                    delay(POLL_MS)
                    val next = waitReason() ?: break
                    if (next != (_state.value as? UpdateState.Waiting)?.reason) waitFor(release, downloaded, next)
                }
                gate(release, downloaded)
            }
            return
        }
        if (!platform.canInstall()) {
            if (!asked) { asked = true; platform.askInstallPermission() }
            awaitingPermission = true
            waitFor(release, downloaded, PERMISSION_REASON)
            return
        }
        val installing = UpdateState.Installing(release)
        finish(installing)
        platform.install(downloaded, clear = { waitReason() == null }) { result ->
            // A late answer, after onMainMenu found the session gone, is for an install that already ended.
            if (result == UpdateInstallResult.STARTED || _state.value != installing) return@install
            dropFile()
            finish(if (result == UpdateInstallResult.CANCELLED) UpdateState.Available(release) else UpdateState.Failed("Update failed", release))
        }
    }

    /**
     * The Global Constraints order: the platform's game reason, then the host's (Netplay), then the platform's others; off
     * the main menu with nothing else running, "Ready to install".
     */
    private fun waitReason(): String? {
        val other = platform.busyReason()
        if (other == GAME_REASON) return other
        val host = hostBusy().takeUnless { here && it == "" }
        if (!host.isNullOrEmpty()) return host
        return other ?: if (host == "") READY_REASON else null
    }

    /**
     * Waits without the foreground service: a wait can last hours (a game, the app in the background), which would spend the
     * app's daily dataSync allowance. A process that dies meanwhile asks again through [UpdatePlatform.pendingUpdate].
     */
    private fun waitFor(release: AppRelease, downloaded: UpdateFile, reason: String) {
        _state.value = UpdateState.Waiting(release, downloaded, reason)
        here = false
        platform.keepAlive(false, null)
        platform.showWaiting(reason)
    }

    /** Leaves the download or the wait; offering the release again (or failing) ends the pending update. */
    private fun finish(state: UpdateState) {
        _state.value = state
        if (state !is UpdateState.Installing) here = false
        platform.keepAlive(false, null)
        platform.showWaiting(null)
        if (state is UpdateState.Available || state is UpdateState.Failed) platform.pendingUpdate = null
    }

    private fun dropFile() {
        file?.let(platform::discard)
        file = null
    }

    private fun percent(done: Long, total: Long): Int = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0

    private fun downloadingText(done: Long, total: Long) = "Downloading ${percent(done, total)}%"

    companion object {
        const val POLL_MS = 2_000L
        const val GAME_REASON = "Close your game to install"
        const val PERMISSION_REASON = "Allow installs from NX Redux to update"
        const val READY_REASON = "Ready to install"
    }
}
