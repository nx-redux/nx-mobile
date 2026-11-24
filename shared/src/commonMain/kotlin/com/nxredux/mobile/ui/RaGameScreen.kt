package com.nxredux.mobile.ui

import com.nxredux.mobile.shared.res.nav_icon_filter
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.ui.nx.HintTouch
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxRichListBody
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.nxListInset
import com.nxredux.mobile.ui.nx.nxListLabelSize
import com.nxredux.mobile.achievements.RaConsoles
import com.nxredux.mobile.runner.CoreRegistry
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text

/**
 * One game's achievements with their badges and state; Y shows only the locked ones. The list alone (no description
 * area; the rows use the space), A opens the highlighted one's detail page: on its own page, or on a split window to the
 * right of this list ([RaSplitScreen]). [split]: a pane of the column browser, titled by the game alone; [focused] =
 * false: its dimmed left pane beside an achievement, no hints.
 */
@Composable
fun RaGameScreen(
    screen: Screen.RaGame, controller: RaController, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null,
    split: Boolean = false,
    focused: Boolean = true,
) {
    // Built once per list, not per d-pad move (the selection recomposes this screen).
    val visible = remember(screen.achievements, screen.lockedOnly) { visibleAchievements(screen) }
    val filter = raGameFilterHint(screen.lockedOnly)
    // A game on a console only external emulators run has its achievements shown, never unlocked here.
    val note = RaConsoles.unlocksNote(
        externalOnly = screen.game.consoleTag?.let { CoreRegistry.consoleOf(it)?.externalOnly } == true, pickedExternal = false,
    )
    Column(modifier.fillMaxSize()) {
        NxRichListBody(
            title = toolPageTitleHead(screen, split).orEmpty(), titleSuffix = toolPageCount(screen),
            rows = remember(visible, screen.badges) { visible.map { a -> raAchievementRow(a, screen.badges) } },
            selectedIndex = selectedIndex,
            hints = if (focused) listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Details"), filter) else emptyList(),
            onSelect = onSelect, onOpen = onOpen, modifier = Modifier.weight(1f), onHint = onHint,
            emptyText = if (screen.lockedOnly && screen.achievements.isNotEmpty()) "Everything unlocked." else null,
        )
        if (note != null) Text(
            note, color = LocalNxColors.current.hint, fontFamily = LocalNxFont.current, fontSize = NxTextSizes.secondary(nxListLabelSize()),
            modifier = Modifier.padding(horizontal = nxListInset(), vertical = 6.dp),
        )
    }
}

/** Y on a game's achievements: Locked only / Show all; touch, a filter icon lit while Locked only is on. */
internal fun raGameFilterHint(lockedOnly: Boolean): Hint =
    Hint(NxButton.Y, if (lockedOnly) "Show all" else "Locked only", HintTouch.Icon(Res.drawable.nav_icon_filter, lit = lockedOnly))
