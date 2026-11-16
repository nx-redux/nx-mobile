package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.FileImportTree
import com.nxredux.mobile.storage.FileRefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BiosCopiesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun a_copy_goes_once_no_folder_on_record_has_it() {
        val old = mapOf("SEGACD" to setOf("bios_CD_U.bin", "bios_CD_E.bin"), "MD" to setOf("bios_CD_E.bin", "bios_CD_J.bin"))
        val (record, stale) = BiosCopies.update(old, mapOf("SEGACD" to (setOf("bios_CD_E.bin") to true), "MD" to (emptySet<String>() to true)))
        assertEquals(mapOf("SEGACD" to setOf("bios_CD_E.bin")), record)
        assertEquals(setOf("bios_CD_U.bin", "bios_CD_J.bin"), stale)
    }

    @Test fun a_folder_that_did_not_list_in_full_or_was_not_read_keeps_its_copies() {
        val old = mapOf("SEGACD" to setOf("bios_CD_U.bin"), "PS2" to setOf("scph39001.bin"))
        val (record, stale) = BiosCopies.update(old, mapOf("SEGACD" to (setOf("bios_CD_E.bin") to false)))
        assertEquals(mapOf("SEGACD" to setOf("bios_CD_U.bin", "bios_CD_E.bin"), "PS2" to setOf("scph39001.bin")), record)
        assertEquals(emptySet<String>(), stale)
    }

    @Test fun the_record_reads_back_what_it_wrote() {
        val record = mapOf("DC" to setOf("dc/dc_boot.bin"), "GBA" to setOf("gba_bios.bin"))
        assertEquals(record, BiosCopies.parse(BiosCopies.format(record)))
        assertEquals(emptyMap<String, Set<String>>(), BiosCopies.parse(""))
    }

    @Test fun an_import_reports_the_files_it_takes_by_path_and_a_full_listing() {
        val bios = tmp.newFolder("Bios", "PS2")
        File(bios, "a.bin").writeText("a")
        File(bios, "b.bin.part").writeText("cut")
        File(bios, "sub").mkdir(); File(bios, "sub/c.bin").writeText("c")
        val found = TreeSync.Found()
        TreeSync(FileImportTree).importDir(FileRefs.of(bios.absolutePath), tmp.newFolder("system"), recursive = true, whenDiffers = true, found = found)
        assertEquals(setOf("a.bin", "sub/c.bin"), found.seen)
        assertTrue(found.complete)
    }
}
