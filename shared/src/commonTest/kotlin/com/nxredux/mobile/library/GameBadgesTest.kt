package com.nxredux.mobile.library

import com.nxredux.mobile.library.GameBadges.BETA
import com.nxredux.mobile.library.GameBadges.DEMO
import com.nxredux.mobile.library.GameBadges.HACK
import com.nxredux.mobile.library.GameBadges.HOMEBREW
import com.nxredux.mobile.library.GameBadges.NONE
import com.nxredux.mobile.library.GameBadges.PROTO
import com.nxredux.mobile.library.GameBadges.TRANSLATED
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.MemorySidecarStore
import com.nxredux.mobile.storage.Recents
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class GameBadgesTest {
    @Test fun each_tag_in_the_table_gives_its_badge_in_any_case() {
        val cases = mapOf(
            "Super Mario World (Hack)" to HACK, "Super Mario World [h]" to HACK, "Super Mario World [h1]" to HACK,
            "Super Mario World [h2C]" to HACK, "Zelda [hIR]" to HACK, "Zelda [hM04]" to HACK,
            "Kaizo Mario (Hack by T. Takemoto)" to HACK, "Mario (SMW Hack)" to HACK, "Mario [HACK]" to HACK,
            "Mother 3 (Translated)" to TRANSLATED, "Mother 3 [T+Eng]" to TRANSLATED, "Seiken [T-Spa1.0]" to TRANSLATED,
            "Seiken [T+Eng1.1_Group]" to TRANSLATED, "Tales (English Translation)" to TRANSLATED,
            "Star Fox 2 (Proto)" to PROTO, "Star Fox 2 (Prototype)" to PROTO, "Star Fox 2 (Proto 2)" to PROTO,
            "Earthbound (Beta)" to BETA, "Earthbound (Beta 1)" to BETA, "Earthbound (BETA2)" to BETA,
            "Sonic (Demo)" to DEMO, "Sonic (Kiosk Demo)" to DEMO, "Sonic (Sample)" to DEMO,
            "Tetris Clone (Homebrew)" to HOMEBREW, "Tetris Clone (PD)" to HOMEBREW, "Tetris Clone [pd]" to HOMEBREW,
        )
        for ((stem, badge) in cases) assertEquals(badge, GameBadges.detect(stem), stem)
    }

    @Test fun words_in_the_title_and_other_tags_give_none() {
        for (stem in listOf(
            "Hack", "Beta Ray Bill", "The Translated Ones (USA)", "Demon's Crest (USA)", "Prototype (USA)",
            "Zelda (USA) (Rev 1)", "Zelda [!]", "Zelda [b1]", "Zelda [a1]", "Zelda [f1]", "Zelda (Hu)", "Jurassic (T-Rex)",
            "Demolition Man (USA)", "Homebrewed Coffee", "Beta (Unclosed", "Betamax (USA)", "Zelda (Demolition)",
        )) assertNull(GameBadges.detect(stem), stem)
    }

    @Test fun a_tag_in_a_comma_list_counts_and_the_table_order_breaks_ties() {
        assertEquals(PROTO, GameBadges.detect("Game (USA, Proto)"))
        assertEquals(HACK, GameBadges.detect("Game (Beta) [T+Eng] (Hack)"))
        assertEquals(TRANSLATED, GameBadges.detect("Game (Proto) [T+Eng]"))
        assertEquals(PROTO, GameBadges.detect("Game (Beta) (Proto)"))
        assertEquals(DEMO, GameBadges.detect("Game (Demo) (PD)"))
    }

    @Test fun the_choice_wins_then_the_msu_set_then_the_tags() {
        assertEquals(HACK, GameBadges.shown(null, null, "Game (Hack)"))
        assertEquals(MsuSets.MSU1, GameBadges.shown(null, MsuSets.MSU1, "Game (Hack)"))
        assertNull(GameBadges.shown(NONE, MsuSets.MSU1, "Game (Hack)"))
        assertEquals(DEMO, GameBadges.shown(DEMO, MsuSets.MSU1, "Game (Hack)"))
        assertEquals(HACK, GameBadges.shown("Sparkly", null, "Game (Hack)"))   // a word this build does not know is Auto
        assertNull(GameBadges.shown(null, null, "Game (USA)"))
    }

    @Test fun badges_txt_keeps_every_line_and_drops_one_set_back_to_auto() {
        val read = GameBadges.parse("/Roms/A/Game.sfc\tHack\n/Roms/A/Other.sfc\tSparkly\nno tab line\n")
        assertEquals(mapOf("/Roms/A/Game.sfc" to "Hack", "/Roms/A/Other.sfc" to "Sparkly"), read)
        val next = GameBadges.with(read, "/Roms/A/Game.sfc", null)
        assertEquals(mapOf("/Roms/A/Other.sfc" to "Sparkly"), next)
        assertEquals("/Roms/A/Other.sfc\tSparkly\n/Roms/B/X.gb\tNone\n", GameBadges.render(GameBadges.with(next, "/Roms/B/X.gb", NONE)))
        assertSame(read, GameBadges.with(read, "/Roms/A/Tab\tName.sfc", HACK))   // cannot be a line: unchanged
    }

    private val gbaDir = "Roms/Game Boy Advance (GBA)"

    private fun game(file: String, dir: String = gbaDir, lookupDir: String = dir, badge: String? = null, choice: String? = null) =
        IndexedGame(Recents.HOME, dir, file, "H/$dir/$file", "H/$dir", Names.plainName(Names.stripExtension(file)), lookupDir = lookupDir, badge = badge, badgeChoice = choice)

    private fun index(vararg games: IndexedGame) = LibraryIndex(
        LibraryIndex.VERSION, "H", emptyList(), emptyMap(), listOf(IndexedSystem("GBA", "Game Boy Advance", games = games.toList())),
    )

    @Test fun an_index_shows_the_detected_badge_and_keeps_the_msu_set_for_its_files() {
        val games = index(
            game("Mother 3 (Japan) [T+Eng].gba"),
            game("SMW (MSU1) (Hack).sfc", badge = MsuSets.MSU1, choice = NONE),
            game("disc.cue", dir = "$gbaDir/Legend (Proto)", lookupDir = gbaDir),   // a disc folder's tags are its folder's
            game("Plain (USA).gba", choice = BETA),
        ).toSystems().single().games.associateBy { it.fileName }
        assertEquals(TRANSLATED, games.getValue("Mother 3 (Japan) [T+Eng].gba").badge)
        assertNull(games.getValue("SMW (MSU1) (Hack).sfc").badge)
        assertEquals(MsuSets.MSU1, games.getValue("SMW (MSU1) (Hack).sfc").msuSet)
        assertEquals(NONE, games.getValue("SMW (MSU1) (Hack).sfc").badgeChoice)
        assertEquals(PROTO, games.getValue("disc.cue").badge)
        assertEquals(BETA, games.getValue("Plain (USA).gba").badge)
    }

    @Test fun set_badge_choice_changes_one_game_and_leaves_an_unknown_one_alone() {
        val ix = index(game("A (Hack).gba"), game("B.gba"))
        val a = ix.toSystems().single().games.first { it.fileName == "A (Hack).gba" }
        val none = ix.setBadgeChoice(a, NONE)
        assertEquals(listOf(NONE, null), none.systems.single().games.map { it.badgeChoice })
        assertNull(none.toSystems().single().games.first().badge)
        assertEquals(HACK, none.setBadgeChoice(a, null).toSystems().single().games.first().badge)
        assertSame(ix, ix.setBadgeChoice(a.copy(fileName = "nope.gba"), HACK))
        assertSame(none, none.setBadgeChoice(a, NONE))
    }

    @Test fun the_scan_reads_each_games_choice_and_a_hack_beside_its_original_keeps_the_clean_name() = runTest {
        val dir = "H/Roms/Game Boy Advance (GBA)"
        val src = FakeRomsSource(
            dirs = mapOf(
                "H" to listOf("Roms"), "H/Roms" to listOf("Game Boy Advance (GBA)"),
                dir to listOf("Zelda (USA).gba", "Zelda (Hack).gba", "Metroid (USA).gba", "Metroid (Europe).gba"),
            ),
        )
        val choices = mapOf("/$gbaDir/Metroid (Europe).gba" to DEMO, "/$gbaDir/Zelda (USA).gba" to "Sparkly")
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }, badgeChoices = { choices })
            .scan(ContentRef("H"), emptyList(), emptyMap())
        val games = index.systems.single { it.tag == "GBA" }.games.associateBy { it.file }
        assertEquals("Zelda", games.getValue("Zelda (USA).gba").name)
        assertEquals("Zelda", games.getValue("Zelda (Hack).gba").name)   // the badge tells them apart
        assertEquals(HACK, games.getValue("Zelda (Hack).gba").shownBadge)
        assertNull(games.getValue("Zelda (USA).gba").badgeChoice)        // an unknown word is not kept as a choice
        assertEquals(DEMO, games.getValue("Metroid (Europe).gba").badgeChoice)
        assertEquals("Metroid", games.getValue("Metroid (Europe).gba").name)
        assertEquals("Metroid", games.getValue("Metroid (USA).gba").name)
    }
}
