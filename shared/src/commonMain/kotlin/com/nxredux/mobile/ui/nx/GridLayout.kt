package com.nxredux.mobile.ui.nx

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A Grid tab: [STILL] when its items fit its rows (filled top row first), else [SLIDING] (columns, sideways). */
enum class GridMode { STILL, SLIDING }

internal fun gridModeFor(count: Int, columns: Int, rows: Int = 2): GridMode =
    if (count <= rows * columns) GridMode.STILL else GridMode.SLIDING

/** A tablet-class window: its shortest side is [GRID_TABLET_MIN_DP] or more (the unfolded Fold, a tablet). */
internal const val GRID_TABLET_MIN_DP = 600f

/** Whether a window [widthDp] × [heightDp] (the measured app area) is tablet-class: it gets 3–4 Grid rows. */
internal fun gridTabletClass(widthDp: Float, heightDp: Float): Boolean = min(widthDp, heightDp) >= GRID_TABLET_MIN_DP

/** The smallest share of the 154 dp tile a tablet's 3–4 rows may shrink to; below it the grid keeps the phone's 2 rows. */
internal const val GRID_TABLET_MIN_TILE = 0.9f

/**
 * A Grid body [bodyDp] tall's row count: 2 on a phone; on a [tablet] as many rows of the phone landscape tile as fit
 * with [GRID_RING_ROOM] above and below, floor((body − 2·4 + 14) / (154 + 14)), clamped to 3–4, while those rows keep
 * the tile at [GRID_TABLET_MIN_TILE] × 154 or more (a split-screen, freeform or padded body too short: 2, the phone grid).
 */
internal fun gridRowsFor(tablet: Boolean, bodyDp: Float): Int {
    if (!tablet) return 2
    val sizing = bodyDp - 2 * GRID_RING_ROOM
    val rows = floor((sizing + GAP) / (LAND_H + GAP)).toInt().coerceIn(3, 4)
    val tile = min(LAND_H, (sizing - (rows - 1) * GAP) / rows)
    return if (tile >= GRID_TABLET_MIN_TILE * LAND_H) rows else 2
}

/** The smallest share of the 128 dp portrait tile the tall portrait's three rows may shrink to ([gridTallPortrait]). */
internal const val GRID_TALL_MIN_TILE = 0.9f

/**
 * Whether a phone's portrait Grid sized [sizingH] tall ([gridSizingHeight]) is the tall portrait (the Flip): three rows
 * of the portrait tile fit at [GRID_TALL_MIN_TILE] × 128 dp or more, a sizing of 373.6 dp (a body of 381.6) or more.
 * The Fold cover's body (about 290) is far below it.
 */
internal fun gridTallPortrait(sizingH: Float): Boolean = sizingH - 2 * GAP >= 3 * GRID_TALL_MIN_TILE * PORT_H - 0.01f

/** The tall portrait's tiles are at least this wide, dp ([gridTallColumns]). */
internal const val GRID_TALL_MIN_TILE_W = 110f

/** The tall portrait's columns on a body [bodyW] wide: as many as fit at [GRID_TALL_MIN_TILE_W] between the [gutter]s, 2..3. */
internal fun gridTallColumns(bodyW: Float, gutter: Float): Int =
    floor((bodyW - 2 * gutter + GAP) / (GRID_TALL_MIN_TILE_W + GAP)).toInt().coerceIn(2, 3)

/** A game list's Grid tile, width over height (182.6 × 116.7, the Flip's former tall portrait tile), on every screen. */
internal const val GRID_GAME_TILE_AR = 182.6f / 116.7f

/**
 * The grid for a window [windowW] × [windowH] and a body [bodyW] × [bodyH] (dp): [gridRowsFor] its class, sized with
 * [gridSizingHeight]. A phone gets exactly [gridGeometry]'s two-row grid, except in the tall portrait
 * ([gridTallPortrait]): three rows of [gridTallColumns] columns (2 on the Flip, 3 on a wider phone) filling the width,
 * up to [TALL_H] tall. The first columns rest whole on the gutters; scrolled, the selected column is centred
 * ([GridGeometry.anchorX]) with its neighbours peeking in, and the last rests on the far gutter. A tablet keeps its own rows.
 * [games] (a game list): outside the tall portrait, each tile keeps [gridGeometry]'s height and is [GRID_GAME_TILE_AR]
 * times as wide, with as many columns as fit between the gutters ([wideGameTiles]).
 * [consoles] (the main menu's Consoles tab, as on the handheld): on a phone's two-row portrait grid only,
 * half-width tiles at the same height, two whole columns at the start, the selection centred once it scrolls
 * ([wideConsoleTiles]); landscape, the tall portrait and a tablet keep their tiles.
 */
internal fun gridFor(
    windowW: Float, windowH: Float, bodyW: Float, bodyH: Float, portrait: Boolean, games: Boolean = false, consoles: Boolean = false,
): GridGeometry {
    val tablet = gridTabletClass(windowW, windowH)
    val sizing = gridSizingHeight(bodyH)
    if (!tablet && portrait && gridTallPortrait(sizing)) {
        val gutter = 16f
        val columns = gridTallColumns(bodyW, gutter)
        val w = (bodyW - 2 * gutter - (columns - 1) * GAP) / columns
        return GridGeometry(
            tileW = w, tileH = min(TALL_H, (sizing - 2 * GAP) / 3), gap = GAP, gutter = gutter, columns = columns, rows = 3,
            anchorX = (bodyW - w) / 2, tallPortrait = true,
        )
    }
    val rows = gridRowsFor(tablet, bodyH)
    val g = gridGeometry(bodyW, sizing, portrait, rows)
    return when {
        games -> g.wideGameTiles(bodyW)
        consoles && portrait && rows == 2 -> g.wideConsoleTiles(bodyW)
        else -> g
    }
}

/**
 * [this] phone portrait grid with the Consoles tab's wide tiles, as the handheld's Consoles grid: two across
 * [widthDp] between the gutters, (room − gap) / 2 each, the same height. The first two columns rest whole on the
 * gutters; from the second on the selected column is centred ([GridGeometry.anchorX]) with about half of each neighbour
 * peeking in, and the last rests on the far gutter ([gridSlideX]'s end clamps). [GridGeometry.wideConsoles] widens the
 * logo inset with the tile.
 */
private fun GridGeometry.wideConsoleTiles(widthDp: Float): GridGeometry {
    val w = (widthDp - 2 * gutter - gap) / 2
    return copy(tileW = w, columns = 2, anchorX = (widthDp - w) / 2, wideConsoles = true)
}

/**
 * [this] grid's rows with game list tiles: the same height, [GRID_GAME_TILE_AR] times as wide, as many columns as fit
 * [widthDp] between the gutters (at least one); a tile wider than that room shrinks to it, keeping the aspect.
 */
private fun GridGeometry.wideGameTiles(widthDp: Float): GridGeometry {
    val room = widthDp - 2 * gutter
    val w = min(tileH * GRID_GAME_TILE_AR, room)
    val columns = max(1, floor((room + gap) / (w + gap)).toInt())
    return copy(tileW = w, tileH = w / GRID_GAME_TILE_AR, columns = columns)
}

/**
 * The index a d-pad [button] moves to. Still (row-major, [columns] wide):
 * left/right one tile along the row order, up/down one row, wrapping top <-> bottom in the same column (the last tile
 * when that spot is empty). Sliding (column-major, [rows] rows): left/right a column, up/down within the column,
 * wrapping (with two rows, the column's other row).
 * Returns null only for LEFT past the first item or RIGHT past the last (the main menu switches tab there).
 */
internal fun gridStep(index: Int, count: Int, columns: Int, button: NxButton, rows: Int = 2): Int? {
    if (count <= 0 || columns <= 0) return index
    val last = count - 1
    val r = rows.coerceAtLeast(1)
    return when (gridModeFor(count, columns, r)) {
        GridMode.STILL -> {
            val rows = last / columns + 1
            val col = index % columns
            when (button) {
                NxButton.LEFT -> if (index > 0) index - 1 else null
                NxButton.RIGHT -> if (index < last) index + 1 else null
                NxButton.UP -> if (rows == 1) index else {
                    val row = index / columns
                    val target = (if (row == 0) rows - 1 else row - 1) * columns + col
                    min(target, last)
                }
                NxButton.DOWN -> if (rows == 1) index else {
                    val row = index / columns
                    val target = (if (row == rows - 1) 0 else row + 1) * columns + col
                    min(target, last)
                }
                else -> index
            }
        }
        GridMode.SLIDING -> when (button) {
            NxButton.LEFT -> if (index >= r) index - r else null
            NxButton.RIGHT -> if (index / r < last / r) min(index + r, last) else null
            NxButton.UP, NxButton.DOWN -> {
                val start = index - index % r
                val size = min(r, count - start)
                start + (index - start + if (button == NxButton.DOWN) 1 else size - 1) % size
            }
            else -> index
        }
    }
}

/** Whether [index] is on the grid's top row (still: the first row; sliding: its column's top): UP there stays on the main menu. */
internal fun gridAtTop(index: Int, count: Int, columns: Int, rows: Int = 2): Boolean {
    if (count <= 0 || columns <= 0) return true
    val r = rows.coerceAtLeast(1)
    return when (gridModeFor(count, columns, r)) {
        GridMode.STILL -> index < columns
        GridMode.SLIDING -> index % r == 0
    }
}

/**
 * A grid's measures in dp: tile size, gap, the screen gutter, how many columns fit, and its rows (2 on phones).
 * [anchorX]: a sliding grid's selected-column x in place of the usual half-cut anchor: the Consoles tab's portrait
 * tile and the tall portrait's, centred ([wideConsoleTiles], [gridFor]); null, the usual anchor.
 * [wideConsoles]: the Consoles tab's wide portrait tiles: the logo's side inset grows with the tile ([gridLogoInset]).
 * [tallPortrait]: the tall portrait's three rows ([gridFor]): still the portrait type (its tool and logo sizes).
 */
internal data class GridGeometry(
    val tileW: Float, val tileH: Float, val gap: Float, val gutter: Float, val columns: Int, val rows: Int = 2,
    val anchorX: Float? = null, val wideConsoles: Boolean = false, val tallPortrait: Boolean = false,
)

private const val GAP = 14f
private const val LAND_W = 140f
private const val LAND_H = 154f
private const val PORT_H = 128f
/** The tall portrait's tile height at most ([gridFor]), dp. */
private const val TALL_H = 117.3f
private const val PORT_W = 138f   // the full-size portrait tile's width (the geometry fills the width, about this)
private const val LOGO_SIDE = 22f
private const val LOGO_VERTICAL = 30f

/**
 * A console logo's inset in its tile, (sides, top and bottom): 22 × 30 dp at the full-size tile (140 × 154 landscape,
 * 138 × 128 portrait), each scaled down with the tile's width or height when the tile is smaller; never grown. The
 * Consoles tab's wide portrait tile ([GridGeometry.wideConsoles]): the sides keep their share of the full-size tile's
 * width (grown with it too), so the logo keeps its proportion of the tile instead of filling the extra width.
 */
internal fun gridLogoInset(g: GridGeometry, portrait: Boolean): Pair<Float, Float> {
    val specW = if (portrait) PORT_W else LAND_W
    val specH = if (portrait) PORT_H else LAND_H
    val side = if (g.wideConsoles) g.tileW / specW else min(1f, g.tileW / specW)
    return Pair(LOGO_SIDE * side, LOGO_VERTICAL * min(1f, g.tileH / specH))
}

/**
 * A logo's height as drawn (Fit) in a box [boxW] × [boxH]: its [aspect] (height over width) at the box's width, capped
 * at the box's height. A null [aspect] (no logo, or its size unknown) is half the box.
 */
internal fun logoDrawnHeight(boxW: Float, boxH: Float, aspect: Float?): Float = aspect?.let { min(boxH, boxW * it) } ?: (boxH * 0.5f)

/** The top of a line [gap] under a logo [drawnH] tall drawn centred in a box [boxH] tall at [boxTop]. */
internal fun underLogoTop(boxTop: Float, boxH: Float, drawnH: Float, gap: Float): Float = boxTop + (boxH + drawnH) / 2 + gap

/**
 * The grid for a body [widthDp] × [heightDp] (under the header, above the hint bar; less the ring room, [gridSizingHeight]). Landscape: 140 × 154 tiles,
 * shrunk (keeping their aspect) when two rows don't fit the height; as many columns as fit between the gutters.
 * Portrait: three columns that fill the width exactly, 128 tall (or less to fit two rows).
 * More than two [rows] (a tablet-class screen, [gridRowsFor]): the phone landscape tile in either orientation, shrunk
 * (keeping its aspect) only when that many rows don't fit, with as many columns as fit between the landscape gutters.
 */
internal fun gridGeometry(widthDp: Float, heightDp: Float, portrait: Boolean, rows: Int = 2): GridGeometry {
    if (rows > 2) {
        val h = min(LAND_H, (heightDp - (rows - 1) * GAP) / rows)
        val w = LAND_W * h / LAND_H
        val gutter = 24f
        val columns = max(1, floor((widthDp - 2 * gutter + GAP) / (w + GAP)).toInt())
        return GridGeometry(tileW = w, tileH = h, gap = GAP, gutter = gutter, columns = columns, rows = rows)
    }
    val gutter = if (portrait) 16f else 24f
    val fitH = (heightDp - GAP) / 2
    return if (portrait) {
        val w = (widthDp - 2 * gutter - 2 * GAP) / 3
        GridGeometry(tileW = w, tileH = min(PORT_H, fitH), gap = GAP, gutter = gutter, columns = 3)
    } else {
        val h = min(LAND_H, fitH)
        val w = LAND_W * h / LAND_H
        val columns = max(1, floor((widthDp - 2 * gutter + GAP) / (w + GAP)).toInt())
        GridGeometry(tileW = w, tileH = h, gap = GAP, gutter = gutter, columns = columns)
    }
}

/** The room kept clear above the top row and below the bottom row: the selected game's ring and 1 dp, so the body's clip never cuts it. */
internal const val GRID_RING_ROOM = NX_TILE_RING + 1f

/**
 * The height a Grid body [bodyDp] tall (from the header down to the hint bar: a Grid has no info band) hands
 * [gridGeometry]: less [GRID_RING_ROOM] above and below the rows, so fitted rows shrink enough to be centred by [gridRowsTop].
 */
internal fun gridSizingHeight(bodyDp: Float): Float = bodyDp - 2 * GRID_RING_ROOM

/**
 * The body a Grid is sized from ([gridFor], [gridSizingHeight]) when its box [boxDp] tall also holds a hidden
 * page title's room [titleRoomDp] at its top (Layouts → Page title Hide): the box less that room, so the tiles and the
 * row count stay exactly those of the shown title. The rows are then centred in the whole box ([gridRowsTop] of [boxDp]),
 * from where the title would be down to the hint bar (or its kept room). No room (the title shown): the box itself.
 */
internal fun gridSizingBody(boxDp: Float, titleRoomDp: Float): Float = titleRoomSizingBody(boxDp, titleRoomDp)

/**
 * The [rows]' top y in a Grid body [bodyDp] tall: centred in the body (between the header and the hint bar), but kept
 * inside it with [GRID_RING_ROOM] above and below them.
 */
internal fun gridRowsTop(bodyDp: Float, tileH: Float, gap: Float, rows: Int = 2): Float {
    val rowsH = rows * tileH + (rows - 1) * gap
    val centred = (bodyDp - rowsH) / 2
    return max(GRID_RING_ROOM, min(centred, bodyDp - GRID_RING_ROOM - rowsH))
}

/**
 * The rows a grid of [count] items centres ([gridRowsTop]): a still grid only those it fills (top first, at least one),
 * so a single row sits at the body's vertical centre; a sliding grid all its [GridGeometry.rows].
 */
internal fun gridUsedRows(count: Int, mode: GridMode, g: GridGeometry): Int =
    if (mode == GridMode.STILL) min(g.rows, max(1, (count + g.columns - 1) / g.columns.coerceAtLeast(1))) else g.rows

/** A still grid's column-0 x: the full [GridGeometry.columns] frame centred, so a short row starts in the left column. */
internal fun gridStillX(count: Int, g: GridGeometry, widthDp: Float): Float = (widthDp - (g.columns * (g.tileW + g.gap) - g.gap)) / 2

/**
 * A sliding grid's column-0 x for the selection at [index]: its column anchored at gutter + 0.55 × tile + gap (the
 * previous column peeks in half-cut; [GridGeometry.anchorX] in its place when set), never sliding past the first column
 * (on the gutter) or the last (on the far gutter).
 */
internal fun gridSlideX(index: Int, count: Int, g: GridGeometry, widthDp: Float): Float {
    val content = gridSlideColumns(count, g) * (g.tileW + g.gap) - g.gap
    if (content <= widthDp - 2 * g.gutter) return g.gutter
    return max(widthDp - g.gutter - content, min(g.gutter, gridAnchorX(index / g.rows, g)))
}

/** Column 0's x with column [col] on the anchor, unclamped: [gridSlideX] before it stops at either end. */
internal fun gridAnchorX(col: Int, g: GridGeometry): Float =
    (g.anchorX ?: (g.gutter + 0.55f * g.tileW + g.gap)) - col * (g.tileW + g.gap)

/** A column at [columnX] is cut off at a screen edge (it gets the 70% black layer). */
internal fun gridColumnCut(columnX: Float, g: GridGeometry, widthDp: Float): Boolean =
    columnX < g.gutter - 1 || columnX + g.tileW > widthDp - g.gutter + 1

/** Whether there is more to scroll to on the left and on the right (those screen edges fade to black). */
internal fun gridEdges(slideX: Float, count: Int, g: GridGeometry, widthDp: Float): Pair<Boolean, Boolean> {
    val content = gridSlideColumns(count, g) * (g.tileW + g.gap) - g.gap
    return Pair(slideX < g.gutter - 1, slideX + content > widthDp - g.gutter + 1)
}

/** A sliding grid's column count: [count] items column-major over [GridGeometry.rows] rows. */
internal fun gridSlideColumns(count: Int, g: GridGeometry): Int = (count + g.rows - 1) / g.rows
