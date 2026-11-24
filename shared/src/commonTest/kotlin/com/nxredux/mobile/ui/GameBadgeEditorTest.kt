package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameBadges
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.NoUserData
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.UserDataStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameBadgeEditorTest {
    private class Badges(var text: Map<String, String> = emptyMap(), var fails: Boolean = false) : UserDataStore by NoUserData {
        override suspend fun readBadges(): Map<String, String> = text
        override suspend fun writeBadges(choices: Map<String, String>): Boolean {
            if (fails) return false
            text = choices
            return true
        }
    }

    private val dir = "Roms/Game Boy Advance (GBA)"
    private val game = GameEntry(
        ContentRef("H/$dir/Zelda (Hack).gba"), "Zelda (Hack).gba", "Zelda", folder = ContentRef("H/$dir"), sourceId = Recents.HOME,
        relativeDir = dir, badge = GameBadges.HACK,
    )
    private val path = "/$dir/Zelda (Hack).gba"

    private fun TestScope.editor(store: Badges, saved: MutableList<String?>, failed: MutableList<Unit>) =
        GameBadgeEditor(this, store, StandardTestDispatcher(testScheduler), saved = { _, c -> saved += c }, failed = { failed += Unit })

    @Test fun steps_show_at_once_and_the_last_one_is_written_beside_the_other_lines() = runTest {
        val store = Badges(mapOf("/Roms/X/Other.gb" to GameBadges.DEMO))
        val saved = ArrayList<String?>()
        val e = editor(store, saved, ArrayList())
        val row = e.section(game).rows.single()
        assertEquals("Badge", row.row.label)
        assertEquals(listOf("Auto", "None", "Hack", "Translated", "Proto", "Beta", "Demo", "Homebrew"), row.choices?.labels)
        assertEquals(0, row.choices?.index)
        e.step(game, 1)
        e.step(game, 1)
        assertEquals("Hack", e.section(game).rows.single().row.secondary)   // before any write lands
        advanceUntilIdle()
        assertEquals(mapOf("/Roms/X/Other.gb" to GameBadges.DEMO, path to GameBadges.HACK), store.text)
        assertEquals(GameBadges.HACK, saved.last())
        e.step(game, -2)   // back round to Auto: its line goes
        advanceUntilIdle()
        assertEquals(mapOf("/Roms/X/Other.gb" to GameBadges.DEMO), store.text)
        e.choose(game, GameBadges.CHOICES.indexOf(GameBadges.NONE))
        advanceUntilIdle()
        assertEquals(GameBadges.NONE, store.text[path])
    }

    @Test fun picking_auto_takes_the_choice_off_again() = runTest {
        val store = Badges()
        val e = editor(store, ArrayList(), ArrayList())
        e.choose(game, GameBadges.CHOICES.indexOf(GameBadges.NONE))
        advanceUntilIdle()
        assertEquals(mapOf(path to GameBadges.NONE), store.text)
        e.choose(game, 0)
        assertEquals(null, e.choiceOf(game))
        advanceUntilIdle()
        assertEquals(emptyMap(), store.text)
    }

    @Test fun a_failed_write_puts_the_row_back_on_the_last_saved_choice() = runTest {
        val store = Badges()
        val failed = ArrayList<Unit>()
        val e = editor(store, ArrayList(), failed)
        e.choose(game, GameBadges.CHOICES.indexOf(GameBadges.BETA))
        advanceUntilIdle()
        store.fails = true
        e.step(game, 1)
        assertEquals(GameBadges.DEMO, e.choiceOf(game))
        advanceUntilIdle()
        assertEquals(1, failed.size)
        assertEquals(GameBadges.BETA, e.choiceOf(game))
        assertEquals(mapOf(path to GameBadges.BETA), store.text)
    }
}
