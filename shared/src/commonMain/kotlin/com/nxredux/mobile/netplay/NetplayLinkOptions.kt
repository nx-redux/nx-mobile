package com.nxredux.mobile.netplay

/**
 * The core options a link session gives its core before load, as the handheld sets them: nx-redux
 * `gblink.c` (gambatte's link mode, port and the host's address as 12 digits) and `gbalink_mode.c` (gpSP's
 * `gpsp_serial` and mGBA's `mgba_link_mode` for the transport). Lockstep cores get none.
 */
object NetplayLinkOptions {
    const val GPSP_SERIAL = "gpsp_serial"
    const val GPSP_DEFAULT = "auto"
    const val MGBA_LINK = "mgba_link_mode"
    const val MGBA_DEFAULT = "cable"
    const val GB_MODE = "gambatte_gb_link_mode"
    const val GB_PORT_KEY = "gambatte_gb_link_network_port"
    const val GB_SERVER = "Network Server"
    const val GB_CLIENT = "Network Client"
    /** gambatte re-reads the mode on a variable update: this one stops its network serial (Leave in a GB link session). */
    const val GB_OFF = "Not Connected"
    const val GB_PORT = 56400

    fun gbIpKey(digit: Int) = "gambatte_gb_link_network_server_ip_$digit"

    /**
     * gbalink_serial_option for gpSP: a [mixed] session (the other side runs mGBA) runs the game's own mode whatever is
     * pinned (`auto`, `auto_cable` over USB). Otherwise over USB `auto` is `auto_cable` (real cable unless the game has
     * a wireless adapter); elsewhere `lockstep` is `auto`.
     */
    fun gpspSerial(value: String, overUsb: Boolean, mixed: Boolean): String = when {
        mixed -> if (overUsb) "auto_cable" else "auto"
        overUsb && value == "auto" -> "auto_cable"
        !overUsb && value == "lockstep" -> "auto"
        else -> value
    }

    /**
     * gbalink_serial_option for mGBA in a session ([forSolo] outside one): a peer that sent no `wl=` ([peerHasWl] false:
     * a build without the Wi-Fi modes) speaks only the real cable. A [mixed] session runs the game's own mode, as
     * [gpspSerial]. Otherwise over USB `cable` and `auto` are `auto_cable` (the adapter for a game that has one), and off
     * it `cable` and `auto_cable` are `auto` (the real cable is the lockstep one).
     */
    fun mgbaMode(value: String, overUsb: Boolean, peerHasWl: Boolean, mixed: Boolean): String = when {
        !peerHasWl -> "cable"
        mixed -> if (overUsb) "auto_cable" else "auto"
        overUsb && (value == "auto" || value == "cable") -> "auto_cable"
        !overUsb && (value == "cable" || value == "auto_cable") -> "auto"
        else -> value
    }

    /**
     * gbalink_serial_option outside a session: mGBA's mode matters only in one, and `cable` keeps the core from scanning
     * the ROM for it on every load. Never saved, like [forSession]'s.
     */
    fun forSolo(coreId: String): Map<String, String> = if (coreId == "mgba") mapOf(MGBA_LINK to MGBA_DEFAULT) else emptyMap()

    /** gbalink_link_mode_key: the core option that holds [coreId]'s link mode; null for a core without one. */
    fun modeKey(coreId: String): String? = when (coreId) {
        "gpsp" -> GPSP_SERIAL
        "mgba" -> MGBA_LINK
        else -> null
    }

    private fun defaultMode(coreId: String): String = if (coreId == "mgba") MGBA_DEFAULT else GPSP_DEFAULT

    /** "192.168.1.7" -> "192168001007" (each octet zero-padded to 3); null when [ip] is not dotted IPv4. */
    fun ipDigits(ip: String): String? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { p -> p.takeIf { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) }?.toInt()?.takeIf { it in 0..255 } ?: return null }
        return octets.joinToString("") { it.toString().padStart(3, '0') }
    }

    /** The host's READY mode: the raw [modeKey] value ([own], or the core default); "" for a core without one. */
    fun hostMode(coreId: String, own: String?): String = if (modeKey(coreId) == null) "" else own ?: defaultMode(coreId)

    /**
     * The joiner's mode: the host's from READY ([host]); its own when the host sent none, or in a [mixed] session (the
     * host's value is the other core's option); "" for a core without one.
     */
    fun joinerMode(coreId: String, own: String?, host: String, mixed: Boolean): String = when {
        modeKey(coreId) == null -> ""
        mixed || host.isEmpty() -> own ?: defaultMode(coreId)
        else -> host
    }

    /** The options [session]'s core is launched with; [linkMode]: the agreed raw mode for gpSP or mGBA (null: the default). */
    fun forSession(session: NetplaySession, linkMode: String?): Map<String, String> = when (NetplayCores.link(session.coreId)) {
        NetplayLinkKind.GBLINK -> buildMap {
            put(GB_PORT_KEY, GB_PORT.toString())
            if (session.role == NetplayRole.HOST) {
                put(GB_MODE, GB_SERVER)
            } else {
                put(GB_MODE, GB_CLIENT)
                (ipDigits(session.peerIp) ?: "0".repeat(12)).forEachIndexed { i, d -> put(gbIpKey(i + 1), d.toString()) }
            }
        }
        NetplayLinkKind.NETPACKET -> {
            val mode = linkMode?.ifEmpty { null } ?: defaultMode(session.coreId)
            when (session.coreId) {
                "gpsp" -> mapOf(GPSP_SERIAL to gpspSerial(mode, session.overUsb, session.mixed))
                "mgba" -> mapOf(MGBA_LINK to mgbaMode(mode, session.overUsb, session.peerWl != null, session.mixed))
                else -> emptyMap()
            }
        }
        null -> emptyMap()
    }
}
