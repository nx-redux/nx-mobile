package com.nxredux.mobile.cheats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheatFileTest {
    // Shape of a libretro-database file (Nintendo - Game Boy).
    private val sample = """
        cheats = 3

        cheat0_desc = "Infinite Lives"
        cheat0_code = "0109C0C1"
        cheat0_enable = false

        cheat1_desc = "Start On Level 9 (\"Type A\")"
        cheat1_code = "0109E0C2+010AE1C2"
        cheat1_enable = true

        cheat2_desc = "Max Score &quot;Bonus&quot;"
        cheat2_code = "01FFC5C1"
        cheat2_enable = false
    """.trimIndent()

    @Test fun parses_desc_code_and_enable_in_index_order() {
        assertEquals(
            listOf(
                Cheat("Infinite Lives", "0109C0C1", false),
                Cheat("Start On Level 9 (\"Type A\")", "0109E0C2+010AE1C2", true),
                Cheat("Max Score \"Bonus\"", "01FFC5C1", false),
            ),
            CheatFile.parse(sample),
        )
    }

    @Test fun count_line_can_come_anywhere_and_must_be_positive() {
        assertNull(CheatFile.parse(""))
        assertNull(CheatFile.parse("cheat0_desc = \"x\"\ncheat0_code = \"y\"\n"))
        // RetroArch's cheat search writes the count last.
        assertEquals(listOf(Cheat("x", "y")), CheatFile.parse("cheat0_desc = \"x\"\ncheat0_code = \"y\"\ncheats = 1\n"))
        assertEquals(listOf(Cheat("x", "y")), CheatFile.parse("cheats_x = 9\ncheats = 1\ncheat0_desc = \"x\"\ncheat0_code = \"y\"\n"))
        assertNull(CheatFile.parse("cheats = 0\n"))
        assertNull(CheatFile.parse("cheats = abc\n"))
        assertEquals(listOf(Cheat("x", "y")), CheatFile.parse("\n\n  cheats=1\ncheat0_desc = \"x\"\ncheat0_code = \"y\"\n"))
    }

    @Test fun a_quoted_count_is_read() {   // libretro's PSP files: cheats = "213"
        val text = "cheats = \"2\"\n\ncheat0_desc = \"x\"\ncheat0_code =  \"_L 0x1 0x2\"\ncheat1_desc = \"y\"\ncheat1_code = \"z\"\n"
        assertEquals(listOf(Cheat("x", "_L 0x1 0x2"), Cheat("y", "z")), CheatFile.parse(text))
        assertNull(CheatFile.parse("cheats = \"2\n"))   // half-quoted
        assertNull(CheatFile.parse("cheats = \"0\"\n"))
    }

    @Test fun crlf_and_bom_are_tolerated() {
        val text = "\uFEFFcheats = 1\r\ncheat0_desc = \"Lives\"\r\ncheat0_code = \"01\"\r\ncheat0_enable = true\r\n"
        assertEquals(listOf(Cheat("Lives", "01", true)), CheatFile.parse(text))
    }

    @Test fun enable_is_a_bare_case_insensitive_boolean() {
        val base = "cheats = 1\ncheat0_desc = \"d\"\ncheat0_code = \"c\"\n"
        assertEquals(true, CheatFile.parse(base + "cheat0_enable = TRUE\n")!![0].enabled)
        assertEquals(false, CheatFile.parse(base + "cheat0_enable = False\n")!![0].enabled)
        // Quoted or garbage: the line is skipped, the cheat stays disabled.
        assertEquals(false, CheatFile.parse(base + "cheat0_enable = \"true\"\n")!![0].enabled)
        assertEquals(false, CheatFile.parse(base + "cheat0_enable = yes\n")!![0].enabled)
    }

    @Test fun other_keys_and_out_of_range_indices_are_ignored() {
        val text = """
            cheats = 1
            cheat0_desc = "d"
            cheat0_code = "c"
            cheat0_address = 1234
            cheat0_big_endian = false
            cheat0_unknown_key = 5
            cheat1_desc = "out of range"
            cheat1_code = "zz"
            cheat7_enable = true
        """.trimIndent()
        assertEquals(listOf(Cheat("d", "c")), CheatFile.parse(text))
    }

    @Test fun a_cheat_without_a_code_is_dropped() {
        val text = "cheats = 3\ncheat0_desc = \"a\"\ncheat0_code = \"1\"\ncheat1_desc = \"no code\"\ncheat2_desc = \"c\"\ncheat2_code = \"3\"\n"
        assertEquals(listOf(Cheat("a", "1"), Cheat("c", "3")), CheatFile.parse(text))
        // An empty code counts as missing.
        assertEquals(emptyList(), CheatFile.parse("cheats = 1\ncheat0_desc = \"a\"\ncheat0_code = \"\"\n"))
    }

    @Test fun a_cheat_without_a_desc_is_named_by_its_number() {
        // 135 database files have one (Sonic 3 & Knuckles' cheat 59); the handheld names them too.
        val text = "cheats = 3\ncheat0_desc = \"\"\ncheat0_code = \"1\"\ncheat1_desc = \"b\"\ncheat1_code = \"2\"\ncheat2_code = \"3\"\n"
        assertEquals(listOf(Cheat("Cheat 1", "1"), Cheat("b", "2"), Cheat("Cheat 3", "3")), CheatFile.parse(text))
        val ram = "cheats = 1\ncheat0_handler = 1\ncheat0_value = 2\n"
        assertEquals(listOf(Cheat("Cheat 1", "", mem = MemoryPatch(value = 2))), CheatFile.parse(ram))
    }

    @Test fun database_html_entities_are_decoded() {
        val text = "cheats = 1\ncheat0_desc = \"P1 &amp; P2 &lt;3&gt; &apos;x&apos;&nbsp;Pok&eacute;mon &amp;amp; &bogus;\"\ncheat0_code = \"1\"\n"
        assertEquals("P1 & P2 <3> 'x' Pokémon &amp; &bogus;", CheatFile.parse(text)!![0].desc)
    }

    @Test fun a_bad_line_is_skipped_not_fatal() {
        // The bad desc line is skipped; the cheat keeps its code under its number.
        val text = "cheats = 2\ncheat0_desc = \"unterminated\ncheat0_code = \"1\"\ncheat1_desc = \"ok\"\ncheat1_code = \"2\"\n"
        assertEquals(listOf(Cheat("Cheat 1", "1"), Cheat("ok", "2")), CheatFile.parse(text))
        // A value without quotes is a bad line too.
        assertEquals(listOf(Cheat("Cheat 1", "1"), Cheat("ok", "2")), CheatFile.parse("cheats = 2\ncheat0_desc = bare\ncheat0_code = \"1\"\ncheat1_desc = \"ok\"\ncheat1_code = \"2\"\n"))
    }

    @Test fun a_long_code_line_is_read_whole() {
        // Database codes run to 15,000 chars (PSP): no fixed-size line buffer may cut them.
        val code = "_L 0x20000000 0x00000000 ".repeat(600).trim()
        val text = "cheats = 1\ncheat0_desc = \"d\"\ncheat0_code = \"$code\"\n"
        assertEquals(listOf(Cheat("d", code)), CheatFile.parse(text))
    }

    @Test fun duplicate_keys_last_wins_and_code_is_verbatim() {
        val text = "cheats = 1\ncheat0_desc = \"first\"\ncheat0_desc = \"second\"\ncheat0_code = \" 01 + 02 \"\n"
        assertEquals(listOf(Cheat("second", " 01 + 02 ")), CheatFile.parse(text))
    }

    @Test fun count_is_capped() {
        val text = "cheats = 999999\ncheat0_desc = \"d\"\ncheat0_code = \"c\"\ncheat5000_desc = \"far\"\ncheat5000_code = \"x\"\n"
        assertEquals(listOf(Cheat("d", "c")), CheatFile.parse(text))
    }

    // RetroArch "handler 1" cheats: a RAM patch the frontend applies, no code. The FBNeo shape: quoted
    // numbers, the count line last.
    private val ramCheats = """
        cheat0_desc = "Inf energy P1"
        cheat0_handler = "1"
        cheat0_address = "4608"
        cheat0_value = "0x40"
        cheat0_memory_search_size = "3"
        cheat0_cheat_type = "1"
        cheat0_enable = "true"

        cheat1_desc = "Score"
        cheat1_handler = 1
        cheat1_address = 16
        cheat1_value = 4660
        cheat1_memory_search_size = 4
        cheat1_cheat_type = 1
        cheat1_big_endian = "true"
        cheat1_address_bit_position = 255
        cheat1_repeat_count = 3
        cheat1_repeat_add_to_value = 1
        cheat1_repeat_add_to_address = 2

        cheat2_desc = "Handler 0 without a code"
        cheat2_handler = 0

        cheats = "3"
    """.trimIndent()

    @Test fun ram_patch_cheats_parse_with_the_count_line_last() {
        assertEquals(
            listOf(
                Cheat("Inf energy P1", "", mem = MemoryPatch(address = 4608, value = 0x40, size = 3, type = 1)),
                Cheat(
                    "Score", "",
                    mem = MemoryPatch(
                        address = 16, value = 4660, size = 4, type = 1, bitMask = 255, bigEndian = true,
                        repeatCount = 3, repeatAddToValue = 1, repeatAddToAddress = 2,
                    ),
                ),
            ),
            CheatFile.parse(ramCheats),   // a quoted enable = "true" stays off; a handler-0 cheat with no code is dropped
        )
    }

    @Test fun ram_patch_defaults_follow_retroarch() {
        val text = "cheats = 1\ncheat0_desc = \"d\"\ncheat0_handler = 1\n"
        assertEquals(MemoryPatch(), CheatFile.parse(text)!![0].mem)
        assertEquals(1, MemoryPatch().repeatCount)
        assertEquals(1, MemoryPatch().repeatAddToAddress)
    }

    @Test fun numbers_read_like_strtoul_base_0() {
        fun value(v: String) = CheatFile.parse("cheats = 1\ncheat0_desc = \"d\"\ncheat0_handler = 1\ncheat0_value = $v\n")!![0].mem!!.value
        assertEquals(255L, value("0xFF"))
        assertEquals(8L, value("010"))                // octal, as RetroArch reads it
        assertEquals(12L, value("\"12\""))
        assertEquals(4294967295L, value("4294967295"))
        assertEquals(0L, value("x1"))                 // not a number: the field keeps its default
    }

    @Test fun a_code_cheat_has_no_memory_patch() {
        assertNull(CheatFile.parse("cheats = 1\ncheat0_desc = \"d\"\ncheat0_code = \"01\"\n")!![0].mem)
    }
}
