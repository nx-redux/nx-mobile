package com.nxredux.mobile.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPreferencesTest {
    /** Stands in for the provider: the owner's entries in a map, the same call protocol. */
    private class FakeOwner(val data: MutableMap<String, Any?> = HashMap()) : PrefsChannel {
        var calls = 0
        var failing = false
        /** Runs after the entries are copied for a fetch, before the answer reaches the caller. */
        var afterGetAll: (() -> Unit)? = null
        override fun call(method: String, name: String, args: Map<String, Any?>): Map<String, Any?> = also { calls++ }.run { when (method) {
            PrefsProvider.METHOD_GET_ALL -> { if (failing) error("menu gone"); HashMap(data).also { afterGetAll?.invoke() } }
            PrefsProvider.METHOD_APPLY -> {
                if (args[PrefsProvider.ARG_CLEAR] == true) data.clear()
                @Suppress("UNCHECKED_CAST")
                (args[PrefsProvider.ARG_REMOVE] as List<String>).forEach(data::remove)
                @Suppress("UNCHECKED_CAST")
                data.putAll(args[PrefsProvider.ARG_PUT] as Map<String, Any?>)
                mapOf(PrefsProvider.RESULT_OK to true)
            }
            else -> error(method)
        } }
    }

    @Test fun writeIsSeenByOwner() {
        val owner = FakeOwner()
        ProviderPreferences("p", owner, QueueExecutor()).edit().putString("k", "v").commit()
        assertEquals("v", owner.data["k"])
    }

    @Test fun freshInstanceSeesOwnerWrite() {
        val owner = FakeOwner()
        val first = ProviderPreferences("p", owner, QueueExecutor())
        assertNull(first.getString("k", null))
        owner.data["k"] = "later"
        assertEquals("later", ProviderPreferences("p", owner, QueueExecutor()).getString("k", null))
    }

    @Test fun ownWriteReadsBackAtOnce() {
        val prefs = ProviderPreferences("p", FakeOwner(), QueueExecutor())
        prefs.edit().putInt("n", 3).apply()
        assertEquals(3, prefs.getInt("n", 0))
        assertTrue(prefs.contains("n"))
    }

    @Test fun typesRoundTrip() {
        val owner = FakeOwner()
        ProviderPreferences("p", owner, QueueExecutor()).edit()
            .putBoolean("b", true).putInt("i", 7).putLong("l", 1L shl 40).putFloat("f", 1.5f)
            .putString("s", "x").putStringSet("set", setOf("a", "b")).commit()
        val read = ProviderPreferences("p", owner, QueueExecutor())
        assertTrue(read.getBoolean("b", false))
        assertEquals(7, read.getInt("i", 0))
        assertEquals(1L shl 40, read.getLong("l", 0))
        assertEquals(1.5f, read.getFloat("f", 0f), 0f)
        assertEquals("x", read.getString("s", null))
        assertEquals(setOf("a", "b"), read.getStringSet("set", null))
        assertEquals(6, read.all.size)
    }

    @Test fun removeAndClear() {
        val owner = FakeOwner(hashMapOf("a" to "1", "b" to "2", "c" to "3"))
        val prefs = ProviderPreferences("p", owner, QueueExecutor())
        prefs.edit().remove("a").commit()
        assertFalse(owner.data.containsKey("a"))
        assertFalse(prefs.contains("a"))
        prefs.edit().clear().putString("d", "4").commit()
        assertEquals(mapOf<String, Any?>("d" to "4"), owner.data)
        assertEquals(mapOf<String, Any?>("d" to "4"), prefs.all)
    }

    @Test fun staleSnapshotIsRefetchedInTheBackground() {
        val owner = FakeOwner(hashMapOf("k" to "old"))
        val background = QueueExecutor()
        var clock = 0L
        val prefs = ProviderPreferences("p", owner, background) { clock }
        background.runAll()
        owner.data["k"] = "new"
        clock = 1001
        val calls = owner.calls
        assertEquals("old", prefs.getString("k", null))   // served at once from the snapshot
        assertEquals(calls, owner.calls)                   // the reading thread made no call
        background.runAll()
        assertEquals("new", prefs.getString("k", null))
    }

    @Test fun freshSnapshotIsNotRefetched() {
        val owner = FakeOwner(hashMapOf("k" to "old"))
        val background = QueueExecutor()
        var clock = 0L
        val prefs = ProviderPreferences("p", owner, background) { clock }
        background.runAll()
        owner.data["k"] = "new"
        clock = 1000
        assertEquals("old", prefs.getString("k", null))
        background.runAll()
        assertEquals(1, owner.calls)
    }

    @Test fun firstSnapshotLoadsInTheBackground() {
        val owner = FakeOwner(hashMapOf("k" to "v"))
        val background = QueueExecutor()
        val prefs = ProviderPreferences("p", owner, background)
        assertEquals(0, owner.calls)
        background.runAll()
        assertEquals(1, owner.calls)
        assertEquals("v", prefs.getString("k", null))
        assertEquals(1, owner.calls)
    }

    @Test fun notifiedChangeIsSeenAfterTheBackgroundRefetch() {
        val owner = FakeOwner(hashMapOf("k" to "old"))
        val background = QueueExecutor()
        val prefs = ProviderPreferences("p", owner, background) { 0L }
        background.runAll()
        owner.data["k"] = "new"
        prefs.changed()
        val calls = owner.calls
        assertEquals("old", prefs.getString("k", null))
        assertEquals(calls, owner.calls)
        background.runAll()
        assertEquals("new", prefs.getString("k", null))
    }

    @Test fun refetchStartedBeforeAWriteDoesNotUndoIt() {
        val owner = FakeOwner(hashMapOf("k" to "old"))
        val background = QueueExecutor()
        val prefs = ProviderPreferences("p", owner, background) { 0L }
        background.runAll()
        owner.afterGetAll = { owner.afterGetAll = null; prefs.edit().putString("k", "mine").commit() }
        prefs.changed()
        background.runAll()   // that fetch's "old" predates the write: dropped and fetched again
        assertEquals("mine", prefs.getString("k", null))
    }

    @Test fun failedFirstFetchStartsEmptyAndRetries() {
        val owner = FakeOwner(hashMapOf("k" to "v"))
        owner.failing = true
        val background = QueueExecutor()
        var clock = 0L
        val prefs = ProviderPreferences("p", owner, background) { clock }
        background.runAll()
        assertNull(prefs.getString("k", null))
        owner.failing = false
        clock = 1001
        prefs.getString("k", null)
        background.runAll()
        assertEquals("v", prefs.getString("k", null))
    }

    @Test fun failedFirstFetchOnTheReadingThreadDoesNotThrow() {
        val owner = FakeOwner(hashMapOf("k" to "v"))
        owner.failing = true
        val prefs = ProviderPreferences("p", owner, QueueExecutor())
        assertEquals("d", prefs.getString("k", "d"))
    }
}

/** Runs queued work only when the test says so: the "background" of [ProviderPreferences]. */
class QueueExecutor : java.util.concurrent.Executor {
    private val queue = ArrayDeque<Runnable>()
    override fun execute(command: Runnable) { queue.addLast(command) }
    fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
}
