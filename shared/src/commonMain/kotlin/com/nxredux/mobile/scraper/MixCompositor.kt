package com.nxredux.mobile.scraper

/** Straight-alpha ARGB8888 pixels (`0xAARRGGBB`), row-major, [width] x [height] (the platform codec's in and out). */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height)) {
    init { require(width > 0 && height > 0 && pixels.size == width * height) { "bad image size" } }
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}

/**
 * The mix, exactly NX Redux's compositor (`scraper_compositor.c` `Compositor_create`, `nextui/area_scale.c`
 * `AreaScale_argb`): a transparent [W] x [H] canvas; the screenshot filled into the canvas less [W] x 6% each side and
 * [H] x 12% below (centre-cropped), copied top-aligned; the box art (3D, else 2D) fitted (grown when smaller) within half
 * the canvas at the bottom left, the wheel within half its width and 30% of its height at the bottom right, each [PADDING]
 * from the edges over a hard black shadow of its own shape. Every resize is [resizeSharp]. SDL's straight-alpha blend
 * (`SDL_BLENDMODE_BLEND`: colour `src*a + dst*(1-a)`, alpha `a + dst*(1-a)`) and its colour/alpha mod are reproduced
 * with /255 integer maths. Pure: decoding and the PNG encode are the platform's ([PixelCodec]).
 */
object MixCompositor {
    /** NX Redux's `COMPOSITOR_MIX_W/H`: the List layout's fitted box width, 4:3. */
    const val W = 384
    const val H = 288
    /** The 1024x768 originals x0.375 (19 -> 7, 5 -> 2, 3 -> 1). */
    const val PADDING = 7
    const val BOX_SHADOW = 2
    const val WHEEL_SHADOW = 1
    private const val BOX_SHADOW_ALPHA = 100
    private const val WHEEL_SHADOW_ALPHA = 80

    /** The mix of whichever layers are given; null when all three are missing. */
    fun create(screenshot: ArgbImage?, box: ArgbImage?, wheel: ArgbImage?): ArgbImage? {
        if (screenshot == null && box == null && wheel == null) return null
        val canvas = ArgbImage(W, H)   // transparent
        val padX = (W * 0.06).toInt()        // 23
        val padBottom = (H * 0.12).toInt()   // 34
        screenshot?.let { copy(canvas, fillSharp(it, W - padX * 2, H - padBottom), padX, 0) }
        box?.let {
            val s = fitSharp(it, (W * 0.50).toInt(), (H * 0.50).toInt())
            layer(canvas, s, PADDING, H - s.height - PADDING, BOX_SHADOW, BOX_SHADOW_ALPHA)
        }
        wheel?.let {
            val s = fitSharp(it, (W * 0.50).toInt(), (H * 0.30).toInt())
            layer(canvas, s, W - s.width - PADDING, H - s.height - PADDING, WHEEL_SHADOW, WHEEL_SHADOW_ALPHA)
        }
        return canvas
    }

    /** A downloaded mix: [src] fitted whole (grown when smaller, [resizeSharp]) and centred on a transparent [W] x [H] canvas, copied as it is (no blend, no shadow). */
    fun onCanvas(src: ArgbImage): ArgbImage {
        val (w, h) = fitSize(src.width, src.height, W, H, grow = true)
        val canvas = ArgbImage(W, H)
        copy(canvas, resizeSharp(src, w, h), (W - w) / 2, (H - h) / 2)
        return canvas
    }

    /** `fitSize`: [sw] x [sh] within [maxW] x [maxH], aspect kept, truncated, at least 1x1; [grow] false caps the scale at 1. */
    fun fitSize(sw: Int, sh: Int, maxW: Int, maxH: Int, grow: Boolean): Pair<Int, Int> {
        var scale = minOf(maxW.toDouble() / sw, maxH.toDouble() / sh)
        if (!grow && scale > 1.0) scale = 1.0
        return maxOf(1, (sw * scale).toInt()) to maxOf(1, (sh * scale).toInt())
    }

    private fun fitSharp(src: ArgbImage, maxW: Int, maxH: Int): ArgbImage {
        val (w, h) = fitSize(src.width, src.height, maxW, maxH, grow = true)
        return resizeSharp(src, w, h)
    }

    /** `fillSharp`: [src] scaled to cover [tw] x [th] (aspect kept, truncated, at least the target), the overflow cropped from the centre. */
    fun fillSharp(src: ArgbImage, tw: Int, th: Int): ArgbImage {
        val scale = maxOf(tw.toDouble() / src.width, th.toDouble() / src.height)
        val sw = maxOf(tw, (src.width * scale).toInt())
        val sh = maxOf(th, (src.height * scale).toInt())
        val scaled = resizeSharp(src, sw, sh)
        val ox = (sw - tw) / 2; val oy = (sh - th) / 2
        val out = ArgbImage(tw, th)
        for (y in 0 until th) scaled.pixels.copyInto(out.pixels, y * tw, (y + oy) * sw + ox, (y + oy) * sw + ox + tw)
        return out
    }

    /**
     * `resizeSharp`: exactly [w] x [h]. Growing first repeats pixels by the largest whole factor that fits both ways
     * (nearest, when it is 2 or more), so pixel art stays hard-edged; then [areaScale] does the rest.
     */
    fun resizeSharp(src: ArgbImage, w: Int, h: Int): ArgbImage {
        val k = minOf(w / src.width, h / src.height)
        return areaScale(if (k >= 2) scaleNearestInt(src, k) else src, w, h)
    }

    /** Every source pixel as a [k] x [k] block. */
    fun scaleNearestInt(src: ArgbImage, k: Int): ArgbImage {
        val out = ArgbImage(src.width * k, src.height * k)
        for (y in 0 until out.height) {
            val s = (y / k) * src.width; val d = y * out.width
            for (x in 0 until out.width) out.pixels[d + x] = src.pixels[s + x / k]
        }
        return out
    }

    /** The source span an output index covers ([i0], [i1]) and the overlap of its first and last index (`spanOf`, in floats as the C). */
    private class Span(val i0: Int, val i1: Int, val w0: Float, val w1: Float) {
        fun weight(i: Int): Float = if (i == i0) w0 else if (i == i1 - 1) w1 else 1f
    }

    private fun spanOf(d: Int, ratio: Float, n: Int): Span {
        val a = d * ratio; val b = (d + 1) * ratio
        var i0 = a.toInt(); var i1 = b.toInt()
        if (i1.toFloat() < b) i1++
        if (i1 > n) i1 = n
        if (i0 >= i1) i0 = i1 - 1
        return if (i1 - i0 == 1) Span(i0, i1, b - a, b - a) else Span(i0, i1, (i0 + 1).toFloat() - a, b - (i1 - 1).toFloat())
    }

    private fun to8(v: Float): Int { val i = (v + 0.5f).toInt(); return if (i < 0) 0 else if (i > 255) 255 else i }

    /**
     * `AreaScale_argb`: every output pixel averages the source pixels it covers (fractional edges by overlap), colour
     * weighted by alpha (premultiplied floats across, then down, then back to straight alpha); a fully transparent
     * result is 0.
     */
    fun areaScale(src: ArgbImage, dw: Int, dh: Int): ArgbImage {
        val sw = src.width; val sh = src.height
        val mid = FloatArray(4 * dw * sh)
        val rx = sw.toFloat() / dw; val ry = sh.toFloat() / dh
        for (x in 0 until dw) {
            val s = spanOf(x, rx, sw)
            for (y in 0 until sh) {
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                for (i in s.i0 until s.i1) {
                    val p = src.pixels[y * sw + i]
                    val pa = (p ushr 24).toFloat() * s.weight(i)
                    a += pa; r += pa * ((p shr 16) and 0xFF); g += pa * ((p shr 8) and 0xFF); b += pa * (p and 0xFF)
                }
                val m = 4 * (y * dw + x)
                mid[m] = a / rx; mid[m + 1] = r / rx; mid[m + 2] = g / rx; mid[m + 3] = b / rx
            }
        }
        val out = ArgbImage(dw, dh)
        for (y in 0 until dh) {
            val s = spanOf(y, ry, sh)
            for (x in 0 until dw) {
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                for (i in s.i0 until s.i1) {
                    val m = 4 * (i * dw + x); val w = s.weight(i)
                    a += mid[m] * w; r += mid[m + 1] * w; g += mid[m + 2] * w; b += mid[m + 3] * w
                }
                a /= ry; r /= ry; g /= ry; b /= ry
                out.pixels[y * dw + x] = if (a > 0f) (to8(a) shl 24) or (to8(r / a) shl 16) or (to8(g / a) shl 8) or to8(b / a) else 0
            }
        }
        return out
    }

    /** `SDL_BLENDMODE_NONE`: [img]'s pixels as they are at ([x], [y]), clipped to [canvas]. */
    private fun copy(canvas: ArgbImage, img: ArgbImage, x: Int, y: Int) = each(canvas, img, x, y) { _, s -> s }

    /** The shape's shadow ([offset] down-right, its alpha x [shadowAlpha] / 255, black), then the shape blended over it. */
    private fun layer(canvas: ArgbImage, img: ArgbImage, x: Int, y: Int, offset: Int, shadowAlpha: Int) {
        each(canvas, img, x + offset, y + offset) { d, s -> blend(d, 0, (s ushr 24) * shadowAlpha / 255) }
        each(canvas, img, x, y) { d, s -> blend(d, s and 0xFFFFFF, s ushr 24) }
    }

    /** SDL's blend of colour [rgb] at alpha [a] over [d]: colour `rgb*a + d*(255-a)`, alpha `a + dA*(255-a)`, each /255. */
    private fun blend(d: Int, rgb: Int, a: Int): Int {
        if (a == 0) return d
        if (a == 255) return (0xFF shl 24) or rgb
        val na = 255 - a
        fun ch(shift: Int) = (((rgb shr shift) and 0xFF) * a + ((d shr shift) and 0xFF) * na) / 255
        val da = a + (d ushr 24) * na / 255
        return (da shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private inline fun each(canvas: ArgbImage, img: ArgbImage, x: Int, y: Int, op: (dst: Int, src: Int) -> Int) {
        for (sy in 0 until img.height) {
            val cy = y + sy
            if (cy < 0 || cy >= canvas.height) continue
            for (sx in 0 until img.width) {
                val cx = x + sx
                if (cx < 0 || cx >= canvas.width) continue
                val i = cy * canvas.width + cx
                canvas.pixels[i] = op(canvas.pixels[i], img.pixels[sy * img.width + sx])
            }
        }
    }
}
