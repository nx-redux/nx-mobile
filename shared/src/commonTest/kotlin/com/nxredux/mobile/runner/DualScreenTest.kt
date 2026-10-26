package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DualScreenTest {
    @Test fun mode_off_without_display() = assertEquals(DualMode.OFF, DualScreen.mode(false, true, true, false))
    @Test fun mode_off_when_setting_off() = assertEquals(DualMode.OFF, DualScreen.mode(true, false, true, true))
    @Test fun mode_split_for_ds() = assertEquals(DualMode.SPLIT, DualScreen.mode(true, true, true, false))
    @Test fun mode_panel_up_for_ds() = assertEquals(DualMode.PANEL_UP, DualScreen.mode(true, true, true, true))
    @Test fun mode_panel_for_regular_game() = assertEquals(DualMode.PANEL, DualScreen.mode(true, true, false, false))

    @Test fun menu_goes_on_the_second_display_when_the_main_one_is_the_picture() {
        // The wide monitor is the picture on AUTO, so the menu stays on the phone.
        assertFalse(DualScreen.menuOnSecond(1248, 1972, 2560, 1600, rotated = false, BottomScreen.AUTO))
        // Bottom = main puts the picture on the second: menu stays on main.
        assertFalse(DualScreen.menuOnSecond(1248, 1972, 2560, 1600, rotated = false, BottomScreen.MAIN))
        // Bottom = second puts the picture on main: the menu goes to the second.
        assertTrue(DualScreen.menuOnSecond(1248, 1972, 2560, 1600, rotated = false, BottomScreen.SECOND))
    }

    @Test fun nds_split_top_main() {
        val main = DualScreen.ndsScreen(1920f, 1080f, NdsScreen.TOP).single()
        assertEquals(NdsLayout.TOP_SRC, main.src); assertFalse(main.touch)
        assertEquals(PxRect(240f, 0f, 1440f, 1080f), main.dst)
        val second = DualScreen.ndsScreen(1240f, 1080f, NdsScreen.TOP.other).single()
        assertEquals(NdsLayout.BOTTOM_SRC, second.src); assertTrue(second.touch)
    }
    @Test fun nds_split_swapped_touch_follows_bottom() {
        assertTrue(DualScreen.ndsScreen(1920f, 1080f, NdsScreen.BOTTOM).single().touch)
        assertFalse(DualScreen.ndsScreen(1240f, 1080f, NdsScreen.BOTTOM.other).single().touch)
    }
    @Test fun nds_split_main_follows_area() =
        assertEquals(PxRect(0f, 0f, 1080f, 810f), DualScreen.ndsScreen(1080f, 1920f, NdsScreen.TOP).single().dst.let { it.copy(y = 0f) })
    @Test fun nds_panel_up_is_side_by_side_with_bottom_touch() {
        val r = DualScreen.ndsPanelUp(1920f, 1080f, NdsSettings(landscape = NdsLayoutChoice.PIP))
        assertEquals(2, r.size); assertEquals(listOf(false, true), r.map { it.touch })
    }
    @Test fun nds_panel_up_keeps_its_own_fractional_fit() {
        val r = DualScreen.ndsPanelUp(2400f, 1080f, NdsSettings())
        assertEquals(PxRect(0f, 90f, 1200f, 900f), r[0].dst)
        assertEquals(PxRect(1200f, 90f, 1200f, 900f), r[1].dst)
        assertEquals(listOf(NdsScreen.TOP, NdsScreen.BOTTOM), r.map { it.screen })
        assertEquals(listOf(NdsScreen.BOTTOM, NdsScreen.TOP), DualScreen.ndsPanelUp(2400f, 1080f, NdsSettings(big = NdsScreen.BOTTOM)).map { it.screen })
    }
    @Test fun n3ds_split_rects() {
        assertEquals(FrameRect(0.1f, 0.5f, 0.9f, 1f), DualScreen.n3dsScreen(1240f, 1080f, NdsScreen.TOP.other).single().src)
        assertEquals(1800f / 1080f, DualScreen.n3dsScreen(1920f, 1080f, NdsScreen.TOP).single().dst.let { it.width / it.height }, 0.01f)
    }
    @Test fun n3ds_split_core_options() = assertEquals(
        mapOf("citra_layout_option" to "default", "citra_stacked_small_proportion" to "1",
              "citra_swap_screen" to "Top", "citra_swap_screen_mode" to "Hold"),
        DualScreen.n3dsCoreOptions(DualMode.SPLIT))
    @Test fun n3ds_panel_up_side_by_side_swap_toggles() = assertEquals(
        mapOf("citra_layout_option" to "side_by_side", "citra_swap_screen" to "Top", "citra_swap_screen_mode" to "Toggle"),
        DualScreen.n3dsCoreOptions(DualMode.PANEL_UP))
    @Test fun n3ds_off_restores() = assertNull(DualScreen.n3dsCoreOptions(DualMode.OFF))

    @Test fun n3ds_half_folded_pins_the_swap() = assertEquals(
        mapOf("citra_swap_screen" to "Top", "citra_swap_screen_mode" to "Hold"),
        DualScreen.n3dsForcedOptions(DualMode.OFF, halfFolded = true))
    @Test fun n3ds_split_wins_over_half_folded() =
        assertEquals(DualScreen.n3dsCoreOptions(DualMode.SPLIT), DualScreen.n3dsForcedOptions(DualMode.SPLIT, halfFolded = true))
    @Test fun n3ds_flat_forces_nothing() {
        assertNull(DualScreen.n3dsForcedOptions(DualMode.OFF, halfFolded = false))
        assertEquals(DualScreen.n3dsCoreOptions(DualMode.PANEL_UP), DualScreen.n3dsForcedOptions(DualMode.PANEL_UP, halfFolded = false))
    }

    @Test fun n3ds_swap_restore_prefers_the_running_values() = assertEquals(
        mapOf("citra_swap_screen" to "Bottom", "citra_swap_screen_mode" to "Hold"),
        DualScreen.n3dsSwapRestore(mapOf("citra_swap_screen" to "Bottom", "citra_swap_screen_mode" to "Hold"),
            mapOf("citra_swap_screen" to "Top", "citra_swap_screen_mode" to "Toggle")))
    @Test fun n3ds_swap_restore_falls_back_to_stored_then_defaults() {
        assertEquals(mapOf("citra_swap_screen" to "Bottom", "citra_swap_screen_mode" to "Toggle"),
            DualScreen.n3dsSwapRestore(null, mapOf("citra_swap_screen" to "Bottom")))
        assertEquals(mapOf("citra_swap_screen" to "Top", "citra_swap_screen_mode" to "Hold"),
            DualScreen.n3dsSwapRestore(mapOf("citra_swap_screen_mode" to "Hold"), emptyMap()))
    }
    @Test fun n3ds_touch_anywhere_in_split() {
        assertEquals("disabled", DualScreen.n3dsTouchMainOnly(DualMode.SPLIT, padOverGame = true))
        assertEquals("enabled", DualScreen.n3dsTouchMainOnly(DualMode.OFF, padOverGame = true))
        assertEquals("disabled", DualScreen.n3dsTouchMainOnly(DualMode.OFF, padOverGame = false))
    }
    @Test fun picture_on_second_when_wider() = assertEquals(PictureOn.SECOND, DualScreen.pictureOn(1240, 1080, 1920, 1080))
    @Test fun picture_on_main_when_wider() = assertEquals(PictureOn.MAIN, DualScreen.pictureOn(1920, 1080, 1240, 1080))
    @Test fun picture_on_tie_goes_second() = assertEquals(PictureOn.SECOND, DualScreen.pictureOn(1920, 1080, 1280, 720))
    @Test fun picture_on_main_for_a_rotated_core() =
        assertEquals(PictureOn.MAIN, DualScreen.pictureOn(1240, 1080, 1920, 1080, rotated = true))
    // Bottom screen: the chosen one is the touch screen, so the picture goes on the other; Auto is the shape rule.
    @Test fun bottom_main_puts_the_picture_on_second() =
        assertEquals(PictureOn.SECOND, DualScreen.pictureOn(1920, 1080, 1240, 1080, bottom = BottomScreen.MAIN))
    @Test fun bottom_second_puts_the_picture_on_main() =
        assertEquals(PictureOn.MAIN, DualScreen.pictureOn(1240, 1080, 1920, 1080, bottom = BottomScreen.SECOND))
    @Test fun bottom_auto_is_the_shape_rule() {
        assertEquals(DualScreen.pictureOn(1240, 1080, 1920, 1080), DualScreen.pictureOn(1240, 1080, 1920, 1080, bottom = BottomScreen.AUTO))
        assertEquals(DualScreen.pictureOn(1920, 1080, 1240, 1080), DualScreen.pictureOn(1920, 1080, 1240, 1080, bottom = BottomScreen.AUTO))
        assertEquals(PictureOn.SECOND, DualScreen.pictureOn(1280, 960, 1280, 960, bottom = BottomScreen.AUTO))   // same shape
    }
    @Test fun a_rotated_core_wins_over_the_bottom_screen() =
        assertEquals(PictureOn.MAIN, DualScreen.pictureOn(1920, 1080, 1240, 1080, rotated = true, bottom = BottomScreen.MAIN))
    @Test fun bottom_screen_parses_the_setting_and_the_override_wins() {
        assertEquals(BottomScreen.AUTO, BottomScreen.parse("auto"))
        assertEquals(BottomScreen.MAIN, BottomScreen.parse("main"))
        assertEquals(BottomScreen.SECOND, BottomScreen.parse("second"))
        assertEquals(BottomScreen.AUTO, BottomScreen.parse(null))
        assertEquals(BottomScreen.AUTO, BottomScreen.parse("x"))
        assertEquals(BottomScreen.SECOND, BottomScreen.resolve("second", BottomScreen.MAIN))
        assertEquals(BottomScreen.MAIN, BottomScreen.resolve("main", BottomScreen.SECOND))
        assertEquals(BottomScreen.SECOND, BottomScreen.resolve("default", BottomScreen.SECOND))
        assertEquals(BottomScreen.AUTO, BottomScreen.resolve(null, BottomScreen.AUTO))
        assertEquals(listOf("auto", "main", "second"), BottomScreen.entries.map { it.value })   // stored prefs stay valid
    }
    @Test fun picture_on_main_without_a_size() = assertEquals(PictureOn.MAIN, DualScreen.pictureOn(1920, 1080, 0, 0))
    @Test fun full_frame_fits_and_centres() =
        assertEquals(PxRect(240f, 0f, 1440f, 1080f), DualScreen.fullFrame(1920f, 1080f, 4f / 3f))
    @Test fun full_frame_letterboxes_a_wide_picture() =
        assertEquals(PxRect(0f, 191f, 1240f, 697f), DualScreen.fullFrame(1240f, 1080f, 16f / 9f))
    @Test fun full_frame_empty_without_an_aspect() = assertEquals(PxRect(0f, 0f, 0f, 0f), DualScreen.fullFrame(1920f, 1080f, 0f))
    @Test fun split_main_screen_follows_the_picture() {
        assertEquals(NdsScreen.TOP, DualScreen.splitMain(NdsScreen.TOP, PictureOn.MAIN))
        assertEquals(NdsScreen.BOTTOM, DualScreen.splitMain(NdsScreen.TOP, PictureOn.SECOND))
        assertEquals(NdsScreen.TOP, DualScreen.splitMain(NdsScreen.BOTTOM, PictureOn.SECOND))
    }
    @Test fun pen_goes_where_the_bottom_screen_is() {
        val top = DualScreen.ndsScreen(1920f, 1080f, NdsScreen.TOP)
        val bottom = DualScreen.ndsScreen(1240f, 1080f, NdsScreen.BOTTOM)
        assertEquals(PictureOn.SECOND, DualScreen.penOn(main = top, second = bottom))       // split, bottom screen on the second display
        assertEquals(PictureOn.MAIN, DualScreen.penOn(main = bottom, second = top))         // split, swapped
        assertEquals(PictureOn.MAIN, DualScreen.penOn(main = bottom, second = emptyList())) // single screen
        val both = DualScreen.ndsPanelUp(1920f, 1080f, NdsSettings())
        assertEquals(PictureOn.SECOND, DualScreen.penOn(main = both, second = both))        // panel up, the picture on the second display
        assertNull(DualScreen.penOn(main = top, second = emptyList()))                     // top screen alone
        assertNull(DualScreen.penOn(emptyList(), emptyList()))                             // not split
    }
}
