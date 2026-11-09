package com.nxredux.mobile.netplay

import com.nxredux.mobile.runner.CoreRegistry

/**
 * Which tags have netplay and with which cores. The first core of a tag is the handheld pak's (nx-redux main), this
 * device's netplay default, which is also the tag's single-player core here (MD, SMS, GG, SG1000 and SEGACD: picodrive;
 * GPGX: genesis_plus_gx). GB, GBC, GBA and MGBA are link sessions: no input lockstep, each player on their own save.
 * GBA and MGBA run either core (gpSP and mGBA keep the same raw flash save, so either reads the other folder's); mGBA is
 * cable-only ([cableOnly]). Core versions are never compared (not on the wire).
 */
object NetplayCores {
    private val PICODRIVE_FIRST = listOf("picodrive", "genesis_plus_gx")
    private val table: Map<String, List<String>> = mapOf(
        "FC" to listOf("fceumm"), "FDS" to listOf("fceumm"),
        "SFC" to listOf("snes9x", "mednafen_supafaust"), "SUPA" to listOf("mednafen_supafaust", "snes9x"),
        "MD" to PICODRIVE_FIRST, "SMS" to PICODRIVE_FIRST, "GG" to PICODRIVE_FIRST, "SG1000" to PICODRIVE_FIRST, "SEGACD" to PICODRIVE_FIRST,
        "GPGX" to listOf("genesis_plus_gx", "picodrive"),   // also GPGX_SMS, GPGX_GG, GPGX_SG1000, GPGX_SEGACD (CoreRegistry.handheldTag)
        "32X" to listOf("picodrive"),
        "PS" to listOf("pcsx_rearmed", "swanstation"), "PSX" to listOf("swanstation", "pcsx_rearmed"),
        "FBN" to listOf("fbneo"),
        "N64" to listOf("mupen64plus_next"),   // two players, the session's emulation options forced (NetplayPads)
        "GB" to listOf("gambatte"), "GBC" to listOf("gambatte"),   // GB link: gambatte's own network serial (TCP 56400)
        // GBA link: the netpacket interface over TCP 55437. Both folders run either core (the same raw flash save).
        "GBA" to listOf("gpsp", "mgba"), "MGBA" to listOf("mgba", "gpsp"),
    )

    fun supported(tag: String): Boolean = CoreRegistry.handheldTag(tag) in table

    /**
     * [tag]'s netplay cores, default first; without the cable ([usb] false) the [cableOnly] ones are left out. Every
     * adoption decision below uses this connection-aware list.
     */
    fun offered(tag: String, usb: Boolean = true): List<String> =
        table[CoreRegistry.handheldTag(tag)].orEmpty().let { cores -> if (usb) cores else cores.filterNot(::cableOnly) }

    /** The handheld pak's core for [tag]; null for a tag without netplay. */
    fun defaultCore(tag: String): String? = offered(tag).firstOrNull()

    /** Frames each side runs on its own before the state sync: mupen64plus-next takes no state until it has run. */
    fun presyncFrames(core: String): Int = if (core == "mupen64plus_next") 30 else 0

    /** A session on [core] runs OpenGL ES: mupen64plus-next on Vulkan swaps in parallel RSP, which changes the emulation. */
    fun forcesGles(core: String?): Boolean = core == "mupen64plus_next"

    /** The display name (CoreRegistry's), the id for a core this device does not know. */
    fun name(core: String): String = CoreRegistry.forId(core)?.name ?: core

    private val LINK: Map<String, NetplayLinkKind> =
        mapOf("gambatte" to NetplayLinkKind.GBLINK, "gpsp" to NetplayLinkKind.NETPACKET, "mgba" to NetplayLinkKind.NETPACKET)

    /** How [core]'s link session talks; null for a lockstep core. */
    fun link(core: String): NetplayLinkKind? = LINK[core]
    fun isLink(core: String): Boolean = core in LINK

    /** MGBA's link is the real cable only, which runs over the USB cable alone (the handheld's `--modes usb`). */
    fun usbOnly(tag: String): Boolean = tag.trim().uppercase() == "MGBA"

    /** mGBA's link runs over the USB cable alone, whichever folder runs it. */
    fun cableOnly(core: String): Boolean = core == "mgba"

    /**
     * Joining: the host's HELLO named [hostCore] (null: it named none). This device runs [own], or the host's core when
     * the Emulator row is Auto ([pick] null) and [tag] offers it over this connection ([usb]); null = refuse (show
     * [refusal], send nothing).
     */
    fun joinerRun(tag: String, own: String, pick: String?, hostCore: String?, usb: Boolean): String? = when {
        hostCore == null || hostCore == own -> own
        pick == null && hostCore in offered(tag, usb) -> hostCore
        else -> null
    }

    /** Joining: the host sent `REJECT core-<hostCore>`. The core to reconnect with once (Auto, offered, first refusal), or null. */
    fun joinerRetry(tag: String, pick: String?, hostCore: String, mayRetry: Boolean, usb: Boolean): String? =
        hostCore.takeIf { mayRetry && pick == null && it in offered(tag, usb) }

    /**
     * Hosting: the joiner named [joinerCore] (null: none) from folder [joinerTag], [joinerAuto] when its Emulator row is
     * Auto (`auto=1`, a phone). The core to run; null = REJECT core. Phone and phone follow the host: on Auto both ways the
     * host keeps [own] when the joiner's folder offers it (the joiner switches on this HELLO). Otherwise, on Auto, the host
     * adopts the joiner's core (a handheld cannot switch).
     */
    fun hostRun(
        tag: String, own: String, pick: String?, joinerCore: String?, joinerTag: String?, joinerAuto: Boolean, usb: Boolean,
    ): String? = when {
        joinerCore == null || joinerCore == own -> own
        pick == null && joinerAuto && joinerTag != null && own in offered(joinerTag, usb) -> own
        pick == null && joinerCore in offered(tag, usb) -> joinerCore
        else -> null
    }

    /** The joiner's message for a host on [hostCore] in folder [hostTag]: "Set Emulator" when this device pins another offered core. */
    fun refusal(tag: String, pick: String?, hostCore: String, hostTag: String?, usb: Boolean): String {
        val name = name(hostCore)
        return if (pick != null && pick != hostCore && hostCore in offered(tag, usb)) NetplayText.setEmulator(name)
        else NetplayText.coreMismatch(name, hostTag)
    }

}

/** A link session's backend: gambatte runs its own socket; gpSP and mGBA use the runner's GBA link. */
enum class NetplayLinkKind { GBLINK, NETPACKET }
