package com.nxredux.mobile.cheats.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheatDbManifestTest {
    private val manifest = CheatDbManifest(
        stamp = "Wed, 21 Aug 2024 10:00:00 GMT", installedAt = 1758750000000L,
        files = listOf("Cheats/GB/Tetris (World).cht", "Cheats/GB/Alleyway (World).cht", "Cheats/GBA/Advance Wars (USA).cht", "Cheats/MGBA/Advance Wars (USA).cht"),
    )

    @Test fun render_then_parse_round_trips() {
        val text = manifest.render()
        assertTrue(text.startsWith("# nx-mobile cheatdb 1\nstamp = Wed, 21 Aug 2024 10:00:00 GMT\ninstalled = 1758750000000\nCheats/GB/Tetris (World).cht\n"))
        assertEquals(manifest, CheatDbManifest.parse(text))
    }

    @Test fun tags_round_trip_and_default_to_null() {
        val tagged = manifest.copy(tags = listOf("GB", "GBA", "MGBA"))
        assertTrue(tagged.render().contains("\ntags = GB,GBA,MGBA\n"))
        assertEquals(tagged, CheatDbManifest.parse(tagged.render()))
        assertNull(CheatDbManifest.parse(manifest.render())!!.tags)
    }

    @Test fun cht_commit_round_trips_and_an_older_manifest_parses_without_it() {
        val committed = manifest.copy(tags = listOf("GB"), patchesStamp = "p", chtCommit = "0123456789abcdef0123456789abcdef01234567")
        assertTrue(committed.render().contains("\ncht_commit = 0123456789abcdef0123456789abcdef01234567\n"))
        assertEquals(committed, CheatDbManifest.parse(committed.render()))
        assertNull(CheatDbManifest.parse(manifest.render())!!.chtCommit)
        assertFalse(manifest.render().contains("cht_commit"))
    }

    @Test fun counts_group_by_tag() {
        assertEquals(mapOf("GB" to 2, "GBA" to 1, "MGBA" to 1), manifest.countsByTag)
        assertEquals(3, manifest.systems)
        assertEquals(4, manifest.fileCount)
        assertTrue(manifest.installed)
    }

    @Test fun crlf_blank_and_comment_lines_are_tolerated() {
        val text = "# nx-mobile cheatdb 1\r\n\r\nstamp = installed\r\n# note\r\ninstalled = 5\r\nCheats/FC/x.cht\r\n"
        assertEquals(CheatDbManifest("installed", 5L, listOf("Cheats/FC/x.cht")), CheatDbManifest.parse(text))
    }

    @Test fun unknown_keys_are_ignored_and_missing_ones_default() {
        val text = "# nx-mobile cheatdb 1\nfuture = yes\nCheats/FC/x.cht\n"
        assertEquals(CheatDbManifest(CheatDbManifest.UNKNOWN_STAMP, 0L, listOf("Cheats/FC/x.cht")), CheatDbManifest.parse(text))
    }

    @Test fun unknown_version_is_null() {
        assertNull(CheatDbManifest.parse("# nx-mobile cheatdb 2\nCheats/FC/x.cht\n"))
        assertNull(CheatDbManifest.parse(""))
        assertNull(CheatDbManifest.parse("Cheats/FC/x.cht\n"))
    }

    @Test fun no_files_parses_but_is_not_installed() {
        val m = CheatDbManifest.parse("# nx-mobile cheatdb 1\nstamp = x\ninstalled = 1\n")
        assertEquals(emptyList(), m!!.files)
        assertFalse(m.installed)
        assertEquals(0, m.systems)
    }

    @Test fun a_path_containing_equals_is_still_a_path() {
        val m = CheatDbManifest.parse("# nx-mobile cheatdb 1\nCheats/GB/A = B (USA).cht\n")
        assertEquals(listOf("Cheats/GB/A = B (USA).cht"), m!!.files)
    }

    @Test fun the_patches_stamp_round_trips_and_defaults_to_null() {
        val both = manifest.copy(tags = listOf("GB", "PS2"), patchesStamp = "Sat, 24 Oct 2026 22:57:54 GMT")
        assertTrue(both.render().contains("\ntags = GB,PS2\npatches_stamp = Sat, 24 Oct 2026 22:57:54 GMT\n"))
        assertEquals(both, CheatDbManifest.parse(both.render()))
        assertNull(CheatDbManifest.parse(manifest.render())!!.patchesStamp)
    }

    @Test fun each_source_has_its_stamp() {
        val m = manifest.copy(patchesStamp = "p")
        assertEquals("Wed, 21 Aug 2024 10:00:00 GMT", m.stampOf(CheatDbSource.LIBRETRO))
        assertEquals("p", m.stampOf(CheatDbSource.PCSX2))
        assertEquals(CheatDbManifest.UNKNOWN_STAMP, manifest.stampOf(CheatDbSource.PCSX2))   // patches never installed
    }

    @Test fun patch_files_count_under_their_console() {
        val m = CheatDbManifest("s", 1L, listOf("Cheats/FC/x.cht", "Cheats/PS2/Patches/SLUS-21678_428113C2.pnach", "Cheats/PS2/Patches/6D980D22.pnach"))
        assertEquals(mapOf("FC" to 1, "PS2" to 2), m.countsByTag)
        assertEquals(m, CheatDbManifest.parse(m.render()))
    }
}
