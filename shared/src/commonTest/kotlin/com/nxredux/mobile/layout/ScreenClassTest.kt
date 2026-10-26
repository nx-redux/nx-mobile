package com.nxredux.mobile.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenClassTest {
    @Test fun phone_2400x1080_is_regular() {
        assertEquals(ScreenClass.REGULAR_PHONE, screenClass(2400, 1080, 411))
    }

    @Test fun fold_cover_is_stubby() {
        assertEquals(ScreenClass.STUBBY_PHONE, screenClass(1972, 1248, 475))
    }

    @Test fun tab_s10_fe_is_wide_tablet() {
        assertEquals(ScreenClass.WIDE_TABLET, screenClass(2304, 1440, 822))
    }

    @Test fun unfolded_fold_is_near_square() {
        assertEquals(ScreenClass.NEAR_SQUARE_TABLET, screenClass(2448, 1848, 704))
    }

    @Test fun orientation_does_not_matter() {
        assertEquals(screenClass(2400, 1080, 411), screenClass(1080, 2400, 411))
    }

    @Test fun split_screen_half_of_unfolded_fold_is_a_phone() {
        assertEquals(ScreenClass.STUBBY_PHONE, screenClass(1216, 1848, 463))
    }

    @Test fun unit_is_11mm_from_ydpi() {
        assertEquals(181.9f, unitPx(420f, 2.625f), 0.1f)
    }

    @Test fun implausible_ydpi_falls_back_to_density() {
        assertEquals(11f / 25.4f * 420f, unitPx(0f, 2.625f), 0.01f)
    }
}
