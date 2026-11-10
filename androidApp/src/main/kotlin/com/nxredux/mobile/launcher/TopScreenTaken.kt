package com.nxredux.mobile.launcher

import android.content.Context
import java.io.File

/**
 * A game left the top screen to an app opened there, then took it anyway (in-game menu → Use top screen). The game runs in
 * its own process, so it leaves a file; back in front, the menu takes the top screen back ([TopScreenHost.reclaim]).
 */
object TopScreenTaken {
    private fun file(context: Context) = File(context.filesDir, "top_screen.taken")

    fun mark(context: Context) { runCatching { file(context).createNewFile() } }

    /** True once after [mark]. */
    fun consume(context: Context): Boolean = runCatching { file(context).delete() }.getOrDefault(false)
}
