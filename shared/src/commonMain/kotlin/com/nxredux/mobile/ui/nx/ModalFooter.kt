package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.compositionLocalOf

/** How a modal's footer acts: tappable key hints (a controller or the on-screen pad is present) or real buttons. */
enum class FooterMode { HINTS, BUTTONS }

/** Hints whenever a pad exists to press them with; buttons only when there is none (landscape, no controller). */
fun footerModeFor(controllerConnected: Boolean, padVisible: Boolean): FooterMode =
    if (controllerConnected || padVisible) FooterMode.HINTS else FooterMode.BUTTONS

/** The footer mode in effect for the screen content; HINTS by default so an action is never hidden. */
val LocalFooterMode = compositionLocalOf { FooterMode.HINTS }

/**
 * [WAIT]: a page that waits on something else (Netplay's hosting and joining pages); B cancels it, A does nothing.
 * [BUSY]: a page nothing can act on any more (Netplay's "starting game"): no footer.
 */
enum class ModalKind { MENU, PROMPT, CONFIRM, INFO, WAIT, BUSY }

/** One on-screen button in BUTTONS mode; a tap sends [button] as if pressed. */
data class FooterAction(val button: NxButton, val label: String, val primary: Boolean)

/** One footer word ([NxModal]): its [label] and the [button] a tap (or A while it is lit) sends. */
data class FooterWord(val label: String, val button: NxButton)

/**
 * [footer] as side-by-side words, as setup's buttons and the in-game Quit dialog: the confirm (A) first, then the rest,
 * the words alone, no key glyphs: the pad lights one ([DialogFocus]) and A presses the lit one, so a key on a word would
 * contradict it; a tap presses it directly.
 */
fun footerWords(footer: ModalFooter): List<FooterWord> = when (footer) {
    ModalFooter.None -> emptyList()
    is ModalFooter.Hints -> footer.hints.sortedBy { if (it.button == NxButton.A) 0 else if (it.button == NxButton.B) 1 else 2 }
        .map { FooterWord(it.label, it.button) }
    is ModalFooter.Buttons -> footer.actions.sortedBy { if (it.primary) 0 else 1 }.map { FooterWord(it.label, it.button) }
}

sealed interface ModalFooter {
    data object None : ModalFooter
    data class Hints(val hints: List<Hint>) : ModalFooter
    data class Buttons(val actions: List<FooterAction>) : ModalFooter
}

/**
 * The footer for [kind] in [mode]: the menu none (its rows are the actions, A selecting and B going back as everywhere);
 * the others get cancel + confirm, info only confirm, wait only cancel.
 */
fun modalFooter(kind: ModalKind, mode: FooterMode, confirmLabel: String = "OK", cancelLabel: String = "Cancel"): ModalFooter = when (kind) {
    ModalKind.MENU -> ModalFooter.None
    ModalKind.PROMPT, ModalKind.CONFIRM -> when (mode) {
        FooterMode.HINTS -> ModalFooter.Hints(listOf(Hint(NxButton.B, cancelLabel), Hint(NxButton.A, confirmLabel)))
        FooterMode.BUTTONS -> ModalFooter.Buttons(listOf(FooterAction(NxButton.B, cancelLabel, primary = false), FooterAction(NxButton.A, confirmLabel, primary = true)))
    }
    ModalKind.INFO -> when (mode) {
        FooterMode.HINTS -> ModalFooter.Hints(listOf(Hint(NxButton.A, confirmLabel)))
        FooterMode.BUTTONS -> ModalFooter.Buttons(listOf(FooterAction(NxButton.A, confirmLabel, primary = true)))
    }
    ModalKind.WAIT -> when (mode) {
        FooterMode.HINTS -> ModalFooter.Hints(listOf(Hint(NxButton.B, cancelLabel)))
        FooterMode.BUTTONS -> ModalFooter.Buttons(listOf(FooterAction(NxButton.B, cancelLabel, primary = false)))
    }
    ModalKind.BUSY -> ModalFooter.None
}
