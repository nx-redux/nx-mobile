package com.nxredux.mobile.kids

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A boot (alarms are gone), a clock or time zone change, an app update (alarms are gone with the old process) or the
 * exact-alarm grant: the mode is read from the clock again and its alarms set again, so nothing stays unlocked.
 */
class KidsClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val kids = AndroidKids.get(context)
        kids.refresh()
        KidsAlarms.reschedule(context, kids.mode.value, kids.now().epochMs)
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
