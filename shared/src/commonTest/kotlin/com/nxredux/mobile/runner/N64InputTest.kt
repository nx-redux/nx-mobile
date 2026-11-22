package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class N64InputTest {
    private val A = 96; private val B = 97; private val X = 99; private val Y = 100
    private val L1 = 102; private val R1 = 103; private val L2 = 104; private val R2 = 105
    private val START = 108; private val SELECT = 109
    private val ORIGINAL = -1   // a marker: "whatever the caller would have sent"

    @Test fun appliesTo_only_n64() {
        assertTrue(N64Input.appliesTo("N64"))
        assertTrue(N64Input.appliesTo(" n64 "))
        for (tag in listOf("DC", "PS", "SFC", "NDS", "GBA", "")) assertFalse(N64Input.appliesTo(tag), tag)
    }

    @Test fun face_buttons_by_name() {
        // mupen64plus_next with mupen64plus-alt-map: JOYPAD_B = N64 A, JOYPAD_Y = N64 B.
        assertEquals(B, N64Input.toRunner(NxButton.A, ORIGINAL))
        assertEquals(Y, N64Input.toRunner(NxButton.B, ORIGINAL))
    }

    @Test fun c_buttons_from_the_pad_and_from_physical_x_y() {
        assertEquals(X, N64Input.toRunner(NxButton.C_UP, ORIGINAL))      // JOYPAD_X = C-Up
        assertEquals(A, N64Input.toRunner(NxButton.C_DOWN, ORIGINAL))    // JOYPAD_A = C-Down
        assertEquals(L1, N64Input.toRunner(NxButton.C_LEFT, ORIGINAL))   // JOYPAD_L = C-Left
        assertEquals(R1, N64Input.toRunner(NxButton.C_RIGHT, ORIGINAL))  // JOYPAD_R = C-Right
        assertEquals(A, N64Input.toRunner(NxButton.Y, ORIGINAL))         // physical Y = C-Down (NX Redux)
        assertEquals(L1, N64Input.toRunner(NxButton.X, ORIGINAL))        // physical X = C-Left (NX Redux)
    }

    @Test fun shoulders_and_start() {
        assertEquals(L2, N64Input.toRunner(NxButton.L2, ORIGINAL))       // Z
        assertEquals(SELECT, N64Input.toRunner(NxButton.L1, ORIGINAL))   // N64 L = JOYPAD_SELECT
        assertEquals(R2, N64Input.toRunner(NxButton.R1, ORIGINAL))       // N64 R = JOYPAD_R2
        assertEquals(START, N64Input.toRunner(NxButton.START, ORIGINAL))
    }

    @Test fun r2_and_select_send_nothing() {
        assertNull(N64Input.toRunner(NxButton.R2, R2))
        assertNull(N64Input.toRunner(NxButton.SELECT, SELECT))
    }

    @Test fun directions_and_menu_pass_through() {
        for (b in listOf(NxButton.UP, NxButton.DOWN, NxButton.LEFT, NxButton.RIGHT, NxButton.MENU))
            assertEquals(42, N64Input.toRunner(b, 42), b.name)
    }

    @Test fun every_sent_keycode_is_one_releaseAll_covers() {
        // GameInputRouter.releaseAllGameInput sends key-up for A B X Y L1 L2 R1 R2 START SELECT MENU's own keycodes.
        val released = setOf(A, B, X, Y, L1, L2, R1, R2, START, SELECT)
        for (b in NxButton.entries.filterNot { it.isDirection || it == NxButton.MENU }) {
            val sent = N64Input.toRunner(b, ORIGINAL) ?: continue
            assertTrue(sent in released, "${b.name} sends $sent")
        }
    }
}
