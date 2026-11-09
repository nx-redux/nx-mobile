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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.AppState
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar
import com.nxredux.mobile.ui.nx.nxTextScale

/**
 * What the buttons do on the keyboard right now, for its hint row: on a key, typing and the shortcuts; in the field,
 * the cursor; on the box's buttons, A as that button's word ([confirmLabel] or Cancel).
 */
fun keyboardHints(entry: TextEntry, confirmLabel: String): List<Hint> = when (val f = entry.focus) {
    is EntryFocus.Keys -> listOf(
        // X shifts too, but its key is on the board: five hints fit a handheld's bar, six drop A's.
        Hint(NxButton.A, "Type"), Hint(NxButton.B, "Delete"), Hint(NxButton.Y, "Space"),
        Hint(NxButton.START, "Done"), Hint(NxButton.MENU, "Cancel"),
    )
    EntryFocus.Field -> listOf(
        Hint(NxButton.LEFT, "Move cursor"), Hint(NxButton.B, "Delete"), Hint(NxButton.Y, "Space"),
        Hint(NxButton.START, "Done"), Hint(NxButton.MENU, "Cancel"),
    )
    is EntryFocus.Button -> listOf(Hint(NxButton.A, if (f.index == 0) confirmLabel else "Cancel"), Hint(NxButton.MENU, "Cancel"))
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

/**
 * The NX keyboard: [entry]'s page of keys, the pad's highlight lit while it is on a key, a tap typing a key ([onKey]);
 * its hint row under the keys, a tapped hint sent as its button ([onHint]).
 */
@Composable
fun NxKeyboard(entry: TextEntry, onKey: (Key) -> Unit, onHint: (NxButton) -> Unit, confirmLabel: String, modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    val rows = NxKeys.rows(entry.page, entry.twoFields)
    val lit = (entry.focus as? EntryFocus.Keys)?.at
    val keyHeight = 40.dp * nxTextScale()
    Column(modifier.fillMaxWidth().background(colors.modalPanel).navigationBarsPadding().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { c, key ->
                        val on = lit == KeyPos(r, c)
                        Box(
                            Modifier.weight(NxKeys.width(key)).height(keyHeight)
                                .background(if (on) colors.main else colors.fieldGround, RoundedCornerShape(8.dp))
                                .pointerInput(key, entry) { detectTapGestures { onKey(key) } },
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
    NxKeyboard(e, onKey = state::keyboardTapKey, onHint = state::onButton, confirmLabel = state.textEntryConfirmLabel)
}
