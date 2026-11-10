package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Over the paused game picture while the in-game menu is in the dual-screen panel on the other display: a see-through
 * shade and a pause mark in the middle; both lift ([dimmed] off) while a row that changes the picture is highlighted, so the
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
        if (dimmed) Row(horizontalArrangement = Arrangement.spacedBy(PAUSE_GAP)) {
            repeat(2) { Box(Modifier.size(PAUSE_BAR_W, PAUSE_BAR_H).background(colors.listText, RoundedCornerShape(PAUSE_BAR_W / 3))) }
        }
    }
}

private val PAUSE_BAR_W = 14.dp
private val PAUSE_BAR_H = 48.dp
private val PAUSE_GAP = 14.dp
