package com.nxredux.mobile.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The mockup's vectors, so the app draws the same picture.
class AbstractArtTest {
    private val e = 1e-3

    @Test fun seedsAreFnv1aOverUtf8() {
        assertEquals(0xE40C292C.toInt(), abstractSeed("a"))
        assertEquals(0xD973B800.toInt(), abstractSeed("MD/Gunstar Heroes (USA).md"))
        assertEquals(0x3D4A9DFF, abstractSeed("MD/Ristar (USA, Europe).md"))
        assertEquals(0x17687B85, abstractSeed("MD/Vectorman (USA, Europe).md"))
        assertEquals(0x811C9DC5.toInt(), abstractSeed(""))
        assertEquals(abstractSeed("MD/Gunstar Heroes (USA).md"), abstractSeed(abstractArtKey("MD", "Gunstar Heroes (USA).md")))
    }

    @Test fun mulberry32MatchesTheMockup() {
        val r = Mulberry32(abstractSeed("a"))
        assertEquals(.6216856814, r.next(), 1e-9); assertEquals(.3082234741, r.next(), 1e-9); assertEquals(.3668607599, r.next(), 1e-9)
        val g = Mulberry32(abstractSeed("MD/Gunstar Heroes (USA).md"))
        assertEquals(.2258742147, g.next(), 1e-9); assertEquals(.6691273961, g.next(), 1e-9); assertEquals(.0791059679, g.next(), 1e-9)
        val ri = Mulberry32(abstractSeed("MD/Ristar (USA, Europe).md"))
        assertEquals(.9396040437, ri.next(), 1e-9); assertEquals(.5606119912, ri.next(), 1e-9); assertEquals(.6291568682, ri.next(), 1e-9)
    }

    private data class Row(val key: String, val h0: Double, val shift: Double, val stops: Int, val ang: Double, val kinds: List<Int>)

    @Test fun plansMatchTheVectors() {
        for (row in listOf(
            Row("a", 223.807, 49.163, 3, 107.730, listOf(2, 1, 2)),
            Row("MD/Gunstar Heroes (USA).md", 81.315, -45.911, 3, 108.071, listOf(0, 2, 1)),
            Row("MD/Ristar (USA, Europe).md", 338.257, -33.672, 2, 277.303, listOf(1, 0, 0, 2, 2)),
            Row("MD/Vectorman (USA, Europe).md", 264.901, -50.702, 2, 237.492, listOf(2, 0, 0, 1)),
        )) {
            val p = abstractPlan(abstractSeed(row.key))
            assertEquals(row.h0, p.h0, e, row.key)
            assertEquals(row.shift, p.shift, e, row.key)
            assertEquals(row.stops, p.stops.size, row.key)
            assertEquals(row.ang, p.angle, e, row.key)
            assertEquals(row.kinds, p.shapes.map { it.kind }, row.key)
            assertEquals(row.h0, p.stops[0].hue, e, row.key)
            assertEquals(hue(row.h0 + row.shift), p.stops[1].hue, e, row.key)
            if (row.stops == 3) assertEquals(hue(row.h0 - row.shift / 2), p.stops[2].hue, e, row.key)
        }
    }

    private fun hue(h: Double) = ((h % 360) + 360) % 360

    @Test fun theSameSeedGivesTheSamePicture() {
        val s = abstractSeed("SFC/Super Metroid (Japan, USA) (En).sfc")
        assertEquals(abstractPlan(s), abstractPlan(s))
        assertTrue(abstractPlan(s) != abstractPlan(s + 1))
    }

    @Test fun rangesStayInsideTheRules() {
        for (k in 0 until 200) {
            val p = abstractPlan(abstractSeed("T/$k"))
            assertTrue(p.shapes.size in 3..5)
            assertTrue(p.stops.size in 2..3)
            for (c in p.stops) { assertTrue(c.saturation in 35.0..55.0); assertTrue(c.lightness in 25.0..45.0); assertEquals(1.0, c.alpha) }
            for (sh in p.shapes) {
                assertTrue(sh.kind in 0..2)
                assertTrue(sh.color.saturation in 35.0..55.0); assertTrue(sh.color.lightness in 45.0..65.0)
                assertTrue(sh.color.alpha in 0.12..0.28)
                assertTrue(abs(sh.rotation) <= 60.0)
            }
        }
    }

    @Test fun shapesScaleWithTheBitmap() {
        val p = abstractPlan(abstractSeed("a"))
        // kinds 2, 1, 2: a stripe 3 W long, a band (0.6 + 0.6a) W long with a th / 2 corner
        val stripe = p.shapes[0]
        assertEquals(3 * ABSTRACT_W, stripe.length, e)
        val band = p.shapes[1]
        assertEquals(band.thickness / 2, band.corner, e)
        val big = abstractPlan(abstractSeed("a"), 1024.0, 768.0)
        for ((s1, s2) in p.shapes.zip(big.shapes)) {
            assertEquals(s1.x * 2, s2.x, e); assertEquals(s1.y * 2, s2.y, e)
            assertEquals(s1.length * 2, s2.length, e); assertEquals(s1.thickness * 2, s2.thickness, e); assertEquals(s1.radius * 2, s2.radius, e)
        }
    }

    @Test fun theGroundRunsThroughTheCentreAtItsAngle() {
        val p = abstractPlan(abstractSeed("a"))   // 107.73°
        val (x0, y0, x1, y1) = p.groundLine()
        assertEquals(ABSTRACT_W / 2, (x0 + x1) / 2, e); assertEquals(ABSTRACT_H / 2, (y0 + y1) / 2, e)
        assertTrue(y1 > y0)   // y points down: past 90° the line runs down and left
        assertTrue(x1 < x0)
    }

    @Test fun hslIsTheCssConversion() {
        assertEquals(0xFF0000, hslRgb(0.0, 100.0, 50.0))
        assertEquals(0x00FF00, hslRgb(120.0, 100.0, 50.0))
        assertEquals(0x808080, hslRgb(200.0, 0.0, 50.2))
        assertEquals(0x6C4C93, hslRgb(267.0, 32.0, 43.7))
    }

    @Test fun urlsCarryTheSeed() {
        val url = abstractArtUrl("MD", "Gunstar Heroes (USA).md")
        assertEquals("nxabstract://d973b800", url)
        assertEquals(0xD973B800.toInt(), abstractSeedOf(url))
        assertNull(abstractSeedOf("file:///x.png"))
        assertNull(abstractSeedOf("nxabstract://zz"))
    }
}
