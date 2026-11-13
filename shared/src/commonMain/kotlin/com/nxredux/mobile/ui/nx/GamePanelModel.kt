package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.cheats.CheatEntry
import com.nxredux.mobile.gametime.GameTimeFormat
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.TouchSlot

/** The game panel's tabs, in display order; [GamePanelModel.tabs] hides the ones that don't apply. */
enum class GamePanelTab(val label: String) { SHORTCUTS("Shortcuts"), SETTINGS("Settings"), ACHIEVEMENTS("Achievements"), CHEATS("Cheats"), PLAYERS("Players") }

/**
 * The tab's word in the in-game menu's tab row: Shortcuts is the Menu tab there (the menu's words with the shortcuts
 * beside or below them); the dual-screen panel keeps [GamePanelTab.label].
 */
val GamePanelTab.menuLabel: String get() = if (this == GamePanelTab.SHORTCUTS) MENU_TAB_LABEL else label

const val MENU_TAB_LABEL = "Menu"

/**
 * The Shortcuts tab's tiles, in display order: the player's touch buttons ([TOUCH], each its own action), then the
 * panel's own. No Screenshot: the spec dropped it everywhere.
 */
enum class Shortcut(val label: String) {
    TOUCH(""), SWAP_DISC("Swap disc"), FAST_FORWARD("Fast forward"), REWIND("Rewind"), MIC("Mic"), RESET("Reset"),
}

/**
 * One shortcut tile: [enabled] false is drawn dimmed and does nothing; [value] is the display text under the label
 * ("2/3" for discs, "Slot 2" for a save state); [on] makes it a toggle (fast forward, rewind, the mic; null for the
 * action shortcuts), drawn as a quick-settings tile: filled while on, outlined while off, with no On/Off words.
 * [action]: a [Shortcut.TOUCH] tile's, run as its touch button runs it (held while pressed).
 */
data class ShortcutState(
    val shortcut: Shortcut, val label: String, val enabled: Boolean, val value: String? = null, val on: Boolean? = null,
    val action: ShortcutAction? = null,
)

/**
 * The header on every tab: the game's [title], its box art ([artPath]; null draws the [consoleTag]'s logo), its total
 * play time before this launch ([totalMs], read once as the game starts; no running count), the (unlocked, total)
 * RetroAchievements count ([ra] null: no set, no count), and the host's [clockText] and [batteryPercent] (null:
 * unknown), since the game hides the status bar.
 */
data class GamePanelHeader(
    val title: String,
    val artPath: String?,
    val consoleTag: String,
    val totalMs: Long,
    val ra: Pair<Int, Int>?,
    val clockText: String,
    val batteryPercent: Int?,
)

/**
 * One Cheats tab row: the cheat's [name], the file it came from ([source]: blank with a single file) and whether it is
 * [on] (its switch).
 */
data class CheatRow(val name: String, val source: String, val on: Boolean)

/** One row of the Settings tab ([GamePanelState.quick]): what a player changes mid-game, applied as it is touched. */
sealed interface QuickSetting {
    val label: String

    /** The game's volume, [value] 0..1 of the device's range. */
    data class Volume(override val label: String, val value: Float) : QuickSetting

    /**
     * An option with a few [values] ([labels]: what each reads as), the one in use at [index] (-1: a value not among
     * them, shown as it is in [current]). [segments]: every value side by side; else the value between two arrows.
     * [enabled] false: the game's layout holds it (dimmed, not changed).
     */
    data class Choice(
        val key: String, override val label: String, val values: List<String>, val labels: List<String>, val index: Int,
        val current: String, val segments: Boolean, val enabled: Boolean = true,
    ) : QuickSetting
}

/** Where a controller is inside the panel (single-screen in-game menu): the tab row, or the current tab's item [index]. */
sealed interface PanelFocus {
    data object Tabs : PanelFocus
    data class Item(val index: Int) : PanelFocus
}

/**
 * What the game panel shows: the [header], the visible [tabs] and the current [tab], the Shortcuts tab's [shortcuts],
 * this game's [achievements] with the downloaded [badges] (file name → image model), and the Players tab's [players]
 * and [playerPads] (the Players page's types: the slots, and the pads a slot can be set to), the Cheats tab's [cheats]
 * and what it says without any ([noCheatsText]). [focus]: where the controller is (null: not in the panel). [showGamePill]:
 * the DS/3DS panel-up Game pill (bottom-right), which sends [GamePanelEvent.ShowGame].
 */
data class GamePanelState(
    val header: GamePanelHeader,
    val tabs: List<GamePanelTab>,
    val tab: GamePanelTab,
    val shortcuts: List<ShortcutState>,
    val achievements: List<RaAchievement>,
    val badges: Map<String, String>,
    val players: List<PlayerSlotRow>,
    val playerPads: List<PlayerPadRow>,
    val cheats: List<CheatRow> = emptyList(),
    val noCheatsText: String = "",
    val focus: PanelFocus? = null,
    val showGamePill: Boolean = false,
    // the Game pill in the corner buttons' look, as the Panel pill it answers there (a controller attached); else the hollow pill
    val gamePillCorner: Boolean = false,
    /** The Shortcuts tab's save-state slots, in slot order (none: no strip), and the one Save and Load use (1-based). */
    val slots: List<SlotUi> = emptyList(),
    val slot: Int? = null,
    /** The Settings tab's rows. */
    val quick: List<QuickSetting> = emptyList(),
)

/** What a tap (or A) on the panel asks the game for. */
sealed interface GamePanelEvent {
    /** Next disc, as the menu's Disc row cycles. */
    data object SwapDisc : GamePanelEvent
    /** The Settings tab's volume to [value] (0..1). */
    data class SetVolume(val value: Float) : GamePanelEvent
    /** The Settings tab's option [key] to [value], live. */
    data class SetQuick(val key: String, val value: String) : GamePanelEvent
    /** The Settings tab's "All options": the in-game menu on its Options page. */
    data object OpenOptions : GamePanelEvent
    /** Save-state [slot] (1-based) picked on the strip: Save and Load use it. */
    data class SelectSlot(val slot: Int) : GamePanelEvent
    /** A touch button's tile pressed ([down]) or let go, as the button on the pad: a hold runs while down. */
    data class Touch(val action: ShortcutAction, val down: Boolean) : GamePanelEvent
    data class SelectTab(val tab: GamePanelTab) : GamePanelEvent
    /** Put pad [key] in player [slot] (0-based); null empties it. */
    data class SetPlayer(val slot: Int, val key: String?) : GamePanelEvent
    /** Turn cheat [index] (the game's cheat list) [on] or off, live. */
    data class SetCheat(val index: Int, val on: Boolean) : GamePanelEvent
    /** The Game pill: the DS/3DS screen comes back to the second display. */
    data object ShowGame : GamePanelEvent
    /** The toggled fast forward to [on] (the tile shows the live state, so this turns off a toggle a binding left on). */
    data class SetFastForward(val on: Boolean) : GamePanelEvent
    /** The toggled rewind to [on]. */
    data class SetRewind(val on: Boolean) : GamePanelEvent
    /** The session's mic toggled to [on] (the tile shows the live state). */
    data class SetMic(val on: Boolean) : GamePanelEvent
    /** Reset: always asks first (the in-game menu's Reset dialog). */
    data object Reset : GamePanelEvent
}

/** The panel's rules, pure so the in-game menu and both hosts share them. */
object GamePanelModel {
    /**
     * Shortcuts always; Settings on the second-screen panel ([settings]); Achievements only with a RetroAchievements
     * set; Cheats while the core takes them ([cheats], even with none for the game: the tab says how to add some);
     * Players only with more than one player slot.
     */
    fun tabs(raAvailable: Boolean, playerSlots: Int, cheats: Boolean = false, settings: Boolean = false): List<GamePanelTab> = buildList {
        add(GamePanelTab.SHORTCUTS)
        if (settings) add(GamePanelTab.SETTINGS)
        if (raAvailable) add(GamePanelTab.ACHIEVEMENTS)
        if (cheats) add(GamePanelTab.CHEATS)
        if (playerSlots > 1) add(GamePanelTab.PLAYERS)
    }

    /**
     * A Settings tab option: [values] with the one in use ([current]) marked; side by side while there are at most
     * [QUICK_SEGMENTS_MAX] and none has its own name ([labels]) too long to share a row, else between two arrows.
     */
    fun quickChoice(key: String, label: String, values: List<String>, current: String, labels: List<String> = values, enabled: Boolean = true) =
        QuickSetting.Choice(key, label, values, labels, values.indexOf(current), current, segments = values.size in 2..QUICK_SEGMENTS_MAX, enabled = enabled)

    const val QUICK_SEGMENTS_MAX = 4

    /** The value [delta] steps on from [c]'s (wrapping; from a value not among them, the first or the last); null with none to step to. */
    fun quickStep(c: QuickSetting.Choice, delta: Int): String? {
        if (c.values.isEmpty()) return null
        val at = if (c.index >= 0) (c.index + delta).mod(c.values.size) else if (delta > 0) 0 else c.values.lastIndex
        return c.values[at].takeIf { it != c.current }
    }

    /** A shader preset or overlay file as the Settings tab names it: its file's name without the folder or the extension. */
    fun quickFileLabel(value: String): String = value.substringAfterLast('/').substringBeforeLast('.').ifEmpty { value }

    /** The points badge at the end of an achievement's name; none for one worth no points. */
    fun pointsBadge(a: RaAchievement): String? = a.points.takeIf { it > 0 }?.toString()

    /** [current] while it is still shown; Shortcuts once it has gone (RA signed out, a pad unplugged). */
    fun resolveTab(current: GamePanelTab, tabs: List<GamePanelTab>): GamePanelTab =
        if (current in tabs) current else GamePanelTab.SHORTCUTS

    /**
     * The touch buttons the panel shows a tile for, in the buttons' order: each button's action ([touch], the player's),
     * the first two Save state and Load state while unset, the rest left out while unset. An action with a tile of
     * the panel's own (Toggle FF, Toggle Rewind, Reset) is left out, as is one already listed, and a rewind hold where
     * the game can't rewind now ([rewind] false).
     */
    fun panelTouchActions(touch: Map<TouchSlot, ShortcutAction>, rewind: Boolean): List<ShortcutAction> =
        TouchSlot.entries.mapNotNull { touch[it] ?: PANEL_TOUCH_DEFAULTS[it] }.distinct()
            .filter { it !in PANEL_OWN_ACTIONS && (rewind || !it.rewind) }

    private val PANEL_TOUCH_DEFAULTS = mapOf(TouchSlot.ONE to ShortcutAction.SAVE_STATE, TouchSlot.TWO to ShortcutAction.LOAD_STATE)
    private val PANEL_OWN_ACTIONS = setOf(ShortcutAction.TOGGLE_FF, ShortcutAction.TOGGLE_REWIND, ShortcutAction.RESET)

    /** A touch button's tile for [action]: Save and Load say the save-state [slot] they use (null: not read yet). */
    private fun touchTile(action: ShortcutAction, slot: Int?): ShortcutState {
        val label = when (action) {
            ShortcutAction.SAVE_STATE -> "Save"
            ShortcutAction.LOAD_STATE -> "Load"
            ShortcutAction.HOLD_FF -> "Hold fast forward"
            ShortcutAction.HOLD_REWIND -> "Hold rewind"
            else -> action.label
        }
        val state = action == ShortcutAction.SAVE_STATE || action == ShortcutAction.LOAD_STATE
        return ShortcutState(Shortcut.TOUCH, label, true, value = slot?.takeIf { state }?.let { "Slot $it" }, action = action)
    }

    /**
     * The Shortcuts tab: the touch buttons' tiles ([touch], [panelTouchActions]; Save and Load on the save-state [slot]),
     * Swap disc for a multi-disc game outside netplay (disc swaps aren't synced), then Fast forward ([ff]: running now),
     * Rewind ([rewind]: rewinding now; null where the game can't rewind, the core or its Rewind row off: hidden, never
     * greyed), Mic ([mic]: on now; null off DS/3DS: hidden) and Reset. Netplay and link sessions keep only Swap disc's
     * rule: the rest would desync the players. [discIndex] is 0-based.
     */
    fun shortcuts(
        discCount: Int, discIndex: Int, netplay: Boolean, ff: Boolean, rewind: Boolean?, mic: Boolean? = null,
        touch: List<ShortcutAction> = emptyList(), slot: Int? = null,
    ): List<ShortcutState> = buildList {
        if (!netplay) touch.forEach { add(touchTile(it, slot)) }
        if (discCount > 1 && !netplay) add(ShortcutState(Shortcut.SWAP_DISC, Shortcut.SWAP_DISC.label, true, "${discIndex + 1}/$discCount"))
        if (netplay) return@buildList
        add(ShortcutState(Shortcut.FAST_FORWARD, Shortcut.FAST_FORWARD.label, true, on = ff))
        if (rewind != null) add(ShortcutState(Shortcut.REWIND, Shortcut.REWIND.label, true, on = rewind))
        if (mic != null) add(ShortcutState(Shortcut.MIC, Shortcut.MIC.label, true, on = mic))
        add(ShortcutState(Shortcut.RESET, Shortcut.RESET.label, true))
    }

    /**
     * What activating [s] asks for: nothing while disabled; Swap disc, a toggle flipped (from its live [ShortcutState.on]),
     * or Reset. A touch button's tile asks on its press and release instead ([GamePanelEvent.Touch]).
     */
    fun eventFor(s: ShortcutState): GamePanelEvent? = if (!s.enabled) null else when (s.shortcut) {
        Shortcut.TOUCH -> null
        Shortcut.SWAP_DISC -> GamePanelEvent.SwapDisc
        Shortcut.FAST_FORWARD -> s.on?.let { GamePanelEvent.SetFastForward(on = !it) }
        Shortcut.REWIND -> s.on?.let { GamePanelEvent.SetRewind(on = !it) }
        Shortcut.MIC -> s.on?.let { GamePanelEvent.SetMic(on = !it) }
        Shortcut.RESET -> GamePanelEvent.Reset
    }

    /**
     * What a Fast forward or Rewind tile asks of the toggles (null for any other event): the one tapped to its new state,
     * and turning one on turns the other off (last toggle wins, as the Toggle bindings). Both panels share it.
     */
    fun speedEffect(event: GamePanelEvent): MenuEffect.SetSpeed? = when (event) {
        is GamePanelEvent.SetFastForward -> MenuEffect.SetSpeed(ff = event.on, rewind = if (event.on) false else null)
        is GamePanelEvent.SetRewind -> MenuEffect.SetSpeed(ff = if (event.on) false else null, rewind = event.on)
        else -> null
    }

    /**
     * Player [slot]'s value [delta] steps on (wrapping) among Empty and every connected pad, benched ones included (picking
     * one puts it back in play); a held slot's pad, not among them, steps from Empty.
     */
    fun cyclePlayer(slot: Int, delta: Int, players: List<PlayerSlotRow>, pads: List<PlayerPadRow>): String? {
        val candidates = listOf<String?>(null) + pads.map { it.key }
        val at = candidates.indexOf(players.getOrNull(slot)?.key).coerceAtLeast(0)
        return candidates[(at + delta).mod(candidates.size)]
    }

    /** A tap on player [slot]: its next value ([cyclePlayer]), as RIGHT on the Players page steps it. */
    fun nextPlayer(slot: Int, players: List<PlayerSlotRow>, pads: List<PlayerPadRow>): GamePanelEvent.SetPlayer =
        GamePanelEvent.SetPlayer(slot, cyclePlayer(slot, 1, players, pads))

    /** A player slot's value: "Empty", the name, or "<name> (disconnected)"; the panel and the Players page both show it. */
    fun playerValue(s: PlayerSlotRow): String = when {
        s.key == null -> "Empty"
        s.connected -> s.name
        else -> "${s.name} (disconnected)"
    }

    /**
     * The Cheats tab's rows for the game's [entries], in their order (a row's index is the cheat's): the source is what
     * the entry's info adds to its name, "(GameShark) Max Score" -> GameShark, "Built-in: Speed" -> Built-in.
     */
    fun cheatRows(entries: List<CheatEntry>): List<CheatRow> = entries.map { e ->
        val source = if (e.info == e.desc) "" else e.info.removeSuffix(e.desc).trim().removeSuffix(":").removeSurrounding("(", ")")
        CheatRow(e.desc, source, e.enabled)
    }

    /** A tap (or A) on cheat [index]: its switch flipped; null past the list. */
    fun cheatEvent(index: Int, rows: List<CheatRow>): GamePanelEvent.SetCheat? =
        rows.getOrNull(index)?.let { GamePanelEvent.SetCheat(index, on = !it.on) }

    /**
     * The Cheats tab without any: [NO_CHEATS] and how to add some, the Cheats & Patches tool or a RetroArch `.cht` file in
     * the game's [folder] (`Cheats/<TAG>` in the home folder), named as the first file looked for ([expected]) once the
     * cheats have been resolved.
     */
    fun noCheatsText(folder: String, expected: String): String =
        if (expected.isBlank() || folder.isBlank()) "$NO_CHEATS\n$ADD_CHEATS a RetroArch .cht file to the Cheats folder."
        else "$NO_CHEATS\n$ADD_CHEATS a RetroArch .cht file as $folder/$expected."

    const val NO_CHEATS = "No cheats for this game."
    private const val ADD_CHEATS = "Download them with Tools → Cheats & Patches, or add"

    /** The disc after [index] (0-based) of [count], wrapping, as each Swap disc tap steps it. */
    fun nextDisc(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1).mod(count)

    /** The toast a Swap disc tap shows: "Disc 2 of 3" ([index] 0-based). */
    fun discToast(index: Int, count: Int): String = "Disc ${index + 1} of $count"

    /** The header's achievement line, "Achievements 3 / 40"; none without a set (no count, or a total of 0). */
    fun raLine(h: GamePanelHeader): String? = h.ra?.takeIf { it.second > 0 }?.let { (unlocked, total) -> "Achievements $unlocked / $total" }

    /** The header's play line: "Total 4h 12m". */
    fun timeLine(h: GamePanelHeader): String = "Total ${GameTimeFormat.duration(h.totalMs)}"
}
