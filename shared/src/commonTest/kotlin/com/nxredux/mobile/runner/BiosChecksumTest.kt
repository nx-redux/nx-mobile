package com.nxredux.mobile.runner

import com.nxredux.mobile.bios.KnownBios
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BiosChecksumTest {
    private val known = KnownBios.parse(
        listOf(
            "disksys.rom\t\tFDS\t8192\tfds1,fds2\t1\t0",
            "bios_CD_U.bin\t\tSEGACD\t131072\tus1\t1\t0",
            "us_scd1_9210.bin\t\tSEGACD\t131072\tus1\t0\t0",
            "us_scd2_9306.bin\t\tSEGACD\t131072\tus2\t0\t0",
            "bios_CD_E.bin\t\tSEGACD\t131072\teu1\t1\t0",
            "eu_mcd1_9210.bin\t\tSEGACD\t131072\teu1\t0\t0",
            "bios_CD_J.bin\t\tSEGACD\t131072\tjp1\t1\t0",
            "bios_CD_U.zip\t\tSEGACD\t\t\t0\t0",
            "scph1001.bin\t\tPS\t524288\tps1\t0\t0",
            "syscard1.pce\t\tPCE\t262144\tcard1\t0\t0",
            "syscard3.pce\t\tPCE\t262144,262656\tcard3a,card3b\t1\t0",
            "ps2-0230a-20080220.bin\t\tPS2\t4194304\tps2a\t1\t0",
            "SCPH-39001.bin\t\tPS2\t4194304\tps2b\t0\t0",
            "saturn_bios.bin\t\tSATURN\t524288\tssjp\t0\t0",
            "mpr-17933.bin\t\tSATURN\t524288\tssus\t0\t0",
        ).joinToString("\n"),
    )

    private fun files(vararg f: BiosFile): (String) -> BiosFile? = f.associateBy { it.name }::get

    private fun fdsProblem(file: BiosFile): BiosProblem? {
        val groups = BiosChecksum.toCheck(known, "FDS", CoreRegistry.requiredBios("FDS"), "fds", setOf(file.name), "", null, null)
        return BiosChecksum.problem(known, groups, files(file))
    }

    @Test fun a_listed_dump_passes() {
        assertNull(fdsProblem(BiosFile("disksys.rom", 8192, "fds1")))
        assertNull(fdsProblem(BiosFile("disksys.rom", 8192, "FDS2")))   // md5 case does not matter
    }

    @Test fun a_wrong_size_refuses_with_the_good_size() {
        val p = assertIs<BiosProblem.WrongSize>(fdsProblem(BiosFile("disksys.rom", 16384, "bad")))
        assertTrue(p.refuses)
        assertEquals(
            "disksys.rom in Bios/FC/ is not the Famicom Disk System BIOS: a good dump is 8 KB, this one is 16 KB. Replace it with a good dump.",
            BiosChecksum.message(p, "Bios/FC/"),
        )
    }

    @Test fun the_right_size_with_an_unlisted_md5_only_warns() {
        val p = assertIs<BiosProblem.Unlisted>(fdsProblem(BiosFile("disksys.rom", 8192, "other")))
        assertFalse(p.refuses)
        assertEquals(
            "disksys.rom in Bios/FDS/ is not a known good dump of the Famicom Disk System BIOS. If the game does not start, replace it.",
            BiosChecksum.message(p, "Bios/FDS/"),
        )
    }

    @Test fun another_consoles_bios_refuses_and_is_named() {
        val p = assertIs<BiosProblem.OtherDump>(fdsProblem(BiosFile("disksys.rom", 524288, "ps1")))
        assertTrue(p.refuses)
        assertEquals(
            "disksys.rom in Bios/FDS/ is scph1001.bin, the Sony PlayStation BIOS, not the Famicom Disk System BIOS. Replace it with a good dump.",
            BiosChecksum.message(p, "Bios/FDS/"),
        )
    }

    @Test fun an_unreadable_file_or_an_unknown_name_is_not_judged() {
        assertNull(fdsProblem(BiosFile("disksys.rom", 1, null)))
        val empty = BiosChecksum.toCheck(KnownBios.EMPTY, "FDS", CoreRegistry.requiredBios("FDS"), "fds", setOf("disksys.rom"), "", null, null)
        assertEquals(emptyList(), empty)
        // A launch the need does not apply to checks nothing.
        assertEquals(emptyList(), BiosChecksum.toCheck(known, "FC", CoreRegistry.requiredBios("FC"), "nes", setOf("disksys.rom"), "", null, null))
    }

    @Test fun another_card_of_the_same_console_only_warns() {
        val groups = BiosChecksum.toCheck(known, "PCE", CoreRegistry.requiredBios("PCE"), "cue", setOf("syscard3.pce"), "", null, null)
        val p = assertIs<BiosProblem.OtherDump>(BiosChecksum.problem(known, groups, files(BiosFile("syscard3.pce", 262144, "card1"))))
        assertFalse(p.refuses)
        assertEquals(
            "syscard3.pce in Bios/PCE/ is syscard1.pce, another TurboGrafx-16 BIOS. If the game does not start, put a good syscard3.pce there.",
            BiosChecksum.message(p, "Bios/PCE/"),
        )
        assertNull(BiosChecksum.problem(known, groups, files(BiosFile("syscard3.pce", 262656, "card3b"))))
    }

    @Test fun a_sega_cd_name_takes_any_dump_of_its_region() {
        val needs = CoreRegistry.requiredBios("SEGACD")
        val groups = BiosChecksum.toCheck(known, "SEGACD", needs, "cue", setOf("bios_CD_U.bin"), SegaPadType.GPGX, DiscRegion.US, null)
        assertNull(BiosChecksum.problem(known, groups, files(BiosFile("bios_CD_U.bin", 131072, "us2"))))   // a later US model, renamed
        val p = assertIs<BiosProblem.OtherDump>(BiosChecksum.problem(known, groups, files(BiosFile("bios_CD_U.bin", 131072, "eu1"))))
        assertTrue(p.refuses)   // region-locked: a European BIOS never boots a US disc
        assertEquals(
            "bios_CD_U.bin in Bios/SEGACD/ is the European Sega CD BIOS, not the US Sega CD BIOS. Replace it with a good dump.",
            BiosChecksum.message(p, "Bios/SEGACD/"),
        )
    }

    @Test fun a_known_region_checks_only_that_regions_files() {
        val present = setOf("bios_CD_U.bin", "bios_CD_E.bin")
        val groups = BiosChecksum.toCheck(known, "SEGACD", CoreRegistry.requiredBios("SEGACD"), "chd", present, SegaPadType.GPGX, DiscRegion.EU, null)
        assertEquals(listOf(listOf("bios_CD_E.bin")), groups.map { g -> g.map { it.name } })
        val bad = files(BiosFile("bios_CD_U.bin", 131072, "jp1"), BiosFile("bios_CD_E.bin", 131072, "eu1"))
        assertNull(BiosChecksum.problem(known, groups, bad))
    }

    @Test fun without_a_region_each_sega_cd_file_is_held_to_its_own_and_one_good_is_enough() {
        val present = setOf("bios_CD_U.bin", "bios_CD_J.bin")
        val groups = BiosChecksum.toCheck(known, "SEGACD", CoreRegistry.requiredBios("SEGACD"), "chd", present, SegaPadType.GPGX, null, null)
        assertNull(BiosChecksum.problem(known, groups, files(BiosFile("bios_CD_U.bin", 131072, "eu1"), BiosFile("bios_CD_J.bin", 131072, "jp1"))))
        val p = BiosChecksum.problem(known, groups, files(BiosFile("bios_CD_U.bin", 131072, "eu1"), BiosFile("bios_CD_J.bin", 131072, "zz")))
        assertIs<BiosProblem.Unlisted>(p)   // the warning first: the core may load that one
    }

    @Test fun picodrive_archives_are_not_judged() {
        val groups = BiosChecksum.toCheck(
            known, "SEGACD", CoreRegistry.requiredBios("SEGACD"), "cue", setOf("bios_CD_U.zip", "bios_CD_U.bin"), SegaPadType.PICODRIVE, DiscRegion.US, null,
        )
        assertEquals(emptyList(), groups)
    }

    @Test fun ps2_takes_any_name_and_judges_only_another_consoles_dump() {
        val needs = CoreRegistry.requiredBios("PS2")
        val present = setOf("mine.bin", "scph1001.bin", "notes.txt")
        val all = BiosChecksum.toCheck(known, "PS2", needs, "iso", present, "", null, null)
        assertEquals(listOf(listOf("mine.bin", "scph1001.bin")), all.map { g -> g.map { it.name } })
        val ps1 = BiosFile("scph1001.bin", 524288, "ps1")
        // An unlisted dump under a free name may be a revision the table misses: no word.
        assertNull(BiosChecksum.problem(known, all, files(BiosFile("mine.bin", 4194304, "unlisted"), ps1)))
        val picked = BiosChecksum.toCheck(known, "PS2", needs, "iso", present, "", null, "scph1001.bin")
        val p = assertIs<BiosProblem.OtherDump>(BiosChecksum.problem(known, picked, files(ps1)))
        assertTrue(p.refuses)
        assertEquals(
            "scph1001.bin in Bios/PS2/ is the Sony PlayStation BIOS, not a Sony PlayStation 2 BIOS. Replace it with a good dump.",
            BiosChecksum.message(p, "Bios/PS2/"),
        )
        // A pick no longer in the folder falls back to every .bin, as the core's Auto does.
        assertEquals(all, BiosChecksum.toCheck(known, "PS2", needs, "iso", present, "", null, "gone.bin"))
    }

    @Test fun saturn_takes_any_regions_dump_under_any_of_its_names() {
        val needs = CoreRegistry.requiredBios("SATURN")
        val groups = BiosChecksum.toCheck(known, "SATURN", needs, "cue", setOf("saturn_bios.bin"), "", null, null)
        assertNull(BiosChecksum.problem(known, groups, files(BiosFile("saturn_bios.bin", 524288, "ssus"))))
        // A revision the table misses still boots: no word.
        assertNull(BiosChecksum.problem(known, groups, files(BiosFile("saturn_bios.bin", 524288, "unlisted"))))
        val ps1 = assertIs<BiosProblem.OtherDump>(BiosChecksum.problem(known, groups, files(BiosFile("saturn_bios.bin", 524288, "ps1"))))
        assertTrue(ps1.refuses)
    }

    @Test fun md5_cache_keeps_a_row_while_size_and_time_match() {
        val cache = BiosMd5Cache.parse("/s/a.bin\t10\t100\tabc\nbroken\n/s/b.bin\tx\t1\tdef\n")
        assertEquals("abc", cache.get("/s/a.bin", 10, 100))
        assertNull(cache.get("/s/a.bin", 10, 101))
        assertNull(cache.get("/s/a.bin", 11, 100))
        assertNull(cache.get("/s/b.bin", 1, 1))
        cache.put("/s/c.bin", 5, 6, "ghi")
        cache.retain { it != "/s/a.bin" }
        assertEquals("/s/c.bin\t5\t6\tghi\n", cache.format())
        assertEquals("ghi", BiosMd5Cache.parse(cache.format()).get("/s/c.bin", 5, 6))
    }
}
