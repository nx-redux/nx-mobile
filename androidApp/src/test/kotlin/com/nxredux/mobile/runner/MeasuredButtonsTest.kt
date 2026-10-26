package com.nxredux.mobile.runner

import androidx.compose.ui.geometry.Rect
import com.nxredux.mobile.settings.PadGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The landscape pad's measured width: the wider half's drawn groups, from the content edge, less the edge nudge. */
class MeasuredButtonsTest {
    private val unit = 100f   // nudge 20 px
    private val leftPad = Rect(40f, 0f, 600f, 700f)    // content box inside the left half's padding
    private val rightPad = Rect(40f, 0f, 600f, 700f)

    @Test fun waits_for_both_halves() {
        val m = MeasuredButtons()
        m.onLeft(PadGroup.DPAD, Rect(60f, 300f, 360f, 600f), leftPad, unit)
        assertNull(m.widthPx())
        assertFalse(m.accept())
    }

    @Test fun wider_half_counts_for_both() {
        val m = MeasuredButtons()
        m.onLeft(PadGroup.DPAD, Rect(60f, 300f, 360f, 600f), leftPad, unit)          // 360 - 40 - 20 = 300
        m.onLeft(PadGroup.SYSTEM_LEFT, Rect(60f, 620f, 380f, 690f), leftPad, unit)   // 320
        m.onRight(PadGroup.FACE, Rect(200f, 300f, 580f, 600f), rightPad, unit)        // 600 - 200 - 20 = 380
        assertEquals(380f, m.widthPx()!!, 0.01f)
        assertTrue(m.accept())
        assertEquals(380f, m.accepted!!, 0.01f)
    }

    // A group the editor moves further in widens the pad; one that leaves (Rect.Zero) no longer counts.
    @Test fun follows_moves_and_forgets_removed_groups() {
        val m = MeasuredButtons()
        m.onLeft(PadGroup.DPAD, Rect(60f, 300f, 360f, 600f), leftPad, unit)
        m.onRight(PadGroup.FACE, Rect(300f, 300f, 580f, 600f), rightPad, unit)        // 280
        m.onRight(PadGroup.SYSTEM_RIGHT, Rect(100f, 620f, 580f, 690f), rightPad, unit) // 480
        assertEquals(480f, m.widthPx()!!, 0.01f)
        m.onRight(PadGroup.SYSTEM_RIGHT, Rect.Zero, rightPad, unit)
        assertEquals(300f, m.widthPx()!!, 0.01f)
    }

    @Test fun accepts_only_a_change_of_two_px_or_more() {
        val m = MeasuredButtons()
        m.onLeft(PadGroup.DPAD, Rect(60f, 300f, 360f, 600f), leftPad, unit)
        m.onRight(PadGroup.FACE, Rect(300f, 300f, 580f, 600f), rightPad, unit)
        assertTrue(m.accept())
        m.onLeft(PadGroup.DPAD, Rect(60f, 300f, 361.5f, 600f), leftPad, unit)
        assertFalse(m.accept())
        assertEquals(300f, m.accepted!!, 0.01f)
        m.onLeft(PadGroup.DPAD, Rect(60f, 300f, 362f, 600f), leftPad, unit)
        assertTrue(m.accept())
        assertEquals(302f, m.accepted!!, 0.01f)
    }
}
