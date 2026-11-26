package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NotificationQueue

/**
 * The text a core's on-screen message (libretro SET_MESSAGE / _EXT) shows as an in-game toast: every core's as it sends
 * it, but for the few that are noise for a player. fceumm's FDS disk insert reads as the side that went in, its eject and
 * select steps of the same flip say nothing more; Azahar's room of its own with nobody in it yet plays as on its own.
 */
object CoreMessages {
    /** A message that gives no duration of its own. */
    const val DEFAULT_MS = 3000L
    /** A core's few frames would be gone before they are read. */
    const val MIN_MS = 1500L
    /** A toast that stays over the game for minutes is in the way. */
    const val MAX_MS = 10_000L

    private const val ROOM_OPEN = "Local wireless: room open"
    private val FDS_INSERTED = Regex("""^Disk (\d+) of (\d+) Side ([A-Z]) Inserted$""")
    private val FDS_STEP = Regex("""^Disk \d+ of \d+ Side [A-Z] (Ejected|Selected)$""")

    /** The toast for [msg] from the core [coreId], or null: not shown. */
    fun toast(coreId: String, msg: String): String? {
        val text = msg.trim().takeIf { it.isNotEmpty() } ?: return null
        return when (coreId) {
            "azahar" -> text.takeIf { it != ROOM_OPEN }
            "fceumm" -> {
                if (FDS_STEP.matches(text)) return null
                val m = FDS_INSERTED.matchEntire(text) ?: return text
                val (disk, of, side) = m.destructured
                "Disk $disk of $of, side $side"
            }
            else -> text
        }
    }

    /** The core's toasts that also show on the picture when it is on the second display: news about the game itself. */
    fun alsoOnPicture(coreId: String): Boolean = coreId == "fceumm" || coreId == "azahar"

    /** How long a message the core gave [givenMs] for stays (0 or less: none given). */
    fun durationMs(givenMs: Int): Long = if (givenMs <= 0) DEFAULT_MS else givenMs.toLong().coerceIn(MIN_MS, MAX_MS)
}

/** A core message to show: its [text] for [durationMs]; [status] a line that replaces the core's previous status line. */
data class CoreToast(val text: String, val durationMs: Long, val status: Boolean)

/**
 * Which of a core's messages reach the screen, and where in the in-game toasts: [CoreMessages]' text, none while the
 * in-game menu is open (the game is held, the menu covers the toasts). A plain message seen within [REPEAT_WINDOW_MS]
 * of the last time is dropped, so a core that sends a line every frame shows it once; a status or progress line only
 * when it differs from the one up now, which it replaces.
 */
class CoreMessageToasts {
    private val lastSeen = HashMap<String, Long>()
    /** The core's toasts up now, oldest first; the status line's and the latest plain one's ids. */
    private val shown = ArrayList<Long>()
    private var statusId = -1L
    private var statusText = ""
    private var plainId = -1L
    private var plainAt = Long.MIN_VALUE

    fun take(coreId: String, msg: String, givenMs: Int, status: Boolean, now: Long, menuOpen: Boolean): CoreToast? {
        if (menuOpen) return null
        val text = CoreMessages.toast(coreId, msg) ?: return null
        if (!status) {
            lastSeen.entries.removeAll { now - it.value >= REPEAT_WINDOW_MS }
            val repeat = text in lastSeen
            lastSeen[text] = now
            if (repeat) return null
        }
        return CoreToast(text, CoreMessages.durationMs(givenMs), status)
    }

    /**
     * [msg] into [queue] when [take] lets it through; the toast shown, or null. A core's text that keeps changing (a
     * counter, a typed-text echo) never pushes the app's own toasts out: a status line replaces the last, plain lines
     * within [COALESCE_MS] of each other the latest, at most [MAX_SHOWN] of the core's are up at once (the oldest goes),
     * and with the queue full of the app's own nothing is shown.
     */
    fun show(queue: NotificationQueue, coreId: String, msg: String, givenMs: Int, status: Boolean, now: Long, menuOpen: Boolean): CoreToast? {
        val live = queue.toasts.map { it.id }.toSet()
        shown.retainAll(live)
        if (status && statusId in live && CoreMessages.toast(coreId, msg) == statusText) return null
        val t = take(coreId, msg, givenMs, status, now, menuOpen) ?: return null
        val replaced = if (status) statusId else plainId.takeIf { now - plainAt < COALESCE_MS }
        if (replaced != null && replaced in shown) drop(queue, replaced)
        while (shown.size >= MAX_SHOWN) drop(queue, shown.first())
        if (queue.toasts.size >= queue.max) {
            if (shown.isEmpty()) return null
            drop(queue, shown.first())
        }
        val id = queue.push(t.text, null, t.durationMs, now)
        if (id < 0) return null
        shown.add(id)
        if (status) { statusId = id; statusText = t.text } else { plainId = id; plainAt = now }
        return t
    }

    private fun drop(queue: NotificationQueue, id: Long) {
        queue.dismiss(id)
        shown.remove(id)
    }

    companion object {
        const val REPEAT_WINDOW_MS = 4000L
        /** Plain lines this close together are one changing line. */
        const val COALESCE_MS = 250L
        /** The core's toasts up at once, of the queue's few. */
        const val MAX_SHOWN = 2
    }
}
