package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry

/**
 * Console Settings → Frame Skip, for Wii and GameCube (Dolphin), the consoles where it showed: what the game does when
 * the phone can't keep up. Other consoles keep Auto without the row. Auto runs two
 * frames in a step to hold the game's speed, showing one of them; Off shows every frame and lets the game slow down
 * instead (its sound may then crackle). A frontend `nx_` key, per game with a console default, applied live.
 *
 * Off by default on Dolphin (Wii, GameCube): SMG2 at 3x on a Retroid Pocket Nova showed ~37 fps with Auto (frames
 * skipped to hold speed) against ~56 fps with Off at 94-96% speed, smoother than standalone Dolphin (2026-11-03).
 */
object FrameSkipOptions {
    const val KEY = "nx_frame_skip"
    const val AUTO = "Auto"
    const val OFF = "Off"

    private const val INFO = "When the phone can't keep up: Auto skips frames\nto hold the game's speed; Off shows every frame\n" +
        "and lets the game slow down a little (its sound\nmay crackle then)."

    /** The row, default Off; Wii and GameCube only. */
    val row: FrontendOption = FrontendOption(KEY, "Frame Skip", listOf(AUTO, OFF), OFF, INFO, visual = false)

    /** [tag]'s rows: the Frame Skip row for a console on Dolphin, else none. */
    fun rowsFor(tag: String): List<FrontendOption> = if (appliesTo(tag)) listOf(row) else emptyList()

    fun appliesTo(tag: String): Boolean = CoreRegistry.forTag(tag)?.id == "dolphin"

    fun defaultFor(tag: String): String = if (appliesTo(tag)) OFF else AUTO

    /** Whether the runner may skip frames, for saved [values] of [tag] (its default when unset or unknown). */
    fun skipOf(values: Map<String, String>, tag: String): Boolean =
        (values[KEY]?.takeIf { it == AUTO || it == OFF } ?: defaultFor(tag)) == AUTO
}
