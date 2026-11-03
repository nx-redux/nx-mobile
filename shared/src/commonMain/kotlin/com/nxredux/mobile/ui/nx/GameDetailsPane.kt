package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.library.path
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent
import com.nxredux.mobile.ui.panelArt
import com.nxredux.mobile.ui.raBadgeModel

/** What the details pane lists under its header: nothing, the downloading line, or the game's achievement [Rows]. */
internal sealed interface DetailsBody {
    data object None : DetailsBody
    data object Downloading : DetailsBody
    data class Rows(val achievements: List<RaAchievement>, val badges: Map<String, String>) : DetailsBody
}

/**
 * The details pane's header for [entry]: its name, box art (else its console's logo), total play time from [info], and
 * the achievement count: the pane's own list's ([ra], pending counted as unlocked, as [RaPanel.progress]) once it has
 * one, else the menus' cached count ([GameInfo.ra]). No clock or battery: the status bar shows on the menu.
 */
internal fun detailsHeader(entry: Listed, info: GameInfo?, ra: RaPanel?): GamePanelHeader {
    val g = entry.game
    val list = (ra?.content as? RaPanelContent.Game)?.achievements?.takeIf { it.isNotEmpty() }
    val count = list?.let { all -> all.count { it.state != RaAchievementState.LOCKED } to all.size }
        ?: info?.ra?.let { it.unlocked to it.total }
    return GamePanelHeader(
        entry.shown, panelArt(g.boxartRef?.value, g.box2dRef?.value, null, g.screenshotRef?.value),
        entry.system.tag, info?.totalMs ?: 0L, count, clockText = "", batteryPercent = null,
    )
}

/** [ra] when it is [entry]'s (its feed may lag the highlight by a moment, or still be another game's); else null. */
internal fun detailsRaFor(entry: Listed, ra: RaPanel?): RaPanel? = ra?.takeIf { it.path == entry.game.path }

/** [ra]'s body: the achievement rows of a game with any, the downloading line while they download, else nothing (signed out, no data). */
internal fun detailsBody(ra: RaPanel?): DetailsBody = when (val c = ra?.content) {
    is RaPanelContent.Game -> if (c.achievements.isEmpty()) DetailsBody.None else DetailsBody.Rows(c.achievements, c.badges)
    RaPanelContent.Downloading -> DetailsBody.Downloading
    else -> DetailsBody.None
}

/**
 * The highlighted game's details, full screen on the menu's bottom screen while the game list is on the top one (the
 * menu on the second display): the game panel's header ([GamePanelHeaderBar]: art or console logo, title, play time,
 * achievement count) across the top, then its achievements as [AchievementRow]s, the whole pane one scroll, full width in
 * portrait and landscape alike. [ra]: the pane's achievements ([com.nxredux.mobile.ui.AppState.gameDetailRa]; null signed
 * out), used only while it is [entry]'s ([detailsRaFor]). [entry] null (an empty list): the pane alone. On a touchscreen
 * a Back pill bottom-right calls [onBack] (B and the back gesture do the same through AppState), since the pane has no hints.
 */
@Composable
fun GameDetailsPane(entry: Listed?, info: GameInfo?, ra: RaPanel?, onBack: () -> Unit) {
    val colors = LocalNxColors.current
    val touch = LocalHasTouchscreen.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (entry != null) {
            val shown = detailsRaFor(entry, ra)
            val body = detailsBody(shown)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (touch) DP_LIST_BOTTOM else DP_LIST_END),
            ) {
                // The header in its own gutters, as the game panel's; the rows in the panel's narrower list gutter.
                item(key = "header") { GamePanelHeaderBar(detailsHeader(entry, info, shown)) }
                when (body) {
                    DetailsBody.None -> Unit
                    DetailsBody.Downloading -> item(key = "downloading") {
                        Text(
                            "Downloading achievements…", color = colors.hint, fontFamily = LocalNxFont.current, fontSize = DP_MESSAGE_SIZE,
                            maxLines = 1, modifier = Modifier.fillMaxWidth().padding(horizontal = DP_LIST_GUTTER + 12.dp),
                        )
                    }
                    is DetailsBody.Rows -> items(body.achievements, key = { it.id }) { a ->
                        Box(Modifier.padding(horizontal = DP_LIST_GUTTER)) { AchievementRow(a.title, a, raBadgeModel(body.badges, a), selected = false, Modifier) }
                    }
                }
            }
        }
        if (touch) PanelPill("Back", Modifier.align(Alignment.BottomEnd).padding(DP_PILL_MARGIN), onClick = onBack)
    }
}

private val DP_LIST_GUTTER = 8.dp   // the game panel's list gutter
private val DP_LIST_BOTTOM = 52.dp   // the last row clears the Back pill
private val DP_LIST_END = 8.dp       // without the pill
private val DP_PILL_MARGIN = 10.dp
private val DP_MESSAGE_SIZE = 13.sp
