package com.nxredux.mobile.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Per-slot caps, never upscaled, PNG where alpha or lossless pixels matter, JPEG otherwise. */
class ArtFitTest {
    private val shot = ArtCaps.of(ArtSlot.SCREENSHOT)
    private val hero = ArtCaps.of(ArtSlot.HERO)
    private val grid = ArtCaps.of(ArtSlot.GRID)
    private val box3d = ArtCaps.of(ArtSlot.BOX_3D)
    private val box2d = ArtCaps.of(ArtSlot.BOX_2D)

    @Test fun capsPerSlot() {
        assertEquals(ArtCap(900, 900, ArtEncoding.PNG), box3d)
        assertEquals(ArtCap(480, 576, ArtEncoding.PNG), box2d)   // NX Redux's 2D box and wheel: full-colour PNG within 480x576
        assertEquals(ArtCap(480, 576, ArtEncoding.PNG), ArtCaps.of(ArtSlot.WHEEL))
        assertEquals(ArtCap(384, 288, ArtEncoding.PNG), ArtCaps.of(ArtSlot.MIX))
        assertEquals(ArtCap(1280, 960), shot)
        assertEquals(ArtCap(600, 900, ArtEncoding.JPEG, 85), grid)
        assertEquals(ArtCap(1920, 640, ArtEncoding.JPEG, 85), hero)
    }

    @Test fun neverUpscales() {
        assertEquals(256 to 224, ArtFit.size(256, 224, shot))
        assertEquals(1280 to 960, ArtFit.size(1280, 960, shot))
        assertEquals(1 to 1, ArtFit.size(1, 1, hero))
    }

    @Test fun fitsHeroCap() = assertEquals(1920 to 620, ArtFit.size(3840, 1240, hero))

    @Test fun fitsGridCap() = assertEquals(579 to 900, ArtFit.size(920, 1430, grid))

    @Test fun fitKeepsAspectAndAtLeastOnePixel() {
        assertEquals(900 to 450, ArtFit.size(2000, 1000, box3d))
        assertEquals(450 to 900, ArtFit.size(1000, 2000, box3d))
        assertEquals(1920 to 1, ArtFit.size(100_000, 10, hero))
        assertEquals(1 to 640, ArtFit.size(10, 100_000, hero))
    }

    @Test fun box3dAlwaysPng() {
        assertEquals(ArtEncoding.PNG, ArtFit.encoding(ImageType.JPEG, hasAlpha = false, box3d))
        assertEquals(ArtEncoding.PNG, ArtFit.encoding(ImageType.PNG, hasAlpha = true, box3d))
    }

    @Test fun box2dAndWheelAlwaysPngAndShrinkOnly() {
        assertEquals(ArtEncoding.PNG, ArtFit.encoding(ImageType.JPEG, hasAlpha = false, box2d))
        assertEquals(ArtEncoding.PNG, ArtFit.encoding(ImageType.JPEG, hasAlpha = false, ArtCaps.of(ArtSlot.WHEEL)))
        assertEquals(480 to 360, ArtFit.size(1600, 1200, box2d))
        assertEquals(400 to 576, ArtFit.size(1000, 1440, box2d))
        assertEquals(200 to 100, ArtFit.size(200, 100, ArtCaps.of(ArtSlot.WHEEL)))   // never grown
    }

    @Test fun jpegSourceStaysJpeg() {
        assertEquals(ArtEncoding.JPEG, ArtFit.encoding(ImageType.JPEG, hasAlpha = false, shot))
    }

    @Test fun pngWithAlphaStaysPng() {
        assertEquals(ArtEncoding.PNG, ArtFit.encoding(ImageType.PNG, hasAlpha = false, shot))   // pixel art stays lossless
        assertEquals(ArtEncoding.PNG, ArtFit.encoding(ImageType.WEBP, hasAlpha = false, shot))
    }

    @Test fun heroForcedJpeg() {
        assertEquals(ArtEncoding.JPEG, ArtFit.encoding(ImageType.PNG, hasAlpha = true, hero))
        assertEquals(ArtEncoding.JPEG, ArtFit.encoding(ImageType.PNG, hasAlpha = false, grid))
    }

    @Test fun passThroughOnlyWhenWithinCapAndSameEncoding() {
        assertTrue(ArtFit.passThrough(ImageType.PNG, 256, 224, hasAlpha = false, shot))
        assertTrue(ArtFit.passThrough(ImageType.JPEG, 1920, 620, hasAlpha = false, hero))
        assertTrue(ArtFit.passThrough(ImageType.PNG, 900, 900, hasAlpha = true, box3d))
        assertFalse(ArtFit.passThrough(ImageType.PNG, 3840, 1240, hasAlpha = false, shot))   // too large
        assertFalse(ArtFit.passThrough(ImageType.PNG, 1920, 620, hasAlpha = false, hero))    // needs JPEG
        assertFalse(ArtFit.passThrough(ImageType.JPEG, 600, 600, hasAlpha = false, box3d))   // needs PNG
        assertFalse(ArtFit.passThrough(ImageType.WEBP, 100, 100, hasAlpha = false, shot))    // re-encoded as PNG
        assertFalse(ArtFit.passThrough(ImageType.UNKNOWN, 100, 100, hasAlpha = false, shot))
    }

    @Test fun imageTypeFromMagicBytes() {
        fun b(vararg x: Int) = ByteArray(x.size) { x[it].toByte() }
        assertEquals(ImageType.PNG, imageTypeOf(b(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(ImageType.JPEG, imageTypeOf(b(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
        assertEquals(ImageType.WEBP, imageTypeOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()))
        assertEquals(ImageType.GIF, imageTypeOf("GIF89a".encodeToByteArray()))
        assertEquals(ImageType.UNKNOWN, imageTypeOf("RIFF\u0000\u0000\u0000\u0000WAVE".encodeToByteArray()))
        assertEquals(ImageType.UNKNOWN, imageTypeOf("<html>".encodeToByteArray()))
        assertEquals(ImageType.UNKNOWN, imageTypeOf(b(0x89, 0x50)))
        assertEquals(ImageType.UNKNOWN, imageTypeOf(ByteArray(0)))
    }
}
