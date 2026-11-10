package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.cheats.CheatEntry
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamePanelModelTest {
    private val speedTiles = listOf(Shortcut.FAST_FORWARD, Shortcut.REWIND, Shortcut.RESET)

    private fun shortcuts(
        discCount: Int = 1, discIndex: Int = 0, netplay: Boolean = false,
        ff: Boolean = false, rewind: Boolean? = false, mic: Boolean? = null,
        touch: List<ShortcutAction> = emptyList(), slot: Int? = null,
    ) = GamePanelModel.shortcuts(discCount, discIndex, netplay, ff, rewind, mic, touch, slot)

    @Test fun tabs_shortcuts_only() {
        assertEquals(listOf(GamePanelTab.SHORTCUTS), GamePanelModel.tabs(raAvailable = false, playerSlots = 1))
        assertEquals(listOf(GamePanelTab.SHORTCUTS), GamePanelModel.tabs(raAvailable = false, playerSlots = 0))
    }

    @Test fun tabs_with_ra_and_players() {
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS, GamePanelTab.PLAYERS), GamePanelModel.tabs(raAvailable = true, playerSlots = 2))
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS), GamePanelModel.tabs(raAvailable = true, playerSlots = 1))
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.PLAYERS), GamePanelModel.tabs(raAvailable = false, playerSlots = 4))
    }

    @Test fun tabs_with_cheats_sit_before_players() {
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.CHEATS), GamePanelModel.tabs(raAvailable = false, playerSlots = 1, cheats = true))
        assertEquals(
            listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS, GamePanelTab.CHEATS, GamePanelTab.PLAYERS),
            GamePanelModel.tabs(raAvailable = true, playerSlots = 2, cheats = true),
        )
        assertEquals(GamePanelTab.SHORTCUTS, GamePanelModel.resolveTab(GamePanelTab.CHEATS, GamePanelModel.tabs(raAvailable = true, playerSlots = 2)))
    }

    @Test fun cheat_rows_take_the_source_from_the_info() {
        val rows = GamePanelModel.cheatRows(listOf(
            CheatEntry("Infinite Lives", "01", "Infinite Lives", enabled = false),
            CheatEntry("Max Score", "02", "(GameShark) Max Score", enabled = true),
            CheatEntry("Speed", "03", "Built-in: Speed", enabled = false),
        ))
        assertEquals(listOf(CheatRow("Infinite Lives", "", false), CheatRow("Max Score", "GameShark", true), CheatRow("Speed", "Built-in", false)), rows)
    }

    @Test fun a_cheat_tap_flips_its_switch() {
        val rows = listOf(CheatRow("A", "", false), CheatRow("B", "", true))
        assertEquals(GamePanelEvent.SetCheat(0, on = true), GamePanelModel.cheatEvent(0, rows))
        assertEquals(GamePanelEvent.SetCheat(1, on = false), GamePanelModel.cheatEvent(1, rows))
        assertNull(GamePanelModel.cheatEvent(2, rows))
    }

    @Test fun no_cheats_text_says_where_to_get_them() {
        assertEquals(
            "No cheats for this game.\nDownload them with Tools → Cheats & Patches, or add a RetroArch .cht file as Cheats/PS/Crash.cht.",
            GamePanelModel.noCheatsText("Cheats/PS", "Crash.cht"),
        )
        assertEquals(
            "No cheats for this game.\nDownload them with Tools → Cheats & Patches, or add a RetroArch .cht file to the Cheats folder.",
            GamePanelModel.noCheatsText("Cheats/PS", ""),
        )
    }

    @Test fun resolve_tab_falls_back_when_gone() {
        val shortcutsOnly = GamePanelModel.tabs(raAvailable = false, playerSlots = 1)
        assertEquals(GamePanelTab.SHORTCUTS, GamePanelModel.resolveTab(GamePanelTab.ACHIEVEMENTS, shortcutsOnly))
        assertEquals(GamePanelTab.SHORTCUTS, GamePanelModel.resolveTab(GamePanelTab.PLAYERS, shortcutsOnly))
        val all = GamePanelModel.tabs(raAvailable = true, playerSlots = 2)
        assertEquals(GamePanelTab.PLAYERS, GamePanelModel.resolveTab(GamePanelTab.PLAYERS, all))
        assertEquals(GamePanelTab.ACHIEVEMENTS, GamePanelModel.resolveTab(GamePanelTab.ACHIEVEMENTS, all))
    }

    @Test fun swap_disc_only_multi_disc_and_not_netplay() {
        assertTrue(shortcuts(discCount = 1).none { it.shortcut == Shortcut.SWAP_DISC })
        assertTrue(shortcuts(discCount = 0).none { it.shortcut == Shortcut.SWAP_DISC })
        assertTrue(shortcuts(discCount = 3, netplay = true).none { it.shortcut == Shortcut.SWAP_DISC })
        val swap = shortcuts(discCount = 3, discIndex = 1).single { it.shortcut == Shortcut.SWAP_DISC }
        assertTrue(swap.enabled)
        assertEquals("2/3", swap.value)
        assertEquals("Swap disc", swap.label)
    }

    // The touch buttons' tiles: each button's action in the buttons' order, Save and Load on the first two while unset;
    // one with a tile of the panel's own, a repeat, or a rewind hold where the game can't rewind is left out.
    @Test fun touch_buttons_get_tiles_with_save_and_load_by_default() {
        val save = ShortcutAction.SAVE_STATE; val load = ShortcutAction.LOAD_STATE
        assertEquals(listOf(save, load), GamePanelModel.panelTouchActions(emptyMap(), rewind = true))
        assertEquals(listOf(save, load), GamePanelModel.panelTouchActions(mapOf(TouchSlot.THREE to save, TouchSlot.FOUR to load), rewind = true))
        val set = mapOf(TouchSlot.ONE to ShortcutAction.HOLD_FF, TouchSlot.THREE to ShortcutAction.HOLD_REWIND, TouchSlot.FOUR to ShortcutAction.TOGGLE_FF)
        assertEquals(listOf(ShortcutAction.HOLD_FF, load, ShortcutAction.HOLD_REWIND), GamePanelModel.panelTouchActions(set, rewind = true))
        assertEquals(listOf(ShortcutAction.HOLD_FF, load), GamePanelModel.panelTouchActions(set, rewind = false))
        assertEquals(listOf(save, load), GamePanelModel.panelTouchActions(mapOf(TouchSlot.THREE to ShortcutAction.RESET, TouchSlot.FOUR to ShortcutAction.TOGGLE_REWIND), rewind = true))
    }

    // A touch tile is first, named for its action, with the save-state slot under Save and Load; netplay has none.
    @Test fun touch_tiles_come_first_and_name_the_slot() {
        val list = shortcuts(discCount = 2, touch = listOf(ShortcutAction.SAVE_STATE, ShortcutAction.LOAD_STATE, ShortcutAction.HOLD_FF), slot = 2)
        assertEquals(listOf("Save", "Load", "Hold fast forward", "Swap disc", "Fast forward", "Rewind", "Reset"), list.map { it.label })
        assertEquals(listOf("Slot 2", "Slot 2", null), list.take(3).map { it.value })
        assertTrue(list.take(3).all { it.shortcut == Shortcut.TOUCH && it.on == null && it.enabled })
        assertEquals(ShortcutAction.HOLD_FF, list[2].action)
        assertNull(shortcuts(touch = listOf(ShortcutAction.SAVE_STATE)).first().value)   // the slot not read yet
        assertNull(GamePanelModel.eventFor(list.first()))   // its press and release are the tile's own
        assertTrue(shortcuts(netplay = true, touch = listOf(ShortcutAction.SAVE_STATE)).isEmpty())
    }

    @Test fun tiles() {
        for (list in listOf(shortcuts(), shortcuts(discCount = 2))) {
            val tiles = list.filter { it.shortcut in speedTiles }
            assertEquals(speedTiles, tiles.map { it.shortcut })
            assertTrue(tiles.all { it.enabled })
            assertEquals(listOf(false, false, null), tiles.map { it.on })   // FF and Rewind toggles; Reset an action
        }
        // Display order: the touch buttons, Swap disc, then Fast forward, Rewind, Mic and Reset.
        assertEquals(Shortcut.entries.toList(), shortcuts(discCount = 2, mic = false, touch = listOf(ShortcutAction.SAVE_STATE)).map { it.shortcut })
        // The live state fills the toggle.
        assertEquals(true, shortcuts(ff = true).single { it.shortcut == Shortcut.FAST_FORWARD }.on)
        assertEquals(true, shortcuts(rewind = true).single { it.shortcut == Shortcut.REWIND }.on)
    }

    // The Settings tab is the second-screen panel's: after Shortcuts, only where asked for.
    @Test fun settings_tab_sits_after_shortcuts() {
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.SETTINGS, GamePanelTab.ACHIEVEMENTS), GamePanelModel.tabs(raAvailable = true, playerSlots = 1, settings = true))
        assertTrue(GamePanelTab.SETTINGS !in GamePanelModel.tabs(raAvailable = true, playerSlots = 2, cheats = true))
    }

    // A Settings option: up to four values sit side by side, more step between two arrows (wrapping); a value not among
    // them steps to the first or the last, and a step onto the value in use asks for nothing.
    @Test fun a_quick_choice_marks_its_value_and_steps_round() {
        val scaling = GamePanelModel.quickChoice("k", "Screen Scaling", listOf("Auto", "Native", "Aspect", "Fullscreen"), "Aspect")
        assertEquals(2, scaling.index); assertTrue(scaling.segments); assertTrue(scaling.enabled)
        val shader = GamePanelModel.quickChoice("s", "Shader", listOf("None", "nx/line.glslp", "nx/grid.glslp", "User/crt/easy.glslp", "x.glslp"), "None")
        assertTrue(!shader.segments)
        assertEquals("nx/line.glslp", GamePanelModel.quickStep(shader, 1))
        assertEquals("x.glslp", GamePanelModel.quickStep(shader, -1))
        val gone = shader.copy(index = -1, current = "User/removed.glslp")
        assertEquals("None", GamePanelModel.quickStep(gone, 1)); assertEquals("x.glslp", GamePanelModel.quickStep(gone, -1))
        assertNull(GamePanelModel.quickStep(GamePanelModel.quickChoice("o", "Overlay", listOf("None"), "None"), 1))
        assertTrue(!GamePanelModel.quickChoice("o", "Overlay", listOf("None"), "None").segments)   // one value: nothing to pick between
        assertEquals("easy", GamePanelModel.quickFileLabel("User/crt/easy.glslp"))
        assertEquals("None", GamePanelModel.quickFileLabel("None"))
        assertEquals("scan lines", GamePanelModel.quickFileLabel("scan lines.png"))
    }

    @Test fun noScreenshot() {
        assertTrue(Shortcut.entries.none { it.name == "SCREENSHOT" })
    }

    @Test fun rewindHiddenWithoutRewind() {
        val list = shortcuts(rewind = null)
        assertTrue(list.none { it.shortcut == Shortcut.REWIND })
        assertEquals(listOf(Shortcut.FAST_FORWARD, Shortcut.RESET), list.map { it.shortcut })
    }

    @Test fun micTileOrder() {
        val list = shortcuts(mic = false)
        assertEquals(listOf("Fast forward", "Rewind", "Mic", "Reset"), list.takeLast(4).map { it.label })
        assertEquals(false, list.single { it.shortcut == Shortcut.MIC }.on)
    }

    @Test fun micTileHidden() {
        assertTrue(shortcuts(mic = null).none { it.shortcut == Shortcut.MIC })
        assertTrue(shortcuts(netplay = true, mic = true).none { it.shortcut == Shortcut.MIC })
    }

    @Test fun micTileFlips() {
        assertEquals(GamePanelEvent.SetMic(on = false), GamePanelModel.eventFor(shortcuts(mic = true).single { it.shortcut == Shortcut.MIC }))
        assertEquals(GamePanelEvent.SetMic(on = true), GamePanelModel.eventFor(shortcuts(mic = false).single { it.shortcut == Shortcut.MIC }))
    }

    @Test fun netplayHidesAll() {
        for (list in listOf(shortcuts(netplay = true), shortcuts(netplay = true, ff = true, rewind = true), shortcuts(discCount = 2, netplay = true))) {
            assertTrue(list.none { it.shortcut in speedTiles })
        }
    }

    @Test fun ffTileToggles() {
        val off = shortcuts().single { it.shortcut == Shortcut.FAST_FORWARD }
        assertEquals(GamePanelEvent.SetFastForward(on = true), GamePanelModel.eventFor(off))
        val on = shortcuts(ff = true).single { it.shortcut == Shortcut.FAST_FORWARD }
        assertEquals(GamePanelEvent.SetFastForward(on = false), GamePanelModel.eventFor(on))
        assertEquals(GamePanelEvent.SetRewind(on = true), GamePanelModel.eventFor(shortcuts().single { it.shortcut == Shortcut.REWIND }))
        assertEquals(GamePanelEvent.SetRewind(on = false), GamePanelModel.eventFor(shortcuts(rewind = true).single { it.shortcut == Shortcut.REWIND }))
        assertEquals(GamePanelEvent.Reset, GamePanelModel.eventFor(shortcuts().single { it.shortcut == Shortcut.RESET }))
    }

    @Test fun disabled_shortcut_has_no_event() {
        val list = shortcuts(discCount = 2)
        for (s in list) assertNull(GamePanelModel.eventFor(s.copy(enabled = false)))
        assertEquals(GamePanelEvent.SwapDisc, GamePanelModel.eventFor(list.single { it.shortcut == Shortcut.SWAP_DISC }))
        // The event follows the typed state, not the display text.
        val typed = ShortcutState(Shortcut.FAST_FORWARD, "Fast forward", enabled = true, value = "whatever", on = true)
        assertEquals(GamePanelEvent.SetFastForward(false), GamePanelModel.eventFor(typed))
    }

    @Test fun player_tap_cycles_empty_and_every_connected_pad() {
        val slots = listOf(PlayerSlotRow("a", "Pad A", true), PlayerSlotRow(null, "", false))
        val pads = listOf(PlayerPadRow("a", "Pad A"), PlayerPadRow("b", "Pad B"), PlayerPadRow("c", "Pad C"))
        assertEquals(GamePanelEvent.SetPlayer(0, "b"), GamePanelModel.nextPlayer(0, slots, pads))
        assertEquals(GamePanelEvent.SetPlayer(1, "a"), GamePanelModel.nextPlayer(1, slots, pads))
        // After the last pad (a benched one included), Empty again.
        assertEquals(GamePanelEvent.SetPlayer(0, null), GamePanelModel.nextPlayer(0, listOf(PlayerSlotRow("c", "Pad C", true)), pads))
    }

    @Test fun cycle_player_steps_both_ways_and_a_held_slot_starts_from_empty() {
        val slots = listOf(PlayerSlotRow("a", "Pad A", true), PlayerSlotRow("z", "Gone", false))
        val pads = listOf(PlayerPadRow("a", "Pad A"), PlayerPadRow("c", "Benched C"))
        assertEquals("c", GamePanelModel.cyclePlayer(0, 1, slots, pads))
        assertEquals(null, GamePanelModel.cyclePlayer(0, -1, slots, pads))
        assertEquals("a", GamePanelModel.cyclePlayer(1, 1, slots, pads))   // "z" is not connected: from Empty
        assertEquals("c", GamePanelModel.cyclePlayer(1, -1, slots, pads))  // wraps to the last pad
    }

    @Test fun disc_toast_is_one_based() {
        assertEquals("Disc 2 of 3", GamePanelModel.discToast(1, 3))
        assertEquals("Disc 1 of 2", GamePanelModel.discToast(0, 2))
    }

    @Test fun next_disc_cycles() {
        assertEquals(1, GamePanelModel.nextDisc(0, 3))
        assertEquals(2, GamePanelModel.nextDisc(1, 3))
        assertEquals(0, GamePanelModel.nextDisc(2, 3))
        assertEquals(0, GamePanelModel.nextDisc(5, 3))   // a stale index out of range wraps too
    }

    private fun achievement(state: RaAchievementState, points: Int = 10) =
        RaAchievement(1, "First Barrel", "Jump a barrel", points, "b", state, null, null, 0f, 0f, null)

    @Test fun points_badge_only_with_points() {
        assertEquals("10", GamePanelModel.pointsBadge(achievement(RaAchievementState.LOCKED, points = 10)))
        assertNull(GamePanelModel.pointsBadge(achievement(RaAchievementState.UNLOCKED, points = 0)))
    }

    @Test fun header_lines_total_only_and_ra_only_with_a_set() {
        val h = GamePanelHeader("Tetris", null, "GB", 4 * 3_600_000L + 12 * 60_000L, null, "9:00", null)
        assertEquals("Total 4h 12m", GamePanelModel.timeLine(h))
        assertNull(GamePanelModel.raLine(h))
        assertNull(GamePanelModel.raLine(h.copy(ra = 0 to 0)))   // a summary with no achievements: no line, no room
        assertEquals("Achievements 3 / 40", GamePanelModel.raLine(h.copy(ra = 3 to 40)))
    }
}
