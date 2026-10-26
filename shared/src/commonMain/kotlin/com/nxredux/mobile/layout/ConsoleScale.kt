package com.nxredux.mobile.layout

import com.nxredux.mobile.runner.CoreRegistry

/** How a console's picture is scaled on screen, which decides how the pad layout sizes the game. */
enum class ConsoleScale {
    /** Integer-height consoles scaled to a whole multiple of their native lines. */
    WHOLE_HEIGHT,

    /** Handhelds shown at native size (scaled by a whole number). */
    NATIVE,

    /** Consoles that fill the available area. */
    FILL,

    /** Nintendo DS: two stacked screens. */
    DS,

    /** Nintendo 3DS: two screens of different sizes. */
    N3DS,
}

private val nativeIds = setOf("gb", "gbc", "gba", "sgb", "gg", "ngp", "ngpc", "wsc", "lynx", "pkm", "vb", "p8")
private val fillIds = setOf("n64", "ps", "dc", "gc", "wii", "psp")

/** The [ConsoleScale] of the console [tag] belongs to; an unknown tag or a console not listed is [ConsoleScale.WHOLE_HEIGHT]. */
fun consoleScale(tag: String): ConsoleScale = when (CoreRegistry.consoleOf(tag)?.id) {
    in nativeIds -> ConsoleScale.NATIVE
    in fillIds -> ConsoleScale.FILL
    "nds" -> ConsoleScale.DS
    "3ds" -> ConsoleScale.N3DS
    else -> ConsoleScale.WHOLE_HEIGHT
}
