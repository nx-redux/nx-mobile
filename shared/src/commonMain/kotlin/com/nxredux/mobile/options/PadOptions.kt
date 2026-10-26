package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.PadFaceLayout
import com.nxredux.mobile.runner.PadMap
import com.nxredux.mobile.runner.SegaPadType

/**
 * Console Settings rows about the controller: Controller Layout for every console (the letters printed on the pad;
 * buttons work as printed, [com.nxredux.mobile.runner.PhysicalKeys]), Controller Type for the Sega pad tags, and
 * Portrait Pad for the two-screen consoles and arcade.
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

    /**
     * Arcade, WonderSwan and Lynx: the same setting, for the games whose picture is taller than wide (the game offers
     * the corner icon only for those); a wide game keeps its band whatever this says.
     */
    val portraitPadTallRow = portraitPadRow.copy(
        info = "Hidden gives a game taller than wide (a vertical\narcade board, a handheld game held upright)\n" +
            "the pad's room in portrait, with the see-through\npad over the game. Wide games keep the pad.\n" +
            "Long-press the corner icon in game to switch.",
        brief = "Hidden: tall games get the pad's room",
    )

    /** The consoles with two screens. */
    private fun twoScreens(tag: String): Boolean = NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag)

    /**
     * The consoles some of whose games are taller than wide: arcade's vertical boards, and the handhelds whose cores
     * turn an upright game's picture (Beetle WonderSwan: Rotate display, a 9:14 aspect; Handy: the cartridge's
     * rotation, its width and height swapped).
     */
    private val TALL_GAME_CONSOLES = setOf("fbn", "wsc", "lynx")

    private fun tallGames(tag: String): Boolean = CoreRegistry.consoleOf(tag)?.id in TALL_GAME_CONSOLES

    /** The consoles that offer Portrait Pad: the two-screen ones ([portraitPadRow]) and those with tall games ([portraitPadTallRow]). */
    fun hasPortraitPadRow(tag: String): Boolean = twoScreens(tag) || tallGames(tag)

    /** The portrait band is hidden: [values] say so and [tag] offers the row. */
    fun portraitPadHidden(tag: String, values: Map<String, String>): Boolean =
        hasPortraitPadRow(tag) && values[PORTRAIT_PAD] == HIDDEN

    fun tableFor(tag: String): List<FrontendOption> =
        (if (PadMap.isSegaPadTag(tag)) listOf(segaPadRow) else emptyList()) +
            (if (twoScreens(tag)) listOf(portraitPadRow) else if (tallGames(tag)) listOf(portraitPadTallRow) else emptyList()) +
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
