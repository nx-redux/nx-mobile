package com.nxredux.mobile.sync

/** A peer's discovery flag (nx-redux `sync_proto.c`): `0`, `R` (ROMs on), `c` (client-only), `C` (both). */
data class SyncFlags(val romsOn: Boolean, val clientOnly: Boolean)

/** A Device Sync datagram. [Hello.nonce] / [Ack.nonce]: the peer's `v3` nonce; null for a peer this phone can't pair with. */
sealed interface SyncMessage {
    data class Hello(val ip: String, val flags: SyncFlags, val nonce: String?) : SyncMessage
    data class Ack(val ip: String, val flags: SyncFlags, val nonce: String?) : SyncMessage
    /** The peer's user confirmed the pairing code. */
    data object Ok : SyncMessage
    data object Ready : SyncMessage
    data object Done : SyncMessage
    /** The peer's run failed or was stopped after pairing: DONE's counterpart, as a failure. */
    data object Fail : SyncMessage
}

/**
 * The handheld's Device Sync wire (nx-redux `workspace/all/sync/sync.c` and `sync_proto.c`):
 * `HELLO_TRIMUI_SYNC:<ip>:<flag>` broadcast, `TRIMUI_SYNC_ACK:<ip>:<flag>` unicast, each with a NUL and `v3:<nonce>`
 * ([SyncPairing]); OK once the user confirmed the pairing code, then READY / DONE (or FAIL, from either side once
 * paired, when its run fails or is stopped). A peer without a valid `v3` trailer is refused at pairing. The phone sends
 * `c`, or `C` with ROMs on.
 */
object SyncWire {
    const val UDP_PORT = 19999
    const val RSYNC_PORT = 18730
    const val HELLO = "HELLO_TRIMUI_SYNC:"
    const val ACK = "TRIMUI_SYNC_ACK:"
    const val READY = "TRIMUI_SYNC_READY"
    const val DONE = "TRIMUI_SYNC_DONE"
    const val FAIL = "TRIMUI_SYNC_FAIL"
    const val OK = "TRIMUI_SYNC_OK"
    private const val TRAILER = "v3:"

    /** The phone's flag: `C` with ROMs on, `c` without; client-only either way. */
    fun flag(roms: Boolean): Char = if (roms) 'C' else 'c'
    /** The discovery HELLO for [ownIp] (a dotted IPv4 address) with the phone's flag and the run's [nonce]; else IllegalArgumentException. */
    fun hello(ownIp: String, roms: Boolean = false, nonce: String): String = "$HELLO${checked(ownIp)}:${flag(roms)}${trailer(nonce)}"
    /** The answer to a peer's HELLO, from [ownIp] (a dotted IPv4 address) with the flag and the run's [nonce]; else IllegalArgumentException. */
    fun ack(ownIp: String, roms: Boolean = false, nonce: String): String = "$ACK${checked(ownIp)}:${flag(roms)}${trailer(nonce)}"

    private fun trailer(nonce: String) = "\u0000$TRAILER${requireNotNull(SyncPairing.checkedNonce(nonce)) { "not a nonce: $nonce" }}"

    /** [text] as a message, or null for anything else (a bad IP, an unknown flag, another app's packet). */
    fun parse(text: String): SyncMessage? {
        val head = text.substringBefore('\u0000')
        val trailer = if ('\u0000' in text) text.substringAfter('\u0000') else null
        if (head == READY) return SyncMessage.Ready
        if (head == DONE) return SyncMessage.Done
        if (head == FAIL) return SyncMessage.Fail
        if (head == OK) return SyncMessage.Ok
        val hello = head.startsWith(HELLO)
        if (!hello && !head.startsWith(ACK)) return null
        val payload = head.removePrefix(if (hello) HELLO else ACK)
        val ip = ipv4(payload.substringBefore(':')) ?: return null
        val flag = if (':' in payload) payload.substringAfter(':') else "0"
        val flags = when (flag) {
            "0" -> SyncFlags(romsOn = false, clientOnly = false)
            "R" -> SyncFlags(romsOn = true, clientOnly = false)
            "c" -> SyncFlags(romsOn = false, clientOnly = true)
            "C" -> SyncFlags(romsOn = true, clientOnly = true)
            else -> return null
        }
        val nonce = trailer?.takeIf { it.startsWith(TRAILER) }?.let { SyncPairing.checkedNonce(it.removePrefix(TRAILER)) }
        return if (hello) SyncMessage.Hello(ip, flags, nonce) else SyncMessage.Ack(ip, flags, nonce)
    }

    /** [text] when it is a canonical dotted IPv4 address (four 0..255 parts, no leading zeros), else null. */
    fun ipv4(text: String?): String? {
        val parts = text?.split('.') ?: return null
        if (parts.size != 4) return null
        val ok = parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all { it in '0'..'9' } && (p == "0" || p[0] != '0') && p.toInt() <= 255 }
        return if (ok) text else null
    }

    /** [ip] as an unsigned 32-bit number, for the handheld's "lower IP serves" rule. */
    fun ipValue(ip: String): Long = checked(ip).split('.').fold(0L) { acc, p -> acc * 256 + p.toLong() }

    private fun checked(ip: String): String = requireNotNull(ipv4(ip)) { "not a dotted IPv4 address: $ip" }
}

/** Who runs the rsync daemon (nx-redux `sync_is_server`). The phone is always client-only, so it serves only another phone. */
object SyncRoles {
    /** Exactly one client-only side: the other serves. Otherwise the lower IP serves. */
    fun isServer(ownClientOnly: Boolean, peerClientOnly: Boolean, ownIp: String, peerIp: String): Boolean = when {
        ownClientOnly && !peerClientOnly -> false
        !ownClientOnly && peerClientOnly -> true
        else -> SyncWire.ipValue(ownIp) < SyncWire.ipValue(peerIp)
    }
}
