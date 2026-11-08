package com.nxredux.mobile.runner

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.lowLatencyWifiLock
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayLinkOptions
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySaves
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplaySessionDir
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.settings.PrefsProvider
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.NotificationQueue
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
internal interface GameNetplayHost : LifecycleOwner {
    val activity: ComponentActivity
    val retroView: com.swordfish.libretrodroid.GLRetroView?
    val menuOpen: Boolean
    val focusPaused: Boolean
    val cheatsSupported: Boolean
    var lastSlotCache: Int?
    val notifications: NotificationQueue
    fun publishNotifications()
    fun withMenu(block: (InGameMenuModel) -> Unit)
    fun fail(message: String, title: String)
    fun quitGame(chosen: Boolean, autoState: Boolean)
    fun becomeLocal(localCheats: (() -> Job)?)
}

/**
 * The game screen's side of a netplay launch (Wi-Fi or USB lockstep, or a GB / GBA link): the session read from the
 * intent, its engine ([NetplayRun]) and Wi-Fi lock, the session's saves, its notices, Leave, and a link session's end,
 * after which the game is a local one ([local]). Not the menu's netplay sheet. Main thread, but for [prepareSaves]' IO.
 */
internal class GameNetplay(private val host: GameNetplayHost) {
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
    /** A Wi-Fi session's radio out of power save for the screen's life: a delayed frame's input stalls both players. */
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

    /** onCreate: the session this launch plays (a session launches cold: no saved or requested resume slot). */
    fun onCreate(intent: Intent, savedInstanceState: Bundle?) {
        val launchSession = netplaySessionFrom(
            intent.getStringExtra(GameActivity.EXTRA_NETPLAY_ROLE), intent.getStringExtra(GameActivity.EXTRA_NETPLAY_PEER),
            intent.getStringExtra(GameActivity.EXTRA_CORE_ID) ?: "", intent.getStringExtra(GameActivity.EXTRA_NETPLAY_FOLDER_CORE),
            intent.getBooleanExtra(GameActivity.EXTRA_NETPLAY_EMPTY_SAVES, false),
            intent.getBooleanExtra(GameActivity.EXTRA_NETPLAY_USB, false),
        )
        // netplay link: recreated after a process kill once its session had ended: the local game it had become, as a
        // normal launch (its resume slot below), not a session.
        local = launchSession?.endsAsLocalGame == true && savedInstanceState?.getBoolean(KEY_NETPLAY_LOCAL) == true
        session = launchSession.takeUnless { local }
        restored = session != null && savedInstanceState != null
        // The cable is the menu process's: never instantiated here (a fresh, unstarted one would stop the menu's VPN).
        if (session?.overUsb == true) usbToken = intent.getLongExtra(GameActivity.EXTRA_NETPLAY_USB_TOKEN, -1L).takeIf { it >= 0 }
        if (session?.overUsb == false) wifi = lowLatencyWifiLock(host.activity, "nxredux:netplay")?.also { runCatching { it.acquire() } }
    }

    fun onSaveInstanceState(outState: Bundle) {
        if (local) outState.putBoolean(KEY_NETPLAY_LOCAL, true)
    }

    /**
     * After the native destroy (super.onDestroy), off the main thread: the engine shut down, a session copy deleted.
     * Only this screen's own claim is deleted: a newer launch's copy (committed before this teardown) is kept.
     */
    fun onDestroyed() {
        wifi?.let { runCatching { if (it.isHeld) it.release() } }
        wifi = null
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
    private suspend fun start(np: NetplaySession): Boolean = when (NetplayCores.link(np.coreId)) {
        null -> NetplayGame(np, host.lifecycleScope, SystemClock::elapsedRealtime, ::onNotice).also { run = it }.start()
        NetplayLinkKind.NETPACKET -> (run as? NetplayLinkGame)?.let { it.begin(); it.live } ?: false
        NetplayLinkKind.GBLINK -> true
    }

    /** netplay link: the GBA link engine, started before the core loads; the raw gpsp_serial the core runs with, or null. */
    suspend fun connectLink(np: NetplaySession, ownSerial: String?): String? =
        NetplayLinkGame(np, host.lifecycleScope, SystemClock::elapsedRealtime, ::onNotice).also { run = it }.connect(ownSerial)

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
                if (pillShown) notifications.hideProgress()
                pillShown = false
                notifications.push(notice.text, null, NETPLAY_NOTICE_MS, now)
                if (session?.endsAsLocalGame == true) linkEnded() else {
                    // The ended session holds no frames: an open menu pauses the game as for any other launch.
                    if (host.menuOpen || host.focusPaused) host.retroView?.frameSpeed = 0
                    ended = true
                    host.withMenu { it.endNetplay(local = false) }
                }
            }
            is NetplayNotice.Failed -> {
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
        if (session?.let { NetplayCores.link(it.coreId) } == NetplayLinkKind.GBLINK) host.retroView?.let { view ->
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
        // The link holds no frames now: an open menu pauses the game as for any other launch.
        if (host.menuOpen || host.focusPaused) host.retroView?.frameSpeed = 0
        ended = true
        playLocally()
        host.withMenu {
            it.cheatsAvailable = host.cheatsSupported
            host.lastSlotCache?.let { slot -> it.showSlot(slot - 1) }
            it.endNetplay(local = true)
        }
    }

    /** netplay link: the session ended; from here [now] is null and the game is the normal launch ([GameActivity.becomeLocal]). */
    private fun playLocally() {
        if (local) return
        local = true
        host.becomeLocal(localCheats.also { localCheats = null })
    }

    private companion object {
        const val KEY_NETPLAY_LOCAL = "nx_netplay_local"   // netplay link: the session had ended ([local])
        const val NETPLAY_NOTICE_MS = 5000L   // netplay: "Netplay ended ...", "Using <core> for this match ..."
        const val NETPLAY_PILL_MS = 1000L     // netplay: a countdown pill outlives one 250 ms status tick
    }
}
