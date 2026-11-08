package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A word list's box holds its rows as the list lays them out, each a whole number of px ([wordListHeight]): never a
 * fraction short, which would make it scroll by a px and fade its last row (the Fold cover: 6 rows of 87 px, a 521 px box).
 */
class WordListHeightTest {
    @Test fun theBoxIsTheRowsInWholePx() {
        val d = Density(2.625f)
        val h = wordListHeight(33.079365.dp, 6, d)
        assertEquals(522, with(d) { h.roundToPx() })
        assertEquals(522f, with(d) { h.toPx() }, 0.01f)
    }

    @Test fun noRowsIsOneRow() {
        val d = Density(2f)
        assertEquals(40f, with(d) { wordListHeight(20.dp, 0, d).toPx() }, 0.01f)
    }
}
