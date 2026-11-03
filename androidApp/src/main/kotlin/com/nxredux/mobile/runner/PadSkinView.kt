package com.nxredux.mobile.runner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.skin.PlacedControl
import com.nxredux.mobile.skin.SkinCell
import com.nxredux.mobile.skin.SkinPackManifest
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.SkinPackSource
import com.nxredux.mobile.ui.nx.SkinPadTouch
import com.nxredux.mobile.ui.nx.SkinPressShape
import com.nxredux.mobile.ui.nx.TouchShortcut
import com.nxredux.mobile.ui.nx.touchShortcutLabel
import com.nxredux.mobile.ui.nx.SKIN_PRESS_SHADE
import com.nxredux.mobile.ui.nx.SKIN_LABEL_CAP
import com.nxredux.mobile.ui.nx.SKIN_LABEL_GREY
import com.nxredux.mobile.ui.nx.SKIN_STRETCH_MAX
import androidx.compose.ui.graphics.asImageBitmap
import com.swordfish.libretrodroid.NxLog
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A pad skin pack: its [manifest] and its pictures, read through [open] by file name and decoded once each (a game
 * uses one or two: portrait and landscape). The built-in packs live in the app's assets under `skins/<id>/`.
 */
internal class PadSkinPack(val manifest: SkinPackManifest, private val open: (String) -> InputStream) {
    private val pictures = HashMap<String, Bitmap?>()

    /** The picture [name], or null (logged) when it cannot be read. */
    fun picture(name: String): Bitmap? = pictures.getOrPut(name) {
        runCatching { open(name).use { BitmapFactory.decodeStream(it) } }
            .onFailure { NxLog.w(TAG, "skin ${manifest.id}: can't read $name: ${it.message}") }.getOrNull()
    }

    /** This pack for the menu's Compose skin ([SkinPackSource]), the same pictures. */
    fun toSource(): SkinPackSource = SkinPackSource(manifest) { picture(it)?.asImageBitmap() }

    companion object {
        private const val TAG = "PadSkin"

        /** The built-in pack [id] from the assets, or null (logged) when it is missing or not readable. */
        fun builtIn(context: Context, id: String): PadSkinPack? {
            val assets = context.assets
            val text = runCatching { assets.open("skins/$id/skin.json").bufferedReader().use { it.readText() } }.getOrNull()
            val manifest = text?.let(SkinPackManifest::parse)
            if (manifest == null) { NxLog.w(TAG, "skin $id: no readable skin.json"); return null }
            return PadSkinPack(manifest) { assets.open("skins/$id/$it") }
        }
    }
}

/** What a skin's buttons do beyond the pad's own buttons. */
/** A strip slot's written label: its cap height in picture px (MENU's is 17). */
private const val STRIP_CAP = 17f

internal enum class SkinAction(val slot: TouchSlot? = null) {
    MIC, SWAP, SAVE, LOAD, REWIND, FAST,
    /** The frame strip's touch shortcut slots: the player's actions for them ([PadSkinView.shortcuts]). */
    TOUCH_1(TouchSlot.ONE), TOUCH_2(TouchSlot.TWO), TOUCH_3(TouchSlot.THREE), TOUCH_4(TouchSlot.FOUR),
}

/**
 * Draws a [SkinPlacement] (the skin assembled for this window) over the game and takes its buttons' touches: the pad
 * buttons go to [onPad], the rest to [onAction] (press and release). A touch on no button falls through to the game
 * view under it (the DS touch screen). The picture is drawn as rendered: slices copied or tiled, then a layout's pieces
 * (if it is built from them) and the labels; the only thing painted is a held cap's shade. The skin is its own layer
 * ([layer], recorded once per placement), the shades drawn over it here: a press records a few shapes, not the skin again.
 */
@SuppressLint("ViewConstructor")
internal class PadSkinView(
    context: Context,
    private val pack: PadSkinPack,
    private val onPad: (PadEvent) -> Unit,
    private val onAction: (SkinAction, Boolean) -> Unit,
    private val haptics: Boolean,
    /** The view's size changed (insets, a fold, a resize): the layout is worked out again for it. */
    private val onResized: () -> Unit,
) : FrameLayout(context) {

    /** The skin itself: its own view, so its drawing is kept and replayed while only the shades over it change. */
    private val layer = object : View(context) {
        override fun onDraw(canvas: Canvas) = drawSkin(canvas)
    }.also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    /** The layout drawn and hit-tested; set by the pad layout for this view's size. */
    var placement: SkinPlacement? = null
        set(value) {
            if (field == value) return
            field = value
            picture = value?.let { pack.picture(it.spec.image) }
            parts = value?.spec?.parts?.let { pack.picture(it) }
            refresh()
        }
    /** The touch shortcut slots' actions: the strip's TOUCH_n buttons write theirs; an empty slot is plain strip, not pressed. */
    var shortcuts: Map<TouchSlot, TouchShortcut> = emptyMap()
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }
    /** The in-game menu is open: the frame strip's shortcuts (SAVE, LOAD, ...) neither drawn nor pressed, its black left. */
    var stripHidden: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }
    /** [placement] as drawn and hit-tested now: without the strip's shortcuts while [stripHidden]. */
    private var shown: SkinPlacement? = null
    private fun refresh() {
        val p = placement
        val ids = p?.spec?.frame?.strip?.labels?.map { it.id }?.toSet().orEmpty()
        val gone = if (stripHidden) ids else ids.filter { id -> slotOf(id)?.let { it !in shortcuts } ?: false }.toSet()
        shown = if (p == null || gone.isEmpty()) p
            else p.copy(pieces = if (stripHidden) p.pieces.filterNot { it.strip } else p.pieces, controls = spread(p.controls.filterNot { it.spec.id in gone }, p, ids))
        touch.placement = shown
        layer.invalidate()
        invalidate()
    }
    private var picture: Bitmap? = null
    private var parts: Bitmap? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()
    private val touch = SkinPadTouch(onPad, { id, down -> onAction(SkinAction.valueOf(id), down) }, ::tick, ::invalidate)
    /** The labels under the buttons, written here (the picture has none there). */
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SKIN_LABEL_GREY
        textAlign = Paint.Align.CENTER
        typeface = runCatching { Typeface.createFromAsset(context.assets, "composeResources/com.nxredux.mobile.shared.res/font/misans_semibold.ttf") }
            .getOrDefault(Typeface.DEFAULT_BOLD)
    }
    private val slotLabel = Paint(label)
    /**
     * The strip's filled slots spread over the strip's whole width, an even share each (two: centred in its halves),
     * in slot order; the strip's other buttons as placed.
     */
    private fun spread(controls: List<PlacedControl>, p: SkinPlacement, ids: Set<String>): List<PlacedControl> {
        val all = p.controls.filter { it.spec.id in ids && slotOf(it.spec.id) != null }
        val kept = controls.filter { it in all }
        if (all.isEmpty() || kept.isEmpty() || kept.size == all.size) return controls
        val share = all.first().spec.hw * 2f
        val left = all.minOf { it.x } - share / 2f
        val newShare = share * all.size / kept.size
        val moved = kept.mapIndexed { i, c -> c to c.copy(spec = c.spec.copy(hw = newShare / 2f), x = left + newShare * (i + 0.5f)) }.toMap()
        return controls.map { moved[it] ?: it }
    }
    private fun slotOf(id: String): TouchSlot? = SkinAction.entries.firstOrNull { it.name == id }?.slot
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((SKIN_PRESS_SHADE * 255).roundToInt(), 0, 0, 0) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) onResized()
    }

    /** The skin: slices, pieces, labels (the [layer]'s drawing). */
    private fun drawSkin(canvas: Canvas) {
        val p = shown ?: return
        val pic = picture ?: return
        // the screens are holes: the game shows through them (the background runs under them otherwise)
        canvas.save()
        for (g in p.glasses) canvas.clipOutRect(g.rect.x, g.rect.y, g.rect.x + g.rect.width, g.rect.y + g.rect.height)
        for (c in p.cells) drawCell(canvas, pic, c, p.k)
        parts?.let { pp ->
            for (piece in p.pieces) {
                src.set(piece.src.x.roundToInt(), piece.src.y.roundToInt(), (piece.src.x + piece.src.width).roundToInt(), (piece.src.y + piece.src.height).roundToInt())
                dst.set(piece.dst.x.roundToInt(), piece.dst.y.roundToInt(), (piece.dst.x + piece.dst.width).roundToInt(), (piece.dst.y + piece.dst.height).roundToInt())
                canvas.drawBitmap(pp, src, dst, paint)
            }
        }
        for (c in p.controls) {
            val text = c.spec.label ?: continue
            if (c.spec.labelH <= 0f) continue
            label.textSize = c.spec.labelH / SKIN_LABEL_CAP
            canvas.drawText(text, c.x, c.y + c.spec.labelDy + c.spec.labelH / 2f, label)   // the baseline: cap height centred
        }
        // the strip's touch slots: the action's short name, in the label grey (a running toggle in white)
        for (c in p.controls) {
            val sc = slotOf(c.spec.id)?.let(shortcuts::get) ?: continue
            val cap = STRIP_CAP * p.k
            slotLabel.textSize = cap / SKIN_LABEL_CAP
            slotLabel.color = if (sc.on) android.graphics.Color.WHITE else SKIN_LABEL_GREY
            canvas.drawText(touchShortcutLabel(sc.action).uppercase(), c.x, c.y + cap / 2f, slotLabel)
        }
        canvas.restore()
    }

    /** The held caps' shades over the skin layer (a press redraws only these). */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        for (shape in touch.pressedShapes()) when (shape) {   // a held cap's shade, the one thing painted
            is SkinPressShape.Circle -> canvas.drawCircle(shape.x, shape.y, shape.r, shade)
            is SkinPressShape.Box -> canvas.drawRoundRect(shape.left, shape.top, shape.right, shape.bottom, shape.corner, shape.corner, shade)
        }
    }

    /** One slice: copied where its part is fixed, tiled in picture-size steps where it repeats (the last tile cut). */
    private fun drawCell(canvas: Canvas, pic: Bitmap, c: SkinCell, k: Float) {
        val tileW = c.src.width * k
        val tileH = c.src.height * k
        if (tileW <= 0f || tileH <= 0f || c.dst.width <= 0f || c.dst.height <= 0f) return
        // a plain strip a few px across is stretched, not tiled: the same look in one draw (tiled, a big window's
        // strips would be thousands of draws a frame)
        val tileX = c.tileX && c.src.width > SKIN_STRETCH_MAX
        val tileY = c.tileY && c.src.height > SKIN_STRETCH_MAX
        val nx = if (tileX) ceil(c.dst.width / tileW - 0.001f).toInt().coerceAtLeast(1) else 1
        val ny = if (tileY) ceil(c.dst.height / tileH - 0.001f).toInt().coerceAtLeast(1) else 1
        for (j in 0 until ny) for (i in 0 until nx) {
            val x0 = c.dst.x + i * (if (tileX) tileW else c.dst.width)
            val y0 = c.dst.y + j * (if (tileY) tileH else c.dst.height)
            val w = if (tileX) min(tileW, c.dst.x + c.dst.width - x0) else c.dst.width
            val h = if (tileY) min(tileH, c.dst.y + c.dst.height - y0) else c.dst.height
            val sw = if (tileX) w / k else c.src.width
            val sh = if (tileY) h / k else c.src.height
            src.set(c.src.x.roundToInt(), c.src.y.roundToInt(), (c.src.x + sw).roundToInt(), (c.src.y + sh).roundToInt())
            dst.set(x0.roundToInt(), y0.roundToInt(), (x0 + w).roundToInt(), (y0 + h).roundToInt())
            if (src.width() > 0 && src.height() > 0 && dst.width() > 0 && dst.height() > 0) canvas.drawBitmap(pic, src, dst, paint)
        }
    }

    private fun tick() {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onDetachedFromWindow() {
        touch.releaseAll()
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return touch.press(e.getPointerId(0).toLong(), e.getX(0), e.getY(0))   // false: the game view gets it
            MotionEvent.ACTION_POINTER_DOWN -> touch.press(e.getPointerId(i).toLong(), e.getX(i), e.getY(i))
            MotionEvent.ACTION_MOVE -> for (p in 0 until e.pointerCount) touch.move(e.getPointerId(p).toLong(), e.getX(p), e.getY(p))
            MotionEvent.ACTION_POINTER_UP -> touch.release(e.getPointerId(i).toLong())
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touch.releaseAll()
        }
        return true
    }

}
