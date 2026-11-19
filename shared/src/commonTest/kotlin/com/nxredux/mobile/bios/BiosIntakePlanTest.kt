package com.nxredux.mobile.bios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiosIntakePlanTest {
    private val ps1 = "490f666e1afb15b7362b406ed1cea246"
    private val cdU = "2efd74e3232ff260e371b99f84024f7f"
    private val gba = "a860e8c0b6d573d191e4ec7db1b1e4f6"
    private val gb = "32fbbd84168d3482956eb3c5051637f5"
    private val known = KnownBios(listOf(
        KnownBiosEntry("scph5501.bin", emptyList(), "PS", setOf(524288L), setOf(ps1), required = false, bootChange = false),
        KnownBiosEntry("bios_CD_U.bin", listOf("us_scd1_9210.bin"), "SEGACD", setOf(131072L), setOf(cdU), required = true, bootChange = false),
        KnownBiosEntry("gba_bios.bin", emptyList(), "GBA", setOf(16384L), setOf(gba), required = false, bootChange = false),
        KnownBiosEntry("gb_bios.bin", emptyList(), "GB", setOf(256L), setOf(gb), required = false, bootChange = true),
        KnownBiosEntry("neogeo.zip", emptyList(), "FBN", emptySet(), setOf("4".repeat(32)), required = true, bootChange = false),
    ))
    private val nothing = ExistingBios { _, _ -> null }

    private fun plan(vararg p: PickedBios, existing: ExistingBios = nothing) = BiosIntakePlanner.plan(p.toList(), known, existing)

    @Test
    fun verifiedByMd5() {
        val r = plan(PickedBios("scph5501.bin", 524288, ps1))
        assertEquals(listOf(BiosWrite("scph5501.bin", "PS", "scph5501.bin", BiosOutcome.VERIFIED, bootChange = false)), r.writes)
    }

    @Test
    fun renamedByMd5() {
        val r = plan(PickedBios("[BIOS] Sega CD (USA).bin", 131072, cdU))
        assertEquals(listOf(BiosWrite("[BIOS] Sega CD (USA).bin", "SEGACD", "bios_CD_U.bin", BiosOutcome.RENAMED, bootChange = false)), r.writes)
    }

    @Test
    fun nameOnlyRightSizeIsNotVerified() {
        val r = plan(PickedBios("GBA_BIOS.BIN", 16384, "0".repeat(32)))
        assertEquals(listOf(BiosWrite("GBA_BIOS.BIN", "GBA", "gba_bios.bin", BiosOutcome.NOT_VERIFIED, bootChange = false)), r.writes)
    }

    @Test
    fun aliasNameMatches() {
        val r = plan(PickedBios("us_scd1_9210.bin", 131072, "0".repeat(32)))
        assertEquals("bios_CD_U.bin", r.writes.single().name)
    }

    @Test
    fun archiveNameMatchesAnySize() {
        val r = plan(PickedBios("neogeo.zip", 999, "0".repeat(32)))
        assertEquals(BiosOutcome.NOT_VERIFIED, r.writes.single().outcome)
    }

    @Test
    fun nameOnlyWrongSizeIsSkipped() {
        val r = plan(PickedBios("gba_bios.bin", 999, "0".repeat(32)))
        assertEquals(emptyList(), r.writes)
        assertEquals(listOf(BiosSkip("gba_bios.bin", "Wrong size (expected 16384 bytes)")), r.skips)
    }

    @Test
    fun unknownFilesAreCountedNotListed() {
        val r = plan(PickedBios("a.txt", 3, "1".repeat(32)), PickedBios("game.gba", 4, "2".repeat(32)), PickedBios("x/y.bin", 5, null))
        assertEquals(3, r.otherFiles)
        assertEquals(emptyList(), r.skips)
        assertEquals(emptyList(), r.writes)
    }

    @Test
    fun subfolderPathMatchesByBaseName() {
        val r = plan(PickedBios("BIOS/PS1/scph5501.bin", 524288, ps1))
        assertEquals(BiosWrite("BIOS/PS1/scph5501.bin", "PS", "scph5501.bin", BiosOutcome.VERIFIED, bootChange = false), r.writes.single())
    }

    @Test
    fun identicalExistingIsAlreadyThere() {
        val r = plan(PickedBios("scph5501.bin", 524288, ps1), existing = { c, n -> if (c == "PS" && n == "scph5501.bin") ps1 else null })
        assertEquals(BiosOutcome.ALREADY_THERE, r.writes.single().outcome)
        assertFalse(r.writes.single().needsWrite)
    }

    @Test
    fun verifiedExistingKeptOverNotVerified() {
        val r = plan(PickedBios("gba_bios.bin", 16384, "0".repeat(32)), existing = { _, _ -> gba })
        assertEquals(BiosOutcome.KEPT_YOURS, r.writes.single().outcome)
        assertFalse(r.writes.single().needsWrite)
    }

    @Test
    fun unverifiedExistingReplacedByVerified() {
        val r = plan(PickedBios("gba_bios.bin", 16384, gba), existing = { _, _ -> "0".repeat(32) })
        assertEquals(BiosOutcome.VERIFIED, r.writes.single().outcome)
        assertTrue(r.writes.single().needsWrite)
    }

    @Test
    fun samePickTwiceWritesOnce() {
        val r = plan(PickedBios("scph5501.bin", 524288, ps1), PickedBios("SCPH-5501 (USA).bin", 524288, ps1))
        assertEquals(1, r.writes.count { it.needsWrite })
        assertEquals(BiosOutcome.ALREADY_THERE, r.writes.single { it.pickedPath == "SCPH-5501 (USA).bin" }.outcome)
    }

    @Test
    fun verifiedBeatsNotVerifiedForOneDestination() {
        val r = plan(PickedBios("gba_bios.bin", 16384, "0".repeat(32)), PickedBios("[BIOS] GBA.bin", 16384, gba))
        assertEquals("[BIOS] GBA.bin", r.writes.single { it.needsWrite }.pickedPath)
        assertEquals(listOf(BiosSkip("gba_bios.bin", "Another copy was used")), r.skips)
    }

    @Test
    fun oneFileSeveralConsoles() {
        val shared = KnownBios(known.entries + KnownBiosEntry("bios_CD_U.bin", emptyList(), "MD", setOf(131072L), setOf(cdU), required = false, bootChange = false))
        val r = BiosIntakePlanner.plan(listOf(PickedBios("bios_CD_U.bin", 131072, cdU)), shared, nothing)
        assertEquals(setOf("SEGACD", "MD"), r.writes.map { it.console }.toSet())
    }

    @Test
    fun bootChangeCarried() {
        assertTrue(plan(PickedBios("gb_bios.bin", 256, gb)).writes.single().bootChange)
    }

    @Test
    fun unhashedFileFallsToNameRule() {
        val r = plan(PickedBios("gba_bios.bin", 16384, null))
        assertEquals(BiosOutcome.NOT_VERIFIED, r.writes.single().outcome)
    }

    @Test
    fun onlyFilesThatCanMatchAreRead() {
        assertTrue(known.shouldRead(524288, "SCPH-5501 (USA).bin"))       // a listed size: may be a renamed BIOS
        assertTrue(known.shouldRead(999, "gba_bios.bin"))                 // a known name: planned by name (or skipped as wrong size)
        assertTrue(known.shouldRead(5_000_000, "neogeo.zip"))             // a set archive: any size
        assertTrue(known.shouldRead(null, "unknown.bin"))                 // size unknown: read, up to the largest listed size
        assertFalse(known.shouldRead(8_388_608, "Pokemon Emerald.gba"))   // no listed size, no known name: never opened
        assertFalse(known.shouldRead(4_000_000_000, "game.iso"))
        assertEquals(524288L, known.maxSize)
    }

    @Test
    fun anyNameConsoleFindsTheSameBytesUnderAnotherName() {
        val ps2Md5 = "d5ce2c7d119f563ce04bc04dbc3a323e"
        val withPs2 = KnownBios(known.entries + KnownBiosEntry("SCPH-39001.bin", emptyList(), "PS2", setOf(4194304L), setOf(ps2Md5), required = false, bootChange = false))
        val have = object : ExistingBios {
            override fun md5(console: String, name: String): String? = null
            override fun anyWithMd5(console: String, md5: String) = console == "PS2" && md5 == ps2Md5
        }
        val r = BiosIntakePlanner.plan(listOf(PickedBios("my ps2 bios.bin", 4194304, ps2Md5)), withPs2, have)
        assertEquals(BiosOutcome.ALREADY_THERE, r.writes.single().outcome)
        // Consoles that need exact names do not look under other names.
        val gbaHave = object : ExistingBios {
            override fun md5(console: String, name: String): String? = null
            override fun anyWithMd5(console: String, md5: String) = true
        }
        assertEquals(BiosOutcome.VERIFIED, BiosIntakePlanner.plan(listOf(PickedBios("gba_bios.bin", 16384, gba)), known, gbaHave).writes.single().outcome)
    }

    @Test
    fun hiddenJunkIsIgnoredEntirely() {
        assertFalse(BiosIntakePlanner.isCandidate("BIOS/._scph5501.bin"))
        assertFalse(BiosIntakePlanner.isCandidate(".DS_Store"))
        assertTrue(BiosIntakePlanner.isCandidate("BIOS/scph5501.bin"))
    }
}
