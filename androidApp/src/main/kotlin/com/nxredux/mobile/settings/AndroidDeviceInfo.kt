package com.nxredux.mobile.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.system.Os
import android.system.OsConstants
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.ui.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Settings -> System -> About on Android: the fixed rows from the package, BuildConfig and [Build]; the GPU from a throwaway GL context. */
object AndroidDeviceInfo {

    fun appInfo(context: Context): AppInfo {
        val pm = context.packageManager
        val pkg = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()
        return AppInfo(
            version = pkg.versionName ?: "?",
            versionCode = code,
            commit = BuildConfig.GIT_COMMIT,
            commitDate = BuildConfig.COMMIT_DATE,
            platform = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = device(),
            chip = chip(),
            pageSizeKb = runCatching { (Os.sysconf(OsConstants._SC_PAGESIZE) / 1024).toInt() }.getOrDefault(0),
        )
    }

    /** `Samsung SM-F956B`: the maker capitalised, left out when the model already starts with it. */
    private fun device(): String {
        val maker = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.titlecase() }
        val model = Build.MODEL.orEmpty().trim()
        return when {
            model.isEmpty() -> maker.ifEmpty { "Unknown" }
            maker.isEmpty() || model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    /**
     * `Qualcomm SM8650` from the SoC fields (API 31+, "unknown" parts left out; Qualcomm reports itself as "QTI"), else
     * the board's hardware name.
     */
    private fun chip(): String {
        if (Build.VERSION.SDK_INT >= 31) {
            val parts = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .map { it.orEmpty().trim() }.filter { it.isNotEmpty() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
                .map { if (it == "QTI") "Qualcomm" else it }
            if (parts.isNotEmpty()) return parts.joinToString(" ")
        }
        return Build.HARDWARE.orEmpty().trim().ifEmpty { "Unknown" }
    }

    private val gpuLock = Mutex()
    private var gpu: String? = null   // GL_RENDERER once read; the process asks the driver once

    /** GL_RENDERER (`Adreno (TM) 750`), read once off the main thread from a 1x1 pbuffer context; null when that failed. */
    suspend fun gpu(): String? = gpuLock.withLock {
        gpu ?: withContext(Dispatchers.IO) { readRenderer() }?.also { gpu = it }
    }

    /** Creates a GLES 3 (else 2) context on a 1x1 pbuffer, reads GL_RENDERER and releases everything again. */
    private fun readRenderer(): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        var surface = EGL14.EGL_NO_SURFACE
        var context = EGL14.EGL_NO_CONTEXT
        try {
            for ((renderable, clientVersion) in listOf(EGL_OPENGL_ES3_BIT_KHR to 3, EGL14.EGL_OPENGL_ES2_BIT to 2)) {
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                val attribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, renderable, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE,
                )
                if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] < 1) continue
                val config = configs[0] ?: continue
                context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE), 0)
                if (context == EGL14.EGL_NO_CONTEXT) continue
                surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
                if (surface == EGL14.EGL_NO_SURFACE) { EGL14.eglDestroyContext(display, context); context = EGL14.EGL_NO_CONTEXT; continue }
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    EGL14.eglDestroySurface(display, surface); surface = EGL14.EGL_NO_SURFACE
                    EGL14.eglDestroyContext(display, context); context = EGL14.EGL_NO_CONTEXT
                    continue
                }
                return GLES20.glGetString(GLES20.GL_RENDERER)?.trim()?.ifEmpty { null }
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
    }

    /** EGL_KHR_create_context's ES 3 renderable bit (EGL14 has no constant for it). */
    private const val EGL_OPENGL_ES3_BIT_KHR = 0x40

    /** Plain text on the clipboard, labelled as the app's. */
    fun copyText(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("NX Redux", text)) }
    }
}
