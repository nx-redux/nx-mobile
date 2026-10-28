package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Color
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.SecondaryPreset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A selected options row's label rect and label text (NX Redux UI_renderSettingsRow: accentPill() under
 * UI_getListTextColor(1), the accent's own text): the Settings value pages and the in-game menu follow the accent as the
 * main menu's pill does. The bar behind the value and the value on it follow the Secondary accent (default Auto: NX teal
 * on White, light text).
 */
class OptionsRowAccentTest {
    private val teal = Color(0xFF006666)
    private val light = Color(0xFFDCDCDC)

    @Test fun aSelectedOptionsRowTakesTheAccentAndItsText() {
        val red = nxColorsFor(AccentPreset.RED)
        // The default Secondary accent is Auto: each accent's own dark pair (White's is NX teal).
        assertEquals(NxOptionsSelection(Color(0xFFF87171), Color.Black, Color(0xFF703333), light), nxOptionsSelection(red))
        val sky = nxColorsFor(AccentPreset.SKY)
        assertEquals(NxOptionsSelection(Color(0xFF38BDF8), Color.Black, Color(0xFF195570), light), nxOptionsSelection(sky))
        assertEquals(teal, nxOptionsSelection(nxColorsFor(AccentPreset.WHITE)).bar)
    }

    @Test fun theBarTakesTheSecondaryAccentAndItsText() {
        val moss = nxColorsFor(AccentPreset.SKY, SecondaryAccent.Preset(SecondaryPreset.MOSS))
        val s = nxOptionsSelection(moss)
        assertEquals(Color(0xFF365314) to light, s.bar to s.barText)
        val red = nxOptionsSelection(nxColorsFor(AccentPreset.SKY, SecondaryAccent.Preset(SecondaryPreset.RED)))
        assertEquals(Color(0xFF660000) to Color(0xFFDCDCDC), red.bar to red.barText)
        val auto = nxOptionsSelection(nxColorsFor(AccentPreset.SKY, SecondaryAccent.Auto))
        assertEquals(Color(0xFF195570) to Color(0xFFDCDCDC), auto.bar to auto.barText)
        val autoOnWhite = nxOptionsSelection(nxColorsFor(AccentPreset.WHITE, SecondaryAccent.Auto))
        assertEquals(teal to Color(0xFFDCDCDC), autoOnWhite.bar to autoOnWhite.barText)
    }

    @Test fun theInGameMenuTakesTheAccentAndTheSecondaryAccentLikeSettings() {
        // GameActivity builds the in-game colours from both settings; its options rows and adjust strip resolve through here.
        val red = nxColorsFor(AccentPreset.RED, SecondaryAccent.Preset(SecondaryPreset.MOSS))
        assertEquals(NxOptionsSelection(Color(0xFFF87171), Color.Black, Color(0xFF365314), light), nxOptionsSelection(red))
    }
}
