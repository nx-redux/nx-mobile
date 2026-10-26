package com.nxredux.mobile.options

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.runner.N3dsNewControls
import com.nxredux.mobile.settings.stepFrom

/**
 * Nintendo 3DS (Azahar) screen controls. Unlike the DS, the core draws both screens itself: the layout is Azahar's own
 * core option ([LAYOUT]), which it applies live. Like the DS the app keeps one layout per orientation ([layoutRows]:
 * portrait, portrait with a controller, landscape) and sets [LAYOUT] from the one in use ([coreLayoutFor]); the core option
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
     * How many times smaller Azahar draws the second screen in its top over bottom (nx-mobile's core patch; not a declared
     * option, so never shown or saved): half for Large screen, alike for Stacked ([stackedSmallProportion]).
     */
    const val STACKED_SMALL_PROPORTION = "citra_stacked_small_proportion"

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

    /**
     * SELECT + Right walks this order (Left the other way), wrapping: the DS's names. Large screen and Stacked are both the
     * core's top over bottom ("default"), the touch screen at half size or at the same size ([stackedSmallProportion]);
     * the core's own large screen (the touch screen beside) is left out, as on the DS.
     */
    val LAYOUT_CYCLE = listOf("large", "stacked", "single_screen", "side_by_side")
    private val LABELS = mapOf(
        "large" to "Large screen", "stacked" to "Stacked",
        "single_screen" to "Single screen", "side_by_side" to "Side by side",
    )

    // The layout keys are versioned because the defaults changed (per screen): an old value that was just the old default
    // reads as no pick, so the new default applies ([OLD_LAYOUT_KEYS]).
    const val LAYOUT_PORTRAIT = "nx_3ds_layout_portrait_v2"
    const val LAYOUT_PORTRAIT_CONTROLLER = "nx_3ds_layout_portrait_controller_v2"
    const val LAYOUT_LANDSCAPE = "nx_3ds_layout_landscape_v2"

    /** Each layout key to its previous key and that key's old default (the core's top over bottom). */
    val OLD_LAYOUT_KEYS: Map<String, Pair<String, String>> = mapOf(
        LAYOUT_PORTRAIT to ("nx_3ds_layout_portrait" to "default"),
        LAYOUT_PORTRAIT_CONTROLLER to ("nx_3ds_layout_portrait_controller" to "default"),
        LAYOUT_LANDSCAPE to ("nx_3ds_layout_landscape" to "default"),
    )

    /** The keys that can be absent ("no pick"): left out of [FrontendOptions.defaults] and of a config read when unset. */
    val NO_PICK_KEYS: Set<String> = OLD_LAYOUT_KEYS.keys

    /**
     * The stored value of layout row [key] in [values]: its own, else the old key's unless that was the old default; null
     * (no pick) when neither is set or the value is not a layout. An old "large_screen" (the core's) reads as "large".
     */
    fun layoutValue(values: Map<String, String>, key: String): String? {
        val (oldKey, oldDefault) = OLD_LAYOUT_KEYS.getValue(key)
        val stored = FrontendOptions.migrateKey(values, key, oldKey, oldDefault)
        return (if (stored == "large_screen") "large" else stored)?.takeIf { it in LAYOUT_CYCLE }
    }

    /**
     * The layout a screen starts with before the player picks one: Large screen on a Fold-shaped screen or tablet in
     * landscape, Stacked in portrait (the controller row too), Side by side on a regular phone in landscape.
     */
    fun defaultLayout(screen: ScreenClass, landscape: Boolean): String = when {
        !landscape -> "stacked"
        screen.isFoldShaped || screen.isTablet -> "large"
        else -> "side_by_side"
    }

    private const val LAYOUT_INFO_LIST = "Large screen: the top one big, the bottom one\nhalf its size under it.\n" +
        "Stacked: one above the other, the same size.\nSingle screen: the top one only.\n" +
        "Side by side: next to each other.\nSELECT + Left/Right cycles it."

    private fun layoutRow(key: String, label: String, default: String, info: String, fold: String, brief: String = "") = FrontendOption(
        key, label, LAYOUT_CYCLE, default, info + fold, visual = true, valueLabels = LAYOUT_CYCLE.map(::layoutLabel), brief = brief,
    )

    /**
     * Console Settings' layout rows, one per orientation as on the DS (in-game: applied live), for [screen] (null: unknown,
     * so the regular phone's defaults). The row default is the screen's [defaultLayout], shown while nothing is stored.
     * On a device that [canHalfFold] each says it is the layout when not half-folded ([NdsOptions.foldNote]).
     */
    fun layoutRowsFor(screen: ScreenClass?, canHalfFold: Boolean = DeviceTraits.canHalfFold): List<FrontendOption> {
        val known = screen ?: ScreenClass.REGULAR_PHONE
        val fold = NdsOptions.foldNote(canHalfFold)
        return listOf(
            layoutRow(LAYOUT_PORTRAIT, "Layout (portrait)", defaultLayout(known, false), "How the two screens share a portrait screen.\n$LAYOUT_INFO_LIST", fold),
            layoutRow(
                LAYOUT_PORTRAIT_CONTROLLER, "Layout (portrait, controller)", defaultLayout(known, false),
                "The portrait layout while a controller that is not a clip-on\nis connected and the pad is hidden.\nSELECT + Left/Right cycles it then.", fold,
                brief = "Used while a controller (not a clip-on) hides the pad",
            ),
            layoutRow(LAYOUT_LANDSCAPE, "Layout (landscape)", defaultLayout(known, true), "How the two screens share a landscape screen.\n$LAYOUT_INFO_LIST", fold),
        )
    }

    /** [layoutRowsFor] an unknown screen; built when read, as [NdsOptions.table]. */
    val layoutRows: List<FrontendOption> get() = layoutRowsFor(null)

    /** The row in use: landscape, else portrait with a (non-clip-on) controller ([controllerMode]), else portrait. */
    fun layoutKey(landscape: Boolean, controllerMode: Boolean): String = when {
        landscape -> LAYOUT_LANDSCAPE
        controllerMode -> LAYOUT_PORTRAIT_CONTROLLER
        else -> LAYOUT_PORTRAIT
    }

    /** The app's layout for the row in use on [screen]: the pick (old keys read too), else the screen's [defaultLayout]. */
    fun appLayoutFor(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, screen: ScreenClass): String =
        layoutValue(values, layoutKey(landscape, controllerMode)) ?: defaultLayout(screen, landscape)

    /**
     * The core's [LAYOUT] value for the row in use: Large screen and Stacked are its top over bottom ("default"). Half-folded
     * ([tabletop]) it is always that, whatever the player saved: the app lays the two screens out itself, from a Stacked frame.
     */
    fun coreLayoutFor(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, screen: ScreenClass, tabletop: Boolean): String =
        if (tabletop) "default" else when (val app = appLayoutFor(values, landscape, controllerMode, screen)) {
            "large", "stacked" -> "default"
            else -> app
        }

    /**
     * How many times smaller the core draws the touch screen in its top over bottom: half for Large screen, else alike.
     * Half-folded ([tabletop]) the screens are equal ("1") whatever the player saved.
     */
    fun stackedSmallProportion(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, screen: ScreenClass, tabletop: Boolean): String =
        if (!tabletop && appLayoutFor(values, landscape, controllerMode, screen) == "large") "2" else "1"

    /** SELECT + Left/Right: the row in use, as key to the layout [delta] steps from the one showing on [screen], wrapping. */
    fun cycleLayout(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, delta: Int, screen: ScreenClass): Pair<String, String> {
        return layoutKey(landscape, controllerMode) to LAYOUT_CYCLE.stepFrom(appLayoutFor(values, landscape, controllerMode, screen), delta)
    }

    /** What the hotkey's notice shows for a layout value. */
    fun layoutLabel(value: String): String = LABELS[value] ?: value

    /** [DEADZONE]'s stored percentage as 0..1; the core's 15% when missing or unreadable. */
    fun deadzone(value: String?): Float = value?.toIntOrNull()?.coerceIn(0, 100)?.let { it / 100f } ?: DEFAULT_DEADZONE
}
