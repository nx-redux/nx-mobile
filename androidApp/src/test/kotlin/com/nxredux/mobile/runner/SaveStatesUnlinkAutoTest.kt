package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.NxPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SaveStatesUnlinkAutoTest {
    private val root: File = Files.createTempDirectory("unlink-auto").toFile()
    private val paths = NxPaths("GBA", "mgba", "Kirby.gba")
    private val states = SaveStates(root, paths)

    @After fun cleanUp() { root.deleteRecursively() }

    private fun put(path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    @Test fun a_pointer_on_the_hidden_slot_is_dropped_with_its_frame_and_the_state_stays() {
        val pointer = put(paths.lastSlot, "9")
        val frame = put(paths.thumb(NxPaths.AUTO_SLOT), "png")
        val state = put(paths.state(NxPaths.AUTO_SLOT), "state")

        states.unlinkAuto()

        assertFalse(pointer.exists())
        assertFalse(frame.exists())
        assertTrue(state.exists())
    }

    @Test fun a_pointer_on_a_manual_slot_is_left_alone() {
        val pointer = put(paths.lastSlot, "2")
        val frame = put(paths.thumb(2), "png")

        states.unlinkAuto()

        assertEquals("2", pointer.readText())
        assertTrue(frame.exists())
    }

    @Test fun without_a_pointer_the_hidden_frame_still_goes() {
        val frame = put(paths.thumb(NxPaths.AUTO_SLOT), "png")

        states.unlinkAuto()

        assertFalse(frame.exists())
    }
}
