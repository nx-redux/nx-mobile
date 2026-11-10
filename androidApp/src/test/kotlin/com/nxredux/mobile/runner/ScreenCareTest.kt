package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScreenCareTest {
    private var t = 0L
    private var sleepChanges = 0
    private val care = ScreenCare({ t }, { emptyList() }, { sleepChanges++ })

    @Test fun stagesFollowTheIdleTime() {
        assertEquals(CareStage.AWAKE, careStage(CARE_DIM_AFTER_MS - 1))
        assertEquals(CareStage.DIM, careStage(CARE_DIM_AFTER_MS))
        assertEquals(CareStage.SLEEP, careStage(CARE_SLEEP_AFTER_MS))
    }

    @Test fun theShiftWalksOneDpAStepAndComesBackToTheCentre() {
        for (i in 0 until 9) {
            val (ax, ay) = shiftStep(i)
            val (bx, by) = shiftStep(i + 1)
            assertTrue(abs(ax) <= 1 && abs(ay) <= 1)
            assertTrue("step $i", abs(ax - bx) + abs(ay - by) in 1..2)
        }
        assertEquals(0 to 0, shiftStep(9))
        assertEquals(9, (0 until 9).map(::shiftStep).toSet().size)
    }

    @Test fun aRunningGameNobodyTouchesSleepsAndInputWakesIt() {
        t = CARE_SLEEP_AFTER_MS
        care.tick(running = true)
        assertTrue(care.letSleep)
        assertEquals(1, sleepChanges)
        care.input()
        assertFalse(care.letSleep)
        assertEquals(2, sleepChanges)
    }

    @Test fun aHeldGameNeverCountsAsIdle() {
        t = CARE_SLEEP_AFTER_MS
        care.tick(running = false)
        t += CARE_DIM_AFTER_MS - 1
        care.tick(running = true)
        assertFalse(care.letSleep)
        assertEquals(0, sleepChanges)
    }

    @Test fun turnedOffItNeverLetsTheScreenSleep() {
        care.enabled = false
        t = CARE_SLEEP_AFTER_MS * 2
        care.tick(running = true)
        assertFalse(care.letSleep)
    }
}
