package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.gridTileIdentity
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.ceil
import kotlin.math.floor

private const val SLIDE_MS = 260        // the row's slide and the edge shading
private const val CUT_ALPHA = 0.7f      // the layer over a column cut off at an edge
private const val EDGE_SHARE = 0.2f     // how much of the width an edge fade covers

/** The d-pad presses a Grid has taken ([AppState.gridPresses]): a press ends a swipe's or a tap's hold on the row. */
internal val LocalGridPresses = compositionLocalOf<State<Int>> { mutableIntStateOf(0) }

/** How long a Grid tile takes to light up or go back to unlit, in place (ms, [NxMenuEasing]). */
internal const val GRID_LIT_MS = 120

/** Whether tile [index] is lit when [litIndex] is: one tile at a time, so a move changes only the two tiles involved. */
internal fun gridTileLit(index: Int, litIndex: Int): Boolean = index == litIndex


/** Below this tile height (dp) the grid neither draws nor reports its columns: a layout pass not yet at its size. */
internal const val GRID_TILE_MIN_DP = 40f

/** Whether a body [widthDp] × [heightDp] with grid [g] is a real layout (not a zero or tiny pass) to draw and report. */
internal fun gridDrawable(widthDp: Float, heightDp: Float, g: GridGeometry): Boolean =
    widthDp > 0f && heightDp > 0f && g.tileW > 0f && g.tileH >= GRID_TILE_MIN_DP && g.columns > 0

/**
 * Tile [index]'s top-left in the rows' frame, dp, from column 0's x (the still frame's x or the slide's) and the top row:
 * [GridMode.STILL] row-major over [GridGeometry.columns], [GridMode.SLIDING] column-major over [GridGeometry.rows].
 */
internal fun gridTileOffset(index: Int, mode: GridMode, g: GridGeometry): Pair<Float, Float> {
    val col = if (mode == GridMode.STILL) index % g.columns else index / g.rows
    val row = if (mode == GridMode.STILL) index / g.columns else index % g.rows
    return Pair(col * (g.tileW + g.gap), row * (g.tileH + g.gap))
}

/**
 * The columns a sliding grid composes with column 0 at [shiftX] (dp): those overlapping the viewport [widthDp], plus one
 * on each side (so a column sliding in is already there), clamped to the grid's columns. Empty for no tiles.
 */
internal fun gridVisibleColumns(shiftX: Float, count: Int, g: GridGeometry, widthDp: Float): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val pitch = g.tileW + g.gap
    val last = (count - 1) / g.rows
    val first = floor((-shiftX - g.tileW) / pitch).toInt() + 1       // the first column whose right edge is past 0
    val end = ceil((widthDp - shiftX) / pitch).toInt() - 1           // the last column whose left edge is before the width
    return (first - 1).coerceIn(0, last)..(end + 1).coerceIn(0, last)
}

/**
 * An eased value that animates to [target] ([ms], [NxMenuEasing]) and snaps to it whenever [reset] changes (a new tab or
 * list, a new geometry), so a new set of tiles never glides in from the old one's place. A swipe's release already
 * settling on [target] keeps its spring ([easeUnlessSettling]).
 */
@Composable
private fun rememberEased(target: Float, reset: Any, ms: Int): Animatable<Float, AnimationVector1D> {
    val a = remember(reset) { Animatable(target) }
    LaunchedEffect(a, target) { a.easeUnlessSettling(target, tween(ms, easing = NxMenuEasing)) }
    return a
}

/**
 * [rememberEased] whose target is read from State outside composition (smooth scrolling at 120 Hz): it eases to
 * [target] ([ms], [NxMenuEasing]) each time that changes, starting at it (and snapping to it whenever [reset] changes).
 * Nothing recomposes for a new target: the caller reads the value in a layer, or through a derived State that changes
 * only at the ends of a fade.
 */
@Composable
private fun rememberEasedOf(reset: Any, ms: Int, target: () -> Float): Animatable<Float, AnimationVector1D> {
    val a = remember(reset) { Animatable(Snapshot.withoutReadObservation(target)) }
    val current = rememberUpdatedState(target)
    LaunchedEffect(a) {
        snapshotFlow { current.value() }.collectLatest { t ->
            if (a.isRunning || a.value != t) a.easeUnlessSettling(t, tween(ms, easing = NxMenuEasing))
        }
    }
    return a
}

/**
 * The Grid style: [tiles] in two rows on a phone (three in the tall portrait, the Flip, of two or three columns whose
 * selection centres once it scrolls; 3–4 on a tablet-class window, [LocalWindowSize]; [gridFor]), still when they fit those rows, else in
 * columns sliding sideways to keep the selection anchored. The selected tile lights up in place ([GRID_LIT_MS]: the
 * "Logo" look for a logo, tool or collection, a [NX_TILE_RING_W] dp ring for a game; the old one fades back); columns cut
 * off at an edge are shaded and an edge with more to scroll fades to black. Reports its column and row counts through
 * [onColumns] once laid out (the d-pad steps by tiles from then on).
 * A tap selects a tile, a tap on the selected one opens it, a long press calls [onLongPress]. A sliding grid also follows
 * a horizontal drag 1:1 and, on release, carries on and snaps to a column ([gridScrollSnap]): a swipe only scrolls, the
 * selection (lit, and what A and the hints act on) never changes and may scroll out of view. A tap selects without
 * moving the row; the next d-pad press ([LocalGridPresses]) brings the selection back to where the d-pad puts it
 * ([gridShiftTarget]).
 * [games]: a game list's grid: wide tiles ([gridFor]'s games, the tall portrait's aspect) with the bottom shade on the
 * lit game with art only, fading with its lit face ([NxMenuTile]'s gameFade).
 * [layoutKey]: the owner of this grid (the current Screen instance); a new key re-reports the columns even when the tiles
 * and columns are equal, as after AppState resets its column count on re-showing the same list in place.
 * A sliding grid composes only the columns in view (and one each side), so a long game list loads only those screenshots.
 * The rows keep [GRID_RING_ROOM] clear above and below (the body clips, for the slide) and are centred in the body,
 * between the header and the hint bar (a Grid has no info band); a still grid centres only the rows it fills ([gridUsedRows]).
 * [titleRoom]: the hidden page title's room at the top of this box: sized without it ([gridSizingBody]), centred with
 * it, so a hidden title moves the rows up by half its height and nothing else changes.
 * [consoles]: the main menu's Consoles tab: half-width tiles in a phone's two-row portrait, the selection
 * centred once it scrolls ([gridFor]).
 */
@Composable
fun NxTileGrid(
    tiles: List<GridTile>, selectedIndex: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    onColumns: (columns: Int, rows: Int) -> Unit,
    tileInfo: (Int) -> List<InfoLine>,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    games: Boolean = false,
    titleRoom: Dp = 0.dp,
    consoles: Boolean = false,
) {
    val colors = LocalNxColors.current
    val portrait = LocalPortrait.current
    val window = LocalWindowSize.current
    val presses = LocalGridPresses.current
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val width = maxWidth.value
        val height = maxHeight.value
        // Sized with ring room above and below (the clip never cuts a selected game's ring), then centred by gridRowsTop.
        // A hidden title's room at the box's top ([titleRoom]) is left out of the sizing (the shown title's
        // tiles and rows) but not the centring: the rows sit in the middle of the whole box, which the clip covers.
        val g = gridFor(window.width.value, window.height.value, width, gridSizingBody(height, titleRoom.value), portrait, games = games, consoles = consoles)
        if (!gridDrawable(width, height, g)) return@BoxWithConstraints
        val report by rememberUpdatedState(onColumns)
        LaunchedEffect(layoutKey, tiles, g.columns, g.rows) { report(g.columns, g.rows) }

        val count = tiles.size
        val mode = gridModeFor(count, g.columns, g.rows)
        val identity = remember(tiles) { gridTileIdentity(tiles) }   // built once per tile list, not per recomposition
        val reset = remember(identity, g, width) { Triple(identity, g, width) }   // a rescan's new counts keep the eased state
        // For smooth scrolling at 120 Hz the selection is read through [selection] where it is used (the slide's target,
        // the lit tile, a tap), and the tiles follow the lit and cut states outside composition ([rememberEasedOf]), so a
        // step does not recompose every tile with this body.
        val selection = rememberUpdatedState(selectedIndex)
        fun sel(): Int = selection.value().coerceIn(0, maxOf(0, count - 1))
        // A swipe's or a tap's hold on the row, apart from the selection: in force until the next d-pad press.
        val scroll = remember(reset) { mutableStateOf<GridScroll?>(null) }
        ClearStaleScroll(scroll, presses)
        // Where the row rests: a still grid's place, else the swipe's or tap's hold, else the selection's column.
        val target = remember(reset, mode, count) {
            derivedStateOf {
                if (mode == GridMode.STILL) gridStillX(count, g, width)
                else gridShiftTarget(gridScrollAfter(scroll.value, presses.value), presses.value, sel(), count, g, width)
            }
        }
        val shiftTarget = target.value
        val shift = rememberEased(shiftTarget, reset, SLIDE_MS)
        // While a finger drags a sliding grid: the slide it shows (NaN otherwise, the eased shift).
        val dragX = remember(reset) { mutableFloatStateOf(Float.NaN) }
        fun slide(): Float = dragX.floatValue.takeUnless { it.isNaN() } ?: shift.value
        // The slide the cut shading and edge fades follow: the finger's while it drags, else the target.
        val fadeX = remember(reset) { derivedStateOf { dragX.floatValue.takeUnless { it.isNaN() } } }

        val litIndex = remember(reset) { derivedStateOf { sel() } }
        val select by rememberUpdatedState(onSelect)
        val open by rememberUpdatedState(onOpen)
        val tapOpens by rememberUpdatedState(LocalNxTapOpens.current)
        val longPress by rememberUpdatedState(onLongPress)
        val hasLongPress = onLongPress != null
        // A tap on another tile selects it in place: a sliding row stays where it is (a swipe's scroll or not).
        val tapSelect by rememberUpdatedState { i: Int ->
            if (mode == GridMode.SLIDING) scroll.value = gridScrollOnTap(scroll.value, presses.value, target.value)
            select(i)
        }
        // One object for every tile's taps, reading the selection and callbacks when a tap lands, so no tile recomposes
        // for a new callback or selection.
        val taps = remember { GridTaps({ sel() }, { i -> tapOpens(i) }, { i -> tapSelect(i) }, { i -> open(i) }, { i -> longPress?.invoke(i) }) }
        val info = rememberUpdatedState(tileInfo)

        // Sliding: only the columns in view at the target and at the current slide (the window follows the slide as it
        // crosses a column, so a column leaving is kept until it is out of view). Still: at most rows x columns, all of them.
        val window: IntRange = if (mode == GridMode.STILL) tiles.indices else {
            val atTarget = gridVisibleColumns(shiftTarget, count, g, width)
            val atShift by remember(reset) { derivedStateOf { gridVisibleColumns(slide(), count, g, width) } }
            val cols = minOf(atTarget.first, atShift.first)..maxOf(atTarget.last, atShift.last)
            (cols.first * g.rows)..minOf(count - 1, cols.last * g.rows + g.rows - 1)
        }

        // A still grid centres the rows it fills (one row sits at the vertical centre); a sliding one all its rows.
        val used = gridUsedRows(count, mode, g)
        val rowsH = used * g.tileH + (used - 1) * g.gap
        val rowsTop = gridRowsTop(height, g.tileH, g.gap, used)
        // A tablet's tile is the landscape one; the tall portrait's wide tile takes the portrait type.
        val portraitType = portrait && (g.rows == 2 || g.tallPortrait)
        val logoInset = remember(g, portraitType) { gridLogoInset(g, portraitType) }
        val swipe = if (mode != GridMode.SLIDING) Modifier else Modifier.horizontalSwipe(
            key = reset, value = shift, live = dragX, perDp = 1f,
            lo = gridSlideX(count - 1, count, g, width), hi = gridSlideX(0, count, g, width), give = (g.tileW + g.gap) / 2,
            ms = SLIDE_MS,
            // A release scrolls: the row settles on the snapped rest and stays there; the selection is kept.
            target = { start, drag, velocity ->
                val rest = gridScrollSnap(start, drag, velocity, count, g, width)
                scroll.value = GridScroll(presses.value, rest)
                sel() to rest
            },
            onSelect = {},
        )
        Box(
            // The swipe before the slide's layer: the finger is tracked in a frame that doesn't move with the row.
            Modifier.align(Alignment.TopStart).offset(y = rowsTop.dp).fillMaxWidth().height(rowsH.dp)
                .then(swipe)
                .graphicsLayer { translationX = slide().dp.toPx() },
        ) {
            for (i in window) {
                key(i) {
                    GridCell(
                        tiles[i], i, mode, g, width, reset, litIndex, target, fadeX, info, logoInset, portraitType, games, taps, hasLongPress,
                    )
                }
            }
        }

        if (mode == GridMode.SLIDING) {
            val edges by remember(reset, shiftTarget) { derivedStateOf { gridEdges(fadeX.value ?: shiftTarget, count, g, width) } }
            val (moreLeft, moreRight) = edges
            val left = rememberEased(if (moreLeft) 1f else 0f, reset, SLIDE_MS)
            val right = rememberEased(if (moreRight) 1f else 0f, reset, SLIDE_MS)
            val clear = colors.background.copy(alpha = 0f)   // role: a fade gradient's clear end
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(EDGE_SHARE)
                    .graphicsLayer { alpha = left.value }
                    .background(Brush.horizontalGradient(listOf(colors.background, clear))),
            )
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(EDGE_SHARE)
                    .graphicsLayer { alpha = right.value }
                    .background(Brush.horizontalGradient(listOf(clear, colors.background))),
            )
        }
    }
}

/** Drops a swipe's or tap's hold on the row once a d-pad press has come ([gridScrollAfter]); its own scope. */
@Composable
private fun ClearStaleScroll(scroll: MutableState<GridScroll?>, presses: State<Int>) {
    val scrolled = gridScrollAfter(scroll.value, presses.value)
    SideEffect { if (scrolled == null) scroll.value = null }
}

/** A Grid's tile taps, read when a tap lands ([nxTap]): one object for the whole grid. */
@Stable
private class GridTaps(
    val selected: () -> Int, val opens: (Int) -> Boolean, val select: (Int) -> Unit, val open: (Int) -> Unit, val longPress: (Int) -> Unit,
) {
    fun tap(i: Int) = nxTap(i, i == selected(), opens(i), select, open)
}

/**
 * One Grid cell at its place in the rows' frame: its tile lit in place ([GridTileFace]) and, on a sliding grid, the
 * cut-column shade when it is cut off at an edge ([gridColumnCut], following the finger's slide or the [target]). Every
 * input is stable or a State, so a selection step or a new slide target recomposes only the tiles whose lit or cut
 * state changed.
 */
@Composable
private fun GridCell(
    tile: GridTile, i: Int, mode: GridMode, g: GridGeometry, width: Float, reset: Any, litIndex: State<Int>,
    target: State<Float>, fadeX: State<Float?>, tileInfo: State<(Int) -> List<InfoLine>>, logoInset: Pair<Float, Float>,
    portrait: Boolean, games: Boolean, taps: GridTaps, hasLongPress: Boolean,
) {
    val colors = LocalNxColors.current
    val (x, y) = gridTileOffset(i, mode, g)
    // The cut shade eases in or out as the column crosses an edge, following the finger's slide or the target; read
    // outside composition ([rememberEasedOf]), so a step or a drag recomposes no tile.
    val shade = rememberEasedOf(reset, SLIDE_MS) {
        if (mode == GridMode.SLIDING && gridColumnCut((fadeX.value ?: target.value) + x, g, width)) CUT_ALPHA else 0f
    }
    Box(
        Modifier.offset(x.dp, y.dp).size(g.tileW.dp, g.tileH.dp)
            .pointerInput(i, hasLongPress) {
                detectTapGestures(
                    onTap = { taps.tap(i) },
                    onLongPress = if (hasLongPress) { _ -> taps.longPress(i) } else null,
                )
            },
    ) {
        GridTileFace(
            tile, i, litIndex, reset, info = remember(i) { { tileInfo.value(i) } }, logoInset = logoInset,
            portrait = portrait, gameFade = games,
        )
        // The cut-column shade, on the tile's own rounded shape (NxMenuTile clips itself; this box is not).
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(NX_TILE_CORNER.dp))
                .graphicsLayer { alpha = shade.value }.background(colors.background),
        )
    }
}

/**
 * One Grid tile, lit in place: unlit, and over it the lit tile (the "Logo" look's outline, or a game's ring) faded in over
 * [GRID_LIT_MS] as it becomes the selection ([litIndex]) and out as it stops being it. The fade is read outside
 * composition and drawn as a layer alpha; a face is composed only while it shows, so a step recomposes only the tiles
 * whose face appears or goes.
 */
@Composable
private fun GridTileFace(
    tile: GridTile, i: Int, litIndex: State<Int>, reset: Any, info: () -> List<InfoLine>, logoInset: Pair<Float, Float>,
    portrait: Boolean, gameFade: Boolean,
) {
    // Lit when it is the selection ([gridTileLit]), read outside composition: a step recomposes only the two tiles
    // whose face shows or hides as their fade crosses 0 or 1.
    val amount = rememberEasedOf(reset, GRID_LIT_MS) { if (gridTileLit(i, litIndex.value)) 1f else 0f }
    val unlitShows by remember(amount) { derivedStateOf { amount.value < 1f } }
    val litShows by remember(amount) { derivedStateOf { amount.value > 0f } }
    // A game list's names follow Appearance → Text size, its stats at 80% of them (as the Carousel's and Backdrop's).
    val titleSize = if (gameFade) 14.sp * nxTextScale() else 14.sp
    val statsSize = if (gameFade) titleSize * 0.8f else 13.sp
    if (unlitShows) NxMenuTile(
        tile, lit = false, caption = true, info = info, logoInset = logoInset, portrait = portrait,
        modifier = Modifier.fillMaxSize(), titleSize = titleSize, outlineLit = true, gameFade = gameFade, statsSize = statsSize,
    )
    if (litShows) NxMenuTile(
        tile, lit = true, caption = true, info = info, logoInset = logoInset, portrait = portrait,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = amount.value }, titleSize = titleSize, outlineLit = true, gameFade = gameFade, statsSize = statsSize,
    )
}
