package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.settings.OptionTextSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The options row: a list row's pitch and label, its value a little smaller, NX Redux's value arrows and padding. */
class OptionsRowTest {
    // An options row's label is 0.8 of its list's label, the value 0.8 of that; Text size scales the list (its pitch) instead.
    @Test fun labelIsFourFifthsOfTheListLabelAndTheValueOfThat() {
        assertEquals(16f, nxOptionsLabelSize(20.sp).value, 1e-4f)
        assertEquals(12.8f, nxOptionsValueSize(20.sp).value, 1e-4f)
    }

    // Text size scales a list's pitch: Large as is, Small 0.6 / 0.8 of it (and the text follows the row).
    @Test fun textSizeScalesTheListPitch() {
        assertEquals(32f, nxOptionsRowHeight(32.dp).value, 1e-4f)
        assertEquals(24f, nxOptionsRowHeight(32.dp, OptionTextSize.SMALL.ofListLabel).value, 1e-4f)
        // Whole rows stretch to fill the height, so Small's text is smaller, though not exactly 0.75 of Large's.
        assertTrue(nxListLabelSize(NxListPitch * 0.75f).value < nxListLabelSize().value)
    }

    // The page title and the hint bar scale with Text size too: 1 at Large, 0.75 at Small.
    @Test fun fixedSizesScaleWithTheTextSize() {
        assertEquals(1f, nxTextScale(OptionTextSize.LARGE.ofListLabel), 1e-4f)
        assertEquals(0.75f, nxTextScale(OptionTextSize.SMALL.ofListLabel), 1e-4f)
    }

    @Test fun innerPaddingIsTenDp() {
        assertEquals(10f, NxOptionsInnerPad.value)
    }

    @Test fun arrowsOnlyOnASelectedCycleableRow() {
        assertEquals("< On >", nxOptionsValueText("On", selected = true, cycleable = true))
        assertEquals("On", nxOptionsValueText("On", selected = false, cycleable = true))
        assertEquals("12 games", nxOptionsValueText("12 games", selected = true, cycleable = false))
        assertEquals("12 games", nxOptionsValueText("12 games", selected = false, cycleable = false))
    }

    @Test fun inGameRowsMapToOptionRows() {
        assertEquals(NxRow("Scaling", secondary = "Aspect", cycleable = true), menuOptionRow(MenuRow.Option("Scaling", "scaling", "Aspect")))
        assertEquals(NxRow("Shaders", secondary = ">"), menuOptionRow(MenuRow.Submenu("Shaders", PageKind.Shaders)))
        // NX Redux (ma_menu.c): a sub-page's action row shows a ">" (no arrows) and takes the two-layer bar.
        assertEquals(NxRow("Save for game", secondary = ">"), menuOptionRow(MenuRow.Action("Save for game", MenuAction.SAVE_FOR_GAME)))
    }

    @Test fun pillListSizesAreUnchanged() {
        assertEquals(32.5f, NxListPitch.value)
        assertEquals(20.15f, nxListLabelSize().value, 0.001f)
        assertEquals(false, NxRow("Zelda").cycleable)
    }
}
