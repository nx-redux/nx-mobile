package com.nxredux.mobile.test

import android.content.Context
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.runner.writeAtomically
import com.swordfish.libretrodroid.NxLog
import org.json.JSONObject
import java.io.File

/**
 * Debug builds only: the adb test hooks' results, one JSON file per hook under filesDir/nxtest/ (read with
 * `adb shell run-as com.nxredux.mobile.dev cat files/nxtest/<name>.json`). The hooks' receiver lives in the debug source set;
 * this writer is in main because GameActivity reports through it. A release build writes nothing.
 */
object NxTestResults {
    private const val DIR = "nxtest"

    fun dir(context: Context): File = File(context.filesDir, DIR)

    /** Writes `<name>.json` atomically with [fields] plus "ts" (wall-clock millis); no-op in release, never throws. */
    fun write(context: Context, name: String, vararg fields: Pair<String, Any?>) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val json = JSONObject()
            fields.forEach { (k, v) -> json.put(k, v ?: JSONObject.NULL) }
            json.put("ts", System.currentTimeMillis())
            File(dir(context), "$name.json").writeAtomically(json.toString().toByteArray())
        }.onFailure { NxLog.w("NxTest", "could not write $name.json", it) }
    }

    /**
     * A game the GAME hook starts shows over the lock screen and turns the screen on (the test devices keep a
     * PIN lock, which the hooks never bypass: only this activity shows, the device stays locked). The hook sets the flag;
     * GameActivity takes it once. No-op in release.
     */
    fun markShowWhenLocked(context: Context) {
        if (!BuildConfig.DEBUG) return
        runCatching { File(dir(context), SHOW_WHEN_LOCKED).apply { parentFile?.mkdirs() }.writeText("1") }
    }

    /** True once after [markShowWhenLocked] (the flag is removed); always false in release. */
    fun takeShowWhenLocked(context: Context): Boolean =
        BuildConfig.DEBUG && File(dir(context), SHOW_WHEN_LOCKED).let { it.isFile && it.delete() }

    private const val SHOW_WHEN_LOCKED = "show-when-locked.flag"

    /** Deletes every result file; no-op in release. */
    fun reset(context: Context) {
        if (!BuildConfig.DEBUG) return
        dir(context).listFiles()?.filter { it.name.endsWith(".json") }?.forEach { it.delete() }
    }
}
