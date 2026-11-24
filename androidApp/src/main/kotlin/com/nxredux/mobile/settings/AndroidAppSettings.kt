package com.nxredux.mobile.settings

import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.kids.KidsPin
import android.content.Context
import androidx.core.content.edit
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.gpudriver.GpuDriverChoice
import com.nxredux.mobile.options.VulkanFallback
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.runner.BottomScreen
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import com.nxredux.mobile.DebugLogFile
import com.swordfish.libretrodroid.NxLog
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class AndroidAppSettings(context: Context) : AppSettings {
    private val app = context.applicationContext
    private val prefs = Prefs.open(context, PREFS_NAME)

    private fun bool(key: String, default: Boolean): ReadWriteProperty<Any?, Boolean> = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) = prefs.edit { putBoolean(key, value) }
    }

    /** Null removes the key. */
    private fun nullableString(key: String): ReadWriteProperty<Any?, String?> = object : ReadWriteProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String? = prefs.getString(key, null)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) =
            prefs.edit { if (value == null) remove(key) else putString(key, value) }
    }

    /** One entry a line; blank lines are dropped on read. */
    private fun lines(key: String): ReadWriteProperty<Any?, List<String>> = object : ReadWriteProperty<Any?, List<String>> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): List<String> =
            prefs.getString(key, "")!!.lineSequence().filter { it.isNotBlank() }.toList()
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: List<String>) =
            prefs.edit { putString(key, value.joinToString("\n")) }
    }

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

    override var padSkin: Boolean by bool(KEY_PAD_SKIN, true)

    override var padTheme: PadTheme
        get() = prefs.getString(KEY_PAD_THEME, null)?.let { runCatching { PadTheme.valueOf(it) }.getOrNull() } ?: PadTheme.DEFAULT
        set(value) = prefs.edit { putString(KEY_PAD_THEME, value.name) }

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

    override var toolApps: List<String> by lines(KEY_TOOL_APPS)

    override var androidConsoleApps: List<String> by lines(KEY_ANDROID_CONSOLE_APPS)

    override var mainMenuTipHidden: Boolean by bool(KEY_MAIN_MENU_TIP_HIDDEN, false)

    override var gameListTipHidden: Boolean by bool(KEY_GAME_LIST_TIP_HIDDEN, false)

    override var androidGamesAsked: Boolean by bool(KEY_ANDROID_GAMES_ASKED, false)
    override var micAsked: Boolean by bool(KEY_MIC_ASKED, false)
    override var micButtonShown: Boolean by bool(KEY_MIC_BUTTON_SHOWN, false)
    override var layoutTipHidden: Boolean by bool(KEY_LAYOUT_TIP_HIDDEN, false)

    override var allFilesAsked: Boolean by bool(KEY_ALL_FILES_ASKED, false)

    override var onboardingDone: Boolean?
        get() = if (prefs.contains(KEY_ONBOARDING_DONE)) prefs.getBoolean(KEY_ONBOARDING_DONE, false) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_ONBOARDING_DONE) else putBoolean(KEY_ONBOARDING_DONE, value) }

    override var onboardingStep: String? by nullableString(KEY_ONBOARDING_STEP)

    override var androidPins: List<String> by lines(KEY_ANDROID_PINS)

    override var externalPicks: Map<String, String>
        get() = ExternalPicksCodec.decode(prefs.getString(KEY_EXTERNAL_PICKS, null))
        set(value) = prefs.edit { putString(KEY_EXTERNAL_PICKS, ExternalPicksCodec.encode(value)) }

    override var raEnabled: Boolean by bool(KEY_RA_ENABLED, false)

    override var raUsername: String
        get() = prefs.getString(KEY_RA_USERNAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_RA_USERNAME, value) }

    override var raToken: String? by nullableString(KEY_RA_TOKEN)

    override var raShowNotifications: Boolean by bool(KEY_RA_NOTIFICATIONS, true)

    override var raNotificationSeconds: Int
        get() = prefs.getInt(KEY_RA_NOTIFICATION_SECONDS, 3).coerceIn(1, 5)
        set(value) = prefs.edit { putInt(KEY_RA_NOTIFICATION_SECONDS, value) }

    override var raProgressSeconds: Int
        get() = prefs.getInt(KEY_RA_PROGRESS_SECONDS, 1).coerceIn(0, 5)
        set(value) = prefs.edit { putInt(KEY_RA_PROGRESS_SECONDS, value) }

    override var raUnlockSound: Boolean by bool(KEY_RA_UNLOCK_SOUND, true)

    override var raUnlockScreenshot: Boolean by bool(KEY_RA_UNLOCK_SCREENSHOT, false)

    override var raSortOrder: RaSortOrder
        get() = prefs.getString(KEY_RA_SORT_ORDER, null)?.let { runCatching { RaSortOrder.valueOf(it) }.getOrNull() } ?: RaSortOrder.DEFAULT
        set(value) = prefs.edit { putString(KEY_RA_SORT_ORDER, value.name) }

    override var ssUsername: String
        get() = prefs.getString(KEY_SS_USERNAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_SS_USERNAME, value) }

    override var ssPassword: String? by nullableString(KEY_SS_PASSWORD)

    override var sgdbApiKey: String? by nullableString(KEY_SGDB_KEY)
    override var tgdbApiKey: String? by nullableString(KEY_TGDB_KEY)

    override var artReplaceExisting: Boolean by bool(KEY_ART_REPLACE, false)
    override var artBox2d: Boolean by bool(KEY_ART_BOX2D, false)
    override var artWheel: Boolean by bool(KEY_ART_WHEEL, false)
    override var artMix: Boolean by bool(KEY_ART_MIX, false)
    override var fetchGameInfo: Boolean by bool(KEY_FETCH_GAME_INFO, true)
    override var artWheelMigrated: Boolean by bool(KEY_ART_WHEEL_MIGRATED, false)

    override var showHomeTab: Boolean by bool(KEY_SHOW_HOME_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
    override var showConsolesTab: Boolean by bool(KEY_SHOW_CONSOLES_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
    override var showGamesTab: Boolean by bool(KEY_SHOW_GAMES_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
    override var showCollectionsTab: Boolean by bool(KEY_SHOW_COLLECTIONS_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
    override var showToolsTab: Boolean by bool(KEY_SHOW_TOOLS_TAB, MenuStyles.DEFAULT_TAB_SHOWN)
    override var showNewShelf: Boolean by bool(KEY_SHOW_NEW_SHELF, true)
    override var showNotStartedShelf: Boolean by bool(KEY_SHOW_NOT_STARTED_SHELF, true)
    override var showMostPlayedShelf: Boolean by bool(KEY_SHOW_MOST_PLAYED_SHELF, true)
    override var showNetplayHint: Boolean by bool(KEY_SHOW_NETPLAY_HINT, true)

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

    override var menuPageTitle: Boolean by bool(KEY_MENU_PAGE_TITLE, MenuStyles.DEFAULT_PAGE_TITLE)

    override var menuButtonHints: Boolean by bool(KEY_MENU_BUTTON_HINTS, MenuStyles.DEFAULT_BUTTON_HINTS)

    override var menuExtraInfo: Boolean by bool(KEY_MENU_EXTRA_INFO, MenuStyles.DEFAULT_EXTRA_INFO)
    override var menuHomeStats: Boolean by bool(KEY_MENU_HOME_STATS, MenuStyles.DEFAULT_HOME_STATS)
    override var gameListLayoutOptions: Boolean by bool(KEY_GAME_LIST_LAYOUT_OPTIONS, true)
    override var gameListAchievementsPanel: Boolean by bool(KEY_GAME_LIST_ACHIEVEMENTS_PANEL, true)

    override var padVibration: PadVibration
        get() = PadVibration.fromStored(prefs.getString(KEY_PAD_VIBRATION, null))
        set(value) = prefs.edit { putString(KEY_PAD_VIBRATION, value.name) }

    override var shortcuts: String
        // never written: the defaults (Save / Load on the skin's slots); written empty (Clear all): none
        get() = prefs.getString(KEY_SHORTCUTS, null) ?: com.nxredux.mobile.shortcuts.Shortcuts.DEFAULT_BASE
        set(value) = prefs.edit { putString(KEY_SHORTCUTS, value) }

    override var systemButtons: String
        get() = prefs.getString(KEY_SYSTEM_BUTTONS, null).orEmpty()
        set(value) = prefs.edit { if (value.isEmpty()) remove(KEY_SYSTEM_BUTTONS) else putString(KEY_SYSTEM_BUTTONS, value) }

    override var skinTweaks: String
        get() = prefs.getString(KEY_SKIN_TWEAKS, null).orEmpty()
        set(value) = prefs.edit { if (value.isEmpty()) remove(KEY_SKIN_TWEAKS) else putString(KEY_SKIN_TWEAKS, value) }

    override var overlayTweaks: String
        get() = prefs.getString(KEY_OVERLAY_TWEAKS, null).orEmpty()
        set(value) = prefs.edit { if (value.isEmpty()) remove(KEY_OVERLAY_TWEAKS) else putString(KEY_OVERLAY_TWEAKS, value) }

    override var gameRumble: GameRumble
        get() = GameRumble.fromStored(prefs.getString(KEY_GAME_RUMBLE, null))
        set(value) = prefs.edit { putString(KEY_GAME_RUMBLE, value.name) }

    override var secondScreen: Boolean by bool(KEY_SECOND_SCREEN, true)

    override var bottomScreen: BottomScreen
        get() = BottomScreen.parse(prefs.getString(KEY_BOTTOM_SCREEN, null))
        set(value) {
            prefs.edit { putString(KEY_BOTTOM_SCREEN, value.value) }
        }

    override var pauseWhenUnfocused: Boolean by bool(KEY_PAUSE_UNFOCUSED, true)

    override var menuInfoAchievements: Boolean by bool(KEY_MENU_INFO_ACHIEVEMENTS, MenuStyles.DEFAULT_INFO_ACHIEVEMENTS)

    override var screenCare: Boolean by bool(KEY_SCREEN_CARE, true)

    override var saveOnQuit: Boolean by bool(KEY_SAVE_ON_QUIT, true)

    override var aResumes: Boolean by bool(KEY_A_RESUMES, true)

    override var playerName: String
        get() = prefs.getString(KEY_PLAYER_NAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PLAYER_NAME, value) }

    override var debugLog: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOG, false)
        set(value) {
            prefs.edit { putBoolean(KEY_DEBUG_LOG, value) }
            NxLog.enabled = value   // at once: the app, the runner and the cores in this process
            if (value) DebugLogFile.start(app) else DebugLogFile.stop()
        }

    /** The MAC seed of a DS link room's members: random once, then kept. */
    val ndsMacSeed: Int
        get() = if (prefs.contains(KEY_NDS_MAC_SEED)) prefs.getInt(KEY_NDS_MAC_SEED, 0)
        else kotlin.random.Random.nextInt(0, 1 shl 24).also { prefs.edit { putInt(KEY_NDS_MAC_SEED, it) } }

    override var cableLinkDelayPin: Int
        get() = prefs.getInt(KEY_CABLE_LINK_DELAY, NetplayCores.CABLE_LINK_DELAY_DEFAULT).coerceIn(0, NetplayCores.CABLE_LINK_DELAY_MAX)
        set(value) = prefs.edit { putInt(KEY_CABLE_LINK_DELAY, value.coerceIn(0, NetplayCores.CABLE_LINK_DELAY_MAX)) }

    override var cableLinkDelayOverlay: Boolean by bool(KEY_CABLE_LINK_DELAY_OVERLAY, false)

    override var androidKeyboard: Boolean by bool(KEY_ANDROID_KEYBOARD, false)

    override var kidsEnabled: Boolean by bool(KEY_KIDS_ENABLED, false)

    override var kidsSchedule: String? by nullableString(KEY_KIDS_SCHEDULE)

    override var kidsAllowedApps: Set<String>
        get() = prefs.getStringSet(KEY_KIDS_APPS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_KIDS_APPS, value.toSet()) }

    override var kidsUnlockUntil: Long
        get() = prefs.getLong(KEY_KIDS_UNLOCK_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_KIDS_UNLOCK_UNTIL, value) }

    override var kidsFlagHome: String? by nullableString(KEY_KIDS_FLAG_HOME)

    override var kidsOnWhenHome: Boolean
        get() = prefs.getBoolean(KEY_KIDS_ON_WHEN_HOME, false)
        set(value) = prefs.edit { if (value) putBoolean(KEY_KIDS_ON_WHEN_HOME, true) else remove(KEY_KIDS_ON_WHEN_HOME) }

    override fun setKidsPin(pin: String) = prefs.edit { putString(KEY_KIDS_PIN, KidsPin.hash(pin)) }

    override fun kidsPinMatches(pin: String): Boolean = prefs.getString(KEY_KIDS_PIN, null)?.let { KidsPin.matches(pin, it) } == true

    override val kidsPinSet: Boolean get() = prefs.getString(KEY_KIDS_PIN, null) != null

    override fun clearKidsPin() = prefs.edit { remove(KEY_KIDS_PIN) }

    override var saveFormat: SaveFormat
        get() = SaveFormat.fromStored(prefs.getInt(KEY_SAVE_FORMAT, -1))
        set(value) = prefs.edit { putInt(KEY_SAVE_FORMAT, value.stored) }

    override var stateFormat: StateFormat
        get() = StateFormat.fromStored(prefs.getInt(KEY_STATE_FORMAT, -1))
        set(value) = prefs.edit { putInt(KEY_STATE_FORMAT, value.stored) }

    override var useExtractedFileName: Boolean by bool(KEY_USE_EXTRACTED_FILE_NAME, false)

    override var gpuDriver: String
        get() = prefs.getString(KEY_GPU_DRIVER, null) ?: GpuDriverChoice.SYSTEM
        set(value) = prefs.edit { putString(KEY_GPU_DRIVER, value) }

    /**
     * A marker is committed, not applied: it must be on disk before the launch it guards can kill the process. In the
     * game process that is [ProviderPreferences]' commit, which waits until the menu process has committed it to its
     * file (and every apply queued before it); blocking, so never on the main thread. A clear is applied: a marker left
     * behind only costs a crash record when that process is gone, and the game process's end waits for its sends.
     */
    override var gpuDriverMarker: String?
        get() = prefs.getString(KEY_GPU_DRIVER_MARKER, null)
        set(value) {
            if (value == null) prefs.edit { remove(KEY_GPU_DRIVER_MARKER) }
            else if (!prefs.edit().putString(KEY_GPU_DRIVER_MARKER, value).commit()) NxLog.w("NXAPP", "the GPU driver launch marker could not be saved")
        }

    override var gpuDriverCrashes: String? by nullableString(KEY_GPU_DRIVER_CRASHES)

    override var lastLaunchMaliSystemVulkan: Boolean by bool(KEY_LAST_LAUNCH_MALI_SYSTEM_VULKAN, false)

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
        private const val KEY_PAD_SKIN = "pad_skin"
        private const val KEY_PAD_THEME = "pad_theme"
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
        private const val KEY_MIC_BUTTON_SHOWN = "mic_button_shown"
        private const val KEY_LAYOUT_TIP_HIDDEN = "layout_tip_hidden"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_ONBOARDING_STEP = "onboarding_step"
        private const val KEY_ANDROID_PINS = "android_pins"
        private const val KEY_EXTERNAL_PICKS = "external_picks"
        private const val KEY_RA_ENABLED = "ra_enabled"
        private const val KEY_RA_USERNAME = "ra_username"
        private const val KEY_RA_TOKEN = "ra_token"
        private const val KEY_RA_NOTIFICATIONS = "ra_notifications"
        private const val KEY_RA_NOTIFICATION_SECONDS = "ra_notification_seconds"
        private const val KEY_RA_PROGRESS_SECONDS = "ra_progress_seconds"
        private const val KEY_RA_UNLOCK_SOUND = "ra_unlock_sound"
        private const val KEY_RA_UNLOCK_SCREENSHOT = "ra_unlock_screenshot"
        private const val KEY_RA_SORT_ORDER = "ra_sort_order"
        private const val KEY_SS_USERNAME = "ss_username"
        private const val KEY_SS_PASSWORD = "ss_password"
        private const val KEY_ART_REPLACE = "art_replace_existing"
        private const val KEY_ART_BOX2D = "art_box2d"
        private const val KEY_ART_WHEEL = "art_wheel"
        private const val KEY_ART_MIX = "art_mix"
        private const val KEY_FETCH_GAME_INFO = "fetch_game_info"
        private const val KEY_ART_WHEEL_MIGRATED = "art_wheel_migrated"
        private const val KEY_SHOW_HOME_TAB = "show_home_tab"
        private const val KEY_SHOW_CONSOLES_TAB = "show_consoles_tab"
        private const val KEY_SHOW_COLLECTIONS_TAB = "show_collections_tab"
        private const val KEY_SHOW_TOOLS_TAB = "show_tools_tab"
        private const val KEY_SHOW_GAMES_TAB = "show_games_tab"
        private const val KEY_SHOW_NEW_SHELF = "show_new_shelf"
        private const val KEY_SHOW_NOT_STARTED_SHELF = "show_not_started_shelf"
        private const val KEY_SHOW_MOST_PLAYED_SHELF = "show_most_played_shelf"
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
        private const val KEY_MAIN_MENU_TIP_HIDDEN = "main_menu_tip_hidden"
        private const val KEY_GAME_LIST_TIP_HIDDEN = "game_list_tip_hidden"
        private const val KEY_PAD_VIBRATION = "pad_vibration"
        private const val KEY_SHORTCUTS = "shortcuts"
        private const val KEY_SYSTEM_BUTTONS = "system_buttons"
        private const val KEY_SKIN_TWEAKS = "skin_tweaks"
        private const val KEY_OVERLAY_TWEAKS = "overlay_tweaks"
        private const val KEY_GAME_RUMBLE = "game_rumble"
        private const val KEY_DEBUG_LOG = "debug_log"
        private const val KEY_PLAYER_NAME = "player_name"
        private const val KEY_NDS_MAC_SEED = "nds_mac_seed"
        private const val KEY_CABLE_LINK_DELAY = "cable_link_delay"
        private const val KEY_CABLE_LINK_DELAY_OVERLAY = "cable_link_delay_overlay"
        private const val KEY_ANDROID_KEYBOARD = "android_keyboard"
        private const val KEY_KIDS_ENABLED = "kids_enabled"
        private const val KEY_KIDS_SCHEDULE = "kids_schedule"
        private const val KEY_KIDS_APPS = "kids_apps"
        private const val KEY_KIDS_UNLOCK_UNTIL = "kids_unlock_until"
        private const val KEY_KIDS_FLAG_HOME = "kids_flag_home"
        private const val KEY_KIDS_ON_WHEN_HOME = "kids_on_when_home"
        private const val KEY_KIDS_PIN = "kids_pin"
        private const val KEY_SECOND_SCREEN = "second_screen"
        private const val KEY_BOTTOM_SCREEN = "bottom_screen"
        private const val KEY_PAUSE_UNFOCUSED = "pause_unfocused"
        private const val KEY_SCREEN_CARE = "screen_care"
        private const val KEY_SAVE_ON_QUIT = "save_on_quit"
        private const val KEY_A_RESUMES = "a_resumes"
        private const val KEY_MENU_INFO_ACHIEVEMENTS = "menu_info_achievements"
        private const val KEY_SAVE_FORMAT = "save_format"
        private const val KEY_STATE_FORMAT = "state_format"
        private const val KEY_USE_EXTRACTED_FILE_NAME = "use_extracted_file_name"
        private const val KEY_VULKAN_FAILURES = "vulkan_failures"
        private const val KEY_GPU_DRIVER = "gpu_driver"
        private const val KEY_GPU_DRIVER_MARKER = "gpu_driver_marker"
        private const val KEY_GPU_DRIVER_CRASHES = "gpu_driver_crashes"
        private const val KEY_LAST_LAUNCH_MALI_SYSTEM_VULKAN = "last_launch_mali_system_vulkan"
        /** The build a Vulkan failure is recorded for: a new release, or a new debug build, tries Vulkan again. */
        private const val APP_VERSION = BuildConfig.VERSION_NAME + "-" + BuildConfig.GIT_COMMIT
        private const val KEY_MENU_ACCENT = "menu_accent"
        private const val KEY_MENU_ACCENT2 = "menu_accent2"
        private const val KEY_SGDB_KEY = "sgdb_api_key"
        private const val KEY_TGDB_KEY = "tgdb_api_key"
        private const val KEY_GAME_TIME_DEVICE = "game_time_device_id"
    }
}
