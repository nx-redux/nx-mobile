package com.nxredux.mobile.test

import android.app.Activity
import android.os.Bundle

/**
 * Debug builds only, in the game process: [NxGameStopReceiver]'s hooks (its extras) as an activity start, for devices
 * that drop the broadcast ([NxTestActivity]). No window: it acts and finishes.
 */
class NxGameStopActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NxGameStopReceiver().onReceive(applicationContext, intent)
        finish()
    }
}
