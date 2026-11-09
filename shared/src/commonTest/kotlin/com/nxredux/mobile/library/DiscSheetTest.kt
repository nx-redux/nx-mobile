package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Companion
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DiscSheetTest {
    @Test fun a_cue_names_its_files_quoted_or_not() {
        val cue = "FILE \"Crash (Track 1).bin\" BINARY\r\n  TRACK 01 MODE2/2352\r\n    INDEX 01 00:00:00\r\n" +
            "file track2.bin BINARY\r\nFILE \"Crash (Track 1).bin\" BINARY\n  REM FILE \"not.bin\"\n"
        assertEquals(listOf("Crash (Track 1).bin", "track2.bin"), DiscSheet.references("Crash.cue", cue))
    }
    @Test fun an_m3u_skips_comments_blank_lines_and_a_bom() {
        val m3u = "\uFEFF#EXTM3U\n\nFF7 (Disc 1).cue\n  # a note\n./FF7 (Disc 2).chd\r\n"
        assertEquals(listOf("FF7 (Disc 1).cue", "FF7 (Disc 2).chd"), DiscSheet.references("FF7.M3U", m3u))
    }
    @Test fun a_cue_starting_with_a_bom_keeps_its_first_file() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(listOf("a.bin"), DiscSheet.references("x.cue", DiscSheet.decode(bom + "FILE \"a.bin\" BINARY\n".encodeToByteArray())))
        // A legacy sheet behind a BOM: the BOM is not read as Windows-1252 text.
        assertEquals("FILE \"\u00e9.bin\"\n", DiscSheet.decode(bom + byteArrayOf(*"FILE \"".encodeToByteArray(), 0xE9.toByte(), *".bin\"\n".encodeToByteArray())))
    }
    @Test fun a_bom_is_stripped_once_by_the_sheet_read() = runTest {
        val src = FakeRomsSource(mapOf("d" to listOf("x.cue")), texts = mapOf("d/x.cue" to "\uFEFFFILE \"a.bin\" BINARY\n"))
        assertEquals("FILE \"a.bin\" BINARY\n", DiscSheet.readSheet(src, ContentRef("d/x.cue")))
    }
    @Test fun references_outside_the_folder_are_ignored() {
        assertEquals(emptyList(), DiscSheet.references("x.cue", "FILE \"../x.bin\" BINARY\nFILE \"sub/x.bin\" BINARY\nFILE \"disc\\x.bin\" BINARY\n"))
        assertEquals(listOf("ok.chd"), DiscSheet.references("x.m3u", "../other/d1.chd\nok.chd\n"))
    }
    @Test fun ccd_and_toc_imply_their_same_stem_image() {
        assertEquals(listOf("Game.img", "Game.bin", "Game.sub"), DiscSheet.references("Game.ccd", "[CloneCD]"))
        assertEquals(listOf("Game.img", "Game.bin", "Game.sub"), DiscSheet.references("Game.toc", ""))
        assertEquals(emptyList(), DiscSheet.references("Game.chd", "anything"))
        assertTrue(DiscSheet.isSheet("a.CUE")); assertTrue(DiscSheet.isSheet("a.m3u")); assertFalse(DiscSheet.isSheet("a.chd"))
    }
    @Test fun an_m3u_expands_its_cues_one_level() = runTest {
        val texts = mapOf(
            "FF7.m3u" to "FF7 (Disc 1).cue\nFF7 (Disc 2).cue\nOther.m3u\n",
            "FF7 (Disc 1).cue" to "FILE \"FF7 (Disc 1).bin\" BINARY\n",
            "FF7 (Disc 2).cue" to "FILE \"FF7 (Disc 2).bin\" BINARY\n",
            "Other.m3u" to "Deep.cue\n",
        )
        assertEquals(
            listOf(
                SheetRef("FF7 (Disc 1).cue", "FF7.m3u"), SheetRef("FF7 (Disc 1).bin", "FF7 (Disc 1).cue"),
                SheetRef("FF7 (Disc 2).cue", "FF7.m3u"), SheetRef("FF7 (Disc 2).bin", "FF7 (Disc 2).cue"),
                SheetRef("Other.m3u", "FF7.m3u"),                                            // a nested m3u is not followed
            ),
            DiscSheet.expand("FF7.m3u") { texts[it] },
        )
        assertEquals(emptyList(), DiscSheet.expand("Gone.cue") { null })
    }
    @Test fun hidden_names_are_what_sibling_sheets_load_each_read_once() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("sd/PSX" to listOf("Crash.cue", "CRASH.BIN", "FF7.m3u", "FF7 (Disc 1).cue", "FF7 (Disc 1).bin", "Game.ccd", "Game.img", "Loose.bin")),
            texts = mapOf(
                "sd/PSX/Crash.cue" to "FILE \"Crash.bin\" BINARY\n",
                "sd/PSX/FF7.m3u" to "FF7 (Disc 1).cue\n",
                "sd/PSX/FF7 (Disc 1).cue" to "FILE \"FF7 (Disc 1).bin\" BINARY\n",
            ),
        )
        val reads = ArrayList<String>()
        val files = src.listChildren(ContentRef("sd/PSX"))
        val hidden = DiscSheet.hiddenIn(files) { reads += it.name; src.readHead(it.ref, DiscSheet.HEAD_BYTES) }
        assertEquals(setOf("crash.bin", "ff7 (disc 1).cue", "ff7 (disc 1).bin", "game.img"), hidden)
        assertEquals(listOf("Crash.cue", "FF7.m3u", "FF7 (Disc 1).cue"), reads)   // ccd is never read; each cue/m3u once
        assertEquals(emptySet(), DiscSheet.hiddenIn(listOf(files.last())) { error("no sheet, no read") })
    }
    @Test fun companions_match_names_whatever_their_case_and_report_the_first_missing_one() = runTest {
        val ok = FakeRomsSource(
            dirs = mapOf("H/PS" to listOf("Crash.cue", "CRASH.BIN")),
            texts = mapOf("H/PS/Crash.cue" to "FILE \"Crash.bin\" BINARY\n"),
        )
        assertEquals(CompanionLookup.Found(listOf(Companion("Crash.bin", ContentRef("H/PS/CRASH.BIN")))), DiscSheet.companions(ok, ContentRef("H/PS"), "Crash.cue"))
        assertEquals(CompanionLookup.Found(emptyList()), DiscSheet.companions(ok, ContentRef("H/PS"), "CRASH.BIN"))   // not a sheet

        val gone = FakeRomsSource(
            dirs = mapOf("H/PS" to listOf("FF7.m3u", "FF7 (Disc 1).cue", "FF7 (Disc 1).bin", "FF7 (Disc 2).cue")),
            texts = mapOf(
                "H/PS/FF7.m3u" to "FF7 (Disc 1).cue\nFF7 (Disc 2).cue\n",
                "H/PS/FF7 (Disc 1).cue" to "FILE \"FF7 (Disc 1).bin\" BINARY\n",
                "H/PS/FF7 (Disc 2).cue" to "FILE \"FF7 (Disc 2).bin\" BINARY\n",
            ),
        )
        assertEquals(CompanionLookup.Missing("FF7 (Disc 2).bin", "FF7 (Disc 2).cue"), DiscSheet.companions(gone, ContentRef("H/PS"), "FF7.m3u"))
    }
    @Test fun a_name_that_only_a_hidden_listing_could_match_is_missing_not_a_throw() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("H/PS" to listOf("Game.cue", "GAME (TRACK 1).BIN")),
            texts = mapOf("H/PS/Game.cue" to "FILE \"Game (Track 1).bin\" BINARY\n"),
        )
        src.listingFault = ListingFault.UNAVAILABLE
        assertEquals(CompanionLookup.Missing("Game (Track 1).bin", "Game.cue"), DiscSheet.companions(src, ContentRef("H/PS"), "Game.cue"))
    }
    @Test fun a_ccd_needs_its_image_and_takes_its_subchannel_when_present() = runTest {
        val src = FakeRomsSource(dirs = mapOf("H/PCE" to listOf("Game.ccd", "Game.img", "Game.sub", "Lone.ccd")))
        assertEquals(
            CompanionLookup.Found(listOf(Companion("Game.img", ContentRef("H/PCE/Game.img")), Companion("Game.sub", ContentRef("H/PCE/Game.sub")))),
            DiscSheet.companions(src, ContentRef("H/PCE"), "Game.ccd"),
        )
        assertEquals(CompanionLookup.Missing("Lone.img", "Lone.ccd"), DiscSheet.companions(src, ContentRef("H/PCE"), "Lone.ccd"))
    }

    private val gdi = "3\n1 0 4 2352 track01.bin 0\n2 756 0 2352 \"track 02.raw\" 0\n3 45000 4 2352 track03.bin 0"

    @Test fun a_gdi_names_its_tracks_plain_or_quoted() {
        assertEquals(listOf("track01.bin", "track 02.raw", "track03.bin"), DiscSheet.references("Game.gdi", gdi))
        assertTrue(DiscSheet.isSheet("x.gdi")); assertTrue(DiscSheet.isSheet("X.GDI"))
    }
    @Test fun a_gdi_tolerates_crlf_a_bom_extra_spaces_and_trailing_blank_lines() {
        val messy = "\uFEFF3\r\n1  0   4 2352   track01.bin   0\r\n\t2 756 0 2352 \"track 02.raw\"  0 \r\n3 45000 4 2352 track03.bin 0\r\n\r\n  \r\n"
        assertEquals(listOf("track01.bin", "track 02.raw", "track03.bin"), DiscSheet.references("Game.gdi", messy))
    }
    @Test fun a_gdi_track_outside_the_folder_or_on_a_short_line_is_ignored() {
        val text = "4\n1 0 4 2352 sub/track01.bin 0\n2 756 0 2352 \"..\\x\\track02.raw\" 0\n3 45000 4 2352\n4 45100 4 2352 ./track04.bin 0\n"
        assertEquals(listOf("track04.bin"), DiscSheet.references("Game.gdi", text))
        assertEquals(emptyList(), DiscSheet.references("Game.gdi", ""))
    }
    @Test fun a_gdi_hides_its_tracks_and_stays_listed() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("sd/DC" to listOf("Game.gdi", "track01.bin", "track 02.raw", "TRACK03.BIN", "Other.cdi")),
            texts = mapOf("sd/DC/Game.gdi" to gdi),
        )
        val files = src.listChildren(ContentRef("sd/DC"))
        assertEquals(setOf("track01.bin", "track 02.raw", "track03.bin"), DiscSheet.hiddenIn(files) { src.readHead(it.ref, DiscSheet.HEAD_BYTES) })
    }
    @Test fun a_gdi_hands_the_core_every_track_or_names_the_missing_one() = runTest {
        val ok = FakeRomsSource(
            dirs = mapOf("H/DC" to listOf("Game.gdi", "track01.bin", "track 02.raw", "track03.bin")),
            texts = mapOf("H/DC/Game.gdi" to gdi),
        )
        assertEquals(
            CompanionLookup.Found(listOf(
                Companion("track01.bin", ContentRef("H/DC/track01.bin")),
                Companion("track 02.raw", ContentRef("H/DC/track 02.raw")),
                Companion("track03.bin", ContentRef("H/DC/track03.bin")),
            )),
            DiscSheet.companions(ok, ContentRef("H/DC"), "Game.gdi"),
        )
        val gone = FakeRomsSource(
            dirs = mapOf("H/DC" to listOf("Game.gdi", "track01.bin", "track03.bin")),
            texts = mapOf("H/DC/Game.gdi" to gdi),
        )
        assertEquals(CompanionLookup.Missing("track 02.raw", "Game.gdi"), DiscSheet.companions(gone, ContentRef("H/DC"), "Game.gdi"))
    }
    @Test fun an_m3u_listing_gdis_expands_to_their_tracks() = runTest {
        val texts = mapOf(
            "Shenmue.m3u" to "Shenmue (Disc 1).gdi\nShenmue (Disc 2).gdi\n",
            "Shenmue (Disc 1).gdi" to "1\n1 0 4 2352 \"Shenmue (Disc 1) (Track 1).bin\" 0\n",
            "Shenmue (Disc 2).gdi" to "1\n1 0 4 2352 \"Shenmue (Disc 2) (Track 1).bin\" 0\n",
        )
        assertEquals(
            listOf(
                SheetRef("Shenmue (Disc 1).gdi", "Shenmue.m3u"), SheetRef("Shenmue (Disc 1) (Track 1).bin", "Shenmue (Disc 1).gdi"),
                SheetRef("Shenmue (Disc 2).gdi", "Shenmue.m3u"), SheetRef("Shenmue (Disc 2) (Track 1).bin", "Shenmue (Disc 2).gdi"),
            ),
            DiscSheet.expand("Shenmue.m3u") { texts[it] },
        )
    }

    @Test fun a_sheet_in_a_legacy_encoding_reads_as_cp1252() {
        val cp1252 = byteArrayOf(*"FILE \"Pok".encodeToByteArray(), 0xE9.toByte(), *"mon ".encodeToByteArray(), 0x92.toByte(), *"99.bin\" BINARY\n".encodeToByteArray())
        assertEquals("FILE \"Pokémon \u201999.bin\" BINARY\n", DiscSheet.decode(cp1252))
        val utf8 = "FILE \"Pokémon.bin\" BINARY\n".encodeToByteArray()
        assertEquals("FILE \"Pokémon.bin\" BINARY\n", DiscSheet.decode(utf8))
        // A head read can end inside a multi-byte character: still UTF-8, not a legacy sheet.
        val cut = "FILE \"a.bin\" BINARY\nREM é".encodeToByteArray().let { it.copyOfRange(0, it.size - 1) }
        assertEquals("FILE \"a.bin\" BINARY\nREM ", DiscSheet.decode(cut, truncated = true))
        // A whole file ending in a lead byte is not valid UTF-8: a legacy sheet, its last character kept.
        assertEquals("FILE \"a.bin\" BINARY\nREM \u00c3", DiscSheet.decode(cut))
    }
    @Test fun a_legacy_sheet_finds_and_hides_its_files() = runTest {
        val cue = byteArrayOf(*"FILE \"Pok".encodeToByteArray(), 0xE9.toByte(), *"mon.bin\" BINARY\n".encodeToByteArray())
        val src = FakeRomsSource(dirs = mapOf("H/PS" to listOf("Pokémon.cue", "Pokémon.bin")), bytes = mapOf("H/PS/Pokémon.cue" to cue))
        assertEquals(CompanionLookup.Found(listOf(Companion("Pokémon.bin", ContentRef("H/PS/Pokémon.bin")))), DiscSheet.companions(src, ContentRef("H/PS"), "Pokémon.cue"))
        val files = src.listChildren(ContentRef("H/PS"))
        assertEquals(setOf("pokémon.bin"), DiscSheet.hiddenIn(files) { src.readHead(it.ref, DiscSheet.HEAD_BYTES) })
    }
    @Test fun a_name_spelled_in_another_code_page_matches_the_one_listed_file_differing_only_in_non_ascii() = runTest {
        // An ISO-8859-2 sheet read as CP1252: "Ł" (0xA3) comes out as "£"; the listing has the real name.
        val src = FakeRomsSource(
            dirs = mapOf("H/PS" to listOf("Łódź.cue", "Łódź (Track 1).bin", "Other (Track 1).bin")),
            bytes = mapOf("H/PS/Łódź.cue" to byteArrayOf(*"FILE \"".encodeToByteArray(), 0xA3.toByte(), 0xF3.toByte(), *"d".encodeToByteArray(), 0xBC.toByte(), *" (Track 1).bin\" BINARY\n".encodeToByteArray())),
        )
        val found = DiscSheet.companions(src, ContentRef("H/PS"), "Łódź.cue") as CompanionLookup.Found
        assertEquals(listOf(ContentRef("H/PS/Łódź (Track 1).bin")), found.companions.map { it.ref })
        val files = src.listChildren(ContentRef("H/PS"))
        assertEquals(setOf("łódź (track 1).bin"), DiscSheet.hiddenIn(files) { src.readHead(it.ref, DiscSheet.HEAD_BYTES) })
        // Two listed names with the same ASCII shape: no guess, the name is missing.
        val twin = FakeRomsSource(
            dirs = mapOf("H/PS" to listOf("Łódź.cue", "Łódź (Track 1).bin", "Žódž (Track 1).bin")),
            bytes = mapOf("H/PS/Łódź.cue" to byteArrayOf(*"FILE \"".encodeToByteArray(), 0xA3.toByte(), 0xF3.toByte(), *"d".encodeToByteArray(), 0xBC.toByte(), *" (Track 1).bin\" BINARY\n".encodeToByteArray())),
        )
        assertTrue(DiscSheet.companions(twin, ContentRef("H/PS"), "Łódź.cue") is CompanionLookup.Missing)
    }
}
