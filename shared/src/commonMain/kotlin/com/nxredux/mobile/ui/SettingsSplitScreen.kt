package com.nxredux.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxHintBarKeepHeight

/** An unfocused pane's opacity. */
internal const val UNFOCUSED_ALPHA = 0.6f

/** The divider between the panes: 1 dp of white at 8%. */
private const val DIVIDER_ALPHA = 0.08f

/**
 * The two-pane Settings on a wide landscape window ([settingsSplit]): the categories on the left, the category's page
 * on the right, a faint divider between. The focus follows the screen ([settingsPane]): on the Settings root the left
 * pane is focused and the right previews the highlighted category (no highlight, dimmed; a tap opens it on that row);
 * on a page the right pane is [page] exactly as the single page draws it, and the left pane shows the categories dimmed
 * on the page's own (a tap there focuses it, [AppState.settingsCategoryTap]). Only the focused pane shows hints (both
 * bars keep their height, so the panes' rows stay level). The Remove folder dialog is drawn here, over both panes.
 */
@Composable
internal fun SettingsSplitScreen(state: AppState, s: Screen, selection: State<ListSelection>, page: @Composable () -> Unit) {
    val categories = state.settingsSections()   // not remembered: a second screen coming or going changes it
    SplitPanes(
        divider = false,   // the categories and the page are one surface, as the in-game menu's two panes
        left = {
            if (s is Screen.SettingsMenu) {
                WithIndex(selection) {
                    SettingsMenuScreen(s, it, state::select, { state.openSelected() }, onHint = state::onButton)
                }
            } else {
                val current = categories.indexOf(settingsCategoryOf(s)).coerceAtLeast(0)
                SettingsMenuScreen(
                    Screen.SettingsMenu(categories), current,
                    onSelect = state::settingsCategoryTap, onOpen = state::settingsCategoryTap,
                    modifier = Modifier.alpha(UNFOCUSED_ALPHA), hints = emptyList(),
                )
            }
        },
        right = {
            if (s is Screen.SettingsMenu) {
                WithIndex(selection) { i ->
                    s.sections.getOrNull(i)?.let { section ->
                        val gpu by state.gpu.collectAsState()   // About's GPU row lands after its query
                        val update by state.updateLabel.collectAsState()   // and its update row follows the updater
                        val preview = remember(s, section, gpu, update) { state.settingsPreview(section) }
                        SettingsScreen(
                            preview, -1,
                            onSelect = state::settingsPreviewTap, onOpen = state::settingsPreviewTap,
                            modifier = Modifier.alpha(UNFOCUSED_ALPHA), titled = true, hints = emptyList(),
                        )
                    }
                }
            } else {
                page()
            }
        },
        // Over both panes, so its scrim takes every tap until it is answered.
        over = { if ((s as? Screen.Settings)?.confirmRemove != null) SettingsRemoveDialog(state::onButton) },
    )
}

/**
 * The two-pane frame on a wide landscape window ([settingsSplit]): [left] in [SETTINGS_SPLIT_LEFT] of the width, a 1 dp
 * divider at 8%, [right] in the rest, then [over] drawn over both panes (a dialog's scrim covers the whole window).
 * Both panes' hint bars keep their height, so their rows stay level when only the focused one shows hints. Used by the
 * two-pane Settings, the column browsers (RetroAchievements, Game Tracker, Artwork) and the in-game menu, which passes its
 * own [leftShare] and no [divider] (its list sits beside the game panel as one surface).
 */
@Composable
internal fun SplitPanes(
    left: @Composable () -> Unit, right: @Composable () -> Unit, over: @Composable () -> Unit = {},
    leftShare: Float = SETTINGS_SPLIT_LEFT, divider: Boolean = true,
) {
    val colors = LocalNxColors.current
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNxHintBarKeepHeight provides true) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(leftShare).fillMaxHeight()) { left() }
                if (divider) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.listText.copy(alpha = DIVIDER_ALPHA)))
                Box(Modifier.weight(1f - leftShare).fillMaxHeight()) { right() }
            }
        }
        over()
    }
}

/** The Settings menu's title: the two-pane left pane's, and the one-page menu's. */
internal const val SETTINGS_TITLE = "Settings"
