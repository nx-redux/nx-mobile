package com.nxredux.mobile.kids

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Kids Mode's boundary alarms ([KidsAlarms]), in the main process. Both read the clock again ([KidsController.refresh]):
 * at the lock the change to Locked quits the game and brings the tiles forward ([lockAtBirth] for a controller made now). The warning is given once per end, and
 * not before its time (a clock moved back); the alarms are set again from the mode after either, so an alarm that came
 * a moment early is set once more.
 */
class KidsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != KidsAlarms.ACTION_WARN && action != KidsAlarms.ACTION_LOCK) return
        val kids = AndroidKids.get(context)   // made now (a process started for this alarm): it locks a live game itself
        kids.refresh()
        val mode = kids.mode.value
        when (action) {
            KidsAlarms.ACTION_WARN -> if (mode is KidsMode.Open && KidsAlarms.warnedFor(context) != mode.untilMs &&
                kids.now().epochMs >= warnAtMs(mode.untilMs) - EARLY_MS
            ) {
                KidsAlarms.setWarnedFor(context, mode.untilMs)
                AndroidKids.warn(context)
            }
            KidsAlarms.ACTION_LOCK -> Unit   // refresh's change to Locked (or the controller's birth) did the lock
        }
        KidsAlarms.reschedule(context, mode, kids.now().epochMs)
    }

    private companion object {
        /** An exact alarm comes on time, an inexact one late; this much early still counts as the warning's time. */
        const val EARLY_MS = 60_000L
    }
}
