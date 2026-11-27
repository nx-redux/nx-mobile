package com.nxredux.mobile.runner

import com.nxredux.mobile.runner.MapKey.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonMapRowsTest {
    private fun jobs(rows: List<MapRow>) = rows.map { it.job }

    @Test
    fun ps1Rows() {
        val rows = ButtonMapRows.rowsFor("PS")
        assertEquals(listOf(MapRow(A, "×"), MapRow(B, "○"), MapRow(X, "□"), MapRow(Y, "△")), rows.take(4))
        assertTrue(MapRow(L3, "L3") in rows && MapRow(R3, "R3") in rows)
    }

    @Test
    fun ndsRows() {
        val rows = ButtonMapRows.rowsFor("NDS")
        assertTrue(MapRow(L2, "Swap screens") in rows)
        assertTrue(MapRow(R2, "Stylus") in rows)
        assertTrue(MapRow(L1, "L") in rows && MapRow(R1, "R") in rows)
    }

    @Test
    fun gbRows() {
        val rows = ButtonMapRows.rowsFor("GB")
        assertEquals(listOf(A, B, X, Y), jobs(rows))
        assertEquals(listOf("A", "B", "TA", "TB"), rows.map { it.label })
    }

    @Test
    fun ps2Rows() {
        val rows = ButtonMapRows.rowsFor("PS2")
        assertTrue(MapRow(L3, "L3") in rows && MapRow(R3, "R3") in rows)
    }

    @Test
    fun wiiSidewaysRows() {
        val rows = ButtonMapRows.rowsFor("WII", wiiPad = WiiPad.SIDEWAYS)
        assertTrue(MapRow(L3, "Recenter") in rows)
        assertTrue(MapRow(R3, "HOME") in rows)
        assertEquals(MapRow(A, "2"), rows.first())
        assertTrue(MapRow(R2, "Shake") in rows)
        assertFalse(L1 in jobs(rows) || R1 in jobs(rows) || L2 in jobs(rows))
    }

    @Test
    fun sfcRows() {
        val rows = ButtonMapRows.rowsFor("SFC")
        assertEquals(listOf(MapRow(A, "A"), MapRow(B, "B"), MapRow(X, "X"), MapRow(Y, "Y")), rows.take(4))
        assertFalse(L3 in jobs(rows) || R3 in jobs(rows))
    }

    @Test
    fun n64RowsByName() {
        assertEquals(
            listOf(MapRow(A, "A"), MapRow(B, "B"), MapRow(X, "C-Left"), MapRow(Y, "C-Down"),
                MapRow(L1, "L"), MapRow(R1, "R"), MapRow(L2, "Z")),
            ButtonMapRows.rowsFor("N64"),
        )
    }

    @Test
    fun n3dsRows() {
        val old = ButtonMapRows.rowsFor("3DS")
        assertFalse(L2 in jobs(old) || R2 in jobs(old))
        assertTrue(MapRow(L3, "Swap screens") in old)
        val new = ButtonMapRows.rowsFor("3DS", newControls = true)
        assertTrue(MapRow(L2, "ZL") in new && MapRow(R2, "ZR") in new)
    }

    @Test
    fun gameCubeRows() {
        val rows = ButtonMapRows.rowsFor("GC")
        assertFalse(L1 in jobs(rows))
        assertTrue(MapRow(R1, "Z") in rows && MapRow(L2, "L") in rows && MapRow(R2, "R") in rows)
    }

    @Test
    fun dreamcastByLetter() {
        assertEquals(listOf(MapRow(A, "A"), MapRow(B, "B"), MapRow(X, "X"), MapRow(Y, "Y")), ButtonMapRows.rowsFor("DC").take(4))
    }

    @Test
    fun rowsKeepPadOrderThenHotkeys() {
        val order = MapKey.entries
        for (tag in listOf("PS", "PS2", "NDS", "3DS", "WIIU", "SFC")) {
            val js = jobs(ButtonMapRows.rowsFor(tag))
            assertEquals(js.sortedBy { order.indexOf(it) }, js, tag)
        }
    }
}
