package com.nxredux.mobile.external

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.AppSettings

/** The platform's side of opening games in standalone emulators: which are installed, and the hand-off. */
interface ExternalApps {
    /** The first of [e]'s packages installed here; null when none is. */
    fun installedPackage(e: ExternalEmulator): String?

    /** The installed app's own label; null when it is not installed. */
    fun label(packageId: String): String?

    suspend fun launch(request: ExternalLaunch): ExternalLaunchResult
}

/** A game handed to [emulator] in [packageId]; [realPath]: its file's path when it has one (All files access, not SAF). */
data class ExternalLaunch(
    val emulator: ExternalEmulator,
    val packageId: String,
    val tag: String,
    val ref: ContentRef,
    val folder: ContentRef,
    val fileName: String,
    val realPath: String?,
)

enum class ExternalLaunchResult { STARTED, NOT_INSTALLED, NO_ACCESS, FAILED }

/** A platform without other emulators: none is installed. */
object NoExternalApps : ExternalApps {
    override fun installedPackage(e: ExternalEmulator): String? = null
    override fun label(packageId: String): String? = null
    override suspend fun launch(request: ExternalLaunch) = ExternalLaunchResult.NOT_INSTALLED
}

/**
 * Which apps can open [game] ([ExternalEmulators.offered]); [naomiSets] is read only for an entry limited to NAOMI sets.
 * [realPath]: the game's path a path-only app may be given ([ExternalLaunch.realPath]); null offers none of them.
 * [hasCommands]: a [commandsFileName] sidecar is beside the game ([hasCommandsSidecar]).
 */
fun offeredFor(game: GameEntry, naomiSets: NaomiSets, realPath: String?, hasCommands: Boolean = false): (ExternalEmulator) -> Boolean {
    val realFile = realPath != null
    val zipped = Names.isArchive(game.fileName)
    val set = Names.stripExtension(game.fileName)
    val extension = Names.extensionOf(game.fileName)
    val naomi by lazy { naomiSets.title(set) != null }
    return { e ->
        ExternalEmulators.offered(
            e, realFile, zipped,
            isNaomiSet = e.sets == SetRule.NAOMI && naomi,
            isModel3Set = e.sets == SetRule.MODEL3 && ExternalEmulators.isModel3Set(set),
            extension = extension, hasCommands = hasCommands,
        )
    }
}

/** The sidecar a [ExternalEmulator.commandsSidecar] recipe reads beside [fileName]: its base name plus `.commands`. */
fun commandsFileName(fileName: String): String = Names.stripExtension(fileName) + ".commands"

/** Whether [game]'s [commandsFileName] sidecar is a file beside it; an unlistable folder counts as none. */
suspend fun hasCommandsSidecar(source: RomsSource, game: GameEntry): Boolean = runCatching {
    source.child(game.folder, commandsFileName(game.fileName))?.isDirectory == false
}.getOrDefault(false)

/** [tag]'s apps that are installed and can open the game, in [table] order, each with its package's own label. */
fun installedExternal(
    tag: String, apps: ExternalApps, offeredFor: (ExternalEmulator) -> Boolean, table: List<ExternalEmulator> = ExternalEmulators.all,
): List<Pair<ExternalEmulator, String>> = ExternalEmulators.forTag(tag, table).mapNotNull { e ->
    val pkg = apps.installedPackage(e) ?: return@mapNotNull null
    if (!offeredFor(e)) return@mapNotNull null
    e to (apps.label(pkg) ?: e.name)
}

/**
 * The app [gamePath] opens in: its stored pick while that app is installed and can open it; for a console without a core,
 * the first such app in [table] order (a pick that went falls through to the next); else null (our core).
 */
fun externalPickFor(
    gamePath: String, tag: String, settings: AppSettings, apps: ExternalApps, offeredFor: (ExternalEmulator) -> Boolean,
    table: List<ExternalEmulator> = ExternalEmulators.all,
): ExternalEmulator? {
    val candidates = ExternalEmulators.forTag(tag, table)
    fun usable(e: ExternalEmulator) = apps.installedPackage(e) != null && offeredFor(e)
    settings.externalPicks[gamePath]?.let { id -> candidates.firstOrNull { it.id == id } }?.takeIf(::usable)?.let { return it }
    if (CoreRegistry.consoleOf(tag)?.externalOnly != true) return null
    return candidates.firstOrNull(::usable)
}

/** What a game's Options says when it opens in another app: [label], the installed app's own; null when none is installed. */
fun opensElsewhereNote(label: String?): String =
    "Opens in ${label ?: "another emulator"}. Save states, the in-game menu and achievement unlocks are not available there."
