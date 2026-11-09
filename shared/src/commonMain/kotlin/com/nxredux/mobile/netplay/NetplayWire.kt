package com.nxredux.mobile.netplay

/**
 * NX Redux netplay wire format, byte-compatible with the handheld (nx-redux network_common.c, wizard_net.c at
 * 8f5a760b): ports, discovery packet, name escaping and normalisation. The control lines are in `wizard/`. Pure.
 */
object NetplayWire {
    const val CONTROL_PORT = 55440
    const val DISCOVERY_PORT = 55441
    const val ENGINE_PORT = 55435

    /** The GBA link (nx-redux GBALINK_DEFAULT_PORT); GB link runs on gambatte's own 56400 (NetplayLinkOptions). */
    const val GBALINK_PORT = 55437

    const val MAGIC = 0x4E58575A
    const val VERSION = 2
    const val LINK_MODE = "wizard"
    const val PACKET_SIZE = 110
    const val ESC = '\u001f'
    private const val NAME_FIELD = 64
    private const val MODE_FIELD = 32

    fun escape(s: String): String = s.replace(' ', ESC)
    fun unescape(s: String): String = s.replace(ESC, ' ')

    /** The game token both sides send: the ROM file name minus its last extension (prelaunch's `${name%.*}`). */
    fun gameName(fileName: String): String = fileName.substringBeforeLast('.', fileName)

    /** wiz_normalize_name: drop ()/[] tags (nested), keep ASCII letters/digits lowercased, at most 63 chars. */
    fun normalize(name: String): String {
        val out = StringBuilder()
        var depth = 0
        for (c in name) {
            if (out.length >= 63) break
            when (c) {
                '(', '[' -> depth++
                ')', ']' -> if (depth > 0) depth--
                else -> if (depth == 0 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')) out.append(c.lowercaseChar())
            }
        }
        return out.toString()
    }

    fun equivalent(a: String, b: String): Boolean {
        val na = normalize(a)
        return na.isNotEmpty() && na == normalize(b)
    }

    /** NET_DiscoveryPacket: magic, version, crc 0, port, game_name[64], link_mode[32]; big-endian, NUL-padded. */
    fun encodeDiscovery(game: String, port: Int = CONTROL_PORT, version: Int = VERSION): ByteArray {
        val b = ByteArray(PACKET_SIZE)
        putInt(b, 0, MAGIC); putInt(b, 4, version); putInt(b, 8, 0)
        b[12] = (port shr 8).toByte(); b[13] = port.toByte()
        putString(b, 14, NAME_FIELD, game)
        putString(b, 14 + NAME_FIELD, MODE_FIELD, LINK_MODE)
        return b
    }

    /** null unless exactly 110 bytes with the wizard magic. The caller filters version and link mode. */
    fun decodeDiscovery(bytes: ByteArray, length: Int = bytes.size): Discovery? {
        if (length != PACKET_SIZE || bytes.size < PACKET_SIZE) return null
        if (getInt(bytes, 0) != MAGIC) return null
        val port = ((bytes[12].toInt() and 0xff) shl 8) or (bytes[13].toInt() and 0xff)
        return Discovery(getInt(bytes, 4), port, getString(bytes, 14, NAME_FIELD), getString(bytes, 14 + NAME_FIELD, MODE_FIELD))
    }

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        b[at] = (v ushr 24).toByte(); b[at + 1] = (v ushr 16).toByte(); b[at + 2] = (v ushr 8).toByte(); b[at + 3] = v.toByte()
    }
    private fun getInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
            ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)

    /** At most field-1 UTF-8 bytes, cut back to a character boundary, NUL-padded (always NUL-terminated). */
    private fun putString(b: ByteArray, at: Int, field: Int, s: String) {
        val bytes = s.encodeToByteArray()
        var n = minOf(bytes.size, field - 1)
        while (n > 0 && n < bytes.size && (bytes[n].toInt() and 0xC0) == 0x80) n--
        bytes.copyInto(b, at, 0, n)
    }
    private fun getString(b: ByteArray, at: Int, field: Int): String {
        var end = at
        while (end < at + field && b[end] != 0.toByte()) end++
        return b.copyOfRange(at, end).decodeToString()
    }
}

data class Discovery(val version: Int, val port: Int, val game: String, val linkMode: String)

/** [LINK] is only ever sent as `link-<core>.<wl>.<wl>` ([com.nxredux.mobile.netplay.wizard.Reject]), never bare. */
enum class RejectReason(val wire: String) { VERSION("version"), GAME("game"), ROLE("role"), CORE("core"), SYNC("sync"), LINK("link"), OTHER("") }
