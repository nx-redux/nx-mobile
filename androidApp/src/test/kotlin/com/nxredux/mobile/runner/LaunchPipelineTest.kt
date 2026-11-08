package com.nxredux.mobile.runner

import com.swordfish.libretrodroid.Variable
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchPipelineTest {
    @Test fun optionExtrasSplitAtTheFirstEquals() =
        assertEquals(mapOf("a" to "1", "b" to "x=y", "c" to ""), optionExtras(arrayOf("a=1", "b=x=y", "c=")))

    @Test fun optionExtrasDropEntriesWithoutAKey() =
        assertEquals(mapOf("a" to "1"), optionExtras(arrayOf("=v", "novalue", "a=1")))

    @Test fun aLaterOptionExtraWins() = assertEquals(mapOf("a" to "2"), optionExtras(arrayOf("a=1", "a=2")))

    @Test fun forcedValuesReplaceInPlaceAndMissingOnesFollow() {
        val options = listOf(Variable("a", "1"), Variable("b", "2"), Variable("c", "3"))
        val merged = withForced(options, linkedMapOf("b" to "B", "z" to "Z", "y" to "Y"))
        assertEquals(listOf("a" to "1", "b" to "B", "c" to "3", "z" to "Z", "y" to "Y"), merged.map { it.key to it.value })
    }

    @Test fun withoutForcedValuesTheOptionsStayAsTheyAre() {
        val options = listOf(Variable("a", "1", description = "kept"))
        assertEquals(options, withForced(options, emptyMap()))
    }

    @Test fun aSplitReplacesItsKeysAndAddsThemLast() {
        val merged = listOf(Variable("layout", "0"), Variable("swap", "off"), Variable("other", "x"))
            .withN3dsSplit(mapOf("swap" to "on", "layout" to "4"))
        assertEquals(listOf("other" to "x", "swap" to "on", "layout" to "4"), merged.map { it.key to it.value })
    }

    @Test fun noSplitLeavesTheOptions() {
        val options = listOf(Variable("layout", "0"))
        assertEquals(options, options.withN3dsSplit(null))
    }
}
