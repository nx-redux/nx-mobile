package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.options.ConsoleRow
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxRowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which pages draw NX Redux's options row (label: value) and which keep the pill list. */
class OptionsPagesTest {
    private val page = EmulatorPage("GBA", "gpsp", "gpSP", emptyList(), emptyList(), forGame = false, gameFileExists = false)

    @Test fun settingsPagesWithValuesUseTheOptionsRow() {
        val navigation = setOf(SettingsSection.EMULATORS)   // Library draws touch rows (its All files switch)
        for (section in SettingsSection.entries - navigation) {
            assertEquals(NxRowKind.OPTIONS, listRowKind(Screen.Settings(section, emptyList())), section.name)
        }
        assertEquals(NxRowKind.OPTIONS, listRowKind(Screen.RaSettings(emptyList())))
        assertEquals(NxRowKind.OPTIONS, listRowKind(Screen.EmulatorOptions(page, emptyList())))
        assertEquals(NxRowKind.OPTIONS, listRowKind(Screen.ArtworkSettings(emptyList())))
    }

    @Test fun artworkValueRowsCycle() {
        assertEquals(NxRow("Replace existing art", secondary = "Off", cycleable = true), artworkSettingNxRow(ArtworkSettingRow(ArtworkSettingKind.REPLACE, "Replace existing art", "Off")))
        assertFalse(artworkSettingNxRow(ArtworkSettingRow(ArtworkSettingKind.REPLACE, "Replace existing art", "Off", enabled = false)).cycleable)
        assertEquals(NxRow("Reset artwork…"), artworkSettingNxRow(ArtworkSettingRow(ArtworkSettingKind.RESET, "Reset artwork…")))
        // The account's rows open the sign-in or read only: no arrows.
        assertEquals(NxRow("ScreenScraper logout", secondary = "5 / 100 today"), artworkSettingNxRow(ArtworkSettingRow(ArtworkSettingKind.SS_LOGOUT, "ScreenScraper logout", "5 / 100 today")))
    }

    @Test fun categoryAndGameListsKeepThePill() {
        assertEquals(NxRowKind.PILL, listRowKind(Screen.SettingsMenu(SettingsSection.entries)))
        assertEquals(NxRowKind.PILL, listRowKind(Screen.Systems(emptyList())))
        assertEquals(NxRowKind.PILL, listRowKind(Screen.EmulatorList(emptyList())))
        assertEquals(NxRowKind.PILL, listRowKind(Screen.EmulatorConsoles("gpsp", "gpSP", listOf("GBA"))))
        assertEquals(NxRowKind.PILL, listRowKind(Screen.RetroAchievements()))
    }

    @Test fun emulatorListShowsEachHandheldTagOnce() {
        assertEquals("GPGX", emulatorListTags(EmulatorListRow("genesis_plus_gx", "Genesis Plus GX", listOf("GPGX", "GPGX_SMS", "GPGX_GG"))))
        assertEquals("MD · GG", emulatorListTags(EmulatorListRow("picodrive", "PicoDrive", listOf("MD", "GG"))))
    }

    @Test fun emulatorListTitleDropsEmulatorInTheSplit() {
        assertEquals("Settings", emulatorListTitle(split = true))   // the left pane already shows Emulators
        assertEquals("Emulator Settings", emulatorListTitle(split = false))
    }

    @Test fun settingsNavigationPagesUseTheListRows() {
        // Every row opens a page or runs an action: list rows at the list size, as the RetroAchievements page.
        for (section in listOf(SettingsSection.EMULATORS)) {
            assertEquals(NxRowKind.PILL, listRowKind(Screen.Settings(section, emptyList())), section.name)
        }
    }

    @Test fun settingsArrowsOnlyWhereLeftRightChangeTheValue() {
        assertEquals(NxRow("Pad theme", secondary = "Retro", cycleable = true), settingsNxRow(SettingsRow(SettingsKind.PAD_THEME, "Pad theme", "Retro")))
        assertTrue(settingsNxRow(SettingsRow(SettingsKind.DEFAULT_TAG, "Game Boy", "GB")).cycleable)
        assertTrue(settingsNxRow(SettingsRow(SettingsKind.GAME_LIST_STYLE, "Game lists", "List")).cycleable)
        assertTrue(settingsNxRow(SettingsRow(SettingsKind.DEBUG_LOG, "Debug log", "Off")).cycleable)   // Developer: flips on LEFT/RIGHT too
        assertTrue(settingsNxRow(SettingsRow(SettingsKind.MENU_STYLE, "Consoles", "List")).cycleable)
        assertFalse(settingsNxRow(SettingsRow(SettingsKind.MENU_STYLE, "Consoles", "List", enabled = false)).cycleable)
        // NX Redux ITEM_CYCLE: every value row steps on LEFT/RIGHT and shows the arrows.
        for (k in listOf(SettingsKind.PAD_THEME, SettingsKind.ACCENT)) {
            assertTrue(settingsNxRow(SettingsRow(k, "x", "y")).cycleable, k.name)
        }
        assertFalse(settingsNxRow(SettingsRow(SettingsKind.DEFAULT_TAG, "Game Boy", "GB", enabled = false)).cycleable)
        assertFalse(settingsNxRow(SettingsRow(SettingsKind.INFO, "Platform", "Android")).cycleable)
        assertFalse(settingsNxRow(SettingsRow(SettingsKind.EXTRA, "Roms", "ROM folder")).cycleable)
        assertEquals(NxRow("Hidden games", secondary = "2 games"), settingsNxRow(SettingsRow(SettingsKind.HIDDEN, "Hidden games", "2 games")))
        assertEquals(NxRow("Game Boy", enabled = false, secondary = "GB"), settingsNxRow(SettingsRow(SettingsKind.DEFAULT_TAG, "Game Boy", "GB", enabled = false)))
    }

    @Test fun raSettingsArrowsOnTheSteppedRows() {
        assertTrue(raSettingsNxRow(RaSettingsRow(RaSettingKind.NOTIFICATION_SECONDS, "Notification duration", "3 s")).cycleable)
        assertTrue(raSettingsNxRow(RaSettingsRow(RaSettingKind.PROGRESS_SECONDS, "Progress duration", "Off")).cycleable)
        assertTrue(raSettingsNxRow(RaSettingsRow(RaSettingKind.SORT_ORDER, "Achievement sort order", "Type")).cycleable)
        assertEquals(NxRow("Show notifications", secondary = "On", cycleable = true), raSettingsNxRow(RaSettingsRow(RaSettingKind.NOTIFICATIONS, "Show notifications", "On")))
        assertTrue(raSettingsNxRow(RaSettingsRow(RaSettingKind.ENABLED, "Enable achievements", "On")).cycleable)
        assertFalse(raSettingsNxRow(RaSettingsRow(RaSettingKind.ENABLED, "Enable achievements", "Off", enabled = false)).cycleable)
        assertEquals(NxRow("Status", enabled = false, secondary = "x"), raSettingsNxRow(RaSettingsRow(RaSettingKind.STATUS, "Status", "x", enabled = false)))
    }

    @Test fun emulatorValueRowsCycle() {
        val layout = ConsoleRow(PadOptions.all.first { it.key == PadOptions.LAYOUT }, PadOptions.AUTO)
        val opt = CoreOption("opt_a", "x", label = "Option A", values = listOf("x", "y"), defaultValue = "x")
        assertTrue(emulatorNxRow(EmulatorRow.Console(layout)).cycleable)
        assertTrue(emulatorNxRow(EmulatorRow.Core(opt, inCategory = false)).cycleable)
        assertFalse(emulatorNxRow(EmulatorRow.Category("video", "Video")).cycleable)
        assertFalse(emulatorNxRow(EmulatorRow.Header("Console settings")).cycleable)
    }

    private fun folder(tag: String, games: Int = 1, unassigned: Boolean = false) = SystemFolder(
        tag, tag, CoreRegistry.forTag(tag),
        (1..games).map { GameEntry(ContentRef("r/$tag$it"), "$tag$it", "$tag$it", folder = ContentRef("r"), sourceId = "home", relativeDir = "") },
        unassigned = unassigned,
    )

    @Test fun defaultEmulatorsListMultiEmulatorConsolesFirstThenTheRestEachByName() {
        val systems = listOf(folder("SMS"), folder("GG"), folder("32X"), folder("MD"), folder("PSP", games = 0), folder("", unassigned = true))
        val rows = defaultEmulatorRows(systems, mapOf("sfc" to "SUPA", "sms" to "GPGX_SMS"))
        assertEquals(
            listOf(
                "Game Boy Advance" to "gpSP (GBA)", "Sega CD" to "PicoDrive (SEGACD)",
                "Sega Game Gear" to "PicoDrive (GG)", "Sega Genesis" to "PicoDrive (MD)",
                "Sega Master System" to "Genesis Plus GX (GPGX)",   // GPGX_SMS shows as the handheld's GPGX
                "Sega SG-1000" to "PicoDrive (SG1000)", "Sony PlayStation" to "PCSX-ReARMed (PS)", "Super Nintendo ES" to "Supafaust (SUPA)",
                "Sega 32X" to "PicoDrive (32X)",   // single-emulator consoles follow, by name
            ),
            rows.map { it.label to it.secondary },
        )
        // A single emulator is read-only (dimmed, no arrows); a console with a choice cycles even without games.
        assertEquals(listOf(true, true, true, true, true, true, true, true, false), rows.map { it.enabled })
        assertTrue(rows.all { it.kind == SettingsKind.DEFAULT_TAG })
        assertFalse(settingsNxRow(rows[8]).cycleable)
        assertTrue(settingsNxRow(rows[0]).cycleable)
        assertEquals(listOf("gba", "segacd", "gg", "md", "sms", "sg1000", "ps", "sfc", "32x"), rows.map { it.consoleId })
    }

    @Test fun defaultEmulatorsShowTheBuiltInTagForAStaleStoredOne() {
        assertEquals("gpSP (GBA)", defaultEmulatorRows(emptyList(), mapOf("gba" to "NOPE")).first { it.consoleId == "gba" }.secondary)
        assertEquals("PicoDrive (MD)", defaultEmulatorRows(emptyList(), mapOf("md" to "NOPE")).single { it.consoleId == "md" }.secondary)
    }

    @Test fun genesisDefaultsToPicoDriveAndCanSwitchToGenesisPlusGx() {
        val md = defaultEmulatorRows(emptyList(), emptyMap()).single { it.consoleId == "md" }   // two tags: listed without games
        assertEquals("PicoDrive (MD)", md.secondary)
        assertTrue(md.enabled)
        assertEquals("Genesis Plus GX (GPGX)", defaultEmulatorRows(emptyList(), mapOf("md" to "GPGX")).single { it.consoleId == "md" }.secondary)
    }
}
