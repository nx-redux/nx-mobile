package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class KeptMenuTest {
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
    private fun listed(name: String, box: String? = null) = Listed(gba, GameEntry(ContentRef("r/$name"), "$name.gba", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)", boxartRef = box?.let(::ContentRef)))
    private val items = listOf(ContextItem(ContextKind.PIN, "Pin"))

    @Test fun a_menu_stays_open_on_its_games_fresh_entry() {
        val fresh = listed("Zelda", box = "art/zelda.png")
        val kept = keptMenu(ContextMenu(listed("Zelda"), items, index = 0), listOf(listed("Mario"), fresh))
        assertSame(fresh, kept?.entry)
        assertEquals(items, kept?.items)
    }

    @Test fun a_menu_whose_game_left_the_list_closes() {
        assertNull(keptMenu(ContextMenu(listed("Zelda"), items), listOf(listed("Mario"))))
    }

    @Test fun no_menu_or_a_menu_without_a_game_is_kept_as_is() {
        assertNull(keptMenu(null, listOf(listed("Mario"))))
        val tool = ContextMenu(null, items)
        assertSame(tool, keptMenu(tool, emptyList()))
    }
}
