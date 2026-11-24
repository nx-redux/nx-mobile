package com.nxredux.mobile.external

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.FileRefs
import java.io.File

/**
 * Hands games to standalone emulators. [displayIdOfMain]: the display the menu is on, so the app opens where the player
 * is looking (dual screen); null launches as usual.
 */
class AndroidExternalApps(private val context: Context, private val displayIdOfMain: () -> Int?) : ExternalApps {
    private val pm get() = context.packageManager

    // Called while the menu opens: one lookup per package, and a failing one only means "not installed".
    override fun installedPackage(e: ExternalEmulator): String? =
        e.packages.firstOrNull { it !in ExternalEmulators.SPOOFED && installed(it) }

    override fun label(packageId: String): String? =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageId, 0)).toString() }.getOrNull()

    override suspend fun launch(request: ExternalLaunch): ExternalLaunchResult {
        if (!installed(request.packageId)) return ExternalLaunchResult.NOT_INSTALLED
        // A sidecar recipe needs its file written and named in the intent; without that the app would open with no game.
        if (request.emulator.commandsSidecar) return ExternalLaunchResult.FAILED
        val uri = if (request.emulator.takesUri) uriOf(request.ref) else null
        val spec = buildIntent(request, uri?.toString(), displayIdOfMain()) ?: return ExternalLaunchResult.NO_ACCESS
        val options = spec.displayId?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
        return try {
            context.startActivity(spec.toIntent(), options)
            ExternalLaunchResult.STARTED
        } catch (e: ActivityNotFoundException) { ExternalLaunchResult.FAILED } catch (e: SecurityException) { ExternalLaunchResult.FAILED }
    }

    private fun installed(packageId: String) = runCatching { pm.getLaunchIntentForPackage(packageId) != null }.getOrDefault(false)

    /** A real file through our FileProvider (the app has no access to its path); a SAF document as it is, which we can grant. */
    private fun uriOf(ref: ContentRef): Uri? = runCatching {
        if (FileRefs.isFile(ref)) FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, File(FileRefs.path(ref)))
        else Uri.parse(ref.value)
    }.getOrNull()

    private companion object {
        const val AUTHORITY_SUFFIX = ".games"
    }
}

/** The hand-off intent's parts, apart from [Intent] (a stub off a device) so the recipe rules can be checked in unit tests. */
internal data class ExternalIntent(
    val action: String,
    val categories: List<String>,
    val packageId: String,
    val activity: String,
    val data: String?,
    /** The game's extension, for the data's MIME type. */
    val extension: String?,
    val extras: Map<String, String>,
    val flags: Int,
    /** The URI the app is granted, also when it travels in an extra (a grant covers only data and clip data). */
    val grantUri: String?,
    val displayId: Int?,
)

/** [l]'s intent, given the game's [uri] (null when it has none) and the display to open on; null when the app cannot be given the game. */
internal fun buildIntent(l: ExternalLaunch, uri: String?, displayId: Int?): ExternalIntent? {
    val e = l.emulator
    val passed = when (e.pass) {
        GamePass.VIEW_DATA, GamePass.URI_EXTRA -> uri
        GamePass.PATH_EXTRA -> l.realPath
    } ?: return null
    val extras = buildMap {
        putAll(e.extras)
        if (e.pass != GamePass.VIEW_DATA) e.extraKey?.let { put(it, passed) }
    }
    val grantUri = uri.takeIf { e.takesUri }
    var flags = Intent.FLAG_ACTIVITY_NEW_TASK
    if (e.clearTask) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK
    if (grantUri != null) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
    return ExternalIntent(
        action = e.action, categories = e.categories, packageId = l.packageId, activity = e.activity,
        data = passed.takeIf { e.pass == GamePass.VIEW_DATA },
        extension = Names.extensionOf(l.fileName).takeIf { e.pass == GamePass.VIEW_DATA },
        extras = extras, flags = flags, grantUri = grantUri, displayId = displayId,
    )
}

internal fun ExternalIntent.toIntent(): Intent {
    val i = Intent(action)
    categories.forEach(i::addCategory)
    i.component = ComponentName(packageId, activity)
    data?.let { i.setDataAndType(Uri.parse(it), extension?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension) ?: "*/*") }
    extras.forEach { (k, v) -> i.putExtra(k, v) }
    grantUri?.let { i.clipData = ClipData.newRawUri("", Uri.parse(it)) }
    return i.addFlags(flags)
}
