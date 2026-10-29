package com.nxredux.mobile.ui

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.LocalEntry
import com.nxredux.mobile.storage.LocalFiles
import com.nxredux.mobile.storage.LocalVolume
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The home pick's refusal, the list-style picker's and Android's picker's alike (MainActivity). */
const val HOME_NOT_INTERNAL_ROOT = "The phone's storage root can't be the home. Pick or create a folder in it, like NXRedux."
/** The home picker's status at the phone's storage root. */
const val INTERNAL_ROOT_STATUS = "Storage root: pick or create a folder in it"
const val HOME_NOT_WRITABLE = "That folder can't be written to. Pick a folder on your phone's storage or SD card."
internal const val BAD_FOLDER_NAME = "That name can't be used."
internal const val FOLDER_EXISTS = "A folder with that name already exists."
internal const val FOLDER_NOT_CREATED = "Could not create the folder."
internal const val STORAGE_TITLE = "Storage"
private const val USE_FOLDER = "Use this folder"
private const val SAVE_HERE = "Save here"

/** What the list-style picker ([FilePickerController]) picks. */
sealed interface FilePickMode {
    /** The home folder: checked for writes before it is taken. */
    data object Home : FilePickMode
    data object Extra : FilePickMode
    data object ImportFolder : FilePickMode
    /** A `.zip`: zips list after the folders and there is no action row. */
    data object ImportZip : FilePickMode
    /** Export's save-as: the folder to write [suggestedName] in; a file of that name asks "Replace …?" first. */
    data class Export(val suggestedName: String) : FilePickMode
}

/** A row of [Screen.FilePicker]. */
sealed interface PickerRow {
    val label: String
    /** "Use this folder" / "Save here": picks the folder shown. */
    data class Action(override val label: String) : PickerRow
    data class Volume(val volume: LocalVolume) : PickerRow { override val label: String get() = volume.name }
    /** Opens the New folder prompt, as X: below the action row, so a d-pad reaches it. */
    data object NewFolder : PickerRow { override val label: String get() = "New folder" }   // as the X hint and the prompt
    /** "..": the folder above (a volume's root: the volume list), as B. */
    data object Up : PickerRow { override val label: String get() = ".." }
    data class Folder(override val label: String) : PickerRow
    data class Zip(override val label: String) : PickerRow
    /** A file in a folder pick: shown (a ROMs folder looks like one), never picked or opened. */
    data class File(override val label: String) : PickerRow
    /** The usual empty-list text, below the action row; A does nothing. */
    data object Empty : PickerRow { override val label: String get() = "Nothing here" }
}

private val byName: Comparator<String> = compareBy<String> { it.lowercase() }.thenBy { it }

/**
 * A folder's rows for [mode]: the action row and New folder (neither in [FilePickMode.ImportZip]), "..", the folders
 * A–Z ignoring case, then the files A–Z (in ImportZip only the `.zip` files, which pick; elsewhere every file, shown but
 * not picked); dot entries hidden; "Nothing here" when no folder or file is left.
 */
/**
 * What a folder is as a home, for the home picker: empty (hidden names aside); an NX Redux folder (its `Roms`, with at
 * most `Bios`, `Saves`, `Collections` and hidden names beside it); else one holding other files, whose games setup will
 * sort into Roms.
 */
internal fun homeFolderStatus(entries: List<LocalEntry>): String {
    val shown = entries.filter { !it.name.startsWith(".") }
    if (shown.isEmpty()) return "Empty folder"
    val own = setOf("roms", "bios", "saves", "collections", "cheats")
    if (shown.any { it.isDir && it.name.equals("Roms", true) } && shown.all { it.isDir && it.name.lowercase() in own }) return "NX Redux folder"
    return "Has other files: games found in it will be sorted into Roms"
}

/** [canPick] false: no "Use this folder" (the phone's storage root for a home or an extra folder; New folder stays). */
internal fun pickerRows(mode: FilePickMode, entries: List<LocalEntry>, canPick: Boolean = true): List<PickerRow> {
    val action = when (mode) {
        FilePickMode.Home, FilePickMode.Extra, FilePickMode.ImportFolder -> PickerRow.Action(USE_FOLDER)
        is FilePickMode.Export -> PickerRow.Action(SAVE_HERE)
        FilePickMode.ImportZip -> null
    }
    val shown = entries.filter { !it.name.startsWith(".") }
    val folders = shown.filter { it.isDir }.map { it.name }.sortedWith(byName).map { PickerRow.Folder(it) }
    val files: List<PickerRow> = if (mode == FilePickMode.ImportZip) {
        shown.filter { !it.isDir && it.name.endsWith(".zip", ignoreCase = true) }.map { it.name }.sortedWith(byName).map { PickerRow.Zip(it) }
    } else shown.filter { !it.isDir }.map { it.name }.sortedWith(byName).map { PickerRow.File(it) }
    val listed: List<PickerRow> = folders + files
    val newFolder = if (action != null) PickerRow.NewFolder else null
    // The folder's entries first, the actions (Use this folder / Save here, New folder) last: pinned at the bottom.
    return listOf(PickerRow.Up) + listed.ifEmpty { listOf(PickerRow.Empty) } + listOfNotNull(action?.takeIf { canPick }, newFolder)
}

/**
 * The app's own list-style picker, used for every pick while All files access is on. It opens over the current
 * screen, starts in the file home's parent (HOME, EXTRA) or the home itself (imports, export) or at the volume list, and
 * walks folders by path through [files]. A result is delivered after the screen it was opened from (and its selection) is
 * back, so a caller sees the same screen as after Android's picker; B at the volume list delivers null. One listing or
 * check at a time: presses wait for it.
 */
internal class FilePickerController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val files: LocalFiles,
) {
    private var origin: Screen? = null
    private var originSelection = ListSelection(0, 0)
    private var onResult: (ContentRef?) -> Unit = {}
    private var volumes: List<LocalVolume> = emptyList()
    private var work: Job? = null

    /** Opens the picker for [mode]; [home] is the current home (a file home sets the start); [onResult] gets the pick or null (at once, when a picker is already open). */
    fun open(mode: FilePickMode, home: ContentRef?, onResult: (ContentRef?) -> Unit) {
        if (origin != null) {
            // Already open: the second pick is cancelled, the first keeps its origin. A picker something else replaced is dropped.
            if (screen.value is Screen.FilePicker) return onResult(null)
            work?.cancel()
            val stale = this.onResult
            origin = null
            originSelection = ListSelection(0, 0)
            this.onResult = {}
            stale(null)
        }
        origin = screen.value
        originSelection = selection.value
        this.onResult = onResult
        launchWork { start(mode, home) }
    }

    /** The picker's buttons; false when [s] is not the picker. */
    fun onButton(s: Screen, button: NxButton): Boolean {
        val p = s as? Screen.FilePicker ?: return false
        if (work?.isActive == true) return true
        val prompt = p.prompt
        val replace = p.replace
        when {
            prompt != null -> when (button) {
                NxButton.A -> confirmNewFolder(p, prompt)
                NxButton.B -> { screen.value = p.copy(prompt = null) }
                else -> Unit
            }
            replace != null -> when (button) {
                NxButton.A -> finish(FileRefs.of(replace))
                NxButton.B -> { screen.value = p.copy(replace = null) }
                else -> Unit
            }
            else -> when (button) {
                NxButton.UP -> step(p, -1)
                NxButton.DOWN -> step(p, 1)
                NxButton.LEFT, NxButton.RIGHT -> switchSide(p)   // either way, wrapping: entries ↔ actions
                NxButton.A -> activate(p, p.rows.getOrNull(selection.value.index))
                NxButton.B -> up(p)
                NxButton.X -> { if (p.dir != null && p.mode != FilePickMode.ImportZip) screen.value = p.copy(prompt = "") }
                else -> Unit
            }
        }
        return true
    }

    /** The New folder prompt's text as typed. */
    fun draft(text: String) {
        val p = screen.value as? Screen.FilePicker ?: return
        if (p.prompt != null) screen.value = p.copy(prompt = text)
    }

    private fun launchWork(block: suspend () -> Unit) {
        work?.cancel()
        work = scope.launch { block() }
    }

    private suspend fun start(mode: FilePickMode, home: ContentRef?) {
        volumes = files.volumes()
        val homePath = home?.takeIf(FileRefs::isFile)?.let(FileRefs::path)
        val start = when (mode) {
            FilePickMode.Home, FilePickMode.Extra -> homePath?.let(::parentOf)
            else -> homePath
        }
        // One storage: straight into it; the storage list only when there is a choice (a card).
        show(mode, start?.takeIf { volumeOf(it) != null } ?: volumes.singleOrNull()?.path)
    }

    /** Shows [dir] (null: the volume list), the highlight on [focus] (a folder name or a volume path) when it is listed. */
    private suspend fun show(mode: FilePickMode, dir: String?, focus: String? = null) {
        val entries = if (dir == null) emptyList() else files.list(dir)
        val rootPick = (mode == FilePickMode.Home || mode == FilePickMode.Extra) && dir != null && isInternalRoot(dir)
        val rows = if (dir == null) volumes.map { PickerRow.Volume(it) } else pickerRows(mode, entries, canPick = !rootPick)
        // The home pick: what "Use this folder" would take ([homeFolderStatus]).
        val status = if (mode == FilePickMode.Home && dir != null) (if (isInternalRoot(dir)) INTERNAL_ROOT_STATUS else homeFolderStatus(entries)) else null
        screen.value = Screen.FilePicker(mode, dir, dir?.let(::titleOf) ?: STORAGE_TITLE, rows, status = status)
        val at = rows.indexOfFirst { r -> focus != null && ((r is PickerRow.Folder && r.label == focus) || (r is PickerRow.Volume && r.volume.path == focus)) }
        // Else the first entry to walk into (not "..", whose A would leave), else the top.
        val first = rows.indexOfFirst { it is PickerRow.Folder || it is PickerRow.Zip || it is PickerRow.Volume }
        selection.value = ListSelection((if (at >= 0) at else first).coerceAtLeast(0), rows.size)
    }

    private fun activate(p: Screen.FilePicker, row: PickerRow?) {
        when (row) {
            is PickerRow.Volume -> launchWork { show(p.mode, row.volume.path) }
            is PickerRow.Folder -> launchWork { show(p.mode, "${p.dir}/${row.label}") }
            is PickerRow.Zip -> finish(FileRefs.of("${p.dir}/${row.label}"))
            is PickerRow.Action -> pickHere(p)
            PickerRow.Up -> up(p)
            PickerRow.NewFolder -> { screen.value = p.copy(prompt = "") }
            is PickerRow.File, PickerRow.Empty, null -> Unit
        }
    }

    private fun pickHere(p: Screen.FilePicker) {
        val dir = p.dir ?: return
        when (val mode = p.mode) {
            FilePickMode.Home -> launchWork { pickHome(dir) }
            is FilePickMode.Export -> launchWork { pickExport(p, dir, mode.suggestedName) }
            else -> finish(FileRefs.of(dir))
        }
    }

    /** [dir] is the phone's own storage root (not a card's). */
    private fun isInternalRoot(dir: String): Boolean = volumes.any { it.primary && it.path.trimEnd('/') == dir.trimEnd('/') }

    /** The home must take writes (saves, badges and the handheld's files live there); a refusal stays in the picker. */
    private suspend fun pickHome(dir: String) {
        if (isInternalRoot(dir)) { error.value = HOME_NOT_INTERNAL_ROOT; return }   // a card's root is fine: a handheld's
        if (!files.canWrite(dir)) { error.value = HOME_NOT_WRITABLE; return }
        finish(FileRefs.of(dir))
    }

    /** A file of that name in any case asks first: a case-insensitive volume would replace it. */
    private suspend fun pickExport(p: Screen.FilePicker, dir: String, name: String) {
        val target = "$dir/$name"
        if (files.list(dir).any { it.name.equals(name, ignoreCase = true) }) { screen.value = p.copy(replace = target); return }
        finish(FileRefs.of(target))
    }

    /** B: up one level; at a volume's root the volume list (with one storage, cancel); at the volume list, cancel. */
    /** The entry the highlight left for the actions ([switchSide]), where the next sideways press takes it back. */
    private var entryBeforeActions = -1

    /**
     * LEFT or RIGHT: from the folder's entries to the actions pinned under them (Use this folder / Save here, else Create
     * new folder), and from the actions back to the entry the highlight left (else the first entry), wrapping either way.
     */
    private fun switchSide(p: Screen.FilePicker) {
        val first = p.rows.indexOfFirst { it is PickerRow.Action || it == PickerRow.NewFolder }
        if (first < 0) return
        val at = selection.value.index
        val index = if (at < first) { entryBeforeActions = at; first } else {
            entryBeforeActions.takeIf { it in 0 until first }
                ?: p.rows.indexOfFirst { it is PickerRow.Folder || it is PickerRow.Zip }.takeIf { it >= 0 } ?: 0
        }
        selection.value = selection.value.copy(index = index)
    }

    /** UP ([by] -1) or DOWN (+1), wrapping, over the rows that take the selection: a file is passed over. */
    private fun step(p: Screen.FilePicker, by: Int) {
        val n = p.rows.size
        if (n == 0) return
        var i = selection.value.index
        repeat(n) {
            i = (i + by + n) % n
            if (p.rows[i] !is PickerRow.File) { selection.value = selection.value.copy(index = i); return }
        }
    }

    private fun up(p: Screen.FilePicker) {
        val dir = p.dir ?: return finish(null)
        val volume = volumeOf(dir)
        if ((volume == null || dir == volume.path) && volumes.size <= 1) return finish(null)   // no storage list to go back to
        if (volume == null || dir == volume.path) launchWork { show(p.mode, null, focus = volume?.path) }
        else launchWork { show(p.mode, parentOf(dir), focus = dir.substringAfterLast('/')) }
    }

    /** Validates [draft] (trimmed) before any listing; a taken name is checked in any case (a case-insensitive volume). */
    private fun confirmNewFolder(p: Screen.FilePicker, draft: String) {
        val dir = p.dir ?: return
        val name = draft.trim()
        if (!FileRefs.isPlainName(name)) { error.value = BAD_FOLDER_NAME; return }
        launchWork { makeFolder(p, dir, name) }
    }

    private suspend fun makeFolder(p: Screen.FilePicker, dir: String, name: String) {
        if (files.list(dir).any { it.name.equals(name, ignoreCase = true) }) { error.value = FOLDER_EXISTS; return }
        if (!files.mkdir(dir, name)) { error.value = FOLDER_NOT_CREATED; screen.value = p.copy(prompt = null); return }
        show(p.mode, "$dir/$name")
    }

    /** The origin screen and selection back, then [ref] (null: cancelled) to the caller, once. */
    private fun finish(ref: ContentRef?) {
        origin?.let { screen.value = it }
        selection.value = originSelection
        origin = null
        val deliver = onResult
        onResult = {}
        deliver(ref)
    }

    private fun volumeOf(path: String): LocalVolume? =
        volumes.filter { path == it.path || path.startsWith(it.path.trimEnd('/') + "/") }.maxByOrNull { it.path.length }

    private fun parentOf(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }

    /** The folder's path from its volume: "Internal storage/NXRedux/Roms". */
    private fun titleOf(dir: String): String = volumeOf(dir)?.let { it.name + dir.removePrefix(it.path) } ?: dir
}
