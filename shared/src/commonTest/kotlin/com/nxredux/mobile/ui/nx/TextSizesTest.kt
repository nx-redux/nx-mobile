package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared text roles ([NxTextSizes]), from the list label (16 sp here) or fixed. */
class TextSizesTest {
    private val label = 16.sp

    @Test fun listLabelIsSixtyTwoPercentOfThePitch() {
        assertEquals(16.12f, NxTextSizes.listLabel(26.dp).value, 1e-4f)
        assertEquals(20.15f, NxTextSizes.listLabel(32.5.dp).value, 1e-4f)
        assertEquals(NxTextSizes.listLabel(32.5.dp).value, nxListLabelSize().value)
    }

    // A list's rows stretch to fill its height; its label stays the target pitch's, so a pill grows with the row, not the text.
    @Test fun aStretchedRowKeepsTheTargetPitchsLabel() {
        val label = NxTextSizes.listLabel(NxListPitch).value
        assertEquals(45f * (1f - 2 * NX_PILL_PAD) - label * NX_FONT_LINE, nxPillGap(45f, 1f), 1e-4f)
        assertEquals(60f * (1f - 2 * NX_PILL_PAD) - label * NX_FONT_LINE, nxPillGap(60f, 1f), 1e-4f)
    }

    @Test fun headlineSecondaryAndCaptionStepFromTheLabel() {
        assertEquals(18.4f, NxTextSizes.headline(label).value, 1e-4f)
        assertEquals(12.8f, NxTextSizes.secondary(label).value, 1e-4f)
        assertEquals(10.24f, NxTextSizes.caption(label).value, 1e-4f)
        // A rich row: its title is the secondary size, its second line one step under that, the caption.
        assertEquals(NxTextSizes.caption(label).value, NxTextSizes.secondary(NxTextSizes.secondary(label)).value, 1e-4f)
    }

    @Test fun optionRowsTakeOneStepUnderTheListLabelTheirValueSmaller() {
        assertEquals(12.8f, nxOptionsLabelSize(label).value, 1e-4f)    // one step under the 16 sp list label
        assertEquals(10.24f, nxOptionsValueSize(label).value, 1e-4f)   // 0.8 of that
    }

    @Test fun linesAreRoomy() {
        assertEquals(15.36f, NxTextSizes.line(12.8.sp).value, 1e-4f)
        assertEquals(2.16f, NxTextSizes.RICH_BLOCK_OF_TITLE, 1e-4f)                 // both boxes: (1 + 0.8) x 1.2
    }

    @Test fun fixedSizes() {
        assertEquals(14f, NxTextSizes.pageTitle.value)
        assertEquals(10.8f, NxTextSizes.hint.value, 1e-4f)
        assertEquals(8.37f, NxTextSizes.badge(13.5.dp).value, 1e-4f)
        assertEquals(14f, NxTextSizes.gameInfo.value)
        assertEquals(16f, NxTextSizes.modalText.value)
        assertEquals(12f, NxTextSizes.modalSmall.value)
        assertEquals(14f, NxTextSizes.modalButton.value)
        assertEquals(15f, NxTextSizes.dialogBody.value)
        assertEquals(16f, NxTextSizes.menuMessage.value)
        assertEquals(13f, NxTextSizes.menuInfo.value)
    }

    // The system font size is followed between 0.85 and 1.3, held there past either end (Android goes to 2.0).
    @Test fun theFontScaleIsHeldToItsRange() {
        assertEquals(1f, nxFontScale(1f))
        assertEquals(1.15f, nxFontScale(1.15f))
        assertEquals(1.3f, nxFontScale(2f))
        assertEquals(0.85f, nxFontScale(0.5f))
    }

    @Test fun noticesAndPadLabelsAreFixed() {
        assertEquals(15f, NxTextSizes.notice.value, 1e-4f)
        assertEquals(12f, NxTextSizes.padLabel.value, 1e-4f)
    }
}
