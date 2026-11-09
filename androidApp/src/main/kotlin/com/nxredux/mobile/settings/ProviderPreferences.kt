package com.nxredux.mobile.settings

import android.content.SharedPreferences
import com.swordfish.libretrodroid.NxLog
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

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
 * main process ([PrefsProvider]), so its cached copy never writes over them later; they are kept in memory here
 * ([written]) and read back at once. [apply] keeps the value here at once and sends it on [sender], one at a time in
 * order: the call may have to cold-start the menu's process, which must not stall the UI thread. [commit] waits for its
 * send (and every one queued before it). They are never written to the file from this process: [local]'s copy
 * may be older than the owner's, and saving it would undo the menu's changes. Change listeners are not supported:
 * nothing in the game process uses them.
 */
class ProviderPreferences(
    private val name: String,
    private val channel: PrefsChannel,
    private val local: SharedPreferences,
    private val sender: Executor = SENDER,
) : SharedPreferences {
    private val lock = Any()
    /** This process's applied writes: a value, or [REMOVED]. */
    private val written = HashMap<String, Any>()
    /** A clear was applied: [local]'s (or [fetched]'s) entries are gone. */
    private var cleared = false
    /** The owner's entries as the last [refresh] read them; null: none yet, [local] stands. */
    private var fetched: Map<String, Any?>? = null
    /** Counts this process's writes kept in [written]: a [refresh] tells whether one came while it was asking. */
    private var writes = 0L
    /** [apply] sends queued and not yet answered: the owner's entries may not hold them yet. */
    private var unsent = 0
    /**
     * [apply]s whose send failed, by key (a value or [REMOVED]): the owner never got them, so a [refresh] keeps them in
     * [written]. A later successful send of the same key drops it (the owner holds the newer value).
     */
    private val failed = HashMap<String, Any>()
    /** An applied clear never reached the owner: its entries still stand there, so a [refresh] keeps every write here. */
    private var failedClear = false

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
     * if one landed while the call ran (the owner's answer may predate it), or if its send failed (the owner never got
     * it); otherwise the owner's entries already hold them, and a later change there wins.
     */
    fun refresh() {
        val startedAt = beginRefresh()
        @Suppress("UNCHECKED_CAST")
        val entries = runCatching { channel.call(PrefsProvider.METHOD_GET_ALL, name, emptyMap())[PrefsProvider.RESULT_ENTRIES] as Map<String, Any?> }
            .onFailure { NxLog.w("NXAPP", "settings read ($name): ${it.message}") }
            .getOrNull() ?: return
        endRefresh(startedAt, entries)
    }

    /** [writes] now, or -1 when an [apply] is still on its way: the owner's answer may predate it, so [written] stays. */
    private fun beginRefresh(): Long = synchronized(lock) { if (unsent > 0) -1L else writes }

    private fun endRefresh(startedAt: Long, entries: Map<String, Any?>) = synchronized(lock) {
        fetched = HashMap(entries)
        if (startedAt >= 0 && writes == startedAt && !failedClear) {
            written.clear()
            written.putAll(failed)
            cleared = false
        }
    }

    /** A send's outcome for [failed]; under [lock]. */
    private fun sent(ok: Boolean, put: Map<String, Any?>, remove: Collection<String>, clear: Boolean) {
        if (ok) {
            if (clear) { failed.clear(); failedClear = false }
            remove.forEach(failed::remove)
            put.keys.forEach(failed::remove)
        } else {
            if (clear) { failedClear = true; failed.clear() }
            remove.forEach { failed[it] = REMOVED }
            put.forEach { (k, v) -> if (v != null) failed[k] = v }
        }
    }

    /** Keeps an edit in [written]; under [lock]. */
    private fun keep(put: Map<String, Any?>, remove: Collection<String>, clear: Boolean) {
        writes++
        if (clear) { cleared = true; written.clear() }
        remove.forEach { written[it] = REMOVED }
        put.forEach { (k, v) -> if (v != null) written[k] = v }
    }

    private fun send(args: Map<String, Any?>): Boolean =
        runCatching { channel.call(PrefsProvider.METHOD_APPLY, name, args)[PrefsProvider.RESULT_OK] == true }
            .onFailure { NxLog.w("NXAPP", "settings write ($name): ${it.message}") }
            .getOrDefault(false)

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

        private fun args() = mapOf(PrefsProvider.ARG_PUT to HashMap(put), PrefsProvider.ARG_REMOVE to remove.toList(), PrefsProvider.ARG_CLEAR to clear)

        /**
         * Blocking, behind every [apply] queued before it: for a write that must have reached the owner before the process
         * goes. Kept here only once the owner took it.
         */
        override fun commit(): Boolean {
            val args = args()
            val putNow = HashMap(put); val removeNow = remove.toList(); val clearNow = clear
            val task = FutureTask {
                send(args).also { ok -> if (ok) synchronized(lock) { keep(putNow, removeNow, clearNow); sent(true, putNow, removeNow, clearNow) } }
            }
            // Already on the sender (a send's own code): waiting for a task queued behind this one would never end.
            if (onSender.get() == true) { task.run(); return runCatching { task.get() }.getOrDefault(false) }
            sender.execute { asSender(task::run) }
            return runCatching { task.get() }.getOrDefault(false)
        }

        /**
         * Read back at once, sent in order on [sender]. A failed send keeps the value for this process's session (the
         * owner never got it; logged), through later refreshes too ([failed]). The process ending waits for the queue
         * ([awaitSends]).
         */
        override fun apply() {
            val args = args()
            val putNow = HashMap(put); val removeNow = remove.toList(); val clearNow = clear
            synchronized(lock) { keep(putNow, removeNow, clearNow); unsent++ }
            sender.execute {
                var ok = false
                try { ok = asSender { send(args) } } finally { synchronized(lock) { sent(ok, putNow, removeNow, clearNow); unsent-- } }
            }
        }
    }

    companion object {
        private val REMOVED = Any()

        /** Set while a send runs on the sender thread: a [SharedPreferences.Editor.commit] there runs inline. */
        private val onSender = ThreadLocal<Boolean>()

        private inline fun <T> asSender(block: () -> T): T {
            val outer = onSender.get()
            onSender.set(true)
            try { return block() } finally { if (outer == null) onSender.remove() }
        }

        /** One thread for every file's sends: a write never overtakes an earlier one. */
        private val SENDER: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "prefs-send").apply { isDaemon = true } }

        /**
         * Waits up to [timeoutMs] for every send queued so far (the default sender's): before the game process ends, so
         * an [apply] just made is not lost with it. False on a timeout.
         */
        fun awaitSends(timeoutMs: Long): Boolean =
            runCatching { SENDER.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS); true }.getOrDefault(false)

        /**
         * [refresh] for every file in [prefs] in one call into the owner: a call may have to start the menu's process, so
         * one start, not one per file. Blocking, as [refresh]; a failed call keeps every view as it was, and a file the
         * answer leaves out keeps its own.
         */
        fun refreshAll(channel: PrefsChannel, prefs: Collection<ProviderPreferences>) {
            if (prefs.isEmpty()) return
            val started = prefs.associateWith { it.beginRefresh() }
            @Suppress("UNCHECKED_CAST")
            val files = runCatching {
                channel.call(PrefsProvider.METHOD_GET_ALL_FILES, "", mapOf(PrefsProvider.ARG_NAMES to prefs.map { it.name }))[PrefsProvider.RESULT_FILES] as Map<String, Map<String, Any?>>
            }.onFailure { NxLog.w("NXAPP", "settings read: ${it.message}") }.getOrNull() ?: return
            for ((p, startedAt) in started) files[p.name]?.let { p.endRefresh(startedAt, it) }
        }
    }
}
