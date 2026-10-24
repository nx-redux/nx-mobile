package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LoadingScrim = Color(0xE6000000)   // 90% black: darker than NxModal's, so the message reads over list text

/**
 * A screen load past its grace period (AppState.loading): the current screen dimmed under a spinner and [message]
 * ("Loading…" unless the load names itself). The scrim swallows taps so no row underneath opens. There is no visible
 * hint: B / system Back cancel the load silently, or do nothing for one that cannot stop (AppState / ScreenLoader).
 */
@Composable
fun NxLoadingOverlay(message: String = "Loading…", modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    BoxWithConstraints(
        modifier.fillMaxSize().background(LoadingScrim).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        // The message is a plain list row's label (~20 sp); the spinner follows it.
        val label = nxListLabelSize()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                Modifier.size((label.value * 1.6f).dp), color = colors.hint, trackColor = Color.Transparent,
                strokeWidth = (label.value * 0.18f).dp,
            )
            Spacer(Modifier.height((label.value * 0.8f).dp))
            Text(message, color = colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = label)
        }
    }
}
