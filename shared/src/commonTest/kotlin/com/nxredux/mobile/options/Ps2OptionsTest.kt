package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ps2OptionsTest {
    @Test fun rowListsAutoThenFiles() {
        assertEquals(listOf("auto", "a.bin", "b.bin"), Ps2Options.biosRow(listOf("b.bin", "a.bin")).values)
    }
    @Test fun rowHiddenWithOneFile() {
        assertTrue(Ps2Options.rowsFor(listOf("a.bin")).isEmpty())
        assertTrue(Ps2Options.rowsFor(emptyList()).isEmpty())
        assertEquals(1, Ps2Options.rowsFor(listOf("a.bin", "b.bin")).size)
    }
    @Test fun deletedStoredFileFallsBackToAuto() {
        assertEquals(mapOf("armsx2_bios" to "auto"), Ps2Options.biosOption("old.bin", listOf("new.bin")))
    }
    @Test fun storedFileKept() {
        assertEquals(mapOf("armsx2_bios" to "new.bin"), Ps2Options.biosOption("new.bin", listOf("new.bin", "b.bin")))
    }
    @Test fun rowIsInEveryConsoleRowsList() {
        assertTrue(ConsoleSettings.all.any { it.key == Ps2Options.BIOS })
        assertTrue(ConsoleSettings.tableFor("PS2", biosFiles = listOf("a.bin", "b.bin")).any { it.key == Ps2Options.BIOS })
    }
}
