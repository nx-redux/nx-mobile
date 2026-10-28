package com.nxredux.mobile.runner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Android EmulatorRunner: hands the session to GameActivity. The only class outside GameActivity that names it.
 * [ownTask] is read per launch: true while the menu runs as the home screen (launcher mode), see [gameLaunchFlags].
 */
class LibretroRunner(private val context: Context, private val ownTask: () -> Boolean) : EmulatorRunner {
    /** A game screen is alive (playing, suspended in its own task, or closing); main thread only. Import saves waits for it. */
    val gameScreenAlive: Boolean get() = GameActivity.gameScreenAlive

    /** Runs first in every [launch], on the main thread: the menu clears the second display for the game. */
    var beforeLaunch: () -> Unit = {}

    /** The launch threw after [beforeLaunch] (the activity could not start); the error is rethrown after it. */
    var launchFailed: () -> Unit = {}

    override fun launch(session: GameSession, onStarted: () -> Unit) {
        beforeLaunch()
        val intent = Intent(context, GameActivity::class.java)
            .putExtra(GameActivity.EXTRA_LAUNCH_ID, PendingLaunches.put(onStarted))
            .putExtra(GameActivity.EXTRA_CORE_FILE, session.core.libraryFileName)
            .putExtra(GameActivity.EXTRA_CORE_ID, session.core.id)
            .putExtra(GameActivity.EXTRA_TAG, session.systemTag)
            .putExtra(GameActivity.EXTRA_TITLE, session.displayName)
            .putExtra(GameActivity.EXTRA_FILE_NAME, session.fileName)
            .putExtra(GameActivity.EXTRA_CONTENT_URI, session.content.value)
            .putExtra(GameActivity.EXTRA_FOLDER_URI, session.folder.value)
            .putExtra(GameActivity.EXTRA_LOAD_MODE, session.core.loadMode.name)
            .putExtra(GameActivity.EXTRA_OPTIONS, session.core.defaultOptions.map { "${it.key}=${it.value}" }.toTypedArray())
            .putExtra(GameActivity.EXTRA_COMPANION_NAMES, session.companions.map { it.fileName }.toTypedArray())
            .putExtra(GameActivity.EXTRA_COMPANION_URIS, session.companions.map { it.ref.value }.toTypedArray())
            .putExtra(GameActivity.EXTRA_SYSTEM_SETS, session.systemSets.toTypedArray())
            .apply { session.resumeSlot?.let { putExtra(GameActivity.EXTRA_RESUME_SLOT, it) } }
            .apply { session.gamePath?.let { putExtra(GameActivity.EXTRA_GAME_PATH, it) } }
            .apply { session.artPath?.let { putExtra(GameActivity.EXTRA_ART_PATH, it) } }
            .apply { session.launchName?.let { putExtras(launchNameExtras(it)) } }   // the hold's name (LaunchNameHold)
            .apply {
                session.netplay?.let {
                    putExtra(GameActivity.EXTRA_NETPLAY_ROLE, it.role.name)
                    putExtra(GameActivity.EXTRA_NETPLAY_PEER, it.peerIp)
                    putExtra(GameActivity.EXTRA_NETPLAY_FOLDER_CORE, it.folderCoreId)
                    putExtra(GameActivity.EXTRA_NETPLAY_EMPTY_SAVES, it.emptySaves)
                    putExtra(GameActivity.EXTRA_NETPLAY_USB, it.overUsb)
                }
            }
            .addFlags(gameLaunchFlags(ownTask()))
        try {
            context.startActivity(intent)
            // The launch sequence's black meets the game screen's black: no open animation. Android 14+ sets it in
            // GameActivity.onCreate (overrideActivityTransition).
            if (session.launchName != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION") (context as? Activity)?.overridePendingTransition(0, 0)
            }
        } catch (e: Throwable) {
            launchFailed()
            throw e
        }
    }
}

/**
 * The game's own task only in launcher mode. There Home brings the home task forward and leaves the game
 * suspended in its own task; a new launch replaces a suspended game (its auto-state was written on stop). Otherwise
 * the game shares the menu's task (no NEW_TASK, so the manifest taskAffinity is ignored), the only way GameActivity's
 * cross-fade can run: Android ignores an app's custom animation (ActivityOptions or overrideActivityTransition) for a
 * task change.
 */
internal fun gameLaunchFlags(ownTask: Boolean): Int =
    if (ownTask) Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK else 0

/**
 * The onStarted callbacks of launches GameActivity has not settled yet, keyed by the id in its intent. Process-wide and
 * in memory only: a process that dies between launch and start loses the callback (the game then goes unrecorded).
 * GameActivity [take]s its entry exactly once: on the first frame to run it, or on a refusal / finish to drop it.
 */
internal object PendingLaunches {
    const val NONE = -1L   // an intent without an id (a restore from recents in a fresh process)
    private val callbacks = HashMap<Long, () -> Unit>()
    // Seeded per process: an id restarting at 0 would let a restored GameActivity from an earlier process drop a new launch's entry.
    private var nextId = kotlin.random.Random.nextLong(0, Long.MAX_VALUE / 2)

    @Synchronized fun put(onStarted: () -> Unit): Long = nextId++.also { callbacks[it] = onStarted }

    /** Removes and returns [id]'s callback; null when it was already taken or never registered in this process. */
    @Synchronized fun take(id: Long): (() -> Unit)? = callbacks.remove(id)
}
