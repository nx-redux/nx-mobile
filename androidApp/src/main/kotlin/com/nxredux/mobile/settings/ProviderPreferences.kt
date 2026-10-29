package com.nxredux.mobile.settings

import android.content.SharedPreferences

/**
 * The call into the main process's [PrefsProvider], in plain Kotlin types (the provider converts to and from a Bundle
 * at its own edge). [args] and the result hold Boolean, Int, Long, Float, String, `Set<String>`, `List<String>` and maps.
 */
fun interface PrefsChannel {
    fun call(method: String, name: String, args: Map<String, Any?>): Map<String, Any?>
}

/**
 * A [SharedPreferences] for the game process: the main process owns the file, so a copy kept here would go stale in both
 * directions. Read from a snapshot taken on construction; a write goes to the owner at once (apply and commit alike) and
 * updates the snapshot. Change listeners are not supported: nothing in the game process uses them.
 */
class ProviderPreferences(private val name: String, private val channel: PrefsChannel) : SharedPreferences {
    private val snapshot = HashMap<String, Any?>(channel.call(PrefsProvider.METHOD_GET_ALL, name, emptyMap()))

    @Synchronized override fun getAll(): MutableMap<String, *> = HashMap(snapshot)
    @Synchronized override fun contains(key: String) = snapshot.containsKey(key)
    @Synchronized override fun getString(key: String, defValue: String?) = snapshot[key] as? String ?: defValue
    @Synchronized override fun getInt(key: String, defValue: Int) = snapshot[key] as? Int ?: defValue
    @Synchronized override fun getLong(key: String, defValue: Long) = snapshot[key] as? Long ?: defValue
    @Synchronized override fun getFloat(key: String, defValue: Float) = snapshot[key] as? Float ?: defValue
    @Synchronized override fun getBoolean(key: String, defValue: Boolean) = snapshot[key] as? Boolean ?: defValue

    @Suppress("UNCHECKED_CAST")
    @Synchronized override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (snapshot[key] as? Set<String>)?.let { HashSet(it) } ?: defValues

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        throw UnsupportedOperationException("The game process's settings have no change listeners")

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val put = HashMap<String, Any?>()
        private val remove = HashSet<String>()
        private var clear = false

        private fun set(key: String, value: Any): SharedPreferences.Editor { remove.remove(key); put[key] = value; return this }

        override fun putString(key: String, value: String?) = if (value == null) remove(key) else set(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = if (values == null) remove(key) else set(key, HashSet(values))
        override fun putInt(key: String, value: Int) = set(key, value)
        override fun putLong(key: String, value: Long) = set(key, value)
        override fun putFloat(key: String, value: Float) = set(key, value)
        override fun putBoolean(key: String, value: Boolean) = set(key, value)
        override fun remove(key: String): SharedPreferences.Editor { put.remove(key); remove.add(key); return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }

        override fun commit(): Boolean {
            val args = mapOf(PrefsProvider.ARG_PUT to HashMap(put), PrefsProvider.ARG_REMOVE to remove.toList(), PrefsProvider.ARG_CLEAR to clear)
            val ok = runCatching { channel.call(PrefsProvider.METHOD_APPLY, name, args)[PrefsProvider.RESULT_OK] == true }.getOrDefault(false)
            if (ok) synchronized(this@ProviderPreferences) {
                if (clear) snapshot.clear()
                remove.forEach(snapshot::remove)
                snapshot.putAll(put)
            }
            return ok
        }

        /** Synchronous too: the game process can be killed any moment, and a write must not be lost with it. */
        override fun apply() { commit() }
    }
}
