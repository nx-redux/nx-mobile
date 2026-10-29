package com.nxredux.mobile.launcher

/**
 * Where an app icon layer's shape sits, so a tool tile can draw it as a one-colour glyph like the built-in tools'.
 * The layer is judged by its alpha alone: a logo stays clear of the icon's edges and leaves most of it empty, while a
 * layer that is really a background (an opaque fill, a legacy icon's square or circle plate) does not, and is refused.
 */
object IconSilhouette {
    /** The square to crop, in the layer's pixels; it may reach past the layer's edges (that part is empty). */
    data class Frame(val x: Float, val y: Float, val side: Float)

    /** The share of the frame the shape spans, as the built-in glyphs span 36 of their 48 (MenuArt's menu_icon_*). */
    const val CONTENT = 0.75f

    private const val SEEN = 32        // alpha a pixel needs to count toward the shape's bounds
    private const val SOLID = 128      // alpha a pixel needs to count as covering
    private const val MAX_COVER = 0.8f // more of the icon than this covered: a background, not a logo
    private const val MAX_EDGE = 0.5f  // more of the icon's outer ring than this covered: it bleeds to the edges
    private const val MAX_FILL = 0.9f  // more of the shape's own bounds than this covered: a plain block (a legacy plate)
    private const val FAINT = 64       // [shapeAlpha]: at or below this a pixel is shade, not shape
    private const val FIRM = 160       // [shapeAlpha]: at or above this a pixel is wholly shape

    /**
     * Alpha [a] (0-255) as the shape's: a faint pixel (a long shadow, a soft glow under the logo, which stand out once
     * tinted) drops out, a firm one is opaque, and the edges between keep their smoothing.
     */
    fun shapeAlpha(a: Int): Int = when {
        a <= FAINT -> 0
        a >= FIRM -> 255
        else -> (a - FAINT) * 255 / (FIRM - FAINT)
    }

    private const val LIGHT_FROM = 176   // [layerAlpha]: a pixel's least channel from here on starts to read as white
    private const val LIGHT = 224        // [layerAlpha]: from here on it is white, a hole in a coloured logo
    private const val MIN_LIGHT = 0.03f  // [layerAlpha]: less of the shape white than this: specks, not detail
    private const val MAX_LIGHT = 0.6f   // [layerAlpha]: more than this: a white logo (its colour only an accent)

    /**
     * A foreground layer's [argb] pixels as its shape's alpha ([shapeAlpha]), the white inside a coloured logo cut
     * out: a full-colour logo's white is its detail (Chrome's ring, a play triangle), which a themed icon draws as
     * holes, and the logo alone in alpha would be a plain disc. A logo mostly white, or with barely any, keeps it.
     */
    fun layerAlpha(argb: IntArray): IntArray {
        val alpha = IntArray(argb.size) { shapeAlpha(argb[it] ushr 24) }
        var firm = 0; var light = 0
        for (i in argb.indices) if (alpha[i] == 255) { firm++; if (leastChannel(argb[i]) >= LIGHT) light++ }
        if (firm == 0 || light < MIN_LIGHT * firm || light > MAX_LIGHT * firm) return alpha
        for (i in argb.indices) {
            val l = leastChannel(argb[i])
            if (l > LIGHT_FROM) alpha[i] = if (l >= LIGHT) 0 else alpha[i] * (LIGHT - l) / (LIGHT - LIGHT_FROM)
        }
        return alpha
    }

    private fun leastChannel(c: Int) = minOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)

    private const val KEY_SHARE = 0.6f // the outer ring's share one colour needs to be the icon's plate ([contrastAlpha])
    private const val NEAR = 24        // [contrastAlpha]: a colour this close to the plate's (largest channel gap) is plate
    private const val FAR = 96         // [contrastAlpha]: one this far is wholly logo

    /**
     * The shape in a whole [size] × [size] icon's [argb] pixels (row by row, the visible square, opaque): what differs
     * from its plate, the one colour most of its outer ring shows, as alpha for [frame]. For an icon whose logo is
     * painted on its plate in one layer, with no themed layer. Null when no colour holds most of the ring (a gradient,
     * a photo): there is no plate to tell the logo from.
     */
    fun contrastAlpha(argb: IntArray, size: Int): IntArray? {
        require(size > 1 && argb.size == size * size)
        val ring = argb.filterIndexed { i, _ -> i % size == 0 || i % size == size - 1 || i < size || i >= size * (size - 1) }
        // Quantised to 3 bits a channel, so a plate's dithering or a soft edge still counts as its colour.
        val (key, count) = ring.groupBy { it and 0xE0E0E0 }.maxByOrNull { it.value.size }!!.let { it.key to it.value.size }
        if (count < KEY_SHARE * ring.size) return null
        val plate = ring.filter { it and 0xE0E0E0 == key }
        val r0 = plate.sumOf { it shr 16 and 0xFF } / plate.size
        val g0 = plate.sumOf { it shr 8 and 0xFF } / plate.size
        val b0 = plate.sumOf { it and 0xFF } / plate.size
        return IntArray(argb.size) { i ->
            val c = argb[i]
            val gap = maxOf(kotlin.math.abs((c shr 16 and 0xFF) - r0), kotlin.math.abs((c shr 8 and 0xFF) - g0), kotlin.math.abs((c and 0xFF) - b0))
            when {
                gap <= NEAR -> 0
                gap >= FAR -> 255
                else -> (gap - NEAR) * 255 / (FAR - NEAR)
            }
        }
    }

    /**
     * [alpha] is a [size] × [size] layer's alpha (0-255, [shapeAlpha]'s), row by row, covering the icon's visible
     * square. The frame centred on its shape with the shape at [CONTENT] of it; null when there is no shape or it is
     * not a logo.
     */
    fun frame(alpha: IntArray, size: Int): Frame? {
        require(size > 0 && alpha.size == size * size)
        var minX = size; var minY = size; var maxX = -1; var maxY = -1
        var cover = 0L; var edge = 0; var solid = 0
        for (y in 0 until size) for (x in 0 until size) {
            val a = alpha[y * size + x]
            cover += a
            if (a >= SOLID) {
                solid++
                if (x == 0 || y == 0 || x == size - 1 || y == size - 1) edge++
            }
            if (a >= SEEN) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        if (cover > MAX_COVER * 255L * size * size) return null
        if (size > 1 && edge > MAX_EDGE * 4 * (size - 1)) return null
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        if (solid > MAX_FILL * w * h) return null
        val side = maxOf(w, h) / CONTENT
        return Frame((minX + maxX + 1) / 2f - side / 2, (minY + maxY + 1) / 2f - side / 2, side)
    }
}
