package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
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
    private val cap: Int,
    val modeKey: String,
    val maxScaleKey: String,
    val scaleKey: String,
) {
    DS(NdsRenderer.CORE_ID_MELONDSDS, "melonds_opengl_resolution", 8, 4, NdsOptions.MODE_3D, NdsOptions.MAX_SCALE_3D, NdsOptions.SCALE_3D),
    N3DS("azahar", "citra_resolution_factor", 10, 3, "nx_3ds_3d_resolution", "nx_3ds_3d_max_scale", "nx_3ds_3d_scale"),
    PS2("armsx2", "armsx2_upscale", 4, 2, "nx_ps2_3d_resolution", "nx_ps2_3d_max_scale", "nx_ps2_3d_scale"),
    PSP("ppsspp", "ppsspp_internal_resolution", 10, 3, "nx_psp_3d_resolution", "nx_psp_3d_max_scale", "nx_psp_3d_scale"),

    /** GameCube and Wii: one core, so one set of rows (each console's file keeps its own values). */
    DOLPHIN("dolphin", "dolphin_efb_scale", 8, 3, "nx_dolphin_3d_resolution", "nx_dolphin_3d_max_scale", "nx_dolphin_3d_scale"),

    /** PlayStation on SwanStation's hardware renderer. Light enough that the AYANEO's Mali-G76 holds 60 fps at 4x. */
    PS1("swanstation", "swanstation_GPU_ResolutionScale", 8, 4, "nx_ps1_3d_resolution", "nx_ps1_3d_max_scale", "nx_ps1_3d_scale"),

    /**
     * PlayStation on PCSX-ReARMed: its NEON renderer draws on the CPU, at native size or, with Enhanced Resolution, at 2x
     * (not the 640-wide and 480-line modes, already sharp). The AYANEO holds 60 fps at 2x in Marvel Super Heroes and
     * Street Fighter EX Plus Alpha.
     */
    PCSX("pcsx_rearmed", "pcsx_rearmed_neon_enhancement_enable", 2, 2, "nx_pcsx_3d_resolution", "nx_pcsx_3d_max_scale", "nx_pcsx_3d_scale"),

    /** Dreamcast on Flycast: its Internal Resolution draws the whole picture at a multiple of 640 x 480. */
    DC("flycast", "reicast_internal_resolution", 8, 3, "nx_dc_3d_resolution", "nx_dc_3d_max_scale", "nx_dc_3d_scale"),

    /**
     * Nintendo 64 on Mupen64Plus-Next: GLideN64 draws the whole picture at its window size, a multiple of 320 x 240
     * (patches/mupen64plus_next/0014 applies it mid-game); ParaLLEl-RDP, on Vulkan, at its own upscaling ([alsoSet]).
     */
    N64("mupen64plus_next", "mupen64plus-43screensize", 8, 4, "nx_n64_3d_resolution", "nx_n64_3d_max_scale", "nx_n64_3d_scale"),

    /** Saturn on YabaSanshiro: draws the whole picture at 1x, 2x or 4x of the game's own size (patches apply it mid-game). */
    SATURN("yabasanshiro", "yabasanshiro_resolution_mode", 4, 2, "nx_saturn_3d_resolution", "nx_saturn_3d_max_scale", "nx_saturn_3d_scale"),

    /** Wii U on Cemu: its Internal Resolution draws the whole picture (both screens, Stacked) at a multiple of 1280 x 720 each. */
    CEMU("cemu", "cemu_internal_resolution", 3, 2, "nx_wiiu_3d_resolution", "nx_wiiu_3d_max_scale", "nx_wiiu_3d_scale");

    /** The core draws its whole picture at the scale, not only the 3D: the rows say "the game". */
    val wholePicture: Boolean get() = this == PS1 || this == PCSX || this == PS2 || this == PSP || this == DOLPHIN || this == DC || this == N64 || this == SATURN || this == CEMU

    /**
     * Auto draws at native size whatever the display (so no Max Scale row): Cemu's internal resolution resizes every
     * screen-shaped render target, and some games break when they are (Wind Waker HD's shadows and foliage go black at 2x,
     * Mario Kart 8 loses effects).
     */
    val autoNative: Boolean get() = this == CEMU

    /**
     * The scale is set at launch only: the game keeps it through any size change or row change until the next launch.
     * Cemu reads its internal resolution at start only (its Restart option); changing it mid-game breaks games.
     */
    val launchOnly: Boolean get() = this == CEMU

    /** The scale a [launchOnly] console's core holds after a launch that had no window to size it: its locked Native default. */
    val unsizedLaunchValue: Int? get() = if (launchOnly) 1 else null

    /** Performance draws at native size: no Scale row (the PS2 runs slowly above it; PCSX-ReARMed has only 1x and 2x). */
    val nativePerformance: Boolean get() = this == PS2 || this == PCSX

    /**
     * Performance's Scale is the player's pick of every scale the display reaches, from Native (Auto never goes above
     * Native here, so it is the only way up), not capped at half of it.
     */
    val fullPerformance: Boolean get() = this == CEMU

    /** The Scale an unset game's Performance draws at: Native where above it can break games ([autoNative]). */
    val defaultScale: Int get() = if (this == CEMU) 1 else Nds3dResolution.DEFAULT_PERFORMANCE_SCALE

    /** The scales the core has, when not every whole one up to [coreMax] (YabaSanshiro: no 3x); null: all of them. */
    val scaleSteps: List<Int>? get() = if (this == SATURN) listOf(1, 2, 4) else null

    /** The three stored rows. */
    val keys: Set<String> get() = setOf(modeKey, maxScaleKey, scaleKey)

    /**
     * The Max Scale an unset game starts with on a modest GPU ([DeviceTraits.modestGpu]); null: the same as elsewhere.
     * On the AYANEO's Mali-G76, Dante's Inferno holds 60 fps at 2x and drops to ~42 at 3x, Dragon Ball Z Budokai
     * Tenkaichi 3 holds 60 at 1x and drops to ~21 at 2x, and Mario Kart: Double Dash!! holds 60 at 1x and drops to
     * ~36-40 at 2x on either renderer.
     */
    private val modestCap: Int? get() = when (this) {
        PSP -> 2
        PS2 -> 1
        DOLPHIN -> 1
        else -> null
    }

    /** Something here follows the GPU ([modestCap], ParaLLEl-RDP's Auto in [alsoSet]): a launch reads it first. */
    val followsGpu: Boolean get() = modestCap != null || this == N64

    /** The Max Scale an unset game starts with: [modestCap] on a modest GPU, else the console's own. */
    val defaultCap: Int get() = modestCap?.takeIf { DeviceTraits.modestGpu(DeviceTraits.gpu) } ?: cap

    /** This console's display maximum ([Nds3dResolution.displayMaxOf], [N3ds3dResolution.displayMaxOf]) for a [window] (px, width to height) of class [screen]. */
    fun displayMaxOf(window: Pair<Int, Int>?, screen: ScreenClass?): Int? = when (this) {
        DS -> Nds3dResolution.displayMaxOf(window, screen)
        N3DS -> N3ds3dResolution.displayMaxOf(window, screen)
        PS2 -> window?.let { Ps2Resolution.displayMax(it.first, it.second) }
        PSP -> window?.let { PspResolution.displayMax(it.first, it.second) }
        DOLPHIN -> window?.let { DolphinResolution.displayMax(it.first, it.second) }
        PS1, PCSX -> window?.let { Ps1Resolution.displayMax(it.first, it.second) }
        DC -> window?.let { DcResolution.displayMax(it.first, it.second) }
        N64 -> window?.let { N64Resolution.displayMax(it.first, it.second) }
        SATURN -> window?.let { SaturnResolution.displayMax(it.first, it.second) }
        CEMU -> window?.let { CemuResolution.displayMax(it.first, it.second) }
    }

    /**
     * [displayMaxOf] for a second display that the whole picture goes to. The Wii U's TV goes there alone, so its reach
     * is a 1280 x 720 picture's, not the Stacked frame's.
     */
    fun secondDisplayMaxOf(window: Pair<Int, Int>?, screen: ScreenClass?): Int? = when (this) {
        CEMU -> window?.let { CemuResolution.tvDisplayMax(it.first, it.second) }
        else -> displayMaxOf(window, screen)
    }

    /**
     * The core option's value for scale [n]: ARMSX2 reads "2x", PPSSPP, Flycast and GLideN64 the size ("960x544", "1280x960"),
     * PCSX-ReARMed whether Enhanced Resolution is on, YabaSanshiro its mode (a 3 that slips past [value] goes down to 2x),
     * the others a bare number.
     */
    fun coreValue(n: Int): String = when (this) {
        PS2 -> "${n}x"
        PSP -> "${PspResolution.NATIVE_W * n}x${PspResolution.NATIVE_H * n}"
        PCSX -> if (n >= 2) "enabled" else "disabled"
        DC -> "${DcResolution.NATIVE_W * n}x${DcResolution.NATIVE_H * n}"
        N64 -> "${N64Resolution.NATIVE_W * n}x${N64Resolution.NATIVE_H * n}"
        SATURN -> when { n >= 4 -> "4x"; n >= 2 -> "2x"; else -> "original" }
        CEMU -> if (n == 1) "native" else "${n}x"
        else -> "$n"
    }

    /**
     * Other core options scale [n] sets with [coreKey]: ParaLLEl-RDP's upscaling for the N64 on Vulkan ([auto]: the
     * rows are on Auto; [slowParallel]: ParaLLEl-RDP runs slowly on this GPU and driver, [N64Resolution.parallelUpscaling]).
     */
    fun alsoSet(n: Int, auto: Boolean, slowParallel: Boolean): Map<String, String> = when (this) {
        N64 -> mapOf(N64Resolution.PARALLEL_KEY to "${N64Resolution.parallelUpscaling(n, auto, slowParallel)}x")
        else -> emptyMap()
    }

    companion object {
        /** The console for a system [tag]; null when it has no 3D Resolution. */
        fun forTag(tag: String): Res3dConsole? = when (tag.trim().uppercase()) {
            "NDS" -> DS
            "3DS" -> N3DS
            "PS2" -> PS2
            "PSP" -> PSP
            "GC", "WII" -> DOLPHIN
            "PSX" -> PS1
            "PS" -> PCSX
            "DC" -> DC
            "N64" -> N64
            "SATURN" -> SATURN
            "WIIU" -> CEMU
            else -> null
        }
    }
}

/** The internal resolution (whole scale of 256x192) the melonDS GL renderer draws the 3D at, from the layout and the display. */
object Nds3dResolution {
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

    /** Performance never goes above half the [displayMax], rounded up (the PS2: native, 1x; the Wii U: all of it). */
    fun performanceCeiling(displayMax: Int, console: Res3dConsole = Res3dConsole.DS): Int = when {
        console.nativePerformance -> 1
        console.fullPerformance -> displayMax.coerceIn(1, console.coreMax)
        else -> ceil(displayMax / 2.0).toInt().coerceIn(1, console.coreMax)
    }

    /**
     * The scale to send for [mode]: [current] is the shown scale (null: no screens placed yet, so [displayMax] stands in),
     * [maxScale] the Auto cap and [scale] the Performance pick. [shaded]: a shader preset is on; it is drawn from the native
     * picture, so Auto draws at 1x rather than render detail the shader shrinks away. Auto is 1x too where
     * [Res3dConsole.autoNative].
     */
    fun value(
        mode: Nds3dMode, maxScale: Int, scale: Int, current: Int?, displayMax: Int, console: Res3dConsole = Res3dConsole.DS,
        shaded: Boolean = false,
    ): Int {
        val v = when (mode) {
            Nds3dMode.AUTO -> if (shaded || console.autoNative) 1 else minOf(current ?: displayMax, maxScale, displayMax)
            Nds3dMode.QUALITY -> min(current ?: displayMax, displayMax)
            Nds3dMode.PERFORMANCE -> min(scale, performanceCeiling(displayMax, console))
        }.coerceIn(1, console.coreMax)
        // A core without every scale: Quality goes up to the next one so the picture is never drawn below its shown size,
        // Auto and Performance down, as they hold back for speed.
        val steps = console.scaleSteps ?: return v
        return if (mode == Nds3dMode.QUALITY) steps.firstOrNull { it >= v } ?: steps.last() else steps.last { it <= v }
    }

    /**
     * The scale to send mid-game, the core having been sent [sent] (null: nothing yet): [valueFor] now, but for a
     * [Res3dConsole.launchOnly] console that already has its launch scale, which it keeps.
     */
    fun liveValue(
        sent: Int?, values: Map<String, String>, current: Int?, displayMax: Int, console: Res3dConsole, shaded: Boolean = false,
    ): Int = sent?.takeIf { console.launchOnly } ?: valueFor(values, current, displayMax, console, shaded)

    /**
     * [value] from [console]'s stored 3D Resolution rows in [values] (its mode, max scale and scale keys); a row unset or
     * unreadable takes its row default.
     */
    fun valueFor(
        values: Map<String, String>, current: Int?, displayMax: Int, console: Res3dConsole = Res3dConsole.DS, shaded: Boolean = false,
    ): Int = value(
        mode = Nds3dMode.parse(values[console.modeKey]),
        maxScale = values[console.maxScaleKey]?.toIntOrNull() ?: console.defaultCap,
        scale = values[console.scaleKey]?.toIntOrNull() ?: console.defaultScale,
        current = current,
        displayMax = displayMax,
        console = console,
        shaded = shaded,
    )
}

/**
 * The shown scale and the second display's reach for a picture [mainHeightPx] tall on main (null: none there) and
 * [secondHeightPx] tall on the second display (null: none there; it then reaches only 1), each height read by
 * [scaleOf].
 */
private fun shownScalesBy(mainHeightPx: Int?, secondHeightPx: Int?, scaleOf: (Int) -> Int): Pair<Int?, Int> {
    val second = secondHeightPx?.let(scaleOf)
    return listOfNotNull(mainHeightPx?.let(scaleOf), second).maxOrNull() to (second ?: 1)
}

/**
 * Scales of a picture a core draws at a whole multiple (1 to [max]) of its [nativeW] x [nativeH] frame; a part-way
 * scale rounds up.
 */
private class WholeScale(private val nativeW: Int, private val nativeH: Int, private val max: Int) {
    private fun up(scale: Double): Int = ceil(scale - SLACK).toInt().coerceIn(1, max)

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up. */
    fun displayMax(w: Int, h: Int): Int = maxOf(
        up(minOf(w.toDouble() / nativeW, h.toDouble() / nativeH)), up(minOf(h.toDouble() / nativeW, w.toDouble() / nativeH)),
    )

    /** The scale a picture [pictureHeightPx] px tall shows at. */
    fun currentScale(pictureHeightPx: Int): Int = up(pictureHeightPx.toDouble() / nativeH)

    /** The scale a frame [heightPx] rows tall was drawn at: the nearest whole. */
    fun frameScale(heightPx: Int): Int = (heightPx / nativeH.toDouble()).roundToInt().coerceIn(1, max)

    private companion object {
        /** Below a whole scale by this much (a px or two of rounding) still counts as that scale. */
        const val SLACK = 0.02
    }
}

/**
 * The PS2 (ARMSX2) 3D Resolution maths: the core draws the whole picture at a whole multiple of its 640 x 448 frame (1x to 4x),
 * so scales come from the picture's height. Performance is native (1x): there is no Scale row.
 */
object Ps2Resolution {
    const val NATIVE_W = 640
    const val NATIVE_H = 448

    /** The core's most. */
    private const val MAX = 4

    /** The picture is shown 4:3 at [NATIVE_H] lines, so a whole scale n needs 4/3 * 448 * n px across and 448 * n down. */
    private const val SHOWN_W = NATIVE_H * 4 / 3

    /** The biggest whole scale the shown picture reaches in a [w] x [h] px window, either way up (at least 1, at most the core's 4). */
    fun displayMax(w: Int, h: Int): Int =
        maxOf(minOf(w / SHOWN_W, h / NATIVE_H), minOf(h / SHOWN_W, w / NATIVE_H)).coerceIn(1, MAX)

    /** The scale a picture [pictureHeightPx] px tall shows at (nearest whole, 1 to 4). */
    fun currentScale(pictureHeightPx: Int): Int = (pictureHeightPx / NATIVE_H.toDouble()).roundToInt().coerceIn(1, MAX)

    /**
     * The shown scale and the second display's reach for a picture [mainHeightPx] tall on main (null: none there) and
     * [secondHeightPx] tall on the second display (null: none there; it then reaches only 1).
     */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
}

/**
 * The PSP (PPSSPP) 3D Resolution maths: the core draws the whole picture at a whole multiple of its 480 x 272 screen (1x to
 * 10x), so scales come from the picture's height. A part-way scale rounds up, as the DS's: the picture is drawn at least as
 * sharp as it is shown.
 */
object PspResolution {
    const val NATIVE_W = 480
    const val NATIVE_H = 272

    /** The core's most. */
    private const val MAX = 10

    private val scales = WholeScale(NATIVE_W, NATIVE_H, MAX)

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up, rounded up (1 to 10). */
    fun displayMax(w: Int, h: Int): Int = scales.displayMax(w, h)

    /** The scale a picture [pictureHeightPx] px tall shows at, rounded up (1 to 10). */
    fun currentScale(pictureHeightPx: Int): Int = scales.currentScale(pictureHeightPx)

    /** The scale a frame [heightPx] rows tall was drawn at: the nearest whole (the core's "crop to 16:9" sends 270 a scale). */
    fun frameScale(heightPx: Int): Int = scales.frameScale(heightPx)

    /** The shown scale and the second display's reach, as [Ps2Resolution.shownScales]. */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
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

/**
 * Dolphin's internal resolution (dolphin_efb_scale): a whole multiple of the GameCube / Wii frame, 528 lines (576 on a
 * PAL game, still read as one scale). The core changes it mid-game on both renderers: on Vulkan its swapchain follows the
 * new size, so a raised scale is drawn and shown at it.
 */
object DolphinResolution {
    const val NATIVE_W = 640
    const val NATIVE_H = 528

    /** The most the rows offer (the core goes to 12x, far past any phone screen). */
    private const val MAX = 8

    private val scales = WholeScale(NATIVE_W, NATIVE_H, MAX)

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up, rounded up (1 to 8). */
    fun displayMax(w: Int, h: Int): Int = scales.displayMax(w, h)

    /** The scale a picture [pictureHeightPx] px tall shows at, rounded up (1 to 8). */
    fun currentScale(pictureHeightPx: Int): Int = scales.currentScale(pictureHeightPx)

    /** The scale a frame [heightPx] rows tall was drawn at: the nearest whole (a PAL frame is 576 a scale). */
    fun frameScale(heightPx: Int): Int = scales.frameScale(heightPx)

    /** The shown scale and the second display's reach, as [Ps2Resolution.shownScales]. */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
}

/**
 * Cemu's internal resolution (cemu_internal_resolution): a whole multiple of the Wii U's 1280 x 720 screen. The Stacked
 * layout puts the TV and the GamePad one above the other, so the frame is 1280 x 1440 a scale.
 */
object CemuResolution {
    const val NATIVE_W = 1280
    const val NATIVE_H = 1440

    private const val MAX = 3

    private val scales = WholeScale(NATIVE_W, NATIVE_H, MAX)
    /** The TV screen alone: what a second display showing only it is measured by. */
    private val tv = WholeScale(NATIVE_W, NATIVE_H / 2, MAX)

    /** The scale the TV picture alone reaches fitted in a [w] x [h] px window, either way up (1 to 3). */
    fun tvDisplayMax(w: Int, h: Int): Int = tv.displayMax(w, h)

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up, rounded up (1 to 3). */
    fun displayMax(w: Int, h: Int): Int = scales.displayMax(w, h)

    /** The scale a picture [pictureHeightPx] px tall shows at, rounded up (1 to 3). */
    fun currentScale(pictureHeightPx: Int): Int = scales.currentScale(pictureHeightPx)

    /** The scale a frame [heightPx] rows tall was drawn at: the nearest whole. */
    fun frameScale(heightPx: Int): Int = scales.frameScale(heightPx)

    /** The shown scale and the second display's reach, as [Ps2Resolution.shownScales]. */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
}

/**
 * SwanStation's internal resolution (swanstation_GPU_ResolutionScale): a whole multiple of the PlayStation's picture, shown
 * 4:3 at 240 lines (320 x 240). It changes mid-game: on OpenGL ES the frame follows; on Vulkan the core draws at the new
 * scale but keeps handing over the size it launched at until the next launch.
 */
object Ps1Resolution {
    const val NATIVE_W = 320
    const val NATIVE_H = 240

    /** The most the rows offer (the core goes to 16x). */
    private const val MAX = 8

    private val scales = WholeScale(NATIVE_W, NATIVE_H, MAX)

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up, rounded up (1 to 8). */
    fun displayMax(w: Int, h: Int): Int = scales.displayMax(w, h)

    /** The scale a picture [pictureHeightPx] px tall shows at, rounded up (1 to 8). */
    fun currentScale(pictureHeightPx: Int): Int = scales.currentScale(pictureHeightPx)

    /** The shown scale and the second display's reach, as [Ps2Resolution.shownScales]. */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
}

/**
 * Flycast's internal resolution (reicast_internal_resolution, read by its height): a whole multiple of the Dreamcast's
 * 640 x 480 picture. The core reads it at every options change and hands over frames at the new size on either
 * renderer; its geometry stays 640 x 480 whatever the scale.
 */
object DcResolution {
    const val NATIVE_W = 640
    const val NATIVE_H = 480

    /** The most the rows offer (the core goes to 20x). */
    private const val MAX = 8

    private val scales = WholeScale(NATIVE_W, NATIVE_H, MAX)

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up, rounded up (1 to 8). */
    fun displayMax(w: Int, h: Int): Int = scales.displayMax(w, h)

    /** The scale a picture [pictureHeightPx] px tall shows at, rounded up (1 to 8). */
    fun currentScale(pictureHeightPx: Int): Int = scales.currentScale(pictureHeightPx)

    /** The shown scale and the second display's reach, as [Ps2Resolution.shownScales]. */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
}

/**
 * Saturn on YabaSanshiro: the common 224-line game picture, shown 4:3, at 1x, 2x or 4x. The core multiplies the game's own
 * size, so a hi-res game (704 x 480) draws twice as many pixels per side as its shown scale suggests.
 */
object SaturnResolution {
    const val NATIVE_H = 224

    private const val SHOWN_W = NATIVE_H * 4 / 3
    private const val MAX = 4

    private val scales = WholeScale(SHOWN_W, NATIVE_H, MAX)

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up, rounded up (1 to 4). */
    fun displayMax(w: Int, h: Int): Int = scales.displayMax(w, h)

    /** The scale a picture [pictureHeightPx] px tall shows at, rounded up (1 to 4). */
    fun currentScale(pictureHeightPx: Int): Int = scales.currentScale(pictureHeightPx)

    /** The shown scale and the second display's reach, as [Ps2Resolution.shownScales]. */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
}

/**
 * The N64's 320 x 240 picture on Mupen64Plus-Next. GLideN64 draws at a window of any whole multiple and hands over frames
 * that size; ParaLLEl-RDP upscales by 1, 2, 4 or 8 only ([parallelUpscaling]).
 */
object N64Resolution {
    const val NATIVE_W = 320
    const val NATIVE_H = 240

    /** ParaLLEl-RDP's upscaling option. */
    const val PARALLEL_KEY = "mupen64plus-parallel-rdp-upscaling"

    /** The most the rows offer (GLideN64's list stops at 12x, ParaLLEl-RDP's at 8x). */
    private const val MAX = 8

    private val scales = WholeScale(NATIVE_W, NATIVE_H, MAX)

    /**
     * ParaLLEl-RDP's upscaling for scale [n]: the biggest it offers (1, 2, 4 or 8) up to [n]. A pick made by hand is the
     * player's, held only by the display; Auto ([auto]) stops at [AUTO_MAX], or native where ParaLLEl-RDP runs slowly
     * ([slowParallel]). Mario Kart 64 mid-race: on the AYANEO (Mali-G76, 2 GB) 60 fps at 1x, ~40 at 2x (~35 on malisx2),
     * and 4x has the game killed for memory at launch; on the Nova (Adreno 740) 60 at 2x and 45-51 at 4x on Turnip, ~48 at
     * 1x and ~40 at 2x on its own driver. OpenGL ES (GLideN64) holds 60 at 4x on both.
     */
    fun parallelUpscaling(n: Int, auto: Boolean, slowParallel: Boolean): Int {
        val top = if (auto) minOf(n, if (slowParallel) 1 else AUTO_MAX) else n
        return PARALLEL_SCALES.last { it <= top.coerceAtLeast(1) }
    }

    /** What ParaLLEl-RDP offers. */
    private val PARALLEL_SCALES = listOf(1, 2, 4, 8)

    /** Auto's most on ParaLLEl-RDP: 60 fps on a flagship, where 4x is not. */
    private const val AUTO_MAX = 2

    /**
     * ParaLLEl-RDP runs slowly on [gpu]: a modest one ([DeviceTraits.modestGpu]), or an Adreno on its own driver
     * ([customDriver] false), which takes the general shader ([VulkanSwitch.adrenoSystemDriverEnv]).
     */
    fun slowParallel(gpu: String?, customDriver: Boolean): Boolean =
        DeviceTraits.modestGpu(gpu) || (!customDriver && DeviceTraits.isAdreno(gpu))

    /** The scale the picture reaches fitted in a [w] x [h] px window, either way up, rounded up (1 to 8). */
    fun displayMax(w: Int, h: Int): Int = scales.displayMax(w, h)

    /** The scale a picture [pictureHeightPx] px tall shows at, rounded up (1 to 8). */
    fun currentScale(pictureHeightPx: Int): Int = scales.currentScale(pictureHeightPx)

    /** The shown scale and the second display's reach, as [Ps2Resolution.shownScales]. */
    fun shownScales(mainHeightPx: Int?, secondHeightPx: Int?): Pair<Int?, Int> =
        shownScalesBy(mainHeightPx, secondHeightPx, ::currentScale)
}
