package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton

/**
 * L2/R2 as digital buttons from a pad's trigger axes (AXIS_LTRIGGER/AXIS_BRAKE, AXIS_RTRIGGER/AXIS_GAS, the larger of each
 * pair): a pull past [press] presses, falling back under [release] releases (the gap keeps a trigger resting near the line
 * from chattering). A trigger that has come as a key event once goes by its keys alone from then on ([onKey]), so a pad
 * that sends both a key and an axis for one pull presses once. A pull whose key fires a shortcut is taken ([take]): neither
 * the key nor the axis reaches the core until the key lets go ([untake]), on the analog triggers too ([analog]).
 *
 * Pure: the host feeds axes and keys and sends each change to the core (or its hotkeys).
 */
class TriggerAxes(private val press: Float = 0.5f, private val release: Float = 0.3f) {
    private val held = HashSet<NxButton>()       // L2/R2 the core has been given as pressed
    private val keysSeen = HashSet<NxButton>()   // L2/R2 that came as key events: their axes are ignored
    private val taken = HashSet<NxButton>()      // L2/R2 a shortcut's key holds now: the core sees them at rest
    private var lastLeft = 0f                    // the analog values as last read ([analog])
    private var lastRight = 0f

    /** The trigger values of one motion event; the presses and releases they make, L2 before R2. */
    fun onAxes(left: Float, right: Float): List<Pair<NxButton, Boolean>> {
        val out = ArrayList<Pair<NxButton, Boolean>>(2)
        fun pull(button: NxButton, v: Float) {
            if (button in keysSeen) return
            val isHeld = button in held
            if (!isHeld && v > press) set(button, true)?.let(out::add)
            else if (isHeld && v < release) set(button, false)?.let(out::add)
        }
        pull(NxButton.L2, left)
        pull(NxButton.R2, right)
        return out
    }

    /**
     * L2/R2 as a key event; true when the core should see it (a pull its axis already pressed is not pressed again, a
     * stray second up is dropped). From the first key on, that trigger's axes are ignored.
     */
    fun onKey(button: NxButton, down: Boolean): Boolean {
        keysSeen += button
        return set(button, down) != null
    }

    /**
     * [button]'s key fired a shortcut: this pull is the shortcut's. Its axis is ignored as a key's would be (a pad sends
     * both for one pull); true when the axis had pressed it already, so the host sends the core its release.
     */
    fun take(button: NxButton): Boolean {
        taken += button
        keysSeen += button
        return held.remove(button)
    }

    /** The shortcut's key let go: the trigger is the game's again. */
    fun untake(button: NxButton) { taken -= button }

    /** The shortcuts' releases will not come (backgrounded, the pad gone): every trigger is the game's again. */
    fun untakeAll() = taken.clear()

    /** Analog triggers (Dreamcast, GameCube): the values the core gets for this read, a [take]n trigger at rest. */
    fun analog(left: Float, right: Float): Pair<Float, Float> {
        lastLeft = left
        lastRight = right
        return analogNow()
    }

    /** The last [analog] values as the core should have them now (after a [take]). */
    fun analogNow(): Pair<Float, Float> =
        (if (NxButton.L2 in taken) 0f else lastLeft) to (if (NxButton.R2 in taken) 0f else lastRight)

    /** The menu opened or the game paused: the host released everything in the core, so nothing is held here. */
    fun releaseAll() {
        held.clear()
        taken.clear()   // the shortcut's release goes to the menu now
        lastLeft = 0f
        lastRight = 0f
    }

    private fun set(button: NxButton, down: Boolean): Pair<NxButton, Boolean>? {
        if (down == button in held) return null
        if (down) held += button else held -= button
        return button to down
    }
}
