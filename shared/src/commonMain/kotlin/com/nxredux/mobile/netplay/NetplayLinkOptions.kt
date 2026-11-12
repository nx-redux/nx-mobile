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

    /** Azahar's room link: host, join or off; the core reads these at load, and the mode again on a variable update. */
    const val N3DS_MODE = "nx_3ds_link"
    const val N3DS_IP = "nx_3ds_link_ip"
    const val N3DS_PORT_KEY = "nx_3ds_link_port"
    const val N3DS_NAME = "nx_3ds_link_name"
    /** The Wi-Fi network's broadcast address, for the room search; left out when it is unknown. */
    const val N3DS_BROADCAST = "nx_3ds_link_broadcast"
    const val N3DS_HOST = "host"
    const val N3DS_JOIN = "join"
    const val N3DS_AUTO = "auto"
    /** Leave in a 3DS link session: the core leaves the room and the game plays on. */
    const val N3DS_OFF = "off"
    /** Azahar's default room port. */
    const val N3DS_PORT = 24872

    /** melonDS DS's room link: the same variables as [N3DS_MODE]'s, on its own port; the core reads them at load, and the mode again on a variable update. */
    const val NDS_MODE = "nx_nds_link"
    const val NDS_IP = "nx_nds_link_ip"
    const val NDS_PORT_KEY = "nx_nds_link_port"
    const val NDS_NAME = "nx_nds_link_name"
    const val NDS_BROADCAST = "nx_nds_link_broadcast"
    const val NDS_PORT = 24874
    /** The core option for the console's MAC; unset, the firmware's own MAC is kept. */
    const val NDS_MAC_MODE = "melonds_mac_address_mode"

    /** This install's DS MAC from [seed]: Nintendo's prefix and the seed's low three bytes, so two phones never share one. */
    fun ndsMac(seed: Int): String = "00:08:BF:%02X:%02X:%02X".format((seed shr 16) and 0xFF, (seed shr 8) and 0xFF, seed and 0xFF)

    /** The PSP ad hoc link's variables, read by the runner and the PPSSPP core ([pspLink]). */
    const val PSP_MODE = "nx_psp_link"
    const val PSP_IP = "nx_psp_link_ip"
    const val PSP_BROADCAST = "nx_psp_link_broadcast"
    const val PSP_SELF = "nx_psp_link_self"

    /**
     * This install's PSP MAC from [seed], 12 lowercase hex digits: the locally administered prefix 02 (PPSSPP's MAC match
     * ignores the first octet, so the rest carries the seed), a zero octet, then the seed as eight hex digits. The seed is
     * the DS one, drawn below 2^24, so those are a zero octet and its three bytes, and two phones never share one.
     */
    fun pspMac(seed: Int): String = "0200" + (7 downTo 0).map { "0123456789abcdef"[(seed shr (it * 4)) and 0xF] }.joinToString("")

    /**
     * The PPSSPP network options a linked game runs under, forced by the app: WLAN on, no built-in server (the runner's
     * ad hoc server answers), no UPnP, the port offset the link's ports sit behind, and [mac] (12 hex digits) as the console's MAC.
     */
    fun pspNetwork(mac: String): Map<String, String> = buildMap {
        put("ppsspp_enable_wlan", "enabled")
        put("ppsspp_enable_builtin_pro_ad_hoc_server", "disabled")
        put("ppsspp_enable_upnp", "disabled")
        put("ppsspp_port_offset", "10000")
        mac.forEachIndexed { i, c -> put("ppsspp_change_mac_address" + (i + 1).toString().padStart(2, '0'), c.toString()) }
    }

    /**
     * The PSP link's launch variables: [mode] ("auto", "host" or "join"), the host's address for a join ([hostIp]), the
     * Wi-Fi network's [broadcast] address and this device's own address ([self]; always sent together with the broadcast).
     */
    fun pspLink(mode: String, hostIp: String?, broadcast: String?, self: String?): Map<String, String> = buildMap {
        put(PSP_MODE, mode)
        hostIp?.let { put(PSP_IP, it) }
        broadcast?.let { put(PSP_BROADCAST, it) }
        self?.let { put(PSP_SELF, it) }
    }

    /**
     * The options a PSP launch sends for its ad hoc network. Linked, in a sheet session ([sessionOptions], [forSession]'s,
     * whatever [localWireless] says) or outside one with Local Wireless on and both Wi-Fi addresses known (the runner
     * needs them to find the others and to tell its own traffic apart): WLAN on ([pspNetwork] with [macSeed]'s
     * [pspMac]), the link's variables, and [broadcast] and [self] when both are known. Otherwise only WLAN off: WLAN on
     * without the link variables would never find the others.
     */
    fun forPsp(sessionOptions: Map<String, String>?, localWireless: Boolean, self: String?, broadcast: String?, macSeed: Int): Map<String, String> {
        val addresses = if (self != null && broadcast != null) mapOf(PSP_BROADCAST to broadcast, PSP_SELF to self) else emptyMap()
        val link = when {
            sessionOptions != null -> sessionOptions + addresses
            localWireless && addresses.isNotEmpty() -> pspLink(N3DS_AUTO, null, broadcast, self)
            else -> return mapOf("ppsspp_enable_wlan" to "disabled")
        }
        return pspNetwork(pspMac(macSeed)) + link
    }

    /**
     * This player's name in Azahar's room: the [player] name in the characters a room takes (ASCII letters, digits,
     * space, dot, underscore, hyphen), then [number] as four digits. The room turns away a second member with the same
     * name, and two devices can carry the same one, hence the number (drawn per launch).
     */
    fun n3dsRoomName(player: String, number: Int): String {
        val base = player.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in " ._-" }.trim().ifEmpty { "Player" }
        return "$base-${(number.mod(10000)).toString().padStart(4, '0')}"
    }

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
     * the ROM for it on every load. Azahar and melonDS DS get their automatic local wireless under [roomName] (null:
     * none), searching for rooms on [broadcast] too; melonDS DS also runs under [ndsMac] when given. Never saved, like
     * [forSession]'s.
     */
    fun forSolo(coreId: String, roomName: String? = null, broadcast: String? = null, ndsMac: String? = null): Map<String, String> = when (coreId) {
        "mgba" -> mapOf(MGBA_LINK to MGBA_DEFAULT)
        // 3DS local wireless with no session: Azahar finds or opens a room itself once the game turns its wireless on.
        "azahar" -> if (roomName == null) emptyMap() else buildMap {
            put(N3DS_MODE, N3DS_AUTO)
            put(N3DS_NAME, roomName)
            put(N3DS_PORT_KEY, N3DS_PORT.toString())
            broadcast?.let { put(N3DS_BROADCAST, it) }
        }
        "melondsds" -> if (roomName == null) emptyMap() else buildMap {
            put(NDS_MODE, N3DS_AUTO)
            put(NDS_NAME, roomName)
            put(NDS_PORT_KEY, NDS_PORT.toString())
            broadcast?.let { put(NDS_BROADCAST, it) }
            ndsMac?.let { put(NDS_MAC_MODE, it) }
        }
        else -> emptyMap()
    }

    /** gbalink_link_mode_key: the core option that holds [coreId]'s link mode; null for a core without one. */
    fun modeKey(coreId: String): String? = when (coreId) {
        "gpsp" -> GPSP_SERIAL
        "mgba" -> MGBA_LINK
        else -> null
    }

    private fun defaultMode(coreId: String): String = if (coreId == "mgba") MGBA_DEFAULT else GPSP_DEFAULT

    const val PAIR = "mgba_pair"
    const val PAIR_LOCAL = "mgba_pair_local"
    const val PAIR_OTHER_ROM = "mgba_pair_other_rom"
    const val PAIR_OTHER_SAVE = "mgba_pair_other_save"
    const val PAIR_RTC = "mgba_pair_rtc"
    const val PAIR_BIOS = "mgba_pair_bios"

    /**
     * A Cable Link session's mGBA variables: pair mode, which GBA is this player's, the other GBA's ROM and save (an empty
     * path for none), the session clock and the agreed BIOS. They are not declared core options: the core reads them once
     * at load. The real cable stays the link mode, so the core never scans the ROM for a Wi-Fi mode. Empty outside Cable Link.
     */
    fun forPair(session: NetplaySession): Map<String, String> = if (!session.cableLink) emptyMap() else mapOf(
        PAIR to "ON",
        PAIR_LOCAL to session.localSlot.toString(),
        PAIR_OTHER_ROM to session.otherRomPath,
        PAIR_OTHER_SAVE to session.otherSavePath.orEmpty(),
        PAIR_RTC to session.rtcEpoch.toString(),
        PAIR_BIOS to session.biosMode,
        MGBA_LINK to MGBA_DEFAULT,
    )

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

    /**
     * The options [session]'s core is launched with; [linkMode]: the agreed raw mode for gpSP or mGBA (null: the default);
     * [roomName]: this player's name in a 3DS or DS link's room ([n3dsRoomName]; null: the role's); [ndsMac]: a DS link's MAC
     * ([ndsMac]; null: the firmware's).
     */
    fun forSession(session: NetplaySession, linkMode: String?, roomName: String? = null, ndsMac: String? = null): Map<String, String> = when (session.linkKind) {
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
        NetplayLinkKind.N3DSLINK -> buildMap {
            put(N3DS_PORT_KEY, N3DS_PORT.toString())
            if (session.role == NetplayRole.HOST) {
                put(N3DS_MODE, N3DS_HOST)
                put(N3DS_NAME, roomName ?: "Player 1")
            } else {
                put(N3DS_MODE, N3DS_JOIN)
                put(N3DS_NAME, roomName ?: "Player 2")
                put(N3DS_IP, session.peerIp)
            }
        }
        NetplayLinkKind.NDSLINK -> buildMap {
            put(NDS_PORT_KEY, NDS_PORT.toString())
            if (session.role == NetplayRole.HOST) {
                put(NDS_MODE, N3DS_HOST)
                put(NDS_NAME, roomName ?: "Player 1")
            } else {
                put(NDS_MODE, N3DS_JOIN)
                put(NDS_NAME, roomName ?: "Player 2")
                put(NDS_IP, session.peerIp)
            }
            ndsMac?.let { put(NDS_MAC_MODE, it) }
        }
        NetplayLinkKind.PSPLINK ->
            if (session.role == NetplayRole.HOST) pspLink(N3DS_HOST, null, null, null) else pspLink(N3DS_JOIN, session.peerIp, null, null)
        null -> emptyMap()
    }
}
