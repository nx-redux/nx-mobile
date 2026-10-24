package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The RetroAchievements sign-in: a username and a masked password field in the shared modal frame. The username takes
 * focus and the soft keyboard on open; Next moves to the password, Done there or A signs in with both as the host holds
 * them; B or the scrim cancels.
 */
@Composable
fun NxSignInPrompt(
    username: String, password: String,
    onUsernameChange: (String) -> Unit, onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit, onCancel: () -> Unit,
    caption: String = "Sign in to RetroAchievements",
) {
    val colors = LocalNxColors.current
    val userFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { userFocus.requestFocus() }
    NxModal(
        caption = caption, kind = ModalKind.PROMPT, confirmLabel = "Sign in",
        onButton = { if (it == NxButton.B) onCancel() else onConfirm() },
    ) {
        Text("Username", color = colors.hint, fontFamily = LocalNxFont.current, fontSize = nxModalSmall())
        Spacer(Modifier.height(4.dp))
        SignInField(
            username, onUsernameChange, password = false, focus = userFocus,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
        )
        Spacer(Modifier.height(12.dp))
        Text("Password", color = colors.hint, fontFamily = LocalNxFont.current, fontSize = nxModalSmall())
        Spacer(Modifier.height(4.dp))
        SignInField(
            password, onPasswordChange, password = true, focus = passwordFocus,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        )
    }
}

/** One field, styled like [NxTextPrompt]'s: keeps its own cursor, strips line breaks and tabs, reports every change. */
@Composable
private fun SignInField(
    value: String, onValueChange: (String) -> Unit, password: Boolean, focus: FocusRequester,
    keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions,
) {
    val colors = LocalNxColors.current
    // A [value] the host changed itself (not an echo of our edit) re-seeds the field with the cursor at the end.
    var field by rememberNxPromptField(value)
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
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).background(Color(0xFF2A2A2A), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
