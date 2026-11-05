package com.nxredux.mobile.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mix as NX Redux composes it (`scraper_compositor.c`, `nextui/area_scale.c`), pixel for pixel. */
class MixCompositorTest {
    private fun solid(w: Int, h: Int, argb: Int) = ArgbImage(w, h, IntArray(w * h) { argb })
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test fun onCanvasCentresAWideImageWhole() {
        val out = MixCompositor.onCanvas(solid(768, 288, red))
        assertEquals(MixCompositor.W, out.width); assertEquals(MixCompositor.H, out.height)
        for (y in listOf(0, 71, 216, 287)) for (x in 0 until out.width) assertEquals(0, out[x, y] ushr 24, "row $y")
        for (x in 0 until out.width) assertEquals(0xFF, out[x, 144] ushr 24)
    }

    @Test fun onCanvasGrowsASmallImage() {
        val out = MixCompositor.onCanvas(solid(96, 72, green))
        assertTrue(out.pixels.all { it == green })
    }

    @Test fun fitSizeTruncatesAndKeepsOnePixel() {
        assertEquals(96 to 144, MixCompositor.fitSize(100, 150, 192, 144, grow = true))
        assertEquals(192 to 48, MixCompositor.fitSize(200, 50, 192, 86, grow = true))
        assertEquals(192 to 172, MixCompositor.fitSize(160, 144, 192, 192, grow = true))   // 1.2 x 144 = 172.8 -> 172
        assertEquals(160 to 144, MixCompositor.fitSize(160, 144, 192, 192, grow = false))
        assertEquals(192 to 1, MixCompositor.fitSize(100_000, 10, 192, 144, grow = true))
    }

    @Test fun areaScaleWeightsColourByAlpha() {
        // Opaque white beside transparent black: half alpha, still white (the transparent pixel does not darken it).
        val out = MixCompositor.areaScale(ArgbImage(2, 1, intArrayOf(-1, 0)), 1, 1)
        assertEquals(0x80FFFFFF.toInt(), out[0, 0])
        assertEquals(0, MixCompositor.areaScale(ArgbImage(2, 2), 1, 1)[0, 0])   // all transparent: 0
        // A 3 -> 2 shrink: the middle source pixel is shared half and half.
        val row = MixCompositor.areaScale(ArgbImage(3, 1, intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt() or 0x960000, 0xFF000000.toInt() or 0xFF0000)), 2, 1)
        assertEquals(0xFF000000.toInt() or (50 shl 16), row[0, 0])   // (0 + 150 * 0.5) / 1.5
        assertEquals(0xFF000000.toInt() or (220 shl 16), row[1, 0])  // (150 * 0.5 + 255) / 1.5
    }

    @Test fun resizeSharpRepeatsWholeFactorsFirst() {
        val checker = ArgbImage(2, 1, intArrayOf(red, blue))
        val four = MixCompositor.resizeSharp(checker, 4, 2)   // k = 2: pure blocks
        assertEquals(listOf(red, red, blue, blue), (0 until 4).map { four[it, 0] })
        val five = MixCompositor.resizeSharp(checker, 5, 2)   // k = 2 (4x2), then 4 -> 5: only the middle column blends
        assertEquals(red, five[0, 0]); assertEquals(red, five[1, 1]); assertEquals(blue, five[3, 0]); assertEquals(blue, five[4, 1])
        val mid = five[2, 0]
        assertEquals(0xFF, mid ushr 24); assertEquals(0, (mid shr 8) and 0xFF)
        assertTrue(((mid shr 16) and 0xFF) in 127..128 && (mid and 0xFF) in 127..128, mid.toString(16))
    }

    @Test fun fillSharpCropsTheCentre() {
        // 160x144 -> 338x254: scale 2.1125, 338x304, 25 rows cropped from the top.
        val src = ArgbImage(160, 144, IntArray(160 * 144) { i -> if (i / 160 < 10) red else blue })
        val out = MixCompositor.fillSharp(src, 338, 254)
        assertEquals(338 to 254, out.width to out.height)
        assertEquals(blue, out[0, 0])   // the top 10 source rows (21 of 304) are within the 25 cropped
        assertEquals(blue, out[337, 253])
    }

    @Test fun nothingToComposeIsNoMix() = assertNull(MixCompositor.create(null, null, null))

    @Test fun layout() {
        val mix = MixCompositor.create(solid(160, 144, red), solid(100, 150, green), solid(200, 50, blue))!!
        assertEquals(384 to 288, mix.width to mix.height)
        // The screenshot: x 23..360, y 0..253, copied opaque; transparent around it.
        assertEquals(0, mix[22, 0]); assertEquals(red, mix[23, 0]); assertEquals(red, mix[360, 0]); assertEquals(0, mix[361, 0])
        assertEquals(red, mix[150, 253]); assertEquals(0, mix[150, 254])
        // The box: 96x144 at (7, 137); its shadow 2 down-right at alpha 100 (over the screenshot: 255 * 155 / 255).
        assertEquals(green, mix[7, 137]); assertEquals(green, mix[102, 280])
        assertEquals(0xFF9B0000.toInt(), mix[103, 200])   // shadow over the screenshot
        assertEquals(0x64000000, mix[104, 282])            // shadow over the transparent canvas
        assertEquals(0, mix[103, 283])                     // below the shadow's bottom (282): canvas
        // The wheel: 192x48 at (185, 233), its shadow 1 down-right at alpha 80.
        assertEquals(blue, mix[185, 233]); assertEquals(blue, mix[376, 280])
        assertEquals(0x50000000, mix[377, 281]); assertEquals(0x50000000, mix[377, 234])
        assertEquals(0, mix[377, 233])
    }

    @Test fun aSemiTransparentLayerBlendsAsSdl() {
        // SDL_BLENDMODE_BLEND over the transparent canvas: colour x alpha, alpha kept (no premultiply undone).
        val mix = MixCompositor.create(null, null, solid(192, 86, 0x80FFFFFF.toInt()))!!
        assertEquals((128 shl 24) or 0x808080, mix[185, 195])   // 255 * 128 / 255 = 128 per channel
        // Its shadow under it, then the wheel over the shadow: alpha 40 + ...
        val shadowA = 128 * 80 / 255                           // 40
        val a = 128 + shadowA * 127 / 255                      // 147
        assertEquals((a shl 24) or 0x808080, mix[190, 200])
    }

    @Test fun missingLayersAreSkipped() {
        val mix = MixCompositor.create(solid(4, 3, red), null, null)!!
        assertEquals(red, mix[23, 0]); assertEquals(0, mix[10, 280])
    }
}
