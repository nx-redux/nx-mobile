package com.nxredux.mobile.ui.nx

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.MainRow
import com.nxredux.mobile.ui.SETTINGS_ROWS_FADE_ROWS
import com.nxredux.mobile.ui.SETTINGS_ROWS_FADE_STEPS
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.nxredux.mobile.ui.INFO_SEPARATOR
import com.nxredux.mobile.ui.InfoKind

/*
 * A main menu List's rows (List Home, and the Consoles, Collections and Tools tabs in the List style): one continuous
 * list, every row one line at the same pitch. Every name starts [nxListInset] inside the selected pill's edge, the
 * pill's edge on the gutter. Unselected names are the ink at 70% ([NxColors.inkName]); the selected row is the only accent thing: the accent pill,
 * black text. A row's tag sits after its name (a Home game's reason, [homeListRows]: Continue, New or Not started; a
 * console's tag where two share a name). On Home the selected game's facts (Extra info) are a line under the stats
 * ([HomeFactsLine]). A list shorter than its band sits on the band's bottom; a longer one fills it, the selection moving freely until it reaches the third
 * row from an edge ([homeListScroll]), the edges fading by whole rows as Settings' do ([nxRowFade]).
 */

/** The facts line's crossfade ([HomeFactsLine]). */
private const val HOME_LIST_CAPTION_MS = 120

/** List Home's row pitch at Large text (Appearance → Text size scales it). */
private val HOME_LIST_PITCH = 36.dp

/** The tag's gap after its name. */
private val HOME_LIST_TAG_GAP = 12.dp

/** The selected row's tag: the pill's text colour at these. */
private const val HOME_LIST_TAG_FILL_ON_PILL = 0.14f   // role: List Home's tag pill on the accent
private const val HOME_LIST_TEXT_ON_PILL = 0.70f   // role: List Home's tag word on the accent

/**
 * The scroll (px from the content's top) that keeps List Home's [margin] rows past item [selected] in view, from the
 * [current] scroll: unchanged while they show, else the least move that brings them in. [heights]: each item's px
 * (an item not [isRow] is skipped when counting rows); [viewport]: the list's px. Clamped to the content.
 */
internal fun homeListScroll(heights: List<Int>, isRow: (Int) -> Boolean, selected: Int, viewport: Int, current: Int, margin: Int): Int {
    if (heights.isEmpty()) return 0
    val tops = heights.runningFold(0) { a, h -> a + h }
    val total = tops.last()
    val max = (total - viewport).coerceAtLeast(0)
    fun reach(step: Int): Int {
        var i = selected
        var left = margin
        while (left > 0 && i + step in heights.indices) { i += step; if (isRow(i)) left-- }
        return i
    }
    val last = reach(1)
    val first = reach(-1)
    val lo = tops[last + 1] - viewport   // the last kept row's bottom on the viewport's
    val hi = tops[first]                 // the first kept row's top on the viewport's
    val s = if (lo <= hi) current.coerceIn(lo, hi) else tops[selected] - (viewport - heights[selected]) / 2
    return s.coerceIn(0, max)
}

/** The item and px into it at scroll [s] over items [heights] tall. */
internal fun homeListAnchor(heights: List<Int>, s: Int): Pair<Int, Int> {
    var top = 0
    heights.forEachIndexed { i, h ->
        if (s < top + h || i == heights.lastIndex) return i to (s - top)
        top += h
    }
    return 0 to 0
}

/**
 * A row's px for [count] rows of [base] px in a [band] px tall: [base] while they fit; else stretched so the whole rows
 * that fit fill the band (never by more than a row's share of the less-than-a-row left over).
 */
internal fun homeListRowPx(band: Int, count: Int, base: Int): Int =
    if (count * base <= band || base <= 0) base else band / maxOf(1, band / base)

/** The list's px in a [band] px tall: the whole band while the content ([total]) fits it, else whole [pitch] rows, so none is cut at rest. */
internal fun homeListViewport(band: Int, total: Int, pitch: Int): Int =
    if (total <= band) band else (band / maxOf(1, pitch)) * pitch

/**
 * A main menu List's rows ([names]: labels and tags) from [top] (the stats' or the icon row's bottom; the fade parts
 * them) and [gapRows] row pitches of room under it (Home: one, under its stats) down to the box's bottom, the tab row's
 * top; the first row's text on [gutter] + [nxListInset]. Taps select, a tap on the selected row opens ([nxTap]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainListBody(
    names: List<NxRow>, selection: () -> Int, top: Dp, gutter: Dp,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, modifier: Modifier = Modifier,
    gapRows: Int = 0,
    endRoom: Dp = 0.dp,   // room kept clear on the right (a game list's boxed List art): the names stop short of it
    onPageRows: ((Int) -> Unit)? = null,   // told the whole rows it shows (a game list pages LEFT / RIGHT by them)
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val density = LocalDensity.current
    val tapOpens = LocalNxTapOpens.current
    val nameSize = nxListLabel()
    val infoSize = nxOptionsLabelSize(nameSize) * SETTING_DESC_OF_LABEL   // a Settings row's description size
    val basePitch = nxOptionsRowHeight(HOME_LIST_PITCH, LocalNxOptionText.current)
    val pill = basePitch * (1 - 2 * NX_PILL_PAD)   // the pill's height, whatever the rows stretch to
    val basePx = with(density) { basePitch.roundToPx() }
    val sel = selection()
    val state = rememberLazyListState()
    val nameStyle = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = nameSize, lineHeight = nameSize)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val start = top + basePitch * gapRows
        val band = (constraints.maxHeight - with(density) { start.roundToPx() }).coerceAtLeast(basePx)
        // A list longer than the band: its rows a little taller, so whole rows fill it from its top to the tab row.
        val pitchPx = homeListRowPx(band, names.size, basePx)
        val pitch = with(density) { pitchPx.toDp() }
        val heights = List(names.size) { pitchPx }
        val viewport = homeListViewport(band, heights.sum(), pitchPx)
        if (onPageRows != null) {
            val report by rememberUpdatedState(onPageRows)
            val whole = maxOf(1, band / maxOf(1, pitchPx))
            LaunchedEffect(whole) { report(whole) }
        }
        // The selection kept clear of the fade: it moves freely until it reaches the third row from an edge.
        val placed = remember(state) { BooleanArray(1) }
        LaunchedEffect(sel, names.size, viewport) {
            if (sel !in names.indices) return@LaunchedEffect
            snapshotFlow { state.layoutInfo }.first { it.totalItemsCount == names.size && it.visibleItemsInfo.isNotEmpty() }
            val current = state.firstVisibleItemIndex * pitchPx + state.firstVisibleItemScrollOffset
            val target = homeListScroll(heights, { true }, sel, viewport, current, SETTINGS_ROWS_FADE_ROWS)
            val (index, offset) = homeListAnchor(heights, target)
            val animate = placed[0]
            placed[0] = true
            if (target != current) { if (animate) state.animateScrollToItem(index, offset) else state.scrollToItem(index, offset) }
        }
        // A list that fits sits on the band's bottom; a longer one fills it, from right under its top to the tab row.
        val fits = heights.sum() <= band
        LazyColumn(
            Modifier.align(if (fits) Alignment.BottomStart else Alignment.TopStart).padding(top = if (fits) 0.dp else start)
                .fillMaxWidth().height(with(density) { viewport.toDp() }).nxRowEdgeMask(state),
            state = state, verticalArrangement = Arrangement.Bottom, flingBehavior = rememberNxRowSnapFling(state),
        ) {
            itemsIndexed(names) { r, name ->
                val selected = r == sel
                Box(
                    Modifier.fillMaxWidth().height(pitch).nxRowFade(state, r, SETTINGS_ROWS_FADE_STEPS)
                        .combinedClickable(
                            remember { MutableInteractionSource() }, indication = null,
                            onClick = { nxTap(r, selected, tapOpens(r), onSelect, onOpen) },
                            onLongClick = onLongPress?.let { lp -> { lp(r) } },
                        )
                        .padding(start = gutter, end = maxOf(gutter, endRoom), top = (pitch - pill) / 2, bottom = (pitch - pill) / 2),
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .then(if (selected) Modifier.background(colors.accent, RoundedCornerShape(50)) else Modifier)
                            .padding(horizontal = nxListInset()),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        NameAndTag(
                            name = {
                                // A game list's subtitle or tag line after the name, muted and smaller ([NxRow.secondary]).
                                val label = name.secondary?.let { sec ->
                                    buildAnnotatedString {
                                        append(name.label); append("  ")
                                        withStyle(SpanStyle(color = if (selected) colors.onAccentMuted else colors.hint, fontSize = NxTextSizes.secondary(nameSize))) { append(sec) }
                                    }
                                } ?: AnnotatedString(name.label)
                                Text(
                                    label, color = if (selected) colors.onAccent else colors.inkName, style = nameStyle,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = it,
                                    modifier = if (selected) Modifier.basicMarquee() else Modifier,
                                )
                            },
                            tag = name.tag?.let { t -> { HomeListTag(t, selected, infoSize, font) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * [name] (a Text reporting its layout) with [tag] [HOME_LIST_TAG_GAP] after its visible end: the tag is measured
 * first and the name ellipsizes in what is left, so the tag follows the last letter (or the ellipsis), never a fixed x.
 */
@Composable
private fun NameAndTag(name: @Composable ((TextLayoutResult) -> Unit) -> Unit, tag: (@Composable () -> Unit)?) {
    val end = remember { FloatArray(1) }
    Layout(content = { name { end[0] = it.getLineRight(0) }; tag?.invoke() }) { ms, c ->
        val gap = HOME_LIST_TAG_GAP.roundToPx()
        val t = ms.getOrNull(1)?.measure(c.copy(minWidth = 0))
        val room = (c.maxWidth - (t?.let { it.width + gap } ?: 0)).coerceAtLeast(0)
        val n = ms[0].measure(c.copy(minWidth = 0, maxWidth = room))
        val nameEnd = kotlin.math.ceil(end[0]).toInt().coerceIn(0, n.width)
        val w = if (t != null) nameEnd + gap + t.width else n.width
        val h = maxOf(n.height, t?.height ?: 0)
        layout(w, h) {
            n.place(0, (h - n.height) / 2)
            t?.place(nameEnd + gap, (h - t.height) / 2)
        }
    }
}

/** A game's reason tag, [HOME_LIST_TAG_GAP] after its name: an ink-14 pill, its word inkDim; on the pill, the pill's text colour. */
@Composable
private fun HomeListTag(tag: String, selected: Boolean, size: TextUnit, font: androidx.compose.ui.text.font.FontFamily) {
    val colors = LocalNxColors.current
    Box(
        Modifier.background(if (selected) colors.onAccent.copy(alpha = HOME_LIST_TAG_FILL_ON_PILL) else colors.inkFaint, RoundedCornerShape(50))   // role: the tag pill on the accent
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            tag, color = if (selected) colors.onAccent.copy(alpha = HOME_LIST_TEXT_ON_PILL) else colors.inkDim,   // role: the tag word on the accent
            fontFamily = font, fontWeight = FontWeight.Medium, fontSize = size, lineHeight = size * 1.3f, maxLines = 1,
        )
    }
}

/** One run of List Home's facts line ([homeCaptionParts]): a value, a label (a separator too) or the trophy. */
internal data class CaptionPart(val text: String, val value: Boolean, val trophy: Boolean = false)

/** "1h 17m", "31s": a play time ([GameTimeFormat.duration]), a value; anything else in the time segment is its label. */
private val DURATION = Regex("""^\d+[hms]( \d+[ms])*$""")

/**
 * The facts [lines] as the top stats read: values (the play time, "0 of 92", the next goal's name) apart from their
 * labels ("Today", "Next:"), the trophy before the count, segments parted by the separator dot.
 */
internal fun homeCaptionParts(lines: List<InfoLine>): List<CaptionPart> = buildList {
    lines.forEachIndexed { i, line ->
        if (i > 0) add(CaptionPart(INFO_SEPARATOR, value = false))
        when (line.kind) {
            InfoKind.TIME -> {
                val bits = line.text.split(" - ")
                if (bits.size == 2) { add(CaptionPart(bits[0] + " ", value = false)); add(CaptionPart(bits[1], value = true)) }
                else add(CaptionPart(line.text, value = DURATION.matches(line.text)))
            }
            InfoKind.ACHIEVEMENTS -> { add(CaptionPart("", value = false, trophy = true)); add(CaptionPart(line.text, value = true)) }
            InfoKind.COUNT -> add(CaptionPart(line.text, value = true))
            InfoKind.NEXT -> {
                val name = line.text.removePrefix(NEXT_PREFIX)
                if (name != line.text) add(CaptionPart(NEXT_PREFIX, value = false))
                add(CaptionPart(name, value = true))
            }
        }
    }
}

private const val NEXT_PREFIX = "Next: "

/**
 * The selected game's facts: one line in [modifier]'s box at [size], left-aligned, as the stats above it read (values
 * in the ink, labels, the dot and the trophy in their grey: [homeCaptionParts]), ellipsized; a new selection's
 * crossfades in over [HOME_LIST_CAPTION_MS]. Never moves; an app or a tool leaves it empty.
 */
@Composable
internal fun HomeFactsLine(lines: List<InfoLine>, size: TextUnit, modifier: Modifier) {
    val colors = LocalNxColors.current
    val label = colors.hint
    val icon = with(LocalDensity.current) { size.toDp() }
    val info = infoText(lines, label, icon)
    Crossfade(lines, modifier, animationSpec = tween(HOME_LIST_CAPTION_MS), label = "facts") { shown ->
        val text = remember(shown, colors) {
            buildAnnotatedString {
                homeCaptionParts(shown).forEach { p ->
                    if (p.trophy) appendInlineContent(TROPHY, "[trophy]")
                    else withStyle(SpanStyle(color = if (p.value) colors.listText else label)) { append(p.text) }
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(
                text, color = label, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size,
                maxLines = 1, overflow = TextOverflow.Ellipsis, inlineContent = info.inlineContent,
            )
        }
    }
}

/**
 * The selected game's facts under a game list's title ([HomeFactsLine]'s look) in [rows] lines of [line] dp each: one
 * line, or with two (a phone upright, where the title's icons leave the line little room) its next goal ("Next: …") on
 * the second. The room never changes with the selection, so the list under it never moves.
 */
@Composable
internal fun GameFactsLines(lines: List<InfoLine>, size: TextUnit, line: Dp, rows: Int, modifier: Modifier) {
    Column(modifier.height(line * rows).fillMaxWidth()) {
        HomeFactsLine(if (rows > 1) lines.filter { it.kind != InfoKind.NEXT } else lines, size, Modifier.fillMaxWidth().height(line))
        if (rows > 1) HomeFactsLine(lines.filter { it.kind == InfoKind.NEXT }, size, Modifier.fillMaxWidth().height(line))
    }
}
