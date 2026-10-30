package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_button_a
import com.nxredux.mobile.shared.res.nav_button_b
import com.nxredux.mobile.shared.res.nav_button_menu
import com.nxredux.mobile.shared.res.nav_button_select
import com.nxredux.mobile.shared.res.nav_button_start
import com.nxredux.mobile.shared.res.nav_button_x
import com.nxredux.mobile.shared.res.nav_button_y
import com.nxredux.mobile.shared.res.nav_dpad_horizontal
import com.nxredux.mobile.shared.res.nav_dpad_vertical
import com.nxredux.mobile.shared.res.nav_ps_circle
import com.nxredux.mobile.shared.res.nav_ps_cross
import com.nxredux.mobile.shared.res.nav_ps_square
import com.nxredux.mobile.shared.res.nav_ps_triangle
import com.nxredux.mobile.shared.res.nav_trigger_l1
import com.nxredux.mobile.shared.res.nav_trigger_l2
import com.nxredux.mobile.shared.res.nav_trigger_r1
import com.nxredux.mobile.shared.res.nav_trigger_r2
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.TextUnit

data class Hint(val button: NxButton, val label: String, val touch: HintTouch = HintTouch.None)

/** Hint-bar slot of each button: Options (MENU) always leads, then the pad left to right: d-pad, left shoulders, SELECT / START, face buttons Y X B A, right shoulders. */
private fun NxButton.padPosition(): Int = when (this) {
    NxButton.MENU -> -1 // Options leads every bar wherever MENU sits on the pad, so it is always found in the same spot
    NxButton.UP, NxButton.DOWN, NxButton.LEFT, NxButton.RIGHT, NxButton.L3 -> 0
    NxButton.L2 -> 1; NxButton.L1 -> 2
    NxButton.SELECT -> 3; NxButton.HOME -> 4; NxButton.START -> 5
    NxButton.Y -> 6; NxButton.X -> 7; NxButton.B -> 8; NxButton.A -> 9
    NxButton.C_UP, NxButton.C_DOWN, NxButton.C_LEFT, NxButton.C_RIGHT, NxButton.R3 -> 9
    NxButton.R1 -> 10; NxButton.R2 -> 11
}

/** [hints] with Options (MENU) first, then in the order their buttons sit on the pad, so a hint bar reads like the controller in hand; ties keep their given order. */
fun List<Hint>.inPadOrder(): List<Hint> = sortedBy { it.button.padPosition() }

/** Text label for a button (the on-screen pad's default label, [PadProfile.label]); the hint bar renders the glyph art instead. */
fun NxButton.glyph(): String = when (this) {
    NxButton.A -> "A"; NxButton.B -> "B"; NxButton.X -> "X"; NxButton.Y -> "Y"
    NxButton.L1 -> "L1"; NxButton.R1 -> "R1"; NxButton.L2 -> "L2"; NxButton.R2 -> "R2"
    NxButton.START -> "START"; NxButton.SELECT -> "SELECT"; NxButton.MENU -> "MENU"; NxButton.HOME -> "HOME"
    NxButton.L3 -> "L3"; NxButton.R3 -> "R3"
    NxButton.UP -> "▲"; NxButton.DOWN -> "▼"; NxButton.LEFT -> "◀"; NxButton.RIGHT -> "▶"
    NxButton.C_UP -> "▲"; NxButton.C_DOWN -> "▼"; NxButton.C_LEFT -> "◀"; NxButton.C_RIGHT -> "▶"
}

/** The NX Redux `nav_*` glyph art for this button. */
fun NxButton.navDrawable(): DrawableResource = when (this) {
    NxButton.A -> Res.drawable.nav_button_a
    NxButton.B -> Res.drawable.nav_button_b
    NxButton.X -> Res.drawable.nav_button_x
    NxButton.Y -> Res.drawable.nav_button_y
    NxButton.MENU, NxButton.HOME, NxButton.L3, NxButton.R3 -> Res.drawable.nav_button_menu   // no stick-click art: never hinted
    NxButton.START -> Res.drawable.nav_button_start
    NxButton.SELECT -> Res.drawable.nav_button_select
    NxButton.L1 -> Res.drawable.nav_trigger_l1
    NxButton.L2 -> Res.drawable.nav_trigger_l2
    NxButton.R1 -> Res.drawable.nav_trigger_r1
    NxButton.R2 -> Res.drawable.nav_trigger_r2
    NxButton.UP, NxButton.DOWN, NxButton.C_UP, NxButton.C_DOWN -> Res.drawable.nav_dpad_vertical
    NxButton.LEFT, NxButton.RIGHT, NxButton.C_LEFT, NxButton.C_RIGHT -> Res.drawable.nav_dpad_horizontal
}

/** The hint art for a pad symbol: Kenney's PlayStation prompts (CC0), in the `nav_*` art's white-disc style. */
fun PadIcon.navDrawable(): DrawableResource = when (this) {
    PadIcon.PS_CROSS -> Res.drawable.nav_ps_cross
    PadIcon.PS_CIRCLE -> Res.drawable.nav_ps_circle
    PadIcon.PS_TRIANGLE -> Res.drawable.nav_ps_triangle
    PadIcon.PS_SQUARE -> Res.drawable.nav_ps_square
}

/** Fixed hint element height, screen-independent so the hint bar is the same physical size on any display. */
val HINT_GLYPH_SIZE: Dp = 18.dp

/** The transparent margin round the pad art's disc, as a share of the glyph's box (16 px of the 128 px art; the badge's too). */
internal const val NAV_ART_MARGIN = 16f / 128f

@Composable
fun NxButtonGlyph(button: NxButton, size: Dp, modifier: Modifier = Modifier, tint: Color = LocalNxColors.current.main) {
    if (button == NxButton.SELECT || button == NxButton.START) NxShapeGlyph(button == NxButton.START, size, modifier, tint)
    else NxArtGlyph(button.navDrawable(), button.name, size, modifier, tint)
}

/**
 * SELECT's and START's glyph: the other buttons' disc (the middle 3/4 of a [size] box, as the 128 px art's 96 px disc)
 * with a shape cut in the bar's dark where their art had the word, too small to read at a hint's size: START a play
 * triangle ([start]), SELECT a rounded rectangle.
 */
@Composable
private fun NxShapeGlyph(start: Boolean, size: Dp, modifier: Modifier, tint: Color) {
    val cut = LocalNxColors.current.badgeText
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.75f / 2
        val c = center
        drawCircle(tint, r, c)
        if (start) {
            // The triangle's centroid on the disc's centre, its point to the right.
            val h = r * 0.95f; val w = h * 0.87f
            drawPath(Path().apply {
                moveTo(c.x - w / 3, c.y - h / 2); lineTo(c.x + w * 2 / 3, c.y); lineTo(c.x - w / 3, c.y + h / 2); close()
            }, cut)
        } else {
            val w = r * 1.1f; val h = r * 0.62f
            drawRoundRect(cut, Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(h * 0.25f))
        }
    }
}

@Composable
private fun NxArtGlyph(art: DrawableResource, description: String, size: Dp, modifier: Modifier = Modifier, tint: Color = LocalNxColors.current.main) {
    Image(
        painter = painterResource(art),
        contentDescription = description,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        // The art's white disc in the UI ink, its colours (the d-pad's red) scaled alike: a multiply.
        colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
    )
}

/**
 * A pad label that has no `nav_*` art ("C", "Mode") as a badge matching that art: one character sits on a white disc
 * filling the middle 3/4 of a [size] box (as the 128 px art's 96 px disc does); a longer label ("TA", "Opt 1") on a
 * white pill of the disc's height that widens to fit it, at the same text size. The text is in the dark of the bar behind.
 */
@Composable
fun NxBadgeGlyph(text: String, size: Dp, modifier: Modifier = Modifier) {
    val disc = size * 0.75f
    val fontSize = NxTextSizes.badge(disc).pinned()   // ~60% of the disc, whatever the label's length or the font scale
    val label = @Composable {
        Text(text, color = LocalNxColors.current.badgeText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold,
            fontSize = fontSize, lineHeight = fontSize, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
    if (text.length <= 1) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Box(Modifier.size(disc).background(LocalNxColors.current.main, CircleShape), contentAlignment = Alignment.Center) { label() }
        }
    } else {
        // The same margin round the pill as round the disc, so the spacing in the bar matches.
        Box(modifier.height(size).padding(horizontal = (size - disc) / 2), contentAlignment = Alignment.Center) {
            Box(Modifier.height(disc).widthIn(min = disc).background(LocalNxColors.current.main, RoundedCornerShape(50)).padding(horizontal = disc * 0.3f),
                contentAlignment = Alignment.Center) { label() }
        }
    }
}

/** The room between two hints in the bar. */
private val HINT_GAP = 18.dp

/** The hint bar's height: its glyph row and 6 dp above and below ([NxHintBar]'s padding). */
internal val NX_HINT_BAR_ROW_HEIGHT: Dp = HINT_GLYPH_SIZE + 12.dp

/** The hint bar's share of the ground ([NxColors.scrimHints]'s alpha): dark enough for the hints to read over any art, still
 * slightly see-through. The info band's fade starts at this, so it reads as the bar continuing upward. */
val NX_HINT_BAR_ALPHA: Float = NxDefaults.colors.scrimHints.alpha

/**
 * Hint bars below draw nothing and take no room ([NxHintBar]): Settings, where every button does what a console menu's
 * does, unless Layouts → Button hints shows them. A bar that must show (Shortcuts' binding, a pop-up's) passes `always`.
 */
val LocalNxHintBarHidden = staticCompositionLocalOf { false }

/**
 * The hint bars below's side padding: the page's text edge (Settings: its section words',
 * [com.nxredux.mobile.ui.settingsSectionStart]; a List game list: its names'), so the bar lines up with the text above
 * it, its first glyph's disc on that edge. Null: the page inset ([nxPageInset]).
 */
val LocalNxHintBarStart = staticCompositionLocalOf<Dp?> { null }

/** The hint bars below's right padding when it differs from their left ([LocalNxHintBarStart]): a two-pane right pane's. */
val LocalNxHintBarEnd = staticCompositionLocalOf<Dp?> { null }

/**
 * The hint bars below's least height, their hints centred in it: a game list's, the main menu's tab row's
 * ([MENU_TAB_ROW_HEIGHT]), so its words sit where the tab words do. Null: as tall as a glyph and its padding.
 */
val LocalNxHintBarHeight = staticCompositionLocalOf<Dp?> { null }

/** Hint bars below keep their height when empty ([NxHintBar]'s keepHeight): provided by the two-pane Settings only. */
val LocalNxHintBarKeepHeight = staticCompositionLocalOf { false }

/** The Game Switcher's bar's share of the ground ([NxColors.scrimSwitcher]'s alpha), its info band's edge too. */
val NX_SWITCHER_BAR_ALPHA: Float = NxDefaults.colors.scrimSwitcher.alpha

/**
 * [onHint], when given, makes each hint tappable: tapping it sends that hint's button. Each hint's glyph comes from
 * [LocalHintGlyphs]: the standard art, a pad symbol's art, the on-screen pad's label as a badge, or no glyph (the hint's
 * text alone, still tappable, where the on-screen pad does not draw the button). On a touch-only screen
 * ([LocalFooterMode] BUTTONS: no controller, no on-screen pad) it draws nothing and takes no room. [scrim]: the bar's ground.
 * [keepHeight]: an empty bar keeps a glyph's height (the two-pane Settings, where only the focused pane has hints, so
 * both panes' rows stay level). Under [LocalNxHintBarHidden] (Settings) it draws nothing unless [always].
 */
@Composable
fun NxHintBar(
    hints: List<Hint>, modifier: Modifier = Modifier, onHint: ((NxButton) -> Unit)? = null, scrim: Color = LocalNxColors.current.scrimHints,
    keepHeight: Boolean = LocalNxHintBarKeepHeight.current,
    always: Boolean = false,
) {
    val colors = LocalNxColors.current
    val pageInset = nxPageInset()
    // A touch-only screen has no hint bar: its hints are reached by touch ([Hint.touch], taps, long press, back).
    if (LocalFooterMode.current == FooterMode.BUTTONS) return
    if (LocalNxHintBarHidden.current && !always) return   // Settings: no bar unless Button hints shows it
    val glyphs = LocalHintGlyphs.current
    // ~60% of the glyph height; fixed, not screen- or font-scaled, but scaled by Appearance → Text size with the glyph.
    val scale = nxTextScale()
    val labelSize = nxHintLabelSize()
    val glyphSize = HINT_GLYPH_SIZE * scale
    // The hints in pad order, each whole or not at all. Too many for the bar (a phone's portrait width with five hints):
    // A Select and B Back go first ([plainHint]), then those that would still run past the right edge, rightmost first.
    // The bar's height is a glyph's whatever is left out.
    val ordered = hints.inPadOrder()
    Layout(
        content = {
            ordered.map { it to glyphs.glyphFor(it.button) }.forEachIndexed { i, (hint, glyph) ->
                val clickable = if (onHint != null) Modifier.clickable { onHint(hint.button) } else Modifier
                // The first glyph is pulled back by the art's transparent margin, so its disc sits on the text edge above.
                val item = if (i == 0 && glyph != HintGlyph.TextOnly) Modifier.offset(x = -(glyphSize * NAV_ART_MARGIN)).then(clickable) else clickable
                NxHintItem(hint, glyph, glyphSize, labelSize, item)
            }
        },
        modifier = modifier.fillMaxWidth().background(scrim)
            .then(LocalNxHintBarHeight.current?.let { Modifier.heightIn(min = it) } ?: Modifier)
            .padding(
                start = LocalNxHintBarStart.current ?: pageInset.start, end = LocalNxHintBarEnd.current ?: LocalNxHintBarStart.current ?: pageInset.end,
                top = 6.dp, bottom = 6.dp,
            ).let { if (keepHeight) it.heightIn(min = glyphSize) else it },
    ) { measurables, c ->
        val gap = HINT_GAP.roundToPx()
        val all = measurables.map { it.measure(Constraints(maxHeight = c.maxHeight)) }
        // Too many to fit: A Select and B Back go first (every list's A selects and B goes back); the rest as they fit.
        val whole = all.sumOf { it.width } + gap * (all.size - 1).coerceAtLeast(0)
        val kept = if (whole <= c.maxWidth) all.indices.toList() else all.indices.filterNot { plainHint(ordered[it]) }
        val shown = ArrayList<Pair<Placeable, Int>>()
        var end = 0
        for (k in kept) {
            val p = all[k]
            val x = if (shown.isEmpty()) 0 else end + gap
            if (x + p.width > c.maxWidth) break   // this one and every one after it left out
            shown += p to x
            end = x + p.width
        }
        val h = maxOf(shown.maxOfOrNull { it.first.height } ?: 0, c.minHeight)
        layout(hintBarWidth(c.hasBoundedWidth, c.maxWidth, end), h) { shown.forEach { (p, x) -> p.place(x, (h - p.height) / 2) } }
    }
}

/** One hint as a bar draws it: its [glyph] at [glyphSize] (none text-only), then its label in capitals at [labelSize]. */
@Composable
internal fun NxHintItem(hint: Hint, glyph: HintGlyph, glyphSize: Dp, labelSize: TextUnit, modifier: Modifier = Modifier) {
    // At least a glyph's height, so a text-only hint's tap target is as tall as the others.
    Row(modifier.heightIn(min = glyphSize), verticalAlignment = Alignment.CenterVertically) {
        when (glyph) {
            is HintGlyph.Art -> NxButtonGlyph(glyph.button, size = glyphSize)
            is HintGlyph.Badge -> NxBadgeGlyph(glyph.text, size = glyphSize)
            is HintGlyph.Icon -> NxArtGlyph(glyph.icon.navDrawable(), glyph.icon.label, size = glyphSize)
            HintGlyph.TextOnly -> Unit
        }
        if (glyph != HintGlyph.TextOnly) Spacer(Modifier.width(3.dp))
        Text(hint.label.uppercase(), color = LocalNxColors.current.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = labelSize, lineHeight = labelSize, maxLines = 1, softWrap = false)
    }
}

/**
 * A few [hints] in a row, in the given order (most wanted first): as many as fit [room] with [HINT_GAP] between,
 * always the first. The main menu's, beside its bottom tabs (it has no hint bar). A tap sends its button to [onHint].
 */
@Composable
internal fun NxFittedHints(hints: List<Hint>, room: Dp, onHint: ((NxButton) -> Unit)?, modifier: Modifier = Modifier) {
    if (hints.isEmpty()) return
    val glyphs = LocalHintGlyphs.current
    val glyphSize = HINT_GLYPH_SIZE * nxTextScale()
    val labelSize = nxHintLabelSize()
    Layout(
        content = {
            hints.forEach { hint ->
                NxHintItem(hint, glyphs.glyphFor(hint.button), glyphSize, labelSize, if (onHint != null) Modifier.clickable { onHint(hint.button) } else Modifier)
            }
        },
        modifier = modifier,
    ) { measurables, c ->
        val gap = HINT_GAP.roundToPx()
        val roomPx = room.roundToPx()
        val all = measurables.map { it.measure(Constraints(maxHeight = c.maxHeight)) }
        val shown = ArrayList<Pair<Placeable, Int>>()
        var end = 0
        for (p in all) {
            val x = if (shown.isEmpty()) 0 else end + gap
            if (shown.isNotEmpty() && x + p.width > roomPx) break
            shown += p to x
            end = x + p.width
        }
        val h = shown.maxOfOrNull { it.first.height } ?: 0
        layout(end, h) { shown.forEach { (p, x) -> p.place(x, (h - p.height) / 2) } }
    }
}

/** A hint's label size: [NxTextSizes.hint], scaled by Appearance → Text size; the main menu's tab words share it. */
@Composable
internal fun nxHintLabelSize(): TextUnit = NxTextSizes.hint.pinned() * nxTextScale()

/** A hint every list has, the first to go when a bar's hints don't fit ([NxHintBar]): A "Select" and B "Back". */
internal fun plainHint(h: Hint): Boolean =
    h.button == NxButton.A && h.label.equals("Select", true) || h.button == NxButton.B && h.label.equals("Back", true)

/**
 * The hint bar's width: the [maxWidth] it is given, or, asked [bounded] false (an intrinsic width: NxEmptyState sizes
 * its bar to the pills), its [hints]' own, so it never reports an infinite size.
 */
internal fun hintBarWidth(bounded: Boolean, maxWidth: Int, hints: Int): Int = if (bounded) maxWidth else hints
