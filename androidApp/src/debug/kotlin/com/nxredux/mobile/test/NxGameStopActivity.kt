package com.nxredux.mobile.test

import android.app.Activity
import android.os.Bundle
import com.nxredux.mobile.runner.GameActivity
import com.swordfish.libretrodroid.NxLog

/**
 * Debug builds only, in the game process: GAME_STOP (or GAME_FF with the `ff` extra) as an activity start, for devices
 * that drop the broadcast ([NxTestActivity]). No window: it acts and finishes.
 */
class NxGameStopActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ff = NxGameStopReceiver.EXTRA_FF
        val done = if (intent.hasExtra(ff)) GameActivity.debugFastForward(intent.getBooleanExtra(ff, false)) else GameActivity.debugQuit()
        if (!done) NxLog.w("NxTest", "no live game in the game process")
        finish()
    }
}
