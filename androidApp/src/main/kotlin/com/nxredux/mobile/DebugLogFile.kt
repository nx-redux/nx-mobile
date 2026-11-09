package com.nxredux.mobile

import android.content.Context
import android.os.Build
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.FileRefs
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * With Settings → System → Debug log on, this process's log lines also go to a file in the home folder's [DIR], so a
 * player without adb can attach it to a bug report. Each process (the menu's, the game's) writes its own file from a
 * `logcat` child filtered to its pid: that catches the app, the runner, every core (also those that print through
 * `__android_log_print` themselves), and the lines already in the buffer from before the setting was turned on.
 *
 * The app reads the child's output and writes the file itself, flushing whenever the pipe runs dry: `logcat -f` writes
 * in 4 KB blocks, so the last lines before a crash (the ones a report needs) would die in its buffer. A Java crash's
 * stack is written straight from the uncaught-exception handler too, since the process may be gone before those lines
 * come back through the pipe. The child dies with the process (Android kills the app's whole process group).
 */
object DebugLogFile {
    private const val TAG = "DebugLogFile"
    const val DIR = "Logs"
    /** Sessions kept in [DIR]; older ones go when a new one starts. */
    internal const val KEEP = 20
    /** A file this big moves to `.1` (then `.2`), so a long session keeps its newest lines. */
    private const val ROTATE_BYTES = 4L shl 20
    private const val ROTATIONS = 2
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val ROTATED = Regex("""\.log\.\d+$""")

    private val lock = Any()
    private var logcat: Process? = null
    private var out: RotatingFile? = null
    private var crashHook = false
    /** The game process's console and core ([nameGame]), for its file's name; it may come before the file opens. */
    private var gameLabel: String? = null

    /** Starts this process's file, off the calling thread (the home lookup and the fork touch storage). Idempotent. */
    fun start(context: Context) {
        val app = context.applicationContext
        Thread({ startNow(app) }, "debug-log-file").start()
    }

    /**
     * Names the game process's file after the game's console [tag] and [coreId] (`...-game-PS2-armsx2.log`), so a
     * report's file is easy to pick out. The file opens at process start, before the game is known: an open one is
     * renamed, one still to open takes the name.
     */
    fun nameGame(tag: String, coreId: String) {
        synchronized(lock) {
            val label = label(tag, coreId)
            gameLabel = label
            val s = out?.takeIf { "-game" in it.file.name } ?: return
            val renamed = File(s.file.parentFile, labelled(s.file.name, label))
            if (renamed != s.file && !s.moveTo(renamed)) NxLog.w(TAG, "could not rename ${s.file.name} to ${renamed.name}")
        }
    }

    /** Stops this process's file; the lines written so far stay. */
    fun stop() {
        synchronized(lock) {
            logcat?.destroy()
            logcat = null
            out?.close()
            out = null
        }
    }

    private fun startNow(app: Context) {
        synchronized(lock) {
            // Turned off again before this thread ran: the setter's stop() came first.
            if (!NxLog.enabled || logcat?.isAlive == true) return
            val home = AndroidHomeLocator(app).current()?.takeIf(FileRefs::isFile)?.let { File(FileRefs.path(it)) }
            if (home == null) {
                NxLog.w(TAG, "no home folder to write the log in")
                return
            }
            val dir = File(home, DIR)
            if (!dir.isDirectory && !dir.mkdirs()) {
                NxLog.w(TAG, "could not make ${dir.path}")
                return
            }
            dir.list()?.let { names -> stale(names.toList(), KEEP - 1).forEach { File(dir, it).delete() } }
            val sink = try {
                val game = AppProcess.isGame()
                RotatingFile(File(dir, name(LocalDateTime.now(), game, gameLabel.takeIf { game })), ROTATE_BYTES, ROTATIONS)
            } catch (e: IOException) {
                NxLog.w(TAG, "could not open the log file in ${dir.path}", e)
                return
            }
            val child = try {
                ProcessBuilder("logcat", "-v", "threadtime", "--pid=${android.os.Process.myPid()}")
                    .redirectErrorStream(true).start()
            } catch (e: IOException) {
                sink.close()
                NxLog.w(TAG, "logcat did not start", e)
                return
            }
            logcat = child
            out = sink
            installCrashHook()
            Thread({ pump(child, sink) }, "debug-log-pump").apply { isDaemon = true }.start()
        }
        // The first lines a report needs; logcat picks them up from the buffer.
        NxLog.i(
            TAG,
            "NX Redux ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT}, ${app.packageName}) on " +
                "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}, ${soc()}), Android " +
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ABIs ${Build.SUPPORTED_ABIS.joinToString()}",
        )
    }

    /** Copies the child's lines into [sink] until the child ends or [stop] swaps the file out. */
    private fun pump(child: Process, sink: RotatingFile) {
        try {
            child.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    synchronized(lock) {
                        if (out !== sink) return
                        sink.write(line)
                        if (!reader.ready()) sink.flush()
                    }
                }
            }
        } catch (_: IOException) {
            // The child was stopped, or the file went away (the card removed): the lines so far stay.
        } finally {
            synchronized(lock) {
                if (out === sink) {
                    sink.close()
                    out = null
                }
            }
        }
    }

    /** Once per process: a Java crash's stack goes into the file before Android's own handler ends the process. */
    private fun installCrashHook() {
        if (crashHook) return
        crashHook = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                synchronized(lock) {
                    out?.let { s ->
                        s.write("FATAL EXCEPTION in thread \"${thread.name}\"")
                        e.stackTraceToString().lineSequence().forEach(s::write)
                        s.flush()
                    }
                }
            } catch (_: Throwable) {
                // Never stand between the crash and Android's handler.
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The chip, which the GPU follows: Android 12 names it, older ones only the board. */
    private fun soc(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE

    /** The file a session started at [now] writes: sorts by time, says which process and, once known, the game's [label]. */
    internal fun name(now: LocalDateTime, game: Boolean, label: String? = null): String =
        "${STAMP.format(now)}-${if (game) "game" else "app"}${label?.let { "-$it" }.orEmpty()}.log"

    /** [tag] and [coreId] as one file-name-safe piece: `PS2-armsx2`. */
    internal fun label(tag: String, coreId: String): String =
        "$tag-$coreId".replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** A game file's [name] with [label] in place of any earlier one: the start stamp stays, so the order does too. */
    internal fun labelled(name: String, label: String): String = name.substringBefore("-game") + "-game-$label.log"

    /**
     * The files in [names] past the newest [keep] sessions: a session is a `.log` and its rotations (`.log.1`, ...),
     * sessions sort by name (their start time). Anything else in the folder is left alone.
     */
    internal fun stale(names: List<String>, keep: Int): List<String> {
        val ours = names.filter { it.endsWith(".log") || ROTATED.containsMatchIn(it) }
        val sessions = ours.groupBy { it.replace(ROTATED, ".log") }
        return sessions.keys.sortedDescending().drop(keep.coerceAtLeast(0)).flatMap { sessions.getValue(it) }
    }
}

/**
 * Lines appended to [file]; once it holds [maxBytes] it moves to `<file>.1` (an older `.1` to `.2`, up to [rotations])
 * and a fresh one starts. Not thread-safe: [DebugLogFile] calls it under its lock.
 */
internal class RotatingFile(file: File, private val maxBytes: Long, private val rotations: Int) {
    var file: File = file
        private set
    private var writer: Writer = open()
    private var bytes = file.length()

    private fun open(): Writer = FileOutputStream(file, true).bufferedWriter()

    fun write(line: String) {
        if (bytes >= maxBytes) rotate()
        writer.write(line)
        writer.write("\n")
        bytes += line.toByteArray().size + 1
    }

    fun flush() = writer.flush()

    fun close() {
        try {
            writer.close()
        } catch (_: IOException) {
        }
    }

    /** Carries on in [to]: the file and its rotations are renamed, the lines go on there. False (still in [file]) on failure. */
    fun moveTo(to: File): Boolean {
        close()
        val moved = file.renameTo(to)
        if (moved) {
            for (i in 1..rotations) File("${file.path}.$i").takeIf { it.exists() }?.renameTo(File("${to.path}.$i"))
            file = to
        }
        writer = open()
        return moved
    }

    private fun rotate() {
        close()
        for (i in rotations downTo 1) {
            val from = if (i == 1) file else File("${file.path}.${i - 1}")
            val to = File("${file.path}.$i")
            if (from.exists()) {
                to.delete()
                from.renameTo(to)
            }
        }
        writer = open()
        bytes = 0
    }
}
