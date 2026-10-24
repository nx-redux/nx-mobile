package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.ui.formatUnlockTime
import kotlin.math.roundToInt

/** The badge's largest and smallest size on the detail page; between them it takes what the text leaves. */
internal val AchievementBadgeMax = 96.dp
internal val AchievementBadgeMin = 40.dp

/** The gap between the badge and the name. */
private val AchievementBadgeGap = 10.dp

/**
 * The badge size on a detail page [available] tall whose text takes [textHeight]: what is left after the text and the
 * gap, between [AchievementBadgeMin] and [AchievementBadgeMax]; the largest when the height is unbounded.
 */
internal fun achievementBadgeSize(available: Dp, textHeight: Dp): Dp =
    if (!available.value.isFinite()) AchievementBadgeMax
    else (available - textHeight - AchievementBadgeGap).coerceIn(AchievementBadgeMin, AchievementBadgeMax)

/**
 * One achievement in full, NX Redux style: a centred column with the badge ([badgePath], the locked variant while
 * locked), the bold name, the description, then dim lines for points, when it was unlocked or a locked one's progress
 * (no bare state word: the badge shows it, [achievementStateLine]), unlock rate, type and whether its notifications are
 * muted. Sizes are the shared roles of the list label of the space it
 * gets: the name a headline, the description and the info lines the secondary size. The text keeps its size and the
 * badge takes the height left (up to [AchievementBadgeMax]), so the page fits without scrolling; it only scrolls where
 * even the smallest badge does not fit. Shared by the game list's achievements panel and the Tools achievement browser
 * (page and pane).
 */
@Composable
fun AchievementDetail(a: RaAchievement, badgePath: String?, muted: Boolean, modifier: Modifier = Modifier, textShare: Float = 1f) {
    val colors = LocalNxColors.current
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val bounded = maxHeight.value.isFinite()
        // Appearance → Text size, times [textShare] (the achievements panel's narrower box reads a step smaller).
        val label = (if (bounded) nxListLabelSize(maxHeight) else NxTextSizes.LABEL_FALLBACK) * (nxTextScale() * textShare)
        val name = NxTextSizes.headline(label)
        val body = NxTextSizes.secondary(label)
        val padV = 8.dp
        val room = if (bounded) maxHeight - padV * 2 else Dp.Infinity
        val align = TextAlign.Center
        val lines: @Composable () -> Unit = {
            DetailLine(a.title, colors.listText, name, FontWeight.Bold, align)
            if (a.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                DetailLine(a.description, colors.listText, body, align = align)
            }
            Spacer(Modifier.height(8.dp))
            DetailLine("${a.points} points", colors.hint, body, align = align)
            stateLine(a)?.let { DetailLine(it, colors.hint, body, align = align) }
            DetailLine("${raRarityPercent(a.rarity)}% unlock rate", colors.hint, body, align = align)
            a.type?.let { DetailLine("[${it.label}]", colors.hint, body, align = align) }
            if (muted) DetailLine("Muted", colors.hint, body, align = align)
        }
        val scroll = rememberScrollState()
        // A long description fades where more scrolls in (the lists' cue).
        Box(Modifier.fillMaxWidth().nxScrollFade(scroll, height = 56.dp, strong = true).verticalScroll(scroll).padding(horizontal = 28.dp, vertical = padV), contentAlignment = Alignment.Center) {
            Layout(
                content = {
                    NxBadge(badgePath, AchievementBadgeMax, corner = 12.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { lines() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val text = measurables.last().measure(Constraints(maxWidth = width))
                val badgeMeasurable = measurables.takeIf { it.size > 1 }?.first()
                val gap = AchievementBadgeGap.roundToPx()
                val side = achievementBadgeSize(room, text.height.toDp()).roundToPx()
                val badge = badgeMeasurable?.measure(Constraints.fixed(side, side))
                val height = (badge?.let { it.height + gap } ?: 0) + text.height
                layout(width, height) {
                    badge?.place((width - badge.width) / 2, 0)
                    text.place((width - text.width) / 2, height - text.height)
                }
            }
        }
    }
}

/**
 * The detail's progress line, without a bare state word (the badge shows locked or not): when an unlocked one was
 * unlocked, a locked one's progress; nothing for a pending one, a locked one without progress, or an unknown time.
 */
internal fun achievementStateLine(a: RaAchievement, unlockTime: (Long) -> String): String? = when {
    a.state == RaAchievementState.UNLOCKED -> a.unlockedAt?.let { "Unlocked ${unlockTime(it * 1000)}" }
    a.state == RaAchievementState.LOCKED && !a.progress.isNullOrBlank() -> "Progress: ${a.progress}"
    else -> null
}

private fun stateLine(a: RaAchievement): String? = achievementStateLine(a, ::formatUnlockTime)

/** Exactly two decimals, without `String.format` (not in commonMain): 12.5 -> "12.50", 3.14159 -> "3.14", 0 -> "0.00". */
fun raRarityPercent(value: Float): String {
    val cents = (value * 100).roundToInt().coerceAtLeast(0)
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

@Composable
private fun DetailLine(text: String, color: Color, size: TextUnit, weight: FontWeight = FontWeight.SemiBold, align: TextAlign = TextAlign.Center) {
    Text(
        text = text,
        color = color,
        fontFamily = LocalNxFont.current,
        fontWeight = weight,
        fontSize = size,
        lineHeight = NxTextSizes.line(size),
        textAlign = align,
    )
}
