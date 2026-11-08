package com.nxredux.mobile.netplay

import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.netplay.usb.UsbCable
import com.nxredux.mobile.netplay.wizard.HostSaves
import com.nxredux.mobile.netplay.wizard.SaveSink
import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.flow.Flow

/** This device on Wi-Fi: its IPv4 address and the name shown to the other player. */
data class WifiInfo(val ip: String, val deviceName: String)

/** A host found by discovery: its address and the game it advertises. */
data class FoundHost(val ip: String, val game: String)

/** A running discovery broadcast; [stop] ends it (idempotent). */
interface Advert { fun stop() }

/** The platform side of netplay (sockets, Wi-Fi, discovery, save copies), implemented per platform. */
interface NetplayPlatform {
    /** null when not on Wi-Fi. */
    fun wifi(): WifiInfo?
    fun openWifiSettings()
    /** Broadcasts [game] on UDP [NetplayWire.DISCOVERY_PORT] every second until [Advert.stop]. */
    fun advertise(game: String): Advert
    /** Protocol 2 "wizard" hosts, deduped by IP, this device's own IP ignored. */
    fun browse(): Flow<FoundHost>
    /** Waits for one client on TCP [NetplayWire.CONTROL_PORT] (backlog 1); cancellable. */
    suspend fun accept(): NetplayChannel
    /** Sends the discovery packet for [game] to [ip] alone every second until [Advert.stop] (a cable has no broadcast). */
    fun advertiseTo(game: String, ip: String): Advert
    /** One 5 s attempt to reach a host's control port; throws [NetplayIoException]. [link]: USB binds to the cable. */
    suspend fun connect(ip: String, link: NetplayLink = NetplayLink.WIFI): NetplayChannel
    /** The USB cable link; null where the platform has none. */
    val usb: UsbCable? get() = null
    /** The Connection the Role page showed last, remembered for the next session. */
    var lastLink: NetplayLink
    /**
     * The game's battery saves from private storage, staged as `nxsave.srm`/`nxsave.sav` for one joiner; throws when
     * unreadable. [content] is the game file, listed when it is an archive the runner unpacks (saves may be named after
     * the inner file).
     */
    suspend fun hostSaves(tag: String, romFileName: String, content: ContentRef): HostSaves
    /**
     * The GBA game's own link mode as a caps token ([GbaWifiLink]), read from [content] (the game file [romFileName]);
     * "none" when it cannot be read as a ROM (an archive, a document that will not open). Reads the whole ROM, on the
     * platform's I/O threads.
     */
    suspend fun gbaLinkMode(romFileName: String, content: ContentRef): String
    /** Where a joiner's pulls land (a fresh session copy per join). */
    fun clientSink(romFileName: String): SaveSink
    fun keepScreenOn(on: Boolean)
}

/**
 * Handed to the runner with the launch (GameSession.netplay). [emptySaves]: a save copy was due but did not happen (this
 * device has no usable rsync), so this side plays on an empty isolated copy, as the other side does.
 * [overUsb]: the session runs over the USB cable; the game screen stops the cable when it releases the session.
 * [mixed]: a GBA link whose caps named gpSP on one side and mGBA on the other (the wizard outcome's `mixed`); [peerWl]:
 * the other side's game link mode from its caps (null: it sent none, a build without the Wi-Fi modes).
 */
data class NetplaySession(
    val role: NetplayRole,
    val peerIp: String,
    val coreId: String,
    val folderCoreId: String,
    val emptySaves: Boolean = false,
    val overUsb: Boolean = false,
    val mixed: Boolean = false,
    val peerWl: String? = null,
) {
    /** A link session (GB/GBC/GBA) never plays on a copy: each player keeps their own save, which a trade needs. */
    val onCopy: Boolean get() = !NetplayCores.isLink(coreId) && (emptySaves || NetplaySaves.playsOnCopy(role, coreId, folderCoreId))

    /**
     * Once the session ends, the game is a normal local one: a link session's (each player runs their own game on their
     * own save, nothing is synced from the host). A lockstep session's game is the host's synced state: it stays a
     * netplay game (Continue / Quit, no states, options or cheats).
     */
    val endsAsLocalGame: Boolean get() = NetplayCores.isLink(coreId)

    /**
     * RetroAchievements run as in a normal launch: a link session's (each player's game is their own, on their own save,
     * so an unlock is theirs). A lockstep session has none: the client plays the host's synced state.
     */
    val allowsAchievements: Boolean get() = endsAsLocalGame
}

/** The browse filter: which discovery packets become a [FoundHost]. Pure, so every platform filters alike. */
object NetplayDiscovery {
    /**
     * The host [packet] (its [length] bytes) from [senderIp] advertises, or null when it is not a protocol 2 wizard packet,
     * comes from [ownIp], or [senderIp] is already in [seen].
     */
    fun accept(packet: ByteArray, length: Int, senderIp: String, ownIp: String?, seen: Set<String>): FoundHost? {
        if (senderIp == ownIp || senderIp in seen) return null
        val d = NetplayWire.decodeDiscovery(packet, length) ?: return null
        if (d.version != NetplayWire.VERSION || d.linkMode != NetplayWire.LINK_MODE) return null
        return FoundHost(senderIp, d.game)
    }
}
