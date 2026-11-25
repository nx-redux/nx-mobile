package com.nxredux.mobile.options

import com.nxredux.mobile.options.WiiuOptions.BIG_SCREEN
import com.nxredux.mobile.options.WiiuOptions.LAYOUT_LANDSCAPE
import com.nxredux.mobile.options.WiiuOptions.LAYOUT_PORTRAIT
import com.nxredux.mobile.options.WiiuOptions.LAYOUT_PORTRAIT_CONTROLLER
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.runner.PadBandRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WiiuOptionsTest {
    @Test fun keys_values_and_defaults() {
        assertEquals("nx_wiiu_layout_portrait", LAYOUT_PORTRAIT)
        assertEquals("nx_wiiu_layout_portrait_controller", LAYOUT_PORTRAIT_CONTROLLER)
        assertEquals("nx_wiiu_layout_landscape", LAYOUT_LANDSCAPE)
        assertEquals("nx_wiiu_big_screen", BIG_SCREEN)
        assertEquals(listOf(LAYOUT_PORTRAIT, LAYOUT_PORTRAIT_CONTROLLER, LAYOUT_LANDSCAPE, BIG_SCREEN), WiiuOptions.table.map { it.key })
        assertEquals(
            mapOf(LAYOUT_PORTRAIT to "stacked", LAYOUT_PORTRAIT_CONTROLLER to "stacked", LAYOUT_LANDSCAPE to "large", BIG_SCREEN to "top"),
            WiiuOptions.table.associate { it.key to it.default },
        )
        val layouts = listOf("large", "stacked", "single", "pip", "side-by-side")
        for (key in listOf(LAYOUT_PORTRAIT, LAYOUT_PORTRAIT_CONTROLLER, LAYOUT_LANDSCAPE)) assertEquals(layouts, FrontendOptions.option(key)!!.values)
        assertEquals(listOf("top", "bottom"), FrontendOptions.option(BIG_SCREEN)!!.values)
        assertEquals(listOf("TV", "GamePad"), FrontendOptions.option(BIG_SCREEN)!!.valueLabels)
        assertTrue(WiiuOptions.table.all { it.visual && it.valueLabels.size == it.values.size })
    }

    @Test fun applies_to_the_wiiu_only() {
        assertTrue(WiiuOptions.appliesTo("WIIU"))
        assertTrue(WiiuOptions.appliesTo(" wiiu"))
        assertFalse(WiiuOptions.appliesTo("WII"))
        assertFalse(WiiuOptions.appliesTo("NDS"))
    }

    @Test fun the_layout_shown_is_the_pick_else_large_in_landscape_and_stacked_in_portrait() {
        assertEquals(NdsLayoutChoice.LARGE, WiiuOptions.layoutShown(emptyMap(), landscape = true, controllerMode = false))
        assertEquals(NdsLayoutChoice.STACKED, WiiuOptions.layoutShown(emptyMap(), landscape = false, controllerMode = false))
        assertEquals(NdsLayoutChoice.STACKED, WiiuOptions.layoutShown(emptyMap(), landscape = false, controllerMode = true))
        val picked = mapOf(LAYOUT_PORTRAIT to "pip", LAYOUT_PORTRAIT_CONTROLLER to "single", LAYOUT_LANDSCAPE to "side-by-side")
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, WiiuOptions.layoutShown(picked, landscape = true, controllerMode = false))
        assertEquals(NdsLayoutChoice.PIP, WiiuOptions.layoutShown(picked, landscape = false, controllerMode = false))
        assertEquals(NdsLayoutChoice.SINGLE, WiiuOptions.layoutShown(picked, landscape = false, controllerMode = true))
        assertEquals(NdsLayoutChoice.LARGE, WiiuOptions.layoutShown(mapOf(LAYOUT_LANDSCAPE to "nonsense"), landscape = true, controllerMode = false))
    }

    @Test fun settings_read_the_big_screen() {
        assertEquals(NdsScreen.TOP, WiiuOptions.settings(emptyMap()).big)
        assertEquals(NdsScreen.BOTTOM, WiiuOptions.settings(mapOf(BIG_SCREEN to "bottom")).big)
        // the DS keys are not the Wii U's
        assertEquals(NdsScreen.TOP, WiiuOptions.settings(mapOf(NdsOptions.BIG_SCREEN to "bottom")).big)
    }

    @Test fun swap_flips_the_big_screen() {
        assertEquals(BIG_SCREEN to "bottom", WiiuOptions.swapBigScreen(emptyMap()))
        assertEquals(BIG_SCREEN to "top", WiiuOptions.swapBigScreen(mapOf(BIG_SCREEN to "bottom")))
        assertEquals("GamePad", NdsOptions.valueLabel(BIG_SCREEN, "bottom"))
    }

    @Test fun the_running_game_shows_only_the_layout_row_in_use() {
        fun shown(view: NdsView) = WiiuOptions.inGameRows(WiiuOptions.table, view).map { it.key }
        assertEquals(listOf(LAYOUT_LANDSCAPE, BIG_SCREEN), shown(NdsView(landscape = true, controllerMode = false)))
        assertEquals(listOf(LAYOUT_PORTRAIT, BIG_SCREEN), shown(NdsView(landscape = false, controllerMode = false)))
        assertEquals(listOf(LAYOUT_PORTRAIT_CONTROLLER, BIG_SCREEN), shown(NdsView(landscape = false, controllerMode = true)))
    }

    @Test fun console_settings_list_the_wiiu_screen_rows() {
        val keys = ConsoleSettings.tableFor("WIIU").map { it.key }
        assertTrue(keys.containsAll(WiiuOptions.table.map { it.key }))
        assertTrue(keys.indexOf(LAYOUT_PORTRAIT) < keys.indexOf(com.nxredux.mobile.runner.Res3dConsole.CEMU.modeKey))
        assertTrue(ConsoleSettings.all.map { it.key }.containsAll(WiiuOptions.table.map { it.key }))
        assertFalse(ConsoleSettings.tableFor("NDS").any { it.key in WiiuOptions.table.map { o -> o.key } })
    }

    @Test fun a_controller_drops_the_band_and_uses_the_controller_row() {
        assertTrue(PadBandRule.controllerLayout("WIIU", portrait = true, controllerNames = listOf("Xbox Wireless Controller")))
        assertFalse(PadBandRule.controllerLayout("WIIU", portrait = false, controllerNames = listOf("Xbox Wireless Controller")))
    }
    @Test fun under_a_skin_only_a_regular_phones_landscape_takes_the_players_layout() {
        val picked = mapOf(LAYOUT_LANDSCAPE to NdsLayoutChoice.SIDE_BY_SIDE.value)
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, WiiuOptions.skinLayout(picked, landscape = true, controllerMode = false, ScreenClass.REGULAR_PHONE))
        assertEquals(NdsLayoutChoice.LARGE, WiiuOptions.skinLayout(emptyMap(), landscape = true, controllerMode = false, ScreenClass.REGULAR_PHONE))
        for (screen in listOf(ScreenClass.STUBBY_PHONE, ScreenClass.WIDE_TABLET, ScreenClass.NEAR_SQUARE_TABLET))
            assertEquals(null, WiiuOptions.skinLayout(picked, landscape = true, controllerMode = false, screen), "$screen")
        for (screen in ScreenClass.entries) assertEquals(null, WiiuOptions.skinLayout(picked, landscape = false, controllerMode = false, screen), "$screen")
    }

    @Test fun a_skin_locks_the_layout_rows_it_ignores() {
        assertEquals(emptySet(), WiiuOptions.lockedLayoutRows(false, ScreenClass.REGULAR_PHONE))
        assertEquals(setOf(LAYOUT_PORTRAIT), WiiuOptions.lockedLayoutRows(true, ScreenClass.REGULAR_PHONE))
        for (screen in listOf(ScreenClass.STUBBY_PHONE, ScreenClass.WIDE_TABLET, ScreenClass.NEAR_SQUARE_TABLET))
            assertEquals(setOf(LAYOUT_PORTRAIT, LAYOUT_LANDSCAPE), WiiuOptions.lockedLayoutRows(true, screen), "$screen")
    }
}
