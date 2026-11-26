package com.nxredux.mobile.runner

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

    /** How long a message the core gave [givenMs] for stays (0 or less: none given). */
    fun durationMs(givenMs: Int): Long = if (givenMs <= 0) DEFAULT_MS else givenMs.toLong().coerceIn(MIN_MS, MAX_MS)
}

/** A core message to show: its [text] for [durationMs]; [status] a line that replaces the core's previous status line. */
data class CoreToast(val text: String, val durationMs: Long, val status: Boolean)

/**
 * Which of a core's messages reach the screen: [CoreMessages]' text, none while the in-game menu is open (the game is
 * held, the menu covers the toasts), and an exact repeat seen within [REPEAT_WINDOW_MS] of the last time dropped, so a
 * core that sends a line every frame shows it once.
 */
class CoreMessageToasts {
    private val lastSeen = HashMap<String, Long>()

    fun take(coreId: String, msg: String, givenMs: Int, status: Boolean, now: Long, menuOpen: Boolean): CoreToast? {
        if (menuOpen) return null
        val text = CoreMessages.toast(coreId, msg) ?: return null
        lastSeen.entries.removeAll { now - it.value >= REPEAT_WINDOW_MS }
        val repeat = text in lastSeen
        lastSeen[text] = now
        return if (repeat) null else CoreToast(text, CoreMessages.durationMs(givenMs), status)
    }

    companion object {
        const val REPEAT_WINDOW_MS = 4000L
    }
}
