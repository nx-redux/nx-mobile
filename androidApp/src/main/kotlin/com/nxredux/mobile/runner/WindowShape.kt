package com.nxredux.mobile.runner

import android.app.Activity
import android.graphics.Point
import android.os.Build
import android.view.WindowInsets
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.portraitBandPx
import com.nxredux.mobile.layout.screenClass
import kotlin.math.min

/**
 * The window as the pad layout measures it, one reading for the renderer, the pad, the input router's cycles and the
 * settings pages so none of them can disagree near a threshold: its size in px, the display cutout's insets on it, and
 * the display's density and ydpi (the button unit).
 */
internal data class WindowShape(
    val widthPx: Int,
    val heightPx: Int,
    val cutoutTop: Int,
    val cutoutLeft: Int,
    val cutoutRight: Int,
    val density: Float,
    val ydpi: Float,
) {
    /** The window's smallest width, dp. */
    val smallestWidthDp: Int get() = (min(widthPx, heightPx) / density).toInt()

    val screenClass: ScreenClass get() = screenClass(widthPx, heightPx, smallestWidthDp)

    /**
     * The portrait pad band's height for this window, px: the main menu's rule on the window's height under the top
     * cutout, so the band keeps its height between the menu and the game.
     */
    val portraitBandPx: Float get() = portraitBandPx(widthPx.toFloat(), (heightPx - cutoutTop).toFloat(), ydpi, density)
}

/**
 * This activity's [WindowShape]: `currentWindowMetrics` on Android 11+ (known from onCreate, insets included). Below
 * that the display's real size (not the window's, so a split-screen window does not follow) and the decor view's cutout
 * (Android 9+; none before), which is only known once the window is attached and its insets dispatched: a read before
 * then (onCreate) sees cutoutTop = 0. The game lays its pad out again when the insets arrive
 * ([PadLayoutController.applyGameTopMargin]).
 */
internal fun Activity.windowShape(): WindowShape {
    val dm = resources.displayMetrics
    if (Build.VERSION.SDK_INT >= 30) {
        val metrics = windowManager.currentWindowMetrics
        val cut = metrics.windowInsets.getInsets(WindowInsets.Type.displayCutout())
        return WindowShape(metrics.bounds.width(), metrics.bounds.height(), cut.top, cut.left, cut.right, dm.density, dm.ydpi)
    }
    val size = Point().also { @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealSize(it) }
    val cutout = if (Build.VERSION.SDK_INT >= 28) window?.decorView?.rootWindowInsets?.displayCutout else null
    return WindowShape(
        size.x, size.y, cutout?.safeInsetTop ?: 0, cutout?.safeInsetLeft ?: 0, cutout?.safeInsetRight ?: 0, dm.density, dm.ydpi,
    )
}

/** This activity's window's [ScreenClass] ([windowShape]; the class does not change on rotation). */
internal fun Activity.screenClass(): ScreenClass = windowShape().screenClass
