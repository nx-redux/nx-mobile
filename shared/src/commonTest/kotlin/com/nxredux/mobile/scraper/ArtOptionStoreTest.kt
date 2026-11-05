package com.nxredux.mobile.scraper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtOptionStoreTest {
    private val a = ArtOption(ArtSlot.SCREENSHOT, "http://x/a", "ss", gameId = "1", assetId = "us")
    private val b = ArtOption(ArtSlot.SCREENSHOT, "http://x/b", "ss", gameId = "1", assetId = "eu")
    private val c = ArtOption(ArtSlot.GRID, "http://x/c", "sgdb", assetId = "7")

    private fun host() = FakeHost(emptyList())

    @Test fun record_merges_by_key_keeps_order_and_pick() = runTest {
        val s = ArtOptionStore(host())
        s.record("g", listOf(a, b), false)
        s.setPick("g", ArtSlot.SCREENSHOT, a.key)
        s.record("g", listOf(b, c), true)
        val g = s.get("g")!!
        assertEquals(listOf(a, b, c), g.options)
        assertTrue(g.wide)
        assertEquals(mapOf(ArtSlot.SCREENSHOT to a.key), g.picks)
        assertTrue(s.picked("g", ArtSlot.SCREENSHOT))
        assertFalse(s.picked("g", ArtSlot.BOX_2D))
        s.record("h", listOf(c), false)
        assertEquals(mapOf("g" to setOf(ArtSlot.SCREENSHOT)), s.picks())
    }

    @Test fun a_grid_and_hero_with_one_asset_id_are_both_kept() = runTest {
        val s = ArtOptionStore(host())
        val hero = c.copy(slot = ArtSlot.HERO, url = "http://x/h")
        s.record("g", listOf(c, hero), false)
        assertEquals(listOf(c, hero), s.get("g")!!.options)
    }

    @Test fun persists_across_instances() = runTest {
        val h = host()
        ArtOptionStore(h).apply { record("g", listOf(a, c), true); setPick("g", ArtSlot.BOX_2D, c.key) }
        assertEquals(GameOptions(listOf(a, c), true, mapOf(ArtSlot.BOX_2D to c.key)), ArtOptionStore(h).get("g"))
    }

    @Test fun drop_option_clears_pick_when_it_was_the_pick() = runTest {
        val s = ArtOptionStore(host())
        s.record("g", listOf(a, b), false)
        s.setPick("g", ArtSlot.SCREENSHOT, a.key)
        s.setPick("g", ArtSlot.BOX_2D, c.key)
        s.dropOption("g", a.key)
        assertEquals(listOf(b), s.get("g")!!.options)
        assertEquals(mapOf(ArtSlot.BOX_2D to c.key), s.get("g")!!.picks)
    }

    @Test fun picksPerSlot() = runTest {
        val h = host()
        val s = ArtOptionStore(h)
        s.setPick("g", ArtSlot.BOX_2D, "k1")
        s.setPick("g", ArtSlot.SCREENSHOT, "k2")
        assertEquals(setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT), s.pickedSlots("g"))
        s.setPick("g", ArtSlot.BOX_2D, null)
        assertEquals(setOf(ArtSlot.SCREENSHOT), s.pickedSlots("g"))
        assertEquals(setOf(ArtSlot.SCREENSHOT), ArtOptionStore(h).pickedSlots("g"))
        assertEquals(emptySet<ArtSlot>(), s.pickedSlots("other"))
    }

    @Test fun part_one_file_is_not_read() = runTest {
        // Part 1's file (one pick per game): never read, so its pick protects nothing.
        val h = host().apply {
            files["art_options.json"] = """{"g":{"options":[{"slot":"SCREENSHOT","url":"http://x/a","source":"ss","assetId":"us"}],"wide":true,"pick":"ss/SCREENSHOT/us"}}"""
        }
        val s = ArtOptionStore(h)
        assertNull(s.get("g"))
        assertEquals(emptySet<ArtSlot>(), s.pickedSlots("g"))
    }

    @Test fun forget_scope_and_all() = runTest {
        val s = ArtOptionStore(host())
        s.record("g1", listOf(a, b), false)
        s.record("g2", listOf(c), false)
        assertEquals(mapOf("g1" to setOf(a.key, b.key)), s.forget(setOf("g1")))
        assertNull(s.get("g1"))
        assertEquals(listOf(c), s.get("g2")!!.options)
        assertEquals(mapOf("g2" to setOf(c.key)), s.forget(null))
        assertNull(s.get("g2"))
    }

    @Test fun unreadable_file_is_not_saved_over() = runTest {
        val h = host().apply { failRead = true }
        val s = ArtOptionStore(h)
        assertFailsWith<IllegalStateException> { s.record("g", listOf(a), false) }
        assertTrue(h.writes.isEmpty())
    }

    @Test fun corrupt_file_is_kept_aside_then_store_starts_empty() = runTest {
        val h = host().apply { files["art_options2.json"] = "not json" }
        ArtOptionStore(h).record("g", listOf(a), false)
        assertEquals("not json", h.files["art_options2.bad.json"])
        assertEquals(listOf(a), ArtOptionStore(h).get("g")!!.options)
    }
}
