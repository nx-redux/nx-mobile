package com.nxredux.mobile.runner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.nxredux.mobile.options.PxRect
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A resume's screenshot over the game view until the resumed game's own frame replaces it (NX Redux's minarch shows
 * the state's screenshot from its window's first moment): no black while the core loads, and no flash of the fresh
 * boot that runs before the state loads. The screenshot is the picture alone ([FrontendRenderer.thumbnailPicture]), so
 * the cover sits where the picture is drawn ([spot]: under a pad skin, its screen glass, not the view's middle); until
 * that is known it takes the game view's bounds, and before the game view exists the container's. With [waitForPicture]
 * (a DS / 3DS screenshot is one screen of a layout: the view's bounds would be the wrong spot) it stays hidden until the
 * spot is known instead. A spot on the second display puts it in that display's [secondLayer] (px of its surface) and
 * nothing over main. Main thread only.
 */
internal class ResumeCover(
    context: Context,
    private val container: FrameLayout,
    private val waitForPicture: Boolean,
    private val spot: () -> CoverSpot?,
    private val secondLayer: () -> FrameLayout?,
) {
    private val image = coverImage(context)
    private val follow = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
    private var bitmap: Bitmap? = null
    private var game: View? = null   // the game view once it exists: the cover takes its bounds; before, the container's
    private var second: ImageView? = null   // the cover in the second display's window while its spot is there

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
        second?.animate()?.alpha(0f)?.setDuration(FADE_MS)?.start()
        image.animate().alpha(0f).setDuration(FADE_MS).withEndAction { remove() }.start()
    }

    /** Takes the cover away (the resumed frame is up, the resume failed, or the game is closing); safe to call twice. */
    fun remove() {
        image.animate().cancel()
        container.removeOnLayoutChangeListener(follow)
        game?.removeOnLayoutChangeListener(follow)
        container.removeView(image)
        image.setImageDrawable(null)
        dropSecond()
        bitmap = null
    }

    /** Re-places the cover: the picture was laid out again, or the second display changed ([spot] may have moved). */
    fun place() {
        val bmp = bitmap ?: return
        val at = spot()
        if (at?.display == ThumbnailDisplay.SECOND) {
            image.visibility = View.INVISIBLE
            placeOnSecond(bmp, at.picture)
            return
        }
        dropSecond()
        val p = at?.picture?.takeIf { it.width >= 1f && it.height >= 1f }
        image.visibility = if (p == null && waitForPicture) View.INVISIBLE else View.VISIBLE
        if (p == null && waitForPicture) return
        val g = game?.takeIf { it.width > 0 && it.height > 0 }   // laid out; before, the cover keeps the container's size
        val box = when {
            p != null -> intArrayOf(p.x.roundToInt(), p.y.roundToInt(), p.width.roundToInt(), p.height.roundToInt())
            g != null -> intArrayOf(g.left, g.top, g.width, g.height)
            else -> null
        }
        val (w, h) = if (box != null) box[2] to box[3] else container.width to container.height
        if (box != null) (image.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.width != w || lp.height != h || lp.leftMargin != box[0] || lp.topMargin != box[1]) {
                lp.width = w; lp.height = h; lp.leftMargin = box[0]; lp.topMargin = box[1]
                image.layoutParams = lp
            }
        }
        fit(image, bmp, w, h)
    }

    /** The cover over [picture] in the second display's layer; nowhere until both are there (never over the wrong screen). */
    private fun placeOnSecond(bmp: Bitmap, picture: PxRect?) {
        val layer = secondLayer()
        val p = picture?.takeIf { it.width >= 1f && it.height >= 1f }
        if (layer == null || p == null) { dropSecond(); return }
        val x = p.x.roundToInt()
        val y = p.y.roundToInt()
        val w = p.width.roundToInt()
        val h = p.height.roundToInt()
        val view = second?.takeIf { it.parent === layer } ?: run {
            dropSecond()   // a window that went: its replacement takes a new view
            coverImage(layer.context).also {
                it.setImageBitmap(bmp)
                it.alpha = image.alpha
                layer.addView(it, FrameLayout.LayoutParams(w, h).apply { leftMargin = x; topMargin = y })
                second = it
            }
        }
        (view.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.width != w || lp.height != h || lp.leftMargin != x || lp.topMargin != y) {
                lp.width = w; lp.height = h; lp.leftMargin = x; lp.topMargin = y
                view.layoutParams = lp
            }
        }
        fit(view, bmp, w, h)
    }

    private fun dropSecond() {
        val view = second ?: return
        second = null
        view.animate().cancel()
        (view.parent as? ViewGroup)?.removeView(view)
        view.setImageDrawable(null)
    }

    private fun fit(view: ImageView, bmp: Bitmap, w: Int, h: Int) {
        val fills = fillsView(bmp.width, bmp.height, w, h)
        view.scaleType = if (fills) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.FIT_CENTER
        view.setBackgroundColor(if (fills) Color.TRANSPARENT else Color.BLACK)
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

        private fun coverImage(context: Context) = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

        /** [file] decoded, or null when it is missing or not an image. Blocking I/O. */
        fun decode(file: File): Bitmap? = if (file.isFile) runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() else null
    }
}
