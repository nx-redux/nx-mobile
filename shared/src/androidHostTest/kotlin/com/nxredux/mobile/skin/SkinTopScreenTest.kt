package com.nxredux.mobile.skin

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Portrait on a phone (no stacked layout: the large one): the top screen's frame is cut to the top picture's shape
 * (the 3DS's 400 x 240, the DS's 256 x 192), so its black border is as thin above and below as at the sides, and the
 * bottom screen moves up by what that saves (user).
 */
class SkinTopScreenTest {
    private val carbon = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))

    private fun cover(variant: SkinVariant) =
        assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true, variant = variant), "$variant")

    private fun aspect(r: com.nxredux.mobile.options.PxRect) = r.width / r.height

    @Test
    fun the_3ds_top_screen_takes_its_picture_shape() {
        val p = cover(SkinVariant.N3DS_REGULAR)
        val top = assertNotNull(p.glass(GlassScreen.TOP)).rect
        assertTrue(abs(aspect(top) - 400f / 240f) < 0.01f, "3DS top $top")
    }

    // the DS's 4:3 across the cover's width would be taller than the room: height limits it, so the frame stays the
    // full-width one (the picture between thin bars at its sides) and nothing moves
    @Test
    fun a_height_limited_ds_top_screen_keeps_the_full_frame() {
        val p = cover(SkinVariant.DS)
        val top = assertNotNull(p.glass(GlassScreen.TOP)).rect
        assertTrue(top.width * 192f / 256f > top.height, "DS top $top is height-limited")
        assertTrue(top.width > 1248f - 200f, "DS top $top full width")
    }

    @Test
    fun the_bottom_screen_moves_up_under_the_shorter_frame() {
        val p = cover(SkinVariant.N3DS_REGULAR)
        val top = assertNotNull(p.glass(GlassScreen.TOP)).rect
        val touch = assertNotNull(p.glass(GlassScreen.TOUCH)).rect
        // under the top frame, and no further below it than the render's own gap (frame border + a few px)
        val gap = touch.y - (top.y + top.height)
        assertTrue(gap > 0f && gap < 120f, "touch $touch top $top gap $gap")
        // the buttons stay where they were
        val dpad = p.controls.first { it.spec.id == "DPAD" }
        assertTrue(dpad.y > touch.y + touch.height, "DPAD ${dpad.y} under the touch screen $touch")
    }

    // with the room the shorter top frame frees (user, layout b): the 2x bottom screen up under it, SELECT START just under
    // that, the stick and the face up 18 (412 over the bottom, picture px), the C-stick nub between them; the bottom row stays
    @Test
    fun the_pad_rises_with_the_room_the_top_frame_frees() {
        val p = cover(SkinVariant.N3DS)
        val c = p.controls.associateBy { it.spec.id }
        val k = p.k
        val bottom = 1972f
        for (id in listOf("A", "STICK")) assertEquals(412f, (bottom - c.getValue(id).y) / k, 1f, id)
        assertEquals(140f, (bottom - c.getValue("MENU").y) / k, 1f, "MENU stays")
        val touch = assertNotNull(p.glass(GlassScreen.TOUCH)).rect
        assertEquals(640f, touch.width / k, 1f, "the 2x bottom screen")
        assertTrue(c.getValue("SELECT").y > touch.y + touch.height, "SELECT under the bottom screen")
        // a DS on the cover (height-limited: no room freed) keeps its pad where it was
        val ds = cover(SkinVariant.DS).controls.associateBy { it.spec.id }
        assertEquals(394f, (bottom - ds.getValue("A").y) / k, 1f, "DS A unchanged")
    }
}
