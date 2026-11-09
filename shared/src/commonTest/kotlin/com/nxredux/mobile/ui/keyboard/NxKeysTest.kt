package com.nxredux.mobile.ui.keyboard

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NxKeysTest {
    private fun text(row: List<Key>) = row.joinToString("") { (it as Key.Char).c.toString() }

    @Test fun lettersLayoutMatchesSpec() {
        val rows = NxKeys.rows(KeyPage.LETTERS, twoFields = true)
        assertEquals("1234567890", text(rows[0]))
        assertEquals("qwertyuiop", text(rows[1]))
        assertEquals("asdfghjkl@", text(rows[2]))
        assertEquals(Key.Shift, rows[3].first())
        assertEquals(Key.Backspace, rows[3].last())
        assertEquals("zxcvbnm_", text(rows[3].drop(1).dropLast(1)))
        assertEquals(listOf(Key.Symbols, Key.Char('.'), Key.Space, Key.Char('-'), Key.Paste, Key.Next, Key.Done), rows[4])
    }

    @Test fun nextOnlyOnTwoFieldPrompts() = assertFalse(Key.Next in NxKeys.rows(KeyPage.LETTERS, twoFields = false).last())

    @Test fun upFromTopRowLeavesKeyboard() = assertNull(NxKeys.move(KeyPage.LETTERS, false, KeyPos(0, 3), NxButton.UP))

    @Test fun holdRightStopsAtRowEnd() {
        var at = KeyPos(1, 0)
        repeat(20) { at = NxKeys.move(KeyPage.LETTERS, false, at, NxButton.RIGHT)!! }
        assertEquals(KeyPos(1, 9), at)
    }

    @Test fun downKeepsColumnByCentre() {
        val rows = NxKeys.rows(KeyPage.LETTERS, false)
        val below = NxKeys.move(KeyPage.LETTERS, false, KeyPos(3, 3), NxButton.DOWN)!!
        assertEquals(Key.Space, rows[below.row][below.col])
        assertEquals(KeyPos(1, 0), NxKeys.move(KeyPage.LETTERS, false, KeyPos(0, 0), NxButton.DOWN))
    }

    @Test fun downFromLastRowStays() = assertEquals(KeyPos(4, 2), NxKeys.move(KeyPage.LETTERS, false, KeyPos(4, 2), NxButton.DOWN))

    @Test fun wordKeysAreWideEnoughForTheirWords() {
        assertEquals(1.5f, NxKeys.width(Key.Paste)); assertEquals(1.5f, NxKeys.width(Key.Next)); assertEquals(1.5f, NxKeys.width(Key.Done))
    }

    @Test fun homeIsG() {
        val rows = NxKeys.rows(KeyPage.LETTERS, false)
        assertEquals(Key.Char('g'), rows[NxKeys.HOME.row][NxKeys.HOME.col])
    }

    @Test fun symbolsPageKeepsShapeAndBottomRow() {
        val letters = NxKeys.rows(KeyPage.LETTERS, true)
        val symbols = NxKeys.rows(KeyPage.SYMBOLS, true)
        assertEquals(letters.map { it.size }, symbols.map { it.size })
        assertEquals("1234567890", text(symbols[0]))
        assertEquals(Key.Backspace, symbols[3].last())
        assertEquals(letters[4], symbols[4])
    }
}
