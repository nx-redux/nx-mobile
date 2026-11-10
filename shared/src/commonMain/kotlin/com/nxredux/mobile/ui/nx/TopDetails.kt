package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.library.path
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent

/** What the top screen's game details list under their header: nothing, the downloading line, or the game's achievement [Rows]. */
internal sealed interface DetailsBody {
    data object None : DetailsBody
    data object Downloading : DetailsBody
    data class Rows(val achievements: List<RaAchievement>, val badges: Map<String, String>) : DetailsBody
}

/** [ra] when it is [entry]'s (its feed may lag the highlight by a moment, or still be another game's); else null. */
internal fun detailsRaFor(entry: Listed, ra: RaPanel?): RaPanel? = ra?.takeIf { it.path == entry.game.path }

/** [ra]'s body: the achievement rows of a game with any, the downloading line while they download, else nothing (signed out, no data). */
internal fun detailsBody(ra: RaPanel?): DetailsBody = when (val c = ra?.content) {
    is RaPanelContent.Game -> if (c.achievements.isEmpty()) DetailsBody.None else DetailsBody.Rows(c.achievements, c.badges)
    RaPanelContent.Downloading -> DetailsBody.Downloading
    else -> DetailsBody.None
}
