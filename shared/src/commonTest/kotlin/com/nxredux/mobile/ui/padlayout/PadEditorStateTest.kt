package com.nxredux.mobile.ui.padlayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.nxredux.mobile.settings.GroupTweak
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PadEditorStateTest {
    private val pad = Rect(0f, 600f, 1000f, 1000f)          // the band, root px
    private fun state(initial: PadLayout = PadLayout.EMPTY) = PadEditorState(initial).apply {
        onGroup(PadGroup.DPAD, Rect(100f, 750f, 250f, 900f), pad, 50f, Rect(100f, 750f, 250f, 900f))
        onGroup(PadGroup.FACE, Rect(750f, 750f, 900f, 900f), pad, 50f, Rect(750f, 750f, 900f, 900f))
    }

    @Test fun a_tap_on_a_group_selects_it_and_empty_space_clears() {
        val s = state()
        assertEquals(PadGroup.FACE, s.groupAt(Offset(800f, 800f)))
        assertNull(s.groupAt(Offset(500f, 800f)))
        s.select(PadGroup.FACE)
        assertEquals(PadGroup.FACE, s.selected)
        s.select(null)
        assertNull(s.selected)
    }

    @Test fun dragging_moves_in_units_and_stops_at_the_pad_edge() {
        val s = state()
        s.dragBy(PadGroup.DPAD, Offset(100f, -50f))
        assertEquals(GroupTweak(dx = 2f, dy = -1f), s.layout.tweaks[PadGroup.DPAD])
        assertEquals(Rect(200f, 700f, 350f, 850f), s.rectOf(PadGroup.DPAD))
        s.dragBy(PadGroup.DPAD, Offset(-5000f, 0f))
        assertEquals(Rect(0f, 700f, 150f, 850f), s.rectOf(PadGroup.DPAD))
        assertTrue(s.dirty)
    }

    @Test fun a_drag_before_the_pad_has_a_unit_is_ignored() {
        val s = PadEditorState(PadLayout.EMPTY).apply { onGroup(PadGroup.DPAD, Rect(100f, 750f, 250f, 900f), pad, 0f, Rect(100f, 750f, 250f, 900f)) }
        s.dragBy(PadGroup.DPAD, Offset(100f, 0f))
        assertEquals(PadLayout.EMPTY, s.layout)
    }

    @Test fun ending_a_drag_near_home_snaps_back() {
        val s = state()
        s.dragBy(PadGroup.DPAD, Offset(10f, 5f))             // 0.2, 0.1 units
        s.endDrag(PadGroup.DPAD)
        assertNull(s.layout.tweaks[PadGroup.DPAD])
        assertFalse(s.dirty)
    }

    @Test fun size_steps_by_five_percent_within_range() {
        val s = state()
        s.select(PadGroup.FACE)
        s.setScale(1.23f)
        assertEquals(1.25f, s.scaleOf(PadGroup.FACE), 1e-4f)
        s.setScale(3f)
        assertEquals(1.5f, s.scaleOf(PadGroup.FACE), 1e-4f)
        s.setScale(0.1f)
        assertEquals(0.7f, s.scaleOf(PadGroup.FACE), 1e-4f)
    }

    @Test fun reset_group_and_reset_all() {
        val s = state(PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dx = 1f), PadGroup.C_STICK to GroupTweak(dy = 1f))))
        s.select(PadGroup.DPAD)
        s.resetGroup()
        assertEquals(setOf(PadGroup.C_STICK), s.layout.tweaks.keys)     // a group this pad lacks is kept
        s.resetAll()
        assertEquals(PadLayout.EMPTY, s.layout)
    }

    @Test fun overlapping_groups_are_reported() {
        val s = state()
        assertEquals(emptySet(), s.overlapping())
        s.dragBy(PadGroup.DPAD, Offset(600f, 0f))
        assertEquals(setOf(PadGroup.DPAD, PadGroup.FACE), s.overlapping())
    }

    @Test fun cancel_asks_only_with_changes() {
        val clean = state()
        assertTrue(clean.requestCancel())
        val s = state()
        s.dragBy(PadGroup.DPAD, Offset(100f, 0f))
        assertFalse(s.requestCancel())
        assertTrue(s.confirmDiscard)
        s.keepEditing()
        assertFalse(s.confirmDiscard)
        assertFalse(s.requestCancel())
        assertFalse(s.requestCancel())                         // a second Cancel keeps the prompt (only Discard leaves)
        assertTrue(s.confirmDiscard)
    }

    @Test fun untweaked_groups_that_overlap_by_default_are_not_flagged() {
        val s = PadEditorState(PadLayout.EMPTY).apply {
            onGroup(PadGroup.DPAD, Rect(100f, 750f, 250f, 900f), pad, 50f, Rect(100f, 750f, 250f, 900f))
            onGroup(PadGroup.FACE, Rect(750f, 750f, 900f, 900f), pad, 50f, Rect(750f, 750f, 900f, 900f))
            onGroup(PadGroup.C_STICK, Rect(700f, 700f, 800f, 800f), pad, 50f, Rect(700f, 700f, 800f, 800f))   // covers FACE's corner, as today's pad does
        }
        assertEquals(emptySet(), s.overlapping())
        s.dragBy(PadGroup.DPAD, Offset(600f, 0f))                 // DPAD now sits on FACE and the C-stick
        assertEquals(setOf(PadGroup.DPAD, PadGroup.FACE, PadGroup.C_STICK), s.overlapping())
    }

    @Test fun the_resize_handle_is_the_corner_nearest_the_middle() {
        val r = Rect(100f, 200f, 300f, 400f)
        assertEquals(Offset(300f, 200f), resizeHandle(r, Offset(1000f, 0f)))     // a group low and left: top right
        assertEquals(Offset(100f, 400f), resizeHandle(r, Offset(0f, 1000f)))
    }

    @Test fun where_groups_overlap_the_one_drawn_on_top_is_picked() {
        val s = state()
        s.onGroup(PadGroup.SYSTEM_LEFT, Rect(100f, 750f, 250f, 900f), pad, 50f, Rect(100f, 750f, 250f, 900f))   // MENU's row moved over the d-pad
        s.onGroup(PadGroup.L_SHOULDERS, Rect(700f, 700f, 950f, 950f), pad, 50f, Rect(700f, 700f, 950f, 950f))   // shoulders under the face buttons
        assertEquals(PadGroup.DPAD, s.groupAt(Offset(150f, 800f)))
        assertEquals(PadGroup.FACE, s.groupAt(Offset(800f, 800f)))
        assertEquals(PadGroup.L_SHOULDERS, s.groupAt(Offset(720f, 720f)))
    }

    @Test fun shrinking_back_after_an_edge_pushed_a_group_in_returns_it() {
        val s = state()
        s.dragBy(PadGroup.DPAD, Offset(-100f, 0f))                                // flush with the left edge
        val before = s.rectOf(PadGroup.DPAD)
        s.select(PadGroup.DPAD)
        s.beginResize()
        s.setScale(1.5f)                                                           // grows: the edge pushes it in
        s.setScale(1f)
        s.endResize()
        assertEquals(before, s.rectOf(PadGroup.DPAD))
    }
}
