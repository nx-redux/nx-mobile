package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class HelpFlowTest {
    // A break inside a sentence (wrapped by hand for a narrower note) becomes a space; the card wraps the words.
    @Test fun aBreakInsideASentenceFlows() {
        assertEquals(
            "The controller the game sees.\nAuto picks it from the game (GameTDB), preferring the Classic Controller when the game takes one.",
            helpFlow("The controller the game sees.\nAuto picks it from the game (GameTDB),\npreferring the Classic Controller when the game takes one."),
        )
        assertEquals("the see-through pad over the game", helpFlow("the see-through\npad over the game"))
    }

    // After a sentence, a colon, a blank line or at the end, the break stays: lists keep a line per item.
    @Test fun sentenceAndListBreaksStay() {
        assertEquals("Stacked: one above the other.\nSingle screen: the top one only.\n", helpFlow("Stacked: one above the other.\nSingle screen: the top one only.\n"))
        assertEquals("Values:\nAuto", helpFlow("Values:\nAuto"))
        assertEquals("One\n\nTwo", helpFlow("One\n\nTwo"))
        assertEquals("Is it on?\nYes!\nGood", helpFlow("Is it on?\nYes!\nGood"))
    }
}
