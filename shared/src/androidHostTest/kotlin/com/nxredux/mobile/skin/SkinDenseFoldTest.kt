package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A dense inner screen (the Z Fold 8 Ultra's 2256x2504 at 480 dpi) keeps the fold board, drawn a little smaller. */
class SkinDenseFoldTest {
    private val pack = skinPack()
    private val foldBoard = { p: SkinPlacement -> p.spec.tree.parts.any { it.isHinge } }

    @Test
    fun theUltrasInnerScreenGetsTheFoldBoard() {
        for (v in listOf(SkinVariant.DS, SkinVariant.GBA, SkinVariant.SINGLE)) for (dpi in listOf(420, 480, 520)) {
            val ds = v == SkinVariant.DS
            val p = assertNotNull(SkinChoice.place(pack, 2256, 2504, dpi, tablet = true, ds = ds, picture = if (ds) null else 240f to 160f, foldable = true, variant = v), "$v $dpi")
            assertTrue(foldBoard(p), "$v $dpi: ${p.spec.id}")
        }
    }

    @Test
    fun theUltrasMenuIsTwoScreens() {
        val p = assertNotNull(SkinChoice.placeMenu(pack, 2256, 2504, 480, foldable = true, tablet = true))
        assertTrue(foldBoard(p), p.spec.id)
    }

    @Test
    fun aFoldBoardThatFitsIsNotShrunk() {
        val at = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, foldable = true))
        assertEquals("flex_ds_fold", at.spec.id)
        assertEquals(1f, at.k)
    }

    @Test
    fun aPhoneKeepsThePhoneBoard() {
        val p = assertNotNull(SkinChoice.place(pack, 1080, 2400, 420, tablet = false, ds = true, foldable = false))
        assertTrue(!foldBoard(p), p.spec.id)
    }

    // The Ultra sideways: 2504 x 2256, its hinge across the window's middle.
    private fun sideways(v: SkinVariant, dpi: Int = 480, hinge: Float? = null) = SkinChoice.place(
        pack, 2504, 2256, dpi, tablet = true, ds = v == SkinVariant.DS, picture = if (v == SkinVariant.DS || v == SkinVariant.N3DS) null else 240f to 160f,
        hingeY = hinge, foldable = true, variant = v,
    )

    @Test
    fun aTwoScreenGameSidewaysOnTheUltraGetsTheFoldBoard() {
        for (v in listOf(SkinVariant.DS, SkinVariant.N3DS)) for (dpi in listOf(420, 480)) {
            val flat = assertNotNull(sideways(v, dpi), "$v $dpi")
            assertTrue(foldBoard(flat), "$v $dpi flat: ${flat.spec.id}")
            val half = assertNotNull(sideways(v, dpi, hinge = 1128f), "$v $dpi")
            assertTrue(foldBoard(half), "$v $dpi half-folded: ${half.spec.id}")
        }
    }

    @Test
    fun aOneScreenGameSidewaysKeepsTheLandscapeShellUntilHalfFolded() {
        for (v in listOf(SkinVariant.GBA, SkinVariant.SINGLE)) {
            val flat = assertNotNull(sideways(v), "$v")
            assertEquals(SkinFamily.GRIP, flat.spec.family, "$v flat: ${flat.spec.id}")
            val half = assertNotNull(sideways(v, hinge = 1128f), "$v")
            assertTrue(foldBoard(half), "$v half-folded: ${half.spec.id}")
        }
    }

    @Test
    fun theMenuSidewaysIsTwoScreensOnlyHalfFolded() {
        val flat = assertNotNull(SkinChoice.placeMenu(pack, 2504, 2256, 480, foldable = true, tablet = true))
        assertEquals(SkinFamily.GRIP, flat.spec.family, flat.spec.id)
        val half = assertNotNull(SkinChoice.placeMenu(pack, 2504, 2256, 480, foldable = true, tablet = true, hingeY = 1128f))
        assertTrue(foldBoard(half), half.spec.id)
        // the camera cutout's strip below the menu (the Ultra turned one way): 2120 px tall
        val cut = assertNotNull(SkinChoice.placeMenu(pack, 2504, 2120, 480, foldable = true, tablet = true, hingeY = 1128f))
        assertTrue(foldBoard(cut), cut.spec.id)
    }

    @Test
    fun aWideWindowSidewaysKeepsTheLandscapeShell() {
        // the wide Fold 8's inner screen sideways (its hinge runs down it there) and a tablet
        for ((w, h) in listOf(2448 to 1848, 2304 to 1440)) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, 420, tablet = true, ds = true, foldable = w == 2448, variant = SkinVariant.DS), "${w}x$h")
            assertEquals(SkinFamily.GRIP, p.spec.family, "${w}x$h: ${p.spec.id}")
        }
    }
}
