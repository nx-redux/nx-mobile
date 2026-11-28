package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A pad's face keycodes are its letters (BUTTON_A is A wherever it sits); each console takes them by its [FaceMode]. */
class DcInputTest {
    private val a = PhysicalKeys.BUTTON_A
    private val b = PhysicalKeys.BUTTON_B
    private val x = PhysicalKeys.BUTTON_X
    private val y = PhysicalKeys.BUTTON_Y

    private val letterTags = listOf("SFC", "SUPA", "FC", "FDS", "GB", "GBC", "GBA", "MGBA", "SGB", "NDS", "3DS", "VB", "LYNX", "PKM", "WSC", "N64", "WII", "WIIU", "GC")
    private val swappedTags = listOf("DC", "NGP", "NGPC")
    private val positionTags = listOf("PS", "PSP", "MD", "GPGX", "SEGACD", "32X", "SMS", "GG", "SG1000", "PCE", "P8", "A2600", "A5200",
        "A7800", "COLECO", "PRBOOM", "FBN", "", "UNKNOWN")

    @Test fun applies_to_the_dc_tag_only() {
        assertTrue(DcInput.appliesTo("DC"))
        assertTrue(DcInput.appliesTo(" dc "))
        assertFalse(DcInput.appliesTo("PS"))
        assertFalse(DcInput.appliesTo("NDS"))
        assertFalse(DcInput.appliesTo("FBN"))
    }

    @Test fun face_keycodes_are_androids() {
        assertEquals(listOf(96, 97, 99, 100), listOf(a, b, x, y))
    }

    @Test fun each_tag_has_its_face_mode() {
        for (t in letterTags) assertEquals(FaceMode.LETTERS, PhysicalKeys.modeFor(t), t)
        for (t in swappedTags) assertEquals(FaceMode.LETTERS_SWAPPED, PhysicalKeys.modeFor(t), t)
        for (t in positionTags) assertEquals(FaceMode.POSITIONS, PhysicalKeys.modeFor(t), t)
        assertEquals(FaceMode.LETTERS, PhysicalKeys.modeFor(" sfc "))
    }

    @Test fun to_runner_by_face_mode() {
        assertEquals(96, PhysicalKeys.toRunner("SFC", 96))
        assertEquals(97, PhysicalKeys.toRunner("DC", 96))
        assertEquals(97, PhysicalKeys.toRunner("PS", 96))   // A = ✕ = RetroPad B
        assertEquals(96, PhysicalKeys.toRunner("PS", 97))
        assertEquals(100, PhysicalKeys.toRunner("PS", 99))
        assertEquals(97, PhysicalKeys.toRunner("MD", 96))
        assertEquals(108, PhysicalKeys.toRunner("PS", 108))
    }

    @Test fun letter_consoles_get_the_letter() {
        for (tag in letterTags) assertEquals(listOf(a, b, x, y), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it) }, tag)
    }

    // flycast reads JOYPAD_B as the Dreamcast's A, JOYPAD_A as B, JOYPAD_Y as X, JOYPAD_X as Y; NGP's A is JOYPAD_B.
    @Test fun dc_and_ngp_get_the_letter_through_their_core_swap() {
        for (tag in swappedTags) assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it) }, tag)
    }

    // The pad's bottom key (BUTTON_A) is the console's bottom button, RetroPad B.
    @Test fun positional_consoles_go_by_position() {
        for (tag in positionTags) assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it) }, tag)
    }

    @Test fun other_keys_pass_through() {
        for (tag in letterTags + swappedTags + positionTags) for (code in -1..400)
            if (code !in listOf(a, b, x, y)) assertEquals(code, PhysicalKeys.toRunner(tag, code), "$tag keycode $code")
    }

    @Test fun the_mapping_is_its_own_inverse_so_down_and_up_match() {
        for (tag in letterTags + swappedTags + positionTags)
            for (code in listOf(a, b, x, y)) assertEquals(code, PhysicalKeys.toRunner(tag, PhysicalKeys.toRunner(tag, code)))
    }

}
