package com.nxredux.mobile.ui.keyboard

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.math.abs

/** A key on the NX keyboard. */
sealed interface Key {
    data class Char(val c: kotlin.Char) : Key
    data object Shift : Key
    data object Symbols : Key
    data object Space : Key
    data object Backspace : Key
    data object Paste : Key
    data object Next : Key
    data object Done : Key
}

/** The keyboard's two pages: letters, and the symbols a password may need. */
enum class KeyPage { LETTERS, SYMBOLS }

/** A key's place: its [row] and its index in that row. */
data class KeyPos(val row: Int, val col: Int)

/**
 * The NX keyboard's layout and how the highlight moves on it: just enough keys for credentials, art-site keys and a
 * game search. Both pages have the same five rows, so the highlight keeps its place when the page flips.
 */
object NxKeys {
    private fun chars(s: String) = s.map { Key.Char(it) }

    private val numbers = chars("1234567890")
    private val letters = listOf(
        numbers,
        chars("qwertyuiop"),
        chars("asdfghjkl@"),
        listOf(Key.Shift) + chars("zxcvbnm_") + Key.Backspace,
    )
    private val symbols = listOf(
        numbers,
        chars("!#$%^&*()+"),
        chars("=/\\:;'\",?~"),
        listOf(Key.Shift) + chars("<>[]{}|`") + Key.Backspace,
    )

    /** Where the highlight starts: the middle of the letters. */
    val HOME = KeyPos(2, 4)

    /** [page]'s rows; Next only where there is a next field ([twoFields]). */
    fun rows(page: KeyPage, twoFields: Boolean): List<List<Key>> {
        val bottom = listOfNotNull(Key.Symbols, Key.Char('.'), Key.Space, Key.Char('-'), Key.Paste, Key.Next.takeIf { twoFields }, Key.Done)
        return (if (page == KeyPage.LETTERS) letters else symbols) + listOf(bottom)
    }

    /** A key's width in key units: space spans four; shift, backspace and the word keys one and a half (room for the word). */
    fun width(key: Key): Float = when (key) {
        Key.Space -> 4f
        Key.Shift, Key.Backspace, Key.Paste, Key.Next, Key.Done -> 1.5f
        else -> 1f
    }

    /**
     * Where the highlight goes from [at] on a [dir] press; null for UP from the top row (it leaves the keyboard for the
     * field). Row ends and the bottom row hold it, so a held direction stops rather than wraps. A row change lands on the
     * key whose centre is nearest, each row's centres taken as a share of its own width (rows differ in length).
     */
    fun move(page: KeyPage, twoFields: Boolean, at: KeyPos, dir: NxButton): KeyPos? {
        val rows = rows(page, twoFields)
        val row = rows[at.row]
        return when (dir) {
            NxButton.LEFT -> at.copy(col = (at.col - 1).coerceAtLeast(0))
            NxButton.RIGHT -> at.copy(col = (at.col + 1).coerceAtMost(row.lastIndex))
            NxButton.UP -> if (at.row == 0) null else nearest(rows, at, at.row - 1)
            NxButton.DOWN -> if (at.row == rows.lastIndex) at else nearest(rows, at, at.row + 1)
            else -> at
        }
    }

    private fun centres(row: List<Key>): List<Float> {
        val total = row.sumOf { width(it).toDouble() }.toFloat()
        var x = 0f
        return row.map { k -> val c = (x + width(k) / 2f) / total; x += width(k); c }
    }

    private fun nearest(rows: List<List<Key>>, at: KeyPos, target: Int): KeyPos {
        val from = centres(rows[at.row])[at.col.coerceIn(0, rows[at.row].lastIndex)]
        val to = centres(rows[target])
        return KeyPos(target, to.indices.minBy { abs(to[it] - from) })
    }
}
