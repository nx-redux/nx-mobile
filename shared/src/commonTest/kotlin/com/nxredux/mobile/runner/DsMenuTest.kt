package com.nxredux.mobile.runner

import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.ui.ContextKind
import com.nxredux.mobile.ui.ContextMenus
import com.nxredux.mobile.ui.ListSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DsMenuTest {
    @AfterTest fun reset() {
        DsMenu.dir = null
    }

    private fun system(tag: String) = SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())

    @Test fun only_the_menu_content_is_the_menu() {
        assertTrue(DsMenu.isMenu("nds-menu:"))
        assertFalse(DsMenu.isMenu("file:///sdcard/Roms/NDS/nds-menu:"))
        assertFalse(DsMenu.isMenu(null))
    }

    // Our own client: nothing of a real DS's is needed.
    @Test fun forced_options_are_the_built_in_files_in_ds_mode() {
        assertEquals(mapOf("melonds_console_mode" to "ds", "melonds_sysfile_mode" to "builtin", "melonds_boot_mode" to "direct"), DsMenu.FORCED_OPTIONS)
    }

    @Test fun info_no_longer_asks_for_dumps() {
        assertFalse("BIOS" in DsMenu.INFO.synopsis.orEmpty())
        assertFalse("firmware" in DsMenu.INFO.synopsis.orEmpty())
    }

    @Test fun the_client_is_found_in_the_copied_folder() {
        assertEquals(null, DsMenu.appPath)
        DsMenu.dir = "/data/app/files/nds-dlp"
        assertEquals("/data/app/files/nds-dlp/download-play.nds", DsMenu.appPath)
    }

    @Test fun the_ds_list_starts_with_the_menu() {
        val rows = DsMenu.withEntry(listOf(system("NDS")), emptyList())
        assertEquals(listOf(DsMenu.TITLE), rows.map { it.game.shownName })
        assertTrue(DsMenu.isMenu(rows.single().game.ref.value))
    }

    @Test fun the_row_points_its_art_at_the_shipped_files() {
        DsMenu.dir = "/data/app/files/nds-dlp"
        val g = DsMenu.withEntry(listOf(system("NDS")), emptyList()).single().game
        assertEquals("file:///data/app/files/nds-dlp/boxart.png", g.boxartRef?.value)
        assertEquals("file:///data/app/files/nds-dlp/mix.png", g.mixRef?.value)
        assertEquals("file:///data/app/files/nds-dlp/wheel.png", g.wheelRef?.value)
    }

    @Test fun other_consoles_get_no_menu_row() {
        assertTrue(DsMenu.withEntry(listOf(system("GBA")), emptyList()).isEmpty())
        assertTrue(DsMenu.withEntry(listOf(system("3DS")), emptyList()).isEmpty())
    }

    // With or without a real DS's dumps, the row is there.
    @Test fun the_row_shows_without_dumps() {
        assertEquals(listOf(DsMenu.TITLE), DsMenu.withEntry(listOf(system("NDS")), emptyList()).map { it.game.shownName })
    }

    // A guest who owns no DS game still reaches DS Download Play.
    @Test fun a_library_without_a_ds_console_gets_one() {
        val gba = system("GBA").copy(displayName = "Game Boy Advance")
        val snes = system("SFC").copy(displayName = "Super Nintendo ES")
        val shown = DsMenu.withConsole(listOf(gba, snes))
        assertEquals(listOf("Game Boy Advance", "Nintendo DS", "Super Nintendo ES"), shown.map { it.displayName })
        val ds = shown[1]
        assertEquals("NDS", ds.tag)
        assertTrue(ds.games.isEmpty())
        assertEquals(listOf(DsMenu.TITLE), DsMenu.withEntry(listOf(ds), emptyList()).map { it.game.shownName })
    }

    @Test fun no_extra_ds_console_beside_one() {
        val gba = system("GBA")
        val nds = system("NDS")
        assertEquals(listOf(gba, nds), DsMenu.withConsole(listOf(gba, nds)))
    }

    // The DS console the row brings in holds only DS Download Play: its caption names it rather than "0 games".
    @Test fun the_empty_ds_console_is_captioned_ds_download_play() {
        val ds = DsMenu.withConsole(emptyList()).single()
        assertEquals(DsMenu.TITLE, com.nxredux.mobile.ui.consoleInfoLines(com.nxredux.mobile.ui.MainRow.System(ds)).single().text)
        assertEquals("1 game", com.nxredux.mobile.ui.consoleInfoLines(com.nxredux.mobile.ui.MainRow.System(system("NDS").copy(games = listOf(DsMenu.entry())))).single().text)
    }

    @Test fun the_empty_ds_console_goes_before_unassigned() {
        val unassigned = SystemFolder("Unassigned", "UNASSIGNED", null, emptyList(), unassigned = true)
        assertEquals(listOf("Nintendo DS", "Unassigned"), DsMenu.withConsole(listOf(unassigned)).map { it.displayName })
    }

    // No file to pin, hide, rename, collect or re-art, and nothing to play over netplay: only Game Settings.
    @Test fun the_rows_menu_holds_only_game_settings() {
        val nds = system("NDS")
        val row = DsMenu.withEntry(listOf(nds), emptyList()).single()
        val items = ContextMenus.itemsFor(ListSource.Console(listOf(nds)), row, pinned = false, netplay = true, netplayLeads = true)
        assertEquals(listOf(ContextKind.GAME_SETTINGS), items.map { it.kind })
        assertFalse(ContextMenus.hasNetplay(row))
    }
}
