package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.ui.InfoLine

private const val STACK_SLIDE_MS = 300   // the stack's slide and scale, as the horizontal row's
/** The fade to transparent at the top and bottom of the plain-black stacks' bodies. */
private val STACK_EDGE_FADE = 20.dp

/**
 * A vertical stack (Carousel / Backdrop, orientation Vertical): the horizontal row stood on end ([stackItem] on
 * [StackGeometry.asRow]). The selection full size with its centre at ([StackGeometry.centreX], [StackGeometry.centreY]),
 * the others above and below it, smaller and receding as [style]'s row does, nothing from four steps. It slides (0.3 s,
 * [NxMenuEasing]) to keep the selection there; a vertical drag moves it live by its items' positions ([rowSwipeDelta])
 * and, on release, settles on the item nearest where it would rest ([rowSwipeTarget]), the same as the row's on X.
 * A tap on a neighbour selects it, a tap on the selection opens it ([LocalNxTapOpens]), a long press calls [onLongPress].
 * The body is clipped (a hidden page title's room is part of the body, so the stack runs up into it; the caller centres
 * [g] in the whole body); [edgeFade]: its top and bottom fade out over [STACK_EDGE_FADE] (the plain-black stacks).
 * [item] draws item i in its slot ([StackGeometry.slotW] × [StackGeometry.slotH]; `distance` is i − the animated
 * position, read at draw time); [overlay] draws over the stack in body coordinates (a count, a caption), inside the clip
 * and the fade. [layoutKey] / [dataKey]: a new Screen or a new list snaps the stack to its selection instead of sliding.
 * [marks]: the items are the main menu's marks, drawn past their slot unclipped when dimmed ([itemCompositing]).
 * [reach]: each item's drawn extent (the main menu's consoles, [StackReach]): the items are placed by what they draw
 * ([stackReachDy]), the same gap between every pair, instead of by their slots.
 * [sideAlpha]: each item's opacity by its distance in place of [stackItem]'s (the main menu's Consoles and Collections,
 * [mainMenuSideAlpha]).
 */
@Composable
internal fun NxStack(
    count: Int, selectedIndex: () -> Int, g: StackGeometry, style: RowStyle, edgeFade: Boolean,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    layoutKey: Any?, dataKey: Any?,
    modifier: Modifier = Modifier,
    marks: Boolean = false,
    reach: ((Int) -> StackReach)? = null,
    sideAlpha: ((distance: Float) -> Float)? = null,
    item: @Composable BoxScope.(i: Int, selected: Boolean, distance: () -> Float) -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    if (count <= 0 || g.slotW <= 1f || g.slotH <= 1f) return
    // The selection is read only by FollowSelection and the item loop ([StackItems]): a step recomposes those and the
    // two items whose lit state changed, not the stack.
    val pos = remember(layoutKey, dataKey) { Animatable(selectionNow(selectedIndex, count).toFloat()) }
    FollowSelection(pos, selectedIndex, count, STACK_SLIDE_MS)
    // While a finger drags the stack: the position it shows (NaN otherwise, the animated one).
    val dragPos = remember(pos) { mutableFloatStateOf(Float.NaN) }
    val at = rememberShownPosition(pos, dragPos)
    val taps = rememberItemTaps(selectedIndex, count, onSelect, onOpen, onLongPress)
    val hasLongPress = onLongPress != null
    val geometry by rememberUpdatedState(g)
    val row = g.asRow()

    Box(
        modifier.fillMaxSize().clipToBounds()
            .then(if (edgeFade) Modifier.nxStackEdgeFade(STACK_EDGE_FADE) else Modifier)
            .axisSwipe(
                vertical = true, key = pos, value = pos, live = dragPos, perDp = -1f / rowSwipeStep(row),
                lo = 0f, hi = (count - 1).toFloat(), give = 0.5f, ms = STACK_SLIDE_MS,
                target = { start, drag, velocity ->
                    val i = rowSwipeTarget(start, drag, velocity, count, geometry.asRow())
                    i to i.toFloat()
                },
                onSelect = { taps.swipeSelect(it) },
                toValue = { rowSwipeDelta(it, geometry.asRow()) }, slope = { rowSwipeSlope(it, geometry.asRow()) },
            ),
    ) {
        StackItems(count, selectedIndex, at, g, style, marks, reach, sideAlpha, taps, hasLongPress, item)
        Box(Modifier.fillMaxSize()) { overlay() }   // in body coordinates
    }
}

/**
 * The stack's items: the ones stackItem shows and one more hidden at each end ([rowComposeWindow]; an item hidden takes
 * no touch), each lit when it is the selection, keyed by recycled slots ([ItemSlots]) so a step re-fills the slot
 * leaving at one end with the item coming in at the other. Recomposes when the selection or the window moves; each
 * [StackItem] skips unless its lit state or its item changed.
 */
@Composable
private fun StackItems(
    count: Int, selectedIndex: () -> Int, at: () -> Float, g: StackGeometry, style: RowStyle, marks: Boolean,
    reach: ((Int) -> StackReach)?, sideAlpha: ((distance: Float) -> Float)?, taps: ItemTaps, hasLongPress: Boolean,
    item: @Composable BoxScope.(i: Int, selected: Boolean, distance: () -> Float) -> Unit,
) {
    val sel = selectedIndex().coerceIn(0, count - 1)
    val window by remember(at, count) { derivedStateOf { rowComposeWindow(at(), count) } }
    val slots = remember { ItemSlots() }.of(window)
    for (i in window) {
        key(slots.getValue(i)) { StackItem(i, i == sel, count, g, style, marks, reach, sideAlpha, at, taps, hasLongPress, item) }
    }
}

/** One stack item in its slot, placed, scaled and faded by its distance from the shown position (read in its layer). */
@Composable
private fun StackItem(
    i: Int, selected: Boolean, count: Int, g: StackGeometry, style: RowStyle, marks: Boolean,
    reach: ((Int) -> StackReach)?, sideAlpha: ((distance: Float) -> Float)?, at: () -> Float, taps: ItemTaps, hasLongPress: Boolean,
    item: @Composable BoxScope.(i: Int, selected: Boolean, distance: () -> Float) -> Unit,
) {
    val geometry by rememberUpdatedState(g)
    val shownAt by rememberUpdatedState(at)
    val distance = remember(i) { { i - shownAt() } }
    Box(
        Modifier
            .offset { IntOffset((g.centreX - g.slotW / 2).dp.roundToPx(), (g.centreY - g.slotH / 2).dp.roundToPx()) }
            .size(g.slotW.dp, g.slotH.dp)
            .graphicsLayer {
                val r = stackItem(style, i - at(), g)
                translationY = (if (reach != null) stackReachDy(i, at(), count, g, reach) else r.dx).dp.toPx()
                scaleX = r.scale
                scaleY = r.scale
                val a = sideAlpha?.invoke(i - at()) ?: r.alpha
                alpha = if (r.visible) a.coerceIn(0f, 1f) else 0f
                compositingStrategy = itemCompositing(marks)
            }
            .shownItemTaps(i, taps, hasLongPress) { stackItem(style, distance(), geometry).visible },
        contentAlignment = Alignment.Center,
    ) {
        item(i, selected, distance)
    }
}

/** The vertical stack's edge fade: the content fades to transparent over [fade] at the top and the bottom. */
internal fun Modifier.nxStackEdgeFade(fade: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val f = fade.toPx().coerceAtMost(size.height / 2)
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = f), blendMode = BlendMode.DstIn)
        drawRect(
            Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - f, endY = size.height), blendMode = BlendMode.DstIn,
        )
    }

/**
 * A game-list stack's caption (Backdrop-Vertical, Carousel-Vertical): Backdrop's three lines in the column from
 * [StackGeometry.captionX] to [bodyW] less [StackGeometry.captionRight], left-aligned ([alignment] LEFT: the text starts
 * the caption gap after the stack) or right-aligned (RIGHT, the mirror: it ends the gap before the stack, so it hugs the
 * stack rather than the screen edge), the block vertically centred on the selected item ([StackGeometry.centreY]). The
 * name ([title], [nameSp] SemiBold, up to two balanced lines, then an ellipsis), then
 * the time and trophy row and the "Next: <name>" row at 14 sp in grey ([backdropCaptionRows] via [CaptionInfoRows]: a
 * row without data is left out; a next goal too long takes up to two lines, the block growing round its centre), 3 dp
 * apart. [shadow]: Backdrop's text shadow (over a picture). [splitNext] (the portrait family): "Next: <name>"
 * always on its own lines, long or not.
 */
@Composable
internal fun StackCaption(
    title: String?, info: List<InfoLine>, g: StackGeometry, bodyW: Float, bodyH: Float, shadow: Boolean, nameSp: Float = 18f,
    splitNext: Boolean = false, alignment: VerticalListAlignment = VerticalListAlignment.LEFT,
    infoShare: Float? = null,   // the info rows at this share of the name's size; null: 14 sp
) {
    if (title == null) return
    val end = alignment == VerticalListAlignment.RIGHT
    val align = if (end) TextAlign.End else TextAlign.Start
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val textShadow = if (shadow) Shadow(colors.background, blurRadius = 6f) else null
    val nameSize = nameSp.sp
    val nameStyle = TextStyle(
        fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = nameSize, textAlign = align,
        lineBreak = LineBreak.Heading, shadow = textShadow,
    )
    val infoStyle = TextStyle(fontFamily = font, fontSize = infoShare?.let { (nameSp * it).sp } ?: 14.sp, textAlign = align, shadow = textShadow)
    val width = (bodyW - g.captionRight - g.captionX).coerceAtLeast(0f)
    Column(
        Modifier.stackCaptionPlace(g).width(width.dp).captionTap(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = if (end) Alignment.End else Alignment.Start,
    ) {
        Text(title, color = colors.accent, maxLines = 2, overflow = TextOverflow.Ellipsis, style = nameStyle)   // the selected game's name in the accent
        // The centred block may grow: "Next: <name>" may take its two lines whatever the body's room.
        CaptionInfoRows(backdropCaptionRows(info), infoStyle, 12.dp, avail = width.dp, spare = bodyH.dp, alwaysSplitNext = splitNext)
    }
}

/** Places the measured caption with its left edge at [StackGeometry.captionX] and its middle on [StackGeometry.centreY]. */
private fun Modifier.stackCaptionPlace(g: StackGeometry): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val w = if (constraints.hasBoundedWidth) constraints.maxWidth else p.width
    val h = if (constraints.hasBoundedHeight) constraints.maxHeight else p.height
    layout(w, h) {
        p.place(g.captionX.dp.roundToPx(), g.centreY.dp.roundToPx() - p.height / 2)
    }
}
