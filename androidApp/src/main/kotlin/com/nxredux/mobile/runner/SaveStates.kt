package com.nxredux.mobile.runner

import android.graphics.Bitmap
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.Rzip
import com.nxredux.mobile.storage.SaveLookup
import com.nxredux.mobile.storage.StateBytes
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Per-game save-state slots in minarch's layout ([NxPaths]). The UI numbers slots 1..[SLOT_COUNT];
 * on disk they are 0-based. A slot is "occupied" when its state file exists; its timestamp is that
 * file's last-modified time. Names and encoding follow Settings -> Library -> Saves; loading falls back to the
 * other names (every format, both Use extracted file name settings; the next one when a file is damaged), and reads
 * take RZIP or raw states with a RetroArch RASTATE header skipped. Screenshots are PNG data under minarch's `.bmp`
 * names (nx-redux's minarch loads them with IMG_Load, which sniffs the content; BitmapFactory and Coil sniff too, so
 * real BMPs load as well).
 * Encoding is slow for a full frame: call the writers off the main thread.
 */
class SaveStates(private val root: File, private val paths: NxPaths) {

    data class Slot(val index: Int, val occupied: Boolean, val timestampMillis: Long, val thumb: File?)

    private fun mtime(path: String): Long? = File(root, path).takeIf { it.isFile }?.lastModified()

    /** The file holding disk [diskSlot] now: the configured name, else the newest under another name; null when none. */
    private fun existing(diskSlot: Int): File? = SaveLookup.pick(paths.stateCandidates(diskSlot), ::mtime)?.let { File(root, it) }

    /**
     * Disk [diskSlot]'s state, decoded: the candidates in [SaveLookup.ordered]'s order until one decodes to non-empty
     * bytes, so a damaged or empty file never hides a good state under another name. Null when none does; when none
     * does and a file could not be read, that read's IOException is thrown.
     */
    private fun readFirst(diskSlot: Int): ByteArray? {
        var failure: IOException? = null
        for (path in SaveLookup.ordered(paths.stateCandidates(diskSlot), ::mtime)) {
            val raw = try { File(root, path).readBytes() } catch (e: IOException) { failure = e; continue }
            decode(raw)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        failure?.let { throw it }
        return null
    }

    private fun encode(bytes: ByteArray) = if (paths.stateCompressed) Rzip.encode(bytes) else bytes

    /** As minarch reads a state (ma_saves.c:177-291): RZIP or raw, a RetroArch RASTATE header skipped; null when malformed. */
    private fun decode(raw: ByteArray): ByteArray? = Rzip.decode(raw)?.let(StateBytes::stripRaStateHeader)

    fun stateFile(slot: Int) = File(root, paths.state(slot - 1))   // where a save writes
    fun thumbFile(slot: Int) = File(root, paths.thumb(slot - 1))

    fun slots(): List<Slot> = (1..SLOT_COUNT).map { i -> slot(i, i - 1) }

    /** Disk [diskSlot]'s state and thumbnail as the slot numbered [index]. */
    private fun slot(index: Int, diskSlot: Int): Slot {
        val s = existing(diskSlot)
        val t = File(root, paths.thumb(diskSlot))
        return Slot(index, s != null, s?.lastModified() ?: 0L, t.takeIf { it.exists() })
    }

    /** Writes the state and remembers [slot] as the last used one (minarch's `<rom.ext>.txt`). */
    fun writeState(slot: Int, bytes: ByteArray) {
        stateFile(slot).writeAtomically(encode(bytes))
        rememberSlot(slot)
    }

    /** Records UI [slot] (1..[SLOT_COUNT]) as the last used one, 0-based on disk; minarch does this on save and load. */
    fun rememberSlot(slot: Int) {
        synchronized(slotPointerLock) { File(root, paths.lastSlot).writeAtomically((slot - 1).toString().encodeToByteArray()) }
    }

    fun readState(slot: Int): ByteArray? = readFirst(slot - 1)

    fun writeThumb(slot: Int, bitmap: Bitmap) = thumbFile(slot).writeAtomically(pngBytes(bitmap))

    /** The state for a disk slot (0..7 manual, [NxPaths.AUTO_SLOT] = auto-resume), or null if absent. */
    fun readSlotState(diskSlot: Int): ByteArray? =
        runCatching { readFirst(diskSlot) }.getOrNull()

    fun writeAuto(bytes: ByteArray) = File(root, paths.state(NxPaths.AUTO_SLOT)).writeAtomically(encode(bytes))

    fun writeAutoThumb(bitmap: Bitmap) = File(root, paths.thumb(NxPaths.AUTO_SLOT)).writeAtomically(pngBytes(bitmap))

    /** Deletes the auto resume state and its thumbnail: a launch with the pointer on it then boots fresh. */
    fun dropAuto() {
        paths.stateCandidates(NxPaths.AUTO_SLOT).forEach { File(root, it).delete() }
        File(root, paths.thumb(NxPaths.AUTO_SLOT)).delete()
    }

    /**
     * Points `<rom.ext>.txt` at the auto slot (9). With [onlyIfNotManual] (leaving via Home), it is only
     * written when absent or already 9: a slot file naming a manual slot is left alone so resuming keeps
     * using the slot the player chose, and so is an unreadable/garbage one (minarch reads that as slot 0).
     */
    fun markAuto(onlyIfNotManual: Boolean): Unit = synchronized(slotPointerLock) {
        val file = File(root, paths.lastSlot)
        if (onlyIfNotManual && file.exists()) {
            val current = runCatching { file.readText().trim().toIntOrNull() }.getOrNull()
            if (current != NxPaths.AUTO_SLOT) return
        }
        file.writeAtomically(NxPaths.AUTO_SLOT.toString().encodeToByteArray())
    }

    /**
     * The frame comes in at the game view's native size (2400x1080 landscape, 2176x1812 on the Fold's inner screen),
     * and Quit encodes slot 9's before it can finish, so it is encoded at most [THUMB_MAX_EDGE] on its long side.
     * Nothing needs the native size: nx-redux's minarch and launcher IMG_Load the file and scale it to their box, their
     * own captures are at most 1280x720, and the app's menu strip and switcher card scale it down. The caller's
     * [bitmap] is left as it is (and not recycled). PNG ignores the quality argument (lossless).
     */
    private fun pngBytes(bitmap: Bitmap): ByteArray {
        val (w, h) = thumbSize(bitmap.width, bitmap.height)
        val scaled = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        try {
            val out = ByteArrayOutputStream(scaled.byteCount / 4)
            check(scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed" }
            return out.toByteArray()
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    /** The last-used UI slot (1..[SLOT_COUNT]); 1 when unknown or when it was the auto-resume slot. */
    fun lastSlot(): Int = runCatching { File(root, paths.lastSlot).readText().trim().toInt() }
        .getOrNull()?.takeIf { it in 0 until SLOT_COUNT }?.plus(1) ?: 1

    companion object {
        const val SLOT_COUNT = NxPaths.SLOT_COUNT

        /** The long side a saved screenshot is scaled down to (never up): nx-redux's largest screen, 1280x720. */
        const val THUMB_MAX_EDGE = 1280

        /** [width] x [height] scaled to fit [maxEdge] on its long side, aspect kept (rounded, at least 1 px); as is when it fits. */
        internal fun thumbSize(width: Int, height: Int, maxEdge: Int = THUMB_MAX_EDGE): Pair<Int, Int> {
            val long = maxOf(width, height)
            if (long <= maxEdge) return width to height
            val scale = maxEdge.toDouble() / long
            return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
        }

        /**
         * Serializes every write of `<rom.ext>.txt` ([rememberSlot], [markAuto]'s read-then-write) across the
         * process: a Home write queued by one activity instance can overlap a manual save in the next.
         */
        private val slotPointerLock = Any()
    }
}
