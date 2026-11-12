package com.nxredux.mobile.netplay

import com.nxredux.mobile.runner.CoreRegistry

/**
 * Which tags have netplay and with which cores. The first core of a tag is the handheld pak's (nx-redux main), this
 * device's netplay default, which is also the tag's single-player core here (MD, SMS, GG, SG1000 and SEGACD: picodrive;
 * GPGX: genesis_plus_gx). GB, GBC, GBA, MGBA and 3DS are link sessions: no input lockstep, each player on their own save.
 * GBA and MGBA run either core (gpSP and mGBA keep the same raw flash save, so either reads the other folder's); mGBA
 * leaves the USB cable only for a game with a Wi-Fi link mode ([wifiOk]). Core versions are never compared (not on the
 * wire). The `wl` arguments are the games' link mode tokens ([GbaWifiLink]); null when unknown or not sent.
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
        "3DS" to listOf("azahar"),   // 3DS link: local wireless through Azahar's own room (UDP 24872), phone to phone
        "NDS" to listOf("melondsds"),   // DS link: local wireless through melonDS DS's own room (UDP 24874), phone to phone
    )

    fun supported(tag: String): Boolean = CoreRegistry.handheldTag(tag) in table

    /** Cable Link over Wi-Fi: every GBA and MGBA folder game, whichever core the folder runs. */
    fun cableLinkOffered(tag: String): Boolean = CoreRegistry.handheldTag(tag.trim().uppercase()) in CABLE_LINK_TAGS

    /** A Cable Link session runs both GBAs on mGBA (the pair core), whatever either side's folder or Emulator row says. */
    const val CABLE_LINK_CORE = "mgba"
    private val CABLE_LINK_TAGS = setOf("GBA", "MGBA")
    /** The longest input delay a Cable Link session can be pinned to (the engine's 1..8; 0 is Auto). */
    const val CABLE_LINK_DELAY_MAX = 8
    /** A fresh install pins Cable Link at one frame: two phones on home Wi-Fi held full speed there with near-zero stalls. */
    const val CABLE_LINK_DELAY_DEFAULT = 1

    /**
     * [tag]'s netplay cores, default first; without the cable ([usb] false) the ones [wifiOk] refuses for this game ([wl])
     * are left out. Every adoption decision below uses this connection-aware list.
     */
    fun offered(tag: String, usb: Boolean = true, wl: String? = null): List<String> =
        table[CoreRegistry.handheldTag(tag)].orEmpty().let { cores -> if (usb) cores else cores.filter { wifiOk(it, wl) } }

    /** The handheld pak's core for [tag]; null for a tag without netplay. */
    fun defaultCore(tag: String): String? = offered(tag).firstOrNull()

    /** Frames each side runs on its own before the state sync: mupen64plus-next takes no state until it has run. */
    fun presyncFrames(core: String): Int = if (core == "mupen64plus_next") 30 else 0

    /** A session on [core] runs OpenGL ES: mupen64plus-next on Vulkan swaps in parallel RSP, which changes the emulation. */
    fun forcesGles(core: String?): Boolean = core == "mupen64plus_next"

    /** The display name (CoreRegistry's), the id for a core this device does not know. */
    fun name(core: String): String = CoreRegistry.forId(core)?.name ?: core

    private val LINK: Map<String, NetplayLinkKind> =
        mapOf(
            "gambatte" to NetplayLinkKind.GBLINK, "gpsp" to NetplayLinkKind.NETPACKET, "mgba" to NetplayLinkKind.NETPACKET,
            "azahar" to NetplayLinkKind.N3DSLINK, "melondsds" to NetplayLinkKind.NDSLINK,
        )

    /** How [core]'s link session talks; null for a lockstep core. */
    fun link(core: String): NetplayLinkKind? = LINK[core]
    fun isLink(core: String): Boolean = core in LINK

    /** An MGBA game without a Wi-Fi link mode links over the real cable alone, so the USB cable alone (the handheld's `--modes usb`). */
    fun usbOnly(tag: String, wl: String?): Boolean = tag.trim().uppercase() == "MGBA" && wl !in GbaWifiLink.WIFI_MODES

    /** gbalink_transport_ok: off the USB cable mGBA links only in the game's Wi-Fi mode, whichever folder runs it. */
    fun wifiOk(core: String, wl: String?): Boolean = core != "mgba" || wl in GbaWifiLink.WIFI_MODES

    /**
     * WizCaps_mgbaNeedsWl: off the USB cable, mGBA on one side ([run] here, [peerCore] there) against a side that sent no
     * `wl=` ([ourWl], [peerWl] null): that side would fall back to the real cable over Wi-Fi, so the pair is refused.
     */
    fun needsPeerWl(run: String, peerCore: String?, ourWl: String?, peerWl: String?, usb: Boolean): Boolean =
        !usb && ((run == "mgba" && peerWl == null) || (peerCore == "mgba" && ourWl == null))

    /**
     * A phone joiner on Auto (`auto=1`) from folder [joinerTag] with game mode [joinerWl] switches to the host's [run] when
     * its folder offers it over this connection, exactly as [joinerRun] decides on that side.
     */
    fun joinerSwitches(run: String, joinerTag: String?, joinerAuto: Boolean, usb: Boolean, joinerWl: String?): Boolean =
        joinerAuto && joinerTag != null && run in offered(joinerTag, usb, joinerWl)

    /** gpSP and mGBA on two sides link when both games run the same Wi-Fi mode ([GbaWifiLink.mixedOk]); never two other cores. */
    private fun mixes(a: String, b: String, ourWl: String?, peerWl: String?, usb: Boolean): Boolean =
        link(a) == NetplayLinkKind.NETPACKET && link(b) == NetplayLinkKind.NETPACKET && GbaWifiLink.mixedOk(ourWl, peerWl, usb)

    /**
     * Joining: the host's HELLO named [hostCore] (null: it named none). This device runs [own], or the host's core when
     * the Emulator row is Auto ([pick] null) and [tag] offers it over this connection ([usb]); otherwise [own] against the
     * host's other core when the two games' modes allow a mixed link ([ourWl], [peerWl]); null = refuse (show [refusal],
     * send nothing).
     */
    fun joinerRun(
        tag: String, own: String, pick: String?, hostCore: String?, usb: Boolean, ourWl: String? = null, peerWl: String? = null,
    ): String? = when {
        hostCore == null || hostCore == own -> own
        pick == null && hostCore in offered(tag, usb, ourWl) -> hostCore
        mixes(own, hostCore, ourWl, peerWl, usb) -> own
        else -> null
    }

    /**
     * Joining: the host sent `REJECT core-<hostCore>`. The core to reconnect with once (Auto, offered for this game
     * [ourWl], first refusal), or null.
     */
    fun joinerRetry(tag: String, pick: String?, hostCore: String, mayRetry: Boolean, usb: Boolean, ourWl: String? = null): String? =
        hostCore.takeIf { mayRetry && pick == null && it in offered(tag, usb, ourWl) }

    /**
     * Hosting: the joiner named [joinerCore] (null: none) from folder [joinerTag], [joinerAuto] when its Emulator row is
     * Auto (`auto=1`, a phone). The core to run; null = REJECT core. Phone and phone follow the host: on Auto both ways the
     * host keeps [own] when the joiner's folder offers it (the joiner switches on this HELLO). Otherwise, on Auto, the host
     * adopts the joiner's core (a handheld cannot switch). Failing both, [own] links with the joiner's other core when
     * the two games' modes allow it ([ourWl], [peerWl]).
     */
    fun hostRun(
        tag: String, own: String, pick: String?, joinerCore: String?, joinerTag: String?, joinerAuto: Boolean, usb: Boolean,
        ourWl: String? = null, peerWl: String? = null,
    ): String? = when {
        joinerCore == null || joinerCore == own -> own
        pick == null && joinerSwitches(own, joinerTag, joinerAuto, usb, peerWl) -> own
        pick == null && joinerCore in offered(tag, usb, ourWl) -> joinerCore
        mixes(own, joinerCore, ourWl, peerWl, usb) -> own
        else -> null
    }

    /** The joiner's message for a host on [hostCore] in folder [hostTag]: "Set Emulator" when this device pins another offered core. */
    fun refusal(tag: String, pick: String?, hostCore: String, hostTag: String?, usb: Boolean, wl: String? = null): String {
        val name = name(hostCore)
        return if (pick != null && pick != hostCore && hostCore in offered(tag, usb, wl)) NetplayText.setEmulator(name)
        else NetplayText.coreMismatch(name, hostTag)
    }

}

/**
 * A link session's backend: gambatte runs its own socket; gpSP and mGBA use the runner's GBA link; Azahar and melonDS DS host
 * or join their own multiplayer rooms, which carry the games' local wireless.
 */
enum class NetplayLinkKind { GBLINK, NETPACKET, N3DSLINK, NDSLINK }
