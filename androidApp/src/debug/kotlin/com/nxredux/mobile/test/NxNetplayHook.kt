package com.nxredux.mobile.test

import android.content.Context
import com.nxredux.mobile.netplay.AndroidNetplay
import com.nxredux.mobile.netplay.NetplayChannel
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayWire
import com.nxredux.mobile.netplay.wizard.ClientOffer
import com.nxredux.mobile.netplay.wizard.ClientOutcome
import com.nxredux.mobile.netplay.wizard.HostOffer
import com.nxredux.mobile.netplay.wizard.HostOutcome
import com.nxredux.mobile.netplay.wizard.Wizard
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.runner.LibretroRunner
import com.nxredux.mobile.runner.PadMap
import com.nxredux.mobile.runner.SegaPadType
import com.nxredux.mobile.storage.DataRoot
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.Recent
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * NETPLAY (`--es role host|client --es path <rom> --es tag <TAG> --es core <core> [--es peer <host ip>] [--es pad
 * auto|3|6|none] [--es force_pad 3|6]`): one Wi-Fi session through the real handshake ([Wizard] over [AndroidNetplay]: caps, save copy, START)
 * with the Emulator pinned to [core], then the game as the netplay sheet launches it. No discovery: the host listens, the
 * client connects to `peer` (retried for 30 s). `pad`: the Controller Type this side offers; absent, the game's own
 * setting; `none`, no `pad=` at all (an older phone or the handheld). `force_pad`: this side plays that pad whatever the
 * handshake agreed, so a test can make the two sides disagree on purpose (the drift check must then fire). netplay.json: state `listening` (host),
 * `started` (with core and pad) or `refused` (with the reason).
 */
internal object NxNetplayHook {
    private const val TAG = "NxTest"
    private const val CONNECT_TRIES = 60
    private const val CONNECT_GAP_MS = 500L
    private var job: Job? = null

    fun start(
        app: Context, scope: CoroutineScope, role: String?, peer: String?, path: String?, tag: String?, core: String?, pad: String?,
        forcePad: String?,
    ) {
        fun refuse(reason: String) = NxTestResults.write(app, "netplay", "state" to "refused", "reason" to reason)
        val host = role == "host"
        if ((!host && role != "client") || path == null || tag == null || core == null || (!host && peer == null)) {
            refuse("role, path, tag, core (and peer for a client) are required"); return
        }
        val file = File(path)
        if (!file.isFile) { refuse("no file $path"); return }
        val descriptor = CoreRegistry.forTag(tag.uppercase()) ?: run { refuse("no core for $tag"); return }
        job?.cancel()
        job = scope.launch {
            val np = AndroidNetplay(app) { DataRoot.of(app) }
            val rom = file.name
            val game = NetplayWire.gameName(rom)
            val offered = when {
                !PadMap.isSegaPadTag(tag) || pad == "none" -> null
                pad != null -> pad
                else -> runCatching { np.segaPadSetting(tag, core, rom) }.getOrDefault(SegaPadType.AUTO)
            }
            NxLog.i(TAG, "NETPLAY $role $rom on $core, pad ${offered ?: "none"}")
            val agreed = try {
                if (host) {
                    NxTestResults.write(app, "netplay", "state" to "listening")
                    val ch = np.accept()
                    val out = ch.use {
                        Wizard.host(it, HostOffer(game, tag, core, core, usb = false, pad = offered), saves = { np.hostSaves(tag, rom, FileRefs.of(path)) })
                    }
                    if (out !is HostOutcome.Started) { refuse("turned: ${(out as HostOutcome.Turned).reason}"); return@launch }
                    NetplaySession(NetplayRole.HOST, out.peer, out.core, descriptor.id, out.emptySaves, segaPad = out.pad)
                } else {
                    val ch = connect(np, peer!!) ?: run { refuse("no connection to $peer"); return@launch }
                    val out = ch.use {
                        Wizard.client(it, ClientOffer(game, false, tag, core, core, usb = false, mayRetry = false, pad = offered), np.clientSink(rom))
                    }
                    if (out !is ClientOutcome.Started) { refuse("join: $out"); return@launch }
                    NetplaySession(NetplayRole.CLIENT, peer, out.core, descriptor.id, segaPad = out.pad)
                }
            } catch (e: Exception) {
                refuse("handshake: ${e.message}"); return@launch
            }
            val session = forcePad?.let { agreed.copy(segaPad = it) } ?: agreed
            NxTestResults.write(app, "netplay", "state" to "started", "core" to session.coreId, "pad" to session.segaPad)
            val runCore = CoreRegistry.forId(session.coreId) ?: descriptor   // the agreed core, as LaunchController.launchNetplay
            NxTestResults.markShowWhenLocked(app)
            LibretroRunner(app) { true }.apply { recordLaunches = false }.launch(
                GameSession(tag.uppercase(), file.nameWithoutExtension, runCore, FileRefs.of(path), FileRefs.of(file.parent ?: "/"), rom, netplay = session),
                Recent(path),
            )
        }
    }

    private suspend fun connect(np: AndroidNetplay, ip: String): NetplayChannel? {
        repeat(CONNECT_TRIES) {
            runCatching { return np.connect(ip) }
            delay(CONNECT_GAP_MS)
        }
        return null
    }

    private inline fun <T> NetplayChannel.use(block: (NetplayChannel) -> T): T = try { block(this) } finally { close() }
}
