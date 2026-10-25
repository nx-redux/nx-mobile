package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreConfigTest {
    @Test fun parses_entries_locks_and_other_lines() {
        val f = CoreConfig.parse("a = 1\n-b = two words\n\nbind Up = UP\n# note\n")
        assertEquals(mapOf("a" to "1", "b" to "two words"), f.values)
        assertEquals(setOf("b"), f.locked)
        assertEquals(
            listOf(ConfigLine.Entry("a", "1", false), ConfigLine.Entry("b", "two words", true), ConfigLine.Other(""),
                ConfigLine.Other("bind Up = UP"), ConfigLine.Other("# note")),
            f.lines,
        )
    }

    @Test fun handles_crlf_and_values_containing_the_separator() {
        val f = CoreConfig.parse("a = x = y\r\nb = 2\r\n")
        assertEquals(mapOf("a" to "x = y", "b" to "2"), f.values)
    }

    @Test fun first_duplicate_wins_like_minarch() {
        assertEquals(mapOf("a" to "1"), CoreConfig.parse("a = 1\na = 2\n").values)
        val lockedFirst = CoreConfig.parse("-x = a\nx = b\n")
        assertEquals("a", lockedFirst.values["x"])
        assertEquals(setOf("x"), lockedFirst.locked)
        assertEquals(emptySet(), CoreConfig.parse("x = a\n-x = b\n").locked)
    }

    @Test fun render_updates_in_place_appends_new_keys_and_keeps_other_lines() {
        val existing = CoreConfig.parse("x = old\nbind Up = UP\ny = keep\n")
        val out = CoreConfig.render(existing, listOf("x" to "new", "z" to "added"))
        assertEquals("x = new\nbind Up = UP\ny = keep\nz = added\n", out)
    }

    @Test fun render_keeps_locked_values_and_drops_duplicate_lines() {
        val existing = CoreConfig.parse("-x = locked\na = 1\na = 2\n")
        val out = CoreConfig.render(existing, listOf("x" to "changed", "a" to "3"))
        assertEquals("-x = locked\na = 3\n", out)
        assertEquals(mapOf("x" to "locked", "a" to "3"), CoreConfig.parse(out).values)
    }

    @Test fun render_without_an_existing_file_writes_values_in_order() {
        assertEquals("b = 2\na = 1\n", CoreConfig.render(null, listOf("b" to "2", "a" to "1")))
    }

    @Test fun resolve_game_file_beats_console_and_does_not_merge() {
        val console = CoreConfig.parse("a = console\nb = console\n")
        val game = CoreConfig.parse("a = game\n")
        val r = CoreConfig.resolve(mapOf("a" to "d", "c" to "d"), console, game)
        assertEquals(ConfigSource.GAME, r.source)
        assertEquals(mapOf("a" to "game", "c" to "d"), r.values)
    }

    @Test fun resolve_uses_console_when_no_game_file() {
        val r = CoreConfig.resolve(mapOf("a" to "d"), CoreConfig.parse("-a = console\n"), null)
        assertEquals(ConfigSource.CONSOLE, r.source)
        assertEquals(mapOf("a" to "console"), r.values)
        assertEquals(setOf("a"), r.locked)
    }

    @Test fun resolve_falls_back_to_defaults() {
        val r = CoreConfig.resolve(mapOf("a" to "d"), null, null)
        assertEquals(ConfigSource.NONE, r.source)
        assertEquals(mapOf("a" to "d"), r.values)
        assertEquals(emptySet(), r.locked)
    }

    @Test fun frontend_keys_round_trip_with_core_options() {
        val text = CoreConfig.render(null, listOf(
            "gambatte_gb_palette" to "GBC - Blue",
            FrontendOptions.SCALING to "Native",
            FrontendOptions.OVERLAY to "Aspect - LCD Grid.png",
        ))
        val values = CoreConfig.parse(text).values
        assertEquals("Native", FrontendOptions.fromConfig(values)[FrontendOptions.SCALING])
        assertEquals("Aspect - LCD Grid.png", FrontendOptions.fromConfig(values)[FrontendOptions.OVERLAY])
        assertEquals(mapOf("gambatte_gb_palette" to "GBC - Blue"), FrontendOptions.coreValues(values))
    }

    @Test fun save_keeps_unused_nx_keys() {
        val existing = CoreConfig.parse("nx_cpu_speed = Powersave\nnx_screen_scaling_v2 = Aspect\n")
        val text = CoreConfig.render(existing, listOf(FrontendOptions.SCALING to "Native"))
        assertEquals("nx_cpu_speed = Powersave\nnx_screen_scaling_v2 = Native\n", text)
    }

    @Test fun frontend_keys_fall_back_one_layer_like_core_options() {
        val console = CoreConfig.parse("nx_screen_scaling_v2 = Fullscreen\n")
        val game = CoreConfig.parse("nx_screen_scaling_v2 = Native\n")
        fun scaling(c: ConfigFile?, g: ConfigFile?) =
            FrontendOptions.fromConfig(CoreConfig.resolve(emptyMap(), c, g).values)[FrontendOptions.SCALING]
        assertEquals("Native", scaling(console, game))
        assertEquals("Fullscreen", scaling(console, null))   // game file restored away
        assertEquals("Auto", scaling(null, null))          // console file restored away too
    }

    @Test fun locked_frontend_key_is_reported_locked() {
        val resolved = CoreConfig.resolve(emptyMap(), CoreConfig.parse("-nx_screen_scaling = Native\n"), null)
        assertEquals(setOf("nx_screen_scaling"), resolved.locked)
    }

    @Test fun resolve_migrates_minarch_keys_to_nx() {
        val console = CoreConfig.parse("minarch_overlay = Aspect.png\n-minarch_scale_filter = LINEAR\ncore_x = 1\n")
        val r = CoreConfig.resolve(mapOf("core_x" to "0"), console, null)
        assertEquals(mapOf("core_x" to "1", "nx_overlay" to "Aspect.png", "nx_scale_filter" to "LINEAR"), r.values)
        assertEquals(setOf("nx_scale_filter"), r.locked)
    }

    @Test fun resolve_prefers_nx_over_minarch_when_both_exist() {
        val game = CoreConfig.parse("minarch_overlay = Old.png\nnx_overlay = New.png\n")
        assertEquals("New.png", CoreConfig.resolve(emptyMap(), null, game).values["nx_overlay"])
    }

    @Test fun render_renames_legacy_entries_in_place_keeping_locks() {
        val existing = CoreConfig.parse("# keep me\n-minarch_scale_filter = LINEAR\nminarch_overlay = Aspect.png\ncore_x = 1\nbind A = 1\n")
        val values = listOf("core_x" to "2", "nx_overlay" to "Aspect.png", "nx_scale_filter" to "NEAREST")
        val out = CoreConfig.render(existing, values, FrontendOptions::migrateKey)
        assertEquals("# keep me\n-nx_scale_filter = LINEAR\nnx_overlay = Aspect.png\ncore_x = 2\nbind A = 1\n", out)
        assertEquals(setOf("nx_scale_filter"), CoreConfig.parse(out).locked)
    }

    @Test fun render_dedupes_a_legacy_line_after_its_nx_twin() {
        val existing = CoreConfig.parse("nx_overlay = A.png\nminarch_overlay = B.png\n")
        assertEquals("nx_overlay = A.png\n", CoreConfig.render(existing, emptyList(), FrontendOptions::migrateKey))
        assertEquals("nx_overlay = C.png\n", CoreConfig.render(existing, listOf("nx_overlay" to "C.png"), FrontendOptions::migrateKey))
    }

    @Test fun render_drops_matching_keys() {
        val existing = CoreConfig.parse("core_x = 1\ncore_y = 2\n")
        assertEquals("core_y = 3\n", CoreConfig.render(existing, listOf("core_y" to "3"), drop = { it == "core_x" }))
    }

    @Test fun forced_keys_ignore_the_file_and_count_as_locked() {
        val r = CoreConfig.resolve(mapOf("a" to "1"), CoreConfig.parse("a = 2\nb = 3\n"), null, forced = setOf("a"))
        assertEquals(mapOf("a" to "1", "b" to "3"), r.values)
        assertEquals(setOf("a"), r.locked)
    }

    @Test fun a_stored_hidden_value_resolves_to_the_default() {
        val hidden = mapOf("a" to setOf("x"))
        val console = CoreConfig.parse("a = x\nb = x\n")
        val r = CoreConfig.resolve(mapOf("a" to "1"), console, null, hidden = hidden)
        assertEquals(mapOf("a" to "1", "b" to "x"), r.values)
        assertEquals(ConfigSource.CONSOLE, r.source)
        val game = CoreConfig.resolve(mapOf("a" to "1"), console, CoreConfig.parse("-a = x\n"), hidden = hidden)
        assertEquals(mapOf("a" to "1"), game.values)
        // Without a default the key is left out, so the core keeps its own default.
        assertEquals(emptyMap(), CoreConfig.resolve(emptyMap(), CoreConfig.parse("a = x\n"), null, hidden = hidden).values)
    }

    @Test fun a_stored_visible_value_of_an_option_with_hidden_values_is_kept() {
        val r = CoreConfig.resolve(mapOf("a" to "1"), null, CoreConfig.parse("a = 2\n"), hidden = mapOf("a" to setOf("x")))
        assertEquals(mapOf("a" to "2"), r.values)
    }

    @Test fun with_key_seeds_a_new_game_file_from_the_console() {
        val console = CoreConfig.parse("melonds_jit_enable = disabled\n-nx_overlay = None\nnx_nds_big_screen = top\n")
        val text = CoreConfig.withKey(console, null, "nx_nds_big_screen", "bottom")
        assertEquals("melonds_jit_enable = disabled\n-nx_overlay = None\nnx_nds_big_screen = bottom\n", text)
        val r = CoreConfig.resolve(emptyMap(), console, CoreConfig.parse(text))
        assertEquals(mapOf("melonds_jit_enable" to "disabled", "nx_overlay" to "None", "nx_nds_big_screen" to "bottom"), r.values)
        assertEquals(setOf("nx_overlay"), r.locked)
    }

    @Test fun with_key_updates_an_existing_game_file_and_starts_one_from_nothing() {
        assertEquals("a = 1\nnx_nds_pip_size = large\n", CoreConfig.withKey(CoreConfig.parse("b = 2\n"), CoreConfig.parse("a = 1\n"), "nx_nds_pip_size", "large"))
        assertEquals("nx_nds_pip_size = small\n", CoreConfig.withKey(null, null, "nx_nds_pip_size", "small"))
    }

    @Test fun with_key_keeps_a_locked_value() {
        assertEquals("-nx_nds_big_screen = top\n", CoreConfig.withKey(null, CoreConfig.parse("-nx_nds_big_screen = top\n"), "nx_nds_big_screen", "bottom"))
    }

    @Test fun a_full_save_drops_the_migrated_screen_effect_key_only() {
        assertTrue(FrontendOptions.droppedOnSave(FrontendOptions.EFFECT))
        assertFalse(FrontendOptions.droppedOnSave(NdsOptions.LAYOUT_PORTRAIT))
        assertFalse(FrontendOptions.droppedOnSave("melonds_opengl_resolution"))
    }

    private val renderer = "swanstation_GPU_Renderer"

    @Test fun a_key_forced_by_the_launch_saves_its_stored_value_not_the_running_one() {
        val running = listOf(renderer to "Auto", "swanstation_GPU_ResolutionScale" to "3", "nx_screen_scaling" to "Native")
        val stored = mapOf(renderer to "Software", "swanstation_GPU_ResolutionScale" to "1")
        assertEquals(
            listOf(renderer to "Software", "swanstation_GPU_ResolutionScale" to "3", "nx_screen_scaling" to "Native"),
            CoreConfig.valuesToSave(running, stored, sessionForced = setOf(renderer)),
        )
    }

    @Test fun a_forced_key_with_no_stored_value_is_left_out_so_the_file_keeps_its_line() {
        val running = listOf(renderer to "Auto", "a" to "1")
        val toSave = CoreConfig.valuesToSave(running, emptyMap(), setOf(renderer))
        assertEquals(listOf("a" to "1"), toSave)
        // render() keeps every existing line it is not given: the file's own value survives.
        assertEquals("OpenGL", CoreConfig.parse(CoreConfig.render(CoreConfig.parse("$renderer = OpenGL\n"), toSave)).values[renderer])
    }

    @Test fun nothing_forced_saves_the_running_values_unchanged() {
        val running = listOf(renderer to "Auto", "a" to "1")
        assertEquals(running, CoreConfig.valuesToSave(running, mapOf(renderer to "Software"), emptySet()))
    }
}
