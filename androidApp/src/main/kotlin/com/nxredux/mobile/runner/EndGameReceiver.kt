package com.nxredux.mobile.runner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nxredux.mobile.AppProcess
import com.swordfish.libretrodroid.NxLog

/**
 * In the game process: the menu starts another game ([GameProcessControl]), so this one quits with its auto state and
 * its process ends ([GameActivity.endForHandoff]). Not exported; the menu sends it only to this package. A process
 * already ending ([GameActivity.ending]) is left to finish its writes; one with no game screen (started just for this
 * broadcast, the game having died meanwhile) ends at once.
 */
class EndGameReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != endGameAction(context)) return
        GameHeartbeat.start(context)   // the menu waits on this process's end while the beat moves
        if (GameActivity.ending) return   // the last screen's teardown is ending the process: the menu sees it go
        if (GameActivity.endForHandoff()) return
        if (!AppProcess.isGame(context)) return
        NxLog.i("NXAPP", "no game screen in the game process: ending it")
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
