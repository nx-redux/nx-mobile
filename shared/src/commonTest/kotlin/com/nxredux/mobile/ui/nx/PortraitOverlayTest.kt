package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.layout.overlayHeightPx
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlin.test.Test
import kotlin.test.assertEquals

class PortraitOverlayTest {
    @Test fun overlay_height_matches_model() {
        // The composable's rows (shoulders, gap, middle row at the band's scale, gap, system row) add up to the layout
        // model's overlay height for a wide band (scale 1.25), a tight one (scale 1) and one between.
        for ((w, h) in listOf(20f to 20f, 9f to 6.2f, 9.5f to 7.4f)) {
            val unit = 37.5f
            val density = 2.625f
            val rows = portraitOverlayUnits(midRowScale(w, h))
            val padding = 2f * CLUSTER_PADDING * density
            assertEquals(overlayHeightPx(unit, w, h, density), rows * unit + padding, 0.01f, "band $w x $h")
        }
    }

    @Test fun bottom_row_shows_only_assigned_shortcuts() {
        val ff = TouchShortcut(ShortcutAction.TOGGLE_FF, false)
        assertEquals(listOf(PortraitBottomPiece.SYSTEM), portraitBottomRow(emptyMap(), false))
        assertEquals(
            listOf(PortraitBottomPiece.SHORTCUT_1, PortraitBottomPiece.SYSTEM),
            portraitBottomRow(mapOf(TouchSlot.ONE to ff), false),
        )
        assertEquals(
            listOf(PortraitBottomPiece.SYSTEM, PortraitBottomPiece.SHORTCUT_2),
            portraitBottomRow(mapOf(TouchSlot.TWO to ff), false),
        )
        assertEquals(
            listOf(PortraitBottomPiece.SHORTCUT_1, PortraitBottomPiece.SYSTEM, PortraitBottomPiece.SHORTCUT_2),
            portraitBottomRow(emptyMap(), true),
        )
    }
}
