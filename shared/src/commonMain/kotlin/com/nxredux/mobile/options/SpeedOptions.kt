package com.nxredux.mobile.options

/**
 * Console Settings → fast forward and rewind, for every console: the top fast-forward speed, whether the sound plays
 * while fast-forwarding, and rewind (off by default; it costs battery) with its sound. Frontend `nx_` keys, saved like
 * the other console rows and applied live. Rewind rows are left out for cores that cannot rewind ([REWIND_KEYS]).
 */
object SpeedOptions {
    const val MAX_FF = "nx_max_ff_speed"
    const val FF_AUDIO = "nx_ff_audio"
    const val REWIND = "nx_rewind"
    const val REWIND_AUDIO = "nx_rewind_audio"

    /** The rows only a rewind-capable core offers. */
    val REWIND_KEYS: Set<String> = setOf(REWIND, REWIND_AUDIO)

    private val speeds = (2..8).map { it.toString() }

    val table: List<FrontendOption> = listOf(
        FrontendOption(
            MAX_FF, "Max FF Speed", speeds, "4",
            "How fast fast forward can run. It never goes\nfaster than this, whatever the shortcut asks for.",
            brief = "Fast forward never runs faster than this",
            visual = false, valueLabels = speeds.map { "${it}x" },
        ),
        FrontendOption(
            FF_AUDIO, "Fast Forward Audio", listOf("Off", "On"), "Off",
            "Play the game's sound while fast forwarding.\nOff keeps it quiet.",
            visual = false,
        ),
        FrontendOption(
            REWIND, "Rewind", listOf("Off", "On"), "Off",
            "Keeps the last moments of play so you can go\nback with the Rewind shortcut. Uses battery.",
            brief = "Keeps the last moments so you can go back (uses battery)",
            visual = false,
        ),
        FrontendOption(
            REWIND_AUDIO, "Rewind Audio", listOf("Off", "On"), "Off",
            "Play the game's sound while rewinding.\nOff keeps it quiet.",
            visual = false,
        ),
    )
}

/** The speed settings as the game reads them. */
data class SpeedSettings(
    val maxFf: Int = 4,
    val ffAudio: Boolean = false,
    val rewind: Boolean = false,
    val rewindAudio: Boolean = false,
) {
    companion object {
        /** From saved [values]; [rewind] is off whenever the core can't ([canRewind]); anything unknown is the default. */
        fun parse(values: Map<String, String>, canRewind: Boolean): SpeedSettings = SpeedSettings(
            maxFf = values[SpeedOptions.MAX_FF]?.toIntOrNull()?.takeIf { it in 2..8 } ?: 4,
            ffAudio = values[SpeedOptions.FF_AUDIO] == "On",
            rewind = canRewind && values[SpeedOptions.REWIND] == "On",
            rewindAudio = values[SpeedOptions.REWIND_AUDIO] == "On",
        )
    }
}
