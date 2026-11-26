package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals

/** What the app types into a core's open text entry (the Wii U software keyboard) after its own text box's OK. */
class CoreTextInputTest {
    private fun chars(s: String) = s.map { CoreKey(0, it.code) }
    private val enter = CoreKey(CoreTextInput.RETROK_RETURN, 0)
    private val backspace = CoreKey(CoreTextInput.RETROK_BACKSPACE, 0)

    @Test
    fun the_typed_text_goes_in_then_enter() {
        assertEquals(chars("Link") + enter, CoreTextInput.keys(initial = "", answer = "Link"))
    }

    @Test
    fun the_core_s_initial_text_is_cleared_first() {
        assertEquals(List(3) { backspace } + chars("Zelda") + enter, CoreTextInput.keys(initial = "abc", answer = "Zelda"))
    }

    @Test
    fun a_character_never_reads_as_a_key_of_its_own() {
        // U+010F is RETROK_KP_ENTER's number: sent as a key code it would accept the entry.
        assertEquals(listOf(CoreKey(0, 0x10F), enter), CoreTextInput.keys(initial = "", answer = "\u010F"))
    }

    @Test
    fun nothing_typed_answers_with_the_account_name() {
        assertEquals("Mii", CoreTextInput.answer(typed = "", name = "Mii", maxLength = 10))
        assertEquals("Mii", CoreTextInput.answer(typed = "   ", name = "Mii", maxLength = 10))
        assertEquals("", CoreTextInput.answer(typed = "", name = "", maxLength = 10))
    }

    @Test
    fun the_typed_text_is_kept_as_typed_inside_the_core_s_length() {
        assertEquals("Link Jr", CoreTextInput.answer(typed = "Link Jr", name = "Mii", maxLength = 10))
        assertEquals("Linkster", CoreTextInput.answer(typed = "Linksters!", name = "Mii", maxLength = 8))
        assertEquals("Linksters!", CoreTextInput.answer(typed = "Linksters!", name = "Mii", maxLength = 0))   // 0: no limit given
    }

    @Test
    fun line_breaks_controls_and_characters_past_the_core_s_16_bits_are_dropped() {
        assertEquals("ab c", CoreTextInput.answer(typed = "a\nb\t c\u0007", name = "", maxLength = 0))
        assertEquals("Link", CoreTextInput.answer(typed = "Li😀nk", name = "", maxLength = 0))
    }

    @Test
    fun the_name_is_cut_to_the_core_s_length_too() {
        assertEquals("Mohamm", CoreTextInput.answer(typed = "", name = "Mohammad", maxLength = 6))
    }
}
