package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar
import com.nxredux.mobile.ui.nx.nxPageInset
import com.nxredux.mobile.ui.nx.nxScrollFade

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
 * A root page in the Settings style (Settings, Tools → Artwork): its word block ([SettingsWordBlock] with [title] and
 * [words]) anchored bottom-left at the gutter and [settingsBlockBottom] up; the status [lines] ([SettingsStatusLines])
 * at the top left, level with the mark ([SETTINGS_MARK_TOP] down) and kept clear of it while [clearMark] (the wide
 * stage's left pane never reaches it); [headline] and [description]: a tool's head in the block ([SettingsWordHead]). On a short screen the lines push the block down, never draw over it. The rest
 * of the screen is empty (the mark is the background's, [SettingsBackground]). A tap on a word calls [onTap];
 * A opens, B goes back to Tools. [rowsFocused]: the two-pane stage's left pane while its page has the focus (the current
 * word drops to the open grey with a "›"). [hintBar]: draw the page's [hints] (the root's A/B, [SETTINGS_ROOT_HINTS]) below the block
 * ([onHint] answers a tap on one); the wide stage draws its own bar, so it passes false.
 */
@Composable
fun SettingsRootScreen(
    title: String, words: List<String>, lines: List<String>, selectedIndex: Int,
    onTap: (Int) -> Unit, modifier: Modifier = Modifier,
    rowsFocused: Boolean = false, hintBar: Boolean = false, onHint: ((NxButton) -> Unit)? = null,
    clearMark: Boolean = true, headline: String? = null, description: List<String> = emptyList(),
    hints: List<Hint> = SETTINGS_ROOT_HINTS, extra: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        // The block bottom-anchored (the lines at the top) while it fits; where it can't (a skin's small touch screen),
        // the page scrolls under the thin fade, the current word kept in view ([SettingsWordBlock]).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxWidth().nxScrollFade(scroll, SETTINGS_ROOT_FADE).verticalScroll(scroll)
                    .heightIn(min = maxHeight),
                verticalArrangement = if (lines.isEmpty()) Arrangement.Bottom else Arrangement.SpaceBetween,
            ) {
                if (lines.isNotEmpty()) SettingsStatusLines(
                    lines,
                    Modifier.padding(
                        start = settingsSectionStart(), top = SETTINGS_MARK_TOP,
                        end = if (clearMark) nxPageInset().end + SETTINGS_MARK + SETTINGS_MARK_TOP else settingsPlateEnd(),
                    ),
                )
                SettingsWordBlock(
                    title, words, selectedIndex, rowsFocused, onTap,
                    Modifier.padding(start = settingsSectionStart(), end = settingsPlateEnd(), bottom = settingsBlockBottom()),
                    headline = headline, description = description, extra = extra,
                )
            }
        }
        if (hintBar) NxHintBar(hints, onHint = onHint)
    }
}

/** The root page's "more this way" fade where its block scrolls ([SettingsRootScreen]): a sliver, as the menu lists'. */
private val SETTINGS_ROOT_FADE = 14.dp
