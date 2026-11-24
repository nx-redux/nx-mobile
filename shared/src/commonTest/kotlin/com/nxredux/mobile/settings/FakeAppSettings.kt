package com.nxredux.mobile.settings

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.runner.ContentRef

/**
 * An in-memory [AppSettings] holding only the settings the interface gives no default for. A setting the interface
 * defaults keeps dropping its writes here, so a test that needs one kept overrides it in a subclass.
 */
open class FakeAppSettings(
    override var treeRef: ContentRef? = null,
    override var padSkin: Boolean = true,
    override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap(),
    override var gameListStyle: MenuStyle = MenuStyles.DEFAULT_GAME_LIST_STYLE,
    override var androidGamesAsked: Boolean = false,
) : AppSettings {
    override var extraFolders: List<ExtraFolder> = emptyList()
    override var defaultTags: Map<String, String> = emptyMap()
    override var padTheme: PadTheme = PadTheme.DEFAULT
    override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
    override var gameListOrientation: MenuOrientation = MenuStyles.DEFAULT_ORIENTATION
    override var gameListVerticalAlignment: VerticalListAlignment = VerticalListAlignment.DEFAULT
    override var toolApps: List<String> = emptyList()
    override var androidConsoleApps: List<String> = emptyList()
    override var androidPins: List<String> = emptyList()
    override var externalPicks: Map<String, String> = emptyMap()
    override var raEnabled = false
    override var raUsername = ""
    override var raToken: String? = null
    override var raShowNotifications = true
    override var raNotificationSeconds = 3
    override var raProgressSeconds = 1
    override var raSortOrder = RaSortOrder.DEFAULT
    override var ssUsername = ""
    override var ssPassword: String? = null
    override var sgdbApiKey: String? = null
    override var tgdbApiKey: String? = null
    override var artReplaceExisting = false
    override var showHomeTab = true
    override var showConsolesTab = true
    override var showCollectionsTab = true
    override var showToolsTab = true
    override var showNewShelf = true
    override var showNotStartedShelf = true
    override var showNetplayHint = true
    override var consoleArt = ConsoleArt.DEFAULT
    override var gameListArt = GameListArt.DEFAULT
    override var backdropArt = BackdropArt.DEFAULT
    override var menuPageTitle = MenuStyles.DEFAULT_PAGE_TITLE
    override var menuButtonHints = MenuStyles.DEFAULT_BUTTON_HINTS
    override var menuExtraInfo = MenuStyles.DEFAULT_EXTRA_INFO
    override var menuHomeStats = true
    override var gameListLayoutOptions = true
    override var gameListAchievementsPanel = true
    override var padVibration = PadVibration.DEFAULT
    override var gameRumble = GameRumble.DEFAULT
    override var accent = AccentPreset.DEFAULT
    override var secondaryAccent: SecondaryAccent = SecondaryAccent.DEFAULT
}
