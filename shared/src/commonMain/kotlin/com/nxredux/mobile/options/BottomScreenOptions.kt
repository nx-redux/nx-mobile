package com.nxredux.mobile.options

import com.nxredux.mobile.runner.BottomScreen

/**
 * Console Settings → Bottom screen, for every console: which display is the touch screen when a second one is in use,
 * over Settings → Emulators → Bottom screen (System default follows it). A frontend `nx_` key, saved like the other
 * console rows; GameActivity moves the picture live when it changes.
 */
object BottomScreenOptions {
    const val KEY = "nx_bottom_screen"
    const val DEFAULT = "default"   // System default; the others are [BottomScreen.value]s ([BottomScreen.resolve] reads them)

    val row = FrontendOption(
        KEY, "Bottom screen", listOf(DEFAULT, BottomScreen.MAIN.value, BottomScreen.SECOND.value), DEFAULT,
        "Which screen is the touch screen with a second\nscreen in use: the picture goes on the other.\n" +
            "System default follows Settings → Emulators.",
        brief = "The touch screen; the game shows on the other",
        visual = false, valueLabels = listOf("System default", "Main screen", "Second screen"),
    )
}
