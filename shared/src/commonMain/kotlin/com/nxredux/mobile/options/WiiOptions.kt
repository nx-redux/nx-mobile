package com.nxredux.mobile.options

import com.nxredux.mobile.runner.WiiController
import com.nxredux.mobile.runner.WiiMotion

/**
 * Console Settings rows for the Wii: the controller the game gets, the phone's motion sensors, the screen shape and the
 * touch pointer's calibration. Frontend `nx_` keys, per game with a console default; GameActivity applies the controller
 * and motion live (applyWii), the rest at launch.
 */
object WiiOptions {
    const val CONTROLLER = "nx_wii_controller"
    const val MOTION = "nx_wii_motion"
    const val SCREEN = "nx_wii_screen"
    const val POINTER_WIDTH = "nx_wii_pointer_width"
    const val POINTER_HEIGHT = "nx_wii_pointer_height"
    const val POINTER_OFFSET = "nx_wii_pointer_offset"
    private val PERCENTS = (50..150 step 5).map { it.toString() }
    private val OFFSETS = (-20..20).map { it.toString() }

    val controllerRow = FrontendOption(
        CONTROLLER, "Wii Controller",
        listOf(WiiController.AUTO, WiiController.SIDEWAYS, WiiController.UPRIGHT, WiiController.NUNCHUK, WiiController.CLASSIC),
        WiiController.AUTO,
        "The controller the game sees.\nAuto picks it from the game (GameTDB),\npreferring the Classic Controller when the game takes one.",
        visual = false,
        valueLabels = listOf("Auto", "Sideways Wiimote", "Wiimote", "Wiimote + Nunchuk", "Classic Controller"),
    )

    val motionRow = FrontendOption(
        MOTION, "Motion Controls", listOf(WiiMotion.AUTO, WiiMotion.ON, WiiMotion.OFF), WiiMotion.AUTO,
        "Tilt and turn the phone to move the Wiimote.\nAuto turns it on for MotionPlus games.\nOff tilts with the left stick.",
        visual = false, valueLabels = listOf("Auto", "On", "Off"),
    )

    val screenRow = FrontendOption(
        SCREEN, "Wii Screen", listOf(WiiScreen.AUTO, WiiScreen.FOUR_THREE, WiiScreen.WIDE), WiiScreen.AUTO,
        "The Wii's screen shape. Auto is 4:3 when the game starts\nin portrait, 16:9 in landscape.\n" +
            "Set when the game starts: rotating keeps it until the next launch.",
        brief = "Set at launch. Auto: 4:3 in portrait, 16:9 in landscape",
        visual = false, valueLabels = listOf("Auto", "4:3", "16:9"), atLaunch = true,
    )

    // The touch pointer's calibration ([WiiScreen.pointerOptions]), for a device or game where a tap misses the finger.
    val pointerWidthRow = FrontendOption(
        POINTER_WIDTH, "Pointer Width", PERCENTS, "100",
        "How far the cursor moves sideways for a touch.\nLower it if the cursor lands further out than your finger,\n" +
            "raise it if it falls short. Applies the next time the game starts.",
        brief = "Applies next launch",
        visual = false, valueLabels = PERCENTS.map { "$it%" }, atLaunch = true,
    )
    val pointerHeightRow = FrontendOption(
        POINTER_HEIGHT, "Pointer Height", PERCENTS, "100",
        "How far the cursor moves up and down for a touch.\nLower it if the cursor lands further out than your finger,\n" +
            "raise it if it falls short. Applies the next time the game starts.",
        brief = "Applies next launch",
        visual = false, valueLabels = PERCENTS.map { "$it%" }, atLaunch = true,
    )
    val pointerOffsetRow = FrontendOption(
        POINTER_OFFSET, "Pointer Offset", OFFSETS, "0",
        "Moves the cursor up (+) or down (-) against your finger.\nApplies the next time the game starts.",
        brief = "Applies next launch",
        visual = false, valueLabels = OFFSETS.map { if (it.toInt() > 0) "+$it" else it }, atLaunch = true,
    )

    val table: List<FrontendOption> = listOf(controllerRow, motionRow, screenRow, pointerWidthRow, pointerHeightRow, pointerOffsetRow)

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "WII"
}

/**
 * The Wii's widescreen system setting (SYSCONF), from Console Settings → Wii Screen and the orientation the game starts in.
 * Games read it once at boot, and Dolphin applies it only then, so it is chosen at launch.
 */
object WiiScreen {
    const val AUTO = "auto"
    const val FOUR_THREE = "4:3"
    const val WIDE = "16:9"
    /** Dolphin's option for it (System Configuration > Widescreen (Wii)), locked in Emulator Settings. */
    const val CORE_KEY = "dolphin_widescreen"

    /** 16:9 for [value] when the game starts in [portrait] or not: Auto (or unknown) is 4:3 in portrait, 16:9 in landscape. */
    fun widescreen(value: String?, portrait: Boolean): Boolean = when (value) {
        FOUR_THREE -> false
        WIDE -> true
        else -> !portrait
    }

    fun coreValue(widescreen: Boolean): String = if (widescreen) "enabled" else "disabled"

    /**
     * The touch pointer's geometry for the shape (Dolphin's IR yaw, pitch and vertical offset), calibrated on the Fold in
     * Super Mario Galaxy 2 so a tap lands under the finger: a 4:3 picture needs more yaw than a 16:9 one. The touch is in
     * 0..1 of the picture, so the phone's screen does not change it. Locked in Emulator Settings (saved values would win);
     * Console Settings' Pointer Width / Height scale the yaw / pitch ([width] / [height], percent) and Pointer Offset
     * moves the cursor up (+) or down ([offset], added to the offset) for a device or game where a tap misses.
     */
    fun pointerOptions(widescreen: Boolean, width: String? = null, height: String? = null, offset: String? = null): Map<String, String> {
        val yaw = if (widescreen) 18 else 20
        val scaleX = width?.toIntOrNull()?.coerceIn(50, 150) ?: 100
        val scaleY = height?.toIntOrNull()?.coerceIn(50, 150) ?: 100
        val up = offset?.toIntOrNull()?.coerceIn(-20, 20) ?: 0
        return mapOf(
            "dolphin_ir_yaw" to scaled(yaw, scaleX).toString(),
            "dolphin_ir_pitch" to scaled(20, scaleY).toString(),
            "dolphin_ir_offset" to (16 + up).toString(),
        )
    }

    /** [base] degrees at [percent], rounded into Dolphin's 1..100. */
    private fun scaled(base: Int, percent: Int): Int = ((base * percent + 50) / 100).coerceIn(1, 100)
}
