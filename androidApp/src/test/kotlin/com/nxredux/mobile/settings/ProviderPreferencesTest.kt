package com.nxredux.mobile.settings

import android.content.SharedPreferences
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
        override fun call(method: String, name: String, args: Map<String, Any?>): Map<String, Any?> {
            calls++
            if (failing) error("menu gone")
            check(method == PrefsProvider.METHOD_APPLY) { method }
            if (args[PrefsProvider.ARG_CLEAR] == true) data.clear()
            @Suppress("UNCHECKED_CAST")
            (args[PrefsProvider.ARG_REMOVE] as List<String>).forEach(data::remove)
            @Suppress("UNCHECKED_CAST")
            data.putAll(args[PrefsProvider.ARG_PUT] as Map<String, Any?>)
            return mapOf(PrefsProvider.RESULT_OK to true)
        }
    }

    /** This process's own copy of the file, as read at launch; read-only here (the wrapper never edits it). */
    private class LocalFile(val data: Map<String, Any?>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String, defValue: String?) = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int) = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = data[key] as? Boolean ?: defValue
        override fun contains(key: String) = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = error("the game process never writes the file itself")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    }

    @Test fun readsComeFromTheLocalFileWithoutChannelCalls() {
        val owner = FakeOwner()
        val prefs = ProviderPreferences("p", owner, LocalFile(mapOf("s" to "x", "i" to 7, "b" to true, "set" to setOf("a"))))
        assertEquals("x", prefs.getString("s", null))
        assertEquals(7, prefs.getInt("i", 0))
        assertTrue(prefs.getBoolean("b", false))
        assertEquals(setOf("a"), prefs.getStringSet("set", null))
        assertTrue(prefs.contains("s"))
        assertEquals(4, prefs.all.size)
        assertEquals(0, owner.calls)
    }

    @Test fun writeGoesThroughTheChannelAndIsVisibleLocally() {
        val owner = FakeOwner()
        val prefs = ProviderPreferences("p", owner, LocalFile(mapOf("k" to "old")))
        assertTrue(prefs.edit().putString("k", "new").putLong("l", 1L shl 40).putFloat("f", 1.5f).commit())
        assertEquals("new", owner.data["k"])
        assertEquals("new", prefs.getString("k", null))
        assertEquals(1L shl 40, prefs.getLong("l", 0))
        assertEquals(1.5f, prefs.getFloat("f", 0f), 0f)
        prefs.edit().putInt("n", 3).apply()   // apply is synchronous too
        assertEquals(3, owner.data["n"])
        assertEquals(3, prefs.getInt("n", 0))
    }

    @Test fun failedChannelWriteReturnsFalseAndLeavesTheLocalValue() {
        val owner = FakeOwner().apply { failing = true }
        val prefs = ProviderPreferences("p", owner, LocalFile(mapOf("k" to "old")))
        assertFalse(prefs.edit().putString("k", "new").commit())
        assertEquals("old", prefs.getString("k", null))
    }

    @Test fun removeAndClear() {
        val owner = FakeOwner(hashMapOf("a" to "1", "b" to "2", "c" to "3"))
        val prefs = ProviderPreferences("p", owner, LocalFile(mapOf("a" to "1", "b" to "2", "c" to "3")))
        prefs.edit().remove("a").commit()
        assertFalse(owner.data.containsKey("a"))
        assertFalse(prefs.contains("a"))
        assertNull(prefs.getString("a", null))
        prefs.edit().clear().putString("d", "4").commit()
        assertEquals(mapOf<String, Any?>("d" to "4"), owner.data)
        assertEquals(mapOf<String, Any?>("d" to "4"), prefs.all)
        assertNull(prefs.getString("b", null))
    }
}
