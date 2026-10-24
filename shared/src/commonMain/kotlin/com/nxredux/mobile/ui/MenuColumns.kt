package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.NxButton

/** Most items a two-column context menu holds (two full [MODAL_ROWS] columns). */
internal const val MENU_MAX_TWO_COLUMN = MODAL_ROWS * 2

/** 2 for a menu of 9..16 items when two columns [fitsTwo]; else 1 (a single, scrolling column). */
internal fun menuColumns(count: Int, fitsTwo: Boolean): Int = if (fitsTwo && count > MODAL_ROWS && count <= MENU_MAX_TWO_COLUMN) 2 else 1

/** The rows a column of a [count]-item menu in [columns] columns holds: two columns balance (the left one more when odd). */
internal fun menuColumnRows(count: Int, columns: Int): Int = if (columns >= 2) (count + 1) / 2 else MODAL_ROWS

/**
 * The index after [button] in a menu of [count] items drawn in [columns] columns, column-major, balanced
 * ([menuColumnRows]: the left column holds the extra one). UP/DOWN walk the items in that order as one loop: past a column's foot onto the next
 * column's top, past the last item round to the first (and back). RIGHT/LEFT keep the row, RIGHT landing on the right
 * column's last item when it is shorter. One column: UP/DOWN loop, LEFT/RIGHT do nothing.
 */
internal fun menuStep(index: Int, count: Int, columns: Int, button: NxButton): Int {
    if (count <= 0) return index
    if (button == NxButton.UP) return (index - 1).mod(count)
    if (button == NxButton.DOWN) return (index + 1).mod(count)
    if (columns < 2) return index
    val rows = menuColumnRows(count, columns)
    val row = index % rows
    val col = index / rows
    return when (button) {
        NxButton.RIGHT -> if (col == 0 && count > rows) minOf(rows + row, count - 1) else index
        NxButton.LEFT -> if (col == 1) row else index
        else -> index
    }
}
