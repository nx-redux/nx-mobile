package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.ScreenClass.NEAR_SQUARE_TABLET
import com.nxredux.mobile.layout.ScreenClass.REGULAR_PHONE
import com.nxredux.mobile.layout.ScreenClass.STUBBY_PHONE
import com.nxredux.mobile.layout.ScreenClass.WIDE_TABLET
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsLayoutChoice.LARGE
import com.nxredux.mobile.options.NdsLayoutChoice.PIP
import com.nxredux.mobile.options.NdsLayoutChoice.SIDE_BY_SIDE
import com.nxredux.mobile.options.NdsLayoutChoice.SINGLE
import com.nxredux.mobile.options.NdsLayoutChoice.STACKED
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PipCorner
import com.nxredux.mobile.options.PipOpacity
import com.nxredux.mobile.options.PipSize
import com.nxredux.mobile.options.PxRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The spaces below are what the pad rules leave the DS pair (see the Layout tests): the game area after the landscape
 * set-aside, or above the portrait band. The hidden-band spaces assume an overlay of about 1000 px (unfolded Fold, 1700 px
 * space), 700 px (Fold cover, 1300 px) and 650 px (Tab S10 FE, 1800 px).
 */
class NdsLayoutTest {
    private val margin = 20f

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.01f, "$what: expected $expected, got $actual")

    private fun rect(expected: PxRect, actual: PxRect, what: String = "") {
        near(expected.x, actual.x, "$what x"); near(expected.y, actual.y, "$what y")
        near(expected.width, actual.width, "$what width"); near(expected.height, actual.height, "$what height")
    }

    private fun assertInside(inner: PxRect, outer: PxRect, what: String) = assertTrue(
        inner.x >= outer.x - 0.01f && inner.y >= outer.y - 0.01f &&
            inner.x + inner.width <= outer.x + outer.width + 0.01f && inner.y + inner.height <= outer.y + outer.height + 0.01f,
        "$what: $inner not inside $outer",
    )

    private fun overlaps(a: PxRect, b: PxRect) =
        a.x < b.x + b.width - 0.01f && b.x < a.x + a.width - 0.01f && a.y < b.y + b.height - 0.01f && b.y < a.y + a.height - 0.01f

    private fun layout(
        w: Float, h: Float, choice: NdsLayoutChoice, landscape: Boolean, screen: ScreenClass = REGULAR_PHONE, s: NdsSettings = NdsSettings(),
    ) = NdsLayout.regions(PxRect(0f, 0f, w, h), choice, landscape, screen, s, margin)

    /** The scales of the regions, as width / 256 (a non-whole scale shows its fraction). */
    private fun scales(r: List<NdsRegion>) = r.map { it.dst.width / 256f }

    private fun assertScales(vararg expected: Float, actual: List<NdsRegion>) {
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { i, e -> near(e, scales(actual)[i], "scale $i") }
    }

    // --- The spec's table: Fold cover (stubby phone) ---

    @Test fun fold_cover_landscape_large_4x_2x() = assertScales(4f, 2f, actual = layout(1474f, 1248f, LARGE, true, STUBBY_PHONE))

    @Test fun fold_cover_landscape_stacked_3x() = assertScales(3f, 3f, actual = layout(1474f, 1248f, STACKED, true, STUBBY_PHONE))

    @Test fun fold_cover_landscape_single_and_pip_5x_the_most_that_fits() {
        assertScales(5f, actual = layout(1474f, 1248f, SINGLE, true, STUBBY_PHONE))
        near(5f, layout(1474f, 1248f, PIP, true, STUBBY_PHONE)[0].dst.width / 256f, "pip big")
    }

    @Test fun fold_cover_portrait_large_3x_touch_fills_to_374() {
        val r = layout(1248f, 950f, LARGE, false, STUBBY_PHONE)
        rect(PxRect(240f, 0f, 768f, 576f), r[0].dst, "top")
        near(374f, r[1].dst.height, "touch height")
        near(374f * 4f / 3f, r[1].dst.width, "touch width")
        near(576f, r[1].dst.y, "touch y")
        assertEquals(listOf(true, false), r.map { it.shaded })   // another size than the top: unshaded
        assertEquals(listOf(false, true), r.map { it.touch })
    }

    @Test fun fold_cover_portrait_stacked_2x_in_the_band_3x_hidden() {
        assertScales(2f, 2f, actual = layout(1248f, 950f, STACKED, false, STUBBY_PHONE))
        assertScales(3f, 3f, actual = layout(1248f, 1300f, STACKED, false, STUBBY_PHONE))
    }

    @Test fun fold_cover_portrait_single_4x() = assertScales(4f, actual = layout(1248f, 950f, SINGLE, false, STUBBY_PHONE))

    @Test fun fold_cover_portrait_side_by_side_2x() = assertScales(2f, 2f, actual = layout(1248f, 950f, SIDE_BY_SIDE, false, STUBBY_PHONE))

    // --- Unfolded Fold (near-square tablet) ---

    @Test fun unfolded_landscape_large_6x_3x() = assertScales(6f, 3f, actual = layout(1828f, 1848f, LARGE, true, NEAR_SQUARE_TABLET))

    @Test fun unfolded_landscape_stacked_4x() = assertScales(4f, 4f, actual = layout(1828f, 1848f, STACKED, true, NEAR_SQUARE_TABLET))

    @Test fun unfolded_landscape_single_6x_one_under_the_7x_that_fits() {
        assertScales(6f, actual = layout(1828f, 1848f, SINGLE, true, NEAR_SQUARE_TABLET))
        assertScales(7f, actual = layout(1828f, 1848f, SINGLE, false, NEAR_SQUARE_TABLET))   // portrait keeps the most
    }

    @Test fun unfolded_portrait_large_4x_2x() = assertScales(4f, 2f, actual = layout(1848f, 1238f, LARGE, false, NEAR_SQUARE_TABLET))

    @Test fun unfolded_portrait_stacked_3x_band_4x_hidden() {
        assertScales(3f, 3f, actual = layout(1848f, 1238f, STACKED, false, NEAR_SQUARE_TABLET))
        assertScales(4f, 4f, actual = layout(1848f, 1700f, STACKED, false, NEAR_SQUARE_TABLET))
    }

    @Test fun unfolded_portrait_single_6x() = assertScales(6f, actual = layout(1848f, 1238f, SINGLE, false, NEAR_SQUARE_TABLET))

    @Test fun unfolded_portrait_side_by_side_3x() = assertScales(3f, 3f, actual = layout(1848f, 1238f, SIDE_BY_SIDE, false, NEAR_SQUARE_TABLET))

    // --- Tab S10 FE (wide tablet) ---

    @Test fun tab_landscape_large_5x_2x() = assertScales(5f, 2f, actual = layout(1560f, 1440f, LARGE, true, WIDE_TABLET))

    @Test fun tab_landscape_stacked_3x() = assertScales(3f, 3f, actual = layout(1560f, 1440f, STACKED, true, WIDE_TABLET))

    @Test fun tab_landscape_single_5x() = assertScales(5f, actual = layout(1560f, 1440f, SINGLE, true, WIDE_TABLET))

    @Test fun tab_landscape_side_by_side_3x() = assertScales(3f, 3f, actual = layout(1560f, 1440f, SIDE_BY_SIDE, true, WIDE_TABLET))

    @Test fun tab_portrait_large_5x_3x() = assertScales(5f, 3f, actual = layout(1440f, 1579f, LARGE, false, WIDE_TABLET))

    @Test fun tab_portrait_hidden_large_5x_4x() = assertScales(5f, 4f, actual = layout(1440f, 1800f, LARGE, false, WIDE_TABLET))

    @Test fun tab_portrait_stacked_4x() = assertScales(4f, 4f, actual = layout(1440f, 1579f, STACKED, false, WIDE_TABLET))

    @Test fun tab_portrait_single_5x() = assertScales(5f, actual = layout(1440f, 1579f, SINGLE, false, WIDE_TABLET))

    @Test fun tab_portrait_side_by_side_2x() = assertScales(2f, 2f, actual = layout(1440f, 1579f, SIDE_BY_SIDE, false, WIDE_TABLET))

    // --- 1080x2400 phone ---

    @Test fun phone_landscape_large_3x_2x() = assertScales(3f, 2f, actual = layout(1780f, 1080f, LARGE, true))

    @Test fun phone_landscape_stacked_2x() = assertScales(2f, 2f, actual = layout(1780f, 1080f, STACKED, true))

    @Test fun phone_landscape_single_5x() = assertScales(5f, actual = layout(1780f, 1080f, SINGLE, true))

    @Test fun phone_landscape_side_by_side_3x() = assertScales(3f, 3f, actual = layout(1780f, 1080f, SIDE_BY_SIDE, true))

    @Test fun phone_portrait_large_4x_2x() = assertScales(4f, 2f, actual = layout(1080f, 1305f, LARGE, false))

    @Test fun phone_portrait_stacked_3x() = assertScales(3f, 3f, actual = layout(1080f, 1305f, STACKED, false))

    @Test fun phone_portrait_single_4x() = assertScales(4f, actual = layout(1080f, 1305f, SINGLE, false))

    @Test fun phone_portrait_side_by_side_2x() = assertScales(2f, 2f, actual = layout(1080f, 1305f, SIDE_BY_SIDE, false))

    // --- Placement and flags ---

    @Test fun large_centres_the_touch_screen_under_the_top_whatever_the_corner() {
        for (corner in PipCorner.entries) {
            val r = layout(1474f, 1248f, LARGE, true, STUBBY_PHONE, NdsSettings(corner = corner, size = PipSize.SMALL))
            // 1024 x 768 over 512 x 384: the pair is 1152 high, centred in 1474 x 1248.
            rect(PxRect(225f, 48f, 1024f, 768f), r[0].dst, "$corner top")
            rect(PxRect(481f, 816f, 512f, 384f), r[1].dst, "$corner touch")
        }
    }

    @Test fun large_swapped_puts_the_bottom_screen_on_top_and_the_touch_stays_on_the_bottom_screen() {
        val r = layout(1474f, 1248f, LARGE, true, STUBBY_PHONE, NdsSettings(big = NdsScreen.BOTTOM))
        assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), r.map { it.screen })
        assertEquals(listOf(true, false), r.map { it.touch })
    }

    @Test fun large_with_equal_scales_shades_both() {
        val r = layout(256f, 384f, LARGE, false)
        assertScales(1f, 1f, actual = r)
        assertEquals(listOf(true, true), r.map { it.shaded })
    }

    @Test fun the_pair_is_centred_in_a_space_that_does_not_start_at_the_origin() {
        val r = NdsLayout.regions(PxRect(100f, 50f, 1100f, 400f), SIDE_BY_SIDE, true, REGULAR_PHONE, NdsSettings(), margin)
        // 2x: 1024 x 384, centred: x 100 + 38, y 50 + 8.
        rect(PxRect(138f, 58f, 512f, 384f), r[0].dst, "left")
        rect(PxRect(650f, 58f, 512f, 384f), r[1].dst, "right")
    }

    @Test fun stacked_and_side_by_side_swap_with_the_main_screen_and_touch_follows_the_bottom_screen() {
        val s = NdsSettings(big = NdsScreen.BOTTOM)
        for (choice in listOf(STACKED, SIDE_BY_SIDE)) {
            val r = layout(1780f, 1080f, choice, false, REGULAR_PHONE, s)
            assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), r.map { it.screen }, "$choice")
            assertEquals(listOf(true, false), r.map { it.touch }, "$choice")
            val normal = layout(1780f, 1080f, choice, false)
            assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), normal.map { it.screen }, "$choice")
            assertEquals(listOf(false, true), normal.map { it.touch }, "$choice")
        }
    }

    @Test fun single_shows_the_big_screen_only() {
        val top = layout(1080f, 1305f, SINGLE, false)
        assertEquals(1, top.size)
        assertFalse(top[0].touch)
        rect(PxRect(28f, 268f, 1024f, 768f), top[0].dst)   // centred: (1080 - 1024) / 2, (1305 - 768) / 2 rounded down
        val bottom = layout(1080f, 1305f, SINGLE, false, REGULAR_PHONE, NdsSettings(big = NdsScreen.BOTTOM)).single()
        assertTrue(bottom.touch)
        assertEquals(NdsLayout.BOTTOM_SRC, bottom.src)
    }

    @Test fun pip_puts_the_other_screen_in_a_corner_of_the_big_one_untouchable() {
        val r = layout(1474f, 1248f, PIP, true, STUBBY_PHONE, NdsSettings(corner = PipCorner.BOTTOM_RIGHT, size = PipSize.SMALL, opacity = PipOpacity.HALF))
        assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), r.map { it.screen })
        rect(PxRect(97f, 144f, 1280f, 960f), r[0].dst, "big")
        rect(PxRect(1280f - 320f - 20f + 97f, 144f + 960f - 240f - 20f, 320f, 240f), r[1].dst, "inset")
        assertEquals(listOf(true, false), r.map { it.shaded })
        assertEquals(listOf(false, false), r.map { it.touch })   // the bottom screen is the inset here
        assertEquals(listOf(false, true), r.map { it.inset })
        assertEquals(listOf(1f, 0.5f), r.map { it.alpha })
        val swapped = layout(1474f, 1248f, PIP, true, STUBBY_PHONE, NdsSettings(big = NdsScreen.BOTTOM))
        assertEquals(listOf(true, false), swapped.map { it.touch })
    }

    @Test fun inset_stays_inside_a_short_big_screen() {
        for (corner in PipCorner.entries) for (size in PipSize.entries) {
            val r = layout(1000f, 200f, PIP, false, REGULAR_PHONE, NdsSettings(corner = corner, size = size))
            assertInside(r[1].dst, r[0].dst, "$corner $size")
        }
    }

    @Test fun a_space_too_small_for_a_screen_has_no_regions() {
        for (choice in NdsLayoutChoice.entries) for (landscape in listOf(false, true)) {
            assertEquals(emptyList(), layout(0f, 1500f, choice, landscape), "$choice")
            assertEquals(emptyList(), layout(255f, 191f, choice, landscape), "$choice")
        }
        assertEquals(emptyList(), layout(511f, 1000f, SIDE_BY_SIDE, true))
        assertEquals(emptyList(), layout(1000f, 383f, STACKED, false))
        assertEquals(emptyList(), layout(1000f, 383f, LARGE, false))
    }

    @Test fun every_combination_keeps_the_invariants() {
        val spaces = listOf(1080f to 1305f, 1000f to 200f, 1780f to 1080f, 700f to 1800f, 1801f to 701f, 1474f to 1248f, 1248f to 950f)
        for ((w, h) in spaces) for (landscape in listOf(false, true)) for (screen in ScreenClass.entries) for (choice in NdsLayoutChoice.entries)
            for (big in NdsScreen.entries) for (corner in PipCorner.entries) for (size in PipSize.entries) {
                val s = NdsSettings(big = big, corner = corner, size = size)
                val r = layout(w, h, choice, landscape, screen, s)
                val what = "$w x $h landscape=$landscape $screen $choice $s"
                val space = PxRect(0f, 0f, w, h)
                for (reg in r) {
                    assertInside(reg.dst, space, what)
                    near(4f / 3f, reg.dst.width / reg.dst.height, "$what aspect")
                    assertEquals(NdsLayout.src(reg.screen), reg.src, what)
                    assertEquals(reg.screen == NdsScreen.BOTTOM && !reg.inset, reg.touch, what)
                    if (reg.inset) assertFalse(reg.shaded, what)
                }
                assertEquals(r.size, r.map { it.screen }.toSet().size, "$what: a screen twice")
                val full = r.filter { !it.inset }
                for (i in full.indices) for (j in i + 1 until full.size) assertFalse(overlaps(full[i].dst, full[j].dst), "$what: overlap")
                for (inset in r.filter { it.inset }) {
                    assertEquals(r.last(), inset, "$what: the inset is drawn last")
                    assertInside(inset.dst, full.single().dst, what)
                }
                // Shaded regions share one size and sit at whole pixels, so one preset chain serves them.
                assertTrue(r.filter { it.shaded }.map { it.dst.width to it.dst.height }.distinct().size <= 1, what)
                for (reg in r.filter { it.shaded }) for (v in listOf(reg.dst.x, reg.dst.y, reg.dst.width, reg.dst.height))
                    assertEquals(kotlin.math.floor(v), v, "$what whole px")
                if (choice != LARGE) for (reg in full) near(kotlin.math.floor(reg.dst.width / 256f), reg.dst.width / 256f, "$what whole scale")
            }
    }

    @Test fun a_ds_pixel_maps_to_its_centre_in_the_region() {
        val big = layout(1080f, 1305f, SINGLE, false, REGULAR_PHONE, NdsSettings(big = NdsScreen.BOTTOM))[0]
        val (x0, y0) = NdsLayout.pointIn(big, 0f, 0f)
        near(big.dst.x + 4f * 0.5f, x0, "x0"); near(big.dst.y + 4f * 0.5f, y0, "y0")
        val (x1, y1) = NdsLayout.pointIn(big, 255f, 191f)
        near(big.dst.x + big.dst.width - 2f, x1, "x1"); near(big.dst.y + big.dst.height - 2f, y1, "y1")
    }

    @Test fun a_ds_frame_is_the_stack_at_any_whole_scale() {
        assertTrue(NdsLayout.isDsFrame(256, 384))
        assertTrue(NdsLayout.isDsFrame(512, 768))
        assertTrue(NdsLayout.isDsFrame(1024, 1536))
        assertTrue(NdsLayout.isDsFrame(2048, 3072))
        assertFalse(NdsLayout.isDsFrame(0, 0))          // before the first frame
        assertFalse(NdsLayout.isDsFrame(256, 192))      // one screen
        assertFalse(NdsLayout.isDsFrame(512, 384))      // side by side (a core layout we lock off)
        assertFalse(NdsLayout.isDsFrame(300, 450))      // 2:3 but not whole 256 steps
        assertFalse(NdsLayout.isDsFrame(512, 769))
    }
}
