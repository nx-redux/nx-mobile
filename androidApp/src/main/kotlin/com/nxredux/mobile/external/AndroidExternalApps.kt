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
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.FileRefs
import java.io.File

/**
 * Hands games to standalone emulators. [source] reads a recipe's sidecar beside the game; [displayIdOfMain]: the display
 * the menu is on, so the app opens where the player is looking (dual screen); null launches as usual.
 */
class AndroidExternalApps(
    private val context: Context,
    private val source: RomsSource,
    private val displayIdOfMain: () -> Int?,
) : ExternalApps {
    private val pm get() = context.packageManager

    // Called while the menu opens: one lookup per package, and a failing one only means "not installed".
    override fun installedPackage(e: ExternalEmulator): String? =
        e.packages.firstOrNull { it !in ExternalEmulators.SPOOFED && installed(it) }

    override fun label(packageId: String): String? =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageId, 0)).toString() }.getOrNull()

    override suspend fun launch(request: ExternalLaunch): ExternalLaunchResult {
        if (!installed(request.packageId)) return ExternalLaunchResult.NOT_INSTALLED
        val e = request.emulator
        val uri = if (e.takesUri) uriOf(request.ref) else null
        val commands = if (e.commandsSidecar) commandsBeside(request) else null
        val text = if (e.needsText) gameText(runCatching { source.readHead(request.ref, TEXT_GAME_MAX) }.getOrNull()) else null
        val tokens = GameTokens(path = request.realPath, dir = dirOf(request.realPath), text = text)
        val spec = buildIntent(request, uri?.toString(), displayIdOfMain(), commands, tokens) ?: return ExternalLaunchResult.NO_ACCESS
        val options = spec.displayId?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
        return try {
            context.startActivity(spec.toIntent(), options)
            ExternalLaunchResult.STARTED
        } catch (e: ActivityNotFoundException) { ExternalLaunchResult.FAILED } catch (e: SecurityException) { ExternalLaunchResult.FAILED }
    }

    // Optional: without one the app starts with its own last command line.
    private suspend fun commandsBeside(l: ExternalLaunch): String? = runCatching {
        source.child(l.folder, commandsFileName(l.fileName))?.takeIf { !it.isDirectory }?.let { source.readText(it.ref) }
    }.getOrNull()

    private fun installed(packageId: String) = runCatching { pm.getLaunchIntentForPackage(packageId) != null }.getOrDefault(false)

    /** A real file through our FileProvider (the app has no access to its path); a SAF document as it is, which we can grant. */
    private fun uriOf(ref: ContentRef): Uri? = runCatching {
        if (FileRefs.isFile(ref)) FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, File(FileRefs.path(ref)))
        else Uri.parse(ref.value)
    }.getOrNull()

    private companion object {
        const val AUTHORITY_SUFFIX = ".games"

        // A title or game id file is a few bytes; the cap keeps a mistaken pick of a big image cheap.
        const val TEXT_GAME_MAX = 1024
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
    val arrayExtras: Map<String, List<String>>,
    val flags: Int,
    /** The URI the app is granted, also when it travels in an extra (a grant covers only data and clip data). */
    val grantUri: String?,
    val displayId: Int?,
)

/** The sidecar a [ExternalEmulator.commandsSidecar] recipe reads beside [fileName]: its base name plus `.commands`. */
internal fun commandsFileName(fileName: String): String = Names.stripExtension(fileName) + ".commands"

/** The folder of a real path, for a recipe's `{dir}`. */
internal fun dirOf(path: String?): String? = path?.let { File(it).parent }

/**
 * [l]'s intent, given the game's [uri] (null when it has none), the values for the recipe's [tokens] and the display to
 * open on; null when the app cannot be given the game (no URI or path, or a token the game has no value for). A sidecar
 * recipe passes no game: its [commands] (the sidecar's text, null when there is none) name it.
 */
internal fun buildIntent(
    l: ExternalLaunch, uri: String?, displayId: Int?, commands: String? = null, tokens: GameTokens = GameTokens.NONE,
): ExternalIntent? {
    val e = l.emulator
    val passed = if (!e.passesGame) null else when (e.pass) {
        GamePass.VIEW_DATA, GamePass.URI_EXTRA -> uri
        GamePass.PATH_EXTRA -> l.realPath
    } ?: return null
    val data = e.data?.let { tokens.fill(it) ?: return null } ?: passed.takeIf { e.pass == GamePass.VIEW_DATA }
    val extras = buildMap {
        for ((k, v) in e.extras) put(k, tokens.fill(v) ?: return null)
        if (passed != null && e.pass != GamePass.VIEW_DATA) e.extraKey?.let { put(it, passed) }
        if (e.commandsSidecar && commands != null) put(COMMAND_EXTRA, commands)
    }
    val arrayExtras = e.arrayExtras.mapValues { (_, values) -> values.map { tokens.fill(it) ?: return null } }
    val grantUri = uri.takeIf { e.takesUri }
    var flags = Intent.FLAG_ACTIVITY_NEW_TASK
    if (e.clearTask) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK
    if (grantUri != null) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
    return ExternalIntent(
        action = e.action, categories = e.categories, packageId = l.packageId, activity = e.activity,
        data = data,
        extension = Names.extensionOf(l.fileName).takeIf { passed != null && e.pass == GamePass.VIEW_DATA },
        extras = extras, arrayExtras = arrayExtras, flags = flags, grantUri = grantUri, displayId = displayId,
    )
}

private const val COMMAND_EXTRA = "command"

internal fun ExternalIntent.toIntent(): Intent {
    val i = Intent(action)
    categories.forEach(i::addCategory)
    i.component = ComponentName(packageId, activity)
    data?.let { i.setDataAndType(Uri.parse(it), extension?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension) ?: "*/*") }
    extras.forEach { (k, v) -> i.putExtra(k, v) }
    arrayExtras.forEach { (k, v) -> i.putExtra(k, v.toTypedArray()) }
    grantUri?.let { i.clipData = ClipData.newRawUri("", Uri.parse(it)) }
    return i.addFlags(flags)
}
