package com.nxredux.mobile.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.LaunchActivity
import com.nxredux.mobile.NotificationIds
import com.nxredux.mobile.NxApplication
import com.nxredux.mobile.achievements.RaDownloadService
import com.nxredux.mobile.runner.GameActivity
import com.nxredux.mobile.scraper.ScrapeService
import com.nxredux.mobile.sync.DeviceSyncService
import com.nxredux.mobile.tools.CheatDbService
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Why the app can't be replaced now, in the Global Constraints order (the host's Netplay reason comes before all but the
 * game, from [UpdateController.hostBusy]); null when nothing would be lost. Last, [foreground] false waits as "Ready to
 * install": Android blocks a hidden app's confirm screen silently, so the install starts once the user is back. Pure, for the test.
 */
internal fun busyReason(game: Boolean, sync: Boolean, art: Boolean, ra: Boolean, cheats: Boolean, foreground: Boolean): String? = when {
    game -> UpdateController.GAME_REASON
    sync -> "Waiting for Device Sync to finish"
    art -> "Waiting for artwork to finish"
    ra -> "Waiting for the achievements download to finish"
    cheats -> "Waiting for Cheats & Patches to finish"
    !foreground -> UpdateController.READY_REASON
    else -> null
}

/**
 * The github flavor's [UpdatePlatform]: GitHub over HttpURLConnection, the APK in `cacheDir/update/` (NxApplication
 * clears it on start), PackageInstaller for the install ([UpdateInstallReceiver] gets its status) and the check times
 * in the `app_update` prefs. [scope]: the controller's (Main); I/O moves to IO here.
 */
class AndroidUpdatePlatform(private val app: Context, private val scope: CoroutineScope) : UpdatePlatform {
    private val prefs = app.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val userAgent = "NXRedux/${BuildConfig.VERSION_NAME}"

    /** The debug build (NX Redux Dev) installs only the release's `-dev` APK. */
    override val dev: Boolean = BuildConfig.DEBUG
    override val installedCode: Long = BuildConfig.VERSION_CODE.toLong()
    override val installedVersion: String = BuildConfig.VERSION_NAME

    override suspend fun fetchLatest(): String = withContext(Dispatchers.IO) {
        val conn = open(BuildConfig.UPDATE_FEED, "application/vnd.github+json")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("feed: HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun download(release: AppRelease, onProgress: (done: Long, total: Long) -> Unit): UpdateFile =
        withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "update").apply { mkdirs() }
            val name = release.apkUrl.substringAfterLast('/').substringBefore('?').ifEmpty { "update.apk" }
            val target = File(dir, name)
            // Its own name per attempt: a cancelled download stalled in a read deletes only its own file when it ends.
            val part = File(dir, "$name.${System.nanoTime()}.part")
            try {
                val conn = open(release.apkUrl, "application/octet-stream")
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("download: HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                    var done = 0L
                    var reported = 0L
                    conn.inputStream.use { input ->
                        part.outputStream().use { output ->
                            val buf = ByteArray(CHUNK)
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                // The controller's state is main-thread state: report there, every PROGRESS_STEP.
                                if (done - reported >= PROGRESS_STEP) { reported = done; withContext(Dispatchers.Main) { onProgress(done, total) } }
                            }
                        }
                    }
                    if (total > 0 && done != total) throw IOException("download: $done of $total bytes")
                    withContext(Dispatchers.Main) { onProgress(done, total) }
                } finally {
                    conn.disconnect()
                }
                target.delete()
                if (!part.renameTo(target)) throw IOException("download: rename failed")
                UpdateFile(target.path)
            } catch (e: Throwable) {   // a cancel too: the controller only learns of the file once this returns
                part.delete()
                throw e
            }
        }

    override suspend fun sha256(file: UpdateFile): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        File(file.path).inputStream().use { input ->
            val buf = ByteArray(CHUNK)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun discard(file: UpdateFile) { File(file.path).delete() }

    /** Main thread only: reads GameActivity's sessions, the keep-alive services' state and the started activities. */
    override fun busyReason(): String? = busyReason(
        game = GameActivity.gameScreenAlive,
        sync = DeviceSyncService.active,
        art = ScrapeService.active,
        ra = RaDownloadService.active,
        cheats = CheatDbService.busy(),
        foreground = NxApplication.foreground,
    )

    override fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    override fun askInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (e: Exception) {   // no such screen on this device: the Waiting row still says what to allow
            NxLog.w(TAG, "install permission screen refused (${e::class.java.simpleName})")
        }
    }

    /** The install's session: [WRITING] while [install] writes it, its id once written, null when there is none. Main thread only. */
    private var session: Int? = null
    /** [session] written but not committed: a game started during the copy, so [resumeInstall] commits it on a free main menu. */
    private var uncommitted: PackageInstaller.Session? = null
    private var uncommittedResult: ((UpdateInstallResult) -> Unit)? = null

    /** Writes [file] into a PackageInstaller session on IO, then commits it on Main; [onResult] is called on Main. */
    override fun install(file: UpdateFile, clear: () -> Boolean, onResult: (UpdateInstallResult) -> Unit) {
        val installer = app.packageManager.packageInstaller
        session = WRITING
        scope.launch {
            val written = try {
                withContext(Dispatchers.IO) { writeSession(installer, File(file.path)) }
            } catch (e: CancellationException) {
                session = null
                throw e
            } catch (e: Exception) {
                session = null
                NxLog.e(TAG, "install session failed", e)
                onResult(UpdateInstallResult.FAILED)
                return@launch
            }
            val id = written.first
            session = id
            UpdateInstallReceiver.expect(id, clear) { result ->
                if (result != UpdateInstallResult.STARTED && session == id) session = null
                onResult(result)
            }
            // Committing may install with no confirm screen (USER_ACTION_NOT_REQUIRED), so a game started meanwhile holds it.
            if (!clear()) { uncommitted = written.second; uncommittedResult = onResult; return@launch }
            commit(installer, id, written.second, onResult)
        }
    }

    private fun commit(installer: PackageInstaller, id: Int, written: PackageInstaller.Session, onResult: (UpdateInstallResult) -> Unit) {
        try {
            written.use { it.commit(statusReceiver(id)) }
        } catch (e: Exception) {
            NxLog.e(TAG, "install commit failed", e)
            dropSession(installer, id)
            onResult(UpdateInstallResult.FAILED)
        }
    }

    override fun resumeInstall(): Boolean {
        val id = session ?: return false
        if (id == WRITING) return true   // still copying the APK in: the commit follows
        val installer = app.packageManager.packageInstaller
        if (installer.getSessionInfo(id) == null || !UpdateInstallReceiver.waits(id)) return dropSession(installer, id)
        uncommitted?.let { held ->   // the controller calls this only on a free main menu
            val onResult = uncommittedResult!!
            uncommitted = null
            uncommittedResult = null
            commit(installer, id, held, onResult)
            return true   // a failed commit already answered FAILED
        }
        val confirm = UpdateInstallReceiver.confirm(id) ?: return true   // committed; the status asking for the confirm screen is yet to come
        return try {
            app.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            NxLog.w(TAG, "confirm screen refused again (${e::class.java.simpleName})")
            dropSession(installer, id)
        }
    }

    /** Abandons [id], whose status no longer reaches the controller; false, for [resumeInstall]. */
    private fun dropSession(installer: PackageInstaller, id: Int): Boolean {
        UpdateInstallReceiver.forget(id)
        uncommitted?.let { runCatching { it.close() } }
        uncommitted = null
        uncommittedResult = null
        runCatching { installer.abandonSession(id) }
        if (session == id) session = null
        return false
    }

    /** A session holding the APK, with its id; an earlier attempt's leftover session is abandoned first. */
    private fun writeSession(installer: PackageInstaller, apk: File): Pair<Int, PackageInstaller.Session> {
        for (old in installer.mySessions) runCatching { installer.abandonSession(old.sessionId) }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            // The installer of record updating its own app may skip the confirm screen (API 31+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output, CHUNK)
                    session.fsync(output)
                }
            }
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
        return id to session
    }

    /** The status PendingIntent: explicit to [UpdateInstallReceiver]; mutable, as PackageInstaller fills in its extras. */
    private fun statusReceiver(sessionId: Int): android.content.IntentSender {
        val intent = Intent(app, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION).setPackage(app.packageName)
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(app, sessionId, intent, mutable or PendingIntent.FLAG_UPDATE_CURRENT).intentSender
    }

    override fun keepAlive(active: Boolean, text: String?) = AppUpdateService.update(app, active, text)

    /** On the download's channel, under its own id ([NotificationIds.APP_UPDATE_WAIT]) so the service's stop can't remove it. */
    override fun showWaiting(reason: String?) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (reason == null) { nm.cancel(NotificationIds.APP_UPDATE_WAIT); return }
        // API 33+ needs the runtime grant; without it the About row still says what the install waits for.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (nm.getNotificationChannel(WAIT_CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(WAIT_CHANNEL_ID, "App update", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows an app update download"
            })
        }
        val open = Intent(app, LaunchActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val cancel = Intent(app, UpdateWaitCancelReceiver::class.java).setAction(UpdateWaitCancelReceiver.ACTION).setPackage(app.packageName)
        val notice = NotificationCompat.Builder(app, WAIT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("NX Redux update")
            .setContentText(reason)
            .setContentIntent(PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(0, "Cancel", PendingIntent.getBroadcast(app, 0, cancel, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOnlyAlertOnce(true)
            .build()
        try {
            nm.notify(NotificationIds.APP_UPDATE_WAIT, notice)
        } catch (e: SecurityException) {   // the grant was revoked between the check and the post
            NxLog.w(TAG, "wait notice refused")
        }
    }

    override var lastSuccess: Long
        get() = prefs.getLong("last_success", 0L)
        set(value) { prefs.edit().putLong("last_success", value).apply() }

    override var lastFailure: Long
        get() = prefs.getLong("last_failure", 0L)
        set(value) { prefs.edit().putLong("last_failure", value).apply() }

    override var silenced: String?
        get() = prefs.getString("silenced", null)
        set(value) { prefs.edit().putString("silenced", value).apply() }

    override var pendingUpdate: String?
        get() = pendingUpdate(app)
        set(value) { prefs.edit().putString(PENDING_KEY, value).apply() }

    /** A GET with the Cheats & Patches tool's timeouts; [accept]: the Accept header. */
    private fun open(url: String, accept: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", userAgent)
    }

    companion object {
        private const val PENDING_KEY = "pending_update"

        /** The version an in-app update is installing, for [AppUpdatedReceiver]: its notice is for our own updates only. */
        fun pendingUpdate(context: Context): String? =
            context.getSharedPreferences("app_update", Context.MODE_PRIVATE).getString(PENDING_KEY, null)

        private const val TAG = "NxUpdate"
        /** [session] while the APK is written; PackageInstaller's ids are positive. */
        private const val WRITING = -1
        private const val CHUNK = 64 * 1024
        private const val PROGRESS_STEP = 256 * 1024L
        /** The same channel as [AppUpdateService]'s, created here too in case the wait outlives a channel reset. */
        private const val WAIT_CHANNEL_ID = "app-update"
    }
}
