package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllGamesTest {
    private fun game(name: String, file: String = "$name.rom") =
        GameEntry(ContentRef("r/$file"), file, name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/x")
    private fun system(tag: String, vararg games: GameEntry) =
        SystemFolder("Console $tag", tag, CoreRegistry.forTag(tag), games.toList())
    private fun names(r: List<Listed>) = r.map { it.game.shownName }

    @Test fun mixesConsolesInNameOrder() {
        val gba = system("MGBA", game("Zelda"), game("advance wars"))
        val snes = system("SFC", game("Mario"))
        assertEquals(listOf("advance wars", "Mario", "Zelda"), names(AllGames.entries(listOf(gba, snes), null)))
    }

    @Test fun sameNameOnTwoConsolesKeepsBothByTag() {
        val nes = system("FC", game("Tetris", "Tetris.nes"))
        val gb = system("GB", game("Tetris", "Tetris.gb"))
        val all = AllGames.entries(listOf(nes, gb), null)
        assertEquals(listOf("Tetris", "Tetris"), names(all))
        assertEquals(listOf("FC", "GB"), all.map { it.system.tag })   // tag order: FC before GB
        assertEquals(listOf(nes, gb), all.map { it.system })
        assertEquals(all, AllGames.entries(listOf(gb, nes), null))   // stable whatever the input order
    }

    @Test fun leavesOutUnassignedAndCoreless() {
        val unassigned = SystemFolder("Unassigned", "", null, listOf(game("Lost")), unassigned = true)
        val coreless = SystemFolder("Nothing", "NOPE", null, listOf(game("Bare")))
        assertTrue(AllGames.entries(listOf(unassigned, coreless), null).isEmpty())
    }

    @Test fun addsAndroidGames() {
        val gba = system("MGBA", game("Zelda"), game("Alpha"))
        val android = SystemFolder("Android", ANDROID_TAG, null, listOf(game("Minecraft")), android = true)
        val all = AllGames.entries(listOf(gba), android)
        assertEquals(listOf("Alpha", "Minecraft", "Zelda"), names(all))
        assertTrue(all[1].system.android)
    }

    @Test fun emptyLibraryIsEmpty() {
        assertTrue(AllGames.entries(emptyList(), null).isEmpty())
    }
}
