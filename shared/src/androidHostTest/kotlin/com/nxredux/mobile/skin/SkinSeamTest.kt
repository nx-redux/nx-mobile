package com.nxredux.mobile.skin

import com.nxredux.mobile.layout.DeviceTraits
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The fold board as one slab where no hinge runs across the window ([SkinSeam], [DeviceTraits.hingeAcross]). */
class SkinSeamTest {
    private val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))
    private val stretchMax = 64f

    @AfterTest
    fun reset() {
        DeviceTraits.canHalfFold = false
    }

    @Test
    fun theHingeShowsOnlyWhereARealOneRunsAcross() {
        DeviceTraits.canHalfFold = false
        assertFalse(DeviceTraits.hingeAcross(1848, 2448, acrossPortrait = true), "a tablet")
        DeviceTraits.canHalfFold = true
        assertTrue(DeviceTraits.hingeAcross(1848, 2448, acrossPortrait = true), "the wide Fold upright")
        assertFalse(DeviceTraits.hingeAcross(2448, 1848, acrossPortrait = true), "the wide Fold sideways")
        assertFalse(DeviceTraits.hingeAcross(2256, 2504, acrossPortrait = false), "the Ultra upright")
        assertTrue(DeviceTraits.hingeAcross(2504, 2256, acrossPortrait = false), "the Ultra sideways")
        assertTrue(DeviceTraits.hingeAcross(2256, 2504, acrossPortrait = null), "not known yet: as before")
    }

    @Test
    fun theSlabCoversTheHingeRowAndMeetsTheBody() {
        for ((w, h, dpi) in listOf(Triple(2256, 2504, 480), Triple(1848, 2448, 420), Triple(2304, 2880, 420))) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = true, ds = true, foldable = true), "${w}x$h")
            val draws = SkinSeam.draws(p, stretchMax)
            val hinge = p.cells.filter { c -> draws.any { kotlin.math.abs(it.cell.src.x - c.src.x) < 0.5f } }
            assertTrue(draws.isNotEmpty(), "${w}x$h ${p.spec.id}")
            val rows = p.spec.tree.parts
            val at = rows.indexOfFirst { it.isHinge }
            val hingeTop = rows.take(at).sumOf { it.len }.toFloat()
            val hingeCells = p.cells.filter { kotlin.math.abs(it.src.y - hingeTop) < 0.5f }
            // each hinge column: a mirror reaching over the whole hinge row, and a fade above it
            assertEquals(hingeCells.size * 2, draws.size, "${w}x$h")
            for (c in hingeCells) {
                val mirror = draws.single { it.mirror && kotlin.math.abs(it.cell.dst.x - c.dst.x) < 0.5f }.cell.dst
                assertTrue(mirror.y < c.dst.y && mirror.y + mirror.height > c.dst.y + c.dst.height, "${w}x$h mirror $mirror over $c")
                val fade = draws.single { it.fade && kotlin.math.abs(it.cell.dst.x - c.dst.x) < 0.5f }.cell.dst
                assertEquals(mirror.y, fade.y, 0.5f)
                assertTrue(fade.y + fade.height <= c.dst.y, "${w}x$h the fade ends above the hinge")
            }
            assertTrue(hinge.isNotEmpty())
        }
    }

    @Test
    fun aBoardWithoutAHingeHasNoSlab() {
        val p = assertNotNull(SkinChoice.place(pack, 1080, 2400, 420, tablet = false, ds = true))
        assertEquals(emptyList(), SkinSeam.draws(p, stretchMax))
    }

    @Test
    fun withoutTheHingeTheTopScreenReachesTheMiddle() {
        val with = assertNotNull(SkinChoice.place(pack, 2256, 2504, 480, tablet = true, ds = true, foldable = true))
        val without = assertNotNull(SkinChoice.place(pack, 2256, 2504, 480, tablet = true, ds = true, foldable = true, hingeShown = false))
        val top = assertNotNull(with.glass(GlassScreen.TOP)).rect
        val slab = assertNotNull(without.glass(GlassScreen.TOP)).rect
        assertTrue(slab.height > top.height, "taller: $slab over $top")
        // the frame's room ends at the middle: its glass a frame border short of it at most
        assertTrue(slab.y + slab.height <= 2504 / 2f && slab.y + slab.height > 2504 / 2f - 120f, "$slab")
        val menu = assertNotNull(SkinChoice.placeMenu(pack, 2256, 2504, 480, foldable = true, tablet = true, hingeShown = false))
        val menuTop = assertNotNull(menu.glasses.firstOrNull { it.id == "menutop" }).rect
        assertTrue(menuTop.y + menuTop.height > 2504 / 2f - 120f, "$menuTop")
    }
}
