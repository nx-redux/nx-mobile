package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.options.offers
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** How the DS 3D Resolution is picked: [AUTO] follows the shown size up to the Max Scale, the others ignore the layout's cap. */
enum class Nds3dMode(val value: String, val label: String) {
    AUTO("auto", "Auto"), QUALITY("quality", "Quality"), PERFORMANCE("performance", "Performance");

    companion object {
        /** The mode stored as [v]; unknown or missing reads as [AUTO]. */
        fun parse(v: String?): Nds3dMode = entries.firstOrNull { it.value == v } ?: AUTO
    }
}

/**
 * The consoles with a 3D Resolution: the core the app sets it on ([coreId]), the core's option and range, the cap an unset
 * game starts with, and the three rows ([modeKey], [maxScaleKey], [scaleKey]) the player's pick is stored under.
 */
enum class Res3dConsole(
    val coreId: String,
    val coreKey: String,
    val coreMax: Int,
    val defaultCap: Int,
    val modeKey: String,
    val maxScaleKey: String,
    val scaleKey: String,
) {
    DS(NdsRenderer.CORE_ID_MELONDSDS, "melonds_opengl_resolution", 8, 4, NdsOptions.MODE_3D, NdsOptions.MAX_SCALE_3D, NdsOptions.SCALE_3D),
    N3DS("azahar", "citra_resolution_factor", 10, 3, "nx_3ds_3d_resolution", "nx_3ds_3d_max_scale", "nx_3ds_3d_scale");

    /** The three stored rows. */
    val keys: Set<String> get() = setOf(modeKey, maxScaleKey, scaleKey)

    /** This console's display maximum ([Nds3dResolution.displayMaxOf], [N3ds3dResolution.displayMaxOf]) for a [window] (px, width to height) of class [screen]. */
    fun displayMaxOf(window: Pair<Int, Int>?, screen: ScreenClass?): Int? = when (this) {
        DS -> Nds3dResolution.displayMaxOf(window, screen)
        N3DS -> N3ds3dResolution.displayMaxOf(window, screen)
    }

    companion object {
        /** The console for a system [tag]; null when it has no 3D Resolution. */
        fun forTag(tag: String): Res3dConsole? = when (tag.trim().uppercase()) {
            "NDS" -> DS
            "3DS" -> N3DS
            else -> null
        }
    }
}

/** The internal resolution (whole scale of 256x192) the melonDS GL renderer draws the 3D at, from the layout and the display. */
object Nds3dResolution {
    /** The most the core's resolution option offers. */
    const val CORE_MAX = 8

    /** The Max Scale an unset game starts with. */
    const val DEFAULT_CAP = 4

    /** The Scale an unset game's Performance draws at. */
    const val DEFAULT_PERFORMANCE_SCALE = 2

    /** The scale of the biggest non-inset screen in [regions] (a part-way scale rounds up); null when there is none. */
    fun currentScale(regions: List<NdsRegion>): Int? = regions.filterNot { it.inset }
        .maxOfOrNull { ceil(it.dst.width / NdsLayout.FRAME_WIDTH - 0.001f).toInt() }

    /**
     * The biggest scale any layout offered on this display reaches, either way up (at least 1): what Quality can use and
     * what Auto and Performance are measured against.
     */
    fun displayMax(windowW: Float, windowH: Float, screen: ScreenClass): Int {
        var best = 1
        for ((w, h) in listOf(windowW to windowH, windowH to windowW)) {
            val landscape = w > h
            for (layout in NdsLayoutChoice.entries) {
                if (!offers(layout, landscape, screen)) continue
                val regions = NdsLayout.regions(PxRect(0f, 0f, w, h), layout, landscape, screen, NdsSettings(), 0f)
                best = maxOf(best, currentScale(regions) ?: 1)
            }
        }
        return best
    }

    /** [displayMax] for a [window] of that px size (width to height) and class; null when either is unknown or the window is empty. */
    fun displayMaxOf(window: Pair<Int, Int>?, screen: ScreenClass?): Int? {
        if (window == null || screen == null || window.first <= 0 || window.second <= 0) return null
        return displayMax(window.first.toFloat(), window.second.toFloat(), screen)
    }

    /** Performance never goes above half the [displayMax], rounded up. */
    fun performanceCeiling(displayMax: Int, console: Res3dConsole = Res3dConsole.DS): Int =
        ceil(displayMax / 2.0).toInt().coerceIn(1, console.coreMax)

    /**
     * The scale to send for [mode]: [current] is the shown scale (null: no screens placed yet, so [displayMax] stands in),
     * [maxScale] the Auto cap and [scale] the Performance pick.
     */
    fun value(
        mode: Nds3dMode, maxScale: Int, scale: Int, current: Int?, displayMax: Int, console: Res3dConsole = Res3dConsole.DS,
    ): Int {
        val v = when (mode) {
            Nds3dMode.AUTO -> minOf(current ?: displayMax, maxScale, displayMax)
            Nds3dMode.QUALITY -> min(current ?: displayMax, displayMax)
            Nds3dMode.PERFORMANCE -> min(scale, performanceCeiling(displayMax, console))
        }
        return v.coerceIn(1, console.coreMax)
    }

    /**
     * [value] from [console]'s stored 3D Resolution rows in [values] (its mode, max scale and scale keys); a row unset or
     * unreadable takes its row default.
     */
    fun valueFor(values: Map<String, String>, current: Int?, displayMax: Int, console: Res3dConsole = Res3dConsole.DS): Int = value(
        mode = Nds3dMode.parse(values[console.modeKey]),
        maxScale = values[console.maxScaleKey]?.toIntOrNull() ?: console.defaultCap,
        scale = values[console.scaleKey]?.toIntOrNull() ?: DEFAULT_PERFORMANCE_SCALE,
        current = current,
        displayMax = displayMax,
        console = console,
    )
}

/** The 3DS (Azahar) 3D Resolution maths: the core draws both screens at a factor of their native size, so scales come from frame widths. */
object N3ds3dResolution {
    private const val TOP_WIDTH = 400f
    private const val BOTTOM_WIDTH = 320f

    /** Azahar's 1x frame (width, height) for each layout the app offers: Stacked, Large screen, Single screen, Side by side. */
    private val FRAMES_1X = listOf(400f to 480f, 400f to 360f, 400f to 240f, 720f to 240f)

    /**
     * Every 1x frame Azahar reports for the layouts the app uses (our patched ComputeLayoutGeometry): Stacked (400 x 480
     * either way round), Large screen (400 x 360; 320 x 360 with the bottom screen prominent), Single screen (400 x 240 top,
     * 320 x 240 bottom), Side by side (720 x 240 either way round). No two reach the same size at factors 1 to 10.
     */
    internal val FRAME_SHAPES_1X = listOf(400 to 480, 400 to 360, 320 to 360, 400 to 240, 320 to 240, 720 to 240)

    /**
     * Azahar's 1x top over bottom frames (Large screen and Stacked: the top, then the bottom screen prominent) with the
     * second screen [proportion] times smaller, as our core patch sizes them (whole px, rounded down).
     */
    internal fun stackedShapes(proportion: Float): List<Pair<Int, Int>> {
        val h = 240 + (240f / proportion).toInt()
        return listOf(400 to h, maxOf(320, (400f / proportion).toInt()) to h)
    }

    /**
     * The 1x frames Azahar can report while [proportion] is in effect: its top over bottom ([stackedShapes]), Single screen
     * and Side by side. For every proportion the app sends no two reach the same size at factors 1 to 10.
     */
    internal fun shapesInUse(proportion: Float): List<Pair<Int, Int>> = stackedShapes(proportion) + listOf(400 to 240, 320 to 240, 720 to 240)

    /**
     * The 1x frames [frameFactor] reads under [proportion]: [FRAME_SHAPES_1X] for an unknown one or the whole ones the app
     * always sent (1, 2); for a fractional one only its [shapesInUse], since its top over bottom shapes can meet the
     * half-size ones at other factors (1.14: 400 x 450 at 4x is 320 x 360 at 5x). A late frame of the proportion before
     * (awaited by the send's layout watch) then reads as unknown, keeping the factor last read.
     */
    internal fun framesTried(proportion: Float?): List<Pair<Int, Int>> =
        if (proportion == null || proportion < 1f || proportion == 1f || proportion == 2f) FRAME_SHAPES_1X else shapesInUse(proportion)

    /**
     * The factor a [frameW] x [frameH] frame was rendered at: the whole f (1 to 10) for which it is f times one of the
     * shapes [framesTried] under the [proportion] in effect (null: not known); null for any other frame (none yet, or a
     * layout this does not know).
     */
    fun frameFactor(frameW: Float, frameH: Float, proportion: Float? = null): Int? {
        val w = frameW.roundToInt()
        val h = frameH.roundToInt()
        if (w <= 0 || h <= 0) return null
        return framesTried(proportion).firstNotNullOfOrNull { (bw, bh) ->
            (w / bw).takeIf { f -> f in 1..Res3dConsole.N3DS.coreMax && w == bw * f && h == bh * f }
        }
    }

    /**
     * The scale a picture [pictureWidth] px wide shows at when it is part of a frame [frameWidth] px wide rendered at
     * [factor] (at least 1): the frame's own factor, so a different frame of the same screen reads the same.
     */
    fun currentScale(pictureWidth: Float, frameWidth: Float, factor: Int): Int =
        floor(pictureWidth * factor / frameWidth).toInt().coerceAtLeast(1)

    /** The scale of the biggest non-inset screen in [regions] (width over its native 400 top / 320 bottom); null when there is none. */
    fun regionScale(regions: List<NdsRegion>): Int? = regions.filterNot { it.inset }.maxOfOrNull {
        floor(it.dst.width / (if (it.screen == NdsScreen.TOP) TOP_WIDTH else BOTTOM_WIDTH)).toInt().coerceAtLeast(1)
    }

    /** The biggest scale any offered layout's 1x frame reaches in a [windowW] x [windowH] px window, either way up (at least 1). */
    @Suppress("UNUSED_PARAMETER")
    fun displayMax(windowW: Float, windowH: Float, screen: ScreenClass): Int {
        var best = 1
        for ((w, h) in listOf(windowW to windowH, windowH to windowW)) {
            for ((fw, fh) in FRAMES_1X) best = maxOf(best, floor(min(w / fw, h / fh)).toInt())
        }
        return best
    }

    /** [displayMax] for a [window] of that px size (width to height) and class; null when either is unknown or the window is empty. */
    fun displayMaxOf(window: Pair<Int, Int>?, screen: ScreenClass?): Int? {
        if (window == null || screen == null || window.first <= 0 || window.second <= 0) return null
        return displayMax(window.first.toFloat(), window.second.toFloat(), screen)
    }
}

/**
 * The game area a DS layout was made for: its size, which screen (class and display id: unfolding or another DeX display is not a resize), which way up,
 * half-folded or flat, and the Dual screen mode. A new
 * value from a change in size alone (a DeX window dragged) waits to settle ([Nds3dSender]); any other change goes at once.
 */
data class Nds3dArea(
    val width: Float,
    val height: Float,
    val screen: ScreenClass,
    val displayId: Int?,
    val landscape: Boolean,
    val halfFolded: Boolean,
    val dual: DualMode,
) {
    /** Only the size changed since [prev] (null: this is the first layout, which is never one). */
    fun isSizeChangeFrom(prev: Nds3dArea?): Boolean =
        prev != null && (width != prev.width || height != prev.height) && copy(width = prev.width, height = prev.height) == prev
}

/**
 * Decides when a new resolution goes to the core. A change from a window resize ([offer] with sizeChange) waits until it has
 * stopped for [settleMs] (a resize drags through many sizes); any other change goes at once. It never returns the value it
 * last returned.
 */
class Nds3dSender(private val settleMs: Long = 300) {
    private var last: Int? = null
    private var held: Int? = null
    private var pendingAt = 0L

    /** A value to send now for [value], or null (unchanged, or held to settle after a size change). */
    fun offer(value: Int, sizeChange: Boolean, nowMs: Long): Int? {
        if (sizeChange) {
            held = value
            pendingAt = nowMs
            return null
        }
        held = null
        return send(value)
    }

    /** A size change's value is waiting to settle: [tick] until it is sent. */
    val pending: Boolean get() = held != null

    /** The pending value once it has settled, else null. */
    fun tick(nowMs: Long): Int? {
        val value = held ?: return null
        if (nowMs - pendingAt < settleMs) return null
        held = null
        return send(value)
    }

    private fun send(value: Int): Int? = if (value == last) null else value.also { last = it }
}
