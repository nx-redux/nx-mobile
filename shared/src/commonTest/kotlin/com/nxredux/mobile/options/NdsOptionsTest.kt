package com.nxredux.mobile.options

import com.nxredux.mobile.options.NdsOptions.BIG_SCREEN
import com.nxredux.mobile.options.NdsOptions.LAYOUT_LANDSCAPE
import com.nxredux.mobile.options.NdsOptions.LAYOUT_PORTRAIT
import com.nxredux.mobile.options.NdsOptions.LAYOUT_PORTRAIT_CONTROLLER
import com.nxredux.mobile.options.NdsOptions.PIP_CORNER
import com.nxredux.mobile.options.NdsOptions.PIP_OPACITY
import com.nxredux.mobile.options.NdsOptions.PIP_SIZE
import com.nxredux.mobile.layout.ScreenClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NdsOptionsTest {
    @Test fun keys_values_and_defaults_follow_the_spec() {
        assertEquals(listOf("nx_nds_layout_portrait_v3", "nx_nds_layout_portrait_controller_v2", "nx_nds_layout_landscape_v2", "nx_nds_3d_resolution", "nx_nds_3d_max_scale", "nx_nds_3d_scale", "nx_nds_big_screen", "nx_nds_pip_corner", "nx_nds_pip_size",
            "nx_nds_pip_opacity"),
            NdsOptions.table.map { it.key })
        assertEquals(
            mapOf(LAYOUT_PORTRAIT to "large", LAYOUT_PORTRAIT_CONTROLLER to "large", LAYOUT_LANDSCAPE to "side-by-side", NdsOptions.MODE_3D to "auto", NdsOptions.MAX_SCALE_3D to "4", NdsOptions.SCALE_3D to "2", BIG_SCREEN to "top", PIP_CORNER to "top-right", PIP_SIZE to "medium",
                PIP_OPACITY to "100"),
            NdsOptions.table.associate { it.key to it.default },
        )
        val layouts = listOf("large", "stacked", "single", "pip", "side-by-side")
        assertEquals(layouts, FrontendOptions.option(LAYOUT_PORTRAIT)!!.values)
        assertEquals(layouts, FrontendOptions.option(LAYOUT_PORTRAIT_CONTROLLER)!!.values)
        assertEquals(layouts, FrontendOptions.option(LAYOUT_LANDSCAPE)!!.values)
        assertNull(NdsSettings().portrait)
        assertEquals("Layout (portrait, controller)", FrontendOptions.option(LAYOUT_PORTRAIT_CONTROLLER)!!.label)
        assertEquals(listOf("top", "bottom"), FrontendOptions.option(BIG_SCREEN)!!.values)
        assertEquals(listOf("top-left", "top-right", "bottom-left", "bottom-right"), FrontendOptions.option(PIP_CORNER)!!.values)
        assertEquals(listOf("small", "medium", "large"), FrontendOptions.option(PIP_SIZE)!!.values)
        assertEquals(listOf(0.25f, 0.33f, 0.40f), PipSize.entries.map { it.fraction })
        assertEquals(listOf("100", "75", "50"), FrontendOptions.option(PIP_OPACITY)!!.values)
        assertEquals(listOf("100%", "75%", "50%"), FrontendOptions.option(PIP_OPACITY)!!.valueLabels)
        assertEquals(listOf(1f, 0.75f, 0.5f), PipOpacity.entries.map { it.alpha })
        assertEquals("Inset Opacity", FrontendOptions.option(PIP_OPACITY)!!.label)
        val keys = NdsOptions.table.map { it.key }
        assertEquals(keys.indexOf(PIP_SIZE) + 1, keys.indexOf(PIP_OPACITY))    // right after the size row
        assertEquals(keys.indexOf(LAYOUT_PORTRAIT) + 1, keys.indexOf(LAYOUT_PORTRAIT_CONTROLLER))    // right after the portrait row
        assertEquals(PIP_OPACITY, keys.last())    // the last row
        assertTrue(NdsOptions.table.all { (it.visual || it.key in NdsOptions.RESOLUTION_KEYS) && it.valueLabels.size == it.values.size })
    }

    @Test fun resolutionRows() {
        val (mode, max, scale) = NdsOptions.resolutionRows(null)
        assertEquals(listOf("3D Resolution", "Max Scale", "Scale"), listOf(mode.label, max.label, scale.label))
        assertEquals(listOf("auto", "quality", "performance"), mode.values)
        assertEquals(listOf("Auto", "Quality", "Performance"), mode.valueLabels)
        assertEquals("auto", mode.default)
        assertEquals("How sharply 3D models are drawn. 2D art, text and the screen's size don't change.", mode.info)
        assertEquals("4", max.default)
        assertEquals(NdsOptions.MODE_3D to setOf("auto"), max.shownWhen)
        assertEquals("2", scale.default)
        assertEquals(NdsOptions.MODE_3D to setOf("performance"), scale.shownWhen)
        assertEquals((1..8).map { "${it}x" }, max.valueLabels)
        assertNull(mode.shownWhen)
        // No Adjust strip: the paused core draws no new frame, so the change only shows after Continue.
        assertTrue(NdsOptions.resolutionRows(null).none { it.visual })
    }

    @Test fun listsFollowTheDisplay() {
        fun lists(displayMax: Int?) = NdsOptions.resolutionRows(displayMax).let { (_, max, scale) -> max.values to scale.values }
        fun upTo(n: Int) = (1..n).map { it.toString() }
        assertEquals(upTo(8) to upTo(5), lists(9))
        assertEquals(upTo(3) to upTo(2), lists(3))
        assertEquals("4", NdsOptions.resolutionRows(3)[1].default)   // clamped where it is used
        assertEquals(upTo(8) to upTo(8), lists(null))
    }

    @Test fun shownFor() {
        val rows = NdsOptions.resolutionRows(null)
        fun shown(values: Map<String, String>) = rows.shownFor(values).map { it.label }
        assertEquals(listOf("3D Resolution", "Max Scale"), shown(emptyMap()))
        assertEquals(listOf("3D Resolution", "Scale"), shown(mapOf(NdsOptions.MODE_3D to "performance")))
        assertEquals(listOf("3D Resolution"), shown(mapOf(NdsOptions.MODE_3D to "quality")))
    }

    @Test fun only_nds_gets_the_ds_rows() {
        val pad = listOf(BottomScreenOptions.row, PadOptions.layoutRow) + (SpeedOptions.table + AudioOptions.row)
        val dsPad = listOf(BottomScreenOptions.row, PadOptions.portraitPadRow, PadOptions.layoutRow) + (SpeedOptions.table + AudioOptions.row)
        assertEquals(NdsOptions.table + dsPad + FrontendOptions.table, FrontendOptions.allFor("NDS"))
        assertEquals(NdsOptions.table + dsPad + FrontendOptions.table, FrontendOptions.allFor(" nds "))
        assertEquals(pad + FrontendOptions.table, FrontendOptions.allFor("GBA"))
        assertEquals(pad + FrontendOptions.table, FrontendOptions.allFor(""))
        assertEquals("medium", FrontendOptions.fromConfig(emptyMap(), "NDS")[PIP_SIZE])
        assertEquals("large", FrontendOptions.fromConfig(mapOf(PIP_SIZE to "large"), "NDS")[PIP_SIZE])
        assertFalse(PIP_SIZE in FrontendOptions.fromConfig(mapOf(PIP_SIZE to "large"), "GBA"))
        assertFalse(PIP_SIZE in FrontendOptions.defaults())
    }

    @Test fun menu_rows_have_no_auto() {
        for (row in NdsOptions.table.filter { it.key in NdsOptions.NO_PICK_KEYS }) {
            assertFalse("auto" in row.values)
            assertFalse("Auto" in row.valueLabels)
            assertFalse("Auto" in row.info)
        }
        assertNull(NdsLayoutChoice.entries.firstOrNull { it.value == "auto" })
    }

    @Test fun stored_values_parse_and_unknown_ones_fall_back() {
        val s = NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "stacked", BIG_SCREEN to "bottom", PIP_CORNER to "top-left", PIP_SIZE to "large"))
        assertEquals(NdsSettings(NdsLayoutChoice.STACKED, null, NdsScreen.BOTTOM, PipCorner.TOP_LEFT, PipSize.LARGE), s)
        assertEquals(NdsSettings(), NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "Stacked", PIP_SIZE to "huge", BIG_SCREEN to "")))
        assertEquals(NdsSettings(), NdsSettings.parse(emptyMap()))
        assertEquals(PipOpacity.THREE_QUARTERS, NdsSettings.parse(mapOf(PIP_OPACITY to "75")).opacity)
        assertEquals(PipOpacity.HALF, NdsSettings.parse(mapOf(PIP_OPACITY to "50")).opacity)
        assertEquals(PipOpacity.FULL, NdsSettings.parse(mapOf(PIP_OPACITY to "60")).opacity)
        assertEquals(PipOpacity.FULL, NdsSettings().opacity)
        assertEquals(NdsScreen.BOTTOM, NdsScreen.TOP.other)
    }

    @Test fun the_controller_portrait_layout_parses_and_has_no_pick_by_default() {
        assertNull(NdsSettings().controller)
        assertNull(NdsSettings.parse(emptyMap()).controller)
        assertEquals(NdsLayoutChoice.SINGLE, NdsSettings.parse(mapOf(LAYOUT_PORTRAIT_CONTROLLER to "single")).controller)
        assertNull(NdsSettings.parse(mapOf(LAYOUT_PORTRAIT_CONTROLLER to "huge")).controller)
        val s = NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "single", LAYOUT_PORTRAIT_CONTROLLER to "pip"))
        assertEquals(NdsLayoutChoice.SINGLE, s.layoutShown(false, false, ScreenClass.REGULAR_PHONE))
        assertEquals(NdsLayoutChoice.PIP, s.layoutShown(false, true, ScreenClass.REGULAR_PHONE))
    }

    @Test fun rows_cycle_and_show_labels() {
        assertEquals("side-by-side", FrontendOptions.cycle(LAYOUT_PORTRAIT, "large", -1, emptyList()))
        assertEquals("large", FrontendOptions.cycle(LAYOUT_PORTRAIT, "stacked", -1, emptyList()))
        assertEquals("Picture in picture", FrontendOptions.option(LAYOUT_PORTRAIT)!!.labelOf("pip"))
        assertEquals("weird", FrontendOptions.option(LAYOUT_PORTRAIT)!!.labelOf("weird"))
        assertEquals("Aspect", FrontendOptions.option(FrontendOptions.SCALING)!!.labelOf("Aspect"))
    }

    private val fold = ScreenClass.STUBBY_PHONE
    private val phone = ScreenClass.REGULAR_PHONE

    private fun cycle(values: Map<String, String>, screen: ScreenClass, controllerMode: Boolean = false, delta: Int = 1, landscape: Boolean = false) =
        NdsOptions.cycleLayout(values, landscape, controllerMode, delta, screen)

    @Test fun fold_landscape_default_large() {
        for (s in listOf(ScreenClass.STUBBY_PHONE, ScreenClass.NEAR_SQUARE_TABLET, ScreenClass.WIDE_TABLET))
            assertEquals(NdsLayoutChoice.LARGE, ndsDefault(s, landscape = true))
    }

    @Test fun phone_landscape_default_side_by_side() {
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, ndsDefault(phone, landscape = true))
    }

    @Test fun phone_portrait_default_large() {
        assertEquals(NdsLayoutChoice.LARGE, ndsDefault(phone, landscape = false))
        assertEquals(NdsLayoutChoice.LARGE, ndsDefault(fold, landscape = false))
    }

    @Test fun controller_row_defaults_to_portrait_default() {
        for (s in ScreenClass.entries) {
            assertEquals(ndsDefault(s, false), NdsOptions.layoutShown(emptyMap(), landscape = false, controllerMode = true, screen = s))
            val row = NdsOptions.layoutRows(s).first { it.key == LAYOUT_PORTRAIT_CONTROLLER }
            assertEquals(ndsDefault(s, false).value, row.default)
        }
    }

    @Test fun old_untouched_stacked_reads_as_no_pick() {
        assertNull(NdsSettings.parse(mapOf("nx_nds_layout_landscape" to "stacked")).landscape)
        assertNull(NdsSettings.parse(mapOf("nx_nds_layout_portrait_controller" to "stacked")).controller)
        assertEquals(NdsLayoutChoice.PIP, NdsSettings.parse(mapOf("nx_nds_layout_landscape" to "pip")).landscape)
        // The new key wins over the old one, even when it says stacked.
        assertEquals(NdsLayoutChoice.STACKED, NdsSettings.parse(mapOf(LAYOUT_LANDSCAPE to "stacked", "nx_nds_layout_landscape" to "pip")).landscape)
    }

    @Test fun old_portrait_auto_reads_as_no_pick() {
        assertNull(NdsSettings.parse(mapOf("nx_nds_layout_portrait_v2" to "auto")).portrait)
        assertEquals(NdsLayoutChoice.PIP, NdsSettings.parse(mapOf("nx_nds_layout_portrait_v2" to "pip")).portrait)
        assertEquals(NdsLayoutChoice.SINGLE, NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "single", "nx_nds_layout_portrait_v2" to "auto")).portrait)
    }

    @Test fun old_side_by_side_kept() {
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, NdsSettings.parse(mapOf("nx_nds_layout_landscape" to "side-by-side")).landscape)
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, NdsSettings.parse(mapOf("nx_nds_layout_portrait_v2" to "side-by-side")).portrait)
    }

    @Test fun a_config_read_keeps_no_pick_rows_absent_and_migrates_old_picks() {
        val read = FrontendOptions.fromConfig(mapOf("nx_nds_layout_landscape" to "stacked", "nx_nds_layout_portrait_v2" to "pip"), "NDS")
        assertFalse(LAYOUT_LANDSCAPE in read)
        assertFalse(LAYOUT_PORTRAIT_CONTROLLER in read)
        assertEquals("pip", read[LAYOUT_PORTRAIT])
        assertFalse(LAYOUT_PORTRAIT in FrontendOptions.defaults("NDS"))
        assertEquals("medium", read[PIP_SIZE])
    }

    @Test fun a_migrated_old_auto_is_no_pick_in_a_config_read() {
        // The old controller default was stacked, so an old "auto" there is kept by migrateKey: it must still read as no pick.
        val read = FrontendOptions.fromConfig(mapOf("nx_nds_layout_portrait_controller" to "auto"), "NDS")
        assertFalse(LAYOUT_PORTRAIT_CONTROLLER in read)
        assertNull(NdsOptions.layoutValue(mapOf("nx_nds_layout_portrait_controller" to "auto"), LAYOUT_PORTRAIT_CONTROLLER))
        assertEquals("pip", NdsOptions.layoutValue(mapOf("nx_nds_layout_portrait_controller" to "pip"), LAYOUT_PORTRAIT_CONTROLLER))
    }

    @Test fun layout_cycle_steps_from_the_default_when_unset() {
        assertEquals(LAYOUT_PORTRAIT to "stacked", cycle(emptyMap(), phone))                     // large -> stacked
        assertEquals(LAYOUT_PORTRAIT to "side-by-side", cycle(emptyMap(), phone, delta = -1))    // large wraps back
        assertEquals(LAYOUT_LANDSCAPE to "large", cycle(emptyMap(), phone, landscape = true))    // side-by-side wraps to large
        assertEquals(LAYOUT_LANDSCAPE to "pip", cycle(emptyMap(), phone, landscape = true, delta = -1))
    }

    @Test fun layout_cycles_in_portrait_and_wraps() {
        assertEquals(LAYOUT_PORTRAIT to "single", cycle(mapOf(LAYOUT_PORTRAIT to "stacked"), phone))
        assertEquals(LAYOUT_PORTRAIT to "pip", cycle(mapOf(LAYOUT_PORTRAIT to "single"), phone))
        assertEquals(LAYOUT_PORTRAIT to "side-by-side", cycle(mapOf(LAYOUT_PORTRAIT to "pip"), phone))
        assertEquals(LAYOUT_PORTRAIT to "large", cycle(mapOf(LAYOUT_PORTRAIT to "side-by-side"), phone))   // wraps
    }

    @Test fun cycle_keeps_side_by_side_in_fold_portrait() {
        assertEquals(LAYOUT_PORTRAIT to "side-by-side", cycle(mapOf(LAYOUT_PORTRAIT to "pip"), fold))
        assertEquals(LAYOUT_PORTRAIT to "large", cycle(mapOf(LAYOUT_PORTRAIT to "side-by-side"), fold))
    }

    @Test fun cycle_skips_side_by_side_on_fold_landscape() {
        assertEquals(LAYOUT_LANDSCAPE to "large", cycle(mapOf(LAYOUT_LANDSCAPE to "pip"), fold, landscape = true))      // pip -> (skip) -> large
        assertEquals(LAYOUT_LANDSCAPE to "pip", cycle(mapOf(LAYOUT_LANDSCAPE to "large"), fold, landscape = true, delta = -1))
        assertEquals(LAYOUT_LANDSCAPE to "stacked", cycle(emptyMap(), fold, landscape = true))   // from the default, large
        // A regular phone keeps it.
        assertEquals(LAYOUT_LANDSCAPE to "side-by-side", cycle(mapOf(LAYOUT_LANDSCAPE to "pip"), phone, landscape = true))
        // The landscape key serves both pad and controller modes; portrait keys are untouched.
        assertEquals(LAYOUT_LANDSCAPE to "single", cycle(mapOf(LAYOUT_PORTRAIT to "pip", LAYOUT_LANDSCAPE to "stacked"), phone, controllerMode = true, landscape = true))
        assertEquals("Single screen", NdsOptions.valueLabel(LAYOUT_LANDSCAPE, "single"))
        assertEquals("Picture in picture", NdsOptions.valueLabel(LAYOUT_LANDSCAPE, "pip"))
    }

    @Test fun saved_side_by_side_shows_large_on_fold_landscape() {
        val values = mapOf(LAYOUT_LANDSCAPE to "side-by-side")
        assertEquals(NdsLayoutChoice.LARGE, NdsOptions.layoutShown(values, landscape = true, controllerMode = false, screen = fold))
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, NdsOptions.layoutShown(values, landscape = true, controllerMode = false, screen = phone))
        assertEquals(LAYOUT_LANDSCAPE to "stacked", cycle(values, fold, landscape = true))   // steps from what shows (large)
        // Portrait on the same screen keeps it.
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, NdsOptions.layoutShown(mapOf(LAYOUT_PORTRAIT to "side-by-side"), landscape = false, controllerMode = false, screen = fold))
        // The stored pick itself is kept.
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE, NdsSettings.parse(values).landscape)
    }

    @Test fun the_landscape_row_omits_side_by_side_on_fold_shaped_screens_only() {
        fun landscape(screen: ScreenClass?) = NdsOptions.layoutRows(screen).first { it.key == LAYOUT_LANDSCAPE }
        assertEquals(listOf("large", "stacked", "single", "pip"), landscape(fold).values)
        assertEquals(listOf("large", "stacked", "single", "pip", "side-by-side"), landscape(phone).values)
        assertEquals(listOf("large", "stacked", "single", "pip", "side-by-side"), landscape(null).values)
        assertEquals(landscape(fold).values.size, landscape(fold).valueLabels.size)
        assertEquals("large", landscape(fold).default)
        assertTrue(NdsOptions.layoutRows(fold).filter { it.key != LAYOUT_LANDSCAPE }.all { "side-by-side" in it.values })
        assertEquals(NdsOptions.layoutRows(fold) + NdsOptions.table.drop(3), ConsoleSettings.tableFor("NDS", fold).take(NdsOptions.table.size))
    }

    @Test fun layout_cycle_writes_only_the_key_in_use() {
        val values = mapOf(LAYOUT_PORTRAIT to "pip", LAYOUT_PORTRAIT_CONTROLLER to "single")
        assertEquals(LAYOUT_PORTRAIT_CONTROLLER to "pip", cycle(values, phone, controllerMode = true))
        assertEquals(LAYOUT_PORTRAIT_CONTROLLER to "stacked", cycle(values, phone, controllerMode = true, delta = -1))
        assertEquals(LAYOUT_PORTRAIT to "side-by-side", cycle(values, phone))
        // The controller key starts from the portrait default, large.
        assertEquals(LAYOUT_PORTRAIT_CONTROLLER to "stacked", cycle(emptyMap(), fold, controllerMode = true))
        assertEquals("Single screen", NdsOptions.valueLabel(LAYOUT_PORTRAIT_CONTROLLER, "single"))
    }

    @Test fun corner_cycles_clockwise_and_wraps_both_ways() {
        assertEquals(PIP_CORNER to "bottom-right", NdsOptions.cycleCorner(emptyMap(), 1))          // default top-right
        assertEquals(PIP_CORNER to "top-left", NdsOptions.cycleCorner(emptyMap(), -1))
        assertEquals(PIP_CORNER to "top-left", NdsOptions.cycleCorner(mapOf(PIP_CORNER to "bottom-left"), 1))
        assertEquals(PIP_CORNER to "bottom-left", NdsOptions.cycleCorner(mapOf(PIP_CORNER to "top-left"), -1))
        assertEquals(PIP_CORNER to "top-right", NdsOptions.cycleCorner(mapOf(PIP_CORNER to "top-left"), 1))
        assertEquals(PIP_CORNER to "bottom-right", NdsOptions.cycleCorner(mapOf(PIP_CORNER to "top-right"), 1))
        assertEquals("Bottom left", NdsOptions.valueLabel(PIP_CORNER, "bottom-left"))
    }

    @Test fun swap_flips_the_big_screen_and_labels_it() {
        assertEquals(BIG_SCREEN to "bottom", NdsOptions.swapBigScreen(emptyMap()))
        assertEquals(BIG_SCREEN to "top", NdsOptions.swapBigScreen(mapOf(BIG_SCREEN to "bottom")))
        assertEquals("Bottom screen", NdsOptions.valueLabel(BIG_SCREEN, "bottom"))
        assertEquals("Stacked", NdsOptions.valueLabel(LAYOUT_PORTRAIT, "stacked"))
    }

    @Test fun nds_is_the_only_tag() {
        assertTrue(NdsOptions.appliesTo("NDS"))
        assertFalse(NdsOptions.appliesTo("MGBA"))
    }

    @Test fun layout_rows_say_the_fold_sets_the_layout_half_folded() {
        for (screen in listOf(null, ScreenClass.NEAR_SQUARE_TABLET)) {
            assertTrue(NdsOptions.layoutRows(screen, canHalfFold = true).all { NdsOptions.FOLD_NOTE in it.info })
        }
    }

    @Test fun layout_rows_leave_the_fold_out_where_the_device_cannot_half_fold() {
        assertTrue(NdsOptions.layoutRows(ScreenClass.REGULAR_PHONE, canHalfFold = false).none { NdsOptions.FOLD_NOTE in it.info })
        assertTrue(NdsOptions.layoutRows(ScreenClass.REGULAR_PHONE, canHalfFold = false).all { it.info.endsWith("do not apply to the DS screens.") })
    }

    @Test fun inGameShowsOnlyTheLayoutRowInUse() {
        val rows = NdsOptions.tableFor(ScreenClass.REGULAR_PHONE)
        fun layoutKeys(view: NdsView) = NdsOptions.inGameRows(rows, emptyMap(), view, ScreenClass.REGULAR_PHONE).map { it.key }.filter { it in NdsOptions.LAYOUT_KEYS }
        assertEquals(listOf(LAYOUT_LANDSCAPE), layoutKeys(NdsView(landscape = true, controllerMode = false)))
        assertEquals(listOf(LAYOUT_PORTRAIT), layoutKeys(NdsView(landscape = false, controllerMode = false)))
        assertEquals(listOf(LAYOUT_PORTRAIT_CONTROLLER), layoutKeys(NdsView(landscape = false, controllerMode = true)))
        // The rest pass through: Big Screen stays.
        assertTrue(BIG_SCREEN in NdsOptions.inGameRows(rows, emptyMap(), NdsView(true, false), ScreenClass.REGULAR_PHONE).map { it.key })
    }

    @Test fun insetRowsOnlyInPictureInPicture() {
        val rows = NdsOptions.tableFor(ScreenClass.REGULAR_PHONE)
        val inset = setOf(PIP_CORNER, PIP_SIZE, PIP_OPACITY)
        fun insetShown(values: Map<String, String>, view: NdsView) =
            NdsOptions.inGameRows(rows, values, view, ScreenClass.REGULAR_PHONE).map { it.key }.filter { it in inset }
        val landscape = NdsView(landscape = true, controllerMode = false)
        assertEquals(emptyList(), insetShown(emptyMap(), landscape))
        assertEquals(listOf(PIP_CORNER, PIP_SIZE, PIP_OPACITY), insetShown(mapOf(LAYOUT_LANDSCAPE to "pip"), landscape))
        // PiP set for landscape only: portrait shows no inset rows.
        assertEquals(emptyList(), insetShown(mapOf(LAYOUT_LANDSCAPE to "pip"), NdsView(landscape = false, controllerMode = false)))
        assertEquals(3, insetShown(mapOf(LAYOUT_PORTRAIT_CONTROLLER to "pip"), NdsView(landscape = false, controllerMode = true)).size)
    }
}
