package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadFailureTest {
    // Lines as flycast (naomi_cart.cpp / libretro.cpp at the pinned commit) logs them through the runner: "[TYPE] text".
    private val awbiosBroken = listOf(
        "W [NAOMI] awbios: Cannot open bios0.ic23",
        "W [NAOMI] awbios: Cannot open bios.ic23_l",
        "W [NAOMI] Warning: Region 0 bios not found in awbios",
        "W [NAOMI] awbios: Cannot open bios0.ic23",
        "W [NAOMI] awbios: Cannot open bios.ic23_l",
        "E [BOOT] Error: cannot load BIOS awbios",
    )

    @Test fun atomiswave_bios_without_the_file_names_it() {
        assertEquals(
            "Metal Slug 6 needs awbios.zip with bios0.ic23 or bios.ic23_l in it: put a complete awbios.zip in Bios/DC/",
            LoadFailure.message("Metal Slug 6", awbiosBroken, "mslug6.zip", "awbios"),
        )
    }

    @Test fun bios_archive_comes_from_the_error_when_the_table_has_none() {
        assertEquals(
            "Metal Slug 6 needs awbios.zip with bios0.ic23 or bios.ic23_l in it: put a complete awbios.zip in Bios/DC/",
            LoadFailure.message("Metal Slug 6", awbiosBroken),
        )
        // A game without its own BIOS: flycast names the archive, extension included.
        assertEquals(
            "Giant Gram 2000 needs a complete naomi.zip in Bios/DC/",
            LoadFailure.message("Giant Gram 2000", listOf("E [BOOT] Error: cannot load BIOS naomi.zip")),
        )
    }

    @Test fun a_bios_name_that_lives_in_another_archive_uses_the_table() {
        val lines = listOf(
            "W [NAOMI] naomi-jp-d: Cannot open epr-21576d.ic27",
            "W [NAOMI] Warning: Region 0 bios not found in naomi-jp-d",
            "W [NAOMI] naomi-jp-d: Cannot open epr-21576d.ic27",
            "E [BOOT] Error: cannot load BIOS naomi-jp-d",
        )
        assertEquals(
            "Kasei Channel Mars TV needs naomi.zip with epr-21576d.ic27 in it: put a complete naomi.zip in Bios/DC/",
            LoadFailure.message("Kasei Channel Mars TV", lines, "marstv.zip", "naomi"),
        )
    }

    @Test fun many_missing_bios_files_are_cut_short() {
        val files = listOf("epr-21576h.ic27", "epr-21576g.ic27", "epr-21578h.ic27", "epr-21578g.ic27", "epr-21577h.ic27")
        val lines = files.map { "W [NAOMI] naomi: Cannot open $it" } + "E [BOOT] Error: cannot load BIOS naomi.zip"
        assertEquals(
            "Giant Gram 2000 needs naomi.zip with epr-21576h.ic27, epr-21576g.ic27, epr-21578h.ic27 or one of 2 more in it: " +
                "put a complete naomi.zip in Bios/DC/",
            LoadFailure.message("Giant Gram 2000", lines, "gram2000.zip", "naomi"),
        )
    }

    @Test fun a_missing_rom_in_the_set() {
        val lines = listOf(
            "W [NAOMI] mslug6.zip: Cannot open ax3201m01.mrom1",
            "E [BOOT] Cannot find ax3201m01.mrom1",
        )
        assertEquals(
            "Metal Slug 6 needs ax3201m01.mrom1, which mslug6.zip does not have: the set is incomplete or from another MAME version",
            LoadFailure.message("Metal Slug 6", lines, "mslug6.zip", "awbios"),
        )
        assertEquals(
            "Mushiking II needs mushi.ic8, which neither mushik2k.zip nor its parent mushik2e.zip has: " +
                "the set is incomplete or from another MAME version",
            LoadFailure.message("Mushiking II", listOf("E [BOOT] Cannot find mushi.ic8"), "mushik2k.zip", "naomi", "mushik2e"),
        )
    }

    @Test fun set_wording_only_for_flycast_arcade_loads() {
        // Another core's line of the same shape, not a set loaded as is: shown as the core wrote it.
        assertEquals(
            "The core couldn't load Foo: Cannot find foo",
            LoadFailure.message("Foo", listOf("E Cannot find foo")),
        )
        assertEquals(
            "The core couldn't load Foo: Invalid ROM: truncated foo",
            LoadFailure.message("Foo", listOf("E [libretro] Invalid ROM: truncated foo")),
        )
        // flycast's log type is enough without the set file.
        assertEquals(
            "Foo needs foo, which the set does not have: the set is incomplete or from another MAME version",
            LoadFailure.message("Foo", listOf("E [BOOT] Cannot find foo")),
        )
        // And a plain line is enough for a set loaded as is.
        assertEquals(
            "Foo needs foo, which foo.zip does not have: the set is incomplete or from another MAME version",
            LoadFailure.message("Foo", listOf("E Cannot find foo"), "foo.zip"),
        )
    }

    @Test fun a_truncated_rom() {
        assertEquals(
            "Metal Slug 6 can't start: ax3201p01.fmem1 in mslug6.zip is truncated, the set is damaged or from another MAME version",
            LoadFailure.message("Metal Slug 6", listOf("E [BOOT] Invalid ROM: truncated ax3201p01.fmem1"), "mslug6.zip"),
        )
    }

    @Test fun any_other_error_is_shown_as_the_core_wrote_it() {
        assertEquals(
            "The core couldn't load Foo: Unknown game",
            LoadFailure.message("Foo", listOf("W [NAOMI] Parent not found: bar", "E [BOOT] Unknown game")),
        )
        assertEquals(
            "The core couldn't load Foo: can't open /sdcard/x.lst",
            LoadFailure.message("Foo", listOf("E [BOOT] Error: can't open /sdcard/x.lst\n")),
        )
        // Another core's plain line; the last error wins over a later warning.
        assertEquals(
            "The core couldn't load Foo: failed to read m3u file ...",
            LoadFailure.message("Foo", listOf("E [libretro]: failed to read m3u file ...", "W something else")),
        )
    }

    @Test fun a_long_line_is_one_trimmed_line() {
        val long = "x".repeat(300)
        val msg = LoadFailure.message("Foo", listOf("E first\tpart  $long"))!!
        assertEquals(true, msg.endsWith("…"))
        assertEquals(false, msg.contains('\t'))
    }

    @Test fun nothing_useful_keeps_the_generic_text() {
        assertNull(LoadFailure.message("Foo", emptyList()))
        assertNull(LoadFailure.message("Foo", listOf("W [NAOMI] Parent not found: bar")))
        assertNull(LoadFailure.message("Foo", listOf("garbage without a level", "E ")))
    }
}
