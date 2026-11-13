package com.nxredux.mobile.gpudriver

/**
 * One launch's crash marker ([GpuDriverCrash]): its write and its clears, so a marker never outlives a launch that was
 * abandoned (Back, a quit) before or while it was being written. [write] sets the stored marker (null clears it); its
 * non-null write may block (a commit), which is why it runs on a background thread and why [abandon] never waits for it.
 *
 * Race-free because:
 * - [mark] writes under [lock] and only after seeing [abandoned] false, having set [touched] first; [abandon] sets
 *   [abandoned] first, then reads [touched]. Both are volatile, so at least one side sees the other: either [mark]
 *   writes nothing, or [abandon] returns true and the caller runs [clear].
 * - [clear] takes [lock], so it waits for a write in flight and then clears it; one that already finished is cleared at
 *   once. A clear after the launch was abandoned can therefore never be overtaken by the marker it clears.
 */
class GpuDriverMarkerGuard(private val write: (String?) -> Unit) {
    private val lock = Any()
    @Volatile private var abandoned = false
    @Volatile private var touched = false
    private var marked = false

    /** Writes [text] (blocking, off the main thread) unless the launch was abandoned; true when written. */
    fun mark(text: String): Boolean = synchronized(lock) {
        touched = true
        if (abandoned) return false
        write(text)
        marked = true
        true
    }

    /**
     * The launch is over (its screen is destroyed): no marker is written from now on. Never blocks. True when a marker
     * was or may be being written: the caller then runs [clear] off the main thread (it may wait for that write).
     */
    fun abandon(): Boolean {
        abandoned = true
        return touched
    }

    /**
     * Clears the marker this launch wrote, after any write in flight (it waits for it: only off the main thread then,
     * or once [mark] has returned). Nothing when none was written; true when it cleared one.
     */
    fun clear(): Boolean = synchronized(lock) {
        if (!marked) return false
        marked = false
        write(null)
        true
    }
}
