package com.nxredux.mobile.runner

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.R
import com.nxredux.mobile.lowLatencyWifiLock
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayLinkOptions
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySaves
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplaySessionDir
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.cablelink.CheckpointFiles
import com.nxredux.mobile.netplay.cablelink.PairSaveCheckpoint
import com.nxredux.mobile.netplay.wizard.CableLinkSetup
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.PrefsProvider
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.NotificationQueue
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroNetplay
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

/**
 * What [GameNetplay] needs from [GameActivity]: the pause state the session follows, the notices and menu it updates,
 * and the screen's ways out (the failure dialog, Quit, the local game a link session's end leaves).
 */
internal interface GameNetplayHost : GameScreenHost {
    val menuOpen: Boolean
    val focusPaused: Boolean
    val cheatsSupported: Boolean
    var lastSlotCache: Int?
    val notifications: NotificationQueue
    fun publishNotifications()
    fun withMenu(block: (InGameMenuModel) -> Unit)
    fun fail(message: String, title: String = GameActivity.START_FAILED)
    fun quitGame(chosen: Boolean, autoState: Boolean)
    fun becomeLocal(localCheats: (() -> Job)?)
    /** Opens the in-game menu (nothing when it is open). */
    fun showMenu()
}

/**
 * The game screen's side of a netplay launch (Wi-Fi or USB lockstep, or a GB / GBA link): the session read from the
 * intent, its engine ([NetplayRun]) and Wi-Fi lock, the session's saves, its notices, Leave, and a link session's end,
 * after which the game is a local one ([local]). Not the menu's netplay sheet. Main thread, but for [prepareSaves]' IO.
 */
internal class GameNetplay(private val host: GameNetplayHost) {
    /** The intent's Cable Link fields, or null for any other launch. */
    private fun cableLinkFrom(intent: Intent): CableLinkSetup? {
        if (!intent.getBooleanExtra(GameActivity.EXTRA_NETPLAY_CABLE_LINK, false)) return null
        return CableLinkSetup(
            otherRomPath = intent.getStringExtra(GameActivity.EXTRA_NETPLAY_OTHER_ROM).orEmpty(),
            otherSavePath = intent.getStringExtra(GameActivity.EXTRA_NETPLAY_OTHER_SAVE),
            localSlot = intent.getIntExtra(GameActivity.EXTRA_NETPLAY_LOCAL_SLOT, 0),
            biosMode = intent.getStringExtra(GameActivity.EXTRA_NETPLAY_BIOS).orEmpty(),
            rtcEpoch = intent.getLongExtra(GameActivity.EXTRA_NETPLAY_RTC, 0L),
        )
    }

    /**
     * netplay: this launch's session (read in onCreate from the EXTRA_NETPLAY_* extras); null for every other launch. In a
     * session there are no save states or auto-resume, no cheats, Core Options or local multiplayer slots, no achievements
     * outside a link session ([NetplaySession.allowsAchievements]), and the menu is only "Leave netplay?".
     */
    var session: NetplaySession? = null
        private set
    /** netplay: recreated after a process kill with a session's intent: that session is gone, nothing is started. */
    var restored = false
        private set
    /** netplay over USB: the cable's start this screen ran on; a newer sheet start makes this screen's stop a no-op. */
    private var usbToken: Long? = null
    /** netplay: the engine session; set just before it starts, so onDestroy always shuts down what was started. */
    var run: NetplayRun? = null
        private set
    /**
     * A Wi-Fi session's radio out of power save while its engine runs: a delayed frame's input stalls both players. Held
     * from the engine's start to the session's end, not for a session that never starts (restored after a kill).
     */
    private var wifi: android.net.wifi.WifiManager.WifiLock? = null
    private var pillShown = false   // the countdown pill is the session's (hidden again on Clear)
    /** netplay: the session ended and the game plays on alone (the menu is then Continue / Quit). */
    var ended = false
        private set
    /**
     * netplay link: the link session ended ([playLocally]), or this screen came back after a process kill with one that
     * had: the game is a normal local launch from then on. The one switch the menu, the panel and the option guards
     * read, through [now]; [session] itself stays for the launch's setup and onDestroy's release.
     */
    var local = false
        private set
    /** netplay: the session this screen plays in now: [session], or null once a link session's game is a local one. */
    val now: NetplaySession? get() = session.takeUnless { local }
    /** netplay link: starts the cheat load a normal launch starts at once ([LaunchPipeline]), once the game is local ([playLocally]). */
    var localCheats: (() -> Job)? = null
    /** netplay: this screen's id on the session copy it plays on ([NetplaySessionDir.claim]). */
    private val owner by lazy { java.util.UUID.randomUUID().toString() }
    /** netplay: the engine did not start; the game stays paused and silent behind the failure dialog (closeMenu too). */
    var startFailed = false
        private set
    @Volatile private var ownCopyFailed = false   // a host's copy of its own saves failed: it starts without them
    /** Cable Link: the real save's checkpoints ([attachCheckpoint]); null for every other launch. */
    private var pairSaves: PairSaveSession? = null

    /** onCreate: the session this launch plays (a session launches cold: no saved or requested resume slot). */
    fun onCreate(intent: Intent, savedInstanceState: Bundle?) {
        val launchSession = netplaySessionFrom(
            intent.getStringExtra(GameActivity.EXTRA_NETPLAY_ROLE), intent.getStringExtra(GameActivity.EXTRA_NETPLAY_PEER),
            intent.getStringExtra(GameActivity.EXTRA_CORE_ID) ?: "", intent.getStringExtra(GameActivity.EXTRA_NETPLAY_FOLDER_CORE),
            intent.getBooleanExtra(GameActivity.EXTRA_NETPLAY_EMPTY_SAVES, false),
            intent.getBooleanExtra(GameActivity.EXTRA_NETPLAY_USB, false),
            intent.getBooleanExtra(GameActivity.EXTRA_NETPLAY_MIXED, false),
            intent.getStringExtra(GameActivity.EXTRA_NETPLAY_PEER_WL),
            cableLinkFrom(intent),
        )
        // netplay link: recreated after a process kill once its session had ended: the local game it had become, as a
        // normal launch (its resume slot below), not a session.
        local = launchSession?.endsAsLocalGame == true && savedInstanceState?.getBoolean(KEY_NETPLAY_LOCAL) == true
        session = launchSession.takeUnless { local }
        restored = session != null && savedInstanceState != null
        // The cable is the menu process's: never instantiated here (a fresh, unstarted one would stop the menu's VPN).
        if (session?.overUsb == true) usbToken = intent.getLongExtra(GameActivity.EXTRA_NETPLAY_USB_TOKEN, -1L).takeIf { it >= 0 }
    }

    fun onSaveInstanceState(outState: Bundle) {
        if (local) outState.putBoolean(KEY_NETPLAY_LOCAL, true)
    }

    /**
     * After the native destroy (super.onDestroy), off the main thread: the engine shut down, a session copy deleted.
     * Only this screen's own claim is deleted: a newer launch's copy (committed before this teardown) is kept.
     */
    fun onDestroyed() {
        releaseWifi()
        // Cable Link ended by the screen going (a quit, the system): the last verified checkpoint becomes the real save. A
        // verdict still in flight is lost with the engine, which the native destroy already shut down.
        pairSaves?.end(null)
        session?.let { np ->
            val owner = owner
            val filesDir = host.activity.filesDir
            NetplayGame.release(run, if (np.onCopy) ({ NetplaySessionDir.release(filesDir, owner) }) else null)
            // The cable carried this session (the sheet handed it over at launch), unless a newer launch's start took it over.
            // Stopped in the menu process, which owns it: a Binder call, so off the main thread.
            usbToken?.takeIf { np.overUsb }?.let { token ->
                val app = host.activity.applicationContext
                thread(name = "usb-cable-stop") {
                    runCatching { PrefsProvider.usbCableStopIf(app, token) }.onFailure { NxLog.w(TAG_LOG, "could not stop the USB cable", it) }
                }
            }
        }
    }

    /**
     * netplay: the saves a session plays on, before the battery load. A host on its folder's own core keeps its
     * real saves but loses the game's auto-resume state (handheld 19d3a5e1), so a later resume cannot roll back battery
     * saves written in the session; manual slots stay. A host on another core plays on a fresh copy of its own battery
     * saves in the session folder; one whose saves were not served (emptySaves) on an empty copy. A client plays on the
     * host's saves, committed there before launch (an empty folder when the host had none).
     */
    suspend fun prepareSaves(np: NetplaySession, tag: String, romFileName: String, dataRoot: File, paths: NxPaths) {
        val filesDir = host.activity.filesDir
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    !np.onCopy -> {
                        SaveSession.awaitAutoWrites()   // a Home write of this game's auto state may still be landing
                        val marker = File(dataRoot, NxPaths(tag, np.folderCoreId, romFileName).lastSlot).takeIf { it.exists() }?.readText()
                        NetplaySaves.resumeVictims(tag, np.folderCoreId, romFileName, marker, paths.naming, paths.innerFileName) { File(dataRoot, it).exists() }
                            .forEach { File(dataRoot, it).delete() }
                        host.lastSlotCache = null
                    }
                    // A host on another core plays on a copy of its own saves; one whose saves were not served on an empty
                    // copy, as the joiner does. Under the session folder's lock (prepareHost).
                    np.role == NetplayRole.HOST -> {
                        val savesDir = if (np.emptySaves) null else File(dataRoot, paths.savesDir)
                        val copied = NetplaySessionDir.prepareHost(filesDir, owner, savesDir, paths) {
                            NxLog.e(TAG_LOG, "netplay: copying the host's own saves failed", it)
                        }
                        if (!copied) ownCopyFailed = true
                    }
                    // The host's copy committed before launch (kept), or an empty one; a copy an older screen claimed is stale.
                    else -> NetplaySessionDir.claim(filesDir, owner)
                }
            }.onFailure {
                NxLog.e(TAG_LOG, "netplay saves setup failed", it)
                // A host on a copy whose claim itself failed plays without its saves too: say so (first frame).
                if (np.role == NetplayRole.HOST && np.onCopy) ownCopyFailed = true
            }
        }
    }

    /**
     * netplay: the engine starts once the core has loaded and can serialize, so a handheld's 10 s state waits begin only
     * now. The host listens (its 45 s CONNECTING limit counts from here); the client connects with its 60 × 500 ms
     * retries. A menu or background already in place pauses it at once. False: it did not start, and the failure dialog
     * is up.
     */
    suspend fun onFirstFrame(np: NetplaySession, coreName: String): Boolean {
        if (np.savesAtCheckpoints) host.retroView?.let { view -> view.afterStep = { afterPairFrame(view) } }
        if (!start(np)) {
            // The game must not play on (or be heard) behind the dialog, a menu closed meanwhile included.
            startFailed = true
            host.retroView?.frameSpeed = 0
            host.retroView?.audioEnabled = false
            host.fail(NetplayText.FAILED, NetplayText.FAILED)
            return false
        }
        if (host.menuOpen || host.focusPaused || !host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) run?.pause()
        val now = SystemClock.elapsedRealtime()
        netplayFirstFrameNotices(np, coreName, ownCopyFailed).forEach {
            host.notifications.push(it, null, NETPLAY_NOTICE_MS, now)
        }
        host.publishNotifications()
        return true
    }

    /**
     * netplay: starts this screen's session once the first frame is up ([run] set first, so onDestroy shuts down
     * what started). A GBA link's engine started before the load ([connectLink]); its status ticks from now. GB link has
     * no engine: gambatte runs it.
     */
    private suspend fun start(np: NetplaySession): Boolean {
        holdWifi(np)
        return when (np.linkKind) {
            null -> {
                val settings = AndroidAppSettings(host.activity)
                val overlay = np.cableLink && settings.cableLinkDelayOverlay
                NetplayGame(
                    np, host.lifecycleScope, SystemClock::elapsedRealtime, ::onNotice,
                    delayPin = if (np.cableLink) settings.cableLinkDelayPin else 0,
                    onDelay = if (overlay) ::showDelay else null,
                    onEnding = { verified -> pairSaves?.end(verified) },
                ).also { run = it }.start()
            }
            NetplayLinkKind.NETPACKET -> (run as? NetplayLinkGame)?.let { it.begin(); it.live } ?: false
            NetplayLinkKind.GBLINK -> true
        }
    }

    /** netplay link: the GBA link engine, started before the core loads; the raw link mode the core runs with, or null. */
    suspend fun connectLink(np: NetplaySession, ownSerial: String?): String? {
        holdWifi(np)
        return NetplayLinkGame(np, host.lifecycleScope, SystemClock::elapsedRealtime, ::onNotice).also { run = it }.connect(ownSerial)
    }

    /** The low-latency Wi-Fi lock for a session over Wi-Fi; once (the link engine's connect comes before its start). */
    private fun holdWifi(np: NetplaySession) {
        if (np.overUsb || wifi != null) return
        wifi = lowLatencyWifiLock(host.activity, "nxredux:netplay")?.also { runCatching { it.acquire() } }
    }

    /** The session is over (or the screen is): the radio may save power again. */
    private fun releaseWifi() {
        wifi?.let { runCatching { if (it.isHeld) it.release() } }
        wifi = null
    }

    /** netplay: a status notice from [NetplayGame]'s ticker (main thread). */
    private fun onNotice(notice: NetplayNotice) {
        val notifications = host.notifications
        val now = SystemClock.elapsedRealtime()
        when (notice) {
            NetplayNotice.Clear -> {
                if (!pillShown) return
                notifications.hideProgress()
                pillShown = false
            }
            is NetplayNotice.Pill -> {
                notifications.progress(notice.text, "", null, NETPLAY_PILL_MS, now)
                pillShown = true
            }
            is NetplayNotice.Ended -> {
                showDelay(null)
                releaseWifi()
                if (pillShown) notifications.hideProgress()
                pillShown = false
                notifications.push(notice.text, null, NETPLAY_NOTICE_MS, now)
                if (session?.endsAsLocalGame == true) linkEnded()
                else if (session?.cableLink == true) cableLinkEnded()
                else {
                    // The ended session holds no frames: an open menu pauses the game as for any other launch.
                    if (host.menuOpen || host.focusPaused) host.retroView?.frameSpeed = 0
                    ended = true
                    host.withMenu { it.endNetplay(local = false) }
                }
            }
            is NetplayNotice.Failed -> {
                showDelay(null)
                if (pillShown) notifications.hideProgress()
                pillShown = false
                host.fail(notice.text, NetplayText.FAILED)
            }
        }
        host.publishNotifications()
    }

    /**
     * The menu's Leave: DISCONNECT and shut the engine down (off main), then quit; there are no save states: no auto
     * state. A link session ends the link instead and plays on, the menu open on the normal root.
     */
    fun leave() {
        if (session?.endsAsLocalGame == true) leaveLink() else { run?.leave(); host.quitGame(chosen = true, autoState = true) }
    }

    /**
     * netplay link: Leave. The link ends and the game plays on ([linkEnded]); the menu stays open on the normal root and
     * the player leaves later with Quit. A GBA link's engine sends DISCONNECT and shuts down off main; [NetplayRun.leave]
     * detaches it first, so no Ended notice follows. GB link has no engine: gambatte's network serial stops on its mode's
     * variable update, read at the next frame (the menu holds frames, so on Continue). Set past [GameActivity.applyOptions]'
     * guard and not stored: the link options are this launch's ([GameActivity.graphicsForcedOptions]), never the user's config.
     */
    private fun leaveLink() {
        run?.leave()
        if (pillShown) { host.notifications.hideProgress(); pillShown = false; host.publishNotifications() }   // the ticker is gone
        if (session?.linkKind == NetplayLinkKind.GBLINK) host.retroView?.let { view ->
            val off = Variable(NetplayLinkOptions.GB_MODE, NetplayLinkOptions.GB_OFF)
            view.queueEvent { runCatching { view.updateVariables(off) }.onFailure { NxLog.w("GameActivity", "ending the GB link failed", it) } }
        }
        linkEnded()
    }

    /**
     * netplay link: the session is over, from the other side or the network ([NetplayNotice.Ended]) or by Leave
     * ([leaveLink]): the game is a local one from here ([playLocally]); an open menu stays open, paused, and becomes the
     * normal root on the slot a normal launch shows.
     */
    private fun linkEnded() {
        releaseWifi()
        // The link holds no frames now: an open menu pauses the game as for any other launch.
        if (host.menuOpen || host.focusPaused) host.retroView?.frameSpeed = 0
        ended = true
        playLocally()
        host.withMenu {
            it.cheatsAvailable = host.cheatsSupported
            it.endNetplay(local = true)
        }
    }

    /**
     * Cable Link: the session is over and its save is committed from the last verified checkpoint, so the game stops (it
     * holds the other player's GBA, and nothing more would be saved) and the menu offers only Quit ([quitOnly]).
     */
    private fun cableLinkEnded() {
        ended = true
        host.retroView?.frameSpeed = 0
        host.retroView?.audioEnabled = false
        host.showMenu()
        host.withMenu { it.endNetplay(local = false, quitOnly = true) }
    }

    /** Cable Link's session ended: the game stays stopped and the menu stays open on Quit alone. */
    val quitOnly: Boolean get() = ended && session?.cableLink == true

    /** netplay link: the session ended; from here [now] is null and the game is the normal launch ([GameActivity.becomeLocal]). */
    private fun playLocally() {
        if (local) return
        local = true
        host.becomeLocal(localCheats.also { localCheats = null })
    }

    /** Cable Link: the launch's checkpoint files (the real save, through the battery save's naming and encoding). */
    fun attachCheckpoint(files: CheckpointFiles) {
        pairSaves = PairSaveSession(PairSaveCheckpoint(files), PairSaveFiles.writer::execute)
    }

    /**
     * Cable Link, on the GL thread after each step: the local save is snapshot at each drift-check frame, and the one the
     * engine's last verified frame names becomes the checkpoint (written off this thread).
     */
    private fun afterPairFrame(view: GLRetroView) {
        val saves = pairSaves ?: return
        runCatching {
            saves.afterFrame(LibretroNetplay.runFrame(), LibretroNetplay.lastVerifiedFrame()) { view.serializeSRAM(useEmulationThread = false) }
        }.onFailure { NxLog.w(TAG_LOG, "pair save snapshot failed", it) }
    }

    /** The Developer overlay's input delay; null hides it. Main thread. */
    private fun showDelay(frames: Int?) {
        val label = host.activity.findViewById<TextView>(R.id.netplayDelay) ?: return
        if (frames == null) { label.visibility = View.GONE; return }
        label.text = NetplayText.inputDelay(frames)
        label.visibility = View.VISIBLE
    }

    private companion object {
        const val KEY_NETPLAY_LOCAL = "nx_netplay_local"   // netplay link: the session had ended ([local])
        const val NETPLAY_NOTICE_MS = 5000L   // netplay: "Netplay ended ...", "Using <core> for this match ..."
        const val NETPLAY_PILL_MS = 1000L     // netplay: a countdown pill outlives one 250 ms status tick
    }
}
