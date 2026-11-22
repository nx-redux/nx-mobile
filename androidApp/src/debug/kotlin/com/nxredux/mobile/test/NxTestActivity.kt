package com.nxredux.mobile.test

import android.app.Activity
import android.os.Bundle

/**
 * Debug builds only: [NxTestReceiver]'s hooks through `adb shell am start -n …/.test.NxTestActivity -a <action> …`.
 * Some devices (the AYANEO Pocket Air Mini, Android 11) stop delivering adb broadcasts to an app once it has been
 * force-stopped, until a restart; an activity start always arrives. No window: it hands the intent on and finishes.
 */
class NxTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NxTestReceiver().onReceive(applicationContext, intent)
        finish()
    }
}
