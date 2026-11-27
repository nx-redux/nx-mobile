package com.nxredux.mobile.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nxredux.mobile.library.ArcadeCompanions
import com.nxredux.mobile.library.CompanionLookup
import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.runner.ArcadeSetsAsset
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.DsMenu
import com.nxredux.mobile.runner.N3dsDownloadPlay
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.runner.LibretroRunner
import com.nxredux.mobile.runner.LiveGameStore
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.FileRomsSource
import com.nxredux.mobile.storage.Recent
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
 * - SYNC (`--es link wifi|cable --ez roms true|false --ez confirm true|false`): starts a Device Sync as the screen
 *   would; sync.json (state "confirm" with the peer's IP while the confirm page shows). With confirm (default false) A is
 *   pressed there, so an unattended run gets past it.
 * - SYNC_STOP: stops (or backs out of) that sync; sync.json state "stopped".
 * - GAME (`--es path <rom> --es tag <TAG> [--ei slot n] [--es game_path <library path>]`): launches the ROM as the
 *   launcher does (game_path: the game's identity, so a paused one is brought back, not started again, and its game time,
 *   per-game settings and custom GPU driver apply; by default the library's path of the file under the home's Roms or an
 *   extra folder); GameActivity writes
 *   game.json, battery.json and resume.json. Send it with `am broadcast --allow-background-activity-starts`: a receiver
 *   may not start an activity from the background otherwise (Android 10+). The game shows over the lock screen and
 *   turns the screen on ([NxTestResults.markShowWhenLocked]).
 * - GAME with `--es path nds-menu: --es tag NDS`: DS Download Play (the DS menu, no file).
 * - GAME with `--es path n3ds-dlp: --es tag 3DS`: 3DS Download Play (the bundled client).
 * - GAME_STOP: the live game quits as the menu's Quit does (battery save and auto state written); its process ends.
 * - GAME_FF (`--ez on true|false`, default true): fast forward on or off in the live game, as the panel's tile sets it.
 *   As an activity start use NxGameStopActivity with `--ez ff true|false`: it runs in the game's task.
 * - GAME_DISC (`--ei index n`, 0-based): the live game changes to disc n, as the menu's Disc row does.
 * - GAME_RESET: the live game resets, as the menu's Reset does.
 * - GAME_SLOT (`--ei slot n --ez save true|false`, default load): the live game saves to or loads slot n, numbered from 1 as
 *   the menu shows it, as the menu does.
 * - NETPLAY: one Wi-Fi session through the real handshake, then the game ([NxNetplayHook]). Send it with
 *   `--allow-background-activity-starts`, as GAME.
 */
class NxTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        NxLog.i(TAG, "hook ${intent.action}")
        when (intent.action) {
            ACTION_RESET -> NxTestResults.reset(app)
            ACTION_SYNC -> {
                val link = if (intent.getStringExtra("link").equals("wifi", ignoreCase = true)) SyncLink.WIFI else SyncLink.CABLE
                startSync(app, link, intent.getBooleanExtra("roms", false), intent.getBooleanExtra("confirm", false))
            }
            ACTION_SYNC_STOP -> stopSync(app)
            ACTION_GAME -> {
                val path = intent.getStringExtra("path")
                val tag = intent.getStringExtra("tag")
                val slot = if (intent.hasExtra("slot")) intent.getIntExtra("slot", -1) else null
                if (path == null || tag == null) NxTestResults.write(app, "game", "state" to "refused", "reason" to "path and tag are required")
                else launchGame(app, path, tag, slot, intent.getStringExtra("game_path") ?: libraryPath(app, path))
            }
            // The game runs in the game process: handed on to its receiver there ([NxGameStopReceiver]).
            ACTION_GAME_STOP -> if (LiveGameStore.alive(app)) app.sendBroadcast(Intent(app, NxGameStopReceiver::class.java))
            else NxLog.w(TAG, "GAME_STOP: no live game")
            ACTION_GAME_FF -> if (LiveGameStore.alive(app)) {
                app.sendBroadcast(Intent(app, NxGameStopReceiver::class.java).putExtra(NxGameStopReceiver.EXTRA_FF, intent.getBooleanExtra("on", true)))
            } else NxLog.w(TAG, "GAME_FF: no live game")
            ACTION_GAME_DISC -> if (LiveGameStore.alive(app)) {
                app.sendBroadcast(Intent(app, NxGameStopReceiver::class.java).putExtra(NxGameStopReceiver.EXTRA_DISC, intent.getIntExtra("index", 0)))
            } else NxLog.w(TAG, "GAME_DISC: no live game")
            ACTION_GAME_RESET -> if (LiveGameStore.alive(app)) {
                app.sendBroadcast(Intent(app, NxGameStopReceiver::class.java).putExtra(NxGameStopReceiver.EXTRA_RESET, true))
            } else NxLog.w(TAG, "GAME_RESET: no live game")
            ACTION_GAME_SLOT -> if (LiveGameStore.alive(app)) {
                app.sendBroadcast(Intent(app, NxGameStopReceiver::class.java)
                    .putExtra(NxGameStopReceiver.EXTRA_SLOT, intent.getIntExtra("slot", 0))
                    .putExtra(NxGameStopReceiver.EXTRA_SAVE, intent.getBooleanExtra("save", false)))
            } else NxLog.w(TAG, "GAME_SLOT: no live game")
            ACTION_NETPLAY -> NxNetplayHook.start(
                app, scope, intent.getStringExtra("role"), intent.getStringExtra("peer"), intent.getStringExtra("path"),
                intent.getStringExtra("tag"), intent.getStringExtra("core"), intent.getStringExtra("pad"),
                intent.getStringExtra("force_pad"),
            )
        }
    }

    private companion object {
        const val TAG = "NxTest"
        const val ACTION_RESET = "com.nxredux.mobile.test.RESET"
        const val ACTION_SYNC = "com.nxredux.mobile.test.SYNC"
        const val ACTION_SYNC_STOP = "com.nxredux.mobile.test.SYNC_STOP"
        const val ACTION_GAME = "com.nxredux.mobile.test.GAME"
        const val ACTION_GAME_STOP = "com.nxredux.mobile.test.GAME_STOP"
        const val ACTION_GAME_FF = "com.nxredux.mobile.test.GAME_FF"
        const val ACTION_GAME_DISC = "com.nxredux.mobile.test.GAME_DISC"
        const val ACTION_GAME_RESET = "com.nxredux.mobile.test.GAME_RESET"
        const val ACTION_GAME_SLOT = "com.nxredux.mobile.test.GAME_SLOT"
        const val ACTION_NETPLAY = "com.nxredux.mobile.test.NETPLAY"
        const val SYNC_TIMEOUT_MS = 20 * 60 * 1000L

        /** Process-level: a hook's work outlives onReceive. Main thread, like the controller's own scope. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var syncJob: Job? = null

        fun startSync(app: Context, link: SyncLink, roms: Boolean, confirm: Boolean) {
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
                // Written either way: a harness waits for it before pressing A on the other device.
                val confirming = launch {
                    c.page.collect {
                        if (it is DeviceSyncPage.Confirm) {
                            NxTestResults.write(app, "sync", "state" to "confirm", "peer" to it.peerIp, "link" to link.name)
                            if (confirm) c.onButton(NxButton.A)
                        }
                    }
                }
                val end = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                    c.page.first { it is DeviceSyncPage.Done || it is DeviceSyncPage.Failed || it == DeviceSyncPage.NoWifi }
                }
                confirming.cancel()
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

        /** As the user would: B out of Searching / Confirm / WaitingForCable / NoWifi, confirmed Stop on Syncing. */
        fun stopSync(app: Context) {
            syncJob?.cancel()
            syncJob = null
            val c = DeviceSyncs.current()
            when (val p = c?.page?.value) {
                is DeviceSyncPage.Searching, is DeviceSyncPage.Confirm, DeviceSyncPage.WaitingForCable, DeviceSyncPage.NoWifi -> c.onButton(NxButton.B)
                is DeviceSyncPage.Syncing -> c.cancel()
                else -> NxLog.i(TAG, "SYNC_STOP on $p")
            }
            NxTestResults.write(app, "sync", "state" to "stopped")
        }

        /**
         * The library's path of the ROM file [path]: `/Roms/…` under the home folder, `/Extra/<id>/…` under an extra
         * folder; null elsewhere (the launch then has no game identity).
         */
        fun libraryPath(app: Context, path: String): String? {
            val home = AndroidHomeLocator(app).current()?.takeIf(FileRefs::isFile)?.let(FileRefs::path)
            if (home != null && path.startsWith("$home/Roms/")) return path.removePrefix(home)
            for (extra in AndroidAppSettings(app).extraFolders) {
                if (!FileRefs.isFile(extra.ref)) continue
                val root = FileRefs.path(extra.ref)
                if (!path.startsWith("$root/")) continue
                val rel = path.removePrefix("$root/")
                return GamePath.of(GameRef(extra.id, rel.substringBeforeLast('/', ""), rel.substringAfterLast('/')))
            }
            return null
        }

        /**
         * The launcher's path (AppState.launch → LibretroRunner) for a ROM read by path (a file ref): the tag's core,
         * a disc sheet's companions from its folder, [slot] as the resume slot, in its own task (the receiver has no
         * activity). Never in Recently Played; in game time only with a [gamePath].
         */
        fun launchGame(app: Context, path: String, tag: String, slot: Int?, gamePath: String?) {
            val core = CoreRegistry.forTag(tag)
            if (path == N3dsDownloadPlay.CONTENT && core != null) {
                // 3DS Download Play: the bundled client in place of a game file (as its row launches it).
                NxTestResults.markShowWhenLocked(app)
                val session = GameSession("3DS", N3dsDownloadPlay.TITLE, core, ContentRef(N3dsDownloadPlay.CONTENT), ContentRef(""),
                    N3dsDownloadPlay.FILE_NAME, null, emptyList(), fresh = true, gamePath = gamePath ?: N3dsDownloadPlay.CONTENT)
                scope.launch { LibretroRunner(app) { true }.apply { recordLaunches = false }.launch(session, Recent(path, N3dsDownloadPlay.TITLE)) }
                return
            }
            if (path == DsMenu.CONTENT && core != null) {
                // DS Download Play: no file, the game process boots the DS menu (as its row launches it).
                NxTestResults.markShowWhenLocked(app)
                val session = GameSession("NDS", DsMenu.TITLE, core, ContentRef(DsMenu.CONTENT), ContentRef(""), DsMenu.FILE_NAME,
                    null, emptyList(), fresh = true)
                scope.launch { LibretroRunner(app) { true }.apply { recordLaunches = false }.launch(session, Recent(path, DsMenu.TITLE)) }
                return
            }
            val file = File(path)
            if (core == null || !file.isFile) {
                NxTestResults.write(app, "game", "state" to "refused", "reason" to if (core == null) "no core for $tag" else "no file $path")
                return
            }
            val folder = FileRefs.of(file.parent ?: "/")
            NxLog.i(TAG, "GAME $path as ${gamePath ?: "no library game"}")
            scope.launch {
                val lookup = runCatching { DiscSheet.companions(FileRomsSource({ true }, { true }), folder, file.name) }
                    .getOrElse { CompanionLookup.Missing(it.message ?: "companions", file.name) }
                val sheet = when (val found = lookup) {
                    is CompanionLookup.Found -> found.companions
                    is CompanionLookup.Missing -> {
                        NxTestResults.write(app, "game", "state" to "refused", "reason" to "missing ${found.name}")
                        return@launch
                    }
                }
                // An fbneo set's parent and BIOS sets, as the launcher adds them (AppState.launch).
                val arcade = if (core.id == "fbneo" && CoreRegistry.loadsArchive(tag.uppercase(), file.name)) {
                    ArcadeCompanions.find(FileRomsSource({ true }, { true }), folder, file.name, ArcadeSetsAsset.get(app))
                } else null
                val session = GameSession(tag.uppercase(), file.nameWithoutExtension, core, FileRefs.of(path), folder, file.name,
                    resumeSlot = slot, companions = sheet + arcade?.companions.orEmpty(),
                    systemSets = arcade?.systemSets.orEmpty(), gamePath = gamePath)
                NxTestResults.markShowWhenLocked(app)
                LibretroRunner(app) { true }.apply { recordLaunches = false }.launch(session, Recent(path))
            }
        }
    }
}
