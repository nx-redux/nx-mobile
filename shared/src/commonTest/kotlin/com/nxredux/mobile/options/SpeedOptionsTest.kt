package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedOptionsTest {
    @Test fun defaults() {
        assertEquals(SpeedSettings(4, false, false, false), SpeedSettings.parse(emptyMap(), true))
    }

    @Test fun parsesValues() {
        val s = SpeedSettings.parse(mapOf(SpeedOptions.MAX_FF to "8", SpeedOptions.FF_AUDIO to "On", SpeedOptions.REWIND to "On"), true)
        assertEquals(8, s.maxFf); assertEquals(true, s.ffAudio); assertEquals(true, s.rewind)
    }

    @Test fun badSpeedFallsBack() {
        for (v in listOf("9", "x", "1")) assertEquals(4, SpeedSettings.parse(mapOf(SpeedOptions.MAX_FF to v), true).maxFf)
    }

    @Test fun noRewindCoreForcesOff() {
        assertEquals(false, SpeedSettings.parse(mapOf(SpeedOptions.REWIND to "On"), false).rewind)
    }

    @Test fun rowsAreCompactWithTheirTextBehindInfo() {
        for (o in SpeedOptions.table) assertEquals("", o.brief, o.label)
        for (o in SpeedOptions.table) kotlin.test.assertTrue(o.info.isNotBlank(), o.label)
    }
}
