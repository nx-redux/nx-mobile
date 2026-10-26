package com.nxredux.mobile.ui.nx

import androidx.compose.ui.geometry.Offset
import com.nxredux.mobile.layout.overlayHeightPx
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    /** The overlay's C-stick on a band [w] x [h] units: in the middle row, centred between the clusters, clear of their boxes. */
    private fun assertCStickFits(w: Float, h: Float, minWidth: Float) {
        val s = midRowScale(w, h)
        val f = faceClusterScale(w, h, s, 3f, 3f)
        val row = w - 2f * OVERLAY_EDGE_NUDGE
        val box = overlayCStick(row, s, f)
        val what = "band $w x $h"
        assertEquals((3f * s + row - 3f * f) / 2f, box.center.x, 1e-4f, "$what: centred between the clusters")
        assertEquals(box.width, box.height, 1e-4f)
        assertTrue(box.top >= 0f && box.bottom <= 3f * s + 1e-4f, "$what: inside the middle row's height")
        assertTrue(box.width <= C_STICK_DIAMETER * s + 1e-4f, "$what: at most full size")
        assertTrue(box.width >= minWidth - 1e-3f, "$what: ${box.width} wide, at least $minWidth")
        // Its well keeps 0.1 off both clusters' whole touch boxes (the d-pad takes touches across its square).
        val r = box.width / 2f
        for (b in overlayClusterBoxes(row, s, f)) assertTrue(b.distanceTo(box.center) >= r + PORTRAIT_C_STICK_BUTTON_GAP - 1e-3f, "$what: box $b")
    }

    @Test fun c_stick_is_full_size_on_the_fold_cover_band() {
        // The Fold's cover (1248 px wide): 9 x 6.91 units, the clusters at 1.13 and 1.82 units apart.
        assertCStickFits(9f, 6.91f, minWidth = C_STICK_DIAMETER * 1.13f)
    }

    @Test fun c_stick_on_the_narrowest_band_at_the_largest_scale_fits_the_gap() {
        // 9 units wide, tall enough that the width bounds the scale (1.233): the clusters only 1.2 units apart, so the
        // well is that gap less 0.1 each side (1.0 across, full size would be 1.48).
        assertEquals(1.2333f, midRowScale(9f, 7.4f), 1e-3f)
        assertCStickFits(9f, 7.4f, minWidth = 1.0f)
    }

    @Test fun c_stick_fits_every_band() {
        // The gap between the clusters is at least 1.2 units on every band 9 or more wide: the well at least 1.0.
        for (w in listOf(9f, 9.5f, 10f, 11f, 13.05f, 16f)) for (h in listOf(6.2f, 6.91f, 7.4f, 9f)) assertCStickFits(w, h, minWidth = 1.0f)
    }

    @Test fun c_stick_sits_at_the_row_top_and_takes_only_touches_in_its_well() {
        val s = midRowScale(9f, 7.4f)
        val box = overlayCStick(9f - 2f * OVERLAY_EDGE_NUDGE, s, s)
        assertEquals(0f, box.top, 1e-4f)
        assertTrue(cStickWellHit(box, box.center) && !cStickWellHit(box, Offset(box.left + 0.01f, box.bottom - 0.01f)))
    }
}
