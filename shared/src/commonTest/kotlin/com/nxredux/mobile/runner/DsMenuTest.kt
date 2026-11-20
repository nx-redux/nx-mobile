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
        DsMenu.artDir = null
    }

    private fun system(tag: String) = SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())

    @Test fun only_the_menu_content_is_the_menu() {
        assertTrue(DsMenu.isMenu("nds-menu:"))
        assertFalse(DsMenu.isMenu("file:///sdcard/Roms/NDS/nds-menu:"))
        assertFalse(DsMenu.isMenu(null))
    }

    @Test fun missing_lists_the_absent_dumps_in_order() {
        assertEquals(emptyList(), DsMenu.missing(setOf("bios7.bin", "bios9.bin", "firmware.bin", "dsi_bios7.bin")))
        assertEquals(listOf("bios9.bin", "firmware.bin"), DsMenu.missing(setOf("bios7.bin")))
    }

    // melonDS DS lists any DS firmware dump whatever its name; a DSi one cannot boot the DS menu.
    @Test fun a_ds_firmware_dump_under_another_name_counts() {
        assertEquals(emptyList(), DsMenu.missing(setOf("bios7.bin", "bios9.bin", "nds_firmware.bin")))
        assertEquals(emptyList(), DsMenu.missing(setOf("bios7.bin", "bios9.bin", "Firmware_NDS.BIN")))
        assertEquals(listOf("firmware.bin"), DsMenu.missing(setOf("bios7.bin", "bios9.bin", "dsi_firmware.bin")))
    }

    @Test fun message_names_the_folder() {
        assertEquals("DS Download Play needs a real DS's firmware.bin in Bios/NDS/", DsMenu.missingMessage(listOf("firmware.bin")))
    }

    @Test fun the_ds_list_starts_with_the_menu() {
        val rows = DsMenu.withEntry(listOf(system("NDS")), emptyList(), ready = true)
        assertEquals(listOf(DsMenu.TITLE), rows.map { it.game.shownName })
        assertTrue(DsMenu.isMenu(rows.single().game.ref.value))
    }

    @Test fun the_row_points_its_art_at_the_shipped_files() {
        DsMenu.artDir = "/data/app/files/nds-menu"
        val g = DsMenu.withEntry(listOf(system("NDS")), emptyList(), ready = true).single().game
        assertEquals("file:///data/app/files/nds-menu/boxart.png", g.boxartRef?.value)
        assertEquals("file:///data/app/files/nds-menu/mix.png", g.mixRef?.value)
        assertEquals("file:///data/app/files/nds-menu/wheel.png", g.wheelRef?.value)
    }

    @Test fun other_consoles_get_no_menu_row() {
        assertTrue(DsMenu.withEntry(listOf(system("GBA")), emptyList(), ready = true).isEmpty())
        assertTrue(DsMenu.withEntry(listOf(system("3DS")), emptyList(), ready = true).isEmpty())
    }

    // Without the dumps the row could only refuse.
    @Test fun no_row_without_the_dumps() {
        assertTrue(DsMenu.withEntry(listOf(system("NDS")), emptyList(), ready = false).isEmpty())
    }

    // A guest who owns no DS game still reaches DS Download Play.
    @Test fun the_dumps_bring_a_ds_console_into_a_library_without_one() {
        val gba = system("GBA").copy(displayName = "Game Boy Advance")
        val snes = system("SFC").copy(displayName = "Super Nintendo ES")
        val shown = DsMenu.withConsole(listOf(gba, snes), ready = true)
        assertEquals(listOf("Game Boy Advance", "Nintendo DS", "Super Nintendo ES"), shown.map { it.displayName })
        val ds = shown[1]
        assertEquals("NDS", ds.tag)
        assertTrue(ds.games.isEmpty())
        assertEquals(listOf(DsMenu.TITLE), DsMenu.withEntry(listOf(ds), emptyList(), ready = true).map { it.game.shownName })
    }

    @Test fun no_extra_ds_console_without_the_dumps_or_beside_one() {
        val gba = system("GBA")
        assertEquals(listOf(gba), DsMenu.withConsole(listOf(gba), ready = false))
        val nds = system("NDS")
        assertEquals(listOf(gba, nds), DsMenu.withConsole(listOf(gba, nds), ready = true))
    }

    // The DS console the dumps bring in holds only DS Download Play: its caption names it rather than "0 games".
    @Test fun the_empty_ds_console_is_captioned_ds_download_play() {
        val ds = DsMenu.withConsole(emptyList(), ready = true).single()
        assertEquals(DsMenu.TITLE, com.nxredux.mobile.ui.consoleInfoLines(com.nxredux.mobile.ui.MainRow.System(ds)).single().text)
        assertEquals("1 game", com.nxredux.mobile.ui.consoleInfoLines(com.nxredux.mobile.ui.MainRow.System(system("NDS").copy(games = listOf(DsMenu.entry())))).single().text)
    }

    @Test fun the_empty_ds_console_goes_before_unassigned() {
        val unassigned = SystemFolder("Unassigned", "UNASSIGNED", null, emptyList(), unassigned = true)
        assertEquals(listOf("Nintendo DS", "Unassigned"), DsMenu.withConsole(listOf(unassigned), ready = true).map { it.displayName })
    }

    // No file to pin, hide, rename, collect or re-art, and nothing to play over netplay: only Game Settings.
    @Test fun the_rows_menu_holds_only_game_settings() {
        val nds = system("NDS")
        val row = DsMenu.withEntry(listOf(nds), emptyList(), ready = true).single()
        val items = ContextMenus.itemsFor(ListSource.Console(listOf(nds)), row, pinned = false, netplay = true, netplayLeads = true)
        assertEquals(listOf(ContextKind.GAME_SETTINGS), items.map { it.kind })
        assertFalse(ContextMenus.hasNetplay(row))
    }
}
