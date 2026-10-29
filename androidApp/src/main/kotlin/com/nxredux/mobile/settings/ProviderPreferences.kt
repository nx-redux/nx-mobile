package com.nxredux.mobile.settings

import android.content.SharedPreferences
import com.swordfish.libretrodroid.NxLog

/**
 * The call into the main process's [PrefsProvider], in plain Kotlin types (the provider converts to and from a Bundle
 * at its own edge). [args] and the result hold Boolean, Int, Long, Float, String, `Set<String>`, `List<String>` and maps.
 */
fun interface PrefsChannel {
    fun call(method: String, name: String, args: Map<String, Any?>): Map<String, Any?>
}

/**
 * A [SharedPreferences] for the game process: the main process owns the file, so a copy kept here would go stale in both
 * directions. Read from a snapshot, fetched again when it is older than [MAX_AGE_MS] (the menu may change settings while a game
 * plays); a write goes to the owner at once (apply and commit alike) and updates the snapshot. Change listeners are not supported: nothing in the game process uses them.
 */
class ProviderPreferences(
    private val name: String,
    private val channel: PrefsChannel,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : SharedPreferences {
    private var snapshot = HashMap<String, Any?>(channel.call(PrefsProvider.METHOD_GET_ALL, name, emptyMap()))
    private var fetchedAt = now()

    /** Call under the lock before a read. A failed fetch keeps the old snapshot. */
    private fun fresh(): HashMap<String, Any?> {
        if (now() - fetchedAt > MAX_AGE_MS) {
            runCatching { HashMap(channel.call(PrefsProvider.METHOD_GET_ALL, name, emptyMap())) }
                .onSuccess { snapshot = it; fetchedAt = now() }
                .onFailure { NxLog.w("NXAPP", "settings refresh ($name): ${it.message}"); fetchedAt = now() }
        }
        return snapshot
    }

    @Synchronized override fun getAll(): MutableMap<String, *> = HashMap(fresh())
    @Synchronized override fun contains(key: String) = fresh().containsKey(key)
    @Synchronized override fun getString(key: String, defValue: String?) = fresh()[key] as? String ?: defValue
    @Synchronized override fun getInt(key: String, defValue: Int) = fresh()[key] as? Int ?: defValue
    @Synchronized override fun getLong(key: String, defValue: Long) = fresh()[key] as? Long ?: defValue
    @Synchronized override fun getFloat(key: String, defValue: Float) = fresh()[key] as? Float ?: defValue
    @Synchronized override fun getBoolean(key: String, defValue: Boolean) = fresh()[key] as? Boolean ?: defValue

    @Suppress("UNCHECKED_CAST")
    @Synchronized override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (fresh()[key] as? Set<String>)?.let { HashSet(it) } ?: defValues

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
            val ok = runCatching { channel.call(PrefsProvider.METHOD_APPLY, name, args)[PrefsProvider.RESULT_OK] == true }.onFailure { NxLog.w("NXAPP", "settings write ($name): ${it.message}") }.getOrDefault(false)
            if (ok) synchronized(this@ProviderPreferences) {
                if (clear) snapshot.clear()
                remove.forEach(snapshot::remove)
                snapshot.putAll(put)
                fetchedAt = now()
            }
            return ok
        }

        /** Synchronous too: the game process can be killed any moment, and a write must not be lost with it. */
        override fun apply() { commit() }
    }

    private companion object {
        const val MAX_AGE_MS = 1000L
    }
}
