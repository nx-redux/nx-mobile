package com.nxredux.mobile.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenBandTest {
    private val tall = CoreFrame(224f, 384f, 0f, false)
    private val wide = CoreFrame(320f, 224f, 0f, false)

    @Test fun vertical_arcade_offers_hide() = assertTrue(hideOffered(ConsoleScale.WHOLE_HEIGHT, tall))

    @Test fun rotated_frame_counts_as_tall() =
        assertTrue(hideOffered(ConsoleScale.WHOLE_HEIGHT, CoreFrame(384f, 224f, 0f, true)))

    @Test fun reported_aspect_wins() =
        assertFalse(hideOffered(ConsoleScale.WHOLE_HEIGHT, CoreFrame(224f, 384f, 1.33f, false)))

    @Test fun wide_game_does_not() = assertFalse(hideOffered(ConsoleScale.WHOLE_HEIGHT, wide))

    @Test fun ds_always() {
        assertTrue(hideOffered(ConsoleScale.DS, wide))
        assertTrue(hideOffered(ConsoleScale.N3DS, null))
    }

    @Test fun none_before_first_frame() = assertFalse(hideOffered(ConsoleScale.WHOLE_HEIGHT, null))

    @Test fun overlay_height_adds_padding() {
        // Wide band: midRowScale = 1.25, so (2.42 + 3.75) units + 2 x 16 dp.
        assertEquals((2.42f + 3.75f) * 10f + 2f * 16f * 2f, overlayHeightPx(10f, 20f, 20f, 2f), 0.001f)
    }

    @Test fun quarter_rule_space() = assertEquals(1268f, hiddenSpaceHeight(1868f, 800f), 0.001f)

    @Test fun ds_and_3ds_reach_a_third() {
        assertEquals(1f / 3f, hiddenBandReach(ds = true), 0.0001f)
        assertEquals(0.25f, hiddenBandReach(ds = false), 0.0001f)
        // The unfolded Fold, portrait, band hidden (overlay ~1028 px of 2448): 6x + 3x (1152 + 576) fits only with a third.
        assertTrue(hiddenSpaceHeight(2448f, 1028f, hiddenBandReach(ds = true)) >= 1728f)
        assertTrue(hiddenSpaceHeight(2448f, 1028f, hiddenBandReach(ds = false)) < 1728f)
    }

    @Test fun short_picture_centred_above_overlay() =
        assertEquals((1068f - 672f) / 2f, hiddenPictureTop(1868f, 800f, 672f), 0.001f)

    @Test fun tall_picture_top_aligned() = assertEquals(0f, hiddenPictureTop(1868f, 800f, 1200f), 0.001f)

    @Test fun clear_when_above_overlay() {
        assertTrue(overlayClear(1068f, 1868f, 800f))
        assertFalse(overlayClear(1069f, 1868f, 800f))
    }

    @Test fun band_waits_for_the_first_frame_only_when_hidden_is_chosen_and_the_shape_decides() {
        // Arcade set Hidden: the band waits until the first frame says tall (overlay) or wide (band).
        assertTrue(bandAwaitsFrame(hiddenChosen = true, ConsoleScale.WHOLE_HEIGHT, frame = null))
        assertFalse(bandAwaitsFrame(hiddenChosen = true, ConsoleScale.WHOLE_HEIGHT, wide))
        assertFalse(bandAwaitsFrame(hiddenChosen = true, ConsoleScale.WHOLE_HEIGHT, tall))
        // Shown: nothing to decide.
        assertFalse(bandAwaitsFrame(hiddenChosen = false, ConsoleScale.WHOLE_HEIGHT, frame = null))
        // DS / 3DS hide without a frame: their layout starts with the overlay, no band.
        assertFalse(bandAwaitsFrame(hiddenChosen = true, ConsoleScale.DS, frame = null))
        assertFalse(bandAwaitsFrame(hiddenChosen = true, ConsoleScale.N3DS, frame = null))
    }

    @Test fun upright_handheld_games_offer_hide_and_landscape_ones_do_not() {
        // Beetle WonderSwan rotated: the 224 x 144 frame turned a quarter, reported at 9:14.
        assertTrue(hideOffered(ConsoleScale.NATIVE, CoreFrame(224f, 144f, 9f / 14f, true)))
        assertFalse(hideOffered(ConsoleScale.NATIVE, CoreFrame(224f, 144f, 14f / 9f, false)))
        // Handy: a rotated cartridge swaps the frame to 102 x 160, its aspect with it.
        assertTrue(hideOffered(ConsoleScale.NATIVE, CoreFrame(102f, 160f, 102f / 160f, false)))
        assertFalse(hideOffered(ConsoleScale.NATIVE, CoreFrame(160f, 102f, 160f / 102f, false)))
    }
}
