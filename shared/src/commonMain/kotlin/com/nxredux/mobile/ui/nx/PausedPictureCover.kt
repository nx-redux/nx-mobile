package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Over the paused game picture while the in-game menu is in the dual-screen panel on the other display: a see-through
 * shade and "Paused" in the middle; both lift ([dimmed] off) while a row that changes the picture is highlighted, so the
 * change shows as it is made. It takes every touch either way: the paused core gets none.
 */
@Composable
fun PausedPictureCover(dimmed: Boolean) {
    val colors = LocalNxColors.current
    Box(
        Modifier.fillMaxSize().then(if (dimmed) Modifier.background(colors.scrimPaused) else Modifier)
            .pointerInput(Unit) { detectTapGestures { /* swallow */ } },
        contentAlignment = Alignment.Center,
    ) {
        if (dimmed) Text("Paused", color = colors.listText, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal, fontSize = 32.sp)
    }
}
