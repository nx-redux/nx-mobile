package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton

/**
 * L2/R2 as digital buttons from a pad's trigger axes (AXIS_LTRIGGER/AXIS_BRAKE, AXIS_RTRIGGER/AXIS_GAS, the larger of each
 * pair): a pull past [press] presses, falling back under [release] releases (the gap keeps a trigger resting near the line
 * from chattering). A trigger that has come as a key event once goes by its keys alone from then on ([onKey]), so a pad
 * that sends both a key and an axis for one pull presses once. A pull whose key fires a shortcut is taken ([take]): neither
 * the key nor the axis reaches the core until the key lets go ([untake]), on the analog triggers too ([analog]).
 *
 * Pure: the host feeds axes and keys and sends each change to the core (or its hotkeys). The axis path reports through
 * primitives ([onAxes]' bits, [analogLeft] / [analogRight]): it runs for every joystick event, so nothing is allocated.
 */
class TriggerAxes(private val press: Float = 0.5f, private val release: Float = 0.3f) {
    private val held = HashSet<NxButton>()       // L2/R2 the core has been given as pressed
    private val keysSeen = HashSet<NxButton>()   // L2/R2 that came as key events: their axes are ignored
    private val taken = HashSet<NxButton>()      // L2/R2 a shortcut's key holds now: the core sees them at rest
    private var lastLeft = 0f                    // the analog values as last read ([analog])
    private var lastRight = 0f

    /**
     * The trigger values of one motion event; the presses and releases they make as bits ([forEachChange] reads them,
     * L2 before R2), 0 for none.
     */
    fun onAxes(left: Float, right: Float): Int =
        pull(NxButton.L2, left, L2_CHANGED, L2_DOWN) or pull(NxButton.R2, right, R2_CHANGED, R2_DOWN)

    private fun pull(button: NxButton, v: Float, changed: Int, down: Int): Int {
        if (button in keysSeen) return 0
        val isHeld = button in held
        return when {
            !isHeld && v > press -> if (set(button, true)) changed or down else 0
            isHeld && v < release -> if (set(button, false)) changed else 0
            else -> 0
        }
    }

    /**
     * L2/R2 as a key event; true when the core should see it (a pull its axis already pressed is not pressed again, a
     * stray second up is dropped). From the first key on, that trigger's axes are ignored.
     */
    fun onKey(button: NxButton, down: Boolean): Boolean {
        keysSeen += button
        return set(button, down)
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

    /** Analog triggers (Dreamcast, GameCube): this read's values; the core gets [analogLeft] and [analogRight]. */
    fun analog(left: Float, right: Float) {
        lastLeft = left
        lastRight = right
    }

    /** The last [analog] L2 value as the core should have it now: at rest while a shortcut has [take]n it. */
    val analogLeft: Float get() = if (NxButton.L2 in taken) 0f else lastLeft
    /** The last [analog] R2 value as the core should have it now: at rest while a shortcut has [take]n it. */
    val analogRight: Float get() = if (NxButton.R2 in taken) 0f else lastRight

    /** The menu opened or the game paused: the host released everything in the core, so nothing is held here. */
    fun releaseAll() {
        held.clear()
        taken.clear()   // the shortcut's release goes to the menu now
        lastLeft = 0f
        lastRight = 0f
    }

    /** True when [button] changed to [down]. */
    private fun set(button: NxButton, down: Boolean): Boolean {
        if (down == button in held) return false
        if (down) held += button else held -= button
        return true
    }

    companion object {
        private const val L2_CHANGED = 1
        private const val L2_DOWN = 2
        private const val R2_CHANGED = 4
        private const val R2_DOWN = 8

        /** Each change in [changes] ([onAxes]), L2 before R2. Inline: the caller's block is no object. */
        inline fun forEachChange(changes: Int, block: (NxButton, Boolean) -> Unit) {
            if (changes == 0) return
            if (changed(changes, NxButton.L2)) block(NxButton.L2, down(changes, NxButton.L2))
            if (changed(changes, NxButton.R2)) block(NxButton.R2, down(changes, NxButton.R2))
        }

        /** [button] (L2 or R2) changed in [changes]. */
        fun changed(changes: Int, button: NxButton): Boolean = changes and (if (button == NxButton.L2) L2_CHANGED else R2_CHANGED) != 0

        /** [button]'s change in [changes] is a press. */
        fun down(changes: Int, button: NxButton): Boolean = changes and (if (button == NxButton.L2) L2_DOWN else R2_DOWN) != 0
    }
}
