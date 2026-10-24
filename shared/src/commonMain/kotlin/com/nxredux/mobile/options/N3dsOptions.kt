package com.nxredux.mobile.options

import com.nxredux.mobile.runner.N3dsNewControls
import com.nxredux.mobile.settings.stepFrom

/**
 * Nintendo 3DS (Azahar) screen controls. Unlike the DS, the core draws both screens itself: the layout is Azahar's own
 * core option ([LAYOUT]), which it applies live. Like the DS the app keeps one layout per orientation ([layoutRows]:
 * portrait, portrait with a controller, landscape) and sets [LAYOUT] from the one in use ([layoutFor]); the core option
 * itself is locked. The SELECT + Left/Right hotkey changes that row and saves it to the game file like the DS hotkeys do.
 */
object N3dsOptions {
    const val LAYOUT = "citra_layout_option"
    /** The core's per-axis right-stick deadzone in percent; the stylus d-pad cursor starts just past it. */
    const val DEADZONE = "citra_analog_deadzone"
    const val DEFAULT_DEADZONE = 0.15f

    /** Console Settings → New 3DS Controls ([N3dsNewControls]): auto, on or off, per game with a console default. */
    const val NEW_CONTROLS = "nx_3ds_new_controls"

    val newControlsRow = FrontendOption(
        NEW_CONTROLS, "New 3DS Controls", listOf(N3dsNewControls.AUTO, N3dsNewControls.ON, N3dsNewControls.OFF), N3dsNewControls.AUTO,
        "ZL, ZR and the C-stick on the on-screen pad.\nAuto shows them for games that use them\n(New 3DS and Circle Pad Pro games).\n" +
            "A controller's right stick, L2 and R2 always reach the game.",
        brief = "On-screen ZL, ZR and C-stick",
        visual = false, valueLabels = listOf("Auto", "On", "Off"),
    )

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "3DS"

    /**
     * How many times smaller Azahar draws the second screen in Top over bottom (nx-mobile's core patch; not a declared
     * option, so never shown or saved): landscape gives the top screen three quarters of the height, portrait keeps both alike.
     */
    const val STACKED_SMALL_PROPORTION = "citra_stacked_small_proportion"

    fun stackedSmallProportion(landscape: Boolean): String = if (landscape) "3" else "1"

    /**
     * While the on-screen buttons are over the game, a finger touches the bottom screen only when it is the main one
     * (swapped); the stylus cursor still touches anywhere (nx-mobile's core patch; not declared, never shown or saved).
     */
    const val TOUCH_MAIN_ONLY = "citra_touch_main_only"

    fun touchMainOnly(padOverGame: Boolean): String = if (padOverGame) "enabled" else "disabled"

    /**
     * While stylus mode is on the core keeps its touch cursor shown however long it is left still, like the DS's pen dot;
     * off, it hides after its idle timeout (nx-mobile's core patch; not declared, never shown or saved).
     */
    const val CURSOR_VISIBLE = "citra_cursor_visible"

    fun cursorVisible(stylus: Boolean): String = if (stylus) "enabled" else "disabled"

    /** SELECT + Right walks this order (Left the other way), wrapping. */
    val LAYOUT_CYCLE = listOf("default", "large_screen", "side_by_side", "single_screen")
    private val LABELS = mapOf(
        "default" to "Top over bottom", "large_screen" to "Large screen",
        "side_by_side" to "Side by side", "single_screen" to "Single screen",
    )

    const val LAYOUT_PORTRAIT = "nx_3ds_layout_portrait"
    const val LAYOUT_PORTRAIT_CONTROLLER = "nx_3ds_layout_portrait_controller"
    const val LAYOUT_LANDSCAPE = "nx_3ds_layout_landscape"

    private const val LAYOUT_INFO_LIST = "Top over bottom: one above the other\n(in landscape the top one three quarters of the height).\n" +
        "Large screen: the top one big, the bottom small at its top right.\nSide by side: next to each other.\n" +
        "Single screen: the top one only.\nSELECT + Left/Right cycles it."

    private fun layoutRow(key: String, label: String, info: String, brief: String = "") = FrontendOption(
        key, label, LAYOUT_CYCLE, "default", info, visual = true, valueLabels = LAYOUT_CYCLE.map(::layoutLabel), brief = brief,
    )

    /** Console Settings' layout rows, one per orientation as on the DS (in-game: applied live). */
    val layoutRows: List<FrontendOption> = listOf(
        layoutRow(LAYOUT_PORTRAIT, "Layout (portrait)", "How the two screens share a portrait screen.\n$LAYOUT_INFO_LIST"),
        layoutRow(
            LAYOUT_PORTRAIT_CONTROLLER, "Layout (portrait, controller)",
            "The portrait layout while a controller that is not a clip-on\nis connected and the pad is hidden.\nSELECT + Left/Right cycles it then.",
            brief = "Used while a controller (not a clip-on) hides the pad",
        ),
        layoutRow(LAYOUT_LANDSCAPE, "Layout (landscape)", "How the two screens share a landscape screen.\n$LAYOUT_INFO_LIST"),
    )

    /** The row in use: landscape, else portrait with a (non-clip-on) controller ([controllerMode]), else portrait. */
    fun layoutKey(landscape: Boolean, controllerMode: Boolean): String = when {
        landscape -> LAYOUT_LANDSCAPE
        controllerMode -> LAYOUT_PORTRAIT_CONTROLLER
        else -> LAYOUT_PORTRAIT
    }

    /** The core's [LAYOUT] value for the row in use; an unknown or missing value counts as "default". */
    fun layoutFor(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean): String =
        values[layoutKey(landscape, controllerMode)]?.takeIf { it in LAYOUT_CYCLE } ?: "default"

    /** SELECT + Left/Right: the row in use, as key to the layout [delta] steps from its value, wrapping. */
    fun cycleLayout(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, delta: Int): Pair<String, String> {
        return layoutKey(landscape, controllerMode) to LAYOUT_CYCLE.stepFrom(layoutFor(values, landscape, controllerMode), delta)
    }

    /** What the hotkey's notice shows for a layout value. */
    fun layoutLabel(value: String): String = LABELS[value] ?: value

    /** [DEADZONE]'s stored percentage as 0..1; the core's 15% when missing or unreadable. */
    fun deadzone(value: String?): Float = value?.toIntOrNull()?.coerceIn(0, 100)?.let { it / 100f } ?: DEFAULT_DEADZONE
}
