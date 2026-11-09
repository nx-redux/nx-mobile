package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveGameTest {
    private val live = LiveGame(42, 7L, "Roms/GBA/Zelda.gba", "mgba", "GBA", null, false)

    private fun session(path: String? = "Roms/GBA/Zelda.gba", core: String = "mgba", slot: Int? = null, netplay: com.nxredux.mobile.netplay.NetplaySession? = null): GameSession {
        val ref = ContentRef("/x/Zelda.gba")
        return GameSession("GBA", "Zelda", CoreDescriptor(core, "lib$core.so", setOf("gba")), ref, ref, "Zelda.gba", resumeSlot = slot, gamePath = path, netplay = netplay)
    }

    @Test fun sameGameContinueCanBringBack() {
        assertTrue(live.canBringBack(session()))
        assertTrue(live.canBringBack(session(slot = 9)))
    }

    @Test fun slotPickCannotBringBack() = assertFalse(live.canBringBack(session(slot = 3)))

    @Test fun otherGameCannot() {
        assertFalse(live.canBringBack(session(path = "Roms/GBA/Other.gba")))
        assertFalse(live.canBringBack(session(core = "gpsp")))
        assertFalse(live.copy(gamePath = null).canBringBack(session(path = null)))
    }

    @Test fun netplayCannot() = assertFalse(live.copy(netplay = true).canBringBack(session()))

    // Home during a slow quit, then the same game: its closing task must not be brought forward.
    @Test fun closingCannot() = assertFalse(live.copy(closing = true).canBringBack(session()))

    @Test fun recordBeforeClosingReadsAsNotClosing() =
        assertEquals(live, LiveGame.decode("1\t42\t7\t1Roms/GBA/Zelda.gba\tmgba\tGBA\t\t0"))

    @Test fun codecRoundTrip() {
        for (g in listOf(live, live.copy(gamePath = null, resumeSlot = 9, netplay = true), live.copy(gamePath = "a\tb\\c\nd", tag = ""), live.copy(closing = true)))
            assertEquals(g, LiveGame.decode(g.encode()))
    }

    @Test fun garbageDecodesToNull() {
        for (t in listOf("", "x", "1\t2", "1\tx\t7\t0\ta\tb\t\t0", "2\t1\t7\t0\ta\tb\t\t0", "1\t1\t7\t0\ta\tb\t\t2", "1\t1\t7\t1\\q\ta\tb\t\t0", "2\t1\t7\t0\ta\tb\t\t0\t2", "1\t1\t7\t0\ta\tb\t\t0\t0"))
            assertNull(LiveGame.decode(t), t)
    }
}
