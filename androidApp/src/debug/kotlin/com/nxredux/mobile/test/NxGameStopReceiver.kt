package com.nxredux.mobile.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nxredux.mobile.runner.GameActivity
import com.swordfish.libretrodroid.NxLog

/** Debug builds only, in the game process: [NxTestReceiver]'s GAME_STOP, handed on, quits the live game there. */
class NxGameStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!GameActivity.debugQuit()) NxLog.w("NxTest", "GAME_STOP: no live game in the game process")
    }
}
