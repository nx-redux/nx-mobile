package com.nxredux.mobile.cheats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheatMatchTest {
    private val gb = setOf("gb", "gbc", "dmg", "zip")

    @Test fun display_name_strips_extensions_and_trailing_groups() {
        assertEquals("Super Example World", CheatMatch.displayName("Super Example World (USA).zip"))
        assertEquals("Super Example World", CheatMatch.displayName("Super Example World (USA) [!].7z"))
        assertEquals("Super Example World", CheatMatch.displayName("Super Example World (USA) (Rev 1).rar"))
        assertEquals("Game", CheatMatch.displayName("Game.p8.png"))          // repeated short extensions
        assertEquals("Tetris", CheatMatch.displayName("Tetris"))
        assertEquals("(Weird)", CheatMatch.displayName("(Weird).gb"))         // never nuked to nothing
        assertEquals("a.longext", CheatMatch.displayName("a.longext"))        // 7-char extension stays
        assertTrue(CheatMatch.displayName(" (USA).gb").isNotEmpty())
        assertTrue(CheatMatch.displayName("   ").isNotEmpty())
    }

    @Test fun candidates_follow_minarch_order() {
        assertEquals(
            listOf(
                "Tetris (World) (Rev 1).gb.cht",
                "Tetris (World) (Rev 1).dmg.cht",
                "Tetris (World) (Rev 1).gbc.cht",
                "Tetris (World) (Rev 1).cht",
                "Tetris.cht",
            ),
            CheatMatch.candidates("Tetris (World) (Rev 1).gb", "Tetris", gb),
        )
    }

    @Test fun candidates_try_the_full_rom_stem_before_the_display_name() {
        // A GoodTools-style cheat named after the whole stem still matches exactly with the title cleaned.
        val c = CheatMatch.candidates("Tetris [!].gb", "Tetris", gb)
        assertEquals(listOf("Tetris [!].gb.cht", "Tetris [!].dmg.cht", "Tetris [!].gbc.cht", "Tetris [!].cht", "Tetris.cht"), c)
    }

    @Test fun candidates_for_a_zip_try_the_inner_extensions_and_the_title() {
        assertEquals(
            listOf("1941.zip.cht", "1941.dmg.cht", "1941.gb.cht", "1941.gbc.cht", "1941.cht", "1941: Counter Attack.cht"),
            CheatMatch.candidates("1941.zip", "1941: Counter Attack", gb),
        )
    }

    @Test fun candidates_without_an_extension_skip_the_extension_variants() {
        assertEquals(listOf("Tetris.cht"), CheatMatch.candidates("Tetris", "Tetris", gb))
        assertEquals(listOf("Tetris.cht"), CheatMatch.candidates("Tetris", "", gb))
    }

    // Ported from nx-redux tests/test_cheat_match.c.
    @Test fun exact_single_match_is_selected() {
        val files = listOf("Advance Wars (USA, Europe) (Code Breaker).cht")
        assertEquals(files, CheatMatch.select("Advance Wars", "Advance Wars (USA, Europe)", files))
    }

    @Test fun different_stems_are_rejected() {
        val files = listOf("Super Mario World (USA).cht", "Super Mario Bros (USA).cht")
        assertEquals(emptyList(), CheatMatch.select("Super Mario", "Super Mario (USA)", files))
    }

    @Test fun matching_region_is_preferred() {
        val files = listOf("Sonic (Japan).cht", "Sonic (USA).cht")
        assertEquals(listOf("Sonic (USA).cht"), CheatMatch.select("Sonic", "Sonic (USA)", files))
    }

    @Test fun same_region_variants_are_merged_in_input_order() {
        val files = listOf("Contra (USA) (GameShark).cht", "Contra (USA) (Code Breaker).cht", "Contra (USA).cht")
        assertEquals(
            listOf("Contra (USA).cht", "Contra (USA) (GameShark).cht", "Contra (USA) (Code Breaker).cht"),
            CheatMatch.select("Contra", "Contra (USA)", files),
        )
    }

    @Test fun variants_returns_the_same_group_no_source_first() {
        val files = listOf("Contra (USA) (GameShark).cht", "Contra (USA).cht", "Contra (Japan).cht", "Contra (USA) (Code Breaker).cht")
        assertEquals(
            listOf("Contra (USA).cht", "Contra (USA) (GameShark).cht", "Contra (USA) (Code Breaker).cht"),
            CheatMatch.variants("Contra (USA) (GameShark).cht", files),
        )
    }

    @Test fun plain_file_is_preferred_for_a_region_less_rom() {
        val files = listOf("Zelda.cht", "Zelda (USA).cht")
        assertEquals(listOf("Zelda.cht"), CheatMatch.select("Zelda", "Zelda", files))
    }

    @Test fun fewer_extra_qualifiers_win_then_the_smallest_key() {
        val files = listOf("Tetris (World) (Rev 1).cht", "Tetris (World).cht", "Tetris (Japan).cht")
        assertEquals(listOf("Tetris (World).cht"), CheatMatch.select("Tetris", "Tetris (World).gb", files))
        // Same overlap, same extras, no preferred region: the lexically smaller key ("france" < "japan") wins.
        assertEquals(listOf("Tetris (France).cht"), CheatMatch.select("Tetris", "Tetris.gb", listOf("Tetris (Japan).cht", "Tetris (France).cht")))
    }

    @Test fun a_region_less_rom_takes_usa_then_world_then_europe() {
        assertEquals(listOf("Tetris (World).cht"), CheatMatch.select("Tetris", "Tetris.gb", listOf("Tetris (World).cht", "Tetris (Japan).cht")))
        val files = listOf("Game (Europe).cht", "Game (Japan).cht", "Game (USA).cht", "Game (World).cht")
        assertEquals(listOf("Game (USA).cht"), CheatMatch.select("Game", "Game.md", files))
        assertEquals(listOf("Game (Europe).cht"), CheatMatch.select("Game", "Game.md", listOf("Game (Japan).cht", "Game (Europe).cht")))
        // The ROM's own region still comes first.
        assertEquals(listOf("Game (Japan).cht"), CheatMatch.select("Game", "Game (Japan).md", files))
    }

    @Test fun a_source_only_file_loses_to_a_regional_one_for_a_region_less_rom() {
        // The database's `(Rumbles)` packs have no region, so they used to win as "no extra qualifiers".
        val files = listOf(
            "Contra III - The Alien Wars (USA) (Action Replay).cht", "Contra III - The Alien Wars (USA).cht",
            "Contra Iii - The Alien Wars (Rumbles).cht",
        )
        assertEquals(
            listOf("Contra III - The Alien Wars (USA).cht", "Contra III - The Alien Wars (USA) (Action Replay).cht"),
            CheatMatch.select("Contra III - The Alien Wars", "Contra III - The Alien Wars.sfc", files),
        )
        // Alone, it still loads; a plain region-less file still takes it in.
        val rumbles = listOf("Comix Zone (Rumbles).cht")
        assertEquals(rumbles, CheatMatch.select("Comix Zone", "Comix Zone.md", rumbles))
        assertEquals(
            listOf("Comix Zone.cht", "Comix Zone (Rumbles).cht"),
            CheatMatch.select("Comix Zone", "Comix Zone.md", listOf("Comix Zone (USA).cht", "Comix Zone (Rumbles).cht", "Comix Zone.cht")),
        )
        // A ROM with a region keeps its region's sourced file over another region's plain one.
        val jp = listOf("Game (Japan) (Action Replay).cht", "Game (USA).cht")
        assertEquals(listOf("Game (Japan) (Action Replay).cht"), CheatMatch.select("Game", "Game (Japan).md", jp))
    }

    @Test fun a_bracket_group_inside_the_name_stays_in_the_stem() {
        val files = listOf("Game [Hack] Deluxe (USA).cht", "Game (USA).cht")
        assertEquals(listOf("Game [Hack] Deluxe (USA).cht"), CheatMatch.select("Game [Hack] Deluxe", "Game [Hack] Deluxe (Europe).gba", files))
        assertEquals(listOf("Game (USA).cht"), CheatMatch.select("Game", "Game (USA).gba", files))
    }

    @Test fun stems_that_differ_only_in_spacing_or_punctuation_match_when_none_is_exact() {
        // Dreamcast: the ROM set and the database spell names differently.
        val dc = listOf("Soul Calibur.cht", "Soul Fighter.cht", "Marvel Vs. Capcom 2.cht", "Marvel Vs. Capcom.cht")
        assertEquals(listOf("Soul Calibur.cht"), CheatMatch.select("Soulcalibur", "Soulcalibur (USA).chd", dc))
        assertEquals(listOf("Marvel Vs. Capcom 2.cht"), CheatMatch.select("Marvel vs Capcom 2", "Marvel vs Capcom 2.chd", dc))
        assertEquals(listOf("Chu Chu Rocket.cht"), CheatMatch.select("ChuChu Rocket", "ChuChu Rocket.chd", listOf("Chu Chu Rocket.cht")))
        assertEquals(listOf("Quake III Arena.cht"), CheatMatch.select("Quake III - Arena", "Quake III - Arena.chd", listOf("Quake III Arena.cht")))
        // An exact stem wins: the loose one is only a fallback.
        val mm = listOf("MegaMan X (USA).cht", "Mega Man X (Japan).cht")
        assertEquals(listOf("Mega Man X (Japan).cht"), CheatMatch.select("Mega Man X", "Mega Man X (USA).sfc", mm))
        // Letters and digits still have to agree.
        assertEquals(emptyList(), CheatMatch.select("Marvel vs Capcom", "Marvel vs Capcom 2.chd", listOf("Marvel Vs. Capcom 2.cht")))
    }

    @Test fun group_key_tie_break_matches_the_c_code() {
        // "Game|rev 1," < "Game|rev," because ' ' < ','.
        val files = listOf("Game (Rev).cht", "Game (Rev 1).cht")
        assertEquals(listOf("Game (Rev 1).cht"), CheatMatch.select("Game", "Game.gb", files))
    }

    @Test fun stem_match_is_case_insensitive_and_non_cht_files_are_ignored() {
        val files = listOf("tetris (World).CHT", "Tetris (World).txt", "Tetris (World).cht.bak")
        assertEquals(listOf("tetris (World).CHT"), CheatMatch.select("Tetris", "Tetris.gb", files))
    }

    @Test fun metacharacters_in_the_name_are_literal() {
        val files = listOf("Q*bert (USA).cht", "Qbert (USA).cht", "Mr. Do! (USA).cht")
        assertEquals(listOf("Q*bert (USA).cht"), CheatMatch.select("Q*bert", "Q*bert (USA).gb", files))
        assertEquals(listOf("Mr. Do! (USA).cht"), CheatMatch.select("Mr. Do!", "Mr. Do! (USA).gb", files))
    }

    @Test fun source_label_keeps_case_and_is_empty_for_regions() {
        assertEquals("GameShark", CheatMatch.sourceLabel("Contra (USA) (GameShark).cht"))
        assertEquals("Code Breaker", CheatMatch.sourceLabel("Contra (USA, Europe) (Code Breaker).cht"))
        assertEquals("", CheatMatch.sourceLabel("Zelda (USA).cht"))
    }

    // libretro's PSP set names files "Name [US] [ULUS-10041].cht" (nx-redux #257): the stem ends at " [" and the
    // region code compares with the ROM's No-Intro region.
    @Test fun psp_bracket_region_matches_the_rom_region() {
        val files = listOf(
            "Grand Theft Auto - Liberty City Stories [EU] [ULES-00151].cht",
            "Grand Theft Auto - Liberty City Stories [JP] [ULJM-05255].cht",
            "Grand Theft Auto - Liberty City Stories [US] [ULUS-10041].cht",
        )
        val name = "Grand Theft Auto - Liberty City Stories"
        assertEquals(listOf(files[2]), CheatMatch.select(name, "$name (USA).iso", files))
        assertEquals(listOf(files[0]), CheatMatch.select(name, "$name (Europe) (En,Fr,De,Es,It).iso", files))
    }

    @Test fun psp_multi_region_code_matches_either_region() {
        val files = listOf("Lumines [JP] [ULJM-05013].cht", "Lumines [US-EU] [ULUS-10046].cht")
        assertEquals(listOf(files[1]), CheatMatch.select("Lumines", "Lumines (Europe)", files))
    }

    @Test fun psp_serial_in_the_rom_name_picks_that_file() {
        val files = listOf("Daxter [US] [UCUS-98618].cht", "Daxter [US] [UCUS-98646].cht")
        assertEquals(listOf(files[1]), CheatMatch.select("Daxter", "Daxter (USA) [UCUS-98646]", files))
    }

    @Test fun psp_retail_serial_beats_a_homebrew_id_of_the_same_region() {
        val files = listOf("Patapon 2 [US] [HB4B12D3BC].cht", "Patapon 2 [US] [UCUS-98732].cht", "Patapon 2 [EU] [UCES-01177].cht")
        assertEquals(listOf(files[1]), CheatMatch.select("Patapon 2", "Patapon 2 (USA) (En,Fr,Es).iso", files))
    }

    // The Mega Drive set as it ships: the ROM is region-less, its files are a sourced USA file, a source-only pack and the
    // plain file under a longer title ("A _ B": the database's alternate titles, or its "&").
    private val sonic3d = listOf(
        "Sonic 3D Blast (USA) (Action Replay).cht", "Sonic 3d Blast (Rumbles).cht",
        "Sonic 3D Blast _ Sonic 3D Flickies' Island (USA, Europe, Korea).cht",
    )

    @Test fun a_source_only_pack_loses_to_a_file_for_the_roms_region() {
        val ar = listOf("Sonic 3D Blast (USA) (Action Replay).cht")
        assertEquals(ar, CheatMatch.select("Sonic 3D Blast", "Sonic 3D Blast.md", sonic3d))
        assertEquals(ar, CheatMatch.select("Sonic 3D Blast", "Sonic 3D Blast (USA).md", sonic3d))
        // Another region's file doesn't take its place: a region-less ROM keeps the pack over a Japanese file.
        val jp = listOf("Chuck Rock II (Japan) (Action Replay).cht", "Chuck Rock Ii (Rumbles).cht")
        assertEquals(listOf("Chuck Rock Ii (Rumbles).cht"), CheatMatch.select("Chuck Rock II", "Chuck Rock II.md", jp))
        assertEquals(listOf(jp[0]), CheatMatch.select("Chuck Rock II", "Chuck Rock II (Japan).md", jp))
    }

    @Test fun an_alternate_title_after_an_underscore_matches_when_no_stem_does() {
        val files = listOf("Sonic 3D Blast _ Sonic 3D Flickies' Island (USA, Europe, Korea).cht")
        assertEquals(files, CheatMatch.select("Sonic 3D Blast", "Sonic 3D Blast.md", files))
        assertEquals(files, CheatMatch.select("Sonic 3D Flickies' Island", "Sonic 3D Flickies' Island (Europe).md", files))
        assertEquals(emptyList(), CheatMatch.select("Sonic 3D", "Sonic 3D.md", files))
        // An exact stem wins: "_" is also the database's "&" (Asterix & Obelix is another game).
        val asterix = listOf("Asterix _ Obelix (Europe).cht", "Asterix (Europe).cht")
        assertEquals(listOf("Asterix (Europe).cht"), CheatMatch.select("Asterix", "Asterix (Europe).sfc", asterix))
    }

    @Test fun files_for_the_same_regional_rom_merge_with_the_winning_group() {
        val files = listOf("Game (USA).cht", "Game (USA) (Beta).cht", "Game (USA, Europe) (Action Replay).cht", "Game (Japan).cht")
        // The USA ROM is the one the (USA, Europe) file covers; a beta is another ROM and never merges by region.
        assertEquals(listOf("Game (USA).cht", "Game (USA, Europe) (Action Replay).cht"), CheatMatch.select("Game", "Game (USA).md", files))
        // A European ROM shares the (USA, Europe) file's ROM, not the USA one.
        val eu = listOf("Game (Europe).cht", "Game (USA).cht", "Game (USA, Europe) (Action Replay).cht")
        assertEquals(listOf("Game (Europe).cht", "Game (USA, Europe) (Action Replay).cht"), CheatMatch.select("Game", "Game (Europe).md", eu))
    }

    @Test fun contra_hard_corps_keeps_its_region_group() {
        val files = listOf("Contra - Hard Corps (Rumbles).cht", "Contra - Hard Corps (USA, Korea) (Action Replay).cht", "Contra - Hard Corps (USA, Korea).cht")
        val usa = listOf("Contra - Hard Corps (USA, Korea).cht", "Contra - Hard Corps (USA, Korea) (Action Replay).cht")
        assertEquals(usa, CheatMatch.select("Contra - Hard Corps", "Contra - Hard Corps.md", files))
        assertEquals(usa, CheatMatch.select("Contra - Hard Corps", "Contra - Hard Corps (USA, Korea).md", files))
    }

    @Test fun variants_bring_in_the_same_regional_roms_files() {
        val files = listOf("Game (USA).cht", "Game (USA, Europe) (Action Replay).cht", "Game (Japan).cht", "Game (USA) (Beta).cht", "Game (Rumbles).cht")
        assertEquals(listOf("Game (USA).cht", "Game (USA, Europe) (Action Replay).cht"), CheatMatch.variants("Game (USA).cht", files))
        assertEquals(listOf("Game (Japan).cht"), CheatMatch.variants("Game (Japan).cht", files))
    }

    @Test fun a_titled_file_competes_when_the_exact_name_has_only_a_pack() {
        val files = listOf("Mercs (Rumbles).cht", "Senjou no Ookami II _ Mercs (World).cht")
        assertEquals(listOf("Senjou no Ookami II _ Mercs (World).cht"), CheatMatch.select("Mercs", "Mercs.md", files))
    }
}
