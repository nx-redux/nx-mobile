package com.nxredux.mobile.runner

import com.nxredux.mobile.options.WiiuOptions

/**
 * Cemu is heavy: a phone under 3.5 GiB of total RAM gets one warning, then the game starts as asked. The line sits
 * below 4 GiB because a phone sold as 4 GB reports only about 3.6 to 3.8 GiB in total.
 */
object WiiuRamWarning {
    const val MESSAGE = "Wii U games may not run well on this phone."
    private const val MIN_TOTAL_RAM = 3584L shl 20

    fun due(tag: String, totalRamBytes: Long, warned: Boolean): Boolean =
        WiiuOptions.appliesTo(tag) && totalRamBytes < MIN_TOTAL_RAM && !warned
}
