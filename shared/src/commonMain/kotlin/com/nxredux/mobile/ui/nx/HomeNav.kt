package com.nxredux.mobile.ui.nx

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A block of row tiles moved through as a grid: [count] tiles from pin index [start], [perRow] a row. */
data class HomeBand(val start: Int, val count: Int, val perRow: Int) {
    val end get() = start + count
}

/**
 * Grid Home's focus order as rectangles: [top] (Continue, the inline games, the squares in reading order), then the row
 * tiles [pins] ([perRow] a row), moved through in [bands]: the pinned games, then each shelf at its board's
 * [HomeBoard.shelfCols] ([homeShape]). Flat index i < [topCount] is a top tile, else pin i − topCount.
 */
data class HomeShape(
    val top: List<HomeRect>, val pins: List<HomeRect> = emptyList(), val perRow: Int = 2,
    val bands: List<HomeBand> = if (pins.isEmpty()) emptyList() else listOf(HomeBand(0, pins.size, perRow)),
) {
    val topCount get() = top.size
    val count get() = topCount + pins.size
}

/** Focus memory: the top tile last on ([lastTop]), the top tile left before it ([prevTop], for ties), the last pin. */
data class HomeMemory(val lastTop: Int = 0, val prevTop: Int = -1, val lastPin: Int = -1)

internal fun homeShape(b: HomeBoard): HomeShape {
    val tiles = List(b.rowGames + b.shelfTiles.size) { homeBoardTile(b, it) }
    val bands = buildList {
        if (b.rowGames > 0) add(HomeBand(0, b.rowGames, b.perRow))
        var at = b.rowGames
        for ((i, n) in b.shelfSizes.withIndex()) { add(HomeBand(at, n, b.shelfCols.getOrElse(i) { HOME_SHELF_COLS })); at += n }
    }
    return HomeShape(b.top.map { it.rect }, tiles, b.perRow, bands)
}

private const val E = 0.5f

/**
 * The nearest top tile on [button]'s side that overlaps tile [i] across the move (sideways: in y; up/down: in x).
 * Ties go to [HomeMemory.prevTop], else the top-left one. Null: none that way.
 */
private fun HomeShape.topMove(i: Int, button: NxButton, memory: HomeMemory): Int? {
    val c = top[i]
    val side = button == NxButton.LEFT || button == NxButton.RIGHT
    fun overlaps(t: HomeRect) = if (side) min(t.y + t.h, c.y + c.h) - max(t.y, c.y) > E else min(t.x + t.w, c.x + c.w) - max(t.x, c.x) > E
    fun dist(t: HomeRect) = when (button) {
        NxButton.RIGHT -> t.x - (c.x + c.w)
        NxButton.LEFT -> c.x - (t.x + t.w)
        NxButton.DOWN -> t.y - (c.y + c.h)
        else -> c.y - (t.y + t.h)
    }
    val cand = top.indices.filter { it != i && dist(top[it]) > -E && overlaps(top[it]) }
    if (cand.isEmpty()) return null
    val best = cand.minOf { dist(top[it]) }
    val near = cand.filter { dist(top[it]) < best + E }
    return near.firstOrNull { it == memory.prevTop } ?: near.minWith(compareBy({ top[it].y }, { top[it].x }))
}

/**
 * The first band's first row: its leftmost pin that top tile [i] overlaps in x (as a pin index): a wide tile goes to the first game
 * under it, a tile over two pins to the left one. Overlapping none: the pin nearest the tile's centre.
 */
private fun HomeShape.rowUnder(i: Int): Int {
    val c = top[i]
    val first = bands.firstOrNull() ?: return 0
    val row = (first.start until first.start + min(first.perRow, first.count)).sortedBy { pins[it].x }
    row.firstOrNull { min(pins[it].x + pins[it].w, c.x + c.w) - max(pins[it].x, c.x) > E }?.let { return it }
    val mid = c.x + c.w / 2
    return row.minBy { abs(pins[it].x + pins[it].w / 2 - mid) }
}

/** In [band], the tile of its row [row] whose centre is nearest [x]'s (a pin index); a tie (within [E]) goes to the left one. */
private fun HomeShape.nearestIn(band: HomeBand, row: Int, x: Float): Int {
    val from = band.start + row * band.perRow
    val tiles = from until min(band.end, from + band.perRow)
    fun d(i: Int) = abs(pins[i].x + pins[i].w / 2 - x)
    val best = tiles.minOf { d(it) }
    return tiles.first { d(it) < best + E }
}

/**
 * The next flat index for [button] from [index]: the same index to stay; null past a LEFT / RIGHT edge (switch tab).
 * Top tiles move by position ([topMove]); DOWN out of the top goes to the first pin under the tile ([rowUnder]), UP out
 * of it stays (the caller sends it to the tab row, [homeAtTop]). Row tiles move within their band ([HomeBand]): along
 * the row, null past its ends; DOWN / UP a row in the same column (onto a shorter last row: its last tile). DOWN past a
 * band's last row goes to the next band's first row and UP past its first row to the previous band's last row, each to
 * the tile nearest in x; the last band's last row stays, and UP from the first band goes to the top tile last on.
 */
internal fun homeStep(index: Int, shape: HomeShape, memory: HomeMemory, button: NxButton): Int? {
    if (shape.count <= 0) return index
    if (index < shape.topCount) {
        shape.topMove(index, button, memory)?.let { return it }
        return when (button) {
            NxButton.LEFT, NxButton.RIGHT -> null
            NxButton.DOWN -> if (shape.pins.isNotEmpty()) shape.topCount + shape.rowUnder(index) else index
            else -> index
        }
    }
    val i = index - shape.topCount
    val bi = shape.bands.indexOfFirst { i >= it.start && i < it.end }
    if (bi < 0) return index
    val band = shape.bands[bi]
    val j = i - band.start
    val k = band.perRow
    val n = band.count
    val mid = shape.pins[i].x + shape.pins[i].w / 2
    return when (button) {
        NxButton.LEFT -> if (j % k > 0) index - 1 else null
        NxButton.RIGHT -> if (j % k < k - 1 && j + 1 < n) index + 1 else null
        NxButton.DOWN -> when {
            j + k < n -> index + k
            j / k < (n - 1) / k -> shape.topCount + band.end - 1
            bi + 1 < shape.bands.size -> shape.topCount + shape.nearestIn(shape.bands[bi + 1], 0, mid)
            else -> index
        }
        NxButton.UP -> when {
            j >= k -> index - k
            bi > 0 -> shape.bands[bi - 1].let { p -> shape.topCount + shape.nearestIn(p, (p.count - 1) / p.perRow, mid) }
            else -> memory.lastTop.takeIf { it in 0 until shape.topCount } ?: 0
        }
        else -> index
    }
}

/** UP from [index] finds nothing above: it stays. */
internal fun homeAtTop(index: Int, shape: HomeShape, memory: HomeMemory): Boolean =
    index < shape.topCount && homeStep(index, shape, memory, NxButton.UP) == index

/** [m] after a move [from] → [to]: inside the top, the tile left is the tie-break; any top tile is the one last on. */
internal fun homeRemember(m: HomeMemory, from: Int, to: Int, shape: HomeShape): HomeMemory = when {
    to < shape.topCount && from < shape.topCount && from != to -> m.copy(lastTop = to, prevTop = from)
    to < shape.topCount -> m.copy(lastTop = to)
    from < shape.topCount -> m.copy(prevTop = from, lastPin = to)
    else -> m.copy(lastPin = to)
}

/** [index] moved into [new] in the same section: a top tile keeps its place (else the last top tile); a pin likewise. */
internal fun homeClamp(index: Int, old: HomeShape, new: HomeShape): Int {
    if (new.count <= 0) return 0
    if (index < old.topCount) return index.coerceIn(0, new.topCount - 1)
    if (new.pins.isEmpty()) return new.topCount - 1
    return new.topCount + (index - old.topCount).coerceIn(0, new.pins.size - 1)
}
