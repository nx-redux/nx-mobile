package com.nxredux.mobile.runner

/**
 * The games DS and 3DS Download Play keep so joining the same game again skips the download: their size on this phone,
 * and clearing them to reclaim the space (Settings -> Library). A cleared game downloads again on its next join.
 */
interface DownloadPlayData {
    fun sizeBytes(): Long

    /** Deletes every kept game; the bytes freed. */
    fun clear(): Long
}
