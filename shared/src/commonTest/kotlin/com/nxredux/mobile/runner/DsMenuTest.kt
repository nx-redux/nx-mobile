package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DsMenuTest {
    @Test fun only_the_menu_content_is_the_menu() {
        assertTrue(DsMenu.isMenu("nds-menu:"))
        assertFalse(DsMenu.isMenu("file:///sdcard/Roms/NDS/nds-menu:"))
        assertFalse(DsMenu.isMenu(null))
    }

    @Test fun missing_lists_the_absent_dumps_in_order() {
        assertEquals(emptyList(), DsMenu.missing(setOf("bios7.bin", "bios9.bin", "firmware.bin", "dsi_bios7.bin")))
        assertEquals(listOf("bios9.bin", "firmware.bin"), DsMenu.missing(setOf("bios7.bin")))
    }

    @Test fun message_names_the_folder() {
        assertEquals("DS Download Play needs a real DS's firmware.bin in Bios/NDS/", DsMenu.missingMessage(listOf("firmware.bin")))
    }
}

class DsMenuEntryTest {
    private fun system(tag: String) = com.nxredux.mobile.library.SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())

    @Test fun the_ds_list_starts_with_the_menu() {
        DsMenu.listed = true
        val nds = system("NDS")
        val rows = DsMenu.withEntry(listOf(nds), emptyList())
        assertEquals(listOf(DsMenu.TITLE), rows.map { it.game.shownName })
        assertTrue(DsMenu.isMenu(rows.single().game.ref.value))
        DsMenu.listed = false
    }

    @Test fun other_consoles_get_no_menu_row() {
        assertTrue(DsMenu.withEntry(listOf(system("GBA")), emptyList()).isEmpty())
    }
}
