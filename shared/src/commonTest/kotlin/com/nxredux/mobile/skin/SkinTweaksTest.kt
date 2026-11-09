package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals

class SkinTweaksTest {
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

    @Test fun limit_keeps_any_move_and_any_positive_scale() {
        // the room round a group is what holds it ([SkinTweaks.apply]), not a cap on the tweak
        assertEquals(SkinTweak(500f, -500f, 2f), SkinTweaks.limit(SkinTweak(500f, -500f, 2f)))
        assertEquals(SkinTweak(900f, 0f, 0.1f), SkinTweaks.limit(SkinTweak(900f, 0f, 0.1f)))
    }

    @Test fun limit_drops_a_scale_that_is_not_a_size() {
        for (s in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) assertEquals(1f, SkinTweaks.limit(SkinTweak(3f, 4f, s)).scale, "$s")
    }

    @Test fun tweaks_are_keyed_by_layout_not_pack() {
        val c: ConsoleTweaks = mapOf("stacked_menu" to mapOf("A+B" to SkinTweak(5f, 5f, 1f)))
        // no pack id in the key: nx-carbon and nx-plasma both resolve layout "stacked_menu" to the same entry
        for (pack in listOf("nx-carbon", "nx-plasma")) assertEquals(SkinTweak(5f, 5f, 1f), c["stacked_menu"]?.get("A+B"), pack)
    }

    @Test fun a_layouts_tweaks_are_read_and_saved_leaving_the_rest() {
        val stored = SkinTweaks.encode(mapOf(
            "gba" to mapOf("stacked" to mapOf("A+B" to SkinTweak(1f, 2f, 1f)), "wide" to mapOf("D" to SkinTweak(3f, 0f, 1f))),
            "nes" to mapOf("stacked" to mapOf("A" to SkinTweak(5f, 5f, 1f))),
        ))
        assertEquals(mapOf("A+B" to SkinTweak(1f, 2f, 1f)), SkinTweaks.of(stored, "gba", "stacked"))
        assertEquals(emptyMap(), SkinTweaks.of(stored, "snes", "stacked"))
        val saved = SkinTweaks.decode(SkinTweaks.withLayout(stored, "gba", "stacked", mapOf("A+B" to SkinTweak(9f, 9f, 1.1f))))
        assertEquals(mapOf("A+B" to SkinTweak(9f, 9f, 1.1f)), saved["gba"]?.get("stacked"))
        assertEquals(mapOf("D" to SkinTweak(3f, 0f, 1f)), saved["gba"]?.get("wide"))
        assertEquals(mapOf("A" to SkinTweak(5f, 5f, 1f)), saved["nes"]?.get("stacked"))
        // none left: the layout goes, then the console with no layouts left
        val cleared = SkinTweaks.decode(SkinTweaks.withLayout(stored, "nes", "stacked", emptyMap()))
        assertEquals(setOf("gba"), cleared.keys)
        assertEquals(setOf("wide"), SkinTweaks.decode(SkinTweaks.withLayout(stored, "gba", "stacked", emptyMap()))["gba"]?.keys)
    }

    @Test fun an_unedited_overlay_group_follows_the_skin_an_edited_one_stays() {
        val a = SkinTweak(1f, 2f, 1f)
        val b = SkinTweak(3f, 4f, 1f)
        val b2 = SkinTweak(9f, 9f, 1.2f)
        fun stored(vararg g: Pair<String, SkinTweak>) = SkinTweaks.encode(mapOf("gba" to mapOf("stacked" to mapOf(*g))))
        val overlay = stored("B" to b2)
        assertEquals(mapOf("A" to a, "B" to b2), SkinTweaks.overlayOf(stored("A" to a, "B" to b), overlay, "gba", "stacked"))
        val a3 = SkinTweak(7f, 7f, 1f)
        assertEquals(mapOf("A" to a3, "B" to b2), SkinTweaks.overlayOf(stored("A" to a3, "B" to SkinTweak(5f, 5f, 1f)), overlay, "gba", "stacked"))
    }

    @Test fun an_empty_overlay_is_the_skin() {
        val skin = SkinTweaks.encode(mapOf("gba" to mapOf("stacked" to mapOf("A" to SkinTweak(1f, 2f, 1f)))))
        assertEquals(SkinTweaks.of(skin, "gba", "stacked"), SkinTweaks.overlayOf(skin, "", "gba", "stacked"))
    }
}
