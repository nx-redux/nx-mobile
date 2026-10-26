package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

/**
 * A confirm (with [onCancel]: B cancels, A confirms) or an info dialog (A or a scrim tap dismisses), in the shared modal
 * frame. [scrimDismisses] false: a scrim tap does nothing (an answer the user must choose). [scrollSteps]: the host's
 * d-pad up / down for a text taller than the room ([NxModal]).
 */
@Composable
fun NxDialog(
    title: String, text: String, onConfirm: () -> Unit, confirmLabel: String = "OK", onCancel: (() -> Unit)? = null, cancelLabel: String = "Cancel",
    scrimDismisses: Boolean = true, scrollSteps: Flow<Int>? = null,
) {
    val colors = LocalNxColors.current
    NxModal(
        caption = title, kind = if (onCancel != null) ModalKind.CONFIRM else ModalKind.INFO, confirmLabel = confirmLabel, cancelLabel = cancelLabel, scrimDismisses = scrimDismisses,
        scrollSteps = scrollSteps,
        onButton = { if (it == NxButton.B) onCancel?.invoke() else onConfirm() },
    ) {
        Text(text, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = nxDialogBody())
    }
}

/**
 * A choice among [choices], each a full-width button in the shared modal frame, for touch first: a tap on a
 * button calls [onChoose] with its index, a tap on the scrim (or B) [onCancel]. [selected] is the highlighted button,
 * held by the caller so a pad-driven host moves it with its own input (UP/DOWN, A [onChoose]); a menu, it has no hints.
 * [text], when not blank, sits above the buttons.
 */
@Composable
fun NxChoiceDialog(title: String, text: String, choices: List<String>, selected: Int, onChoose: (Int) -> Unit, onCancel: () -> Unit) {
    val colors = LocalNxColors.current
    NxModal(
        caption = title, kind = ModalKind.MENU,
        onButton = { if (it == NxButton.B) onCancel() else if (it == NxButton.A && selected in choices.indices) onChoose(selected) },
    ) {
        if (text.isNotBlank()) {
            Text(text, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = nxDialogBody())
            Spacer(Modifier.height(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.forEachIndexed { i, label -> NxChoiceButton(label, i == selected) { onChoose(i) } }
        }
    }
}

/** One [NxChoiceDialog] button, 48dp tall: the highlighted one filled as the list pill, the others outlined. */
@Composable
private fun NxChoiceButton(label: String, highlighted: Boolean, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    val base = Modifier.fillMaxWidth().height(48.dp).clip(shape).clickable(onClick = onClick)
    val styled = if (highlighted) base.background(colors.main, shape) else base.border(1.dp, colors.main.copy(alpha = 0.3f), shape)   // role: an outlined button's ring
    Box(styled, contentAlignment = Alignment.Center) {
        Text(label, color = if (highlighted) colors.listTextSelected else colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = nxModalButton(), maxLines = 1)
    }
}
