package com.nxredux.mobile.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// An Android game's abstract picture from its launcher icon: the palette, the recoloured plan, the icon's place, the URL.
class IconPaletteTest {
    private val e = 1e-6

    private fun icon(vararg runs: Pair<Int, Int>): IntArray = runs.flatMap { (argb, n) -> List(n) { argb } }.toIntArray()

    private fun hueGap(a: Double, b: Double) = abs(a - b).let { minOf(it, 360 - it) }

    @Test fun aTwoColourIconGivesItsTwoHuesStrongestFirst() {
        val p = iconPalette(icon(0xFFFF0000.toInt() to 300, 0xFF0000FF.toInt() to 100, 0xFFFFFFFF.toInt() to 50))!!
        assertTrue(p.size >= 2)
        assertEquals(0.0, p[0].hue, 0.5)     // red: the most pixels
        assertEquals(240.0, p[1].hue, 0.5)   // then blue
        assertEquals(100.0, p[0].saturation, 0.5); assertEquals(50.0, p[0].lightness, 0.5)
        assertTrue(p.all { it.alpha == 1.0 })
    }

    @Test fun saturationWeighsTheRanking() {
        // more muted green pixels than vivid orange ones, but the orange outweighs them by saturation
        val p = iconPalette(icon(0xFFFF8000.toInt() to 100, 0xFF50AF50.toInt() to 120))!!
        assertEquals(30.0, p[0].hue, 1.0)
        assertEquals(120.0, p[1].hue, 1.0)
    }

    @Test fun transparentPixelsAreIgnored() {
        // a sea of half-transparent green round a small opaque red / blue mark: the green never counts
        val p = iconPalette(icon(0x40008000 to 5000, 0x7F00FF00 to 5000, 0xFFFF0000.toInt() to 30, 0xFF0000FF.toInt() to 20))!!
        assertTrue(p.none { hueGap(it.hue, 120.0) < 30 })
        assertEquals(0.0, p[0].hue, 0.5)
        assertNull(iconPalette(icon(0x10FF0000 to 100, 0x00000000 to 100)))   // nothing opaque: the plain picture
        assertNull(iconPalette(IntArray(0)))
    }

    @Test fun aColourfulIconWithSomeGreyKeepsItsColours() {
        val p = iconPalette(icon(0xFF808080.toInt() to 600, 0xFFFFFFFF.toInt() to 200, 0xFFFF0000.toInt() to 60, 0xFF00C000.toInt() to 40))!!
        assertTrue(p.any { hueGap(it.hue, 0.0) < 5 && it.saturation > 50 })
        assertTrue(p.any { hueGap(it.hue, 120.0) < 5 && it.saturation > 50 })
    }

    @Test fun aGreyIconGivesAGreyscalePaletteOfThreeOrMoreLevels() {
        val p = iconPalette(icon(0xFF202020.toInt() to 300, 0xFF909090.toInt() to 200, 0xFFE0E0E0.toInt() to 100, 0xFF223344.toInt() to 5))!!
        assertTrue(p.size >= 3)
        assertTrue(p.all { it.saturation == 0.0 })
        val levels = p.map { it.lightness }
        assertEquals(levels.size, levels.distinct().size)
        for (a in levels) for (b in levels) if (a != b) assertTrue(abs(a - b) >= 15.0)
        assertEquals(0x20 / 255.0 * 100, levels[0], 0.5)   // the strongest level first
        assertTrue(levels.max() - levels.min() > 50)       // charcoal to light grey
    }

    @Test fun aSingleToneWhiteIconGetsDarkerSteps() {
        val p = iconPalette(icon(0xFFFFFFFF.toInt() to 400, 0x00000000 to 200))!!
        assertEquals(listOf(100.0, 75.0, 50.0), p.map { it.lightness })
        assertTrue(p.all { it.saturation == 0.0 })
        val black = iconPalette(icon(0xFF000000.toInt() to 10))!!
        assertEquals(listOf(0.0, 25.0, 50.0), black.map { it.lightness })   // a dark tone steps lighter
    }

    @Test fun aSingleHueIconGetsStepsOfItsHue() {
        val p = iconPalette(icon(0xFFFF0000.toInt() to 100, 0xFFFFFFFF.toInt() to 300))!!
        assertTrue(p.size >= 3)
        assertTrue(p.all { hueGap(it.hue, 0.0) < 1 })
        assertEquals(3, p.map { it.lightness }.distinct().size)
    }

    @Test fun thePaletteRecoloursThePlanAndKeepsItsShapes() {
        val seed = abstractAppSeed("com.example.alpha/com.example.alpha.Main")
        val palette = listOf(Hsla(10.0, 90.0, 50.0), Hsla(200.0, 60.0, 40.0), Hsla(100.0, 50.0, 70.0))
        val plain = abstractPlan(seed)
        val p = abstractPlan(seed, palette = palette)
        assertEquals(plain.angle, p.angle); assertEquals(plain.stops.size, p.stops.size); assertEquals(plain.shapes.size, p.shapes.size)
        for ((a, b) in plain.shapes.zip(p.shapes)) {
            assertEquals(a.copy(color = b.color), b)   // the same shape, place, turn and size
            assertEquals(a.color.alpha, b.color.alpha, e)
        }
        p.stops.forEachIndexed { i, c -> assertEquals(palette[i % 3].hue, c.hue, e) }
        p.shapes.forEachIndexed { i, s -> assertEquals(palette[(i + 1) % 3].hue, s.color.hue, e) }
        assertEquals(70.0, p.stops[0].saturation, e)   // capped
        assertTrue(p.stops.all { it.lightness in 20.0..50.0 && it.alpha == 1.0 })
        assertTrue(p.shapes.all { it.color.lightness in 35.0..75.0 })
        assertEquals(plain, abstractPlan(seed, palette = null))
        assertEquals(plain, abstractPlan(seed, palette = emptyList()))
        // a white icon's derived steps stay distinct on the ground
        val white = abstractPlan(seed, palette = iconPalette(intArrayOf(-1)))
        assertEquals(white.stops.size, white.stops.map { it.lightness }.distinct().size)
    }

    @Test fun theIconSitsCentredAt40PercentOfTheHeightAtItsAspect() {
        val sq = iconPlacement(1024.0, 768.0, 192.0, 192.0)
        assertEquals(307.2, sq.height, e); assertEquals(307.2, sq.width, e)
        assertEquals(512.0, sq.left + sq.width / 2, e); assertEquals(384.0, sq.top + sq.height / 2, e)
        val wide = iconPlacement(ABSTRACT_W, ABSTRACT_H, 200.0, 100.0)
        assertEquals(ABSTRACT_H * 0.4, wide.height, e); assertEquals(wide.height * 2, wide.width, e)
        assertEquals(ABSTRACT_W / 2, wide.left + wide.width / 2, e); assertEquals(ABSTRACT_H / 2, wide.top + wide.height / 2, e)
        assertTrue(wide.shadowDy > 0 && wide.shadowBlur > 0 && wide.shadowBlur < wide.height / 10)
    }

    @Test fun androidUrlsHaveTheirOwnFormAndRomUrlsAreUnchanged() {
        val id = "com.example.alpha/com.example.alpha.MainActivity"
        val url = abstractAppArtUrl(id)
        assertEquals("nxabstract://app/$id", url)
        assertEquals(id, abstractAppIdOf(url))
        assertNull(abstractSeedOf(url))                           // never read as a ROM's
        assertEquals(abstractSeed(abstractArtKey(ANDROID_TAG, id)), abstractAppSeed(id))   // the same seed as its Android-tag key
        val rom = abstractArtUrl("MD", "Gunstar Heroes (USA).md")
        assertEquals("nxabstract://d973b800", rom)
        assertNull(abstractAppIdOf(rom))
        assertNull(abstractAppIdOf("nxabstract://app/"))
        assertNull(abstractAppIdOf("file:///x.png"))
        assertNotEquals(abstractAppSeed("a/A"), abstractAppSeed("b/B"))
    }

    @Test fun androidUrlsHaveAnIconLessForm() {
        val id = "com.example.alpha/com.example.alpha.MainActivity"
        val iconed = abstractAppArtUrl(id)
        val bare = abstractAppArtUrl(id, icon = false)
        assertEquals("nxabstract://app/$id?icon=0", bare)
        assertNotEquals(iconed, bare)                            // its own memory-cache key
        assertEquals(AbstractAppArt(id, icon = true), abstractAppArtOf(iconed))
        assertEquals(AbstractAppArt(id, icon = false), abstractAppArtOf(bare))
        assertEquals(id, abstractAppIdOf(bare))
        assertNull(abstractSeedOf(bare))
        assertNull(abstractAppArtOf("nxabstract://app/?icon=0"))
        assertNull(abstractAppArtOf("nxabstract://app/a/A?x=1"))
        assertNull(abstractAppArtOf(abstractArtUrl("MD", "Gunstar Heroes (USA).md")))
    }
}
