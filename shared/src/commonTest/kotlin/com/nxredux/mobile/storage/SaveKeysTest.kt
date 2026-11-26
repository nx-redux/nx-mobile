package com.nxredux.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveKeysTest {
    @Test fun every_format_names_the_same_game() {
        assertEquals(SaveKey(SaveKind.BATTERY, "Pokemon Emerald"), SaveKeys.of("Pokemon Emerald.gba.sav"))  // MinUI
        assertEquals(SaveKey(SaveKind.BATTERY, "Pokemon Emerald"), SaveKeys.of("Pokemon Emerald.sav"))      // Generic
        assertEquals(SaveKey(SaveKind.BATTERY, "Pokemon Emerald"), SaveKeys.of("Pokemon Emerald.srm"))      // Retroarch
        assertEquals(SaveKey(SaveKind.BATTERY, "Crash"), SaveKeys.of("Crash.chd.sav"))
        assertEquals(SaveKey(SaveKind.BATTERY, "Crash"), SaveKeys.of("Crash.SRM"))
    }
    @Test fun a_dot_in_the_title_is_not_a_rom_extension() {
        assertEquals("Super Mario Bros. 3", SaveKeys.of("Super Mario Bros. 3.sav")!!.game)
        assertEquals("Super Mario Bros. 3", SaveKeys.of("Super Mario Bros. 3.nes.sav")!!.game)
        assertEquals("Game v1.1", SaveKeys.of("Game v1.1.srm")!!.game)
    }
    /** The fold's failed-write guard groups a written name by its key again: every format's name must give it back. */
    @Test fun every_formats_name_round_trips_to_its_key() {
        for ((game, rom) in listOf("Pokemon Emerald" to "Pokemon Emerald.gba", "Super Mario Bros. 3" to "Super Mario Bros. 3.nes", "Crash" to "Crash.chd")) {
            for (format in SaveFormat.entries) {
                val battery = SaveKey(SaveKind.BATTERY, game)
                assertEquals(battery, SaveKeys.of(SaveKeys.fileName(battery, format, rom)!!), "$format $rom")
                val clock = SaveKey(SaveKind.CLOCK, game)
                assertEquals(clock, SaveKeys.of(SaveKeys.fileName(clock, format, rom)!!), "$format $rom clock")
            }
        }
    }
    /** A ROM inside an archive named after it (`X.gb.zip`): every format's name, and the ROM, give one key. */
    @Test fun an_archived_rom_name_round_trips_to_one_key() {
        val rom = "X.gb.zip"
        val game = SaveKeys.romKey(rom)
        assertEquals("X", game)
        for (format in SaveFormat.entries) {
            val name = SaveKeys.batteryName(format, rom)
            assertEquals(SaveKey(SaveKind.BATTERY, game), SaveKeys.of(name), "$format $name")
            assertEquals(name, SaveKeys.fileName(SaveKey(SaveKind.BATTERY, game), format, rom), "$format")
        }
        assertEquals(SaveKey(SaveKind.CLOCK, game), SaveKeys.of(SaveKeys.fileName(SaveKey(SaveKind.CLOCK, game), SaveFormat.RETROARCH, rom)!!))
        assertEquals("Sonic", SaveKeys.romKey("Sonic.md.7z"))
        assertEquals("Super Mario Bros. 3", SaveKeys.romKey("Super Mario Bros. 3.zip"))
    }
    @Test fun clock_files_follow_their_game() {
        assertEquals(SaveKey(SaveKind.CLOCK, "Pokemon Crystal"), SaveKeys.of("Pokemon Crystal.gbc.rtc"))
    }
    @Test fun other_files_have_no_key() {
        assertNull(SaveKeys.of("vmu_save_A1.bin"))
        assertNull(SaveKeys.of("Game.A1.bin"))
        assertNull(SaveKeys.of("Game.bkr"))
        assertNull(SaveKeys.of("Game.srm.20261015-101010.bak"))
    }
    @Test fun rom_entries_drop_known_extensions_only() {
        assertEquals("Sonic", SaveKeys.romKey("Sonic.md"))
        assertEquals("Sonic", SaveKeys.romKey("Sonic.zip"))
        assertEquals("Final Fantasy VII", SaveKeys.romKey("Final Fantasy VII"))      // disc folder
        assertEquals("Super Mario Bros. 3", SaveKeys.romKey("Super Mario Bros. 3"))
    }
    @Test fun names_in_each_format() {
        val k = SaveKey(SaveKind.BATTERY, "Pokemon Emerald")
        assertEquals("Pokemon Emerald.srm", SaveKeys.fileName(k, SaveFormat.RETROARCH, null))
        assertEquals("Pokemon Emerald.srm", SaveKeys.fileName(k, SaveFormat.RETROARCH_COMPRESSED, null))
        assertEquals("Pokemon Emerald.sav", SaveKeys.fileName(k, SaveFormat.GENERIC, null))
        assertEquals("Pokemon Emerald.gba.sav", SaveKeys.fileName(k, SaveFormat.MINUI, "Pokemon Emerald.gba"))
        assertNull(SaveKeys.fileName(k, SaveFormat.MINUI, null))
        assertEquals("Pokemon Crystal.gbc.rtc", SaveKeys.fileName(SaveKey(SaveKind.CLOCK, "Pokemon Crystal"), SaveFormat.RETROARCH, "Pokemon Crystal.gbc"))
        assertNull(SaveKeys.fileName(SaveKey(SaveKind.CLOCK, "X"), SaveFormat.RETROARCH, null))
    }
    @Test fun batteryName_matches_NxPaths() {
        for (f in SaveFormat.entries) assertEquals(
            NxPaths("GBA", "gpsp", "Game.gba", SaveNaming(saveFormat = f)).saveRam.substringAfterLast('/'),
            SaveKeys.batteryName(f, "Game.gba"))
    }

    /** `X.gb.sav` is ROM `X.gb`'s MinUI name and ROM `X.gb.zip`'s Generic name alike. */
    @Test fun a_save_named_after_a_rom_file_alone_does_not_tell_the_rom() {
        assertEquals(true, SaveKeys.romAmbiguous("Red.gb.sav"))
        assertEquals(true, SaveKeys.romAmbiguous("Red.gb.srm"))
        assertEquals(false, SaveKeys.romAmbiguous("Red.gb.zip.sav"))
        assertEquals(false, SaveKeys.romAmbiguous("Red.sav"))
        assertEquals(false, SaveKeys.romAmbiguous("Red.gb.rtc"))
        assertNull(SaveKeys.romOf("Red.gb.sav"))
        assertNull(SaveKeys.romOf("Red.sav"))
        assertEquals("Red.gb.zip", SaveKeys.romOf("Red.gb.zip.sav"))
        assertEquals("Red.gb", SaveKeys.romOf("Red.gb.rtc"))
        // Another copy's name settles it: an archive's MinUI name, or the stripped name ROM `Red.gb` gives (not `Red.gb.zip`).
        assertEquals("Red.gb.zip", SaveKeys.romOf("Red.gb.sav", listOf("Red.gb.zip.sav")))
        assertEquals("Red.gb", SaveKeys.romOf("Red.gb.sav", listOf("Red.srm")))
        assertEquals("Red.gb", SaveKeys.romOf("Red.sav", listOf("Red.gb.sav")))
        assertNull(SaveKeys.romOf("Red.gb.sav", listOf("Red.gb.srm")))
    }
}
