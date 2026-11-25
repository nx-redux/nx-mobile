package com.nxredux.mobile.achievements

import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class RaLaunchHashTest {
    private val h = "0123456789abcdef0123456789abcdef"
    private fun fp() = RaFingerprints(RaHomeTree(raHomeSource("W (WII)")) { ContentRef("home") })
    private val target = RaLaunchTarget(RaGamePointer("WII", "home", "Roms/W (WII)", "g.iso", "g"), "10:5000", 19, storable = true)

    @Test fun computes_then_hits_after_an_answer() = runTest {
        val fp = fp(); var calls = 0
        val (first, l) = RaLaunchHash.of(fp, target) { calls++; h }
        assertEquals(h, first); RaLaunchHash.answer(fp, l!!, h, 42)
        val (second, l2) = RaLaunchHash.of(fp, target) { calls++; "other" }
        assertEquals(h, second); assertEquals(1, calls); assertEquals(42, l2!!.cached!!.game)
    }

    @Test fun nothing_is_stored_without_an_answer_or_a_stamp() = runTest {
        val fp = fp(); var calls = 0
        RaLaunchHash.of(fp, target) { calls++; h }
        RaLaunchHash.of(fp, target) { calls++; h }
        assertEquals(2, calls)
        val (_, l) = RaLaunchHash.of(fp, target.copy(stamp = null)) { h }
        assertNull(l)
    }

    @Test fun known_then_unknown_forgets_the_entry() = runTest {
        val fp = fp()
        val (_, l) = RaLaunchHash.of(fp, target) { h }; RaLaunchHash.answer(fp, l!!, h, 42)
        val (_, hit) = RaLaunchHash.of(fp, target) { h }; RaLaunchHash.answer(fp, hit!!, h, 0)
        var calls = 0; RaLaunchHash.of(fp, target) { calls++; h }
        assertEquals(1, calls)
    }

    @Test fun a_raw_archive_hash_is_never_stored() = runTest {
        val fp = fp(); val raw = target.copy(storable = false)
        val (_, l) = RaLaunchHash.of(fp, raw) { h }; RaLaunchHash.answer(fp, l!!, h, 42)
        var calls = 0; RaLaunchHash.of(fp, raw) { calls++; h }
        assertEquals(1, calls)
    }

    @Test fun an_unknown_game_is_cached_too() = runTest {
        val fp = fp()
        val (_, l) = RaLaunchHash.of(fp, target) { h }; RaLaunchHash.answer(fp, l!!, h, 0)
        var calls = 0
        val (again, hit) = RaLaunchHash.of(fp, target) { calls++; "other" }
        assertEquals(h, again); assertEquals(0, calls); assertEquals(RaFingerprint(h, 0), hit!!.cached)
    }
}
