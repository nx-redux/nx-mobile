package com.nxredux.mobile.ui.nx

import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nxredux.mobile.runner.PadPresence

/** The buttons any controller has at least one of: a gamepad's, or a joystick's numbered ones. */
private val PAD_BUTTONS = intArrayOf(
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_2,
)

/** A controller in hand ([PadPresence.isController]): the menu, the game's pad layout and its players all go by it. */
fun InputDevice.isController(): Boolean {
    val padSource = supportsSource(InputDevice.SOURCE_GAMEPAD) || supportsSource(InputDevice.SOURCE_JOYSTICK)
    return PadPresence.isController(isVirtual, padSource, padButton = padSource && hasKeys(*PAD_BUTTONS).any { it })
}

private fun anyGamepad(im: InputManager?): Boolean {
    if (im == null) return false
    return im.inputDeviceIds.any { id -> im.getInputDevice(id)?.isController() == true }
}

@Composable
actual fun rememberControllerConnected(): Boolean {
    val context = LocalContext.current
    val im = remember { context.getSystemService(InputManager::class.java) }
    var connected by remember { mutableStateOf(anyGamepad(im)) }
    DisposableEffect(im) {
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { connected = anyGamepad(im) }
            override fun onInputDeviceRemoved(deviceId: Int) { connected = anyGamepad(im) }
            override fun onInputDeviceChanged(deviceId: Int) { connected = anyGamepad(im) }
        }
        im?.registerInputDeviceListener(listener, null)
        onDispose { im?.unregisterInputDeviceListener(listener) }
    }
    return connected
}
