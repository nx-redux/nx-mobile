package com.nxredux.mobile.netplay

import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.netplay.usb.UsbCable
import com.nxredux.mobile.netplay.wizard.CableLinkOffer
import com.nxredux.mobile.netplay.wizard.CableLinkSetup
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

    /** Cable Link over Wi-Fi can run on this platform (the pair core and its files). */
    val cableLinkAvailable: Boolean get() = false

    /**
     * This side's Cable Link offer for the GBA game [romFileName] ([content]): its ROM's hash and size, its BIOS hash and
     * the files the exchange reads and writes. [findLibraryRom] answers a library ROM's ref value for a hash and size.
     * Throws [CableLinkRefused] with the player's text when the game can't be offered; anything else when it can't be read.
     */
    suspend fun cableLinkOffer(
        tag: String, romFileName: String, content: ContentRef, findLibraryRom: suspend (hash: String, sizeBytes: Long) -> String?,
    ): CableLinkOffer = throw CableLinkRefused(NetplayText.CABLE_LINK_READ_FAILED)
}

/** A game that can't be offered for Cable Link over Wi-Fi; [text] is what the sheet says. */
class CableLinkRefused(val text: String) : Exception(text)

/**
 * Handed to the runner with the launch (GameSession.netplay). [emptySaves]: a save copy was due but did not happen (this
 * device has no usable rsync), so this side plays on an empty isolated copy, as the other side does.
 * [overUsb]: the session runs over the USB cable; the game screen stops the cable when it releases the session.
 * [mixed]: a GBA link whose caps named gpSP on one side and mGBA on the other (the wizard outcome's `mixed`); [peerWl]:
 * the other side's game link mode from its caps (null: it sent none, a build without the Wi-Fi modes).
 * [cableLink]: a Cable Link over Wi-Fi session (each phone runs both GBAs); the rest is what its handshake settled
 * ([com.nxredux.mobile.netplay.wizard.CableLinkSetup]): [otherRomPath] the other player's ROM as a filesystem path,
 * [otherSavePath] its save in a session dir (null: none), [localSlot] 1 on the host and 2 on the guest, [biosMode]
 * `real` or `hle`, [rtcEpoch] the host's clock in epoch seconds. Unused outside Cable Link.
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
    val cableLink: Boolean = false,
    val otherRomPath: String = "",
    val otherSavePath: String? = null,
    val localSlot: Int = if (role == NetplayRole.HOST) 1 else 2,
    val biosMode: String = CableLinkSetup.BIOS_HLE,
    val rtcEpoch: Long = 0L,
) {
    /** This session with what a Cable Link handshake settled. */
    fun withCableLink(setup: CableLinkSetup): NetplaySession = copy(
        cableLink = true,
        otherRomPath = setup.otherRomPath,
        otherSavePath = setup.otherSavePath,
        localSlot = setup.localSlot,
        biosMode = setup.biosMode,
        rtcEpoch = setup.rtcEpoch,
    )

    /**
     * How this session's link talks; null for a lockstep session. Cable Link runs on the lockstep engine (both GBAs on each
     * phone, only inputs on the wire), although its core is mGBA.
     */
    val linkKind: NetplayLinkKind? get() = if (cableLink) null else NetplayCores.link(coreId)

    /**
     * A link session (GB/GBC/GBA) never plays on a copy: each player keeps their own save, which a trade needs. Nor does
     * Cable Link: the player's own GBA boots from their real save, which only verified checkpoints write
     * ([savesAtCheckpoints]).
     */
    val onCopy: Boolean get() = !cableLink && !NetplayCores.isLink(coreId) && (emptySaves || NetplaySaves.playsOnCopy(role, coreId, folderCoreId))

    /**
     * Cable Link: the real save is written only from the last checkpoint both phones agreed on, at the session's end; the
     * game's own battery-save writes are off for the whole launch.
     */
    val savesAtCheckpoints: Boolean get() = cableLink

    /**
     * Once the session ends, the game is a normal local one: a link session's (each player runs their own game on their
     * own save, nothing is synced from the host). A lockstep session's game is the host's synced state, and a Cable Link
     * session's holds the other player's GBA: it stays a netplay game (Continue / Quit, no states, options or cheats).
     */
    val endsAsLocalGame: Boolean get() = linkKind != null

    /**
     * No layout, swap or other core option changes once the session runs: the other device would not follow. A 3DS, DS or PSP
     * link's game is each player's own, so its picture stays theirs to arrange, as in a normal launch.
     */
    val locksOptions: Boolean get() = linkKind != NetplayLinkKind.N3DSLINK && linkKind != NetplayLinkKind.NDSLINK && linkKind != NetplayLinkKind.PSPLINK

    /**
     * RetroAchievements run as in a normal launch: a link session's (each player's game is their own, on their own save,
     * so an unlock is theirs), and Cable Link's (the core shows and maps only this player's GBA). A lockstep session has
     * none: the client plays the host's synced state.
     */
    val allowsAchievements: Boolean get() = endsAsLocalGame || cableLink
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
