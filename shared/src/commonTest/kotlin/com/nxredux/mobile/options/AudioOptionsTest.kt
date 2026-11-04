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
            assertEquals(AudioOptions.rows, rows.takeLast(AudioOptions.rows.size), tag)
            assertTrue(ConsoleSettings.all.containsAll(AudioOptions.rows), tag)
        }
    }

    @Test fun rowIsCompactWithItsTextBehindInfo() {
        assertEquals("", AudioOptions.row.brief)
        assertTrue(AudioOptions.row.info.isNotBlank())
    }

    // Audio Buffer: Auto is the runner's own size (0); a size in ms otherwise; read at launch.
    @Test fun theBufferRowGivesTheRunnerItsSize() {
        assertEquals(listOf("Auto", "50 ms", "75 ms", "100 ms"), AudioOptions.bufferRow.values.map(AudioOptions.bufferRow::labelOf))
        assertEquals(0, AudioOptions.bufferMsOf(emptyMap(), "GBA"))
        assertEquals(0, AudioOptions.bufferMsOf(mapOf(AudioOptions.BUFFER to "auto"), "SNES"))
        assertEquals(75, AudioOptions.bufferMsOf(mapOf(AudioOptions.BUFFER to "75"), "GBA"))
        assertEquals(0, AudioOptions.bufferMsOf(mapOf(AudioOptions.BUFFER to "huge"), "GBA"))
        // Auto is 50 ms on the heavy consoles (Wii, GameCube, PS2); a chosen size wins there too.
        for (tag in listOf("WII", "GC", "PS2")) assertEquals(AudioOptions.HEAVY_BUFFER_MS, AudioOptions.bufferMsOf(emptyMap(), tag), tag)
        assertEquals(50, AudioOptions.bufferMsOf(mapOf(AudioOptions.BUFFER to "auto"), "WII"))
        assertEquals(100, AudioOptions.bufferMsOf(mapOf(AudioOptions.BUFFER to "100"), "PS2"))
        assertTrue(AudioOptions.bufferRow.atLaunch)
    }

    // Fill Audio Gaps: Auto (with Frame Skip Off), On, Off as the runner's 0, 1, 2; live.
    @Test fun theFillGapsRowGivesTheRunnerItsMode() {
        assertEquals(listOf("Auto", "On", "Off"), AudioOptions.fillGapsRow.values.map(AudioOptions.fillGapsRow::labelOf))
        assertEquals(listOf(0, 1, 2), AudioOptions.fillGapsRow.values.map { AudioOptions.fillGapsOf(mapOf(AudioOptions.FILL_GAPS to it)) })
        assertEquals(0, AudioOptions.fillGapsOf(emptyMap()))
        assertEquals(0, AudioOptions.fillGapsOf(mapOf(AudioOptions.FILL_GAPS to "sometimes")))
        assertEquals(false, AudioOptions.fillGapsRow.atLaunch)
    }
}
