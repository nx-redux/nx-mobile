package com.nxredux.mobile.ui.nx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackdropShadeTest {
    @Test fun keysAreTheSpecsEasedStops() {
        assertEquals(
            listOf(0f to 0.85f, 0.12f to 0.60f, 0.32f to 0.20f, 0.55f to 0.28f, 0.82f to 0.62f, 1f to 0.80f),
            BACKDROP_SHADE_KEYS,
        )
    }

    @Test fun passesThroughEveryKey() {
        for (key in BACKDROP_SHADE_KEYS) assertTrue(key in BACKDROP_SHADE, "missing $key")
    }

    @Test fun runsTopToBottomFromZeroToOne() {
        val positions = BACKDROP_SHADE.map { it.first }
        assertEquals(positions.sorted(), positions)
        assertEquals(positions.distinct(), positions)
        assertEquals(0f, positions.first())
        assertEquals(1f, positions.last())
        assertTrue(positions.zipWithNext { a, b -> b - a }.all { it <= 0.02f + 1e-4f })
    }

    @Test fun neverOvershootsBetweenTwoKeys() {
        for ((a, b) in BACKDROP_SHADE_KEYS.zipWithNext()) {
            val lo = minOf(a.second, b.second) - 1e-4f
            val hi = maxOf(a.second, b.second) + 1e-4f
            BACKDROP_SHADE.filter { it.first in a.first..b.first }.forEach { assertTrue(it.second in lo..hi, "$it outside $a..$b") }
        }
    }

    @Test fun hasNoKink() {
        // The slope never jumps by more than 1 from one stop to the next, 2% apart.
        val slopes = BACKDROP_SHADE.zipWithNext { a, b -> (b.second - a.second) / (b.first - a.first) }
        slopes.zipWithNext { a, b -> assertTrue(abs(b - a) <= 1f, "kink: $a to $b in $slopes") }
    }

    @Test fun easedStopsKeepsAStraightLineStraight() {
        val line = easedStops(listOf(0f to 0f, 0.5f to 0.5f, 1f to 1f), step = 0.1f)
        assertEquals(11, line.size)
        line.forEach { (x, y) -> assertEquals(x, y, 1e-5f) }
    }

    @Test fun gradientRunsTopDownWithStopsAtBothEnds() {
        // The order Brush.verticalGradient takes: top (0) first, strictly increasing, dark at both ends and light between.
        assertTrue(BACKDROP_SHADE.zipWithNext().all { (a, b) -> b.first > a.first })
        assertEquals(0f to 0.85f, BACKDROP_SHADE.first())
        assertEquals(1f to 0.80f, BACKDROP_SHADE.last())
        assertTrue(BACKDROP_SHADE.any { it.first in 0.001f..0.05f && it.second > 0.7f })   // a stop near the top, still dark
        assertTrue(BACKDROP_SHADE.any { it.first in 0.95f..0.999f && it.second > 0.7f })   // one near the bottom
        assertTrue(BACKDROP_SHADE.all { it.second in 0f..1f })
        assertTrue(BACKDROP_SHADE.minOf { it.second } >= 0.2f - 1e-4f)
    }
}
