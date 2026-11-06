package com.nxredux.mobile.kids

import com.nxredux.mobile.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a PIN prompt is for: the tiles screen's lock, or setup's and Change PIN's new PIN and its confirmation. */
enum class PinPurpose { UNLOCK, SETUP_NEW, SETUP_CONFIRM, CHANGE }

enum class PinResult { OK, WRONG, WAIT }

/**
 * Kids Mode's state ([mode]) from the settings, the clock and the flag file ([KidsPlatform]). Parent mode lives in memory
 * only: a process death goes back to the schedule. The platform calls [refresh] on resume, screen on, a clock change and
 * each boundary alarm; the menu calls [tick] and [parentInput].
 */
class KidsController(private val settings: AppSettings, private val platform: KidsPlatform) {
    private var parent = false
    private var parentInputAt = 0L
    private var wrongTries = 0
    private var rescheduled = false

    /** Set while a setup step has a system permission screen open, so the guard leaves it alone. */
    var granting: Boolean = false

    /** After the fifth wrong PIN, no try counts until then (epoch ms; 0: no wait). */
    var waitUntil: Long = 0L
        private set

    private val _mode = MutableStateFlow(compute())
    val mode: StateFlow<KidsMode> = _mode.asStateFlow()

    /** The flag file's rule ([flagAction]), then the mode from the clock as it is now. */
    fun refresh() {
        if (settings.kidsEnabled) {
            val flag = platform.flag
            val home = flag.homeKey()
            val seen = if (home != null) flag.seen() else FlagSeen.UNREADABLE
            when (val a = flagAction(settings.kidsEnabled, settings.kidsFlagHome, home, seen)) {
                FlagAction.Keep -> Unit
                FlagAction.TurnOff -> { settings.kidsEnabled = false; parent = false }
                is FlagAction.Rewrite -> if (flag.write()) settings.kidsFlagHome = a.home
            }
        }
        publish()
    }

    /** The parent's timed unlock from now ([unlockUntil]); the kid plays, so Parent mode ends. */
    fun unlock(chip: UnlockChip) {
        settings.kidsUnlockUntil = unlockUntil(platform.now(), chip)
        parent = false
        publish()
    }

    /** "Open full menu" after the right PIN: the whole menu until [lock] or [PARENT_IDLE_MS] without input. */
    fun enterParent(nowMs: Long) {
        if (!settings.kidsEnabled) return
        parent = true
        parentInputAt = nowMs
        publish()
    }

    /** A menu input (a press or a touch) while in Parent mode: its idle time starts again, on the platform's clock as [tick]'s. */
    fun parentInput() {
        if (parent) parentInputAt = platform.now().epochMs
    }

    /** Usage access, as the guard needs it; Settings shows "Guard off" while it is missing. */
    fun hasUsageAccess(): Boolean = platform.hasUsageAccess()

    /** Android's Usage access page; [granting] until the app is back ([returned]). */
    fun openUsageAccess() { granting = true; platform.openUsageAccess() }

    /** Alarms & reminders (true where Android needs no grant, before 14); Settings says "Exact timing off" while it is missing. */
    fun canExactAlarms(): Boolean = platform.canExactAlarms()

    /** Android's Alarms & reminders page; [granting] until the app is back ([returned]). */
    fun openExactAlarms() { granting = true; platform.openExactAlarms() }

    /** The app is back in front: a permission screen a setup step opened has closed. */
    fun returned() { granting = false }

    /** The flag file's path for Forgot PIN? ([KidsFlag.flagPath]). */
    fun flagPath(): String? = platform.flag.flagPath()

    /** The platform's clock now: the tiles screen's clock and the PIN wait read it, as the mode does. */
    fun now(): LocalNow = platform.now()

    /** Parent mode ends after [PARENT_IDLE_MS] without input; the schedule's mode is computed again either way. */
    fun tick(nowMs: Long) {
        if (parent && nowMs - parentInputAt >= PARENT_IDLE_MS) parent = false
        publish()
    }

    /**
     * Parent mode ends (the screen went off), a timed unlock and the play window still count; a change to Locked locks
     * as any other does ([KidsPlatform.lockNow]). Nothing outside Parent mode.
     */
    fun endParent() {
        if (!parent) return
        parent = false
        publish()
    }

    /** Lock now: Parent mode ends and a timed unlock is dropped (a play window still counts). */
    fun lock() {
        parent = false
        settings.kidsUnlockUntil = 0L
        publish()
    }

    /** Kids Mode on, with its flag file; the parent who turned it on keeps the full menu (Parent) until they lock. */
    fun turnOn() {
        settings.kidsEnabled = true
        val flag = platform.flag
        flag.homeKey()?.let { home -> if (flag.write()) settings.kidsFlagHome = home }
        parent = true
        parentInputAt = platform.now().epochMs
        publish()
    }

    /** Kids Mode off: the flag file goes, the settings (schedule, apps, PIN) stay. */
    fun turnOff() {
        settings.kidsEnabled = false
        settings.kidsUnlockUntil = 0L
        settings.kidsFlagHome = null
        platform.flag.delete()
        parent = false
        publish()
    }

    /** [pin] against the stored hash. The fifth wrong try in a row starts a [PIN_WAIT_MS] wait in which no try counts. */
    fun tryPin(pin: String, nowMs: Long): PinResult {
        if (nowMs < waitUntil) return PinResult.WAIT
        if (settings.kidsPinMatches(pin)) { wrongTries = 0; return PinResult.OK }
        wrongTries++
        if (wrongTries >= PIN_TRIES) {
            wrongTries = 0
            waitUntil = nowMs + PIN_WAIT_MS
            return PinResult.WAIT
        }
        return PinResult.WRONG
    }

    private fun compute(): KidsMode = kidsMode(
        settings.kidsEnabled, parent, platform.now(), KidsSchedule.decode(settings.kidsSchedule), settings.kidsUnlockUntil,
    )

    /**
     * The mode now: a change (and the first publish) sets the alarms again; Open or Parent becoming Locked quits the game
     * and brings the tiles screen forward ([KidsPlatform.lockNow]), once per such change.
     */
    private fun publish() {
        val was = _mode.value
        val now = compute()
        if (now == was && rescheduled) return
        _mode.value = now
        rescheduled = true
        platform.reschedule(now)
        if (now is KidsMode.Locked && (was is KidsMode.Open || was is KidsMode.Parent)) platform.lockNow()
    }

    companion object {
        const val PARENT_IDLE_MS = 5 * 60_000L
        const val PIN_TRIES = 5
        const val PIN_WAIT_MS = 30_000L
    }
}
