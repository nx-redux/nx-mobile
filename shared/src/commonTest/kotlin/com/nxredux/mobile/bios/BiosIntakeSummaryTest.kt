package com.nxredux.mobile.bios

import kotlin.test.Test
import kotlin.test.assertEquals

class BiosIntakeSummaryTest {
    private fun w(path: String, console: String, name: String, o: BiosOutcome, boot: Boolean = false) = BiosWrite(path, console, name, o, boot)

    @Test
    fun linesPerFileByConsoleThenSkipsThenNote() {
        val plan = BiosIntakePlan(
            writes = listOf(
                w("scph5501.bin", "PS", "scph5501.bin", BiosOutcome.VERIFIED),
                w("dir/[BIOS] Sega CD (USA).bin", "SEGACD", "bios_CD_U.bin", BiosOutcome.RENAMED),
                w("gb_bios.bin", "GB", "gb_bios.bin", BiosOutcome.NOT_VERIFIED, boot = true),
                w("x/gba_bios.bin", "GBA", "gba_bios.bin", BiosOutcome.ALREADY_THERE),
                w("syscard3.pce", "PCE", "syscard3.pce", BiosOutcome.KEPT_YOURS),
            ),
            skips = listOf(BiosSkip("a/dc_boot.bin", "Wrong size (expected 2097152 bytes)")),
            otherFiles = 12,
        )
        val s = BiosIntakeSummary(plan, emptyList())
        assertEquals("Imported 3 BIOS, 2 already there", s.headline())
        assertEquals(
            listOf(
                "Game Boy: gb_bios.bin (not verified, changes boot)",
                "Game Boy Advance: gba_bios.bin (already there)",
                "Sega CD: bios_CD_U.bin (renamed from [BIOS] Sega CD (USA).bin)",
                "Sony PlayStation: scph5501.bin (verified)",
                "TurboGrafx-16: syscard3.pce (kept yours, verified)",
                "",
                "Skipped dc_boot.bin: wrong size (expected 2097152 bytes)",
                "",
                "12 other files skipped. Anything skipped can still go in Bios/<CONSOLE>/ by hand.",
            ),
            s.lines(),
        )
    }

    @Test
    fun failedWritesListedWithSkips() {
        val plan = BiosIntakePlan(listOf(w("scph5501.bin", "PS", "scph5501.bin", BiosOutcome.VERIFIED)), emptyList(), 1)
        // A write that failed leaves its console's lines and is listed with the skips.
        val s = BiosIntakeSummary(plan, listOf(BiosSkip("scph5501.bin", "Could not write")))
        assertEquals("Imported 0 BIOS", s.headline())
        assertEquals(listOf("Skipped scph5501.bin: could not write", "", "1 other file skipped. Anything skipped can still go in Bios/<CONSOLE>/ by hand."), s.lines())
    }

    @Test
    fun noNoteWhenNothingSkipped() {
        val plan = BiosIntakePlan(listOf(w("scph5501.bin", "PS", "scph5501.bin", BiosOutcome.VERIFIED)), emptyList(), 0)
        assertEquals(listOf("Sony PlayStation: scph5501.bin (verified)"), BiosIntakeSummary(plan, emptyList()).lines())
    }

    @Test
    fun emptyPlanIsAMessage() {
        assertEquals(BiosIntakeResult.Message("No BIOS found"), BiosIntakeSummary.resultOf(BiosIntakePlan(emptyList(), emptyList(), 40), emptyList()))
        val done = BiosIntakeSummary.resultOf(BiosIntakePlan(emptyList(), listOf(BiosSkip("a.bin", "Wrong size (expected 1 bytes)")), 0), emptyList())
        assertEquals(true, done is BiosIntakeResult.Done)
    }
}
