package com.nxredux.mobile.settings

import android.content.Context
import androidx.core.content.edit
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.options.VulkanFallback
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.runner.BottomScreen
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import com.swordfish.libretrodroid.NxLog

class AndroidAppSettings(context: Context) : AppSettings {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var treeRef: ContentRef?
        get() = prefs.getString(KEY_TREE, null)?.let(::ContentRef)
        set(value) = prefs.edit { if (value == null) remove(KEY_TREE) else putString(KEY_TREE, value.value) }

    override var extraFolders: List<ExtraFolder>
        get() = prefs.getString(KEY_EXTRAS, "")!!.lineSequence().filter { it.isNotBlank() }
            .mapNotNull { line -> line.indexOf('\t').takeIf { it > 0 }?.let { ExtraFolder(line.substring(0, it), ContentRef(line.substring(it + 1))) } }
            .toList()
        set(value) = prefs.edit { putString(KEY_EXTRAS, value.joinToString("\n") { "${it.id}\t${it.ref.value}" }) }

    override var defaultTags: Map<String, String>
        get() = prefs.getString(KEY_DEFAULT_TAGS, "")!!.lineSequence().filter { it.isNotBlank() }
            .mapNotNull { line -> line.indexOf('\t').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
            .toMap()
        set(value) = prefs.edit { putString(KEY_DEFAULT_TAGS, value.entries.joinToString("\n") { "${it.key}\t${it.value}" }) }

    override var padTheme: PadTheme
        get() = prefs.getString(KEY_PAD_THEME, null)?.let { runCatching { PadTheme.valueOf(it) }.getOrNull() } ?: PadTheme.DEFAULT
        set(value) = prefs.edit { putString(KEY_PAD_THEME, value.name) }

    override var padButtons: PadButtons
        get() = PadButtons.fromStored(prefs.getString(KEY_PAD_BUTTONS, null))
        set(value) = prefs.edit { putString(KEY_PAD_BUTTONS, value.name) }

    override var menuStyles: Map<MenuCategory, MenuStyle>
        get() = MenuStyles.decode(prefs.getString(KEY_MENU_STYLES, null))
        set(value) = prefs.edit { putString(KEY_MENU_STYLES, MenuStyles.encode(value)) }

    override var gameListStyle: MenuStyle
        get() = prefs.getString(KEY_GAME_LIST_STYLE, null)?.let { runCatching { MenuStyle.valueOf(it) }.getOrNull() }?.takeIf { it in MenuStyles.GAME_LIST_STYLES } ?: MenuStyles.DEFAULT_GAME_LIST_STYLE
        set(value) = prefs.edit { putString(KEY_GAME_LIST_STYLE, value.name) }

    override var menuOrientations: Map<MenuCategory, MenuOrientation>
        get() = MenuStyles.decodeOrientations(prefs.getString(KEY_MENU_ORIENTATIONS, null))
        set(value) = prefs.edit { putString(KEY_MENU_ORIENTATIONS, MenuStyles.encodeOrientations(value)) }

    override var gameListOrientation: MenuOrientation
        get() = prefs.getString(KEY_GAME_LIST_ORIENTATION, null)?.let { runCatching { MenuOrientation.valueOf(it) }.getOrNull() } ?: MenuStyles.DEFAULT_ORIENTATION
        set(value) = prefs.edit { putString(KEY_GAME_LIST_ORIENTATION, value.name) }

    override var gameListVerticalAlignment: VerticalListAlignment
        get() = prefs.getString(KEY_GAME_LIST_VERTICAL_ALIGNMENT, null)?.let { runCatching { VerticalListAlignment.valueOf(it) }.getOrNull() } ?: VerticalListAlignment.DEFAULT
        set(value) = prefs.edit { putString(KEY_GAME_LIST_VERTICAL_ALIGNMENT, value.name) }

    override var toolApps: List<String>
        get() = prefs.getString(KEY_TOOL_APPS, "")!!.lineSequence().filter { it.isNotBlank() }.toList()
        set(value) = prefs.edit { putString(KEY_TOOL_APPS, value.joinToString("\n")) }

    override var androidConsoleApps: List<String>
        get() = prefs.getString(KEY_ANDROID_CONSOLE_APPS, "")!!.lineSequence().filter { it.isNotBlank() }.toList()
        set(value) = prefs.edit { putString(KEY_ANDROID_CONSOLE_APPS, value.joinToString("\n")) }

    override var androidGamesAsked: Boolean
        get() = prefs.getBoolean(KEY_ANDROID_GAMES_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_ANDROID_GAMES_ASKED, value) }
    override var micAsked: Boolean
        get() = prefs.getBoolean(KEY_MIC_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_MIC_ASKED, value) }

    override var allFilesAsked: Boolean
        get() = prefs.getBoolean(KEY_ALL_FILES_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_ALL_FILES_ASKED, value) }

    override var onboardingDone: Boolean?
        get() = if (prefs.contains(KEY_ONBOARDING_DONE)) prefs.getBoolean(KEY_ONBOARDING_DONE, false) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_ONBOARDING_DONE) else putBoolean(KEY_ONBOARDING_DONE, value) }

    override var onboardingStep: String?
        get() = prefs.getString(KEY_ONBOARDING_STEP, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_ONBOARDING_STEP) else putString(KEY_ONBOARDING_STEP, value) }

    override var androidPins: List<String>
        get() = prefs.getString(KEY_ANDROID_PINS, "")!!.lineSequence().filter { it.isNotBlank() }.toList()
        set(value) = prefs.edit { putString(KEY_ANDROID_PINS, value.joinToString("\n")) }

    override var raEnabled: Boolean
        get() = prefs.getBoolean(KEY_RA_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_RA_ENABLED, value) }

    override var raUsername: String
        get() = prefs.getString(KEY_RA_USERNAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_RA_USERNAME, value) }

    override var raToken: String?
        get() = prefs.getString(KEY_RA_TOKEN, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_RA_TOKEN) else putString(KEY_RA_TOKEN, value) }

    override var raShowNotifications: Boolean
        get() = prefs.getBoolean(KEY_RA_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_RA_NOTIFICATIONS, value) }

    override var raNotificationSeconds: Int
        get() = prefs.getInt(KEY_RA_NOTIFICATION_SECONDS, 3).coerceIn(1, 5)
        set(value) = prefs.edit { putInt(KEY_RA_NOTIFICATION_SECONDS, value) }

    override var raProgressSeconds: Int
        get() = prefs.getInt(KEY_RA_PROGRESS_SECONDS, 1).coerceIn(0, 5)
        set(value) = prefs.edit { putInt(KEY_RA_PROGRESS_SECONDS, value) }

    override var raSortOrder: RaSortOrder
        get() = prefs.getString(KEY_RA_SORT_ORDER, null)?.let { runCatching { RaSortOrder.valueOf(it) }.getOrNull() } ?: RaSortOrder.DEFAULT
        set(value) = prefs.edit { putString(KEY_RA_SORT_ORDER, value.name) }

    override var ssUsername: String
        get() = prefs.getString(KEY_SS_USERNAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_SS_USERNAME, value) }

    override var ssPassword: String?
        get() = prefs.getString(KEY_SS_PASSWORD, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_SS_PASSWORD) else putString(KEY_SS_PASSWORD, value) }

    override var sgdbApiKey: String?
        get() = prefs.getString(KEY_SGDB_KEY, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_SGDB_KEY) else putString(KEY_SGDB_KEY, value) }
    override var tgdbApiKey: String?
        get() = prefs.getString(KEY_TGDB_KEY, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_TGDB_KEY) else putString(KEY_TGDB_KEY, value) }

    override var artReplaceExisting: Boolean
        get() = prefs.getBoolean(KEY_ART_REPLACE, false)
        set(value) = prefs.edit { putBoolean(KEY_ART_REPLACE, value) }
    override var artBox2d: Boolean
        get() = prefs.getBoolean(KEY_ART_BOX2D, false)
        set(value) = prefs.edit { putBoolean(KEY_ART_BOX2D, value) }
    override var artWheel: Boolean
        get() = prefs.getBoolean(KEY_ART_WHEEL, false)
        set(value) = prefs.edit { putBoolean(KEY_ART_WHEEL, value) }
    override var artMix: Boolean
        get() = prefs.getBoolean(KEY_ART_MIX, false)
        set(value) = prefs.edit { putBoolean(KEY_ART_MIX, value) }

    override var showHomeTab: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HOME_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_HOME_TAB, value) }
    override var showConsolesTab: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CONSOLES_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_CONSOLES_TAB, value) }
    override var showCollectionsTab: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COLLECTIONS_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_COLLECTIONS_TAB, value) }
    override var showToolsTab: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TOOLS_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_TOOLS_TAB, value) }
    override var showNewShelf: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NEW_SHELF, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_NEW_SHELF, value) }
    override var showNotStartedShelf: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOT_STARTED_SHELF, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_NOT_STARTED_SHELF, value) }
    override var showNetplayHint: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NETPLAY_HINT, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_NETPLAY_HINT, value) }

    override var consoleArt: ConsoleArt
        get() = ConsoleArt.fromStored(prefs.getString(KEY_CONSOLE_ART, null))
        set(value) = prefs.edit { putString(KEY_CONSOLE_ART, value.name) }
    override var gameListArt: GameListArt
        get() = GameListArt.fromStored(prefs.getString(KEY_GAME_LIST_ART, null))
        set(value) = prefs.edit { putString(KEY_GAME_LIST_ART, value.name) }
    override var backdropArt: BackdropArt
        get() = BackdropArt.fromStored(prefs.getString(KEY_BACKDROP_ART, null))
        set(value) = prefs.edit { putString(KEY_BACKDROP_ART, value.name) }
    override var switcherLayout: SwitcherLayout
        get() = SwitcherLayout.fromStored(prefs.getString(KEY_SWITCHER_LAYOUT, null))
        set(value) = prefs.edit { putString(KEY_SWITCHER_LAYOUT, value.name) }
    override var optionTextSize: OptionTextSize
        get() = OptionTextSize.fromStored(prefs.getString(KEY_OPTION_TEXT_SIZE, null))
        set(value) = prefs.edit { putString(KEY_OPTION_TEXT_SIZE, value.name) }

    override var menuPageTitle: Boolean
        get() = prefs.getBoolean(KEY_MENU_PAGE_TITLE, MenuStyles.DEFAULT_PAGE_TITLE)
        set(value) = prefs.edit { putBoolean(KEY_MENU_PAGE_TITLE, value) }

    override var menuButtonHints: Boolean
        get() = prefs.getBoolean(KEY_MENU_BUTTON_HINTS, MenuStyles.DEFAULT_BUTTON_HINTS)
        set(value) = prefs.edit { putBoolean(KEY_MENU_BUTTON_HINTS, value) }

    override var menuExtraInfo: Boolean
        get() = prefs.getBoolean(KEY_MENU_EXTRA_INFO, MenuStyles.DEFAULT_EXTRA_INFO)
        set(value) = prefs.edit { putBoolean(KEY_MENU_EXTRA_INFO, value) }
    override var menuHomeStats: Boolean
        get() = prefs.getBoolean(KEY_MENU_HOME_STATS, MenuStyles.DEFAULT_HOME_STATS)
        set(value) = prefs.edit { putBoolean(KEY_MENU_HOME_STATS, value) }
    override var gameListLayoutOptions: Boolean
        get() = prefs.getBoolean(KEY_GAME_LIST_LAYOUT_OPTIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_GAME_LIST_LAYOUT_OPTIONS, value) }
    override var gameListAchievementsPanel: Boolean
        get() = prefs.getBoolean(KEY_GAME_LIST_ACHIEVEMENTS_PANEL, true)
        set(value) = prefs.edit { putBoolean(KEY_GAME_LIST_ACHIEVEMENTS_PANEL, value) }

    override var padVibration: PadVibration
        get() = PadVibration.fromStored(prefs.getString(KEY_PAD_VIBRATION, null))
        set(value) = prefs.edit { putString(KEY_PAD_VIBRATION, value.name) }

    override var padLayouts: PadLayoutMap
        // The old key's landscape moves are dropped on read until the first save writes the versioned key ([PadLayouts.decodeStored]).
        get() = PadLayouts.decodeStored(prefs.getString(KEY_PAD_LAYOUTS_V2, null), prefs.getString(KEY_PAD_LAYOUTS_V1, null))
        set(value) = prefs.edit { putString(KEY_PAD_LAYOUTS_V2, PadLayouts.encode(value)) }

    override var shortcuts: String
        get() = prefs.getString(KEY_SHORTCUTS, null).orEmpty()
        set(value) = prefs.edit { if (value.isEmpty()) remove(KEY_SHORTCUTS) else putString(KEY_SHORTCUTS, value) }

    override var systemButtons: String
        get() = prefs.getString(KEY_SYSTEM_BUTTONS, null).orEmpty()
        set(value) = prefs.edit { if (value.isEmpty()) remove(KEY_SYSTEM_BUTTONS) else putString(KEY_SYSTEM_BUTTONS, value) }

    override var gameRumble: GameRumble
        get() = GameRumble.fromStored(prefs.getString(KEY_GAME_RUMBLE, null))
        set(value) = prefs.edit { putString(KEY_GAME_RUMBLE, value.name) }

    override var menuPad: Boolean
        get() = prefs.getBoolean(KEY_MENU_PAD, true)
        set(value) {
            prefs.edit { putBoolean(KEY_MENU_PAD, value) }
        }

    override var secondScreen: Boolean
        get() = prefs.getBoolean(KEY_SECOND_SCREEN, true)
        set(value) {
            prefs.edit { putBoolean(KEY_SECOND_SCREEN, value) }
        }

    override var bottomScreen: BottomScreen
        get() = BottomScreen.parse(prefs.getString(KEY_BOTTOM_SCREEN, null))
        set(value) {
            prefs.edit { putString(KEY_BOTTOM_SCREEN, value.value) }
        }

    override var pauseWhenUnfocused: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_UNFOCUSED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_PAUSE_UNFOCUSED, value) }
        }

    override var debugLog: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOG, false)
        set(value) {
            prefs.edit { putBoolean(KEY_DEBUG_LOG, value) }
            NxLog.enabled = value   // at once: the app, the runner and the cores in this process
        }

    override var saveFormat: SaveFormat
        get() = SaveFormat.fromStored(prefs.getInt(KEY_SAVE_FORMAT, -1))
        set(value) = prefs.edit { putInt(KEY_SAVE_FORMAT, value.stored) }

    override var stateFormat: StateFormat
        get() = StateFormat.fromStored(prefs.getInt(KEY_STATE_FORMAT, -1))
        set(value) = prefs.edit { putInt(KEY_STATE_FORMAT, value.stored) }

    override var useExtractedFileName: Boolean
        get() = prefs.getBoolean(KEY_USE_EXTRACTED_FILE_NAME, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_EXTRACTED_FILE_NAME, value) }

    /** The cores Vulkan failed on for this phone ([VulkanFallback]), kept per build (its version name and commit). */
    override val vulkanFailedCores: Set<String>
        get() = VulkanFallback.failedCores(prefs.getString(KEY_VULKAN_FAILURES, null), APP_VERSION)

    /** Vulkan could not start for [coreId] on this phone: later launches of it go straight to OpenGL ES. */
    fun recordVulkanFailure(coreId: String) =
        prefs.edit { putString(KEY_VULKAN_FAILURES, VulkanFallback.record(prefs.getString(KEY_VULKAN_FAILURES, null), coreId, APP_VERSION)) }

    override var accent: AccentPreset
        get() = AccentPreset.fromId(prefs.getString(KEY_MENU_ACCENT, null))
        set(value) = prefs.edit { putString(KEY_MENU_ACCENT, value.id) }

    override var secondaryAccent: SecondaryAccent
        get() = SecondaryAccent.fromId(prefs.getString(KEY_MENU_ACCENT2, null))
        set(value) = prefs.edit { putString(KEY_MENU_ACCENT2, value.id) }

    /** Game time's device id: generated once per install, kept here so it survives a deleted database. */
    val gameTimeDeviceId: String
        get() = prefs.getString(KEY_GAME_TIME_DEVICE, null) ?: java.util.UUID.randomUUID().toString().also { id ->
            prefs.edit { putString(KEY_GAME_TIME_DEVICE, id) }
        }

    companion object {
        /** The app's prefs file (SecondScreens reads its debug switch there too). */
        const val PREFS_NAME = "nx-mobile"
        private const val KEY_TREE = "tree_uri"
        private const val KEY_EXTRAS = "extra_folders"
        private const val KEY_DEFAULT_TAGS = "default_tags"
        private const val KEY_PAD_THEME = "pad_theme"
        private const val KEY_PAD_BUTTONS = "pad_buttons"
        private const val KEY_MENU_STYLES = "menu_styles"
        private const val KEY_GAME_LIST_STYLE = "game_list_style"
        private const val KEY_MENU_ORIENTATIONS = "menu_orientations"
        private const val KEY_GAME_LIST_ORIENTATION = "game_list_orientation"
        private const val KEY_GAME_LIST_VERTICAL_ALIGNMENT = "game_list_vertical_alignment"
        private const val KEY_TOOL_APPS = "tool_apps"
        private const val KEY_ANDROID_CONSOLE_APPS = "android_console_apps"
        private const val KEY_ANDROID_GAMES_ASKED = "android_games_asked"
        private const val KEY_ALL_FILES_ASKED = "all_files_asked"
        private const val KEY_MIC_ASKED = "mic_asked"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_ONBOARDING_STEP = "onboarding_step"
        private const val KEY_ANDROID_PINS = "android_pins"
        private const val KEY_RA_ENABLED = "ra_enabled"
        private const val KEY_RA_USERNAME = "ra_username"
        private const val KEY_RA_TOKEN = "ra_token"
        private const val KEY_RA_NOTIFICATIONS = "ra_notifications"
        private const val KEY_RA_NOTIFICATION_SECONDS = "ra_notification_seconds"
        private const val KEY_RA_PROGRESS_SECONDS = "ra_progress_seconds"
        private const val KEY_RA_SORT_ORDER = "ra_sort_order"
        private const val KEY_SS_USERNAME = "ss_username"
        private const val KEY_SS_PASSWORD = "ss_password"
        private const val KEY_ART_REPLACE = "art_replace_existing"
        private const val KEY_ART_BOX2D = "art_box2d"
        private const val KEY_ART_WHEEL = "art_wheel"
        private const val KEY_ART_MIX = "art_mix"
        private const val KEY_SHOW_HOME_TAB = "show_home_tab"
        private const val KEY_SHOW_CONSOLES_TAB = "show_consoles_tab"
        private const val KEY_SHOW_COLLECTIONS_TAB = "show_collections_tab"
        private const val KEY_SHOW_TOOLS_TAB = "show_tools_tab"
        private const val KEY_SHOW_NEW_SHELF = "show_new_shelf"
        private const val KEY_SHOW_NOT_STARTED_SHELF = "show_not_started_shelf"
        private const val KEY_SHOW_NETPLAY_HINT = "show_netplay_hint"
        private const val KEY_CONSOLE_ART = "console_art"
        private const val KEY_GAME_LIST_ART = "game_list_art"
        private const val KEY_BACKDROP_ART = "backdrop_art"
        private const val KEY_SWITCHER_LAYOUT = "switcher_layout"
        private const val KEY_OPTION_TEXT_SIZE = "option_text_size"
        private const val KEY_MENU_PAGE_TITLE = "menu_page_title"
        private const val KEY_MENU_BUTTON_HINTS = "menu_button_hints"
        private const val KEY_MENU_EXTRA_INFO = "menu_extra_info"
        private const val KEY_MENU_HOME_STATS = "menu_home_stats"
        private const val KEY_GAME_LIST_LAYOUT_OPTIONS = "game_list_layout_options"
        private const val KEY_GAME_LIST_ACHIEVEMENTS_PANEL = "game_list_achievements_panel"
        private const val KEY_PAD_VIBRATION = "pad_vibration"
        /**
         * Pad layouts before the landscape pad moved beside the picture: read until the first save writes
         * [KEY_PAD_LAYOUTS_V2], then never again; kept, so a downgrade to an earlier build still finds its layouts.
         */
        private const val KEY_PAD_LAYOUTS_V1 = "pad_layouts"
        private const val KEY_PAD_LAYOUTS_V2 = "pad_layouts_v2"
        private const val KEY_SHORTCUTS = "shortcuts"
        private const val KEY_SYSTEM_BUTTONS = "system_buttons"
        private const val KEY_GAME_RUMBLE = "game_rumble"
        private const val KEY_DEBUG_LOG = "debug_log"
        private const val KEY_SECOND_SCREEN = "second_screen"
        private const val KEY_BOTTOM_SCREEN = "bottom_screen"
        private const val KEY_PAUSE_UNFOCUSED = "pause_unfocused"
        private const val KEY_MENU_PAD = "menu_pad"
        private const val KEY_SAVE_FORMAT = "save_format"
        private const val KEY_STATE_FORMAT = "state_format"
        private const val KEY_USE_EXTRACTED_FILE_NAME = "use_extracted_file_name"
        private const val KEY_VULKAN_FAILURES = "vulkan_failures"
        /** The build a Vulkan failure is recorded for: a new release, or a new debug build, tries Vulkan again. */
        private const val APP_VERSION = BuildConfig.VERSION_NAME + "-" + BuildConfig.GIT_COMMIT
        private const val KEY_MENU_ACCENT = "menu_accent"
        private const val KEY_MENU_ACCENT2 = "menu_accent2"
        private const val KEY_SGDB_KEY = "sgdb_api_key"
        private const val KEY_TGDB_KEY = "tgdb_api_key"
        private const val KEY_GAME_TIME_DEVICE = "game_time_device_id"
    }
}
