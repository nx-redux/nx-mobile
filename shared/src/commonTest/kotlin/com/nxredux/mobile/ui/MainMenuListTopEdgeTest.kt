package com.nxredux.mobile.ui

import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT
import com.nxredux.mobile.ui.nx.menuTabUnderlineBottom
import com.nxredux.mobile.ui.nx.NxHintInkTop
import com.nxredux.mobile.ui.nx.NxListEdgeFit
import com.nxredux.mobile.ui.nx.nxBandArrowInkTop
import com.nxredux.mobile.ui.nx.nxInfoBandGeometry
import com.nxredux.mobile.ui.nx.nxListBalancedGap
import com.nxredux.mobile.ui.nx.nxListFit
import com.nxredux.mobile.ui.nx.nxListFitUnder
import com.nxredux.mobile.ui.nx.nxListTopGapUnder
import com.nxredux.mobile.ui.nx.nxPillGap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The main menu's List under its shown tab row (landscape, and portrait): the rows the fit with the first
 * pill one pill gap under the underline gives, shifted so the underline-to-first-pill gap equals the last pill's gap to
 * the band's arrows.
 */
class MainMenuListTopEdgeTest {
    private val density = 2.625f   // the Fold's cover screen

    @Test fun theShownTabRowHasAnEdgeInEitherOrientation() {
        assertNull(mainMenuListTopEdge(tabRow = false))
        // Either orientation, any font scale (the tab words are pinned): the underline 9.22 dp above the list.
        val edge = mainMenuListTopEdge(tabRow = true)!!.value
        assertEquals(MENU_TAB_ROW_HEIGHT.value - menuTabUnderlineBottom(), edge, 1e-4f)
        assertEquals(9.22f, edge, 0.01f)
    }

    @Test fun theFirstPillSitsOnePillGapUnderTheEdge() {
        val edge = 8.95f
        val row = 33.9f
        val gap = nxListTopGapUnder(edge, row, 0.9f)
        // Edge, then the top gap, then the pill's half-gap inset in its row: one whole pill gap.
        assertEquals(nxPillGap(row, 0.9f), edge + gap + nxPillGap(row, 0.9f) / 2, 1e-4f)
    }

    @Test fun thePillGapMatchesTheFold() {
        // Measured on the Fold's cover screen at font scale 0.9: 89 px rows, 79 px pills, so 10 px (3.8 dp) apart, when
        // the label was the row's own (now it is the target pitch's, so that row's pitch is passed as the target).
        assertTrue(abs(nxPillGap(89 / density, 0.9f, (89 / density).dp) * density - 10f) <= 1.5f)
    }

    /** The visible gaps (px) a fit gives: underline to the first pill's top, the last pill's bottom to the arrows' ink. */
    private fun gaps(fit: NxListEdgeFit, height: Int, reserve: Int, density: Float, fontScale: Float, edge: Float, band: Float): Pair<Float, Float> {
        val rowPx = (fit.row.value * density).roundToInt()
        val halfGap = nxPillGap(fit.row.value, fontScale) * density / 2
        val top = edge * density + fit.gapPx + halfGap
        val ink = height - reserve + nxBandArrowInkTop(band) * density
        val bottom = ink - (fit.gapPx + fit.rows * rowPx) + halfGap
        return top to bottom
    }

    /** The fit before the balance: the first pill one pill gap under the edge (its gap, count and pitch). */
    private fun pillGapFit(height: Int, reserve: Int, density: Float, fontScale: Float, edge: Float): Pair<Int, Pair<Int, androidx.compose.ui.unit.Dp>> {
        var gap = 0
        var fit = nxListFit(height - reserve, density)
        repeat(2) {
            gap = (nxListTopGapUnder(edge, fit.second.value, fontScale) * density).roundToInt()
            fit = nxListFit(height - gap - reserve, density)
        }
        return gap to fit
    }

    @Test fun theArrowsInkSitsHalfTheBandLessHalfAnArrowDown() {
        // A 29 dp band: the 7 dp tall pair centred, its ink 11 dp under the band's top.
        assertEquals(11f, nxBandArrowInkTop(29f), 1e-4f)
    }

    @Test fun theBalancedGapEqualsTopAndBottom() {
        // 10 px edge, ink 1000 px down, 7 rows of 120: (1000 - 840 - 10) / 2 = 75; edge + 75 = 85 = 1000 - (75 + 840).
        assertEquals(75, nxListBalancedGap(10f, 1000f, 7, 120))
    }

    @Test fun theFitUnderTheEdgeBalancesTheGapsKeepingTheRows() {
        for (density in listOf(2f, 2.625f, 3f, 3.5f)) for (fontScale in listOf(0.85f, 0.9f, 1f, 1.3f))
        for (heightDp in listOf(300, 340, 420, 640, 760)) {
            val height = (heightDp * density).roundToInt()
            val geometry = nxInfoBandGeometry(21f * fontScale / 0.9f, NxHintInkTop.value)
            val band = geometry.height
            val reserve = (geometry.reserve * density).roundToInt()
            val edge = mainMenuListTopEdge(tabRow = true)!!.value
            val fit = nxListFitUnder(height, reserve, density, fontScale, edge, band)
            val what = "density $density font $fontScale height $heightDp dp"
            // The rows keep the pill-gap fit's count and pitch.
            val (_, plain) = pillGapFit(height, reserve, density, fontScale, edge)
            assertEquals(plain.first, fit.rows, what)
            assertEquals(plain.second, fit.row, what)
            // The rows' slot is exactly the rows, so they rest whole.
            val rowPx = (fit.row.value * density).roundToInt()
            assertEquals(height, fit.gapPx + fit.rows * rowPx + fit.bottomPx, what)
            // The two visible gaps match to the rounding of the gap (a px), and they are real gaps.
            val (top, bottom) = gaps(fit, height, reserve, density, fontScale, edge, band)
            assertTrue(abs(top - bottom) <= 1f, "$what: top $top px, bottom $bottom px")
            assertTrue(top > 0f, "$what: top $top px")
        }
    }

    @Test fun theFoldCoverMovesTheRowsDownAboutHalfTheDifference() {
        // The cover's measures before (portrait, 0.9): 10 px under the underline, 33 px to the arrows. Here a
        // like slot: the pill-gap fit gives about one pill gap at the top, the balance about the mean of the two.
        val fontScale = 0.9f
        val geometry = nxInfoBandGeometry(21f, NxHintInkTop.value)
        val band = geometry.height
        val reserve = (geometry.reserve * density).roundToInt()
        val edge = mainMenuListTopEdge(tabRow = true)!!.value
        val height = 720   // seven rows and their slack
        val fit = nxListFitUnder(height, reserve, density, fontScale, edge, band)
        val (before, _) = pillGapFit(height, reserve, density, fontScale, edge)
        val beforeTop = edge * density + before + nxPillGap(fit.row.value, fontScale) * density / 2
        val (top, bottom) = gaps(fit, height, reserve, density, fontScale, edge, band)
        assertTrue(abs(beforeTop - nxPillGap(fit.row.value, fontScale) * density) <= 1f)
        assertTrue(top > beforeTop, "top $top px, before $beforeTop px")
        assertTrue(abs(top - bottom) <= 1f, "top $top px, bottom $bottom px")
    }
}
