package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.NxButton

/** A Settings row's description: its [line] under the name (at most two lines), and a fuller [detail] for the info popup (empty: the line is all). */
internal data class SettingText(val line: String, val detail: String = "")

/** A compact row: no line, [text] (or a fuller [detail]) behind the info icon only. */
private fun quiet(text: String, detail: String = "") = SettingText("", detail.ifEmpty { text })

/** Reset to defaults' line on each page that has one, and the fuller text behind its info icon. */
private fun resetText(section: SettingsSection?): SettingText = when (section) {
    SettingsSection.APPEARANCE -> SettingText("Resets every setting on this page", "Put Pad theme, Pad buttons, Accent, Secondary accent, Text size and Button layout back")
    SettingsSection.MAIN_MENU -> SettingText("Resets every setting on this page", "Put every Layouts group and Extra info back to their defaults")
    SettingsSection.CONTROLS -> SettingText("Resets every setting on this page", "Put Pad vibration and Game rumble back to their defaults")
    SettingsSection.SHORTCUTS -> SettingText("Sets every shortcut to None", "Unbind every action and take both touch buttons off the pad")
    SettingsSection.SYSTEM_BUTTONS -> SettingText("Back to this controller's own buttons", "SELECT, START and MENU go back to the buttons that send them; other controllers keep theirs")
    else -> SettingText("Put this page back to its defaults")
}

/**
 * [row]'s description on its touch row ([settingsTouchRow]), from the user guide's Settings pages: a short line only
 * for a status, a consequence or a name that needs it, with the guide's fuller text behind the info icon; every other
 * row is compact, its text behind the icon ([quiet]). [section]: the page it is on (Reset to defaults'
 * line names that page's settings). Empty for a row that needs none (About's, a console's default emulator).
 */
internal fun settingsInfo(row: SettingsRow, section: SettingsSection? = null): SettingText = when (row.kind) {
    // Emulation.
    SettingsKind.DEFAULTS -> SettingText("The emulator each console's games run on")
    SettingsKind.EMULATORS -> SettingText("Each console's settings and emulator options; a game can override them")
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
            "row under Display.",
    )
    // Appearance.
    SettingsKind.PAD_SKIN -> quiet(
        "The handheld drawn around the game, or full screen",
        "On draws the Pad theme's handheld around the game and the menus. Off runs the game full screen with see-through " +
            "buttons over it, and the menus without an on-screen pad.",
    )
    SettingsKind.PAD_THEME -> quiet(
        "The skin of the on-screen pad",
        "The skin drawn around the game and the menus: the whole handheld, its buttons and screens. NX Carbon is smoke " +
            "grey, NX Onyx and NX Obsidian black, and Plasma, Ember, Volt and Tide translucent purple, red, green and blue. " +
            "Where a skin has no layout for the window, the plain dark pad shows instead.",
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
    // Layouts' group rows: no text on the row (their pages' rows say what each group holds).
    SettingsKind.LAYOUT_HOME, SettingsKind.LAYOUT_CONSOLES, SettingsKind.LAYOUT_COLLECTIONS, SettingsKind.LAYOUT_TOOLS,
    SettingsKind.LAYOUT_GAME_LISTS, SettingsKind.LAYOUT_TITLE_HINTS, SettingsKind.LAYOUT_SWITCHER -> SettingText("")
    SettingsKind.EXTRA_INFO -> SettingText(
        "Play time and achievements for games",
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
        HomeShelf.NOT_STARTED -> SettingText("Games you have never played")   // the line says it all: no info icon
        HomeShelf.MOST_PLAYED -> SettingText("Games you have played the longest")
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
    SettingsKind.BACKDROP_ART -> quiet(
        "The box in a Backdrop row; the background stays the screenshot. In every layout it also sets the game panel's " +
            "header and the Game Switcher's box.",
    )
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
    SettingsKind.BUTTON_LAYOUT -> quiet("Move and resize the buttons, per console")
    SettingsKind.BUTTON_LAYOUT_CONSOLE -> SettingText(
        "Whose buttons you edit",
        "Each console keeps its own button layout, in portrait and in landscape. The editor shows the layout a game " +
            "would show on this screen.",
    )
    SettingsKind.BUTTON_LAYOUT_WII_PAD -> quiet("Each Wii controller has its own layout")
    SettingsKind.BUTTON_LAYOUT_N3DS_PAD -> quiet(
        "Regular 3DS or New 3DS buttons",
        "Regular 3DS and New 3DS buttons each have their own layout. New 3DS adds ZL, ZR and the C-stick; a game uses it " +
            "when its New 3DS Controls setting turns them on.",
    )
    SettingsKind.BUTTON_LAYOUT_ORIENTATION -> quiet("Edit the buttons for this orientation")
    SettingsKind.BUTTON_LAYOUT_RESET -> SettingText("Resets this console's buttons", "Put the chosen console's buttons back where the skin puts them")
    SettingsKind.SHORTCUTS -> quiet(
        "Controller buttons and touch buttons for fast forward, rewind and reset",
        "Bind a controller button or combo to fast forward, rewind or reset, and give the on-screen pad up to two touch " +
            "buttons. Every console and game uses these unless its in-game Shortcuts page overrides them. If your MENU " +
            "button does nothing, set it in Controls → System buttons.",
    )
    // While it waits for a press, its line says how to bind.
    SettingsKind.SHORTCUT_BIND -> SettingText(
        row.note.orEmpty(),
        "Press A, then the button to use. For a combo, hold MENU and press another button. B cancels, X sets it back " +
            "to None. A button another action had moves to this one. MENU alone and SELECT+START open the menu, and " +
            "SELECT and START stay the game's, so MENU alone, SELECT and START can't be bound.",
    )
    SettingsKind.SYSTEM_BUTTONS -> quiet(
        "Which buttons are SELECT, START and MENU on your controller",
        "Some controllers send no MENU, or another code for SELECT or START. Pick the button to use for each; it then " +
            "acts only as that, in the menu and in games. Each controller model keeps its own.",
    )
    // While it waits for a press, its line says how to cancel.
    SettingsKind.SYSTEM_BUTTON_BIND -> SettingText(
        row.note.orEmpty(),
        "Press A, then the button to use. B or Back cancel. A button another role had swaps with this one. Without a " +
            "MENU button, SELECT+START opens the menu.",
    )
    SettingsKind.SHORTCUT_TOUCH -> quiet(
        "What this on-screen touch button does; None hides it",
        "The action of a touch button on the on-screen pad. None, the default, leaves it off the pad. Hold actions work " +
            "while your finger stays on it, toggles flip on each tap. All four sit in the row under the pad skin's " +
            "screen (Adjust buttons in the in-game Controls moves them); over a full-screen game they show as see-through pills.",
    )
    // System's Launcher and Developer groups.
    SettingsKind.HOME_SCREEN -> quiet(
        "Make the app your Android home screen",
        "On, Android asks whether to make NX Redux your home app; Home then brings you back to the main menu's Home " +
            "tab. Off opens Android's home settings, so you can pick your old launcher again.",
    )
    SettingsKind.ANDROID_GAMES -> quiet("Pick the apps in the Android games console")
    // Library (the home, extras and game rows describe themselves: [settingsTouchRow]).
    SettingsKind.ALL_FILES -> SettingText(row.note.orEmpty(), "Lets cores and saves use your home folder's real path")   // a line only for the warning
    SettingsKind.MIC_ACCESS -> quiet("Lets DS and 3DS games hear the Mic toggle's live mic")
    SettingsKind.ADD_EXTRA -> SettingText("Scan another folder for games too, such as one on an SD card")   // under its name, as an action row reads
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
    // Settings -> Kids Mode.
    SettingsKind.KIDS_ENABLE -> quiet(
        "Big tiles of allowed apps outside play times",
        "On, games open only in the play times; outside them your kid sees big tiles of the allowed apps, and Tools, " +
            "Settings and the Apps drawer need your PIN. The first time you turn it on, a short setup asks for a PIN, the " +
            "apps, the play times and two permissions. It needs NX Redux as the home app.",
    )
    SettingsKind.KIDS_PIN -> quiet("Choose a new 6-digit PIN")
    SettingsKind.KIDS_DAY -> quiet(
        "When games open on this day",
        "The hours games open on this day, in half-hour steps, or Locked to keep them closed all day. Ten minutes " +
            "before the end your kid is told to save.",
    )
    SettingsKind.KIDS_APPS -> quiet("The apps your kid can open while games are closed", "The apps your kid can open while games are closed. $KIDS_APPS_AVOID")
    SettingsKind.KIDS_GUARD -> SettingText(
        row.secondary.orEmpty(),
        "Without Usage access, NX Redux cannot send your kid back from an app that isn't allowed. Grant it here.",
    )
    SettingsKind.KIDS_ALARMS -> SettingText(
        row.secondary.orEmpty(),
        "Without Alarms & reminders, the warning and the lock can come a few minutes late. Allow it here.",
    )
    SettingsKind.KIDS_HOME -> SettingText(
        row.secondary.orEmpty(),
        "Without NX Redux as the home app, Home leaves it and the lock can't bring your kid back from other apps. Set it here.",
    )
    SettingsKind.KIDS_FORGOT -> SettingText(
        row.secondary.orEmpty(),
        "Kids Mode keeps a file named kids-mode.flag at this path. Delete it with a file manager or from a computer and " +
            "Kids Mode turns off the next time NX Redux opens. The play times and allowed apps stay.",
    )
    else -> SettingText("")
}

