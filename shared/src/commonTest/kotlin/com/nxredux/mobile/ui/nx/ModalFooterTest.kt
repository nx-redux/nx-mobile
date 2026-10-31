package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class ModalFooterTest {
    @Test fun hints_when_a_controller_or_the_pad_is_present_buttons_otherwise() {
        assertEquals(FooterMode.HINTS, footerModeFor(controllerConnected = true, padVisible = false))
        assertEquals(FooterMode.HINTS, footerModeFor(controllerConnected = false, padVisible = true))
        assertEquals(FooterMode.HINTS, footerModeFor(controllerConnected = true, padVisible = true))
        assertEquals(FooterMode.BUTTONS, footerModeFor(controllerConnected = false, padVisible = false))
    }

    @Test fun the_menu_shows_back_and_select_hints_and_never_buttons() {
        assertEquals(ModalFooter.None, modalFooter(ModalKind.MENU, FooterMode.HINTS))   // its rows are the actions: A selects, B goes back, no hints
        assertEquals(ModalFooter.None, modalFooter(ModalKind.MENU, FooterMode.BUTTONS))
    }

    @Test fun prompt_and_confirm_show_cancel_and_confirm_as_hints_or_as_two_buttons() {
        for (kind in listOf(ModalKind.PROMPT, ModalKind.CONFIRM)) {
            assertEquals(ModalFooter.Hints(listOf(Hint(NxButton.B, "Cancel"), Hint(NxButton.A, "OK"))), modalFooter(kind, FooterMode.HINTS), "$kind")
            assertEquals(
                ModalFooter.Buttons(listOf(FooterAction(NxButton.B, "Cancel", primary = false), FooterAction(NxButton.A, "Copy", primary = true))),
                modalFooter(kind, FooterMode.BUTTONS, confirmLabel = "Copy"), "$kind",
            )
        }
    }

    @Test fun info_shows_one_confirm_hint_or_one_primary_button() {
        assertEquals(ModalFooter.Hints(listOf(Hint(NxButton.A, "OK"))), modalFooter(ModalKind.INFO, FooterMode.HINTS))
        assertEquals(ModalFooter.Buttons(listOf(FooterAction(NxButton.A, "OK", primary = true))), modalFooter(ModalKind.INFO, FooterMode.BUTTONS))
    }

    @Test fun wait_shows_only_cancel() {
        assertEquals(ModalFooter.Hints(listOf(Hint(NxButton.B, "Cancel"))), modalFooter(ModalKind.WAIT, FooterMode.HINTS))
        assertEquals(ModalFooter.Buttons(listOf(FooterAction(NxButton.B, "Cancel", primary = false))), modalFooter(ModalKind.WAIT, FooterMode.BUTTONS))
    }

    // The footer as words, the confirm first, no key glyphs (A presses the lit one).
    @Test fun the_footer_reads_as_words_naming_their_keys() {
        assertEquals(
            listOf(FooterWord("OK", NxButton.A), FooterWord("Cancel", NxButton.B)),
            footerWords(modalFooter(ModalKind.CONFIRM, FooterMode.HINTS)),
        )
        assertEquals(
            listOf(FooterWord("OK", NxButton.A), FooterWord("Cancel", NxButton.B)),
            footerWords(modalFooter(ModalKind.CONFIRM, FooterMode.BUTTONS)),
        )
        val scroll = ModalFooter.Hints(listOf(Hint(NxButton.DOWN, "Scroll"), Hint(NxButton.B, "Cancel"), Hint(NxButton.A, "OK")))
        assertEquals(listOf("OK", "Cancel", "Scroll"), footerWords(scroll).map { it.label })
        assertEquals(listOf(NxButton.A, NxButton.B, NxButton.DOWN), footerWords(scroll).map { it.button })
        assertEquals(emptyList(), footerWords(ModalFooter.None))
    }

    @Test fun busy_shows_no_footer() {
        assertEquals(ModalFooter.None, modalFooter(ModalKind.BUSY, FooterMode.HINTS))
        assertEquals(ModalFooter.None, modalFooter(ModalKind.BUSY, FooterMode.BUTTONS))
    }
}
