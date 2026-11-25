package com.nxredux.mobile.runner

/** Cemu is heavy: a phone under 4 GB of RAM gets one warning, then the game starts as asked. */
object WiiuRamWarning {
    const val MESSAGE = "Wii U games may not run well on this phone."
    private const val MIN_TOTAL_RAM = 4L shl 30

    fun due(tag: String, totalRamBytes: Long, warned: Boolean): Boolean =
        tag == "WIIU" && totalRamBytes < MIN_TOTAL_RAM && !warned
}
