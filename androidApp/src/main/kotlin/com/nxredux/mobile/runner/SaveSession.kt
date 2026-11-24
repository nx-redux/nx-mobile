package com.nxredux.mobile.runner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.test.NxTestResults
import com.nxredux.mobile.ui.nx.SlotUi
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.ScreenRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What [SaveSession] reads from [GameActivity]: the view and the state that decides whether a save may be written now.
 * Read only: the save and quit rules below rely on when the screen sets them, so nothing here may.
 */
internal interface SaveSessionHost : GameScreenHost {
    val gameReady: Boolean
    val netplay: NetplaySession?
    val speed: SpeedController
    val rumbles: Array<RumbleMotor>
}

/**
 * The game's saves for one screen: save states (the slot strip, manual Save / Load, the resume slot loaded at start,
 * the Home auto state written on stop and the quit's), the battery save and clock file, and the thumbnails. Owns the
 * ordering rules between them: the GL thread's queue for every serialize, [autoWriter] for every slot-9 write, and
 * [coreLock] for the off-main native calls a destroy must not overlap. [GameActivity] forwards onStart, onStop,
 * onSaveInstanceState, onUserLeaveHint and the pause's battery save here. Main thread unless a function says otherwise.
 */
internal class SaveSession(
    private val host: SaveSessionHost,
    private val renderer: FrontendRenderer,
    private val menu: InGameMenuController,
    private val dual: DualScreenController,
) {
    companion object {
        // Instance-state key: the slot to resume from when the system recreates this activity after a process kill.
        internal const val KEY_RESUME_SLOT = "nx_resume_slot"

        /**
         * The game process's settings are a copy refreshed when it comes back to the front; a quit (a handoff from the
         * menu, a game beside the menu) may come while the copy is behind. [refresh] gets at most [timeoutMs] (it calls
         * into the menu's process, which may have to start first); then [read] answers on what is there.
         */
        internal suspend fun readFresh(timeoutMs: Long, refresh: suspend () -> Unit, read: () -> Boolean): Boolean {
            withTimeoutOrNull(timeoutMs) { refresh() }
            return read()
        }

        /**
         * The disk slot a game screen starts from: none in netplay (a session launches cold); the [saved] one when the
         * screen is recreated; else the launch's. Without Save on quit a screen recreated [afterKill] whose bundle names
         * nothing starts fresh: falling back to the launch slot would resume after a kill all the same.
         */
        internal fun startSlot(afterKill: Boolean, saved: Int?, launch: Int?, saveOnQuit: Boolean, netplay: Boolean): Int? = when {
            netplay -> null
            saved != null -> saved
            afterKill && !saveOnQuit -> null
            else -> launch
        }

        /** [writeAutoStateOnStop]: at most this long for the GL thread to start the serialize, and again to finish it. */
        private const val AUTO_SERIALIZE_WAIT_MS = 10_000L
        /** [captureThumbnail]: a frame read waits behind the GL thread's queue (a save state's serialize) and one draw. */
        private const val FRAME_READ_WAIT_MS = 1_500L
        // Process-wide, single thread: the Home auto state and its thumbnail are written off the main thread
        // in submission order, and outlive the activity (a stopped one may be destroyed before they finish).
        private val autoWriter = Executors.newSingleThreadExecutor { Thread(it, "nx-auto-state") }

        /** Blocks until every queued Home write has finished. Call off the main thread before touching slot 9. */
        fun awaitAutoWrites() { runCatching { autoWriter.submit {}.get() } }

        /** Runs [block] on [autoWriter], behind every auto-state write queued so far. */
        fun afterAutoWrites(block: () -> Unit) { autoWriter.execute(block) }
    }

    // openMenu reads no files on the main thread. The slot strip ([slotsUi]) and the last-used
    // slot are read on IO at start and after every write of them, so an open shows them at once; an open re-reads the
    // strip, the overlay sets and the presets on IO and publishes them when they land (the cached ones show meanwhile).
    val slotsUi = mutableStateOf<List<SlotUi>>(emptyList())
    @Volatile var lastSlotCache: Int? = null   // SaveStates.lastSlot(); null until the first read
        set(value) {
            if (field == value) return
            field = value
            host.activity.runOnUiThread { dual.onSlotsChanged(lastMoved = true) }   // its Save and Load tiles name the slot
        }
    private var slotsJob: Job? = null   // the newest strip refresh (main thread only); a newer one cancels it
    private val lastSlotWaiters = LastSlotWaiters()   // callbacks of cancelled refreshes too: the next one to land runs them
    var saveStates: SaveStates? = null
        private set
    private var batterySaves: BatterySaves? = null
    private var clockSave: ClockSave? = null
    var resumeSlot: Int? = null   // disk slot to load once the game runs (EXTRA_RESUME_SLOT); null = fresh
        private set
    // The quit's off-main native calls run under [coreLock] and only while ![coreGone]. An observer
    // added after the view's (so ON_DESTROY reaches it first) sets it under the lock before LibretroDroid.destroy(): a
    // quit cut short by a destroy never touches a destroyed core, nor the next game's once it loads.
    private val coreLock = Any()
    @Volatile private var coreGone = false
    // True from start until the requested resume load has finished (or failed): until then the running state
    // is the fresh boot, so it must not overwrite the resume point.
    @Volatile var resumePending = false
        private set
    private var autoWrittenOnStop = false   // onStop queued an auto state's serialize and write since the last onStart
    // onStop's serializes queued on the GL thread and not yet run: each holds the core's lock while it runs.
    private val stopSerializes = java.util.concurrent.atomic.AtomicInteger()
    // Home screenshot for the switcher (main thread only): onPause starts a PixelCopy; its bitmap is written as
    // the slot-9 thumb once onStop has queued the auto state. [homeThumbGen] drops callbacks from an older pause.
    private var homeThumb: Bitmap? = null
    private var homeThumbGen = 0
    private var homeThumbStarted = false   // the leave hint started this stop's screenshot
    private var autoWriteOk: AtomicBoolean? = null   // this stop's queued auto-state write succeeded (set on autoWriter)
    private var autoWriteFailed: AtomicBoolean? = null   // this stop's queued auto-state write threw (set on autoWriter)
    // The last stop's serialize gave no state (a core that has none): the next stop's bundle keeps the previous resume
    // point instead of naming a slot 9 that state will not replace.
    @Volatile private var autoStateEmpty = false
    // The newest resume point known to be on disk, for the instance state when this stop's own
    // auto write has already failed: the slot this screen started from (onCreate), then slot 9 once a stop's write lands.
    @Volatile private var lastResumePoint: Int? = null
    // API 26–27 only: onSaveInstanceState runs before onStop there, so its bundle is kept to be updated
    // after the onStop write (the framework parcels it only after onStop returns).
    private var pendingOutState: Bundle? = null
    private val slotJobs = mutableListOf<Job>()   // manual Save/Load in flight (main thread only); Quit joins them

    /** onCreate: the slot this screen resumes from ([KEY_RESUME_SLOT] or the launch's), decided before any stop can come. */
    fun startFrom(slot: Int?) {
        resumeSlot = slot
        resumePending = slot != null
        lastResumePoint = slot
    }

    /** The game's save states (null: none, as in netplay), once its paths are known. */
    fun open(states: SaveStates?) { saveStates = states }

    /** The battery save and clock file the game loads now and writes from here ([writeBatterySave]). */
    fun attachBattery(battery: BatterySaves, clock: ClockSave?) {
        batterySaves = battery
        clockSave = clock
    }

    /** The view's ON_DESTROY, ahead of its native destroy: waits for a quit's read in flight and stops the next ([withLiveCore]). */
    fun onCoreDestroy() { synchronized(coreLock) { coreGone = true } }

    /**
     * Leaving via Home/recents: grab the frame for the switcher preview now. This runs before the pause,
     * while the GL surface still holds a frame; by onPause GLRetroView has already paused it (no data).
     */
    fun onUserLeaveHint() {
        if (!host.quitting && host.gameReady && !resumePending) {
            val gen = ++homeThumbGen
            homeThumb = null
            homeThumbStarted = true
            captureThumbnail { bmp -> if (gen == homeThumbGen && bmp != null) onHomeThumb(bmp) }
        }
    }

    fun onStart() {
        autoWrittenOnStop = false   // a later Home must write (and record) a fresh auto state
        autoWriteOk = null
        autoWriteFailed = null
        homeThumb = null
        homeThumbStarted = false
        homeThumbGen++   // a capture still in flight belongs to the previous stop
        pendingOutState = null
    }

    /** Main thread. Before onStop's auto state: keep it for onStop. After it: queue it behind that state. */
    private fun onHomeThumb(bmp: Bitmap) {
        if (autoWrittenOnStop) queueHomeThumb(bmp) else homeThumb = bmp
    }

    /** Writes the slot-9 thumb after this stop's auto state, and only if that write succeeded (they must match). */
    private fun queueHomeThumb(bmp: Bitmap) {
        val ss = saveStates ?: return
        val ok = autoWriteOk ?: return
        autoWriter.execute {
            if (ok.get()) runCatching { ss.writeAutoThumb(bmp) }.onFailure { NxLog.w(TAG_LOG, "auto-resume thumb on stop failed", it) }
        }
    }

    /**
     * onStop: [writeAuto] queues the auto state ([writeAutoStateOnStop]); then, on API 26–27 where onSaveInstanceState
     * already ran, the outcome is recorded in its bundle.
     */
    fun onStop(writeAuto: Boolean, unlinkAuto: Boolean = false) {
        if (writeAuto) writeAutoStateOnStop()
        // On [autoWriter], behind any slot-9 write still queued from before the switch went off.
        if (unlinkAuto) unlinkAuto()
        pendingOutState?.let(::putResumeSlot)
        pendingOutState = null
    }

    /**
     * After a process kill the system recreates this activity with its original intent, so the slot to
     * resume from must travel in the instance state: the auto state onStop just wrote, or the requested
     * slot while its load is still pending. On API 28+ this runs after onStop; on 26–27 before it, so the
     * bundle is also updated from onStop (see [pendingOutState]).
     */
    fun onSaveInstanceState(outState: Bundle) {
        putResumeSlot(outState)
        pendingOutState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) outState else null
    }

    /**
     * [autoWrittenOnStop] means the auto state's serialize (GL thread) and write ([autoWriter]) were queued, not that they
     * landed. When either is already known to have failed, or the last stop's core gave no state ([autoStateEmpty]), the
     * bundle keeps the previous resume point ([lastResumePoint]) rather than naming a slot 9 this stop never replaced.
     * Usually both are still in flight here (onSaveInstanceState follows onStop at once on API 28+) and the bundle is
     * parcelled when this returns, so slot 9 is recorded optimistically. The write is an atomic rename, so a failure
     * leaves the previous slot-9 file whole: a recreation resumes an older auto state (or toasts "Resume failed" when there
     * was none), never a torn one. Blocking the main thread on the serialize or the write is not worth it for a rare failure.
     */
    private fun putResumeSlot(outState: Bundle) {
        val slot = when {
            autoWrittenOnStop && (autoWriteFailed?.get() == true || autoStateEmpty) -> lastResumePoint
            autoWrittenOnStop -> NxPaths.AUTO_SLOT
            resumePending -> resumeSlot
            else -> null
        }
        if (slot != null) outState.putInt(KEY_RESUME_SLOT, slot) else outState.remove(KEY_RESUME_SLOT)
    }

    private fun writeAutoStateOnStop() {
        val view = host.retroView ?: return
        val ss = saveStates ?: return
        if (!host.gameReady || resumePending) return
        // Serialized on the GL thread (paused, it still runs queued events, and native makes the paused view's context
        // current: a core may run emulation and GL inside it, Azahar's RunLoop drain), but not waited for here: a PS2,
        // GameCube, Wii or PSP state is tens of MB. The GL thread's queue keeps it after this frame and the FF/rewind
        // pause around it, and ahead of a resumed frame, a quit's serialize and the native destroy. The write is queued
        // on [autoWriter] now, so whatever awaits the auto writes (a quit, a resume, a slot load, the process end) waits
        // for this state too; it waits there for the GL thread's result. The files are atomic renames, so a reader
        // sees the previous state or the new one.
        val t0 = SystemClock.elapsedRealtime()
        val serialized = HandedOffResult<ByteArray>()
        stopSerializes.incrementAndGet()
        val queued = host.speed.aroundStateNow {
            runCatching {
                view.queueEvent {
                    try {
                        serialized.run {
                            val bytes = runCatching { view.serializeState() }
                                .onFailure { NxLog.e(TAG_LOG, "auto-resume state on stop failed", it) }.getOrNull()
                            NxLog.i(TAG_LOG, "auto-state on stop: serialize=${SystemClock.elapsedRealtime() - t0}ms")
                            bytes
                        }
                    } finally { stopSerializes.decrementAndGet() }
                }
            }.onFailure { NxLog.e(TAG_LOG, "auto-resume state on stop failed", it) }.isSuccess
        }
        if (!queued) { stopSerializes.decrementAndGet(); return }
        autoWrittenOnStop = true
        val ok = AtomicBoolean(false).also { autoWriteOk = it }
        val failed = AtomicBoolean(false).also { autoWriteFailed = it }
        autoWriter.execute {
            val bytes = serialized.await(AUTO_SERIALIZE_WAIT_MS)
            if (bytes == null || bytes.isEmpty()) {
                // No state (the core has none yet, or the GL thread never ran it): the previous resume point stays.
                autoStateEmpty = bytes != null
                failed.set(true)
                return@execute
            }
            autoStateEmpty = false
            val t1 = SystemClock.elapsedRealtime()
            runCatching {
                ss.writeAuto(bytes)
                ok.set(true)   // the state is replaced: its thumb must follow even if the slot pointer fails
                lastResumePoint = NxPaths.AUTO_SLOT
            }.onFailure { failed.set(true); NxLog.e(TAG_LOG, "auto-resume state on stop failed", it) }
            if (ok.get()) runCatching { ss.markAuto(onlyIfNotManual = true) }
                .onFailure { NxLog.e(TAG_LOG, "auto-resume slot pointer on stop failed", it) }
            runCatching { lastSlotCache = ss.lastSlot() }   // the pointer may name slot 9 (shown as slot 1)
            NxLog.i(TAG_LOG, "auto-state on stop: write=${SystemClock.elapsedRealtime() - t1}ms bytes=${bytes.size}")
        }
        homeThumb?.let { queueHomeThumb(it) }
        homeThumb = null
        // Stopped without the leave hint (the screen off, another screen on top): the paused core's frame still matches
        // the state just queued, read behind its serialize on the GL thread.
        if (!homeThumbStarted) {
            homeThumbStarted = true
            val gen = ++homeThumbGen
            captureThumbnail(readWaitMs = 2 * AUTO_SERIALIZE_WAIT_MS) { bmp -> if (gen == homeThumbGen && bmp != null) onHomeThumb(bmp) }
        }
    }

    /**
     * Save on quit off: queues [SaveStates.unlinkAuto] on [autoWriter], behind any slot-9 write still queued from before
     * the switch went off. Run at the game's start too, so a game that never reaches onStop (a crash, a force stop)
     * leaves no pointer on a hidden state older than its in-game saves.
     */
    fun unlinkAuto() {
        val ss = saveStates ?: return
        autoWriter.execute { runCatching { ss.unlinkAuto() }.onFailure { NxLog.w(TAG_LOG, "unlinking the hidden state failed", it) } }
    }

    /** Loads the resume slot right after start, like minarch's State_resume; a failure keeps the fresh boot. */
    suspend fun resumeAtFirstFrame(view: GLRetroView, dataRoot: File, paths: NxPaths) {
        val slot = resumeSlot
        if (slot != null) resumeFrom(view, slot, dataRoot, paths) else resumePending = false
    }

    private suspend fun resumeFrom(view: GLRetroView, slot: Int, dataRoot: File, paths: NxPaths) {
        var ok = false
        try {
            val ss = saveStates ?: return
            ok = withContext(Dispatchers.IO) {
                awaitAutoWrites()   // a Home write of this game may still be landing
                runCatching { ss.readSlotState(slot)?.let { host.speed.aroundState { view.unserializeState(it) } } ?: false }.getOrDefault(false)
            }
            if (!ok) host.toast("Resume failed")
        } finally {
            if (BuildConfig.DEBUG) {
                NxTestResults.write(host.activity, "resume", "slot" to slot, "ok" to ok, "path" to File(dataRoot, paths.state(slot)).absolutePath)
            }
            resumePending = false
        }
    }

    /**
     * The core asked to end without HOME this session (a game's own "Wii Menu", a channel launch): the resume point on
     * disk is an older session's, behind the game's own save on the NAND. Dropped, in order with the auto writes, so the
     * next launch boots fresh.
     */
    suspend fun dropAutoInOrder() {
        withContext(Dispatchers.IO) { saveStates?.let { ss -> autoWriter.submit { ss.dropAuto() }.get() } }
    }

    /**
     * The Wii's HOME: the resume point (slot 9, its thumb and the pointer) saved before the core's HOME menu opens, on
     * [autoWriter] so it lands in order with an on-stop write of the same files. [onSaved] runs off the main thread once
     * it is on disk; [done] on main, saved or not. Tracked as a slot job: Quit waits for it.
     */
    fun writeHomeResumePoint(view: GLRetroView, ss: SaveStates, onSaved: () -> Unit, done: () -> Unit) {
        captureThumbnail { bmp ->
            host.lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val bytes = host.speed.aroundState { view.serializeState() }
                    check(bytes.isNotEmpty()) { "core returned no state" }
                    autoWriter.submit {
                        if (bmp != null) ss.writeAutoThumb(bmp)
                        ss.writeAuto(bytes)
                        ss.markAuto(onlyIfNotManual = false)
                        lastResumePoint = NxPaths.AUTO_SLOT
                    }.get()
                    onSaved()
                }.onFailure { NxLog.w(TAG_LOG, "resume point before Wii HOME failed", it) }
                withContext(Dispatchers.Main) { done() }
            }.also { trackSlotJob(it) }
        }
    }

    /**
     * The quit's saves, minarch's Menu_autosaveQuit: screenshot + slot 9 + point the slot file at it, then [finish]. The
     * battery save is written once, off the main thread, before [finish] (so the write lands before the native destroy);
     * on a handoff this runs inside the new screen's onCreate, which it never blocks (the new game waits for this
     * screen's release). The core is read first (state, then SRAM, back to back under [coreLock]), and only then comes
     * the wait (a queued Home write), so a destroy during the wait never finds the serialize still ahead of it. [chosen]:
     * the player picked Quit, so the slot file points at the auto state as minarch does; a quit nobody chose (a newer game
     * screen took the session) leaves the player's manual slot pointer alone, as leaving via Home does
     * ([writeAutoStateOnStop]). The game must be running ([view] up, its first frame drawn). [release] runs off the main
     * thread once the core is read (true), or with the core already destroyed (false): what else takes the core's lock
     * (the achievements' unload) waits there, not on the main thread behind a queued serialize.
     */
    fun writeOnQuit(view: GLRetroView, chosen: Boolean, autoState: Boolean, release: (live: Boolean) -> Unit, finish: () -> Unit) {
        val ss = saveStates
        // While a resume is still loading, the running state is the fresh boot: keep the resume point as is. Without
        // [autoState] (the core asked to end) the running state is not worth resuming: keep it as is too.
        if (ss == null || resumePending || !autoState) {
            // Not cancellable: the system may destroy the screen during the quit (stopped, low memory), and the battery
            // save must still be written; only the finish after it is then moot.
            host.lifecycleScope.launch(NonCancellable) {
                // A manual Save/Load or a Wii HOME write still running reads the core off the main thread: it must end
                // before the destroy, which waits only for the auto writes.
                joinSlotJobs(slotJobs)
                withContext(Dispatchers.IO) {
                    val live = withLiveCore { writeBatterySave(notify = true); release(true) } != null
                    if (!live) release(false)
                }
                finish()
            }.invokeOnCompletion { quitWritten.countDown() }
            return
        }
        captureThumbnail { bmp ->
            host.lifecycleScope.launch(NonCancellable) {   // as above: a destroy mid-quit must not drop the resume point
                // A manual Save/Load still running must finish before the state is serialized.
                joinSlotJobs(slotJobs)
                // true = written, null = skipped (the core can't save states), false = failed.
                val ok: Boolean? = withContext(Dispatchers.IO) {
                    // Both reads of the core before any wait; null = the core was already destroyed (nothing to write).
                    val bytes = host.speed.aroundState {
                        withLiveCore {
                            // On the GL thread (a core may draw inside it: Azahar), waited for under the lock: the GL
                            // thread never takes coreLock, so the destroy observer just waits for it.
                            val state = runCatching { view.serializeState() }
                                .onFailure { NxLog.e(TAG_LOG, "auto-resume state failed", it) }
                            writeBatterySave(notify = true)   // local: always completes, before the native destroy
                            release(true)
                            state
                        }
                    }
                    if (bytes == null) release(false)
                    val ok = when {
                        bytes == null || bytes.isFailure -> false
                        bytes.getOrThrow().isEmpty() -> null
                        else -> runCatching {
                            // On [autoWriter], as every slot-9 write: behind a queued Home write (older, same files), and
                            // nothing queued later can land between the thumb, the state and the pointer.
                            autoWriter.submit {
                                if (bmp != null) ss.writeAutoThumb(bmp)
                                ss.writeAuto(bytes.getOrThrow())
                                ss.markAuto(onlyIfNotManual = !chosen)
                            }.get()
                            true
                        }.onFailure { NxLog.e(TAG_LOG, "auto-resume state failed", it) }.getOrDefault(false)
                    }
                    ok
                }
                if (ok == false) host.toast("Could not save resume point")
                finish()
            }.invokeOnCompletion { quitWritten.countDown() }
        }
    }

    /**
     * Counted down once the quit's write ([writeOnQuit]) has ended, also when the screen was destroyed under it.
     */
    private val quitWritten = CountDownLatch(1)

    /** Off the main thread: waits up to [timeoutMs] for the quit's write; false when it has not ended by then. */
    fun awaitQuitWrite(timeoutMs: Long): Boolean = quitWritten.await(timeoutMs, TimeUnit.MILLISECONDS)

    /**
     * Runs [block] (off-main native calls) unless the core is destroyed; null when it is. Holding [coreLock] keeps the
     * destroy (see [onCoreDestroy]) from starting under the block. Not for the main thread's own calls: they cannot
     * overlap onDestroy.
     */
    private fun <T> withLiveCore(block: () -> T): T? = synchronized(coreLock) { if (coreGone) null else block() }

    /**
     * Re-reads the slot strip (and the last-used slot) on IO and publishes it to [slotsUi]; main thread. The listing and
     * the thumbnails' bounds stay off the main thread: the previous strip shows until this lands (it differs only after
     * a write, which refreshes it too), and a newer refresh cancels an older one so a stale read never lands last.
     * [onLast] gets the last-used slot once a refresh lands (main), this one or a newer one that cancelled it.
     */
    fun refreshSlots(onLast: ((Int) -> Unit)? = null) {
        val ss = saveStates ?: run { slotsUi.value = emptyList(); return }
        lastSlotWaiters.add(onLast)
        slotsJob?.cancel()
        slotsJob = host.lifecycleScope.launch {
            val (slots, last) = withContext(Dispatchers.IO) {
                runCatching { readSlots(ss) to ss.lastSlot() }
                    .onFailure { NxLog.w(TAG_LOG, "could not list the slots", it) }.getOrNull()
            } ?: return@launch
            slotsUi.value = slots
            lastSlotCache = last
            dual.onSlotsChanged(lastMoved = false)   // its strip
            lastSlotWaiters.takeAll().forEach { it(last) }
        }
    }

    /**
     * netplay link: the game became a local one: its save states from now. The last-used slot is read with the strip,
     * off the main thread; [onLast] gets it when it lands, so an open menu can show that slot. Until then a menu or a
     * shortcut reads it as on a cold start (the cache is empty).
     */
    fun openLocal(dataRoot: File, paths: NxPaths, onLast: (Int) -> Unit) {
        saveStates = SaveStates(dataRoot, paths)
        lastSlotCache = null
        refreshSlots(onLast)
    }

    /** The slot strip for [ss]: file listings and thumbnail bounds. Blocking I/O. */
    private fun readSlots(ss: SaveStates): List<SlotUi> = ss.slots().map { slotUi(it, "Slot ${it.index}") }

    /**
     * One slot as the strip shows it; the thumbnail's ref carries its stamp and the state's time, so a new save reloads
     * it even when the screenshot kept its old modification time ([contentStamp]). Blocking I/O.
     */
    private fun slotUi(s: SaveStates.Slot, label: String): SlotUi {
        val sub = if (s.occupied) formatSlotTime(s.timestampMillis) else "empty"
        val thumb = s.thumb?.let { FileRefs.versioned(it.absolutePath, it.contentStamp() xor s.timestampMillis) }
        return SlotUi(
            label = label,
            subtitle = sub,
            thumbPath = thumb,
            occupied = s.occupied,
            aspect = s.thumb?.let { thumbAspect(it) } ?: (4f / 3f),
        )
    }

    /** Reads a thumbnail's width/height (bounds only) to get its aspect, so the preview frame hugs it. */
    private fun thumbAspect(file: File): Float {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight else 4f / 3f
    }

    private fun formatSlotTime(ms: Long): String =
        java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US).format(java.util.Date(ms))

    /**
     * Refuses a manual Save / Load ([saveToSlot], [loadFromSlot]) before the first frame or while the Resume load is still
     * to land, as pressWiiHome and swapDisc do: a Save would capture the fresh boot over the user's slot (and move the
     * pointer to it), and a Load would be overwritten by the resume's unserialize while the pointer says the loaded slot.
     */
    private fun slotsBusy(): Boolean {
        // A quit is serializing its resume point: a Load would move the pointer off it, a Save race its read of the core.
        if (host.quitting) return true
        if (host.gameReady && !resumePending) return false
        host.toast("Still loading")
        return true
    }

    fun saveToSlot(slot: Int) {
        val view = host.retroView ?: return
        val ss = saveStates ?: return
        if (slotsBusy()) return
        captureThumbnail { bmp ->
            trackSlotJob(host.lifecycleScope.launch(Dispatchers.IO) {
                val ok = runCatching {
                    // Empty = the core refused (or is gone): keep the slot's old state and report the failure.
                    val bytes = host.speed.aroundState { view.serializeState() }
                    check(bytes.isNotEmpty()) { "core returned no state" }
                    ss.writeState(slot, bytes)
                    if (bmp != null) ss.writeThumb(slot, bmp)
                }.isSuccess
                withContext(Dispatchers.Main) {
                    if (ok) { lastSlotCache = slot; refreshSlots(); host.toast("Saved to slot $slot") } else host.toast("Save failed")
                }
            })
        }
    }

    /** Loads manual [slot] and records it as the last used one; the menu closes on success. */
    fun loadFromSlot(slot: Int) {
        val view = host.retroView ?: return
        val ss = saveStates ?: return
        if (slotsBusy()) return
        // The read is in the job too, not on main: a state file is up to tens of MB (PSP and DC).
        trackSlotJob(host.lifecycleScope.launch(Dispatchers.IO) {
            val read = runCatching { ss.readState(slot) }
                .onFailure { NxLog.e(TAG_LOG, "could not read slot $slot", it) }
            if (read.getOrNull() == null && read.isSuccess) return@launch   // empty slot: ignore (the menu stays open)
            val ok = read.getOrNull()?.let { bytes -> runCatching { host.speed.aroundState { view.unserializeState(bytes) } }.getOrDefault(false) } ?: false
            if (ok) runCatching { saveStates?.rememberSlot(slot) }
                .onFailure { NxLog.e(TAG_LOG, "could not record last slot", it) }
            // The loaded state's rumble may stay latched unreported: start from still rather than re-apply the old one.
            withContext(Dispatchers.Main) {
                if (ok) {
                    host.rumbles.forEach { it.forget() }
                    lastSlotCache = slot   // the pointer moved: a save or load right after follows it before the refresh lands
                    refreshSlots()
                    menu.closeMenu()
                } else host.toast("Load failed")
            }
        })
    }

    /** Main thread only (menu handlers and the main-looper PixelCopy callback). */
    private fun trackSlotJob(job: Job) {
        slotJobs.removeAll { !it.isActive }
        slotJobs += job
    }

    /**
     * The game's picture for a save state: one picture of the game alone, the screen the picture is on ([FrontendRenderer.thumbnailSource]:
     * with a second display the picture display's; a DS / 3DS game one screen of the layout). Read from the core's own
     * frame ([GLRetroView.readFrame]), so it never depends on what a display shows at that moment (main draws black under
     * the panel, a surface may be gone or paused at a stop), at the size the save state's thumbnail is stored at
     * ([SaveStates.thumbSize]). Without a frame read (none in [readWaitMs], or no picture size yet) it is copied from the
     * display instead ([copyThumbnail]). [fullSize] (a backdrop drawn over the picture, an unlock's screenshot) always
     * copies what the display shows, at the picture's own pixel size. [cb] runs on the main thread.
     */
    fun captureThumbnail(fullSize: Boolean = false, readWaitMs: Long = FRAME_READ_WAIT_MS, cb: (Bitmap?) -> Unit) {
        val view = host.retroView ?: return cb(null)
        val source = renderer.thumbnailSource()   // taken now: the layout the picture was drawn in
        if (fullSize) return copyThumbnail(view, source, fullSize = true, cb)
        val read = frameRead(source, view.getGameGeometry()[3]) { w, h -> SaveStates.thumbSize(w, h) }
            ?: return copyThumbnail(view, source, fullSize = false, cb)
        val main = Handler(Looper.getMainLooper())
        val answered = AtomicBoolean(false)
        val fallback = Runnable { if (answered.compareAndSet(false, true)) copyThumbnail(view, source, fullSize = false, cb) }
        val src = ScreenRect(read.src.left, read.src.top, read.src.right, read.src.bottom)
        val queued = view.readFrame(src, read.width, read.height) { rgba ->
            main.post {
                if (!answered.compareAndSet(false, true)) return@post
                main.removeCallbacks(fallback)
                val bmp = rgba?.let { frameBitmap(it, read) }
                if (bmp != null) cb(bmp) else copyThumbnail(view, source, fullSize = false, cb)
            }
        }
        if (!queued) return fallback.run()
        main.postDelayed(fallback, readWaitMs)
    }

    /** A frame read's RGBA rows as a bitmap, turned to stand as the picture is shown; null when the size is off. */
    private fun frameBitmap(rgba: ByteArray, read: FrameRead): Bitmap? {
        if (rgba.size != read.width * read.height * 4) return null
        val bmp = Bitmap.createBitmap(read.width, read.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(rgba))
        if (read.quarterTurns == 0) return bmp
        // Counterclockwise as the core asks; Android turns clockwise for a positive angle.
        val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(-90f * read.quarterTurns) }, false)
        if (turned !== bmp) bmp.recycle()
        return turned
    }

    /**
     * PixelCopy of what the display with the picture shows ([source]), cut to the game's own picture ([thumbnailCrop]: no
     * bars or anything drawn around it). Only that rect is copied, straight into a bitmap of the thumbnail's size, or with
     * [fullSize] the picture's own pixel size. Null rather than a copy of a display that does not show the picture now
     * ([ThumbnailSource.copyable]): main under the panel is black, the second display's surface may be gone.
     */
    private fun copyThumbnail(view: GLRetroView, source: ThumbnailSource, fullSize: Boolean, cb: (Bitmap?) -> Unit) {
        val second = dual.drawnSecondSurface()
        if (!source.copyable(mainBlack = dual.mainShowsNoPicture(), secondDrawing = second != null)) return cb(null)
        val onSecond = second?.takeIf { source.display == ThumbnailDisplay.SECOND }
        val w = onSecond?.width ?: view.width
        val h = onSecond?.height ?: view.height
        if (w <= 0 || h <= 0) return cb(null)
        val crop = thumbnailCrop(source.picture, w, h)
        val src = crop?.let { Rect(it.x, it.y, it.x + it.width, it.y + it.height) } ?: Rect(0, 0, w, h)
        val (bw, bh) = if (fullSize) src.width() to src.height() else SaveStates.thumbSize(src.width(), src.height())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val done = PixelCopy.OnPixelCopyFinishedListener { result ->
            if (result == PixelCopy.SUCCESS) cb(bmp)
            else {
                bmp.recycle()
                cb(null)
            }
        }
        try {
            val main = Handler(Looper.getMainLooper())
            if (onSecond != null) PixelCopy.request(onSecond.surface, src, bmp, done, main) else PixelCopy.request(view, src, bmp, done, main)
        } catch (e: Exception) {
            bmp.recycle()
            cb(null)
        }
    }

    /**
     * onPause: synchronous (the process may be killed once backgrounded), unless a save state's serialize or unserialize
     * holds the core's lock (a resume still loading, a Save / Load, a Wii HOME write, the last stop's auto state; a big
     * PS2 or Wii state takes seconds): the SRAM read would wait for it on the main thread. Then it goes on the GL thread's
     * queue, where those serializes run too: it follows them without blocking main, and it always runs before the native
     * destroy queued behind it, so a game screen destroyed without a quit still writes it. Main thread.
     */
    fun writeBatterySaveOnPause() {
        val busy = resumePending || host.speed.inState || stopSerializes.get() > 0
        val view = host.retroView
        writeBatterySaveOnPause(busy, write = { writeBatterySave(notify = false) }, queueBeforeDestroy = { view?.queueBeforeDestroy(it) == true })
    }

    /**
     * Persists the battery save locally. Safe from any thread: the native side locks the core, and writes are
     * serialized on the [BatterySaves] instance so they land in call order. [notify] toasts a failure. Returns true
     * when the file changed (unchanged SRAM is not rewritten). Saves live in private storage only. The clock file
     * ([clockSave]) is written with it; either changing returns true.
     */
    fun writeBatterySave(notify: Boolean): Boolean {
        val view = host.retroView ?: return false
        val battery = batterySaves ?: return false
        if (!host.gameReady) return false
        // netplay: only a host on its folder's own core, with its saves served, writes its real saves; a copy never. Cable
        // Link writes the real save only from verified checkpoints (GameNetplay), never from the running game.
        host.netplay?.let { if (it.onCopy || it.savesAtCheckpoints) return false }
        return runCatching {
            synchronized(battery) {
                val wrote = battery.write(view.serializeSRAM(useEmulationThread = false))
                // The clock file alongside (minarch writes both together); its failure is logged, never toasted.
                val clockWrote = clockSave?.let { c ->
                    runCatching { c.write(view.serializeRTC(useEmulationThread = false)) }
                        .onFailure { NxLog.e(TAG_LOG, "clock save failed", it) }.getOrDefault(false)
                } ?: false
                wrote || clockWrote
            }
        }
            .onFailure {
                NxLog.e(TAG_LOG, "battery save failed", it)
                if (notify) host.activity.runOnUiThread { host.toast("Battery save failed") }
            }.getOrDefault(false)
    }
}

/**
 * The pause's battery save: [write] now, unless the core is [busy] with a save state; then it is handed to
 * [queueBeforeDestroy] (the GL thread, ahead of the native destroy). Written now when that refuses: never lost.
 */
internal fun writeBatterySaveOnPause(busy: Boolean, write: () -> Unit, queueBeforeDestroy: (() -> Unit) -> Boolean) {
    if (busy && queueBeforeDestroy(write)) return
    write()
}

/**
 * Waits for every job in [jobs], also one added while it waits (a Wii HOME write is tracked only once its screenshot
 * lands). Call on the thread that owns [jobs].
 */
internal suspend fun joinSlotJobs(jobs: List<Job>) {
    while (true) {
        val running = jobs.filter { it.isActive }
        if (running.isEmpty()) return
        running.forEach { it.join() }
    }
}

/** Last-used slot callbacks waiting for a strip refresh to land; main thread only. */
internal class LastSlotWaiters {
    private val waiting = mutableListOf<(Int) -> Unit>()

    fun add(onLast: ((Int) -> Unit)?) { if (onLast != null) waiting += onLast }

    fun takeAll(): List<(Int) -> Unit> = waiting.toList().also { waiting.clear() }
}
