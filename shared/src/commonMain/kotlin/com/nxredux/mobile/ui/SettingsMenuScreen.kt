package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nxredux.mobile.ui.nx.LocalFooterMode

/**
 * Tools -> Settings: the section block ([SettingsSectionBlock]) alone, anchored bottom-left at the gutter and
 * [SETTINGS_BLOCK_BOTTOM] up, the rest of the screen empty (the mark is the background's, [SettingsBackground]). A tap on
 * a section selects it, a tap on the selected one opens it ([onTap]); A opens, B goes back to Tools. "Back" is drawn
 * in touch mode only ([settingsBackShown]), and calls [onBack]. [rowsFocused]: the two-pane Settings' left pane while
 * its page has the focus (the current section drops to the main ink).
 */
@Composable
fun SettingsMenuScreen(
    screen: Screen.SettingsMenu, selectedIndex: Int,
    onTap: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier,
    rowsFocused: Boolean = false,
) {
    val back = onBack.takeIf { settingsBackShown(LocalFooterMode.current) }
    Box(modifier.fillMaxSize()) {
        SettingsSectionBlock(
            screen.sections, selectedIndex, rowsFocused, onTap, back,
            Modifier.align(Alignment.BottomStart)
                .padding(start = settingsGutter(settingsSizeClass()), end = settingsGutter(settingsSizeClass()), bottom = SETTINGS_BLOCK_BOTTOM),
        )
    }
}
