package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsLayoutChoice
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

    private fun layout(w: Float, h: Float, landscape: Boolean, s: NdsSettings = NdsSettings()) =
        NdsLayout.regions(w, h, landscape, s, margin)

    @Test fun portrait_pip_puts_the_big_screen_on_top_and_the_inset_in_its_corner() {
        val r = layout(1080f, 1500f, landscape = false, NdsSettings(portrait = NdsLayoutChoice.PIP, corner = PipCorner.BOTTOM_RIGHT))
        assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), r.map { it.screen })
        rect(PxRect(0f, 0f, 1080f, 810f), r[0].dst, "big")
        rect(PxRect(703.6f, 522.7f, 356.4f, 267.3f), r[1].dst, "inset")
        assertEquals(listOf(true, false), r.map { it.shaded })
        assertEquals(listOf(false, true), r.map { it.touch })   // the inset bottom screen, with no pad over the game
        assertEquals(listOf(false, true), r.map { it.inset })
        assertEquals(NdsLayout.BOTTOM_SRC, r[1].src)
        assertEquals(FrameRect(0f, 0f, 1f, 0.5f), NdsLayout.TOP_SRC)
    }

    @Test fun pip_with_the_bottom_screen_big_makes_it_touchable() {
        val r = layout(1080f, 1500f, false, NdsSettings(NdsLayoutChoice.PIP, big = NdsScreen.BOTTOM, corner = PipCorner.TOP_LEFT, size = PipSize.SMALL))
        assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), r.map { it.screen })
        assertEquals(listOf(true, false), r.map { it.touch })
        rect(PxRect(20f, 20f, 270f, 202.5f), r[1].dst, "inset")
    }

    @Test fun pip_inset_carries_the_opacity() {
        val r = layout(1080f, 1500f, false, NdsSettings(NdsLayoutChoice.PIP, opacity = PipOpacity.HALF))
        assertEquals(listOf(1f, 0.5f), r.map { it.alpha })
        assertEquals(listOf(1f, 1f), layout(1080f, 1500f, false, NdsSettings(NdsLayoutChoice.PIP)).map { it.alpha })
    }

    @Test fun only_the_inset_is_ever_translucent() {
        for (portrait in NdsLayoutChoice.entries) for (landscape in listOf(false, true)) {
            val r = layout(1080f, 1500f, landscape, NdsSettings(portrait = portrait, opacity = PipOpacity.HALF))
            assertTrue(r.filter { !it.inset }.all { it.alpha == 1f }, "$portrait landscape=$landscape")
            if (landscape || portrait != NdsLayoutChoice.PIP) assertTrue(r.none { it.inset }, "$portrait landscape=$landscape")
        }
    }

    @Test fun stacked_fits_both_at_one_scale_centred_and_swap_exchanges_them() {
        val r = layout(1080f, 1500f, false, NdsSettings(portrait = NdsLayoutChoice.STACKED))
        rect(PxRect(40f, 0f, 1000f, 750f), r[0].dst, "top")
        rect(PxRect(40f, 750f, 1000f, 750f), r[1].dst, "bottom")
        assertEquals(listOf(true, true), r.map { it.shaded })
        assertEquals(listOf(false, true), r.map { it.touch })
        val swapped = layout(1080f, 1500f, false, NdsSettings(portrait = NdsLayoutChoice.STACKED, big = NdsScreen.BOTTOM))
        assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), swapped.map { it.screen })
        rect(PxRect(40f, 0f, 1000f, 750f), swapped[0].dst, "bottom first")
        assertTrue(swapped[0].touch)
    }

    @Test fun single_shows_the_big_screen_only() {
        val top = layout(1080f, 1500f, false, NdsSettings(portrait = NdsLayoutChoice.SINGLE))
        assertEquals(1, top.size)
        assertFalse(top[0].touch)
        rect(PxRect(0f, 0f, 1080f, 810f), top[0].dst)
        val bottom = layout(1080f, 1500f, false, NdsSettings(portrait = NdsLayoutChoice.SINGLE, big = NdsScreen.BOTTOM)).single()
        assertTrue(bottom.touch)
        assertEquals(NdsLayout.BOTTOM_SRC, bottom.src)
    }

    @Test fun landscape_side_by_side_whatever_the_portrait_layout() {
        for (p in NdsLayoutChoice.entries) {
            val r = layout(2400f, 1080f, true, NdsSettings(portrait = p, landscape = NdsLayoutChoice.SIDE_BY_SIDE))
            rect(PxRect(0f, 90f, 1200f, 900f), r[0].dst, "$p left")
            rect(PxRect(1200f, 90f, 1200f, 900f), r[1].dst, "$p right")
            assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), r.map { it.screen })
            assertEquals(listOf(false, true), r.map { it.touch })
        }
        assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), layout(2400f, 1080f, true, NdsSettings(landscape = NdsLayoutChoice.SIDE_BY_SIDE, big = NdsScreen.BOTTOM)).map { it.screen })
    }

    @Test fun controller_mode_uses_the_controller_portrait_layout() {
        val s = NdsSettings(portrait = NdsLayoutChoice.PIP, controller = NdsLayoutChoice.SINGLE)
        assertEquals(layout(1080f, 1500f, false, s), NdsLayout.regions(1080f, 1500f, false, s, margin, controllerMode = false))
        assertEquals(2, NdsLayout.regions(1080f, 1500f, false, s, margin, controllerMode = false).size)   // pip
        assertEquals(
            layout(1080f, 1500f, false, NdsSettings(portrait = NdsLayoutChoice.SINGLE)),
            NdsLayout.regions(1080f, 1500f, false, s, margin, controllerMode = true),
        )
        // Landscape ignores both portrait layouts.
        assertEquals(layout(2400f, 1080f, true, s), NdsLayout.regions(2400f, 1080f, true, s, margin, controllerMode = true))
    }

    @Test fun an_unpicked_layout_draws_the_screens_default() {
        assertEquals(layout(1080f, 1607f, false, NdsSettings(NdsLayoutChoice.LARGE)), layout(1080f, 1607f, false))
        assertEquals(layout(2400f, 1080f, true, NdsSettings(landscape = NdsLayoutChoice.SIDE_BY_SIDE)), layout(2400f, 1080f, true))
        // Fold-shaped landscape defaults to large, and shows a saved side by side as large too.
        val large = NdsLayout.regions(1248f, 995f, true, NdsSettings(landscape = NdsLayoutChoice.LARGE), margin, screen = ScreenClass.STUBBY_PHONE)
        assertEquals(large, NdsLayout.regions(1248f, 995f, true, NdsSettings(), margin, screen = ScreenClass.STUBBY_PHONE))
        assertEquals(large, NdsLayout.regions(1248f, 995f, true, NdsSettings(landscape = NdsLayoutChoice.SIDE_BY_SIDE), margin, screen = ScreenClass.STUBBY_PHONE))
        // The controller row, unpicked, is the portrait default.
        assertEquals(layout(1080f, 2340f, false, NdsSettings(NdsLayoutChoice.LARGE)), NdsLayout.regions(1080f, 2340f, false, NdsSettings(), margin, controllerMode = true))
    }

    @Test fun an_empty_area_has_no_regions() {
        assertEquals(emptyList(), layout(0f, 1500f, false))
        assertEquals(emptyList(), layout(1080f, 0f, true))
    }

    @Test fun inset_stays_inside_a_short_big_screen() {
        // A split-screen or near-square area: the big screen is height-limited (200 × 4/3 = 266.7 wide), narrower than
        // 40% of the area's width (400).
        for (corner in PipCorner.entries) for (size in PipSize.entries) {
            val r = layout(1000f, 200f, false, NdsSettings(NdsLayoutChoice.PIP, corner = corner, size = size))
            assertInside(r[1].dst, r[0].dst, "$corner $size")
        }
    }

    @Test fun every_combination_keeps_the_invariants() {
        val areas = listOf(1080f to 1500f, 1000f to 200f, 2400f to 1080f, 700f to 1800f, 1800f to 700f)
        for ((w, h) in areas) for (landscape in listOf(false, true)) for (p in NdsLayoutChoice.entries)
            for (big in NdsScreen.entries) for (corner in PipCorner.entries) for (size in PipSize.entries) for (l in NdsLayoutChoice.entries) {
                val s = NdsSettings(p, l, big, corner, size)
                val r = layout(w, h, landscape, s)
                val what = "$w x $h landscape=$landscape $s"
                assertTrue(r.isNotEmpty(), what)
                for (reg in r) {
                    assertInside(reg.dst, PxRect(0f, 0f, w, h), what)
                    near(4f / 3f, reg.dst.width / reg.dst.height, "$what aspect")
                    assertEquals(NdsLayout.src(reg.screen), reg.src, what)
                    // Touch only on the bottom screen: always the full-size (shaded) one, never the inset; neither the inset
                    // nor large screen's small one takes the shader.
                    assertEquals(reg.screen == NdsScreen.BOTTOM, reg.touch, what)   // no pad over the game: wherever it is
                    if (reg.inset) assertFalse(reg.shaded, what)
                }
                assertEquals(r.size, r.map { it.screen }.toSet().size, "$what: a screen twice")
                val full = r.filter { !it.inset }
                for (i in full.indices) for (j in i + 1 until full.size) assertFalse(overlaps(full[i].dst, full[j].dst), "$what: overlap")
                for (inset in r.filter { it.inset }) {
                    assertEquals(r.last(), inset, "$what: the inset is drawn last")
                    assertInside(inset.dst, full.single().dst, what)
                }
                // Shaded regions share one size, so one preset chain serves them without resizing its FBOs.
                assertEquals(1, r.filter { it.shaded }.map { it.dst.width to it.dst.height }.distinct().size, what)
            }
    }

    @Test fun a_ds_pixel_maps_to_its_centre_in_the_region() {
        val big = layout(1080f, 1500f, false, NdsSettings(NdsLayoutChoice.PIP, big = NdsScreen.BOTTOM))[0]
        val (x0, y0) = NdsLayout.pointIn(big, 0f, 0f)
        near(1080f / 512f, x0, "x0"); near(810f / 384f, y0, "y0")
        val (x1, y1) = NdsLayout.pointIn(big, 255f, 191f)
        near(1080f - 1080f / 512f, x1, "x1"); near(810f - 810f / 384f, y1, "y1")
    }

    @Test fun shaded_regions_are_whole_pixels_of_one_exact_size_for_odd_areas() {
        // One preset chain draws every shaded region, so they must match to the pixel or its FBOs reallocate each frame.
        val areas = listOf(1081f to 917f, 917f to 1081f, 1081f to 1501f, 2401f to 1079f, 1001f to 201f, 333f to 777f, 90f to 60f)
        for ((w, h) in areas) for (landscape in listOf(false, true)) for (p in NdsLayoutChoice.entries)
            for (big in NdsScreen.entries) for (corner in PipCorner.entries) for (size in PipSize.entries) for (l in NdsLayoutChoice.entries) {
                val s = NdsSettings(p, l, big, corner, size)
                val shaded = layout(w, h, landscape, s).filter { it.shaded }
                val what = "$w x $h landscape=$landscape $s"
                val first = shaded.first().dst
                for (reg in shaded) {
                    assertEquals(first.width, reg.dst.width, "$what width")
                    assertEquals(first.height, reg.dst.height, "$what height")
                    for (v in listOf(reg.dst.x, reg.dst.y, reg.dst.width, reg.dst.height)) assertEquals(kotlin.math.floor(v), v, "$what whole px")
                    assertInside(reg.dst, PxRect(0f, 0f, w, h), what)
                }
                for (inset in layout(w, h, landscape, s).filter { it.inset }) assertInside(inset.dst, shaded.single().dst, "$what inset")
            }        // Too small for a screen pixel: nothing, rather than a zero-size region.
        for (landscape in listOf(false, true)) assertEquals(emptyList(), layout(1f, 1f, landscape))
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

    @Test fun no_touch_while_the_landscape_pad_is_over_the_game() {
        val s = NdsSettings(big = NdsScreen.TOP)
        val free = NdsLayout.regions(2400f, 1080f, true, s, margin)
        val covered = NdsLayout.regions(2400f, 1080f, true, s, margin, padOverGame = true)
        assertTrue(free.any { it.touch }, "side by side: the bottom screen is touchable without the pad")
        assertFalse(covered.any { it.touch }, "side by side under the pad: no touch")
        assertEquals(free.map { it.dst }, covered.map { it.dst })    // only touch changes
    }

    @Test fun portrait_touch_ignores_the_pad_flag() {
        for (layout in listOf(NdsLayoutChoice.STACKED, NdsLayoutChoice.SINGLE, NdsLayoutChoice.PIP)) {
            val s = NdsSettings(portrait = layout, big = NdsScreen.BOTTOM)
            assertEquals(
                NdsLayout.regions(1080f, 1600f, false, s, margin),
                NdsLayout.regions(1080f, 1600f, false, s, margin, padOverGame = true),
                "$layout",
            )
        }
    }

    @Test fun with_the_pad_over_the_game_only_the_main_bottom_screen_takes_touches() {
        for (p in NdsLayoutChoice.entries) for (l in NdsLayoutChoice.entries) for (landscape in listOf(false, true)) {
            for (big in NdsScreen.entries) {
                val s = NdsSettings(p, l, big)
                val r = NdsLayout.regions(1080f, 1500f, landscape, s, margin, padOverGame = landscape, portraitPadOver = !landscape)
                assertEquals(if (big == NdsScreen.BOTTOM) listOf(NdsScreen.BOTTOM) else emptyList(), r.filter { it.touch }.map { it.screen }, "$s $landscape")
            }
        }
    }

    @Test fun portrait_hidden_band_turns_off_touch_on_the_screen_under_the_clusters() {
        val bottomLow = NdsLayout.regions(1080f, 2400f, false, NdsSettings(portrait = NdsLayoutChoice.STACKED, big = NdsScreen.TOP), margin, portraitPadOver = true)
        assertFalse(bottomLow.any { it.touch }, "the touch screen below, under the clusters: no touch")
        val bottomHigh = NdsLayout.regions(1080f, 2400f, false, NdsSettings(portrait = NdsLayoutChoice.STACKED, big = NdsScreen.BOTTOM), margin, portraitPadOver = true)
        assertEquals(listOf(NdsScreen.BOTTOM), bottomHigh.filter { it.touch }.map { it.screen }, "swapped up, clear of the clusters: touchable")
        // Only touch changes; no cover (the band shown) leaves every region as it was.
        val free = NdsLayout.regions(1080f, 2400f, false, NdsSettings(portrait = NdsLayoutChoice.STACKED, big = NdsScreen.TOP), margin)
        assertEquals(free.map { it.dst }, bottomLow.map { it.dst })
        assertTrue(free.any { it.touch })
    }

    @Test fun large_screen_puts_the_small_one_apart_at_the_corner() {
        val s = NdsSettings(NdsLayoutChoice.LARGE, NdsLayoutChoice.LARGE, big = NdsScreen.TOP, corner = PipCorner.TOP_RIGHT, size = PipSize.SMALL)
        // Landscape: the small screen beside the big one, level with its top, the pair centred.
        val l = layout(2400f, 1080f, true, s)
        assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), l.map { it.screen })
        rect(PxRect(300f, 0f, 1440f, 1080f), l[0].dst, "landscape big")
        rect(PxRect(1740f, 0f, 360f, 270f), l[1].dst, "landscape small")
        assertEquals(listOf(true, false), l.map { it.shaded })
        assertEquals(listOf(false, true), l.map { it.touch })   // the small bottom screen, nothing over it
        assertEquals(listOf(true, false), layout(2400f, 1080f, true, s.copy(big = NdsScreen.BOTTOM)).map { it.touch })
        // With the pad clusters over the game only the big screen could take touches: R2 swaps the bottom one there.
        assertEquals(listOf(false, false), NdsLayout.regions(2400f, 1080f, true, s, margin, padOverGame = true).map { it.touch })
        assertEquals(listOf(false, false), NdsLayout.regions(1080f, 2000f, false, s, margin, portraitPadOver = true).map { it.touch })
        assertEquals(listOf(true, false), NdsLayout.regions(1080f, 2000f, false, s.copy(big = NdsScreen.BOTTOM), margin, portraitPadOver = true).map { it.touch })
        assertFalse(l.any { it.inset })
        // Portrait: the small screen above the big one at its right, the pair top-aligned.
        val p = layout(1080f, 2000f, false, s)
        rect(PxRect(811f, 0f, 269f, 202f), p[1].dst, "portrait small")
        rect(PxRect(0f, 202f, 1080f, 810f), p[0].dst, "portrait big")
        // Bottom-left: below the big screen, at its left.
        val bl = layout(1080f, 2000f, false, s.copy(corner = PipCorner.BOTTOM_LEFT))
        rect(PxRect(0f, 0f, 1080f, 810f), bl[0].dst, "bottom-left big")
        rect(PxRect(0f, 810f, 269f, 202f), bl[1].dst, "bottom-left small")
    }

    @Test fun landscape_stacked_gives_the_first_screen_three_quarters_of_the_height() {
        val r = layout(2400f, 1080f, true, NdsSettings(landscape = NdsLayoutChoice.STACKED))
        assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), r.map { it.screen })
        rect(PxRect(660f, 0f, 1080f, 810f), r[0].dst, "top")
        rect(PxRect(1020f, 810f, 360f, 270f), r[1].dst, "bottom")
        assertEquals(listOf(true, false), r.map { it.shaded })
        assertEquals(listOf(false, true), r.map { it.touch })
    }

    @Test fun landscape_single_is_centred() {
        val s = NdsSettings(landscape = NdsLayoutChoice.SINGLE, big = NdsScreen.TOP)
        val r = NdsLayout.regions(2520f, 1080f, true, s, margin)
        assertEquals(1, r.size)
        rect(PxRect(540f, 0f, 1440f, 1080f), r[0].dst, "flip single")
        val r2 = NdsLayout.regions(2400f, 1000f, true, s, margin)
        // Centred, rounded down to a whole pixel like every shaded region (1333 wide leaves 1067: x = 533, not 533.5).
        near(kotlin.math.floor((2400f - r2[0].dst.width) / 2f), r2[0].dst.x, "centred x")
        near(kotlin.math.floor((1000f - r2[0].dst.height) / 2f), r2[0].dst.y, "centred y")
    }

    @Test fun landscape_pip_is_the_centred_big_screen_with_an_inset_inside_it() {
        val s = NdsSettings(landscape = NdsLayoutChoice.PIP, big = NdsScreen.TOP, corner = PipCorner.BOTTOM_RIGHT)
        val r = NdsLayout.regions(2520f, 1080f, true, s, margin)
        assertEquals(2, r.size)
        rect(PxRect(540f, 0f, 1440f, 1080f), r[0].dst, "big")
        assertTrue(r[1].inset)
        assertInside(r[1].dst, r[0].dst, "inset inside big")
        assertEquals(NdsScreen.BOTTOM, r[1].screen)
    }

    @Test fun landscape_pip_inset_is_a_share_of_the_big_screen_width() {
        for (size in PipSize.entries) {
            val s = NdsSettings(landscape = NdsLayoutChoice.PIP, size = size)
            val r = NdsLayout.regions(2520f, 1080f, true, s, margin)
            near(size.fraction * r[0].dst.width, r[1].dst.width, "$size")   // Medium: 0.33 x 1440, not 0.33 x 2520
        }
    }

    @Test fun landscape_side_by_side_is_the_regular_phone_default() {
        assertNull(NdsSettings().landscape)
        assertEquals(
            NdsLayout.regions(2520f, 1080f, true, NdsSettings(landscape = NdsLayoutChoice.SIDE_BY_SIDE), margin),
            NdsLayout.regions(2520f, 1080f, true, NdsSettings(), margin),
        )
    }
}
