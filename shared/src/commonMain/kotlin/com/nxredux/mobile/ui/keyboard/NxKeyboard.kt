package com.nxredux.mobile.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.nxredux.mobile.ui.nx.nxKeyboardTop
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.AppState
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar
import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.touchOnly

/**
 * What the buttons do on the keyboard right now, for its hint row: on a key, typing and the shortcuts; in the field,
 * the cursor; on the box's buttons, A as that button's word ([confirmLabel] or Cancel). START (done) and B (cancel)
 * answer the box from anywhere, so they are in every row: the box's own buttons carry no glyphs.
 */
fun keyboardHints(entry: TextEntry, confirmLabel: String): List<Hint> {
    val answers = listOf(Hint(NxButton.START, confirmLabel), Hint(NxButton.B, "Cancel"))
    return when (val f = entry.focus) {
        is EntryFocus.Keys -> listOf(Hint(NxButton.A, "Type"), Hint(NxButton.X, "Delete"), Hint(NxButton.Y, "Space")) + answers
        // A password's field: A shows or hides it.
        EntryFocus.Field -> (if (entry.fields[entry.active].password) listOf(
            Hint(NxButton.LEFT, "Move cursor"), Hint(NxButton.A, if (entry.reveal) "Hide" else "Show"), Hint(NxButton.X, "Delete"), Hint(NxButton.Y, "Space"),
        ) else listOf(Hint(NxButton.LEFT, "Move cursor"), Hint(NxButton.X, "Delete"), Hint(NxButton.Y, "Space"))) + answers
        // On a button A is that button's word: the other answer keeps its own.
        is EntryFocus.Button -> if (f.index == 0) listOf(Hint(NxButton.A, confirmLabel), answers[1]) else listOf(Hint(NxButton.A, "Cancel"), answers[0])
    }
}

private fun label(key: Key, entry: TextEntry): String = when (key) {
    is Key.Char -> if (entry.shift) key.c.uppercaseChar().toString() else key.c.toString()
    Key.Shift -> "⇧"
    Key.Symbols -> if (entry.page == KeyPage.LETTERS) "#+=" else "abc"
    Key.Space -> "space"
    Key.Backspace -> "⌫"
    Key.Paste -> "Paste"
    Key.Next -> "Next"
    Key.Done -> "Done"
}

/** A key's height where the screen has the room. */
private val KEY_HEIGHT = 40.dp
/** The least a key shrinks to on a short screen: still a fingertip's target on a small handheld. */
private val KEY_MIN_HEIGHT = 22.dp
/** The share of the window's height the keyboard (keys and hint row) may take. */
private const val KEYBOARD_MAX_SHARE = 0.58f
/** The hint row's height under the keys, and the room over them. */
private val KEYBOARD_HINTS_ROOM = 40.dp
private val KEYBOARD_TOP = 8.dp

/**
 * The NX keyboard: [entry]'s page of keys, the pad's highlight lit while it is on a key, a tap moving it there and typing ([onKey]);
 * its hint row under the keys, a tapped hint sent as its button ([onHint]).
 */
@Composable
fun NxKeyboard(entry: TextEntry, onKey: (KeyPos) -> Unit, onHint: (NxButton) -> Unit, confirmLabel: String, modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    val rows = NxKeys.rows(entry.page, entry.twoFields)
    val lit = (entry.focus as? EntryFocus.Keys)?.at
    val gap = 6.dp
    // A short screen: the keys give way, so the prompt's box above them and the hint row under them both stay in sight.
    val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // With no pad there is no hint row under the keys: they keep the same room below as above.
    val under = if (touchOnly()) KEYBOARD_TOP else KEYBOARD_HINTS_ROOM * nxTextScale()
    val keyHeight = (KEY_HEIGHT * nxTextScale()).let { full ->
        if (window <= 0.dp) full
        else ((window * KEYBOARD_MAX_SHARE - under - KEYBOARD_TOP - gap * (rows.size - 1)) / rows.size).coerceIn(KEY_MIN_HEIGHT, full)
    }
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth().background(colors.modalPanel).navigationBarsPadding().padding(top = KEYBOARD_TOP), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = if (touchOnly()) KEYBOARD_TOP else 0.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { c, key ->
                        val on = lit == KeyPos(r, c)
                        Box(
                            Modifier.weight(NxKeys.width(key)).height(keyHeight)
                                .background(if (on) colors.main else colors.fieldGround, RoundedCornerShape(8.dp))
                                .pointerInput(r, c) {
                                    // A light tick under the finger, as a phone's keyboard gives (the system's haptics setting rules it).
                                    detectTapGestures { haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap); onKey(KeyPos(r, c)) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label(key, entry), color = if (on) colors.listTextSelected else colors.listText,
                                fontFamily = LocalNxFont.current, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 16.sp * nxTextScale(), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        NxHintBar(keyboardHints(entry, confirmLabel), onHint = onHint, always = true)
    }
}

/**
 * The NX keyboard while a prompt types through it ([AppState.textEntry]), nothing otherwise: placed under a window's
 * pages, it takes their bottom, so the prompt's box sits over the keys, never under them. Only the window the player
 * holds draws it (the bottom screen in dual mode).
 */
@Composable
fun NxKeyboardBar(state: AppState) {
    val entry by state.textEntry.collectAsState()
    val e = entry ?: return
    // Where the keys start, for a prompt's box to stay above them ([nxKeyboardTop]).
    DisposableEffect(Unit) { onDispose { nxKeyboardTop.value = null } }
    NxKeyboard(
        e, onKey = state::keyboardTapKey, onHint = state::onButton, confirmLabel = state.textEntryConfirmLabel,
        modifier = Modifier.onGloballyPositioned { nxKeyboardTop.value = it.positionInRoot().y },
    )
}
