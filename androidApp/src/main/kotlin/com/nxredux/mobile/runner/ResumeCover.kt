package com.nxredux.mobile.runner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import kotlin.math.abs

/**
 * A resume's screenshot over the game view until the resumed game's own frame replaces it (NX Redux's minarch shows
 * the state's screenshot from its window's first moment): no black while the core loads, and no flash of the fresh
 * boot that runs before the state loads. Up before the game view exists it fills the game container; then it takes the
 * game view's bounds, which are what the screenshot captured.
 * Main thread only.
 */
internal class ResumeCover(context: Context, private val container: FrameLayout) {
    private val image = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val follow = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
    private var bitmap: Bitmap? = null
    private var game: View? = null   // the game view once it exists: the cover takes its bounds; before, the container's

    /** Shows [bmp] over [container] (no game view yet: the cover fills it). */
    fun show(bmp: Bitmap) {
        bitmap = bmp
        image.setImageBitmap(bmp)
        container.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addOnLayoutChangeListener(follow)
        place()
    }

    /** The game view is in [container] now: the cover moves just above it (under the overlay and the rest), on its bounds. */
    fun follow(view: View) {
        if (bitmap == null) return
        game = view
        container.removeView(image)
        // Filling the container until the game view is laid out: a 0×0 start would leave a moment of black.
        container.addView(image, container.indexOfChild(view) + 1, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.addOnLayoutChangeListener(follow)
        place()
    }

    /**
     * Fades the cover out over [FADE_MS], then [remove]s it: the game's frame under it is the same picture, so the fade
     * is unseen, and it hides a frame the game's surface may still be late with (a dark frame or the screen behind, on
     * the Fold).
     */
    fun fadeOut() {
        if (bitmap == null) return
        image.animate().alpha(0f).setDuration(FADE_MS).withEndAction { remove() }.start()
    }

    /** Takes the cover away (the resumed frame is up, or the resume failed); safe to call twice. */
    fun remove() {
        image.animate().cancel()
        container.removeOnLayoutChangeListener(follow)
        game?.removeOnLayoutChangeListener(follow)
        container.removeView(image)
        image.setImageDrawable(null)
        bitmap = null
    }

    private fun place() {
        val bmp = bitmap ?: return
        val g = game?.takeIf { it.width > 0 && it.height > 0 }   // laid out; before, the cover keeps the container's size
        val (w, h) = if (g != null) g.width to g.height else container.width to container.height
        if (g != null) (image.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.width != w || lp.height != h || lp.leftMargin != g.left || lp.topMargin != g.top) {
                lp.width = w; lp.height = h; lp.leftMargin = g.left; lp.topMargin = g.top
                image.layoutParams = lp
            }
        }
        val fills = fillsView(bmp.width, bmp.height, w, h)
        image.scaleType = if (fills) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.FIT_CENTER
        image.setBackgroundColor(if (fills) Color.TRANSPARENT else Color.BLACK)
    }

    companion object {
        /** How many of the game's frames after the resume land before the cover fades: its surface shows them a frame or two late. */
        const val SETTLE_FRAMES = 6
        private const val FADE_MS = 150L

        /** How far a screenshot's aspect may be from the view's and still be this view's frame (rounding when scaled down). */
        private const val ASPECT_SLACK = 0.02f

        /** True when a [w]×[h] screenshot is this [viewW]×[viewH] view's frame scaled down: drawn over it whole. */
        fun fillsView(w: Int, h: Int, viewW: Int, viewH: Int): Boolean {
            if (w <= 0 || h <= 0 || viewW <= 0 || viewH <= 0) return false
            val a = w.toFloat() / h
            val v = viewW.toFloat() / viewH
            return abs(a - v) / v <= ASPECT_SLACK
        }

        /** [file] decoded, or null when it is missing or not an image. Blocking I/O. */
        fun decode(file: File): Bitmap? = if (file.isFile) runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() else null
    }
}
