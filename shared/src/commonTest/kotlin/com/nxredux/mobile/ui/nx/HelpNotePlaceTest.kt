package com.nxredux.mobile.ui.nx

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where the Settings help note sits beside its row. */
class HelpNotePlaceTest {
    private val gap = 12
    private val margin = 16

    @Test fun widthFollowsTheRowWithinItsBounds() {
        assertEquals(350, helpNoteWidth(1000, Rect(100f, 0f, 450f, 50f), 280, 480, margin))
        assertEquals(480, helpNoteWidth(1000, Rect(100f, 0f, 700f, 50f), 280, 480, margin))   // a wide row: the cap
        assertEquals(280, helpNoteWidth(1000, Rect(100f, 0f, 200f, 50f), 280, 480, margin))   // a narrow one: the floor
        assertEquals(400 - 32, helpNoteWidth(400, Rect(0f, 0f, 400f, 50f), 280, 480, margin)) // never past the margins
        assertEquals(480, helpNoteWidth(1000, null, 280, 480, margin))
    }

    @Test fun belowTheRowWhenItFits() {
        val row = Rect(40f, 100f, 440f, 160f)
        val p = helpNotePlace(800, 1000, row, 400, 300, gap, margin)
        assertTrue(p.below)
        assertEquals(40, p.x)
        assertEquals(160 + gap, p.y)
    }

    @Test fun aboveARowNearTheBottom() {
        // Settings rows sit on the bottom baseline: the note goes over them.
        val row = Rect(40f, 900f, 440f, 960f)
        val p = helpNotePlace(800, 1000, row, 400, 300, gap, margin)
        assertFalse(p.below)
        assertEquals(900 - gap - 300, p.y)
    }

    @Test fun tooTallForEitherSideTakesTheRoomierOne() {
        val row = Rect(40f, 300f, 440f, 360f)
        val room = helpNoteRoom(1000, row, gap, margin)
        assertEquals(1000 - 360 - gap - margin, room)
        val p = helpNotePlace(800, 1000, row, 400, room, gap, margin)
        assertTrue(p.below)
    }

    @Test fun keptInsideTheMargins() {
        val row = Rect(700f, 100f, 790f, 160f)
        assertEquals(800 - margin - 280, helpNotePlace(800, 1000, row, 280, 200, gap, margin).x)
    }

    @Test fun centredWithNoRow() {
        val p = helpNotePlace(800, 1000, null, 400, 200, gap, margin)
        assertEquals(200, p.x)
        assertEquals(400, p.y)
    }
}
