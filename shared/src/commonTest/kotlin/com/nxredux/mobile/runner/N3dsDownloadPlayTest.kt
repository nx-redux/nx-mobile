package com.nxredux.mobile.runner

import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.ui.ContextKind
import com.nxredux.mobile.ui.ContextMenus
import com.nxredux.mobile.ui.ListSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class N3dsDownloadPlayTest {
    private fun system(tag: String) = SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())

    @Test fun only_the_row_content_is_the_row() {
        assertTrue(N3dsDownloadPlay.isRow("n3ds-dlp:"))
        assertFalse(N3dsDownloadPlay.isRow("nds-menu:"))
        assertFalse(N3dsDownloadPlay.isRow(null))
    }

    @Test fun the_3ds_list_starts_with_the_row() {
        val rows = N3dsDownloadPlay.withEntry(listOf(system("3DS")), emptyList())
        assertEquals(listOf(N3dsDownloadPlay.TITLE), rows.map { it.game.shownName })
        assertTrue(N3dsDownloadPlay.isRow(rows.single().game.ref.value))
    }

    @Test fun other_consoles_get_no_row() {
        assertTrue(N3dsDownloadPlay.withEntry(listOf(system("NDS")), emptyList()).isEmpty())
        assertTrue(N3dsDownloadPlay.withEntry(listOf(system("GBA")), emptyList()).isEmpty())
    }

    // A guest who owns no 3DS game still reaches 3DS Download Play.
    @Test fun a_library_without_3ds_gets_a_3ds_console() {
        val gba = system("GBA").copy(displayName = "Game Boy Advance")
        val snes = system("SFC").copy(displayName = "Super Nintendo ES")
        val shown = N3dsDownloadPlay.withConsole(listOf(gba, snes))
        val name = CoreRegistry.consoleOf("3DS")?.displayName
        assertEquals(listOf("Game Boy Advance", name, "Super Nintendo ES"), shown.map { it.displayName })
        assertTrue(shown[1].games.isEmpty())
        assertEquals("3DS", shown[1].tag)
    }

    @Test fun no_extra_console_beside_one() {
        val gba = system("GBA")
        val n3ds = system("3DS")
        assertEquals(listOf(gba, n3ds), N3dsDownloadPlay.withConsole(listOf(gba, n3ds)))
    }

    @Test fun the_empty_3ds_console_is_captioned_3ds_download_play() {
        val n3ds = N3dsDownloadPlay.withConsole(emptyList()).single()
        assertEquals(N3dsDownloadPlay.TITLE, com.nxredux.mobile.ui.consoleInfoLines(com.nxredux.mobile.ui.MainRow.System(n3ds)).single().text)
    }

    // No file to pin, hide, rename, collect or re-art, and nothing to play over netplay: only Game Settings.
    @Test fun the_rows_menu_holds_only_game_settings() {
        val n3ds = system("3DS")
        val row = N3dsDownloadPlay.withEntry(listOf(n3ds), emptyList()).single()
        val items = ContextMenus.itemsFor(ListSource.Console(listOf(n3ds)), row, pinned = false, netplay = true, netplayLeads = true)
        assertEquals(listOf(ContextKind.GAME_SETTINGS), items.map { it.kind })
        assertFalse(ContextMenus.hasNetplay(row))
    }

    @Test fun system_rows_cover_both() {
        assertTrue(SystemRows.isRow(DsMenu.CONTENT))
        assertTrue(SystemRows.isRow(N3dsDownloadPlay.CONTENT))
        assertFalse(SystemRows.isRow("file:///sdcard/Roms/3DS/game.cci"))
        assertEquals(N3dsDownloadPlay.INFO, SystemRows.info(N3dsDownloadPlay.CONTENT))
        assertEquals(DsMenu.INFO, SystemRows.info(DsMenu.CONTENT))
    }
}
