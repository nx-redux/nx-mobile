package com.nxredux.mobile.runner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WiiPointerTest {
    @Test fun insideTheDeadZoneIsStill() {
        assertEquals(0f, WiiPointer.velocity(0.1f))
        assertEquals(0f, WiiPointer.velocity(-0.14f))
    }

    @Test fun fullDeflectionIsOneWidthPerSecond() {
        assertTrue(abs(WiiPointer.velocity(1f) - 1f) < 1e-4f && WiiPointer.velocity(0f) == 0f)
        assertTrue(abs(WiiPointer.velocity(-1f) + 1f) < 1e-4f)
        assertTrue(abs(WiiPointer.velocity(-1.5f) + 1f) < 1e-4f)   // past full tilt reads as full tilt
    }

    @Test fun theDeadZoneIsRescaledSoSpeedStartsAtZero() {
        assertTrue(WiiPointer.velocity(0.16f) < 0.02f)
    }
}
