package com.nxredux.mobile.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Which pane of the two-pane Settings a screen is: the categories (left) or a category's pages (right). */
enum class SettingsPane { LEFT, RIGHT }

/** The left pane's share of the width. */
internal const val SETTINGS_SPLIT_LEFT = 0.35f

/** How wide a window must be, against its height, for Settings to split. The unfolded Fold's landscape (933 × 704) is 1.33. */
private const val SPLIT_MIN_ASPECT = 1.3f

/** The narrowest window Settings splits in. */
private val SPLIT_MIN_WIDTH = 600.dp

/**
 * Settings shows its categories and the selected category's options side by side on a wide landscape window (the
 * Flip's 21:9, 16:9–20:9 phones, the unfolded Fold); portrait and narrow windows keep one page at a time.
 */
fun settingsSplit(window: DpSize): Boolean =
    window.width >= SPLIT_MIN_WIDTH && window.width.value >= window.height.value * SPLIT_MIN_ASPECT

/** The Settings categories: the sections the Settings root lists (sub-pages open from them). Second screen only while one is connected. */
fun settingsCategories(secondScreen: Boolean = false): List<SettingsSection> =
    SettingsSection.entries.filter { it.parent == null && (it != SettingsSection.SECOND_SCREEN || secondScreen) }

/**
 * The pane [screen] is drawn in when Settings splits: the Settings root is the left pane (focused there), a Settings
 * page, sub-page or Emulator Settings page the right; null for any other screen (drawn whole), a game's own Game
 * Settings (a modal over its list, [gameSettingsModal]) included.
 */
fun settingsPane(screen: Screen): SettingsPane? = when (screen) {
    is Screen.SettingsMenu -> SettingsPane.LEFT
    is Screen.EmulatorOptions -> if (gameSettingsModal(screen)) null else SettingsPane.RIGHT
    is Screen.Settings, is Screen.EmulatorList, is Screen.EmulatorConsoles -> SettingsPane.RIGHT
    is Screen.ToolApps -> if (screen.pick == AppPick.GAMES) SettingsPane.RIGHT else null   // the app drawer is a tool, drawn whole
    else -> null
}

/** Whether [screen] draws as the two-pane Settings in [window]: a split window and a Settings pane. */
fun settingsSplitView(window: DpSize, screen: Screen): Boolean = settingsSplit(window) && settingsPane(screen) != null

/**
 * A game's own Game Settings (opened from its context menu, [Screen.EmulatorOptions.game] set), its categories
 * included: a modal over the game list it came from, the same on every window. The console-wide page is not.
 */
fun gameSettingsModal(screen: Screen): Boolean = screen is Screen.EmulatorOptions && screen.game != null

/**
 * How far the NX-mark watermark moves right, as a share of the width: split, it centres on the right pane (the left
 * pane's share plus half the rest, less the middle); one page, it stays centred.
 */
fun settingsMarkShift(split: Boolean): Float =
    if (split) SETTINGS_SPLIT_LEFT + (1f - SETTINGS_SPLIT_LEFT) / 2f - 0.5f else 0f

/** The category a right-pane [screen] belongs to (its top section; Emulator Settings is under Emulators, Android games under Library); null otherwise. */
fun settingsCategoryOf(screen: Screen): SettingsSection? = when (screen) {
    is Screen.Settings -> generateSequence(screen.section) { it.parent }.last()
    is Screen.EmulatorList, is Screen.EmulatorConsoles, is Screen.EmulatorOptions -> SettingsSection.EMULATORS
    is Screen.ToolApps -> if (screen.pick == AppPick.GAMES) SettingsSection.LIBRARY else null
    else -> null
}

/**
 * The RetroAchievements column browser on a split window ([settingsSplit], the same rule): a level below the
 * RetroAchievements page (its Settings, the games list, a game's achievements, one achievement) is the focused right
 * pane, the level it came from the dimmed left pane with the row that opened it highlighted ([AppState.raParent]). The
 * page itself is drawn whole (B from the first level returns to it full width), as is every window [settingsSplit]
 * does not split.
 */
fun raSplitView(window: DpSize, screen: Screen): Boolean = settingsSplit(window) && raSplitLevel(screen)

/** Whether [screen] is a RetroAchievements level drawn beside its parent when the window splits ([raSplitView]). */
fun raSplitLevel(screen: Screen): Boolean =
    screen is Screen.RaSettings || screen is Screen.RaGames || screen is Screen.RaGame || screen is Screen.RaAchievement

/**
 * The Game Tracker column browser on a split window ([settingsSplit], the same rule): a game's sessions are the focused
 * right pane, the tracker list the dimmed left pane with that game's row highlighted ([AppState.gameTimeParent]). The
 * list itself is drawn whole and previews nothing, as the RetroAchievements levels.
 */
fun gameTimeSplitView(window: DpSize, screen: Screen): Boolean = settingsSplit(window) && screen is Screen.GameTimeSessions

/**
 * The Artwork column browser on a split window ([settingsSplit], the same rule): a page below the Artwork page (Fetch by
 * console, Progress, Settings, Reset artwork) is the focused right pane, the page it came from the dimmed left pane with
 * the row that opened it highlighted ([AppState.artworkParent]): the Artwork page beside the first three, Settings beside
 * Reset. The Artwork page itself is drawn whole, as the RetroAchievements page.
 */
fun artworkSplitView(window: DpSize, screen: Screen): Boolean = settingsSplit(window) && artworkSplitLevel(screen)

/** Whether [screen] is an Artwork page drawn beside its parent when the window splits ([artworkSplitView]). */
fun artworkSplitLevel(screen: Screen): Boolean =
    screen is Screen.ArtworkConsoles || screen is Screen.ArtworkProgress || screen is Screen.ArtworkSettings || screen is Screen.ArtworkReset

/** Whether [screen] is drawn as two panes in [window]: the two-pane Settings or a column browser (RetroAchievements, Game Tracker, Artwork). */
fun twoPaneView(window: DpSize, screen: Screen): Boolean =
    settingsSplitView(window, screen) || raSplitView(window, screen) || gameTimeSplitView(window, screen) || artworkSplitView(window, screen)

/** The RetroAchievements page's row that opened [screen] (Achievements, Settings), highlighted in the left pane; null below that. */
fun raPageRow(screen: Screen): Int? = when (screen) {
    is Screen.RaGames -> RaAction.entries.indexOf(RaAction.ACHIEVEMENTS)
    is Screen.RaSettings -> RaAction.entries.indexOf(RaAction.SETTINGS)
    else -> null
}

/** The level a RetroAchievements left pane shows ([AppState.raParent]): its [screen] and the highlighted [row], the one that opened the right pane. */
data class RaParent(val screen: Screen, val row: Int)

/** The page an Artwork left pane shows ([AppState.artworkParent]): its [screen] (the Artwork page or Settings) and the highlighted [row], the one that opened the right pane. */
data class ArtworkParent(val screen: Screen, val row: Int)
