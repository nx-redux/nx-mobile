package com.nxredux.mobile.runner

/**
 * When a pad laid out beside the picture may show: not before the picture's place is known (the first frame's geometry),
 * so the clusters never flash at their unplaced size, and after [timeoutMs] regardless, for a game that never draws.
 * The timeout counts only once the core runs ([onCoreRunning]: the game is loaded), from that or the layout's start,
 * whichever is later, so a slow load (a big arcade ROM) never shows a pad its first frame would replace. A load that
 * never ends (a hung copy, a netplay peer, a core stuck loading) still shows the pads [loadCapMs] after the layout, so
 * a touch-only player keeps MENU.
 * Times are any monotonic clock's milliseconds.
 */
internal class PadReveal(val timeoutMs: Long = 1500, val loadCapMs: Long = LOAD_CAP_MS) {
    private var startedMs: Long? = null
    private var runningMs: Long? = null
    private var pictureKnown = false

    /** A new layout of the pad: hidden again until [onPictureKnown] or the timeout. */
    fun onLayoutStart(nowMs: Long) {
        startedMs = nowMs
        pictureKnown = false
    }

    /** The core loaded the game and runs: the timeout starts counting (the first call only; a new surface is no reload). */
    fun onCoreRunning(nowMs: Long) {
        if (runningMs == null) runningMs = nowMs
    }

    /** The picture was placed from the core's geometry: the pad may show. */
    fun onPictureKnown() {
        pictureKnown = true
    }

    /** Whether a pad is waiting now: a layout started, its picture not known yet. */
    fun pending(): Boolean = startedMs != null && !pictureKnown

    /**
     * How long from [nowMs] until the timeout shows the pad: [timeoutMs] from the later of the layout's start and the
     * core's run, or [loadCapMs] from the layout's start while the core is not running yet; null without a layout.
     */
    fun timeoutInMs(nowMs: Long): Long? {
        val started = startedMs ?: return null
        val running = runningMs ?: return (started + loadCapMs - nowMs).coerceAtLeast(0L)
        return (maxOf(started, running) + timeoutMs - nowMs).coerceAtLeast(0L)
    }

    companion object {
        /** The longest the pads wait for a core that never runs, ms. */
        const val LOAD_CAP_MS = 8000L
    }

    /** Whether the pad shows at [nowMs]; true before any layout (nothing to wait for). */
    fun shouldShow(nowMs: Long): Boolean {
        if (startedMs == null || pictureKnown) return true
        return timeoutInMs(nowMs) == 0L
    }
}

/** What a frontend value change does to the portrait band's wait for the first frame ([bandGate]). */
internal enum class BandGate { NONE, RELAYOUT, START_WAIT, STOP_WAIT }

/**
 * The portrait layout after a change of Portrait Pad or of the frame ([builtHidden]: the layout was built with the band
 * hidden; [hiddenNow]: it would be now; [bandShown]: the band is laid out; [waiting]: it waits unseen for the first
 * frame; [waitsNow]: it would now, [com.nxredux.mobile.layout.bandAwaitsFrame]). A flip of hidden lays out again; else a
 * shown band starts or stops waiting (the launch's real values arrive after onCreate's layout, which had the defaults).
 */
internal fun bandGate(builtHidden: Boolean, hiddenNow: Boolean, bandShown: Boolean, waiting: Boolean, waitsNow: Boolean): BandGate = when {
    builtHidden != hiddenNow -> BandGate.RELAYOUT
    !bandShown || waiting == waitsNow -> BandGate.NONE
    waitsNow -> BandGate.START_WAIT
    else -> BandGate.STOP_WAIT
}
