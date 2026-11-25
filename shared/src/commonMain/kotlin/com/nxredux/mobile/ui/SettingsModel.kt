package com.nxredux.mobile.ui

import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.SystemRole
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.NxButton

enum class SettingsKind { PLAYER_NAME, HOME, ADD_EXTRA, EXTRA, RESCAN, HIDDEN, UNASSIGNED, EMULATORS, DEFAULTS, DEFAULT_TAG, PAD_SKIN, PAD_THEME, MENU_STYLE, GAME_LIST_STYLE, MENU_ORIENTATION, GAME_LIST_ORIENTATION, GAME_LIST_VERTICAL_ALIGNMENT, CONTROLLER_ART, TAB_SHOWN, SHELF_SHOWN, HINT_SHOWN, LIST_ART, BACKDROP_ART, LAYOUT_OPTIONS, ACHIEVEMENTS_PANEL, PAGE_TITLE, BUTTON_HINTS, EXTRA_INFO, GAME_INFO, HOME_STATS, SWITCHER_LAYOUT, PAD_VIBRATION, GAME_RUMBLE, ACCENT, SECONDARY_ACCENT, INFO, HOME_SCREEN, ANDROID_GAMES, COPY_INFO, BUTTON_LAYOUT, BUTTON_LAYOUT_CONSOLE, BUTTON_LAYOUT_ORIENTATION, BUTTON_LAYOUT_WII_PAD, BUTTON_LAYOUT_N3DS_PAD, BUTTON_LAYOUT_SEGA_PAD, BUTTON_LAYOUT_RESET, DEBUG_LOG, CLEAR_LINK_ROMS, CABLE_LINK_DELAY, CABLE_LINK_DELAY_OVERLAY, SECOND_SCREEN_ENABLE, BOTTOM_SCREEN, PAUSE_UNFOCUSED, SCREEN_CARE, SAVE_ON_QUIT, A_RESUMES, GPU_DRIVER, SAVES, SAVE_FORMAT, STATE_FORMAT, EXTRACTED_NAME, EXPORT_SAVES, IMPORT_SAVES, IMPORT_FOLDER, IMPORT_ZIP, IMPORT_RETROARCH, IMPORT_DONE, IMPORT_BIOS, IMPORT_BIOS_FOLDER, IMPORT_BIOS_FILES, IMPORT_BIOS_ZIP, IMPORT_BIOS_RETROARCH, RESET_DEFAULTS, ALL_FILES, MIC_ACCESS, APP_UPDATE, KEYBOARD,
    // Game Settings: the game's own badge ([GameBadgeEditor]).
    GAME_BADGE,
    // Controls -> Shortcuts: its opener, an action's press-to-bind row, a touch button's action.
    SHORTCUTS, SHORTCUT_BIND, SHORTCUT_TOUCH,
    // Controls -> System buttons: its opener and a role's press-to-bind row (SELECT, START, MENU).
    SYSTEM_BUTTONS, SYSTEM_BUTTON_BIND,
    // Emulation -> GPU driver ([GPU_DRIVER] opens it): a driver to use (System driver or an installed one), and Add driver.
    GPU_DRIVER_ITEM, GPU_DRIVER_ADD,
    LAYOUT_HOME, LAYOUT_GAMES_TAB, LAYOUT_CONSOLES, LAYOUT_COLLECTIONS, LAYOUT_TOOLS, LAYOUT_GAME_LISTS, LAYOUT_TITLE_HINTS, LAYOUT_SWITCHER, TEXT_SIZE,
    // A group's label and the space before it (System's General, Launcher, Developer, About, Licenses): never selected.
    SECTION, GAP,
    // System's Licenses group: a paragraph (never selected) and the row that opens the website's full list.
    TEXT, LICENSES_LINK,
    // Settings -> Kids Mode: the page itself, On / Off, Change PIN, a day's play time, Allowed apps, the two missing
    // permissions' warnings and Forgot PIN?.
    KIDS_MODE, KIDS_ENABLE, KIDS_PIN, KIDS_DAY, KIDS_APPS, KIDS_GUARD, KIDS_ALARMS, KIDS_HOME, KIDS_FORGOT }

/**
 * A Settings page. [parent] null: a section of the Tools -> Settings menu, in this order; else a sub-page of that
 * section, opened from its [opener] row, B going back there. Layouts ([MAIN_MENU]) is a section of its own right after
 * Appearance, as NX Redux's main page category.
 */
enum class SettingsSection(val label: String, val parent: SettingsSection? = null, val opener: SettingsKind? = null) {
    LIBRARY("Library"), EMULATORS("Emulation"), APPEARANCE("Appearance"), MAIN_MENU("Layouts"), CONTROLS("Controls"),
    // Only while a second screen is connected: Enable and Bottom screen.
    SECOND_SCREEN("Second screen"),
    // General, Launcher (with a launcher), Developer and About groups on one page; the last category.
    SYSTEM("System"),
    // Kids Mode: only where the platform has it ([AppState.settingsSections]); a parent's page (Off or Parent mode).
    KIDS("Kids Mode", opener = SettingsKind.KIDS_MODE),
    DEFAULT_EMULATORS("Default emulators", EMULATORS, SettingsKind.DEFAULTS),
    BUTTON_LAYOUT("Button layout", APPEARANCE, SettingsKind.BUTTON_LAYOUT), SAVES("Saves", LIBRARY, SettingsKind.SAVES),
    SHORTCUTS("Shortcuts", CONTROLS, SettingsKind.SHORTCUTS),
    SYSTEM_BUTTONS("System buttons", CONTROLS, SettingsKind.SYSTEM_BUTTONS),
    GPU_DRIVER("GPU driver", EMULATORS, SettingsKind.GPU_DRIVER),
    IMPORT_SAVES("Import saves", SAVES, SettingsKind.IMPORT_SAVES),
    // An import's results; Done and B go back to Saves ([AppState.leaveSubPage]), past Import saves.
    IMPORT_RESULT("Import results", IMPORT_SAVES),
    IMPORT_BIOS("Import BIOS", LIBRARY, SettingsKind.IMPORT_BIOS),
    // Layouts' groups: a tab's options, the game lists', the page title's and hints', the Game Switcher's.
    LAYOUT_HOME("Home", MAIN_MENU, SettingsKind.LAYOUT_HOME), LAYOUT_GAMES_TAB("Games", MAIN_MENU, SettingsKind.LAYOUT_GAMES_TAB),
    LAYOUT_CONSOLES("Consoles", MAIN_MENU, SettingsKind.LAYOUT_CONSOLES),
    LAYOUT_COLLECTIONS("Collections", MAIN_MENU, SettingsKind.LAYOUT_COLLECTIONS), LAYOUT_TOOLS("Tools", MAIN_MENU, SettingsKind.LAYOUT_TOOLS),
    LAYOUT_GAME_LISTS("Game lists", MAIN_MENU, SettingsKind.LAYOUT_GAME_LISTS), LAYOUT_TITLE_HINTS("Title and hints", MAIN_MENU, SettingsKind.LAYOUT_TITLE_HINTS),
    LAYOUT_SWITCHER("Game Switcher", MAIN_MENU, SettingsKind.LAYOUT_SWITCHER);

    /** A Layouts group: its value rows above a picture of them ([Screen.Settings.shot]); B goes back. */
    val layoutGroup: Boolean get() = parent == MAIN_MENU

    /** A Layouts group's tab (Home, Consoles, Collections, Tools); null for the others. */
    val tab: MenuCategory? get() = when (this) {
        LAYOUT_HOME -> MenuCategory.HOME
        LAYOUT_GAMES_TAB -> MenuCategory.GAMES
        LAYOUT_CONSOLES -> MenuCategory.CONSOLES
        LAYOUT_COLLECTIONS -> MenuCategory.COLLECTIONS
        LAYOUT_TOOLS -> MenuCategory.TOOLS
        else -> null
    }

    /** Library or one of its sub-pages: the pages whose rows follow the index. */
    val library: Boolean get() = this == LIBRARY || parent == LIBRARY

    /** Emulators or one of its sub-pages (Default emulators): a changed default tag leaves the index stale. */
    val emulators: Boolean get() = this == EMULATORS || parent == EMULATORS

    /** Leaving it may reload the library (the index stale): Library, Emulators and their sub-pages. */
    val reloads: Boolean get() = library || emulators

    /** Drawn with the detail lines above its rows: Library (its counts and last scan) and Default emulators; not Saves (its banner). */
    val detailed: Boolean get() = this == LIBRARY || this == DEFAULT_EMULATORS

    /**
     * Every row opens a page or runs an action (a count or state as its secondary), none holds a value: drawn as list
     * rows at the list size, as the RetroAchievements page: Emulators. A page with a value row
     * (a toggle or a cycled setting, About's read-only values) keeps the options rows for all its rows, the navigation
     * ones too (System's Use as home screen), so one page never mixes the two sizes.
     */
    val navigation: Boolean get() = this == EMULATORS
}

/** The last row of Appearance, Layouts and Controls (NX Redux's label; its description has no place on a Settings page here). */
internal const val RESET_DEFAULTS_LABEL = "Reset to defaults"

internal fun onOff(on: Boolean) = if (on) "On" else "Off"

internal fun showHide(on: Boolean) = if (on) "Show" else "Hide"

/** A main menu tab's or the game list's style, as the Appearance rows show it. */
internal fun menuStyleLabel(style: MenuStyle): String = when (style) {
    MenuStyle.LIST -> "List"
    MenuStyle.GRID -> "Grid"
    MenuStyle.CAROUSEL -> "Carousel"
    MenuStyle.BACKDROP -> "Backdrop"
    MenuStyle.MINIMAL -> "Minimal"
}

/** A Carousel's or a Backdrop's orientation, as its Orientation row shows it. */
internal fun orientationLabel(o: MenuOrientation): String = when (o) {
    MenuOrientation.HORIZONTAL -> "Horizontal"
    MenuOrientation.VERTICAL -> "Vertical"
}

internal fun verticalAlignmentLabel(a: VerticalListAlignment): String = when (a) {
    VerticalListAlignment.LEFT -> "Left"
    VerticalListAlignment.RIGHT -> "Right"
}

/**
 * A touch value row's picker ([AppState.chooser], the in-game menu's look): the row's [title], every value's [labels],
 * the d-pad cursor [selected] and the value set [current] (-1: none).
 */
data class ValueChooser(val title: String, val labels: List<String>, val selected: Int, val current: Int = -1)

/** Use as home screen's values: it is not a [SettingValue] (Android answers it). */
internal val ON_OFF = listOf("Off", "On")

/** A Settings value row's values as its touch control shows them: every value's [labels] in LEFT / RIGHT order, the current [index]. */
data class SettingChoices(val labels: List<String>, val index: Int)

/** A Settings row's full description over the page ([AppState.settingInfo]); [scrollSeq] / [scrollDir] count UP/DOWN steps. */
data class SettingInfoPopup(val title: String, val text: String, val scrollSeq: Int = 0, val scrollDir: Int = 0)

/**
 * [enabled] false: dimmed and A does nothing (a default emulator of a one-tag console). [category]: a MENU_STYLE row's tab.
 * [warning]: its secondary is a problem (a folder not available), drawn in the warning colour. [shortcut] / [touchSlot]:
 * the action or touch button of a Shortcuts row; [note] on a bind row: it is waiting for a press, and what to do.
 */
data class SettingsRow(val kind: SettingsKind, val label: String, val secondary: String? = null, val extra: ExtraFolder? = null, val consoleId: String? = null, val enabled: Boolean = true, val category: MenuCategory? = null, val shelf: HomeShelf? = null, val hint: NxButton? = null, val landscape: Boolean? = null, val warning: Boolean = false, val note: String? = null, val shortcut: ShortcutAction? = null, val touchSlot: TouchSlot? = null, val systemRole: SystemRole? = null,
    /** A Kids Mode play-time row's ISO day. */
    val day: Int? = null,
    /** A GPU driver page row's driver: [GpuDriverChoice.SYSTEM] or an installed driver's id. */
    val gpuDriver: String? = null,
    /** The GPU driver page's driver in use: its row is marked. */
    val current: Boolean = false)

/** A row the selection lands on: not a group label, the space before one, or a paragraph. */
val SettingsRow.selectable: Boolean get() = kind != SettingsKind.SECTION && kind != SettingsKind.GAP && kind != SettingsKind.TEXT

/**
 * Settings -> Emulation -> Default emulators: every console with several tags (cycled, so one can be set before it has
 * games) and every other console [systems] hold games for (Unassigned left out), as a dimmed read-only row. The value
 * names the emulator behind the tag, "mGBA (MGBA)", so two tags on one core would still differ by tag. A stored pick
 * that is not one of the console's tags is ignored. Sorted by console name.
 */
internal fun defaultEmulatorRows(systems: List<SystemFolder>, defaultTags: Map<String, String>): List<SettingsRow> {
    val withGames = systems.filter { !it.unassigned && it.gameCount > 0 }.mapNotNullTo(HashSet()) { CoreRegistry.consoleOf(it.tag)?.id }
    // The consoles with a choice first (more than one emulator), then the single-emulator ones, each A to Z.
    return CoreRegistry.consoles.filter { it.tags.size > 1 || it.id in withGames }
        .sortedWith(compareBy({ it.tags.size <= 1 }, { it.displayName.lowercase() })).map { c ->
        val tag = c.defaultTagFor(defaultTags[c.id])
        SettingsRow(SettingsKind.DEFAULT_TAG, c.displayName, CoreRegistry.emulatorLabel(tag), consoleId = c.id, enabled = c.tags.size > 1)
    }
}
