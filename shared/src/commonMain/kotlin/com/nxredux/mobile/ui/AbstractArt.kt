package com.nxredux.mobile.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI

/**
 * Generated abstract art for a game with nothing else to show: a soft gradient ground and a few large translucent
 * shapes, the same for the same game every time.
 * Pure here: the seed, the PRNG and the plan; each platform's fetcher ([com.nxredux.mobile.ui.nx.abstractArtFetcher]) draws a plan.
 * Never real art: a game showing it still counts as having none (fetching, the missing-art counts).
 */

/** The base bitmap, px (4:3), as the mockup's; a larger render draws the same plan scaled ([abstractPlan]'s w, h). */
internal const val ABSTRACT_W = 512.0
internal const val ABSTRACT_H = 384.0

/** The model scheme an abstract picture loads by: `nxabstract://<seed, 8 hex digits>`. */
internal const val ABSTRACT_SCHEME = "nxabstract"

/** The seed's text: the system tag, "/", the ROM's file name as on disk (extension included, no folder). */
internal fun abstractArtKey(tag: String, fileName: String): String = "$tag/$fileName"

/** FNV-1a 32-bit over [key]'s UTF-8 bytes. */
internal fun abstractSeed(key: String): Int {
    var h = 0x811C9DC5.toInt()
    for (b in key.encodeToByteArray()) h = (h xor (b.toInt() and 0xFF)) * 0x01000193
    return h
}

/** The image model for [tag] / [fileName]'s abstract picture. */
internal fun abstractArtUrl(tag: String, fileName: String): String =
    "$ABSTRACT_SCHEME://" + (abstractSeed(abstractArtKey(tag, fileName)).toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')

/**
 * The image model for an Android game's abstract picture: `nxabstract://app/<app id>` (its package / activity), seeded
 * by [ANDROID_TAG] and the app id ([abstractAppSeed]); the platform's fetcher builds its palette from the
 * app's launcher icon and draws the icon in the middle ([iconPalette], [iconPlacement]). [icon] false: the same palette
 * and plan without the icon, `nxabstract://app/<app id>?icon=0` (a game list's full-screen background, behind a cover
 * that already shows it).
 */
internal fun abstractAppArtUrl(appId: String, icon: Boolean = true): String =
    "$ABSTRACT_SCHEME://$ABSTRACT_APP_HOST/$appId" + if (icon) "" else ABSTRACT_NO_ICON

/** The host of [abstractAppArtUrl]'s form. */
internal const val ABSTRACT_APP_HOST = "app"

/** The query that marks [abstractAppArtUrl]'s icon-less form. */
internal const val ABSTRACT_NO_ICON = "?icon=0"

/** An [abstractAppArtUrl] parsed: the app id, and whether the icon is drawn in the middle. */
internal data class AbstractAppArt(val appId: String, val icon: Boolean)

/** The app id and icon flag an [abstractAppArtUrl] carries, or null for any other model (a ROM's picture included). */
internal fun abstractAppArtOf(url: String): AbstractAppArt? {
    val rest = url.removePrefix("$ABSTRACT_SCHEME://$ABSTRACT_APP_HOST/").takeIf { it.length != url.length } ?: return null
    val icon = !rest.endsWith(ABSTRACT_NO_ICON)
    val id = rest.removeSuffix(ABSTRACT_NO_ICON)
    return if (id.isBlank() || '?' in id) null else AbstractAppArt(id, icon)
}

/** The app id an [abstractAppArtUrl] carries (either form), or null for any other model (a ROM's picture included). */
internal fun abstractAppIdOf(url: String): String? = abstractAppArtOf(url)?.appId

/** An Android game's seed: the one [abstractArtUrl] gives for [ANDROID_TAG] and the app id. */
internal fun abstractAppSeed(appId: String): Int = abstractSeed(abstractArtKey(ANDROID_TAG, appId))

/**
 * The image model for Home's "Pick a game" picture: `nxabstract://accent/<rrggbb>`, the fixed [PICK_A_GAME_ART_KEY]
 * seed's layout, dark, with the accent in its shapes ([accentArtPlan]). The colour is in the URL, so the memory cache keys on it and a
 * changed accent loads a new picture.
 */
internal fun abstractAccentArtUrl(rgb: Int): String =
    "$ABSTRACT_SCHEME://$ABSTRACT_ACCENT_HOST/" + (rgb and 0xFFFFFF).toString(16).padStart(6, '0')

/** The host of [abstractAccentArtUrl]'s form. */
internal const val ABSTRACT_ACCENT_HOST = "accent"

/** The seed's text for [abstractAccentArtUrl]'s picture. */
internal const val PICK_A_GAME_ART_KEY = "pick-a-game"

/** The colour (0xRRGGBB) an [abstractAccentArtUrl] carries, or null for any other model. */
internal fun abstractAccentOf(url: String): Int? {
    val hex = url.removePrefix("$ABSTRACT_SCHEME://$ABSTRACT_ACCENT_HOST/").takeIf { it.length != url.length } ?: return null
    if (hex.length != 6 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return hex.toInt(16)
}

/** The seed an [abstractArtUrl] carries, or null for any other model. */
internal fun abstractSeedOf(url: String): Int? {
    val hex = url.removePrefix("$ABSTRACT_SCHEME://").takeIf { it.length != url.length } ?: return null
    if (hex.length != 8) return null
    return hex.toLongOrNull(16)?.toInt()
}

/** mulberry32: each [next] a double in [0, 1), the mockup's JS bit for bit. */
internal class Mulberry32(seed: Int) {
    private var a = seed
    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/** A colour as CSS hsla: [hue] in degrees 0–360, [saturation] and [lightness] in percent, [alpha] 0–1. */
internal data class Hsla(val hue: Double, val saturation: Double, val lightness: Double, val alpha: Double = 1.0) {
    /** ARGB, the channels rounded as a canvas does. */
    fun argb(): Int = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or hslRgb(hue, saturation, lightness)
}

/** CSS hsl → 0xRRGGBB ([s], [l] in percent). */
internal fun hslRgb(h: Double, s: Double, l: Double): Int {
    val sf = s / 100
    val lf = l / 100
    val a = sf * min(lf, 1 - lf)
    fun f(n: Int): Int {
        val k = (n + hueOf(h) / 30) % 12
        val v = lf - a * max(-1.0, min(min(k - 3, 9 - k), 1.0))
        return (v * 255).roundToInt().coerceIn(0, 255)
    }
    return (f(0) shl 16) or (f(8) shl 8) or f(4)
}

private fun hueOf(h: Double): Double = ((h % 360) + 360) % 360

/**
 * One shape, its origin at ([x], [y]), turned [rotation] degrees (clockwise, y down): [kind] 0 a circle of [radius];
 * 1 a rounded band [length] × [thickness] with corner [corner] (a stadium); 2 a stripe [length] × [thickness], square ends.
 * Both rects are centred on the origin.
 */
internal data class AbstractShape(
    val kind: Int, val x: Double, val y: Double, val rotation: Double,
    val radius: Double, val length: Double, val thickness: Double, val corner: Double, val color: Hsla,
)

/**
 * A picture [w] × [h] px: a linear ground through the centre at [angle] degrees (0 towards +x, clockwise) through
 * [stops] evenly spaced, then [shapes] in order, source-over. [h0] and [shift] are the hue picks, kept for the tests.
 */
internal data class AbstractPlan(
    val w: Double, val h: Double, val h0: Double, val shift: Double, val angle: Double,
    val stops: List<Hsla>, val shapes: List<AbstractShape>,
) {
    /** The ground's line (x0, y0, x1, y1): centre ∓ dir · half, half = (|W cos| + |H sin|) / 2. */
    fun groundLine(): List<Double> {
        val rad = angle * PI / 180
        val dx = cos(rad)
        val dy = sin(rad)
        val half = (abs(w * dx) + abs(h * dy)) / 2
        return listOf(w / 2 - dx * half, h / 2 - dy * half, w / 2 + dx * half, h / 2 + dy * half)
    }
}

/**
 * The plan for [seed], drawn on [w] × [h] (the mockup's 512 × 384 by default; a larger bitmap gets the same picture
 * scaled). The PRNG calls in exactly the mockup's order. A [palette] (an app icon's,
 * [iconPalette]) recolours it ([recoloured]): the same shapes, layout and opacities.
 */
internal fun abstractPlan(seed: Int, w: Double = ABSTRACT_W, h: Double = ABSTRACT_H, palette: List<Hsla>? = null): AbstractPlan {
    val plan = seedPlan(seed, w, h)
    return if (palette.isNullOrEmpty()) plan else plan.recoloured(palette)
}

/**
 * [this] plan in [palette]'s colours: stop i takes colour i, shape i colour i + 1 (both wrapping), each keeping its
 * hue, its saturation capped (70 the ground, 80 a shape) and its lightness mapped in order into the plan's bands
 * (20–50 the ground, 35–75 a shape) so the picture stays dark enough behind text; every shape keeps its alpha.
 */
internal fun AbstractPlan.recoloured(palette: List<Hsla>): AbstractPlan {
    val n = palette.size
    return copy(
        stops = stops.indices.map { i -> palette[i % n].let { Hsla(it.hue, min(it.saturation, 70.0), 20 + 0.3 * it.lightness) } },
        shapes = shapes.mapIndexed { i, s ->
            val c = palette[(i + 1) % n]
            s.copy(color = Hsla(c.hue, min(c.saturation, 80.0), 35 + 0.4 * c.lightness, s.color.alpha))
        },
    )
}

private fun seedPlan(seed: Int, w: Double, h: Double): AbstractPlan {
    val r = Mulberry32(seed)
    val h0 = r.next() * 360
    val sat = DoubleArray(3) { 35 + r.next() * 20 }
    val lig = DoubleArray(3) { 25 + r.next() * 20 }
    val mag = 25 + r.next() * 35
    val shift = if (r.next() < 0.5) -mag else mag
    val hues = listOf(h0, h0 + shift, h0 - shift / 2).map(::hueOf)
    val nStops = if (r.next() < 0.5) 2 else 3
    val angle = r.next() * 360
    val nShapes = 3 + floor(r.next() * 3).toInt()
    val shapes = List(nShapes) {
        val kind = floor(r.next() * 3).toInt()
        val px = r.next() * w
        val py = r.next() * h
        val a = r.next()
        val b = r.next()
        val q = r.next()
        val color = Hsla(hueOf(h0 + (r.next() * 2 - 1) * 40), 35 + r.next() * 20, 45 + r.next() * 20, 0.12 + r.next() * 0.16)
        when (kind) {
            0 -> AbstractShape(kind, px, py, 0.0, radius = (0.18 + 0.30 * a) * w, length = 0.0, thickness = 0.0, corner = 0.0, color = color)
            1 -> {
                val th = (0.14 + 0.16 * b) * h
                AbstractShape(kind, px, py, (2 * q - 1) * 35, radius = 0.0, length = (0.6 + 0.6 * a) * w, thickness = th, corner = th / 2, color = color)
            }
            else -> AbstractShape(
                kind, px, py, (if (b < 0.5) -1 else 1) * (30 + 30 * q), radius = 0.0, length = 3 * w,
                thickness = (0.06 + 0.12 * a) * h, corner = 0.0, color = color,
            )
        }
    }
    return AbstractPlan(w, h, h0, shift, angle, List(nStops) { Hsla(hues[it], sat[it], lig[it]) }, shapes)
}
