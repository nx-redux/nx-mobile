package com.nxredux.mobile.shaders

enum class ScaleType { NONE, SOURCE, VIEWPORT, ABSOLUTE }
/** [BORDER] (transparent black outside the texture) is asked for by slang presets only; GLSL presets get [EDGE] for it. */
enum class WrapMode { EDGE, REPEAT, MIRRORED, BORDER }

/** One pass of a RetroArch legacy GLSL preset, with its source text (pragma lines stripped). */
data class ShaderPass(
    val source: String,
    /** null = unspecified: the host samples with its Screen Sharpness filter. */
    val filterLinear: Boolean?,
    val wrap: WrapMode,
    val scaleTypeX: ScaleType,
    val scaleTypeY: ScaleType,
    val scaleX: Float,
    val scaleY: Float,
    val frameCountMod: Int,
    val alias: String,
)

/** A lookup texture the preset samples by [id]; [path] is relative to the shaders root. */
data class ShaderLut(val id: String, val path: String, val linear: Boolean, val wrap: WrapMode)

/** A `#pragma parameter`; [default] already reflects any `<id> = value` override in the preset. */
data class ShaderParam(val id: String, val label: String, val default: Float, val min: Float, val max: Float, val step: Float)

data class ParsedChain(val passes: List<ShaderPass>, val luts: List<ShaderLut>, val params: List<ShaderParam>, val warnings: List<String>)

class PresetException(message: String) : Exception(message)

/**
 * RetroArch `.glslp` presets and single `.glsl` files (the libretro `glsl-shaders` format), resolved into plain
 * data: no file I/O beyond the [read] callback, no GL. Unsupported features warn; `#reference` is an error.
 */
object GlslPreset {
    const val MAX_PASSES = 8
    const val MAX_LUTS = 16
    private val IGNORED_PASS_KEYS = listOf("float_framebuffer", "srgb_framebuffer", "rgb10_framebuffer", "mipmap_input")
    private const val BOM = "\uFEFF"
    private val PRAGMA = Regex("""^\s*#pragma\s+parameter\s+(\S+)\s+"([^"]*)"\s+(\S+)\s+(\S+)\s+(\S+)(?:\s+(\S+))?(?:\s.*)?$""")

    fun parse(presetPath: String, read: (String) -> String?): ParsedChain {
        val path = normalize(presetPath) ?: throw PresetException("bad path: $presetPath")
        val text = read(path) ?: throw PresetException("not found: $path")
        if (path.endsWith(".glsl", ignoreCase = true)) {
            val params = pragmas(text)
            val pass = ShaderPass(stripPragmas(text), null, WrapMode.EDGE, ScaleType.NONE, ScaleType.NONE, 1f, 1f, 0, "")
            return ParsedChain(listOf(pass), emptyList(), params, emptyList())
        }
        val dir = path.substringBeforeLast('/', "")
        val warnings = mutableListOf<String>()
        val cfg = LinkedHashMap<String, String>()
        for (raw in text.removePrefix(BOM).lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#reference")) throw PresetException("unsupported: #reference")
            if (line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            cfg.putIfAbsent(line.substring(0, eq).trim(), unquote(line.substring(eq + 1)))
        }
        val count = cfg["shaders"]?.toIntOrNull() ?: throw PresetException("missing shaders count")
        if (count < 1) throw PresetException("shaders must be at least 1")
        if (count > MAX_PASSES) throw PresetException("too many passes: $count (max $MAX_PASSES)")
        val params = mutableListOf<ShaderParam>()
        val passes = (0 until count).map { n ->
            val rel = cfg["shader$n"] ?: throw PresetException("missing shader$n")
            val file = resolve(dir, rel) ?: throw PresetException("shader$n escapes the shaders folder: $rel")
            val source = read(file) ?: throw PresetException("not found: $file")
            for (p in pragmas(source)) if (params.none { it.id == p.id }) params += p
            IGNORED_PASS_KEYS.forEach { k -> if (cfg.containsKey("$k$n")) warnings += "$k$n ignored" }
            val both = cfg["scale_type$n"]?.let(::scaleType)
            var typeX = both ?: cfg["scale_type_x$n"]?.let(::scaleType)
            var typeY = both ?: cfg["scale_type_y$n"]?.let(::scaleType)
            if (typeX != null && typeY == null) typeY = ScaleType.SOURCE
            if (typeY != null && typeX == null) typeX = ScaleType.SOURCE
            val scale = cfg["scale$n"]?.toFloatOrNull()
            ShaderPass(
                source = stripPragmas(source),
                filterLinear = cfg["filter_linear$n"]?.let(::bool),
                wrap = wrap(cfg["wrap_mode$n"]),
                scaleTypeX = typeX ?: ScaleType.NONE,
                scaleTypeY = typeY ?: ScaleType.NONE,
                scaleX = scale ?: cfg["scale_x$n"]?.toFloatOrNull() ?: 1f,
                scaleY = scale ?: cfg["scale_y$n"]?.toFloatOrNull() ?: 1f,
                frameCountMod = cfg["frame_count_mod$n"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                alias = cfg["alias$n"].orEmpty(),
            )
        }
        if (cfg.containsKey("feedback_pass")) warnings += "feedback_pass ignored"
        if (cfg.containsKey("parameters")) warnings += "parameters ignored"
        val lutIds = cfg["textures"]?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (lutIds.size > MAX_LUTS) warnings += "textures beyond $MAX_LUTS ignored"
        val luts = lutIds.take(MAX_LUTS).mapNotNull { id ->
            val rel = cfg[id]
            val file = rel?.let { resolve(dir, it) }
            if (file == null) { warnings += "texture $id has no file"; null } else {
                if (cfg.containsKey("${id}_mipmap")) warnings += "${id}_mipmap ignored"
                ShaderLut(id, file, cfg["${id}_linear"]?.let(::bool) ?: true, wrap(cfg["${id}_wrap_mode"]))
            }
        }
        val overridden = params.map { p -> cfg[p.id]?.toFloatOrNull()?.takeIf { it.isFinite() }?.let { p.copy(default = it.coerceIn(p.min, p.max)) } ?: p }
        return ParsedChain(passes, luts, overridden, warnings)
    }

    fun pragmas(source: String): List<ShaderParam> {
        val out = mutableListOf<ShaderParam>()
        for (line in source.removePrefix(BOM).lines()) {
            val m = PRAGMA.find(line) ?: continue
            val (id, label, def, min, max, step) = m.destructured
            val minF = min.toFloatOrNull() ?: continue
            val maxF = max.toFloatOrNull() ?: continue
            val defF = def.toFloatOrNull() ?: continue
            val stepF = step.toFloatOrNull() ?: (0.1f * (maxF - minF))
            // A malformed range would crash the menu later (empty coerceIn range, NaN formatting).
            if (!(minF.isFinite() && maxF.isFinite() && defF.isFinite() && stepF.isFinite()) || maxF < minF) continue
            if (out.none { it.id == id }) out += ShaderParam(id, label, defF.coerceIn(minF, maxF), minF, maxF, stepF)
        }
        return out
    }

    /** [source] without its pragma lines and without a leading BOM, which would hide a first-line `#version`. */
    fun stripPragmas(source: String): String =
        source.removePrefix(BOM).lines().filterNot { PRAGMA.matches(it) }.joinToString("\n")

    fun normalize(path: String): String? {
        val parts = ArrayDeque<String>()
        for (p in path.replace('\\', '/').split('/')) {
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(p)
            }
        }
        return parts.joinToString("/")
    }

    internal fun resolve(dir: String, rel: String): String? = normalize(if (dir.isEmpty()) rel else "$dir/$rel")

    internal fun unquote(v: String): String = v.trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }

    internal fun bool(v: String) = v.equals("true", ignoreCase = true) || v == "1"

    internal fun scaleType(v: String): ScaleType? = when (v.lowercase()) {
        "source" -> ScaleType.SOURCE
        "viewport" -> ScaleType.VIEWPORT
        "absolute" -> ScaleType.ABSOLUTE
        else -> null
    }

    /** RetroArch's default is clamp_to_border, which GLES lacks: edge (what RetroArch itself does on GLES). */
    private fun wrap(v: String?): WrapMode = when (v?.lowercase()) {
        "repeat" -> WrapMode.REPEAT
        "mirrored_repeat" -> WrapMode.MIRRORED
        else -> WrapMode.EDGE
    }
}
