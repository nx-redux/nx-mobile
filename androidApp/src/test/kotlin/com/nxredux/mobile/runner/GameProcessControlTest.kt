package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameProcessControlTest {
    @get:Rule val tmp = TemporaryFolder()

    private val alive = HashSet<Int>()
    private val store by lazy { LiveGameStore(File(tmp.root, "game-process/live")) { it in alive } }
    private var ends = 0
    private val killed = ArrayList<Int>()
    private var slept = 0L
    private var brings = 0
    private var bringResult = true
    /** Run on each sleep: the fake clock's hook, e.g. the game process ending after its quit. */
    private var onSleep: (Long) -> Unit = {}

    private val control by lazy {
        GameProcessControl(
            store,
            sendEnd = { ends++ },
            killPid = { killed += it; alive -= it },
            bringToFront = { brings++; bringResult },
            sleep = { slept += it; onSleep(slept) },
        )
    }

    private fun session(path: String? = "Roms/GBA/Zelda.gba", slot: Int? = null): GameSession {
        val ref = ContentRef("/x/Zelda.gba")
        return GameSession("GBA", "Zelda", CoreDescriptor("mgba", "libmgba.so", setOf("gba")), ref, ref, "Zelda.gba", resumeSlot = slot, gamePath = path)
    }

    private fun live(pid: Int = 42, path: String = "Roms/GBA/Zelda.gba") {
        alive += pid
        store.write(LiveGame(pid, 7L, path, "mgba", "GBA", null, false))
    }

    @Test fun noLiveGameStarts() {
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertEquals(0, ends)
        assertEquals(0, brings)
        assertTrue(killed.isEmpty())
    }

    @Test fun sameGameIsBroughtBack() {
        live()
        assertEquals(GameProcessControl.Decision.BROUGHT_BACK, control.prepare(session(slot = 9)))
        assertEquals(1, brings)
        assertEquals(0, ends)
        assertTrue(killed.isEmpty())
    }

    @Test fun differentGameIsEnded() {
        live(path = "Roms/GBA/Metroid.gba")
        onSleep = { t -> if (t >= 300) alive -= 42 }   // the quit's saves take a few polls
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertEquals(0, brings)   // another game: never brought back
        assertEquals(1, ends)
        assertEquals(300L, slept)
        assertTrue(killed.isEmpty())
    }

    @Test fun stuckGameIsKilledAfterTimeout() {
        live(path = "Roms/GBA/Metroid.gba")
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertEquals(1, ends)
        assertEquals(listOf(42), killed)
        assertTrue(slept >= 10_000L)
    }

    @Test fun bringBackFailureEndsAndStarts() {
        live()
        bringResult = false
        onSleep = { alive -= 42 }
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertEquals(1, brings)
        assertEquals(1, ends)
        assertTrue(killed.isEmpty())
    }
}
