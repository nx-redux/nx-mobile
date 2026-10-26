package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.MINIMAL_LEAVE_MS
import com.nxredux.mobile.ui.NavMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The Minimal layout (Layouts → a tab's or the game lists' layout: Minimal): big names only, no art. The selected name is
 * solid in the accent and a little larger, one row under the body's top; the others are its outline in fade steps
 * ([minimalAlpha]), running on behind the title or tab row and the hints to the screen's edges. Home adds its stats
 * strip and the tags of its rows ([HomeMinimalScreen]); the tabs and the game lists show the names alone. A on the
 * selection grows its band over the screen before it opens ([MinimalOpenBand], AppState's minimalOpening). Between two
 * Minimal pages a folder morphs ([MinimalFlight]): its name flies into the list's title as the list's names rise in, and
 * B drops the title back into its row as the other names fade in.
 */

/** The glide of the names to a new selection (the d-pad's; a swipe settles on its own spring). */
private const val MINIMAL_MOVE_MS = 340

/** A jump of more than this many names (a wrap from the last to the first) snaps instead of gliding through them all. */
internal const val MINIMAL_SNAP_STEPS = 8

/** An unselected name's outline: this share of the accent, [MINIMAL_STROKE] dp wide; the fade steps ([minimalAlpha]) dim it further. */
private const val MINIMAL_OUTLINE_ALPHA = .6f
private const val MINIMAL_STROKE = 1f

/** The selected name grows by this much, from its left edge. */
private const val MINIMAL_SELECTED_SCALE = 1.04f

/**
 * A name's tag (Home's: Continue, New, Not started, Pick up again, Tool) at the end of its name, as its last word: on the
 * name's last line when it fits there, else wrapping to the next with the words. A solid pill in the Secondary accent
 * with its text in the Accent, a size smaller: [MINIMAL_TAG_SP] text, [MINIMAL_TAG_H] tall. Shown on
 * the selected name only; the other names keep its room (blank), so a name's lines don't change when it is selected.
 */
private const val MINIMAL_TAG_SP = 12f
private const val MINIMAL_TAG_H = 20f
private const val MINIMAL_TAG_INLINE = "tag"

/** A name too long for its row wraps at its words onto at most this many lines (an ellipsis on the last); each name takes the rows its lines need. */
private const val MINIMAL_MAX_LINES = 3

/** A name behind the strip, the tab row or the title keeps this share of its fade step, so their text reads over it. */
private const val MINIMAL_UNDER_STRIP = .3f

/** A swipe's release carries on this long at the finger's speed to pick the name it lands on. */
private const val MINIMAL_THROW_S = .25f

/** The open band: after the other names fade out ([MINIMAL_LEAVE_MS]), it grows to the whole screen, going black over its last [OPEN_DARK_FROM]. */
private const val OPEN_GROW_MS = 350
private const val OPEN_DARK_FROM = .4f
private val OpenEasing = CubicBezierEasing(.65f, 0f, .35f, 1f)
/** The open band's colour: this share of the accent over the background, a deep shade (dark grey for White) that doesn't flash. */
private const val OPEN_BAND_TINT = .28f

/** A folder's morph: the name's (or the title's) flight, and the new list's names rising in after a moment, one after another. */
internal const val MINIMAL_MORPH_MS = 280
internal val MinimalMorphEasing = CubicBezierEasing(.65f, 0f, .35f, 1f)
private const val MINIMAL_RISE_DELAY_MS = 80
private const val MINIMAL_RISE_MS = 220
private const val MINIMAL_RISE_STAGGER_MS = 30
private const val MINIMAL_RISE_STAGGERED = 8   // names past this many from the top rise with the last
private const val MINIMAL_RISE_DP = 24f
private const val MINIMAL_RISE_END = (MINIMAL_RISE_DELAY_MS + MINIMAL_RISE_STAGGER_MS * MINIMAL_RISE_STAGGERED + MINIMAL_RISE_MS).toFloat()

/** A Minimal name's size in dp, the same on every screen (Appearance → Text size scales it): a larger screen shows more names. */
private const val MINIMAL_NAME_DP = 38f

/**
 * A name's opacity by its distance from the selection. Below it the next name is at [MINIMAL_NEXT_ALPHA], and from there
 * the names fade smoothly with how far down the screen they sit ([minimalAlpha]'s depth) to [MINIMAL_BOTTOM_ALPHA] at
 * its bottom edge: a taller screen spreads the fade over more names, so each steps down less.
 * Above it fainter, in steps: 0.3, 0.22, then [MENU_FAR_ALPHA], so the list reads downwards from the selection.
 */
private const val MINIMAL_NEXT_ALPHA = .5f
private const val MINIMAL_BOTTOM_ALPHA = 0f
private val MINIMAL_FADE_ABOVE = floatArrayOf(1f, .3f, .22f, MENU_FAR_ALPHA)

/**
 * A name's opacity [distance] names from the shown position (negative above it), linear between the steps so it stays
 * smooth while the names slide. Below, [depth]: how far down it sits from the row under the selection to the screen's
 * bottom edge (0 to 1).
 */
internal fun minimalAlpha(distance: Float, depth: Float = 0f): Float {
    if (distance >= 0f) {
        val near = 1f + (MINIMAL_NEXT_ALPHA - 1f) * distance.coerceAtMost(1f)
        return near * (1f + (MINIMAL_BOTTOM_ALPHA / MINIMAL_NEXT_ALPHA - 1f) * depth.coerceIn(0f, 1f))
    }
    val steps = MINIMAL_FADE_ABOVE
    val d = -distance
    val last = steps.size - 1
    if (d >= last) return steps[last]
    val i = d.toInt()
    return steps[i] + (steps[i + 1] - steps[i]) * (d - i)
}

/**
 * The names composed around the shown position [pos] of [count]: [above] before it and [below] after it (each name is at
 * least one row tall, so a row count of the screen above and below the selection's place covers what shows), clamped
 * to the list. A list of thousands composes only these.
 */
internal fun minimalWindow(pos: Float, count: Int, above: Int, below: Int): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val at = floor(pos).toInt()
    return (at - above).coerceIn(0, count - 1)..(at + 1 + below).coerceIn(0, count - 1)
}

/**
 * Where the open band starts: the selected name as last laid out (its coordinates, text style and label), reported by
 * [MinimalNames] and read when the band first composes ([MinimalOpenOverlay]).
 */
internal class MinimalSelected {
    var coords: LayoutCoordinates? = null
    var style: TextStyle? = null
    var label: String? = null
}

/** A name or a title as its page last laid it out, its top left [at] in the window drawn at [scale]: where a morph starts. */
internal class MorphText(val text: String, val at: Offset, val width: Float, val scale: Float, val style: TextStyle)

/** [coords]' top left in the window, and the scale it is drawn at there (its layers'). */
internal fun morphPlace(coords: LayoutCoordinates): Pair<Offset, Float> {
    val at = coords.localToWindow(Offset.Zero)
    val w = coords.size.width.toFloat()
    return at to if (w > 0f) (coords.localToWindow(Offset(w, 0f)).x - at.x) / w else 1f
}

/** What the page showing last laid out: its selected Minimal [name], and a game list's [title]. The next page's morph starts there. */
internal class MinimalMorph {
    var name: MorphText? = null
    var title: MorphText? = null
}

/** The page drawn: the shared [morph], whether it is the one showing ([live]: not sliding out) and how it came in ([entry]). */
internal class MinimalPage(val morph: MinimalMorph, val live: State<Boolean>, val entry: NavMotion) {
    private val played = mutableSetOf<String>()

    /**
     * True the first time [part] of the page's entry ([motion]) asks, false after: the morph plays as the page comes in,
     * not again when its list is composed anew on it (a tab switch, a layout change).
     */
    fun arriving(motion: NavMotion, part: String): Boolean = entry == motion && played.add(part)
}

internal val LocalMinimalPage = compositionLocalOf<MinimalPage?> { null }

/** The names' text style at the current text size and font (the selection's, solid in the accent). */
@Composable
private fun minimalNameStyle(): TextStyle {
    val density = LocalDensity.current
    val size = MINIMAL_NAME_DP * nxTextScale()
    return TextStyle(
        fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold, fontSize = (size / density.fontScale).sp,
        letterSpacing = (-0.02).em, lineHeight = ((size + 2f) / density.fontScale).sp, color = LocalNxColors.current.accent, drawStyle = Fill,
    )
}

/**
 * A page body in the Minimal layout: [names] ([NxRow.label], and an [NxRow.tag] on Home's rows; nothing else shows) over
 * the whole box, the hint bar at the bottom ([NxScreenScaffold]). The selection sits a third of the way down the box, at
 * least one row under the body's top ([top] down the box: Home's strip room), the names running on above and below it
 * ([minimalAnchor]). A drag moves the names with the finger and,
 * released, they settle on the name the throw lands on, selected ([axisSwipe]). A tap selects, a tap on the selected name
 * opens it (every name, so the open band starts at the selection's place; [tapOpens] may open on one tap), a long press calls [onLongPress]. The d-pad
 * lives in AppState (UP / DOWN wrap on a fresh press, as a List). [overlay] draws over the names (Home's strip). Draw it
 * under the tab row or title (zIndex), so the names run on under it. [leaving]: a row is opening (AppState's minimalOpening),
 * the other names fading out. Come in by a morph ([LocalMinimalPage]), a list's names rise in ([NavMotion.MINIMAL_FORWARD])
 * or the last list's title drops into the selected row as the others fade in ([NavMotion.MINIMAL_BACK]).
 */
@Composable
internal fun MinimalScreen(
    names: List<NxRow>, selection: () -> Int, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, onHint: ((NxButton) -> Unit)?,
    selected: MinimalSelected, modifier: Modifier = Modifier, top: Float = 0f, hideHints: Boolean = false,
    layoutKey: Any? = null, leaving: Boolean = false, tapOpens: (Int) -> Boolean = NxTapNeverOpens,
    onSwipeSelect: ((Int) -> Unit)? = null,   // a swipe's release: moves the highlight only (default: onSelect)
    onPageRows: ((Int) -> Unit)? = null,   // the names one screen shows under the selection (a game list's LEFT / RIGHT page)
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val gutter = if (LocalPortrait.current) 16.dp else 24.dp
    val page = LocalMinimalPage.current
    // How this page came in, read once: the title dropping back (from the list's), or the names rising in.
    val returning = remember { page?.arriving(NavMotion.MINIMAL_BACK, "back") == true }
    val back = remember { page?.morph?.title.takeIf { returning } }
    val morph = remember { Animatable(if (returning) 0f else 1f) }
    LaunchedEffect(morph) { if (morph.value < 1f) morph.animateTo(1f, tween(MINIMAL_MORPH_MS, easing = MinimalMorphEasing)) }
    val rise = remember { Animatable(if (page?.arriving(NavMotion.MINIMAL_FORWARD, "rise") == true) 0f else MINIMAL_RISE_END) }
    LaunchedEffect(rise) { if (rise.value < MINIMAL_RISE_END) rise.animateTo(MINIMAL_RISE_END, tween(MINIMAL_RISE_END.toInt(), easing = LinearEasing)) }
    val leave = remember { Animatable(1f) }
    LaunchedEffect(leaving) { if (leaving) leave.animateTo(0f, tween(MINIMAL_LEAVE_MS.toInt())) else leave.snapTo(1f) }
    val motion = remember {
        MinimalMotion(
            others = { leave.value * morph.value },
            // the selected name waits under the title's flight
            selection = { if (back != null && morph.value < 1f) 0f else 1f },
            rise = { k ->
                val t = rise.value - MINIMAL_RISE_DELAY_MS - MINIMAL_RISE_STAGGER_MS * k.coerceIn(0, MINIMAL_RISE_STAGGERED)
                EaseOutCubic.transform((t / MINIMAL_RISE_MS).coerceIn(0f, 1f))
            },
        )
    }
    // The body between [top] and the hint bar, in this box: where the selection is placed.
    val box = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var area by remember { mutableStateOf<Rect?>(null) }
    var boxH by remember { mutableFloatStateOf(0f) }
    Box(modifier.fillMaxSize().onGloballyPositioned { box[0] = it; boxH = it.size.height.toFloat() }) {
        area?.let { a ->
            CompositionLocalProvider(LocalNxTapOpens provides tapOpens) {
                MinimalNames(names, a, boxH, selection, onSelect, onOpen, onLongPress, selected, gutter, layoutKey, motion, page, onSwipeSelect, onPageRows)
            }
        }
        // B from a Minimal list: its title flies down into the selected row.
        if (back != null && morph.value < 1f) {
            val label = names.getOrNull(selection().coerceIn(0, (names.size - 1).coerceAtLeast(0)))?.label
            if (label != null) MinimalFlight(back, label, minimalNameStyle(), { selected.coords?.takeIf { it.isAttached }?.let(::morphPlace) }, { morph.value }, { box[0] })
        }
        Column(Modifier.fillMaxSize()) {
            if (top > 0f) Spacer(Modifier.height(top.dp))
            NxScreenScaffold(
                rows = emptyList(), selectedIndex = 0, hints = hints, onSelect = onSelect, onOpen = onOpen,
                modifier = Modifier.weight(1f), onHint = onHint, hideHints = hideHints,
                grid = { m ->
                    Box(m.fillMaxSize().onGloballyPositioned { c -> box[0]?.takeIf { it.isAttached }?.let { b -> area = b.localBoundingBoxOf(c, clipBounds = false) } })
                },
            )
        }
        overlay()
    }
}

/** How the names show while a page opens or comes in: the others' and the selection's share of alpha, and name [rise]'s (k from the top) way in. */
private class MinimalMotion(val others: () -> Float, val selection: () -> Float, val rise: (k: Int) -> Float)

/**
 * The names' text and lines at one row width and style, built and measured only for the names that come into view
 * (cached): a list of thousands measures the few on screen. A name with a tag that doesn't fit after its last line's
 * words takes that line's last word down with the tag ("Metroid - Zero" / "Mission [Continue]"), so the tag never sits
 * alone on a line under a full one.
 */
private class MinimalTexts(
    private val names: List<NxRow>, private val measurer: TextMeasurer, private val solid: TextStyle, private val rowW: Int,
    private val tagWidths: Map<String, Int>, private val placeholders: Map<String, Placeholder>, private val pitchPx: Float,
) {
    private val texts = arrayOfNulls<AnnotatedString>(names.size)
    private val lines = IntArray(names.size)   // 0: not measured yet
    private val space by lazy { measurer.measure("a a", solid).size.width - measurer.measure("aa", solid).size.width }

    fun text(i: Int): AnnotatedString = texts[i] ?: build(names[i]).also { texts[i] = it }

    fun lines(i: Int): Int {
        if (lines[i] > 0) return lines[i]
        val tag = names[i].tag
        val t = text(i)
        val ranges = if (tag == null) emptyList() else listOf(AnnotatedString.Range(placeholders.getValue(tag), t.length - tag.length, t.length))
        return measurer.measure(t, solid, maxLines = MINIMAL_MAX_LINES, placeholders = ranges, constraints = Constraints(maxWidth = rowW)).lineCount
            .coerceAtLeast(1).also { lines[i] = it }
    }

    /** Name [i]'s top less name [base]'s, in the stacked names. */
    fun relTop(i: Int, base: Int): Float {
        var t = 0f
        if (i > base) for (j in base until i) t += lines(j) * pitchPx
        else for (j in i until base) t -= lines(j) * pitchPx
        return t
    }

    /**
     * The shown position [p]'s top less its name's (the one it is on or past): between two names it reads between their
     * tops, so a step glides by the names' own heights; pulled past either end, a row a step.
     */
    fun shift(p: Float, count: Int): Float {
        val f = floor(p).toInt()
        return when {
            f < 0 -> p * pitchPx
            f >= count - 1 -> (p - (count - 1)) * pitchPx
            else -> lines(f) * pitchPx * (p - f)
        }
    }

    private fun build(row: NxRow): AnnotatedString {
        val tag = row.tag ?: return minimalText(row.label, null)
        val lay = measurer.measure(row.label, solid, maxLines = MINIMAL_MAX_LINES, constraints = Constraints(maxWidth = rowW))
        val last = lay.lineCount - 1
        val start = lay.getLineStart(last)
        val lineText = row.label.substring(start, lay.getLineEnd(last)).trimEnd()
        val fits = lay.getLineRight(last) - lay.getLineLeft(last) + space + tagWidths.getValue(tag) <= rowW
        val cut = lineText.lastIndexOf(' ')
        val label = if (fits || cut <= 0 || lay.lineCount >= MINIMAL_MAX_LINES) row.label
            else row.label.substring(0, start + cut) + "\n" + row.label.substring(start + cut + 1)
        return minimalText(label, tag)
    }
}

/**
 * The names over the whole box ([boxH] tall), placed so the shown position sits one row down [area] and drawn past the
 * box's edges unclipped. The position eases to the selection (a far jump snaps: [MINIMAL_SNAP_STEPS]) and follows a
 * finger's drag. Only the names in view are composed ([minimalWindow]), keyed by recycled slots ([ItemSlots]).
 */
/**
 * Where a Minimal page's selected name's top sits, px down its box ([boxH] tall): a third of the way down, so the list
 * starts in the upper third rather than right under Home's strip or the title; never higher than one row ([pitchPx])
 * under the body's top [bodyTop].
 */
internal fun minimalAnchor(bodyTop: Float, pitchPx: Float, boxH: Float): Float = max(bodyTop + pitchPx, boxH / 3f)

@Composable
private fun MinimalNames(
    names: List<NxRow>, area: Rect, boxH: Float, selection: () -> Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit,
    onLongPress: ((Int) -> Unit)?, selected: MinimalSelected, gutter: Dp, layoutKey: Any?,
    motion: MinimalMotion, page: MinimalPage?, onSwipeSelect: ((Int) -> Unit)?, onPageRows: ((Int) -> Unit)?,
) {
    val count = names.size
    if (count == 0) return
    val colors = LocalNxColors.current
    val density = LocalDensity.current
    val font = LocalNxFont.current
    val size = MINIMAL_NAME_DP * nxTextScale()
    val pitch = size + 2f
    val pitchPx = with(density) { pitch.dp.toPx() }
    // The selection a third of the way down the box, the names above it running on up behind the strip or title, faded
    // further there; never higher than one row under the body's top (a short landscape box under a tall strip).
    val anchorPx = minimalAnchor(area.top, pitchPx, boxH)
    // A page: the one-line names from the selection down to the body's bottom.
    if (onPageRows != null) {
        val rows = ((area.bottom - anchorPx) / pitchPx).toInt().coerceAtLeast(1)
        val report by rememberUpdatedState(onPageRows)
        LaunchedEffect(rows) { report(rows) }
    }
    val pos = remember(layoutKey) { Animatable(selectionNow(selection, count).toFloat()) }
    val sel = selection().coerceIn(0, count - 1)
    LaunchedEffect(pos, sel) {
        // A wrap (or any far jump) snaps: gliding through every name between would race past hundreds of them.
        if (abs(sel - pos.targetValue) > MINIMAL_SNAP_STEPS) pos.snapTo(sel.toFloat())
        else pos.easeUnlessSettling(sel.toFloat(), tween(MINIMAL_MOVE_MS, easing = NxMenuEasing))
    }
    val dragPos = remember(pos) { mutableFloatStateOf(Float.NaN) }
    val at = rememberShownPosition(pos, dragPos)
    val taps = rememberItemTaps(selection, count, onSelect, onOpen, onLongPress, onSwipeSelect)
    // The solid fill and the tags blend by the shown position ([fill]) over the names it is between ([near]), so nothing
    // switches mid-move.
    val near by remember(count) { derivedStateOf { floor(at()).toInt().coerceIn(0, count - 1)..ceil(at()).toInt().coerceIn(0, count - 1) } }
    val fill = { i: Int -> (1f - abs(i - at())).coerceIn(0f, 1f) }
    // The names in the accent (Appearance → Accent): the selection solid, the others its outline.
    val solid = minimalNameStyle()
    val outline = solid.copy(color = colors.accent.copy(alpha = MINIMAL_OUTLINE_ALPHA), drawStyle = Stroke(width = with(density) { MINIMAL_STROKE.dp.toPx() }))
    val measurer = rememberTextMeasurer()
    // Each name's lines at the row's width (a little narrower for the selection's grow).
    val rowW = ((area.width - 2 * with(density) { gutter.toPx() }) / MINIMAL_SELECTED_SCALE).toInt().coerceAtLeast(1)
    // Each tag's pill (its text padded 8 dp a side) as an inline placeholder after the name and a space.
    val tagStyle = TextStyle(
        fontFamily = font, fontWeight = FontWeight.SemiBold, color = colors.accent,
        fontSize = (MINIMAL_TAG_SP / density.fontScale).sp, lineHeight = (16f / density.fontScale).sp,
    )
    val tagWidths = remember(names, tagStyle) {
        names.mapNotNull { it.tag }.distinct().associateWith { tag -> measurer.measure(tag, tagStyle, maxLines = 1).size.width + with(density) { 16.dp.toPx() }.roundToInt() }
    }
    val placeholders = remember(tagWidths) {
        tagWidths.mapValues { (_, w) -> with(density) { Placeholder(w.toSp(), MINIMAL_TAG_H.dp.toSp(), PlaceholderVerticalAlign.TextCenter) } }
    }
    val texts = remember(names, rowW, solid, tagWidths, pitchPx) { MinimalTexts(names, measurer, solid, rowW, tagWidths, placeholders, pitchPx) }
    val nameW = with(density) { rowW.toDp() }   // every name drawn at the width its lines were counted at
    // The names below make room for the selection's grow (it grows down from its top: [MINIMAL_SELECTED_SCALE] of its
    // lines' height, by its fill), so the gap under it matches the others.
    val grown = { i: Int ->
        var g = 0f
        val p = at()
        for (j in floor(p).toInt().coerceIn(0, count - 1)..ceil(p).toInt().coerceIn(0, count - 1)) {
            if (j < i) g += texts.lines(j) * pitchPx * (MINIMAL_SELECTED_SCALE - 1f) * fill(j)
        }
        g
    }
    val y = { i: Int ->
        val p = at()
        val b = floor(p).toInt().coerceIn(0, count - 1)
        anchorPx + texts.relTop(i, b) - texts.shift(p, count) + grown(i)
    }
    // The names in view: a screen's rows above the selection's place and below it, around the shown position and the
    // selection (so a step's incoming name is there before it glides in).
    val above = ceil(anchorPx / pitchPx).toInt() + 1
    val below = ceil((boxH - anchorPx).coerceAtLeast(0f) / pitchPx).toInt() + 1
    val atWindow by remember(count, above, below, at) { derivedStateOf { minimalWindow(at(), count, above, below) } }
    val selWindow = minimalWindow(sel.toFloat(), count, above, below)
    val window = if (abs(atWindow.first - selWindow.first) > atWindow.last - atWindow.first) atWindow
        else minOf(atWindow.first, selWindow.first)..maxOf(atWindow.last, selWindow.last)
    val slots = remember(layoutKey) { ItemSlots() }.of(window)
    val first = remember { window.first }   // the top name as the page came in: the names rise in from it down
    val risePx = with(density) { MINIMAL_RISE_DP.dp.toPx() }
    Box(
        Modifier.fillMaxSize()
            .axisSwipe(
                vertical = true, key = pos, value = pos, live = dragPos, perDp = -1f / pitch,
                lo = 0f, hi = (count - 1).toFloat(), give = .5f, ms = MINIMAL_MOVE_MS,
                target = { start, drag, velocity ->
                    val i = (start - (drag + velocity * MINIMAL_THROW_S) / pitch).roundToInt().coerceIn(0, count - 1)
                    i to i.toFloat()
                },
                onSelect = { taps.swipeSelect(it) },
            ),
    ) {
        val pill = colors.secondaryAccent
        for (i in window) key(slots.getValue(i)) {
            val lit = i == sel
            val row = names[i]
            val text = texts.text(i)
            val lines = texts.lines(i)
            Box(
                Modifier
                    .offset { IntOffset(0, y(i).roundToInt()) }
                    .fillMaxWidth().height((pitch * lines).dp).padding(horizontal = gutter)
                    .graphicsLayer {
                        val d = i - at()
                        // Behind the strip and the tab row (above the body's top) a name fades to [MINIMAL_UNDER_STRIP] more, over one name's height.
                        val under = ((area.top - y(i) - pitchPx / 2) / pitchPx).coerceIn(0f, 1f)
                        val r = motion.rise(i - first)
                        // Below: how far down from the row under the selection to the screen's bottom edge.
                        val depth = (y(i) - anchorPx - pitchPx) / (boxH - anchorPx - pitchPx).coerceAtLeast(1f)
                        alpha = minimalAlpha(d, depth) * (1f - (1f - MINIMAL_UNDER_STRIP) * under) * r * (if (lit) motion.selection() else motion.others())
                        translationY = (1f - r) * risePx
                        // The alpha on each draw, not through a layer the row's size, which would cut the letters' tails past the tight line.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val s = 1f + (MINIMAL_SELECTED_SCALE - 1f) * fill(i)
                        scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .pointerInput(i) { detectTapGestures(onTap = { taps.tap(i) }, onLongPress = { taps.longPress(i) }) },
                contentAlignment = Alignment.TopStart,
            ) {
                val tag = row.tag
                // The tag's room, blank on the outline.
                val blank = tag?.let { mapOf(MINIMAL_TAG_INLINE to InlineTextContent(placeholders.getValue(it)) {}) }.orEmpty()
                // The outline, fading out as the solid name fades in over it.
                Box(Modifier.graphicsLayer { alpha = 1f - fill(i); compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                    MinimalName(text, outline, nameW, blank)
                }
                if (lit || i in near) {
                    val badge = tag?.let {
                        mapOf(MINIMAL_TAG_INLINE to InlineTextContent(placeholders.getValue(it)) {
                            Box(Modifier.fillMaxSize().background(pill, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                Text(it, style = tagStyle, maxLines = 1)
                            }
                        })
                    }.orEmpty()
                    // The open band starts on the selected name as drawn: its own box, at the text's left.
                    Box(
                        Modifier.graphicsLayer { alpha = fill(i); compositingStrategy = CompositingStrategy.ModulateAlpha }
                            .then(if (lit) Modifier.onGloballyPositioned {
                                selected.coords = it; selected.style = solid; selected.label = row.label
                                // the page showing: where a folder's morph to its list starts
                                if (page?.live?.value == true) morphPlace(it).let { (at, s) -> page.morph.name = MorphText(row.label, at, it.size.width.toFloat(), s, solid) }
                            } else Modifier),
                    ) {
                        MinimalName(text, solid, nameW, badge)
                    }
                }
            }
        }
    }
}

/** A name ([label]) with its [tag]'s inline placeholder after it and a space, when it has a tag. */
private fun minimalText(label: String, tag: String?): AnnotatedString = buildAnnotatedString {
    append(label)
    tag?.let { append(" "); appendInlineContent(MINIMAL_TAG_INLINE, it) }
}

/** One name (with its tag's [inline] content), wrapped at its words onto up to [MINIMAL_MAX_LINES] lines at most [width] wide (where its rows were counted). */
@Composable
private fun MinimalName(text: AnnotatedString, style: TextStyle, width: Dp, inline: Map<String, InlineTextContent>) {
    // Unbounded: its lines run a little taller than the rows' pitch, and a height limit would cut it back to fewer.
    Text(
        text, style = style, maxLines = MINIMAL_MAX_LINES, overflow = TextOverflow.Ellipsis, inlineContent = inline,
        modifier = Modifier.widthIn(max = width).wrapContentHeight(Alignment.Top, unbounded = true),
    )
}

/**
 * A morph's flight over this box ([box]): [from] (a name, or a title) moves to where [to] says [toText] in [toStyle] sits
 * (its window top left and scale; null while not laid out yet, or with nothing to land on: [from] then fades where it is),
 * scaling between the two sizes as one crossfades into the other, at [progress] (0 to 1).
 */
@Composable
internal fun MinimalFlight(
    from: MorphText, toText: String, toStyle: TextStyle, to: () -> Pair<Offset, Float>?, progress: () -> Float, box: () -> LayoutCoordinates?,
) {
    val density = LocalDensity.current
    val fromSp = from.style.fontSize
    val ratio = if (fromSp.isSp && toStyle.fontSize.isSp && fromSp.value > 0f) toStyle.fontSize.value / fromSp.value else .5f
    // Where it is at [g], in this box: its top left and the scale of [from]'s text there.
    fun place(g: Float): Pair<Offset, Float>? {
        val b = box()?.takeIf { it.isAttached } ?: return null
        val src = b.windowToLocal(from.at)
        val dst = to() ?: return src to from.scale
        val d = b.windowToLocal(dst.first)
        return Offset(src.x + (d.x - src.x) * g, src.y + (d.y - src.y) * g) to from.scale + (dst.second * ratio - from.scale) * g
    }
    // The crossfade, through the flight's middle.
    val fade = { g: Float -> ((g - .25f) / .5f).coerceIn(0f, 1f) }
    // Placed as it is drawn (after layout, so the page's coordinates are there on its first frame: no blank frame while
    // the page settles); the alpha alone on a layer, applied per draw so the text drawn past its bounds isn't cut.
    fun Modifier.flying(scale: (Float) -> Float): Modifier = drawWithContent {
        val (at, s) = place(progress()) ?: return@drawWithContent
        translate(at.x, at.y) { scale(scale(s), pivot = Offset.Zero) { this@drawWithContent.drawContent() } }
    }
    Box(Modifier.fillMaxSize()) {
        Text(
            from.text, style = from.style, maxLines = MINIMAL_MAX_LINES, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = with(density) { from.width.coerceAtLeast(1f).toDp() }).wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val g = progress()
                    alpha = if (to() == null) 1f - g else 1f - fade(g)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .flying { it },
        )
        Text(
            toText, style = toStyle, maxLines = 1, softWrap = false,
            modifier = Modifier.graphicsLayer {
                alpha = if (to() == null) 0f else fade(progress())
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }.flying { it / ratio },
        )
    }
}

/**
 * The open band over a page drawn in its box ([box]): from the selected name as [selected] last saw it, read once as it
 * first composes. Draw it while AppState's minimalOpening is this page's; the page opens the row as it fills.
 */
@Composable
internal fun MinimalOpenOverlay(selected: MinimalSelected, box: LayoutCoordinates?) {
    val start = remember {
        val row = selected.coords
        val style = selected.style
        val name = selected.label
        if (box != null && row != null && style != null && name != null && box.isAttached && row.isAttached) {
            Triple(name, box.localBoundingBoxOf(row, clipBounds = false), style) to row.size.width
        } else null
    } ?: return
    val (band, w) = start
    // Its laid-out width, and the scale it is drawn at (the selection's grow), so it keeps its size and lines.
    MinimalOpenBand(band.first, band.second, band.third, laidW = w.toFloat())
}

/**
 * The Minimal open: as the other names fade out ([MINIMAL_LEAVE_MS], the list's own draw), nothing here; then [name]'s
 * row ([from], in this box's coordinates) becomes a band, a dark tint of the accent, that grows to fill the box
 * ([OPEN_GROW_MS]) and goes black as it nears it, the name (in its own accent, at its drawn size) staying where it was
 * and going dark with it. AppState opens the row as it is black ([com.nxredux.mobile.ui.MINIMAL_OPEN_MS]); a game as
 * the band starts, loading under it.
 */
@Composable
internal fun MinimalOpenBand(name: String, from: Rect, style: TextStyle, modifier: Modifier = Modifier, laidW: Float = from.width) {
    val colors = LocalNxColors.current
    // A dark tint of the accent, not the accent itself: a White accent filling the black screen would flash.
    val band = lerp(colors.background, colors.accent, OPEN_BAND_TINT)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(MINIMAL_LEAVE_MS)
        grow.animateTo(1f, tween(OPEN_GROW_MS, easing = LinearEasing))
    }
    BoxWithConstraints(modifier.fillMaxSize().graphicsLayer { alpha = if (grow.value > 0f) 1f else 0f }) {
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val pad = with(density) { 6.dp.toPx() }
        val g = { OpenEasing.transform(grow.value) }
        Box(
            Modifier.fillMaxWidth().layout { m, c ->
                val top = (from.top - pad) * (1 - g())
                val bottom = from.bottom + pad + (h - from.bottom - pad) * g()
                val p = m.measure(Constraints.fixed(c.maxWidth, (bottom - top).roundToInt().coerceAtLeast(0)))
                layout(p.width, p.height) { p.place(0, top.roundToInt()) }
            }.background(band),
        )
        Text(
            name, style = style.copy(drawStyle = Fill), maxLines = MINIMAL_MAX_LINES, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset { IntOffset(from.left.roundToInt(), from.top.roundToInt()) }
                .graphicsLayer { val s = if (laidW > 0f) from.width / laidW else 1f; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0f) }
                .widthIn(max = with(density) { laidW.coerceAtLeast(1f).toDp() }).wrapContentHeight(Alignment.Top, unbounded = true),
        )
        // Black once the band has (nearly) filled the box.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((grow.value - OPEN_DARK_FROM) / (1f - OPEN_DARK_FROM)).coerceIn(0f, 1f) }.background(Color.Black))
    }
}
