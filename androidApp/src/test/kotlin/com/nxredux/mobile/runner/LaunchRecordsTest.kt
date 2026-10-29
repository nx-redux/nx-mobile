package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.Recent
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchRecordsTest {
    private lateinit var dir: File
    private var clock = 1_000_000_000L

    @Before fun setUp() { dir = File.createTempFile("launches", "").also { it.delete(); it.mkdirs() } }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun records() = LaunchRecords(dir) { clock }

    @Test fun startedIsDrainedOnce() {
        val r = records()
        val id = r.pending(Recent("/roms/a.gba", "A"))
        assertTrue(r.started(id))
        assertEquals(listOf(Recent("/roms/a.gba", "A")), r.drainStarted())
        assertEquals(emptyList<Recent>(), r.drainStarted())
    }

    @Test fun pendingIsNotDrainedUntilStarted() {
        val r = records()
        r.pending(Recent("/roms/a.gba", null))
        assertEquals(emptyList<Recent>(), r.drainStarted())
    }

    @Test fun droppedIsNeverDrained() {
        val r = records()
        val id = r.pending(Recent("/roms/a.gba", "A"))
        r.drop(id)
        assertFalse(r.started(id))
        assertEquals(emptyList<Recent>(), r.drainStarted())
    }

    @Test fun startedRecordSurvivesMenuRestart() {
        val id = records().let { r -> r.pending(Recent("/roms/b.nes", null)).also { r.started(it) } }
        assertEquals(listOf(Recent("/roms/b.nes", null)), records().drainStarted())
        assertTrue(id >= 0)
    }

    @Test fun unknownIdStartIsFalse() {
        assertFalse(records().started(12345L))
        assertFalse(records().started(-1L))
    }

    @Test fun idsAreDistinct() {
        val r = records()
        assertTrue(r.pending(Recent("/a")) != r.pending(Recent("/a")))
    }

    @Test fun nameWithTabAndNewlineStaysOnePair() {
        val r = records()
        r.started(r.pending(Recent("/roms/c.gba", "Odd\tName")))
        assertEquals(listOf(Recent("/roms/c.gba", "Odd\tName")), r.drainStarted())
    }

    @Test fun pendingOlderThanADayIsSwept() {
        val r = records()
        val old = r.pending(Recent("/roms/old.gba"))
        clock += 25L * 60 * 60 * 1000
        val fresh = r.pending(Recent("/roms/new.gba"))
        r.drainStarted()
        assertFalse(r.started(old))
        assertTrue(r.started(fresh))
    }
}
