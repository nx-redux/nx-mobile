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
 * A [SharedPreferences] for the game process. Reads come from [local], this process's own instance of the file as it
 * stood at launch, until a [refresh] replaces it with the owner's current entries: a setting changed in the menu while
 * the game was away (launcher mode's Home) reaches it when it comes back. A read never calls the menu process, so a key
 * event never waits on it. Writes go to the owner, the
 * main process ([PrefsProvider]), so its cached copy never writes over them later; once applied there they are kept in
 * memory here ([written]) and read back at once. They are never written to the file from this process: [local]'s copy
 * may be older than the owner's, and saving it would undo the menu's changes. Change listeners are not supported:
 * nothing in the game process uses them.
 */
class ProviderPreferences(
    private val name: String,
    private val channel: PrefsChannel,
    private val local: SharedPreferences,
) : SharedPreferences {
    private val lock = Any()
    /** This process's applied writes: a value, or [REMOVED]. */
    private val written = HashMap<String, Any>()
    /** A clear was applied: [local]'s (or [fetched]'s) entries are gone. */
    private var cleared = false
    /** The owner's entries as the last [refresh] read them; null: none yet, [local] stands. */
    private var fetched: Map<String, Any?>? = null
    /** Counts this process's applied writes: a [refresh] tells whether one landed while it was asking. */
    private var writes = 0L

    /**
     * [key] as this process sees it: its own write, else nothing after a clear, else [fetched]'s, else [local]'s (a
     * lookup, no copy).
     */
    @Suppress("UNCHECKED_CAST")
    private inline fun <T> read(key: String, def: T, fromLocal: () -> T): T = synchronized(lock) {
        val mine = written[key]
        val owners = fetched
        when {
            mine === REMOVED -> def
            mine != null -> mine as T
            cleared -> def
            owners != null -> (owners[key] ?: def) as T
            else -> fromLocal()
        }
    }

    /**
     * Reads the owner's current entries (one call into the main process) and reads from them from now on. Blocking:
     * never on the key-event path. A failed call keeps the view as it was. This process's own writes stay in front only
     * if one landed while the call ran (the owner's answer may predate it); otherwise the owner's entries already hold
     * them, and a later change there wins.
     */
    fun refresh() {
        val startedAt = synchronized(lock) { writes }
        @Suppress("UNCHECKED_CAST")
        val entries = runCatching { channel.call(PrefsProvider.METHOD_GET_ALL, name, emptyMap())[PrefsProvider.RESULT_ENTRIES] as Map<String, Any?> }
            .onFailure { NxLog.w("NXAPP", "settings read ($name): ${it.message}") }
            .getOrNull() ?: return
        synchronized(lock) {
            fetched = HashMap(entries)
            if (writes == startedAt) { written.clear(); cleared = false }
        }
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) {
        val out = HashMap<String, Any?>(if (cleared) emptyMap() else fetched ?: local.all)
        for ((k, v) in written) if (v === REMOVED) out.remove(k) else out[k] = v
        out
    }

    override fun contains(key: String) = synchronized(lock) {
        val mine = written[key]
        if (mine != null) mine !== REMOVED else !cleared && (fetched?.containsKey(key) ?: local.contains(key))
    }
    override fun getString(key: String, defValue: String?) = read(key, defValue) { local.getString(key, defValue) }
    override fun getInt(key: String, defValue: Int) = read(key, defValue) { local.getInt(key, defValue) }
    override fun getLong(key: String, defValue: Long) = read(key, defValue) { local.getLong(key, defValue) }
    override fun getFloat(key: String, defValue: Float) = read(key, defValue) { local.getFloat(key, defValue) }
    override fun getBoolean(key: String, defValue: Boolean) = read(key, defValue) { local.getBoolean(key, defValue) }

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
            val ok = runCatching { channel.call(PrefsProvider.METHOD_APPLY, name, args)[PrefsProvider.RESULT_OK] == true }
                .onFailure { NxLog.w("NXAPP", "settings write ($name): ${it.message}") }
                .getOrDefault(false)
            if (ok) synchronized(lock) {
                writes++
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
