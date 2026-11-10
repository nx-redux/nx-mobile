package com.nxredux.mobile.runner

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * Settings → System → Screen care, for OLED screens (both of the Retroid Pocket Nova's): a game's pad skin, frame and
 * panel sit in the same place for hours, and a game left running with nobody playing holds the same picture on.
 * Everything on a screen moves by a dp now and then ([shiftStep]), so edges never sit on the same pixels; a game nobody
 * has touched for a while dims ([careStage] DIM) and then lets Android's screen timeout turn the screens off (SLEEP).
 * The main menu only moves (its tabs, titles and hints stay put while browsing); it never holds the screen on.
 */
internal enum class CareStage { AWAKE, DIM, SLEEP }

internal const val CARE_DIM_AFTER_MS = 5 * 60_000L
internal const val CARE_SLEEP_AFTER_MS = 10 * 60_000L
internal const val CARE_SHIFT_EVERY_MS = 2 * 60_000L
internal const val CARE_DIM_ALPHA = 0.8f
internal const val CARE_TICK_MS = 10_000L

/** What the screens get after [idleMs] without input while the game runs (a paused game sleeps on its own). */
internal fun careStage(idleMs: Long): CareStage = when {
    idleMs >= CARE_SLEEP_AFTER_MS -> CareStage.SLEEP
    idleMs >= CARE_DIM_AFTER_MS -> CareStage.DIM
    else -> CareStage.AWAKE
}

/**
 * The offset in dp for shift step [step]: a walk round a 3×3 grid of -1..1 dp, one dp a step, back to the centre
 * every nine, so nothing moves far enough to see and no edge stays put.
 */
internal fun shiftStep(step: Int): Pair<Int, Int> = SHIFT_WALK[Math.floorMod(step, SHIFT_WALK.size)]

private val SHIFT_WALK = listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

/**
 * One screen's part: [root]'s children move (all but the dim layer, which stays over the whole screen) and a black
 * layer on top dims it. Touches go through the dim layer: it is not clickable.
 */
internal class CareLayer(private val root: ViewGroup) {
    private val scrim = View(root.context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Moves every child (views added since too) to [dx], [dy] dp. */
    fun shift(dx: Int, dy: Int) {
        val d = root.resources.displayMetrics.density
        val x = (dx * d).roundToInt().toFloat()
        val y = (dy * d).roundToInt().toFloat()
        for (i in 0 until root.childCount) {
            val c = root.getChildAt(i)
            if (c === scrim) continue
            c.translationX = x
            c.translationY = y
        }
    }

    /** Dims the screen (a short fade in), or takes the dim away at once (the player is back). */
    fun dim(on: Boolean) {
        scrim.animate().cancel()
        if (on) {
            scrim.bringToFront()   // over views added after it (the in-game menu, a toast layer)
            scrim.visibility = View.VISIBLE
            scrim.animate().alpha(CARE_DIM_ALPHA).setDuration(1_500L).start()
        } else {
            scrim.alpha = 0f
            scrim.visibility = View.GONE
        }
    }
}

/**
 * Screen care for one game: [input] on every key, stick and touch on either screen, [tick] on a timer while the game
 * is in the foreground. [layers] are the screens now (the second display's comes and goes). [onSleepChanged] tells the
 * game its screen-on hold may go (SLEEP) or must come back.
 */
internal class ScreenCare(
    private val now: () -> Long,
    private val layers: () -> List<CareLayer>,
    private val onSleepChanged: () -> Unit,
) {
    var enabled = true
        set(value) {
            if (field == value) return
            field = value
            lastInput = now()
            apply(CareStage.AWAKE)
            step = 0
            layers().forEach { it.shift(0, 0) }
        }

    private var lastInput = now()
    private var lastShift = now()
    private var step = 0
    private var stage = CareStage.AWAKE

    /** True once the game has gone [CARE_SLEEP_AFTER_MS] without input: the screen-on hold may go. */
    val letSleep: Boolean get() = enabled && stage == CareStage.SLEEP

    /** The player did something: the dim goes, the screens stay on. */
    fun input() {
        lastInput = now()
        if (stage != CareStage.AWAKE) apply(CareStage.AWAKE)
    }

    /**
     * Moves the screens on when the time comes, and dims or lets the screens sleep while [running] with no input. A
     * held game (menu, focus pause) is never dimmed here; its idle time starts again when it plays on.
     */
    fun tick(running: Boolean) {
        if (!enabled) return
        val t = now()
        if (t - lastShift >= CARE_SHIFT_EVERY_MS) {
            lastShift = t
            step++
            val (dx, dy) = shiftStep(step)
            layers().forEach { it.shift(dx, dy) }
        }
        if (!running) lastInput = t
        apply(careStage(t - lastInput))
    }

    /** A screen that just came (the second display's window) takes the current offset and dim. */
    fun adopt(layer: CareLayer) {
        if (!enabled) return
        val (dx, dy) = shiftStep(step)
        layer.shift(dx, dy)
        if (stage != CareStage.AWAKE) layer.dim(true)
    }

    private fun apply(next: CareStage) {
        if (next == stage) return
        val wasSleep = stage == CareStage.SLEEP
        val dimmed = stage != CareStage.AWAKE
        stage = next
        val dimNow = next != CareStage.AWAKE
        if (dimNow != dimmed) layers().forEach { it.dim(dimNow) }
        if (wasSleep != (next == CareStage.SLEEP)) onSleepChanged()
    }
}
