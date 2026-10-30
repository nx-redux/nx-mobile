package com.nxredux.mobile.options

/**
 * Console Settings → Audio Resampling Quality, for every console: how the game's sound is converted to the phone's
 * output rate, as the handheld's frontend option (Low zero-order hold, Medium linear, High and Max sinc; libsamplerate
 * in the runner). A frontend `nx_` key, per game with a console default, applied live. Audible fast forward plays at
 * Medium at most (the runner's choice, not stored).
 */
object AudioOptions {
    const val RESAMPLING = "nx_resampling_quality"

    /** The runner's quality levels ([qualityOf]), the row's values in order. */
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2
    const val MAX = 3

    private val levels = listOf("Low", "Medium", "High", "Max")

    val row = FrontendOption(
        RESAMPLING, "Audio Resampling Quality", levels, levels[HIGH],
        "How cleanly the game's sound is converted for\nthe phone. Higher sounds clearer and takes more\n" +
            "battery. Fast forward plays at Medium at most.",
        visual = false,
    )

    /** The runner's level for saved [values]; anything unknown is High. */
    fun qualityOf(values: Map<String, String>): Int =
        levels.indexOf(values[RESAMPLING]).takeIf { it >= 0 } ?: HIGH
}
