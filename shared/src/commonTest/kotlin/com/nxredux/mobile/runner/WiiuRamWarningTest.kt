package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WiiuRamWarningTest {
    private val gb = 1L shl 30
    private val mb = 1L shl 20

    @Test fun lowRamNotWarnedIsDue() = assertTrue(WiiuRamWarning.due("WIIU", 2 * gb, warned = false))
    @Test fun lowRamWarnedIsNotDue() = assertFalse(WiiuRamWarning.due("WIIU", 2 * gb, warned = true))
    @Test fun threeGbIsDue() = assertTrue(WiiuRamWarning.due("WIIU", 3 * gb, warned = false))
    // a phone sold as 4 GB reports about 3.6 to 3.8 GiB in total
    @Test fun aFourGbPhonesReportedTotalIsNotDue() = assertFalse(WiiuRamWarning.due("WIIU", 3789 * mb, warned = false))
    @Test fun threeAndAHalfGbExactlyIsNotDue() = assertFalse(WiiuRamWarning.due("WIIU", 3584 * mb, warned = false))
    @Test fun eightGbIsNotDue() = assertFalse(WiiuRamWarning.due("WIIU", 8 * gb, warned = false))
    @Test fun theTagIsMatchedAsTheWiiUOptionsMatchIt() = assertTrue(WiiuRamWarning.due(" wiiu", 2 * gb, warned = false))
    @Test fun otherConsolesAreNotDue() = assertFalse(WiiuRamWarning.due("N64", 2 * gb, warned = false))
    @Test fun messageIsExact() = assertEquals("Wii U games may not run well on this phone.", WiiuRamWarning.MESSAGE)
}
