package com.nxredux.mobile.options

/**
 * Where an app setting (a console row, [ConsoleSettings], or a frontend row, [FrontendOptions.table]) is listed, by what
 * it is for rather than where it comes from: the in-game Options page shows one page per group, Emulator Settings and
 * Game Settings one header per group. A group with no rows for the console is not shown.
 */
enum class OptionGroup(val label: String) {
    /** The picture: scaling, sharpness, overlay, offsets, the DS / 3DS / Wii screens, 3D resolution, Graphics API, Debug HUD. */
    DISPLAY("Display"),
    /** The pad and the console's own controls: layout, type, portrait pad, landscape opacity, 3DS and Wii controls. */
    CONTROLS("Controls"),
    /** How the game runs: fast forward, rewind (with their audio), Frame Skip, the PS2 BIOS. */
    GAMEPLAY("Gameplay"),
    /** Sound: the resampler, the buffer, filling gaps. */
    AUDIO("Audio");

    companion object {
        private val CONTROL_KEYS = setOf(
            PadOptions.LAYOUT, PadOptions.SEGA_PAD, FrontendOptions.PAD_LANDSCAPE_OPACITY,
            N3dsOptions.NEW_CONTROLS,
            WiiOptions.CONTROLLER, WiiOptions.MOTION, WiiOptions.POINTER_WIDTH, WiiOptions.POINTER_HEIGHT, WiiOptions.POINTER_OFFSET,
        )
        private val GAMEPLAY_KEYS = setOf(SpeedOptions.MAX_FF, SpeedOptions.FF_AUDIO, SpeedOptions.REWIND, SpeedOptions.REWIND_AUDIO, FrameSkipOptions.KEY, Ps2Options.BIOS)
        private val AUDIO_KEYS = setOf(AudioOptions.RESAMPLING, AudioOptions.BUFFER, AudioOptions.FILL_GAPS)

        /** The group of the setting stored as [key]; the picture rows (every other one) are [DISPLAY]. */
        fun of(key: String): OptionGroup = when (key) {
            in CONTROL_KEYS -> CONTROLS
            in GAMEPLAY_KEYS -> GAMEPLAY
            in AUDIO_KEYS -> AUDIO
            else -> DISPLAY
        }

        /** [rows] by group, in group order, each keeping the rows' order; groups without rows left out. */
        fun <T> grouped(rows: List<T>, key: (T) -> String): List<Pair<OptionGroup, List<T>>> {
            val by = rows.groupBy { of(key(it)) }
            return entries.mapNotNull { g -> by[g]?.let { g to it } }
        }
    }
}
