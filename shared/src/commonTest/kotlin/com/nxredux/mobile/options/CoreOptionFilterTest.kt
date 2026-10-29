package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreOptionFilterTest {
    private fun opt(
        key: String, value: String = "a", label: String = key, info: String = "", category: String = "",
        values: List<String> = listOf("a", "b", "c"), visible: Boolean = true, labelCategorized: String = "",
    ) = CoreOption(
        key = key, value = value, label = label, labelCategorized = labelCategorized, info = info,
        category = category, categoryLabel = category.uppercase(), categoryInfo = "$category info",
        values = values, valueLabels = values.map { it.uppercase() }, visible = visible,
    )

    @Test fun restart_or_reload_in_label_or_info_is_detected_case_insensitively() {
        assertTrue(CoreOptionFilter.requiresRestart(opt("x", label = "Upscaling factor (Restart)")))
        assertTrue(CoreOptionFilter.requiresRestart(opt("x", info = "Requires a content RELOAD.")))
        assertTrue(CoreOptionFilter.requiresRestart(opt("x", labelCategorized = "Mode (restart)")))
        assertFalse(CoreOptionFilter.requiresRestart(opt("x", label = "Aspect Ratio", info = "Changes the shape.")))
    }

    @Test fun live_drops_hidden_locked_and_restart_options_keeping_order() {
        val all = listOf(opt("a"), opt("b", visible = false), opt("c", label = "c (restart)"), opt("d"), opt("e"))
        assertEquals(listOf("a", "e"), CoreOptionFilter.live(all, locked = setOf("d")).map { it.key })
    }

    @Test fun apply_on_restart_options_stay_visible_despite_a_restart_note() {
        val note = "Changing this may require the frontend's video driver to be restarted."
        val renderMode = opt("melonds_render_mode", info = note)
        val other = opt("melonds_console_mode", info = note)
        assertFalse(CoreOptionFilter.requiresRestart(renderMode))
        assertTrue(CoreOptionFilter.requiresRestart(other))
        assertEquals(listOf("melonds_render_mode"), CoreOptionFilter.live(listOf(renderMode, other), locked = emptySet()).map { it.key })
    }

    @Test fun categories_follow_first_appearance_and_skip_uncategorized() {
        val live = listOf(opt("1", category = "video"), opt("2"), opt("3", category = "audio"), opt("4", category = "video"))
        assertEquals(
            listOf(OptionCategory("video", "VIDEO", "video info"), OptionCategory("audio", "AUDIO", "audio info")),
            CoreOptionFilter.categories(live),
        )
        assertEquals(listOf("2"), CoreOptionFilter.uncategorized(live).map { it.key })
        assertEquals(listOf("1", "4"), CoreOptionFilter.inCategory(live, "video").map { it.key })
    }

    @Test fun category_label_falls_back_to_key() {
        val o = opt("1", category = "sys").copy(categoryLabel = "")
        assertEquals("sys", CoreOptionFilter.categories(listOf(o)).single().label)
    }

    @Test fun row_label_prefers_categorized_label_inside_a_category() {
        val o = opt("k", label = "(GLideN64) Bilinear", labelCategorized = "Bilinear")
        assertEquals("Bilinear", CoreOptionFilter.rowLabel(o, inCategory = true))
        assertEquals("(GLideN64) Bilinear", CoreOptionFilter.rowLabel(o, inCategory = false))
        assertEquals("k", CoreOptionFilter.rowLabel(opt("k", label = ""), inCategory = false))
    }

    @Test fun value_label_uses_the_matching_label_or_the_raw_value() {
        assertEquals("B", CoreOptionFilter.valueLabel(opt("k", value = "b")))
        assertEquals("zzz", CoreOptionFilter.valueLabel(opt("k", value = "zzz")))
        assertEquals("b", CoreOptionFilter.valueLabel(opt("k", value = "b").copy(valueLabels = listOf("A", "", "C"))))
    }

    @Test fun cycle_wraps_both_ways() {
        assertEquals("b", CoreOptionFilter.cycle(opt("k", value = "a"), 1))
        assertEquals("a", CoreOptionFilter.cycle(opt("k", value = "c"), 1))
        assertEquals("c", CoreOptionFilter.cycle(opt("k", value = "a"), -1))
    }

    @Test fun cycle_from_an_unknown_value_goes_to_first_or_last() {
        assertEquals("a", CoreOptionFilter.cycle(opt("k", value = "zzz"), 1))
        assertEquals("c", CoreOptionFilter.cycle(opt("k", value = "zzz"), -1))
    }

    @Test fun cycle_without_choices_is_null() {
        assertNull(CoreOptionFilter.cycle(opt("k", values = emptyList()), 1))
    }

    @Test fun pending_values_replace_the_cores() {
        val all = listOf(opt("melonds_render_mode", value = "software"), opt("other", value = "b"))
        val shown = CoreOptionFilter.withPending(all, mapOf("melonds_render_mode" to "opengl", "absent" to "x"))
        assertEquals(listOf("opengl", "b"), shown.map { it.value })
        assertEquals(listOf("melonds_render_mode", "other"), shown.map { it.key })
        assertEquals(all, CoreOptionFilter.withPending(all, emptyMap()))
    }

    private val hiddenB = mapOf("k" to setOf("b"))

    @Test fun hidden_values_are_marked_only_on_their_option() {
        val shown = CoreOptionFilter.withHiddenValues(listOf(opt("k"), opt("other")), hiddenB)
        assertEquals(setOf("b"), shown[0].hiddenValues)
        assertEquals(emptySet(), shown[1].hiddenValues)
        assertEquals(listOf("a", "c"), CoreOptionFilter.visibleValues(shown[0]))
        assertEquals(listOf("a", "b", "c"), CoreOptionFilter.visibleValues(shown[1]))
    }

    @Test fun without_hidden_values_nothing_changes() {
        val all = listOf(opt("k"), opt("other"))
        assertEquals(all, CoreOptionFilter.withHiddenValues(all, emptyMap()))
        assertEquals(all, CoreOptionFilter.withHiddenValues(all, mapOf("absent" to setOf("a"))))
    }

    @Test fun cycle_skips_hidden_values_both_ways() {
        val k = CoreOptionFilter.withHiddenValues(listOf(opt("k", value = "a")), hiddenB).single()
        assertEquals("c", CoreOptionFilter.cycle(k, 1))
        assertEquals("c", CoreOptionFilter.cycle(k, -1))
        assertEquals("a", CoreOptionFilter.cycle(k.copy(value = "c"), 1))
        assertEquals("a", CoreOptionFilter.cycle(k.copy(value = "c"), -1))
    }

    @Test fun a_hidden_current_value_keeps_its_label_and_cycles_to_its_visible_neighbours() {
        // The core runs the hidden value (its own default, not overridden): the row says so, and a step leaves it.
        val k = CoreOptionFilter.withHiddenValues(listOf(opt("k", value = "b")), hiddenB).single()
        assertEquals("B", CoreOptionFilter.valueLabel(k))
        assertEquals("c", CoreOptionFilter.cycle(k, 1))
        assertEquals("a", CoreOptionFilter.cycle(k, -1))
        val last = CoreOptionFilter.withHiddenValues(listOf(opt("k", value = "c")), mapOf("k" to setOf("c"))).single()
        assertEquals("a", CoreOptionFilter.cycle(last, 1))
        assertEquals("b", CoreOptionFilter.cycle(last, -1))
    }

    @Test fun cycle_with_every_value_hidden_is_null() {
        val k = CoreOptionFilter.withHiddenValues(listOf(opt("k")), mapOf("k" to setOf("a", "b", "c"))).single()
        assertNull(CoreOptionFilter.cycle(k, 1))
    }

    @Test fun flycast_hides_per_pixel_alpha_sorting() {
        val hidden = com.nxredux.mobile.runner.CoreRegistry.forTag("DC")!!.hiddenValues
        assertEquals(mapOf("reicast_alpha_sorting" to setOf("per-pixel (accurate)")), hidden)
        val sorting = CoreOption(
            "reicast_alpha_sorting", "per-strip (fast, least accurate)",
            values = listOf("per-strip (fast, least accurate)", "per-triangle (normal)", "per-pixel (accurate)"),
        )
        val shown = CoreOptionFilter.withHiddenValues(listOf(sorting), hidden).single()
        assertEquals(listOf("per-strip (fast, least accurate)", "per-triangle (normal)"), CoreOptionFilter.visibleValues(shown))
        assertEquals("per-strip (fast, least accurate)", CoreOptionFilter.cycle(shown.copy(value = "per-triangle (normal)"), 1))
        // The others: renderers the runner can't serve (Vulkan is reached only through Graphics API) (EmulatorSettingsTest pins them),
        // and N64's stale VRAM limit "0" (N64HiresPack.VRAM_NONE).
        assertEquals(
            setOf("ppsspp_backend", "swanstation_GPU_Renderer", "citra_graphics_api", "dolphin_renderer", "mupen64plus-MaxHiResTxVramLimit"),
            com.nxredux.mobile.runner.CoreRegistry.all.filter { it.id != "flycast" }.flatMap { it.hiddenValues.keys }.toSet(),
        )
    }

    @Test fun offline_keeps_restart_options_and_drops_invisible_and_locked() {
        val restart = CoreOption("a", "1", label = "Res (restart)", values = listOf("1", "2"))
        val hidden = CoreOption("b", "1", visible = false, values = listOf("1"))
        val locked = CoreOption("c", "1", values = listOf("1"))
        val plain = CoreOption("d", "1", values = listOf("1"))
        assertEquals(listOf("a", "d"), CoreOptionFilter.offline(listOf(restart, hidden, locked, plain), setOf("c")).map { it.key })
    }
}
