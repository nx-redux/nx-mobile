package com.nxredux.mobile.ui.keyboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.nxModalText

/**
 * What the prompts need of the NX keyboard: the open prompt's [entry] (null: the platform's keyboard types, the prompts
 * keep their text fields), a tap on a field ([tapField]) and the box's own buttons answering past the keyboard ([answer]).
 */
class PromptKeyboard(val entry: TextEntry?, val tapField: (Int) -> Unit, val answer: (NxButton) -> Unit)

/** The app's [PromptKeyboard]; none outside the menu. */
val LocalPromptKeyboard = compositionLocalOf { PromptKeyboard(null, {}, {}) }

private const val PASSWORD_DOT = '•'

/**
 * A prompt's field while the NX keyboard types it: the text as the keyboard edits it (dots for a [password]), no
 * platform text field, so Android's keyboard never opens. A blinking caret at [cursor] (null: not the active field);
 * the selection outline while the pad's highlight is in it ([focused]); it scrolls to keep the caret in sight. A tap
 * makes it the field typed in.
 */
@Composable
fun NxEntryField(value: String, password: Boolean, cursor: Int?, focused: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    val shown = if (password) PASSWORD_DOT.toString().repeat(value.length) else value
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f, infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse), label = "caret",
    )
    val shape = RoundedCornerShape(12.dp)
    val viewport = remember { mutableStateOf(0) }
    val caretPad = with(LocalDensity.current) { 8.dp.toPx() }
    // Keep the caret inside the visible part of a value wider than the field.
    LaunchedEffect(cursor, layout, viewport.value) {
        val l = layout ?: return@LaunchedEffect
        val c = cursor ?: return@LaunchedEffect
        val x = l.getCursorRect(c.coerceIn(0, shown.length)).left
        val w = viewport.value
        if (w <= 0) return@LaunchedEffect
        if (x - scroll.value > w - caretPad) scroll.scrollTo((x - w + caretPad).toInt().coerceAtLeast(0))
        else if (x < scroll.value) scroll.scrollTo((x - caretPad).toInt().coerceAtLeast(0))
    }
    Box(
        modifier.fillMaxWidth().background(colors.fieldGround, shape)
            .then(if (focused) Modifier.border(2.dp, colors.listText, shape) else Modifier)
            .pointerInput(onTap) { detectTapGestures { onTap() } }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().onSizeChanged { viewport.value = it.width }.horizontalScroll(scroll)) {
            Text(
                shown, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = nxModalText(), maxLines = 1, softWrap = false,
                onTextLayout = { layout = it },
                modifier = Modifier.drawWithContent {
                    drawContent()
                    val l = layout
                    if (cursor != null && l != null) {
                        val r = l.getCursorRect(cursor.coerceIn(0, shown.length))
                        // role: the caret's blink, the field text's colour fading in and out
                        drawLine(colors.listText.copy(alpha = blink), Offset(r.left, r.top), Offset(r.left, r.bottom), strokeWidth = 2f)
                    }
                },
            )
        }
    }
}
