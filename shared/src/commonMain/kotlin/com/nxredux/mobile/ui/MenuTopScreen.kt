package com.nxredux.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.background
import com.nxredux.mobile.shared.res.nx_logo
import com.nxredux.mobile.ui.keyboard.NxKeyboardBar
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.nxScreenInsets
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.LocalWindowSize
import com.nxredux.mobile.ui.nx.NxScreenBackground
import com.nxredux.mobile.ui.nx.NxTheme
import com.nxredux.mobile.ui.nx.footerModeFor
import com.nxredux.mobile.ui.nx.rememberControllerConnected
import com.nxredux.mobile.ui.nx.rememberHasTouchscreen
import org.jetbrains.compose.resources.painterResource

/**
 * The top screen while the main menu is on the second display ([AppState.dualMenu]): the same [AppState] as [App], drawn
 * by [DualMenu.top]: information about what the bottom screen highlights, never focused UI. It has the theme and the
 * launcher locals [App] has ([MenuLocals]), from the same settings; the image loader is App's (process-wide).
 * There is no pad here, so the footer follows a connected controller alone. [inSkin]: drawn in the menu skin's top glass
 * (two screens in one window), already inside the shell's insets.
 */
@Composable
fun MenuTopScreen(state: AppState, inSkin: Boolean = false) {
    val screen by state.screen.collectAsState()
    val dual by state.dualMenu.collectAsState()
    val selection by state.selection.collectAsState()
    val look = rememberMenuLook(state, screen)
    val controllerConnected = rememberControllerConnected()
    val touchscreen = rememberHasTouchscreen()
    NxTheme(colors = look.colors) {
        MenuLocals(state, look, footerModeFor(controllerConnected, padVisible = inSkin), touchscreen) {
            Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().background(LocalNxColors.current.background)
                    .let { if (inSkin) it else it.windowInsetsPadding(nxScreenInsets()) }.clipToBounds(),
            ) {
                // No HandheldShell here: the screen's own shape and size, as the shell gives App's pages.
                CompositionLocalProvider(LocalPortrait provides (maxHeight > maxWidth), LocalWindowSize provides DpSize(maxWidth, maxHeight)) {
                    when (menuTop(dual, screen, selection.index)) {
                        TopContent.Backdrop, is TopContent.GameDetails, is TopContent.ConsoleStats, is TopContent.CollectionStats -> Backdrop()
                    }
                }
            }
            }
        }
    }
}

/** [DualMenu.top] while the menu is on the second display ([dual]); else the backdrop. */
internal fun menuTop(dual: Boolean, screen: Screen, index: Int): TopContent =
    if (dual) DualMenu.top(screen, index) else TopContent.Backdrop

/** Every other page: the app backdrop, dimmed, with the NX logo in the middle. */
@Composable
private fun Backdrop() {
    Box(Modifier.fillMaxSize()) {
        NxScreenBackground(url = null, fallback = Res.drawable.background, fade = false, modifier = Modifier.fillMaxSize(), centred = true)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACKDROP_DIM)))   // role: the other pages' app backdrop dim, lighter than a game's scrimBackdrop
        Image(
            painterResource(Res.drawable.nx_logo), contentDescription = null, colorFilter = ColorFilter.tint(LocalNxColors.current.listText),
            modifier = Modifier.align(Alignment.Center).size(width = 96.dp, height = 92.dp),   // the mark's 80:77
        )
    }
}

private const val BACKDROP_DIM = 0.5f
