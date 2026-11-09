package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RaJournalTest {
    private val e1 = RaUnlock("me", 1, "aaaa", 100L)
    private val e2 = RaUnlock("me", 2, "aaaa", 101L)
    private val other = RaUnlock("you", 1, "aaaa", 102L)

    @Test fun append_writes_one_json_line_and_dedupes() = runTest {
        val store = FakeRaStore(); val j = RaJournal(store, RaPaths.JOURNAL)
        assertTrue(j.append(e1)); assertFalse(j.append(e1.copy(t = 999L))); assertTrue(j.append(e2))
        assertEquals("""{"u":"me","a":1,"m":"aaaa","t":100}""" + "\n" + """{"u":"me","a":2,"m":"aaaa","t":101}""" + "\n", store.text(RaPaths.JOURNAL))
        assertEquals(2, j.count("me")); assertEquals(0, j.count("you"))
    }
    @Test fun entries_filter_by_user_and_keep_bad_lines_on_rewrite() = runTest {
        val store = FakeRaStore(); store.put(RaPaths.JOURNAL, "garbage\n" + """{"u":"you","a":1,"m":"aaaa","t":102}""" + "\n")
        val j = RaJournal(store, RaPaths.JOURNAL)
        j.append(e1); j.append(e2)
        assertEquals(listOf(e1, e2), j.entries("me")); assertEquals(listOf(other, e1, e2), j.entries())
        j.remove(listOf(e1))
        assertEquals("garbage\n" + """{"u":"you","a":1,"m":"aaaa","t":102}""" + "\n" + """{"u":"me","a":2,"m":"aaaa","t":101}""" + "\n", store.text(RaPaths.JOURNAL))
    }
    @Test fun append_after_a_torn_last_line_starts_a_new_line() = runTest {
        val store = FakeRaStore(); val torn = """{"u":"me","a":9,"m":"aaaa""""
        store.put(RaPaths.JOURNAL, torn)
        val j = RaJournal(store, RaPaths.JOURNAL)
        assertTrue(j.append(e1))
        assertEquals(listOf(e1), j.entries())
        assertEquals(torn + "\n" + """{"u":"me","a":1,"m":"aaaa","t":100}""" + "\n", store.text(RaPaths.JOURNAL))
    }
    @Test fun append_failure_propagates() = runTest {
        val store = FakeRaStore().apply { failAppend = true }
        val j = RaJournal(store, RaPaths.JOURNAL)
        val failed = runCatching { j.append(e1) }.exceptionOrNull()
        assertTrue(failed is RaStoreException)
    }

    @Test fun appends_read_the_file_once_not_per_entry() = runTest {
        val store = FakeRaStore(); val j = RaJournal(store, RaPaths.JOURNAL)
        j.append(e1); j.append(e2); assertFalse(j.append(e1)); j.append(other)
        assertEquals(1, store.reads.count { it == RaPaths.JOURNAL })
        assertEquals(listOf(e1, e2, other), j.entries())
    }
    @Test fun a_file_changed_behind_the_journal_is_read_again() = runTest {
        val store = FakeRaStore(); val j = RaJournal(store, RaPaths.JOURNAL)
        j.append(e1)
        store.delete(RaPaths.JOURNAL)   // an account reset
        assertTrue(j.append(e1))
        RaJournal(store, RaPaths.JOURNAL).append(e2)   // another writer
        assertFalse(j.append(e2))
        assertEquals(listOf(e1, e2), j.entries())
    }
    // The other process removed one entry and appended one of the same length: the size alone matches what this
    // journal last saw, yet the file is another.
    @Test fun a_same_size_change_behind_the_journal_is_read_again() = runTest {
        val store = FakeRaStore()
        val game = RaJournal(store, RaPaths.JOURNAL); val menu = RaJournal(store, RaPaths.JOURNAL)
        game.append(e1)
        menu.remove(listOf(e1)); menu.append(e2)
        assertFalse(game.append(e2))
        assertEquals(listOf(e2), game.entries())
    }
    @Test fun a_removed_entry_can_be_appended_again() = runTest {
        val store = FakeRaStore(); val j = RaJournal(store, RaPaths.JOURNAL)
        j.append(e1); j.append(e2); j.remove(listOf(e1))
        assertTrue(j.append(e1)); assertFalse(j.append(e2))
        assertEquals(listOf(e2, e1), j.entries())
    }
    @Test fun a_failed_append_reads_the_file_again_and_starts_a_fresh_line() = runTest {
        val store = FakeRaStore(); val j = RaJournal(store, RaPaths.JOURNAL)
        j.append(e1)
        store.failAppend = true
        assertTrue(runCatching { j.append(e2) }.exceptionOrNull() is RaStoreException)
        store.failAppend = false
        store.put(RaPaths.JOURNAL, store.text(RaPaths.JOURNAL) + """{"u":"me","a":2""")   // what a torn write would leave
        assertTrue(j.append(e2))
        assertEquals(listOf(e1, e2), j.entries())
    }

    // The game process journals an unlock while the menu's sync rewrites the file without the ones it sent: two journals
    // (two processes) on one store. The append waits for the rewrite instead of landing in the file it replaces.
    @Test fun an_append_from_another_process_during_a_remove_is_kept() = runTest {
        val store = FakeRaStore()
        val game = RaJournal(store, RaPaths.JOURNAL); val menu = RaJournal(store, RaPaths.JOURNAL)
        game.append(e1)
        val gate = CompletableDeferred<Unit>()
        store.beforeWrite = { gate.await() }
        launch { menu.remove(listOf(e1)) }
        runCurrent()                        // the rewrite has read the file and waits to write
        launch { game.append(e2) }
        runCurrent()
        store.beforeWrite = {}
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(e2), menu.entries())
    }
}
