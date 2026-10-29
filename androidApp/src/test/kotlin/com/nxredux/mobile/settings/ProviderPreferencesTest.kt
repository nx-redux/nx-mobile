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
        override fun call(method: String, name: String, args: Map<String, Any?>): Map<String, Any?> = also { calls++ }.run { when (method) {
            PrefsProvider.METHOD_GET_ALL -> HashMap(data)
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
        ProviderPreferences("p", owner).edit().putString("k", "v").commit()
        assertEquals("v", owner.data["k"])
    }

    @Test fun freshInstanceSeesOwnerWrite() {
        val owner = FakeOwner()
        val first = ProviderPreferences("p", owner)
        assertNull(first.getString("k", null))
        owner.data["k"] = "later"
        assertEquals("later", ProviderPreferences("p", owner).getString("k", null))
    }

    @Test fun ownWriteReadsBackAtOnce() {
        val prefs = ProviderPreferences("p", FakeOwner())
        prefs.edit().putInt("n", 3).apply()
        assertEquals(3, prefs.getInt("n", 0))
        assertTrue(prefs.contains("n"))
    }

    @Test fun typesRoundTrip() {
        val owner = FakeOwner()
        ProviderPreferences("p", owner).edit()
            .putBoolean("b", true).putInt("i", 7).putLong("l", 1L shl 40).putFloat("f", 1.5f)
            .putString("s", "x").putStringSet("set", setOf("a", "b")).commit()
        val read = ProviderPreferences("p", owner)
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
        val prefs = ProviderPreferences("p", owner)
        prefs.edit().remove("a").commit()
        assertFalse(owner.data.containsKey("a"))
        assertFalse(prefs.contains("a"))
        prefs.edit().clear().putString("d", "4").commit()
        assertEquals(mapOf<String, Any?>("d" to "4"), owner.data)
        assertEquals(mapOf<String, Any?>("d" to "4"), prefs.all)
    }

    @Test fun staleSnapshotIsRefetched() {
        val owner = FakeOwner(hashMapOf("k" to "old"))
        var clock = 0L
        val prefs = ProviderPreferences("p", owner) { clock }
        owner.data["k"] = "new"
        clock = 1001
        assertEquals("new", prefs.getString("k", null))
    }

    @Test fun freshSnapshotIsNotRefetched() {
        val owner = FakeOwner(hashMapOf("k" to "old"))
        var clock = 0L
        val prefs = ProviderPreferences("p", owner) { clock }
        owner.data["k"] = "new"
        clock = 1000
        assertEquals("old", prefs.getString("k", null))
        assertEquals(1, owner.calls)
    }
}
