package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NxPathsTest {
    private val p = NxPaths("GB", "gambatte", "Tetris (World).gb")

    @Test fun battery_save_lives_in_saves_tag() {
        assertEquals("Saves/GB", p.savesDir)
        assertEquals("Saves/GB/Tetris (World).srm", p.saveRam)
    }

    @Test fun states_use_retroarch_naming_with_zero_based_slots() {
        assertEquals(".userdata/shared/GB-gambatte", p.statesDir)
        assertEquals(".userdata/shared/GB-gambatte/Tetris (World).state", p.state(0))
        assertEquals(".userdata/shared/GB-gambatte/Tetris (World).state1", p.state(1))
        assertEquals(".userdata/shared/GB-gambatte/Tetris (World).state7", p.state(7))
        assertEquals(".userdata/shared/GB-gambatte/Tetris (World).state.auto", p.state(NxPaths.AUTO_SLOT))
    }

    @Test fun thumbnails_and_last_slot_use_the_full_file_name() {
        assertEquals(".userdata/shared/.minui/GB", p.minuiDir)
        assertEquals(".userdata/shared/.minui/GB/Tetris (World).gb.0.bmp", p.thumb(0))
        assertEquals(".userdata/shared/.minui/GB/Tetris (World).gb.9.bmp", p.thumb(NxPaths.AUTO_SLOT))
        assertEquals(".userdata/shared/.minui/GB/Tetris (World).gb.txt", p.lastSlot)
    }

    @Test fun config_is_per_platform_and_game_config_keeps_the_extension() {
        assertEquals(".userdata/android/GB-gambatte", p.configDir)
        assertEquals(".userdata/android/GB-gambatte/minarch.cfg", p.consoleConfig)
        assertEquals(".userdata/android/GB-gambatte/Tetris (World).gb.cfg", p.gameConfig)
    }

    @Test fun only_the_last_extension_is_stripped() {
        assertEquals("Saves/GBA/Mario v1.1.srm", NxPaths("GBA", "mgba", "Mario v1.1.zip").saveRam)
        assertEquals("Saves/FC/NoExtension.srm", NxPaths("FC", "fceumm", "NoExtension").saveRam)
    }

    @Test fun only_a_two_to_four_character_extension_is_stripped_like_minarch() {
        assertEquals("Saves/GBA/Mario v1.1.srm", NxPaths("GBA", "mgba", "Mario v1.1").saveRam)
        assertEquals("Saves/GBA/Game.abcde.srm", NxPaths("GBA", "mgba", "Game.abcde").saveRam)
        assertEquals("Saves/GB/Tetris.srm", NxPaths("GB", "gambatte", "Tetris.gb").saveRam)
        assertEquals("Saves/DOOM/Doom.srm", NxPaths("DOOM", "prboom", "Doom.wad4").saveRam)
    }

    @Test fun real_world_names_are_used_verbatim() {
        val name = "Pokémon - Red Version (USA, Europe) (SGB Enhanced) [Hack's].gb"
        val q = NxPaths("GB", "gambatte", name)
        assertEquals("Saves/GB/Pokémon - Red Version (USA, Europe) (SGB Enhanced) [Hack's].srm", q.saveRam)
        assertEquals(".userdata/shared/.minui/GB/$name.3.bmp", q.thumb(3))
    }

    @Test fun out_of_range_slots_are_rejected() {
        assertFailsWith<IllegalArgumentException> { p.state(8) }
        assertFailsWith<IllegalArgumentException> { p.state(-1) }
        assertFailsWith<IllegalArgumentException> { p.thumb(8) }
    }

    @Test fun rzip_magic_is_detected() {
        assertTrue(isRzipCompressed("#RZIPv1#".encodeToByteArray()))
        assertFalse(isRzipCompressed(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)))
        assertFalse(isRzipCompressed("#RZI".encodeToByteArray()))
        assertFalse(isRzipCompressed(ByteArray(0)))
    }

    @Test fun recents_path_matches_minarch() {
        assertEquals(".userdata/shared/.minui/recent.txt", NxPaths.RECENTS)
    }

    @Test fun stem_is_the_rom_name_minus_a_short_extension() {
        assertEquals("Tetris", NxPaths("GB", "gambatte", "Tetris.gb").stem)
        assertEquals("Game.v1.2", NxPaths("GB", "gambatte", "Game.v1.2.gb").stem)
        assertEquals("Archive.tar", NxPaths("GB", "gambatte", "Archive.tar.gz2x").stem)   // ".gz2x" is 5 chars with the dot: stripped
        assertEquals("Archive.tar.gz2xx", NxPaths("GB", "gambatte", "Archive.tar.gz2xx").stem)   // 6: kept
        // Dreamcast discs: the stem is flycast's content name (basename minus its extension), which names the VMU.
        for (ext in listOf("chd", "cdi", "gdi", "cue", "m3u")) {
            assertEquals("Soulcalibur (USA)", NxPaths("DC", "flycast", "Soulcalibur (USA).$ext").stem, ext)
        }
    }

    private fun named(save: SaveFormat = SaveFormat.DEFAULT, state: StateFormat = StateFormat.DEFAULT, extracted: Boolean = false, inner: String? = null, rom: String = "Game.zip") =
        NxPaths("GBA", "mgba", rom, SaveNaming(save, state, extracted), inner)

    @Test fun battery_save_name_per_format() {
        assertEquals("Saves/GBA/Game.srm", named(SaveFormat.RETROARCH, rom = "Game.gba").saveRam)
        assertEquals("Saves/GBA/Game.srm", named(SaveFormat.RETROARCH_COMPRESSED, rom = "Game.gba").saveRam)
        assertEquals("Saves/GBA/Game.sav", named(SaveFormat.GENERIC, rom = "Game.gba").saveRam)
        assertEquals("Saves/GBA/Game.gba.sav", named(SaveFormat.MINUI, rom = "Game.gba").saveRam)
        assertTrue(named(SaveFormat.RETROARCH_COMPRESSED).saveCompressed); assertFalse(named(SaveFormat.MINUI).saveCompressed)
    }

    @Test fun clock_keeps_the_extension_in_every_format() {
        SaveFormat.entries.forEach { assertEquals("Saves/GBA/Game.gba.rtc", named(it, rom = "Game.gba").clock) }
    }

    @Test fun state_names_per_format_and_slot() {
        val ra = named(state = StateFormat.RETROARCH, rom = "Game.gba")
        assertEquals(".userdata/shared/GBA-mgba/Game.state", ra.state(0))
        assertEquals(".userdata/shared/GBA-mgba/Game.state3", ra.state(3))
        assertEquals(".userdata/shared/GBA-mgba/Game.state.auto", ra.state(NxPaths.AUTO_SLOT))
        val ish = named(state = StateFormat.RETROARCH_ISH, rom = "Game.gba")
        assertEquals(".userdata/shared/GBA-mgba/Game.state.0", ish.state(0))
        assertEquals(".userdata/shared/GBA-mgba/Game.state.7", ish.state(7))
        assertEquals(".userdata/shared/GBA-mgba/Game.state.auto", ish.state(NxPaths.AUTO_SLOT))
        val minui = named(state = StateFormat.MINUI, rom = "Game.gba")
        assertEquals(".userdata/shared/GBA-mgba/Game.gba.st0", minui.state(0))
        assertEquals(".userdata/shared/GBA-mgba/Game.gba.st9", minui.state(NxPaths.AUTO_SLOT))
        assertTrue(named(state = StateFormat.RETROARCH_ISH_COMPRESSED).stateCompressed); assertFalse(ish.stateCompressed)
        assertFailsWith<IllegalArgumentException> { ra.state(8) }
    }

    @Test fun candidates_put_the_configured_name_first() {
        assertEquals(
            listOf("Saves/GBA/Game.gba.sav", "Saves/GBA/Game.srm", "Saves/GBA/Game.sav"),
            named(SaveFormat.MINUI, rom = "Game.gba").saveRamCandidates(),
        )
        assertEquals(
            listOf("Saves/GBA/Game.srm", "Saves/GBA/Game.gba.sav", "Saves/GBA/Game.sav"),
            named(SaveFormat.RETROARCH, rom = "Game.gba").saveRamCandidates(),
        )
    }

    @Test fun state_candidates_cover_every_format_for_the_slot() {
        assertEquals(
            listOf(".userdata/shared/GBA-mgba/Game.state.2", ".userdata/shared/GBA-mgba/Game.gba.st2", ".userdata/shared/GBA-mgba/Game.state2"),
            named(state = StateFormat.RETROARCH_ISH, rom = "Game.gba").stateCandidates(2),
        )
        assertEquals(
            listOf(".userdata/shared/GBA-mgba/Game.state.auto", ".userdata/shared/GBA-mgba/Game.gba.st9"),
            named(state = StateFormat.RETROARCH, rom = "Game.gba").stateCandidates(NxPaths.AUTO_SLOT),
        )
    }

    @Test fun candidates_cross_the_extracted_name_setting() {
        // the configured name in the configured format, its other formats, then the other name in every format
        assertEquals(
            listOf(
                "Saves/GBA/Game (USA).srm", "Saves/GBA/Game (USA).gba.sav", "Saves/GBA/Game (USA).sav",
                "Saves/GBA/Game.srm", "Saves/GBA/Game.zip.sav", "Saves/GBA/Game.sav",
            ),
            named(SaveFormat.RETROARCH, extracted = true, inner = "Game (USA).gba").saveRamCandidates(),
        )
        assertEquals(
            listOf(
                "Saves/GBA/Game.zip.sav", "Saves/GBA/Game.srm", "Saves/GBA/Game.sav",
                "Saves/GBA/Game (USA).gba.sav", "Saves/GBA/Game (USA).srm", "Saves/GBA/Game (USA).sav",
            ),
            named(SaveFormat.MINUI, extracted = false, inner = "Game (USA).gba").saveRamCandidates(),
        )
        val d = ".userdata/shared/GBA-mgba"
        assertEquals(
            listOf("$d/Game (USA).state.2", "$d/Game (USA).gba.st2", "$d/Game (USA).state2", "$d/Game.state.2", "$d/Game.zip.st2", "$d/Game.state2"),
            named(state = StateFormat.RETROARCH_ISH, extracted = true, inner = "Game (USA).gba").stateCandidates(2),
        )
        assertEquals(
            listOf("$d/Game.state.auto", "$d/Game.zip.st9", "$d/Game (USA).state.auto", "$d/Game (USA).gba.st9"),
            named(state = StateFormat.RETROARCH, extracted = false, inner = "Game (USA).gba").stateCandidates(NxPaths.AUTO_SLOT),
        )
        // the clock has no fallback on the handheld: one name
        assertEquals("Saves/GBA/Game (USA).gba.rtc", named(extracted = true, inner = "Game (USA).gba").clock)
        assertEquals("Saves/GBA/Game.zip.rtc", named(extracted = false, inner = "Game (USA).gba").clock)
    }

    @Test fun extracted_name_applies_only_with_the_setting_and_an_inner_file() {
        assertEquals("Game (USA).gba", named(extracted = true, inner = "Game (USA).gba").saveName)
        assertEquals("Saves/GBA/Game (USA).srm", named(extracted = true, inner = "Game (USA).gba").saveRam)
        assertEquals("Game.zip", named(extracted = false, inner = "Game (USA).gba").saveName)
        assertEquals("Game.zip", named(extracted = true, inner = null).saveName)
        assertEquals("Saves/GBA/Game.zip.sav", named(SaveFormat.MINUI).saveRam)
        // markers and the core-folder stem keep the launched file
        val p = named(extracted = true, inner = "Game (USA).gba")
        assertEquals(".userdata/shared/.minui/GBA/Game.zip.txt", p.lastSlot)
        assertEquals("Game", p.stem)
        assertEquals("Game (USA)", p.saveStem)
        assertEquals(named(extracted = true, inner = "X.gba").saveRam, named(extracted = true).withInner("X.gba").saveRam)
    }

    @Test fun multi_disc_games_are_named_after_their_m3u_already() {
        // The library launches D/D.m3u for a disc folder (LibraryScanner), the handheld's M3U_findForRom name.
        val p = NxPaths("PS", "pcsx_rearmed", "Final Fantasy VII.m3u", SaveNaming(SaveFormat.MINUI, StateFormat.MINUI))
        assertEquals("Saves/PS/Final Fantasy VII.m3u.sav", p.saveRam)
        assertEquals(".userdata/shared/PS-pcsx_rearmed/Final Fantasy VII.m3u.st0", p.state(0))
    }
    @Test fun every_emulator_of_a_console_shares_the_console_save_folder() {
        assertEquals("Saves/GBA/Game.srm", NxPaths("MGBA", "mgba", "Game.gba").saveRam)
        assertEquals("Saves/PS", NxPaths("PSX", "swanstation", "Crash.chd").savesDir)
        assertEquals("Saves/MD/Sonic.md.rtc", NxPaths("GPGX", "genesis_plus_gx", "Sonic.md").clock)
        assertEquals(".userdata/shared/MGBA-mgba", NxPaths("MGBA", "mgba", "Game.gba").statesDir)   // states stay per emulator
    }
    @Test fun a_genesis_game_on_picodrive_keeps_its_states_where_the_handhelds_md_pak_does() {
        val md = CoreRegistry.forTag("MD")!!
        val p = NxPaths("MD", md.id, "Sonic.md")
        assertEquals(".userdata/shared/MD-picodrive", p.statesDir)
        assertEquals(".userdata/shared/.minui/MD", p.minuiDir)
        assertEquals("Saves/MD", p.savesDir)
        assertEquals(".userdata/shared/GPGX-genesis_plus_gx", NxPaths("GPGX", CoreRegistry.forTag("GPGX")!!.id, "Sonic.md").statesDir)
    }
}
