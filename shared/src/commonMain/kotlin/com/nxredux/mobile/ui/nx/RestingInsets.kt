package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/**
 * The system bars' insets as they will be once any show or hide animation has run, for the menu's pages (which never
 * hide the bars themselves). [WindowInsets.systemBars] follows the bars' animation frame by frame: back from an
 * immersive game, the bars are shown again over ~100 ms and the page would lay out without them first, then shrink.
 * Bars hidden for good (by the system or the OEM) still count as hidden.
 */
@Composable
expect fun restingSystemBars(): WindowInsets

/** One side's inset: the window's resting value when known ([restingPx]), else the live (animated) one. */
internal fun restingInset(livePx: Int, restingPx: Int?): Int = restingPx ?: livePx
