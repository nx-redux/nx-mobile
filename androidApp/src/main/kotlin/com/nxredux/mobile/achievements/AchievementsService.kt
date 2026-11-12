package com.nxredux.mobile.achievements

import android.content.Context
import android.content.pm.PackageManager
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.runner.RomCache
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.storage.LibraryIndexStore
import com.nxredux.mobile.storage.AndroidRomsSources
import com.nxredux.mobile.storage.AndroidHomeLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One controller per process, on a scope no activity cancels (mirrors CheatDbService): a sync survives rotation and a game launch. */
object AchievementsService {
    @Volatile private var instance: RaController? = null

    /**
     * Set by MainActivity to ask for POST_NOTIFICATIONS (API 33+); called on the main thread when the first download of
     * the process starts. The download runs whatever the answer (without it the progress notification is just hidden).
     */
    @Volatile var onNeedNotificationPermission: (() -> Unit)? = null
    @Volatile private var askedPermission = false

    /** The controller when one was built (the notification's Cancel uses it). */
    fun current(): RaController? = instance

    fun platform(context: Context): RaPlatform {
        val app = context.applicationContext
        val homes = AndroidHomeLocator(app)
        return RaPlatform(
            AndroidRaStore(app), AndroidRaHttp(RaRequests.userAgent(versionName(app), "Android ${android.os.Build.VERSION.RELEASE}")),
            AndroidRaHasher(app, AndroidRomsSources.of(app)), AndroidRaSigner, AndroidConnectivity(app),
            RaHomeTree(AndroidRomsSources.of(app)) { homes.current() },   // storage: an unmounted card is not available()
        )
    }

    fun controller(context: Context, settings: AppSettings, indexStore: LibraryIndexStore): RaController = instance ?: synchronized(this) {
        instance ?: run {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            RaController(
                platform(context), settings, { indexStore.read()?.let(LibraryIndexCodec::decode) }, scope, System::currentTimeMillis, work = Dispatchers.Default,
            ).also { instance = it; sweepAfterPrefetch(context.applicationContext, it, scope); keepDownloadAlive(context.applicationContext, it, scope) }
        }
    }

    /**
     * "Download all game data" unpacks every archive in the library into the ROM cache (the hasher reads the inner ROM);
     * once it ends (done, failed or cancelled) the cache is trimmed back to its cap.
     */
    private fun sweepAfterPrefetch(app: Context, controller: RaController, scope: CoroutineScope) {
        scope.launch {
            controller.state.map { it is RaState.Prefetching }.distinctUntilChanged().drop(1).filter { !it }
                .collect { withContext(Dispatchers.IO) { RomCache.sweep(app) } }
        }
    }

    /**
     * "Download all game data" (Settings or setup) runs under [RaDownloadService] with a progress notification, so it
     * goes on with the app off screen, as an artwork fetch does.
     */
    private fun keepDownloadAlive(app: Context, controller: RaController, scope: CoroutineScope) {
        scope.launch {
            var shown = false
            controller.state.collect { s ->
                val download = (s as? RaState.Prefetching)?.takeUnless { it.paused }   // paused: no service (resumed from the app)
                if (download == null && !shown) return@collect
                if (download != null && !askedPermission) {
                    // Asked once per process, and only once a hook (a live MainActivity) actually asked.
                    onNeedNotificationPermission?.let { askedPermission = true; it() }
                }
                shown = download != null
                RaDownloadService.update(app, download)
            }
        }
    }

    private fun versionName(context: Context): String {
        val pm = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        return info.versionName ?: "?"
    }
}
