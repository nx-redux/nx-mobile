package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WiiuLayoutTest {
    private val portrait = PxRect(0f, 0f, 1080f, 1800f)
    private val landscape = PxRect(0f, 0f, 2400f, 1080f)
    private val fold = PxRect(0f, 0f, 2176f, 1812f)
    private val margin = 16f

    private fun regions(space: PxRect, layout: NdsLayoutChoice, big: NdsScreen = NdsScreen.TOP) =
        WiiuLayout.regions(space, layout, NdsSettings(big = big), margin)

    private fun assert16by9(r: NdsRegion) =
        assertTrue(abs(r.dst.width / r.dst.height - 16f / 9f) < 0.01f, "${r.screen} ${r.dst} is not 16:9")

    private fun inside(space: PxRect, r: NdsRegion) {
        assertTrue(r.dst.x >= space.x - 0.01f && r.dst.y >= space.y - 0.01f, "${r.dst} starts outside $space")
        assertTrue(r.dst.x + r.dst.width <= space.x + space.width + 0.01f, "${r.dst} is wider than $space")
        assertTrue(r.dst.y + r.dst.height <= space.y + space.height + 0.01f, "${r.dst} is taller than $space")
    }

    /** Where a tap at the centre of [r]'s screen lands in the core frame, as ScreenRegions.mapTouch works it out. */
    private fun centreTap(r: NdsRegion): Pair<Float, Float> {
        assertTrue(r.touch, "${r.screen} takes no touch")
        return (r.src.left + 0.5f * (r.src.right - r.src.left)) to (r.src.top + 0.5f * (r.src.bottom - r.src.top))
    }

    @Test fun sources_are_the_halves_of_the_stacked_frame() {
        for ((w, h) in listOf(1280 to 1440, 2560 to 2880)) {
            assertEquals(PxRect(0f, 0f, w.toFloat(), h / 2f), px(WiiuLayout.TV_SRC, w, h))
            assertEquals(PxRect(0f, h / 2f, w.toFloat(), h / 2f), px(WiiuLayout.PAD_SRC, w, h))
            // each half is 16:9
            assertEquals(16f / 9f, w / (h / 2f), 0.001f)
        }
        assertEquals(WiiuLayout.TV_SRC, WiiuLayout.src(NdsScreen.TOP))
        assertEquals(WiiuLayout.PAD_SRC, WiiuLayout.src(NdsScreen.BOTTOM))
    }

    private fun px(r: FrameRect, w: Int, h: Int) = PxRect(r.left * w, r.top * h, (r.right - r.left) * w, (r.bottom - r.top) * h)

    @Test fun the_wiiu_frame_is_8_by_9_at_any_scale() {
        assertTrue(WiiuLayout.isWiiuFrame(1280, 1440))
        assertTrue(WiiuLayout.isWiiuFrame(2560, 2880))
        assertTrue(WiiuLayout.isWiiuFrame(3840, 4320))
        assertFalse(WiiuLayout.isWiiuFrame(1280, 720))
        assertFalse(WiiuLayout.isWiiuFrame(256, 384))
        assertFalse(WiiuLayout.isWiiuFrame(0, 0))
    }

    @Test fun every_layout_in_each_orientation_keeps_both_screens_16_by_9_inside_the_space() {
        for (space in listOf(portrait, landscape, fold)) for (layout in NdsLayoutChoice.entries) for (big in NdsScreen.entries) {
            val rs = regions(space, layout, big)
            val expected = if (layout == NdsLayoutChoice.SINGLE) 1 else 2
            assertEquals(expected, rs.size, "$layout in $space")
            rs.forEach { assert16by9(it); inside(space, it) }
            assertEquals(big, rs.first().screen, "$layout draws the big screen first")
            rs.forEach { assertEquals(it.screen == NdsScreen.BOTTOM && !it.inset, it.touch) }
        }
    }

    @Test fun stacked_in_portrait_fills_the_width_one_over_the_other() {
        val (tv, pad) = regions(portrait, NdsLayoutChoice.STACKED)
        assertEquals(NdsScreen.TOP, tv.screen)
        assertEquals(1080f, tv.dst.width, 1f)
        assertEquals(tv.dst.width, pad.dst.width)
        assertEquals(tv.dst.y + tv.dst.height, pad.dst.y)
        assertEquals(tv.dst.x, pad.dst.x)
    }

    @Test fun large_keeps_the_other_screen_at_half_size() {
        for (space in listOf(portrait, landscape, fold)) {
            val (tv, pad) = regions(space, NdsLayoutChoice.LARGE)
            assertEquals(NdsScreen.TOP, tv.screen)
            assertEquals(tv.dst.width / 2f, pad.dst.width, 2f)   // each kept 16:9 in whole px
            assertTrue(pad.touch)
            // no overlap
            val apart = pad.dst.y >= tv.dst.y + tv.dst.height - 0.01f || pad.dst.x >= tv.dst.x + tv.dst.width - 0.01f
            assertTrue(apart, "$space: $tv overlaps $pad")
        }
        // a wide landscape phone: the GamePad beside the TV gives the TV more room than under it
        val (tv, pad) = regions(landscape, NdsLayoutChoice.LARGE)
        assertTrue(pad.dst.x >= tv.dst.x + tv.dst.width - 0.01f)
        assertEquals(1600f, tv.dst.width)
        // the near-square Fold: under it
        val (ftv, fpad) = regions(fold, NdsLayoutChoice.LARGE)
        assertTrue(fpad.dst.y >= ftv.dst.y + ftv.dst.height - 0.01f)
    }

    @Test fun side_by_side_halves_the_width() {
        val (a, b) = regions(landscape, NdsLayoutChoice.SIDE_BY_SIDE)
        assertEquals(1200f, a.dst.width)
        assertEquals(a.dst.x + a.dst.width, b.dst.x)
        assertEquals(a.dst.y, b.dst.y)
    }

    @Test fun pip_puts_the_other_screen_in_a_corner_of_the_big_one() {
        val (big, inset) = regions(landscape, NdsLayoutChoice.PIP)
        assertTrue(inset.inset)
        assertFalse(inset.touch)
        assertTrue(inset.dst.x + inset.dst.width <= big.dst.x + big.dst.width)
        assertTrue(inset.dst.y >= big.dst.y)
        assertTrue(inset.dst.width < big.dst.width / 2f)
    }

    @Test fun swap_trades_the_screens_places() {
        for (space in listOf(portrait, landscape)) for (layout in listOf(NdsLayoutChoice.STACKED, NdsLayoutChoice.SIDE_BY_SIDE, NdsLayoutChoice.LARGE)) {
            val plain = regions(space, layout, NdsScreen.TOP).associate { it.screen to it.dst }
            val swapped = regions(space, layout, NdsScreen.BOTTOM).associate { it.screen to it.dst }
            assertEquals(plain[NdsScreen.TOP], swapped[NdsScreen.BOTTOM], "$layout in $space")
            assertEquals(plain[NdsScreen.BOTTOM], swapped[NdsScreen.TOP], "$layout in $space")
        }
        assertEquals(NdsScreen.TOP, regions(portrait, NdsLayoutChoice.SINGLE, NdsScreen.TOP).single().screen)
        assertEquals(NdsScreen.BOTTOM, regions(portrait, NdsLayoutChoice.SINGLE, NdsScreen.BOTTOM).single().screen)
    }

    @Test fun padTouchMapsInEveryMode() {
        // one display: the GamePad's centre is the centre of the frame's bottom half in every layout that shows it as a
        // screen (Single and PiP show it big once SWAP made it the big one)
        for (space in listOf(portrait, landscape, fold)) for (layout in NdsLayoutChoice.entries) for (big in NdsScreen.entries) {
            val pad = regions(space, layout, big).firstOrNull { it.screen == NdsScreen.BOTTOM && !it.inset } ?: continue
            val (x, y) = centreTap(pad)
            assertEquals(0.5f, x, 0.0001f, "$layout big=$big")
            assertEquals(0.75f, y, 0.0001f, "$layout big=$big")
        }
        assertNotNull(regions(portrait, NdsLayoutChoice.SINGLE, NdsScreen.BOTTOM).firstOrNull { it.touch })
        assertNotNull(regions(portrait, NdsLayoutChoice.PIP, NdsScreen.BOTTOM).firstOrNull { it.touch })
        // split: the GamePad alone on the second display
        val second = DualScreen.wiiuScreen(1080f, 2520f, NdsScreen.BOTTOM).single()
        assert16by9(second)
        val (x, y) = centreTap(second)
        assertEquals(0.5f, x, 0.0001f)
        assertEquals(0.75f, y, 0.0001f)
        // and the TV on the main display takes none
        assertFalse(DualScreen.wiiuScreen(2520f, 1080f, NdsScreen.TOP).single().touch)
    }

    @Test fun split_puts_one_16_by_9_screen_on_each_display() {
        val tv = DualScreen.wiiuScreen(1920f, 1080f, NdsScreen.TOP).single()
        assertEquals(WiiuLayout.TV_SRC, tv.src)
        assertEquals(PxRect(0f, 0f, 1920f, 1080f), tv.dst)
        val inArea = DualScreen.wiiuScreenIn(PxRect(100f, 50f, 1600f, 900f), NdsScreen.BOTTOM).single()
        assertEquals(PxRect(100f, 50f, 1600f, 900f), inArea.dst)
        assertEquals(WiiuLayout.PAD_SRC, inArea.src)
    }

    @Test fun the_wiiu_is_a_dual_screen_console_and_splits_with_a_second_display() {
        assertTrue(DualScreenConsole.appliesTo("WIIU"))
        assertTrue(DualScreenConsole.appliesTo("NDS"))
        assertTrue(DualScreenConsole.appliesTo("3DS"))
        assertTrue(DualScreenConsole.appliesTo(" wiiu "))
        assertFalse(DualScreenConsole.appliesTo("WII"))
        assertFalse(DualScreenConsole.appliesTo("GBA"))
        assertEquals(DualMode.SPLIT, DualScreen.mode(true, true, DualScreenConsole.appliesTo("WIIU"), false))
        assertEquals(DualMode.PANEL_UP, DualScreen.mode(true, true, DualScreenConsole.appliesTo("WIIU"), true))
        assertEquals(DualMode.OFF, DualScreen.mode(false, true, DualScreenConsole.appliesTo("WIIU"), false))
    }

    @Test fun a_passing_frame_that_is_not_the_stack_keeps_the_screens_drawn() {
        // Cemu's layout is locked to the stack: once its screens are drawn, any other size is a transient one
        assertTrue(WiiuLayout.keepsDrawn(1280, 720, drawn = true))
        assertTrue(WiiuLayout.keepsDrawn(2560, 1440, drawn = true))
        assertTrue(WiiuLayout.keepsDrawn(0, 0, drawn = true))
        // the stack itself is laid out again (a new 3D resolution)
        assertFalse(WiiuLayout.keepsDrawn(2560, 2880, drawn = true))
        assertFalse(WiiuLayout.keepsDrawn(1280, 1440, drawn = true))
        // nothing drawn yet: the frame decides as it comes
        assertFalse(WiiuLayout.keepsDrawn(1280, 720, drawn = false))
    }

    @Test fun no_room_no_regions() {
        assertEquals(emptyList(), regions(PxRect(0f, 0f, 0f, 0f), NdsLayoutChoice.STACKED))
    }
}
