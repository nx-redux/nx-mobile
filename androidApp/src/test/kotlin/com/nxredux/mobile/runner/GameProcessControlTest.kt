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
    /** The game process's heartbeat file as the menu reads it (null: none). */
    private var beat: String? = "old"
    /** Android still lists a `:game` process (its death not yet handled). */
    private var listed = false

    private val control by lazy {
        GameProcessControl(
            store,
            sendEnd = { ends++ },
            killPid = { killed += it; alive -= it },
            bringToFront = { brings++; bringResult },
            sleep = { slept += it; onSleep(slept) },
            heartbeat = { beat },
            gameProcessListed = { listed },
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

    @Test fun stalledHeartbeatIsKilledAfterFiveSeconds() {
        live(path = "Roms/GBA/Metroid.gba")
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertEquals(1, ends)
        assertEquals(listOf(42), killed)
        assertEquals(5_000L, slept)
    }

    @Test fun heartbeatThatStopsIsKilledFiveSecondsAfterItsLastBeat() {
        live(path = "Roms/GBA/Metroid.gba")
        onSleep = { t -> if (t <= 3_000) beat = "beat $t" }
        control.prepare(session())
        assertEquals(listOf(42), killed)
        assertEquals(8_000L, slept)
    }

    @Test fun progressingHeartbeatIsNotKilled() {
        live(path = "Roms/GBA/Metroid.gba")
        onSleep = { t -> beat = "beat ${t / 500}"; if (t >= 20_000) alive -= 42 }   // a slow quit, beating every 500 ms
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertTrue(killed.isEmpty())
        assertEquals(20_000L, slept)
    }

    @Test fun heartbeatForeverIsKilledAtTheCap() {
        live(path = "Roms/GBA/Metroid.gba")
        onSleep = { t -> beat = "beat $t" }
        control.prepare(session())
        assertEquals(listOf(42), killed)
        assertEquals(60_000L, slept)
    }

    @Test fun waitsUntilAndroidNoLongerListsTheProcess() {
        live(path = "Roms/GBA/Metroid.gba")
        onSleep = { t -> if (t >= 300) { alive -= 42; listed = t < 700 } }   // the pid is gone before Android handled the death
        listed = true
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertTrue(killed.isEmpty())
        assertEquals(700L, slept)
    }

    @Test fun listingWaitIsBounded() {
        live(path = "Roms/GBA/Metroid.gba")
        onSleep = { t -> if (t >= 300) alive -= 42 }
        listed = true
        assertEquals(GameProcessControl.Decision.START, control.prepare(session()))
        assertEquals(2_300L, slept)
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
