package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomCacheHoldsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun marker(root: File, name: String, pid: Int) = File(root, ".holds/$name@$pid").apply { parentFile.mkdirs(); createNewFile() }

    @Test fun sweepSkipsLiveHold() {
        val root = tmp.newFolder("roms")
        val m = marker(root, "game.zip.10", 4242)
        assertEquals(setOf("game.zip.10"), RomCache.sweepKeep(root) { it == 4242 })
        assertTrue(m.exists())
    }

    @Test fun deadHoldIsIgnoredAndRemoved() {
        val root = tmp.newFolder("roms")
        val m = marker(root, "game.zip.10", 4242)
        assertEquals(emptySet<String>(), RomCache.sweepKeep(root) { false })
        assertFalse(m.exists())
    }

    @Test fun releaseRemovesOwnMarkerOnly() {
        val root = tmp.newFolder("roms")
        val other = marker(root, "a.iso", 777)
        RomCache.holdIn(root, "a.iso", 100)
        RomCache.holdIn(root, "a.iso", 100)   // counted in memory, one marker
        val own = File(root, ".holds/a.iso@100")
        assertTrue(own.exists())
        RomCache.releaseIn(root, "a.iso", 100)
        assertTrue(own.exists())
        RomCache.releaseIn(root, "a.iso", 100)
        assertFalse(own.exists())
        assertTrue(other.exists())
    }
}
