package com.nxredux.mobile.storage

/**
 * One-time cleanup of a stale NDS resume point after the move from melonds to melonDS DS. Both cores share the tag, so
 * the resume marker `.minui/NDS/<rom.ext>.txt` and its slot thumbnails outlive the switch, while the states they point
 * at sit under the old core's `NDS-melonds/` dir, which melonDS DS neither reads nor can load. When the marker exists
 * but melonDS DS has no state for this game (neither the marker's slot nor the auto slot), the marker and the
 * thumbnails of slots without a melonDS DS state are stale: deleting them lets the launch boot fresh instead of failing
 * to resume. The old core's states are never touched. Pure; the platform checks, reads and deletes.
 */
object NdsResumeCleanup {
    const val TAG = "NDS"
    const val CORE_ID = "melondsds"

    /**
     * The files to delete (under `.minui/NDS/` only), empty when there is nothing stale. [markerText] is the resume
     * marker's content, null when it doesn't exist; [exists] answers for a path relative to the data root.
     */
    fun victims(tag: String, coreId: String, romFileName: String, markerText: String?, naming: SaveNaming = SaveNaming.DEFAULT, innerFileName: String? = null, exists: (String) -> Boolean): List<String> {
        if (tag != TAG || coreId != CORE_ID) return emptyList()
        val slot = ResumeInfo.slotFrom(markerText) ?: return emptyList()
        val paths = NxPaths(tag, coreId, romFileName, naming, innerFileName)
        fun hasState(s: Int) = paths.stateCandidates(s).any(exists)
        if (hasState(slot) || hasState(NxPaths.AUTO_SLOT)) return emptyList()
        val thumbs = ((0 until NxPaths.SLOT_COUNT) + NxPaths.AUTO_SLOT)
            .filter { exists(paths.thumb(it)) && !hasState(it) }
            .map(paths::thumb)
        return listOf(paths.lastSlot) + thumbs
    }
}
