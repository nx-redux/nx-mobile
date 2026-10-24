package com.nxredux.mobile.runner

import com.nxredux.mobile.options.SpeedSettings
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.SpeedControl
import com.nxredux.mobile.shortcuts.SpeedEffect
import com.nxredux.mobile.shortcuts.SpeedOutput
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.RewindStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The game's fast forward and rewind: owns the [SpeedControl] the shortcuts drive and puts its [SpeedOutput] on the view
 * ([GLRetroView.fastForward], [GLRetroView.rewinding] and their audio; [GLRetroView.rewindEnabled] from the console's
 * Rewind row). Pause is the view's frameSpeed, which this never touches, so a toggled FF is still on when the menu
 * closes (L6). While rewinding, a 100 ms tick reads [GLRetroView.rewindStatus]: EMPTY (latched by the engine until
 * rewinding goes off) ends a toggled rewind ([SpeedControl.onRewindEmpty], L16). Around a save state's write or read
 * ([aroundState]) FF and rewind are forced off, then put back (L6). [onTiles] runs when the game panel's tiles
 * ([tiles]) change, whatever changed them (a shortcut, the panel, the buffer running out, a speed row, a session).
 * Main thread, but for [aroundState].
 */
internal class SpeedController(
    private val scope: CoroutineScope,
    private val onTiles: () -> Unit = {},
    private val view: () -> GLRetroView?,
) {
    private val control = SpeedControl()
    private var settings = SpeedSettings()
    private var canRewind = false
    private var netplay = false
    private var lastTiles: Pair<Boolean, Boolean?>? = null
    private var stateDepth = 0   // save-state reads/writes in flight ([aroundState]); nested ones count
    private var tick: Job? = null

    val output: SpeedOutput get() = control.output

    /** The console's (or game's) speed rows, applied live; [canRewind] is the core's. */
    fun configure(s: SpeedSettings, canRewind: Boolean) {
        settings = s
        this.canRewind = canRewind
        control.configure(s, canRewind)
        apply()
    }

    /** A shortcut's press or release; [SpeedEffect.AskReset] is the host's to show. */
    fun onAction(a: ShortcutAction, down: Boolean): SpeedEffect? = control.onAction(a, down).also { apply() }

    /**
     * The game panel's Fast forward / Rewind tile: the toggles set directly ([SpeedControl.setToggle]; null leaves one
     * alone), from the tile's live state, so a toggle a binding left on (or one whose binding was removed) goes off here.
     */
    fun setToggle(ff: Boolean? = null, rewind: Boolean? = null) { control.setToggle(ff, rewind); apply() }

    /**
     * What the panel's tiles show: fast forward running, and rewinding (null: no Rewind tile, the core or the console's
     * Rewind row can't, or a session plays). Holds count too, though the panel only shows once they are released.
     */
    fun tiles(): Pair<Boolean, Boolean?> {
        val o = control.output
        return (o.ffSpeed > 0) to o.rewinding.takeIf { rewindShown }
    }

    /** Rewind is offered now: the core can, the console's Rewind row is on, and no session plays. */
    val rewindShown: Boolean get() = canRewind && settings.rewind && !netplay

    /** A netplay or GBA link session plays ([setSession]): every shortcut does nothing. */
    val inSession: Boolean get() = netplay

    /** Focus or the menu took the buttons: holds end, toggles stay (Review Focus 5). */
    fun releaseHolds() { control.releaseHolds(); apply() }

    /** A netplay or GBA link session plays now ([netplay]), or no longer does. */
    fun setSession(netplay: Boolean) {
        this.netplay = netplay
        control.setSession(netplay)
        apply()
    }

    /** Puts the current output on the view (again): after [configure], an action, a new view, a state read or write. */
    fun apply() {
        tiles().takeIf { it != lastTiles }?.let { lastTiles = it; onTiles() }
        val v = view() ?: return
        val o = control.output
        val inState = stateDepth > 0
        v.fastForwardAudio = o.ffAudio
        v.fastForward = if (inState) 0 else o.ffSpeed
        v.rewindAudio = o.rewindAudio
        v.rewindEnabled = settings.rewind && !netplay
        v.rewinding = !inState && o.rewinding
        syncTick(!inState && o.rewinding)
    }

    /**
     * Runs [block] (a save state's serialize or unserialize) with FF and rewind off on the engine, then puts them back.
     * Callable from any dispatcher: the view is set on main before [block] queues its own GL-thread event, so the engine
     * sees FF off first (the GL thread runs its queue in order).
     */
    suspend fun <T> aroundState(block: suspend () -> T): T {
        withContext(Dispatchers.Main.immediate) { enterState() }
        try {
            return block()
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { exitState() }
        }
    }

    /** [aroundState] for a caller already on main that serializes synchronously (onStop's auto state). */
    fun <T> aroundStateNow(block: () -> T): T {
        enterState()
        try { return block() } finally { exitState() }
    }

    private fun enterState() { stateDepth++; apply() }

    private fun exitState() { stateDepth--; apply() }

    private fun syncTick(on: Boolean) {
        if (!on) { tick?.cancel(); tick = null; return }
        if (tick?.isActive == true) return
        tick = scope.launch {
            while (isActive) {
                delay(REWIND_TICK_MS)
                if (view()?.rewindStatus() == RewindStatus.EMPTY && control.output.rewinding) {
                    control.onRewindEmpty()
                    apply()   // a toggled rewind ended: rewinding off stops this tick
                }
            }
        }
    }

    private companion object {
        const val REWIND_TICK_MS = 100L
    }
}
