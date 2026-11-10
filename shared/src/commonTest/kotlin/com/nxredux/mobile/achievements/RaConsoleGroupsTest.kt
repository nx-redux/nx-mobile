package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaConsoleGroupsTest {
    private fun game(title: String, console: String, tag: String?, unlocked: Int, total: Int) =
        RaGameView(title.lowercase(), title.length, title, null, unlocked, total, 0, console, tag)

    @Test fun games_group_by_console_sorted_by_name_with_other_last_and_progress_summed() {
        val games = listOf(
            game("Zelda", "Game Boy Advance", "GBA", 3, 10), game("Mario", "Super Nintendo ES", "SFC", 0, 5),
            game("Unknown", RaConsoleGroups.OTHER, null, 1, 2), game("Metroid", "Game Boy Advance", "MGBA", 4, 4),
            game("Aero", "atari 2600", "A2600", 0, 1),
        )
        val consoles = RaConsoleGroups.of(games)
        assertEquals(listOf("atari 2600", "Game Boy Advance", "Super Nintendo ES", RaConsoleGroups.OTHER), consoles.map { it.name })   // by name, any case; Other last
        val gba = consoles[1]
        assertEquals(listOf("Zelda", "Metroid"), gba.games.map { it.title })   // the games keep their order
        assertEquals(7, gba.unlocked); assertEquals(14, gba.total); assertEquals("GBA", gba.tag)   // the first game's tag draws the logo
        assertNull(consoles.last().tag)
    }

    @Test fun no_games_no_consoles() = assertEquals(emptyList(), RaConsoleGroups.of(emptyList()))

    @Test fun a_console_is_named_by_the_library_then_the_pointer_tag_then_retroachievements() {
        assertEquals("Game Boy Advance (mine)" to "MGBA", RaConsoleGroups.consoleOf("MGBA", "Game Boy Advance (mine)", "GBA", 5))   // the library's own folder
        assertEquals("Super Nintendo ES" to "SUPA", RaConsoleGroups.consoleOf(null, null, "SUPA", 3))                              // no longer indexed: its pointer's tag
        assertEquals("Sega Genesis" to "GPGX", RaConsoleGroups.consoleOf(null, null, "GPGX", 1))                                   // a handheld rom.txt's tag
        assertEquals("Game Boy" to "GB", RaConsoleGroups.consoleOf(null, null, null, RaConsoles.GAMEBOY))                         // no pointer: RetroAchievements' console
        assertEquals("Sega Master System" to "SMS", RaConsoleGroups.consoleOf(null, null, null, RaConsoles.MASTER_SYSTEM))
        assertEquals("TurboGrafx-16" to "PCE", RaConsoleGroups.consoleOf(null, null, null, RaConsoles.PC_ENGINE_CD))
        assertEquals("Sega Genesis" to "MD", RaConsoleGroups.consoleOf(null, null, "NOPE", RaConsoles.MEGA_DRIVE))                // an unknown tag: still RA's
        assertEquals(RaConsoleGroups.OTHER to null, RaConsoleGroups.consoleOf(null, null, null, 9999))                             // neither
        assertEquals(RaConsoleGroups.OTHER to null, RaConsoleGroups.consoleOf(null, null, null, null))
    }
}
