package com.nxredux.mobile.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class PortraitBandTest {
    @Test
    fun fold_cover_band_918() {
        assertEquals(918f, portraitBandPx(1248f, 1868f, 428.1f, 2.625f), 1f)
    }

    @Test
    fun phone_keeps_full_band() {
        assertEquals(959.1f, portraitBandPx(1080f, 2264f, 420f, 2.625f), 0.5f)
    }

    @Test
    fun tablet_band_holds_11mm_buttons() {
        val unit = 11f / 25.4f * 249f
        assertEquals(6.2f * unit + 2f * 16f * 1.75f, portraitBandPx(1440f, 2304f, 249f, 1.75f), 0.5f)
    }

    @Test
    fun band_capped_at_60_percent() {
        assertEquals(0.6f * 800f, portraitBandPx(1440f, 800f, 249f, 1.75f), 0.5f)
    }

    @Test
    fun split_is_the_game_area_under_the_cutout_then_the_whole_pixel_band() {
        // The Fold cover: a 1248 x 1968 window with a 100 px cutout, the band on the 1868 px under it.
        val split = portraitSplit(1248f, 1968f, 100f, 428.1f, 2.625f)
        val band = kotlin.math.round(portraitBandPx(1248f, 1868f, 428.1f, 2.625f))
        assertEquals(PortraitSplit(gameTop = 100f, bandTop = 1968f - band, bandPx = band), split)
        assertEquals(918f, split.bandPx, 1f)
    }
}
