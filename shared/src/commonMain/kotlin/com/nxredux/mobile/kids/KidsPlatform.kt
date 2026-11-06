package com.nxredux.mobile.kids

/** What Kids Mode needs from the platform: the clock, the flag file, the two permissions, alarms and the lock itself. */
interface KidsPlatform {
    fun now(): LocalNow
    val flag: KidsFlag

    /** Usage access (`PACKAGE_USAGE_STATS`): the guard watches the foreground app with it. */
    fun hasUsageAccess(): Boolean
    fun openUsageAccess()

    /** Alarms & reminders (`SCHEDULE_EXACT_ALARM`): the warning and the lock fire on the dot. */
    fun canExactAlarms(): Boolean
    fun openExactAlarms()

    /** The boundary alarms (warning, lock) for [mode], replacing any set before. */
    fun reschedule(mode: KidsMode)

    /** Quit the game without its auto state (its battery save is written), then bring the menu forward. */
    fun lockNow()

    /**
     * The receiver for boots and clock changes on while Kids Mode is on, off while it is off, so a boot or a clock
     * change never starts the app for nothing. Nothing on a platform without one.
     */
    fun setStartReceiver(enabled: Boolean) {}
}
