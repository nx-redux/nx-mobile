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
import com.nxredux.mobile.ui.nx.NxOptionsInnerPad
import com.nxredux.mobile.ui.nx.LocalNxHintBarEnd
import com.nxredux.mobile.ui.nx.LocalNxHintBarStart

/** An unfocused pane's opacity. */
internal const val UNFOCUSED_ALPHA = 0.6f

/**
 * The wide Settings ([settingsSplit]) on its stage ([SettingsStage]): the sections on the left, the rows block on the
 * right. On the Settings root the sections have the focus and the rows preview the highlighted section (no selection,
 * a tap opens it on that row, [AppState.settingsPreviewTap]); on a page the rows have it and the current section drops
 * to the open grey with a "›" (a tap on a section, [AppState.settingsCategoryTap]). A Layouts group's picture is part
 * of its block, under the rows ([settingsWideBlockPage]); the Android games picker draws whole from the old pane's
 * edge. The Remove folder and Reset dialogs are drawn over the whole stage.
 */
@Composable
internal fun SettingsSplitScreen(state: AppState, s: Screen, selection: State<ListSelection>, page: @Composable () -> Unit) {
    val categories = state.settingsSections()   // not remembered: a second screen coming or going changes it
    val root = s as? Screen.SettingsMenu
    SettingsStage(
        title = SETTINGS_TITLE,
        words = (root?.sections ?: categories).map { it.label },
        lines = emptyList(),
        current = { if (root != null) selection.value.index else categories.indexOf(settingsCategoryOf(s)).coerceAtLeast(0) },
        root = root != null,
        onWordTap = state::settingsCategoryTap,
        blockPage = settingsWideBlockPage(s),
        onHint = state::onButton,
        preview = {
            WithIndex(selection) { i ->
                root?.sections?.getOrNull(i)?.let { section ->
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
        },
        page = page,
        over = {
            // Over the whole stage, so its scrim takes every tap until it is answered.
            if ((s as? Screen.Settings)?.confirmRemove != null) SettingsRemoveDialog(state::onButton)
            (s as? Screen.Settings)?.takeIf { it.confirmReset }?.let { settingsResetAsk(it, selection.value.index) }?.let { SettingsResetDialog(it, state::onButton) }
        },
    )
}

/**
 * Settings' wide stage: one stage, nothing above but the small mark (the background's). The word block ([title] and
 * [words], [SettingsRootScreen]) rests bottom-left, the status [lines] at the left pane's top; the page is the rows block
 * ([LocalSettingsBlock], while [blockPage]) in a fixed frame at the right ([settingsFrameWidth], [settingsFrameLeft]: up
 * to 560 dp, its right edge the gutter in), on the word block's baseline and growing up to 24 dp under the top, then
 * scrolling; without [blockPage] the page draws whole from [SETTINGS_SPLIT_LEFT]. On the [root] the words have the
 * focus, [current] is the highlighted word and the right side is its [preview]; on a page the right side is the [page]
 * and [current] drops to the open grey with a "›". [current] is read only by the word block ([WithCurrent]), so a
 * d-pad move on the root recomposes the words and the preview, not the stage. A tap on a word calls [onWordTap]. The
 * root's hints are drawn on the left ([onHint] answers a tap on one), a page's on the right; both sides keep the bar's
 * height, so the blocks share the baseline. [over] is drawn over the whole stage (a dialog's scrim takes every tap).
 */
@Composable
internal fun SettingsStage(
    title: String, words: List<String>, lines: List<String>, current: () -> Int, root: Boolean,
    onWordTap: (Int) -> Unit, blockPage: Boolean, onHint: (NxButton) -> Unit,
    preview: @Composable () -> Unit, page: @Composable () -> Unit, over: @Composable () -> Unit = {},
) {
    val gutter = settingsPlateEnd()   // the right side's plate edge: the frame's and the picture's right edge
    WithSettingsWordBlockSize(title, words) { block ->
        val sectionRight = settingsSectionStart() + block.width
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalNxHintBarKeepHeight provides true) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val left = if (blockPage) settingsFrameLeft(maxWidth, sectionRight, gutter) else maxWidth * SETTINGS_SPLIT_LEFT
                    // A phone on its side: a Layouts group's picture takes the whole room right of the sections.
                    val pictureWidth = settingsLayoutPictureWidth(settingsSizeClass(), maxWidth, sectionRight, gutter)
                    val wideBlock = remember(pictureWidth, block.height) { SettingsBlockMode(crumb = null, pictureWidth = pictureWidth, minHeight = block.height) }
                    // The word block keeps its gutter on the right even when the frame comes within 24 dp of it.
                    Column(Modifier.width(if (blockPage) maxOf(left, sectionRight + gutter) else left).fillMaxHeight()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            // On a page it has the focus: the current word drops to the open grey, with its "›" ([openedLabel]).
                            WithCurrent(current) { SettingsRootScreen(title, words, lines, it, onWordTap, rowsFocused = !root, clearMark = false) }
                        }
                        // The root shows its A/B hints here; a page shows its own on the right. Either way this bar keeps the room, so the baselines meet.
                        NxHintBar(if (root) SETTINGS_ROOT_HINTS else emptyList(), onHint = onHint)
                    }
                    Box(Modifier.padding(start = left).fillMaxSize()) {
                        // A page's own hint bar on the right: from its rows' text edge (the left column already keeps the
                        // room between the panes, so no section-words inset on this side), the gutter on the screen's side.
                        CompositionLocalProvider(
                            LocalSettingsBlock provides wideBlock.takeIf { blockPage },
                            LocalNxHintBarStart provides NxOptionsInnerPad, LocalNxHintBarEnd provides gutter,
                        ) {
                            if (root) preview() else page()
                        }
                    }
                }
            }
            over()
        }
    }
}

/** [content] with [current]'s value, read in this scope only (as [WithIndex]). */
@Composable
private fun WithCurrent(current: () -> Int, content: @Composable (Int) -> Unit) = content(current())

/**
 * The two-pane frame on a wide landscape window ([settingsSplit]): [left] in [SETTINGS_SPLIT_LEFT] of the width, a 1 dp
 * divider at 8%, [right] in the rest, then [over] drawn over both panes (a dialog's scrim covers the whole window).
 * Both panes' hint bars keep their height, so their rows stay level when only the focused one shows hints. Used by the
 * column browsers (RetroAchievements, Game Tracker) and the in-game menu, which passes its
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
