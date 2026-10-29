package com.nxredux.mobile.ui.nx

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A game list's Grid tile, width over height (182.6 × 116.7, the Flip's former tall portrait tile), on every screen. */
internal const val GRID_GAME_TILE_AR = 182.6f / 116.7f

/**
 * The index a d-pad [button] moves to on a grid [columns] wide, row-major: left/right one tile within the row, up/down
 * one row, wrapping top <-> bottom in the same column (the last tile when that spot is empty).
 * Returns null for LEFT from a row's first column or RIGHT from its last (or the last tile): the main menu switches tab
 * there, a game list stops. [rowEnds] false (the app drawer): left/right run on along the row order instead, null
 * only past the first or the last tile.
 */
internal fun gridStep(index: Int, count: Int, columns: Int, button: NxButton, rowEnds: Boolean = true): Int? {
    if (count <= 0 || columns <= 0) return index
    val last = count - 1
    val rows = last / columns + 1
    val col = index % columns
    return when (button) {
        NxButton.LEFT -> if (if (rowEnds) col > 0 else index > 0) index - 1 else null
        NxButton.RIGHT -> if ((!rowEnds || col < columns - 1) && index < last) index + 1 else null
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

/** Whether [index] is on the grid's top row: UP there stays on the main menu. */
internal fun gridAtTop(index: Int, count: Int, columns: Int): Boolean =
    count <= 0 || columns <= 0 || index < columns

/** A grid's measures in dp: tile size, gap, the screen gutter, how many columns fit, and the rows in view. */
internal data class GridGeometry(
    val tileW: Float, val tileH: Float, val gap: Float, val gutter: Float, val columns: Int, val rows: Int = 2,
)

/**
 * A logo's height as drawn (Fit) in a box [boxW] × [boxH]: its [aspect] (height over width) at the box's width, capped
 * at the box's height. A null [aspect] (no logo, or its size unknown) is half the box.
 */
internal fun logoDrawnHeight(boxW: Float, boxH: Float, aspect: Float?): Float = aspect?.let { min(boxH, boxW * it) } ?: (boxH * 0.5f)

/** The top of a line [gap] under a logo [drawnH] tall drawn centred in a box [boxH] tall at [boxTop]. */
internal fun underLogoTop(boxTop: Float, boxH: Float, drawnH: Float, gap: Float): Float = boxTop + (boxH + drawnH) / 2 + gap

/** The room kept clear above the top row and below the bottom row: the selected game's ring and 1 dp, so the body's clip never cuts it. */
internal const val GRID_RING_ROOM = NX_TILE_RING + 1f

/** The fewest rows a Grid shows at once ([gridVertical]). */
internal const val GRID_MIN_ROWS = 3

/** The fewest columns a Grid shows held sideways, whatever its tiles. */
internal const val GRID_MIN_COLUMNS_SIDEWAYS = 3

/** The gap between a Grid's tiles, both ways. */
internal const val GRID_GAP = 12f

/**
 * What a Grid's tiles show, setting their least width (dp) and shape ([aspect], width over height): a game's screenshot,
 * a console's logo, a tool's icon, or a collection's name (sized as an icon).
 */
enum class GridTileKind(val minW: Float, val aspect: Float) {
    SCREENSHOT(200f, GRID_GAME_TILE_AR), LOGO(136f, 1.2f), ICON(110f, 1f), NAME(110f, 1f),
}

/**
 * The Grid for a box [bodyW] × [bodyH] dp of [kind] tiles: its columns from the width alone, as many as fit at
 * [GridTileKind.minW] with [GRID_GAP] between and [gutter] each side, held sideways ([landscape]) at least
 * [GRID_MIN_COLUMNS_SIDEWAYS] (the cells shrinking rather than dropping one), each stretched to fill the row; then as many
 * rows of that width's shape as fill the box under a gap at its top, at least [GRID_MIN_ROWS], each stretched so whole
 * rows fill it exactly. [GridGeometry.rows] is the rows in view.
 */
internal fun gridVertical(bodyW: Float, bodyH: Float, kind: GridTileKind, landscape: Boolean, gutter: Float): GridGeometry {
    val room = bodyW - 2 * gutter
    val fit = floor((room + GRID_GAP) / (kind.minW + GRID_GAP)).toInt()
    val columns = max(if (landscape) GRID_MIN_COLUMNS_SIDEWAYS else 1, fit)
    val tileW = (room - (columns - 1) * GRID_GAP) / columns
    val band = bodyH - GRID_GAP
    val rows = max(GRID_MIN_ROWS, floor(band / (tileW / kind.aspect + GRID_GAP)).toInt())
    val tileH = band / rows - GRID_GAP
    return GridGeometry(tileW = tileW, tileH = tileH, gap = GRID_GAP, gutter = gutter, columns = columns, rows = rows)
}

/**
 * The Grid for a box [bodyW] × [bodyH] dp in a window [windowW] × [windowH] ([portrait] or not): held sideways,
 * [gridVertical]'s; upright, its columns from its own width but its rows the height the same screen gives them held
 * sideways (the window turned, less what the page keeps around the box now), as many of them as the box holds to the
 * nearest whole row, so a tile is one height either way round. With no real sideways twin (the pad under the page
 * upright takes more than the window turned has), tiles of their own shape at their width, as many rows as fit.
 */
internal fun gridMatched(bodyW: Float, bodyH: Float, windowW: Float, windowH: Float, kind: GridTileKind, portrait: Boolean): GridGeometry {
    if (!portrait) return gridVertical(bodyW, bodyH, kind, landscape = true, gutter = GRID_GUTTER_SIDEWAYS)
    val side = gridVertical(
        max(windowW, windowH) - (windowW - bodyW), min(windowW, windowH) - (windowH - bodyH), kind, landscape = true, gutter = GRID_GUTTER_SIDEWAYS,
    )
    val own = gridVertical(bodyW, bodyH, kind, landscape = false, gutter = GRID_GUTTER_UPRIGHT)
    // The page keeping more than the window turned can spare (the on-screen pad under it upright): no sideways twin to
    // match. At most [gridPadMaxColumns] across, its tiles keep their own shape at their width, a fixed height, held
    // to two rows' room (three would be too short to read; one alone would be the faded edge row); as many whole rows as
    // fit, the rest scrolls.
    if (side.tileH < GRID_TILE_MIN_DP) {
        val columns = min(own.columns, gridPadMaxColumns(kind))
        val tileW = (bodyW - 2 * own.gutter - (columns - 1) * GRID_GAP) / columns
        val band = bodyH - GRID_GAP
        val tileH = min(tileW / kind.aspect, band / GRID_PAD_MIN_ROWS - GRID_GAP)
        return own.copy(tileW = tileW, tileH = tileH, columns = columns, rows = max(GRID_PAD_MIN_ROWS, floor(band / (tileH + GRID_GAP)).toInt()))
    }
    val band = bodyH - GRID_GAP
    val rows = max(GRID_MIN_ROWS, (band / (side.tileH + side.gap)).roundToInt())
    return own.copy(tileH = band / rows - GRID_GAP, rows = rows)
}

/** A Grid's side gutters, upright and sideways: the page's ([PAGE_GUTTER_UPRIGHT], [PAGE_GUTTER_SIDEWAYS]). */
internal const val GRID_GUTTER_UPRIGHT = PAGE_GUTTER_UPRIGHT
internal const val GRID_GUTTER_SIDEWAYS = PAGE_GUTTER_SIDEWAYS

/** Every Grid's fixed room over the tab row (or the page's bottom), dp, on any screen: the tabs stand apart from the tiles. */
internal const val GRID_TAB_GAP = 15f

/** The fewest rows a Grid shows with the pad under it upright ([gridMatched]'s fixed-height tiles). */
internal const val GRID_PAD_MIN_ROWS = 2

/**
 * The most columns a Grid of [kind] shows with the pad under it upright: a small screen's screenshots, logos
 * and names two across, its tools' icons (an icon and a short name) three.
 */
internal fun gridPadMaxColumns(kind: GridTileKind): Int = if (kind == GridTileKind.ICON) 3 else 2
