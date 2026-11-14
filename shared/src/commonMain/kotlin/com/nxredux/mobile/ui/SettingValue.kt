package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.stepFrom

/**
 * One value row's setting as it is now, bound to that row (a tab's, a console's, a hint's): [values] in the order
 * LEFT / RIGHT step through them, the [current] one, how to [set] another, and what the page does [after] a change
 * (republish, plus any side effect: Home rebuilt, the tab row's focus dropped, a rumble preview). Settings' rows
 * ([AppState]'s `settingValue`) and RetroAchievements' build one per press, so LEFT / RIGHT, A and the touch controls
 * are one generic path each; onboarding's value rows [step] the same settings without [after] (they republish their
 * own page). A two-value row (an On / Off, Show / Hide or two-choice row) steps by flipping.
 */
internal class SettingValue<T>(
    val values: List<T>,
    val current: T,
    val set: (T) -> Unit,
    /** A value's text: a Settings or RetroAchievements touch row's choices; null: none. */
    val label: ((T) -> String)? = null,
    /** LEFT / RIGHT stop at the ends instead of wrapping (RetroAchievements' durations); A still wraps. */
    val clamps: Boolean = false,
    val after: (T) -> Unit,
) {
    /** Sets the value [by] places from [current] (-1: the one before), wrapping unless [wrap] is false; only the setting changes. */
    fun step(by: Int, wrap: Boolean = true): T {
        val next = if (wrap) values.stepFrom(current, by) else values[(values.indexOf(current) + by).coerceIn(values.indices)]
        set(next)
        return next
    }

    /** A press on the row: [step], then [after] (the page republished, side effects in their order). */
    fun change(by: Int, wrap: Boolean = true) = after(step(by, wrap))

    /** Every value's text ([label]; null when it has none), in [values]' order: a touch row's choices. */
    val labels: List<String>? get() = label?.let(values::map)

    /** [current]'s place in [values] (-1 when it is none of them). */
    val index: Int get() = values.indexOf(current)

    /** A touch pick: sets value [i] of [values], then [after]; nothing when [i] is out of range or already the one. */
    fun changeTo(i: Int) {
        if (setTo(i)) after(values[i])
    }

    /** Sets value [i] of [values] only (no [after]); false when [i] is out of range or already the one. */
    fun setTo(i: Int): Boolean {
        val next = values.getOrNull(i) ?: return false
        if (i == index) return false
        set(next)
        return true
    }
}

/** A row the pad is changing: the page it is on ([rowEditKey]) and its index there. */
data class RowEdit(val key: Any, val index: Int)

/**
 * The page of setting rows [s] is, for [RowEdit] (a Settings section, an emulator's options page, RetroAchievements' or
 * Artwork's settings); null on any other screen.
 */
internal fun rowEditKey(s: Screen): Any? = when (s) {
    is Screen.Settings -> s.section
    is Screen.EmulatorOptions -> "emulator:${s.category}"
    is Screen.RaSettings -> "ra"
    is Screen.ArtworkSettings -> "artwork"
    else -> null
}

/**
 * [s] is drawn beside another page on a [split] window: a Settings page, an emulator's console-wide options (a game's
 * own are a modal), RetroAchievements' or Artwork's settings. Only there do LEFT / RIGHT go between pages and A start a
 * change ([RowEdit]); one page keeps LEFT / RIGHT for its values.
 */
internal fun rowEditTwoPane(s: Screen, split: Boolean): Boolean =
    split && (settingsPane(s) != null || raSplitLevel(s) || artworkSplitLevel(s))

/** [s] has a dialog open that takes the pad (a confirm, a sign-in, a key): no row is changed under it. */
internal fun rowEditBlocked(s: Screen): Boolean = when (s) {
    is Screen.Settings -> s.confirmReset || s.confirmRemove != null || s.nameDraft != null || s.confirmDriver != null
    is Screen.RaSettings -> s.signingIn || s.confirm != null
    is Screen.ArtworkSettings -> s.dialogOpen
    else -> false
}

/** The row of [s] being changed while [selected] is the selection ([edit] is on it), else -1: the UI's [NxSettingRow.editing]. */
internal fun rowEditIndex(s: Screen, edit: RowEdit?, selected: Int): Int =
    if (edit != null && edit.index == selected && edit.key == rowEditKey(s)) selected else -1
