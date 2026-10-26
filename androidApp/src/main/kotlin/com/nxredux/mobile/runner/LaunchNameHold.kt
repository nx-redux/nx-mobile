package com.nxredux.mobile.runner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.nxredux.mobile.ui.nx.LaunchTimeline

/**
 * The launch sequence's hold on the game screen: black, with the launched name drawn on the pixels the menu last drew it
 * on (its [LaunchName], in window px, as the menu captured it; the view's own place in the window is taken off), until
 * the core's first frame and at least [LaunchTimeline.HOLD_MS] after it was first shown ([shownAt], kept across a GLES
 * relaunch). [release] fades the name out over [LaunchTimeline.NAME_FADE_MS] (ease-in, wall-clock: the system's animator
 * scale doesn't touch it) on the black, then the view goes and the game cuts in (spec steps 4-6). Over everything in the
 * window, the resume cover included: the player never sees its screenshot before the name goes. Touches stop here while
 * it is up; the activity holds keys back ([holdSwallows]). Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class LaunchNameHold(
    context: Context,
    private val name: LaunchName,
    val shownAt: Long = SystemClock.uptimeMillis(),
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = misans(context, name.bold)
        textSize = name.textSizePx
        if (name.textSizePx > 0f) letterSpacing = name.letterSpacingPx / name.textSizePx
        color = name.argb
    }
    private val inWindow = IntArray(2)
    private var nameAlpha = 1f
    private var fadeStart = -1L   // uptime the name's fade began; -1: not fading
    private val startFade = Runnable { fadeStart = SystemClock.uptimeMillis(); postOnAnimation(fadeStep) }
    private val fadeStep = object : Runnable {
        override fun run() {
            if (fadeStart < 0L) return
            nameAlpha = nameFadeAlpha(SystemClock.uptimeMillis() - fadeStart)
            invalidate()
            if (nameAlpha > 0f) { postOnAnimation(this); return }
            val done = onGone
            remove()
            done?.invoke()
        }
    }
    private var onGone: (() -> Unit)? = null
    private var released = false

    init {
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * The game's first frame is up: the name fades once [LaunchTimeline.HOLD_MS] since [shownAt] have passed, then the
     * view takes itself out and runs [onGone]. Once; a second call is ignored.
     */
    fun release(onGone: () -> Unit) {
        if (released) return
        released = true
        this.onGone = onGone
        postDelayed(startFade, holdRemainingMs(shownAt, SystemClock.uptimeMillis()))
    }

    /** Out at once, no fade (a failure's dialog, the screen closing); [release]'s callback does not run. */
    fun remove() {
        released = true
        onGone = null
        removeCallbacks(startFade)
        removeCallbacks(fadeStep)
        fadeStart = -1L
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (nameAlpha <= 0f) return
        paint.alpha = (Color.alpha(name.argb) * nameAlpha).toInt()
        getLocationInWindow(inWindow)
        for (line in name.lines) {
            val (x, y) = clampLine(line.xPx - inWindow[0], line.baselinePx - inWindow[1], width, height, name.textSizePx)
            canvas.drawText(line.text, x, y, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = true   // the pad under the black waits for the game

    override fun onDetachedFromWindow() {
        removeCallbacks(startFade)
        removeCallbacks(fadeStep)
        super.onDetachedFromWindow()
    }

    companion object {
        /**
         * The menu's MiSans as Compose Multiplatform packs it into the APK's assets. The menu's Bold on its
         * SemiBold-only family resolves to the SemiBold face with no synthetic bold (Compose fakes bold only for a face
         * lighter than 600), so [bold] is that face; otherwise the regular one.
         */
        private const val FONT_DIR = "composeResources/com.nxredux.mobile.shared.res/font/"

        private fun misans(context: Context, bold: Boolean): Typeface =
            runCatching { Typeface.createFromAsset(context.assets, FONT_DIR + if (bold) "misans_semibold.ttf" else "misans_regular.ttf") }
                .getOrDefault(if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
    }
}

/** The name's alpha [elapsedMs] into its fade: 1 → 0 over [LaunchTimeline.NAME_FADE_MS], ease-in (t²). */
internal fun nameFadeAlpha(elapsedMs: Long): Float {
    val t = (elapsedMs.toFloat() / LaunchTimeline.NAME_FADE_MS).coerceIn(0f, 1f)
    return 1f - t * t
}

/**
 * While the launch name's hold is up the player's input waits for the game (the spec: ignored from A until the emulator
 * has the screen): every key is swallowed but the volume keys, and Back too until the hold has been up
 * [LaunchTimeline.GIVE_UP_MS] ([heldForMs]), so a core that never draws can still be left, as the menu gives up.
 */
internal fun holdSwallows(keyCode: Int, heldForMs: Long): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> false
    KeyEvent.KEYCODE_BACK -> heldForMs < LaunchTimeline.GIVE_UP_MS
    else -> true
}

/** How long a hold shown at [shownAt] still has to stay before its name may fade ([LaunchTimeline.HOLD_MS] minimum); 0: now. */
internal fun holdRemainingMs(shownAt: Long, now: Long): Long = (shownAt + LaunchTimeline.HOLD_MS - now).coerceAtLeast(0L)

/**
 * A line's start [x] and [baseline] kept inside a [w] × [h] view: at least one [textSizePx] of it in from the right and
 * below the top (its ascent), so a capture gone stale (a rotation between the menu and this screen) still shows, never
 * throws. A view smaller than the text pins the line at its start.
 */
internal fun clampLine(x: Float, baseline: Float, w: Int, h: Int, textSizePx: Float): Pair<Float, Float> =
    x.coerceIn(0f, maxOf(0f, w - textSizePx)) to baseline.coerceIn(textSizePx, maxOf(textSizePx, h.toFloat()))

private const val KEY_TEXTS = "launch_name_texts"
private const val KEY_XS = "launch_name_xs"
private const val KEY_BASELINES = "launch_name_baselines"
private const val KEY_SIZE = "launch_name_size"
private const val KEY_SPACING = "launch_name_spacing"
private const val KEY_BOLD = "launch_name_bold"
private const val KEY_ARGB = "launch_name_argb"

/** [name] as the intent's extras: [put] gets each key with a String[], FloatArray, Float, Boolean or Int. */
internal fun writeLaunchName(name: LaunchName, put: (String, Any) -> Unit) {
    put(KEY_TEXTS, name.lines.map { it.text }.toTypedArray())
    put(KEY_XS, name.lines.map { it.xPx }.toFloatArray())
    put(KEY_BASELINES, name.lines.map { it.baselinePx }.toFloatArray())
    put(KEY_SIZE, name.textSizePx)
    put(KEY_SPACING, name.letterSpacingPx)
    put(KEY_BOLD, name.bold)
    put(KEY_ARGB, name.argb)
}

/** The name [writeLaunchName] wrote, read through [get]; null when absent, malformed, or with no lines (no hold). */
internal fun readLaunchName(get: (String) -> Any?): LaunchName? {
    @Suppress("UNCHECKED_CAST") val texts = get(KEY_TEXTS) as? Array<String> ?: return null
    val xs = get(KEY_XS) as? FloatArray ?: return null
    val baselines = get(KEY_BASELINES) as? FloatArray ?: return null
    if (texts.isEmpty() || xs.size != texts.size || baselines.size != texts.size) return null
    val size = get(KEY_SIZE) as? Float ?: return null
    if (!(size > 0f)) return null
    return LaunchName(
        texts.indices.map { LaunchLine(texts[it], xs[it], baselines[it]) },
        size, get(KEY_SPACING) as? Float ?: 0f, get(KEY_BOLD) as? Boolean ?: false, get(KEY_ARGB) as? Int ?: Color.WHITE,
    )
}

/** [name] as extras for the game's intent ([launchNameFrom] reads them back). */
internal fun launchNameExtras(name: LaunchName): Bundle = Bundle().apply {
    writeLaunchName(name) { k, v ->
        when (v) {
            is Array<*> -> putStringArray(k, v.map { it as String }.toTypedArray())
            is FloatArray -> putFloatArray(k, v)
            is Float -> putFloat(k, v)
            is Boolean -> putBoolean(k, v)
            is Int -> putInt(k, v)
        }
    }
}

/** The launch name in a game intent's [extras]; null: none (a launch without the sequence). */
@Suppress("DEPRECATION")
internal fun launchNameFrom(extras: Bundle?): LaunchName? = extras?.let { b -> readLaunchName { b.get(it) } }
