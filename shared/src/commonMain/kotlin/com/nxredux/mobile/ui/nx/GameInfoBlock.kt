package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.INFO_SEPARATOR
import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.ToolKind
import org.jetbrains.compose.resources.painterResource
import kotlin.math.pow

/**
 * The selected item's info on one line (gameInfoLines' segments in order: the time, the achievements with the trophy,
 * the next goal), in grey, joined by [INFO_SEPARATOR], with a shadow so it reads over art (the band's fade,
 * [GameInfoBand], sits behind). One text, the trophy inline, so the whole line ellipsizes at its end: never wraps and no
 * segment overflows another. [alignEnd]: right-aligned (beside a band's arrows).
 */
/**
 * A game list's achievements panel toggle ([com.nxredux.mobile.ui.GameListFrame]'s trophy action): a tap on the selected
 * game's caption (its name and info, [captionTap]) opens or closes the panel too. Null elsewhere: no tap target.
 */
internal val LocalCaptionTap = compositionLocalOf<(() -> Unit)?> { null }

/** The selected game's caption as a tap target for [LocalCaptionTap], with no ripple; unchanged where none is provided. */
@Composable
internal fun Modifier.captionTap(): Modifier {
    val tap = LocalCaptionTap.current ?: return this
    return clickable(remember { MutableInteractionSource() }, indication = null, onClick = tap)
}

@Composable
private fun GameInfoBlock(lines: List<InfoLine>, alignEnd: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    if (lines.isEmpty()) return
    val colors = LocalNxColors.current
    val base = infoStyle()
    val style = base.copy(color = colors.hint, shadow = Shadow(color = colors.background, blurRadius = 6f), fontSize = base.fontSize * scale)
    val info = infoText(lines, colors.hint, icon = ICON * scale)
    Text(
        info.text, modifier, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, inlineContent = info.inlineContent,
    )
}

internal const val TROPHY = "trophy"   // the inline content id of the achievement segment's icon

/**
 * [infoText]'s text: the segments joined by [INFO_SEPARATOR], the trophy's inline content ([TROPHY]) before the
 * achievements. [next]: the next goal's colour (the white ink), a span on that segment only; the rest (and the separator
 * before it) keep the Text's own colour. Null: no span.
 */
internal fun infoAnnotatedString(lines: List<InfoLine>, next: Color? = null): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { i, line ->
        if (i > 0) append(INFO_SEPARATOR)
        if (line.kind == InfoKind.ACHIEVEMENTS) appendInlineContent(TROPHY, "[trophy]")
        if (line.kind == InfoKind.NEXT && next != null) withStyle(SpanStyle(color = next)) { append(line.text) }
        else append(line.text)
    }
}

/** An info line as one text: [text] with the trophy as inline content ([inlineContent]), for a Text to draw. */
internal class InfoText(val text: AnnotatedString, val inlineContent: Map<String, InlineTextContent>)

/**
 * The info [lines] (gameInfoLines' segments in order) joined by [INFO_SEPARATOR], the achievement segment led by the
 * trophy ([icon], [tint]) and the 6 dp after it, inline, the next goal in white ([infoAnnotatedString]). One builder for
 * the List's band, the switcher's, the Grid's selected tile and the Carousel and Backdrop captions.
 */
@Composable
internal fun infoText(lines: List<InfoLine>, tint: Color, icon: Dp = ICON): InfoText {
    val next = LocalNxColors.current.listText
    val density = LocalDensity.current
    // Kept while the colours and sizes are the same (smooth scrolling at 120 Hz): the trophy's inline content is one
    // instance, so a new line re-lays the text out without recomposing the icon.
    val inline = remember(tint, icon, density) {
        val trophy = with(density) {
            InlineTextContent(Placeholder((icon + 6.dp).toSp(), icon.toSp(), PlaceholderVerticalAlign.TextCenter)) {
                Image(painterResource(toolIcon(ToolKind.RETRO_ACHIEVEMENTS)), null, Modifier.size(icon), colorFilter = ColorFilter.tint(tint))
            }
        }
        mapOf(TROPHY to trophy)
    }
    return remember(lines, next, inline) { InfoText(infoAnnotatedString(lines, next = next), inline) }
}

/** A game caption's info row as drawn: its segments, and the most lines it may wrap to before it ellipsizes. */
internal data class CaptionInfoRow(val lines: List<InfoLine>, val maxLines: Int = 1)

/** The most lines the next goal takes once it has a row of its own. */
internal const val CAPTION_NEXT_LINES = 2

/**
 * A game caption's info [rows] fitted to its width (pure: [widths] are each row measured on one line, px, the trophy
 * included, in [rows]' order, against [avail] px). A row that fits stays one line, as does one without a next goal. A
 * row too wide that ends in its next goal splits: the row less the goal on one line ("n of m" with its trophy), then
 * "Next: <name>" alone on up to [CAPTION_NEXT_LINES] lines, fewer if [spareLines] (the info lines of room left under the
 * caption's reserve) runs out. A row that is the next goal alone, too wide, likewise takes up to [CAPTION_NEXT_LINES]
 * lines, its first in the reserve. With no spare line a row stays one line and ellipsizes. [alwaysSplitNext] (the
 * vertical game-list stacks in the portrait family): a row with a next goal splits so even when it fits.
 */
internal fun fitCaptionRows(
    rows: List<List<InfoLine>>, widths: List<Float>, avail: Float, spareLines: Int, alwaysSplitNext: Boolean = false,
): List<CaptionInfoRow> {
    var spare = spareLines
    return rows.flatMapIndexed { i, row ->
        val next = row.filter { it.kind == InfoKind.NEXT }
        val head = row.filter { it.kind != InfoKind.NEXT }
        val width = widths.getOrElse(i) { 0f }
        // A next goal on a row of its own ([backdropCaptionRows]) too long for the width wraps onto the room left.
        if (head.isEmpty() && next.isNotEmpty() && width > avail && spare > 0) {
            val more = minOf(CAPTION_NEXT_LINES - 1, spare)
            spare -= more
            return@flatMapIndexed listOf(CaptionInfoRow(row, 1 + more))
        }
        if ((width <= avail && !alwaysSplitNext) || next.isEmpty() || head.isEmpty() || spare <= 0) return@flatMapIndexed listOf(CaptionInfoRow(row))
        val lines = minOf(CAPTION_NEXT_LINES, spare)
        spare -= lines
        listOf(CaptionInfoRow(head), CaptionInfoRow(next, lines))
    }
}

/**
 * A game caption's info [rows], centred or as [style] aligns them, in grey with
 * the trophy inline and the next goal in white ([infoText]), [fitCaptionRows]-fitted to [avail] (the caption's width):
 * a trophy row too long for it puts "Next: <name>" on a line of its own, using only the [spare] height left under the
 * caption's reserve, so the row above never moves; [alwaysSplitNext]: whether it fits or not ([fitCaptionRows]).
 * Measured once per row text, width and style.
 */
@Composable
internal fun CaptionInfoRows(rows: List<List<InfoLine>>, style: TextStyle, icon: Dp, avail: Dp, spare: Dp, alwaysSplitNext: Boolean = false) =
    CaptionFittedRows(rememberFittedCaptionRows(rows, style, icon, avail, spare, alwaysSplitNext), style, icon)

/** [CaptionInfoRows]' rows as fitted ([fitCaptionRows]), for a caller that also measures them before drawing. */
@Composable
internal fun rememberFittedCaptionRows(
    rows: List<List<InfoLine>>, style: TextStyle, icon: Dp, avail: Dp, spare: Dp, alwaysSplitNext: Boolean = false,
): List<CaptionInfoRow> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val merged = LocalTextStyle.current.merge(style)
    return remember(rows, merged, icon, avail, spare, density, alwaysSplitNext) {
        with(density) {
            val trophy = (icon + 6.dp).toPx()   // infoText's placeholder: the icon and the 6 dp after it
            val widths = rows.map { row ->
                val text = row.joinToString(INFO_SEPARATOR) { it.text }
                measurer.measure(text, merged, maxLines = 1, softWrap = false).size.width +
                    if (row.any { it.kind == InfoKind.ACHIEVEMENTS }) trophy else 0f
            }
            val lineH = measurer.measure("A", merged, maxLines = 1).size.height.coerceAtLeast(1)
            fitCaptionRows(rows, widths, avail.toPx(), (spare.toPx() / lineH).toInt(), alwaysSplitNext)
        }
    }
}

/** [fitted] caption info rows ([rememberFittedCaptionRows]) drawn one under another, as [CaptionInfoRows] draws them. */
@Composable
internal fun CaptionFittedRows(fitted: List<CaptionInfoRow>, style: TextStyle, icon: Dp) {
    val colors = LocalNxColors.current
    for (row in fitted) {
        val info = infoText(row.lines, colors.hint, icon = icon)
        Text(
            info.text, color = colors.hint, maxLines = row.maxLines, overflow = TextOverflow.Ellipsis,
            inlineContent = info.inlineContent, style = style,
        )
    }
}

/** The info lines' text style, without colour or shadow (neither changes the line height [infoLineHeight] measures). */
@Composable
private fun infoStyle() = TextStyle(fontFamily = LocalNxFont.current, fontSize = NxTextSizes.gameInfo)

private val ICON = 12.dp   // the achievement segment's trophy

/** The List's info band padding above and below its one line. */
private val BAND_PAD = 4.dp

/** How far the List's info band at most reaches into the hint bar's empty top (12 dp, as on the mockup's 44 dp bar). */
private const val BAND_MAX_OVERLAP = 12f

/** The solid stretch at the bottom of the List band's fill, down to the hint bar's edge. */
private val BAND_SOLID = 2.dp

/**
 * The List's info band, in dp: [height] (the line plus [BAND_PAD] each side), how far its bottom reaches into the hint
 * bar ([overlap]), and what the list keeps clear above the bar ([reserve], the height less the overlap).
 */
internal data class InfoBand(val height: Float, val overlap: Float, val reserve: Float)

/**
 * The List band for a [lineDp] line over a hint bar whose glyph ink starts [inkTopDp] below its top ([NxHintInkTop]):
 * the overlap is [BAND_MAX_OVERLAP], but never past the ink. The app's bar is about 30 dp (the mockup's 44), its ink
 * about 8.25 dp down, so a flat 12 would put the band's text on the glyph discs. [touch] (no bar at all) reaches into
 * nothing: zero overlap, the whole height reserved.
 */
internal fun nxInfoBandGeometry(lineDp: Float, inkTopDp: Float, touch: Boolean = false): InfoBand {
    val height = lineDp + 2 * BAND_PAD.value
    val overlap = if (touch) 0f else minOf(BAND_MAX_OVERLAP, inkTopDp)
    return InfoBand(height, overlap, height - overlap)
}

/**
 * The info band's fade of [ground] over the element's bounds, no horizontal fade and no hard box: [edgeAlpha] held solid
 * over the bottom [hold] (the stop follows the measured height), then fading linearly to nothing at the top edge.
 */
internal fun Modifier.nxVerticalFade(ground: Color, edgeAlpha: Float, hold: Dp): Modifier = drawWithCache {
    val edge = ground.copy(alpha = edgeAlpha)   // role: a fade gradient's stops
    val solid = if (size.height > 0f) (hold.toPx() / size.height).coerceIn(0f, 1f) else 0f
    val brush = Brush.verticalGradient(0f to ground.copy(alpha = 0f), 1f - solid to edge, 1f to edge)   // role: a fade gradient's stops
    onDrawBehind { drawRect(brush) }
}

/**
 * An eased fade of [ground] from [edgeAlpha] at one edge (the bottom, or the top with [fromTop]) to nothing at the
 * other, with no flat stretch: [easedFadeAlpha] over [EASE_STOPS] stops, enough that the linear pieces don't show. It
 * ends at 0 with zero slope, so on a bright picture there is no line where it runs out; a higher [power] stays dark
 * further from the edge (the switcher's title, two lines tall, keeps about 0.75 of the bar at its subtitle's bottom at
 * 3.5; the info band's 2 keeps that at its text's top).
 */
internal fun Modifier.nxEasedFade(ground: Color, edgeAlpha: Float, fromTop: Boolean = false, power: Float = 2f): Modifier = background(
    Brush.verticalGradient(*Array(EASE_STOPS) { i ->
        val y = i / (EASE_STOPS - 1f)          // 0 at the top
        val t = if (fromTop) y else 1f - y      // 0 at the edge
        y to ground.copy(alpha = easedFadeAlpha(edgeAlpha, t, power))   // role: a fade gradient's stops
    }),
)

/**
 * The eased fade's alpha at [t] (0 at the dark edge, 1 where it has run out): edge × (1 − t^[power])^[EASE_TAIL]. The
 * cubed tail gives it zero slope (and zero curvature) at t = 1, so it meets the picture without a Mach band.
 */
internal fun easedFadeAlpha(edgeAlpha: Float, t: Float, power: Float): Float =
    edgeAlpha * (1f - t.coerceIn(0f, 1f).pow(power)).pow(EASE_TAIL)

internal const val EASE_STOPS = 20
private const val EASE_TAIL = 3f

/** How far the switcher's eased fades reach past their text: above its info line and below its title's subtitle. */
internal val NX_EASED_FADE_REACH: Dp = 64.dp

/** How far the main menu's top band ([nxTopBandFade]) reaches below its tab row (or a game list's title row). */
internal val NX_TOP_BAND_REACH: Dp = 48.dp

/**
 * A top band's ground (the switcher's title, the main menu's tab row): [ground] at [NX_SWITCHER_BAR_ALPHA] at the top
 * edge, eased ([nxEasedFade], power 3.5, dark across two lines) to clear at the bottom. One place, so the two match.
 */
internal fun Modifier.nxTopBandFade(ground: Color): Modifier = nxEasedFade(ground, NX_SWITCHER_BAR_ALPHA, fromTop = true, power = TOP_BAND_POWER)

internal const val TOP_BAND_POWER = 3.5f

/** The room a band keeps for the scroll arrow pair, so the text never moves: two arrows and the 1 dp between them. */
private val ARROW_SLOT = NxScrollArrowWidth * 2 + 1.dp
private val ARROW_GAP = 12.dp   // between the arrows' room and the text

/**
 * The info line's height: the info style's text (merged into LocalTextStyle, as Text does), or the trophy when taller.
 * Measured, so it follows the font and the text scale.
 */
@Composable
private fun infoLineHeight(): Dp {
    val line = rememberTextMeasurer().measure("0", LocalTextStyle.current.merge(infoStyle()), maxLines = 1).size.height
    return maxOf(with(LocalDensity.current) { line.toDp() }, ICON)
}

/**
 * The selected game's [lines] on the switcher's band, the full width of the screen, sitting directly on its hint bar: an
 * eased fade ([nxEasedFade]) from [edgeAlpha] (the bar's alpha, so it reads as the bar continuing) at the bottom edge
 * to nothing at the top, with no solid stretch, over the text and [fadeReach] above it. One line tall, the text
 * left-aligned 16 dp in.
 */
@Composable
fun GameInfoBand(lines: List<InfoLine>, modifier: Modifier = Modifier, edgeAlpha: Float = NX_HINT_BAR_ALPHA, fadeReach: Dp = NX_EASED_FADE_REACH) {
    if (lines.isEmpty()) return
    val colors = LocalNxColors.current
    Box(
        modifier.fillMaxWidth().nxEasedFade(colors.background, edgeAlpha).padding(top = fadeReach, bottom = 2.dp)
            .padding(horizontal = 16.dp).height(infoLineHeight()),
        contentAlignment = Alignment.CenterStart,
    ) { GameInfoBlock(lines, alignEnd = false) }
}

/**
 * The List's info band ([InfoBand] [band], fixed just above the hints, its bottom [InfoBand.overlap] inside the bar):
 * the selected item's [lines], one line centred in it ([BAND_PAD] above and below). Its fill is the background, fading
 * linearly in from nothing at the band's top to [NX_HINT_BAR_ALPHA], held solid over the last [BAND_SOLID] down to the
 * bar's edge, and nothing over the bar itself (its own fill shows). [arrows] (the list's scroll arrow pair): centred on
 * the line, packed flush left at [arrowStart] (where row labels start: [nxListInset], or the main menu's gutter), in a
 * fixed room so the text never moves. The text is right-aligned at the screen gutter (16 dp portrait, 24 dp landscape)
 * and may use all the width back to the arrows, ellipsizing only there. "N games" stays the info grey.
 * [shade] false (the main menu's Consoles List) draws no fill at all, neither the fade nor the solid one of
 * [BandGround.NONE]: the arrows (and any [lines]) sit on the page as it is. The band's height is unchanged, so rows never move.
 */
@Composable
private fun ListInfoBand(lines: List<InfoLine>, band: InfoBand, arrows: @Composable () -> Unit, arrowStart: Dp, ground: BandGround, shade: Boolean) {
    val colors = LocalNxColors.current
    val gutter = if (LocalPortrait.current) 16.dp else 24.dp
    Box(Modifier.fillMaxWidth().height(band.height.dp)) {
        // With no bar under it ([BandGround.NONE]) the fill runs to the band's bottom, held solid from the text's top
        // down, or the text's lower half would sit on the bare page (as NX Redux's infoband.c).
        if (!shade) Unit
        else if (ground == BandGround.NONE) Box(Modifier.fillMaxSize().nxVerticalFade(colors.background, NX_HINT_BAR_ALPHA, hold = band.height.dp - BAND_PAD))
        else Box(Modifier.fillMaxWidth().height((band.height - band.overlap).dp).nxVerticalFade(colors.background, NX_HINT_BAR_ALPHA, hold = BAND_SOLID))
        Row(
            Modifier.fillMaxSize().padding(start = arrowStart, end = gutter, top = BAND_PAD, bottom = BAND_PAD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(ARROW_SLOT), contentAlignment = Alignment.CenterStart) { arrows() }   // a lone arrow takes the first place
            Spacer(Modifier.width(ARROW_GAP))
            // The info on its own line in the bar's row instead ([BarInfoLine]): the band keeps the arrows alone.
            if (ground != BandGround.SHADE_WITH_INFO) Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { GameInfoBlock(lines, alignEnd = true) }
        }
    }
}

/**
 * What lies under the List band's bottom, in the hint bar's row: [BAR] the hint bar (hints shown), [NONE] nothing
 * (hints hidden, the main menu: no shade), [SHADE_WITH_INFO] the bar's shade alone with the selected game's info moved
 * down into it ([BarInfoLine]; hints hidden, a game list).
 */
internal enum class BandGround { BAR, NONE, SHADE_WITH_INFO }

/**
 * Button hints hidden, a List game list: the selected game's [lines] in the hint bar's empty row (its shade under it),
 * vertically centred, right-aligned at the screen gutter and free to use the width back to [start] (the band's arrows'
 * x), as NX Redux's info line on its own row (infoband.c info_top).
 */
@Composable
private fun BarInfoLine(lines: List<InfoLine>, start: Dp) {
    val gutter = if (LocalPortrait.current) 16.dp else 24.dp
    Box(Modifier.fillMaxSize().padding(start = start, end = gutter), contentAlignment = Alignment.CenterEnd) {
        GameInfoBlock(lines, alignEnd = true, Modifier.captionTap(), scale = nxTextScale())   // Appearance → Text size
    }
}

/**
 * Button hints hidden, the Game Switcher's Full screen: the selected game's [lines] in the hint bar's empty row (its
 * scrim laid by the caller), left-aligned at the screen gutter and vertically centred (NX Redux PR #218's drawInfo).
 */
@Composable
internal fun SwitcherBarInfoLine(lines: List<InfoLine>, modifier: Modifier) {
    Box(modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { GameInfoBlock(lines, alignEnd = false) }
}

/**
 * Places the selected item's [info] around a List-style [scaffold] (an NxScreenScaffold), on a one-line band
 * ([nxInfoBandGeometry]) fixed just above the hints, reaching into the bar's empty top and holding the list's scroll
 * arrows (the [NxHintsBand] handed to [scaffold]). The band has one height everywhere, so the list's rows never move;
 * the list ends at the band's [InfoBand.reserve] in both orientations. [info] is read only inside the band, so a new
 * selection's info recomposes the band alone. [textStart]: where the list's row labels start (the band's arrows start
 * there too). [ground]: what is under the band's bottom ([BandGround]); with [BandGround.SHADE_WITH_INFO] the info goes
 * to the band's [NxHintsBand.barInfo], drawn in the bar's row.
 * [shade] false (the main menu's Consoles List, decided by its caller) leaves the band unfilled; its
 * geometry and reserve stay the same, so the list's rows and the arrows keep their places.
 */
@Composable
internal fun GameInfoPlacement(
    info: () -> List<InfoLine>, modifier: Modifier, textStart: Dp = nxListInset(), ground: BandGround = BandGround.BAR, shade: Boolean = true,
    scaffold: @Composable (scaffoldModifier: Modifier, band: NxHintsBand) -> Unit,
) {
    // Touch only, the band reaches into no bar, unless the bar's row is kept for the info ([BandGround.SHADE_WITH_INFO]).
    val geometry = nxInfoBandGeometry(infoLineHeight().value, NxHintInkTop.value, touch = touchOnly() && ground != BandGround.SHADE_WITH_INFO)
    val barInfo: (@Composable () -> Unit)? = if (ground == BandGround.SHADE_WITH_INFO) { { BarInfoLine(info(), textStart) } } else null
    val band = NxHintsBand(reserve = geometry.reserve.dp, overlap = geometry.overlap.dp, barInfo = barInfo) { arrows ->
        ListInfoBand(info(), geometry, arrows, arrowStart = textStart, ground = ground, shade = shade)
    }
    scaffold(modifier, band)
}
