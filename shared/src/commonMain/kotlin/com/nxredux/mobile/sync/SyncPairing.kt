package com.nxredux.mobile.sync

/**
 * Device Sync pairing (shared with nx-redux `workspace/all/sync`, byte for byte): both sides derive the same 4-digit
 * code to show and the same rsync password from the two IPs and the two nonces of the run's HELLO/ACK. Only a device
 * that saw both nonces can know the password, and the user checks on both screens that the codes match.
 */
object SyncPairing {
    /** The rsync daemon user every client URL names (`rsync://nxsync@ip:port/…`). */
    const val USER = "nxsync"
    /** The nonce's random bytes: 16 hex characters on the wire. */
    const val NONCE_BYTES = 8

    data class Secret(val code: String, val password: String)

    /** The code and password of a run between `ownIp:ownNonce` and `peerIp:peerNonce`; either side's order gives the same. */
    fun derive(ownIp: String, ownNonce: String, peerIp: String, peerNonce: String): Secret {
        val own = "$ownIp:$ownNonce"
        val peer = "$peerIp:$peerNonce"
        // Plain code-unit order, which is byte order here: IPs and lowercase hex are ASCII.
        val (lo, hi) = if (own < peer) own to peer else peer to own
        val s = "nxsync3|$lo|$hi"
        val code = Sha256.digest("$s|code".encodeToByteArray()).let { d ->
            ((d[0].toLong() and 0xff shl 24) or (d[1].toLong() and 0xff shl 16) or (d[2].toLong() and 0xff shl 8) or (d[3].toLong() and 0xff)) % 10_000
        }
        val password = hex(Sha256.digest("$s|pass".encodeToByteArray()).copyOf(16))
        return Secret(code.toString().padStart(4, '0'), password)
    }

    /** [random] ([NONCE_BYTES] bytes from a CSPRNG) as the wire's nonce. */
    fun nonce(random: ByteArray): String {
        require(random.size == NONCE_BYTES) { "a nonce takes $NONCE_BYTES bytes" }
        return hex(random)
    }

    /** [text] when it is a wire nonce (16 lowercase hex characters), else null. */
    fun checkedNonce(text: String): String? = text.takeIf { t -> t.length == NONCE_BYTES * 2 && t.all { it in '0'..'9' || it in 'a'..'f' } }

    /** The daemon's `rsyncd.secrets` text for [password] (a derived one: 32 lowercase hex characters). */
    fun secrets(password: String): String {
        require(password.length == 32 && password.all { it in '0'..'9' || it in 'a'..'f' }) { "not a pairing password" }
        return "$USER:$password\n"
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
