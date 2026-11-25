package com.nxredux.mobile.ui.nx

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns pad/keyboard events into presses; held directions auto-repeat through [onRepeat] (default: [onPress]).
 * [menuChord] (the main menu): SELECT and START press on their release, and both held together are MENU, pressed
 * once when the second goes down, neither release pressing anything (a pad with no MENU button opens the context
 * menu so; alone, SELECT and START keep their jobs).
 */
class InputRouter(
    private val scope: CoroutineScope,
    private val onPress: (NxButton) -> Unit,
    private val initialDelayMs: Long = 400,
    private val repeatMs: Long = 90,
    private val onRepeat: (NxButton) -> Unit = onPress,
    private val menuChord: Boolean = false,
) {
    private val held = HashMap<NxButton, Job?>()

    /** SELECT / START down now ([menuChord]), and whether they made the chord (their releases then press nothing). */
    private val chordHeld = HashSet<NxButton>()
    private var chordFired = false

    fun onEvent(event: PadEvent) {
        if (menuChord && (event.button == NxButton.SELECT || event.button == NxButton.START)) return onChordKey(event)
        when (event) {
            is PadEvent.Down -> {
                // A Down for an already-held button (e.g. a stale hold whose Up was lost)
                // re-arms as a fresh press: drop the stale repeat job, then press again.
                held.remove(event.button)?.cancel()
                onPress(event.button)
                held[event.button] = if (event.button.isDirection) scope.launch {
                    delay(initialDelayMs)
                    while (true) { onRepeat(event.button); delay(repeatMs) }
                } else null
            }
            is PadEvent.Up -> held.remove(event.button)?.cancel()
        }
    }

    private fun onChordKey(event: PadEvent) {
        when (event) {
            is PadEvent.Down -> {
                chordHeld += event.button
                if (chordHeld.size == 2 && !chordFired) { chordFired = true; onPress(NxButton.MENU) }
            }
            is PadEvent.Up -> {
                if (!chordHeld.remove(event.button)) return
                if (!chordFired) onPress(event.button)
                if (chordHeld.isEmpty()) chordFired = false
            }
        }
    }

    fun releaseAll() { held.values.forEach { it?.cancel() }; held.clear(); chordHeld.clear(); chordFired = false }
}
