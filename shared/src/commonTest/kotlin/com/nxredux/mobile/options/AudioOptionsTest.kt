package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioOptionsTest {
    @Test fun defaultsToHigh() {
        assertEquals("High", AudioOptions.row.default)
        assertEquals(AudioOptions.HIGH, AudioOptions.qualityOf(emptyMap()))
    }

    @Test fun levelsAreTheHandheldsInOrder() {
        assertEquals(listOf("Low", "Medium", "High", "Max"), AudioOptions.row.values)
        assertEquals(listOf(0, 1, 2, 3), AudioOptions.row.values.map { AudioOptions.qualityOf(mapOf(AudioOptions.RESAMPLING to it)) })
    }

    @Test fun unknownValueIsHigh() {
        assertEquals(AudioOptions.HIGH, AudioOptions.qualityOf(mapOf(AudioOptions.RESAMPLING to "Ultra")))
    }

    @Test fun everyConsoleHasTheRowAfterTheSpeedRows() {
        for (tag in listOf("GBA", "NDS", "PS", "3DS", "DC", "")) {
            val rows = ConsoleSettings.tableFor(tag)
            assertEquals(AudioOptions.row, rows.last(), tag)
            assertTrue(AudioOptions.row in ConsoleSettings.all, tag)
        }
    }

    @Test fun rowIsCompactWithItsTextBehindInfo() {
        assertEquals("", AudioOptions.row.brief)
        assertTrue(AudioOptions.row.info.isNotBlank())
    }
}
