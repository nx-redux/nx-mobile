package com.nxredux.mobile.options

import com.nxredux.mobile.runner.PadFaceLayout
import com.nxredux.mobile.runner.PadMap
import com.nxredux.mobile.runner.SegaPadType

/**
 * Console Settings rows about the controller: Controller Layout for every console (the letters printed on the pad;
 * buttons work as printed, [com.nxredux.mobile.runner.PhysicalKeys]), Controller Type for the Sega pad tags, and
 * Portrait Pad for the two-screen consoles.
 * Frontend `nx_` keys, per game with a console default. The layout row is read on every key, so a change applies when
 * the menu closes; the type is sent to the running core at once (GameActivity.applySegaPad).
 */
object PadOptions {
    const val LAYOUT = "nx_pad_layout"
    const val SEGA_PAD = "nx_sega_pad_type"
    const val PORTRAIT_PAD = "nx_portrait_pad"
    const val SHOWN = "shown"
    const val HIDDEN = "hidden"
    const val AUTO = "auto"
    const val XBOX = "xbox"
    const val NINTENDO = "nintendo"

    val layoutRow = FrontendOption(
        LAYOUT, "Controller Layout", listOf(AUTO, XBOX, NINTENDO), AUTO,
        "The buttons on your controller work as printed:\nA is A. Auto-detect reads your controller.\n" +
            "Pick the other layout to swap A/B and X/Y,\nfor the console's original button positions.\n" +
            "PlayStation, Sega and arcade buttons\nalways go by position.",
        visual = false, valueLabels = listOf("Auto-detect", "Xbox (A at the bottom)", "Nintendo (A on the right)"),
    )

    val segaPadRow = FrontendOption(
        SEGA_PAD, "Controller Type", listOf(SegaPadType.AUTO, SegaPadType.THREE, SegaPadType.SIX), SegaPadType.AUTO,
        "The Sega pad the game sees.\nAuto uses 6 buttons only for games made for them.\n" +
            "Some older games misbehave with a 6-button pad.",
        visual = false, valueLabels = listOf("Auto", "3 buttons", "6 buttons"),
    )

    /**
     * DS and 3DS: the portrait band hidden for the clusters over the game, so the two screens get its room. The in-game
     * corner icon's long press flips it and saves it for the console (GameActivity.togglePortraitPad).
     */
    val portraitPadRow = FrontendOption(
        PORTRAIT_PAD, "Portrait Pad", listOf(SHOWN, HIDDEN), SHOWN,
        "Hidden gives the two screens the pad's room\nin portrait, with the see-through pad over\n" +
            "the game. Long-press the corner icon in game\nto switch.",
        brief = "Hidden: the pad goes see-through over the screens",
        visual = false, valueLabels = listOf("Shown", "Hidden"),
    )

    /** The consoles with two screens, which offer [portraitPadRow]. */
    fun hasPortraitPadRow(tag: String): Boolean = NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag)

    /** The portrait band is hidden: [values] say so and [tag] offers the row. */
    fun portraitPadHidden(tag: String, values: Map<String, String>): Boolean =
        hasPortraitPadRow(tag) && values[PORTRAIT_PAD] == HIDDEN

    fun tableFor(tag: String): List<FrontendOption> =
        (if (PadMap.isSegaPadTag(tag)) listOf(segaPadRow) else emptyList()) +
            (if (hasPortraitPadRow(tag)) listOf(portraitPadRow) else emptyList()) +
            listOf(layoutRow)

    val all: List<FrontendOption> = listOf(segaPadRow, portraitPadRow, layoutRow)

    /**
     * The letters printed on a pad: the [values] setting when forced, else the [detected] layout. A null [detected]
     * (no physical pad: an adb-injected or other virtual-device event) stays null, so its keys are sent as is.
     */
    fun printedLayout(values: Map<String, String>, detected: PadFaceLayout?): PadFaceLayout? {
        if (detected == null) return null
        return when (values[LAYOUT]) {
            XBOX -> PadFaceLayout.XBOX
            NINTENDO -> PadFaceLayout.NINTENDO
            else -> detected
        }
    }

}
