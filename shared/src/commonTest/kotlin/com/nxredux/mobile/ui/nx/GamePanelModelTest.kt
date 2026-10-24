package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.cheats.CheatEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamePanelModelTest {
    private val disabled = listOf(Shortcut.FAST_FORWARD, Shortcut.REWIND, Shortcut.SCREENSHOT, Shortcut.RESET)

    private fun shortcuts(discCount: Int = 1, discIndex: Int = 0, connected: Boolean = false, on: Boolean = true, netplay: Boolean = false) =
        GamePanelModel.shortcuts(discCount, discIndex, connected, on, netplay)

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
            "No cheats for this game.\nDownload them with Tools → Cheat Database, or add a RetroArch .cht file as Cheats/PS/Crash.cht.",
            GamePanelModel.noCheatsText("Cheats/PS", "Crash.cht"),
        )
        assertEquals(
            "No cheats for this game.\nDownload them with Tools → Cheat Database, or add a RetroArch .cht file to the Cheats folder.",
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

    @Test fun second_screen_only_when_connected() {
        assertTrue(shortcuts(connected = false).none { it.shortcut == Shortcut.SECOND_SCREEN })
        val on = shortcuts(connected = true, on = true).single { it.shortcut == Shortcut.SECOND_SCREEN }
        assertTrue(on.enabled)
        assertNull(on.value)   // a toggle: no On/Off words, its fill says it
        assertEquals(true, on.on)
        val off = shortcuts(connected = true, on = false).single { it.shortcut == Shortcut.SECOND_SCREEN }
        assertNull(off.value)
        assertEquals(false, off.on)
        // Netplay hides only Swap disc.
        assertTrue(shortcuts(connected = true, netplay = true).any { it.shortcut == Shortcut.SECOND_SCREEN })
    }

    @Test fun future_shortcuts_always_disabled() {
        for (list in listOf(shortcuts(), shortcuts(discCount = 2, connected = true), shortcuts(netplay = true))) {
            val future = list.filter { it.shortcut in disabled }
            assertEquals(disabled, future.map { it.shortcut })
            assertTrue(future.none { it.enabled })
        }
        // Display order: Swap disc, Second screen, then the four disabled ones.
        assertEquals(Shortcut.entries.toList(), shortcuts(discCount = 2, connected = true).map { it.shortcut })
    }

    @Test fun disabled_shortcut_has_no_event() {
        for (s in shortcuts().filter { it.shortcut in disabled }) assertNull(GamePanelModel.eventFor(s))
        val list = shortcuts(discCount = 2, connected = true, on = true)
        assertEquals(GamePanelEvent.SwapDisc, GamePanelModel.eventFor(list.single { it.shortcut == Shortcut.SWAP_DISC }))
        assertEquals(GamePanelEvent.SetSecondScreen(false), GamePanelModel.eventFor(list.single { it.shortcut == Shortcut.SECOND_SCREEN }))
        val off = shortcuts(connected = true, on = false).single { it.shortcut == Shortcut.SECOND_SCREEN }
        assertEquals(GamePanelEvent.SetSecondScreen(true), GamePanelModel.eventFor(off))
        // The event follows the typed setting, not the display text.
        val typed = ShortcutState(Shortcut.SECOND_SCREEN, "Second screen", enabled = true, value = "whatever", on = true)
        assertEquals(GamePanelEvent.SetSecondScreen(false), GamePanelModel.eventFor(typed))
        // A swap shortcut forced disabled stays inert too.
        assertNull(GamePanelModel.eventFor(list.single { it.shortcut == Shortcut.SWAP_DISC }.copy(enabled = false)))
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
