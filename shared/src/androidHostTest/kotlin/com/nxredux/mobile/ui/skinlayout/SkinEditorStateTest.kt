package com.nxredux.mobile.ui.skinlayout

import androidx.compose.ui.geometry.Offset
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.skin.SkinChoice
import com.nxredux.mobile.skin.SkinPackManifest
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.SkinTweaks
import com.nxredux.mobile.skin.SkinVariant
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The skin layout editor's state on the built-in pack's placed layouts: picking, dragging, resizing, resets. */
class SkinEditorStateTest {
    private val carbon = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))

    /** The portrait menu layout, 1080 x 2520 (k 1). */
    private fun menu(): SkinPlacement = assertNotNull(SkinChoice.placeMenu(carbon, 1080, 2520, 420)).also { assertEquals("stacked_menu", it.spec.id) }

    /** The GBA grip, sideways on the Fold's cover: the face buttons sit right of the screen. */
    private fun grip(): SkinPlacement =
        assertNotNull(SkinChoice.place(carbon, 2420, 1080, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))

    private fun box(p: SkinPlacement, id: String): PxRect = assertNotNull(SkinTweaks.boxes(p)[id]).box

    private fun centre(r: PxRect) = Offset(r.x + r.width / 2f, r.y + r.height / 2f)

    private fun near(a: Float, b: Float, what: String, e: Float = 0.05f) = assertTrue(abs(a - b) < e, "$what: $a vs $b")

    @Test
    fun select_picks_the_group_under_the_finger() {
        val p = menu()
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        assertNull(s.selected)
        for (id in listOf("SELECT+START", "A+B+X+Y", "MENU", "DPAD")) {
            s.select(centre(box(p, id)))
            assertEquals(id, s.selected)
        }
        // the screen holds no group: a touch there selects none
        s.select(centre(p.glasses.first().rect))
        assertNull(s.selected)
        // a moved group is picked where it is drawn now
        s.select(centre(box(p, "MENU")))
        s.drag(Offset(60f, 0f))
        s.select(centre(box(p, "MENU")) + Offset(100f, 0f))
        assertEquals("MENU", s.selected)
    }

    @Test
    fun drag_converts_to_picture_px_and_is_limited() {
        // two window px to the picture px
        val p = menu().copy(k = 2f)
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        s.select(centre(box(p, "MENU")))
        s.drag(Offset(40f, 0f))
        val t = assertNotNull(s.tweaks["MENU"])
        near(t.dx, 20f, "dx"); near(t.dy, 0f, "dy")
        near(box(s.placed(), "MENU").x, box(p, "MENU").x + 40f, "drawn")
        // MENU may move half its larger side (100 picture px): a long drag stops there
        s.drag(Offset(100000f, 0f))
        near(assertNotNull(s.tweaks["MENU"]).dx, 50f, "capped dx")
        near(box(s.placed(), "MENU").x, box(p, "MENU").x + 100f, "drawn at the cap")
        // and comes back at once (what is stored is where it stands)
        s.drag(Offset(-20f, 0f))
        near(assertNotNull(s.tweaks["MENU"]).dx, 40f, "back")
    }

    @Test
    fun a_drag_into_the_no_go_zone_stops_at_its_edge() {
        val p = grip()
        val zone = SkinTweaks.noGo(p).first()
        val s = SkinEditorState(p, emptyMap(), 2420f, 1080f)
        val id = "A+B+X+Y"
        val start = box(p, id)
        assertTrue(start.x > zone.x + zone.width, "$start right of $zone")
        s.select(centre(start))
        s.drag(Offset(-100000f, 0f))
        val at = box(s.placed(), id)
        near(at.x, zone.x + zone.width, "stopped at the zone's edge", e = 0.5f)
        near(assertNotNull(s.tweaks[id]).dx, (at.x - start.x) / p.k, "stored where it stands")
        // a drag back right moves it at once
        s.drag(Offset(10f, 0f))
        near(box(s.placed(), id).x, at.x + 10f, "back right", e = 0.5f)
    }

    @Test
    fun a_drag_moves_into_the_room_another_group_left() {
        // the face buttons dragged right to the window's edge leave room: the d-pad (before them in the layout) dragged
        // right goes into it, stops against them, reads back as far as it got and comes back at once
        val p = menu()
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        val face0 = box(p, "A+B+X+Y"); val dpad0 = box(p, "DPAD")
        s.select(centre(face0)); s.drag(Offset(1000f, 0f))
        val room = 1080f - (face0.x + face0.width)
        near(assertNotNull(s.tweaks["A+B+X+Y"]).dx, room, "face at the edge")
        s.select(centre(dpad0)); s.drag(Offset(1000f, 0f))
        val reached = face0.x + room - (dpad0.x + dpad0.width)
        assertTrue(reached > face0.x - (dpad0.x + dpad0.width) + 1f, "past the face buttons' home")
        near(assertNotNull(s.tweaks["DPAD"]).dx, reached, "d-pad read back")
        near(box(s.placed(), "DPAD").x + dpad0.width, box(s.placed(), "A+B+X+Y").x, "against the face buttons")
        near(assertNotNull(s.tweaks["A+B+X+Y"]).dx, room, "face buttons stay")
        s.drag(Offset(-20f, 0f))
        near(assertNotNull(s.tweaks["DPAD"]).dx, reached - 20f, "back")
    }

    @Test
    fun resize_scales_the_selected_group() {
        val p = menu()
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        s.resize(1.2f)   // nothing selected: nothing changes
        assertTrue(s.tweaks.isEmpty())
        s.select(centre(box(p, "MENU")))
        s.resize(1.2f)
        near(assertNotNull(s.tweaks["MENU"]).scale, 1.2f, "scale", e = 0.001f)
        s.resize(10f)
        near(assertNotNull(s.tweaks["MENU"]).scale, SkinTweaks.MAX_SCALE, "capped", e = 0.001f)
    }

    @Test
    fun reset_group_and_reset_all() {
        val p = menu()
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        s.select(centre(box(p, "MENU"))); s.drag(Offset(30f, 0f))
        s.select(centre(box(p, "SELECT+START"))); s.drag(Offset(0f, 20f))
        assertEquals(setOf("MENU", "SELECT+START"), s.tweaks.keys)
        s.resetGroup()   // the selected one
        assertEquals(setOf("MENU"), s.tweaks.keys)
        assertEquals(box(p, "SELECT+START"), box(s.placed(), "SELECT+START"))
        s.resetAll()
        assertTrue(s.tweaks.isEmpty())
        assertEquals(p, s.placed())
    }

    @Test
    fun dirty_only_after_a_change() {
        val p = menu()
        val initial = mapOf("MENU" to SkinTweak(dx = 30f))
        val s = SkinEditorState(p, initial, 1080f, 2520f)
        assertFalse(s.dirty)
        near(assertNotNull(s.tweaks["MENU"]).dx, 30f, "initial kept")
        s.select(centre(box(p, "DPAD")))
        assertFalse(s.dirty, "selecting is no change")
        s.drag(Offset(0f, -20f))
        assertTrue(s.dirty)
        s.resetGroup()
        assertFalse(s.dirty, "back as it was")
        s.resetAll()
        assertTrue(s.dirty)
        assertTrue(s.requestCancel().not() && s.confirmDiscard, "Cancel asks first when dirty")
        s.keepEditing()
        assertFalse(s.confirmDiscard)
    }
}
