package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Color
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The pad palette each theme and button set resolves to. */
class PadColorsForTest {
    @Test fun charcoalAndRetroIgnoreTheButtonSet() {
        for (b in PadButtons.entries) {
            assertSame(CharcoalPadColors, padColorsFor(PadTheme.CHARCOAL, b))
            assertSame(RetroPadColors, padColorsFor(PadTheme.RETRO, b))
        }
    }

    @Test fun blackGreyIsCharcoalsButtonsOnABlackBody() {
        val c = padColorsFor(PadTheme.BLACK, PadButtons.GREY)
        assertEquals(Color.Black, c.surface)
        assertFalse(c.outline)
        assertSame(CharcoalPadColors.face, c.face)
        assertSame(CharcoalPadColors.dpad, c.dpad)
        assertSame(CharcoalPadColors.pill, c.pill)
        assertEquals(Color.Black, c.socketFill)   // the groove is the body's black: only its lit edge shows
        assertSame(c, padColorsFor(PadTheme.BLACK))   // Grey is the default set
    }

    @Test fun blackOutlineIsAnOutlinePadOnABlackBody() {
        val c = padColorsFor(PadTheme.BLACK, PadButtons.OUTLINE)
        assertEquals(Color.Black, c.surface)
        assertTrue(c.outline)
        assertEquals(Color.Transparent, c.socketFill)
    }

    @Test fun aConsolesOwnFaceIsAnOutlineInItsTintOnTheOutlinePad() {
        val outline = padColorsFor(PadTheme.BLACK, PadButtons.OUTLINE)
        val a = N64PadColors.a
        assertEquals(Color(0xFF3B5BDB), a.tint)
        assertSame(a, padColorsFor(PadTheme.BLACK, PadButtons.GREY).faceOf(a))   // filled pads keep it as it is
        assertTrue(outline.faceOf(a) !== a)
        // The C buttons' dark arrows would vanish on black: the outline pad letters them in the face's tint.
        assertEquals(N64PadColors.c.tint, outline.labelOf(N64PadColors.c, N64PadColors.cLabel))
        assertEquals(N64PadColors.cLabel, CharcoalPadColors.labelOf(N64PadColors.c, N64PadColors.cLabel))
        assertEquals(outline.label, outline.labelOf(null, null))
    }
}
