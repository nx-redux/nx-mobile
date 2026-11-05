package com.nxredux.mobile.options

/**
 * fceumm's X / Y turbo (the skins' TA / TB caps) is on by default since its descriptor default became [ON]. A full
 * options save writes every running value, so a cfg saved before then holds the core's old default [OLD_DEFAULT] as if
 * picked, and would keep turbo off. Such a cfg (last written before the app first ran this version: [needsMigration]'s
 * sinceMs) gets [ON] once; the rewrite makes it newer, so a None saved later is the player's own and stays.
 */
object FceummTurbo {
    const val KEY = "fceumm_turbo_enable"
    const val ON = "Player 1"
    const val OLD_DEFAULT = "None"

    /** Whether [file], last written at [modifiedMs], holds the old default from before [sinceMs] (not locked). */
    fun needsMigration(file: ConfigFile?, modifiedMs: Long, sinceMs: Long): Boolean =
        file != null && modifiedMs < sinceMs && KEY !in file.locked && file.values[KEY] == OLD_DEFAULT
}
