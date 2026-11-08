package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The menu's posture reaches the game's process in the launch intent (the game runs in `:game`, where the menu's
 * [PostureHint] does not exist). The round trip runs on the extras' key/value encoding: a Bundle needs a device.
 */
class PostureSeedTest {
    private fun roundTrip(seed: PostureSeed?): PostureSeed? {
        val extras = HashMap<String, Int>()
        writePostureSeed(seed) { k, v -> extras[k] = v }
        return readPostureSeed { extras[it] }
    }

    @Test fun a_half_folded_menu_reaches_the_game_with_its_hinge_and_window() {
        val seed = PostureSeed(Posture.Tabletop(1100, 1140), 1812, 2176)
        assertEquals(seed, roundTrip(seed))
        // The game's window of the same size starts at the hinge.
        val read = roundTrip(seed)!!
        assertEquals(Posture.Tabletop(1100, 1140), seededPosture(read.posture, read.windowW, read.windowH, 1812, 2176))
    }

    @Test fun a_flat_menu_or_none_seeds_nothing() {
        assertEquals(PostureSeed(Posture.Flat, 1080, 2400), roundTrip(PostureSeed(Posture.Flat, 1080, 2400)))
        assertNull(roundTrip(null))
        assertNull(readPostureSeed { null })
        // A half-written seed (no window size) is none.
        assertNull(readPostureSeed { if (it == KEY_POSTURE_HINGE_TOP) 5 else null })
    }
}
