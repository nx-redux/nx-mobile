package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.options.ShaderOptions
import java.io.File

/**
 * The shader presets (RetroArch `.glslp` + `.glsl` layout): the bundled set on the private SD root (`Shaders/`),
 * app-owned and replaced whole on APK update, so no place for user files; and the user's own in the home's `Shaders/`,
 * never touched by the app. A user preset's path carries [ShaderOptions.USER_PREFIX] ("User/glsl/x.glsl"), so the two
 * never collide and a stored choice says which folder it reads from.
 */
object Shaders {
    const val DIR = "Shaders"

    /** Where presets are read from: the [bundled] private root, and the home ([user]); null while the data is private. */
    data class Roots(val bundled: File, val user: File?) {
        companion object {
            /** The home is the user's folder only once it is not the private root (before the storage move it is). */
            fun of(privateRoot: File, dataRoot: File): Roots = Roots(privateRoot, dataRoot.takeIf { it.absoluteFile != privateRoot.absoluteFile })
        }
    }

    /**
     * The file for [path]: under the home's `Shaders/` with the user prefix (null with no home), else the bundled
     * one. A path a user preset resolves past its prefix ("User/../crt/x") is a bundled one, as it reads.
     */
    fun file(roots: Roots, path: String): File? {
        val user = ShaderOptions.userPath(path)
        return if (user != null) roots.user?.let { File(File(it, DIR), user) } else File(File(roots.bundled, DIR), path)
    }

    /** The text of a shader file by its path ([file]), or null when missing/unreadable. */
    fun read(roots: Roots, path: String): String? = runCatching { file(roots, path)?.takeIf { it.isFile }?.readText() }.getOrNull()

    /**
     * Every preset offered: the bundled ones, then the user's (prefixed), each sorted case-insensitively. Blocking I/O.
     */
    fun presets(roots: Roots): List<String> =
        presetsIn(File(roots.bundled, DIR)) + (roots.user?.let { presetsIn(File(it, DIR)) }.orEmpty()).map { ShaderOptions.USER_PREFIX + it }

    /**
     * The presets in [root]: `*.glslp` anywhere under it, and bare `*.glsl` directly in it or in `glsl/` (the nx-redux
     * layout). Relative paths, sorted case-insensitively; none for a missing folder.
     */
    internal fun presetsIn(root: File): List<String> {
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && !it.name.startsWith("._") && it.name != ".DS_Store" }.forEach { f ->
            val rel = f.relativeTo(root).path.replace('\\', '/')
            val bare = rel.endsWith(".glsl", ignoreCase = true) && (!rel.contains('/') || rel.startsWith("glsl/") && rel.count { it == '/' } == 1)
            if (rel.endsWith(".glslp", ignoreCase = true) || bare) out += rel
        }
        return out.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Syncs `sd/Shaders/` with the APK's shaders: the tree is app-owned and replaced whole on APK update (see
     * [BundledAssets.sync]). Blocking I/O: call off the main thread.
     */
    fun extractBundled(context: Context): Boolean = BundledAssets.sync(context, "shaders", DIR)
}
