package com.nxredux.mobile.ui.nx

import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private fun anyGamepad(im: InputManager?): Boolean {
    if (im == null) return false
    return im.inputDeviceIds.any { id ->
        val d = im.getInputDevice(id) ?: return@any false
        !d.isVirtual && (d.supportsSource(InputDevice.SOURCE_GAMEPAD) || d.supportsSource(InputDevice.SOURCE_JOYSTICK))
    }
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
