package com.nxredux.mobile.options

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A config written before Auto and the new DS / 3DS layouts (only the old keys), read end to end as the game and Emulator
 * Settings read it ([CoreConfig.resolve], then [FrontendOptions.fromConfig] / [EmulatorSettings.page]): an explicit old
 * pick carries over, an untouched old default follows the new default; a full save then writes the new keys and drops
 * the old ones it replaced, and reads back the same.
 */
class OptionsMigrationTest {
    private val ds = CoreConfig.parse(
        "nx_screen_scaling = Native\n" +                     // explicit pick
            "nx_nds_layout_landscape = pip\n" +              // explicit pick
            "nx_nds_layout_portrait_v2 = auto\n" +           // the old default
            "nx_nds_layout_portrait_controller = stacked\n", // the old default
    )
    private val n3ds = CoreConfig.parse(
        "nx_screen_scaling = Aspect\n" +                     // the old default
            "nx_3ds_layout_landscape = single_screen\n" +    // explicit pick
            "nx_3ds_layout_portrait = default\n" +           // the old default
            "nx_3ds_layout_portrait_controller = large_screen\n", // the core's large screen: now Large screen
    )

    private fun frontend(tag: String, console: ConfigFile) =
        FrontendOptions.fromConfig(CoreConfig.resolve(FrontendOptions.defaults(tag), console, null).values, tag)

    private fun page(tag: String, console: ConfigFile) = EmulatorSettings.page(
        tag, CoreRegistry.forTag(tag)!!, null, emptyMap(), console, null, forGame = false, screen = ScreenClass.REGULAR_PHONE,
    ).console.associate { it.option.key to it.value }

    @Test fun ds_old_keys_resolve_to_the_new_values() {
        val v = frontend("NDS", ds)
        assertEquals("Native", v[FrontendOptions.SCALING])
        assertEquals(ScreenScaling.NATIVE, FrontendSettings.parse(v).scaling)
        assertEquals(NdsLayoutChoice.PIP.value, v[NdsOptions.LAYOUT_LANDSCAPE])
        assertNull(v[NdsOptions.LAYOUT_PORTRAIT])              // no pick: the screen's default
        assertNull(v[NdsOptions.LAYOUT_PORTRAIT_CONTROLLER])
        val rows = page("NDS", ds)
        assertEquals(NdsLayoutChoice.PIP.value, rows[NdsOptions.LAYOUT_LANDSCAPE])
        assertEquals(ndsDefault(ScreenClass.REGULAR_PHONE, false).value, rows[NdsOptions.LAYOUT_PORTRAIT])
        assertEquals(ndsDefault(ScreenClass.REGULAR_PHONE, false).value, rows[NdsOptions.LAYOUT_PORTRAIT_CONTROLLER])
    }

    @Test fun n3ds_old_keys_resolve_to_the_new_values() {
        val v = frontend("3DS", n3ds)
        assertEquals("Auto", v[FrontendOptions.SCALING])
        assertEquals(ScreenScaling.AUTO, FrontendSettings.parse(v).scaling)
        assertEquals("single_screen", v[N3dsOptions.LAYOUT_LANDSCAPE])
        assertNull(v[N3dsOptions.LAYOUT_PORTRAIT])
        assertEquals("large", v[N3dsOptions.LAYOUT_PORTRAIT_CONTROLLER])
        val rows = page("3DS", n3ds)
        assertEquals("single_screen", rows[N3dsOptions.LAYOUT_LANDSCAPE])
        assertEquals(N3dsOptions.defaultLayout(ScreenClass.REGULAR_PHONE, false), rows[N3dsOptions.LAYOUT_PORTRAIT])
        assertEquals("large", rows[N3dsOptions.LAYOUT_PORTRAIT_CONTROLLER])
    }

    /** A full save as the in-game Save Changes writes it: every frontend value, the old keys dropped where replaced. */
    private fun fullSave(tag: String, console: ConfigFile): ConfigFile {
        val values = frontend(tag, console).toList()
        val written = values.mapTo(HashSet()) { it.first }
        return CoreConfig.parse(CoreConfig.render(
            console, values, FrontendOptions::migrateKey, drop = { FrontendOptions.droppedOnSave(it, written) },
            lockMovesTo = FrontendOptions.SUPERSEDED_KEYS::get,
        ))
    }

    @Test fun a_full_save_drops_the_old_keys_it_replaced_and_reads_back_the_same() {
        for ((tag, console) in listOf("NDS" to ds, "3DS" to n3ds)) {
            val saved = fullSave(tag, console)
            assertEquals(frontend(tag, console), frontend(tag, saved), tag)
            assertFalse(FrontendOptions.SCALING_V1 in saved.values, tag)
        }
        val ds = fullSave("NDS", ds).values
        assertFalse("nx_nds_layout_landscape" in ds)
        // Untouched old defaults with no pick: the new key is not written, so the old line stays (it reads as no pick).
        assertEquals("auto", ds["nx_nds_layout_portrait_v2"])
        assertEquals("stacked", ds["nx_nds_layout_portrait_controller"])
        val n3ds = fullSave("3DS", n3ds).values
        assertFalse("nx_3ds_layout_landscape" in n3ds)
        assertFalse("nx_3ds_layout_portrait_controller" in n3ds)
        assertEquals("default", n3ds["nx_3ds_layout_portrait"])
    }

    @Test fun a_save_without_the_new_key_keeps_the_old_pick() {
        // One other key changed: the old Screen Scaling and DS layout picks must survive.
        val text = CoreConfig.render(ds, listOf(FrontendOptions.SHARPNESS to "LINEAR"), FrontendOptions::migrateKey,
            drop = { FrontendOptions.droppedOnSave(it, setOf(FrontendOptions.SHARPNESS)) })
        val v = frontend("NDS", CoreConfig.parse(text))
        assertEquals("Native", v[FrontendOptions.SCALING])
        assertEquals(NdsLayoutChoice.PIP.value, v[NdsOptions.LAYOUT_LANDSCAPE])
        // A one-key write of another key (a hotkey) keeps them too; one of the new key replaces the old line.
        val other = CoreConfig.parse(CoreConfig.withKey(ds, null, NdsOptions.BIG_SCREEN, "bottom"))
        assertEquals("pip", other.values["nx_nds_layout_landscape"])
        val same = CoreConfig.parse(CoreConfig.withKey(ds, null, NdsOptions.LAYOUT_LANDSCAPE, NdsLayoutChoice.LARGE.value))
        assertFalse("nx_nds_layout_landscape" in same.values)
        assertEquals(NdsLayoutChoice.LARGE.value, frontend("NDS", same)[NdsOptions.LAYOUT_LANDSCAPE])
    }

    @Test fun superseded_keys_map_each_old_key_to_its_new_one() {
        assertEquals(FrontendOptions.SCALING, FrontendOptions.SUPERSEDED_KEYS[FrontendOptions.SCALING_V1])
        assertEquals(NdsOptions.LAYOUT_PORTRAIT, FrontendOptions.SUPERSEDED_KEYS["nx_nds_layout_portrait_v2"])
        assertEquals(N3dsOptions.LAYOUT_LANDSCAPE, FrontendOptions.SUPERSEDED_KEYS["nx_3ds_layout_landscape"])
        assertTrue(FrontendOptions.SUPERSEDED_KEYS.values.none { it in FrontendOptions.SUPERSEDED_KEYS })   // no chains
    }

    @Test fun a_hand_lock_on_an_old_key_locks_its_new_key_and_moves_to_it_on_save() {
        val locked = CoreConfig.parse(
            "-nx_screen_scaling = Native\n" +                   // locked explicit pick
                "-nx_nds_layout_landscape = pip\n" +            // locked explicit pick
                "nx_nds_big_screen = top\n",
        )
        val read = CoreConfig.resolve(FrontendOptions.defaults("NDS"), locked, null)
        assertTrue(FrontendOptions.SCALING in read.locked)
        assertTrue(NdsOptions.LAYOUT_LANDSCAPE in read.locked)
        assertFalse(NdsOptions.BIG_SCREEN in read.locked)
        // A full save drops the old lines and writes the new keys locked, with the same picks.
        val saved = fullSave("NDS", locked)
        assertFalse(FrontendOptions.SCALING_V1 in saved.values)
        assertFalse("nx_nds_layout_landscape" in saved.values)
        assertEquals(setOf(FrontendOptions.SCALING, NdsOptions.LAYOUT_LANDSCAPE), saved.locked)
        assertEquals(frontend("NDS", locked), frontend("NDS", saved))
        assertEquals(setOf(FrontendOptions.SCALING, NdsOptions.LAYOUT_LANDSCAPE), CoreConfig.resolve(FrontendOptions.defaults("NDS"), saved, null).locked)
        // A one-key write of the new key (a hotkey) carries the lock too.
        val one = CoreConfig.parse(CoreConfig.withKey(locked, null, NdsOptions.LAYOUT_LANDSCAPE, NdsLayoutChoice.LARGE.value))
        assertTrue(NdsOptions.LAYOUT_LANDSCAPE in one.locked)
        assertFalse("nx_nds_layout_landscape" in one.values)
    }
}
