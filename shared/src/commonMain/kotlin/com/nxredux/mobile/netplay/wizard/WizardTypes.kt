package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.cablelink.LinkRomCache

/**
 * What this device hosts: its game token, folder [tag], the core it runs ([pick] or the tag's default) and the Emulator
 * pick (null = Auto); [usb]: over the USB cable (the sheet's Connection), which cable-only cores need; [wl]: the GBA
 * game's link mode token ([com.nxredux.mobile.netplay.GbaWifiLink]), null for other games; [cableLink]: this side picked
 * Cable Link over Wi-Fi (null for every other connection).
 */
data class HostOffer(
    val game: String, val tag: String, val core: String, val pick: String?, val usb: Boolean, val wl: String? = null,
    val cableLink: CableLinkOffer? = null,
)

/**
 * What this device joins with; [usb]: over the USB cable; [mayRetry]: a `REJECT core-<c>` may still be answered by one
 * reconnect with `c`; [wl]: as [HostOffer.wl].
 */
data class ClientOffer(
    val game: String, val any: Boolean, val tag: String, val core: String, val pick: String?, val usb: Boolean,
    val mayRetry: Boolean = true, val wl: String? = null, val cableLink: CableLinkOffer? = null,
)

/** How a host handshake ended. */
sealed interface HostOutcome {
    /**
     * Both sides start [core]; [peer] is the joiner's address; [emptySaves]: saves were due but not served (no rsync).
     * [mixed]: the two sides' caps named different GBA link cores and the games' modes allow a mixed link (each side then
     * runs the game's own mode); [peerWl]: the joiner's `wl=` (null: none sent).
     */
    data class Started(
        val core: String, val peer: String, val emptySaves: Boolean, val mixed: Boolean = false, val peerWl: String? = null,
        val cableLink: CableLinkSetup? = null,
    ) : HostOutcome

    /**
     * The joiner was turned away. [reason]: the REJECT reason sent (`version`, `game`, `role`, `core`, `link`), or `sync` for any
     * failed save copy (REJECT sync when the saves could not be served, a SYNC-FAIL, or no SYNC-DONE within 30 s);
     * null for an I/O or protocol failure. [joinerCore] is the joiner's core for `core` and `link`; [noWifiModes]: refused
     * because mGBA would run off the cable against a side without the Wi-Fi modes
     * ([com.nxredux.mobile.netplay.NetplayCores.needsPeerWl]). For `link` (both games in Wi-Fi modes that can't link),
     * [ownWl] and [joinerWl] are the two games' modes. `cablelink`: one side offered Cable Link over Wi-Fi and the other
     * did not; a failed Cable Link exchange reads as [Wizard.GAME_COPY_FAILED] up to the ROMs and `sync` from the saves on.
     */
    data class Turned(
        val reason: String?, val joinerCore: String? = null, val noWifiModes: Boolean = false,
        val ownWl: String? = null, val joinerWl: String? = null,
    ) : HostOutcome
}

/** How a client handshake ended. */
sealed interface ClientOutcome {
    /**
     * The host starts [core]; this device plays as [player]. [emptySaves] is always false from [Wizard.client]: a client
     * never declines offered saves (without rsync it sends SYNC-FAIL), and when the host skips SYNC-READY this device
     * cannot tell whether it had saves; its empty session copy matches the host either way. [mixed] and [peerWl]: as
     * [HostOutcome.Started], for the host.
     */
    data class Started(
        val core: String, val player: Int, val emptySaves: Boolean, val mixed: Boolean = false, val peerWl: String? = null,
        val cableLink: CableLinkSetup? = null,
    ) : ClientOutcome

    /** The join failed; [message] is the user-facing text. */
    data class Refused(val message: String) : ClientOutcome

    /** The host refused this core and runs [core], which this device offers on Auto: connect again once with it. */
    data class Retry(val core: String) : ClientOutcome
}

/**
 * The joiner's side of the save copy (platform: rsync pulls into a staging dir). [begin] starts a fresh staging area,
 * [pull] copies one offered file, [commit] makes the set the play copy, [abort] discards it. A begun sync always ends
 * with exactly one of [commit] or [abort]. [canPull] false: this device has no usable rsync and fails an offer (SYNC-FAIL).
 */
interface SaveSink {
    val canPull: Boolean
    fun begin()
    suspend fun pull(hostIp: String, wireName: String)
    fun commit()
    fun abort()
}

/**
 * The host's saves for one joiner, staged under their wire names ([files], sorted; empty when the game has none).
 * [canServe] false: no usable rsync, so SYNC-READY is skipped. [start] runs the daemon for [joinerIp] only (false when it
 * did not come up); [close] stops it and drops the staging (idempotent, always called).
 */
interface HostSaves {
    val files: List<String>
    val canServe: Boolean
    suspend fun start(joinerIp: String): Boolean
    fun close()
}

/**
 * This side's Cable Link over Wi-Fi offer: its game's ROM ([romHash], SHA-1 of the ROM bytes, and [romSize]) and its GBA
 * BIOS hash ([biosHash], null when it has none). [clock] is the wall clock in epoch seconds; the host's goes to the
 * guest so both phones run one RTC.
 */
class CableLinkOffer(
    val romHash: String,
    val romSize: Long,
    val biosHash: String?,
    val files: CableLinkFiles,
    val clock: () -> Long,
)

/** The platform's files for a Cable Link exchange. */
interface CableLinkFiles {
    /** Where a ROM from the other side lands, verified against its hash. */
    val cache: LinkRomCache

    /**
     * A filesystem path a core can open for the ROM [hash] of [sizeBytes] already on this device (the library or [cache]),
     * or null when there is none. A library ROM reachable only as a document ref is copied into [cache] first
     * ([LinkRomCache.put] verifies it), so the answer is never a content URI.
     */
    suspend fun findRom(hash: String, sizeBytes: Long): String?

    /** Streams this side's own ROM to [chunk] in order (`chunk(buffer, length)`). */
    suspend fun readOwnRom(chunk: suspend (ByteArray, Int) -> Unit)

    /** This side's battery save as the core would load it; null when the game has none yet. */
    suspend fun ownSave(): ByteArray?

    /** Writes the other side's save as `other.sav` in a fresh session dir (never the user's saves); returns its path. */
    suspend fun writeOtherSave(bytes: ByteArray): String

    /** A diagnostic line (NxLog on a device: only with Settings → System → Debug log on). */
    fun log(message: String) {}
}

/** A ROM transfer under way: [sending] (else receiving) [done] of [total] bytes. */
data class CableLinkProgress(val sending: Boolean, val done: Long, val total: Long)

/**
 * What a Cable Link handshake settled, for the launch. [otherRomPath]: the other player's ROM, a filesystem path (the
 * library's file, or the link ROM cache); [otherSavePath]: the other player's save in a session dir, null when it has
 * none; [localSlot]: 1 on the host, 2 on the guest; [biosMode]: [BIOS_REAL] only when both sides sent the same BIOS
 * hash, else [BIOS_HLE]; [rtcEpoch]: the host's clock in epoch seconds, the same on both phones.
 */
data class CableLinkSetup(
    val otherRomPath: String,
    val otherSavePath: String?,
    val localSlot: Int,
    val biosMode: String,
    val rtcEpoch: Long,
) {
    companion object {
        const val BIOS_REAL = "real"
        const val BIOS_HLE = "hle"
    }
}
