package com.nxredux.mobile.ui

import com.nxredux.mobile.options.ConsoleRow
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.control
import com.nxredux.mobile.ui.nx.flipped
import com.nxredux.mobile.ui.nx.fullInfo
import com.nxredux.mobile.ui.nx.hasMoreInfo
import com.nxredux.mobile.ui.nx.nxSettingControl
import com.nxredux.mobile.ui.nx.nxSwitchOnIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.nx.NxTouchRowSizes
import com.nxredux.mobile.ui.nx.TOUCH_ROW_AIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Settings / Emulator Settings touch rows: their controls, values, descriptions and hints. */
class SettingsTouchRowsTest {
    @Test fun controlFollowsTheValues() {
        assertEquals(NxSettingControl.SWITCH, nxSettingControl(listOf("Off", "On")))
        assertEquals(NxSettingControl.SWITCH, nxSettingControl(listOf("Hide", "Show")))
        assertEquals(NxSettingControl.SWITCH, nxSettingControl(listOf("enabled", "disabled")))
        assertEquals(NxSettingControl.SEGMENTED, nxSettingControl(listOf("Charcoal", "Retro")))
        assertEquals(NxSettingControl.SEGMENTED, nxSettingControl(listOf("Off", "Light", "Medium", "Strong")))
        assertEquals(NxSettingControl.PICKER, nxSettingControl(listOf("Full screen", "List")))   // a label too long for a segment
        assertEquals(NxSettingControl.PICKER, nxSettingControl(listOf("List", "Grid", "Carousel", "Backdrop", "Minimal")))   // five values
        assertEquals(NxSettingControl.PICKER, nxSettingControl(listOf("Controller", "Background", "None", "Wheel")))   // too long together
        assertEquals(NxSettingControl.VALUE, nxSettingControl(listOf("Only")))
        assertEquals(NxSettingControl.VALUE, nxSettingControl(emptyList()))
    }

    @Test fun switchKnowsWhichValueIsOn() {
        assertEquals(1, nxSwitchOnIndex(listOf("Off", "On")))
        assertEquals(0, nxSwitchOnIndex(listOf("Show", "Hide")))
        assertEquals(1, nxSwitchOnIndex(listOf(" hide ", "SHOW")))
        assertEquals(-1, nxSwitchOnIndex(listOf("On", "Auto")))
        assertEquals(0, NxSettingRow("x", choices = listOf("Off", "On"), index = 1).flipped)
        assertEquals(1, NxSettingRow("x", choices = listOf("Off", "On"), index = -1).flipped)
    }

    @Test fun fullInfoIsTheLongerText() {
        val short = NxSettingRow("x", info = "Short", detail = "Short, and much more")
        assertTrue(short.hasMoreInfo); assertEquals("Short, and much more", short.fullInfo)
        val plain = NxSettingRow("x", info = "All of it")
        assertFalse(plain.hasMoreInfo); assertEquals("All of it", plain.fullInfo)
    }

    @Test fun settingsRowsBecomeTouchRows() {
        val rumble = settingsTouchRow(SettingsRow(SettingsKind.GAME_RUMBLE, "Game rumble", "Medium"), SettingChoices(listOf("Off", "Light", "Medium", "Strong"), 2))
        assertEquals(listOf("Off", "Light", "Medium", "Strong"), rumble.choices); assertEquals(2, rumble.index)
        assertEquals(NxSettingControl.SEGMENTED, rumble.control); assertEquals(null, rumble.value)
        assertEquals("", rumble.info); assertTrue(rumble.hasMoreInfo)   // compact, its text behind the info icon
        // A dimmed row shows its value only; a page's opener its value and `›`; an action neither.
        val gb = settingsTouchRow(SettingsRow(SettingsKind.DEFAULT_TAG, "Game Boy", "Gambatte (GB)", consoleId = "gb", enabled = false), null)
        assertEquals(emptyList(), gb.choices); assertEquals("Gambatte (GB)", gb.value); assertFalse(gb.enabled)
        val padLayout = settingsTouchRow(SettingsRow(SettingsKind.PAD_LAYOUT, "Pad layout", "Default"), null)
        assertTrue(padLayout.opens); assertEquals("Default", padLayout.value)
        val reset = settingsTouchRow(SettingsRow(SettingsKind.RESET_DEFAULTS, RESET_DEFAULTS_LABEL), null, SettingsSection.APPEARANCE)
        assertFalse(reset.opens); assertEquals(null, reset.value)
        assertEquals("Resets every setting on this page", reset.info); assertTrue("Text size" in reset.detail)
    }

    @Test fun aDroppedRowIsCompactWithItsTextBehindTheIcon() {
        val accent = settingsTouchRow(SettingsRow(SettingsKind.ACCENT, "Accent", "White"), null)
        assertEquals("", accent.info)
        assertTrue(accent.detail.startsWith("The selection colour")); assertTrue(accent.hasMoreInfo)
        assertEquals(accent.detail, accent.fullInfo)
        val addFolder = settingsInfo(SettingsRow(SettingsKind.ADD_EXTRA, "Add ROM folder"))   // no fuller text: the old line moves behind it
        assertEquals(SettingText("", "Scan another folder for games too, such as one on an SD card"), addFolder)
    }

    // Layouts' group rows say nothing on the row (no line, no info icon, no X Info); each group's page says it under its title.
    @Test fun layoutsGroupRowsMoveTheirDescriptionOntoTheirPages() {
        val groups = SettingsSection.entries.filter { it.layoutGroup }
        assertEquals(7, groups.size)
        for (g in groups) {
            val row = settingsTouchRow(SettingsRow(g.opener!!, g.label, "List"), null, SettingsSection.MAIN_MENU)
            assertEquals("", row.info, "$g"); assertEquals("", row.detail, "$g")
            assertFalse(nxShowsInfo(row, null), "$g")
            assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Select")), settingsHints(listOf(SettingsRow(g.opener!!, g.label)), 0, row, info = false))
            assertTrue(!layoutGroupDescription(g).isNullOrBlank(), "$g")
        }
        assertEquals("Home tab, its layout, shelves and stats", layoutGroupDescription(SettingsSection.LAYOUT_HOME))
        assertEquals(null, layoutGroupDescription(SettingsSection.MAIN_MENU))
        assertEquals(null, layoutGroupDescription(SettingsSection.APPEARANCE))
    }

    // Every touch row: its text (the name, then its description's own lines) with the same air above and below.
    @Test fun touchRowsKeepTheSameAirWhateverTheirText() {
        val sizes = NxTouchRowSizes(16.sp, 14.4.sp, 18.sp, 14.sp, row = 100.dp, segments = 30.dp, bare = 64.dp, labelLineDp = 20.dp, descLineDp = 18.dp)
        val air = TOUCH_ROW_AIR * 2
        assertEquals(20.dp + air, sizes.height(descLines = 0, segmentsBelow = false))            // the name alone: no list-pitch minimum
        assertEquals(20.dp + 18.dp + air, sizes.height(descLines = 1, segmentsBelow = false))   // one line: no room for a second
        assertEquals(20.dp + 36.dp + air, sizes.height(descLines = 2, segmentsBelow = false))
        assertEquals(sizes.height(2, false), sizes.height(5, false))                              // at most two lines (then an ellipsis)
        assertEquals(20.dp + 18.dp + air + 30.dp, sizes.height(descLines = 1, segmentsBelow = true))   // segments under the text add their line
        assertEquals(24.dp + air, sizes.height(descLines = 0, segmentsBelow = false, control = 24.dp))   // a switch taller than the name
        for (lines in 0..2) assertEquals(air, sizes.height(lines, false) - (20.dp + 18.dp * lines))
    }

    @Test fun aKeptRowShowsItsShortLine() {
        val shelf = settingsTouchRow(SettingsRow(SettingsKind.SHELF_SHOWN, "New shelf", shelf = HomeShelf.NEW), null)
        assertEquals("Games found in the last 30 days", shelf.info); assertTrue(shelf.hasMoreInfo)
        val notStarted = settingsTouchRow(SettingsRow(SettingsKind.SHELF_SHOWN, "Not started shelf", shelf = HomeShelf.NOT_STARTED), null)
        assertEquals("Games you have never played", notStarted.info); assertFalse(notStarted.hasMoreInfo)   // the line alone: no info icon
        val stats = settingsTouchRow(SettingsRow(SettingsKind.HOME_STATS, "Stats"), null)
        assertEquals("This month and Most played", stats.info); assertTrue("stats strip" in stats.detail)
        // All files access: a line only for the warning.
        assertEquals("", settingsInfo(SettingsRow(SettingsKind.ALL_FILES, "All files access")).line)
        assertEquals("Off: some ROM folders can't be read", settingsInfo(SettingsRow(SettingsKind.ALL_FILES, "All files access", note = "Off: some ROM folders can't be read")).line)
    }

    @Test fun micAccessRowHasItsInfoText() {
        val row = SettingsRow(SettingsKind.MIC_ACCESS, "Microphone access", "Off")
        assertEquals("Lets DS and 3DS games hear the Mic toggle's live mic", settingsInfo(row).detail)
    }

    @Test fun everyDescriptionLineFitsTwoRowLines() {
        val rows = SettingsKind.entries.map { SettingsRow(it, "x") } + listOf(
            SettingsRow(SettingsKind.TAB_SHOWN, "x", category = MenuCategory.COLLECTIONS),
            SettingsRow(SettingsKind.MENU_STYLE, "x", category = MenuCategory.COLLECTIONS),
            SettingsRow(SettingsKind.SHELF_SHOWN, "x", shelf = HomeShelf.NOT_STARTED),
            SettingsRow(SettingsKind.HINT_SHOWN, "x", hint = NxButton.SELECT),
            SettingsRow(SettingsKind.PAD_LAYOUT_SHAPE, "x", padShape = PadShape.OVERLAY),
            SettingsRow(SettingsKind.DEFAULT_TAG, "x", enabled = false),
        )
        for (r in rows) for (section in listOf(null, SettingsSection.MAIN_MENU, SettingsSection.CONTROLS)) {
            val t = settingsInfo(r, section)
            assertTrue(t.line.length <= 70, "${r.kind}: ${t.line}")
            assertTrue(t.detail.isEmpty() || t.detail.length > t.line.length, "${r.kind}: its detail says more")
        }
    }

    @Test fun touchHintsFollowTheControl() {
        val rows = listOf(SettingsRow(SettingsKind.MENU_PAD, "Menu pad", "On"), SettingsRow(SettingsKind.ACCENT, "Accent", "White"))
        val back = Hint(NxButton.B, "Back")
        val change = Hint(NxButton.LEFT, "Change")
        assertEquals(listOf(back, change, Hint(NxButton.A, "Toggle"), Hint(NxButton.X, "Info")),
            settingsHints(rows, 0, NxSettingRow("Menu pad", choices = listOf("Off", "On"), index = 1), info = true))
        assertEquals(listOf(back, change, Hint(NxButton.A, "Choose")), settingsHints(rows, 1, NxSettingRow("Accent", choices = List(9) { "c$it" }, index = 0)))
        assertEquals(listOf(back, change), settingsHints(rows, 1, NxSettingRow("Pad theme", choices = listOf("Charcoal", "Retro"), index = 0)))   // segmented: A steps
        assertEquals(listOf(back, Hint(NxButton.A, "Select")), settingsHints(rows, 1))   // no touch row: as before
    }

    @Test fun emulatorRowsBecomeTouchRows() {
        assertTrue(emulatorTouchRow(EmulatorRow.Header("Console settings")).section)
        assertTrue(emulatorTouchRow(EmulatorRow.Gap).spacer)
        assertTrue(emulatorTouchRow(EmulatorRow.Category("video", "Video")).opens)
        val opt = CoreOption("opt_a", "y", label = "Option A", info = "What A does", values = listOf("x", "y", "z"), defaultValue = "x")
        val core = emulatorTouchRow(EmulatorRow.Core(opt, inCategory = false))
        assertEquals(listOf("x", "y", "z"), core.choices); assertEquals(1, core.index)
        assertEquals("", core.info); assertEquals("What A does", core.detail); assertTrue(core.hasMoreInfo)   // the core's text: info icon only
        assertEquals(NxSettingControl.SEGMENTED, core.control)
        val layout = PadOptions.all.first { it.key == PadOptions.LAYOUT }
        val console = emulatorTouchRow(EmulatorRow.Console(ConsoleRow(layout, PadOptions.AUTO)))
        assertEquals(layout.values.map(layout::labelOf), console.choices); assertEquals(layout.values.indexOf(PadOptions.AUTO), console.index)
        assertEquals("", console.info); assertEquals(layout.info, console.detail)
        val pad = emulatorTouchRow(EmulatorRow.Console(ConsoleRow(PadOptions.portraitPadRow, PadOptions.SHOWN)))
        assertEquals("Hidden: the pad goes see-through over the screens", pad.info); assertEquals(PadOptions.portraitPadRow.info, pad.detail)
        assertEquals(USE_EMULATOR_SETTINGS_INFO, emulatorTouchRow(EmulatorRow.UseEmulatorSettings).info)
    }

    @Test fun emulatorHintsFollowTheControl() {
        val opt = CoreOption("opt_a", "x", label = "Option A", values = listOf("x", "y"), defaultValue = "x")
        val row = EmulatorRow.Core(opt, inCategory = false)
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.B, "Back"), Hint(NxButton.A, "Select")), emulatorOptionsHints(row))
        val many = NxSettingRow("Option A", info = "cut", choices = List(6) { "v$it" }, index = 0)
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.B, "Back"), Hint(NxButton.A, "Choose"), Hint(NxButton.X, "Info")), emulatorOptionsHints(row, many, truncated = "cut"))
    }

    @Test fun settingValuePicksByIndex() {
        var value = "b"
        val after = mutableListOf<String>()
        val v = SettingValue(listOf("a", "b", "c"), value, { value = it }, { it.uppercase() }, after = { after += it })
        assertEquals(listOf("A", "B", "C"), v.labels); assertEquals(1, v.index)
        v.changeTo(2); assertEquals("c", value); assertEquals(listOf("c"), after)
        v.changeTo(1); assertEquals(listOf("c"), after)   // already the one (as v read it): nothing
        v.changeTo(5); assertEquals("c", value)            // out of range: nothing
        assertEquals(null, SettingValue(listOf(1, 2), 1, {}, after = {}).labels)
    }

    @Test fun androidsOwnSwitchesHaveNoLeftChangeHint() {
        val touch = NxSettingRow("Use as home screen", choices = listOf("Off", "On"), index = 0)
        val back = Hint(NxButton.B, "Back")
        val toggle = Hint(NxButton.A, "Toggle")
        for (kind in listOf(SettingsKind.HOME_SCREEN, SettingsKind.ALL_FILES, SettingsKind.MIC_ACCESS))
            assertEquals(listOf(back, toggle), settingsHints(listOf(SettingsRow(kind, touch.label)), 0, touch), "$kind")
        val pause = SettingsRow(SettingsKind.PAUSE_UNFOCUSED, "Pause when unfocused")
        assertEquals(listOf(back, Hint(NxButton.LEFT, "Change"), toggle), settingsHints(listOf(pause), 0, touch.copy(label = pause.label)))
    }
}
