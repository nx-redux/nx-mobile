package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals

class RaNoticesTest {
    @Test fun a_wbfs_ra_does_not_know_suggests_rvz_or_iso() {
        assertEquals("No achievements for this game. WBFS files are often not recognised: try RVZ or ISO",
            RaNotices.unknownGame("Wii Sports.WBFS", hashUnknown = true))
    }

    @Test fun other_files_and_failed_loads_keep_the_plain_notice() {
        assertEquals(RaNotices.UNKNOWN_GAME, RaNotices.unknownGame("Wii Sports.rvz", hashUnknown = true))
        assertEquals(RaNotices.UNKNOWN_GAME, RaNotices.unknownGame("Wii Sports.wbfs", hashUnknown = false))   // offline: says nothing about the file
        assertEquals(RaNotices.UNKNOWN_GAME, RaNotices.unknownGame(null, hashUnknown = true))
    }
}
