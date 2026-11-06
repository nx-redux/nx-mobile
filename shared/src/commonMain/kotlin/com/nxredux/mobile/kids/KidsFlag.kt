package com.nxredux.mobile.kids

/** What a look at the home root found for `kids-mode.flag`. */
enum class FlagSeen { PRESENT, MISSING, UNREADABLE }

/** The `kids-mode.flag` file in the NXRedux home root: deleting it turns Kids Mode off from outside the app. */
interface KidsFlag {
    /** A stable string for the current home, null when there is none. */
    fun homeKey(): String?
    fun seen(): FlagSeen
    fun write(): Boolean
    fun delete()
    /** Where the flag is (or would be), as Settings → Kids Mode → Forgot PIN? shows it; null when the home has no plain path. */
    fun flagPath(): String? = null
}

sealed interface FlagAction {
    data object Keep : FlagAction
    data object TurnOff : FlagAction
    data class Rewrite(val home: String) : FlagAction
}

/**
 * What to do about the flag. A different home gets a fresh flag (the old one was left behind); in the same home a missing
 * flag means a parent or the user removed it, so Kids Mode turns off. An unreadable home proves nothing and changes nothing.
 */
fun flagAction(enabled: Boolean, recordedHome: String?, currentHome: String?, seen: FlagSeen): FlagAction = when {
    !enabled || currentHome == null -> FlagAction.Keep
    recordedHome != currentHome -> FlagAction.Rewrite(currentHome)
    seen == FlagSeen.MISSING -> FlagAction.TurnOff
    else -> FlagAction.Keep
}
