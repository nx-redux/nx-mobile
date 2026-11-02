package com.nxredux.mobile.scraper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.AndroidPrivateFiles
import com.nxredux.mobile.storage.AndroidRefs
import com.nxredux.mobile.storage.PrivateFiles
import com.nxredux.mobile.storage.AndroidRomsSources
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * One [ArtworkController] per process, on a scope no activity cancels (like AchievementsService): a fetch survives
 * rotation and a game launch, kept alive by [ScrapeService]. The host is relayed so the newest AppState serves it; a
 * destroyed MainActivity [detach]es its own so the process never keeps that activity alive.
 */
object ArtworkService {
    private const val TAG = "NxArtworkService"
    @Volatile private var instance: ArtworkController? = null
    @Volatile private var relay: HostRelay? = null

    /**
     * Set by MainActivity to ask for POST_NOTIFICATIONS (API 33+); called on the main thread when the first job of the
     * process starts. The job runs whatever the answer (without it the progress notification is just hidden).
     */
    @Volatile var onNeedNotificationPermission: (() -> Unit)? = null
    @Volatile private var askedPermission = false

    /** The controller when one was built (the notification actions use it). */
    fun current(): ArtworkController? = instance

    fun controller(context: Context, host: ArtworkHost): ArtworkController = synchronized(this) {
        relay?.attach(host)
        val app = context.applicationContext
        instance ?: build(app, HostRelay(host, AndroidPrivateFiles(app)).also { relay = it }).also { instance = it }
    }

    /** MainActivity.onDestroy: lets go of [host] only while it is still the one served (a newer AppState may have taken over). */
    fun detach(host: ArtworkHost) { relay?.detach(host) }

    private fun build(app: Context, relay: HostRelay): ArtworkController {
        val settings = AndroidAppSettings(app)
        // The engine is built once; the home is read at each call so a new home folder (or the media fallback) is honoured.
        val homes = AndroidHomeLocator(app)
        val source = HomeRomsSource(AndroidRomsSources.of(app)) { homes.current() }
        val http = AndroidScraperHttp("nx-mobile/${versionName(app)}")
        val fitter = AndroidImageFitter()
        val hasher = AndroidRomHasher(AndroidRefs(app.contentResolver))
        val androidArt = AndroidArtFiles(app)
        val credentials = AndroidScraperCredentials(settings)
        val factory: ScrapeEngineFactory = { auth, onArt, onWritten, onAndroidArt, logged, onOptions, picked ->
            // The sources in the order they are asked: ScreenScraper, libretro-thumbnails, TheGamesDB, SteamGridDB.
            val sources = listOf<ArtSource>(
                ScreenScraperSource(http, hasher, auth, System::currentTimeMillis) { delay(it) },
                LibretroThumbnails(http, System::currentTimeMillis) { delay(it) },
                TheGamesDbSource(http, { credentials.tgdbUserKey.orEmpty() }, System::currentTimeMillis) { delay(it) },
                SteamGridDbSource(http, { credentials.sgdbKey }, System::currentTimeMillis) { delay(it) },
            )
            ScrapeEngine(
                http, fitter, source, HomeRomsSource.HOME, sources, onArt, onWritten, androidArt, onAndroidArt, logged,
                extras = { ArtExtras(settings.artBox2d, settings.artWheel, settings.artMix) }, pixels = fitter,
                warn = { NxLog.w(TAG, it) }, onOptions = onOptions, picked = picked,
            )
        }
        val runner = object : ScrapeRunner {
            override fun keepAlive(active: Boolean, progress: String?) {
                if (active && !askedPermission) Handler(Looper.getMainLooper()).post {
                    // Asked once per process, and only once a hook (a live MainActivity) actually asked.
                    val ask = onNeedNotificationPermission
                    if (ask != null && !askedPermission) { askedPermission = true; ask() }
                }
                ScrapeService.update(app, active, progress)
            }
        }
        // A throwing host must not take the process down: log the class only (messages may carry paths or URLs).
        val errors = CoroutineExceptionHandler { _, e -> NxLog.e(TAG, "artwork task failed (${e::class.java.simpleName})") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + errors)
        return ArtworkController(relay, factory, credentials, runner, scope, work = Dispatchers.Default)
    }

    private fun versionName(context: Context): String {
        val pm = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        return info.versionName ?: "?"
    }

}

/**
 * Forwards to the newest host (each MainActivity builds its own AppState). Between a [detach] and the next [attach] (no
 * menu alive: "Don't keep activities", the menu reaped under a game) a running job waits at its next library call
 * instead of losing the art index update; the job file and its log are app-private files, not the AppState's, so
 * Pause and Cancel from the notification still save the job without a host.
 */
internal class HostRelay(host: ArtworkHost, private val files: PrivateFiles) : ArtworkHost {
    private val target = MutableStateFlow<ArtworkHost?>(host)

    fun attach(host: ArtworkHost) { target.value = host }
    /** Compare-and-clear: a newer host already attached stays. */
    fun detach(host: ArtworkHost) { target.compareAndSet(host, null) }

    private suspend fun host(): ArtworkHost = target.value ?: target.filterNotNull().first()
    override suspend fun libraryGames() = host().libraryGames()
    override suspend fun allLibraryGames() = host().allLibraryGames()
    override suspend fun applyArt(gamePath: String, art: ArtRefs) = host().applyArt(gamePath, art)
    override suspend fun refreshAndroidConsole() = host().refreshAndroidConsole()
    override suspend fun readPrivate(name: String) = files.read(name)
    override suspend fun writePrivate(name: String, text: String) = files.write(name, text)
    override suspend fun appendPrivate(name: String, text: String): Boolean { files.append(name, text); return true }
}

/** [delegate] with the placeholder [HOME] resolved to the current home tree at each call (a failure when there is none). */
internal class HomeRomsSource(private val delegate: RomsSource, private val home: () -> ContentRef?) : RomsSource {
    companion object { val HOME = ContentRef("nx-home:") }

    private fun r(ref: ContentRef): ContentRef = if (ref == HOME) home() ?: throw IllegalStateException("No home folder") else ref

    override suspend fun listChildren(dir: ContentRef): List<Entry> = delegate.listChildren(r(dir))
    override suspend fun readText(file: ContentRef): String? = delegate.readText(r(file))
    override suspend fun child(dir: ContentRef, name: String): Entry? = delegate.child(r(dir), name)
    override suspend fun readHead(file: ContentRef, max: Int): ByteArray? = delegate.readHead(r(file), max)
    override fun displayName(tree: ContentRef): String = delegate.displayName(r(tree))
    override suspend fun isOffline(tree: ContentRef): Boolean = delegate.isOffline(r(tree))
    override suspend fun canWrite(tree: ContentRef): Boolean = delegate.canWrite(r(tree))
    override suspend fun createDirectory(parent: ContentRef, name: String): Entry? = delegate.createDirectory(r(parent), name)
    override suspend fun writeText(dir: ContentRef, name: String, text: String): Boolean = delegate.writeText(r(dir), name, text)
    override suspend fun writeBytes(dir: ContentRef, name: String, bytes: ByteArray): Boolean = delegate.writeBytes(r(dir), name, bytes)
    override suspend fun delete(dir: ContentRef, name: String): Boolean = delegate.delete(r(dir), name)
    override suspend fun deleteEmptyDirectory(parent: ContentRef, name: String): Boolean = delegate.deleteEmptyDirectory(r(parent), name)
    override suspend fun rename(dir: ContentRef, from: String, to: String): Boolean = delegate.rename(r(dir), from, to)
}
