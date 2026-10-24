package com.nxredux.mobile.options

import com.nxredux.mobile.options.NdsOptions.BIG_SCREEN
import com.nxredux.mobile.options.NdsOptions.LAYOUT_LANDSCAPE
import com.nxredux.mobile.options.NdsOptions.LAYOUT_PORTRAIT
import com.nxredux.mobile.options.NdsOptions.LAYOUT_PORTRAIT_CONTROLLER
import com.nxredux.mobile.options.NdsOptions.PIP_CORNER
import com.nxredux.mobile.options.NdsOptions.PIP_OPACITY
import com.nxredux.mobile.options.NdsOptions.PIP_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NdsOptionsTest {
    @Test fun keys_values_and_defaults_follow_the_spec() {
        assertEquals(listOf("nx_nds_layout_portrait_v2", "nx_nds_layout_portrait_controller", "nx_nds_layout_landscape", "nx_nds_big_screen", "nx_nds_pip_corner", "nx_nds_pip_size",
            "nx_nds_pip_opacity"),
            NdsOptions.table.map { it.key })
        assertEquals(
            mapOf(LAYOUT_PORTRAIT to "auto", LAYOUT_PORTRAIT_CONTROLLER to "stacked", LAYOUT_LANDSCAPE to "stacked", BIG_SCREEN to "top", PIP_CORNER to "top-right", PIP_SIZE to "medium",
                PIP_OPACITY to "100"),
            NdsOptions.table.associate { it.key to it.default },
        )
        assertEquals(listOf("auto", "pip", "stacked", "single", "large", "side-by-side"), FrontendOptions.option(LAYOUT_PORTRAIT)!!.values)
        assertEquals(listOf("auto", "pip", "stacked", "single", "large", "side-by-side"), FrontendOptions.option(LAYOUT_PORTRAIT_CONTROLLER)!!.values)
        assertEquals(NdsPortraitLayout.AUTO, NdsSettings().portrait)
        assertTrue("Auto: stacked when both screens fit the width, else picture in picture." in FrontendOptions.option(LAYOUT_PORTRAIT)!!.info)
        assertEquals("Layout (portrait, controller)", FrontendOptions.option(LAYOUT_PORTRAIT_CONTROLLER)!!.label)
        assertEquals(listOf("side-by-side", "single", "pip", "large", "stacked"), FrontendOptions.option(LAYOUT_LANDSCAPE)!!.values)
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
        assertTrue(NdsOptions.table.all { it.visual && it.valueLabels.size == it.values.size })
    }

    @Test fun only_nds_gets_the_ds_rows() {
        val pad = listOf(BottomScreenOptions.row, PadOptions.layoutRow) + SpeedOptions.table
        val dsPad = listOf(BottomScreenOptions.row, PadOptions.portraitPadRow, PadOptions.layoutRow) + SpeedOptions.table
        assertEquals(NdsOptions.table + dsPad + FrontendOptions.table, FrontendOptions.allFor("NDS"))
        assertEquals(NdsOptions.table + dsPad + FrontendOptions.table, FrontendOptions.allFor(" nds "))
        assertEquals(pad + FrontendOptions.table, FrontendOptions.allFor("GBA"))
        assertEquals(pad + FrontendOptions.table, FrontendOptions.allFor(""))
        assertEquals("medium", FrontendOptions.fromConfig(emptyMap(), "NDS")[PIP_SIZE])
        assertEquals("large", FrontendOptions.fromConfig(mapOf(PIP_SIZE to "large"), "NDS")[PIP_SIZE])
        assertFalse(PIP_SIZE in FrontendOptions.fromConfig(mapOf(PIP_SIZE to "large"), "GBA"))
        assertFalse(PIP_SIZE in FrontendOptions.defaults())
    }

    @Test fun stored_values_parse_and_unknown_ones_fall_back() {
        val s = NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "stacked", BIG_SCREEN to "bottom", PIP_CORNER to "top-left", PIP_SIZE to "large"))
        assertEquals(NdsSettings(NdsPortraitLayout.STACKED, NdsLandscapeLayout.STACKED, NdsScreen.BOTTOM, PipCorner.TOP_LEFT, PipSize.LARGE), s)
        assertEquals(NdsSettings(), NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "Stacked", PIP_SIZE to "huge", BIG_SCREEN to "")))
        assertEquals(NdsSettings(), NdsSettings.parse(emptyMap()))
        assertEquals(PipOpacity.THREE_QUARTERS, NdsSettings.parse(mapOf(PIP_OPACITY to "75")).opacity)
        assertEquals(PipOpacity.HALF, NdsSettings.parse(mapOf(PIP_OPACITY to "50")).opacity)
        assertEquals(PipOpacity.FULL, NdsSettings.parse(mapOf(PIP_OPACITY to "60")).opacity)
        assertEquals(PipOpacity.FULL, NdsSettings().opacity)
        assertEquals(NdsScreen.BOTTOM, NdsScreen.TOP.other)
    }

    @Test fun the_controller_portrait_layout_parses_and_defaults_to_stacked() {
        assertEquals(NdsPortraitLayout.STACKED, NdsSettings().controller)
        assertEquals(NdsPortraitLayout.STACKED, NdsSettings.parse(emptyMap()).controller)
        assertEquals(NdsPortraitLayout.SINGLE, NdsSettings.parse(mapOf(LAYOUT_PORTRAIT_CONTROLLER to "single")).controller)
        assertEquals(NdsPortraitLayout.STACKED, NdsSettings.parse(mapOf(LAYOUT_PORTRAIT_CONTROLLER to "huge")).controller)
        val s = NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "single", LAYOUT_PORTRAIT_CONTROLLER to "pip"))
        assertEquals(NdsPortraitLayout.SINGLE, s.portrait)
        assertEquals(NdsPortraitLayout.SINGLE, s.portraitLayout(controllerMode = false))
        assertEquals(NdsPortraitLayout.PIP, s.portraitLayout(controllerMode = true))
    }

    @Test fun rows_cycle_and_show_labels() {
        assertEquals("side-by-side", FrontendOptions.cycle(LAYOUT_PORTRAIT, "auto", -1, emptyList()))
        assertEquals("auto", FrontendOptions.cycle(LAYOUT_PORTRAIT, "pip", -1, emptyList()))
        assertEquals("Picture in picture", FrontendOptions.option(LAYOUT_PORTRAIT)!!.labelOf("pip"))
        assertEquals("Auto", FrontendOptions.option(LAYOUT_PORTRAIT)!!.labelOf("auto"))
        assertEquals("weird", FrontendOptions.option(LAYOUT_PORTRAIT)!!.labelOf("weird"))
        assertEquals("Aspect", FrontendOptions.option(FrontendOptions.SCALING)!!.labelOf("Aspect"))
    }

    // Game areas (above the 5.8 cm pad band) measured on real phones.
    private val flip = 1080f to 1607f
    private val foldCover = 1248f to 995f

    private fun cycle(values: Map<String, String>, area: Pair<Float, Float>, controllerMode: Boolean = false, delta: Int = 1, landscape: Boolean = false) =
        NdsOptions.cycleLayout(values, landscape, controllerMode, delta, area.first, area.second)

    @Test fun auto_stacks_when_both_screens_are_at_least_90_percent_of_the_width() {
        assertEquals(NdsPortraitLayout.STACKED, NdsPortraitLayout.AUTO.resolve(1080f, 1607f))    // Flip: 1071 of 1080 wide
        assertEquals(NdsPortraitLayout.PIP, NdsPortraitLayout.AUTO.resolve(1080f, 1441f))        // 961 of 1080: 89%
        assertEquals(NdsPortraitLayout.PIP, NdsPortraitLayout.AUTO.resolve(1248f, 995f))         // Fold cover
        assertEquals(NdsPortraitLayout.STACKED, NdsPortraitLayout.AUTO.resolve(1000f, 1350f))    // exactly 90%
        assertEquals(NdsPortraitLayout.PIP, NdsPortraitLayout.AUTO.resolve(1000f, 1349f))
        assertEquals(NdsPortraitLayout.STACKED, NdsPortraitLayout.AUTO.resolve(1000f, 3000f))    // height to spare
        assertEquals(NdsPortraitLayout.PIP, NdsPortraitLayout.AUTO.resolve(0f, 1500f))
        assertEquals(NdsPortraitLayout.PIP, NdsPortraitLayout.AUTO.resolve(1080f, 0f))
        assertEquals(NdsPortraitLayout.PIP, NdsPortraitLayout.AUTO.resolve(0f, 0f))
        // A concrete layout is itself whatever the area.
        for (p in NdsOptions.CONCRETE_PORTRAIT) for ((w, h) in listOf(flip, foldCover, 0f to 0f)) assertEquals(p, p.resolve(w, h))
        assertEquals(listOf(NdsPortraitLayout.PIP, NdsPortraitLayout.STACKED, NdsPortraitLayout.SINGLE, NdsPortraitLayout.LARGE, NdsPortraitLayout.SIDE_BY_SIDE), NdsOptions.CONCRETE_PORTRAIT)
    }

    @Test fun the_resolved_portrait_layout_applies_auto_only_where_it_is_stored() {
        assertEquals(NdsPortraitLayout.STACKED, NdsSettings().portraitLayout(controllerMode = false, flip.first, flip.second))
        assertEquals(NdsPortraitLayout.PIP, NdsSettings().portraitLayout(controllerMode = false, foldCover.first, foldCover.second))
        val pinned = NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "pip"))
        assertEquals(NdsPortraitLayout.PIP, pinned.portraitLayout(controllerMode = false, flip.first, flip.second))
        val single = NdsSettings.parse(mapOf(LAYOUT_PORTRAIT to "single"))
        assertEquals(NdsPortraitLayout.SINGLE, single.portraitLayout(controllerMode = false, foldCover.first, foldCover.second))
        // The controller row keeps stacked by default and resolves auto the same way when chosen.
        assertEquals(NdsPortraitLayout.STACKED, NdsSettings().portraitLayout(controllerMode = true, foldCover.first, foldCover.second))
        val auto = NdsSettings.parse(mapOf(LAYOUT_PORTRAIT_CONTROLLER to "auto"))
        assertEquals(NdsPortraitLayout.AUTO, auto.controller)
        assertEquals(NdsPortraitLayout.PIP, auto.portraitLayout(controllerMode = true, foldCover.first, foldCover.second))
    }

    @Test fun layout_cycles_in_portrait_and_wraps() {
        // Auto (the default) steps from what it resolved to, among the concrete layouts only.
        assertEquals(LAYOUT_PORTRAIT to "single", cycle(emptyMap(), flip))            // stacked -> single
        assertEquals(LAYOUT_PORTRAIT to "pip", cycle(emptyMap(), flip, delta = -1))   // stacked -> pip
        assertEquals(LAYOUT_PORTRAIT to "stacked", cycle(emptyMap(), foldCover))      // pip -> stacked
        assertEquals(LAYOUT_PORTRAIT to "side-by-side", cycle(emptyMap(), foldCover, delta = -1))
        assertEquals(LAYOUT_PORTRAIT to "stacked", cycle(mapOf(LAYOUT_PORTRAIT to "auto"), foldCover))
        assertEquals(LAYOUT_PORTRAIT to "large", cycle(mapOf(LAYOUT_PORTRAIT to "single"), flip))
        assertEquals(LAYOUT_PORTRAIT to "side-by-side", cycle(mapOf(LAYOUT_PORTRAIT to "large"), flip))
        assertEquals(LAYOUT_PORTRAIT to "pip", cycle(mapOf(LAYOUT_PORTRAIT to "side-by-side"), flip))   // wraps
        assertEquals(LAYOUT_PORTRAIT to "stacked", cycle(mapOf(LAYOUT_PORTRAIT to "pip"), flip))
    }

    @Test fun layout_cycles_in_landscape_and_wraps() {
        // Stacked (the default) is last, so the next wraps to side by side.
        assertEquals(LAYOUT_LANDSCAPE to "side-by-side", NdsOptions.cycleLayout(emptyMap(), landscape = true, controllerMode = false, delta = 1, 2520f, 1080f))
        assertEquals(LAYOUT_LANDSCAPE to "large", NdsOptions.cycleLayout(emptyMap(), landscape = true, controllerMode = false, delta = -1, 2520f, 1080f))
        assertEquals(LAYOUT_LANDSCAPE to "pip", cycle(mapOf(LAYOUT_LANDSCAPE to "single"), flip, landscape = true))
        assertEquals(LAYOUT_LANDSCAPE to "large", cycle(mapOf(LAYOUT_LANDSCAPE to "pip"), flip, landscape = true))
        assertEquals(LAYOUT_LANDSCAPE to "side-by-side", cycle(mapOf(LAYOUT_LANDSCAPE to "stacked"), flip, landscape = true))
        // The landscape key serves both pad and controller modes; portrait keys are untouched.
        assertEquals(LAYOUT_LANDSCAPE to "side-by-side", cycle(mapOf(LAYOUT_PORTRAIT to "pip"), flip, controllerMode = true, landscape = true))
        assertEquals("Single screen", NdsOptions.valueLabel(LAYOUT_LANDSCAPE, "single"))
        assertEquals("Picture in picture", NdsOptions.valueLabel(LAYOUT_LANDSCAPE, "pip"))
    }

    @Test fun layout_cycle_writes_only_the_key_in_use() {
        val values = mapOf(LAYOUT_PORTRAIT to "pip", LAYOUT_PORTRAIT_CONTROLLER to "single")
        assertEquals(LAYOUT_PORTRAIT_CONTROLLER to "large", cycle(values, flip, controllerMode = true))
        assertEquals(LAYOUT_PORTRAIT_CONTROLLER to "stacked", cycle(values, flip, controllerMode = true, delta = -1))
        assertEquals(LAYOUT_PORTRAIT to "stacked", cycle(values, flip))
        // The controller key defaults to stacked.
        assertEquals(LAYOUT_PORTRAIT_CONTROLLER to "single", cycle(emptyMap(), foldCover, controllerMode = true))
        assertEquals(LAYOUT_PORTRAIT_CONTROLLER to "stacked", cycle(mapOf(LAYOUT_PORTRAIT_CONTROLLER to "auto"), foldCover, controllerMode = true))
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
}
