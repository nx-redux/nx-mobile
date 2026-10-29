package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every page's side gutter, dp, upright and sideways: where its text, tiles, tab words and title start. */
internal const val PAGE_GUTTER_UPRIGHT = 16f
internal const val PAGE_GUTTER_SIDEWAYS = 24f

/** The page's side gutter, dp, [portrait] or not ([PAGE_GUTTER_UPRIGHT], [PAGE_GUTTER_SIDEWAYS]). */
internal fun pageGutter(portrait: Boolean): Float = if (portrait) PAGE_GUTTER_UPRIGHT else PAGE_GUTTER_SIDEWAYS

/** [pageGutter] for the screen showing ([LocalPortrait]). */
@Composable
internal fun nxPageGutter(): Dp = pageGutter(LocalPortrait.current).dp
