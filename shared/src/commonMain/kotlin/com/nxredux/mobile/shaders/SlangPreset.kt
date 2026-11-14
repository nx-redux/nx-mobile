package com.nxredux.mobile.shaders

/**
 * One pass of a RetroArch slang preset: each stage a complete `#version 450` source (includes expanded, the other
 * stage's lines and the slang pragmas blanked, so both stages number their lines alike).
 */
data class SlangPass(
    val vertex: String,
    val fragment: String,
    /** null = unspecified: the host samples with its Screen Sharpness filter. */
    val filterLinear: Boolean?,
    val wrap: WrapMode,
    val scaleTypeX: ScaleType,
    val scaleTypeY: ScaleType,
    val scaleX: Float,
    val scaleY: Float,
    val frameCountMod: Int,
    /** The preset's `aliasN`, else the shader's `#pragma name`; empty for none. */
    val alias: String,
    /** The pass's framebuffer format by its slang name ("R8G8B8A8_UNORM", "R16G16B16A16_SFLOAT", ...). */
    val format: String,
    /** The pass's input gets mipmaps. */
    val mipmapInput: Boolean,
)

/** A lookup texture a slang preset samples by [id]; [path] is relative to the shaders root. */
data class SlangLut(val id: String, val path: String, val linear: Boolean, val wrap: WrapMode, val mipmap: Boolean)

data class ParsedSlangChain(val passes: List<SlangPass>, val luts: List<SlangLut>, val params: List<ShaderParam>)

/**
 * RetroArch `.slangp` presets and single `.slang` files (the libretro `slang-shaders` format), resolved into plain
 * data: no file I/O beyond the [read] callback, no GL. A folder copied from RetroArch works as it is: `#reference`
 * chains, `#include`s and paths that climb out of the preset's folder all resolve inside the shaders root.
 */
object SlangPreset {
    /** Presets in the wild stop near 50 passes; past this a file is broken or hostile. */
    const val MAX_PASSES = 128
    const val MAX_LUTS = 64
    const val DEFAULT_FORMAT = "R8G8B8A8_UNORM"
    private const val SRGB_FORMAT = "R8G8B8A8_SRGB"
    private const val FLOAT_FORMAT = "R16G16B16A16_SFLOAT"
    private const val MAX_DEPTH = 32
    private const val MAX_SOURCE_CHARS = 8 * 1024 * 1024
    private const val BOM = "﻿"
    private val REFERENCE = Regex("""^\s*#reference\s+"?([^"]+?)"?\s*$""")
    private val INCLUDE = Regex("""^\s*#include\s+"([^"]+)"""")
    private val INCLUDE_OPTIONAL = Regex("""^\s*#pragma\s+include_optional\s+"([^"]+)"""")
    private val STAGE = Regex("""^\s*#pragma\s+stage\s+(\w+)""")
    private val NAME = Regex("""^\s*#pragma\s+name\s+(\S+)""")
    private val FORMAT = Regex("""^\s*#pragma\s+format\s+(\S+)""")
    private val PARAMETER = Regex("""^\s*#pragma\s+parameter\s""")

    fun isSlang(path: String): Boolean = path.endsWith(".slangp", ignoreCase = true) || path.endsWith(".slang", ignoreCase = true)

    /** A preset value and the folder of the file that set it, which a path value is relative to. */
    private data class Entry(val value: String, val dir: String)

    /** One `.slang` file split into its stages, with what its pragmas declare. */
    data class Shader(val vertex: String, val fragment: String, val name: String, val format: String, val params: List<ShaderParam>)

    fun parse(presetPath: String, read: (String) -> String?): ParsedSlangChain {
        val path = GlslPreset.normalize(presetPath) ?: throw PresetException("bad path: $presetPath")
        if (path.endsWith(".slang", ignoreCase = true)) {
            val shader = shader(path, read)
            val pass = SlangPass(
                shader.vertex, shader.fragment, null, WrapMode.BORDER, ScaleType.NONE, ScaleType.NONE, 1f, 1f, 0,
                shader.name, shader.format.ifEmpty { DEFAULT_FORMAT }, false,
            )
            return ParsedSlangChain(listOf(pass), emptyList(), shader.params)
        }
        val cfg = LinkedHashMap<String, Entry>()
        preset(path, read, cfg, 0)
        fun value(key: String): String? = cfg[key]?.value
        fun file(key: String): String? = cfg[key]?.let { GlslPreset.resolve(it.dir, it.value) }

        val count = value("shaders")?.toIntOrNull() ?: throw PresetException("missing shaders count")
        if (count < 1) throw PresetException("shaders must be at least 1")
        if (count > MAX_PASSES) throw PresetException("too many passes: $count (max $MAX_PASSES)")
        val params = mutableListOf<ShaderParam>()
        // A pass file used twice (common in the bezel packs) is read and split once.
        val shaders = HashMap<String, Shader>()
        val passes = (0 until count).map { n ->
            if (value("shader$n") == null) throw PresetException("missing shader$n")
            val shaderFile = file("shader$n") ?: throw PresetException("shader$n escapes the shaders folder: ${value("shader$n")}")
            val shader = shaders.getOrPut(shaderFile) { shader(shaderFile, read) }
            for (p in shader.params) if (params.none { it.id == p.id }) params += p
            val both = value("scale_type$n")?.let(GlslPreset::scaleType)
            var typeX = both ?: value("scale_type_x$n")?.let(GlslPreset::scaleType)
            var typeY = both ?: value("scale_type_y$n")?.let(GlslPreset::scaleType)
            if (typeX != null && typeY == null) typeY = ScaleType.SOURCE
            if (typeY != null && typeX == null) typeX = ScaleType.SOURCE
            val scale = value("scale$n")?.toFloatOrNull()
            val format = when {
                value("srgb_framebuffer$n")?.let(GlslPreset::bool) == true -> SRGB_FORMAT
                value("float_framebuffer$n")?.let(GlslPreset::bool) == true -> FLOAT_FORMAT
                else -> shader.format.ifEmpty { DEFAULT_FORMAT }
            }
            SlangPass(
                vertex = shader.vertex,
                fragment = shader.fragment,
                filterLinear = value("filter_linear$n")?.let(GlslPreset::bool),
                wrap = wrap(value("wrap_mode$n")),
                scaleTypeX = typeX ?: ScaleType.NONE,
                scaleTypeY = typeY ?: ScaleType.NONE,
                scaleX = scale ?: value("scale_x$n")?.toFloatOrNull() ?: 1f,
                scaleY = scale ?: value("scale_y$n")?.toFloatOrNull() ?: 1f,
                frameCountMod = value("frame_count_mod$n")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                alias = value("alias$n")?.takeIf { it.isNotEmpty() } ?: shader.name,
                format = format,
                mipmapInput = value("mipmap_input$n")?.let(GlslPreset::bool) == true,
            )
        }
        val lutIds = value("textures")?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().distinct()
        if (lutIds.size > MAX_LUTS) throw PresetException("too many textures: ${lutIds.size} (max $MAX_LUTS)")
        val luts = lutIds.map { id ->
            val lutFile = file(id) ?: throw PresetException("texture $id has no file")
            SlangLut(
                id, lutFile,
                linear = value("${id}_linear")?.let(GlslPreset::bool) ?: true,
                wrap = wrap(value("${id}_wrap_mode")),
                mipmap = value("${id}_mipmap")?.let(GlslPreset::bool) == true,
            )
        }
        val overridden = params.map { p ->
            value(p.id)?.toFloatOrNull()?.takeIf { it.isFinite() }?.let { p.copy(default = it.coerceIn(p.min, p.max)) } ?: p
        }
        return ParsedSlangChain(passes, luts, overridden)
    }

    /**
     * Reads [path] into [cfg]: the presets it `#reference`s first (in order), then its own values over theirs, so a
     * "simple preset" is its base with a few values changed.
     */
    private fun preset(path: String, read: (String) -> String?, cfg: LinkedHashMap<String, Entry>, depth: Int) {
        if (depth > MAX_DEPTH) throw PresetException("#reference chain too deep: $path")
        val text = read(path) ?: throw PresetException("not found: $path")
        val dir = path.substringBeforeLast('/', "")
        val own = LinkedHashMap<String, Entry>()
        for (raw in text.removePrefix(BOM).lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val reference = REFERENCE.find(line)
            if (reference != null) {
                val target = GlslPreset.resolve(dir, reference.groupValues[1].trim())
                    ?: throw PresetException("#reference escapes the shaders folder: ${reference.groupValues[1]}")
                preset(target, read, cfg, depth + 1)
                continue
            }
            if (line.startsWith("#") || line.startsWith("//")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            // The first of a repeated key wins within one file.
            own.putIfAbsent(line.substring(0, eq).trim(), Entry(value(line.substring(eq + 1)), dir))
        }
        cfg.putAll(own)
    }

    /** A preset value: the quoted text, else the text up to a trailing comment. */
    internal fun value(raw: String): String {
        val v = raw.trim()
        if (v.startsWith('"')) {
            val end = v.indexOf('"', 1)
            return if (end > 0) v.substring(1, end) else v.substring(1)
        }
        val comment = listOf(v.indexOf(" #"), v.indexOf("\t#"), v.indexOf(" //"), v.indexOf("\t//")).filter { it >= 0 }.minOrNull()
        return (if (comment != null) v.substring(0, comment) else v).trim()
    }

    /** The `.slang` file at [path], includes expanded and split into its two stages. */
    fun shader(path: String, read: (String) -> String?): Shader {
        val lines = ArrayList<String>()
        val budget = intArrayOf(MAX_SOURCE_CHARS)
        expand(path, read, lines, 0, budget)
        return split(path, lines)
    }

    /** Appends [path]'s lines to [out], each `#include` replaced by the file it names (relative to the includer). */
    private fun expand(path: String, read: (String) -> String?, out: MutableList<String>, depth: Int, budget: IntArray) {
        if (depth > MAX_DEPTH) throw PresetException("#include chain too deep: $path")
        val text = read(path) ?: throw PresetException("not found: $path")
        budget[0] -= text.length
        if (budget[0] < 0) throw PresetException("shader too large: $path")
        val dir = path.substringBeforeLast('/', "")
        for (line in text.removePrefix(BOM).lines()) {
            val include = INCLUDE.find(line)
            val optional = if (include == null) INCLUDE_OPTIONAL.find(line) else null
            val name = (include ?: optional)?.groupValues?.get(1)
            if (name == null) {
                out += line.trimEnd('\r')
                continue
            }
            val target = GlslPreset.resolve(dir, name)
            when {
                target == null && optional == null -> throw PresetException("#include escapes the shaders folder: $name")
                target == null -> Unit
                // An optional include that is not there is simply left out.
                optional != null && read(target) == null -> Unit
                else -> expand(target, read, out, depth + 1, budget)
            }
        }
    }

    private enum class Stage { SHARED, VERTEX, FRAGMENT }

    private fun split(path: String, lines: List<String>): Shader {
        val vertex = StringBuilder()
        val fragment = StringBuilder()
        var stage = Stage.SHARED
        var name = ""
        var format = ""
        var sawVertex = false
        var sawFragment = false
        for (line in lines) {
            val stageName = STAGE.find(line)?.groupValues?.get(1)
            var toVertex = stage != Stage.FRAGMENT
            var toFragment = stage != Stage.VERTEX
            when {
                stageName != null -> {
                    stage = when (stageName) {
                        "vertex" -> { sawVertex = true; Stage.VERTEX }
                        "fragment" -> { sawFragment = true; Stage.FRAGMENT }
                        else -> throw PresetException("unknown stage $stageName: $path")
                    }
                    toVertex = false; toFragment = false
                }
                NAME.containsMatchIn(line) -> { name = NAME.find(line)!!.groupValues[1]; toVertex = false; toFragment = false }
                FORMAT.containsMatchIn(line) -> { format = FORMAT.find(line)!!.groupValues[1]; toVertex = false; toFragment = false }
                PARAMETER.containsMatchIn(line) -> { toVertex = false; toFragment = false }
            }
            // The other stage's lines become blank ones: a compile error then points at the same line in either stage.
            if (toVertex) vertex.append(line)
            vertex.append('\n')
            if (toFragment) fragment.append(line)
            fragment.append('\n')
        }
        if (!sawVertex || !sawFragment) throw PresetException("no vertex and fragment stage: $path")
        return Shader(vertex.toString(), fragment.toString(), name, format, GlslPreset.pragmas(lines.joinToString("\n")))
    }

    /** Slang's default is clamp_to_border. */
    private fun wrap(v: String?): WrapMode = when (v?.lowercase()) {
        "repeat" -> WrapMode.REPEAT
        "mirrored_repeat" -> WrapMode.MIRRORED
        "clamp_to_edge" -> WrapMode.EDGE
        else -> WrapMode.BORDER
    }
}
