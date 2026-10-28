package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.InfoLine

/** Below this tile height (dp) the grid neither draws nor reports its columns: a layout pass not yet at its size. */
internal const val GRID_TILE_MIN_DP = 40f

/** Whether a body [widthDp] × [heightDp] with grid [g] is a real layout (not a zero or tiny pass) to draw and report. */
internal fun gridDrawable(widthDp: Float, heightDp: Float, g: GridGeometry): Boolean =
    widthDp > 0f && heightDp > 0f && g.tileW > 0f && g.tileH >= GRID_TILE_MIN_DP && g.columns > 0

/** A Grid's edge rows while more is beyond them: drawn at this, on their own look. */
private val GRID_EDGE_STEPS = listOf(0.4f)

/**
 * The Grid style (main menu tabs, game lists), held either way: [tiles] row by row, scrolling up and down only
 * ([gridVertical]: columns from the width and the tiles' least width, at least 3 rows filling the box, a gap under its top). It rests on a
 * row; while more rows are beyond an edge the row at it is drawn at [GRID_EDGE_STEPS]; the selection stops on the second
 * row from an edge so it is never in it ([homeListScroll]). A grid that fits sits on the box's bottom, its last row a gap
 * over what is under it. Sideways swipes are the main menu's (they switch tabs); Carousels alone scroll sideways.
 * Reports its columns through [onColumns], so the d-pad steps by whole rows ([gridStep]).
 * A game is drawn in colour while selected, the rest grey ([NxMenuTile]'s drained); a console, collection or tool as its
 * mark ([MenuMarkCell]), a console's logo alone on the Consoles tab ([consoles]). A tap selects, a tap on the selected one opens it, a long press calls
 * [onLongPress]. [games]: a game list's wide tiles, the bottom shade on the lit game with art.
 * [layoutKey]: the owner of this grid; a new key re-reports the columns.
 */
@Composable
fun NxTileGrid(
    tiles: List<GridTile>, selectedIndex: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    onColumns: (columns: Int) -> Unit,
    tileInfo: (Int) -> List<InfoLine>,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    games: Boolean = false,
    consoles: Boolean = false,
) {
    val portrait = LocalPortrait.current
    val window = LocalWindowSize.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        // Columns from the width and the tiles' least width; rows the height they have held sideways ([gridMatched]).
        val kind = when { games -> GridTileKind.SCREENSHOT; consoles -> GridTileKind.LOGO; else -> GridTileKind.ICON }
        // Sized above the fixed gap over the tab row ([GRID_TAB_GAP], the last row's own gap part of it).
        val g = gridMatched(width, height - (GRID_TAB_GAP - GRID_GAP), window.width.value, window.height.value, kind, portrait)
        if (!gridDrawable(width, height, g)) return@BoxWithConstraints
        val columns = g.columns
        val rows = (tiles.size + columns - 1) / columns
        LaunchedEffect(layoutKey, tiles, columns, onColumns) { onColumns(columns) }   // again for a new reporter (the page now showing)

        val tapOpens = LocalNxTapOpens.current
        val select by rememberUpdatedState(onSelect)
        val open by rememberUpdatedState(onOpen)
        val longPress by rememberUpdatedState(onLongPress)
        val info = rememberUpdatedState(tileInfo)
        val selection = rememberUpdatedState(selectedIndex)
        val state = rememberLazyListState()
        val sel = selectedIndex()

        // Whole px, from the band itself: [gridVertical]'s rows fill it exactly, so a row a fraction of a px too tall
        // (rounding its dp) would drop the last one from view.
        val gapPx = with(density) { g.gap.dp.roundToPx() }
        // A gap under the box's top; at its bottom the fixed [GRID_TAB_GAP], the last row's own gap part of it.
        val tabGapPx = with(density) { (GRID_TAB_GAP - g.gap).dp.roundToPx() }
        val band = (constraints.maxHeight - gapPx - tabGapPx).coerceAtLeast(g.rows)
        // A row's px: the band's share (the rows fill it), or a fixed-height tile's own when that is less (the pad case).
        val pitchPx = minOf(band / g.rows, with(density) { (g.tileH + g.gap).dp.roundToPx() })
        val tileH = with(density) { (pitchPx - gapPx).toDp() }
        val heights = List(rows) { pitchPx }
        val fits = rows * pitchPx <= band
        val whole = homeListViewport(band, rows * pitchPx, pitchPx)
        // Fewer than three rows in view (the pad under the page, scrolling): the selected row centred in the whole band,
        // half a row showing either side, its outer quarter faded where more is that way ([nxEdgeFade]), never on it.
        val roomy = fits || whole / maxOf(1, pitchPx) >= GRID_MIN_ROWS
        val viewport = if (roomy) whole else band
        val placed = remember(state) { BooleanArray(1) }
        LaunchedEffect(sel / columns, rows, viewport, roomy) {
            val row = sel / columns
            if (row !in 0 until rows) return@LaunchedEffect
            snapshotFlow { state.layoutInfo }.first { it.totalItemsCount == rows && it.visibleItemsInfo.isNotEmpty() }
            val current = state.firstVisibleItemIndex * pitchPx + state.firstVisibleItemScrollOffset
            // Roomy: the selection kept clear of the faded row, moving freely until the second row from an edge.
            val target = if (roomy) homeListScroll(heights, { true }, row, viewport, current, margin = 1)
                else gridCentredScroll(row, rows, pitchPx, viewport)
            val (index, offset) = homeListAnchor(heights, target)
            val animate = placed[0]
            placed[0] = true
            if (target != current) { if (animate) state.animateScrollToItem(index, offset) else state.scrollToItem(index, offset) }
        }
        // The pad case's edge fade: a quarter row at each edge, so the half rows either side of the selection still read.
        val edgeFade = with(density) { (pitchPx / 4).toDp() }
        LazyColumn(
            Modifier.align(if (fits) Alignment.BottomStart else Alignment.TopStart)
                .padding(top = if (fits) 0.dp else g.gap.dp, bottom = if (fits) with(density) { tabGapPx.toDp() } else 0.dp)
                .fillMaxWidth().height(with(density) { viewport.toDp() })
                .then(if (roomy) Modifier.nxRowEdgeMask(state) else Modifier.nxEdgeFade({ state.canScrollBackward }, { state.canScrollForward }, edgeFade)),
            state = state, verticalArrangement = Arrangement.Bottom,
            flingBehavior = if (roomy) rememberNxRowSnapFling(state) else rememberSnapFlingBehavior(state, SnapPosition.Center),
        ) {
            items(rows) { r ->
                // Each row its cells and the gap under them (the last row's: its gap over what is under the grid).
                Row(
                    Modifier.fillMaxWidth().height(with(density) { pitchPx.toDp() }).then(if (roomy) Modifier.nxRowFade(state, r, GRID_EDGE_STEPS) else Modifier)
                        .padding(start = g.gutter.dp, end = g.gutter.dp, bottom = g.gap.dp),
                    horizontalArrangement = Arrangement.spacedBy(g.gap.dp),
                ) {
                    for (c in 0 until columns) {
                        val i = r * columns + c
                        val tile = tiles.getOrNull(i) ?: break
                        Box(
                            Modifier.size(g.tileW.dp, tileH).pointerInput(i, onLongPress != null) {
                                detectTapGestures(
                                    onTap = { nxTap(i, i == selection.value(), tapOpens(i), select, open) },
                                    onLongPress = if (onLongPress != null) { _ -> longPress?.invoke(i) } else null,
                                )
                            },
                        ) {
                            // A game: its picture, grey unless selected ([NxMenuTile]'s drained); a console, collection
                            // or tool: its mark alone ([MenuMarkCell]).
                            if (tile is GridTile.Game) NxMenuTile(
                                // Every tile's name, as Home's (dimmed unless selected); the stats only under the selected one.
                                tile, lit = i == sel, caption = true, info = remember(i) { { if (i == selection.value()) info.value(i) else emptyList() } },
                                logoInset = 0f to 0f, portrait = portrait, modifier = Modifier.fillMaxSize(), titleSize = 14.sp * nxTextScale(),
                                gameFade = games, statsSize = 14.sp * nxTextScale() * 0.8f, drained = true, captionAlways = true,
                            )
                            else MenuMarkCell(tile, i == sel, g.tileW, tileH.value)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The scroll (px) that centres row [row] of [rows] [pitch] px rows in a [viewport] px tall, held to the list's ends (the
 * first rows rest at the top, the last at the bottom).
 */
internal fun gridCentredScroll(row: Int, rows: Int, pitch: Int, viewport: Int): Int =
    (row * pitch - (viewport - pitch) / 2).coerceIn(0, (rows * pitch - viewport).coerceAtLeast(0))
