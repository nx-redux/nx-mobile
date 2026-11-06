package com.nxredux.mobile.kids

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Kids Mode's boundary alarms ([KidsAlarms]), in the main process. Both read the clock again ([KidsController.refresh]):
 * at the lock the change to Locked quits the game and brings the tiles forward. The warning is given once per end, and
 * not before its time (a clock moved back); the alarms are set again from the mode after either, so an alarm that came
 * a moment early is set once more.
 */
class KidsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != KidsAlarms.ACTION_WARN && action != KidsAlarms.ACTION_LOCK) return
        val fresh = AndroidKids.peek() == null   // a process started for this alarm: the controller is born Locked
        val kids = AndroidKids.get(context)
        kids.refresh()
        val mode = kids.mode.value
        when (action) {
            KidsAlarms.ACTION_WARN -> if (mode is KidsMode.Open && KidsAlarms.warnedFor(context) != mode.untilMs &&
                kids.now().epochMs >= warnAtMs(mode.untilMs) - EARLY_MS
            ) {
                KidsAlarms.setWarnedFor(context, mode.untilMs)
                AndroidKids.warn(context)
            }
            KidsAlarms.ACTION_LOCK -> if (fresh && mode is KidsMode.Locked) AndroidKids.lockNow(context)
        }
        KidsAlarms.reschedule(context, mode)
    }

    private companion object {
        /** An exact alarm comes on time, an inexact one late; this much early still counts as the warning's time. */
        const val EARLY_MS = 60_000L
    }
}
