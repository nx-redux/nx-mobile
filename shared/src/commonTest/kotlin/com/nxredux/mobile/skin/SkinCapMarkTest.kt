package com.nxredux.mobile.skin

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** A word printed on a cap ([ControlSpec.mark]): read from the pack and sized with the control. */
class SkinCapMarkTest {
    @Test
    fun aControlReadsItsCapMark() {
        val c = Json.decodeFromString<ControlSpec>(
            """{"id":"L","shape":"rect","x":120,"y":50,"hw":70,"hh":42,"mark":"L","markH":30,"markDy":-2,"markColor":"#E06040"}""")
        assertEquals("L", c.mark)
        assertEquals(30f, c.markH)
        assertEquals(-2f, c.markDy)
        assertEquals("#E06040", c.markColor)
    }

    @Test
    fun aControlReadsItsMarksArt() {
        val c = Json.decodeFromString<ControlSpec>(
            """{"id":"MENU","shape":"circle","x":120,"y":50,"r":58,"markSx":300,"markSy":40,"markSw":160,"markSh":154,"markH":34}""")
        assertEquals(listOf(300, 40, 160, 154), listOf(c.markSx, c.markSy, c.markSw, c.markSh))
        assertEquals(null, c.mark)
        assertEquals(34f, c.markH)
        assertEquals(listOf(300, 40, 160, 154), c.scaled(0.5f).let { listOf(it.markSx, it.markSy, it.markSw, it.markSh) })   // its spot in the picture stays
    }

    @Test
    fun aMarkScalesWithItsControl() {
        val c = ControlSpec("A", ControlShape.CIRCLE, 10f, 20f, r = 50f, label = "A", labelH = 17f, labelDy = 60f, mark = "A", markH = 30f, markDy = 4f)
        val s = c.scaled(0.5f)
        assertEquals(15f, s.markH)
        assertEquals(2f, s.markDy)
        assertEquals(25f, s.r)
        assertEquals(8.5f, s.labelH)
        assertEquals(30f, s.labelDy)
        assertEquals(10f, s.x)   // the place is the placement's, not the size's
    }
}
