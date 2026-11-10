package com.nxredux.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TopFactsTest {
    @Test fun region_is_the_first_region_tag() {
        assertEquals("USA", regionOf("Zelda (USA).gb"))
        assertEquals("Europe", regionOf("Game (Europe) (En,Fr).sfc"))
        assertEquals("Japan", regionOf("Game (Rev 1) (Japan).nes"))
        assertNull(regionOf("Game.sfc"))
        assertNull(regionOf("Game (En,Fr).sfc"))
    }

    @Test fun format_is_the_upper_case_extension() {
        assertEquals("CHD", formatOf("a.chd"))
        assertEquals("SFC", formatOf("A (USA).sfc"))
        assertEquals("", formatOf("noext"))
    }
}
