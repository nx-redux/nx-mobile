package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.shared.res.*
import com.nxredux.mobile.ui.ANDROID_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ControllerArtTest {
    @Test fun everyConsoleHasAPad() {
        val missing = CoreRegistry.consoles.map { it.id }.filter { it !in consolePadsById}
        assertTrue(missing.isEmpty(), "no controller art for $missing")
    }

    @Test fun doomAndPico8ShareTheVic20AndArcadeHasTheCompetitionPro() {
        assertSame(consolePadsById.getValue("prboom"), consolePadsById.getValue("p8"))
        assertSame(Res.drawable.menu_pad_vic20, consolePadsById.getValue("prboom").res)
        assertSame(Res.drawable.menu_pad_fbn, consolePadsById.getValue("fbn").res)
        assertEquals(consolePadsById["fbn"], consolePad("FBN"))
    }

    @Test fun addOnsAndSiblingsReuseAPad() {
        assertSame(consolePadsById.getValue("sfc").res, consolePadsById.getValue("sgb").res)
        assertSame(consolePadsById.getValue("md").res, consolePadsById.getValue("segacd").res)
        assertSame(consolePadsById.getValue("md").res, consolePadsById.getValue("32x").res)
        assertSame(consolePadsById.getValue("md").res, consolePadsById.getValue("saturn").res)
        assertSame(consolePadsById.getValue("ngpc").res, consolePadsById.getValue("ngp").res)
    }

    @Test fun the3dsHasItsOwnArtNotTheDs() {
        assertSame(Res.drawable.menu_pad_3ds, consolePadsById.getValue("3ds").res)
        assertSame(Res.drawable.menu_pad_nds, consolePadsById.getValue("nds").res)
    }

    @Test fun aTagFindsItsConsolesPadWhateverItsCase() {
        assertEquals(consolePadsById["md"], consolePad("GPGX"))   // Genesis Plus GX on Sega Genesis
        assertEquals(consolePadsById["sfc"], consolePad("supa"))
        assertNull(consolePad("NOPE"))   // no console claims it
    }

    @Test fun theAndroidConsoleHasTheGalaxyFold() {
        assertSame(Res.drawable.menu_pad_android, consolePad(ANDROID_TAG)?.res)
        assertTrue(consolePadsById.values.none { it.res == Res.drawable.menu_pad_android })   // not a registry console
    }

    @Test fun brightnessIsPlayStationRelative() {
        assertEquals(1f, consolePadsById.getValue("ps").brightness)
        consolePadsById.forEach { (id, p) -> assertTrue(p.brightness in 0.5f..2f, "$id ${p.brightness}") }
    }

    @Test fun colourMatrixIsIdentityAtFullBrightnessAndSaturation() {
        val m = padColorMatrix(1f, 1f)
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        identity.forEachIndexed { i, v -> assertEquals(v, m[i], 1e-5f, "[$i]") }
    }

    @Test fun colourMatrixGreysThenScalesRgbButNotAlpha() {
        val m = padColorMatrix(0.5f, 0f)
        for (row in 0..2) {   // every colour row is the luminance weights times the brightness
            assertEquals(0.213f * 0.5f, m[row * 5], 1e-5f)
            assertEquals(0.715f * 0.5f, m[row * 5 + 1], 1e-5f)
            assertEquals(0.072f * 0.5f, m[row * 5 + 2], 1e-5f)
        }
        assertEquals(1f, m[18], 1e-5f)
        assertEquals(0f, m[4] + m[9] + m[14] + m[19], 1e-5f)   // no offsets
    }

    @Test fun rowPadBoxIsTheMockupsMultipleOfTheSlot() {
        val b = rowPadBox(130f)   // landscape logo slot 330 × 130
        assertEquals(364f, b.w, 1e-3f); assertEquals(234f, b.h, 1e-3f); assertEquals(0f, b.dy, 1e-3f)
    }

    @Test fun padAlphaShowsOnlyTheFocusedConsole() {
        assertEquals(0.6f, padAlpha(0f), 1e-5f)
        assertEquals(0.3f, padAlpha(-0.5f), 1e-5f)
        assertEquals(0f, padAlpha(1f), 1e-5f)
        assertEquals(0f, padAlpha(2.5f), 1e-5f)
    }

    @Test fun stackPadBoxCentresOnTheLogoAndItsCount() {
        val r = StackReach(30f, 30f, countLine = 18f)   // a logo drawn 60 tall in a 100 slot, its count 26 under it
        val b = stackPadBox(100f, r)
        assertEquals(280f, b.w, 1e-3f); assertEquals(180f, b.h, 1e-3f)   // the row's 2.8 × 1.8
        assertEquals(13f, b.dy, 1e-3f)   // the block's centre is 13 below the slot's
    }

    @Test fun aPadReachIsItsDrawnHalfEitherSideOfTheBlockPlusClearance() {
        val r = StackReach(30f, 30f, countLine = 18f)
        val box = stackPadBox(100f, r)
        val withPad = r.withPad(box, padAspect = 0.5f)   // a wide pad: 280 × 140 drawn in the 280 × 180 box
        assertEquals(70f - 13f + PAD_CLEAR, withPad.padUp, 1e-4f)
        assertEquals(70f + 13f + PAD_CLEAR, withPad.padDown, 1e-4f)
        assertEquals(r.up, withPad.up); assertEquals(r.down, withPad.down)
    }
}
