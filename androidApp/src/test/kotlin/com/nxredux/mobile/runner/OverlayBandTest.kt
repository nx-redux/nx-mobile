package com.nxredux.mobile.runner

import com.nxredux.mobile.skin.ControlShape
import com.nxredux.mobile.skin.ControlSpec
import com.nxredux.mobile.skin.PlacedControl
import com.nxredux.mobile.skin.SkinFamily
import com.nxredux.mobile.skin.SkinLayoutSpec
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinVariant
import com.nxredux.mobile.skin.SliceNode
import com.nxredux.mobile.skin.SlicePart
import com.nxredux.mobile.skin.SliceSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Portrait's picture keeps above the band the bare buttons take: that band comes from the skin's layout, so moving an
 * overlay button up the screen never shrinks the picture.
 */
class OverlayBandTest {
    private val spec = SkinLayoutSpec(
        id = "b", family = SkinFamily.STACKED, variant = SkinVariant.FDS, image = "b.png", width = 1000, height = 2000,
        tree = SliceNode(SliceSplit.ROWS, listOf(SlicePart(2000))), glasses = emptyList(), scales = listOf(emptyList()), controls = emptyList(),
    )

    private fun ctl(id: String, x: Float, y: Float) = PlacedControl(ControlSpec(id, ControlShape.CIRCLE, x, y, r = 50f), x, y)

    private fun placement(aY: Float) = SkinPlacement(spec, 1f, emptyList(), emptyList(), listOf(ctl("A", 700f, aY), ctl("B", 600f, 1700f)))

    @Test
    fun the_portrait_band_ignores_overlay_edits() {
        val skin = placement(aY = 1500f)
        val overlay = placement(aY = 0f)   // the A group dragged to the very top in the overlay
        val band = skinBandHeight(overlayBandSource(skin, overlay)!!, 2000)
        assertEquals(skinBandHeight(skin, 2000), band)
        assertEquals(550, band)   // from A's top (1500 - 50) down
    }

    @Test
    fun no_skin_placement_no_band() {
        assertNull(overlayBandSource(null, placement(aY = 0f)))
    }
}
