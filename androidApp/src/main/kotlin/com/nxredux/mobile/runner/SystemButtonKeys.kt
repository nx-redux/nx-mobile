package com.nxredux.mobile.runner

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.shortcuts.PadModel
import com.nxredux.mobile.shortcuts.SystemButtonMap
import com.nxredux.mobile.shortcuts.SystemButtons

/**
 * Controls → System buttons on Android: a bound button becomes BUTTON_SELECT / BUTTON_START / BUTTON_MODE at the top of
 * every key entry point (MainActivity, GameActivity, the second screen, the pad editor), so everything after it (the
 * menu, the MENU combos, SELECT+START, the game) sees the standard code and the button's old job is gone.
 */
object SystemButtonKeys {
    @Volatile private var stored: String? = null
    @Volatile private var map = SystemButtonMap()

    /** A physical pad's model (a gamepad or joystick, not a virtual device such as adb's), else null. */
    fun model(device: InputDevice?): PadModel? {
        if (device == null || device.isVirtual) return null
        if (!device.supportsSource(InputDevice.SOURCE_GAMEPAD) && !device.supportsSource(InputDevice.SOURCE_JOYSTICK)) return null
        return PadModel(device.vendorId, device.productId, device.name.orEmpty())
    }

    /** Every physical pad connected now, one per model. */
    fun connected(context: Context): List<PadModel> {
        val im = context.getSystemService(InputManager::class.java) ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull { model(im.getInputDevice(it)) }.distinctBy { it.key }
    }

    /** [event] with its key code replaced when its button is bound on its pad's model; the event itself otherwise. */
    fun translate(context: Context, event: KeyEvent): KeyEvent {
        val model = model(event.device) ?: return event
        val code = SystemButtons.translate(current(context), model.key, event.keyCode, event.scanCode) ?: return event
        return KeyEvent(
            event.downTime, event.eventTime, event.action, code, event.repeatCount, event.metaState,
            event.deviceId, event.scanCode, event.flags, event.source,
        )
    }

    // The stored text is read each time (SharedPreferences keeps it in memory) and decoded only when it changed.
    private fun current(context: Context): SystemButtonMap {
        val text = AndroidAppSettings(context).systemButtons
        if (text != stored) { map = SystemButtons.decode(text); stored = text }
        return map
    }
}
