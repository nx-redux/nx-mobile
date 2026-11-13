package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.Composable

/** The display's physical vertical dpi, for sizing UI in real centimetres ([HandheldShell]'s pad band); 0f if the platform can't report it. */
@Composable
expect fun rememberDisplayYdpi(): Float

/**
 * Whether a physical game controller is connected; updates on connect and disconnect. With none and no on-screen pad
 * the screens show touch buttons instead of the hint bar ([footerModeFor]).
 */
@Composable
expect fun rememberControllerConnected(): Boolean

/**
 * Whether the device has a touchscreen (most Android phones and handhelds). A game list's Recent, Search and Layout
 * icons show only then; without one, Settings → Layouts changes the layout and START searches.
 */
@Composable
expect fun rememberHasTouchscreen(): Boolean
