package com.nxredux.mobile.skin

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.FrameRect
import com.nxredux.mobile.runner.NdsRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The in-game menu's room over a skin: its screen glass and the touch shortcut strip under it (hidden while it is open). */
class SkinMenuRectTest {
    private val carbon = skinPack()

    @Test
    fun the_menu_takes_the_glass_and_the_strip_under_it() {
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        val g = assertNotNull(p.glass(GlassScreen.PICTURE))
        val strip = p.controls.filter { it.spec.id.startsWith("TOUCH_") }
        assertTrue(strip.isNotEmpty())
        val r = p.menuRect(g)
        assertEquals(g.rect.x, r.x); assertEquals(g.rect.y, r.y); assertEquals(g.rect.width, r.width)
        assertEquals(strip.maxOf { it.y + it.spec.hh }, r.y + r.height, 0.5f)   // down to the strip's foot
    }

    @Test
    fun a_glass_with_no_strip_under_it_is_the_menus_room_as_it_is() {
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true, variant = SkinVariant.DS))
        val top = assertNotNull(p.glass(GlassScreen.TOP))   // the DS's strip is under the touch screen, not the top one
        assertEquals(top.rect, p.menuRect(top))
    }

    // DS / 3DS on a portrait phone: the top screen alone is too short, so the menu runs on down, to just above the
    // L / R buttons (the frame's foot and a gap clear of them); the touch screen under it is hidden while the menu is open.
    @Test
    fun stacked_screens_give_the_menu_room_down_to_above_the_shoulders() {
        for (variant in listOf(SkinVariant.DS, SkinVariant.N3DS_REGULAR, SkinVariant.N3DS)) {
            val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true, variant = variant), "$variant")
            val top = assertNotNull(p.glass(GlassScreen.TOP)).rect
            val r = assertNotNull(p.menuRoom(), "$variant")
            assertEquals(top, PxRect(r.x, r.y, r.width, top.height), "$variant: the top screen's width, from its top")
            assertTrue(r.y + r.height > top.y + top.height, "$variant: longer than the top screen")
            val shoulders = p.controls.filter { it.spec.id in setOf("L", "R", "ZL", "ZR") && it.y > top.y + top.height }
            assertTrue(shoulders.isNotEmpty(), "$variant")
            val bb = assertNotNull(p.spec.frame).let { (it.h - it.glass.y - it.glass.h) * p.k }
            val clear = shoulders.minOf { it.y - it.spec.hh }
            assertTrue(r.y + r.height + bb < clear, "$variant: the frame's foot clear of the shoulders")
            assertTrue(r.y + r.height + bb > clear - 48f * p.k, "$variant: down to near them")
        }
    }

    @Test
    fun one_screen_keeps_its_glass_and_strip() {
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        assertEquals(p.menuRect(assertNotNull(p.glass(GlassScreen.PICTURE))), p.menuRoom())
    }

    // Under the stretched menu: one glass the menu's size in the layout's own frame; what it covers is not drawn.
    @Test
    fun under_a_stretched_menu_the_screens_are_one_glass_in_the_frame() {
        for (variant in listOf(SkinVariant.DS, SkinVariant.N3DS_REGULAR)) {
            val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true, variant = variant), "$variant")
            val room = assertNotNull(p.stackedMenuRoom(), "$variant")
            val u = p.underMenu(room)
            val glass = u.glasses.single()
            assertEquals(room, glass.rect, "$variant")
            assertEquals(assertNotNull(p.glass(GlassScreen.TOP)).corner, glass.corner, "$variant")
            val frame = u.pieces.filter { it.piece == MENU_FRAME_PIECE && it.fill < 0f }   // (its art; the black over its opening aside)
            assertEquals(8, frame.size, "$variant")
            val fx0 = frame.minOf { it.dst.x }; val fy0 = frame.minOf { it.dst.y }
            val fx1 = frame.maxOf { it.dst.x + it.dst.width }; val fy1 = frame.maxOf { it.dst.y + it.dst.height }
            assertTrue(fx0 < room.x && fy0 < room.y && fx1 > room.x + room.width && fy1 > room.y + room.height, "$variant frame round the room")
            // the frame's solid foot starts right at the room's foot: no see-through band between the menu and the frame
            val f = assertNotNull(p.spec.frame)
            val bb = (f.h - f.glass.y - f.glass.h) * p.k
            assertEquals(room.y + room.height, fy1 - bb, 0.5f, "$variant frame foot")
            val cx = room.x + room.width / 2f; val cy = room.y + room.height / 2f
            for (piece in u.pieces) {   // nothing over the menu's middle, nothing of the old pieces inside the room
                if (piece.fill >= 0f) continue   // (the frame's black leaves every screen open)
                val d = piece.dst
                assertTrue(!(cx in d.x..(d.x + d.width) && cy in d.y..(d.y + d.height)), "$variant piece over the middle")
                if (piece.piece != MENU_FRAME_PIECE) assertTrue(d.x + d.width <= room.x + 1f || d.x >= room.x + room.width - 1f ||
                    d.y + d.height <= room.y + 1f || d.y >= room.y + room.height - 1f, "$variant $d in the room")
            }
            assertTrue(u.controls.none { it.x in room.x..(room.x + room.width) && it.y in room.y..(room.y + room.height) }, "$variant")
        }
    }

    @Test
    fun one_screen_has_no_stacked_room() {
        val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        assertEquals(null, p.stackedMenuRoom())
    }

    // The unfolded Fold and a tablet give the top screen half the screen: the menu stays in it.
    @Test
    fun the_unfolded_fold_and_a_tablet_keep_the_menu_on_the_top_screen() {
        for (variant in listOf(SkinVariant.DS, SkinVariant.N3DS_REGULAR)) {
            for ((name, size) in listOf("inner" to (1848 to 2448), "tablet" to (1440 to 2304))) {
                val tablet = name == "tablet"
                val p = assertNotNull(SkinChoice.place(carbon, size.first, size.second, if (tablet) 280 else 420, tablet = tablet, ds = true,
                    foldable = !tablet, variant = variant), "$variant $name")
                assertEquals(null, p.stackedMenuRoom(), "$variant $name")
                val top = assertNotNull(p.glass(GlassScreen.TOP), "$variant $name")
                assertEquals(p.menuRect(top), p.menuRoom(), "$variant $name")
            }
        }
    }

    // The stretched menu reaches into the touch screen's frame: the whole frame goes, its foot under the menu too.
    @Test
    fun under_a_stretched_menu_the_touch_screens_frame_goes_whole() {
        for (variant in listOf(SkinVariant.DS, SkinVariant.N3DS_REGULAR, SkinVariant.N3DS)) {
            val p = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true, foldable = true, variant = variant), "$variant")
            val touch = assertNotNull(p.glass(GlassScreen.TOUCH), "$variant").rect
            val frame = p.pieces.filter { it.fill >= 0f && it.dst.x <= touch.x && it.dst.y <= touch.y &&
                it.dst.x + it.dst.width >= touch.x + touch.width }.map { it.piece }.filter { it >= 0 }.toSet()
            assertTrue(frame.isNotEmpty(), "$variant: the touch screen has its own frame")
            val u = p.underMenu(assertNotNull(p.stackedMenuRoom(), "$variant"))
            assertTrue(u.pieces.none { it.piece in frame }, "$variant: ${u.pieces.filter { it.piece in frame }.map { it.dst }}")
            assertTrue(u.pieces.any { it.piece >= 0 && it.piece !in frame }, "$variant: the pad's pieces stay")
        }
    }

    // DS / 3DS / Wii U with the menu over the top screen (or over both in one glass): the bottom screen is hidden.
    @Test
    fun the_menu_hides_the_bottom_screen_where_it_lies_over_the_top_one() {
        for (variant in listOf(SkinVariant.DS, SkinVariant.N3DS_REGULAR, SkinVariant.N3DS, SkinVariant.WIIU)) {
            for ((w, h, foldable) in listOf(Triple(1248, 1972, true), Triple(1972, 1248, true), Triple(2520, 1080, false))) {
                val p = SkinChoice.place(carbon, w, h, 420, tablet = false, ds = true, foldable = foldable, variant = variant) ?: continue
                assertTrue(p.menuHidesBottom(), "$variant ${w}x$h ${p.glasses.map { it.screen }}")
            }
        }
    }

    // A two-screen board puts the menu in the bottom screen's glass (the top keeps the paused picture); one screen has none.
    @Test
    fun the_menu_in_the_bottom_glass_or_on_one_screen_hides_nothing() {
        for (variant in listOf(SkinVariant.DS, SkinVariant.N3DS, SkinVariant.WIIU)) {
            val p = assertNotNull(SkinChoice.place(carbon, 1848, 2448, 420, tablet = true, ds = true, foldable = true, variant = variant), "$variant")
            assertEquals(SkinFamily.FLEX, p.spec.family)
            assertTrue(!p.menuHidesBottom(), "$variant inner")
        }
        val gba = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = false, picture = 240f to 160f, variant = SkinVariant.GBA))
        assertTrue(!gba.menuHidesBottom())
    }

    @Test
    fun the_screens_drawn_under_the_menu_leave_out_the_bottom_one_only_where_it_hides() {
        fun region(s: NdsScreen, y: Float) = NdsRegion(s, FrameRect(0f, 0f, 1f, 1f), PxRect(0f, y, 10f, 10f), shaded = false, touch = s == NdsScreen.BOTTOM)
        val regions = listOf(region(NdsScreen.TOP, 0f), region(NdsScreen.BOTTOM, 20f))
        val cover = assertNotNull(SkinChoice.place(carbon, 1248, 1972, 420, tablet = false, ds = true, foldable = true, variant = SkinVariant.N3DS))
        assertEquals(listOf(regions[0]), menuShownScreens(regions, cover))
        val inner = assertNotNull(SkinChoice.place(carbon, 1848, 2448, 420, tablet = true, ds = true, foldable = true, variant = SkinVariant.N3DS))
        assertEquals(regions, menuShownScreens(regions, inner))
    }
}
