package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.shortcuts.EffectiveShortcuts
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.SpeedOutput
import com.nxredux.mobile.shortcuts.TouchSlot

/** A touch shortcut button the pad draws: its [action], and [on] for a toggle that is running now (drawn pressed in). */
data class TouchShortcut(val action: ShortcutAction, val on: Boolean)

/** What a touch shortcut button reads: short, since it is a MENU-sized pill. */
fun touchShortcutLabel(a: ShortcutAction): String = when (a) {
    ShortcutAction.TOGGLE_FF -> "FF"
    ShortcutAction.HOLD_FF -> "Hold FF"
    ShortcutAction.TOGGLE_REWIND -> "Rewind"
    ShortcutAction.HOLD_REWIND -> "Hold Rewind"
    ShortcutAction.RESET -> "Reset"
}

/**
 * The touch buttons to draw: the slots [effective] gives an action (it already leaves out rewind on a core that can't),
 * none during a netplay or link session ([netplay]: the actions would desync the players), and none before the launch
 * read the shortcuts (null). A rewind action also goes while the console's Rewind row is off ([rewind] false), as the
 * panel's Rewind tile does: it would do nothing. A toggle is [TouchShortcut.on] while [output] runs it; a hold shows
 * only its press, and the Toggle FF button lights for a held FF too, as the panel's Fast forward tile does.
 */
fun visibleTouchShortcuts(
    effective: EffectiveShortcuts?,
    output: SpeedOutput,
    netplay: Boolean,
    rewind: Boolean = true,
): Map<TouchSlot, TouchShortcut> {
    if (effective == null || netplay) return emptyMap()
    return effective.touch.filterValues { rewind || !it.rewind }.mapValues { (_, a) ->
        val on = when (a) {
            ShortcutAction.TOGGLE_FF -> output.ffSpeed > 0
            ShortcutAction.TOGGLE_REWIND -> output.rewinding
            else -> false
        }
        TouchShortcut(a, on)
    }
}

/** The pad layout piece of [slot]. */
internal val TouchSlot.padGroup: PadGroup get() = when (this) {
    TouchSlot.ONE -> PadGroup.SHORTCUT_1
    TouchSlot.TWO -> PadGroup.SHORTCUT_2
}

/** The editor's placeholder label for [slot]'s piece. */
internal fun touchSlotPlaceholder(slot: TouchSlot): String = "Shortcut ${slot.ordinal + 1}"

/**
 * A portrait shortcut pill's width beside the MENU row: MENU's [pillWidth] where the [sideRoom] (the row's width either
 * side of the centred MENU row) leaves it with a [gap] from that row, else what is left (a 3DS's four-pill row), so it
 * never reaches the row or past the band's edge.
 */
internal fun portraitShortcutWidth(pillWidth: Float, sideRoom: Float, gap: Float): Float =
    minOf(pillWidth, sideRoom - gap).coerceAtLeast(0f)
