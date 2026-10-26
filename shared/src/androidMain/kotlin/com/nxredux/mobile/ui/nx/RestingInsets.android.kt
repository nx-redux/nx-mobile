package com.nxredux.mobile.ui.nx

import android.graphics.Insets
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Composable
actual fun restingSystemBars(): WindowInsets {
    val view = LocalView.current
    val live = WindowInsets.systemBars
    return remember(view, live) { RestingSystemBars(view, live) }
}

/**
 * The window's insets as last dispatched ([View.getRootWindowInsets]): during an insets animation that is its end
 * state, while [live] (Compose's) is the animation's current frame. [live] is still read first, so a change of it
 * (the animation's start, or a real change of the bars) lays the page out again with the resting value. Before API 30
 * there are no bar animations to follow: [live] as it is.
 */
private class RestingSystemBars(private val view: View, private val live: WindowInsets) : WindowInsets {
    private fun resting(): Insets? =
        if (Build.VERSION.SDK_INT >= 30) view.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars()) else null

    override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
        restingInset(live.getLeft(density, layoutDirection), resting()?.left)

    override fun getTop(density: Density) = restingInset(live.getTop(density), resting()?.top)

    override fun getRight(density: Density, layoutDirection: LayoutDirection) =
        restingInset(live.getRight(density, layoutDirection), resting()?.right)

    override fun getBottom(density: Density) = restingInset(live.getBottom(density), resting()?.bottom)

    override fun equals(other: Any?) = other is RestingSystemBars && other.view === view && other.live == live
    override fun hashCode() = 31 * System.identityHashCode(view) + live.hashCode()
    override fun toString() = "RestingSystemBars($live)"
}
