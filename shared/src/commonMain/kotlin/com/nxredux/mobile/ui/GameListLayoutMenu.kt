package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.nxModalButton
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT
import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.NxModalPanelColor
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.TOP_ICON_INSET

/** A dropdown row's height: a List pill's, slim, so all eight fit a phone's landscape height or scroll ([GameListLayoutMenu]). */
private val ROW_H = 32.dp

/** The room the panel keeps under itself in the page, dp. */
private val BOTTOM_ROOM = 8.dp

/**
 * The Layout icon's dropdown, under the title row's icons at the page's top right: every [GameListLayouts]
 * choice, [current] (the one in use) ticked, [highlighted] (the d-pad's) on the accent pill, as a List's. Taller than
 * the page leaves it, it scrolls, keeping the highlighted row in view. A tap picks a choice ([onPick]); a tap anywhere
 * else closes it ([onDismiss]), as B does.
 */
@Composable
fun GameListLayoutMenu(current: Int, highlighted: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val k = nxTextScale()   // Appearance → Text size: the rows and their words
    val rowH = ROW_H * k
    // The d-pad's row in view: scrolled just enough to show it whole.
    LaunchedEffect(highlighted, scroll.maxValue, rowH) {
        val pitch = with(density) { rowH.roundToPx() }
        val top = highlighted * pitch
        val view = scroll.viewportSize
        if (view > 0) when {
            top < scroll.value -> scroll.animateScrollTo(top)
            top + pitch > scroll.value + view -> scroll.animateScrollTo(top + pitch - view)
        }
    }
    // A tap gesture, not clickable: no full-screen ripple. Taps inside the panel stay there.
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Column(
            Modifier.align(Alignment.TopEnd)
                .padding(top = MENU_TAB_ROW_HEIGHT, end = (if (LocalPortrait.current) 16.dp else 24.dp) - TOP_ICON_INSET)
                .width(200.dp).heightIn(max = (maxHeight - MENU_TAB_ROW_HEIGHT - BOTTOM_ROOM).coerceAtLeast(rowH))
                .background(NxModalPanelColor, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(6.dp)
                .verticalScroll(scroll),
        ) {
            GameListLayouts.all.forEachIndexed { i, layout ->
                val lit = i == highlighted
                val ink = if (lit) colors.onAccent else colors.listText
                Box(
                    Modifier.fillMaxWidth().height(rowH).pointerInput(i) { detectTapGestures { onPick(i) } },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // The pill hugs the label (and the tick), as a List's.
                    Row(
                        Modifier.background(if (lit) colors.accent else Color.Transparent, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(layout.label, color = ink, fontFamily = font, fontSize = nxModalButton(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (i == current) Text("  ✓", color = ink, fontFamily = font, fontSize = nxModalButton(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
