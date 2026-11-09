package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.settings.Sources
import com.nxredux.mobile.storage.StorageUpgradeGate
import com.nxredux.mobile.storage.UpgradeCase
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.tryOr
import com.nxredux.mobile.ui.nx.ListSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the storage upgrade and the copy offer need from [AppState]: the library load after them, the home's paths, Settings. */
internal interface StorageUpgradeHost {
    /** The library loads on the home now ([AppState.load]). */
    fun load()
    /** The picker home becomes its plain path where it can ([AppState]'s convertToFiles); true when it did. */
    suspend fun convertToFiles(): Boolean
    /** The media home's folder as the user sees it. */
    fun mediaPath(media: ContentRef): String
    /** The one-time move of the private data is done (or there is none here). */
    fun dataMoved(): Boolean
    fun openSettings(section: SettingsSection)
    /** The list-style folder picker on the home, when it can open (All files access); [picked] gets the folder or null. */
    fun pickHomeFolder(picked: (ContentRef?) -> Unit)
}

/**
 * The one-time storage upgrade (installs from v0.3.0 and earlier without a full-access home; remove after v1.0): its
 * pages, the adoption of a picker home as an extra and the move of the private data. Also the copy offer after a
 * deliberate home change between two file homes.
 */
internal class StorageUpgradeController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val deps: AppDeps,
    private val host: StorageUpgradeHost,
) {
    /**
     * A deliberate home change from [from] to [to] with All files access, once the data lives in the home: both file
     * folders, not the same. The data is not moved, only offered to copy (the old home may be another build's, e.g. a dev
     * build's own folder, and must never be emptied).
     */
    fun copyOffered(from: ContentRef, to: ContentRef): Boolean =
        deps.storageUpgrade != null && deps.allFilesAccess?.granted() == true && host.dataMoved() &&
            FileRefs.isFile(from) && FileRefs.isFile(to) && from != to

    /** Where [copySavesRow] goes when the offer is answered: the Settings page the home change came from. */
    private var copySavesThen: SettingsSection? = null

    fun offerCopySaves(from: ContentRef, to: ContentRef, then: SettingsSection?) {
        copySavesThen = then
        screen.value = Screen.CopySaves(from, to)
        selection.value = ListSelection(0, 2)
    }

    /** The upgrade screen is up (from its first page until the library loads after it): access changes drive it, not the library. */
    var upgradeOn = false
        private set

    /** Its adoption or move is running: presses and access changes wait for it. */
    private var upgradeBusy = false

    /** The move failed this run: the library goes on with the private data; the screen comes back on the next start. */
    private var upgradeDeferred = false

    /** The saved home when the screen first showed (a picker home the pick may replace). */
    private var upgradeOld: ContentRef? = null

    /** Its first page is for a file home without All files access ([UpgradeCase.ASK_ACCESS]): Not now waits, it never declines. */
    private var upgradeWaits = false

    /** How the upgrade is due now ([StorageUpgradeGate]); null: not due, or no upgrade here. */
    fun upgradeCase(): UpgradeCase? {
        val up = deps.storageUpgrade ?: return null
        return StorageUpgradeGate.due(deps.settings.onboardingDone, up.isDone(), deps.allFilesAccess?.granted() == true, deps.settings.treeRef, up.isWaiting())
    }

    /** The upgrade screen is due. QUIET needs no screen: the platform moves in the background ([load] starts it). */
    fun upgradeDue(): Boolean {
        if (upgradeDeferred) return false
        val case = upgradeCase()
        return case == UpgradeCase.ASK || case == UpgradeCase.ASK_ACCESS
    }

    /** The upgrade's first page; where All files access cannot be had (not declared), the declined path at once. */
    fun startStorageUpgrade() {
        upgradeOn = true
        upgradeOld = deps.settings.treeRef
        upgradeWaits = upgradeCase() == UpgradeCase.ASK_ACCESS
        if (deps.allFilesAccess?.declared != true) return if (upgradeWaits) upgradeWait() else upgradeDecline()
        showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN))
    }

    private fun showStorageUpgrade(s: Screen.StorageUpgrade) {
        screen.value = s
        selection.value = ListSelection(0, storageUpgradeRows(s).size)
    }

    /** A or a tap on the upgrade page's row [index]. */
    fun storageUpgradeRow(index: Int) {
        val s = screen.value as? Screen.StorageUpgrade ?: return
        if (upgradeBusy) return
        if (s.error != null) return upgradeLater()
        when (s.stage) {
            Screen.StorageUpgrade.Stage.EXPLAIN -> when {
                index == 0 -> upgradeAllow()
                upgradeWaits -> upgradeWait()
                else -> upgradeDecline()
            }
            Screen.StorageUpgrade.Stage.PICK -> upgradePick()
            Screen.StorageUpgrade.Stage.DECLINED -> upgradeFinished()
        }
    }

    /** B on the upgrade page: the page is required, so EXPLAIN stays and PICK goes back to it; DECLINED and an error go on as OK. */
    fun storageUpgradeBack(s: Screen.StorageUpgrade) {
        if (upgradeBusy) return
        when {
            s.error != null -> upgradeLater()
            s.stage == Screen.StorageUpgrade.Stage.PICK -> showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN))
            s.stage == Screen.StorageUpgrade.Stage.DECLINED -> upgradeFinished()
            else -> Unit
        }
    }

    /** Allow: granted already, on at once; else Android's page, and the answer comes back through [onAllFilesAccessChanged]. */
    private fun upgradeAllow() {
        if (deps.allFilesAccess?.granted() == true) upgradeGranted() else deps.allFilesAccess?.openSettings()
    }

    /** All files access changed while the upgrade is up: granted on its first page goes on; revoked on the pick goes back to it. */
    fun upgradeAccessChanged() {
        val s = screen.value as? Screen.StorageUpgrade ?: return
        if (upgradeBusy || s.error != null) return
        val granted = deps.allFilesAccess?.granted() == true
        when {
            s.stage == Screen.StorageUpgrade.Stage.EXPLAIN && granted -> upgradeGranted()
            s.stage == Screen.StorageUpgrade.Stage.PICK && !granted -> showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN))
            else -> Unit
        }
    }

    /**
     * Not now on a file home's page: nothing moves to the media home and nothing is marked. The data stays in the app,
     * and the quiet move runs into the file home once access is back.
     */
    private fun upgradeWait() {
        deps.storageUpgrade?.setWaiting()
        upgradeFinished()
    }

    /** Granted: the picker home becomes its path where it can ([convertToFiles]) and the data moves into it; else the pick. */
    private fun upgradeGranted() {
        upgradeBusy = true
        screen.value = Screen.Loading
        scope.launch {
            host.convertToFiles()
            val home = deps.settings.treeRef
            // A file home that can't be read now (card out) waits: the quiet move runs once it is back.
            if (home != null && FileRefs.isFile(home) && deps.homes?.let { it.current() != home } == true) return@launch upgradeFinished()
            if (home != null && FileRefs.isFile(home)) return@launch upgradeMove(home) { upgradeFinished() }
            upgradeBusy = false
            showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.PICK))
        }
    }

    /** The pick's page: the list-style picker on the home (with access on it is always there). */
    private fun upgradePick() {
        host.pickHomeFolder { it?.let(::storageUpgradePicked) }
    }

    /**
     * The folder picked on the upgrade's pick page becomes the home and the data moves into it. A folder other than the
     * old picker home makes that one an extra first (its Bios and Collections copied into [ref], its keys rewritten). A
     * folder over an extra, or over the old home without being it, is refused (its files would list twice).
     */
    fun storageUpgradePicked(ref: ContentRef) {
        val up = deps.storageUpgrade ?: return
        if (!upgradeOn || upgradeBusy) return
        val old = upgradeOld?.takeIf { !FileRefs.isFile(it) }
        val adopt = old != null && Sources.mappedPath(old) != FileRefs.path(ref).trimEnd('/')
        // The extra on this folder's own Roms/ is the old home adopted by an earlier try: the home takes it back.
        val back = deps.settings.extraFolders.firstOrNull { e ->
            (if (FileRefs.isFile(e.ref)) FileRefs.path(e.ref).trimEnd('/') else Sources.mappedPath(e.ref)) == FileRefs.path(ref).trimEnd('/') + "/Roms"
        }
        val clash = deps.settings.extraFolders.filter { it != back }.map { it.ref }.firstOrNull { Sources.overlaps(it, ref) } ?: old?.takeIf { adopt && Sources.overlaps(it, ref) }
        if (clash != null) {
            error.value = "That folder overlaps ${deps.source.displayName(clash)}; files would list twice."
            return
        }
        upgradeBusy = true
        screen.value = Screen.Loading
        scope.launch {
            if (back != null) {
                val restored = tryOr({ false }) { up.restoreHomeKeys(back.id) }
                if (!restored) return@launch upgradeFailed()
                deps.settings.extraFolders = deps.settings.extraFolders.filter { it != back }
            }
            if (adopt) {
                val extra = upgradeAdopt(old!!, ref).getOrElse { return@launch upgradeFailed() }
                extra?.let { deps.settings.extraFolders = deps.settings.extraFolders + it }
            }
            deps.settings.treeRef = ref
            upgradeMove(ref) { upgradeFinished() }
        }
    }

    /** Not now (or no All files access here): the picker home becomes an extra, and the data moves into the media home. */
    private fun upgradeDecline() {
        val up = deps.storageUpgrade ?: return
        upgradeBusy = true
        screen.value = Screen.Loading
        scope.launch {
            val old = deps.settings.treeRef
            if (old != null && !FileRefs.isFile(old)) {
                val extra = upgradeAdopt(old, null).getOrElse { return@launch upgradeFailed() }
                extra?.let { deps.settings.extraFolders = deps.settings.extraFolders + it }
                deps.settings.treeRef = null
            }
            deps.settings.allFilesAsked = true
            val media = deps.homes?.media ?: return@launch upgradeFailed()
            upgradeMove(media) {
                upgradeBusy = false
                showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.DECLINED, folder = host.mediaPath(media)))
            }
        }
    }

    /** [StorageUpgradePlatform.adoptPickerHome]: the extra (null: the old home is gone, nothing to adopt), or its failure. */
    private suspend fun upgradeAdopt(old: ContentRef, into: ContentRef?): Result<ExtraFolder?> {
        val up = deps.storageUpgrade ?: return Result.success(null)
        return tryOr({ Result.failure(it) }) { Result.success(up.adoptPickerHome(old, into)) }
    }

    /** Moves the private data into [home]; marks the upgrade done and runs [then] only when the move finished. */
    private suspend fun upgradeMove(home: ContentRef, then: () -> Unit) {
        val up = deps.storageUpgrade ?: return
        if (!up.moveInto(home)) return upgradeFailed()
        up.markDone()
        then()
    }

    /** The adoption or move did not finish: nothing is marked, the private data stays, and the page says so. */
    private fun upgradeFailed() {
        upgradeBusy = false
        showStorageUpgrade(Screen.StorageUpgrade(Screen.StorageUpgrade.Stage.EXPLAIN, error = UPGRADE_FAILED))
    }

    /** OK after a failure: on to the library for this run; the next start asks again. */
    private fun upgradeLater() {
        upgradeDeferred = true
        upgradeFinished()
    }

    /** The upgrade is over (or put off): the library loads on the home now. */
    private fun upgradeFinished() {
        upgradeOn = false
        upgradeBusy = false
        upgradeOld = null
        host.load()
    }

    /** A or a tap on the copy offer's row [index]: Copy (off the main thread), Keep separate, or OK after a failure. */
    fun copySavesRow(index: Int) {
        val s = screen.value as? Screen.CopySaves ?: return
        if (s.busy) return
        val up = deps.storageUpgrade
        if (s.error != null || index != 0 || up == null) return copySavesDone()
        screen.value = s.copy(busy = true)
        selection.value = ListSelection(0, 0)
        scope.launch {
            val copied = tryOr({ false }) { withContext(deps.compute) { up.copySaves(s.from, s.to) } }
            if (screen.value != s.copy(busy = true)) return@launch
            if (copied) copySavesDone()
            else { screen.value = s.copy(error = COPY_FAILED); selection.value = ListSelection(0, 1) }
        }
    }

    /** The offer answered: back to the Settings page the change came from, else the library. */
    private fun copySavesDone() {
        val then = copySavesThen
        copySavesThen = null
        if (then != null) host.openSettings(then) else host.load()
    }
}

/** The copy offer's error when its copy did not finish. */
private const val COPY_FAILED = "Couldn't copy every save. The old folder still has them all."

/** The storage upgrade's error when its move did not finish. */
private const val UPGRADE_FAILED = "Couldn't move your saves. They're safe; the app will try again next time."
