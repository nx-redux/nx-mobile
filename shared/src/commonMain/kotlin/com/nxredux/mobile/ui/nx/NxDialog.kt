package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.SetupButtons
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
 * A choice among [choices], side by side as setup's buttons ([SetupButtons]: the words, the highlighted one on the
 * accent pill) in the shared modal frame: a tap on one calls [onChoose] with its index, a tap on the scrim (or B)
 * [onCancel]. [selected] is the highlighted button, held by the caller so a pad-driven host moves it with its own input
 * (LEFT/RIGHT or UP/DOWN, A [onChoose]); a menu, it has no hints. [text], when not blank, sits above the buttons.
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
        SetupButtons(choices, 0, selected, NxTextSizes.listLabel(NxListPitch) * nxTextScale(), onSelect = {}, onOpen = onChoose)
    }
}
