package com.nxredux.mobile.storage

import com.nxredux.mobile.settings.stepFrom

/**
 * NX Redux Settings → System → Save format (nx-redux settings.c `save_format_*`): in its cycle order, with its label and
 * the value it stores in `minuisettings.txt` (`saveFormat=`), so a later onboarding or Device Sync copies it unchanged.
 */
enum class SaveFormat(val stored: Int, val label: String, val compressed: Boolean) {
    MINUI(0, "MinUI", false),
    RETROARCH_COMPRESSED(1, "Retroarch (compressed)", true),
    RETROARCH(3, "Retroarch (uncompressed)", false),
    GENERIC(2, "Generic", false);

    /** The format [by] places on, wrapping (LEFT -1, RIGHT and A +1). */
    fun step(by: Int): SaveFormat = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = RETROARCH
        fun fromStored(value: Int?): SaveFormat = entries.firstOrNull { it.stored == value } ?: DEFAULT
    }
}

/** NX Redux Settings → System → Save state format (`state_format_*`, `stateFormat=`), as [SaveFormat]. */
enum class StateFormat(val stored: Int, val label: String, val compressed: Boolean) {
    MINUI(0, "MinUI", false),
    RETROARCH_ISH_COMPRESSED(1, "Retroarch-ish (compressed)", true),
    RETROARCH_ISH(2, "Retroarch-ish (uncompressed)", false),
    RETROARCH_COMPRESSED(3, "Retroarch (compressed)", true),
    RETROARCH(4, "Retroarch (uncompressed)", false);

    fun step(by: Int): StateFormat = entries.stepFrom(this, by)

    companion object {
        val DEFAULT = RETROARCH
        fun fromStored(value: Int?): StateFormat = entries.firstOrNull { it.stored == value } ?: DEFAULT
    }
}

/** The three handheld settings that name a game's save files (Settings → Library → Saves). */
data class SaveNaming(
    val saveFormat: SaveFormat = SaveFormat.DEFAULT,
    val stateFormat: StateFormat = StateFormat.DEFAULT,
    /** NX Redux "Use extracted file name": an unpacked archive's saves are named after the file inside. */
    val useExtractedFileName: Boolean = false,
) {
    companion object { val DEFAULT = SaveNaming() }
}
