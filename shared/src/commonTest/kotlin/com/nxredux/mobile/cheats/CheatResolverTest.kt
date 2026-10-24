package com.nxredux.mobile.cheats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheatResolverTest {
    private val gb = setOf("gb", "gbc", "dmg", "zip")
    private fun cht(vararg cheats: Pair<String, String>) =
        "cheats = ${cheats.size}\n" + cheats.mapIndexed { i, (d, c) -> "cheat${i}_desc = \"$d\"\ncheat${i}_code = \"$c\"\n" }.joinToString("")

    @Test fun exact_candidate_wins_over_the_wildcard_stage() {
        val disk = mapOf(
            "Tetris.gb.cht" to cht("Exact" to "01"),
            "Tetris (World).cht" to cht("Wild" to "02"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Tetris.gb", "Tetris", gb, read = { disk[it] })
        assertEquals(listOf("Exact"), set.entries.map { it.desc })
        assertEquals("Tetris.gb.cht", set.expected)
        assertEquals("Exact", set.entries[0].info)   // single file: no source prefix
    }

    @Test fun a_cheat_named_after_the_full_rom_stem_is_an_exact_match() {
        val disk = mapOf(
            "Tetris [!].cht" to cht("Stem" to "01"),
            "Tetris (GameShark).cht" to cht("Other" to "02"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Tetris [!].gb", "Tetris", gb, read = { disk[it] })
        assertEquals(listOf("Stem"), set.entries.map { it.desc })
    }

    @Test fun a_bad_or_empty_candidate_falls_through_to_the_next() {
        val disk = mapOf(
            "Tetris.gb.cht" to "not a cheat file",
            "Tetris.cht" to cht("Later" to "03"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Tetris.gb", "Tetris", gb, read = { disk[it] })
        assertEquals(listOf("Later"), set.entries.map { it.desc })
        // A reader failure (null) is also "try the next".
        val set2 = CheatResolver.resolve(disk.keys.toList(), "Tetris.gb", "Tetris", gb, read = { if (it == "Tetris.gb.cht") null else disk[it] })
        assertEquals(listOf("Later"), set2.entries.map { it.desc })
    }

    @Test fun wildcard_stage_merges_variants_with_source_prefixes() {
        val disk = mapOf(
            "Contra (USA).cht" to cht("Lives" to "01"),
            "Contra (USA) (GameShark).cht" to cht("Health" to "02"),
            "Contra (Japan).cht" to cht("Wrong region" to "03"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Contra (USA).gb", "Contra", gb, read = { disk[it] })
        assertEquals(listOf("Lives", "Health"), set.entries.map { it.desc })
        assertEquals(listOf("Lives", "(GameShark) Health"), set.entries.map { it.info })
        assertEquals("Contra (USA).gb.cht", set.expected)
    }

    @Test fun wildcard_falls_back_to_the_title_stem() {
        // The file name has no readable stem, but the launcher's title (map.txt alias) does.
        val disk = mapOf("1941: Counter Attack (USA).cht" to cht("Coins" to "04"))
        val set = CheatResolver.resolve(disk.keys.toList(), "1941.zip", "1941: Counter Attack", gb, read = { disk[it] })
        assertEquals(listOf("Coins"), set.entries.map { it.desc })
    }

    @Test fun no_exact_stem_falls_back_to_the_first_file_starting_with_the_name() {   // nx-redux's glob, first hit
        val disk = mapOf(
            "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (GameShark).cht" to cht("Shark" to "01"),
            "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (GBC,SGB Enhanced).cht" to cht("Plain" to "02"),
            "Pokemon - Blue Version (USA, Europe).cht" to cht("Blue" to "03"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Pokemon - Yellow.gbc", "Pokemon - Yellow", gb, read = { disk[it] })
        assertEquals(listOf("Plain"), set.entries.map { it.desc })   // name order: "(GBC" sorts before "(Game"
        assertEquals("Pokemon - Yellow.gbc.cht", set.expected)
    }

    @Test fun an_exact_stem_still_beats_the_prefix_fallback() {
        val disk = mapOf("Tetris 2 (USA).cht" to cht("Two" to "01"), "Tetris (World).cht" to cht("One" to "02"))
        val set = CheatResolver.resolve(disk.keys.toList(), "Tetris.gb", "Tetris", gb, read = { disk[it] })
        assertEquals(listOf("One"), set.entries.map { it.desc })
    }

    @Test fun a_serial_picks_its_tagged_files_over_a_name_match() {
        val psp = setOf("iso", "cso", "pbp")
        val disk = mapOf(
            "Tekken 6 (PlayStation Store) [JP] [NPJH-50184].cht" to cht("Store" to "_L 0x1 0x1"),
            "Tekken 6 [EU] [ULES-01376].cht" to cht("EU" to "_L 0x2 0x2"),
            "Tekken 6 [US] [ULUS-10466].cht" to cht("US" to "_L 0x3 0x3"),
        )
        val us = CheatResolver.resolve(disk.keys.toList(), "Tekken 6.iso", "Tekken 6", psp, read = { disk[it] }, serial = "ULUS-10466")
        assertEquals(listOf("US"), us.entries.map { it.desc })
        // Unknown serial (a .chd, or a disc the cheats don't list): the name stages as before.
        val none = CheatResolver.resolve(disk.keys.toList(), "Tekken 6.iso", "Tekken 6", psp, read = { disk[it] }, serial = "ULUS-99999")
        assertEquals(listOf("US"), none.entries.map { it.desc })   // region-less name: [US] before [EU]
        // A .chd has no serial read: the ROM's region picks its [US] file, not the first name in the folder.
        val chd = CheatResolver.resolve(disk.keys.toList(), "Tekken 6 (USA).chd", "Tekken 6", psp + "chd", read = { disk[it] })
        assertEquals(listOf("US"), chd.entries.map { it.desc })
    }

    @Test fun nothing_matching_gives_an_empty_set_with_the_expected_name() {
        val set = CheatResolver.resolve(listOf("Other Game.cht"), "Tetris.gb", "Tetris", gb, read = { null })
        assertEquals(emptyList(), set.entries)
        assertEquals("Tetris.gb.cht", set.expected)
        assertEquals("Tetris.gb.cht", CheatResolver.resolve(emptyList(), "Tetris.gb", "Tetris", gb, read = { null }).expected)
    }

    @Test fun a_differently_spelled_database_name_still_resolves() {
        val disk = mapOf(
            "Marvel Vs. Capcom 2.cht" to cht("Unlock" to "01"),
            "Marvel Vs. Capcom.cht" to cht("One" to "02"),
            "Soul Calibur.cht" to cht("Health" to "03"),
        )
        val dc = setOf("chd", "cdi", "gdi")
        val mvc2 = CheatResolver.resolve(disk.keys.toList(), "Marvel vs Capcom 2.chd", "Marvel vs Capcom 2", dc, read = { disk[it] })
        assertEquals(listOf("Unlock"), mvc2.entries.map { it.desc })
        val sc = CheatResolver.resolve(disk.keys.toList(), "Soulcalibur (USA).chd", "Soulcalibur", dc, read = { disk[it] })
        assertEquals(listOf("Health"), sc.entries.map { it.desc })
    }

    @Test fun a_bad_file_in_the_merged_group_is_skipped() {
        val disk = mapOf(
            "Contra (USA).cht" to "garbage",
            "Contra (USA) (GameShark).cht" to cht("Health" to "02"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Contra (USA).gb", "Contra", gb, read = { disk[it] })
        assertEquals(listOf("Health"), set.entries.map { it.desc })
        assertEquals(listOf("Health"), set.entries.map { it.info })   // one file loaded: no prefix
    }

    @Test fun rejected_candidates_are_reported() {
        val disk = mapOf(
            "Tetris.gb.cht" to "garbage",
            "Tetris.cht" to cht("Later" to "03"),
        )
        val skipped = mutableListOf<Pair<String, String>>()
        val set = CheatResolver.resolve(disk.keys.toList(), "Tetris.gb", "Tetris", gb, read = { disk[it] }, onSkipped = { n, r -> skipped += n to r })
        assertEquals(listOf("Later"), set.entries.map { it.desc })
        assertEquals(listOf("Tetris.gb.cht"), skipped.map { it.first })
        assertTrue(skipped.single().second.isNotBlank())
        // A null read is the platform's to report, not a rejection.
        skipped.clear()
        CheatResolver.resolve(disk.keys.toList(), "Tetris.gb", "Tetris", gb, read = { if (it == "Tetris.gb.cht") null else disk[it] }, onSkipped = { n, r -> skipped += n to r })
        assertEquals(emptyList(), skipped)
    }

    @Test fun exact_candidate_merges_its_source_variants() {
        val disk = mapOf(
            "Alleyway (World).cht" to cht("Lives" to "01"),
            "Alleyway (World) (GameShark).cht" to cht("Health" to "02"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Alleyway (World).gb", "Alleyway (World)", gb, read = { disk[it] })
        assertEquals(listOf("Lives", "Health"), set.entries.map { it.desc })
        assertEquals(listOf("Lives", "(GameShark) Health"), set.entries.map { it.info })
        assertEquals("Alleyway (World).gb.cht", set.expected)
    }

    @Test fun exact_candidate_does_not_merge_other_regions() {
        val disk = mapOf(
            "Alleyway (World).cht" to cht("Lives" to "01"),
            "Alleyway (Japan).cht" to cht("Health" to "02"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Alleyway (World).gb", "Alleyway (World)", gb, read = { disk[it] })
        assertEquals(listOf("Lives"), set.entries.map { it.desc })
        assertEquals(listOf("Lives"), set.entries.map { it.info })
    }

    @Test fun exact_candidate_that_fails_still_falls_through() {
        val disk = mapOf(
            "Alleyway (World).cht" to "garbage",
            "Alleyway (World) (GameShark).cht" to cht("Health" to "02"),
        )
        val skipped = mutableListOf<String>()
        val set = CheatResolver.resolve(disk.keys.toList(), "Alleyway (World).gb", "Alleyway (World)", gb, read = { disk[it] }, onSkipped = { n, _ -> skipped += n })
        assertEquals(listOf("Health"), set.entries.map { it.desc })
        assertEquals(listOf("Health"), set.entries.map { it.info })   // one file loaded: no prefix
        assertTrue("Alleyway (World).cht" in skipped)
    }

    private val md = setOf("md", "gen", "bin", "zip")

    @Test fun a_titled_file_with_the_same_codes_merges_first_and_repeats_drop() {
        // The Mega Drive set: the USA Action Replay file's codes are inside the (USA, Europe, Korea) file under its longer title.
        val disk = mapOf(
            "Sonic 3D Blast (USA) (Action Replay).cht" to cht("Infinite Rings" to "FFE001:0099", "Infinite Lives" to "FFE002:0005"),
            "Sonic 3d Blast (Rumbles).cht" to cht("Rumble ring pickup/loss" to "R1"),
            "Sonic 3D Blast _ Sonic 3D Flickies' Island (USA, Europe, Korea).cht" to
                cht("Invincibility" to "FFE003:0001", "Infinite Rings" to "FFE001:0099", "Skip Levels" to "FFE004:0001"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Sonic 3D Blast.md", "Sonic 3D Blast", md, read = { disk[it] })
        assertEquals(listOf("Invincibility", "Infinite Rings", "Skip Levels", "Infinite Lives"), set.entries.map { it.desc })
        assertEquals("(Action Replay) Infinite Lives", set.entries.last().info)
    }

    @Test fun a_titled_file_with_other_codes_stays_out() {
        // "_" is also a multi-game cart's or an "&": Asterix & Obelix is another game, with its own codes.
        val disk = mapOf(
            "Asterix (Europe) (Action Replay).cht" to cht("Lives" to "7E0001:09"),
            "Asterix _ Obelix (Europe) (Action Replay).cht" to cht("Energy" to "7E0F00:63"),
        )
        val set = CheatResolver.resolve(disk.keys.toList(), "Asterix (Europe).sfc", "Asterix", setOf("sfc", "smc"), read = { disk[it] })
        assertEquals(listOf("Lives"), set.entries.map { it.desc })
    }

    @Test fun a_files_own_repeated_code_stays() {
        val disk = mapOf("Game (USA).cht" to cht("Lives" to "01", "Lives again" to "01"))
        val set = CheatResolver.resolve(disk.keys.toList(), "Game (USA).gb", "Game", gb, read = { disk[it] })
        assertEquals(listOf("Lives", "Lives again"), set.entries.map { it.desc })
    }
}
