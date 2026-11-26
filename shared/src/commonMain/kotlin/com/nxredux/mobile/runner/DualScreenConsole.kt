package com.nxredux.mobile.runner

import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.WiiuOptions

/**
 * The consoles with two screens (DS, 3DS, Wii U): split across two displays, kept off the second display's panel, and
 * drawn as screen regions where the app places them. Console-specific behaviour keeps its own test.
 */
object DualScreenConsole {
    fun appliesTo(tag: String): Boolean = NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag) || WiiuOptions.appliesTo(tag)
}
