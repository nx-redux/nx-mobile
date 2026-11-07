package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals

class SkinTweaksTest {
    private fun group(free: Boolean = false) =
        SkinGroup(if (free) SkinGroups.STRIP else "A+B", emptyList(), listOf("A"), PxRect(0f, 0f, 200f, 100f), free)

    @Test fun encode_decode_round_trips() {
        val m = mapOf(
            "gba" to mapOf(
                "stacked_menu" to mapOf("A+B" to SkinTweak(10f, -5f, 1.1f), "STRIP" to SkinTweak(0f, 20f, 1f)),
                "wide" to mapOf("UP+DOWN" to SkinTweak(-3.5f, 0f, 0.9f)),
            ),
            "nes" to mapOf("stacked_menu" to mapOf("A" to SkinTweak(1f, 2f, 1.25f))),
        )
        assertEquals(m, SkinTweaks.decode(SkinTweaks.encode(m)))
    }

    @Test fun decode_skips_unknown_and_apply_ignores_groups_not_in_the_layout() {
        val s = "garbage\ngba stacked A+B 1 2 1.5\ngba stacked C x 2 1\n\ngba stacked D 3 4 1\nshort line"
        val m = SkinTweaks.decode(s)
        assertEquals(mapOf("gba" to mapOf("stacked" to mapOf("A+B" to SkinTweak(1f, 2f, 1.5f), "D" to SkinTweak(3f, 4f, 1f)))), m)
    }

    @Test fun limit_clamps_move_and_scale() {
        assertEquals(SkinTweak(100f, -100f, 1.25f), SkinTweaks.limit(SkinTweak(500f, -500f, 2f), group()))
        assertEquals(0.8f, SkinTweaks.limit(SkinTweak(0f, 0f, 0.5f), group()).scale)
    }

    @Test fun the_strip_moves_freely() {
        assertEquals(SkinTweak(900f, 0f, 1.25f), SkinTweaks.limit(SkinTweak(900f, 0f, 3f), group(free = true)))
    }

    @Test fun tweaks_are_keyed_by_layout_not_pack() {
        val c: ConsoleTweaks = mapOf("stacked_menu" to mapOf("A+B" to SkinTweak(5f, 5f, 1f)))
        // no pack id in the key: nx-carbon and nx-plasma both resolve layout "stacked_menu" to the same entry
        for (pack in listOf("nx-carbon", "nx-plasma")) assertEquals(SkinTweak(5f, 5f, 1f), c["stacked_menu"]?.get("A+B"), pack)
    }
}
