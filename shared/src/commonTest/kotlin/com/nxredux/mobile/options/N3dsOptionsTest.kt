package com.nxredux.mobile.options

import com.nxredux.mobile.layout.ScreenClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class N3dsOptionsTest {
    @Test fun applies_to_the_3ds_tag_only() {
        assertTrue(N3dsOptions.appliesTo("3DS"))
        assertTrue(N3dsOptions.appliesTo(" 3ds "))
        assertFalse(N3dsOptions.appliesTo("NDS"))
    }

    private val phone = ScreenClass.REGULAR_PHONE
    private val fold = ScreenClass.STUBBY_PHONE
    private fun v(vararg p: Pair<String, String>) = mapOf(*p)

    @Test fun names_match_ds() {
        assertEquals(listOf("large", "stacked", "single_screen", "side_by_side"), N3dsOptions.LAYOUT_CYCLE)
        assertEquals(listOf("Large screen", "Stacked", "Single screen", "Side by side"), N3dsOptions.LAYOUT_CYCLE.map(N3dsOptions::layoutLabel))
        assertEquals("odd", N3dsOptions.layoutLabel("odd"))
        assertEquals(
            listOf("nx_3ds_layout_portrait_v2", "nx_3ds_layout_portrait_controller_v2", "nx_3ds_layout_landscape_v2"),
            N3dsOptions.layoutRows.map { it.key },
        )
        assertEquals(listOf("Layout (portrait)", "Layout (portrait, controller)", "Layout (landscape)"), N3dsOptions.layoutRows.map { it.label })
        assertTrue(N3dsOptions.layoutRows.all { it.values == N3dsOptions.LAYOUT_CYCLE })
        assertTrue(N3dsOptions.layoutRowsFor(fold).all { it.values == N3dsOptions.LAYOUT_CYCLE })   // no Fold filtering
    }

    @Test fun azahar_large_screen_dropped_reads_large() {
        val old = v("nx_3ds_layout_portrait" to "large_screen", "nx_3ds_layout_landscape" to "single_screen")
        assertEquals("large", N3dsOptions.layoutValue(old, N3dsOptions.LAYOUT_PORTRAIT))
        assertEquals("single_screen", N3dsOptions.layoutValue(old, N3dsOptions.LAYOUT_LANDSCAPE))
        assertEquals("large", N3dsOptions.appLayoutFor(old, false, false, phone))
        assertNull(N3dsOptions.layoutValue(v("nx_3ds_layout_portrait" to "bogus"), N3dsOptions.LAYOUT_PORTRAIT))
        assertNull(N3dsOptions.layoutValue(v(N3dsOptions.LAYOUT_PORTRAIT to "bogus"), N3dsOptions.LAYOUT_PORTRAIT))
        assertEquals("stacked", N3dsOptions.layoutValue(v(N3dsOptions.LAYOUT_PORTRAIT to "stacked", "nx_3ds_layout_portrait" to "single_screen"), N3dsOptions.LAYOUT_PORTRAIT))
    }

    @Test fun old_default_untouched_reads_no_pick() {
        val old = v("nx_3ds_layout_portrait" to "default", "nx_3ds_layout_portrait_controller" to "default", "nx_3ds_layout_landscape" to "default")
        for (key in N3dsOptions.NO_PICK_KEYS) assertNull(N3dsOptions.layoutValue(old, key))
        assertEquals("side_by_side", N3dsOptions.appLayoutFor(old, true, false, phone))
        assertEquals("large", N3dsOptions.appLayoutFor(old, true, false, fold))
        // The frontend config read leaves the unpicked keys out and keeps the picks, as for the DS.
        val cfg = FrontendOptions.fromConfig(v("nx_3ds_layout_portrait" to "default", "nx_3ds_layout_landscape" to "side_by_side"), "3DS")
        assertFalse(N3dsOptions.LAYOUT_PORTRAIT in cfg)
        assertEquals("side_by_side", cfg[N3dsOptions.LAYOUT_LANDSCAPE])
        assertFalse(N3dsOptions.LAYOUT_PORTRAIT in FrontendOptions.defaults("3DS"))
    }

    @Test fun tabletop_forces_stacked_equal() {
        val values = v(N3dsOptions.LAYOUT_PORTRAIT to "large")
        assertEquals("default", N3dsOptions.coreLayoutFor(values, false, false, phone, true))
        assertEquals("1", N3dsOptions.stackedSmallProportion(values, false, false, phone, true))
        assertEquals("default", N3dsOptions.coreLayoutFor(v(), true, false, fold, true))   // an unpicked Fold landscape is Large
        assertEquals("1", N3dsOptions.stackedSmallProportion(v(), true, false, fold, true))
    }

    @Test fun tabletop_ignores_saved_side_by_side() {
        for (saved in listOf("side_by_side", "single_screen")) {
            val values = v(N3dsOptions.LAYOUT_PORTRAIT to saved, N3dsOptions.LAYOUT_LANDSCAPE to saved)
            assertEquals("default", N3dsOptions.coreLayoutFor(values, false, false, phone, true))
            assertEquals("default", N3dsOptions.coreLayoutFor(values, true, false, phone, true))
            assertEquals("1", N3dsOptions.stackedSmallProportion(values, true, false, phone, true))
        }
    }

    @Test fun flat_keeps_saved_layout() {
        assertEquals("side_by_side", N3dsOptions.coreLayoutFor(v(N3dsOptions.LAYOUT_PORTRAIT to "side_by_side"), false, false, phone, false))
        assertEquals("2", N3dsOptions.stackedSmallProportion(v(N3dsOptions.LAYOUT_PORTRAIT to "large"), false, false, phone, false))
    }

    @Test fun large_is_core_default_with_half_touch() {
        val values = v(N3dsOptions.LAYOUT_PORTRAIT to "large")
        assertEquals("default", N3dsOptions.coreLayoutFor(values, false, false, phone, false))
        assertEquals("2", N3dsOptions.stackedSmallProportion(values, false, false, phone, false))
        assertEquals("single_screen", N3dsOptions.coreLayoutFor(v(N3dsOptions.LAYOUT_PORTRAIT to "single_screen"), false, false, phone, false))
        assertEquals("side_by_side", N3dsOptions.coreLayoutFor(v(N3dsOptions.LAYOUT_PORTRAIT to "side_by_side"), false, false, phone, false))
        assertEquals("1", N3dsOptions.stackedSmallProportion(v(N3dsOptions.LAYOUT_PORTRAIT to "single_screen"), false, false, phone, false))
    }

    @Test fun stacked_same_size() {
        val values = v(N3dsOptions.LAYOUT_LANDSCAPE to "stacked")
        assertEquals("default", N3dsOptions.coreLayoutFor(values, true, false, phone, false))
        assertEquals("1", N3dsOptions.stackedSmallProportion(values, true, false, phone, false))
    }

    @Test fun defaults_per_screen() {
        for (screen in ScreenClass.entries) {
            assertEquals("stacked", N3dsOptions.appLayoutFor(emptyMap(), false, false, screen))
            assertEquals("stacked", N3dsOptions.appLayoutFor(emptyMap(), false, true, screen))
        }
        assertEquals("side_by_side", N3dsOptions.appLayoutFor(emptyMap(), true, false, phone))
        assertEquals("side_by_side", N3dsOptions.appLayoutFor(emptyMap(), true, false, ScreenClass.REGULAR_PHONE))
        for (screen in listOf(ScreenClass.STUBBY_PHONE, ScreenClass.WIDE_TABLET, ScreenClass.NEAR_SQUARE_TABLET)) {
            assertEquals("large", N3dsOptions.appLayoutFor(emptyMap(), true, false, screen))
        }
        assertEquals("large", N3dsOptions.appLayoutFor(emptyMap(), true, true, fold))   // landscape has one row
        assertEquals(listOf("stacked", "stacked", "large"), N3dsOptions.layoutRowsFor(fold).map { it.default })
        assertEquals(listOf("stacked", "stacked", "side_by_side"), N3dsOptions.layoutRowsFor(phone).map { it.default })
    }

    @Test fun cycle_wraps() {
        fun next(value: String?, d: Int = 1, screen: ScreenClass = phone) =
            N3dsOptions.cycleLayout(value?.let { v(N3dsOptions.LAYOUT_PORTRAIT to it) } ?: emptyMap(), false, false, d, screen)
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT to "stacked", next("large"))
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT to "single_screen", next("stacked"))
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT to "side_by_side", next("single_screen"))
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT to "large", next("side_by_side"))
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT to "side_by_side", next("large", -1))
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT to "single_screen", next(null))   // unset: from the default (stacked)
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT to "single_screen", next("bogus"))
        // The row in use is cycled: landscape has one row, controller mode its own in portrait.
        val values = v(N3dsOptions.LAYOUT_LANDSCAPE to "single_screen")
        assertEquals(N3dsOptions.LAYOUT_LANDSCAPE to "side_by_side", N3dsOptions.cycleLayout(values, true, true, 1, phone))
        assertEquals(N3dsOptions.LAYOUT_PORTRAIT_CONTROLLER to "single_screen", N3dsOptions.cycleLayout(values, false, true, 1, phone))
    }

    @Test fun layoutRowsAreNotVisual() {
        // Azahar composes the 3DS layout itself, so the paused frame never shows a change: no Adjust strip.
        assertTrue(N3dsOptions.layoutRowsFor(null).all { it.visual == false })
        // The DS layout rows are drawn by the app, so their strip works.
        assertTrue(NdsOptions.layoutRows(null).all { it.visual })
    }

    @Test fun fill_proportion_fills_the_height_left_under_the_top() {
        // The unfolded Fold, portrait, band hidden: the top 4.62x fills the width, the bottom takes the 650 px left.
        assertEquals(1.71f, N3dsOptions.fillProportion(1848f, 1759f))
        // Too short for the top and a half-size bottom: today's half size (the picture shrinks to fit).
        assertEquals(2f, N3dsOptions.fillProportion(1400f, 1080f))
        assertEquals(2f, N3dsOptions.fillProportion(1080f, 900f))
        // Room for both at the top's scale: the bottom as big as the top, never bigger.
        assertEquals(1f, N3dsOptions.fillProportion(1848f, 3000f))
        assertEquals(1f, N3dsOptions.fillProportion(1080f, 1400f))
        // Rounded to two decimals: near-equal spaces give the same value.
        assertEquals(N3dsOptions.fillProportion(1848f, 1759f), N3dsOptions.fillProportion(1848f, 1758.9f))
        assertEquals(2f, N3dsOptions.fillProportion(0f, 0f))
    }

    @Test fun portrait_large_sends_the_fill_proportion() {
        val large = v(N3dsOptions.LAYOUT_PORTRAIT to "large", N3dsOptions.LAYOUT_PORTRAIT_CONTROLLER to "large", N3dsOptions.LAYOUT_LANDSCAPE to "large")
        val fold = PxRect(0f, 0f, 1848f, 1759f)
        assertEquals("1.71", N3dsOptions.stackedSmallProportion(large, false, false, phone, false, fold))
        assertEquals("1.71", N3dsOptions.stackedSmallProportion(large, false, true, phone, false, fold))   // the controller row
        assertEquals("2", N3dsOptions.stackedSmallProportion(large, false, false, phone, false, PxRect(0f, 0f, 1400f, 1080f)))
        assertEquals("1", N3dsOptions.stackedSmallProportion(large, false, false, phone, false, PxRect(0f, 0f, 1848f, 3000f)))
        assertEquals("2", N3dsOptions.stackedSmallProportion(large, false, false, phone, false, null))   // no space yet
        assertEquals("2", N3dsOptions.stackedSmallProportion(large, true, false, phone, false, fold))    // landscape
        assertEquals("1", N3dsOptions.stackedSmallProportion(large, false, false, phone, true, fold))    // half-folded
        assertEquals("1", N3dsOptions.stackedSmallProportion(v(N3dsOptions.LAYOUT_PORTRAIT to "stacked"), false, false, phone, false, fold))
        assertEquals("1", N3dsOptions.stackedSmallProportion(v(N3dsOptions.LAYOUT_PORTRAIT to "single_screen"), false, false, phone, false, fold))
    }

    @Test fun deadzone_reads_the_core_percentage() {
        assertEquals(0.15f, N3dsOptions.deadzone(null))
        assertEquals(0.15f, N3dsOptions.deadzone("x"))
        assertEquals(0.35f, N3dsOptions.deadzone("35"))
        assertEquals(0f, N3dsOptions.deadzone("0"))
    }

    @Test fun layout_rows_say_the_fold_sets_the_layout_half_folded() {
        assertTrue(N3dsOptions.layoutRowsFor(ScreenClass.NEAR_SQUARE_TABLET, canHalfFold = true).all { NdsOptions.FOLD_NOTE in it.info })
        assertTrue(N3dsOptions.layoutRowsFor(ScreenClass.NEAR_SQUARE_TABLET, canHalfFold = false).none { NdsOptions.FOLD_NOTE in it.info })
    }

    @Test fun resolutionRows() {
        val (mode, max, scale) = N3dsOptions.resolutionRows(null)
        assertEquals(listOf("3D Resolution", "Max Scale", "Scale"), listOf(mode, max, scale).map { it.label })
        assertEquals(listOf("nx_3ds_3d_resolution", "nx_3ds_3d_max_scale", "nx_3ds_3d_scale"), listOf(mode, max, scale).map { it.key })
        assertEquals(listOf("auto", "3", "2"), listOf(mode, max, scale).map { it.default })
        assertEquals("nx_3ds_3d_resolution" to setOf("auto"), max.shownWhen)
        assertEquals("nx_3ds_3d_resolution" to setOf("performance"), scale.shownWhen)
        assertTrue(N3dsOptions.resolutionRows(null).none { it.visual })
        assertEquals((1..10).map { it.toString() }, max.values)
        assertEquals((1..10).map { "${it}x" }, max.valueLabels)
        assertTrue("3DS screen" in max.info && "3DS screen" in scale.info)
    }

    @Test fun resolutionRowsFollowTheDisplay() {
        val (_, max, scale) = N3dsOptions.resolutionRows(6)
        assertEquals((1..6).map { it.toString() }, max.values)
        assertEquals((1..3).map { it.toString() }, scale.values)
    }
}
