package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Where the selected options row's value sits on screen ([bounds], screen px), so a value dropdown opens at it. */
class NxValueAnchor { var bounds by mutableStateOf<Rect?>(null) }

/** The screen's [NxValueAnchor], provided by App; null where no dropdown opens (the selected row reports nothing). */
val LocalNxValueAnchor = staticCompositionLocalOf<NxValueAnchor?> { null }

/** A dropdown row's height. */
private val DROPDOWN_ROW = 40.dp
/** Rows shown before the dropdown scrolls. */
private const val DROPDOWN_ROWS = 6
/** The least room kept between the dropdown and the screen's edges, px. */
private const val DROPDOWN_MARGIN = 8

/**
 * A value row's choices as a dropdown at its value ([anchor], screen px) over the modal's dim: right-aligned to the
 * value, under it, or over it when there is no room below. Every one of [labels], the [selected] one lit; a tap picks
 * it ([onPick]); a tap on the dim or Back closes it ([onDismiss]). Long lists scroll after [DROPDOWN_ROWS].
 */
@Composable
fun NxValueDropdown(labels: List<String>, selected: Int, anchor: Rect?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(12.dp)
    val k = nxTextScale()   // Appearance → Text size: the rows and their words
    val rowH = DROPDOWN_ROW * k
    var origin by remember { mutableStateOf(Offset.Zero) }   // the dim's own place on screen (its window may sit under the status bar)
    val atTopLeft = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
    }
    Popup(popupPositionProvider = atTopLeft, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        // The modal's dim over the whole screen; a tap on it closes the dropdown.
        Box(
            Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }.background(NxModalScrimColor)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            Layout(content = {
                Column(
                    // As wide as its longest value (within the bounds), not the widest it may be; taps on it stay on it.
                    Modifier.width(IntrinsicSize.Max).widthIn(min = 160.dp, max = 300.dp).heightIn(max = rowH * DROPDOWN_ROWS + 12.dp)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .clip(shape).background(NxModalCardColor, shape).border(1.dp, colors.hint.copy(alpha = 0.35f), shape)
                        .verticalScroll(rememberScrollState()).padding(6.dp),
                ) {
                    labels.forEachIndexed { i, label ->
                        val lit = i == selected
                        Box(
                            Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(8.dp))
                                .then(if (lit) Modifier.background(colors.main) else Modifier)
                                .clickable { onPick(i) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                label, color = if (lit) colors.listTextSelected else colors.listText, fontFamily = LocalNxFont.current,
                                fontWeight = FontWeight.SemiBold, fontSize = nxModalText(), maxLines = 1,
                            )
                        }
                    }
                }
            }) { measurables, constraints ->
                val menu = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
                val w = constraints.maxWidth; val h = constraints.maxHeight
                val a = anchor?.translate(-origin.x, -origin.y)
                val x = if (a == null) (w - menu.width) / 2
                    else (a.right.toInt() - menu.width).coerceIn(DROPDOWN_MARGIN, maxOf(DROPDOWN_MARGIN, w - menu.width - DROPDOWN_MARGIN))
                val y = when {
                    a == null -> (h - menu.height) / 2
                    a.bottom.toInt() + menu.height <= h - DROPDOWN_MARGIN -> a.bottom.toInt()
                    else -> (a.top.toInt() - menu.height).coerceAtLeast(DROPDOWN_MARGIN)
                }
                layout(w, h) { menu.place(x, y) }
            }
        }
    }
}
