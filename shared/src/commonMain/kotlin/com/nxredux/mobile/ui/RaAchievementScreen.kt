package com.nxredux.mobile.ui

import com.nxredux.mobile.tryOr
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.ui.nx.AchievementDetail
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar
import com.nxredux.mobile.ui.nx.NxPageTitle

/**
 * One achievement in full under its game's page title, the same body as the in-game menu's detail page, over a Back hint.
 * [split]: the focused right pane of the column browser ([RaSplitScreen]), titled without the tool's name.
 */
@Composable
fun RaAchievementScreen(
    screen: Screen.RaAchievement, controller: RaController, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null,
    split: Boolean = false,
) {
    val a = screen.achievement
    val muted by produceState(false, screen.game.hash, a.id) {
        value = tryOr({ false }) { a.id in controller.mutes.load(screen.game.hash) }
    }
    Column(modifier.fillMaxSize()) {
        NxPageTitle(toolPageTitleHead(screen, split).orEmpty(), suffix = toolPageCount(screen))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AchievementDetail(a, raBadgeModel(screen.badges, a), muted)
        }
        NxHintBar(listOf(Hint(NxButton.B, "Back")), onHint = onHint)
    }
}
