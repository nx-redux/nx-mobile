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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.MINIMAL_LEAVE_MS
import com.nxredux.mobile.ui.NavMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The Minimal layout (Layouts → a tab's or the game lists' layout: Minimal): big names only, no art. The selected name is
 * solid in the accent (the only accent on the page) and a little larger, a third of the way down the band; the others are
 * outlines in the ink, one line each, fading by distance to the band's edges ([minimalOutlineAlpha]): nothing shows under
 * the stats row, the title or the tab row. Home adds its stats
 * strip and the tags of its rows ([HomeMinimalScreen]); the tabs and the game lists show the names alone. A on a game or
 * a tool runs the launch sequence from the selected name ([LaunchOverlay], which it reports: [LocalLaunchTargets]). Between two
 * Minimal pages a folder morphs ([MinimalFlight]): its name flies into the list's title as the list's names rise in, and
 * B drops the title back into its row as the other names fade in.
 */

/** The glide of the names to a new selection (the d-pad's; a swipe settles on its own spring). */
private const val MINIMAL_MOVE_MS = 340

/** A jump of more than this many names (a wrap from the last to the first) snaps instead of gliding through them all. */
internal const val MINIMAL_SNAP_STEPS = 8

/** An unselected name's outline: the ink ([NxColors.main]), [MINIMAL_STROKE] dp wide, at its row's opacity ([minimalOutlineAlpha]). */
private const val MINIMAL_STROKE = 1f

/** The selected name grows by this much, from its left edge. */
private const val MINIMAL_SELECTED_SCALE = 1.04f

/**
 * A name's tag (only Home's Continue wears one) after its name, [MINIMAL_TAG_GAP] dp from its end so every name keeps its
 * left edge; a name too long for both is cut short with an ellipsis before the tag. A round-ended pill
 * of the ink at [MINIMAL_TAG_FILL], its word in [NxColors.hint] at [MINIMAL_TAG_SP] sp (pinned), padded
 * [MINIMAL_TAG_PAD_Y] × [MINIMAL_TAG_PAD_X] dp; never an accent. Centred on the name's x-height ([MINIMAL_X_HEIGHT]), it
 * does not grow with the selected name, and an unselected Continue's tag fades with its row.
 */
private const val MINIMAL_TAG_SP = 13f
private const val MINIMAL_TAG_LINE_SP = 17f
private const val MINIMAL_TAG_PAD_X = 8f
private const val MINIMAL_TAG_PAD_Y = 2f
private const val MINIMAL_TAG_H = MINIMAL_TAG_LINE_SP + 2 * MINIMAL_TAG_PAD_Y
private const val MINIMAL_TAG_GAP = 12f
private const val MINIMAL_TAG_FILL = .14f
/** The name font's x-height as a share of its size (MiSans), where the tag centres. */
private const val MINIMAL_X_HEIGHT = .52f
private const val MINIMAL_TAG_INLINE = "tag"

/** A swipe's release carries on this long at the finger's speed to pick the name it lands on. */
private const val MINIMAL_THROW_S = .25f

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
 * The outline ladder: an outlined name's opacity at [d] px (centre to centre) from the selected name's line, [reach] px
 * being the line's distance to the band's edge the name is moving toward (the stats row's bottom above, the tab row's
 * top below). [MINIMAL_OUTLINE_CEILING] next to the line, falling as (1 − t)^[MINIMAL_OUTLINE_GAMMA] (even to the eye: no
 * plateau near the selection, no cliff at the far end) to 0 at the edge and past it, so nothing draws under either row.
 */
private const val MINIMAL_OUTLINE_CEILING = .5f
private const val MINIMAL_OUTLINE_GAMMA = 2.2f

internal fun minimalOutlineAlpha(d: Float, reach: Float): Float {
    if (reach <= 0f) return 0f
    val t = (abs(d) / reach).coerceIn(0f, 1f)
    return MINIMAL_OUTLINE_CEILING * (1f - t).pow(MINIMAL_OUTLINE_GAMMA)
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

/** The selected name as last laid out (its coordinates, text style and label), reported by [MinimalNames]: where B's morph lands. */
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
 * the whole box, the hint bar at the bottom ([NxScreenScaffold]). The band: from [top] down the box (Home's strip room,
 * or the page's icons') to the hint bar's top, or to the box's bottom with [toBoxBottom] (the main menu's box ends on its
 * tab row's top). The selection's line sits a third of the way down it ([minimalAnchor]), the outlines fading to nothing
 * at its edges ([minimalOutlineAlpha]). A drag moves the names with the finger and,
 * released, they settle on the name the throw lands on, selected ([axisSwipe]). A tap selects, a tap on the selected name
 * opens it (every name, so a launch starts from the selection's place; [tapOpens] may open on one tap), a long press calls [onLongPress]. The d-pad
 * lives in AppState (UP / DOWN wrap on a fresh press, as a List). [overlay] draws over the names (Home's strip). [leaving]: a folder is opening (AppState's minimalOpening),
 * the other names fading out; a launch moves and fades nothing (its black covers the page). Come in by a morph ([LocalMinimalPage]), a list's names rise in ([NavMotion.MINIMAL_FORWARD])
 * or the last list's title drops into the selected row as the others fade in ([NavMotion.MINIMAL_BACK]).
 */
@Composable
internal fun MinimalScreen(
    names: List<NxRow>, selection: () -> Int, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, onHint: ((NxButton) -> Unit)?,
    selected: MinimalSelected, modifier: Modifier = Modifier, top: Float = 0f, hideHints: Boolean = false,
    toBoxBottom: Boolean = false, layoutKey: Any? = null, leaving: Boolean = false, tapOpens: (Int) -> Boolean = NxTapNeverOpens,
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
    // The body between [top] and the hint bar, in this box: the band the names show in (to the box's bottom: [toBoxBottom]).
    val box = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var area by remember { mutableStateOf<Rect?>(null) }
    var boxH by remember { mutableFloatStateOf(0f) }
    Box(modifier.fillMaxSize().onGloballyPositioned { box[0] = it; boxH = it.size.height.toFloat() }) {
        area?.let { body ->
            val a = if (toBoxBottom) body.copy(bottom = boxH) else body
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
 * The names' text at one row width and style, built only for the names that come into view (cached): a list of
 * thousands builds the few on screen. Every name is one line; a name with a tag too long for both is cut short with an
 * ellipsis before the tag ("Metroid - Zero Mi… [Continue]"), so the tag always shows.
 */
private class MinimalTexts(
    private val names: List<NxRow>, private val measurer: TextMeasurer, private val solid: TextStyle, private val rowW: Int,
    private val tagWidths: Map<String, Int>,
) {
    private val texts = arrayOfNulls<AnnotatedString>(names.size)

    fun text(i: Int): AnnotatedString = texts[i] ?: build(names[i]).also { texts[i] = it }

    private fun width(s: String) = measurer.measure(s, solid, maxLines = 1, softWrap = false).size.width

    private fun build(row: NxRow): AnnotatedString {
        val tag = row.tag ?: return minimalText(row.label, null)
        val room = rowW - tagWidths.getValue(tag) - width(" ")
        if (width(row.label) <= room) return minimalText(row.label, tag)
        // The longest start of the name that fits with an ellipsis.
        var lo = 0
        var hi = row.label.length
        while (lo < hi) {
            val m = (lo + hi + 1) / 2
            if (width(row.label.substring(0, m).trimEnd() + "\u2026") <= room) lo = m else hi = m - 1
        }
        return minimalText(row.label.substring(0, lo).trimEnd() + "\u2026", tag)
    }
}

/**
 * The selected name's line (its centre), px down its box: [MINIMAL_ANCHOR_SHARE] of the way down the band from its top
 * edge [bandTop] (the stats row's bottom, or the content's top) to its bottom edge [bandBottom]. The list moves, the line
 * does not; the first name leaves the band's top empty.
 */
private const val MINIMAL_ANCHOR_SHARE = .33f

internal fun minimalAnchor(bandTop: Float, bandBottom: Float): Float = bandTop + MINIMAL_ANCHOR_SHARE * (bandBottom - bandTop)

/**
 * The names over the whole box ([boxH] tall), the shown position's name centred on the band's ([area]) line
 * ([minimalAnchor]), drawn unclipped but faded to nothing at the band's edges. The position eases to the selection (a
 * far jump snaps: [MINIMAL_SNAP_STEPS]) and follows a finger's drag. Only the names in view are composed
 * ([minimalWindow]), keyed by recycled slots ([ItemSlots]).
 */
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
    val launchTargets = LocalLaunchTargets.current
    val size = MINIMAL_NAME_DP * nxTextScale()
    val pitch = size + 2f
    val pitchPx = with(density) { pitch.dp.toPx() }
    // The selection's line a third of the way down the band, and the top of its row.
    val line = minimalAnchor(area.top, area.bottom)
    val anchorPx = line - pitchPx / 2
    // A page: the names from the selection down to the band's bottom.
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
    // The selection solid in the accent (Appearance → Accent); the others outlined in the ink.
    val solid = minimalNameStyle()
    val outline = solid.copy(color = colors.main, drawStyle = Stroke(width = with(density) { MINIMAL_STROKE.dp.toPx() }))
    val measurer = rememberTextMeasurer()
    // Each name's one line at the row's width (a little narrower for the selection's grow), an ellipsis at the gutter.
    val rowW = ((area.width - 2 * with(density) { gutter.toPx() }) / MINIMAL_SELECTED_SCALE).toInt().coerceAtLeast(1)
    // Each tag's pill (its text padded [MINIMAL_TAG_PAD_X] a side) as an inline placeholder after the name and a space,
    // the placeholder's start making up the rest of the [MINIMAL_TAG_GAP]. Its bottom on the baseline: the pill is moved
    // down onto the x-height's middle where it is drawn ([tagDrop]).
    val tagStyle = TextStyle(
        fontFamily = font, fontWeight = FontWeight.SemiBold, color = colors.hint,
        fontSize = (MINIMAL_TAG_SP / density.fontScale).sp, lineHeight = (MINIMAL_TAG_LINE_SP / density.fontScale).sp,
    )
    val space = remember(solid) { measurer.measure(" ", solid, maxLines = 1).size.width.toFloat() }
    val lead = with(density) { MINIMAL_TAG_GAP.dp.toPx() } - space
    val pillWidths = remember(names, tagStyle) {
        names.mapNotNull { it.tag }.distinct().associateWith { tag -> measurer.measure(tag, tagStyle, maxLines = 1).size.width + with(density) { (2 * MINIMAL_TAG_PAD_X).dp.toPx() } }
    }
    val tagWidths = pillWidths.mapValues { (_, w) -> (w + lead).roundToInt() }
    val placeholders = remember(tagWidths) {
        tagWidths.mapValues { (_, w) -> with(density) { Placeholder(w.toSp(), MINIMAL_TAG_H.dp.toSp(), PlaceholderVerticalAlign.AboveBaseline) } }
    }
    // The pill's centre from the placeholder's (half its height over the baseline) down to the x-height's middle.
    val tagDrop = with(density) { (MINIMAL_TAG_H / 2).dp.toPx() } - with(density) { size.dp.toPx() } * MINIMAL_X_HEIGHT / 2
    val texts = remember(names, rowW, solid, tagWidths) { MinimalTexts(names, measurer, solid, rowW, tagWidths) }
    val nameW = with(density) { rowW.toDp() }   // every name drawn at the width it was cut to
    // The names below make room for the selection's grow (it grows down from its top: [MINIMAL_SELECTED_SCALE] of a row,
    // by its fill), so the gap under it matches the others.
    val grown = { i: Int ->
        var g = 0f
        val p = at()
        for (j in floor(p).toInt().coerceIn(0, count - 1)..ceil(p).toInt().coerceIn(0, count - 1)) {
            if (j < i) g += pitchPx * (MINIMAL_SELECTED_SCALE - 1f) * fill(j)
        }
        g
    }
    // A name's top: a row a step from the shown position's (pulled past either end too).
    val y = { i: Int -> anchorPx + (i - at()) * pitchPx + grown(i) }
    // The band's edges' distances from the line: the reach of the fade above it and below it.
    val reachUp = line - area.top
    val reachDown = area.bottom - line
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
        val pill = colors.main.copy(alpha = MINIMAL_TAG_FILL)
        for (i in window) key(slots.getValue(i)) {
            val lit = i == sel
            val row = names[i]
            val text = texts.text(i)
            Box(
                Modifier
                    .offset { IntOffset(0, y(i).roundToInt()) }
                    .fillMaxWidth().height(pitch.dp).padding(horizontal = gutter)
                    .graphicsLayer {
                        // Its outline's place on the ladder from its centre's distance to the line this frame (mid-scroll
                        // too), toward the edge it is moving to; solid as it becomes the shown position ([fill]).
                        val d = y(i) + pitchPx / 2 - line
                        val f = fill(i)
                        val ladder = minimalOutlineAlpha(d, if (d < 0f) reachUp else reachDown)
                        val r = motion.rise(i - first)
                        alpha = (ladder + (1f - ladder) * f) * r * (if (lit) motion.selection() else motion.others())
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
                // The tag's pill, drawn with the outline and with the solid name, so it fades with its row; it keeps its
                // size while the selected name grows (the row's scale undone round its centre).
                val badge = tag?.let {
                    val pillW = with(density) { pillWidths.getValue(it).toDp() }
                    mapOf(MINIMAL_TAG_INLINE to InlineTextContent(placeholders.getValue(it)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                            Box(
                                Modifier.width(pillW).height(MINIMAL_TAG_H.dp)
                                    .graphicsLayer { val k = 1f / (1f + (MINIMAL_SELECTED_SCALE - 1f) * fill(i)); scaleX = k; scaleY = k; translationY = tagDrop }
                                    .background(pill, RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center,
                            ) { Text(it, style = tagStyle, maxLines = 1) }
                        }
                    })
                }.orEmpty()
                // The outline, fading out as the solid name fades in over it.
                Box(Modifier.graphicsLayer { alpha = 1f - fill(i); compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                    MinimalName(text, outline, nameW, badge)
                }
                if (lit || i in near) {
                    val laid = remember { arrayOfNulls<TextLayoutResult>(1) }
                    // The selected name as drawn (its own box, at the text's left): where B's morph lands, where a folder's
                    // morph starts and, on the page showing, where a launch starts (its full name, at the selection's grow).
                    Box(
                        Modifier.graphicsLayer { alpha = fill(i); compositingStrategy = CompositingStrategy.ModulateAlpha }
                            .then(if (lit) Modifier.onGloballyPositioned { c ->
                                selected.coords = c; selected.style = solid; selected.label = row.label
                                if (page?.live?.value != false) launchTargets.report(
                                    LaunchTarget(AnnotatedString(row.label), { laid[0] }, { c }, solid, colors.accent, { 1f + (MINIMAL_SELECTED_SCALE - 1f) * fill(i) }),
                                )
                                if (page?.live?.value == true) morphPlace(c).let { (at, s) -> page.morph.name = MorphText(row.label, at, c.size.width.toFloat(), s, solid) }
                            } else Modifier),
                    ) {
                        MinimalName(text, solid, nameW, badge, onTextLayout = if (lit) { r -> laid[0] = r } else null)
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

/** One name (with its tag's [inline] content) on one line at most [width] wide, an ellipsis where it is cut; its layout to [onTextLayout]. */
@Composable
private fun MinimalName(
    text: AnnotatedString, style: TextStyle, width: Dp, inline: Map<String, InlineTextContent>, onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    // Unbounded: its line runs a little taller than the rows' pitch, and a height limit would clip it.
    Text(
        text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, inlineContent = inline, onTextLayout = onTextLayout ?: {},
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
            from.text, style = from.style, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
