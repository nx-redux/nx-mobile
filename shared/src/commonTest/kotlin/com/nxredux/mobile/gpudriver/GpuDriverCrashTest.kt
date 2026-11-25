package com.nxredux.mobile.gpudriver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpuDriverCrashTest {
    private val marker = GpuDriverCrash.Marker(42, "/roms/God of War.iso", "God of War", "turnip-v25", "Turnip v25")

    @Test fun markerRoundTripsWithSpaces() {
        assertEquals(marker, GpuDriverCrash.decode(GpuDriverCrash.encode(marker)))
    }

    @Test fun tabsAndNewlinesInFieldsBecomeSpaces() {
        val m = marker.copy(gameName = "God\tof\nWar\r2", driverName = "Tur\nnip")
        val back = GpuDriverCrash.decode(GpuDriverCrash.encode(m))
        assertEquals(marker.copy(gameName = "God of War 2", driverName = "Tur nip"), back)
    }

    @Test fun decodeOfGarbageIsNull() {
        assertNull(GpuDriverCrash.decode(null))
        assertNull(GpuDriverCrash.decode(""))
        assertNull(GpuDriverCrash.decode("   "))
        assertNull(GpuDriverCrash.decode("42\n/roms/a\nA"))
        assertNull(GpuDriverCrash.decode("x\n/roms/a\nA\nid\nName"))
    }

    @Test fun diedIsFalseWhileProcessAlive() {
        assertFalse(GpuDriverCrash.died(marker, setOf(1, 42)))
        assertTrue(GpuDriverCrash.died(marker, setOf(1, 7)))
        assertTrue(GpuDriverCrash.died(marker, emptySet()))
    }

    @Test fun recordIsIdempotentAndReadable() {
        val once = GpuDriverCrash.record(null, "/a", "d1")
        assertEquals(once, GpuDriverCrash.record(once, "/a", "d1"))
        val two = GpuDriverCrash.record(once, "/a", "d2")
        assertEquals(setOf("d1", "d2"), GpuDriverCrash.crashed(two, "/a"))
        assertEquals(emptySet(), GpuDriverCrash.crashed(two, "/b"))
        assertEquals(emptySet(), GpuDriverCrash.crashed(null, "/a"))
    }

    @Test fun clearGameAndDropDriverRemoveOnlyTheirLines() {
        var s = ""
        s = GpuDriverCrash.record(s, "/a", "d1")
        s = GpuDriverCrash.record(s, "/a", "d2")
        s = GpuDriverCrash.record(s, "/b", "d1")
        val cleared = GpuDriverCrash.clearGame(s, "/a")
        assertEquals(emptySet(), GpuDriverCrash.crashed(cleared, "/a"))
        assertEquals(setOf("d1"), GpuDriverCrash.crashed(cleared, "/b"))
        val dropped = GpuDriverCrash.dropDriver(s, "d1")
        assertEquals(setOf("d2"), GpuDriverCrash.crashed(dropped, "/a"))
        assertEquals(emptySet(), GpuDriverCrash.crashed(dropped, "/b"))
        assertEquals("", GpuDriverCrash.clearGame(null, "/a"))
        assertEquals("", GpuDriverCrash.dropDriver("/a\td1", "d1"))
    }

    @Test fun malformedLinesAreIgnored() {
        val s = "\nnotab\n/a\td1\n/a\td2\textra\n"
        assertEquals(setOf("d1"), GpuDriverCrash.crashed(s, "/a"))
    }

    @Test fun noticeIsTheExactCopy() {
        assertEquals(
            "Turnip v25 crashed in God of War, so it now uses the system driver. Change this in Game Settings.",
            GpuDriverCrash.notice(marker),
        )
    }
}
