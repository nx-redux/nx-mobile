package com.nxredux.mobile.runner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import com.nxredux.mobile.skin.skinTiles
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.SkinPackSource
import com.nxredux.mobile.ui.nx.SkinGlassPass
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
    /** The arcade's turbo caps (turbo A / B): nothing yet, the frontend turbo gives them their meaning; never core input. */
    TURBO_A, TURBO_B,
    /** A Dreamcast skin's swap between the stick and the d-pad on a tight layout. */
    STICK_SWAP,
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
    /** The skin's analog stick: x right, y down, -1..1 ([SkinPadTouch]). */
    private val onStick: (Float, Float) -> Unit = { _, _ -> },
    /** The 3DS skin's C-stick nub (control `CSTICK`), as [onStick]. */
    private val onCStick: (Float, Float) -> Unit = { _, _ -> },
    /** A finger on no cap after one on a cap: on to the game's touch screen (view px, [SkinGlassPass]). */
    onGlass: (Float, Float, Boolean) -> Unit = { _, _, _ -> },
) : FrameLayout(context) {
    private val glass = SkinGlassPass(onGlass)

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
    /** On a layout that swaps them, the stick (true) or the d-pad is shown and pressed ([SkinPlacement.forStick]). */
    var useStick: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }
    /** [placement] as drawn and hit-tested now: without the strip's shortcuts while [stripHidden]. */
    private var shown: SkinPlacement? = null
    private fun refresh() {
        val p = placement?.forStick(useStick)
        val ids = p?.spec?.let { sp -> (listOfNotNull(sp.frame?.strip) + sp.pieces.mapNotNull { it.strip }).flatMap { st -> st.labels.map { it.id } } }?.toSet().orEmpty()
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
    private val holes = Path()
    private val src = Rect()
    private val dst = Rect()
    private val touch = SkinPadTouch(onPad, { id, down -> onAction(SkinAction.valueOf(id), down) }, ::tick, ::invalidate, onStick, onCStick)
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
        // the screens are holes in the background (it runs under them otherwise), cut to the glasses' rounded corners; the
        // frame and the other pieces are drawn whole over the game: their own holes are cut round, so they round its corners
        canvas.save()
        holes.reset()
        for (g in p.glasses) holes.addRoundRect(g.rect.x, g.rect.y, g.rect.x + g.rect.width, g.rect.y + g.rect.height, g.corner, g.corner, Path.Direction.CW)
        canvas.clipOutPath(holes)
        for (c in p.cells) drawCell(canvas, pic, c, p.k)
        canvas.restore()
        canvas.save()
        parts?.let { pp ->
            for (piece in p.pieces) {
                if (piece.knob) continue   // the stick's knob moves: drawn over the layer ([dispatchDraw])
                src.set(piece.src.x.roundToInt(), piece.src.y.roundToInt(), (piece.src.x + piece.src.width).roundToInt(), (piece.src.y + piece.src.height).roundToInt())
                dst.set(piece.dst.x.roundToInt(), piece.dst.y.roundToInt(), (piece.dst.x + piece.dst.width).roundToInt(), (piece.dst.y + piece.dst.height).roundToInt())
                canvas.drawBitmap(pp, src, dst, paint)
            }
        }
        for (c in p.controls) {
            val text = c.spec.label ?: continue
            if (c.spec.labelH <= 0f) continue
            label.textSize = c.spec.labelH / SKIN_LABEL_CAP
            val dx = c.spec.labelDx
            label.textAlign = if (dx < 0f) Paint.Align.RIGHT else if (dx > 0f) Paint.Align.LEFT else Paint.Align.CENTER
            canvas.drawText(text, c.x + dx, c.y + c.spec.labelDy + c.spec.labelH / 2f, label)   // the baseline: cap height centred
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
        // each stick's knob, where its thumb has it
        val p = shown
        val pp = parts
        if (p != null && pp != null) {
            for (piece in p.pieces) {
                if (!piece.knob) continue
                val (kx, ky) = touch.knobOffset(piece.knobOf)
                src.set(piece.src.x.roundToInt(), piece.src.y.roundToInt(), (piece.src.x + piece.src.width).roundToInt(), (piece.src.y + piece.src.height).roundToInt())
                dst.set((piece.dst.x + kx).roundToInt(), (piece.dst.y + ky).roundToInt(), (piece.dst.x + kx + piece.dst.width).roundToInt(), (piece.dst.y + ky + piece.dst.height).roundToInt())
                canvas.drawBitmap(pp, src, dst, paint)
            }
        }
        for (shape in touch.pressedShapes()) when (shape) {   // a held cap's shade, the one thing painted
            is SkinPressShape.Circle -> canvas.drawCircle(shape.x, shape.y, shape.r, shade)
            is SkinPressShape.Box -> canvas.drawRoundRect(shape.left, shape.top, shape.right, shape.bottom, shape.corner, shape.corner, shade)
        }
    }

    /** One slice: copied where its part is fixed, laid in mirrored tiles where it repeats ([skinTiles]). */
    private fun drawCell(canvas: Canvas, pic: Bitmap, c: SkinCell, k: Float) {
        for (t in skinTiles(c, k, SKIN_STRETCH_MAX)) {
            src.set(t.src.x.roundToInt(), t.src.y.roundToInt(), (t.src.x + t.src.width).roundToInt(), (t.src.y + t.src.height).roundToInt())
            dst.set(t.dst.x.roundToInt(), t.dst.y.roundToInt(), (t.dst.x + t.dst.width).roundToInt(), (t.dst.y + t.dst.height).roundToInt())
            if (src.width() <= 0 || src.height() <= 0 || dst.width() <= 0 || dst.height() <= 0) continue
            if (!t.flipX && !t.flipY) { canvas.drawBitmap(pic, src, dst, paint); continue }
            val save = canvas.save()
            canvas.scale(if (t.flipX) -1f else 1f, if (t.flipY) -1f else 1f, dst.exactCenterX(), dst.exactCenterY())
            canvas.drawBitmap(pic, src, dst, paint)
            canvas.restoreToCount(save)
        }
    }

    private fun tick() {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onDetachedFromWindow() {
        touch.releaseAll()
        glass.cancel()
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return touch.press(e.getPointerId(0).toLong(), e.getX(0), e.getY(0))   // false: the game view gets it
            MotionEvent.ACTION_POINTER_DOWN -> {
                val id = e.getPointerId(i).toLong()
                glass.down(id, e.getX(i), e.getY(i), onCap = touch.press(id, e.getX(i), e.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> for (p in 0 until e.pointerCount) {
                val id = e.getPointerId(p).toLong()
                touch.move(id, e.getX(p), e.getY(p)); glass.move(id, e.getX(p), e.getY(p))
            }
            MotionEvent.ACTION_POINTER_UP -> e.getPointerId(i).toLong().let { touch.release(it); glass.up(it) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { touch.releaseAll(); glass.cancel() }
        }
        return true
    }

}
