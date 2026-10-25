package com.nxredux.mobile.options

/** One frontend (minarch) setting as the in-game Frontend page shows it. */
data class FrontendOption(
    val key: String,
    val label: String,
    val values: List<String>,
    val default: String,
    val info: String,
    /** Changes the picture, so it can be adjusted live over the paused frame. */
    val visual: Boolean,
    /** What the menu shows for each of [values], same order; empty = the values themselves. */
    val valueLabels: List<String> = emptyList(),
    /**
     * Read only when the game starts (Graphics API, Wii Screen, the pointer rows): set in Game Settings or
     * Emulator Settings, and left out of the running game's Console Settings, where a change would do nothing.
     */
    val atLaunch: Boolean = false,
    /**
     * The row's visible line (two lines at most), [info] the full text behind its info icon; empty: no line, the row is
     * compact and [info] shows only from the icon.
     */
    val brief: String = "",
) {
    /** The label for [value]; the value itself when it has none (or is unknown). */
    fun labelOf(value: String): String = valueLabels.getOrNull(values.indexOf(value))?.takeIf { it.isNotEmpty() } ?: value
}

/** How the game picture fills its box; [AUTO] lets the layout choose per screen and console. */
enum class ScreenScaling { AUTO, NATIVE, ASPECT, FULLSCREEN }

/** Frontend values parsed for use; anything unknown falls back to the default (sharpness NEAREST, overlay None). */
data class FrontendSettings(
    val scaling: ScreenScaling = ScreenScaling.AUTO,
    val linear: Boolean = false,
    /** Overlay file name, null for None. */
    val overlay: String? = null,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val debugHud: Boolean = false,
    /** The landscape pad clusters' opacity in percent: one of [FrontendOptions.PAD_OPACITIES]. */
    val padLandscapeOpacity: Int = FrontendOptions.PAD_OPACITY_DEFAULT,
) {
    /** [padLandscapeOpacity] as an alpha (40 -> 0.4): the landscape pad clusters and anything drawn over the landscape game with them. */
    val padLandscapeAlpha: Float get() = padLandscapeOpacity / 100f

    companion object {
        /** A missing key takes its default, as in [FrontendOptions.fromConfig]: Auto scaling, overlay None. */
        fun parse(values: Map<String, String>): FrontendSettings = FrontendSettings(
            scaling = when (FrontendOptions.migrateKey(values, FrontendOptions.SCALING, FrontendOptions.SCALING_V1, "Aspect")) {
                "Native" -> ScreenScaling.NATIVE
                "Aspect" -> ScreenScaling.ASPECT
                "Fullscreen" -> ScreenScaling.FULLSCREEN
                else -> ScreenScaling.AUTO
            },
            linear = values[FrontendOptions.SHARPNESS] == "LINEAR",
            overlay = values[FrontendOptions.OVERLAY]?.takeIf { it.isNotEmpty() && it != FrontendOptions.NONE },
            offsetX = offset(values[FrontendOptions.OFFSET_X]),
            offsetY = offset(values[FrontendOptions.OFFSET_Y]),
            debugHud = values[FrontendOptions.DEBUG_HUD] == "On",
            padLandscapeOpacity = values[FrontendOptions.PAD_LANDSCAPE_OPACITY]?.toIntOrNull()
                ?.takeIf { it in FrontendOptions.PAD_OPACITIES } ?: FrontendOptions.PAD_OPACITY_DEFAULT,
        )

        private fun offset(value: String?): Int = value?.toIntOrNull()?.takeIf { it in -64..64 } ?: 0
    }
}

/** The frontend options that make sense on a phone (values and texts follow minarch `ma_config.c`; keys are ours). */
object FrontendOptions {
    /** Screen Scaling, with Auto added: a new key so an untouched old Aspect reads as Auto ([migrateKey]). */
    const val SCALING = "nx_screen_scaling_v2"
    /** Screen Scaling before Auto: read when [SCALING] is absent, never written. */
    const val SCALING_V1 = "nx_screen_scaling"
    const val SHARPNESS = "nx_scale_filter"
    /** Not a row: read once by [ShaderOptions] to migrate Line/Grid to a preset. */
    const val EFFECT = "nx_screen_effect"
    const val OVERLAY = "nx_overlay"
    const val OFFSET_X = "nx_screen_offsetx"
    const val OFFSET_Y = "nx_screen_offsety"
    const val DEBUG_HUD = "nx_debug_hud"
    /** Every console: how opaque the landscape pad clusters are over the game ([FrontendSettings.padLandscapeAlpha]). */
    const val PAD_LANDSCAPE_OPACITY = "nx_pad_landscape_opacity"
    /** [PAD_LANDSCAPE_OPACITY]'s values, in percent, in menu order. */
    val PAD_OPACITIES = listOf(100, 60, 40, 25)
    const val PAD_OPACITY_DEFAULT = 40
    /** Enabled cheats (normalised info texts — desc with its source prefix — joined by `|`); written to the game config only, never a row. */
    const val CHEATS = "nx_cheats"
    const val NONE = "None"

    /** Our frontend keys. */
    const val PREFIX = "nx_"
    /** minarch's own key prefix: read with [migrateKey], never written. */
    const val LEGACY_PREFIX = "minarch_"
    private val OFFSETS = (-64..64).map { it.toString() }

    val table: List<FrontendOption> = listOf(
        FrontendOption(
            SCALING, "Screen Scaling", listOf("Auto", "Native", "Aspect", "Fullscreen"), "Auto",
            "Auto picks the best fit for the screen and console.\nNative uses integer scaling.\nAspect uses core reported aspect ratio.\nFullscreen has non-square pixels.",
            visual = true,
        ),
        FrontendOption(
            SHARPNESS, "Screen Sharpness", listOf("NEAREST", "LINEAR"), "NEAREST",
            "LINEAR smooths lines, but works better when final image is at higher resolution, " +
                "so either core that outputs higher resolution or upscaling with shaders",
            visual = true,
        ),
        // None for every console, whatever overlay files it has: an overlay shows only when the user picks one.
        FrontendOption(OVERLAY, "Overlay", listOf(NONE), NONE, "Choose a custom overlay png from the Overlays folder", visual = true),
        FrontendOption(OFFSET_X, "Offset screen X", OFFSETS, "0", "Moves the picture sideways, in pixels.", visual = true),
        FrontendOption(OFFSET_Y, "Offset screen Y", OFFSETS, "0", "Moves the picture up or down, in pixels.", visual = true),
        FrontendOption(
            PAD_LANDSCAPE_OPACITY, "Pad Opacity (landscape)", PAD_OPACITIES.map { it.toString() }, PAD_OPACITY_DEFAULT.toString(),
            "How opaque the on-screen buttons are in landscape,\nwhere they sit over the game. Portrait keeps its own band.",
            visual = false,
            valueLabels = PAD_OPACITIES.map { "$it%" },
        ),
        FrontendOption(
            DEBUG_HUD, "Debug HUD", listOf("Off", "On"), "Off",
            "Show frames per second, core,\nresolution, and scaler information.",
            brief = "FPS and core info over the game",
            visual = false,
        ),
    )

    /** Every frontend setting for [tag]: its console's rows, then [table]. Defaults, config reads and saves use this. */
    fun allFor(tag: String): List<FrontendOption> = ConsoleSettings.tableFor(tag) + table

    /** Every frontend key for [tag] with its default (the Overlay default is None, whatever files the console has). */
    fun defaults(tag: String = ""): Map<String, String> = allFor(tag).associate { it.key to it.default }

    /** A frontend setting by key, the console rows included (keys are unique). */
    fun option(key: String): FrontendOption? = table.firstOrNull { it.key == key } ?: ConsoleSettings.all.firstOrNull { it.key == key }

    /** Every `nx_*` or legacy `minarch_*` key belongs to the frontend: never a core option. */
    fun isFrontendKey(key: String): Boolean = key.startsWith(PREFIX) || key.startsWith(LEGACY_PREFIX)

    fun isLegacyKey(key: String): Boolean = key.startsWith(LEGACY_PREFIX)

    /**
     * The handheld's own fast-forward and rewind keys (`minarch_max_ff_speed`, `minarch__ff_audio`, `minarch_rewind_*`):
     * never migrated, so a copied handheld cfg neither sets nor locks a [SpeedOptions] row (its rewind is on by choice
     * there, and its buffers are not ours). Read as nothing and dropped on a full save.
     */
    fun isHandheldOnlyKey(key: String): Boolean {
        if (!isLegacyKey(key)) return false
        val name = key.removePrefix(LEGACY_PREFIX)
        return name == "max_ff_speed" || name.trimStart('_') == "ff_audio" || name == "rewind" || name.startsWith("rewind_")
    }

    /** `minarch_<x>` → `nx_<x>` ([isHandheldOnlyKey] ones excepted); any other key unchanged. */
    fun migrateKey(key: String): String =
        if (isLegacyKey(key) && !isHandheldOnlyKey(key)) PREFIX + key.removePrefix(LEGACY_PREFIX) else key

    /**
     * [newKey]'s value from [values], else [oldKey]'s unless it equals [oldDefault] (an untouched old default should
     * follow the new default), else null.
     */
    fun migrateKey(values: Map<String, String>, newKey: String, oldKey: String, oldDefault: String): String? =
        values[newKey] ?: values[oldKey]?.takeIf { it != oldDefault }

    /**
     * Whether a full config save drops a line with [key]: a key moved to the shader rows ([ShaderOptions.MIGRATED_KEYS]),
     * read once and migrated, so the save has already carried its value, or a handheld-only key ([isHandheldOnlyKey]).
     */
    fun droppedOnSave(key: String): Boolean = key in ShaderOptions.MIGRATED_KEYS || isHandheldOnlyKey(key)

    /** [values] with legacy keys renamed, handheld-only ones left out; an existing `nx_` value wins over its legacy twin. */
    fun migrateKeys(values: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        values.forEach { (k, v) -> if (!isLegacyKey(k)) out[k] = v }
        values.forEach { (k, v) -> if (isLegacyKey(k) && !isHandheldOnlyKey(k)) out.putIfAbsent(migrateKey(k), v) }
        return out
    }

    /** The values offered for [key]; the Overlay list is None followed by [overlays]. */
    fun choices(key: String, overlays: List<String>): List<String> = when (key) {
        OVERLAY -> listOf(NONE) + overlays.filter { it != NONE }
        else -> option(key)?.values.orEmpty()
    }

    /** The value [delta] steps from [current] (wrapping; from an unknown value, the first/last). Null for an unknown key. */
    fun cycle(key: String, current: String, delta: Int, overlays: List<String>): String? {
        if (option(key) == null) return null
        return CoreOptionFilter.cycle(CoreOption(key, current, values = choices(key, overlays)), delta)
    }

    /** Every frontend key for [tag] with its value from [values] (kept raw), else its default. */
    fun fromConfig(values: Map<String, String>, tag: String = ""): Map<String, String> {
        val defaults = defaults(tag)
        return allFor(tag).associate { option ->
            val value = if (option.key == SCALING) migrateKey(values, SCALING, SCALING_V1, "Aspect") else values[option.key]
            option.key to (value ?: defaults.getValue(option.key))
        }
    }

    /** [values] without any frontend key: what the core receives as variables. */
    fun coreValues(values: Map<String, String>): Map<String, String> = values.filterKeys { !isFrontendKey(it) }
}
