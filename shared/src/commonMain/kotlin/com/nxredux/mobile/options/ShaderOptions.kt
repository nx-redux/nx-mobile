package com.nxredux.mobile.options

import com.nxredux.mobile.shaders.ShaderParam
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The in-game Shaders page's keys and value rules: one preset key plus one `nx_shader_param_<id>` per pragma parameter. */
object ShaderOptions {
    const val PRESET = "nx_shader"
    const val PARAM_PREFIX = "nx_shader_param_"
    const val NONE = "None"
    const val LINE_PRESET = "nx/line.glslp"
    const val GRID_PRESET = "nx/grid.glslp"
    /** A preset from the home's own `Shaders/` folder, not the bundled set: "User/" and its path there. */
    const val USER_PREFIX = "User/"
    /** Keys read once for migration and dropped on the next Save. */
    val MIGRATED_KEYS: Set<String> = setOf(FrontendOptions.EFFECT)
    private const val MAX_STEPS = 1024

    const val PRESET_LABEL = "Shader"
    const val PRESET_INFO = "Shader preset to run. Screen Sharpness is used where the preset leaves the filter unspecified."
    const val PARAMS_LABEL = "Shader Parameters"
    const val RESET_LABEL = "Reset Parameters"
    const val NO_PARAMS = "No settings for this shader."
    const val RESET_DONE = "Parameters reset."

    fun paramKey(id: String): String = PARAM_PREFIX + id
    fun paramId(key: String): String? = if (key.startsWith(PARAM_PREFIX) && key.length > PARAM_PREFIX.length) key.removePrefix(PARAM_PREFIX) else null
    fun isShaderKey(key: String): Boolean = key == PRESET || paramId(key) != null

    /** Fixed two decimals, rounded half-up, stable for round-tripping through the config file. */
    fun format2(v: Float): String {
        val n = (v * 100f).roundToInt()
        val a = abs(n)
        return (if (n < 0) "-" else "") + (a / 100) + "." + (a % 100).toString().padStart(2, '0')
    }

    /** [path]'s place in the home's `Shaders/` folder, or null for a bundled preset. */
    fun userPath(path: String): String? = path.takeIf { it.startsWith(USER_PREFIX) }?.removePrefix(USER_PREFIX)

    fun presetChoices(presets: List<String>): List<String> = listOf(NONE) + presets.filter { it != NONE }

    /** `min, min+step, …` ending at `max` (appended when the steps don't land on it), at most [MAX_STEPS] entries. */
    fun paramChoices(p: ShaderParam): List<String> {
        if (p.max < p.min) return listOf(format2(p.min))
        if (p.step <= 0f || p.max == p.min) return listOf(format2(p.min), format2(p.max)).distinct()
        val out = ArrayList<String>()
        var i = 0
        while (out.size < MAX_STEPS) {
            val v = p.min + i * p.step
            if (v > p.max + 1e-4f) break
            out += format2(v)
            i++
        }
        val last = format2(p.max)
        if (out.size < MAX_STEPS && out.last() != last) out += last
        return out.distinct()
    }

    fun choices(key: String, presets: List<String>, params: List<ShaderParam>): List<String>? = when {
        key == PRESET -> presetChoices(presets)
        else -> paramId(key)?.let { id -> params.firstOrNull { it.id == id } }?.let(::paramChoices)
    }

    /**
     * The value [delta] steps from [current] (wrapping). From a number off the parameter's grid (a preset default
     * such as 1000 on a 80, 180, … grid), the nearest choice in [delta]'s direction; from any other unknown value,
     * the first/last. Null for an unknown key.
     */
    fun cycle(key: String, current: String, delta: Int, presets: List<String>, params: List<ShaderParam>): String? {
        val values = choices(key, presets, params) ?: return null
        val number = current.toFloatOrNull()?.takeIf { it.isFinite() }
        if (key != PRESET && delta != 0 && number != null && current !in values) {
            val numbers = values.map { it.toFloat() }
            val i = if (delta > 0) numbers.indexOfFirst { it > number }.takeIf { it >= 0 } ?: 0
            else numbers.indexOfLast { it < number }.takeIf { it >= 0 } ?: values.lastIndex
            return values[i]
        }
        return CoreOptionFilter.cycle(CoreOption(key, current, values = values), delta)
    }

    /**
     * The preset key (default None) and every stored parameter key, raw. A config with the retired `nx_screen_effect`
     * ([FrontendOptions.EFFECT]): Line/Grid select the bundled preset when no preset key is stored.
     */
    fun fromConfig(values: Map<String, String>): Map<String, String> {
        val preset = values[PRESET]?.let(::current) ?: when (values[FrontendOptions.EFFECT]) {
            "Line" -> LINE_PRESET
            "Grid" -> GRID_PRESET
            else -> NONE
        }
        return mapOf(PRESET to preset) + values.filterKeys { paramId(it) != null }
    }

    fun preset(values: Map<String, String>): String? = values[PRESET]?.takeIf { it.isNotEmpty() && it != NONE }?.let(::current)

    /**
     * The bundled GLSL presets that now ship as their slang versions only, each with the preset that took its place: a
     * game or console saved with one keeps its shader.
     */
    val REPLACED: Map<String, String> = mapOf(
        "crt/crt-easymode.glslp" to "slang/crt/crt-easymode.slangp",
        "crt/crt-geom.glslp" to "slang/crt/crt-geom.slangp",
        "crt/crt-pi.glslp" to "slang/crt/crt-pi.slangp",
        "crt/zfast-crt.glslp" to "slang/crt/zfast-crt.slangp",
        "handheld/dot.glslp" to "slang/handheld/dot.slangp",
        "handheld/gameboy.glslp" to "slang/handheld/gameboy.slangp",
        "handheld/lcd3x.glslp" to "slang/handheld/lcd3x.slangp",
        "interpolation/sharp-bilinear.glslp" to "slang/pixel-art-scaling/sharp-bilinear.slangp",
        "xbrz/xbrz-freescale.glslp" to "slang/edge-smoothing/xbrz/xbrz-freescale.slangp",
        "glsl/aa-shader-4.0.glsl" to "slang/anti-aliasing/aa-shader-4.0.slangp",
        "glsl/bicubic.glsl" to "slang/interpolation/bicubic.slangp",
        "glsl/edge1pixel.glsl" to "slang/pixel-art-scaling/edge1pixel.slangp",
        "glsl/lcd1x.glsl" to "slang/handheld/lcd1x.slangp",
        "glsl/lcd3x.glsl" to "slang/handheld/lcd3x.slangp",
        "glsl/pixellate.glsl" to "slang/pixel-art-scaling/pixellate.slangp",
        "glsl/res-independent-scanlines.glsl" to "slang/scanlines/res-independent-scanlines.slangp",
        "glsl/retro-v2.glsl" to "slang/handheld/retro-v2.slangp",
        "glsl/scale3x.glsl" to "slang/edge-smoothing/scalenx/scale3x.slangp",
        "glsl/scanline.glsl" to "slang/scanlines/scanline.slangp",
        "glsl/sharp-bilinear.glsl" to "slang/pixel-art-scaling/sharp-bilinear.slangp",
        "glsl/sharp-shimmerless.glsl" to "slang/pixel-art-scaling/sharp-shimmerless.slangp",
    )

    /** The stored preset [value] as it is offered now ([REPLACED]); any other value as it is. */
    fun current(value: String): String = REPLACED[value] ?: value

    /** id → value to upload: the stored value clamped to the range, else the parameter's default. */
    fun paramValues(values: Map<String, String>, params: List<ShaderParam>): Map<String, Float> =
        params.associate { p ->
            // min/max ordered here too: ShaderParam is public, so a hand-built inverted range must not throw.
            val stored = values[paramKey(p.id)]?.toFloatOrNull()?.takeIf { it.isFinite() }
            p.id to (stored?.coerceIn(min(p.min, p.max), max(p.min, p.max)) ?: p.default)
        }

    /** What Save writes: the preset and the active preset's parameters (stored value, else default). */
    fun activeParamEntries(values: Map<String, String>, params: List<ShaderParam>): List<Pair<String, String>> =
        listOf(PRESET to (values[PRESET] ?: NONE)) + params.map { p -> paramKey(p.id) to (values[paramKey(p.id)] ?: format2(p.default)) }

    fun resetEntries(params: List<ShaderParam>): List<Pair<String, String>> = params.map { paramKey(it.id) to format2(it.default) }
}
