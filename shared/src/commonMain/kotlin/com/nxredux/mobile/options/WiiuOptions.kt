package com.nxredux.mobile.options

import com.nxredux.mobile.layout.ScreenClass

/**
 * The Wii U's screen settings: frontend `nx_` keys, per game with a console default. Cemu always draws the TV over the
 * GamePad in one frame; the app places the two screens by the layout row in use, with the DS's layouts.
 */
object WiiuOptions {
    const val LAYOUT_PORTRAIT = "nx_wiiu_layout_portrait"
    const val LAYOUT_PORTRAIT_CONTROLLER = "nx_wiiu_layout_portrait_controller"
    const val LAYOUT_LANDSCAPE = "nx_wiiu_layout_landscape"
    const val BIG_SCREEN = "nx_wiiu_big_screen"

    val LAYOUT_KEYS: Set<String> = setOf(LAYOUT_PORTRAIT, LAYOUT_PORTRAIT_CONTROLLER, LAYOUT_LANDSCAPE)

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "WIIU"

    /** Before a pick: the TV big over a small GamePad in landscape, the two stacked full width in portrait. */
    fun defaultLayout(landscape: Boolean): NdsLayoutChoice = if (landscape) NdsLayoutChoice.LARGE else NdsLayoutChoice.STACKED

    private const val LAYOUT_INFO = "Large screen: the big one, the other at half its size\nunder it, or beside it where that leaves the big one bigger.\n" +
        "Stacked: one above the other, the same size.\nSingle screen: the big one only.\n" +
        "Picture in picture: the big one, the other small in its top right corner.\n" +
        "Side by side: next to each other, the same size.\nThe GamePad takes touch, except as the small picture-in-picture screen."

    private val screenLabels = listOf("TV", "GamePad")

    private fun layoutRow(key: String, label: String, default: NdsLayoutChoice, info: String, brief: String = "") = FrontendOption(
        key, label, NdsLayoutChoice.entries.map { it.value }, default.value, info, visual = true,
        valueLabels = NdsLayoutChoice.entries.map { it.label }, brief = brief,
    )

    val table: List<FrontendOption> = listOf(
        layoutRow(LAYOUT_PORTRAIT, "Layout (portrait)", defaultLayout(false), "How the TV and the GamePad share a portrait screen.\n$LAYOUT_INFO"),
        layoutRow(
            LAYOUT_PORTRAIT_CONTROLLER, "Layout (portrait, controller)", defaultLayout(false),
            "The portrait layout while a controller that is not a clip-on\n(Pocket Taco, FlipPad) is connected and the pad is hidden.\n$LAYOUT_INFO",
            brief = "Used while a controller (not a clip-on) hides the pad",
        ),
        layoutRow(LAYOUT_LANDSCAPE, "Layout (landscape)", defaultLayout(true), "How the TV and the GamePad share a landscape screen.\n$LAYOUT_INFO"),
        FrontendOption(
            BIG_SCREEN, "Big Screen", NdsScreen.entries.map { it.value }, NdsScreen.TOP.value,
            "The screen shown big in large screen, single screen and picture\nin picture, and first in stacked and side by side.\n" +
                "With a second display, the one on the picture display. SWAP swaps it.",
            visual = true, valueLabels = screenLabels,
        ),
    )

    /** The Wii U's screen settings as the DS layouts read them: the layout picks and the big screen, the inset as default. */
    fun settings(values: Map<String, String>): NdsSettings = NdsSettings(
        portrait = layout(values[LAYOUT_PORTRAIT]),
        landscape = layout(values[LAYOUT_LANDSCAPE]),
        controller = layout(values[LAYOUT_PORTRAIT_CONTROLLER]),
        big = NdsScreen.entries.firstOrNull { it.value == values[BIG_SCREEN] } ?: NdsScreen.TOP,
    )

    private fun layout(value: String?): NdsLayoutChoice? = NdsLayoutChoice.entries.firstOrNull { it.value == value }

    /** The layout drawn: the row in use's pick ([controllerMode]: the controller row in portrait), else [defaultLayout]. */
    fun layoutShown(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean): NdsLayoutChoice {
        val s = settings(values)
        val pick = if (landscape) s.landscape else if (controllerMode) s.controller else s.portrait
        return pick ?: defaultLayout(landscape)
    }

    /**
     * The layout the app draws under a pad skin: on a regular phone in [landscape] (as the DS's) the row in use's in the
     * skin's one glass, whose width the two stacked would leave mostly empty; null elsewhere (the Fold, a tablet,
     * portrait), where the skin's own TV over GamePad stays.
     */
    fun skinLayout(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, screen: ScreenClass): NdsLayoutChoice? =
        if (!landscape || screen.isFoldShaped || screen.isTablet) null else layoutShown(values, landscape, controllerMode)

    /** The layout rows a pad skin ([skinOn]) ignores on [screen], shown unavailable ([skinLayout] null there). */
    fun lockedLayoutRows(skinOn: Boolean, screen: ScreenClass): Set<String> = when {
        !skinOn -> emptySet()
        screen.isFoldShaped || screen.isTablet -> setOf(LAYOUT_PORTRAIT, LAYOUT_LANDSCAPE)
        else -> setOf(LAYOUT_PORTRAIT)
    }

    fun activeLayoutKey(view: NdsView): String = when {
        view.landscape -> LAYOUT_LANDSCAPE
        view.controllerMode -> LAYOUT_PORTRAIT_CONTROLLER
        else -> LAYOUT_PORTRAIT
    }

    /** The running game's rows: only the layout row in use on [view]; the rest pass through. */
    fun inGameRows(rows: List<FrontendOption>, view: NdsView): List<FrontendOption> {
        val active = activeLayoutKey(view)
        return rows.filter { it.key !in LAYOUT_KEYS || it.key == active }
    }

    /** SWAP: the other screen as the big one. */
    fun swapBigScreen(values: Map<String, String>): Pair<String, String> = BIG_SCREEN to settings(values).big.other.value
}
