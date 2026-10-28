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
 * Tools -> Settings: the section block ([SettingsSectionBlock]) alone, anchored bottom-left at the gutter and
 * [SETTINGS_BLOCK_BOTTOM] up, the rest of the screen empty (the mark is the background's, [SettingsBackground]). A tap on
 * a section opens it ([onTap]); A opens, B goes back to Tools. [rowsFocused]: the two-pane Settings' left pane while
 * its page has the focus (the current section drops to the main ink). [hintBar]: draw the root's A/B hints below the
 * block ([onHint] answers a tap on one); the wide Settings draws its own bar, so it passes false.
 */
@Composable
fun SettingsMenuScreen(
    screen: Screen.SettingsMenu, selectedIndex: Int,
    onTap: (Int) -> Unit, modifier: Modifier = Modifier,
    rowsFocused: Boolean = false, hintBar: Boolean = false, onHint: ((NxButton) -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SettingsSectionBlock(
                screen.sections, selectedIndex, rowsFocused, onTap,
                Modifier.align(Alignment.BottomStart)
                    .padding(start = settingsSectionStart(), end = settingsGutter(settingsSizeClass()), bottom = SETTINGS_BLOCK_BOTTOM),
            )
        }
        if (hintBar) NxHintBar(SETTINGS_ROOT_HINTS, onHint = onHint)
    }
}
