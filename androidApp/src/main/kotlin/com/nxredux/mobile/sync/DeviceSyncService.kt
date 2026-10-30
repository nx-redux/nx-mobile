package com.nxredux.mobile.sync

import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.nxredux.mobile.KeepAlive
import com.nxredux.mobile.KeepAliveService
import com.nxredux.mobile.NotificationIds
import com.nxredux.mobile.runner.LiveGameStore
import com.nxredux.mobile.storage.AndroidSaveTransfer
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The process's one Device Sync controller (a sync outlives the screen, as an art fetch does). Its scope runs on
 * [Dispatchers.Main.immediate]: button handling and the controller's [DeviceSyncPlatform.gameScreenAlive] check (which
 * reads the live game record) run on the main thread; the platform moves its I/O to IO itself.
 */
object DeviceSyncs {
    private const val TAG = "NxSync"
    @Volatile private var controller: DeviceSyncController? = null
    /**
     * MainActivity's keep-screen-on for all its live instances, set in onCreate; called from any thread (the callee
     * posts to the UI thread).
     */
    @Volatile var screenOn: (Boolean) -> Unit = {}
    /** An Import (from any MainActivity's AndroidSaveTransfer) is writing saves; process state. */
    @Volatile var importing: () -> Boolean = { AndroidSaveTransfer.anyImporting }
    /** MainActivity's POST_NOTIFICATIONS request, asked once when the first sync starts (the sync runs either way). */
    @Volatile var onNeedNotificationPermission: (() -> Unit)? = null
    @Volatile private var askedPermission = false

    fun current(): DeviceSyncController? = controller

    fun get(context: Context): DeviceSyncController = controller ?: synchronized(this) {
        controller ?: run {
            val app = context.applicationContext
            val platform = AndroidDeviceSync(app, { LiveGameStore.alive(app) }, { importing() }) { active, text ->
                if (active && !askedPermission) onNeedNotificationPermission?.let { ask -> askedPermission = true; ask() }
                DeviceSyncService.update(app, active, text)
                screenOn(active)
            }
            // A throwing step must not take the process down (the controller turns expected failures into pages).
            val errors = CoroutineExceptionHandler { _, e -> NxLog.e(TAG, "device sync failed", e) }
            // `now` only measures intervals (HELLO/READY pacing, the server's wait): the monotonic clock. The listings'
            // parses and the plans run on Default, the run itself on Main.
            DeviceSyncController(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errors), platform, work = Dispatchers.Default) {
                SystemClock.elapsedRealtime()
            }
        }.also { controller = it }
    }
}

/**
 * The foreground service (type dataSync) that keeps a Device Sync alive, with its notification (Cancel stops the sync
 * through [DeviceSyncs]). Driven only through [update]: the first active call starts it (a sync is always started by
 * the user in the foreground), later ones update the notification, an inactive one stops it. The state machine is
 * [KeepAliveService]'s, as [com.nxredux.mobile.scraper.ScrapeService]'s is.
 */
class DeviceSyncService : KeepAliveService<String>() {
    companion object {
        private const val TITLE = "Device Sync"
        private val state = KeepAlive(DeviceSyncService::class.java, "NxSyncService", "sync")

        /** The controller side ([DeviceSyncPlatform.keepAlive]): start / update on [active], stop otherwise. Any thread. */
        fun update(context: Context, active: Boolean, text: String?) = state.update(context, active, text)

        /** A sync is keeping the service alive (the updater's install gate). Main thread only. */
        val active: Boolean get() = state.active
    }

    override val keepAlive get() = state
    override val notificationId = NotificationIds.DEVICE_SYNC
    override val channel = Channel("device-sync", "Device Sync", "Shows a running Device Sync")
    override val actions = listOf(Action("Cancel", "com.nxredux.mobile.sync.CANCEL", 2) { DeviceSyncs.current()?.cancel() })

    /** Stop the sync (as Cancel) and the service. */
    override fun onTimedOut() { DeviceSyncs.current()?.cancel() }

    override fun NotificationCompat.Builder.content(progress: String?): NotificationCompat.Builder =
        setContentTitle(TITLE)
            .setContentText(progress?.takeIf { it.isNotBlank() } ?: TITLE)
            .setProgress(0, 0, true)   // no file count across steps: indeterminate while syncing
}
