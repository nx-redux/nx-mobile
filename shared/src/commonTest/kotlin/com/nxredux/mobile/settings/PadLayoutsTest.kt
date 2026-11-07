package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val OVERLAY = PadSlot(PadShape.OVERLAY, PadScreen.NARROW)
private val OVERLAY_WIDE = PadSlot(PadShape.OVERLAY, PadScreen.WIDE)

class PadLayoutsTest {
    private val up = GroupTweak(dy = -2f)
    private val big = GroupTweak(scale = 1.3f)
    private val shared = PadLayout(mapOf(PadGroup.DPAD to up))
    private val n64 = PadLayout(mapOf(PadGroup.FACE to big))

    @Test fun resolve_prefers_the_console_then_shared_then_empty_per_slot() {
        val all: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared, OVERLAY_WIDE to shared),
            "n64" to mapOf(OVERLAY to n64),
        )
        assertEquals(n64, PadLayouts.resolve(all, "n64", OVERLAY))
        assertEquals(shared, PadLayouts.resolve(all, "n64", OVERLAY_WIDE))      // per slot: falls back
        assertEquals(shared, PadLayouts.resolve(all, "gba", OVERLAY))
        assertEquals(shared, PadLayouts.resolve(all, null, OVERLAY))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(mapOf("n64" to mapOf(OVERLAY to n64)), "gba", OVERLAY))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(emptyMap(), "n64", OVERLAY))
    }

    @Test fun starting_layout_is_the_consoles_own_else_shared_else_empty() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared))
        assertEquals(shared, PadLayouts.startingLayout(all, "gba", OVERLAY))
        assertEquals(shared, PadLayouts.startingLayout(all, PadLayouts.SHARED_KEY, OVERLAY))
        assertEquals(PadLayout.EMPTY, PadLayouts.startingLayout(emptyMap(), "gba", OVERLAY))
        val withOwn = PadLayouts.withLayout(all, "gba", OVERLAY, n64)
        assertEquals(n64, PadLayouts.startingLayout(withOwn, "gba", OVERLAY))
    }

    @Test fun with_layout_stores_and_an_empty_shared_layout_removes_the_entry() {
        var all: PadLayoutMap = emptyMap()
        all = PadLayouts.withLayout(all, PadLayouts.SHARED_KEY, OVERLAY, shared)
        assertEquals(shared, PadLayouts.own(all, PadLayouts.SHARED_KEY, OVERLAY))
        assertTrue(PadLayouts.isCustom(all))
        all = PadLayouts.withLayout(all, PadLayouts.SHARED_KEY, OVERLAY, PadLayout.EMPTY)
        assertNull(PadLayouts.own(all, PadLayouts.SHARED_KEY, OVERLAY))
        assertEquals(emptyMap(), all)                                                // no empty Shared key left behind
        assertFalse(PadLayouts.isCustom(all))
    }

    @Test fun a_consoles_empty_layout_is_kept_as_an_explicit_default_over_a_customised_shared() {
        var all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared))
        all = PadLayouts.withLayout(all, "gba", OVERLAY, n64)
        all = PadLayouts.withLayout(all, "gba", OVERLAY, PadLayout.EMPTY)
        assertEquals(PadLayout.EMPTY, PadLayouts.own(all, "gba", OVERLAY))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(all, "gba", OVERLAY))      // today's pad, not Shared's
        assertEquals(PadLayout.EMPTY, PadLayouts.startingLayout(all, "gba", OVERLAY))
        assertEquals(shared, PadLayouts.resolve(all, "nds", OVERLAY))                 // other consoles still inherit
        assertFalse(PadLayouts.isCustom(mapOf("gba" to mapOf(OVERLAY to PadLayout.EMPTY))))   // only tweaks count
        // Reset console drops the marker: the console inherits Shared again.
        assertEquals(shared, PadLayouts.resolve(PadLayouts.withoutScreen(all, "gba", PadScreen.NARROW), "gba", OVERLAY))
    }

    @Test fun without_screen_clears_that_consoles_shapes_on_that_screen_only() {
        val all: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared),
            "gba" to mapOf(OVERLAY to n64),
        )
        assertEquals(mapOf(PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared)), PadLayouts.withoutScreen(all, "gba", PadScreen.NARROW))
        val both = PadLayouts.withLayout(all, "gba", OVERLAY_WIDE, shared)
        assertEquals(mapOf(OVERLAY_WIDE to shared), PadLayouts.withoutScreen(both, "gba", PadScreen.NARROW)["gba"])
    }

    @Test fun each_screen_draws_its_own_layout() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared), "gba" to mapOf(OVERLAY_WIDE to n64))
        assertEquals(n64, PadLayouts.resolve(all, "gba", OVERLAY_WIDE))
        assertEquals(shared, PadLayouts.resolve(all, "gba", OVERLAY))
        assertEquals(PadLayout.EMPTY, PadLayouts.resolve(all, "nds", OVERLAY_WIDE))    // never the other screen's
    }

    @Test fun a_screen_with_nothing_yet_starts_editing_from_the_other_screen() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared))
        assertEquals(shared, PadLayouts.startingLayout(all, PadLayouts.SHARED_KEY, OVERLAY_WIDE))
        assertEquals(shared, PadLayouts.startingLayout(all, "gba", OVERLAY_WIDE))
        val wide = PadLayouts.withLayout(all, PadLayouts.SHARED_KEY, OVERLAY_WIDE, n64)
        assertEquals(n64, PadLayouts.startingLayout(wide, "gba", OVERLAY_WIDE))      // this screen's Shared first
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
            PadLayouts.SHARED_KEY to mapOf(OVERLAY to PadLayout(mapOf(PadGroup.DPAD to GroupTweak(0.5f, -2.25f, 1.1f)))),
            "n64" to mapOf(OVERLAY to PadLayout(mapOf(PadGroup.FACE to big, PadGroup.SYSTEM_LEFT to up)), OVERLAY_WIDE to n64),
        )
        assertEquals(all, PadLayouts.decode(PadLayouts.encode(all)))
        assertTrue("n64\tOVERLAY.WIDE\tFACE\t0.0\t0.0\t1.3" in PadLayouts.encode(all).lines())   // narrow keeps the bare shape name
        assertEquals(emptyMap(), PadLayouts.decode(PadLayouts.encode(emptyMap())))
    }

    @Test fun encode_decode_round_trips_a_consoles_explicit_default() {
        val all: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared),
            "gba" to mapOf(OVERLAY to PadLayout.EMPTY, OVERLAY_WIDE to n64),
        )
        val text = PadLayouts.encode(all)
        assertTrue("gba\tOVERLAY\t-" in text.lines())
        assertEquals(all, PadLayouts.decode(text))
    }

    @Test fun decode_a_tweak_after_the_default_marker_replaces_it() {
        val all = PadLayouts.decode("gba\tOVERLAY\t-\ngba\tOVERLAY\tFACE\t0\t0\t1.3")
        assertEquals(n64, PadLayouts.own(all, "gba", OVERLAY))
        val bad = PadLayouts.decode("\tOVERLAY\t-\ngba\tSIDEWAYS\t-\ngba\tOVERLAY\tx\n*\tOVERLAY\t-")
        assertEquals(emptyMap(), bad)                                                // no key, unknown shape, not "-", Shared
    }

    @Test fun decode_skips_bad_lines_and_clamps_scale() {
        val text = listOf(
            "gba\tOVERLAY.WIDE\tDPAD\t1.0\t2.0\t1.2",   // good
            "gba\tOVERLAY.WIDE\tWHEEL\t1\t1\t1",        // unknown group
            "gba\tSIDEWAYS\tDPAD\t1\t1\t1",             // unknown shape
            "gba\tOVERLAY.WIDE\tFACE\tx\t1\t1",         // not a number
            "gba\tOVERLAY.WIDE\tFACE\tNaN\t1\t1",       // NaN
            "garbage",
            "",
            "gba\tOVERLAY\tFACE\t0\t0\t9.0",            // scale clamped to 1.5
            "gba\tOVERLAY\tDPAD\t0\t0\t0.1",            // scale clamped to 0.7
            "gba\tFLEX\tFACE\t0\t0\t1.2",               // a retired shape (Flex mode): skipped
            "gba\tOVERLAY.NARROW\tFACE\t0\t0\t1.2",     // narrow is written bare
            "gba\tOVERLAY.TALL\tFACE\t0\t0\t1.2",       // unknown screen
            "gba\tOVERLAY.WIDE.X\tFACE\t0\t0\t1.2",
        ).joinToString("\n")
        val all = PadLayouts.decode(text)
        assertEquals(GroupTweak(1f, 2f, 1.2f), all["gba"]!![OVERLAY_WIDE]!!.tweaks[PadGroup.DPAD])
        assertEquals(1, all["gba"]!![OVERLAY_WIDE]!!.tweaks.size)
        assertEquals(1.5f, all["gba"]!![OVERLAY]!!.tweaks[PadGroup.FACE]!!.scale)
        assertEquals(0.7f, all["gba"]!![OVERLAY]!!.tweaks[PadGroup.DPAD]!!.scale)
        assertEquals(setOf(OVERLAY_WIDE, OVERLAY), all["gba"]!!.keys)
        assertEquals(emptyMap(), PadLayouts.decode(null))
        assertEquals(emptyMap(), PadLayouts.decode("\u0000\t\t"))
    }

    @Test fun decode_skips_lines_of_the_retired_portrait_and_half_folded_shapes() {
        val text = listOf(
            "gba\tPORTRAIT\tDPAD\t1\t0\t1",
            "gba\tPORTRAIT\t-",
            "*\tPORTRAIT.WIDE\tFACE\t0\t0\t1.3",
            "nds\tHALF_FOLDED.WIDE\tDPAD\t1\t-0.5\t1.2",
            "nds\tHALF_FOLDED.WIDE\t-",
            "nds\tOVERLAY.WIDE\tFACE\t0\t0\t1.3",   // the landscape line beside them still reads
        ).joinToString("\n")
        assertEquals(mapOf("nds" to mapOf(OVERLAY_WIDE to n64)), PadLayouts.decode(text))
        assertNull(PadSlot.fromCode("PORTRAIT"))
        assertNull(PadSlot.fromCode("HALF_FOLDED.WIDE"))
        assertEquals(listOf(PadShape.OVERLAY), PadShape.offered)
    }

    @Test fun an_unchanged_save_writes_nothing_unless_adopted_from_the_other_screen() {
        val all: PadLayoutMap = mapOf(PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared))
        // A console inheriting Shared, saved unchanged: nothing written (it keeps following Shared).
        assertNull(PadLayouts.afterSave(all, "gba", OVERLAY, shared, dirty = false))
        // Changed: written as the console's own.
        assertEquals(n64, PadLayouts.afterSave(all, "gba", OVERLAY, n64, dirty = true)?.let { PadLayouts.own(it, "gba", OVERLAY) })
        // The wide screen has nothing: it started from the narrow screen's Shared, so an unchanged Save keeps it there.
        val adopted = PadLayouts.afterSave(all, PadLayouts.SHARED_KEY, OVERLAY_WIDE, shared, dirty = false)
        assertEquals(shared, adopted?.let { PadLayouts.own(it, PadLayouts.SHARED_KEY, OVERLAY_WIDE) })
        // Shared's Reset all, saved: the slot goes.
        assertEquals(emptyMap(), PadLayouts.afterSave(all, PadLayouts.SHARED_KEY, OVERLAY, PadLayout.EMPTY, dirty = true))
    }

    @Test fun a_consoles_explicit_default_on_the_other_screen_starts_today_pad() {
        val all: PadLayoutMap = mapOf("gba" to mapOf(OVERLAY_WIDE to PadLayout.EMPTY))
        assertEquals(PadLayout.EMPTY, PadLayouts.startingLayout(all, "gba", OVERLAY))
        assertNull(PadLayouts.afterSave(all, "gba", OVERLAY, PadLayout.EMPTY, dirty = false))
    }
    @Test fun pad_group_names_stay_stable_and_the_shortcut_pieces_read() {
        // Layouts store groups by name: the shortcut pieces are appended, so a stored line naming an old group still reads.
        assertEquals(listOf("DPAD", "FACE", "L_SHOULDERS", "R_SHOULDERS", "SYSTEM_LEFT", "SYSTEM_RIGHT", "C_STICK"), PadGroup.entries.take(7).map { it.name })
        val all = PadLayouts.decode("gba\tOVERLAY.WIDE\tDPAD\t1\t0\t1\ngba\tOVERLAY\tSHORTCUT_1\t0\t-1\t1.2\ngba\tOVERLAY\tSHORTCUT_2\t1\t0\t1")
        assertEquals(GroupTweak(dx = 1f), PadLayouts.own(all, "gba", OVERLAY_WIDE)?.tweaks?.get(PadGroup.DPAD))
        assertEquals(GroupTweak(dy = -1f, scale = 1.2f), PadLayouts.own(all, "gba", OVERLAY)?.tweaks?.get(PadGroup.SHORTCUT_1))
        assertEquals(GroupTweak(dx = 1f), PadLayouts.own(all, "gba", OVERLAY)?.tweaks?.get(PadGroup.SHORTCUT_2))
        assertEquals(all, PadLayouts.decode(PadLayouts.encode(all)))
    }

    @Test fun the_old_line_loses_its_landscape_moves_once_and_keeps_explicit_defaults() {
        val before: PadLayoutMap = mapOf(
            PadLayouts.SHARED_KEY to mapOf(OVERLAY to shared, OVERLAY_WIDE to n64),
            "n64" to mapOf(OVERLAY to n64),                                      // only landscape moves: the console goes
            "gba" to mapOf(OVERLAY to PadLayout.EMPTY),                         // an explicit landscape default: not a move, kept
            "snes" to mapOf(OVERLAY_WIDE to PadLayout.EMPTY, OVERLAY to n64),   // the default stays, the move goes
        )
        val migrated = PadLayouts.decodeStored(current = null, beforeBeside = PadLayouts.encode(before))
        assertEquals(
            mapOf(
                "gba" to mapOf(OVERLAY to PadLayout.EMPTY),
                "snes" to mapOf(OVERLAY_WIDE to PadLayout.EMPTY),
            ),
            migrated,
        )
        // Once the versioned line is written it is read as is: a landscape move made since stays, the old line is ignored.
        val since = PadLayouts.withLayout(migrated, PadLayouts.SHARED_KEY, OVERLAY, n64)
        assertEquals(since, PadLayouts.decodeStored(current = PadLayouts.encode(since), beforeBeside = PadLayouts.encode(before)))
        // Everything cleared since: an empty versioned line, not the old one.
        assertEquals(emptyMap(), PadLayouts.decodeStored(current = "", beforeBeside = PadLayouts.encode(before)))
        assertEquals(emptyMap(), PadLayouts.decodeStored(current = null, beforeBeside = null))
    }

    @Test fun oldLayoutLineStillDecodes() {
        // A line stored before the stick groups were appended reads to the same tweaks.
        val old = "*\tOVERLAY\tDPAD\t0.5\t-1.0\t1.1\nps2\tOVERLAY.WIDE\tFACE\t0.0\t0.0\t1.3\nps2\tOVERLAY.WIDE\tC_STICK\t1.0\t0.0\t1.0"
        val all = PadLayouts.decode(old)
        assertEquals(GroupTweak(0.5f, -1f, 1.1f), PadLayouts.own(all, PadLayouts.SHARED_KEY, OVERLAY)?.tweaks?.get(PadGroup.DPAD))
        assertEquals(mapOf(PadGroup.FACE to big, PadGroup.C_STICK to GroupTweak(dx = 1f)), PadLayouts.own(all, "ps2", OVERLAY_WIDE)?.tweaks)
        assertEquals(listOf("DPAD", "FACE", "L_SHOULDERS", "R_SHOULDERS", "SYSTEM_LEFT", "SYSTEM_RIGHT", "C_STICK", "SHORTCUT_1", "SHORTCUT_2"),
            PadGroup.entries.take(9).map { it.name })
    }

    @Test fun stickGroupsRoundTrip() {
        val all: PadLayoutMap = mapOf("ps2" to mapOf(
            OVERLAY to PadLayout(mapOf(PadGroup.L_STICK to GroupTweak(1f, -0.5f, 1.2f), PadGroup.R_STICK to GroupTweak(dx = -1f))),
            OVERLAY_WIDE to PadLayout(mapOf(PadGroup.R_STICK to big)),
        ))
        val text = PadLayouts.encode(all)
        assertTrue("ps2\tOVERLAY\tL_STICK\t1.0\t-0.5\t1.2" in text.lines())
        assertEquals(all, PadLayouts.decode(text))
    }
}
