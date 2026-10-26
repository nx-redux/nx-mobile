package com.nxredux.mobile.runner

/**
 * The launched item's name as the menu last drew it, for the game screen to draw again on the same pixels while the core
 * loads (the launch sequence's hold): its [lines], the text size in px (after the selected name's scale), its
 * letter-spacing in px, weight ([bold]) and colour ([argb], the accent it ended on). All in screen pixels.
 */
data class LaunchName(val lines: List<LaunchLine>, val textSizePx: Float, val letterSpacingPx: Float, val bold: Boolean, val argb: Int)

/** One line of a [LaunchName]: its [text], start x and baseline y in screen pixels. */
data class LaunchLine(val text: String, val xPx: Float, val baselinePx: Float)
