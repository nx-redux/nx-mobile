package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.InfoLine

private const val ROW_SLIDE_MS = 300   // the row's slide and scale, and its caption's glide

/**
 * A horizontal row's animated position, in items, starting at the selection; new [keys] (a new Screen, a new list: a
 * tab switch, even to an equal list) snap it to its selection instead of sliding. Read without subscribing: the
 * selection itself is read only by [NxRow]'s FollowSelection, its items and the caption.
 */
@Composable
internal fun rememberRowPosition(selectedIndex: () -> Int, count: Int, vararg keys: Any?): Animatable<Float, AnimationVector1D> =
    remember(*keys) { Animatable(selectionNow(selectedIndex, count).toFloat()) }

/**
 * A horizontal row (Carousel / Backdrop, orientation Horizontal), the row counterpart of [NxStack]: it slides [pos]
 * (0.3 s, [NxMenuEasing]) to keep the selection centred ([FollowSelection]); a horizontal drag over the band (from
 * [bandTop], [bandH] tall: it covers every drawn item) moves it live by its items' positions ([rowSwipeDelta] on
 * [swipeG]) and, on release, selects the item nearest where it would come to rest ([rowSwipeTarget]). A tap on a side
 * item selects it, a tap on the centre item opens it ([LocalNxTapOpens]), a long press calls [onLongPress].
 * [item] draws item i in the band (BoxScope: the band's); `lit` is whether it is the selection, drawn lit; `at` the shown
 * position (read in layers only); `taps` the row's one [ItemTaps] (with [shownItemTaps]). The items composed are the ones
 * [rowItem] shows and one hidden past each end ([rowComposeWindow]), keyed by recycled slots ([ItemSlots]).
 * [litSelection] non-null (the Carousel): the window spans the selection's too, so an item a slide is heading to is
 * there already, and the selection is lit while it says so; null (Backdrop: the selection shows by its size alone): the
 * window follows the position only and no item is lit, so a step recomposes no item, only the window.
 */
@Composable
internal fun NxRow(
    count: Int, selectedIndex: () -> Int, pos: Animatable<Float, AnimationVector1D>, swipeG: RowGeometry,
    bandTop: Float, bandH: Float,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    litSelection: (() -> Boolean)? = null,
    item: @Composable BoxScope.(i: Int, lit: Boolean, at: () -> Float, taps: ItemTaps) -> Unit,
) {
    FollowSelection(pos, selectedIndex, count, ROW_SLIDE_MS)
    // While a finger drags the row: the position it shows (NaN otherwise, the animated one).
    val dragPos = remember(pos) { mutableFloatStateOf(Float.NaN) }
    val at = rememberShownPosition(pos, dragPos)
    val taps = rememberItemTaps(selectedIndex, count, onSelect, onOpen, onLongPress)

    Box(Modifier.fillMaxWidth().offset(y = bandTop.dp).height(bandH.dp)
            .horizontalSwipe(
                key = pos, value = pos, live = dragPos, perDp = -1f / rowSwipeStep(swipeG),
                lo = 0f, hi = (count - 1).toFloat(), give = 0.5f, ms = ROW_SLIDE_MS,
                target = { start, drag, velocity ->
                    val i = rowSwipeTarget(start, drag, velocity, count, swipeG)
                    i to i.toFloat()
                },
                onSelect = { taps.swipeSelect(it) },
                toValue = { rowSwipeDelta(it, swipeG) }, slope = { rowSwipeSlope(it, swipeG) },
            )
    ) {
        RowItems(count, selectedIndex, litSelection, at, taps, item)
    }
}

/**
 * The row's items (see [NxRow]). Recomposes only when the window moves, and with [litSelection] when the selection or
 * its lit state does; each item skips unless its lit state or its slot's item changed.
 */
@Composable
private fun BoxScope.RowItems(
    count: Int, selectedIndex: () -> Int, litSelection: (() -> Boolean)?, at: () -> Float, taps: ItemTaps,
    item: @Composable BoxScope.(i: Int, lit: Boolean, at: () -> Float, taps: ItemTaps) -> Unit,
) {
    val atPos by remember(at, count) { derivedStateOf { rowComposeWindow(at(), count) } }
    var sel = -1
    var window = atPos
    if (litSelection != null) {
        val s = selectedIndex().coerceIn(0, count - 1)
        if (litSelection()) sel = s
        val atTarget = rowComposeWindow(s.toFloat(), count)
        window = minOf(atPos.first, atTarget.first)..maxOf(atPos.last, atTarget.last)
    }
    val slots = remember { ItemSlots() }.of(window)
    for (i in window) {
        key(slots.getValue(i)) { item(i, i == sel, at, taps) }
    }
}

/**
 * An item's taps through the row's or stack's [taps], taken only while [shown] says the item is drawn: a composition a
 * frame behind the position (or a window that spans a slide's target) must not let a hidden item take a touch. [shown]
 * is read when the touch lands, so it reads the position and geometry through updated state.
 */
internal fun Modifier.shownItemTaps(i: Int, taps: ItemTaps, hasLongPress: Boolean, shown: () -> Boolean): Modifier =
    pointerInput(i, hasLongPress) {
        detectTapGestures(
            onTap = { if (shown()) taps.tap(i) },
            onLongPress = if (hasLongPress) { _ -> if (shown()) taps.longPress(i) } else null,
        )
    }

/**
 * A row's caption, centred under the selected item: [captionTitle] (up to [nameLines] lines) and its info rows
 * ([backdropCaptionRows]: the time and the achievements, then the next goal, one row each, a row without data left out),
 * [below] dp under the selected item's top ([selTop]: its height and the gap). Fitted, then measured as drawn so the row
 * is placed right on the first frame ([GlideCaptionHeight] glides [glide] to it); too long for the width, "Next: <name>"
 * takes the room under the reserve, down to the body's bottom less a margin ([spare]). Reads the caption itself (the
 * info only under a title), so a selection step recomposes only this and the items.
 */
@Composable
internal fun RowCaption(
    captionTitle: () -> String?, captionInfo: () -> List<InfoLine>, glide: CaptionGlide,
    nameStyle: TextStyle, nameLines: Int, infoStyle: TextStyle, inset: PageInset, avail: Dp, availPx: Int, spare: Float,
    selTop: () -> Float, below: Float, textColor: Color,
) {
    val title = captionTitle()
    val infoRows = if (title != null) backdropCaptionRows(captionInfo()) else emptyList()
    val fitted = rememberFittedCaptionRows(infoRows, infoStyle, 12.dp, avail, spare.dp)
    val drawn = rememberCaptionDrawnHeight(title, nameStyle, nameLines, fitted, infoStyle, availPx)
    GlideCaptionHeight(glide, drawn, ROW_SLIDE_MS)
    if (title != null) {
        Column(
            Modifier.fillMaxWidth().offset { IntOffset(0, (selTop() + below).dp.roundToPx()) }
                .padding(start = inset.start, end = inset.end).wrapContentWidth().captionTap(),   // the tap target is the text, not the strip
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = textColor, maxLines = nameLines, overflow = TextOverflow.Ellipsis, style = nameStyle)
            CaptionFittedRows(fitted, infoStyle, 12.dp)
        }
    }
}
