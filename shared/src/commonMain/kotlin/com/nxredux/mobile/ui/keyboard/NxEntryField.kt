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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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
import com.nxredux.mobile.ui.nx.nxModalSmall
import com.nxredux.mobile.ui.nx.nxModalText

/**
 * What the prompts need of the NX keyboard: the open prompt's [entry] (null: the platform's keyboard types, the prompts
 * keep their text fields), a tap on a field ([tapField]), the box's own buttons answering past the keyboard ([answer])
 * and a password's Show / Hide ([toggleReveal]). [nx]: the NX keyboard is the one that types here. Its entry reaches a
 * prompt a moment apart from the prompt itself (opening, and again closing), and a prompt that fell back to its own
 * text field for that moment would bring the platform's keyboard up: it draws nothing instead.
 */
class PromptKeyboard(
    val entry: TextEntry?, val tapField: (Int) -> Unit, val answer: (NxButton) -> Unit, val toggleReveal: () -> Unit = {},
    /** The box's confirm word, as the keyboard's hints say it ("Search", "Sign in", "OK"). */
    val confirmLabel: String = "OK",
    val nx: Boolean = false,
)

/** The app's [PromptKeyboard]; none outside the menu. */
val LocalPromptKeyboard = compositionLocalOf { PromptKeyboard(null, {}, {}) }

private const val PASSWORD_DOT = '•'

/**
 * A prompt's field while the NX keyboard types it: the text as the keyboard edits it (dots for a [password]), no
 * platform text field, so Android's keyboard never opens. A blinking caret at [cursor] (null: not the active field)
 * and an outline round the field typed in; the outline at full strength while the pad's highlight is in it
 * ([focused]); it scrolls to keep the caret in sight. A tap makes it the field typed in.
 */
@Composable
fun NxEntryField(
    value: String, password: Boolean, cursor: Int?, focused: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier,
    revealed: Boolean = false, onReveal: (() -> Unit)? = null,
) {
    val colors = LocalNxColors.current
    val shown = if (password && !revealed) PASSWORD_DOT.toString().repeat(value.length) else value
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    // Read in the draw only, so the blink redraws the caret and never recomposes the field; no caret, no blink.
    val blink = if (cursor == null) null else rememberInfiniteTransition(label = "caret").animateFloat(
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
    Row(
        modifier.fillMaxWidth().background(colors.fieldGround, shape)
            .then(if (focused) Modifier.border(2.dp, colors.listText, shape) else if (cursor != null) Modifier.border(2.dp, colors.inkOpen, shape) else Modifier)
            .pointerInput(onTap) { detectTapGestures { onTap() } }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).onSizeChanged { viewport.value = it.width }.horizontalScroll(scroll)) {
            Text(
                shown, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = nxModalText(), maxLines = 1, softWrap = false,
                onTextLayout = { layout = it },
                // Room past the last character, so a caret at the end can scroll inside the field's edge, not onto it.
                modifier = Modifier.padding(end = 8.dp).drawWithContent {
                    drawContent()
                    val l = layout
                    if (cursor != null && l != null && blink != null) {
                        val r = l.getCursorRect(cursor.coerceIn(0, shown.length))
                        // role: the caret's blink, the field text's colour fading in and out
                        drawLine(colors.listText.copy(alpha = blink.value), Offset(r.left, r.top), Offset(r.left, r.bottom), strokeWidth = 2f)
                    }
                },
            )
        }
        // A password's Show / Hide: the typed text as it is, for checking it before signing in.
        if (onReveal != null) Text(
            if (revealed) "Hide" else "Show", color = colors.hint, fontFamily = LocalNxFont.current, fontSize = nxModalSmall(),
            modifier = Modifier.padding(start = 12.dp).pointerInput(onReveal) { detectTapGestures { onReveal() } },
        )
    }
}
