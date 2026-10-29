package com.nxredux.mobile.gametime

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.nxredux.mobile.gametime.db.GameTimeDb
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.DataRoot
import com.nxredux.mobile.storage.NxPaths
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

/**
 * Process singleton: the game-time database (`.userdata/shared/game_time.db` under [DataRoot.of]), its recorder for
 * GameActivity and its data for the Game Time screen. A database that can't be opened leaves recording off and the
 * screen showing an error; play is never affected. A failed open is retried on a later call (at most every
 * [RETRY_MS]), so a full disk or a locked file does not turn game time off until the process dies. A data root that
 * changed since the open (the home folder chosen, All files access off) closes it, and the next open is at the new
 * root. Both accessors may block (they open the database): call them off the main thread.
 */
object GameTimeService {
    private const val TAG = "GameTime"
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private const val RETRY_MS = 10_000L
    @Volatile private var opener: RetryingOpen<GameTimeStore>? = null
    @Volatile private var recorder: GameTimeRecorder? = null
    /** The open store's driver, for [closeForMove]. */
    @Volatile private var driver: AndroidSqliteDriver? = null
    /** The database file [opener] opens: a different one ([DataRoot.of] moved) starts over. */
    @Volatile private var openedAt: File? = null

    private fun store(context: Context): GameTimeStore? {
        val app = context.applicationContext
        val file = followRoot(app)
        val o = opener ?: synchronized(this) {
            opener ?: RetryingOpen(RETRY_MS, SystemClock::elapsedRealtime, { NxLog.e(TAG, "game time database unavailable", it) }) { open(app, file) }
                .also { opener = it; openedAt = file }
        }
        return o.get()
    }

    /** The database file at the data root now; a store or recorder opened at another one is closed first. */
    private fun followRoot(app: Context): File {
        val file = File(DataRoot.of(app), NxPaths.GAME_TIME_DB)
        if (openedAt.let { it != null && it != file }) synchronized(this) { if (openedAt != file) closeForMove() }
        return file
    }

    private fun open(app: Context, file: File): GameTimeStore {
        file.parentFile?.mkdirs()
        val driver = AndroidSqliteDriver(GameTimeDb.Schema, app, file.absolutePath)
        return try {
            val name = Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
            GameTimeStore(GameTimeDb(driver), AndroidAppSettings(app).gameTimeDeviceId, name, System::currentTimeMillis) {
                UUID.randomUUID().toString()
            }.also { it.prepare(); this.driver = driver }
        } catch (e: Throwable) {
            runCatching { driver.close() }   // the next try opens its own
            throw e
        }
    }

    /** Kept only once it has the database: a recorder made without one records nothing, so the next game tries again. */
    fun recorder(context: Context): GameTimeRecorder =
        recorder?.takeIf { followRoot(context.applicationContext) == openedAt } ?: synchronized(this) {
            recorder ?: store(context).let { s ->
                GameTimeRecorder(s, serial) { NxLog.e(TAG, "game time write failed", it) }.also { if (s != null) recorder = it }
            }
        }

    /**
     * Blocks until the writes queued so far (a game's last [GameTimeRecorder.end]) have run, at most [timeoutMs]: the game
     * process ends right after. Off the main thread.
     */
    fun flush(timeoutMs: Long = 2_000L) {
        runBlocking { withTimeoutOrNull(timeoutMs) { serial.launch {}.join() } }
    }

    fun data(context: Context): GameTimeData? = store(context)?.let { StoreGameTimeData(it, Dispatchers.IO) }

    /**
     * Gives every game [rename] answers for its new path ([GameTimeStore.renamePaths]), at the data root now (the storage
     * upgrade runs it on the private one, before the move). False when the database can't be opened. Blocks.
     */
    fun renamePaths(context: Context, rename: (String) -> String?): Boolean {
        val s = store(context) ?: return false
        s.renamePaths(s.romPaths().mapNotNull { from -> rename(from)?.let { from to it } }.toMap())
        return true
    }

    /**
     * Closes the database before [com.nxredux.mobile.storage.DataRoot] moves it (a live WAL database is never copied half
     * way) and forgets the store and recorder, so the next call opens it again at the root it is under then.
     */
    fun closeForMove() = synchronized(this) {
        opener = null
        openedAt = null
        recorder = null
        driver?.let { d -> runCatching { d.close() }.onFailure { NxLog.e(TAG, "game time database close failed", it) } }
        driver = null
    }
}

/**
 * [open]'s value once it succeeds, kept for good; a failure is not kept: [get] answers null and tries again on a call
 * at least [retryMs] after it ([now] is a monotonic clock). Thread-safe; one open at a time.
 */
internal class RetryingOpen<T : Any>(
    private val retryMs: Long,
    private val now: () -> Long,
    private val onFailure: (Throwable) -> Unit,
    private val open: () -> T,
) {
    @Volatile private var value: T? = null
    private var failedAt: Long? = null

    fun get(): T? = value ?: synchronized(this) {
        value ?: run {
            val t = now()
            if (failedAt?.let { t - it < retryMs } == true) return@run null
            runCatching(open).onSuccess { value = it; failedAt = null }.onFailure { failedAt = t; onFailure(it) }.getOrNull()
        }
    }
}
