package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.NxButton

/** A Settings row's description: its [line] under the name (at most two lines), and a fuller [detail] for the info popup (empty: the line is all). */
internal data class SettingText(val line: String, val detail: String = "")

/** A compact row: no line, [text] (or a fuller [detail]) behind the info icon only. */
private fun quiet(text: String, detail: String = "") = SettingText("", detail.ifEmpty { text })

/** Reset to defaults' line on each page that has one, and the fuller text behind its info icon. */
private fun resetText(section: SettingsSection?): SettingText = when (section) {
    SettingsSection.APPEARANCE -> SettingText("Resets every setting on this page", "Put Pad theme, Accent, Secondary accent and Text size back")
    SettingsSection.MAIN_MENU -> SettingText("Resets every setting on this page", "Put every Layouts group and Extra info back to their defaults")
    SettingsSection.CONTROLS -> SettingText("Resets this page, pad layouts included", "Put vibration, Menu pad and every pad layout back to defaults")
    else -> SettingText("Put this page back to its defaults")
}

/**
 * [row]'s description on its touch row ([settingsTouchRow]), from the user guide's Settings pages: a short line only
 * for a status, a consequence or a name that needs it, with the guide's fuller text behind the info icon; every other
 * row is compact, its text behind the icon ([quiet]). [section]: the page it is on (Reset to defaults'
 * line names that page's settings). Empty for a row that needs none (About's, a console's default emulator).
 */
internal fun settingsInfo(row: SettingsRow, section: SettingsSection? = null): SettingText = when (row.kind) {
    // Emulators.
    SettingsKind.DEFAULTS -> SettingText("The emulator each console's games run on")
    SettingsKind.EMULATORS -> SettingText("Each emulator's options for a whole console")
    SettingsKind.PAUSE_UNFOCUSED -> quiet(
        "Pause a game while another app takes over the screen",
        "Pauses a game when another app takes over while the game is still on screen: in split screen, in a desktop " +
            "window such as Samsung DeX, or with an app open on a second screen. Go back to the game and it plays on by " +
            "itself. Turn it off to keep a game running while you use another app beside it. The game panel, the DS or 3DS " +
            "bottom screen and the in-game menu never pause it; leaving the app always does.",
    )
    SettingsKind.DEFAULT_TAG -> SettingText("")   // a single-emulator console says nothing: its emulator name is all there is
    // Library's Saves row, then the Saves page.
    SettingsKind.SAVES -> quiet("Save file formats, and export or import your saves")
    SettingsKind.SAVE_FORMAT -> quiet(
        "How battery saves in Saves/<TAG>/ are named",
        "The format of battery saves in Saves/<TAG>/: MinUI (.sav), Retroarch compressed or uncompressed (.srm), or " +
            "Generic. Retroarch (uncompressed), the default, is directly compatible with RetroArch on other devices.",
    )
    SettingsKind.STATE_FORMAT -> quiet(
        "How save states are named",
        "The naming and format of save states: MinUI (.st0…), or Retroarch-ish / Retroarch (.state, .state1…), each " +
            "compressed or uncompressed. The default is Retroarch (uncompressed).",
    )
    SettingsKind.EXTRACTED_NAME -> SettingText(
        "Zipped games: changes which saves they find",
        "For zipped games, saves and states take the extracted file's name instead of the archive's. This changes which " +
            "saves and states a game finds.",
    )
    SettingsKind.EXPORT_SAVES -> quiet("Write one zip of your saves, states and slot pictures")
    SettingsKind.IMPORT_SAVES -> quiet("Copy saves in from a folder, a zip file or RetroArch")
    // Second screen.
    SettingsKind.SECOND_SCREEN_ENABLE -> SettingText(
        "Off leaves it free for another app",
        "Off leaves the second screen alone, free for another app. DS and 3DS games then use their usual single-screen " +
            "layouts and other games show no panel. The game panel's Second screen shortcut changes the same setting " +
            "without leaving the game.",
    )
    SettingsKind.BOTTOM_SCREEN -> SettingText(
        "Which screen is the touch screen",
        "Which screen is the touch screen. Auto picks by shape. Each console can override it with its own Bottom screen " +
            "row in its Console Settings.",
    )
    // Appearance.
    SettingsKind.PAD_THEME -> quiet(
        "Colours of the on-screen pad in portrait",
        "The colours of the on-screen pad in portrait, in the menus and in games. Charcoal is the dark pad; Retro has a " +
            "light grey body with magenta face buttons. The landscape button clusters always stay Charcoal.",
    )
    SettingsKind.ACCENT -> quiet(
        "The selection colour: the selected row, tile or card",
        "The selection colour: the highlight on the selected row, tile or card, and the line under the current tab. It " +
            "also colours Home's Pick a game picture and the in-game menu.",
    )
    SettingsKind.SECONDARY_ACCENT -> quiet(
        "Switches that are on, and the bar behind a selected value",
        "The second colour: a switch that is on, and the bar behind a selected setting's value. Auto uses a darker " +
            "shade of the Accent (with the White accent, Teal).",
    )
    SettingsKind.TEXT_SIZE -> quiet(
        "Size of the text across the app",
        "The size of the text across the app: every list, the Settings pages and the in-game menu, the page titles, the " +
            "tab names and the hint bar. Small is three quarters of Large, with more rows per screen. The text also " +
            "follows Android's own font size setting.",
    )
    SettingsKind.RESET_DEFAULTS -> resetText(section)
    // Layouts.
    SettingsKind.LAYOUT_HOME -> quiet("Home tab, its layout, shelves and stats")
    SettingsKind.LAYOUT_CONSOLES -> quiet("Consoles tab, its layout, orientation and background")
    SettingsKind.LAYOUT_COLLECTIONS -> quiet("Collections tab, its layout and orientation")
    SettingsKind.LAYOUT_TOOLS -> quiet("Tools tab, its layout and orientation")
    SettingsKind.LAYOUT_GAME_LISTS -> quiet("Game lists' layout, art, icons and achievements panel")
    SettingsKind.LAYOUT_TITLE_HINTS -> quiet("The main menu's page title and button hints")
    SettingsKind.LAYOUT_SWITCHER -> quiet("How the Game Switcher shows your recent games")
    SettingsKind.EXTRA_INFO -> SettingText(
        "Play time and achievements in lists",
        "Hide removes the game stats (play time, achievements) from the main menu, the game lists and the Game " +
            "Switcher, for a cleaner look. It works in every mode. A console's or collection's game count stays, and the " +
            "in-game menu, the Game Tracker and RetroAchievements keep all their info.",
    )
    SettingsKind.TAB_SHOWN -> quiet(
        "The ${row.category?.label ?: "tab"} tab in the tab row",
        "Hide takes the tab out of the tab row. A hidden tab is still one press away: MENU on any main menu item offers " +
            "it, or touch and hold the tab row.",
    )
    SettingsKind.MENU_STYLE -> quiet("How the ${row.category?.label ?: "tab"} tab lays out its items")
    SettingsKind.SHELF_SHOWN -> when (row.shelf) {
        HomeShelf.NEW -> SettingText("Games found in the last 30 days", "Home's shelf of games found in the last 30 days")
        HomeShelf.NOT_STARTED -> quiet("Home's shelf of games you have never played")
        else -> SettingText("")
    }
    SettingsKind.HOME_STATS -> SettingText(
        "This month and Most played",
        "Hide removes Home's stats strip (This month, Most played) in every Home layout; its space goes to the top " +
            "section. The cards keep their play time and achievements: Extra info hides those.",
    )
    SettingsKind.MENU_ORIENTATION -> quiet("A Carousel scrolls across, or up and down")
    SettingsKind.CONTROLLER_ART -> quiet(
        "What shows with the consoles: controller, background or none",
        "What shows with the consoles in the List and Carousel styles. Controller: each console's controller. " +
            "Background: the console's own picture (Roms/<console>/.media/bg.png) fills the screen. None: plain black.",
    )
    SettingsKind.GAME_LIST_STYLE -> quiet("How a game list shows its games")
    SettingsKind.GAME_LIST_ORIENTATION -> quiet("A Carousel or Backdrop scrolls across, or up and down")
    SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT -> quiet("The side a vertical game list stands on")
    SettingsKind.LIST_ART -> quiet(
        "The picture a List game list shows",
        "Screenshot fills the background with the game's screenshot; Mix, 3D box art, 2D box art or Wheel show that " +
            "picture whole, in a box on the right; None shows no picture. A game without the picture shows its " +
            "screenshot instead.",
    )
    SettingsKind.BACKDROP_ART -> quiet("The box in a Backdrop row; the background stays the screenshot")
    SettingsKind.LAYOUT_OPTIONS -> SettingText(
        "The Layout icon in game list titles",
        "The Layout icon opens a dropdown of every game list layout; picking one sets the game lists' rows here. Hidden, " +
            "change the layout here instead.",
    )
    SettingsKind.ACHIEVEMENTS_PANEL -> SettingText(
        "Trophy icon and X hint in game lists",
        "Hide removes a game list's trophy icon and the X Achievements hint. X still opens the panel, and the game's " +
            "MENU menu gains an Achievements item. They show only while signed in to RetroAchievements.",
    )
    SettingsKind.PAGE_TITLE -> SettingText(
        "Tab row and game list titles",
        "Hide removes the main menu's tab row and a game list's title; L1 / R1 still switch tabs. It takes effect while " +
            "a controller is connected or the portrait pad shows: with touch alone the tab row stays.",
    )
    SettingsKind.BUTTON_HINTS -> quiet(
        "The hint bar along the main menu and game lists",
        "Hide removes the hint bar along the bottom of the main menu and the game lists; Settings and the in-game menu " +
            "keep theirs. With touch alone there is no hint bar at all.",
    )
    SettingsKind.HINT_SHOWN -> when (row.hint) {
        NxButton.SELECT -> quiet("SELECT's Recent hint; hidden, Recent becomes an icon")
        NxButton.START -> quiet("START's Search hint; hidden, Search becomes an icon")
        NxButton.Y -> quiet("Y's Netplay hint; hidden, Netplay leads a game's menu")
        else -> SettingText("")
    }
    SettingsKind.SWITCHER_LAYOUT -> quiet(
        "One game at a time full screen, or a list of recent games",
        "Full screen: one game at a time, its picture filling the screen; LEFT / RIGHT move between games. List: the " +
            "games as a List-style game list titled Recent; UP / DOWN move between games.",
    )
    // Controls.
    SettingsKind.PAD_VIBRATION -> quiet(
        "A short tick when you press the on-screen pad",
        "A short tick when you press the on-screen pad. A controller never makes the phone tick. It follows Android's " +
            "touch feedback setting: with that off, the pad stays silent.",
    )
    SettingsKind.GAME_RUMBLE -> quiet(
        "Strength of the game's own rumble; a change plays a pulse",
        "The game's own rumble, on the controller you play with when it has a motor, else on the phone. Changing the " +
            "level plays a short pulse at that strength. In a multiplayer game it sets every player's strength.",
    )
    SettingsKind.MENU_PAD -> SettingText(
        "The pad under the menus; games keep theirs",
        "Off takes the pad away: the main menu, the game lists and the Game Switcher then run to the bottom of the " +
            "screen, used by touch or a controller. Games keep their pad.",
    )
    SettingsKind.PAD_LAYOUT -> quiet("Move and resize the on-screen pad's buttons")
    SettingsKind.PAD_LAYOUT_CONSOLE -> SettingText(
        "Whose layout you edit",
        "Shared is the layout every console uses. A console you give a layout of its own uses that instead. Each screen " +
            "size keeps its own layouts; the page edits the screen you are on.",
    )
    SettingsKind.PAD_LAYOUT_SHAPE -> quiet(
        if (row.padShape == PadShape.OVERLAY) "Edit the button clusters over a landscape game" else "Edit the pad below the screen in portrait",
    )
    SettingsKind.PAD_LAYOUT_RESET -> SettingText("Resets its layouts on this screen", "Put the chosen console's layouts on this screen back")
    // System's Launcher and Developer groups.
    SettingsKind.HOME_SCREEN -> quiet(
        "Make the app your Android home screen",
        "On, Android asks whether to make NX Redux your home app; Home then brings you back to the main menu's Home " +
            "tab. Off opens Android's home settings, so you can pick your old launcher again.",
    )
    SettingsKind.ANDROID_GAMES -> quiet("Pick the apps in the Android games console")
    // Library (the home, extras and game rows describe themselves: [settingsTouchRow]).
    SettingsKind.ALL_FILES -> SettingText(row.note.orEmpty(), "Lets cores and saves use your home folder's real path")   // a line only for the warning
    SettingsKind.ADD_EXTRA -> quiet("Scan another folder for games too, such as one on an SD card")
    // System's About group: what each read-only value is, and the two actions.
    SettingsKind.INFO -> SettingText("")   // System's About values read alone, compact (no description)
    SettingsKind.COPY_INFO -> quiet("Put the version, build and device on the clipboard, for a bug report")
    SettingsKind.APP_UPDATE -> quiet("Check for a new version and install it")
    SettingsKind.DEBUG_LOG -> quiet(
        "Detailed logs in Android's system log, for a bug report",
        "Writes the app's and the emulators' detailed logs to Android's system log, for a bug report. Off, only errors " +
            "are logged. It takes effect at once.",
    )
    SettingsKind.LICENSES_LINK -> quiet(row.secondary.orEmpty())   // the URL behind the icon; A opens it
    else -> SettingText("")
}

