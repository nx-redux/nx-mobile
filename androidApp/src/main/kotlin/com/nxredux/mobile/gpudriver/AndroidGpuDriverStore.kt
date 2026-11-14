package com.nxredux.mobile.gpudriver

import android.content.Context
import android.net.Uri
import android.os.Build
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.FileRefs
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Settings → Emulation → GPU driver over [AndroidGpuDrivers] (`filesDir/gpu_drivers`). [pickZip] is the app's zip picker
 * (Save import's: Android's document picker, or the list-style one with All files access); it answers a content or file
 * ref, null when cancelled.
 */
class AndroidGpuDriverStore(
    context: Context,
    private val pickZip: (onRef: (ContentRef?) -> Unit) -> Unit,
) : GpuDriverStore {
    private val app = context.applicationContext
    private val drivers = AndroidGpuDrivers(AndroidGpuDrivers.root(app.filesDir), Build.VERSION.SDK_INT)

    override fun unavailableReason(): String? = AndroidGpuDrivers.unavailableReason(BuildConfig.GPU_DRIVERS, Build.VERSION.SDK_INT)

    override fun installed(): List<GpuDriver> = drivers.installed()

    override suspend fun add(): PackageCheck? {
        val ref = suspendCancellableCoroutine<ContentRef?> { cont -> pickZip { ref -> if (cont.isActive) cont.resume(ref) } } ?: return null
        return withContext(Dispatchers.IO) {
            // install() turns read failures into a reason; anything else a provider or a malformed zip throws reads the same.
            try { drivers.install { open(ref) } } catch (e: RuntimeException) { PackageCheck.Rejected(UNREADABLE) }
        }
    }

    override fun remove(id: String) = drivers.remove(id)

    /** The picked file's bytes; a provider that gives no stream is a read failure ([AndroidGpuDrivers.install] reports it). */
    private fun open(ref: ContentRef): InputStream =
        if (FileRefs.isFile(ref)) FileInputStream(File(FileRefs.path(ref)))
        else app.contentResolver.openInputStream(Uri.parse(ref.value)) ?: throw IOException("no stream for the picked file")

    private companion object {
        const val UNREADABLE = "This package can't be read."
    }
}
