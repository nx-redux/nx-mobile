package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The pad's place among an open dialog's buttons ([NxDialog]): 0 the confirm (A's, where every dialog opens), 1 the
 * cancel (an info dialog has the one). A dialog registers while it shows ([opened], [closed]); the host runs each press through
 * [press] first, so the dialog has the focus alone and every screen's own A and B handling serves it unchanged.
 */
class DialogFocus {
    private var open by mutableIntStateOf(0)
    private var scrolling by mutableIntStateOf(0)   // open dialogs whose text scrolls by UP and DOWN
    private var buttons by mutableIntStateOf(2)     // the newest open dialog's buttons
    /** The lit button: 0 the confirm, 1 the cancel. */
    var index by mutableIntStateOf(0)
        private set

    /** A dialog with [count] buttons shows; [scrolls]: its host scrolls its text by UP and DOWN, so they pass. */
    fun opened(scrolls: Boolean = false, count: Int = 2) { open++; if (scrolls) scrolling++; buttons = count; index = 0 }
    fun closed(scrolls: Boolean = false) { open = (open - 1).coerceAtLeast(0); if (scrolls) scrolling = (scrolling - 1).coerceAtLeast(0); index = 0 }

    /** [button] as the screen under an open dialog should hear it (null: the dialog took it, [dialogFocusPress]); unchanged with none open. */
    fun press(button: NxButton): NxButton? {
        if (open == 0) return button
        val (next, out) = dialogFocusPress(index, button, scrolling > 0, buttons)
        index = next
        return out
    }
}

/**
 * A press on a two-button dialog lit at [index], the dialog having the focus alone: LEFT and RIGHT move the light, A on
 * the cancel is B, B passes; UP and DOWN pass only for a dialog whose text [scrolls]; every other button is taken (null),
 * so nothing under the dialog moves. Returns the new index and the button to pass on.
 */
internal fun dialogFocusPress(index: Int, button: NxButton, scrolls: Boolean = false, count: Int = 2): Pair<Int, NxButton?> = when (button) {
    NxButton.LEFT -> 0 to null
    NxButton.RIGHT -> (count - 1).coerceIn(0, 1) to null
    NxButton.A -> index to (if (index == 1) NxButton.B else NxButton.A)
    NxButton.B -> index to NxButton.B
    NxButton.UP, NxButton.DOWN -> index to button.takeIf { scrolls }
    else -> index to null
}

/** The launcher's [DialogFocus] ([com.nxredux.mobile.ui.AppState.dialogFocus]); null where no host runs presses through one. */
val LocalDialogFocus = staticCompositionLocalOf<DialogFocus?> { null }
