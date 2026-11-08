package com.nxredux.mobile.ui.nx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** The panel's share of the page's width: 60% of a portrait screen (the list shows beside it), under half a landscape one. */
private const val PANEL_SHARE_PORTRAIT = 0.6f
private const val PANEL_SHARE_LANDSCAPE = 0.45f
private const val SLIDE_MS = 220

/**
 * A panel sliding in from the right over a game list, its full height, while [visible]: the modals' scrim behind it
 * (a tap on the scrim calls [onDismiss]), the panel 60% of a portrait screen's width and 45% of a landscape one's.
 * Taps inside stay inside. [content] keeps drawing while it slides out, so the caller keeps what it last showed.
 */
@Composable
fun SlidePanel(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // The modals' scrim over the list behind; a tap on it closes the panel, as on a modal.
    AnimatedVisibility(visible, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(SLIDE_MS)), exit = fadeOut(tween(SLIDE_MS))) {
        Box(Modifier.fillMaxSize().background(LocalNxColors.current.scrimModal).pointerInput(Unit) { detectTapGestures { onDismiss() } })
    }
    AnimatedVisibility(
        visible, modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(tween(SLIDE_MS)) { it }, exit = slideOutHorizontally(tween(SLIDE_MS)) { it },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The device's orientation, not this box's shape: a portrait page above the menu pad (or on a near-square
            // screen) is wide and short, yet a portrait one.
            val share = if (LocalPortrait.current) PANEL_SHARE_PORTRAIT else PANEL_SHARE_LANDSCAPE
            Column(
                Modifier.align(Alignment.TopEnd).width(maxWidth * share).fillMaxHeight()
                    .background(LocalNxColors.current.modalPanel, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .pointerInput(Unit) { detectTapGestures { } },   // taps inside stay inside
                content = content,
            )
        }
    }
}
