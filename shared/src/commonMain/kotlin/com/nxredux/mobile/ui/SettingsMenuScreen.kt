package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar

/** The Settings root's pad hints: A opens the section, B goes back to Tools. */
internal val SETTINGS_ROOT_HINTS = listOf(Hint(NxButton.A, "Open"), Hint(NxButton.B, "Back"))

/**
 * Tools -> Settings: the section block ([SettingsSectionBlock]) alone, as [SettingsRootScreen] draws it. A tap on a
 * section opens it ([onTap]); A opens, B goes back to Tools. [rowsFocused], [hintBar] and [onHint]: as [SettingsRootScreen].
 */
@Composable
fun SettingsMenuScreen(
    screen: Screen.SettingsMenu, selectedIndex: Int,
    onTap: (Int) -> Unit, modifier: Modifier = Modifier,
    rowsFocused: Boolean = false, hintBar: Boolean = false, onHint: ((NxButton) -> Unit)? = null,
) = SettingsRootScreen(
    SETTINGS_TITLE, screen.sections.map { it.label }, emptyList(), selectedIndex, onTap, modifier,
    rowsFocused = rowsFocused, hintBar = hintBar, onHint = onHint,
)

/**
 * A root page in the Settings style (Settings, Tools → Artwork): its word block ([SettingsWordBlock] with [title],
 * [words] and the status [lines] above the title) alone, anchored bottom-left at the gutter and [SETTINGS_BLOCK_BOTTOM]
 * up, the rest of the screen empty (the mark is the background's, [SettingsBackground]). A tap on a word calls [onTap];
 * A opens, B goes back to Tools. [rowsFocused]: the two-pane stage's left pane while its page has the focus (the current
 * word drops to the open grey with a "›"). [hintBar]: draw the root's A/B hints ([SETTINGS_ROOT_HINTS]) below the block
 * ([onHint] answers a tap on one); the wide stage draws its own bar, so it passes false.
 */
@Composable
fun SettingsRootScreen(
    title: String, words: List<String>, lines: List<String>, selectedIndex: Int,
    onTap: (Int) -> Unit, modifier: Modifier = Modifier,
    rowsFocused: Boolean = false, hintBar: Boolean = false, onHint: ((NxButton) -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SettingsWordBlock(
                title, words, selectedIndex, rowsFocused, onTap,
                Modifier.align(Alignment.BottomStart)
                    .padding(start = settingsSectionStart(), end = settingsPlateEnd(), bottom = SETTINGS_BLOCK_BOTTOM),
                lines = lines,
            )
        }
        if (hintBar) NxHintBar(SETTINGS_ROOT_HINTS, onHint = onHint)
    }
}
