package com.nxredux.mobile.runner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.nxredux.mobile.R
import com.nxredux.mobile.achievements.RA_SCREENSHOT_FOLDER
import com.nxredux.mobile.achievements.RaScreenshots
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What an unlock does besides its toast: a short chime ([play]) and the game's picture saved to the device's Pictures
 * ([save]). One per game screen; [release] with it.
 */
internal class UnlockMoments(private val context: Context) {
    private var pool: SoundPool? = null
    private var chime = 0
    @Volatile private var loaded = false

    /** Loads the chime (a few KB, decoded off the main thread), so the first unlock already has it. Main thread. */
    fun prepare() {
        if (pool != null) return
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
        p.setOnLoadCompleteListener { _, id, status -> if (id == chime && status == 0) loaded = true }
        chime = p.load(context, R.raw.ra_unlock, 1)
        pool = p
    }

    /** The chime, over the game's own sound; nothing while it is not loaded. Main thread. */
    fun play() {
        if (loaded) pool?.play(chime, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool?.release()
        pool = null
        loaded = false
    }

    /**
     * [picture] as a PNG named for [game] and [achievement] under Pictures/[RA_SCREENSHOT_FOLDER], where a gallery shows
     * it (before Android 10, which has no such folder without a storage permission: the app's own pictures folder).
     * Blocking I/O; false when it was not saved.
     */
    fun save(picture: Bitmap, game: String, achievement: String): Boolean {
        val name = RaScreenshots.fileName(game, achievement, SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()))
        return try {
            if (Build.VERSION.SDK_INT >= 29) saveToGallery(picture, name) else saveToAppFolder(picture, name)
        } catch (e: Exception) {
            NxLog.w(TAG, "unlock screenshot not saved", e)
            false
        }
    }

    @RequiresApi(29)
    private fun saveToGallery(picture: Bitmap, name: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$RA_SCREENSHOT_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        val written = try {
            resolver.openOutputStream(uri)?.use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        } catch (e: Exception) {
            resolver.delete(uri, null, null)   // never a half-written picture in the gallery
            throw e
        }
        if (!written) { resolver.delete(uri, null, null); return false }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return true
    }

    private fun saveToAppFolder(picture: Bitmap, name: String): Boolean {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false, "Achievements")
        if (!dir.isDirectory && !dir.mkdirs()) return false
        return File(dir, name).outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object { const val TAG = "Achievements" }
}
