package com.nxredux.mobile.storage

/**
 * Whether a game can be resumed and from which save slot (0..7 manual, 9 auto), plus the slot's
 * screenshot as a coil-loadable URI and when it was saved ([savedAt], epoch ms; null without one). Mirrors nextui's
 * `readyResumePath`.
 */
data class ResumeInfo(val slot: Int?, val previewPath: String? = null, val savedAt: Long? = null) {
    val canResume: Boolean get() = slot != null

    companion object {
        val NONE = ResumeInfo(null)

        /** The slot named by `<rom.ext>.txt`: null when there is no file; anything but 0..7 or 9 (garbage, out of range) → 0. */
        fun slotFrom(text: String?): Int? {
            if (text == null) return null
            return text.trim().toIntOrNull()?.takeIf { it in 0 until NxPaths.SLOT_COUNT || it == NxPaths.AUTO_SLOT } ?: 0
        }
    }
}
