package com.nxredux.mobile.runner

import com.nxredux.mobile.runner.PadFaceLayout.NINTENDO
import com.nxredux.mobile.runner.PadFaceLayout.XBOX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Controller Layout names the controller's printed letters: buttons work as printed. A pad's keycodes sit
 * where its detected layout says (an Xbox-layout pad's bottom key is BUTTON_A, a Nintendo-layout pad's BUTTON_B); the
 * setting says which letter is printed there.
 */
class DcInputTest {
    private val a = PhysicalKeys.BUTTON_A
    private val b = PhysicalKeys.BUTTON_B
    private val x = PhysicalKeys.BUTTON_X
    private val y = PhysicalKeys.BUTTON_Y

    private val letterTags = listOf("SFC", "SUPA", "FC", "FDS", "GB", "GBC", "GBA", "MGBA", "SGB", "NDS", "3DS", "VB", "LYNX", "PKM", "WSC", "N64", "WII", "GC")
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

    @Test fun the_printed_letter_is_the_key_when_the_label_matches_the_pad_else_swapped() {
        for (code in -1..400) {
            assertEquals(code, PhysicalKeys.printed(code, XBOX, XBOX), "keycode $code")
            assertEquals(code, PhysicalKeys.printed(code, NINTENDO, NINTENDO), "keycode $code")
            assertEquals(code, PhysicalKeys.printed(code, null, NINTENDO), "keycode $code")
            assertEquals(code, PhysicalKeys.printed(code, XBOX, null), "keycode $code")
        }
        assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.printed(it, XBOX, NINTENDO) })
        assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.printed(it, NINTENDO, XBOX) })
    }

    // Auto with an Xbox pad (detected XBOX, printed XBOX): A is the console's A on SNES, GBA, DS, 3DS (and N64 by name).
    @Test fun letter_consoles_get_the_printed_letter() {
        for (tag in letterTags) {
            assertEquals(listOf(a, b, x, y), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, XBOX, XBOX) }, tag)
            assertEquals(listOf(a, b, x, y), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, NINTENDO, NINTENDO) }, tag)
            // The other label: an Xbox pad set to Nintendo has the console's own positions (bottom = B).
            assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, XBOX, NINTENDO) }, tag)
            assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, NINTENDO, XBOX) }, tag)
        }
    }

    // flycast reads JOYPAD_B as the Dreamcast's A, JOYPAD_A as B, JOYPAD_Y as X, JOYPAD_X as Y; NGP's A is JOYPAD_B.
    @Test fun dc_and_ngp_get_the_printed_letter_through_their_core_swap() {
        for (tag in swappedTags) {
            // Xbox pad, Auto: printed A (bottom) is DC A (bottom).
            assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, XBOX, XBOX) }, tag)
            // Nintendo pad, Auto: printed A (right) is DC A too.
            assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, NINTENDO, NINTENDO) }, tag)
            // The other label swaps the letters back.
            assertEquals(listOf(a, b, x, y), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, XBOX, NINTENDO) }, tag)
            assertEquals(listOf(a, b, x, y), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, NINTENDO, XBOX) }, tag)
        }
    }

    // PlayStation symbols, Sega rows, 1/2, I/II and arcade buttons go by position whatever the label setting.
    @Test fun positional_consoles_follow_the_pads_key_positions_only() {
        for (tag in positionTags) for (label in listOf(XBOX, NINTENDO, null)) {
            assertEquals(listOf(b, a, y, x), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, XBOX, label) }, "$tag $label")
            assertEquals(listOf(a, b, x, y), listOf(a, b, x, y).map { PhysicalKeys.toRunner(tag, it, NINTENDO, label) }, "$tag $label")
        }
    }

    @Test fun no_device_is_sent_as_is_and_other_keys_pass_through() {
        for (tag in letterTags + swappedTags + positionTags) for (code in -1..400) {
            assertEquals(code, PhysicalKeys.toRunner(tag, code, null, XBOX), "$tag keycode $code")
            if (code !in listOf(a, b, x, y)) for (d in listOf(XBOX, NINTENDO)) for (p in listOf(XBOX, NINTENDO))
                assertEquals(code, PhysicalKeys.toRunner(tag, code, d, p), "$tag $d $p keycode $code")
        }
    }

    @Test fun the_mapping_is_its_own_inverse_so_down_and_up_match() {
        for (tag in letterTags + swappedTags + positionTags) for (d in listOf(XBOX, NINTENDO)) for (p in listOf(XBOX, NINTENDO))
            for (code in listOf(a, b, x, y)) assertEquals(code, PhysicalKeys.toRunner(tag, PhysicalKeys.toRunner(tag, code, d, p), d, p))
    }

}
