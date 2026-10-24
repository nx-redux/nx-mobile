package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PORTRAIT = PadSlot(PadShape.PORTRAIT, PadScreen.NARROW)
private val OVERLAY = PadSlot(PadShape.OVERLAY, PadScreen.NARROW)
private val PORTRAIT_WIDE = PadSlot(PadShape.PORTRAIT, PadScreen.WIDE)

class PadLayoutsTest {
    private val up = GroupTweak(dy = -2f)
    private val big = GroupTweak(scale = 1.3f)
    private val shared = PadLayout(mapOf(PadGroup.DPAD to up))
    private val n64 = PadLayout(mapOf(PadGroup.FACE to big))

    @Test fun resolve_prefers_the_console_then_shared_then_empty_per_shape() {
        val all: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared, OVERLAY to shared),
            "n64" to mapOf(PORTRAIT to n64),
        )
        assertEquals(n64, PadLayouts.resolve(all, "n64", PORTRAIT))
        assertEquals(shared, PadLayouts.resolve(all, "n64", OVERLAY))      // per shape: falls back
        assertEquals(shared, PadLayouts.resolve(all, "gba", PORTRAIT))
        assertEquals(shared, PadLayouts.resolve(all, null, PORTRAIT))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(mapOf("n64" to mapOf(PORTRAIT to n64)), "n64", OVERLAY))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(emptyMap(), "n64", PORTRAIT))
    }

    @Test fun starting_layout_is_the_consoles_own_else_shared_else_empty() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared))
        assertEquals(shared, PadLayouts.startingLayout(all, "gba", PORTRAIT))
        assertEquals(shared, PadLayouts.startingLayout(all, PadLayouts.SHARED_KEY, PORTRAIT))
        assertEquals(PadLayout.EMPTY, PadLayouts.startingLayout(all, "gba", OVERLAY))
        val withOwn = PadLayouts.withLayout(all, "gba", PORTRAIT, n64)
        assertEquals(n64, PadLayouts.startingLayout(withOwn, "gba", PORTRAIT))
    }

    @Test fun with_layout_stores_and_an_empty_shared_layout_removes_the_entry() {
        var all: PadLayoutMap = emptyMap()
        all = PadLayouts.withLayout(all, PadLayouts.SHARED_KEY, PORTRAIT, shared)
        assertEquals(shared, PadLayouts.own(all, PadLayouts.SHARED_KEY, PORTRAIT))
        assertTrue(PadLayouts.isCustom(all))
        all = PadLayouts.withLayout(all, PadLayouts.SHARED_KEY, PORTRAIT, PadLayout.EMPTY)
        assertNull(PadLayouts.own(all, PadLayouts.SHARED_KEY, PORTRAIT))
        assertEquals(emptyMap(), all)                                                // no empty Shared key left behind
        assertFalse(PadLayouts.isCustom(all))
    }

    @Test fun a_consoles_empty_layout_is_kept_as_an_explicit_default_over_a_customised_shared() {
        var all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared))
        all = PadLayouts.withLayout(all, "gba", PORTRAIT, n64)
        all = PadLayouts.withLayout(all, "gba", PORTRAIT, PadLayout.EMPTY)
        assertEquals(PadLayout.EMPTY, PadLayouts.own(all, "gba", PORTRAIT))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(all, "gba", PORTRAIT))      // today's pad, not Shared's
        assertEquals(PadLayout.EMPTY, PadLayouts.startingLayout(all, "gba", PORTRAIT))
        assertEquals(shared, PadLayouts.resolve(all, "nds", PORTRAIT))                 // other consoles still inherit
        assertFalse(PadLayouts.isCustom(mapOf("gba" to mapOf(PORTRAIT to PadLayout.EMPTY))))   // only tweaks count
        // Reset console drops the marker: the console inherits Shared again.
        assertEquals(shared, PadLayouts.resolve(PadLayouts.withoutScreen(all, "gba", PadScreen.NARROW), "gba", PORTRAIT))
    }

    @Test fun without_screen_clears_that_consoles_shapes_on_that_screen_only() {
        val all: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared),
            "gba" to mapOf(PORTRAIT to n64, OVERLAY to n64),
        )
        assertEquals(mapOf(PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared)), PadLayouts.withoutScreen(all, "gba", PadScreen.NARROW))
        val both = PadLayouts.withLayout(all, "gba", PORTRAIT_WIDE, shared)
        assertEquals(mapOf(PORTRAIT_WIDE to shared), PadLayouts.withoutScreen(both, "gba", PadScreen.NARROW)["gba"])
    }

    @Test fun each_screen_draws_its_own_layout() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared), "gba" to mapOf(PORTRAIT_WIDE to n64))
        assertEquals(n64, PadLayouts.resolve(all, "gba", PORTRAIT_WIDE))
        assertEquals(shared, PadLayouts.resolve(all, "gba", PORTRAIT))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(all, "nds", PORTRAIT_WIDE))    // never the other screen's
    }

    @Test fun a_screen_with_nothing_yet_starts_editing_from_the_other_screen() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared))
        assertEquals(shared, PadLayouts.startingLayout(all, PadLayouts.SHARED_KEY, PORTRAIT_WIDE))
        assertEquals(shared, PadLayouts.startingLayout(all, "gba", PORTRAIT_WIDE))
        val wide = PadLayouts.withLayout(all, PadLayouts.SHARED_KEY, PORTRAIT_WIDE, n64)
        assertEquals(n64, PadLayouts.startingLayout(wide, "gba", PORTRAIT_WIDE))      // this screen's Shared first
    }

    @Test fun screens_split_at_600dp() {
        assertEquals(PadScreen.NARROW, PadScreen.of(411f))
        assertEquals(PadScreen.WIDE, PadScreen.of(600f))
        assertEquals(PadScreen.WIDE, PadScreen.of(673f))
    }

    @Test fun identity_tweaks_are_dropped() {
        val l = PadLayout.EMPTY.with(PadGroup.DPAD, GroupTweak()).with(PadGroup.FACE, big)
        assertEquals(mapOf(PadGroup.FACE to big), l.tweaks)
        assertEquals(PadLayout.EMPTY, l.without(PadGroup.FACE))
    }

    @Test fun encode_decode_round_trips() {
        val all: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(PORTRAIT to PadLayout(mapOf(PadGroup.DPAD to GroupTweak(0.5f, -2.25f, 1.1f)))),
            "n64" to mapOf(OVERLAY to PadLayout(mapOf(PadGroup.FACE to big, PadGroup.SYSTEM_LEFT to up)), PORTRAIT_WIDE to n64),
        )
        assertEquals(all, PadLayouts.decode(PadLayouts.encode(all)))
        assertTrue("n64\tPORTRAIT.WIDE\tFACE\t0.0\t0.0\t1.3" in PadLayouts.encode(all).lines())   // narrow keeps the bare shape name
        assertEquals(emptyMap(), PadLayouts.decode(PadLayouts.encode(emptyMap())))
    }

    @Test fun encode_decode_round_trips_a_consoles_explicit_default() {
        val all: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared),
            "gba" to mapOf(PORTRAIT to PadLayout.EMPTY, OVERLAY to n64),
        )
        val text = PadLayouts.encode(all)
        assertTrue("gba\tPORTRAIT\t-" in text.lines())
        assertEquals(all, PadLayouts.decode(text))
    }

    @Test fun decode_a_tweak_after_the_default_marker_replaces_it() {
        val all = PadLayouts.decode("gba\tPORTRAIT\t-\ngba\tPORTRAIT\tFACE\t0\t0\t1.3")
        assertEquals(n64, PadLayouts.own(all, "gba", PORTRAIT))
        val bad = PadLayouts.decode("\tPORTRAIT\t-\ngba\tSIDEWAYS\t-\ngba\tPORTRAIT\tx\n*\tPORTRAIT\t-")
        assertEquals(emptyMap(), bad)                                                // no key, unknown shape, not "-", Shared
    }

    @Test fun decode_skips_bad_lines_and_clamps_scale() {
        val text = listOf(
            "gba\tPORTRAIT\tDPAD\t1.0\t2.0\t1.2",       // good
            "gba\tPORTRAIT\tWHEEL\t1\t1\t1",            // unknown group
            "gba\tSIDEWAYS\tDPAD\t1\t1\t1",             // unknown shape
            "gba\tPORTRAIT\tFACE\tx\t1\t1",             // not a number
            "gba\tPORTRAIT\tFACE\tNaN\t1\t1",           // NaN
            "garbage",
            "",
            "gba\tOVERLAY\tFACE\t0\t0\t9.0",            // scale clamped to 1.5
            "gba\tOVERLAY\tDPAD\t0\t0\t0.1",            // scale clamped to 0.7
            "gba\tFLEX\tFACE\t0\t0\t1.2",               // a retired shape (Flex mode): skipped
            "gba\tPORTRAIT.NARROW\tFACE\t0\t0\t1.2",    // narrow is written bare
            "gba\tPORTRAIT.TALL\tFACE\t0\t0\t1.2",      // unknown screen
            "gba\tPORTRAIT.WIDE.X\tFACE\t0\t0\t1.2",
        ).joinToString("\n")
        val all = PadLayouts.decode(text)
        assertEquals(GroupTweak(1f, 2f, 1.2f), all["gba"]!![PORTRAIT]!!.tweaks[PadGroup.DPAD])
        assertEquals(1, all["gba"]!![PORTRAIT]!!.tweaks.size)
        assertEquals(1.5f, all["gba"]!![OVERLAY]!!.tweaks[PadGroup.FACE]!!.scale)
        assertEquals(0.7f, all["gba"]!![OVERLAY]!!.tweaks[PadGroup.DPAD]!!.scale)
        assertEquals(setOf(PORTRAIT, OVERLAY), all["gba"]!!.keys)
        assertEquals(emptyMap(), PadLayouts.decode(null))
        assertEquals(emptyMap(), PadLayouts.decode("\u0000\t\t"))
    }

    @Test fun an_unchanged_save_writes_nothing_unless_adopted_from_the_other_screen() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(PORTRAIT to shared))
        // A console inheriting Shared, saved unchanged: nothing written (it keeps following Shared).
        assertNull(PadLayouts.afterSave(all, "gba", PORTRAIT, shared, dirty = false))
        // Changed: written as the console's own.
        assertEquals(n64, PadLayouts.afterSave(all, "gba", PORTRAIT, n64, dirty = true)?.let { PadLayouts.own(it, "gba", PORTRAIT) })
        // The wide screen has nothing: it started from the narrow screen's Shared, so an unchanged Save keeps it there.
        val adopted = PadLayouts.afterSave(all, PadLayouts.SHARED_KEY, PORTRAIT_WIDE, shared, dirty = false)
        assertEquals(shared, adopted?.let { PadLayouts.own(it, PadLayouts.SHARED_KEY, PORTRAIT_WIDE) })
        // Shared's Reset all, saved: the slot goes.
        assertEquals(emptyMap(), PadLayouts.afterSave(all, PadLayouts.SHARED_KEY, PORTRAIT, PadLayout.EMPTY, dirty = true))
    }

    @Test fun a_consoles_explicit_default_on_the_other_screen_starts_today_pad() {
        val all: PadLayoutMap = mapOf("gba" to mapOf(PORTRAIT_WIDE to PadLayout.EMPTY))
        assertEquals(PadLayout.EMPTY, PadLayouts.startingLayout(all, "gba", PORTRAIT))
        assertNull(PadLayouts.afterSave(all, "gba", PORTRAIT, PadLayout.EMPTY, dirty = false))
    }
    @Test fun pad_group_names_stay_stable_and_the_shortcut_pieces_read() {
        // Layouts store groups by name: the shortcut pieces are appended, so a stored line naming an old group still reads.
        assertEquals(listOf("DPAD", "FACE", "L_SHOULDERS", "R_SHOULDERS", "SYSTEM_LEFT", "SYSTEM_RIGHT", "C_STICK"), PadGroup.entries.take(7).map { it.name })
        val all = PadLayouts.decode("gba\tPORTRAIT\tDPAD\t1\t0\t1\ngba\tOVERLAY\tSHORTCUT_1\t0\t-1\t1.2\ngba\tOVERLAY\tSHORTCUT_2\t1\t0\t1")
        assertEquals(GroupTweak(dx = 1f), PadLayouts.own(all, "gba", PORTRAIT)?.tweaks?.get(PadGroup.DPAD))
        assertEquals(GroupTweak(dy = -1f, scale = 1.2f), PadLayouts.own(all, "gba", OVERLAY)?.tweaks?.get(PadGroup.SHORTCUT_1))
        assertEquals(GroupTweak(dx = 1f), PadLayouts.own(all, "gba", OVERLAY)?.tweaks?.get(PadGroup.SHORTCUT_2))
        assertEquals(all, PadLayouts.decode(PadLayouts.encode(all)))
    }
}
