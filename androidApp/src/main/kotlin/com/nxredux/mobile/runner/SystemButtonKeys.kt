package com.nxredux.mobile.runner

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.shortcuts.PadKeys
import com.nxredux.mobile.shortcuts.PadModel
import com.nxredux.mobile.shortcuts.SystemButtonMap
import com.nxredux.mobile.shortcuts.SystemButtons
import java.util.concurrent.ConcurrentHashMap

/**
 * Controls → System buttons on Android: a bound button (the pad's, or one on a device beside it), or a pad's Back or
 * Menu key standing in for a button it lacks, becomes BUTTON_SELECT / BUTTON_START / BUTTON_MODE at the top of
 * every key entry point (MainActivity, GameActivity, the second screen, the pad editor), so everything after it (the
 * menu, the MENU combos, SELECT+START, the game) sees the standard code and the button's old job is gone.
 */
object SystemButtonKeys {
    @Volatile private var stored: String? = null
    @Volatile private var map = SystemButtonMap()
    @Volatile private var settings: AndroidAppSettings? = null
    /** Each input device's [PadModel.key] by device id, "" for one that is not a pad: an id never names another device. */
    private val modelKeys = ConcurrentHashMap<Int, String>()
    private val padKeys = ConcurrentHashMap<Int, PadKeys>()
    /** Each input device's [source] name by device id, "" for a pad or a device that binds nothing. */
    private val sourceKeys = ConcurrentHashMap<Int, String>()

    /** A physical pad's model (a gamepad or joystick, not a virtual device such as adb's), else null. */
    fun model(device: InputDevice?): PadModel? {
        if (device == null || device.isVirtual) return null
        if (!device.supportsSource(InputDevice.SOURCE_GAMEPAD) && !device.supportsSource(InputDevice.SOURCE_JOYSTICK)) return null
        return PadModel(device.vendorId, device.productId, device.name.orEmpty())
    }

    /**
     * A physical device with buttons that is no pad (a handheld's own SELECT and START on a device beside its pad), by a
     * name that holds from run to run: its vendor and product, or its name's hash when it reports neither (a built-in
     * device). Null for a pad, a virtual device or one without buttons.
     */
    fun source(device: InputDevice?): String? {
        if (device == null || device.isVirtual || model(device) != null) return null
        if (!device.supportsSource(InputDevice.SOURCE_KEYBOARD)) return null
        if (device.vendorId == 0 && device.productId == 0) return "n" + device.name.orEmpty().hashCode().toUInt().toString(16)
        return hex4(device.vendorId) + hex4(device.productId)
    }

    /** [source] for a press that may be bound: never the volume or power keys, which a handheld's own device sends too. */
    fun source(event: KeyEvent): String? = if (event.keyCode in NEVER_BOUND) null else source(event.device)

    private fun hex4(v: Int) = (v and 0xffff).toString(16).padStart(4, '0')

    private val NEVER_BOUND = setOf(
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER,
    )

    /** Every physical pad connected now, one per model. */
    fun connected(context: Context): List<PadModel> {
        val im = context.getSystemService(InputManager::class.java) ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull { model(im.getInputDevice(it)) }.distinctBy { it.key }
    }

    /** [event] with its key code replaced when its button is bound on its pad's model; the event itself otherwise. */
    fun translate(context: Context, event: KeyEvent): KeyEvent {
        val map = current(context)
        val model = modelKey(event)
        if (model == null && map.bound.isEmpty()) return event
        val code = (
            if (model != null) SystemButtons.translate(map, model, event.keyCode, event.scanCode, padKeys(event))
            else sourceKey(event)?.let { SystemButtons.translateFrom(map, it, event.keyCode, event.scanCode) }
        ) ?: return event
        return KeyEvent(
            event.downTime, event.eventTime, event.action, code, event.repeatCount, event.metaState,
            event.deviceId, event.scanCode, event.flags, event.source,
        )
    }

    private fun modelKey(event: KeyEvent): String? {
        modelKeys[event.deviceId]?.let { return it.ifEmpty { null } }
        val device = event.device ?: return null   // not known yet: asked again on the next event
        val key = model(device)?.key
        modelKeys[event.deviceId] = key ?: ""
        return key
    }

    /** What the event's pad says of its own SELECT and START keys, asked once per device; null while the device is not known. */
    private fun padKeys(event: KeyEvent): PadKeys? {
        padKeys[event.deviceId]?.let { return it }
        val device = event.device ?: return null
        val has = device.hasKeys(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)
        return PadKeys(hasSelect = has[0], hasStart = has[1]).also { padKeys[event.deviceId] = it }
    }

    private fun sourceKey(event: KeyEvent): String? {
        sourceKeys[event.deviceId]?.let { return it.ifEmpty { null } }
        val device = event.device ?: return null
        val key = source(device)
        sourceKeys[event.deviceId] = key ?: ""
        return key
    }

    // The stored text is read each time (the preferences keep it in memory, and a game process's copy is refreshed in
    // place) and decoded only when it changed.
    private fun current(context: Context): SystemButtonMap {
        val prefs = settings ?: AndroidAppSettings(context.applicationContext).also { settings = it }
        val text = prefs.systemButtons
        if (text != stored) { map = SystemButtons.decode(text); stored = text }
        return map
    }
}
