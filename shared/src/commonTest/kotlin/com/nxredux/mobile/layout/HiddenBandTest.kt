package com.nxredux.mobile.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenBandTest {
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
}
