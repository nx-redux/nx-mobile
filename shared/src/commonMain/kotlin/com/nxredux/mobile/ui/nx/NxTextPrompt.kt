package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * NX-styled one-line text prompt (Rename Rom, New Collection): a text field in the shared modal frame that takes focus
 * and the soft keyboard on open. Done on the keyboard or A confirms [value] as the host holds it; B or the scrim cancels.
 */
@Composable
fun NxTextPrompt(title: String, value: String, onValueChange: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalNxColors.current
    val focus = remember { FocusRequester() }
    // The field keeps its own cursor, starting at the end; a [value] the host changed itself (not an echo of our edit) re-seeds it.
    var field by rememberNxPromptField(value)
    LaunchedEffect(Unit) { focus.requestFocus() }
    NxModal(caption = title, kind = ModalKind.PROMPT, onButton = { if (it == NxButton.B) onCancel() else onConfirm() }) {
        BasicTextField(
            value = field,
            onValueChange = { new ->
                val cleaned = new.text.replace("\n", "").replace("\r", "").replace("\t", "")
                field = if (cleaned == new.text) new else new.copy(text = cleaned)
                onValueChange(field.text)
            },
            singleLine = true,
            textStyle = TextStyle(color = colors.listText, fontFamily = LocalNxFont.current, fontSize = nxModalText()),
            cursorBrush = SolidColor(colors.listText),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).background(Color(0xFF2A2A2A), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * Whether a prompt field showing [fieldText] re-seeds from the host's [value], [last] being the value it last saw: only
 * when [value] itself moved and is not the field's own text. A host lagging a keystroke behind (still holding the old
 * text while the field already has the new one) is not a change, so typing is never undone.
 */
internal fun nxPromptReseeds(fieldText: String, last: String, value: String): Boolean = value != last && value != fieldText

/**
 * A prompt field's own state (text and cursor, starting at the end of [value]), re-seeded per [nxPromptReseeds]. The
 * last seen value is recorded once the composition applies, so a discarded composition re-seeds again.
 */
@Composable
internal fun rememberNxPromptField(value: String): MutableState<TextFieldValue> {
    val field = remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val last = remember { arrayOf(value) }
    if (nxPromptReseeds(field.value.text, last[0], value)) field.value = TextFieldValue(value, TextRange(value.length))
    SideEffect { last[0] = value }
    return field
}
