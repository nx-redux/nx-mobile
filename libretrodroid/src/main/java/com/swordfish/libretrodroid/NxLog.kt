package com.swordfish.libretrodroid

import android.util.Log

/**
 * The app's and the runner's logger, gated by Settings → System → Debug log. Off (the default) skips verbose,
 * debug, info and warning lines; errors always print. Setting [enabled] also sets the native flag, so the
 * runner's LOGx macros and the cores' log callback follow it in the same process.
 */
object NxLog {
    // android.util.Log's priorities, kept here so the JVM test needs no android.jar values.
    internal const val VERBOSE = 2
    internal const val DEBUG = 3
    internal const val INFO = 4
    internal const val WARN = 5
    internal const val ERROR = 6

    internal val logcat: (Int, String, String, Throwable?) -> Unit = { priority, tag, msg, tr ->
        when (priority) {
            VERBOSE -> Log.v(tag, msg, tr)
            DEBUG -> Log.d(tag, msg, tr)
            INFO -> Log.i(tag, msg, tr)
            WARN -> Log.w(tag, msg, tr)
            else -> Log.e(tag, msg, tr)
        }
    }
    internal val libretroDroidFlag: (Boolean) -> Unit = { LibretroDroid.setLogEnabled(it) }

    /** Where lines go: logcat; a test swaps it. */
    internal var sink = logcat
    /** Mirrors [enabled] into the native library; a test swaps it (no native library on the JVM). */
    internal var nativeFlag = libretroDroidFlag

    /**
     * The first set loads the native library (LibretroDroid's class init), possibly from the Settings toggle on the UI
     * thread: a library that fails to load is logged, not thrown; the Kotlin side follows the setting regardless.
     */
    @Volatile var enabled: Boolean = false
        set(value) {
            field = value
            try {
                nativeFlag(value)
            } catch (t: LinkageError) {   // UnsatisfiedLinkError, ExceptionInInitializerError, then NoClassDefFoundError
                e("NxLog", "Debug log: the native library did not load; only app lines follow the setting", t)
            }
        }

    @JvmStatic fun v(tag: String, msg: String) { if (enabled) sink(VERBOSE, tag, msg, null) }
    @JvmStatic fun d(tag: String, msg: String) { if (enabled) sink(DEBUG, tag, msg, null) }
    @JvmStatic fun i(tag: String, msg: String) { if (enabled) sink(INFO, tag, msg, null) }
    @JvmStatic @JvmOverloads fun w(tag: String, msg: String, tr: Throwable? = null) { if (enabled) sink(WARN, tag, msg, tr) }
    @JvmStatic @JvmOverloads fun e(tag: String, msg: String, tr: Throwable? = null) { sink(ERROR, tag, msg, tr) }
}
