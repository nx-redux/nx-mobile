package com.nxredux.mobile.settings

import android.content.SharedPreferences
import com.swordfish.libretrodroid.NxLog
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The call into the main process's [PrefsProvider], in plain Kotlin types (the provider converts to and from a Bundle
 * at its own edge). [args] and the result hold Boolean, Int, Long, Float, String, `Set<String>`, `List<String>` and maps.
 */
fun interface PrefsChannel {
    fun call(method: String, name: String, args: Map<String, Any?>): Map<String, Any?>
}

/**
 * A [SharedPreferences] for the game process: the main process owns the file, so a copy kept here would go stale in both
 * directions. Reads are served from a snapshot and never wait on the provider once one exists (they run on key events,
 * and a dead menu process would be cold-started by the call); the snapshot is fetched again on [background] when the
 * owner reports a change ([changed]) or, as a fallback, when a read finds it older than [MAX_AGE_MS]. Only a read before
 * the first snapshot fetches on the reading thread. A failed fetch keeps the old snapshot (an empty one at first), and the
 * next stale read retries. A write goes to the owner at once (apply and commit alike) and updates the snapshot. Change
 * listeners are not supported: nothing in the game process uses them.
 */
class ProviderPreferences(
    val name: String,
    private val channel: PrefsChannel,
    private val background: Executor = BACKGROUND,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : SharedPreferences {
    private val lock = Any()
    /** Null until the first fetch answers or fails. */
    private var snapshot: HashMap<String, Any?>? = null
    private var fetchedAt = 0L
    /** Bumped by every write and change notice: a fetch that saw an older generation may predate it, so it is dropped. */
    private var generation = 0
    private var refreshing = false

    init { refresh() }

    /** The owner changed this file: fetch it again off the calling thread. */
    fun changed() = refresh()

    private fun refresh() {
        synchronized(lock) { generation++; if (refreshing) return; refreshing = true }
        background.execute(::refetch)
    }

    private fun fetch(): HashMap<String, Any?> = HashMap(channel.call(PrefsProvider.METHOD_GET_ALL, name, emptyMap()))

    private fun refetch() {
        while (true) {
            val seen = synchronized(lock) { generation }
            val result = runCatching(::fetch)
            synchronized(lock) {
                result.onFailure {
                    NxLog.w("NXAPP", "settings refresh ($name): ${it.message}")
                    if (snapshot == null) snapshot = HashMap()
                    fetchedAt = now(); refreshing = false
                    return
                }
                if (seen == generation) { snapshot = result.getOrThrow(); fetchedAt = now(); refreshing = false; return }
            }
        }
    }

    /** The snapshot to read: stale ones are served and refreshed in the background; only a missing one is fetched here. */
    private fun current(): Map<String, Any?> {
        synchronized(lock) {
            snapshot?.let {
                if (now() - fetchedAt > MAX_AGE_MS) refresh()
                return it
            }
        }
        val first = runCatching(::fetch).onFailure { NxLog.w("NXAPP", "settings read ($name): ${it.message}") }.getOrNull()
        synchronized(lock) {
            if (snapshot == null) { snapshot = first ?: HashMap(); fetchedAt = now() }
            return snapshot!!
        }
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { HashMap(current()) }
    override fun contains(key: String) = current().containsKey(key)
    override fun getString(key: String, defValue: String?) = current()[key] as? String ?: defValue
    override fun getInt(key: String, defValue: Int) = current()[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = current()[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = current()[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = current()[key] as? Boolean ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (current()[key] as? Set<String>)?.let { HashSet(it) } ?: defValues

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
                // A fresh map: a reader may hold the old one outside the lock. Before the first snapshot there is nothing
                // to patch (a partial one would hide the owner's other entries); the pending fetch brings the write too.
                val base = if (clear) HashMap() else snapshot?.let(::HashMap)
                if (base != null) {
                    remove.forEach(base::remove)
                    base.putAll(put)
                    snapshot = base
                }
                generation++
            }
            return ok
        }

        /**
         * Synchronous too, on the caller's thread: the game process can be killed any moment, and a write must not be lost
         * with it. Callers read their own write back at once, so it stays on their thread rather than going [background].
         */
        override fun apply() { commit() }
    }

    private companion object {
        const val MAX_AGE_MS = 1000L

        /** One thread for every file's fetches: they are small and rare, and none may run on the main thread. */
        val BACKGROUND: Executor = Executors.newSingleThreadExecutor { Thread(it, "nx-settings").apply { isDaemon = true } }
    }
}
