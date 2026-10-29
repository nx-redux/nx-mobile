package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LiveGameStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val game = LiveGame(42, 7L, "p", "mgba", "GBA", null, false)

    private fun store(alive: Boolean = true) = LiveGameStore(File(tmp.root, "game-process/live")) { alive }

    @Test fun writeThenCurrent() {
        val s = store()
        s.write(game)
        assertEquals(game, s.current())
        assertFalse(File(tmp.root, "game-process/live.tmp").exists())
    }

    @Test fun staleRecordIsNotLive() {
        store().write(game)
        val s = store(alive = false)
        assertNull(s.current())
        assertFalse(File(tmp.root, "game-process/live").exists())
    }

    @Test fun clearOnlyOwnPid() {
        val s = store()
        s.write(game)
        s.clear(99)
        assertEquals(game, s.current())
        s.clear(42, launchId = 8L)
        assertEquals(game, s.current())
        s.clear(42)
        assertNull(s.current())
    }
}
