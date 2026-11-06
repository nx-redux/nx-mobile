package com.nxredux.mobile.kids

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Kids Mode's alarms and the game's input reports, in the main process.
 * - WARN, LOCK: the clock is read again ([KidsController.refresh]). The lock locks through the change to Locked, or
 *   itself when that did nothing and no menu is shown ([lockOnAlarm]); repeating it is safe. The warning is given once
 *   per end and not before its time ([warnDue]); while the screen is off it is held for the screen-on path
 *   ([AndroidKids]). The alarms are set again from the mode after either.
 * - PARENT_IDLE: Parent mode's 5 minutes without input ran out away from the menu ([KidsController.tick]).
 * - GAME_INPUT: a game's input, so Parent mode's idle time starts again.
 */
class KidsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val locksBefore = AndroidKids.locks   // before get: a controller born here may lock at birth ([lockAtBirth])
        val kids = AndroidKids.get(context)
        when (action) {
            KidsAlarms.ACTION_PARENT_IDLE -> { kids.tick(kids.now().epochMs); AndroidKids.armParentIdle(context); return }
            KidsAlarms.ACTION_GAME_INPUT -> { kids.parentInput(); AndroidKids.armParentIdle(context); return }
            KidsAlarms.ACTION_WARN, KidsAlarms.ACTION_LOCK -> Unit
            else -> return
        }
        kids.refresh()
        val mode = kids.mode.value
        val now = kids.now().epochMs
        var held = false
        when (action) {
            KidsAlarms.ACTION_WARN -> if (warnDue(mode, now, KidsAlarms.warnedFor(context))) {
                if (context.getSystemService(PowerManager::class.java)?.isInteractive == false) held = true
                else {
                    KidsAlarms.setWarnedFor(context, (mode as KidsMode.Open).untilMs)
                    AndroidKids.warn(context)
                }
            }
            KidsAlarms.ACTION_LOCK ->
                if (lockOnAlarm(mode, AndroidKids.menuIsShown, lockedByRefresh = AndroidKids.locks != locksBefore)) AndroidKids.lockNow(context)
        }
        if (held) KidsAlarms.set(context, KidsAlarms.planHeld(mode, now))
        else KidsAlarms.reschedule(context, mode, now)
    }
}
