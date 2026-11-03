package com.nxredux.mobile.runner

/** The Wii controller a game gets. Classic is Dolphin's Classic Controller Pro (all digital). */
enum class WiiPad { SIDEWAYS, UPRIGHT, NUNCHUK, CLASSIC }

/** One game's row of assets/wii/controls.tsv (GameTDB, tools/gametdb): C classic listed, N nunchuk / M MotionPlus required, U upright list. */
data class WiiControls(val classic: Boolean, val nunchuk: Boolean, val motionPlus: Boolean, val upright: Boolean) {
    companion object {
        fun parse(flags: String) = WiiControls('C' in flags, 'N' in flags, 'M' in flags, 'U' in flags)
    }
}

/**
 * Controls → Wii Controller (`nx_wii_controller`): Auto picks from the game's [WiiControls], the others force.
 * Auto prefers the Classic Controller whenever the game takes one (the closest to a gamepad), except for MotionPlus games.
 */
object WiiController {
    const val AUTO = "auto"
    const val SIDEWAYS = "sideways"
    const val UPRIGHT = "upright"
    const val NUNCHUK = "nunchuk"
    const val CLASSIC = "classic"

    // libretro Dolphin's device ids, (n shl 8) or RETRO_DEVICE_JOYPAD (DolphinLibretro/Input.cpp:52-63).
    private const val WIIMOTE = 0x001
    private const val WIIMOTE_SW = 0x201
    private const val WIIMOTE_NC = 0x301
    private const val WIIMOTE_CC_PRO = 0x501
    private const val WIIMOTE_MP = 0x701
    private const val WIIMOTE_MP_SW = 0x801
    private const val WIIMOTE_MP_NC = 0x901

    fun auto(c: WiiControls?): WiiPad = when {
        c == null -> WiiPad.SIDEWAYS
        c.motionPlus -> if (c.nunchuk) WiiPad.NUNCHUK else if (c.upright) WiiPad.UPRIGHT else WiiPad.SIDEWAYS
        c.classic -> WiiPad.CLASSIC
        c.upright -> WiiPad.UPRIGHT   // the hand-kept list overrides GameTDB's region-inconsistent Nunchuk flag (RHAK01 "NU")
        c.nunchuk -> WiiPad.NUNCHUK
        else -> WiiPad.SIDEWAYS
    }

    fun resolve(setting: String?, c: WiiControls?): WiiPad = when (setting) {
        SIDEWAYS -> WiiPad.SIDEWAYS
        UPRIGHT -> WiiPad.UPRIGHT
        NUNCHUK -> WiiPad.NUNCHUK
        CLASSIC -> WiiPad.CLASSIC
        else -> auto(c)
    }

    /** The MotionPlus dongle goes on for games that require it, on any Wiimote type. */
    fun motionPlus(pad: WiiPad, c: WiiControls?): Boolean = pad != WiiPad.CLASSIC && c?.motionPlus == true

    fun device(pad: WiiPad, motionPlus: Boolean): Int = when (pad) {
        WiiPad.SIDEWAYS -> if (motionPlus) WIIMOTE_MP_SW else WIIMOTE_SW
        WiiPad.UPRIGHT -> if (motionPlus) WIIMOTE_MP else WIIMOTE
        WiiPad.NUNCHUK -> if (motionPlus) WIIMOTE_MP_NC else WIIMOTE_NC
        WiiPad.CLASSIC -> WIIMOTE_CC_PRO
    }
}

/** Controls → Motion Controls (`nx_wii_motion`): Auto = on for MotionPlus games. Never without sensors or on Classic. */
object WiiMotion {
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"

    fun resolve(setting: String?, pad: WiiPad, c: WiiControls?, hasSensors: Boolean): Boolean {
        if (!hasSensors || pad == WiiPad.CLASSIC) return false
        return when (setting) {
            ON -> true
            OFF -> false
            else -> c?.motionPlus == true
        }
    }
}
