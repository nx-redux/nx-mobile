package com.nxredux.mobile.netplay.wizard

/**
 * What this device hosts: its game token, folder [tag], the core it runs ([pick] or the tag's default) and the Emulator
 * pick (null = Auto); [usb]: over the USB cable (the sheet's Connection), which cable-only cores need; [wl]: the GBA
 * game's link mode token ([com.nxredux.mobile.netplay.GbaWifiLink]), null for other games.
 */
data class HostOffer(val game: String, val tag: String, val core: String, val pick: String?, val usb: Boolean, val wl: String? = null)

/**
 * What this device joins with; [usb]: over the USB cable; [mayRetry]: a `REJECT core-<c>` may still be answered by one
 * reconnect with `c`; [wl]: as [HostOffer.wl].
 */
data class ClientOffer(
    val game: String, val any: Boolean, val tag: String, val core: String, val pick: String?, val usb: Boolean,
    val mayRetry: Boolean = true, val wl: String? = null,
)

/** How a host handshake ended. */
sealed interface HostOutcome {
    /**
     * Both sides start [core]; [peer] is the joiner's address; [emptySaves]: saves were due but not served (no rsync).
     * [mixed]: the joiner runs the other GBA link core; [peerWl]: the joiner's `wl=` (null: none sent).
     */
    data class Started(
        val core: String, val peer: String, val emptySaves: Boolean, val mixed: Boolean = false, val peerWl: String? = null,
    ) : HostOutcome

    /**
     * The joiner was turned away. [reason]: the REJECT reason sent (`version`, `game`, `role`, `core`), or `sync` for any
     * failed save copy (REJECT sync when the saves could not be served, a SYNC-FAIL, or no SYNC-DONE within 30 s);
     * null for an I/O or protocol failure. [joinerCore] is the joiner's core for `core`.
     */
    data class Turned(val reason: String?, val joinerCore: String? = null) : HostOutcome
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
