package com.nxredux.mobile.runner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nxredux.mobile.storage.Recent

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

    /** False: launches leave no launch record, so they never reach Recently Played (the debug test hook). */
    var recordLaunches = true

    override fun launch(session: GameSession, recent: Recent) {
        beforeLaunch()
        val intent = Intent(context, GameActivity::class.java)
            .putExtra(GameActivity.EXTRA_LAUNCH_ID, if (recordLaunches) LaunchRecords.of(context).pending(recent) else LaunchRecords.NONE)
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
