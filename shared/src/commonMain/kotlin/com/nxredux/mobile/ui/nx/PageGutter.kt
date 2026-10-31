package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Every screen's side inset, dp, upright, sideways and on a tablet-class window (short side 600 dp or more): Home List's
 * old gutter plus a list row's inset ([nxListInset]: 16 + 14, 24 + 14, 48 + 14).
 */
internal const val PAGE_INSET_UPRIGHT = 30f
internal const val PAGE_INSET_SIDEWAYS = 38f
internal const val PAGE_INSET_TABLET = 62f

/** The page inset, dp, before any camera cutout: [tablet], else [portrait] or not. */
internal fun pageInset(portrait: Boolean, tablet: Boolean = false): Float = when {
    tablet -> PAGE_INSET_TABLET
    portrait -> PAGE_INSET_UPRIGHT
    else -> PAGE_INSET_SIDEWAYS
}

/** One side's inset from the screen's edge, dp: the page [inset], or the camera [cutout] on that side when deeper. */
internal fun pageInsetSide(inset: Float, cutout: Float): Float = maxOf(inset, cutout)

/**
 * A screen's side insets, each from the screen's edge ([nxPageInset]): where text, titles, tab words, tiles, captions,
 * hint glyphs and top icons start ([start]) and end ([end]). Only a highlight behind a word (the pill, the focus plate)
 * reaches [nxListInset] outside them, from [pillStart] / [pillEnd]; on the camera side it may run under the hole.
 */
internal data class PageInset(val start: Dp, val end: Dp) {
    /** A highlight's edge on the start side: [nxListInset] outside [start]. Never content's. */
    val pillStart: Dp get() = start - nxListInset()
    /** A highlight's edge on the end side: [nxListInset] outside [end]. Never content's. */
    val pillEnd: Dp get() = end - nxListInset()
}

/**
 * Every screen's side insets (the in-game menu's too, not the game screen's), from the screen's edge: [pageInset] on
 * each side, or the camera cutout on that side when deeper ([pageInsetSide]; the Fold cover held sideways: 40 dp on the
 * camera's side, 38 dp on the other; upright the hole is in the status bar, 30 dp both). The page roots pad only the
 * vertical cutout ([nxScreenInsets]), so the side cutout counts here, once. A 3-button navigation bar on a side is a
 * visible edge the roots pad by: the inset is measured from it, and the cutout counts past it.
 */
@Composable
internal fun nxPageInset(): PageInset {
    val window = LocalWindowSize.current
    val inset = pageInset(LocalPortrait.current, tablet = minOf(window.width, window.height) >= 600.dp)
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val bars = restingSystemBars()
    val left = with(density) { (cutout.getLeft(this, dir) - bars.getLeft(this, dir)).coerceAtLeast(0).toDp().value }
    val right = with(density) { (cutout.getRight(this, dir) - bars.getRight(this, dir)).coerceAtLeast(0).toDp().value }
    val (start, end) = if (dir == LayoutDirection.Ltr) left to right else right to left
    return PageInset(pageInsetSide(inset, start).dp, pageInsetSide(inset, end).dp)
}

/**
 * The insets a page root pads by: the system bars, and the cutout above and below only. The side cutout is left to
 * [nxPageInset], so the page lays out from the screen's edge and its backgrounds reach it.
 */
@Composable
internal fun nxScreenInsets(): WindowInsets = restingSystemBars().union(WindowInsets.displayCutout.only(WindowInsetsSides.Vertical))
