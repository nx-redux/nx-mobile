package com.nxredux.mobile.settings

import com.nxredux.mobile.runner.BottomScreen
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.SaveNaming
import com.nxredux.mobile.storage.StateFormat

/**
 * The value [by] places from [current] in this list, wrapping (LEFT -1, RIGHT and A +1): every cycled setting steps
 * through its values this way. A [current] not in the list counts as one before the first, so +1 lands on the first.
 */
fun <T> List<T>.stepFrom(current: T, by: Int): T = this[(indexOf(current) + by).mod(size)]

/** The portrait virtual pad's palette: CHARCOAL (the dark default) or RETRO (light-grey body, magenta face buttons). */
enum class PadTheme {
    CHARCOAL, RETRO;

    companion object { val DEFAULT = CHARCOAL }
}

/**
 * Controls → Pad vibration: the haptic tick the on-screen pad gives on a press (and a new d-pad direction). OFF, or a
 * LIGHT (the default), MEDIUM or STRONG tick; stored by [name] ([fromStored]).
 */
enum class PadVibration(val label: String) {
    OFF("Off"), LIGHT("Light"), MEDIUM("Medium"), STRONG("Strong");

    /** The level [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): PadVibration = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = LIGHT

        /** The level stored as [name]; missing or unknown reads as [DEFAULT]. */
        fun fromStored(name: String?): PadVibration = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Controls → Game rumble: how strongly a core's rumble (the libretro rumble interface, port 0) drives the controller in
 * use, or the phone. OFF, or LIGHT, MEDIUM (the default) or STRONG, each a felt range the platform maps a core's strength
 * into; stored by [name] ([fromStored]).
 */
enum class GameRumble(val label: String) {
    OFF("Off"), LIGHT("Light"), MEDIUM("Medium"), STRONG("Strong");

    /** The level [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): GameRumble = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = MEDIUM

        /** The level stored as [name]; missing or unknown reads as [DEFAULT]. */
        fun fromStored(name: String?): GameRumble = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}


/** A main menu tab, in the tab row's fixed order. Layouts can hide any; an empty Consoles or Collections tab is left out, and with none left Home shows. */
enum class MenuCategory { HOME, CONSOLES, COLLECTIONS, TOOLS }

/**
 * How a main menu tab, or a game list, lays out its items (Appearance -> Layouts). BACKDROP is the game lists' only
 * ([MenuStyles.MAIN_MENU_STYLES], [MenuStyles.GAME_LIST_STYLES]); MINIMAL is big names only, the selected one solid and
 * the rest outlined. Stored by name: append, never rename.
 */
enum class MenuStyle { LIST, GRID, CAROUSEL, BACKDROP, MINIMAL }

/**
 * Which way a Carousel or a Backdrop runs (Appearance -> Layouts, the Orientation row under one): HORIZONTAL, the row, or
 * VERTICAL, the row stood on end. List and Grid ignore it ([MenuStyles.effectiveOrientation]).
 */
enum class MenuOrientation { HORIZONTAL, VERTICAL }

/**
 * Which side a vertical game list's stack sits (Appearance -> Layouts, the Vertical alignment row): LEFT, the stack left
 * of centre and its caption to its right, or RIGHT, the two mirrored (the caption's text still left-aligned).
 */
enum class VerticalListAlignment {
    LEFT, RIGHT;

    companion object { val DEFAULT = LEFT }
}

/**
 * Layouts → Consoles background: what shows behind the Consoles tab (List and Carousel). CONTROLLER (the default): each
 * console's controller. BACKGROUND: no controller; the selected console's own `.media/bg.png`. NONE: neither, plain black. Stored by [name] ([fromStored]).
 */
enum class ConsoleArt(val label: String) {
    CONTROLLER("Controller"), BACKGROUND("Background"), NONE("None");

    /** The value [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): ConsoleArt = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = CONTROLLER

        /** The value stored as [name]; [DEFAULT] with none (or an unknown one). */
        fun fromStored(name: String?): ConsoleArt = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Layouts → List art: the picture behind or beside a game list in the List style. SCREENSHOT (the default): the
 * full-screen picture. MIX, BOX3D, BOX2D, WHEEL ([boxed]): that picture whole, in a box on the right. NONE: no picture.
 */
enum class GameListArt(val label: String) {
    SCREENSHOT("Screenshot"), MIX("Mix"), BOX3D("3D box art"), BOX2D("2D box art"), WHEEL("Wheel"), NONE("None");

    /** Drawn in a box on the right, the game names stopping short of it. */
    val boxed: Boolean get() = this != SCREENSHOT && this != NONE

    /** The value [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): GameListArt = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = SCREENSHOT

        /** The value stored as [name]; missing or unknown reads as [DEFAULT]. */
        fun fromStored(name: String?): GameListArt = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Layouts → Backdrop art: the box in a Backdrop row, BOX3D (the default), BOX2D or WHEEL. The background stays the screenshot. */
enum class BackdropArt(val label: String) {
    BOX3D("3D box art"), BOX2D("2D box art"), WHEEL("Wheel");

    /** The value [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): BackdropArt = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = BOX3D

        /** The value stored as [name]; missing or unknown reads as [DEFAULT]. */
        fun fromStored(name: String?): BackdropArt = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Layouts → Game Switcher layout (NX Redux PR #218): FULL (the default), one game's resume frame at a time; LIST, the
 * recent games as a game list's List, the selected one's resume frame behind it.
 */
/** Appearance → Text size: an options row's label as a share of the list label ([ofListLabel]). */
enum class OptionTextSize(val label: String, val ofListLabel: Float) {
    LARGE("Large", 0.8f), SMALL("Small", 0.6f);

    /** The value [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): OptionTextSize = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = LARGE

        /** The value stored as [name]; missing or unknown reads as [DEFAULT]. */
        fun fromStored(name: String?): OptionTextSize = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class SwitcherLayout(val label: String) {
    FULL("Full screen"), LIST("List");

    /** The value [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): SwitcherLayout = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = FULL

        /** The value stored as [name]; missing or unknown reads as [DEFAULT]. */
        fun fromStored(name: String?): SwitcherLayout = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** An extra ROM folder: [id] is `Sources.idFor` of it (stable across remove and re-add) and names it in recents, the sidecar store, the index and `Roms/.sources/`. */
data class ExtraFolder(val id: String, val ref: ContentRef)

interface AppSettings {
    /** The home folder (holds Roms/, Bios/, Saves/), or null before the user picked one. */
    var treeRef: ContentRef?
    /** Folders scanned in place with the resolution chain, in the order they were added. */
    var extraFolders: List<ExtraFolder>
    /** Console id -> tag to use for files resolved by console; missing = the console's first tag. */
    var defaultTags: Map<String, String>
    /** Palette of the portrait virtual pad (the landscape clusters always stay Charcoal). */
    var padTheme: PadTheme
    /**
     * Each main menu tab's style (List, Grid or Carousel), Home's too (Layouts → Home layout); a category missing from
     * the map is its default ([MenuStyles.defaultStyleOf]: Grid for Home and Tools, Minimal for Collections, Carousel for Consoles) and a stored
     * Backdrop reads as Carousel ([MenuStyles.styleOf]).
     */
    var menuStyles: Map<MenuCategory, MenuStyle>
    /** The style of every game list (a console's, a collection's, Recently Played); unset is [MenuStyles.DEFAULT_GAME_LIST_STYLE]. */
    var gameListStyle: MenuStyle
    /**
     * Each main menu tab's orientation, used while its style is Carousel; a category missing from the map is
     * [MenuStyles.DEFAULT_ORIENTATION] ([MenuStyles.orientationOf]). Kept while the tab is on another style.
     */
    var menuOrientations: Map<MenuCategory, MenuOrientation>
    /** The game lists' orientation, used while [gameListStyle] is Carousel or Backdrop; kept while it is List or Grid. */
    var gameListOrientation: MenuOrientation
    /** The vertical game lists' side, used while the game lists are a Vertical Carousel or Backdrop; kept otherwise. */
    var gameListVerticalAlignment: VerticalListAlignment
    /** Android apps shown in the Tools tab ([com.nxredux.mobile.launcher.InstalledApp.id]), in the order added. */
    var toolApps: List<String>
    /**
     * The Android console's members ([com.nxredux.mobile.launcher.InstalledApp.id]), in the order added: by the onboarding
     * question or Settings → Library → Android games. One no longer installed is skipped but kept.
     */
    var androidConsoleApps: List<String>
    /** The "Add N Android games to Consoles?" question was answered (Yes or Not now); it is never asked again. */
    var androidGamesAsked: Boolean
    /** Android games pinned on Home ([com.nxredux.mobile.launcher.InstalledApp.id]), in the order pinned (never shortcuts.txt). */
    var androidPins: List<String>

    // RetroAchievements (Tools → RetroAchievements → Settings). The token is the only credential kept.
    var raEnabled: Boolean
    var raUsername: String
    var raToken: String?
    var raShowNotifications: Boolean
    /** Unlock toast duration, 1..5 s. */
    var raNotificationSeconds: Int
    /** Progress pill duration, 0 (off)..5 s. */
    var raProgressSeconds: Int
    var raSortOrder: com.nxredux.mobile.achievements.RaSortOrder

    // ScreenScraper (artwork fetching): the user's optional login; "" / null = none.
    var ssUsername: String
    var ssPassword: String?
    /** SteamGridDB (artwork fetching): the user's own API key, saved only after a check; null = none (source off). */
    var sgdbApiKey: String?
    /** TheGamesDB (artwork fetching): the user's own API key, saved only after a check; null = none (source off). */
    var tgdbApiKey: String?
    /** Art settings → Replace existing art: a fetch re-downloads games that already have art (default off). */
    var artReplaceExisting: Boolean
    /** Art settings → 2D box art: a fetch also downloads the flat 2D box art beside the 3D box (default off). */
    var artBox2d: Boolean
        get() = false
        set(value) {}
    /** Art settings → Wheel: a fetch also downloads the game's logo (default off; Generate mix needs it either way). */
    var artWheel: Boolean
        get() = false
        set(value) {}
    /** Art settings → Generate mix: the device makes NX Redux's screenshot + box + wheel picture (default off; a game has art only with it). */
    var artMix: Boolean
        get() = false
        set(value) {}
    /** Layouts → Home tab, Consoles tab, Collections tab, Tools tab: Show (true, the default) or Hide. A hidden tab stays reachable from MENU. */
    var showHomeTab: Boolean
    var showConsolesTab: Boolean
    var showCollectionsTab: Boolean
    var showToolsTab: Boolean
    /** Layouts → New shelf, Not started shelf: Home shows that shelf (true, the default) or not. Pick up again always shows. */
    var showNewShelf: Boolean
    var showNotStartedShelf: Boolean
    /**
     * Layouts → Recent hint, Search hint, Netplay hint: the main menu's SELECT Recent and START Search and Y
     * Netplay show in the hint bar (true, the default) or not; the button still works hidden, and a hidden Netplay hint puts
     * Netplay in a game's context menu.
     */
    var showRecentHint: Boolean
    var showSearchHint: Boolean
    var showNetplayHint: Boolean
    /** Layouts → Consoles background ([ConsoleArt.DEFAULT] Controller). */
    var consoleArt: ConsoleArt
    /** Layouts → List art ([GameListArt.DEFAULT] Screenshot). */
    var gameListArt: GameListArt
    /** Layouts → Backdrop art ([BackdropArt.DEFAULT] 3D box art). */
    var backdropArt: BackdropArt
    /** Layouts → Game Switcher layout ([SwitcherLayout.DEFAULT] Full screen). */
    var switcherLayout: SwitcherLayout
        get() = SwitcherLayout.DEFAULT
        set(value) {}
    /** Appearance → Text size: the options lists' text ([OptionTextSize.DEFAULT] Large). */
    var optionTextSize: OptionTextSize
        get() = OptionTextSize.DEFAULT
        set(value) {}
    /**
     * Layouts → Page title: the main menu's tab row and a game list's title (default on). Hidden, it takes effect only
     * where a pad's buttons exist ([com.nxredux.mobile.ui.nx.launcherChromeFor]); L1 / R1 still switch tabs.
     */
    var menuPageTitle: Boolean
    /** Layouts → Button hints: the main menu's and game lists' hint bar (default on); hidden only where buttons exist, as [menuPageTitle]. */
    var menuButtonHints: Boolean
    /**
     * Layouts → Extra info: the launcher menus' game stats (the play time, achievements and next goal on cards, tiles,
     * captions and the List band, Home's cards too); default on. Unlike [menuPageTitle] it applies everywhere, touch only
     * or not. Console and collection game counts are not stats and stay. Home's strip follows [menuHomeStats].
     */
    var menuExtraInfo: Boolean
    /** Layouts → Home stats: Home's stats strip (its cards' info follows [menuExtraInfo]); default on. */
    var menuHomeStats: Boolean
    /**
     * Layouts → Layout options: a game list's Layout icon and its dropdown (default on). Hidden, the layout changes in
     * Layouts only.
     */
    var gameListLayoutOptions: Boolean
    /**
     * Layouts → Achievements panel: a game list's trophy icon and X's hint (default on). Hidden, X still opens
     * the panel and a game's menu offers it.
     */
    var gameListAchievementsPanel: Boolean
    /** Controls → Pad vibration: the on-screen pad's press tick ([PadVibration.DEFAULT] Light). */
    var padVibration: PadVibration
    /**
     * Controls → Pad layout: every stored pad layout, console id (or [PadLayouts.SHARED_KEY]) → slot → layout. Empty:
     * the default pad everywhere. Defaulted so a platform or fake without it stores nothing.
     */
    var padLayouts: PadLayoutMap
        get() = emptyMap()
        set(@Suppress("UNUSED_PARAMETER") value) {}
    /**
     * Settings → Controls → Menu pad (default on): off, portrait's main menu drops its bottom on-screen pad and the screen
     * runs to the bottom (the menu is touch-driven). Games keep their own pad. Defaulted so a platform or fake without it
     * stores nothing.
     */
    var menuPad: Boolean
        get() = true
        set(@Suppress("UNUSED_PARAMETER") value) {}
    /**
     * Settings → System → Debug log (default off): off, only errors reach logcat. Defaulted so a platform or fake
     * without it stores nothing.
     */
    var debugLog: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /** Settings → Emulators → Second screen (default on): a built-in or wired second screen shows part of the frame. Defaulted, like [debugLog]. */
    var secondScreen: Boolean
        get() = true
        set(@Suppress("UNUSED_PARAMETER") value) {}
    /**
     * Settings → Emulators → Bottom screen (default Auto): which display is the touch screen; a console's own choice wins.
     * Defaulted, like [debugLog].
     */
    var bottomScreen: BottomScreen
        get() = BottomScreen.AUTO
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /**
     * Settings → Emulators → Pause when unfocused (default on): a game whose window loses the focus to another app (split
     * screen, a freeform window, an app on another display) pauses until it is focused again. Defaulted, like [debugLog].
     */
    var pauseWhenUnfocused: Boolean
        get() = true
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /**
     * First setup's All files access step was answered (Allow or Skip); it is never shown again. Defaulted, like
     * [debugLog].
     */
    var allFilesAsked: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) {}
    /**
     * First-launch onboarding was finished or skipped (true), is to run (false), or was never decided (null): an install
     * from before onboarding, which [com.nxredux.mobile.ui.AppState.load] settles from whether it has a home folder.
     * Defaulted, like [debugLog].
     */
    var onboardingDone: Boolean?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
    /** The onboarding step reached ([com.nxredux.mobile.ui.OnboardingStep.name]), to resume at after the app was killed. */
    var onboardingStep: String?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
    /** Settings → Library → Saves, NX Redux's three save-naming settings. Defaulted, like [debugLog]. */
    var saveFormat: SaveFormat
        get() = SaveFormat.DEFAULT
        set(@Suppress("UNUSED_PARAMETER") value) {}
    var stateFormat: StateFormat
        get() = StateFormat.DEFAULT
        set(@Suppress("UNUSED_PARAMETER") value) {}
    var useExtractedFileName: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) {}
    /** The three as one value, read by the game runner at launch. */
    val saveNaming: SaveNaming
        get() = SaveNaming(saveFormat, stateFormat, useExtractedFileName)
    /**
     * The cores whose Vulkan could not start on this phone under this app version
     * ([com.nxredux.mobile.options.VulkanFallback]); the game screen records them. Defaulted: none.
     */
    val vulkanFailedCores: Set<String>
        get() = emptySet()
    /** Controls → Game rumble: a core's rumble on the controller in use or the phone ([GameRumble.DEFAULT] Medium). */
    var gameRumble: GameRumble
    /** Appearance → Accent: the selection colour; unset or unknown is [AccentPreset.DEFAULT] (White). */
    var accent: AccentPreset
    /** Appearance → Secondary accent: the bar behind a selected option's value; unset or unknown is [SecondaryAccent.DEFAULT] (Teal). */
    var secondaryAccent: SecondaryAccent
}

/** Opens the platform folder picker; the host passes the result to AppState.setHome or AppState.addExtra. */
interface FolderPicker {
    fun pickHome()
    fun pickExtra()
    /** Releases whatever grant the platform holds on [ref] (after the user removed an extra folder). */
    fun forget(ref: ContentRef)
}
