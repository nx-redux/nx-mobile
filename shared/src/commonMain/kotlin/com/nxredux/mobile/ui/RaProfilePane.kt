package com.nxredux.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar
import com.nxredux.mobile.ui.nx.NxPageTitle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.achievements.RaGameView
import com.nxredux.mobile.achievements.RaProfile
import com.nxredux.mobile.achievements.RaProfileUnlock
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NxBadge
import com.nxredux.mobile.ui.nx.NxCountPill
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.nxListLabelSize
import com.nxredux.mobile.ui.nx.nxPageInset
import com.nxredux.mobile.ui.nx.nxScrollFade
import com.nxredux.mobile.ui.nx.nxTextScale

/** A game's progress in the profile: "18 of 42 achievements". */
internal fun raProfileProgress(g: RaGameView): String = "${g.unlocked} of ${g.total} achievements"

/** An unlock's second line in the profile: its game, then when ("1942 · Today, 01:27"). */
internal fun raProfileUnlockLine(u: RaProfileUnlock, time: (Long) -> String): String =
    listOf(u.game, time(u.at * 1000)).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * The player's summary beside the RetroAchievements page on a split window ([RaPageWithProfile]): three sums, the
 * latest unlocks and the games nearest to mastery. To read, not to move through: the pad stays on the page's words.
 * [start]: from the pane's left edge (beside the page: a gap from its words; its own page: the page inset, as its title).
 */
@Composable
internal fun RaProfilePane(p: RaProfile, modifier: Modifier = Modifier, top: Dp = 28.dp, start: Dp = 20.dp) {
    val colors = LocalNxColors.current
    val label = NxTextSizes.secondary(nxListLabelSize()) * nxTextScale()
    val small = NxTextSizes.secondary(label)
    val scroll = rememberScrollState()
    Column(
        modifier.fillMaxSize().nxScrollFade(scroll, height = 40.dp, strong = true).verticalScroll(scroll)
            // the content edge on the right, as the words on the left (not a highlight's, a list inset further out)
            .padding(start = start, end = nxPageInset().end, top = top, bottom = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileSum(groupThousands(p.achievements), if (p.achievements == 1) "Achievement" else "Achievements", colors.listText, label, small, Modifier.weight(1f))
            ProfileSum(groupThousands(p.played), if (p.played == 1) "Game played" else "Games played", colors.listText, label, small, Modifier.weight(1f))
            ProfileSum(groupThousands(p.mastered), "Mastered", if (p.mastered > 0) colors.success else colors.listText, label, small, Modifier.weight(1f))
        }
        if (p.empty) {
            Spacer(Modifier.height(18.dp))
            ProfileText("Play a game with achievements and your unlocks show here.", colors.hint, small)
            return@Column
        }
        if (p.recent.isNotEmpty()) {
            ProfileSection("Recent unlocks", small)
            p.recent.forEach { u ->
                ProfileRow(u.badge, u.title, raProfileUnlockLine(u, ::formatUnlockTime), label, small) {
                    if (u.points > 0) NxCountPill(u.points.toString(), lit = false, fontSize = small)
                }
            }
        }
        if (p.inProgress.isNotEmpty()) {
            ProfileSection("In progress", small)
            p.inProgress.forEach { g ->
                ProfileRow(g.art, g.title, raProfileProgress(g), label, small, share = g.unlocked.toFloat() / g.total)
            }
        }
    }
}

/**
 * Tools -> RetroAchievements -> Profile ([Screen.RaProfile]): the summary under its page title, over a Back hint; a line
 * instead while signed out or before its first read. [split]: the column browser's focused right pane.
 */
@Composable
internal fun RaProfileScreen(state: AppState, onHint: ((NxButton) -> Unit)? = null, split: Boolean = false) {
    val profile by state.raProfile.collectAsState()
    val colors = LocalNxColors.current
    Column(Modifier.fillMaxSize()) {
        NxPageTitle(toolPageTitleHead(Screen.RaProfile, split).orEmpty())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val p = profile
            if (p != null) RaProfilePane(p, top = 8.dp, start = if (split) 20.dp else nxPageInset().start)
            else Box(Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                ProfileText(if (state.settings.raToken == null) "Sign in under Settings to see your profile." else "Reading your achievements…", colors.hint, NxTextSizes.secondary(nxListLabelSize()) * nxTextScale())
            }
        }
        NxHintBar(listOf(Hint(NxButton.B, "Back")), onHint = onHint)
    }
}

@Composable
private fun ProfileSum(value: String, name: String, ink: Color, label: TextUnit, small: TextUnit, modifier: Modifier) {
    val colors = LocalNxColors.current
    Column(modifier.background(colors.modalCard, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(value, color = ink, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold, fontSize = NxTextSizes.headline(label), maxLines = 1)
        Text(name, color = colors.hint, fontFamily = LocalNxFont.current, fontSize = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProfileSection(text: String, size: TextUnit) {
    Spacer(Modifier.height(18.dp))
    Text(text, color = LocalNxColors.current.accent, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ProfileText(text: String, color: Color, size: TextUnit) {
    Text(text, color = color, fontFamily = LocalNxFont.current, fontSize = size, lineHeight = NxTextSizes.line(size))
}

/** A picture ([image]; its room kept without one), a name and a dim line under it; [share]: a progress bar under them; [trailing] at the end. */
@Composable
private fun ProfileRow(
    image: String?, title: String, line: String, label: TextUnit, small: TextUnit, share: Float? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalNxColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(38.dp).height(38.dp).clip(RoundedCornerShape(6.dp)).background(colors.pictureGround)) { NxBadge(image, 38.dp, corner = 6.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = colors.hint, fontFamily = LocalNxFont.current, fontSize = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (share != null) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(colors.fieldGround)) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
