package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether [restingSystemBars] may use the window's resting insets: only a host whose root view gets the window's insets
 * unconsumed turns it on (MainActivity's own window). Off (live insets) elsewhere, e.g. the second screen's
 * presentation, which pads its root for the bars itself and hands Compose none.
 */
val LocalRestingBars = staticCompositionLocalOf { false }

/**
 * The system bars' insets as they will be once any show or hide animation has run, for the menu's pages (which never
 * hide the bars themselves). [WindowInsets.systemBars] follows the bars' animation frame by frame: back from an
 * immersive game, the bars are shown again over ~100 ms and the page would lay out without them first, then shrink.
 * Bars hidden for good (by the system or the OEM) still count as hidden. Without [LocalRestingBars], the live insets.
 */
@Composable
expect fun restingSystemBars(): WindowInsets

/**
 * One side's inset: the window's resting value ([restingPx]) when the host allows it ([enabled]) and it is known, else
 * the live (animated) one.
 */
internal fun restingInset(livePx: Int, restingPx: Int?, enabled: Boolean): Int =
    if (enabled && restingPx != null) restingPx else livePx
