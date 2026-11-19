package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxPadA
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.padA

/** The selected row's touch row as the page draws it, as the controller's own lookup does. */
internal fun AppState.selectedTouchRow(): NxSettingRow? {
    val i = selection.value.index
    return when (val s = screen.value) {
        is Screen.Settings -> s.rows.getOrNull(i)?.let { settingsTouchRow(it, s.choices.getOrNull(i), s.section) }
        is Screen.EmulatorOptions -> s.rows.getOrNull(i)?.takeIf { it.selectable }?.let(::emulatorTouchRow)
        is Screen.RaSettings -> s.rows.getOrNull(i)?.takeIf { it.selectable }?.let(::raSettingsTouchRow)
        is Screen.ArtworkSettings -> s.rows.getOrNull(i)?.takeIf { it.selectable }?.let(::artworkSettingTouchRow)
        is Screen.Onboarding -> s.touchRows?.getOrNull(i)
        else -> null
    }
}

/**
 * LEFT or RIGHT ([side]) on the selected value row the way a pad changes it now that the sides need A first: segments
 * or a stepped value A, then [side], then B; a switch A (it flips); a picker A, the next or previous choice, A. Any
 * other row hears [side] itself.
 */
internal fun AppState.padStep(side: NxButton) {
    when (selectedTouchRow()?.padA) {
        NxPadA.EDIT -> { onButton(NxButton.A); onButton(side); onButton(NxButton.B) }
        NxPadA.FLIP -> onButton(NxButton.A)
        NxPadA.PICK -> {
            onButton(NxButton.A)
            if (chooser.value != null) { onButton(if (side == NxButton.LEFT) NxButton.UP else NxButton.DOWN); onButton(NxButton.A) }
        }
        else -> onButton(side)
    }
}
