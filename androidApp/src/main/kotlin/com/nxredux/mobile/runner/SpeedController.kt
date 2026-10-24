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
 * ([aroundState]) FF and rewind are forced off, then put back (L6). Main thread, but for [aroundState].
 */
internal class SpeedController(private val scope: CoroutineScope, private val view: () -> GLRetroView?) {
    private val control = SpeedControl()
    private var settings = SpeedSettings()
    private var netplay = false
    private var stateDepth = 0   // save-state reads/writes in flight ([aroundState]); nested ones count
    private var tick: Job? = null

    val output: SpeedOutput get() = control.output

    /** The console's (or game's) speed rows, applied live; [canRewind] is the core's. */
    fun configure(s: SpeedSettings, canRewind: Boolean) {
        settings = s
        control.configure(s, canRewind)
        apply()
    }

    /** A shortcut's press or release; [SpeedEffect.AskReset] is the host's to show. */
    fun onAction(a: ShortcutAction, down: Boolean): SpeedEffect? = control.onAction(a, down).also { apply() }

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
