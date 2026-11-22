package com.nxredux.mobile.ui

import com.nxredux.mobile.shortcuts.CaptureResult
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.KeyLabels
import com.nxredux.mobile.shortcuts.PadModel
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.shortcuts.ShortcutCapture
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.Shortcuts
import com.nxredux.mobile.shortcuts.SystemButtons
import com.nxredux.mobile.shortcuts.SystemRole
import com.nxredux.mobile.shortcuts.PhysicalKey
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Shortcuts and System buttons pages need from [AppState]: the Settings page open, its refresh, a notice. */
internal interface ControlBindingsHost {
    /** The Settings page showing; null on any other screen. */
    fun openSection(): SettingsSection?
    fun refreshSettings()
    /** Rebuilds the Settings rows when one of [sections] is the open page. */
    fun refreshSettingsOn(vararg sections: SettingsSection)
    fun showNotice(text: String)
}

/**
 * Controls' two binding pages: Shortcuts (its draft, press to bind's raw keys and its rows) and System buttons (the pad
 * it acts on, the role waiting for a press and its rows). The platform's raw keys come here before the menu sees them.
 */
internal class ControlBindingsController(
    private val deps: AppDeps,
    private val host: ControlBindingsHost,
) {
    /** Controls -> Shortcuts as edited since it opened; null: closed (or untouched), [AppSettings.shortcuts] is all there is. */
    private var shortcutDraft: ShortcutSet? = null

    /** Press to bind's raw keys ([captureKey]); Controls -> Shortcuts' bind rows start it. */
    private val capture = ShortcutCapture()

    private val _capturingShortcut = MutableStateFlow<ShortcutAction?>(null)
    /** The action a Shortcuts bind row is waiting on a press for; while set the platform hands raw keys to [captureKey] first. */
    val capturingShortcut: StateFlow<ShortcutAction?> = _capturingShortcut.asStateFlow()

    fun shortcutSet(): ShortcutSet = shortcutDraft ?: Shortcuts.decodeBase(deps.settings.shortcuts)

    private var controllerConnected = true

    /**
     * The UI reports whether a controller is connected: the Shortcuts page shows its bind rows only with one (the
     * on-screen pad binds nothing), so a wait ends when it goes.
     */
    fun onControllerConnected(connected: Boolean) {
        if (controllerConnected == connected) return
        controllerConnected = connected
        if (!connected) cancelCapture()
        refreshShortcuts()
    }

    /**
     * A raw key from the platform, before it maps to an [NxButton]: true when press to bind took it, so the platform
     * drops it. Waiting, every key is the capture's (MENU opens nothing); after it ends, the keys still held stay
     * its until released ([ShortcutCapture.takes]). Auto-repeats are the platform's to drop.
     */
    fun captureKey(keyCode: Int, down: Boolean): Boolean {
        if (capture.active && host.openSection() != SettingsSection.SHORTCUTS) closeShortcuts()   // a stale wait: never the menu's keys
        if (!capture.takes(keyCode, down)) return false
        val result = capture.onKey(keyCode, down) ?: return true
        val action = _capturingShortcut.value
        _capturingShortcut.value = null
        if (action != null) when (result) {
            is CaptureResult.Bound -> setShortcut(action, result.binding)
            is CaptureResult.Refused -> { refreshShortcuts(); host.showNotice(result.reason) }
            CaptureResult.Cancelled -> refreshShortcuts()
        }
        return true
    }

    fun startCapture(action: ShortcutAction) {
        capture.start()
        _capturingShortcut.value = action
        refreshShortcuts()
    }

    /** Ends a capture without a binding (B on the pad or a touch hint, Back, the page left). */
    fun cancelCapture() {
        if (_capturingShortcut.value == null) return
        capture.cancel()
        _capturingShortcut.value = null
        refreshShortcuts()
    }

    /** [action] on [binding] (null: None; [ShortcutSet.withBinding]). */
    fun setShortcut(action: ShortcutAction, binding: ShortcutBinding?) {
        shortcutDraft = shortcutSet().withBinding(action, binding)
        refreshShortcuts()
    }

    fun setTouch(slot: TouchSlot, action: ShortcutAction?) { shortcutDraft = shortcutSet().withTouch(slot, action) }

    private fun refreshShortcuts() = host.refreshSettingsOn(SettingsSection.SHORTCUTS)

    /**
     * The Shortcuts page closes (any screen but it, [onScreen]; the app backgrounded): a capture ends, and the draft is
     * written once, not per change.
     */
    fun closeShortcuts() {
        capture.cancel()
        _capturingShortcut.value = null
        shortcutDraft?.let { deps.settings.shortcuts = Shortcuts.encodeBase(it) }
        shortcutDraft = null
    }

    /**
     * Controls -> Shortcuts: one press-to-bind row per action (its button or combo, None unbound; the one waiting shows
     * [SHORTCUT_PROMPT] and, as its line, how to bind), the two touch buttons' actions, then Clear all. With no
     * controller the bind rows give way to [SHORTCUTS_NO_CONTROLLER]; their bindings stay.
     */
    fun shortcutRows(): List<SettingsRow> {
        val set = shortcutSet()
        val waiting = _capturingShortcut.value
        val binds = if (!controllerConnected) listOf(SettingsRow(SettingsKind.TEXT, SHORTCUTS_NO_CONTROLLER))
        else ShortcutAction.entries.map { a ->
            val name = a.label(deps.settings.saveOnQuit)
            if (a == waiting) SettingsRow(SettingsKind.SHORTCUT_BIND, name, SHORTCUT_PROMPT, note = "Hold MENU for a combo", shortcut = a)
            else SettingsRow(SettingsKind.SHORTCUT_BIND, name, set.bindings[a]?.let(KeyLabels::label) ?: "None", note = if (a.mic) MIC_ROW_NOTE else null, shortcut = a)
        }
        return binds + TouchSlot.entries.map { t ->
            SettingsRow(SettingsKind.SHORTCUT_TOUCH, t.label, set.touch[t]?.label ?: "None", touchSlot = t)
        } + SettingsRow(SettingsKind.RESET_DEFAULTS, "Clear all")
    }

    // ---- SELECT / START / MENU (Controls → System buttons) ----

    /** The pad that pressed a key last: Controls → System buttons acts on it. */
    private var lastPad: PadModel? = null

    private fun systemButtonMap() = SystemButtons.decode(deps.settings.systemButtons)

    /** The pad Settings acts on: the last one pressed while it's connected, else the first connected. */
    private fun currentPad(): PadModel? {
        val pads = deps.connectedPads()
        return lastPad?.takeIf { p -> pads.any { it.key == p.key } } ?: pads.firstOrNull()
    }

    /**
     * Every key from a physical device, before anything else sees it. [model]: its pad (null: not one); [source]: the
     * device itself when it is no pad (a handheld's own buttons on a device beside its pad), null for a pad or a device
     * that binds nothing. It remembers the pad, and a wait in Controls → System buttons takes the press; true when it
     * used the key (the menu must not see it).
     */
    fun systemButtonKey(model: PadModel?, source: String?, keyCode: Int, scanCode: Int, down: Boolean, repeat: Boolean): Boolean {
        // A wait keeps its pad: another pad's press neither binds nor moves the page to it.
        if (systemButtonWaiting != null || (model?.key ?: source) in systemButtonsHeld) return systemButtonSettingsKey(model, source, keyCode, scanCode, down, repeat)
        if (model != null && down) lastPad = model
        return false
    }

    /** Controls → System buttons: the role waiting for a press (its row says so), or null. */
    var systemButtonWaiting: SystemRole? = null
    /** The devices whose press bound or cancelled a wait, each until its release (swallowed with it). */
    private var systemButtonsHeld: Set<String> = emptySet()

    /**
     * Controls → System buttons: the pad it acts on ([currentPad], by name), then SELECT, START and MENU with their
     * buttons (Standard: the pad's own), then Reset. No pad: [SYSTEM_BUTTONS_NO_CONTROLLER]; the bindings stay.
     */
    fun systemButtonRows(): List<SettingsRow> {
        val pad = currentPad() ?: return listOf(SettingsRow(SettingsKind.TEXT, SYSTEM_BUTTONS_NO_CONTROLLER))
        val map = systemButtonMap()
        return listOf(SettingsRow(SettingsKind.TEXT, pad.name)) + SystemRole.entries.map { role ->
            val value = if (role == systemButtonWaiting) SYSTEM_BUTTON_PROMPT
                else map.bound[pad.key]?.get(role)?.let(::physicalKeyLabel) ?: "Standard"
            SettingsRow(SettingsKind.SYSTEM_BUTTON_BIND, role.label, value, note = if (role == systemButtonWaiting) "B cancels" else null, systemRole = role)
        } + SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL)
    }

    /** A bound key's name: a key code's ([KeyLabels]), or "Button 2F1" for a scan code Android has no key for. */
    private fun physicalKeyLabel(k: PhysicalKey): String =
        if (k.keyCode == 0) "Button ${k.scanCode.toString(16).uppercase()}" else KeyLabels.label(ShortcutBinding(null, k.keyCode))

    /**
     * A key while System buttons waits: the next fresh press binds the role, from its pad or from a device that is no
     * pad (a handheld whose SELECT and START sit on a device beside its pad), swapping with a role that had that button;
     * the pad's B cancels, another pad's keys pass. The press's release is swallowed with it.
     */
    private fun systemButtonSettingsKey(model: PadModel?, source: String?, keyCode: Int, scanCode: Int, down: Boolean, repeat: Boolean): Boolean {
        val sender = model?.key ?: source ?: return false
        if (!down) { val held = sender in systemButtonsHeld; systemButtonsHeld = systemButtonsHeld - sender; return held }
        val role = systemButtonWaiting ?: return false
        val pad = currentPad() ?: return false
        if (model != null && model.key != pad.key) return false
        if (repeat) return true
        systemButtonsHeld = systemButtonsHeld + sender
        systemButtonWaiting = null
        // B cancels; the pad's Back binds like any button (a Retroid Pocket Classic's home button sends it).
        if (model == null || keyCode != KeyCodes.BUTTON_B) {
            val key = PhysicalKey.of(keyCode, scanCode, device = if (model == null) sender else "")
            deps.settings.systemButtons = SystemButtons.encode(SystemButtons.bind(systemButtonMap(), pad.key, role, key))
        }
        host.refreshSettings()
        return true
    }


    /**
     * The screen changed to [s]: leaving the Shortcuts page closes it ([closeShortcuts]), leaving System buttons ends
     * its wait.
     */
    fun onScreen(s: Screen) {
        if ((s as? Screen.Settings)?.section != SettingsSection.SHORTCUTS && (shortcutDraft != null || _capturingShortcut.value != null)) closeShortcuts()
        if ((s as? Screen.Settings)?.section != SettingsSection.SYSTEM_BUTTONS) systemButtonWaiting = null
    }

    /** Shortcuts' Reset: every binding cleared in the draft (written when the page closes). */
    fun clearShortcuts() { shortcutDraft = ShortcutSet.EMPTY }

    /** System buttons' Reset: the connected pad model's SELECT / START / MENU back to its defaults. */
    fun resetSystemButtons() {
        currentPad()?.let { pad -> deps.settings.systemButtons = SystemButtons.encode(SystemButtons.reset(systemButtonMap(), pad.key)) }
    }
}
