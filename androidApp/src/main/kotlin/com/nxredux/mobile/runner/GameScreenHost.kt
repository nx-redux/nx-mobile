package com.nxredux.mobile.runner

import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import com.swordfish.libretrodroid.GLRetroView

/**
 * What the controllers' hosts share of [GameActivity]. Reads only, plus a toast: actions such as the quit or a menu
 * write stay on the hosts that may run them, so a read-only host ([SaveSessionHost]) keeps no way to change the screen.
 */
internal interface GameScreenHost : LifecycleOwner {
    val activity: ComponentActivity
    val retroView: GLRetroView?
    val quitting: Boolean
    fun toast(msg: String): android.widget.Toast
}
