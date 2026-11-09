package com.nxredux.mobile.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.swordfish.libretrodroid.NxLog
import java.lang.ref.WeakReference

/**
 * Kids Mode's cover over an app sent back from the top screen ([TopScreenHost.takeBack]). The menu's window there hides
 * the app but leaves it resumed, so Recents could move it to the other display without a new resume for the guard to
 * see; this opaque activity in front of it on that display stops it. It stays until the top screen hosts an app again
 * or is let go of ([uncover]).
 */
class TopScreenCover : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        covers += WeakReference(this)
    }

    override fun onDestroy() {
        covers.removeAll { it.get() == null || it.get() === this }
        super.onDestroy()
    }

    companion object {
        /** Main thread only. */
        private val covers = mutableListOf<WeakReference<TopScreenCover>>()

        fun cover(context: Context, display: Int) {
            val intent = Intent(context, TopScreenCover::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            runCatching { context.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()) }
                .onFailure { NxLog.w("NXKIDS", "top screen cover on display $display: ${it::class.java.simpleName}") }
        }

        fun uncover() {
            covers.mapNotNull { it.get() }.forEach { it.finishAndRemoveTask() }
            covers.clear()
        }
    }
}
