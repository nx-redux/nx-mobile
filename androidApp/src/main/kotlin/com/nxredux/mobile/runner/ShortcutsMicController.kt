package com.nxredux.mobile.runner

import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.R
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.shortcuts.ClaimedInput
import com.nxredux.mobile.shortcuts.ConsoleHotkeys
import com.nxredux.mobile.shortcuts.EffectiveShortcuts
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.Shortcuts
import com.nxredux.mobile.shortcuts.SpeedEffect
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.PadMic
import com.nxredux.mobile.ui.nx.TouchShortcut
import com.nxredux.mobile.ui.nx.visibleTouchShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What [ShortcutsMicController] needs from [GameActivity]: the state its shortcuts and mic follow, and the actions a
 * shortcut runs (speed, reset, quit, slots), which stay the activity's.
 */
internal interface ShortcutsMicHost : LifecycleOwner {
    /** For the mic permission prompt, which must be registered while the activity is being created. */
    val activity: ComponentActivity
    val settings: AndroidAppSettings
    val speed: SpeedController
    val retroView: com.swordfish.libretrodroid.GLRetroView?
    val menuOpen: Boolean
    val focusPaused: Boolean
    val quitting: Boolean
    val canRewind: Boolean
    val saveStates: SaveStates?
    var lastSlotCache: Int?
    fun askReset()
    fun quitGame(chosen: Boolean, autoState: Boolean)
    fun saveToSlot(slot: Int)
    fun loadFromSlot(slot: Int)
    fun toast(msg: String): android.widget.Toast
}

/**
 * The game's shortcuts (bound keys, the touch shortcut buttons, a pad skin's SAVE / LOAD / REWIND / FAST) and the DS / 3DS
 * Mic toggle. Built at launch ([launch]); the actions themselves (speed, reset, quit, slots) stay [host]'s. Main thread.
 * Constructed with the activity: [micPrompt] must be registered before it is created.
 */
internal class ShortcutsMicController(
    private val host: ShortcutsMicHost,
    private val padLayout: PadLayoutController,
    private val renderer: FrontendRenderer,
    private val menu: InGameMenuController,
    private val dual: DualScreenController,
) {
    /** The core hears a mic ([CoreDescriptor.microphone]: DS, DSi, 3DS); false until the launch resolves the core. */
    var canMic = false
        private set
    /**
     * The session's Mic toggle ([MicControl]), made at launch for a mic core only; null for every other core, so every
     * mic path (pad button, tile, binding, source) is inert. Off at every launch; [applyMic] pushes it everywhere.
     */
    private var mic: MicControl? = null
    /** The pad's mic button, snapshot state read in the pads' composition ([padMic]): set by [applyMic]. */
    private val padMicState = mutableStateOf<PadMic?>(null)
    // RECORD_AUDIO, asked from the Mic toggle only ([setMic]): the answer is pushed at once, so a denial gives noise.
    private val micPrompt = host.activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        mic?.setGranted(granted)
        applyMic()
    }
    private val micAccess by lazy { AndroidMicAccess(host.activity) { micPrompt.launch(android.Manifest.permission.RECORD_AUDIO) } }
    // What [applyShortcuts] last built: the touch buttons' actions ([refreshTouchShortcuts]).
    private var effectiveShortcuts: EffectiveShortcuts? = null
    /** The touch shortcut buttons the pads draw: snapshot state, read in the pads' composition. */
    private val touchShortcuts = mutableStateOf<Map<TouchSlot, TouchShortcut>>(emptyMap())
    // Each touch button's action while its press lasts: the release ends what the press started, even if the slot's
    // action changed meanwhile.
    private val touchHeld = HashMap<TouchSlot, ShortcutAction>()
    /** Controls → Shortcuts with this console's and game's `nx_bind_*` over it, read at launch; null before. */
    var shortcutKeys: ShortcutKeys? = null
        private set
    // What [shortcutKeys] is built from ([applyShortcuts]): Controls → Shortcuts and the console's hotkeys, read at launch
    // (neither can change while the game runs), and this console's or game's overrides, which the in-game Shortcuts page
    // edits live and Save Changes writes.
    private var shortcutBase = ShortcutSet.EMPTY
    private var shortcutClaimed: List<ClaimedInput> = ConsoleHotkeys.always
    var shortcutOverrides: ShortcutOverrides = ShortcutOverrides.NONE
        private set

    /**
     * The launch's shortcuts and Mic toggle. Shortcuts: the base with this console's and game's [overrides], less what a
     * built-in hotkey owns. netplay: they stay bound but do nothing, as fast forward, rewind and reset would desync the
     * players. A link session's end turns them back on ([onSessionEnded]); a lockstep session's does not: the game played
     * on alone after it keeps them off, as it has no save states. DS / 3DS: the Mic toggle, off; netplay hides it (as the
     * speed shortcuts) until a link session's end.
     */
    fun launch(microphone: Boolean, netplay: Boolean, tag: String, overrides: ShortcutOverrides) {
        canMic = microphone
        mic = if (canMic) MicControl(micAccess.usable).apply {
            setGranted(micAccess.granted())
            setAsked(host.settings.micAsked)
            setSession(netplay = netplay)
        } else null
        shortcutBase = Shortcuts.decodeBase(host.settings.shortcuts)
        shortcutClaimed = ConsoleHotkeys.claimed(tag)
        applyShortcuts(overrides)
        applyMic()
    }

    /** A link session ended and the game is a local one: the Mic toggle shows again, off. */
    fun onSessionEnded() {
        mic?.setSession(netplay = false)
        applyMic()
        refreshTouchShortcuts()
    }

    /** The permission may have been granted or revoked in Android's settings meanwhile: live or noise again. */
    fun onResume() {
        mic?.setGranted(micAccess.granted())
        pushMicSource(foreground = true)
    }

    fun onShortcut(action: ShortcutAction, down: Boolean) {
        if (action == ShortcutAction.TOGGLE_MIC) { if (down && mic != null) toggleMic(); return }
        when (host.speed.onAction(action, down)) {
            SpeedEffect.AskReset -> host.askReset()
            SpeedEffect.SaveAndQuit -> host.quitGame(chosen = true, autoState = true)   // the menu's Save & Quit
            SpeedEffect.SaveState -> quickState(save = true)
            SpeedEffect.LoadState -> quickState(save = false)
            null -> Unit
        }
    }

    /** Save state / Load state from a shortcut: the save-state slot last used, as the in-game menu's. */
    private fun quickState(save: Boolean) {
        val ss = host.saveStates ?: run { host.toast("Save states are off here"); return }
        val go = { slot: Int -> if (save) host.saveToSlot(slot) else host.loadFromSlot(slot) }
        host.lastSlotCache?.let { return go(it) }
        // Cold cache (the launch's strip refresh has not landed): the pointer file is read off the main thread.
        host.lifecycleScope.launch {
            val slot = withContext(Dispatchers.IO) { ss.lastSlot() }
            go(host.lastSlotCache ?: slot.also { host.lastSlotCache = it })
        }
    }

    val padShortcuts: Map<TouchSlot, TouchShortcut> get() = touchShortcuts.value

    /** A pad skin's SAVE / LOAD (the last-used slot), REWIND (held) or FAST (toggled), as the touch shortcuts go. */
    fun onSkinShortcut(action: SkinAction, down: Boolean) {
        if (down && host.menuOpen) return   // a release always goes through: a held REWIND must not stick
        when (action) {
            SkinAction.SAVE, SkinAction.LOAD -> if (down) quickState(save = action == SkinAction.SAVE)
            SkinAction.REWIND -> onShortcut(ShortcutAction.HOLD_REWIND, down)
            SkinAction.FAST -> onShortcut(ShortcutAction.TOGGLE_FF, down)
            else -> Unit   // MIC, SWAP: the pad layout's own; the strip's slots go to [onPadShortcut]
        }
    }

    /**
     * A touch shortcut button goes through [onShortcut] as a bound button does: hold while down, toggle on the press.
     * Not while the in-game menu is open (the portrait band stays up and drives it): the menu took the shortcuts.
     */
    fun onPadShortcut(slot: TouchSlot, down: Boolean) {
        if (down) {
            if (host.menuOpen) return
            val action = touchShortcuts.value[slot]?.action ?: return
            touchHeld[slot] = action
            onShortcut(action, true)
        } else {
            touchHeld.remove(slot)?.let { onShortcut(it, false) }
        }
    }

    /**
     * The touch buttons to draw now: after the shortcuts are built ([applyShortcuts]), a session starts or ends, and the
     * speed's tiles change (a toggle's look, the Rewind row).
     */
    fun refreshTouchShortcuts() {
        val speed = host.speed
        val before = touchShortcuts.value.keys
        touchShortcuts.value = visibleTouchShortcuts(effectiveShortcuts, speed.output, speed.inSession, rewind = speed.rewindShown)
        padLayout.refreshSkinShortcuts()
        // A button coming or going changes the landscape pad's second row, so its height and reach: lay out again.
        if (touchShortcuts.value.keys != before) host.activity.findViewById<View?>(R.id.root)?.post { renderer.relayoutFrontend() }
    }

    /**
     * This console's or game's shortcut [overrides] now (launch, the in-game Shortcuts page, Restore defaults): the keys
     * are built again. Only while no shortcut key is held: the menu that changes them released them all as it opened.
     */
    fun applyShortcuts(overrides: ShortcutOverrides) {
        shortcutOverrides = overrides
        val effective = Shortcuts.effective(shortcutBase, overrides, shortcutClaimed, host.canRewind, canMic = canMic)
        effectiveShortcuts = effective
        shortcutKeys = ShortcutKeys(effective, shortcutClaimed, SystemClock::uptimeMillis)
        refreshTouchShortcuts()
    }

    fun publishShortcuts(model: InGameMenuModel) = model.setShortcuts(shortcutBase, shortcutOverrides, shortcutClaimed, host.canRewind, canMic)

    /** The pad's fixed mic button ([PadMic]): the session's Mic toggle on DS / 3DS, hidden in netplay; null otherwise. */
    fun padMic(): PadMic? = padMicState.value

    /** The panels' Mic tile: the toggle while shown ([MicControl.shown]); null (no tile) off DS / 3DS and in netplay. */
    fun micTile(): Boolean? = mic?.takeIf { it.shown }?.on

    /**
     * The one way the Mic toggle changes (the pad's mic button, the Mic tile in the menu or the dual-screen panel, the
     * Toggle mic binding). The first turn on of the install asks for RECORD_AUDIO; meanwhile, and after a denial, the game
     * hears noise. Ignored in netplay ([MicControl.setOn]).
     */
    fun setMic(on: Boolean) {
        val m = mic ?: return
        if (m.setOn(on)) {
            host.settings.micAsked = true
            m.setAsked(true)
            micPrompt.launch(android.Manifest.permission.RECORD_AUDIO)   // the prompt only: never the settings page
        }
        applyMic()
    }

    private fun toggleMic() { mic?.let { setMic(!it.on) } }

    /**
     * What the core hears now: the toggle's source while the game runs in front, else silence, which closes the phone
     * mic ([com.swordfish.libretrodroid.GLRetroView.microphoneSource]). [foreground]: as the motion sensors' sync.
     */
    fun pushMicSource(foreground: Boolean = host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        val m = mic ?: return
        val run = !host.menuOpen && !host.focusPaused && !host.quitting && foreground
        host.retroView?.microphoneSource = (if (run) m.source else MicSource.SILENCE).native
    }

    /** The Mic toggle changed (or its permission or session): the source, the pad's button and both panels' tiles. */
    fun applyMic() {
        val m = mic ?: return
        pushMicSource()
        padMicState.value = if (m.shown) PadMic(m.on, ::toggleMic) else null
        menu.menuModel?.let(menu::publishSpeed)
        dual.refreshDualPanel()
    }
}
