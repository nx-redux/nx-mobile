package com.nxredux.mobile.options

import com.nxredux.mobile.layout.ScreenClass

/**
 * App-owned settings that belong to a console: shown on the in-game Options → Console Settings page,
 * saved like the Frontend rows (`nx_` keys, per game or console). Static and pure, so a settings screen outside a game
 * can offer them without a core. Keyed by console tag, not core: the DS screen rows follow the console's screens; the
 * controller rows ([PadOptions]) and Bottom screen ([BottomScreenOptions]) are shared.
 */
object ConsoleSettings {
    /** [tag]'s rows; [screen] (null: unknown) shapes the DS layout rows ([NdsOptions.tableFor]). */
    fun tableFor(tag: String, screen: ScreenClass? = null): List<FrontendOption> = when {
        NdsOptions.appliesTo(tag) -> NdsOptions.tableFor(screen)
        N3dsOptions.appliesTo(tag) -> N3dsOptions.layoutRows + N3dsOptions.newControlsRow
        WiiOptions.appliesTo(tag) -> WiiOptions.table
        else -> emptyList()
    } + graphicsRows(tag) + BottomScreenOptions.row + PadOptions.tableFor(tag) + SpeedOptions.table

    private fun graphicsRows(tag: String): List<FrontendOption> =
        if (GraphicsApiOptions.appliesTo(tag)) listOf(GraphicsApiOptions.row) else emptyList()

    /** Every console's rows (keys are unique across them). */
    val all: List<FrontendOption> =
        NdsOptions.table + N3dsOptions.layoutRows + N3dsOptions.newControlsRow + WiiOptions.table + GraphicsApiOptions.row + BottomScreenOptions.row + PadOptions.all + SpeedOptions.table
}
