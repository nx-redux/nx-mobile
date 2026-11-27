package com.nxredux.mobile.options

import com.nxredux.mobile.runner.PadMap
import com.nxredux.mobile.runner.SegaPadType

/**
 * Console Settings rows about the controller: Controller Type for the Sega pad tags, a frontend `nx_` key per game with
 * a console default, sent to the running core at once (GameActivity.applySegaPad).
 */
object PadOptions {
    const val SEGA_PAD = "nx_sega_pad_type"

    val segaPadRow = FrontendOption(
        SEGA_PAD, "Controller Type", listOf(SegaPadType.AUTO, SegaPadType.THREE, SegaPadType.SIX), SegaPadType.AUTO,
        "The Sega pad the game sees.\nAuto uses 6 buttons only for games made for them.\n" +
            "Some older games misbehave with a 6-button pad.",
        visual = false, valueLabels = listOf("Auto", "3 buttons", "6 buttons"),
    )

    fun tableFor(tag: String): List<FrontendOption> = if (PadMap.isSegaPadTag(tag)) listOf(segaPadRow) else emptyList()

    val all: List<FrontendOption> = listOf(segaPadRow)
}
