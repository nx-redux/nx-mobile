package com.nxredux.mobile.ui.nx

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** A row's help text as the note shows it; [scrollSeq] counts UP/DOWN steps ([scrollDir], -1 up, +1 down). */
data class NxHelpText(val title: String, val text: String, val scrollSeq: Int = 0, val scrollDir: Int = 0)

/** The selected touch row's plate, in window pixels, with its [label] and its description's size (the note's title size). */
internal data class NxHelpRow(val label: String, val bounds: Rect, val titleSize: TextUnit)

/** Where the selected touch row sits ([NxTouchRow] reports it): the note leaves it lit and sits beside it. */
internal object NxHelpAnchor {
    var row by mutableStateOf<NxHelpRow?>(null)
}

/** Where the note goes: its top-left, its height cap, and whether it sits below the row (else above, or centred with no row). */
internal data class HelpNotePlace(val x: Int, val y: Int, val maxHeight: Int, val below: Boolean)

/** The note's width in a [width]-pixel screen: the [row]'s (within [minWidth]..[maxWidth], [margin] clear of the edges); no row, [maxWidth]. */
internal fun helpNoteWidth(width: Int, row: Rect?, minWidth: Int, maxWidth: Int, margin: Int): Int {
    val most = (width - margin * 2).coerceAtLeast(0)
    return if (row == null) minOf(maxWidth, most) else row.width.roundToInt().coerceIn(minOf(minWidth, most), minOf(maxWidth, most))
}

/** The room the note has on the roomier side of [row] (the whole height less the margins with no row). */
internal fun helpNoteRoom(height: Int, row: Rect?, gap: Int, margin: Int): Int =
    if (row == null) (height - margin * 2).coerceAtLeast(0)
    else maxOf(row.top - gap - margin, height - row.bottom - gap - margin).roundToInt().coerceAtLeast(0)

/**
 * The note's place in a [width] × [height] screen, [noteWidth] × [noteHeight] (measured at [helpNoteRoom]): at the [row]'s
 * left edge, [gap] below it when it fits there, else above when it fits there, else on the roomier side; [margin] clear
 * of the edges. No row: centred. All in pixels.
 */
internal fun helpNotePlace(width: Int, height: Int, row: Rect?, noteWidth: Int, noteHeight: Int, gap: Int, margin: Int): HelpNotePlace {
    if (row == null) return HelpNotePlace((width - noteWidth) / 2, (height - noteHeight) / 2, height - margin * 2, below = true)
    val x = row.left.roundToInt().coerceIn(margin, (width - margin - noteWidth).coerceAtLeast(margin))
    val above = (row.top - gap - margin).roundToInt()
    val below = (height - row.bottom - gap - margin).roundToInt()
    val under = noteHeight <= below || (noteHeight > above && below >= above)
    val y = if (under) (row.bottom + gap).roundToInt() else (row.top - gap).roundToInt() - noteHeight
    return HelpNotePlace(x, y, if (under) below else above, under)
}

private val HelpGap = 12.dp
private val HelpMargin = 16.dp
private val HelpMinWidth = 280.dp
private val HelpMaxWidth = 480.dp
/** How far the note rises toward its row as it fades in. */
private val HelpRise = 4.dp
/** The lit row's corners: its plate's ([NxTouchRow]). */
private val HelpHoleCorner = 14.dp
/** How far one d-pad press scrolls the note: about three lines. */
private val HelpScrollStep = 72.dp

/**
 * A row's help note ([note] null: none; it fades out): two layers (the accent-faint sheet, a black card with the row's
 * name in [NxColors.inkDim] and the text in full ink) beside the focused row, which stays lit through the scrim around
 * it. No OK row: A, B, X (the host) or a tap anywhere close it ([onClose]); UP / DOWN scroll it ([NxHelpText.scrollSeq]),
 * a Scroll hint under the card while it can (a pad in use).
 */
@Composable
fun NxSettingInfoPopup(note: NxHelpText?, onClose: () -> Unit) {
    // The last note shown, so its fade-out still has it.
    val kept = remember { arrayOfNulls<NxHelpText>(1) }
    if (note != null) kept[0] = note
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = note != null
    val n = kept[0] ?: return
    AnimatedVisibility(shown, enter = fadeIn(tween(120)), exit = fadeOut(tween(90))) {
        val rise by transition.animateDp({ tween(120) }, label = "rise") { if (it == EnterExitState.Visible) 0.dp else HelpRise }
        val colors = LocalNxColors.current
        val density = LocalDensity.current
        val scroll = rememberScrollState()
        val stepPx = with(density) { HelpScrollStep.toPx() }
        LaunchedEffect(n.scrollSeq) { if (n.scrollSeq > 0) scroll.animateScrollBy(n.scrollDir * stepPx) }
        val hintScope = rememberCoroutineScope()
        var origin by remember { mutableStateOf<Offset?>(null) }
        val anchor = NxHelpAnchor.row?.takeIf { it.label == n.title }
        val hole = origin?.let { o -> anchor?.bounds?.translate(-o) }
        val corner = with(density) { HelpHoleCorner.toPx() }
        Layout(
            content = {
                Column(Modifier.fillMaxWidth().background(colors.accentFaint, RoundedCornerShape(20.dp)).padding(8.dp)) {
                    val card = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.weight(1f, fill = false).fillMaxWidth().clip(card).background(colors.background).border(1.dp, colors.inkLine, card)
                            .nxScrollFade(scroll).verticalScroll(scroll).padding(20.dp),
                    ) {
                        Text(
                            n.title, color = colors.inkDim, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
                            fontSize = anchor?.titleSize ?: nxModalSmall(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        val body = 16.sp * nxTextScale()   // Appearance → Text size
                        Text(n.text, color = colors.main, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal, fontSize = body, lineHeight = body * 1.45f)
                    }
                    // No OK row and no Close (B closes, as anywhere): with a pad, only Scroll under the card while the d-pad scrolls it.
                    if (LocalFooterMode.current == FooterMode.HINTS && scroll.maxValue > 0) {
                        val hints = listOf(Hint(NxButton.DOWN, "Scroll"))
                        NxHintBar(
                            hints, Modifier.padding(top = 4.dp), scrim = Color.Transparent, always = true,
                            onHint = { b -> if (b == NxButton.DOWN) hintScope.launch { if (scroll.canScrollForward) scroll.animateScrollBy(stepPx) else scroll.animateScrollTo(0) } else onClose() },
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }
                .drawBehind {
                    if (hole == null) drawRect(colors.scrimHelp)
                    else clipPath(Path().apply { addRoundRect(RoundRect(hole, CornerRadius(corner))) }, ClipOp.Difference) { drawRect(colors.scrimHelp) }
                }
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        ) { measurables, c ->
            val gap = HelpGap.roundToPx()
            val margin = HelpMargin.roundToPx()
            val w = helpNoteWidth(c.maxWidth, hole, HelpMinWidth.roundToPx(), HelpMaxWidth.roundToPx(), margin)
            val p = measurables.first().measure(c.copy(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = helpNoteRoom(c.maxHeight, hole, gap, margin)))
            val place = helpNotePlace(c.maxWidth, c.maxHeight, hole, w, p.height, gap, margin)
            // Starts [HelpRise] away from the row and rises to it (up from below it, down from above it; up when centred).
            val r = rise.roundToPx()
            layout(c.maxWidth, c.maxHeight) { p.place(place.x, place.y + if (place.below) r else -r) }
        }
    }
}
