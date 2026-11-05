package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which skin layout set a console's games get ([SkinChoice.variantFor]). */
class SkinVariantForTest {
    @Test
    fun eachConsoleGetsItsButtonSet() {
        assertEquals(SkinVariant.DS, SkinChoice.variantFor("NDS", ds = true))
        for (t in listOf("GB", "GBC", "SGB", "FC")) assertEquals(SkinVariant.GB, SkinChoice.variantFor(t, false), t)
        for (t in listOf("GBA", "MGBA")) assertEquals(SkinVariant.GBA, SkinChoice.variantFor(t, false), t)
        for (t in listOf("SFC", "MD", "PCE")) assertEquals(SkinVariant.SINGLE, SkinChoice.variantFor(t, false), t)
        for (t in listOf("PSP", "")) assertNull(SkinChoice.variantFor(t, false), t)
    }

    @Test
    fun fdsGetsTheFdsLayout() {
        assertEquals(SkinVariant.FDS, SkinChoice.variantFor("FDS", ds = false))
        assertEquals(SkinVariant.GB, SkinChoice.variantFor("FC", ds = false))   // the NES: the Game Boy set, no DISK (fceumm's X / Y turbo on by default)
    }

    @Test
    fun arcadeGetsTheArcadeLayout() {
        assertEquals(SkinVariant.ARCADE, SkinChoice.variantFor("FBN", ds = false))
    }

    @Test
    fun dreamcastDiscsGetTheirsAndArcadeSetsTheArcadeOne() {
        assertEquals(SkinVariant.DC, SkinChoice.variantFor("DC", ds = false))
        assertEquals(SkinVariant.ARCADE, SkinChoice.variantFor("DC", ds = false, arcadeSet = true))
    }

    @Test
    fun n3dsGetsItsOwnLayout() {
        assertEquals(SkinVariant.N3DS, SkinChoice.variantFor("3DS", ds = false))
    }

    @Test
    fun n64GetsItsOwnLayout() {
        assertEquals(SkinVariant.N64, SkinChoice.variantFor("N64", ds = false))
    }
}
