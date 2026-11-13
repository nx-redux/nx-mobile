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
import kotlin.test.assertNull
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

    /**
     * The unfolded Fold's landscape space as measured on the device (its Debug log): the 2448 x 1848 window less the
     * half-reach set-aside each side, the reach 77 + 526 px (edge + measured buttons), so 302 px a side and 1845 x 1848
     * for the screens.
     */
    private val unfoldedLandscape: PxRect = run {
        val side = (77f + 526f) / 2f   // half the reach a side, as the landscape pad beside the picture set aside
        PxRect(side, 0f, 2448f - 2f * side, 1848f)
    }

    private fun unfolded(choice: NdsLayoutChoice) =
        NdsLayout.regions(unfoldedLandscape, choice, landscape = true, NEAR_SQUARE_TABLET, NdsSettings(), margin)

    @Test fun unfolded_landscape_space_is_the_measured_one() {
        near(301.5f, unfoldedLandscape.x, "set-aside")
        near(1845f, unfoldedLandscape.width, "space width")
    }

    @Test fun unfolded_landscape_large_6x_3x() = assertScales(6f, 3f, actual = unfolded(LARGE))

    @Test fun unfolded_landscape_stacked_4x() = assertScales(4f, 4f, actual = unfolded(STACKED))

    // Verified on the Fold: Single 7.00x, 1792 x 1344 in the 1845-wide space.
    @Test fun unfolded_landscape_single_and_pip_7x_the_most_that_fits() {
        assertScales(7f, actual = unfolded(SINGLE))
        near(1792f, unfolded(SINGLE)[0].dst.width, "single width")
        near(1344f, unfolded(SINGLE)[0].dst.height, "single height")
        near(7f, unfolded(PIP)[0].dst.width / 256f, "pip big")
    }

    @Test fun near_square_tablet_landscape_single_keeps_the_most_that_fits() =
        assertScales(7f, actual = layout(1828f, 1848f, SINGLE, true, NEAR_SQUARE_TABLET))

    @Test fun wide_tablet_landscape_single_one_under_the_most_that_fits() {
        assertScales(6f, actual = layout(1828f, 1848f, SINGLE, true, WIDE_TABLET))   // 7x fits
        assertScales(7f, actual = layout(1828f, 1848f, SINGLE, false, WIDE_TABLET))  // portrait keeps the most
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

    @Test fun large_with_equal_scales_shades_only_the_top() {
        val r = layout(256f, 384f, LARGE, false)
        assertScales(1f, 1f, actual = r)
        assertEquals(listOf(true, false), r.map { it.shaded })
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
                if (w >= 512f && h >= 384f) assertTrue(r.isNotEmpty(), what)   // every layout fits at 1x here
                for (reg in r) {
                    assertInside(reg.dst, space, what)
                    near(4f / 3f, reg.dst.width / reg.dst.height, "$what aspect")
                    assertEquals(NdsLayout.src(reg.screen), reg.src, what)
                    assertEquals(reg.screen == NdsScreen.BOTTOM && !reg.inset, reg.touch, what)
                    // Only the top screen takes the shader: the touch screen stays flat where a finger lands.
                    assertEquals(reg.screen == NdsScreen.TOP && !reg.inset, reg.shaded, what)
                }
                assertEquals(r.size, r.map { it.screen }.toSet().size, "$what: a screen twice")
                val full = r.filter { !it.inset }
                for (i in full.indices) for (j in i + 1 until full.size) assertFalse(overlaps(full[i].dst, full[j].dst), "$what: overlap")
                for (inset in r.filter { it.inset }) {
                    assertEquals(r.last(), inset, "$what: the inset is drawn last")
                    assertInside(inset.dst, full.single().dst, what)
                }
                // One shaded region at most; the big screen sits at whole pixels (Large's other screen may fill a fraction).
                assertTrue(r.count { it.shaded } <= 1, what)
                r.firstOrNull()?.let { reg -> for (v in listOf(reg.dst.x, reg.dst.y, reg.dst.width, reg.dst.height))
                    assertEquals(kotlin.math.floor(v), v, "$what whole px") }
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

    // --- Full-width Large: portrait on a tablet / the unfolded Fold, Screen Scaling Auto, the bare buttons ---
    // The unfolded Fold portrait: a 1848 x 2448 game area, the space above the band (a third of it reached) 1848 x 1762,
    // where Large is 6x + 3x. The pads are the player's to move, so they don't count.

    private val foldSpace = PxRect(0f, 0f, 1848f, 1762f)

    @Test fun full_width_large_on_the_unfolded_fold_moves_the_3x_touch_screen_down() {
        val r = NdsLayout.fullWidthLarge(foldSpace, 2448f, NdsScreen.TOP)!!
        rect(PxRect(0f, 0f, 1848f, 1386f), r[0].dst, "top")         // the whole width
        rect(PxRect(540f, 1386f, 768f, 576f), r[1].dst, "touch")    // Large's 3x, right under it
        assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), r.map { it.screen })
        assertEquals(listOf(true, false), r.map { it.shaded })
        assertEquals(listOf(false, true), r.map { it.touch })
    }

    @Test fun full_width_large_on_the_fold_cover_moves_the_3x_touch_screen_down() {
        // the cover in portrait, the game from under the camera cutout: 1248 x 1972, the space above the band 1248 x 1455 (4x + 3x)
        val r = NdsLayout.fullWidthLarge(PxRect(0f, 0f, 1248f, 1455f), 1972f, NdsScreen.TOP)!!
        rect(PxRect(0f, 0f, 1248f, 936f), r[0].dst, "top")
        rect(PxRect(240f, 936f, 768f, 576f), r[1].dst, "touch")
    }

    // --- Screen Scaling: Native keeps whole-number scales, the rest fill the space ---

    private fun scaled(space: PxRect, areaH: Float, choice: NdsLayoutChoice, landscape: Boolean, native: Boolean, screen: ScreenClass = REGULAR_PHONE) =
        NdsLayout.scaledRegions(space, areaH, choice, landscape, screen, NdsSettings(), margin, native)

    @Test fun native_keeps_the_whole_number_layouts() {
        assertScales(6f, 3f, actual = scaled(foldSpace, 2448f, LARGE, false, native = true, NEAR_SQUARE_TABLET))
        assertScales(4f, 4f, actual = scaled(PxRect(0f, 0f, 1848f, 1700f), 1700f, STACKED, false, native = true, NEAR_SQUARE_TABLET))
    }

    @Test fun auto_large_in_portrait_is_the_full_width_on_any_screen() {
        rect(PxRect(0f, 0f, 1848f, 1386f), scaled(foldSpace, 2448f, LARGE, false, native = false, NEAR_SQUARE_TABLET)[0].dst, "fold top")
        // a regular phone: 1080 wide, a 1080 x 1300 space over a 2300 area
        val phone = scaled(PxRect(0f, 0f, 1080f, 1300f), 2300f, LARGE, false, native = false)
        near(1080f, phone[0].dst.width, "phone top width")
        near(0f, phone[0].dst.x, "phone top x")
    }

    @Test fun auto_stacked_in_portrait_takes_the_full_width_for_both_screens() {
        // the Fold cover from under its cutout: 1248 x 1972, both 1248 x 936
        val cover = scaled(PxRect(0f, 0f, 1248f, 1455f), 1972f, STACKED, false, native = false, STUBBY_PHONE)
        rect(PxRect(0f, 0f, 1248f, 936f), cover[0].dst, "cover top")
        rect(PxRect(0f, 936f, 1248f, 936f), cover[1].dst, "cover touch")
        assertEquals(listOf(true, false), cover.map { it.shaded })
        assertEquals(listOf(false, true), cover.map { it.touch })
        // the unfolded Fold: 2 x 1386 is taller than 2448, so as wide as the area's height allows (2 x 1224), centred
        val inner = scaled(foldSpace, 2448f, STACKED, false, native = false, NEAR_SQUARE_TABLET)
        rect(PxRect(108f, 0f, 1632f, 1224f), inner[0].dst, "inner top")
        rect(PxRect(108f, 1224f, 1632f, 1224f), inner[1].dst, "inner touch")
        // swapped: the touch screen first
        val swapped = NdsLayout.scaledRegions(PxRect(0f, 0f, 1248f, 1455f), 1972f, STACKED, false, STUBBY_PHONE, NdsSettings(big = NdsScreen.BOTTOM), margin, native = false)
        assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), swapped.map { it.screen })
    }

    @Test fun auto_fills_the_space_with_the_other_layouts() {
        val space = PxRect(0f, 0f, 2400f, 1080f)
        val single = scaled(space, 1080f, SINGLE, true, native = false)
        near(1080f, single[0].dst.height, "single fills the height")   // 5.6x, not 5x
        val stacked = scaled(space, 1080f, STACKED, true, native = false)   // landscape Stacked: the pair scaled to the height
        near(1080f, stacked.shownRect().height, "landscape stacked fills the height")
        assertEquals(stacked[0].dst.width, stacked[1].dst.width)
        // landscape Large: the pair scaled to the height
        near(1080f, scaled(space, 1080f, LARGE, true, native = false).shownRect().height, "landscape large fills the height")
    }

    @Test fun auto_large_falls_back_to_filling_the_space_when_the_area_is_short() {
        val r = scaled(foldSpace, 1900f, LARGE, false, native = false, NEAR_SQUARE_TABLET)   // no room to move the touch screen down
        near(1762f, r.shownRect().height, "the 6x + 3x pair scaled to the space")
    }

    @Test fun full_width_large_swapped_puts_the_touch_screen_on_top() {
        val r = NdsLayout.fullWidthLarge(foldSpace, 2448f, NdsScreen.BOTTOM)!!
        assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), r.map { it.screen })
        assertEquals(listOf(true, false), r.map { it.touch })
        assertEquals(listOf(false, true), r.map { it.shaded })
    }

    @Test fun full_width_large_gives_way_when_the_area_is_too_short() {
        assertNull(NdsLayout.fullWidthLarge(foldSpace, 1900f, NdsScreen.TOP))   // 1386 + 576 is 1962
        assertNull(NdsLayout.fullWidthLarge(PxRect(0f, 0f, 0f, 0f), 2448f, NdsScreen.TOP))
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

    // Side by side in a pad skin's glass fills it (whole-number 2x leaves wide margins on the Flip): the pair scaled
    // evenly to the glass, centred, both screens still one size in whole px.
    @Test fun regions_filled_to_a_glass_scale_evenly_and_centre() {
        val glass = PxRect(0f, 0f, 1386f, 870f)
        val r = NdsLayout.regions(glass, SIDE_BY_SIDE, true, REGULAR_PHONE, NdsSettings(), 0f).filledTo(glass)
        val shown = r.shownRect()
        assertEquals(1386f, shown.width, 1f)                              // the width fills
        assertEquals((870f - shown.height) / 2f, shown.y, 1f)             // centred
        assertEquals(r[0].dst.width, r[1].dst.width); assertEquals(r[0].dst.height, r[1].dst.height)
        assertEquals(kotlin.math.round(r[0].dst.width), r[0].dst.width)   // whole px
    }
}
