package com.nxredux.mobile.runner

import com.nxredux.mobile.skin.SkinChoice
import com.nxredux.mobile.skin.SkinGroups
import com.nxredux.mobile.skin.SkinPackManifest
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.SkinVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Portrait's picture keeps above the band the bare buttons take: that band comes from the skin's layout, so moving an
 * overlay button up the screen never shrinks the picture.
 */
class OverlayBandTest {
    private val carbon = checkNotNull(SkinPackManifest.parse(File("src/main/assets/skins/nx-carbon/skin.json").readText()))
    private val placed = checkNotNull(SkinChoice.placeClosest(carbon, 1080, 2520, 420, tablet = false, ds = false, picture = 320f to 240f, variant = SkinVariant.GBA))
    private val floor = 32f * 420f / 160f

    private fun groupOf(control: String) = SkinGroups.of(placed.spec).first { control in it.controls }.id

    @Test
    fun the_portrait_band_ignores_overlay_edits() {
        // L moved in the overlay to the window's top, over the picture
        val l = groupOf("L")
        val top0 = placed.controls.filter { it.spec.id == "L" }.minOf { it.y - it.spec.hh }
        val plain = bareLayout(placed, emptyMap(), emptyMap(), 1080, 2520, floor)
        val up = bareLayout(placed, emptyMap(), mapOf(l to SkinTweak(dy = -top0 / placed.k)), 1080, 2520, floor)
        val top = up.placement.controls.filter { it.spec.id == "L" }.minOf { it.y - it.spec.hh }
        assertTrue("L moved to the top: $top", top < 1f)
        assertEquals(plain.band, up.band)
        assertTrue(plain.band > 0)
    }

    @Test
    fun the_portrait_band_follows_skin_edits() {
        // every group up a little: whichever is highest raises the band
        val up = SkinGroups.of(placed.spec).associate { it.id to SkinTweak(dy = -40f) }
        val plain = bareLayout(placed, emptyMap(), emptyMap(), 1080, 2520, floor)
        val skin = bareLayout(placed, up, emptyMap(), 1080, 2520, floor)
        assertNotEquals(plain.band, skin.band)
    }

    @Test
    fun an_owned_overlay_group_keeps_its_place_after_a_skin_edit() {
        // the d-pad arranged in the overlay; a later skin edit moves the face buttons (unedited there) onto its spot
        val dpad = groupOf("DPAD"); val face = groupOf("A")
        val pad0 = placed.controls.single { it.spec.id == "DPAD" }
        val out = bareLayout(placed, mapOf(face to SkinTweak(dx = -100f)), mapOf(dpad to SkinTweak(dx = 150f)), 1080, 2520, floor)
        val pad = out.placement.controls.single { it.spec.id == "DPAD" }
        assertEquals(pad0.x + 150f * placed.k, pad.x, 0.01f)
    }
}
