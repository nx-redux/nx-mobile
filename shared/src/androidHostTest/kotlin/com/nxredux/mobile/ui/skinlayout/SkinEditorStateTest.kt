package com.nxredux.mobile.ui.skinlayout

import androidx.compose.ui.geometry.Offset
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.skin.SkinChoice
import com.nxredux.mobile.skin.SkinPackManifest
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.SkinTweaks
import com.nxredux.mobile.skin.SkinVariant
import com.nxredux.mobile.skin.TweakRules
import com.nxredux.mobile.settings.AppSettings
import java.lang.reflect.Proxy
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
        // a long drag stops at the window's edge (MENU's box is its button's)
        s.drag(Offset(100000f, 0f))
        val edge = 1080f - (box(p, "MENU").x + box(p, "MENU").width)
        near(assertNotNull(s.tweaks["MENU"]).dx, edge / 2f, "dx at the edge")
        near(box(s.placed(), "MENU").let { it.x + it.width }, 1080f, "drawn at the edge")
        // and comes back at once (what is stored is where it stands)
        s.drag(Offset(-20f, 0f))
        near(assertNotNull(s.tweaks["MENU"]).dx, edge / 2f - 10f, "back")
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
        // the face buttons dragged right to the window's edge (their buttons') leave room: the d-pad (before them in the
        // layout) dragged right goes into it, stops with its touch area against theirs, reads back as far as it got and
        // comes back at once
        val p = menu()
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        val face0 = box(p, "A+B+X+Y"); val dpad0 = box(p, "DPAD")
        val faceButtons = p.controls.filter { it.spec.id in setOf("A", "B", "X", "Y") }
        val pad = p.controls.single { it.spec.id == "DPAD" }
        s.select(centre(face0)); s.drag(Offset(1000f, 0f))
        val room = 1080f - faceButtons.maxOf { it.x + it.spec.r }
        near(assertNotNull(s.tweaks["A+B+X+Y"]).dx, room, "face at the edge")
        s.select(centre(dpad0)); s.drag(Offset(1000f, 0f))
        // the d-pad's touch area reaches 22 px past its hit box; the round face buttons' is their circle
        val gap = faceButtons.minOf { it.x - it.spec.r } - (pad.x + pad.spec.r + 22f * p.k)
        val reached = gap + room
        near(assertNotNull(s.tweaks["DPAD"]).dx, reached, "d-pad read back")
        // its touch area's right edge meets the face buttons' nearest edge
        val now = s.placed().controls
        val padRight = now.single { it.spec.id == "DPAD" }.let { it.x + it.spec.r + 22f * p.k }
        val faceLeft = now.filter { it.spec.id in setOf("A", "B", "X", "Y") }.minOf { it.x - it.spec.r }
        near(padRight, faceLeft, "d-pad against the face buttons", e = 0.5f)
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
        // no cap on the size: it grows until its touch area meets its neighbours'
        s.resize(10f)
        val grown = assertNotNull(s.tweaks["MENU"]).scale
        assertTrue(grown > 1.5f && grown < 12f, "grew to $grown")
        near(box(s.placed(), "MENU").width, box(p, "MENU").width * grown, "drawn at that size", e = 0.5f)
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

    private fun overlayState(p: SkinPlacement, skin: Map<String, SkinTweak>, own: Map<String, SkinTweak>, w: Float = 1080f, h: Float = 2520f) =
        SkinEditorState(p, skin + own, w, h, TweakRules.OVERLAY, 0f, skin, own.keys)

    @Test
    fun overlay_reset_returns_to_the_skin_positions() {
        val p = menu()
        val skin = mapOf("MENU" to SkinTweak(dx = 30f))
        val s = overlayState(p, skin, mapOf("MENU" to SkinTweak(dx = -10f), "DPAD" to SkinTweak(dy = 15f)))
        assertEquals(setOf("MENU", "DPAD"), s.saved().keys)
        s.select(centre(box(s.placed(), "MENU")))
        s.resetGroup()
        near(assertNotNull(s.tweaks["MENU"]).dx, 30f, "back at the skin's")
        near(box(s.placed(), "MENU").x, box(SkinTweaks.apply(p, skin, 1080f, 2520f), "MENU").x, "drawn at the skin's", e = 0.5f)
        assertEquals(setOf("DPAD"), s.saved().keys, "follows the skin again")
        s.resetAll()
        assertTrue(s.saved().isEmpty())
        assertEquals(skin.keys, s.tweaks.keys)
        near(assertNotNull(s.tweaks["MENU"]).dx, 30f, "reset all: the skin's")
        assertTrue(s.dirty)
    }

    @Test
    fun an_overlay_drag_saves_only_that_group() {
        val p = menu()
        val skin = mapOf("MENU" to SkinTweak(dx = 30f))
        val s = overlayState(p, skin, emptyMap())
        assertFalse(s.dirty)
        assertTrue(s.saved().isEmpty())
        s.select(centre(box(s.placed(), "DPAD")))
        s.drag(Offset(0f, -20f))
        assertEquals(setOf("DPAD"), s.saved().keys)
        assertEquals(setOf("DPAD"), s.owned)
        near(assertNotNull(s.saved()["DPAD"]).dy, -20f, "dy")
        assertTrue(s.dirty)
        // an owned group dragged back home stores an identity tweak: it no longer follows the skin
        s.drag(Offset(0f, 20f))
        assertEquals(SkinTweak(), s.saved()["DPAD"]?.let { SkinTweak(if (abs(it.dx) < 0.01f) 0f else it.dx, if (abs(it.dy) < 0.01f) 0f else it.dy, it.scale) })
        assertEquals(setOf("DPAD"), s.owned)
        assertTrue(s.dirty, "owned now, where it was following the skin")
    }

    @Test
    fun overlay_reset_of_a_group_with_no_skin_tweak_goes_home() {
        val p = menu()
        val s = overlayState(p, mapOf("MENU" to SkinTweak(dx = 30f)), mapOf("DPAD" to SkinTweak(dy = 15f)))
        s.select(centre(box(s.placed(), "DPAD")))
        s.resetGroup()
        assertNull(s.tweaks["DPAD"])
        assertTrue(s.owned.isEmpty())
        assertEquals(box(p, "DPAD"), box(s.placed(), "DPAD"))
        near(assertNotNull(s.tweaks["MENU"]).dx, 30f, "the other keeps the skin's")
    }

    @Test
    fun a_drag_past_a_neighbour_lands_where_it_is_free() {
        // MENU dragged up past SELECT START to just above them: it goes there, not stopped under them
        val p = menu()
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        val ssTop = p.controls.filter { it.spec.id == "SELECT" || it.spec.id == "START" }.minOf { it.y - it.spec.r }
        val dy = ssTop - 4f - (box(p, "MENU").y + box(p, "MENU").height)
        s.select(centre(box(p, "MENU"))); s.drag(Offset(0f, dy))
        near(box(s.placed(), "MENU").y, box(p, "MENU").y + dy, "above SELECT START", e = 0.5f)
        // pushed on up, it stops at the screen's middle from there: not back under SELECT START
        s.drag(Offset(0f, -10000f))
        val g = SkinTweaks.screenAreas(p).first()
        near(box(s.placed(), "MENU").y, g.y + 0.9f * g.height, "at the screen's middle", e = 0.5f)
    }

    @Test
    fun a_drag_onto_another_group_stops_and_leaves_it() {
        // the d-pad (earlier in the layout) moved right; the face buttons dragged onto it stop against it
        val p = menu()
        val s = SkinEditorState(p, mapOf("DPAD" to SkinTweak(dx = 150f)), 1080f, 2520f)
        val face = p.controls.filter { it.spec.id in setOf("A", "B", "X", "Y") }
        s.select(centre(box(p, "A+B+X+Y"))); s.drag(Offset(-100f, 0f))
        near(assertNotNull(s.tweaks["DPAD"]).dx, 150f, "the d-pad stays")
        val pad = s.placed().controls.single { it.spec.id == "DPAD" }
        val padRight = pad.x + pad.spec.r + 22f * p.k
        near(s.placed().controls.filter { it.spec.id in setOf("A", "B", "X", "Y") }.minOf { it.x - it.spec.r }, padRight, "the face buttons against it", e = 0.5f)
        assertTrue(assertNotNull(s.tweaks["A+B+X+Y"]).dx > -100f + 1f, "stopped short")
        assertTrue(face.isNotEmpty())
    }

    @Test
    fun a_change_that_changes_nothing_is_no_edit() {
        // the face buttons against the d-pad in the skin's layout: in the overlay a drag onto it, or a resize by 1, owns nothing
        val p = menu()
        val face = p.controls.filter { it.spec.id in setOf("A", "B", "X", "Y") }
        val pad = p.controls.single { it.spec.id == "DPAD" }
        val gap = face.minOf { it.x - it.spec.r } - (pad.x + pad.spec.r + 22f * p.k)
        val s = overlayState(p, mapOf("A+B+X+Y" to SkinTweak(dx = -gap / p.k)), emptyMap())
        s.select(centre(box(s.placed(), "A+B+X+Y")))
        s.drag(Offset(-10f, 0f))
        s.resize(1f)
        assertTrue(s.owned.isEmpty(), "${s.owned}")
        assertFalse(s.dirty)
    }

    @Test
    fun drags_stay_quick() {
        // one drag event is what a finger sends each frame: report what a long drag costs on the JVM
        val p = grip()
        val s = SkinEditorState(p, emptyMap(), 2420f, 1080f, minTouchPx = 84f)
        s.select(centre(box(p, "A+B+X+Y")))
        repeat(20) { s.drag(Offset(-3f, 1f)); s.placed() }   // warm up
        val t0 = System.nanoTime()
        repeat(100) { s.drag(Offset(if (it < 50) -6f else 6f, if (it % 2 == 0) 2f else -2f)); s.placed() }
        val ms = (System.nanoTime() - t0) / 1e6
        println("100 drag events: $ms ms")
        assertTrue(ms < 5000.0, "100 drag events took $ms ms")
    }

    @Test
    fun a_skin_editor_saves_like_before() {
        val p = menu()
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f)
        s.select(centre(box(p, "MENU"))); s.drag(Offset(30f, 0f))
        assertEquals(s.tweaks, s.saved())
        assertTrue(s.owned.isEmpty())
        var skin = ""; var overlay = ""
        val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, m, a ->
            when (m.name) {
                "getSkinTweaks" -> skin
                "setSkinTweaks" -> { skin = a[0] as String; null }
                "getOverlayTweaks" -> overlay
                "setOverlayTweaks" -> { overlay = a[0] as String; null }
                else -> error(m.name)
            }
        } as AppSettings
        SkinLayoutEdit.save(settings, "gba", "stacked_menu", s)
        assertEquals(s.tweaks.keys, SkinTweaks.of(skin, "gba", "stacked_menu").keys)
        assertEquals("", overlay)
        val o = SkinLayoutEdit.state(settings, "gba", p, 1080, 2520, overlay = true, minTouchPx = 0f)
        near(assertNotNull(o.tweaks["MENU"]).dx, 30f, "overlay starts from the skin's")
        assertTrue(o.owned.isEmpty())
        o.select(centre(box(o.placed(), "DPAD"))); o.drag(Offset(0f, 10f))
        SkinLayoutEdit.save(settings, "gba", "stacked_menu", o)
        assertEquals(setOf("DPAD"), SkinTweaks.of(overlay, "gba", "stacked_menu").keys)
        assertEquals(setOf("MENU"), SkinTweaks.of(skin, "gba", "stacked_menu").keys, "the skin's stays")
        val again = SkinLayoutEdit.state(settings, "gba", p, 1080, 2520, overlay = true, minTouchPx = 0f)
        assertEquals(setOf("DPAD"), again.owned)
        assertFalse(again.dirty)
    }

    @Test
    fun the_settings_title_says_which_layout_it_edits() {
        assertEquals("Wii · Classic · Pad skin", SkinLayoutEdit.title("Wii · Classic", overlay = false))
        assertEquals("Game Boy Advance · Overlay", SkinLayoutEdit.title("Game Boy Advance", overlay = true))
    }

    @Test
    fun the_red_area_is_each_screen_whole() {
        // what the editor marks is the screen as seen (its area), not the smaller middle that is off-limits
        val p = grip()
        val s = SkinEditorState(p, emptyMap(), 2420f, 1080f)
        assertEquals(SkinTweaks.screenAreas(p), s.screenAreas)
        val a = s.screenAreas.first(); val z = s.noGo.first()
        assertTrue(a.x < z.x && a.y < z.y && a.x + a.width > z.x + z.width && a.y + a.height > z.y + z.height, "$a round $z")
        assertTrue(overlayState(p, emptyMap(), emptyMap(), 2420f, 1080f).screenAreas.isEmpty())
    }

    @Test
    fun overlay_editor_ignores_the_screens() {
        val p = grip()
        val zone = SkinTweaks.noGo(p).first()
        val id = "A+B+X+Y"
        val skin = SkinEditorState(p, emptyMap(), 2420f, 1080f)
        skin.select(centre(box(p, id))); skin.drag(Offset(-100000f, 0f))
        val ov = overlayState(p, emptyMap(), emptyMap(), 2420f, 1080f)
        assertTrue(ov.noGo.isEmpty())
        ov.select(centre(box(p, id))); ov.drag(Offset(-100000f, 0f))
        assertTrue(box(ov.placed(), id).x < box(skin.placed(), id).x - 1f, "the overlay goes on into the glass")
        assertTrue(box(ov.placed(), id).x < zone.x + zone.width - 1f)
    }

    @Test
    fun in_d_pad_mode_the_editor_lands_a_group_where_the_game_does() {
        // the DC's portrait layout carries the stick (ring 170, touch area past it) and the d-pad (156) on one spot: the
        // game tweaks both sets, then shows the d-pad, so the stick's touch area is what stops the group there too
        val p = assertNotNull(SkinChoice.place(carbon, 1080, 2520, 420, tablet = false, ds = false, picture = 320f to 240f, variant = SkinVariant.DC))
        assertEquals("stacked_dc", p.spec.id)
        assertTrue(p.controls.any { it.spec.alt == "stick" } && p.controls.any { it.spec.alt == "dpad" })
        val s = SkinEditorState(p, emptyMap(), 1080f, 2520f, useStick = false)
        val shown = s.placed()
        assertTrue(shown.controls.none { it.spec.alt == "stick" }, "the d-pad set shown")
        val id = assertNotNull(SkinTweaks.boxes(shown).entries.firstOrNull { (_, b) ->
            shown.controls.single { it.spec.id == "DPAD" }.let { c -> b.box.x <= c.x && c.x <= b.box.x + b.box.width && b.box.y <= c.y && c.y <= b.box.y + b.box.height }
        }).key
        s.select(centre(box(shown, id)))
        assertEquals(id, s.selected)
        s.drag(Offset(1000f, 0f))
        val editor = box(s.placed(), id)
        val game = box(SkinTweaks.apply(p, s.saved(), 1080f, 2520f).forStick(false), id)
        near(editor.x, game.x, "x", e = 0.5f); near(editor.y, game.y, "y", e = 0.5f)
        near(editor.width, game.width, "width", e = 0.5f)
        assertTrue(editor.x > box(shown, id).x + 1f, "it moved")
    }
}
