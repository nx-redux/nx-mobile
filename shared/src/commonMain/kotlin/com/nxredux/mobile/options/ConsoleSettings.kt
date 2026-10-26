package com.nxredux.mobile.options

import com.nxredux.mobile.layout.ScreenClass

/**
 * App-owned settings that belong to a console: shown on the in-game Options → Console Settings page,
 * saved like the Frontend rows (`nx_` keys, per game or console). Static and pure, so a settings screen outside a game
 * can offer them without a core. Keyed by console tag, not core: the DS screen rows follow the console's screens; the
 * controller rows ([PadOptions]) and Bottom screen ([BottomScreenOptions]) are shared.
 */
object ConsoleSettings {
    /**
     * [tag]'s rows; [screen] (null: unknown) shapes the DS and 3DS layout rows, [displayMax] (null: unknown) the DS 3D
     * Resolution scales.
     */
    fun tableFor(tag: String, screen: ScreenClass? = null, displayMax: Int? = null): List<FrontendOption> = when {
        NdsOptions.appliesTo(tag) -> NdsOptions.tableFor(screen, displayMax = displayMax)
        N3dsOptions.appliesTo(tag) -> N3dsOptions.layoutRowsFor(screen) + N3dsOptions.newControlsRow
        WiiOptions.appliesTo(tag) -> WiiOptions.table
        else -> emptyList()
    } + graphicsRows(tag) + BottomScreenOptions.row + PadOptions.tableFor(tag) + SpeedOptions.table

    /**
     * The row and the value it shows for [value] (stored, else the row default). Only the 3D Resolution scales differ: a
     * stored scale the display no longer reaches (a smaller window, another phone) is added as a choice, labelled "7x", so
     * it still shows and cycles; the unstored default above the list (Max Scale 4x on a 3x display) is not added, the row
     * shows the list's last scale. Any other row is returned as it is.
     */
    fun fitted(option: FrontendOption, value: String): Pair<FrontendOption, String> = when {
        option.key !in NdsOptions.RESOLUTION_KEYS || value in option.values -> option to value
        value == option.default -> option to option.values.last()
        else -> option.copy(values = option.values + value, valueLabels = option.valueLabels + "${value}x") to value
    }

    private fun graphicsRows(tag: String): List<FrontendOption> =
        if (GraphicsApiOptions.appliesTo(tag)) listOf(GraphicsApiOptions.row) else emptyList()

    /** Every console's rows (keys are unique across them); built when read, as [NdsOptions.table]. */
    val all: List<FrontendOption> get() =
        NdsOptions.table + N3dsOptions.layoutRows + N3dsOptions.newControlsRow + WiiOptions.table + GraphicsApiOptions.row + BottomScreenOptions.row + PadOptions.all + SpeedOptions.table
}
