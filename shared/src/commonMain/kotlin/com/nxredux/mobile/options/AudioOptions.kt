package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry

/**
 * Audio rows for every console: Audio Resampling Quality, Audio Buffer and Fill Audio Gaps ([rows]). Audio Resampling Quality: how the game's sound is converted to the phone's
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

    const val BUFFER = "nx_audio_buffer"
    const val FILL_GAPS = "nx_fill_audio_gaps"

    private val buffers = listOf("auto", "50", "75", "100")

    /**
     * Audio Buffer: how much sound the runner keeps ready ahead of the speaker. Auto is 50 ms on the heavy consoles (Wii,
     * GameCube, PS2: their cores stall now and then on a phone like the Retroid Pocket Nova) and the runner's own size
     * (about 33 ms) elsewhere; more covers slow moments without crackling but plays the sound later against the picture.
     * Read when the game's audio starts, so it applies at the next launch.
     */
    val bufferRow = FrontendOption(
        BUFFER, "Audio Buffer", buffers, buffers[0],
        "How much sound is kept ready ahead of the speaker. Auto keeps 50 ms on Wii, GameCube and PS2, about 33 ms elsewhere.\n" +
            "More covers slow moments without crackling, but the sound comes later against the picture. " +
            "Applies the next time the game starts.",
        brief = "Applies next launch",
        visual = false, valueLabels = listOf("Auto", "50 ms", "75 ms", "100 ms"), atLaunch = true,
    )

    /** Fill Audio Gaps values: Auto (with Frame Skip Off), On, Off; the runner's modes in this order. */
    const val GAPS_AUTO = "auto"
    const val GAPS_ON = "on"
    const val GAPS_OFF = "off"
    private val gaps = listOf(GAPS_AUTO, GAPS_ON, GAPS_OFF)

    /**
     * Fill Audio Gaps: when the game runs slower than full speed, its sound plays at a slower tempo at the same pitch
     * (time stretch, SoundTouch in the runner) instead of running dry and crackling. Auto does it where the game slows
     * down rather than skipping frames (Frame Skip Off). Applied live.
     */
    val fillGapsRow = FrontendOption(
        FILL_GAPS, "Fill Audio Gaps", gaps, GAPS_AUTO,
        "When the game runs slower than full speed, its sound plays a little slower at the same pitch instead of crackling.\n" +
            "Auto does it when Frame Skip is Off, where the game slows down instead of skipping frames. " +
            "It plays the sound about 50 ms later.",
        visual = false, valueLabels = listOf("Auto", "On", "Off"),
    )

    /** Every console's Audio rows, in order. */
    val rows: List<FrontendOption> = listOf(row, bufferRow, fillGapsRow)

    /** Auto's Audio Buffer on the heavy consoles' cores (Wii, GameCube, PS2), in ms. */
    const val HEAVY_BUFFER_MS = 50
    private val heavyCores = setOf("dolphin", "armsx2")

    /**
     * The runner's Audio Buffer in ms for saved [values] of [tag]: the chosen size, else Auto's ([HEAVY_BUFFER_MS] on a
     * heavy console, 0 for the runner's own size elsewhere).
     */
    fun bufferMsOf(values: Map<String, String>, tag: String): Int =
        values[BUFFER]?.toIntOrNull()?.takeIf { it in 1..500 }
            ?: if (CoreRegistry.forTag(tag)?.id in heavyCores) HEAVY_BUFFER_MS else 0

    /** The runner's Fill Audio Gaps mode (0 Auto, 1 On, 2 Off) for saved [values]; anything unknown is Auto. */
    fun fillGapsOf(values: Map<String, String>): Int = gaps.indexOf(values[FILL_GAPS]).takeIf { it >= 0 } ?: 0

    /** The runner's level for saved [values]; anything unknown is High. */
    fun qualityOf(values: Map<String, String>): Int =
        levels.indexOf(values[RESAMPLING]).takeIf { it >= 0 } ?: HIGH
}
