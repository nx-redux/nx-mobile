package com.nxredux.mobile.bios

import com.nxredux.mobile.bios.BiosResultRow.File
import com.nxredux.mobile.bios.BiosResultRow.Group
import com.nxredux.mobile.bios.BiosResultRow.Note
import kotlin.test.Test
import kotlin.test.assertEquals

class BiosIntakeSummaryTest {
    private fun w(path: String, console: String, name: String, o: BiosOutcome, boot: Boolean = false) = BiosWrite(path, console, name, o, boot)

    @Test
    fun rowsGroupedByConsoleThenSkips() {
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
        assertEquals(
            listOf(
                Group("Game Boy"), File("gb_bios.bin", "Not verified · changes boot"),
                Group("Game Boy Advance"), File("gba_bios.bin", "Already there"),
                Group("Sega CD"), File("bios_CD_U.bin", "Renamed from [BIOS] Sega CD (USA).bin"),
                Group("Sony PlayStation"), File("scph5501.bin", "Verified"),
                Group("TurboGrafx-16"), File("syscard3.pce", "Kept yours (verified)"),
                Group("Skipped"), File("dc_boot.bin", "Wrong size (expected 2097152 bytes)"),
                Note("12 other files skipped. Anything skipped can still go in Bios/<CONSOLE>/ by hand."),
            ),
            BiosIntakeSummary(plan, emptyList()).rows(),
        )
    }

    @Test
    fun failedWritesListedWithSkips() {
        val plan = BiosIntakePlan(listOf(w("scph5501.bin", "PS", "scph5501.bin", BiosOutcome.VERIFIED)), emptyList(), 1)
        // A write that failed leaves its console group and is listed with the skips.
        assertEquals(
            listOf(Group("Skipped"), File("scph5501.bin", "Could not write"), Note("1 other file skipped. Anything skipped can still go in Bios/<CONSOLE>/ by hand.")),
            BiosIntakeSummary(plan, listOf(BiosSkip("scph5501.bin", "Could not write"))).rows(),
        )
    }

    @Test
    fun noNoteWhenNothingSkipped() {
        val plan = BiosIntakePlan(listOf(w("scph5501.bin", "PS", "scph5501.bin", BiosOutcome.VERIFIED)), emptyList(), 0)
        assertEquals(listOf(Group("Sony PlayStation"), File("scph5501.bin", "Verified")), BiosIntakeSummary(plan, emptyList()).rows())
    }

    @Test
    fun noticeTextCountsCopies() {
        val plan = BiosIntakePlan(
            listOf(w("a", "PS", "scph5501.bin", BiosOutcome.VERIFIED), w("b", "GBA", "gba_bios.bin", BiosOutcome.ALREADY_THERE)), emptyList(), 0,
        )
        assertEquals("Imported 1 BIOS, 1 already there", BiosIntakeSummary(plan, emptyList()).noticeText())
    }

    @Test
    fun emptyPlanIsAMessage() {
        assertEquals(BiosIntakeResult.Message("No BIOS found"), BiosIntakeSummary.resultOf(BiosIntakePlan(emptyList(), emptyList(), 40), emptyList()))
        val done = BiosIntakeSummary.resultOf(BiosIntakePlan(emptyList(), listOf(BiosSkip("a.bin", "Wrong size (expected 1 bytes)")), 0), emptyList())
        assertEquals(true, done is BiosIntakeResult.Done)
    }
}
