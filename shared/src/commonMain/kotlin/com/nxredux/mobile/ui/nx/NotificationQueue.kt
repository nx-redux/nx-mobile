package com.nxredux.mobile.ui.nx

/** [action]: a button's word on it (the layout tip's "Don't show again"), its tap reported by id; null for none. */
data class NxToast(val id: Long, val text: String, val badgePath: String?, val until: Long, val action: String? = null)
data class NxProgressPill(val title: String, val value: String, val badgePath: String?, val until: Long)

/** The in-game notification model (nx-redux `notification.c`): up to [max] stacked toasts and one progress pill. Pure; the host ticks it. */
class NotificationQueue(private val max: Int = 4) {
    var toasts: List<NxToast> = emptyList(); private set
    var pill: NxProgressPill? = null; private set
    private var nextId = 1L

    /** Adds a toast shown until `now + durationMs`; the oldest drop past [max]. A non-positive duration shows nothing and returns -1. */
    fun push(text: String, badgePath: String?, durationMs: Long, now: Long, action: String? = null): Long {
        if (durationMs <= 0) return -1
        val id = nextId++
        toasts = (toasts + NxToast(id, text, badgePath, now + durationMs, action)).takeLast(max)
        return id
    }

    /** Drops toast [id] at once (its button or Close was tapped). */
    fun dismiss(id: Long) { toasts = toasts.filterNot { it.id == id } }

    companion object {
        /** A duration that never runs out: the toast stays until closed ([dismiss]), as the layout tip. */
        const val UNTIL_CLOSED = Long.MAX_VALUE / 4
    }

    /** Shows or replaces the single progress pill; a non-positive duration clears it. */
    fun progress(title: String, value: String, badgePath: String?, durationMs: Long, now: Long) {
        pill = if (durationMs <= 0) null else NxProgressPill(title, value, badgePath, now + durationMs)
    }

    fun hideProgress() { pill = null }

    /** Drops what has expired; true when anything changed. */
    fun tick(now: Long): Boolean {
        val t = toasts.filter { it.until > now }
        val p = pill?.takeIf { it.until > now }
        val changed = t.size != toasts.size || p != pill
        toasts = t; pill = p
        return changed
    }
}
