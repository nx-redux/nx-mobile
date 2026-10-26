package com.nxredux.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxHintBarKeepHeight
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar

/** An unfocused pane's opacity. */
internal const val UNFOCUSED_ALPHA = 0.6f

/**
 * The wide Settings ([settingsSplit]): one stage, nothing above but the small mark (the background's). The section block
 * ([SettingsMenuScreen]) rests bottom-left; the page is the rows block ([LocalSettingsBlock]) at the bottom right, its
 * left edge at [SETTINGS_ROWS_LEFT] of the width, on the same baseline. On the Settings root the sections have the
 * focus and the rows preview the highlighted section (no selection, a tap opens it on that row,
 * [AppState.settingsPreviewTap]); on a page the rows have it and the current section drops to the main ink (a tap on a
 * section, [AppState.settingsCategoryTap]). A page that is not a rows block ([settingsBlockPage]: a Layouts group with
 * its picture, the Android games picker) draws whole from the old pane's edge. Only the page shows hints; both sides keep
 * the bar's height, so the blocks share the baseline. The Remove folder dialog is drawn here, over the whole stage.
 */
@Composable
internal fun SettingsSplitScreen(state: AppState, s: Screen, selection: State<ListSelection>, page: @Composable () -> Unit) {
    val categories = state.settingsSections()   // not remembered: a second screen coming or going changes it
    val blockPage = settingsBlockPage(s)
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNxHintBarKeepHeight provides true) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val left = maxWidth * settingsStageLeft(blockPage)
                Column(Modifier.width(left).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (s is Screen.SettingsMenu) {
                            WithIndex(selection) {
                                SettingsMenuScreen(s, it, state::settingsCategoryTap, onBack = { state.onButton(NxButton.B) })
                            }
                        } else {
                            val current = categories.indexOf(settingsCategoryOf(s)).coerceAtLeast(0)
                            // The page has the focus: the current section drops to the main ink.
                            SettingsMenuScreen(
                                Screen.SettingsMenu(categories), current,
                                onTap = state::settingsCategoryTap, onBack = { state.onButton(NxButton.B) }, rowsFocused = true,
                            )
                        }
                    }
                    NxHintBar(emptyList())   // no hints here; with a pad it keeps the page bar's room, so the baselines meet
                }
                Box(Modifier.padding(start = left).fillMaxSize()) {
                    CompositionLocalProvider(LocalSettingsBlock provides SettingsBlockMode(crumb = null).takeIf { blockPage }) {
                        if (s is Screen.SettingsMenu) {
                            WithIndex(selection) { i ->
                                s.sections.getOrNull(i)?.let { section ->
                                    val gpu by state.gpu.collectAsState()   // About's GPU row lands after its query
                                    val update by state.updateLabel.collectAsState()   // and its update row follows the updater
                                    val preview = remember(s, section, gpu, update) { state.settingsPreview(section) }
                                    // No selection, undimmed: the current section is the one accent word.
                                    SettingsScreen(
                                        preview, -1,
                                        onSelect = state::settingsPreviewTap, onOpen = state::settingsPreviewTap,
                                        titled = true, hints = emptyList(),
                                    )
                                }
                            }
                        } else {
                            page()
                        }
                    }
                }
            }
        }
        // Over the whole stage, so its scrim takes every tap until it is answered.
        if ((s as? Screen.Settings)?.confirmRemove != null) SettingsRemoveDialog(state::onButton)
    }
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
                if (divider) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.inkEdge))   // the divider between the panes: a hairline
                Box(Modifier.weight(1f - leftShare).fillMaxHeight()) { right() }
            }
        }
        over()
    }
}

/** The Settings menu's title: the two-pane left pane's, and the one-page menu's. */
internal const val SETTINGS_TITLE = "Settings"
