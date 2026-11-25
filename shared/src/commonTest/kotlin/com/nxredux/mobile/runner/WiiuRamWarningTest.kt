package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WiiuRamWarningTest {
    private val gb = 1L shl 30

    @Test fun lowRamNotWarnedIsDue() = assertTrue(WiiuRamWarning.due("WIIU", 2 * gb, warned = false))
    @Test fun lowRamWarnedIsNotDue() = assertFalse(WiiuRamWarning.due("WIIU", 2 * gb, warned = true))
    @Test fun fourGbExactlyIsNotDue() = assertFalse(WiiuRamWarning.due("WIIU", 4 * gb, warned = false))
    @Test fun eightGbIsNotDue() = assertFalse(WiiuRamWarning.due("WIIU", 8 * gb, warned = false))
    @Test fun otherConsolesAreNotDue() = assertFalse(WiiuRamWarning.due("N64", 2 * gb, warned = false))
    @Test fun messageIsExact() = assertEquals("Wii U games may not run well on this phone.", WiiuRamWarning.MESSAGE)
}
