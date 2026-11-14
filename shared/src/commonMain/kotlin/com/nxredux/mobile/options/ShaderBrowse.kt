package com.nxredux.mobile.options

/**
 * The shader picker's levels, from the flat preset list ([ShaderOptions.presetChoices]' paths: the bundled ones, then
 * the user's under [ShaderOptions.USER_PREFIX]). The top level offers None, the bundled presets in a few named groups
 * (by what they do to the picture, each with a line saying so) and the user's folder; below it the user's folders are
 * browsed as they are on disk, one level at a time. A bundled shader that ships in both formats is listed once, as its
 * slang preset.
 */
object ShaderBrowse {
    const val TITLE = "Shader"
    const val YOURS = "Your shaders"
    /** The path of a bundled group's level; a user level's path is its folder under the prefix ("User", "User/crt"). */
    private const val GROUP = "@"
    private const val USER = "User"
    const val YOURS_INFO = "Presets from the Shaders folder in your home folder."
    /** The bundled groups in the order shown, each with what its shaders do. */
    private val GROUPS = linkedMapOf(
        "CRT" to "An old tube TV: scanlines, a phosphor mask and glow. For home consoles and arcade games.",
        "Handheld" to "A handheld's LCD: a visible pixel grid, and the Game Boy's green screen.",
        "Smoothing" to "No retro look: sharper pixels without shimmer, or jagged edges rounded off.",
        "NTSC" to "The analogue video signal: colour bleed and blur, as through a composite cable.",
        "Effects" to "Other looks: a curved screen, colour controls, big pixels, a painted picture.",
    )
    /** The single-file shaders (the nx-redux layout's `glsl/`, and `nx/`) by effect; one not listed here is an effect. */
    private val SINGLE = mapOf(
        "CRT" to setOf("crt-mini", "crt-perfect", "scanline", "res-independent-scanlines", "retro-v2", "line"),
        "Handheld" to setOf("lcd-mini", "lcd-perfect", "lcd1x", "lcd3x", "dmg-mini", "dmg-perfect", "sharp-shimmerless-grid",
            "sharp-shimmerless-subpixel-vrgb", "grid"),
        "Smoothing" to setOf("aa-shader-4.0", "bicubic", "pixel_art_aa", "scale3x", "sharp-bilinear", "sharp-shimmerless", "pixel-perfect",
            "fast-sharpen", "edge1pixel", "stock"),
    )
    private val UPPER = setOf("crt", "ntsc", "lcd", "hd", "dmg", "aa", "vrgb")
    private val SPELLED = mapOf("scalefx" to "ScaleFX", "xbrz" to "xBRZ", "zfast" to "zFast", "lcd1x" to "LCD1x", "lcd3x" to "LCD3x", "v2" to "v2")

    sealed interface Entry { val label: String }
    /** A level to open: [path] is its [entries] path; [info] says what is in it (the bundled groups, the user's folder). */
    data class Folder(override val label: String, val path: String, val info: String = "") : Entry
    /** A preset to use: [value] is the stored `nx_shader` value. */
    data class Preset(override val label: String, val value: String) : Entry

    /** The level at [path] ("" the top): folders first below the top, each part sorted by name. Empty for an unknown path. */
    fun entries(presets: List<String>, path: String): List<Entry> {
        val bundled = presets.filter { it != ShaderOptions.NONE && ShaderOptions.userPath(it) == null }
        val user = presets.mapNotNull(ShaderOptions::userPath)
        return when {
            path.isEmpty() -> buildList {
                add(Preset(ShaderOptions.NONE, ShaderOptions.NONE))
                val used = bundled.map(::group).toSet()
                GROUPS.filter { it.key in used }.forEach { (name, info) -> add(Folder(name, GROUP + name, info)) }
                if (user.isNotEmpty()) add(Folder(YOURS, USER, YOURS_INFO))
            }
            path.startsWith(GROUP) -> bundled.filter { group(it) == path.removePrefix(GROUP) }
                .groupBy { name(it) }
                .map { (label, same) -> Preset(label, same.firstOrNull { it.endsWith(".slangp", ignoreCase = true) } ?: same.first()) }
                .sortedBy { it.label.lowercase() }
            path == USER || path.startsWith("$USER/") -> {
                val inside = if (path == USER) "" else path.removePrefix("$USER/") + "/"
                val below = user.filter { it.startsWith(inside) }.map { it.removePrefix(inside) }
                val folders = below.filter { '/' in it }.map { it.substringBefore('/') }.distinct().sortedBy { it.lowercase() }
                val files = below.filter { '/' !in it }.sortedBy { it.lowercase() }
                // Two files of one name in a folder (x.glslp beside x.slangp) keep their extensions to tell them apart.
                val stems = files.groupingBy { stem(it).lowercase() }.eachCount()
                folders.map { Folder(it, "$USER/$inside$it") } +
                    files.map { Preset(if (stems[stem(it).lowercase()] == 1) stem(it) else it, ShaderOptions.USER_PREFIX + inside + it) }
            }
            else -> emptyList()
        }
    }

    /** The name a level shows as its title. */
    fun title(path: String): String = when {
        path.isEmpty() -> TITLE
        path.startsWith(GROUP) -> path.removePrefix(GROUP)
        path == USER -> YOURS
        else -> path.substringAfterLast('/')
    }

    /** The line over a level's list: what a bundled group's shaders do; none for the top level and the user's folders. */
    fun intro(path: String): String = if (path.startsWith(GROUP)) GROUPS[path.removePrefix(GROUP)].orEmpty() else ""

    /** What the Shader row shows for the stored [value]: a bundled preset by its name, a user's by its file name. */
    fun label(value: String): String = when {
        value.isEmpty() || value == ShaderOptions.NONE -> ShaderOptions.NONE
        ShaderOptions.userPath(value) != null -> stem(value.substringAfterLast('/'))
        else -> name(value)
    }

    /** Whether the stored [current] value is [preset]'s shader: itself, or the other format of the same bundled one. */
    fun isCurrent(preset: Preset, current: String): Boolean {
        val now = current.ifEmpty { ShaderOptions.NONE }
        if (preset.value == now) return true
        if (now == ShaderOptions.NONE || preset.value == ShaderOptions.NONE) return false
        if (ShaderOptions.userPath(now) != null || ShaderOptions.userPath(preset.value) != null) return false
        return group(now) == group(preset.value) && name(now) == name(preset.value)
    }

    /** The values of the presets at [path], in the level's order: what LEFT / RIGHT step through in a preset's preview. */
    fun values(presets: List<String>, path: String): List<String> = entries(presets, path).filterIsInstance<Preset>().map { it.value }

    /**
     * A bundled preset's group: by the folder it ships in (the slang set mirrors the layout under `slang/`), a
     * single-file shader by what it does ([SINGLE]).
     */
    private fun group(path: String): String = when (path.removePrefix("slang/").substringBefore('/', "")) {
        "crt" -> "CRT"
        "handheld" -> "Handheld"
        "interpolation", "xbrz", "edge-smoothing", "anti-aliasing" -> "Smoothing"
        "ntsc" -> "NTSC"
        else -> stem(path.substringAfterLast('/')).lowercase().let { file -> SINGLE.entries.firstOrNull { file in it.value }?.key } ?: "Effects"
    }

    /** A bundled preset's name from its file name: "crt-guest-advanced-hd" is "CRT Guest Advanced HD". */
    private fun name(path: String): String =
        stem(path.substringAfterLast('/')).split('-', '_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { word ->
            val lower = word.lowercase()
            SPELLED[lower] ?: if (lower in UPPER) lower.uppercase() else word.replaceFirstChar { it.uppercase() }
        }

    private fun stem(file: String): String = file.substringBeforeLast('.', file)
}
