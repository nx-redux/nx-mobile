package com.nxredux.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.kids.UnlockChip
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.nxHighlight
import com.nxredux.mobile.ui.nx.nxPageInset
import com.nxredux.mobile.ui.nx.nxScrollFade

/** The unlock sheet's selection: the four chips ([UnlockChip.entries]), then these two rows. */
internal const val KIDS_UNLOCK_FULL_MENU = 4
internal const val KIDS_UNLOCK_SETTINGS = 5
internal const val KIDS_UNLOCK_ROWS = 6

/** The unlock PIN and sheet close after this long without input (the sheet opens Parent mode and Kids Mode's settings). */
internal const val KIDS_UNLOCK_IDLE_MS = 60_000L

/** The chips per row on a sheet [widthDp] wide: a 2 x 2 grid below 480 dp, so no label wraps mid-word; else one row of four. */
internal fun kidsUnlockChipColumns(widthDp: Float): Int = if (widthDp < 480f) 2 else UnlockChip.entries.size

/**
 * The d-pad on the unlock sheet from [index], the chips laid out [columns] per row: LEFT / RIGHT along a row of chips,
 * UP / DOWN between their rows, DOWN from the last row to the rows below them, UP back to the first chip.
 */
internal fun kidsUnlockStep(index: Int, button: NxButton, columns: Int = UnlockChip.entries.size): Int {
    val lastChip = UnlockChip.entries.size - 1
    val cols = columns.coerceIn(1, UnlockChip.entries.size)
    val chip = index <= lastChip
    return when (button) {
        NxButton.LEFT -> if (chip && index % cols > 0) index - 1 else index
        NxButton.RIGHT -> if (chip && index % cols < cols - 1 && index < lastChip) index + 1 else index
        NxButton.DOWN -> when {
            chip && index + cols <= lastChip -> index + cols
            chip -> KIDS_UNLOCK_FULL_MENU
            else -> KIDS_UNLOCK_SETTINGS
        }
        NxButton.UP -> when {
            index == KIDS_UNLOCK_SETTINGS -> KIDS_UNLOCK_FULL_MENU
            index == KIDS_UNLOCK_FULL_MENU -> 0
            index - cols >= 0 -> index - cols
            else -> index
        }
        else -> index
    }
}

/**
 * After the right PIN: "Let them play" with its four chips (a timed unlock), then "For you": Open full menu (Parent mode
 * until a lock or five idle minutes) and Kids Mode settings. Taps choose; the d-pad's item ([selectedIndex]) is lit.
 */
@Composable
internal fun KidsUnlockScreen(
    selectedIndex: Int, onChip: (UnlockChip) -> Unit, onFullMenu: () -> Unit, onSettings: () -> Unit,
    onColumns: (Int) -> Unit = {}, modifier: Modifier = Modifier,
) {
    val colors = LocalNxColors.current
    val inset = nxPageInset()
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val scroll = rememberScrollState()
        val columns = kidsUnlockChipColumns(maxWidth.value)
        LaunchedEffect(columns) { onColumns(columns) }
        Column(
            Modifier.fillMaxSize().nxScrollFade(scroll).verticalScroll(scroll).heightIn(min = maxHeight)
                .padding(start = inset.start, end = inset.end, top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                SheetHeading("Let them play")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    UnlockChip.entries.chunked(columns).forEachIndexed { r, line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            line.forEachIndexed { c, chip ->
                                val i = r * columns + c
                                SheetItem(chip.label, null, lit = i == selectedIndex, Modifier.weight(1f), centred = true, oneLine = true) { onChip(chip) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                SheetHeading("For you")
                SheetItem("Open full menu", "Until you lock it, or 5 minutes without use", lit = selectedIndex == KIDS_UNLOCK_FULL_MENU, Modifier.fillMaxWidth(), onTap = onFullMenu)
                Spacer(Modifier.height(10.dp))
                SheetItem("Kids Mode settings", null, lit = selectedIndex == KIDS_UNLOCK_SETTINGS, Modifier.fillMaxWidth(), trailing = "›", onTap = onSettings)
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text, color = LocalNxColors.current.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}

/** A chip or a row: lit in the accent when selected, else a faint plate. */
@Composable
private fun SheetItem(
    label: String, secondary: String?, lit: Boolean, modifier: Modifier, centred: Boolean = false, trailing: String? = null,
    oneLine: Boolean = false, onTap: () -> Unit,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val content = if (lit) colors.onAccent else colors.listText
    Row(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .background(if (lit) nxHighlight() else colors.inkEdge)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centred) Arrangement.Center else Arrangement.Start,
    ) {
        Column(if (centred) Modifier else Modifier.weight(1f)) {
            Text(
                label, color = content, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                // A chip's label never wraps mid-word: one line, never broken (the grid gives it the room).
                maxLines = if (oneLine) 1 else Int.MAX_VALUE, softWrap = !oneLine,
            )
            if (secondary != null) Text(secondary, color = if (lit) colors.onAccentMuted else colors.hint, fontFamily = font, fontSize = 13.sp)
        }
        if (trailing != null) Box { Text(trailing, color = content, fontFamily = font, fontSize = 20.sp) }
    }
}
