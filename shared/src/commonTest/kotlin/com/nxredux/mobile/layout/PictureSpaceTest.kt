package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals

/** The picture's space per pad room, and the hidden band's placement. */
class PictureSpaceTest {
    @Test fun beside_keeps_set_aside_free_both_sides() =
        assertEquals(PxRect(200f, 0f, 1920f, 1080f), pictureSpace(PadRoom.BESIDE, 2320f, 1080f, 200f, 0f))

    @Test fun beside_never_passes_the_middle() =
        assertEquals(PxRect(500f, 0f, 0f, 400f), pictureSpace(PadRoom.BESIDE, 1000f, 400f, 900f, 0f))

    @Test fun band_and_no_pad_take_the_whole_area() {
        assertEquals(PxRect(0f, 0f, 1080f, 1300f), pictureSpace(PadRoom.BAND, 1080f, 1300f, 200f, 800f))
        assertEquals(PxRect(0f, 0f, 2400f, 1080f), pictureSpace(PadRoom.NONE, 2400f, 1080f, 200f, 800f))
    }

    // The quarter-pad rule: the picture may reach a quarter of the overlay's height into it.
    @Test fun hidden_band_space_reaches_a_quarter_into_the_overlay() =
        assertEquals(PxRect(0f, 0f, 1080f, 1268f), pictureSpace(PadRoom.HIDDEN_BAND, 1080f, 1868f, 0f, 800f))

    @Test fun hidden_band_short_picture_centred_above_the_overlay() {
        val placed = placedFor(PadRoom.HIDDEN_BAND, PxRect(0f, 298f, 1080f, 672f), 1868f, 800f)
        assertEquals((1068f - 672f) / 2f, placed.y, 0.01f)
    }

    @Test fun hidden_band_tall_picture_at_the_top() =
        assertEquals(0f, placedFor(PadRoom.HIDDEN_BAND, PxRect(0f, 20f, 960f, 1228f), 1868f, 800f).y)

    @Test fun other_rooms_keep_the_placement() {
        val p = PxRect(10f, 20f, 30f, 40f)
        assertEquals(p, placedFor(PadRoom.BAND, p, 1868f, 800f))
        assertEquals(p, placedFor(PadRoom.BESIDE, p, 1868f, 800f))
    }
}
