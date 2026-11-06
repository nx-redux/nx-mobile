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
        assertNull(SkinChoice.variantFor("", false))
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
        assertEquals(SkinVariant.N3DS_REGULAR, SkinChoice.variantFor("3DS", ds = false))   // the regular 3DS unless New 3DS Controls
        assertEquals(SkinVariant.N3DS, SkinChoice.variantFor("3DS", ds = false, n3dsNew = true))
    }

    @Test
    fun n64GetsItsOwnLayout() {
        assertEquals(SkinVariant.N64, SkinChoice.variantFor("N64", ds = false))
    }

    @Test
    fun gameCubeGetsItsOwnLayout() {   // a GC disc run under the Wii tag too
        assertEquals(SkinVariant.GC, SkinChoice.variantFor("GC", ds = false))
        assertEquals(SkinVariant.GC, SkinChoice.variantFor("WII", ds = false, gameCube = true))
        assertNull(SkinChoice.variantFor("WII", ds = false))
    }

    @Test
    fun wiiPadsGetTheirOwnLayouts() {   // none until the pad resolves
        assertEquals(SkinVariant.WII_CLASSIC, SkinChoice.variantFor("WII", ds = false, wiiPad = com.nxredux.mobile.runner.WiiPad.CLASSIC))
        assertEquals(SkinVariant.WII_NUNCHUK, SkinChoice.variantFor("WII", ds = false, wiiPad = com.nxredux.mobile.runner.WiiPad.NUNCHUK))
        assertEquals(SkinVariant.WII_SIDEWAYS, SkinChoice.variantFor("WII", ds = false, wiiPad = com.nxredux.mobile.runner.WiiPad.SIDEWAYS))
        assertEquals(SkinVariant.WII_UPRIGHT, SkinChoice.variantFor("WII", ds = false, wiiPad = com.nxredux.mobile.runner.WiiPad.UPRIGHT))
        assertNull(SkinChoice.variantFor("WII", ds = false, wiiPad = null))
        assertEquals(SkinVariant.GC, SkinChoice.variantFor("WII", ds = false, gameCube = true, wiiPad = com.nxredux.mobile.runner.WiiPad.CLASSIC))
    }

    @Test
    fun playStationGetsItsOwnLayout() {   // both PS1 cores, the PS2 (the same skin) and the PSP
        assertEquals(SkinVariant.PS1, SkinChoice.variantFor("PS", ds = false))
        assertEquals(SkinVariant.PS1, SkinChoice.variantFor("PSX", ds = false))
        assertEquals(SkinVariant.PSP, SkinChoice.variantFor("PSP", ds = false))
        assertEquals(SkinVariant.PS1, SkinChoice.variantFor("PS2", ds = false))   // the PS2: the PlayStation skin (user)
        assertEquals(SkinVariant.P8, SkinChoice.variantFor("P8", ds = false))
    }
}
