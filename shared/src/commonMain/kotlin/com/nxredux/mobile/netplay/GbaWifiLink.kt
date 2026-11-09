package com.nxredux.mobile.netplay

/**
 * A GBA game's own link mode (gpSP's per-game choice, nx-redux gwl_mode.c), as the caps' `wl=` token: [WIFI_MODES] or
 * [NONE]. With one of the Wi-Fi modes mGBA links off the USB cable, and gpSP and mGBA can link with each other.
 */
object GbaWifiLink {
    const val NONE = "none"
    val WIFI_MODES: Set<String> = setOf("rfu", "poke", "aw1", "aw2")

    /** A probe's answer as a caps token: anything but a known Wi-Fi mode is [NONE]. */
    fun token(probed: String?): String = probed?.takeIf { it in WIFI_MODES } ?: NONE

    /**
     * gwl_mixed_ok: gpSP and mGBA may link when both sides named the same Wi-Fi mode and the transport keeps it; over the
     * USB cable only rfu does (the other modes turn into the real cable there).
     */
    fun mixedOk(ourWl: String?, peerWl: String?, usb: Boolean): Boolean =
        ourWl != null && ourWl == peerWl && ourWl in WIFI_MODES && (!usb || ourWl == "rfu")
}
