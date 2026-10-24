package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.stepFrom

/**
 * One value row's setting as it is now, bound to that row (a tab's, a console's, a hint's): [values] in the order
 * LEFT / RIGHT step through them, the [current] one, how to [set] another, and what the page does [after] a change
 * (republish, plus any side effect: Home rebuilt, the tab row's focus dropped, a rumble preview). Settings' rows
 * ([AppState]'s `settingValue`) and RetroAchievements' build one per press, so LEFT / RIGHT, A and the touch chooser
 * are one generic path each; onboarding's value rows [step] the same settings without [after] (they republish their
 * own page). A two-value row (an On / Off, Show / Hide or two-choice row) steps by flipping.
 */
internal class SettingValue<T>(
    val values: List<T>,
    val current: T,
    val set: (T) -> Unit,
    /** A value's text: a Settings touch row's choices, and RetroAchievements' touch chooser (three or more values); null: none. */
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
        val next = values.getOrNull(i) ?: return
        if (i == index) return
        set(next)
        after(next)
    }
}
