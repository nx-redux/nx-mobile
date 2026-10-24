package com.nxredux.mobile.shortcuts

import com.nxredux.mobile.options.SpeedSettings

/** What the game shows for the current speed state. */
enum class SpeedIndicator { FAST_FORWARD, REWIND }

/** What the host applies to the engine. [ffSpeed] is 0 when not fast-forwarding. */
data class SpeedOutput(
    val ffSpeed: Int,
    val ffAudio: Boolean,
    val rewinding: Boolean,
    val rewindAudio: Boolean,
    val indicator: SpeedIndicator?,
)

/** Something the host has to do besides applying [SpeedOutput]. */
sealed interface SpeedEffect {
    /** Reset always asks first; the host opens the confirm. */
    data object AskReset : SpeedEffect
}

/**
 * Fast forward and rewind from toggles and holds. Last toggle wins; Hold Rewind pauses a toggled FF and Hold FF a
 * toggled rewind, each resuming on release (or when the rewind buffer runs out); a Hold FF release never turns off a
 * toggled FF, so Toggle and Hold FF on one button can't fight over list order. Knows nothing of pause: the host keeps
 * applying [output] after a resume.
 */
class SpeedControl {
    private var settings = SpeedSettings()
    private var canRewind = false
    private var engineCant = false   // the engine gave up on rewind for this game ([onRewindUnavailable])
    private var netplay = false
    private var toggleFf = false
    private var toggleRewind = false
    private var holdFf = false
    private var holdRewind = false

    var output: SpeedOutput = compute()
        private set

    /** Rewind is offered: the core can, the Rewind row is on, and the engine hasn't given up on this game. */
    val rewindOn get() = canRewind && settings.rewind && !engineCant

    fun configure(s: SpeedSettings, canRewind: Boolean) {
        settings = s
        this.canRewind = canRewind
        if (!rewindOn) clearRewind()
        update()
    }

    fun onAction(a: ShortcutAction, down: Boolean): SpeedEffect? {
        if (netplay) return null
        when (a) {
            ShortcutAction.RESET -> return if (down) SpeedEffect.AskReset else null
            ShortcutAction.TOGGLE_FF -> if (down) {
                toggleFf = !toggleFf
                if (toggleFf) toggleRewind = false
            }
            ShortcutAction.HOLD_FF -> holdFf = down
            ShortcutAction.TOGGLE_REWIND -> if (down && rewindOn) {
                toggleRewind = !toggleRewind
                if (toggleRewind) toggleFf = false
            }
            ShortcutAction.HOLD_REWIND -> holdRewind = down && rewindOn
        }
        update()
        return null
    }

    /** Set what the toggles are directly (a touch button showing state); null leaves one alone. */
    fun setToggle(ff: Boolean? = null, rewind: Boolean? = null) {
        if (netplay) return
        if (ff != null) toggleFf = ff
        if (rewind != null) toggleRewind = rewind && rewindOn
        update()
    }

    /**
     * The engine reports the buffer empty. A toggled rewind ends (resuming a toggled FF). A held one keeps rewinding:
     * the engine alternates advance and rewind itself so the game stays near the oldest point, and a paused FF stays
     * paused until release. Ignored when not rewinding (a Hold FF pausing a toggled rewind must not clear it).
     */
    fun onRewindEmpty() {
        if (!output.rewinding || holdRewind) return
        toggleRewind = false
        update()
    }

    /**
     * The engine stepped back with no buffer built yet (still probing the core): the press did nothing, so the rewind
     * ends, held or toggled, and a toggled FF it paused plays on. Rewind stays offered. Ignored when not rewinding.
     */
    fun onRewindOff() {
        if (!output.rewinding) return
        clearRewind()
        update()
    }

    /**
     * The engine gave up on rewind for this game (no states, a state size that changes, refused serializes, no RAM):
     * rewind ends and stays off for the session, whatever [configure] says later.
     */
    fun onRewindUnavailable() {
        engineCant = true
        clearRewind()
        update()
    }

    /** Focus or the menu took the buttons: holds end, toggles stay. */
    fun releaseHolds() {
        holdFf = false
        holdRewind = false
        update()
    }

    /** Netplay and GBA link sessions: everything off, actions ignored until [netplay] is false. */
    fun setSession(netplay: Boolean) {
        this.netplay = netplay
        if (netplay) {
            toggleFf = false
            clearRewind()
            holdFf = false
        }
        update()
    }

    private fun clearRewind() {
        toggleRewind = false
        holdRewind = false
    }

    private fun update() { output = compute() }

    private fun compute(): SpeedOutput {
        val rewinding = !netplay && rewindOn && (holdRewind || (toggleRewind && !holdFf))
        val ff = !netplay && !rewinding && (holdFf || toggleFf)
        return SpeedOutput(
            ffSpeed = if (ff) settings.maxFf else 0,
            ffAudio = settings.ffAudio,
            rewinding = rewinding,
            rewindAudio = settings.rewindAudio,
            indicator = when {
                rewinding -> SpeedIndicator.REWIND
                ff -> SpeedIndicator.FAST_FORWARD
                else -> null
            },
        )
    }
}
