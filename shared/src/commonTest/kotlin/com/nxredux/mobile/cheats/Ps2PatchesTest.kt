package com.nxredux.mobile.cheats

import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.FrontendOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ps2PatchesTest {
    @Test fun parse_reads_live_and_boot_rows() {
        assertEquals(
            listOf(Ps2PatchGroup("60 FPS", bootOnly = false), Ps2PatchGroup("Widescreen 16:9", bootOnly = true)),
            Ps2Patches.parse("crc=ABCD1234\nlive\t60 FPS\nboot\tWidescreen 16:9\n"),
        )
    }

    @Test fun parse_with_no_groups_is_an_empty_list() {
        assertEquals(emptyList(), Ps2Patches.parse("crc=ABCD1234\n"))
        assertNull(Ps2Patches.parse(""))
        assertNull(Ps2Patches.parse("live\t60 FPS\n"))
    }

    @Test fun initial_enabled_prefers_the_saved_set() {
        val raw = mapOf("armsx2_widescreen_patches" to "enabled")
        assertEquals(setOf("60 FPS"), Ps2Patches.initialEnabled(listOf("60 FPS"), raw))
        assertEquals(emptySet(), Ps2Patches.initialEnabled(emptyList(), raw))
    }

    @Test fun initial_enabled_carries_the_old_options() {
        val raw = mapOf("armsx2_widescreen_patches" to "enabled", "armsx2_no_interlacing_patches" to "enabled")
        assertEquals(setOf(Ps2Patches.WIDESCREEN, Ps2Patches.NO_INTERLACING), Ps2Patches.initialEnabled(null, raw))
        assertEquals(setOf(Ps2Patches.WIDESCREEN), Ps2Patches.initialEnabled(null, mapOf("armsx2_widescreen_patches" to "enabled")))
        assertEquals(emptySet(), Ps2Patches.initialEnabled(null, null))
    }

    @Test fun a_game_file_without_the_old_line_keeps_the_console_default() {
        val console = mapOf("armsx2_widescreen_patches" to "enabled")
        assertEquals(mapOf("armsx2_widescreen_patches" to "enabled", "armsx2_upscale" to "2x"),
            Ps2Patches.layered(console, mapOf("armsx2_upscale" to "2x")))
        assertEquals(mapOf("armsx2_widescreen_patches" to "disabled"),
            Ps2Patches.layered(console, mapOf("armsx2_widescreen_patches" to "disabled")))
        assertNull(Ps2Patches.layered(null, null))
    }

    @Test fun saved_set_is_read_from_the_game_file_only() {
        val values = mapOf(FrontendOptions.PATCHES to "60 FPS|No-Interlacing")
        assertEquals(listOf("60 FPS", "No-Interlacing"), Ps2Patches.savedFrom(values, ConfigSource.GAME))
        assertNull(Ps2Patches.savedFrom(values, ConfigSource.CONSOLE))
        assertNull(Ps2Patches.savedFrom(emptyMap(), ConfigSource.GAME))
    }

    @Test fun boot_patch_turned_on_needs_reset() {
        val set = Ps2PatchSet.start(emptySet()).withGroups(listOf(Ps2PatchGroup("A", bootOnly = true))).toggle("A", true)
        assertEquals("Takes effect after Reset", set.note("A"))
        assertTrue(set.needsReset())
        assertEquals("", set.afterReset().note("A"))
        assertFalse(set.afterReset().needsReset())
    }

    @Test fun applied_patch_turned_off_needs_reset() {
        val set = Ps2PatchSet.start(setOf("B")).withGroups(listOf(Ps2PatchGroup("B", bootOnly = false))).toggle("B", false)
        assertEquals("Fully off after Reset", set.note("B"))
        assertTrue(set.needsReset())
        assertEquals("", set.afterReset().note("B"))
    }

    @Test fun a_boot_patch_turned_on_then_off_says_fully_off() {
        val set = Ps2PatchSet.start(emptySet()).withGroups(listOf(Ps2PatchGroup("A", bootOnly = true)))
            .toggle("A", true).toggle("A", false)
        assertEquals("Fully off after Reset", set.note("A"))
    }

    @Test fun a_live_patch_turned_on_has_no_note() {
        val set = Ps2PatchSet.start(emptySet()).withGroups(listOf(Ps2PatchGroup("C", bootOnly = false))).toggle("C", true)
        assertEquals("", set.note("C"))
        assertFalse(set.needsReset())
    }

    @Test fun codes_and_saved_keys() {
        val groups = listOf(Ps2PatchGroup("60 FPS", false), Ps2PatchGroup("A|B", false))
        val set = Ps2PatchSet.start(setOf("60 FPS", "Gone")).withGroups(groups)
        assertEquals(listOf("patch:60 FPS"), set.codes())
        assertEquals(listOf("patch:60 FPS", "patch:Gone"), Ps2PatchSet.start(setOf("60 FPS", "Gone")).codes())
        // A saved name missing from today's files stays saved: an updated patches.zip may bring it back.
        assertEquals(listOf("60 FPS", "A/B", "Gone"), set.toggle("A|B", true).savedKeys())
    }

    @Test fun a_saved_name_with_the_separator_comes_back_on() {
        val groups = listOf(Ps2PatchGroup("A|B", false))
        val saved = CheatSet.decode(CheatSet.encode(Ps2PatchSet.start(setOf("A|B")).withGroups(groups).savedKeys()))
        val restored = Ps2PatchSet.start(Ps2Patches.initialEnabled(saved, null)).withGroups(groups)
        assertTrue(restored.isOn("A|B"))
    }

    @Test fun a_zipped_disc_lists_under_its_unpacked_file() {
        assertEquals("/s/PS2/SLUS_201/nx-patches.txt", Ps2Patches.listPath("/s/PS2", "MyGame.7z", innerName = "SLUS_201.iso"))
    }

    @Test fun the_set_goes_again_when_the_list_renames_a_saved_name() {
        val before = Ps2PatchSet.start(setOf("A/B"))
        val after = before.withGroups(listOf(Ps2PatchGroup("A|B", false)))
        assertTrue(Ps2PatchSet.resendAfterList(before, after))
        val plain = Ps2PatchSet.start(setOf("60 FPS"))
        assertFalse(Ps2PatchSet.resendAfterList(plain, plain.withGroups(listOf(Ps2PatchGroup("60 FPS", false)))))
    }

    @Test fun a_save_leaves_the_retired_options_lines_alone() {
        val running = listOf("armsx2_widescreen_patches" to "disabled", "armsx2_no_interlacing_patches" to "disabled", "armsx2_upscale" to "2x")
        assertEquals(listOf("armsx2_upscale" to "2x"), Ps2Patches.withoutRetired(running))
    }

    @Test fun list_path_sits_in_the_game_save_folder() {
        assertEquals("/s/PS2/Ico (USA)/nx-patches.txt", Ps2Patches.listPath("/s/PS2", "Ico (USA).chd"))
    }
}
