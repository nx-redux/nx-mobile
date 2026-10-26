package com.nxredux.mobile.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * An Android game's abstract picture from its launcher icon ([abstractAppArtUrl]): the icon's palette, which recolours
 * the seed's plan ([abstractPlan]), and where the icon sits on it. Pure here; the platform reads the icon's pixels and
 * draws the result.
 */

/** A pixel counts only at this alpha or more (about 50%). */
private const val MIN_ALPHA = 128

/** Coloured pixels: HSL saturation from this (percent), lightness inside [COLOUR_LIGHTNESS]; the rest are neutrals. */
private const val MIN_SATURATION = 25.0
private val COLOUR_LIGHTNESS = 10.0..90.0

/** The coloured pixels' share of the opaque ones an icon needs to be read as coloured; below it, its neutrals. */
private const val MIN_COLOURED_SHARE = 0.05

/** Hue bins, degrees; two picks at least [MIN_HUE_GAP] apart; a bin under [MIN_BIN_SHARE] of the weight never picked. */
private const val HUE_BIN = 15.0
private const val MIN_HUE_GAP = 30.0
private const val MIN_BIN_SHARE = 0.02

/** Grey levels: lightness bins of [GREY_BIN] percent; two levels at least [MIN_LEVEL_GAP] apart. */
private const val GREY_BIN = 10.0
private const val MIN_LEVEL_GAP = 15.0

/** The most colours a palette holds, and the fewest (a single tone gets derived steps up to it). */
private const val MAX_COLOURS = 4
private const val MIN_COLOURS = 3

/** 0xAARRGGBB → its HSL as [Hsla] (hue degrees, saturation and lightness percent, alpha 0–1). */
internal fun argbHsla(argb: Int): Hsla {
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    val d = mx - mn
    val s = if (d == 0.0) 0.0 else d / (1 - abs(2 * l - 1))
    val h = when {
        d == 0.0 -> 0.0
        mx == r -> 60 * (((g - b) / d) % 6)
        mx == g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }
    return Hsla(((h % 360) + 360) % 360, s * 100, l * 100, ((argb ushr 24) and 0xFF) / 255.0)
}

/**
 * The palette of an icon's [pixels] (0xAARRGGBB, any order; the platform's ~192 px render of the full adaptive icon),
 * strongest first, 3 or 4 opaque colours; null when no pixel is opaque enough (the plain picture then).
 *
 * Pixels under about 50% alpha are ignored. A coloured icon (coloured pixels at least 5% of the opaque ones): the coloured
 * pixels binned by hue (15°), each bin weighted by its pixels' saturation, its colour the bin's mean; the heaviest bins
 * at least 30° apart, up to 4; a single hue gets derived lightness steps. Otherwise (a grey or white icon) its
 * neutrals: every opaque pixel binned by lightness (10%), the most-filled levels at least 15 apart, up to 4, grey;
 * fewer than 3 get derived steps ([withSteps]: darker for a light tone, lighter for a dark one).
 */
internal fun iconPalette(pixels: IntArray): List<Hsla>? {
    val opaque = pixels.filter { (it ushr 24) >= MIN_ALPHA }.map(::argbHsla)
    if (opaque.isEmpty()) return null
    val coloured = opaque.filter { it.saturation >= MIN_SATURATION && it.lightness in COLOUR_LIGHTNESS }
    return if (coloured.size >= opaque.size * MIN_COLOURED_SHARE) colourPalette(coloured) else greyPalette(opaque)
}

private class Bin {
    var weight = 0.0; var cos = 0.0; var sin = 0.0; var sat = 0.0; var lig = 0.0
    fun mean() = Hsla(((atan2(sin, cos) * 180 / PI) % 360 + 360) % 360, sat / weight, lig / weight)
}

private fun colourPalette(coloured: List<Hsla>): List<Hsla> {
    val bins = HashMap<Int, Bin>()
    for (c in coloured) {
        val w = c.saturation / 100
        val rad = c.hue * PI / 180
        bins.getOrPut(floor(c.hue / HUE_BIN).toInt() % (360 / HUE_BIN.toInt())) { Bin() }.apply {
            weight += w; cos += cos(rad) * w; sin += sin(rad) * w; sat += c.saturation * w; lig += c.lightness * w
        }
    }
    val total = bins.values.sumOf { it.weight }
    val picks = mutableListOf<Hsla>()
    for ((_, bin) in bins.entries.sortedWith(compareByDescending<Map.Entry<Int, Bin>> { it.value.weight }.thenBy { it.key })) {
        if (picks.size == MAX_COLOURS || bin.weight < total * MIN_BIN_SHARE) break
        val c = bin.mean()
        if (picks.all { hueGap(it.hue, c.hue) >= MIN_HUE_GAP }) picks += c
    }
    return if (picks.size >= 2) picks else withSteps(picks)
}

private fun hueGap(a: Double, b: Double): Double = abs(a - b).let { min(it, 360 - it) }

private fun greyPalette(opaque: List<Hsla>): List<Hsla> {
    val counts = IntArray(10)
    val sums = DoubleArray(10)
    for (c in opaque) {
        val i = (c.lightness / GREY_BIN).toInt().coerceIn(0, 9)
        counts[i]++; sums[i] += c.lightness
    }
    val levels = mutableListOf<Double>()
    for (i in (0 until 10).sortedWith(compareByDescending<Int> { counts[it] }.thenBy { it })) {
        if (counts[i] == 0 || levels.size == MAX_COLOURS) break
        val l = sums[i] / counts[i]
        if (levels.all { abs(it - l) >= MIN_LEVEL_GAP }) levels += l
    }
    return withSteps(levels.map { Hsla(0.0, 0.0, it) })
}

/**
 * [picks] filled to 3 colours with steps of the first's hue and saturation: 25 and 50 lightness darker for a light tone
 * (50 or more), lighter for a dark one, then the other way, each kept inside 6–96 and 15 from every colour already in.
 */
internal fun withSteps(picks: List<Hsla>): List<Hsla> {
    if (picks.isEmpty() || picks.size >= MIN_COLOURS) return picks
    val base = picks[0]
    val out = picks.toMutableList()
    val dir = if (base.lightness >= 50) -1 else 1
    for (step in listOf(25.0 * dir, 50.0 * dir, -25.0 * dir, -50.0 * dir)) {
        if (out.size == MIN_COLOURS) break
        val l = base.lightness + step
        if (l in 6.0..96.0 && out.all { abs(it.lightness - l) >= MIN_LEVEL_GAP }) out += base.copy(lightness = l, alpha = 1.0)   // role: an opaque candidate for the icon palette, not a see-through colour
    }
    return out
}

/**
 * The accent's palette (0xRRGGBB, the Accent setting): the colour and two lightness steps of its one hue ([withSteps]),
 * as a single-hue icon gets. A near-grey accent (saturation under the coloured threshold, White) is a pure grey, so its
 * steps are greyscale.
 */
internal fun accentPalette(rgb: Int): List<Hsla> {
    val c = argbHsla((0xFF shl 24) or rgb)
    val base = if (c.saturation < MIN_SATURATION) Hsla(0.0, 0.0, c.lightness) else c
    return withSteps(listOf(base))
}

/**
 * Home's "Pick a game" picture for the accent [rgb]: the [PICK_A_GAME_ART_KEY] seed's layout on a near-black ground
 * tinted by the accent (lightness 3–8), its shapes in the accent's hue (lightness 28–45) at [ACCENT_SHAPE_ALPHA] of a
 * game picture's opacity: dark and subtle behind the card's text, the accent still showing. A near-grey accent (White)
 * is greys.
 */
internal fun accentArtPlan(seed: Int, rgb: Int, w: Double = ABSTRACT_W, h: Double = ABSTRACT_H): AbstractPlan {
    val base = accentPalette(rgb)[0]
    val plan = abstractPlan(seed, w, h)
    val n = plan.stops.size
    return plan.copy(
        stops = plan.stops.indices.map { i -> Hsla(base.hue, min(base.saturation, 45.0), 3.0 + 5.0 * i / max(1, n - 1)) },
        shapes = plan.shapes.map { s ->
            s.copy(color = Hsla(base.hue, min(base.saturation, 75.0), base.lightness.coerceIn(28.0, 45.0), s.color.alpha * ACCENT_SHAPE_ALPHA))
        },
    )
}

/** Pick a game's shapes' opacity over a game picture's: fainter, a glow rather than a pattern. */
internal const val ACCENT_SHAPE_ALPHA = 0.65

/** The icon on its picture: its box ([left], [top], [width], [height]) and its shadow's drop and blur radius, px. */
internal data class IconPlacement(
    val left: Double, val top: Double, val width: Double, val height: Double, val shadowDy: Double, val shadowBlur: Double,
)

/** The icon's height over the picture's. */
internal const val ICON_HEIGHT_SHARE = 0.4

/** The shadow's alpha (black), faint. */
internal const val ICON_SHADOW_ALPHA = 0.3

/**
 * An [iconW] × [iconH] icon on a [w] × [h] picture: centred, [ICON_HEIGHT_SHARE] of its height, at its own aspect;
 * a soft shadow 1.2% of the height down, blurred 2.5%.
 */
internal fun iconPlacement(w: Double, h: Double, iconW: Double, iconH: Double): IconPlacement {
    val height = h * ICON_HEIGHT_SHARE
    val width = if (iconH > 0) height * iconW / iconH else height
    return IconPlacement((w - width) / 2, (h - height) / 2, width, height, shadowDy = h * 0.012, shadowBlur = h * 0.025)
}
