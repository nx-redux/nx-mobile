package com.nxredux.mobile.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nxredux.mobile.runner.GameActivity
import com.swordfish.libretrodroid.NxLog

/**
 * Debug builds only, in the game process: [NxTestReceiver]'s GAME_STOP, handed on, quits the live game there; its
 * GAME_FF (the `ff` extra) turns fast forward on or off.
 */
class NxGameStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = if (intent.hasExtra(EXTRA_FF)) GameActivity.debugFastForward(intent.getBooleanExtra(EXTRA_FF, false)) else GameActivity.debugQuit()
        if (!done) NxLog.w("NxTest", "no live game in the game process")
    }

    companion object {
        const val EXTRA_FF = "ff"
    }
}
