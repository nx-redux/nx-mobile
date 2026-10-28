package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.InfoLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NxTileGridTest {

    @Test fun degenerateLayoutsNeitherDrawNorReport() {
        fun g(w: Float, h: Float, columns: Int = 3) = GridGeometry(w, h, 12f, 16f, columns)
        assertTrue(gridDrawable(900f, 300f, g(200f, 100f)))
        assertFalse(gridDrawable(0f, 300f, g(200f, 100f)))
        assertFalse(gridDrawable(900f, 0f, g(200f, 100f)))
        assertFalse(gridDrawable(900f, 300f, g(200f, GRID_TILE_MIN_DP - 1)))   // tiles under the minimum
        assertTrue(gridDrawable(900f, 300f, g(200f, GRID_TILE_MIN_DP)))
        assertFalse(gridDrawable(900f, 300f, g(0f, 100f)))
        assertFalse(gridDrawable(900f, 300f, g(200f, 100f, columns = 0)))
    }

    @Test fun infoTextJoinsTheSegmentsWithTheTrophyBeforeTheAchievements() {
        val text = infoAnnotatedString(listOf(InfoLine(InfoKind.TIME, "Today - 1m"), InfoLine(InfoKind.ACHIEVEMENTS, "3 of 20")))
        assertEquals("Today - 1m · [trophy]3 of 20", text.text)
        val trophy = text.getStringAnnotations(0, text.length).single()
        assertEquals("Today - 1m · ".length, trophy.start)                 // the trophy leads the achievement segment
        assertEquals(TROPHY, trophy.item)
        assertEquals("", infoAnnotatedString(emptyList()).text)
    }
}
