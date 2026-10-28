package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.nxModalButton
import com.nxredux.mobile.ui.nx.nxModalSmall
import com.nxredux.mobile.ui.nx.nxScrollFade
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.AchievementDetail
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxRichList
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.nxListLabelSize
import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.LocalPortrait

/** The panel's share of the page's width: 60% of a portrait screen (the list shows beside it), under half a landscape one. */
private const val PANEL_SHARE_PORTRAIT = 0.6f
private const val PANEL_SHARE_LANDSCAPE = 0.45f
private const val SLIDE_MS = 220
/** The room above the panel's header and under its list. */
private val PANEL_EDGE = 10.dp

/**
 * The achievements panel ([RaPanel]) sliding in from the right over a game list, its full height (the title row's
 * icons under it too). Its game's name and progress, then the achievements as the RetroAchievements
 * tool lists them (a step smaller than a page's list), or one's details; a message while loading, signed out, or
 * without cached data. No hint bar: the pad's buttons are the list's; on touch the system back and a tap beside the panel stand in. Over
 * the modals' scrim, which closes it on a tap.
 * [onRow]: a tap on an achievement (its details); [onButton]: the sign-in button, as a pad press.
 */
@Composable
fun RaPanelOverlay(panel: RaPanel?, onRow: (Int) -> Unit, onButton: (NxButton) -> Unit) {
    // The last panel shown, so it slides out with its content after it closed.
    var last by remember { mutableStateOf(panel) }
    if (panel != null) last = panel
    // The modals' scrim over the list behind; a tap on it closes the panel, as on a modal.
    AnimatedVisibility(panel != null, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(SLIDE_MS)), exit = fadeOut(tween(SLIDE_MS))) {
        Box(Modifier.fillMaxSize().background(LocalNxColors.current.scrimModal).pointerInput(Unit) { detectTapGestures { onButton(NxButton.X) } })
    }
    AnimatedVisibility(
        panel != null, modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(tween(SLIDE_MS)) { it }, exit = slideOutHorizontally(tween(SLIDE_MS)) { it },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The device's orientation, not this box's shape: a portrait page above the menu pad (or on a near-square
            // screen) is wide and short, yet a portrait one.
            val share = if (LocalPortrait.current) PANEL_SHARE_PORTRAIT else PANEL_SHARE_LANDSCAPE
            last?.let { p ->
                Column(
                    Modifier.align(Alignment.TopEnd).width(maxWidth * share).fillMaxHeight()
                        .background(LocalNxColors.current.modalPanel, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .pointerInput(Unit) { detectTapGestures { } },   // taps inside stay inside
                ) { RaPanelBody(p, onRow, onButton) }
            }
        }
    }
}

@Composable
private fun ColumnScope.RaPanelBody(p: RaPanel, onRow: (Int) -> Unit, onButton: (NxButton) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val scale = nxTextScale()   // Appearance → Text size: the header, the rows, the messages
    // The header: the game and its progress, nothing else (B or the system back leave a details view; a tap beside the
    // panel, B or X close it).
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = PANEL_EDGE, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // The game's name and its progress, "Drill Dozer (3 of 56)": a long name wraps, the progress after it in the hint grey.
        val heading = buildAnnotatedString {
            append(p.title)
            p.progress?.let { withStyle(SpanStyle(color = colors.hint)) { append("\u00A0(" + it.replace(' ', '\u00A0') + ")") } }   // unbroken, kept with the last word
        }
        Text(heading, Modifier.weight(1f), color = colors.listText, fontFamily = font, fontSize = nxModalButton(), fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(4.dp))
    // The same room under the list as above the header.
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = PANEL_EDGE), contentAlignment = Alignment.Center) {
        // A step under the list a page this tall would show ([NxTextSizes.secondary] of its label size).
        val label = NxTextSizes.secondary(nxListLabelSize()) * nxTextScale()   // Appearance → Text size
        when (val c = p.content) {
            RaPanelContent.Loading -> PanelMessage("Loading…")
            RaPanelContent.SignedOut -> PanelMessage("Sign in to RetroAchievements to see this game's achievements.", action = "Sign in") { onButton(NxButton.A) }
            RaPanelContent.NoData -> PanelMessage(if (p.path == null) "No game selected." else "This game has no achievements.")
            RaPanelContent.Downloading -> PanelMessage("Downloading this game's achievements…")
            is RaPanelContent.Failed -> PanelMessage("Couldn't download this game's achievements: ${c.reason}.")
            is RaPanelContent.Unrecognised -> PanelMessage(
                "RetroAchievements doesn't recognise this game's file" + (if (c.hash.isEmpty()) "." else " (hash ${c.hash}).") +
                    " It may be a different version, a hack or a bad dump.",
            )
            RaPanelContent.Unreadable -> PanelMessage("Couldn't read this game's file to identify it.")
            is RaPanelContent.Game -> {
                val a = c.achievements.getOrNull(p.row)
                when {
                    c.achievements.isEmpty() -> PanelMessage("This game has no achievements.")
                    p.detail && a != null -> AchievementDetail(a, raBadgeModel(c.badges, a), muted = false, modifier = Modifier.fillMaxSize(), textShare = 0.8f)
                    else -> {
                        val rows = remember(c.achievements, c.badges) {
                            c.achievements.map { r -> raAchievementRow(r, c.badges) }
                        }
                        // The rows fade where more scrolls in (the lists' cue), the selection a row clear of the fades.
                        val listState = rememberLazyListState()
                        NxRichList(
                            rows, p.row, onSelect = onRow, onOpen = onRow, listState = listState, labelSize = label, edgeMargin = 1,
                            modifier = Modifier.fillMaxSize().nxScrollFade(listState, height = 56.dp, strong = true).padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = colors.hint, fontFamily = font, fontSize = nxModalSmall(), textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                action, color = colors.onAccent, fontFamily = font, fontSize = nxModalSmall(), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(colors.accent, RoundedCornerShape(50)).pointerInput(Unit) { detectTapGestures { onAction() } }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}
