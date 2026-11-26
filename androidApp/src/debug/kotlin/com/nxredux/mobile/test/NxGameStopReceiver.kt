package com.nxredux.mobile.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nxredux.mobile.runner.GameActivity
import com.swordfish.libretrodroid.NxLog

/**
 * Debug builds only, in the game process: [NxTestReceiver]'s GAME_STOP, handed on, quits the live game there; its
 * GAME_FF (the `ff` extra) turns fast forward on or off; its GAME_DISC (the `disc` extra) changes disc, its GAME_RESET (`reset`) resets and its GAME_SLOT (`slot`, `save`) saves or loads a slot.
 */
class NxGameStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = when {
            intent.hasExtra(EXTRA_FF) -> GameActivity.debugFastForward(intent.getBooleanExtra(EXTRA_FF, false))
            intent.hasExtra(EXTRA_DISC) -> GameActivity.debugDisc(intent.getIntExtra(EXTRA_DISC, 0))
            intent.hasExtra(EXTRA_RESET) -> GameActivity.debugReset()
            intent.hasExtra(EXTRA_SLOT) -> GameActivity.debugSlot(intent.getIntExtra(EXTRA_SLOT, 0), intent.getBooleanExtra(EXTRA_SAVE, false))
            else -> GameActivity.debugQuit()
        }
        if (!done) NxLog.w("NxTest", "no live game in the game process")
    }

    companion object {
        const val EXTRA_FF = "ff"
        const val EXTRA_DISC = "disc"
        const val EXTRA_RESET = "reset"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_SAVE = "save"
    }
}
