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
 * A [SharedPreferences] for the game process. Reads come from [local], this process's own instance of the file: the
 * game process is new for every game, so it reads the file as it stood at launch (a setting changed in the menu meanwhile
 * reaches the next game), never calling the menu process, so a key event never waits on it. Writes go to the owner, the
 * main process ([PrefsProvider]), so its cached copy never writes over them later; once applied there they are kept in
 * memory here ([written]) and read back at once. They are never written to the file from this process: [local]'s copy
 * may be older than the owner's, and saving it would undo the menu's changes. Change listeners are not supported:
 * nothing in the game process uses them.
 */
class ProviderPreferences(
    val name: String,
    private val channel: PrefsChannel,
    private val local: SharedPreferences,
) : SharedPreferences {
    private val lock = Any()
    /** This process's applied writes: a value, or [REMOVED]. */
    private val written = HashMap<String, Any>()
    /** A clear was applied: [local]'s entries are gone. */
    private var cleared = false

    /** [key] as this process sees it: its own write, else nothing after a clear, else [local]'s (a lookup, no copy). */
    @Suppress("UNCHECKED_CAST")
    private inline fun <T> read(key: String, def: T, fromLocal: () -> T): T = synchronized(lock) {
        val mine = written[key]
        when {
            mine === REMOVED -> def
            mine != null -> mine as T
            cleared -> def
            else -> fromLocal()
        }
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) {
        val out = HashMap<String, Any?>(if (cleared) emptyMap() else local.all)
        for ((k, v) in written) if (v === REMOVED) out.remove(k) else out[k] = v
        out
    }

    override fun contains(key: String) = synchronized(lock) {
        val mine = written[key]
        if (mine != null) mine !== REMOVED else !cleared && local.contains(key)
    }
    override fun getString(key: String, defValue: String?) = read(key, defValue) { local.getString(key, defValue) }
    override fun getInt(key: String, defValue: Int) = read(key, defValue) { local.getInt(key, defValue) }
    override fun getLong(key: String, defValue: Long) = read(key, defValue) { local.getLong(key, defValue) }
    override fun getFloat(key: String, defValue: Float) = read(key, defValue) { local.getFloat(key, defValue) }
    override fun getBoolean(key: String, defValue: Boolean) = read(key, defValue) { local.getBoolean(key, defValue) }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        read<Set<String>?>(key, defValues) { local.getStringSet(key, defValues) }?.let { HashSet(it) }

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
            if (ok) synchronized(lock) {
                if (clear) { cleared = true; written.clear() }
                remove.forEach { written[it] = REMOVED }
                put.forEach { (k, v) -> if (v != null) written[k] = v }
            }
            return ok
        }

        /**
         * Synchronous too, on the caller's thread: the game process can be killed any moment, and a write must not be lost
         * with it; callers read their own write back at once.
         */
        override fun apply() { commit() }
    }

    private companion object {
        val REMOVED = Any()
    }
}
