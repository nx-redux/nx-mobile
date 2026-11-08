package com.nxredux.mobile.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaomiSetsTest {
    // Rows as cores/scripts/flycast-sets.py writes them from flycast's naomi_roms.cpp (BIOS rows have no title).
    private val table = NaomiSets.parse(
        "awbios\t\t\tawbios\n" +
            "gram2000\t\tGiant Gram 2000\tnaomi\n" +
            "marstv\t\tKasei Channel Mars TV\tnaomi\n" +
            "mslug6\t\tMetal Slug 6\tawbios\n" +
            "mushik2k\tmushik2e\tMushiking The King Of Beetles - Mushiking II / III / III+ (Ver. 1.000-) (Korea)\tnaomi\n" +
            "naomi\t\t\tnaomi\n" +
            "naomi2\t\t\tnaomi2\n" +
            "vstrik3\t\tVirtua Striker 3\tnaomi2\n" +
            "\n" +
            "broken\n",
    )

    @Test fun titles_and_bios_per_set() {
        assertEquals("Metal Slug 6", table.title("mslug6"))
        assertEquals("Metal Slug 6", table.title("MSlug6"))
        assertEquals("awbios", table.biosFor("mslug6"))
        assertEquals("naomi", table.biosFor("gram2000"))
        assertEquals("naomi", table.biosFor("marstv"))    // naomi-jp-d lives in naomi.zip
        assertEquals("naomi2", table.biosFor("VSTRIK3"))
        assertEquals("mushik2e", table.parent("MUSHIK2K"))
        assertNull(table.parent("mslug6"))
        assertNull(table.parent("naomi"))
    }

    @Test fun unknown_sets_and_bios_rows_are_not_games() {
        assertNull(table.title("nope"))
        assertNull(table.biosFor("nope"))
        assertNull(table.title("naomi"))
        assertNull(table.biosFor("naomi"))
        assertNull(table.title("broken"))
    }

    @Test fun bios_archives_are_known_by_name() {
        for (b in listOf("naomi", "awbios", "naomi2", "AWBIOS")) assertTrue(table.isBios(b), b)
        for (b in listOf("mslug6", "nope", "naomi-jp-d")) assertFalse(table.isBios(b), b)
    }

    @Test fun empty_table() {
        assertTrue(NaomiSets.EMPTY.isEmpty)
        assertTrue(NaomiSets.parse("").isEmpty)
        assertFalse(table.isEmpty)
        assertNull(NaomiSets.EMPTY.biosFor("mslug6"))
    }

    @Test fun only_a_known_set_archive_is_loaded_as_is() {
        assertTrue(table.loadsAsIs("mslug6.zip"))
        assertTrue(table.loadsAsIs("MSlug6.7z"))
        assertFalse(table.loadsAsIs("Crazy Taxi (USA).zip"))   // a zipped disc: unpacked like any console's archive
        assertFalse(table.loadsAsIs("naomi.zip"))
        assertTrue(NaomiSets.EMPTY.loadsAsIs("Crazy Taxi (USA).zip"))   // no table in the build: every archive as is
    }

    @Test fun missing_bios_is_the_sets_archive_in_zip_or_7z() {
        assertEquals("awbios", table.missingBios("mslug6.zip", emptySet()))
        assertEquals("awbios", table.missingBios("mslug6.zip", setOf("naomi.zip", "AWBIOS.ZIP")))
        assertNull(table.missingBios("mslug6.zip", setOf("awbios.zip")))
        assertNull(table.missingBios("MSLUG6.7Z", setOf("awbios.7z")))
        assertNull(table.missingBios("unknown.zip", emptySet()))   // not flycast's set: no check
        assertEquals("naomi", table.missingBios("marstv.zip", setOf("naomi-jp-d.zip")))
    }

    @Test fun message_names_the_bios_folder() {
        assertEquals("Metal Slug 6 needs awbios.zip: put it in Bios/DC/", NaomiSets.biosMessage("Metal Slug 6", "awbios"))
    }
}
