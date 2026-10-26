package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.achievements.RaBadgeFolders
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.achievements.sortedBy
import com.nxredux.mobile.cheats.CheatEntry
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.CoreOptionFilter
import com.nxredux.mobile.options.FrontendOption
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.ShaderOptions
import com.nxredux.mobile.options.shownFor
import com.nxredux.mobile.runner.Nds3dMode
import com.nxredux.mobile.settings.stepFrom
import com.nxredux.mobile.shaders.ShaderParam
import com.nxredux.mobile.shortcuts.CaptureResult
import com.nxredux.mobile.shortcuts.ClaimedInput
import com.nxredux.mobile.shortcuts.ConsoleHotkeys
import com.nxredux.mobile.shortcuts.KeyLabels
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutCapture
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.Shortcuts
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.ui.SHORTCUTS_NO_CONTROLLER
import com.nxredux.mobile.ui.SHORTCUT_PROMPT

enum class MenuAction { CONTINUE, QUIT, SAVE_AND_QUIT, QUIT_ONLY, LEAVE_NETPLAY, SAVE_FOR_CONSOLE, SAVE_FOR_GAME, RESTORE_DEFAULTS, RESET_SHADER_PARAMS, RESET, CANCEL }
enum class SlotAction { SAVE, LOAD }

sealed interface PageKind {
    data object Root : PageKind
    data object Options : PageKind
    data object Frontend : PageKind
    /** App-owned rows of this console ([ConsoleSettings]); edited like Frontend rows. */
    data object ConsoleSettings : PageKind
    /**
     * This console's or game's shortcut overrides over Controls → Shortcuts ([InGameMenuModel.setShortcuts]): A binds by
     * press, X sets None here, Y goes back to the default; saved with Save Changes like the other rows.
     */
    data object Shortcuts : PageKind
    data object Shaders : PageKind
    data object ShaderParams : PageKind
    /** Live adjust of one visual setting (frontend or shader) over the paused frame (the menu collapses to a strip). */
    data class Adjust(val key: String) : PageKind
    data object CoreOptions : PageKind
    /** This game's RetroAchievements set, one Option row per achievement (no detail page: A does nothing on a row). */
    data object Achievements : PageKind
    data class Category(val key: String) : PageKind
    data object SaveChanges : PageKind
    /** Quit's choice: Save & Quit (the resume point) or Quit (the last resume point stays), drawn as a dialog ([menuDialog]). */
    data object QuitGame : PageKind
    /** Reset's confirm (reset always asks first): Reset or Cancel, drawn as a dialog over the root ([menuDialog]). */
    data object ResetGame : PageKind
    data object Message : PageKind
    /** Which pad is which player (multiplayer cores only). */
    data object Players : PageKind
}

sealed interface MenuRow : NxDescribedRow {
    val label: String
    override val info: String
    /** The full text the info popup shows when [info] is a short line of it, or none (a compact row); empty: [info] is all. */
    override val detail: String get() = ""
    data class Action(override val label: String, val action: MenuAction, override val info: String = "") : MenuRow
    data class Slot(override val label: String, val action: SlotAction, override val info: String = "") : MenuRow
    data class Submenu(override val label: String, val target: PageKind, override val info: String = "", override val detail: String = "") : MenuRow
    /**
     * [visual]: the setting changes the picture, so A enters adjust mode. [choices]: every value's label, in cycle order
     * (empty when not known); [index]: the current one's, -1 when it is none of them. They pick the row's control
     * ([nxSettingControl]) and let the picker or a segment set a value ([InGameMenuModel.setChoice]).
     */
    data class Option(
        override val label: String,
        val key: String,
        val valueLabel: String,
        override val info: String = "",
        val visual: Boolean = false,
        val choices: List<String> = emptyList(),
        val index: Int = -1,
        override val detail: String = "",
    ) : MenuRow
}

/** The control of [row] ([nxSettingControl] of its choices). */
val MenuRow.Option.control: NxSettingControl get() = nxSettingControl(choices)

/** A popup over the menu's page, taking the input while it shows. */
sealed interface MenuPopup {
    /** Every value of option row [row] of the top page: [current] the one set (-1 none), [cursor] the d-pad's. */
    data class Picker(val row: Int, val title: String, val choices: List<String>, val current: Int, val cursor: Int) : MenuPopup
    /** A row's full description; [scrollSeq] / [scrollDir] as [MenuPage]'s (UP/DOWN scroll a long one). */
    data class Info(val title: String, val text: String, val scrollSeq: Int = 0, val scrollDir: Int = 0) : MenuPopup
}

/** A touch on an options row or its popup ([InGameMenuModel.optionEvent]). */
sealed interface OptionEvent {
    /** Set row [row]'s value to choice [index] (a segment, the switch). */
    data class Choose(val row: Int, val index: Int) : OptionEvent
    data class OpenPicker(val row: Int) : OptionEvent
    /** The row's info icon. */
    data class Info(val row: Int) : OptionEvent
    /** A value in the open picker. */
    data class Pick(val index: Int) : OptionEvent
    data object ClosePopup : OptionEvent
}

/** A player slot for the Players page ([InGameMenuModel.setPlayers]): [key] null = Empty; [connected] false = held for [name]. */
data class PlayerSlotRow(val key: String?, val name: String, val connected: Boolean)

/**
 * A connected physical pad, one a player slot can be set to (benched ones included: choosing one puts it back in play);
 * [name] already numbered when two share one ([com.nxredux.mobile.runner.PadNames]).
 */
data class PlayerPadRow(val key: String, val name: String)

/** One menu screen. [footer] is the page's static text (status line, or the message of a Message page). */
data class MenuPage(
    val kind: PageKind,
    val title: String,
    val rows: List<MenuRow>,
    val selected: Int = 0,
    val footer: String = "",
    /** Plain text an options page shows above its rows (Shortcuts with no controller); not a row, never selected. */
    val intro: String = "",
    /**
     * Message pages only: UP/DOWN ask the UI to scroll the text one step. [scrollSeq] counts the requests (the UI
     * scrolls once per new value), [scrollDir] is the latest one's direction (-1 up, +1 down).
     */
    val scrollSeq: Int = 0,
    val scrollDir: Int = 0,
)

/**
 * [truncatedInfo]: the last info text the UI reported cut off in the list's info area (see [InGameMenuModel.setInfoTruncated]).
 * [panelFocus]: where the controller is in the root's game panel, null on the menu list; [panelTab]: the panel's tab.
 * [panelVersion] counts changes to the panel's other inputs (players, second screen, achievements, header), which the
 * pages do not show, so a host publishing this state on every change still sees a new value.
 * [preview]: the page the root's highlighted row opens, drawn dimmed on the right in place of the game panel: the Options
 * page while the root's Options row is highlighted, else null (the model keeps it current).
 */
data class MenuState(
    val stack: List<MenuPage>,
    val slotIndex: Int,
    val truncatedInfo: String? = null,
    val panelFocus: PanelFocus? = null,
    val panelTab: GamePanelTab = GamePanelTab.SHORTCUTS,
    val panelVersion: Int = 0,
    val popup: MenuPopup? = null,
    val preview: MenuPage? = null,
) {
    val page: MenuPage get() = stack.last()

    /** The selected row's description is cut off on its row: X shows it whole (an options page; the root has none). */
    val selectedInfoTruncated: Boolean
        get() = page.kind != PageKind.Root && page.rows.getOrNull(page.selected)?.info?.let { it.isNotBlank() && it == truncatedInfo } == true

    /** X opens the selected row's info popup: its line is cut off, or it has a fuller detail text ([hasMoreInfo]). */
    val selectedShowsInfo: Boolean
        get() = selectedInfoTruncated || (page.kind != PageKind.Root && page.rows.getOrNull(page.selected)?.hasMoreInfo == true)
}

/** What the host must do in response to input; the model never touches the platform or the core. */
sealed interface MenuEffect {
    data object Close : MenuEffect
    /** Quit, writing the auto state (the resume point). */
    data object Quit : MenuEffect
    /** Quit without the auto state: the last resume point stays as it is (the battery save is still written). */
    data object QuitWithoutResume : MenuEffect
    /**
     * netplay: Leave on the "Leave netplay?" root. Lockstep: the host ends the session and quits without an auto state.
     * A link session ([InGameMenuModel.netplayLink]): the host ends the link and the game plays on, the menu open on the
     * normal root once the host calls [InGameMenuModel.endNetplay].
     */
    data object LeaveNetplay : MenuEffect
    data class SaveSlot(val slot: Int) : MenuEffect
    data class LoadSlot(val slot: Int) : MenuEffect
    /** Load the core's options, then call [InGameMenuModel.showCoreOptions]. */
    data object OpenCoreOptions : MenuEffect
    data class SetOption(val key: String, val value: String) : MenuEffect
    /** Apply a frontend (display) setting now; persisted only by Save Changes. */
    data class SetFrontend(val key: String, val value: String) : MenuEffect
    /** Apply a shader setting (preset or parameter) now; persisted only by Save Changes. */
    data class SetShader(val key: String, val value: String) : MenuEffect
    /** Apply several shader settings at once (Reset Parameters). */
    data class SetShaders(val entries: List<Pair<String, String>>) : MenuEffect
    /** Turn one cheat on/off now (the host re-applies the whole enabled set); persisted only by Save Changes > For game. */
    data class SetCheat(val index: Int, val enabled: Boolean) : MenuEffect
    /** Put disc [index] (0-based) in the drive now; not persisted. [toast]: the panel's Swap disc, which says "Disc N of M". */
    data class ChangeDisc(val index: Int, val toast: Boolean = false) : MenuEffect
    /** Turn the second-screen setting [on] or off (the panel's shortcut; already flipped in the model); the host persists it and re-syncs the dual mode. */
    data class SetSecondScreen(val on: Boolean) : MenuEffect
    /** Flip achievement [id]'s notification mute (already flipped in the model); the host persists it. */
    data class ToggleMute(val id: Int) : MenuEffect
    /**
     * Put pad [key] in player [slot] (0-based), swapping as the host's slot model does and back in play if benched; null
     * empties it, benching the pad that was there (persisted by the host).
     */
    data class SetPlayer(val slot: Int, val key: String?) : MenuEffect
    data object SaveForConsole : MenuEffect
    data object SaveForGame : MenuEffect
    data object RestoreDefaults : MenuEffect
    /**
     * The in-game Shortcuts page changed this console's or game's [overrides]: the host's bindings follow now; persisted
     * only by Save Changes, which writes the overridden rows alone (spec: overrides inherit).
     */
    data class ShortcutsChanged(val overrides: ShortcutOverrides) : MenuEffect
    /**
     * The panel's Fast forward or Rewind tile: set the toggles ([ff], [rewind]; null leaves one alone), then close the
     * menu so the game runs with it on (spec). Turning one on turns the other off, as a Toggle binding does.
     */
    data class SetSpeed(val ff: Boolean?, val rewind: Boolean?) : MenuEffect
    /** The panel's Mic tile: the session's mic to [on], then close the menu. */
    data class SetMic(val on: Boolean) : MenuEffect
    /** The Reset dialog's Reset: restart the game, then close the menu. */
    data object ResetGame : MenuEffect
}

/**
 * The in-game pause menu as a page stack: root (Continue/Save/Load/Options/Quit) → Options → Console Settings (→ Adjust,
 * only for a console with its own rows), Frontend (→ Adjust), Shaders (→ Shader Parameters, → Adjust), Core Options →
 * category, and Save Changes (the cheats are the game panel's Cheats tab). Slots are 0-based internally and 1-based
 * in effects.
 *
 * Beside the root list (but for Save / Load, which show their slot, and Options, which previews its page:
 * [MenuState.preview]) sits the game panel ([panelState]): RIGHT enters it on its tab row, L1/R1 switch its tabs, LEFT at
 * its left edge or B comes back; a page push or pop leaves it. On Options RIGHT opens the page, as A (as Save / Load's
 * RIGHT works their card).
 *
 * The host builds a new model each time the menu opens and calls [setFrontend] and [setShaders] right away;
 * [MenuEffect.Close] does not reset the stack.
 */
class InGameMenuModel(private val gameTitle: String, private val slotCount: Int, initialSlot: Int = 0) {

    init {
        require(slotCount > 0) { "slotCount must be positive" }
    }

    /** The core's disc count and current disc (0-based), from [setDiscs]; the Disc row shows when [discCount] > 1. */
    private var discCount = 0
    private var discIndex = 0
    /** From [setSecondScreen]; the panel shows the Second screen shortcut only while a second display is [secondConnected]. */
    private var secondConnected = false
    private var secondOn = true
    /** From [setSpeedTiles]: the panel's Fast forward and Rewind tiles' live state; [speedRewind] null: no Rewind tile. */
    private var speedFf = false
    private var speedRewind: Boolean? = null
    /** From [setMicTile]: the panel's Mic tile's state; null: no tile (not a mic console). */
    private var micTile: Boolean? = null
    /** The Reset dialog up was opened with the menu for it ([openResetDialog]): Cancel and B resume the game. */
    private var resetClosesOnCancel = false
    /** From [setPlayers]; the panel shows its Players tab when there is more than one slot. */
    private var playerSlots: List<PlayerSlotRow> = emptyList()
    private var playerPads: List<PlayerPadRow> = emptyList()
    /** From [setPanelHeader]; until then the panel's header is the game's title alone. */
    private var panelHeader: GamePanelHeader? = null

    /** The panel's shortcut grid columns as drawn (the UI reports them: 2 when narrow); UP/DOWN step a row of them. */
    var panelColumns: Int = 4

    /** The Options page [optionsPreview] last built, and the inputs it was built from. */
    private var previewPage: Pair<OptionsPreviewInputs, MenuPage>? = null

    var state: MenuState = MenuState(listOf(rootPage()), initialSlot.coerceIn(0, slotCount - 1))
        private set(value) { field = withPreview(value) }

    /** [s] with its [MenuState.preview] for the root's highlighted row (the Options page beside the Options row). */
    private fun withPreview(s: MenuState): MenuState {
        val previews = s.stack.size == 1 && (s.page.rows.getOrNull(s.page.selected) as? MenuRow.Submenu)?.target == PageKind.Options
        val preview = if (previews) optionsPreview() else null
        // The same page object while its inputs hold, so the check is usually an identity one.
        return if (preview == s.preview) s else s.copy(preview = preview)
    }

    /** What [optionsPage] reads: the Console Settings row's inputs ([consoleRows]) and the footer's [status]. */
    private data class OptionsPreviewInputs(val tag: String, val locked: Set<String>, val second: Boolean, val status: ConfigSource, val screen: ScreenClass?)

    /** [optionsPage], rebuilt only when one of its inputs has changed since the last call. */
    private fun optionsPreview(): MenuPage {
        val inputs = OptionsPreviewInputs(frontendTag, frontendLocked, secondConnected, status, screen)
        previewPage?.let { (key, page) -> if (key == inputs) return page }
        return optionsPage().also { previewPage = inputs to it }
    }

    private var options: List<CoreOption> = emptyList()
    private var locked: Set<String> = emptySet()
    private var status: ConfigSource = ConfigSource.NONE

    private var frontend: Map<String, String> = FrontendOptions.defaults()
    private var overlays: List<String> = emptyList()
    private var frontendLocked: Set<String> = emptySet()
    private var frontendTag = ""                 // the console's tag: selects the Console Settings rows
    private var overlayAvailable = true          // false while the DS screens are split (the overlay is off)

    /**
     * The game window's [ScreenClass] (null: unknown): the DS and 3DS layout rows offer that screen's values and show its
     * defaults ([ConsoleSettings.tableFor]). Set before the menu's pages are built.
     */
    var screen: ScreenClass? = null

    /** The biggest DS 3D scale this display reaches (null: unknown): the 3D Resolution rows offer scales up to it. Set before the menu's pages are built. */
    var displayMax3d: Int? = null

    private var nds3dNow: Int? = null            // the DS 3D scale in use (null: unknown / not DS)
    private var nds3dSoftware = false            // the core renders in software: the 3D Resolution rows take no effect

    /**
     * The DS 3D scale in use ([now], null: unknown) and whether the core renders in [software] (the 3D Resolution rows are
     * then static, with a note). Refreshes open Console Settings/Frontend/Adjust pages.
     */
    fun setNds3d(now: Int?, software: Boolean) {
        if (now == nds3dNow && software == nds3dSoftware) return
        nds3dNow = now
        nds3dSoftware = software
        refreshFrontendPages()
    }

    private fun softwareLocked(key: String) = nds3dSoftware && key in NdsOptions.RESOLUTION_KEYS

    // Options → Shortcuts ([setShortcuts]): Controls → Shortcuts, this console's or game's overrides over it, the
    // console's hotkeys (they win: a row on one is Unavailable, press to bind refuses one), whether the core rewinds.
    private var shortcutBase = ShortcutSet.EMPTY
    /** This console's or game's overrides as edited on the Shortcuts page (absent: inherits Controls → Shortcuts). */
    var shortcutOverrides: ShortcutOverrides = ShortcutOverrides.NONE
        private set
    private var shortcutClaimed: List<ClaimedInput> = ConsoleHotkeys.always
    private var shortcutRewind = false
    private var shortcutMic = false
    /** A controller is connected ([setControllerConnected]): without one the page has no bind rows (the on-screen pad binds nothing). */
    private var controllerConnected = true
    /** Press to bind on the Shortcuts page; the host hands it raw key codes ([takesKey], [captureKey]). */
    private var capture = ShortcutCapture(shortcutClaimed)
    private var capturing: ShortcutAction? = null

    /** A Shortcuts row waits for a press: the host gives raw keys to [captureKey] first ([takesKey]). */
    val capturingShortcut: Boolean get() = capturing != null

    private var shaders: Map<String, String> = ShaderOptions.fromConfig(emptyMap())
    private var presets: List<String> = emptyList()
    private var params: List<ShaderParam> = emptyList()

    /**
     * netplay: a Wi-Fi netplay session is running, so the root page is only "Leave netplay?" (Continue / Leave): states,
     * options, cheats and the rest would desync the other device. Set before the menu shows, then [refresh].
     */
    var netplay: Boolean = false

    /**
     * netplay: the session is over and the game plays on alone: the root is the plain Continue / Quit (still no states,
     * options, cheats or discs). Quit leaves without an auto state (a netplay launch has no save states), so it is the
     * plain [MenuAction.QUIT_ONLY], with no Save & Quit.
     */
    var netplayEnded: Boolean = false

    /**
     * netplay: the session is a link one (GB/GBC/GBA, each player on their own save): Leave ends the link and the game
     * plays on as a local one, which the root's footer says. Set with [netplay], then [refresh].
     */
    var netplayLink: Boolean = false

    /** Rebuilds the root page after a host-side flag ([netplay]) changed. */
    fun refresh() = refreshRoot()

    /**
     * netplay: the session ended. [local] (a link session, [com.nxredux.mobile.netplay.NetplaySession.endsAsLocalGame]):
     * the game is a normal local one from now on, so the full root and the panel beside it; otherwise the plain
     * Continue / Quit ([netplayEnded]). The host sets [cheatsAvailable] first.
     */
    fun endNetplay(local: Boolean) {
        if (!netplay) return
        if (local) { netplay = false; netplayEnded = false } else netplayEnded = true
        refreshRoot()
    }

    /**
     * The Save / Load slot shown, 0-based: what the constructor's initialSlot is for a menu that was open before the
     * game had save states (a netplay link session that became a local game).
     */
    fun showSlot(index: Int) {
        state = state.copy(slotIndex = index.coerceIn(0, slotCount - 1))
    }

    /** True for a core that takes cheats (the host sets it as the menu opens): the panel's Cheats tab. */
    var cheatsAvailable: Boolean = false
        set(value) { field = value; fixPanel() }

    /** The game's cheats ([setCheats]): the panel's Cheats tab. */
    private var cheats: List<CheatEntry> = emptyList()
    private var cheatsFolder = ""     // "Cheats/<TAG>", for the empty tab's text
    private var cheatsExpected = ""   // the first file name looked for; blank until the host has resolved

    /** Null while the game is not recognised; the panel's Achievements tab shows only with a set once [achievementsAvailable]. */
    private var achievements: List<RaAchievement>? = null
    private var achievementsAvailable = false
    private var achievementSummary: Pair<Int, Int>? = null
    private var achievementOrder = RaSortOrder.DEFAULT
    private var muted: Set<Int> = emptySet()
    private var badges: Map<String, String> = emptyMap()

    /** Y on the Achievements page: hide unlocked (and pending) achievements. */
    var achievementsLockedOnly = false
        private set

    /**
     * The menu is drawn as two panes ([inGameMenuSplit], set by the host's UI): RIGHT on a row that opens a page opens
     * it, as A (a column browser); value rows still change.
     */
    var split: Boolean = false

    /** An [MenuEffect.OpenCoreOptions] was emitted and its [showCoreOptions] has not arrived yet. */
    private var coreOptionsPending = false

    fun press(button: NxButton): MenuEffect? =
        pressMenu(button).also { if (it == MenuEffect.Close) state = state.copy(panelFocus = null, popup = null) }

    private fun pressMenu(button: NxButton): MenuEffect? {
        state.popup?.let { return pressPopup(it, button) }
        if (state.page.kind == PageKind.Message) {
            when (button) {
                NxButton.A, NxButton.B, NxButton.X -> { pop(); return null }
                NxButton.UP, NxButton.DOWN -> {
                    val p = state.page
                    replaceTop(p.copy(scrollSeq = p.scrollSeq + 1, scrollDir = if (button == NxButton.UP) -1 else 1))
                    return null
                }
                else -> Unit
            }
        }
        if (state.page.kind is PageKind.Adjust) return pressAdjust(button)
        if (state.page.kind == PageKind.Shortcuts) {
            // Waiting for a press, the controller's keys are the capture's ([captureKey]); a touch B cancels, nothing
            // else (MENU included, L23) does anything.
            if (capturing != null) { if (button == NxButton.B) cancelCapture(); return null }
            when (button) {
                NxButton.X -> return clearShortcut(state.page.selected)
                NxButton.Y -> return defaultShortcut(state.page.selected)
                else -> Unit
            }
        }
        if (state.panelFocus != null) return pressPanel(button)
        if ((button == NxButton.L1 || button == NxButton.R1) && panelShown()) { stepTab(if (button == NxButton.L1) -1 else 1); return null }
        if (state.page.kind == PageKind.Achievements) {
            when (button) {
                NxButton.Y -> { achievementsLockedOnly = !achievementsLockedOnly; refreshAchievementsPage(); return null }
                NxButton.X -> selectedAchievementId()?.let { return toggleMute(it) }
                else -> Unit
            }
        } else if (button == NxButton.X) {
            // A button press on Quit (a pad's, the on-screen pad's) picks the quit itself: X leaves the resume point as it is.
            if (selectedAction() == MenuAction.QUIT) return MenuEffect.QuitWithoutResume
            // X shows a row's full description when its row cuts it off or says less, also on visual rows where A adjusts.
            // Achievements keep X = Mute above.
            if (state.selectedShowsInfo) showInfo(state.page.selected)
            return null
        }
        return when (button) {
            NxButton.UP -> { move(-1); null }
            NxButton.DOWN -> { move(1); null }
            NxButton.LEFT -> change(-1)
            NxButton.RIGHT -> when {
                // The Disc row keeps RIGHT for its value; every other row beside the panel enters it.
                panelShown() && state.page.rows.getOrNull(state.page.selected) !is MenuRow.Option -> { state = state.copy(panelFocus = PanelFocus.Tabs); null }
                // Options moves into its preview, as A, whether or not the menu splits.
                (split || state.preview != null) && opensPage(state.page.rows.getOrNull(state.page.selected)) -> activate(state.page.selected)
                else -> change(1)
            }
            // A on Quit saves and quits; a touch tap ([activate]) asks how to quit instead.
            NxButton.A -> if (selectedAction() == MenuAction.QUIT) MenuEffect.Quit else activate(state.page.selected)
            NxButton.B -> back()
            NxButton.MENU -> MenuEffect.Close
            else -> null
        }
    }

    /**
     * Input while a popup shows: the picker's UP/DOWN move (wrapping), A picks, B closes; the info's A/B/X close it and
     * UP/DOWN scroll it. MENU closes the menu with it.
     */
    private fun pressPopup(popup: MenuPopup, button: NxButton): MenuEffect? {
        if (button == NxButton.MENU) return MenuEffect.Close
        when (popup) {
            is MenuPopup.Picker -> when (button) {
                NxButton.UP, NxButton.DOWN -> {
                    val n = popup.choices.size
                    if (n > 0) state = state.copy(popup = popup.copy(cursor = (popup.cursor + if (button == NxButton.UP) -1 else 1).mod(n)))
                }
                NxButton.A -> return pickChoice(popup.cursor)
                NxButton.B -> closePopup()
                else -> Unit
            }
            is MenuPopup.Info -> when (button) {
                NxButton.A, NxButton.B, NxButton.X -> closePopup()
                NxButton.UP, NxButton.DOWN ->
                    state = state.copy(popup = popup.copy(scrollSeq = popup.scrollSeq + 1, scrollDir = if (button == NxButton.UP) -1 else 1))
                else -> Unit
            }
        }
        return null
    }

    /** A touch on an options row's control, its info icon, or the popup over it. */
    fun optionEvent(event: OptionEvent): MenuEffect? = when (event) {
        is OptionEvent.Choose -> setChoice(event.row, event.index)
        is OptionEvent.OpenPicker -> { openPicker(event.row); null }
        is OptionEvent.Info -> { showInfo(event.row); null }
        is OptionEvent.Pick -> pickChoice(event.index)
        OptionEvent.ClosePopup -> { closePopup(); null }
    }

    /** Opens the picker for option row [index] of the top page (selecting it), its current value under the cursor. */
    fun openPicker(index: Int) {
        val row = state.page.rows.getOrNull(index) as? MenuRow.Option ?: return
        if (row.choices.isEmpty() || state.page.kind is PageKind.Adjust) return
        select(index)
        state = state.copy(popup = MenuPopup.Picker(index, row.label, row.choices, row.index, row.index.coerceAtLeast(0)))
    }

    /** Shows row [index]'s full description ([fullInfo]) in the info popup (selecting it); nothing for a row without one. */
    fun showInfo(index: Int) {
        val row = state.page.rows.getOrNull(index) ?: return
        val text = row.fullInfo
        if (text.isBlank()) return
        select(index)
        state = state.copy(popup = MenuPopup.Info(row.label, text))
    }

    /** The open picker's choice [index]: closes it and sets the value ([setChoice]). */
    fun pickChoice(index: Int): MenuEffect? {
        val picker = state.popup as? MenuPopup.Picker ?: return null
        closePopup()
        // The option it opened on, wherever it is now: a core options re-read can show or hide rows above it.
        val rows = state.page.rows
        fun same(r: MenuRow?) = r is MenuRow.Option && r.label == picker.title && r.choices == picker.choices
        val row = picker.row.takeIf { same(rows.getOrNull(it)) } ?: rows.indexOfFirst(::same).takeIf { it >= 0 } ?: return null
        return setChoice(row, index)
    }

    fun closePopup() {
        if (state.popup != null) state = state.copy(popup = null)
    }

    /**
     * Sets option row [row] of the top page to its choice [index] ([MenuRow.Option.choices]), selecting it: the effect
     * the LEFT/RIGHT cycle would give for that value; null when it is already set or out of range.
     */
    fun setChoice(row: Int, index: Int): MenuEffect? {
        val r = state.page.rows.getOrNull(row) as? MenuRow.Option ?: return null
        if (index !in r.choices.indices || state.page.kind == PageKind.Achievements) return null
        select(row)
        if (index == r.index) return null
        return when {
            r.key.startsWith(PLAYER_KEY_PREFIX) -> {
                val candidates = playerCandidates()
                if (index >= candidates.size) return null
                MenuEffect.SetPlayer(r.key.removePrefix(PLAYER_KEY_PREFIX).toInt(), candidates[index])
            }
            r.key == DISC_KEY -> {
                if (index >= discCount) return null
                discIndex = index
                refreshRoot()
                MenuEffect.ChangeDisc(index)
            }
            state.page.kind == PageKind.Shortcuts -> touchSlot(r.key)?.let { slot ->
                touchChoices().let { if (index < it.size) setTouchOverride(slot, it[index]) else null }
            }
            isSettingsPage(state.page.kind) -> settingValues(r.key)?.getOrNull(index)?.let { setFrontendValue(r.key, it) }
            else -> options.firstOrNull { it.key == r.key }?.let { o -> CoreOptionFilter.visibleValues(o).getOrNull(index)?.let { setCoreOption(o, it) } }
        }
    }

    /**
     * The UI laid out the selected row's description [info] on its row and it did ([truncated]) or did not fit. Keyed by the
     * text, so a report for a row that is no longer selected never counts for the new one.
     */
    fun setInfoTruncated(info: String, truncated: Boolean) {
        val next = if (truncated) info else state.truncatedInfo.takeIf { it != info }
        if (next != state.truncatedInfo) state = state.copy(truncatedInfo = next)
    }

    /** Highlights row [index] of the top page; on the root the focus comes back from the panel to the list. */
    fun select(index: Int) {
        if (index in state.page.rows.indices) {
            if (capturing != null && index != state.page.selected) cancelCapture()   // another row: the wait ends
            replaceTop(state.page.copy(selected = index))
            state = state.copy(panelFocus = null)
        }
    }

    fun activate(index: Int): MenuEffect? {
        if (state.page.kind is PageKind.Adjust) return null
        // A tap while a Shortcuts row waits ends the wait, as B (a touch-only player can't press one).
        if (capturing != null) { cancelCapture(); return null }
        val row = state.page.rows.getOrNull(index) ?: return null
        select(index)
        return when (row) {
            is MenuRow.Action -> actionEffect(row.action)
            is MenuRow.Slot -> if (row.action == SlotAction.SAVE) MenuEffect.SaveSlot(state.slotIndex + 1) else MenuEffect.LoadSlot(state.slotIndex + 1)
            is MenuRow.Submenu -> openSubmenu(row.target)
            is MenuRow.Option -> when {
                // An achievement row opens nothing (its description is on the row).
                state.page.kind == PageKind.Achievements -> null
                // A bind row waits for a press ([captureKey]).
                state.page.kind == PageKind.Shortcuts && row.choices.isEmpty() -> { bindAction(row.key)?.let(::startCapture); null }
                // The root has no descriptions (Disc N/M): A shows it.
                state.page.kind == PageKind.Root -> { if (row.fullInfo.isNotBlank()) push(message(row.label, row.fullInfo)); null }
                row.visual -> { push(adjustPage(row.key)); null }
                else -> when (row.control) {
                    NxSettingControl.SWITCH -> setChoice(index, 1 - row.index.coerceIn(0, 1))
                    NxSettingControl.PICKER -> { openPicker(index); null }
                    NxSettingControl.SEGMENTED -> change(1)
                    NxSettingControl.VALUE -> null
                }
            }
        }
    }

    /** The action of the top page's highlighted row, or null for another row. */
    private fun selectedAction(): MenuAction? = (state.page.rows.getOrNull(state.page.selected) as? MenuRow.Action)?.action

    /** The top page sits on the root and came from the game panel: no root row opens Achievements or Players. */
    private fun fromPanel(): Boolean = state.stack.size == 2 &&
        (state.page.kind == PageKind.Achievements || state.page.kind == PageKind.Players)

    /** [row] of the top page opens a page of rows: a submenu. */
    private fun opensPage(row: MenuRow?): Boolean = row is MenuRow.Submenu

    /**
     * A tap on row [index] of the dimmed left pane, the page of kind [parent] under the top one ([menuPanes]): that level
     * comes back, as B would, on [index]; a row that opens a page opens it in place of the old one, as A there would. The
     * row already open, or a [parent] no longer under the top page, does nothing: a one-tap list ([nxTap]) calls this
     * twice, and the second call finds the row open (or the level focused). A page the game panel opened beside the root
     * ([fromPanel]) leaves the root's highlighted row unopened: a tap there comes back as on any other row.
     */
    fun tapParent(parent: PageKind, index: Int): MenuEffect? {
        val stack = state.stack
        if (stack.size < 2 || stack[stack.size - 2].kind != parent || state.page.kind is PageKind.Adjust) return null
        val under = stack[stack.size - 2]
        if (index !in under.rows.indices || (index == under.selected && !(parent == PageKind.Root && fromPanel()))) return null
        pop()
        select(index)
        return if (opensPage(state.page.rows[index])) activate(index) else null
    }

    /**
     * A tap on row [index] of the root's [MenuState.preview]: its page opens, as A on the root's row, with that row
     * highlighted. Nothing without a preview: a one-tap list ([nxTap]) may call this twice, and the second finds it open.
     */
    fun tapPreview(index: Int): MenuEffect? {
        val preview = state.preview ?: return null
        if (index !in preview.rows.indices) return null
        val effect = activate(state.page.selected)
        select(index)
        return effect
    }

    /**
     * Called by the host after [MenuEffect.OpenCoreOptions] with every option the core declared. Ignored when
     * nothing is pending or the user has left the Options page meanwhile (the host loads asynchronously).
     */
    fun showCoreOptions(all: List<CoreOption>, locked: Set<String>) {
        val pending = coreOptionsPending
        coreOptionsPending = false
        if (!pending || state.page.kind != PageKind.Options) return
        options = all
        this.locked = locked
        val page = coreOptionsPage()
        push(if (page.rows.isEmpty()) message("Core Options", NO_LIVE_OPTIONS) else page)
    }

    /** Refreshes option pages after values or visibility changed, keeping selections in range. */
    fun updateOptions(all: List<CoreOption>) {
        options = all
        state = state.copy(stack = state.stack.map { p ->
            when (val k = p.kind) {
                PageKind.CoreOptions -> rebuilt(p, coreOptionsPage())
                is PageKind.Category -> rebuilt(p, categoryPage(k.key))
                else -> p
            }
        })
    }

    /**
     * The frontend values in use (missing keys take their defaults for [overlays] and [tag]), the overlay file names
     * offered for the Overlay setting, the config's locked keys (locked frontend settings are hidden), the console's
     * [tag] (it selects the console's own rows, shown on Options → Console Settings), and whether overlays can show
     * ([overlayAvailable] is false while the DS screens are split). Refreshes open Console Settings/Frontend/Adjust pages.
     */
    fun setFrontend(values: Map<String, String>, overlays: List<String>, locked: Set<String>, tag: String = "", overlayAvailable: Boolean = true) {
        val tagChanged = tag != frontendTag
        val hadConsoleRows = consoleRows().isNotEmpty()
        frontendTag = tag
        this.overlayAvailable = overlayAvailable
        frontend = FrontendOptions.fromConfig(values, tag)
        this.overlays = overlays
        frontendLocked = locked
        // A new lock set (Restore defaults, the Wii's sensors once known) or console may lock or free every Console
        // Settings row: an open Options page offers the row only while it leads somewhere, never to an empty page.
        if (consoleRows().isNotEmpty() != hadConsoleRows) rebuildOptions()
        refreshFrontendPages()
        if (tagChanged) fixPanel()   // the panel's header falls back to the console's logo
    }

    /**
     * The shader values in use (preset + stored parameters), the presets offered, and the active preset's
     * parameters. Refreshes open Shaders/ShaderParams/Adjust pages.
     */
    fun setShaders(values: Map<String, String>, presets: List<String>, params: List<ShaderParam>) {
        shaders = ShaderOptions.fromConfig(values)
        this.presets = presets
        this.params = params
        refreshFrontendPages()
    }

    /**
     * The cheats resolved for this game (the host's set, a toggle from the dual-screen panel included), the folder they
     * were looked for in and the first file name tried (both only for the empty Cheats tab's text). Refreshes the panel.
     */
    fun setCheats(entries: List<CheatEntry>, folder: String, expected: String) {
        cheats = entries
        cheatsFolder = folder
        cheatsExpected = expected
        fixPanel()
    }

    /**
     * This game's achievements ([list] null: the game is not recognised), the (unlocked, total) [summary] for the
     * panel's header, the sort [order], the [muted] ids and the [badges] listing (file name -> image model). Makes the
     * panel's Achievements tab visible (with a set) and refreshes an open Achievements page.
     */
    fun setAchievements(list: List<RaAchievement>?, summary: Pair<Int, Int>?, order: RaSortOrder, muted: Set<Int>, badges: Map<String, String>) {
        achievements = list
        achievementSummary = summary
        achievementOrder = order
        this.muted = muted
        this.badges = badges
        achievementsAvailable = true
        refreshAchievementsPage()
        fixPanel()
    }

    /** Shows or hides the panel's Achievements tab (hidden when RetroAchievements is off or signed out). */
    fun setAchievementsAvailable(available: Boolean) {
        achievementsAvailable = available
        fixPanel()
    }

    /** The badge image model for [a] (the greyed `_lock` variant while locked); null until downloaded. */
    fun badgePath(a: RaAchievement): String? =
        RaBadgeFolders.fileName(a.badge, a.state == RaAchievementState.LOCKED)?.let(badges::get)

    fun isMuted(id: Int): Boolean = id in muted

    fun setStatus(source: ConfigSource) {
        status = source
        state = state.copy(stack = state.stack.map {
            if (it.kind == PageKind.Options || it.kind == PageKind.SaveChanges) it.copy(footer = statusText()) else it
        })
    }

    fun showMessage(title: String, text: String) = push(message(title, text))

    /**
     * The disc state the core reports (disk-control interface): with more than one disc the root page shows "Disc N/M"
     * under Continue. Called by the host after the menu opens; keeps the selected root row.
     */
    fun setDiscs(current: Int, count: Int) {
        discCount = count.coerceAtLeast(0)
        discIndex = current.coerceIn(0, (discCount - 1).coerceAtLeast(0))
        refreshRoot()
    }

    /**
     * A second display is [connected] and the setting is [on]; shows (or hides) the panel's Second screen shortcut and
     * Console Settings' Bottom screen row, keeping the selection.
     */
    fun setSecondScreen(connected: Boolean, on: Boolean) {
        val changed = connected != secondConnected
        secondConnected = connected
        secondOn = on
        refreshRoot()
        if (!changed) return
        // Console Settings may come or go with its Bottom screen row (a console whose other rows are all locked).
        rebuildOptions()
        refreshFrontendPages()
    }

    /** The session's mic is on ([on]) or off; null where this isn't a mic console (DS/3DS): no Mic tile. */
    fun setMicTile(on: Boolean?) {
        micTile = on
        fixPanel()
    }

    /**
     * Fast forward runs now ([ff]) and rewinding does ([rewind]; null where this game can't rewind: the core or its Rewind
     * row): the panel's tiles show it, so a toggle a binding left on (or one removed live) can be turned off here.
     */
    fun setSpeedTiles(ff: Boolean, rewind: Boolean?) {
        speedFf = ff
        speedRewind = rewind
        fixPanel()
    }

    /**
     * A Reset binding or the second-screen panel's Reset tile: the menu shows the Reset dialog, as its own panel's Reset
     * tile does. [closesOnCancel]: the menu opened for it, so Cancel and B close the menu and the game plays on (the
     * player never asked for the menu); otherwise they come back to the root. Nothing in netplay (reset would desync the
     * players) or while the dialog is already up.
     */
    fun openResetDialog(closesOnCancel: Boolean = false) {
        if (netplay || state.page.kind == PageKind.ResetGame) return
        popTo(PageKind.Root)
        push(resetPage())
        resetClosesOnCancel = closesOnCancel
    }

    /** Leaving the Reset dialog without a reset: the menu closes if it opened for the dialog, else back to the root. */
    private fun cancelReset(): MenuEffect? {
        val close = resetClosesOnCancel
        resetClosesOnCancel = false
        popTo(PageKind.Root)
        return if (close) MenuEffect.Close else null
    }

    /** Open Options pages built again (Console Settings offered or not), keeping the selected row and the status footer. */
    private fun rebuildOptions() {
        state = state.copy(stack = state.stack.map { p ->
            if (p.kind == PageKind.Options) rebuilt(p, optionsPage()).copy(footer = statusText()) else p
        })
    }

    private fun changeDisc(delta: Int): MenuEffect? {
        if (discCount < 2) return null
        discIndex = ((discIndex + delta) % discCount + discCount) % discCount
        refreshRoot()
        return MenuEffect.ChangeDisc(discIndex)
    }

    /**
     * The host's player slots and connected pads. Called when the menu opens and after every [MenuEffect.SetPlayer] the
     * host applied; refreshes the panel and an open Players page, keeping their rows.
     */
    fun setPlayers(slots: List<PlayerSlotRow>, pads: List<PlayerPadRow>) {
        playerSlots = slots
        playerPads = pads
        refreshRoot()
        state = state.copy(stack = state.stack.map { p -> if (p.kind == PageKind.Players) rebuilt(p, playersPage()).copy(footer = PLAYERS_FOOTER) else p })
    }

    private fun playersPage() = MenuPage(
        PageKind.Players, PLAYERS_LABEL,
        // Worded as the game panel words a slot (one wording for both).
        playerSlots.mapIndexed { i, s ->
            MenuRow.Option(
                "Player ${i + 1}", "$PLAYER_KEY_PREFIX$i", GamePanelModel.playerValue(s),
                choices = listOf("Empty") + playerPads.map { it.name }, index = playerCandidates().indexOf(s.key),
            )
        },
        footer = PLAYERS_FOOTER,
    )

    private fun changePlayer(slot: Int, delta: Int): MenuEffect =
        MenuEffect.SetPlayer(slot, GamePanelModel.cyclePlayer(slot, delta, playerSlots, playerPads))

    /** Rebuilds the root page, keeping the selection on the row with the same label (root labels are unique), and the panel. */
    private fun refreshRoot() {
        state = state.copy(stack = state.stack.map { p ->
            if (p.kind != PageKind.Root) p else {
                val fresh = rootPage()
                val label = p.rows.getOrNull(p.selected)?.label
                fresh.copy(selected = fresh.rows.indexOfFirst { it.label == label }.coerceAtLeast(0))
            }
        })
        fixPanel()
    }

    // ------------------------------------------------------------------ the game panel

    /** The panel's header: the game, its art and times ([GamePanelHeader]), the clock and battery. Shown at the next draw. */
    fun setPanelHeader(header: GamePanelHeader) {
        panelHeader = header
        fixPanel()
    }

    /**
     * What the root's game panel draws: the header (the achievement count from [setAchievements] while there is a set),
     * the tabs that apply, the Shortcuts (Swap disc, Second screen, Fast forward, Rewind, Reset), the achievements in the chosen
     * order, the cheats, the Players page's rows, and the controller's [MenuState.panelFocus].
     */
    fun panelState(): GamePanelState {
        // Asked on every recomposition of the menu: rebuilt (the achievements sorted again) only when an input changed.
        val key = listOf(
            state.panelTab, state.panelFocus, panelHeader, achievements, achievementsAvailable, achievementSummary, achievementOrder,
            badges, playerSlots, playerPads, discCount, discIndex, secondConnected, secondOn, speedFf, speedRewind, micTile, netplay, frontendTag, cheats, cheatsAvailable,
            cheatsFolder, cheatsExpected,
        )
        panelCache?.takeIf { it.first == key }?.let { return it.second }
        return buildPanelState().also { panelCache = key to it }
    }

    private var panelCache: Pair<List<Any?>, GamePanelState>? = null

    private fun buildPanelState(): GamePanelState {
        val tabs = panelTabs()
        val ra = achievementSummary.takeIf { GamePanelTab.ACHIEVEMENTS in tabs }
        val header = panelHeader ?: GamePanelHeader(gameTitle, null, frontendTag, 0L, null, "", null)
        return GamePanelState(
            header.copy(ra = ra), tabs, GamePanelModel.resolveTab(state.panelTab, tabs), panelShortcuts(), panelAchievements(),
            badges, playerSlots, playerPads, panelCheats(),
            GamePanelModel.noCheatsText(cheatsFolder, cheatsExpected), focus = state.panelFocus,
        )
    }

    /**
     * A tap on the panel (or A on its focused item): Swap disc and Second screen as the menu's own effects, an
     * achievement's detail page, a player slot or pad as the Players page sets them, a cheat switched, a tab shown, a
     * Fast forward / Rewind toggle ([MenuEffect.SetSpeed]: the host closes the menu), or the Reset dialog pushed.
     */
    fun panelEvent(event: GamePanelEvent): MenuEffect? {
        // A tap moves the controller's focus (while it is in the panel) to the item tapped; A's own item is already there.
        if (state.panelFocus != null) panelItemIndex(event)?.let { state = state.copy(panelFocus = PanelFocus.Item(it)) }
        return panelEffect(event)
    }

    private fun panelEffect(event: GamePanelEvent): MenuEffect? = when (event) {
        GamePanelEvent.SwapDisc -> (changeDisc(1) as? MenuEffect.ChangeDisc)?.copy(toast = true)
        is GamePanelEvent.SetSecondScreen -> {
            secondOn = event.on
            refreshRoot()
            MenuEffect.SetSecondScreen(event.on)
        }
        is GamePanelEvent.SelectTab -> {
            if (event.tab in panelTabs()) state = state.copy(panelTab = event.tab, panelFocus = state.panelFocus?.let { PanelFocus.Tabs })
            null
        }
        is GamePanelEvent.SetPlayer -> MenuEffect.SetPlayer(event.slot, event.key)
        is GamePanelEvent.SetCheat -> setCheat(event.index, event.on)
        GamePanelEvent.ShowGame -> null
        // The tiles are hidden in netplay; a stale tap there does nothing.
        is GamePanelEvent.SetFastForward -> if (netplay) null else GamePanelModel.speedEffect(event)
        is GamePanelEvent.SetRewind -> if (netplay || speedRewind == null) null else GamePanelModel.speedEffect(event)
        is GamePanelEvent.SetMic -> if (netplay || micTile == null) null else MenuEffect.SetMic(event.on)
        GamePanelEvent.Reset -> { if (!netplay) { push(resetPage()); resetClosesOnCancel = false }; null }
    }

    /**
     * Whether the root draws the game panel beside its list: not in netplay ([netplay], also once it ended), where the
     * menu is the game header over Continue / Leave (or Quit) and an empty right side.
     */
    val panelBeside: Boolean get() = !netplay

    /**
     * The root's right side is the panel: the root on top with a row other than Save / Load (their slot card) or Options
     * (its [MenuState.preview]) highlighted.
     */
    private fun panelShown(): Boolean =
        panelBeside && state.stack.size == 1 && state.page.rows.getOrNull(state.page.selected) !is MenuRow.Slot && state.preview == null

    /**
     * Achievements with a set; Cheats for a core that takes them (none for the game: the tab says how to add some); Players
     * with more than one slot. Never Cheats or Players in netplay (the root had no Options or Players row there).
     */
    private fun panelTabs(): List<GamePanelTab> = GamePanelModel.tabs(
        raAvailable = achievementsAvailable && !achievements.isNullOrEmpty(), playerSlots = if (netplay) 0 else playerSlots.size,
        cheats = cheatsAvailable && !netplay,
    )

    private fun panelShortcuts(): List<ShortcutState> =
        GamePanelModel.shortcuts(discCount, discIndex, secondConnected, secondOn, netplay, speedFf, speedRewind, micTile)

    private fun panelAchievements(): List<RaAchievement> = achievements.orEmpty().sortedBy(achievementOrder)

    private fun panelCheats(): List<CheatRow> = GamePanelModel.cheatRows(cheats)

    private fun panelTab(): GamePanelTab = GamePanelModel.resolveTab(state.panelTab, panelTabs())

    private fun panelItemCount(tab: GamePanelTab): Int = when (tab) {
        GamePanelTab.SHORTCUTS -> panelShortcuts().size
        GamePanelTab.ACHIEVEMENTS -> panelAchievements().size
        GamePanelTab.CHEATS -> cheats.size
        GamePanelTab.PLAYERS -> playerSlots.size
    }

    /**
     * Input while the focus is in the panel: L1/R1 tabs, UP/DOWN between the tab row and the items (a row of the shortcut
     * grid at a time), LEFT/RIGHT along a grid row, LEFT at the left edge or on the tabs and B back to the list, A the
     * item (on the tab row: Achievements and Players open their full page).
     */
    private fun pressPanel(button: NxButton): MenuEffect? {
        val tab = panelTab()
        val count = panelItemCount(tab)
        val cols = if (tab == GamePanelTab.SHORTCUTS) panelColumns.coerceAtLeast(1) else 1
        val at = (state.panelFocus as? PanelFocus.Item)?.index
        val focus: PanelFocus? = when (button) {
            NxButton.L1, NxButton.R1 -> { stepTab(if (button == NxButton.L1) -1 else 1); return null }
            NxButton.MENU -> return MenuEffect.Close
            NxButton.A -> return if (at == null) { openTabPage(tab); null } else panelItemEvent(tab, at)?.let(::panelEvent)
            NxButton.B -> null
            NxButton.UP -> if (at == null || at - cols < 0) PanelFocus.Tabs else PanelFocus.Item(at - cols)
            NxButton.DOWN -> when {
                at == null -> if (count > 0) PanelFocus.Item(0) else PanelFocus.Tabs
                at + cols < count -> PanelFocus.Item(at + cols)
                at / cols < (count - 1) / cols -> PanelFocus.Item(count - 1)   // the short last row's last pill
                else -> PanelFocus.Item(at)
            }
            NxButton.LEFT -> if (at == null || at % cols == 0) null else PanelFocus.Item(at - 1)
            NxButton.RIGHT -> if (at != null && at % cols < cols - 1 && at + 1 < count) PanelFocus.Item(at + 1) else state.panelFocus
            else -> state.panelFocus
        }
        state = state.copy(panelFocus = focus)
        return null
    }

    /** L1/R1: the previous/next shown tab, wrapping; focus on an item lands on the new tab's first (its tab row if empty). */
    private fun stepTab(delta: Int) {
        val tabs = panelTabs()
        if (tabs.size < 2) return
        val next = tabs.stepFrom(panelTab(), delta)
        val focus = when (state.panelFocus) {
            is PanelFocus.Item -> if (panelItemCount(next) > 0) PanelFocus.Item(0) else PanelFocus.Tabs
            else -> state.panelFocus
        }
        state = state.copy(panelTab = next, panelFocus = focus)
    }

    /** A on the tab row: the Achievements and Players tabs open their full page (Locked only, Mute; the footer). */
    private fun openTabPage(tab: GamePanelTab) {
        when (tab) {
            GamePanelTab.ACHIEVEMENTS -> push(achievementsPage())
            GamePanelTab.PLAYERS -> push(playersPage())
            GamePanelTab.SHORTCUTS, GamePanelTab.CHEATS -> Unit
        }
    }

    /** The shown tab's item that asks for [event] ([panelItemEvent] backwards); null for a tab, the Game pill, or none. */
    private fun panelItemIndex(event: GamePanelEvent): Int? = when (panelTab()) {
        GamePanelTab.SHORTCUTS -> when (event) {
            GamePanelEvent.SwapDisc -> Shortcut.SWAP_DISC
            is GamePanelEvent.SetSecondScreen -> Shortcut.SECOND_SCREEN
            is GamePanelEvent.SetFastForward -> Shortcut.FAST_FORWARD
            is GamePanelEvent.SetRewind -> Shortcut.REWIND
            is GamePanelEvent.SetMic -> Shortcut.MIC
            GamePanelEvent.Reset -> Shortcut.RESET
            else -> null
        }?.let { s -> panelShortcuts().indexOfFirst { it.shortcut == s } }
        GamePanelTab.ACHIEVEMENTS -> null   // its rows ask for nothing
        GamePanelTab.CHEATS -> (event as? GamePanelEvent.SetCheat)?.index
        GamePanelTab.PLAYERS -> (event as? GamePanelEvent.SetPlayer)?.slot
    }?.takeIf { it >= 0 }

    /** What A on item [index] of [tab] asks for, as a tap there would ([GamePanel]); null for a disabled shortcut or an achievement. */
    private fun panelItemEvent(tab: GamePanelTab, index: Int): GamePanelEvent? = when (tab) {
        GamePanelTab.SHORTCUTS -> panelShortcuts().getOrNull(index)?.let(GamePanelModel::eventFor)
        GamePanelTab.ACHIEVEMENTS -> null
        GamePanelTab.CHEATS -> GamePanelModel.cheatEvent(index, panelCheats())
        GamePanelTab.PLAYERS -> if (index < playerSlots.size) GamePanelModel.nextPlayer(index, playerSlots, playerPads) else null
    }

    /**
     * After the panel's data changed: [MenuState.panelVersion] moves on, a tab no longer shown goes back to Shortcuts, an
     * item focus stays in range.
     */
    private fun fixPanel() {
        state = state.copy(panelVersion = state.panelVersion + 1)
        val tab = panelTab()
        val focus = when (val f = state.panelFocus) {
            is PanelFocus.Item -> panelItemCount(tab).let { n -> if (n == 0) PanelFocus.Tabs else PanelFocus.Item(f.index.coerceAtMost(n - 1)) }
            else -> f
        }
        if (tab != state.panelTab || focus != state.panelFocus) state = state.copy(panelTab = tab, panelFocus = focus)
    }

    private fun pressAdjust(button: NxButton): MenuEffect? = when (button) {
        NxButton.UP -> { stepVisual(-1); null }
        NxButton.DOWN -> { stepVisual(1); null }
        NxButton.LEFT -> change(-1)
        NxButton.RIGHT -> change(1)
        NxButton.B -> { pop(); null }
        NxButton.MENU -> MenuEffect.Close
        else -> null
    }

    private fun actionEffect(action: MenuAction): MenuEffect? = when (action) {
        MenuAction.CONTINUE -> MenuEffect.Close
        MenuAction.QUIT -> { push(quitPage()); null }
        MenuAction.SAVE_AND_QUIT -> MenuEffect.Quit
        MenuAction.QUIT_ONLY -> MenuEffect.QuitWithoutResume
        MenuAction.RESET -> { resetClosesOnCancel = false; popTo(PageKind.Root); MenuEffect.ResetGame }
        MenuAction.CANCEL -> cancelReset()
        MenuAction.LEAVE_NETPLAY -> MenuEffect.LeaveNetplay
        MenuAction.SAVE_FOR_CONSOLE -> { popTo(PageKind.Options); MenuEffect.SaveForConsole }
        MenuAction.SAVE_FOR_GAME -> { popTo(PageKind.Options); MenuEffect.SaveForGame }
        // The overrides go with the file (spec: Restore defaults drops them); the host sets the restored layer's after.
        MenuAction.RESTORE_DEFAULTS -> { popTo(PageKind.Options); shortcutOverrides = ShortcutOverrides.NONE; MenuEffect.RestoreDefaults }
        MenuAction.RESET_SHADER_PARAMS -> {
            val entries = ShaderOptions.resetEntries(params)
            shaders = shaders + entries
            refreshFrontendPages()
            push(message("Shaders", ShaderOptions.RESET_DONE))
            MenuEffect.SetShaders(entries)
        }
    }

    private fun openSubmenu(target: PageKind): MenuEffect? {
        when (target) {
            PageKind.Options -> push(optionsPage())
            PageKind.ConsoleSettings -> push(consoleSettingsPage())
            PageKind.Shortcuts -> push(shortcutsPage())
            PageKind.Frontend -> push(frontendPage())
            PageKind.Shaders -> push(shadersPage())
            PageKind.ShaderParams -> push(shaderParamsPage())
            PageKind.CoreOptions -> {
                if (coreOptionsPending) return null
                coreOptionsPending = true
                return MenuEffect.OpenCoreOptions
            }
            PageKind.SaveChanges -> push(saveChangesPage())
            is PageKind.Category -> push(categoryPage(target.key))
            // Achievements and Players open from the game panel only ([openTabPage]).
            PageKind.Achievements, PageKind.Players, is PageKind.Adjust, PageKind.Root, PageKind.Message,
            PageKind.QuitGame, PageKind.ResetGame -> Unit
        }
        return null
    }

    private fun back(): MenuEffect? {
        if (state.stack.size == 1) return MenuEffect.Close
        if (state.page.kind == PageKind.ResetGame) return cancelReset()
        pop()
        return null
    }

    private fun change(delta: Int): MenuEffect? = when (val row = state.page.rows.getOrNull(state.page.selected)) {
        is MenuRow.Slot -> {
            state = state.copy(slotIndex = ((state.slotIndex + delta) % slotCount + slotCount) % slotCount)
            null
        }
        is MenuRow.Option -> when {
            row.key.startsWith(PLAYER_KEY_PREFIX) -> changePlayer(row.key.removePrefix(PLAYER_KEY_PREFIX).toInt(), delta)
            row.key == DISC_KEY -> changeDisc(delta)
            state.page.kind == PageKind.Shortcuts -> touchSlot(row.key)?.let { slot ->
                val choices = touchChoices()
                setTouchOverride(slot, choices[(row.index.coerceAtLeast(0) + delta).mod(choices.size)])
            }
            isSettingsPage(state.page.kind) -> changeFrontend(row.key, delta)
            else -> {
                val option = options.firstOrNull { it.key == row.key }
                val next = option?.let { CoreOptionFilter.cycle(it, delta) }
                if (option == null || next == null) null else setCoreOption(option, next)
            }
        }
        else -> null
    }

    private fun setCoreOption(option: CoreOption, value: String): MenuEffect {
        updateOptions(options.map { if (it.key == option.key) it.copy(value = value) else it })
        return MenuEffect.SetOption(option.key, value)
    }

    /** Pages whose Option rows are console, frontend or shader settings (not core options). */
    private fun isSettingsPage(kind: PageKind) =
        kind == PageKind.ConsoleSettings || kind == PageKind.Frontend || kind == PageKind.Shaders || kind == PageKind.ShaderParams || kind is PageKind.Adjust

    private fun changeFrontend(key: String, delta: Int): MenuEffect? {
        if (ShaderOptions.isShaderKey(key)) {
            val current = shaderValue(key) ?: return null
            return ShaderOptions.cycle(key, current, delta, presets, params)?.let { setFrontendValue(key, it) }
        }
        if (key == FrontendOptions.OVERLAY && !overlayAvailable) return null
        if (softwareLocked(key)) return null
        // An unset DS layout row (no stored value) cycles from its shown default, like frontendRow shows it.
        val option = optionFor(key)
        val current = ConsoleSettings.fitted(option ?: return null, frontend[key] ?: option.default).second
        val values = settingValues(key) ?: return null
        return CoreOptionFilter.cycle(CoreOption(key, current, values = values), delta)?.let { setFrontendValue(key, it) }
    }

    /** A shader key's value: stored, else (an unstored parameter) its default, which it cycles from too. */
    private fun shaderValue(key: String): String? = shaders[key]
        ?: params.firstOrNull { ShaderOptions.paramKey(it.id) == key }?.let { ShaderOptions.format2(it.default) }

    /** Sets a frontend or shader setting to [value] now; null when it already is. */
    private fun setFrontendValue(key: String, value: String): MenuEffect? {
        if (ShaderOptions.isShaderKey(key)) {
            if (value == shaderValue(key)) return null
            shaders = shaders + (key to value)
            refreshFrontendPages()
            return MenuEffect.SetShader(key, value)
        }
        if (value == frontend[key]) return null
        frontend = frontend + (key to value)
        refreshFrontendPages()
        return MenuEffect.SetFrontend(key, value)
    }

    /** The raw values a frontend or shader row offers, in [MenuRow.Option.choices]' order; null for an unknown key. */
    private fun settingValues(key: String): List<String>? = when {
        ShaderOptions.isShaderKey(key) -> ShaderOptions.choices(key, presets, params)
        key == FrontendOptions.OVERLAY && !overlayAvailable -> emptyList()
        key == FrontendOptions.OVERLAY -> FrontendOptions.choices(key, overlays)
        else -> optionFor(key)?.values
    }

    /** The row for [key]: this console's on this [screen] (the DS / 3DS layout rows differ per screen), else the table's. */
    private fun optionFor(key: String): FrontendOption? = consoleRows().firstOrNull { it.key == key } ?: FrontendOptions.option(key)

    /** The Players page's values for a slot: Empty, then every connected pad ([GamePanelModel.cyclePlayer]'s order). */
    private fun playerCandidates(): List<String?> = listOf<String?>(null) + playerPads.map { it.key }

    /** The panel's cheat switch: cheat [index] [on] or off now (the host applies the enabled set); null when it already is. */
    private fun setCheat(index: Int, on: Boolean): MenuEffect? {
        val entry = cheats.getOrNull(index) ?: return null
        if (entry.enabled == on) return null
        cheats = cheats.mapIndexed { i, e -> if (i == index) e.copy(enabled = on) else e }
        fixPanel()
        return MenuEffect.SetCheat(index, on)
    }

    private fun refreshAchievementsPage() {
        state = state.copy(stack = state.stack.map { p -> if (p.kind == PageKind.Achievements) rebuilt(p, achievementsPage()) else p })
    }

    /** The achievements in the chosen order, locked ones only while [achievementsLockedOnly]. */
    private fun visibleAchievements(): List<RaAchievement> {
        val sorted = achievements.orEmpty().sortedBy(achievementOrder)
        return if (achievementsLockedOnly) sorted.filter { it.state == RaAchievementState.LOCKED } else sorted
    }

    private fun achievementsPage(): MenuPage {
        val rows = visibleAchievements().map { a ->
            val value = when (a.state) {
                RaAchievementState.UNLOCKED -> "Unlocked"
                RaAchievementState.PENDING -> "Pending sync"
                RaAchievementState.LOCKED -> "Locked · ${a.points} pts"
            }
            MenuRow.Option((if (a.id in muted) "[M] " else "") + a.title, "$ACH_KEY_PREFIX${a.id}", value, a.description)
        }
        return MenuPage(PageKind.Achievements, ACHIEVEMENTS_LABEL, rows, footer = if (rows.isEmpty()) NO_LOCKED else "")
    }

    /** The achievement an Achievements page row stands for (the page draws it as an [AchievementRow]); null for any other row. */
    fun achievementOf(row: MenuRow): RaAchievement? = (row as? MenuRow.Option)?.let { achievementFor(it.key) }

    private fun achievementFor(key: String): RaAchievement? {
        if (!key.startsWith(ACH_KEY_PREFIX)) return null
        val id = key.removePrefix(ACH_KEY_PREFIX).toIntOrNull() ?: return null
        return achievements?.firstOrNull { it.id == id }
    }

    /** The selected row's achievement on the Achievements page. */
    private fun selectedAchievementId(): Int? =
        if (state.page.kind != PageKind.Achievements) null
        else (state.page.rows.getOrNull(state.page.selected) as? MenuRow.Option)?.let { achievementFor(it.key)?.id }

    /** Flips the mute locally so the `[M]` marker updates now; the host persists it via the effect. */
    private fun toggleMute(id: Int): MenuEffect {
        muted = if (id in muted) muted - id else muted + id
        refreshAchievementsPage()
        return MenuEffect.ToggleMute(id)
    }

    /**
     * Adjust mode UP/DOWN: the previous/next visual row of the page the strip came from (Console Settings, Frontend,
     * Shaders or Shader Parameters), wrapping.
     */
    private fun stepVisual(delta: Int) {
        val key = (state.page.kind as? PageKind.Adjust)?.key ?: return
        val ownerIndex = state.stack.indexOfLast { it.kind !is PageKind.Adjust }
        if (ownerIndex < 0) return
        val owner = state.stack[ownerIndex]
        val visual = owner.rows.indices.filter { (owner.rows[it] as? MenuRow.Option)?.visual == true }
        if (visual.isEmpty()) return
        val at = visual.indexOfFirst { (owner.rows[it] as MenuRow.Option).key == key }.coerceAtLeast(0)
        val target = visual[((at + delta) % visual.size + visual.size) % visual.size]
        val targetKey = (owner.rows[target] as MenuRow.Option).key
        state = state.copy(stack = state.stack.take(ownerIndex) + owner.copy(selected = target) + adjustPage(targetKey))
    }

    /**
     * Rebuilds open Console Settings/Frontend/Shaders/ShaderParams/Adjust pages from the current values. A Console Settings
     * page left with no rows (its Bottom screen row went with the second display, the others locked) closes, with any page
     * above it: back to Options, which no longer offers it.
     */
    private fun refreshFrontendPages() {
        val stack = state.stack.mapNotNull { p ->
            when (val k = p.kind) {
                PageKind.ConsoleSettings -> rebuilt(p, consoleSettingsPage())
                PageKind.Frontend -> rebuilt(p, frontendPage())
                PageKind.Shaders -> rebuilt(p, shadersPage())
                PageKind.ShaderParams -> rebuilt(p, shaderParamsPage())
                // A parameter the new preset no longer has: close its strip rather than show a stale row.
                is PageKind.Adjust -> if (isVanishedParam(k.key)) null else adjustPage(k.key)
                else -> p
            }
        }
        val empty = stack.indexOfFirst { it.kind == PageKind.ConsoleSettings && it.rows.isEmpty() }
        state = state.copy(stack = if (empty > 0) stack.take(empty) else stack)
    }

    private fun isVanishedParam(key: String) =
        ShaderOptions.paramId(key) != null && params.none { ShaderOptions.paramKey(it.id) == key }

    private fun move(delta: Int) {
        val p = state.page
        if (p.rows.isEmpty()) return
        replaceTop(p.copy(selected = ((p.selected + delta) % p.rows.size + p.rows.size) % p.rows.size))
    }

    // A push or pop leaves the panel: back on the root, the focus is on the list.
    // A push or pop also closes a popup: it belonged to the page it was over.
    // A push or pop also ends a Shortcuts wait: its row is no longer on top.
    private fun push(page: MenuPage) { endWait(); state = state.copy(stack = state.stack + page, panelFocus = null, popup = null) }
    private fun pop() { endWait(); if (state.stack.size > 1) state = state.copy(stack = state.stack.dropLast(1), panelFocus = null, popup = null) }
    private fun popTo(kind: PageKind) {
        endWait()
        val i = state.stack.indexOfLast { it.kind == kind }
        if (i >= 0) state = state.copy(stack = state.stack.take(i + 1), panelFocus = null, popup = null)
    }
    private fun replaceTop(page: MenuPage) { state = state.copy(stack = state.stack.dropLast(1) + page) }
    /** [new] with [old]'s title, keeping the selected row by identity (option key / submenu target) when it survives. */
    private fun rebuilt(old: MenuPage, new: MenuPage): MenuPage {
        val oldRow = old.rows.getOrNull(old.selected)
        val same = new.rows.indexOfFirst { sameRow(it, oldRow) }
        val selected = if (same >= 0) same else old.selected.coerceIn(0, (new.rows.size - 1).coerceAtLeast(0))
        val footer = if (new.rows.isNotEmpty()) "" else when (new.kind) {
            PageKind.CoreOptions -> NO_LIVE_OPTIONS
            PageKind.ShaderParams -> ShaderOptions.NO_PARAMS
            PageKind.Achievements -> NO_LOCKED
            else -> NO_CATEGORY_OPTIONS
        }
        return new.copy(title = old.title, selected = selected, footer = footer)
    }

    private fun sameRow(a: MenuRow, b: MenuRow?): Boolean = when {
        a is MenuRow.Option && b is MenuRow.Option -> a.key == b.key
        a is MenuRow.Submenu && b is MenuRow.Submenu -> a.target == b.target
        else -> false
    }

    private fun rootPage() = if (netplay && netplayEnded) MenuPage(
        PageKind.Root, gameTitle,
        // No save states after netplay, so no resume point to choose about: Quit quits.
        listOf(MenuRow.Action("Continue", MenuAction.CONTINUE), MenuRow.Action("Quit", MenuAction.QUIT_ONLY)),
    ) else if (netplay) MenuPage(
        PageKind.Root, NetplayText.LEAVE_TITLE,
        listOf(MenuRow.Action(NetplayText.CONTINUE, MenuAction.CONTINUE), MenuRow.Action(NetplayText.LEAVE, MenuAction.LEAVE_NETPLAY)),
        footer = if (netplayLink) NetplayText.LEAVE_LINK_TEXT else NetplayText.LEAVE_TEXT,
    ) else MenuPage(
        PageKind.Root, gameTitle,
        buildList {
            add(MenuRow.Action("Continue", MenuAction.CONTINUE))
            if (discCount > 1) add(MenuRow.Option(DISC_LABEL, DISC_KEY, "${discIndex + 1}/$discCount", choices = (1..discCount).map { "Disc $it" }, index = discIndex, detail = DISC_INFO))
            add(MenuRow.Slot("Save", SlotAction.SAVE))
            add(MenuRow.Slot("Load", SlotAction.LOAD))
            add(MenuRow.Submenu("Options", PageKind.Options))
            add(MenuRow.Action("Quit", MenuAction.QUIT))
        },
    )

    private fun optionsPage() = MenuPage(
        PageKind.Options, "Options",
        buildList {
            // Only Frontend's name needs a line; the footer's status covers Save Changes.
            if (consoleRows().isNotEmpty()) add(MenuRow.Submenu(CONSOLE_SETTINGS_LABEL, PageKind.ConsoleSettings))
            add(MenuRow.Submenu(SHORTCUTS_LABEL, PageKind.Shortcuts))
            add(MenuRow.Submenu("Frontend", PageKind.Frontend, detail = FRONTEND_DETAIL))   // compact: its text behind the info icon
            add(MenuRow.Submenu("Shaders", PageKind.Shaders))
            add(MenuRow.Submenu("Core Options", PageKind.CoreOptions))
            add(MenuRow.Submenu("Save Changes", PageKind.SaveChanges))
        },
        footer = statusText(),
    )

    /**
     * The console's own rows ([com.nxredux.mobile.options.ConsoleSettings]: the DS screens, the 3DS and Wii controls, the
     * graphics API, the pad), locked ones hidden; values live with the frontend values.
     */
    private fun consoleSettingsPage() = MenuPage(
        PageKind.ConsoleSettings, CONSOLE_SETTINGS_LABEL,
        consoleRows().map(::frontendRow),
    )

    /**
     * This console's Console Settings rows, locked ones and the ones read only at launch ([FrontendOption.atLaunch]: set in
     * Game Settings / Emulator Settings) left out, and Bottom screen while no second display is [secondConnected]; none
     * left = the page is not offered.
     */
    private fun consoleRows() = ConsoleSettings.tableFor(frontendTag, screen, displayMax3d).filter {
        it.key !in frontendLocked && !it.atLaunch && (secondConnected || it.key != BottomScreenOptions.KEY)
    }.shownFor(frontend).map { ConsoleSettings.fitted(it, frontend[it.key] ?: it.default).first }

    private fun frontendPage() = MenuPage(
        PageKind.Frontend, "Frontend",
        FrontendOptions.table.filter { it.key !in frontendLocked }.map(::frontendRow),
    )

    private fun shadersPage() = MenuPage(
        PageKind.Shaders, "Shaders",
        buildList {
            add(presetRow())
            // Parameters belong to a shader: with the preset None there is nothing to tune or reset.
            if ((shaders[ShaderOptions.PRESET] ?: ShaderOptions.NONE) != ShaderOptions.NONE) {
                add(MenuRow.Submenu(ShaderOptions.PARAMS_LABEL, PageKind.ShaderParams))
                add(MenuRow.Action(ShaderOptions.RESET_LABEL, MenuAction.RESET_SHADER_PARAMS))
            }
        },
    )

    private fun shaderParamsPage(): MenuPage {
        val rows = params.map(::paramRow)
        return MenuPage(PageKind.ShaderParams, ShaderOptions.PARAMS_LABEL, rows, footer = if (rows.isEmpty()) ShaderOptions.NO_PARAMS else "")
    }

    private fun presetRow(): MenuRow.Option {
        val value = shaders[ShaderOptions.PRESET] ?: ShaderOptions.NONE
        val choices = ShaderOptions.presetChoices(presets)
        return MenuRow.Option(
            ShaderOptions.PRESET_LABEL, ShaderOptions.PRESET, value, visual = true,
            choices = choices, index = choices.indexOf(value), detail = ShaderOptions.PRESET_INFO,
        )
    }

    private fun paramRow(p: ShaderParam): MenuRow.Option {
        val value = shaders[ShaderOptions.paramKey(p.id)] ?: ShaderOptions.format2(p.default)
        val choices = ShaderOptions.paramChoices(p)
        return MenuRow.Option(p.label, ShaderOptions.paramKey(p.id), value, visual = true, choices = choices, index = choices.indexOf(value))   // compact: no line
    }

    /** The one row an Adjust page shows for [key]: a frontend setting, the preset, or a parameter. */
    private fun adjustPage(key: String): MenuPage {
        val row = when {
            key == ShaderOptions.PRESET -> presetRow()
            ShaderOptions.paramId(key) != null ->
                paramRow(checkNotNull(params.firstOrNull { ShaderOptions.paramKey(it.id) == key }) { "not an active parameter: $key" })
            else -> frontendRow(checkNotNull(optionFor(key)) { "not a frontend key: $key" })
        }
        return MenuPage(PageKind.Adjust(key), row.label, listOf(row))
    }

    private fun frontendRow(o: FrontendOption): MenuRow.Option =
        if (o.key == FrontendOptions.OVERLAY && !overlayAvailable) {
            MenuRow.Option(o.label, o.key, OVERLAY_UNAVAILABLE, OVERLAY_UNAVAILABLE_LINE, detail = OVERLAY_UNAVAILABLE_INFO)
        } else if (softwareLocked(o.key)) {
            val value = ConsoleSettings.fitted(o, frontend[o.key] ?: o.default).second
            MenuRow.Option(o.label, o.key, o.labelOf(value), NdsOptions.SOFTWARE_NOTE)
        } else {
            val value = ConsoleSettings.fitted(o, frontend[o.key] ?: o.default).second
            val values = if (o.key == FrontendOptions.OVERLAY) FrontendOptions.choices(o.key, overlays) else o.values
            // The row's brief line (none: compact); the full text in its info popup.
            val now = nds3dNow
            val shown = if (o.key == NdsOptions.MODE_3D && now != null && value != Nds3dMode.PERFORMANCE.value) "${o.labelOf(value)} · ${now}x now" else o.labelOf(value)
            MenuRow.Option(
                o.label, o.key, shown, o.brief, visual = o.visual,
                choices = values.map(o::labelOf), index = values.indexOf(value), detail = o.info,
            )
        }

    // ------------------------------------------------------------------ Options → Shortcuts

    /**
     * Controls → Shortcuts ([base]), this console's or game's [overrides] over it (the resolved config's `nx_bind_*`, or
     * what this page set since launch), the console's hotkeys ([claimed]: [ConsoleHotkeys.claimed]) and whether the core
     * can rewind (no rewind rows without). Refreshes an open Shortcuts page.
     */
    fun setShortcuts(base: ShortcutSet, overrides: ShortcutOverrides, claimed: List<ClaimedInput>, canRewind: Boolean, canMic: Boolean = false) {
        shortcutBase = base
        shortcutOverrides = overrides
        shortcutRewind = canRewind
        shortcutMic = canMic
        // A new capture only for new hotkeys: the old one's held keys must keep their releases swallowed.
        if (claimed != shortcutClaimed) { shortcutClaimed = claimed; capture.cancel(); capture = ShortcutCapture(claimed); capturing = null }
        refreshShortcutsPage()
    }

    /** Whether a controller is [connected]; one going away ends a wait. Refreshes an open Shortcuts page. */
    fun setControllerConnected(connected: Boolean) {
        if (connected == controllerConnected) return
        controllerConnected = connected
        if (!connected) endWait()
        refreshShortcutsPage()
    }

    /**
     * Whether the host should give this raw key's press ([down]) or release to [captureKey] instead of the menu: every
     * key while a row waits, and the release of one pressed since ([ShortcutCapture.takes]), so MENU's release after
     * MENU+R1 or B's after a cancel never reach the menu. A key repeat it takes the host drops.
     */
    fun takesKey(keyCode: Int, down: Boolean): Boolean = capture.takes(keyCode, down)

    /**
     * A raw key the host gave press to bind ([takesKey]): [MenuEffect.ShortcutsChanged] when it bound the waiting row. A
     * button or combo the console's hotkeys use (or the SELECT+START menu chord) is refused, saying which hotkey has it
     * (spec: built-in hotkeys win); B or Back alone cancels.
     */
    fun captureKey(keyCode: Int, down: Boolean): MenuEffect? {
        val result = capture.onKey(keyCode, down) ?: return null
        val action = capturing ?: return null
        capturing = null
        return when (result) {
            is CaptureResult.Bound -> setBindOverride(shortcutOverrides.withBinding(action, result.binding))
            is CaptureResult.Refused -> { refreshShortcutsPage(); push(message(action.label, result.reason)); null }
            CaptureResult.Cancelled -> { refreshShortcutsPage(); null }
        }
    }

    private fun startCapture(action: ShortcutAction) {
        capture.start()
        capturing = action
        refreshShortcutsPage()
    }

    private fun cancelCapture() {
        endWait()
        refreshShortcutsPage()
    }

    /** Ends a wait without a binding; the keys already down keep their releases swallowed ([takesKey]). */
    private fun endWait() {
        if (capturing == null) return
        capture.cancel()
        capturing = null
    }

    /** X: row [index]'s action or touch button set to None here. */
    private fun clearShortcut(index: Int): MenuEffect? {
        val key = (state.page.rows.getOrNull(index) as? MenuRow.Option)?.key ?: return null
        bindAction(key)?.let { a ->
            if (a in shortcutOverrides.bindings && shortcutOverrides.bindings[a] == null) return null
            return setBindOverride(shortcutOverrides.withBinding(a, null))
        }
        return touchSlot(key)?.let { setTouchOverride(it, null) }
    }

    /** Y: row [index] follows Controls → Shortcuts again (its override dropped); null when it already does. */
    private fun defaultShortcut(index: Int): MenuEffect? {
        val key = (state.page.rows.getOrNull(index) as? MenuRow.Option)?.key ?: return null
        val next = bindAction(key)?.takeIf { it in shortcutOverrides.bindings }?.let(shortcutOverrides::inheriting)
            ?: touchSlot(key)?.takeIf { it in shortcutOverrides.touch }?.let(shortcutOverrides::inheriting)
            ?: return null
        return setBindOverride(next)
    }

    private fun setTouchOverride(slot: TouchSlot, action: ShortcutAction?): MenuEffect? {
        if (slot in shortcutOverrides.touch && shortcutOverrides.touch[slot] == action) return null
        return setBindOverride(shortcutOverrides.withTouch(slot, action))
    }

    private fun setBindOverride(next: ShortcutOverrides): MenuEffect {
        shortcutOverrides = next
        refreshShortcutsPage()
        return MenuEffect.ShortcutsChanged(next)
    }

    private fun bindAction(key: String): ShortcutAction? =
        if (key.startsWith(BIND_KEY_PREFIX)) ShortcutAction.fromKey(key.removePrefix(BIND_KEY_PREFIX)) else null

    private fun touchSlot(key: String): TouchSlot? =
        if (key.startsWith(TOUCH_KEY_PREFIX)) TouchSlot.entries.firstOrNull { it.key == key.removePrefix(TOUCH_KEY_PREFIX) } else null

    /** What a touch button can do here: None, then every action the core has (no rewind ones without rewind) a touch button can do. */
    private fun touchChoices(): List<ShortcutAction?> = listOf<ShortcutAction?>(null) + ShortcutAction.entries.filter { (shortcutRewind || !it.rewind) && it.touch }

    private fun refreshShortcutsPage() {
        state = state.copy(stack = state.stack.map { p -> if (p.kind == PageKind.Shortcuts) rebuilt(p, shortcutsPage()).copy(footer = SHORTCUTS_FOOTER) else p })
    }

    /**
     * One row per action (no rewind rows without rewind), then the two touch buttons: each shows what fires here, marked
     * "(default)" while it inherits Controls → Shortcuts; an action on a button the console's hotkeys use shows
     * Unavailable with the hotkey as its line (spec: built-in hotkeys win). The row waiting for a press says so. With no
     * controller the bind rows give way to [SHORTCUTS_NO_CONTROLLER] as the page's intro; their overrides stay.
     */
    private fun shortcutsPage(): MenuPage {
        val effective = Shortcuts.effective(shortcutBase, shortcutOverrides, shortcutClaimed, shortcutRewind, shortcutMic)
        fun shown(value: String, inherited: Boolean) = if (inherited) "$value$DEFAULT_SUFFIX" else value
        val binds = if (!controllerConnected) emptyList()
        else ShortcutAction.entries.filter { (shortcutRewind || !it.rewind) && (shortcutMic || !it.mic) }.map { a ->
            val key = BIND_KEY_PREFIX + a.key
            val blocked = effective.blocked[a]
            when {
                a == capturing -> MenuRow.Option(a.label, key, SHORTCUT_PROMPT, CAPTURE_LINE)
                blocked != null -> MenuRow.Option(a.label, key, UNAVAILABLE, blocked.note)
                else -> MenuRow.Option(a.label, key, shown(effective.bindings[a]?.let(KeyLabels::label) ?: NONE_LABEL, a !in shortcutOverrides.bindings))
            }
        }
        val choices = touchChoices()
        val touch = TouchSlot.entries.mapIndexed { i, t ->
            val action = effective.touch[t]
            MenuRow.Option(
                "Touch button ${i + 1}", TOUCH_KEY_PREFIX + t.key, shown(action?.label ?: NONE_LABEL, t !in shortcutOverrides.touch),
                choices = choices.map { it?.label ?: NONE_LABEL }, index = choices.indexOf(action),
            )
        }
        return MenuPage(PageKind.Shortcuts, SHORTCUTS_LABEL, binds + touch, footer = SHORTCUTS_FOOTER, intro = if (controllerConnected) "" else SHORTCUTS_NO_CONTROLLER)
    }

    private fun saveChangesPage() = MenuPage(
        PageKind.SaveChanges, "Save Changes",
        listOf(
            MenuRow.Action("Save for console", MenuAction.SAVE_FOR_CONSOLE),
            MenuRow.Action("Save for game", MenuAction.SAVE_FOR_GAME),
            MenuRow.Action("Restore defaults", MenuAction.RESTORE_DEFAULTS),
        ),
        footer = statusText(),
    )

    private fun quitPage() = MenuPage(
        PageKind.QuitGame, "Quit Game",
        listOf(
            MenuRow.Action("Save & Quit", MenuAction.SAVE_AND_QUIT),
            MenuRow.Action("Quit", MenuAction.QUIT_ONLY),
        ),
        footer = QUIT_TEXT,
    )

    private fun resetPage() = MenuPage(
        PageKind.ResetGame, "Reset game?",
        listOf(MenuRow.Action("Reset", MenuAction.RESET), MenuRow.Action("Cancel", MenuAction.CANCEL)),
        footer = RESET_TEXT,
    )

    private fun coreOptionsPage(): MenuPage {
        val live = CoreOptionFilter.live(options, locked)
        val rows = CoreOptionFilter.categories(live).map { MenuRow.Submenu(it.label, PageKind.Category(it.key), detail = it.info) } +
            CoreOptionFilter.uncategorized(live).map { optionRow(it, inCategory = false) }
        return MenuPage(PageKind.CoreOptions, "Core Options", rows)
    }

    private fun categoryPage(key: String): MenuPage {
        val live = CoreOptionFilter.live(options, locked)
        val title = CoreOptionFilter.categories(live).firstOrNull { it.key == key }?.label ?: key
        return MenuPage(PageKind.Category(key), title, CoreOptionFilter.inCategory(live, key).map { optionRow(it, inCategory = true) })
    }

    private fun optionRow(o: CoreOption, inCategory: Boolean): MenuRow.Option {
        val visible = CoreOptionFilter.visibleValues(o)
        return MenuRow.Option(
            CoreOptionFilter.rowLabel(o, inCategory), o.key, CoreOptionFilter.valueLabel(o), detail = o.info,   // the core's text: info icon only
            choices = visible.map { v -> CoreOptionFilter.valueLabel(o.copy(value = v)) }, index = visible.indexOf(o.value),
        )
    }

    private fun message(title: String, text: String) = MenuPage(PageKind.Message, title, emptyList(), footer = text)

    private fun statusText() = when (status) {
        ConfigSource.NONE -> "Using defaults."
        ConfigSource.CONSOLE -> "Using console config."
        ConfigSource.GAME -> "Using game config."
    }

    companion object {
        const val CONSOLE_SETTINGS_LABEL = "Console Settings"
        const val SHORTCUTS_LABEL = "Shortcuts"
        const val SHORTCUTS_FOOTER = "Changes here apply to this console or game. Save them with Save Changes."
        const val BIND_KEY_PREFIX = "nx:bind:"
        const val TOUCH_KEY_PREFIX = "nx:touch:"
        /** A Shortcuts row inheriting Controls → Shortcuts. */
        private const val DEFAULT_SUFFIX = " (default)"
        private const val NONE_LABEL = "None"
        private const val UNAVAILABLE = "Unavailable"
        /** The waiting bind row's line: B cancels (the hint says so). */
        private const val CAPTURE_LINE = "Hold MENU for a combo"
        const val DISC_KEY = "nx:disc"
        const val DISC_LABEL = "Disc"
        const val DISC_INFO = "Left and right change the disc in the drive,\nas opening the lid and swapping it would."
        const val PLAYERS_LABEL = "Players"
        const val PLAYER_KEY_PREFIX = "nx:player:"
        // Presses while the menu is open never claim a slot.
        const val PLAYERS_FOOTER = "Close the menu, then press a button\non a new controller to join."
        const val FRONTEND_DETAIL = "Screen scaling, overlays and on-screen extras"
        const val ACHIEVEMENTS_LABEL = "Achievements"
        const val NO_LOCKED = "Everything unlocked."
        const val ACH_KEY_PREFIX = "ach:"
        const val OVERLAY_UNAVAILABLE = "Unavailable"
        private const val RESET_TEXT = "Progress since your last save will be lost."
        private const val QUIT_TEXT = "Save & Quit keeps your place for next time. Quit keeps the last resume point."
        const val OVERLAY_UNAVAILABLE_LINE = "Off while the DS screens are drawn apart"
        const val OVERLAY_UNAVAILABLE_INFO = "Overlays are off while the DS screens\nare drawn apart."
        private const val NO_LIVE_OPTIONS = "This core has no options that\ncan be changed while running."
        private const val NO_CATEGORY_OPTIONS = "This category has no options."
    }
}
