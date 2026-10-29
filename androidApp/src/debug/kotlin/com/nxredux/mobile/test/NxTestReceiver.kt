package com.nxredux.mobile.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nxredux.mobile.library.CompanionLookup
import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.GameActivity
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.runner.LibretroRunner
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.FileRomsSource
import com.nxredux.mobile.sync.DeviceSyncPage
import com.nxredux.mobile.sync.DeviceSyncs
import com.nxredux.mobile.sync.SyncLink
import com.nxredux.mobile.ui.nx.NxButton
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Debug builds only (debug source set and manifest): adb hooks for the device tests. Results go to filesDir/nxtest/
 * through [NxTestResults]:
 *
 * - RESET: deletes the result files.
 * - SYNC (`--es link wifi|cable --ez roms true|false`): starts a Device Sync as the screen would; sync.json.
 * - SYNC_STOP: stops (or backs out of) that sync; sync.json state "stopped".
 * - GAME (`--es path <rom> --es tag <TAG> [--ei slot n]`): launches the ROM as the launcher does; GameActivity writes
 *   game.json, battery.json and resume.json. Send it with `am broadcast --allow-background-activity-starts`: a receiver
 *   may not start an activity from the background otherwise (Android 10+). The game shows over the lock screen and
 *   turns the screen on ([NxTestResults.markShowWhenLocked]).
 * - GAME_STOP: the live game quits as the menu's Quit does (battery save and auto state written).
 */
class NxTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        NxLog.i(TAG, "hook ${intent.action}")
        when (intent.action) {
            ACTION_RESET -> NxTestResults.reset(app)
            ACTION_SYNC -> {
                val link = if (intent.getStringExtra("link").equals("wifi", ignoreCase = true)) SyncLink.WIFI else SyncLink.CABLE
                startSync(app, link, intent.getBooleanExtra("roms", false))
            }
            ACTION_SYNC_STOP -> stopSync(app)
            ACTION_GAME -> {
                val path = intent.getStringExtra("path")
                val tag = intent.getStringExtra("tag")
                val slot = if (intent.hasExtra("slot")) intent.getIntExtra("slot", -1) else null
                if (path == null || tag == null) NxTestResults.write(app, "game", "state" to "refused", "reason" to "path and tag are required")
                else launchGame(app, path, tag, slot)
            }
            ACTION_GAME_STOP -> if (!GameActivity.debugQuit()) NxLog.w(TAG, "GAME_STOP: no live game")
        }
    }

    private companion object {
        const val TAG = "NxTest"
        const val ACTION_RESET = "com.nxredux.mobile.test.RESET"
        const val ACTION_SYNC = "com.nxredux.mobile.test.SYNC"
        const val ACTION_SYNC_STOP = "com.nxredux.mobile.test.SYNC_STOP"
        const val ACTION_GAME = "com.nxredux.mobile.test.GAME"
        const val ACTION_GAME_STOP = "com.nxredux.mobile.test.GAME_STOP"
        const val SYNC_TIMEOUT_MS = 20 * 60 * 1000L

        /** Process-level: a hook's work outlives onReceive. Main thread, like the controller's own scope. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var syncJob: Job? = null

        fun startSync(app: Context, link: SyncLink, roms: Boolean) {
            syncJob?.cancel()
            val c = DeviceSyncs.get(app)
            if (c.running) { NxTestResults.write(app, "sync", "state" to "refused", "reason" to "a sync is already running"); return }
            NxTestResults.write(app, "sync", "state" to "running", "link" to link.name)
            syncJob = scope.launch {
                c.open()
                c.pick(link)
                val ready = c.page.value as? DeviceSyncPage.Ready
                if (ready != null && ready.romsAvailable && ready.roms != roms) c.onButton(NxButton.X)
                val romsOn = (c.page.value as? DeviceSyncPage.Ready)?.roms ?: false
                // Subscribed before A, so a refusal's notice (emitted inside onButton) is caught.
                var notice: String? = null
                val notices = launch(start = CoroutineStart.UNDISPATCHED) { c.notices.collect { if (notice == null) notice = it } }
                c.onButton(NxButton.A)
                // The collector resumes only once this coroutine suspends (the immediate dispatcher's event loop).
                if (!c.running) delay(500)
                notices.cancel()
                if (!c.running) {
                    NxTestResults.write(app, "sync", "state" to "refused", "reason" to (notice ?: "not started"), "roms" to romsOn)
                    return@launch
                }
                val end = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                    c.page.first { it is DeviceSyncPage.Done || it is DeviceSyncPage.Failed || it == DeviceSyncPage.NoWifi }
                }
                when (end) {
                    is DeviceSyncPage.Done -> NxTestResults.write(app, "sync", "state" to "done", "received" to end.received,
                        "sent" to end.sent, "notes" to end.notes.joinToString("\n"), "roms" to romsOn)
                    is DeviceSyncPage.Failed -> NxTestResults.write(app, "sync", "state" to "failed", "reason" to end.reason,
                        "detail" to end.detail, "roms" to romsOn)
                    DeviceSyncPage.NoWifi -> NxTestResults.write(app, "sync", "state" to "failed", "reason" to "no wifi", "roms" to romsOn)
                    else -> NxTestResults.write(app, "sync", "state" to "timeout", "roms" to romsOn)
                }
            }
        }

        /** As the user would: B out of Searching / WaitingForCable / NoWifi, confirmed Stop on Syncing. */
        fun stopSync(app: Context) {
            syncJob?.cancel()
            syncJob = null
            val c = DeviceSyncs.current()
            when (val p = c?.page?.value) {
                is DeviceSyncPage.Searching, DeviceSyncPage.WaitingForCable, DeviceSyncPage.NoWifi -> c.onButton(NxButton.B)
                is DeviceSyncPage.Syncing -> c.cancel()
                else -> NxLog.i(TAG, "SYNC_STOP on $p")
            }
            NxTestResults.write(app, "sync", "state" to "stopped")
        }

        /**
         * The launcher's path (AppState.launch → LibretroRunner) for a ROM read by path (a file ref): the tag's core,
         * a disc sheet's companions from its folder, [slot] as the resume slot, in its own task (the receiver has no
         * activity). Not recorded in Recently Played or game time (no game path).
         */
        fun launchGame(app: Context, path: String, tag: String, slot: Int?) {
            val core = CoreRegistry.forTag(tag)
            val file = File(path)
            if (core == null || !file.isFile) {
                NxTestResults.write(app, "game", "state" to "refused", "reason" to if (core == null) "no core for $tag" else "no file $path")
                return
            }
            val folder = FileRefs.of(file.parent ?: "/")
            scope.launch {
                val lookup = runCatching { DiscSheet.companions(FileRomsSource({ true }, { true }), folder, file.name) }
                    .getOrElse { CompanionLookup.Missing(it.message ?: "companions", file.name) }
                val companions = when (val found = lookup) {
                    is CompanionLookup.Found -> found.companions
                    is CompanionLookup.Missing -> {
                        NxTestResults.write(app, "game", "state" to "refused", "reason" to "missing ${found.name}")
                        return@launch
                    }
                }
                val session = GameSession(tag.uppercase(), file.nameWithoutExtension, core, FileRefs.of(path), folder, file.name,
                    resumeSlot = slot, companions = companions)
                NxTestResults.markShowWhenLocked(app)
                LibretroRunner(app) { true }.apply { recordLaunches = false }.launch(session, com.nxredux.mobile.storage.Recent(path))
            }
        }
    }
}
