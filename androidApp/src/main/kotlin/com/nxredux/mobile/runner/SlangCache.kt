package com.nxredux.mobile.runner

import android.app.ActivityManager
import android.content.Context
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.shaders.PresetException
import com.nxredux.mobile.shaders.SlangPass
import com.swordfish.libretrodroid.LibretroDroid
import java.io.File
import java.security.MessageDigest

/**
 * Slang passes translated for this device's OpenGL ES ([LibretroDroid.translateSlang]), kept on disk: translating a
 * large preset takes seconds, and the result depends only on the pass's text, the GLSL ES version and the translator
 * (so on the app version). Lives in the cache folder: the system may clear it, and a pass is then translated again.
 */
object SlangCache {
    private const val PREFIX = "slang-"
    /** How a translated pass starts (slangtranslate.cpp). */
    private val MAGIC = "NXSLANG1\n".toByteArray()

    /** The GLSL ES version slang passes are written out as: what the device reports, 300 at least and 320 at most. */
    fun esVersion(context: Context): Int {
        val reported = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion
        val major = reported shr 16
        val minor = reported and 0xFFFF
        return (major * 100 + minor * 10).coerceIn(300, 320)
    }

    /**
     * [pass] translated to GLSL ES [esVersion], from the cache when it was translated before. Blocking (file I/O, and
     * up to seconds of CPU for a new pass). Throws [PresetException] with the compiler's first message.
     */
    fun translate(context: Context, pass: SlangPass, index: Int, esVersion: Int): ByteArray {
        val dir = File(context.cacheDir, PREFIX + BuildConfig.VERSION_CODE)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$esVersion\n".toByteArray())
        digest.update(pass.vertex.toByteArray())
        digest.update(0)
        digest.update(pass.fragment.toByteArray())
        val file = File(dir, digest.digest().joinToString("") { "%02x".format(it) })
        // Anything that is not a translated pass (a damaged file) is translated again and written over.
        runCatching { file.takeIf { it.isFile && it.length() > 0 }?.readBytes() }.getOrNull()
            ?.takeIf { it.size > MAGIC.size && it.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) }?.let { return it }

        val error = arrayOfNulls<String>(1)
        val bytes = LibretroDroid.translateSlang(pass.vertex, pass.fragment, esVersion, error)
            ?: throw PresetException("pass $index: ${error[0] ?: "cannot be translated"}")
        runCatching {
            if (!dir.isDirectory) {
                // Another app version's passes are never read again.
                context.cacheDir.listFiles { f -> f.isDirectory && f.name.startsWith(PREFIX) && f != dir }?.forEach { it.deleteRecursively() }
                dir.mkdirs()
            }
            // Written whole, then renamed: a reader never sees half a pass.
            val part = File(dir, file.name + ".part")
            part.writeBytes(bytes)
            if (!part.renameTo(file)) part.delete()
        }
        return bytes
    }
}
