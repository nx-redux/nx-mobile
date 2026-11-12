package com.nxredux.mobile.ui

import com.nxredux.mobile.kids.KIDS_WARNING
import com.nxredux.mobile.kids.KidsController
import com.nxredux.mobile.kids.KidsMode
import com.nxredux.mobile.kids.KidsSchedule
import com.nxredux.mobile.kids.LocalNow
import com.nxredux.mobile.kids.PinPurpose
import com.nxredux.mobile.kids.PinResult
import com.nxredux.mobile.kids.PlayWindow
import com.nxredux.mobile.kids.UnlockChip
import com.nxredux.mobile.launcher.AppLauncher
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.tryOr
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.gridStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What Kids Mode's screens need from [AppState]: the main menu and its tabs, Settings' pages and pickers, onboarding's
 * way back, the notice and Back.
 */
internal interface KidsHost {
    fun showNotice(text: String)
    /** The main menu on [category] (null: the tab last shown). */
    fun showSystemsNow(category: MenuCategory? = null)
    /** The tiles screen replaces the menu: no hidden tab pushed over it, nothing game-related on the top screen. */
    fun clearMenuTop()
    fun select(index: Int)
    fun back(): Boolean
    fun openSettings(section: SettingsSection)
    fun settingsPage(section: SettingsSection): Screen.Settings
    fun selectableOn(page: Screen.Settings, sel: ListSelection): ListSelection
    fun refreshSettings()
    fun closeChooser()
    fun closeSettingInfo()
    fun rebuildHomeTab()
    fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit)
    fun onboardingNext()
    fun showOnboarding()
    /** The last installed apps listed; null before the first list. */
    val installedApps: List<InstalledApp>?
    /** The menu is not built yet, or first setup's folder steps run: a mode change moves no screen. */
    fun menuWaits(): Boolean
}

/**
 * Kids Mode ([kids]): the tiles screen while Locked; while Open no Tools, Settings or Apps; Parent is the full menu. Also
 * Settings -> Kids Mode and the first switch-on's setup ([KidsSetupStep]), with its PIN, apps and play-time pages.
 */
internal class KidsScreensController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val settings: AppSettings,
    private val launcher: AppLauncher?,
    private val kids: KidsController?,
    private val onboarding: OnboardingController,
    private val host: KidsHost,
) {

    val kidsMode: KidsMode get() = kids?.mode?.value ?: KidsMode.Off

    /** Settings, Tools and the Apps drawer may open: Kids Mode is off, or a parent has the full menu. */
    val kidsToolsAllowed: Boolean get() = kidsMode.let { it == KidsMode.Off || it == KidsMode.Parent }

    val kidsLocked: Boolean get() = kidsMode is KidsMode.Locked

    init {
        // A wake of the phone must not find the parent's unlock where they left it.
        if (kids != null) scope.launch { kids.screenOffs.collect { kidsCloseUnlock() } }
    }

    /** The mode [onKidsMode] last saw; null before its first value. */
    private var kidsModeSeen: KidsMode? = null

    /** Home's pinned tools and apps (and "+N") go while Kids Mode is on, a parent's full menu too: Home is built without them. */
    fun kidsHomeInputs(inputs: HomeInputs): HomeInputs = if (kidsMode == KidsMode.Off) inputs else inputs.copy(tools = emptyList())

    /**
     * Kids Mode's Apps tab, just before Tools: the allowed apps installed now ([kidsTilesOf]), while Kids Mode is on and not
     * locked (a play window, a timed unlock, a parent's full menu). None allowed or installed: no tab.
     */
    fun withKidsApps(tabs: List<MenuTab>): List<MenuTab> {
        if (kidsMode == KidsMode.Off || kidsLocked) return tabs
        val rows = kidsAppRows()
        if (rows.isEmpty()) return tabs
        val at = tabs.indexOfFirst { it.category == MenuCategory.TOOLS }.takeIf { it >= 0 } ?: tabs.size
        return tabs.toMutableList().apply { add(at, MenuTab(MenuCategory.APPS, rows)) }
    }

    /** [withKidsApps]' rows, as last built from the same installed list and allowed set. */
    private var kidsAppRowsMemo: Triple<List<InstalledApp>?, Set<String>, List<MainRow>>? = null

    private fun kidsAppRows(): List<MainRow> {
        val installed = host.installedApps
        val allowed = settings.kidsAllowedApps
        kidsAppRowsMemo?.let { (i, a, rows) -> if (i === installed && a == allowed) return rows }
        val rows = kidsTilesOf(installed.orEmpty(), allowed).map { MainRow.Tool(ToolRow(ToolKind.APP, it.label, null, appId = it.id)) }
        kidsAppRowsMemo = Triple(installed, allowed, rows)
        return rows
    }

    /** The tiles screen; the second screen shows the backdrop ([DualMenu]). */
    fun showKidsHome() {
        host.clearMenuTop()
        screen.value = Screen.KidsHome
        selection.value = ListSelection(0, _kidsTiles.value.size + 1)
        loadKidsTiles()
    }

    private val _kidsTiles = MutableStateFlow<List<KidsTile>>(emptyList())
    /** The tiles screen's apps: the allowed ones installed now ([kidsTilesOf]); the lock comes after them in the selection. */
    val kidsTiles: StateFlow<List<KidsTile>> = _kidsTiles.asStateFlow()
    private var kidsTilesJob: Job? = null
    private var kidsColumns = 1   // the tiles per row as the tiles screen laid them out; the d-pad steps by them

    /** Kids Mode's mode for the kid-facing pages (the next opening under the tiles); always Off without Kids Mode. */
    val kidsModeState: StateFlow<KidsMode> = kids?.mode ?: MutableStateFlow(KidsMode.Off).asStateFlow()

    /** Kids Mode's clock (the tiles screen's clock, the PIN wait's count); null without Kids Mode. */
    fun kidsNow(): LocalNow? = kids?.now()

    /** The 10-minute warning while the menu is in front: the menu's notice ([KIDS_WARNING]). */
    fun kidsShowWarning() = host.showNotice(KIDS_WARNING)

    /** The installed apps listed again for the tiles; the selection keeps its place, the lock staying last. */
    private fun loadKidsTiles() {
        val l = launcher ?: return
        kidsTilesJob?.cancel()
        kidsTilesJob = scope.launch {
            val apps = tryOr({ return@launch }) { l.installedApps() }
            val tiles = kidsTilesOf(apps, settings.kidsAllowedApps)
            _kidsTiles.value = tiles
            if (screen.value == Screen.KidsHome) selection.value = ListSelection(selection.value.index.coerceIn(0, tiles.size), tiles.size + 1)
        }
    }

    /** The tiles screen laid out [columns] tiles per row. */
    fun onKidsColumns(columns: Int) { kidsColumns = columns.coerceAtLeast(1) }

    private var kidsUnlockColumns = UnlockChip.entries.size   // the unlock sheet's chips per row ([kidsUnlockChipColumns])

    /** The unlock sheet laid its chips out [columns] per row; the d-pad steps in that grid. */
    fun onKidsUnlockColumns(columns: Int) { kidsUnlockColumns = columns.coerceAtLeast(1) }

    /** A touch anywhere on the menu: Parent mode's idle time starts again, as a press does; the unlock's idle time too. */
    fun onTouch() { kids?.parentInput(); kidsUnlockInput() }

    private var kidsUnlockIdle: Job? = null

    /** The unlock PIN or sheet is up: the parent's way past the kid's screen. */
    private fun onUnlock(s: Screen): Boolean = s == Screen.KidsUnlock || (s is Screen.KidsPin && s.purpose == PinPurpose.UNLOCK)

    /**
     * Input on the unlock PIN or sheet (or either just opened): it closes after [KIDS_UNLOCK_IDLE_MS] without more, so a
     * parent who walks away never leaves it for the kid.
     */
    private fun kidsUnlockInput() {
        if (!onUnlock(screen.value)) return
        kidsUnlockIdle?.cancel()
        kidsUnlockIdle = scope.launch {
            delay(KIDS_UNLOCK_IDLE_MS)
            kidsCloseUnlock()
        }
    }

    /** The unlock PIN or sheet goes, back to the kid's screen (the screen went off, the app left the front, or idle). */
    fun kidsCloseUnlock() {
        if (!onUnlock(screen.value)) return
        kidsUnlockIdle?.cancel(); kidsUnlockIdle = null
        kidsBackToKid()
    }

    /** A tile: the allowed app [id] starts; any other id (an app taken off the list meanwhile) is ignored. */
    fun kidsOpenApp(id: String) {
        if (kids == null || id !in settings.kidsAllowedApps) return
        val l = launcher ?: return
        if (!l.launch(id)) host.showNotice("Could not open ${_kidsTiles.value.firstOrNull { it.id == id }?.label ?: "the app"}")
    }

    /** The tiles screen's lock: the PIN prompt, a wait from earlier wrong tries still running. */
    fun kidsLockPressed() {
        val k = kids ?: return
        if (screen.value != Screen.KidsHome) return
        screen.value = Screen.KidsPin(PinPurpose.UNLOCK, waitUntil = k.waitUntil)
        selection.value = ListSelection(0, KIDS_PIN_KEYS.size)
        kidsUnlockInput()
    }

    /**
     * The main menu's lock icon in a play window or a timed unlock ([KidsMode.Open]): the PIN prompt, as the tiles' lock
     * is when locked; Back returns to the menu ([kidsBackToKid]). Nothing in any other mode or away from the main menu.
     */
    fun kidsParentPressed() {
        val k = kids ?: return
        if (kidsMode !is KidsMode.Open || screen.value !is Screen.Systems) return
        screen.value = Screen.KidsPin(PinPurpose.UNLOCK, waitUntil = k.waitUntil)
        selection.value = ListSelection(0, KIDS_PIN_KEYS.size)
        kidsUnlockInput()
    }

    /** Off the unlock PIN or the sheet without a choice: the tiles while Locked, else the main menu the lock was pressed on. */
    fun kidsBackToKid() {
        if (kidsLocked) showKidsHome() else host.showSystemsNow()
    }

    /** Digit [d] on the PIN pad: up to six, none while the wait after five wrong tries runs. */
    fun kidsPinDigit(d: Int) {
        val s = screen.value as? Screen.KidsPin ?: return
        val k = kids ?: return
        if (d !in 0..9 || s.typed.length >= KIDS_PIN_LENGTH || k.now().epochMs < s.waitUntil) return
        screen.value = s.copy(typed = s.typed + d)
    }

    /** The PIN pad's Back: the last digit goes; with none typed, the prompt closes. */
    fun kidsPinBack() {
        val s = screen.value as? Screen.KidsPin ?: return
        if (s.typed.isEmpty()) kidsPinLeave(s) else screen.value = s.copy(typed = s.typed.dropLast(1))
    }

    /** A tap on PIN key [key]: the highlight moves onto it (as the d-pad's would), then the key acts. */
    internal fun kidsPinTap(key: PinKey) {
        if (screen.value !is Screen.KidsPin) return
        kidsUnlockInput()
        KIDS_PIN_KEYS.indexOf(key).takeIf { it >= 0 }?.let(host::select)
        when (key) {
            is PinKey.Digit -> kidsPinDigit(key.d)
            PinKey.Back -> kidsPinBack()
            PinKey.Enter -> kidsPinEnter()
        }
    }

    /**
     * The PIN pad's Enter, with all six digits typed: the right PIN opens the unlock sheet; a wrong one clears the digits and
     * counts ([Screen.KidsPin.wrong]: the page shakes and says so); the fifth starts the wait ([KidsController.tryPin]).
     */
    fun kidsPinEnter() {
        val s = screen.value as? Screen.KidsPin ?: return
        val k = kids ?: return
        if (s.typed.length < KIDS_PIN_LENGTH) return
        if (s.purpose != PinPurpose.UNLOCK) return kidsNewPinEnter(s)   // setup's and Change PIN's: a new PIN, twice
        when (k.tryPin(s.typed, k.now().epochMs)) {
            PinResult.OK -> { screen.value = Screen.KidsUnlock; selection.value = ListSelection(0, KIDS_UNLOCK_ROWS); kidsUnlockInput() }
            PinResult.WRONG -> screen.value = s.copy(typed = "", wrong = s.wrong + 1)
            PinResult.WAIT -> screen.value = s.copy(typed = "", wrong = s.wrong + 1, waitUntil = k.waitUntil)
        }
    }

    /**
     * The PIN prompt closed: the unlock one back to the tiles; the confirmation back to the new PIN; setup's and Change
     * PIN's new PIN back to the Kids Mode page (setup cancelled, Kids Mode still off).
     */
    fun kidsPinLeave(s: Screen.KidsPin) {
        when (s.purpose) {
            PinPurpose.UNLOCK -> kidsBackToKid()
            PinPurpose.SETUP_CONFIRM -> if (kidsParentOnly()) kidsAskNewPin(null)
            PinPurpose.SETUP_NEW, PinPurpose.CHANGE -> if (kidsParentOnly()) {
                kidsNewPin = null
                if (kidsPinChange) kidsShowPage(SettingsKind.KIDS_PIN) else kidsSetupExit(done = false)
            }
        }
    }

    /** Let them play's [chip]: the timed unlock starts, and the kid gets the menu (no Tools). The unlock sheet only. */
    fun kidsUnlockChip(chip: UnlockChip) {
        val k = kids ?: return
        if (screen.value != Screen.KidsUnlock) return
        k.unlock(chip)
        kidsLeaveSheet()
    }

    /** Open full menu: Parent mode, the whole menu until Lock or five idle minutes. The unlock sheet only. */
    fun kidsOpenFullMenu() {
        val k = kids ?: return
        if (screen.value != Screen.KidsUnlock) return
        k.enterParent(k.now().epochMs)
        kidsLeaveSheet()
    }

    /** Kids Mode settings: Parent mode, then Settings. The unlock sheet only. */
    fun kidsOpenSettings() {
        val k = kids ?: return
        if (screen.value != Screen.KidsUnlock) return
        k.enterParent(k.now().epochMs)
        kidsLeaveSheet()
        host.openSettings(SettingsSection.KIDS)
    }

    /** Off the sheet onto Home at once, unless the mode's collector ([onKidsMode]) has moved there already. */
    private fun kidsLeaveSheet() {
        kidsUnlockIdle?.cancel(); kidsUnlockIdle = null
        if (isKidsScreen(screen.value) && !kidsLocked) host.showSystemsNow(MenuCategory.HOME)
    }

    /** Tools' Lock Kids Mode: Parent ends and a timed unlock goes; Locked shows the tiles (a play window keeps the menu). */
    fun kidsLockNow() {
        val k = kids ?: return
        k.lock()
        if (kidsLocked && !isKidsScreen(screen.value)) showKidsHome()
    }

    /**
     * The parent bar's Back to Kids Mode ([KidsParentBar]): Parent ends, a timed unlock the kid was given stays; the
     * tiles while Locked, else the menu without Tools.
     */
    fun kidsEndParent() {
        val k = kids ?: return
        k.endParent()
        if (kidsLocked && !isKidsScreen(screen.value)) showKidsHome()
    }

    /** [tabs] with Lock Kids Mode first in Tools while in Parent mode; as they are otherwise. */
    fun withKidsLock(tabs: List<MenuTab>): List<MenuTab> =
        if (kidsMode != KidsMode.Parent) tabs
        else tabs.map { if (it.category == MenuCategory.TOOLS) MenuTab(MenuCategory.TOOLS, listOf(KIDS_LOCK_ROW) + it.rows) else it }

    /** A on a Kids Mode page (or a tap on its selected item): the tile, the lock, the PIN key or the sheet's row. */
    fun kidsActivate(s: Screen) {
        val i = selection.value.index
        when (s) {
            Screen.KidsHome -> _kidsTiles.value.getOrNull(i)?.let { kidsOpenApp(it.id) } ?: kidsLockPressed()
            is Screen.KidsPin -> when (val key = KIDS_PIN_KEYS.getOrNull(i)) {
                is PinKey.Digit -> kidsPinDigit(key.d)
                PinKey.Back -> kidsPinBack()
                PinKey.Enter -> kidsPinEnter()
                null -> Unit
            }
            Screen.KidsUnlock -> when (i) {
                in UnlockChip.entries.indices -> kidsUnlockChip(UnlockChip.entries[i])
                KIDS_UNLOCK_FULL_MENU -> kidsOpenFullMenu()
                KIDS_UNLOCK_SETTINGS -> kidsOpenSettings()
            }
            else -> Unit
        }
    }

    /**
     * The pad on a Kids Mode page. The tiles: the d-pad by tiles (UP from the first row to the lock), A opens, MENU the
     * lock, B nothing. The PIN pad: the d-pad by keys, A presses one, B is Back, START Enter. The sheet: LEFT / RIGHT
     * along the chips, UP / DOWN between the chips and the two rows, A chooses, B back to the tiles.
     */
    fun onKidsButton(s: Screen, button: NxButton) {
        kidsUnlockInput()
        val sel = selection.value
        fun move(to: Int) { if (to != sel.index) selection.value = sel.copy(index = to) }
        when (s) {
            Screen.KidsHome -> when {
                button == NxButton.MENU -> kidsLockPressed()
                button == NxButton.A -> kidsActivate(s)
                button.isDirection -> move(kidsHomeStep(sel.index, _kidsTiles.value.size, kidsColumns, button))
            }
            is Screen.KidsPin -> when {
                button == NxButton.A -> kidsActivate(s)
                button == NxButton.B -> kidsPinBack()
                button == NxButton.START -> kidsPinEnter()
                button.isDirection -> gridStep(sel.index, KIDS_PIN_KEYS.size, KIDS_PIN_COLUMNS, button)?.let(::move)
            }
            Screen.KidsUnlock -> when {
                button == NxButton.A -> kidsActivate(s)
                button == NxButton.B -> kidsBackToKid()
                button.isDirection -> move(kidsUnlockStep(sel.index, button, kidsUnlockColumns))
            }
            else -> Unit
        }
    }

    /** What may stay up while Open: the menu, its game lists (not Hidden or Unassigned, which belong to Settings) and the switcher. */
    private fun kidsOpenScreen(s: Screen): Boolean = when (s) {
        is Screen.Systems, is Screen.Switcher -> true
        is Screen.Games -> s.source != ListSource.Hidden && (s.source as? ListSource.Console)?.system?.unassigned != true
        else -> false
    }

    /**
     * The mode changed: Locked shows the tiles screen over anything; leaving Locked from the tiles screen (or its unlock
     * PIN) lands on Home; Open closes whatever a parent left open but the menu and its lists; on the menu, the tabs follow
     * (Tools, and Home's pinned tools, come and go). Before the first load nothing moves: the load lands through the menu's first show.
     */
    fun onKidsMode(mode: KidsMode) {
        val was = kidsModeSeen
        kidsModeSeen = mode
        if (was == null || was == mode) return
        if (host.menuWaits() || onboarding.step != null) return
        if ((was == KidsMode.Off) != (mode == KidsMode.Off)) host.rebuildHomeTab()   // Home's pinned tools come and go with Kids Mode
        val s = screen.value
        // A parent's picker or a row's description over their page goes with the page.
        if (mode is KidsMode.Locked) { host.closeChooser(); host.closeSettingInfo() }
        when {
            s == Screen.Loading -> Unit
            // A parent's PIN pad (setup's, Change PIN's) goes too, its half-typed PIN with it: only the unlock pad stays.
            mode is KidsMode.Locked -> if (!isKidsScreen(s) || (s is Screen.KidsPin && s.purpose != PinPurpose.UNLOCK)) { kidsNewPin = null; showKidsHome() }
            // Setup's and Change PIN's PIN pad is a parent's page: it stays while Parent (or Off), and goes with them.
            s is Screen.KidsPin && s.purpose != PinPurpose.UNLOCK -> if (!kidsToolsAllowed) host.showSystemsNow(MenuCategory.HOME)
            isKidsScreen(s) -> host.showSystemsNow(MenuCategory.HOME)
            !kidsToolsAllowed && !kidsOpenScreen(s) -> host.showSystemsNow(MenuCategory.HOME)
            s.isMainMenu -> host.showSystemsNow()
        }
    }

    // --- Settings -> Kids Mode and the first switch-on's setup ([KidsSetupStep]) ---

    /** The new PIN typed once, until it is confirmed (and, in setup, until setup finishes: a cancelled setup stores none). */
    internal var kidsNewPin: String? = null   // internal: a test drops it to check setup never ends without a PIN

    /**
     * The Kids Mode page and setup are a parent's: true while Kids Mode is off or in Parent mode. Otherwise the half-typed
     * PIN goes and the kid's screen shows (the tiles while Locked, else Home), and the caller stops.
     */
    private fun kidsParentOnly(): Boolean {
        if (kidsToolsAllowed) return true
        kidsNewPin = null
        if (kidsLocked) showKidsHome() else host.showSystemsNow(MenuCategory.HOME)
        return false
    }
    /** The PIN steps are Change PIN's (stored at once, back to the page), else first setup's. */
    private var kidsPinChange = false

    /**
     * Settings -> Kids Mode: On / Off, Change PIN (once there is one), a row per day, Allowed apps, the warnings for a
     * missing permission (only while on), then Forgot PIN? with the flag file's path. Empty without Kids Mode.
     */
    fun kidsRows(): List<SettingsRow> = buildList {
        val k = kids ?: return@buildList
        val s = settings
        add(SettingsRow(SettingsKind.KIDS_ENABLE, "Kids Mode", if (s.kidsEnabled) "On" else "Off"))
        if (s.kidsPinSet) add(SettingsRow(SettingsKind.KIDS_PIN, "Change PIN"))   // before the first setup, setup sets it
        add(SettingsRow(SettingsKind.GAP, ""))
        add(SettingsRow(SettingsKind.SECTION, "Play times"))
        val week = KidsSchedule.decode(s.kidsSchedule)
        for (d in 1..7) add(SettingsRow(SettingsKind.KIDS_DAY, kidsDayName(d), kidsWindowLabel(week.days[d]), day = d))
        add(SettingsRow(SettingsKind.GAP, ""))
        add(SettingsRow(SettingsKind.KIDS_APPS, "Allowed apps", kidsAppsSummary(s.kidsAllowedApps)))
        if (s.kidsEnabled && !k.hasUsageAccess()) add(SettingsRow(SettingsKind.KIDS_GUARD, "Guard off", "Usage access not granted", warning = true))
        if (s.kidsEnabled && !k.canExactAlarms()) add(SettingsRow(SettingsKind.KIDS_ALARMS, "Exact timing off", "Alarms & reminders not allowed", warning = true))
        if (s.kidsEnabled && kidsNeedsHome()) add(SettingsRow(SettingsKind.KIDS_HOME, "Not the home screen", "NX Redux isn't the home app", warning = true))
        val where = if (s.kidsEnabled && k.flagWriteFailed) KIDS_FLAG_FAILED else k.flagPath() ?: "$KIDS_FLAG_NAME in your home folder"
        add(SettingsRow(SettingsKind.KIDS_FORGOT, "Forgot PIN?", where))
    }

    /** Allowed apps' value: the installed ones' names, two at most and "+N" for the rest; None for none. */
    private fun kidsAppsSummary(ids: Set<String>): String {
        val labels = ids.mapNotNull { launcher?.label(it) }.sortedBy { it.lowercase() }
        return when {
            labels.isEmpty() -> "None"
            labels.size <= 2 -> labels.joinToString(", ")
            else -> labels.take(2).joinToString(", ") + " +${labels.size - 2}"
        }
    }

    /** The Kids Mode page on its row of [kind] (a day's: [day]). */
    private fun kidsShowPage(kind: SettingsKind, day: Int? = null) {
        if (!kidsParentOnly()) return
        val page = host.settingsPage(SettingsSection.KIDS)
        screen.value = page
        val i = page.rows.indexOfFirst { it.kind == kind && (day == null || it.day == day) }.coerceAtLeast(0)
        selection.value = host.selectableOn(page, ListSelection(i, page.rows.size))
    }

    /** Kids Mode switched [on]: the first time (no PIN yet) runs setup; otherwise on at once, or off (its flag deleted). */
    fun kidsSetEnabled(on: Boolean) {
        if (!kidsParentOnly()) return
        val k = kids ?: return
        when {
            !on -> k.turnOff()
            settings.kidsPinSet -> if (kidsNeedsHome()) kidsSetupGo(KidsSetupStep.HOME) else k.turnOn()   // home: setup's last step alone
            else -> { kidsPinChange = false; kidsNewPin = null; kidsAskNewPin(null) }
        }
    }

    /**
     * Kids Mode only turns on while NX Redux is the home app: otherwise Android keeps the lock and the guard from
     * bringing the tiles screen forward, and Home leaves the app. False where there is no home to be (no launcher).
     */
    private fun kidsNeedsHome(): Boolean = launcher?.isHome() == false

    /** Kids Mode's setup runs from onboarding's Who's this for?: it ends on Done, or back on that question when cancelled. */
    var kidsInOnboarding = false

    /**
     * Onboarding's My kids: Kids Mode's setup, as its switch would run it (home its last step). Only right before the
     * home question is onboarding saved at Done ([kidsUseHome]), so the new home instance that accepting the role starts
     * resumes there, Kids Mode on ([kidsHomeCheck]); a relaunch before it asks Who's this for? again.
     */
    fun kidsStartInOnboarding() {
        if (kids == null || settings.kidsEnabled) return host.onboardingNext()
        kidsInOnboarding = true   // onboarding stays saved at Who's this for? until the home question ([kidsUseHome])
        kidsSetEnabled(true)
        if (settings.kidsEnabled) kidsBackToOnboarding(done = true)   // a PIN already set and home: on at once
    }

    /** Onboarding's Done with Kids Mode on (My kids set it up): no way back into the steps before it, only Press Start. */
    fun kidsOnAtDone(): Boolean = onboarding.step == OnboardingStep.DONE && settings.kidsEnabled

    /** Off setup back into onboarding: Done once Kids Mode is on ([done]), else Who's this for? again. */
    private fun kidsBackToOnboarding(done: Boolean) {
        kidsInOnboarding = false
        kidsNewPin = null
        val step = if (done) OnboardingStep.DONE else OnboardingStep.KIDS
        onboarding.startMain(resume = step)
        settings.onboardingStep = onboarding.step?.name
        host.showOnboarding()
    }

    /** Setup's end or its cancel: back into onboarding when it ran from there, else the Kids Mode page. */
    private fun kidsSetupExit(done: Boolean) {
        if (kidsInOnboarding && onboarding.step != null) kidsBackToOnboarding(done) else { kidsInOnboarding = false; kidsShowPage(SettingsKind.KIDS_ENABLE) }
    }

    /**
     * Setup's last step, Use as home screen: the PIN stored and Kids Mode marked to turn on once NX Redux is home
     * ([AppSettings.kidsOnWhenHome]: accepting restarts the menu as a new home instance), then Android asks.
     */
    private fun kidsUseHome() {
        if (!kidsParentOnly()) return
        if (kidsNewPin == null && !settings.kidsPinSet) return kidsAskNewPin(null)   // never on without a PIN
        kidsNewPin?.let(settings::setKidsPin)
        kidsNewPin = null
        settings.kidsOnWhenHome = true
        if (kidsInOnboarding && onboarding.step != null) settings.onboardingStep = OnboardingStep.DONE.name   // a relaunch resumes on Done
        launcher?.setHome(true)
    }

    /**
     * Back from Android's home question ([kidsUseHome]), in this instance or the new home one: home now, Kids Mode on
     * (the tiles on a locked day); declined, it stays off and the page says why.
     */
    fun kidsHomeCheck() {
        val k = kids ?: return
        if (!settings.kidsOnWhenHome) return
        settings.kidsOnWhenHome = false
        if (launcher?.isHome() == true) {
            if (!settings.kidsEnabled) k.turnOn()
            if (screen.value is Screen.KidsSetup) kidsSetupExit(done = true)   // the kid's screen, or onboarding's Done
        } else {
            // Declined: onboarding's setup stays on its home step (Cancel goes back); Settings' goes to the page.
            if (screen.value is Screen.KidsSetup && !kidsInOnboarding) kidsShowPage(SettingsKind.KIDS_ENABLE)
            host.showNotice(KIDS_HOME_REFUSED)
        }
    }

    /** Change PIN: a new PIN twice (only a parent reaches the page, so the old one is not asked). */
    fun kidsChangePin() {
        if (!kidsParentOnly()) return
        if (kids == null) return
        kidsPinChange = true
        kidsNewPin = null
        kidsAskNewPin(null)
    }

    /** The new-PIN prompt (Change PIN's or setup's), with [message] under its dots. */
    private fun kidsAskNewPin(message: String?) {
        screen.value = Screen.KidsPin(if (kidsPinChange) PinPurpose.CHANGE else PinPurpose.SETUP_NEW, message = message)
        selection.value = ListSelection(0, KIDS_PIN_KEYS.size)
    }

    /** Enter on a new PIN: the first time asks again; the second must match, else both start over ([KIDS_PIN_MISMATCH]). */
    private fun kidsNewPinEnter(s: Screen.KidsPin) {
        if (!kidsParentOnly()) return
        if (s.purpose != PinPurpose.SETUP_CONFIRM) {
            kidsNewPin = s.typed
            screen.value = Screen.KidsPin(PinPurpose.SETUP_CONFIRM)
            selection.value = ListSelection(0, KIDS_PIN_KEYS.size)
            return
        }
        val pin = kidsNewPin
        if (pin == null || pin != s.typed) { kidsNewPin = null; return kidsAskNewPin(KIDS_PIN_MISMATCH) }
        if (kidsPinChange) {
            settings.setKidsPin(pin)
            kidsNewPin = null
            kidsShowPage(SettingsKind.KIDS_PIN)
            host.showNotice("PIN changed")
        } else {
            kidsSetupGo(KidsSetupStep.APPS)
        }
    }

    /**
     * Setup's [step], or the first one after it that applies: Usage access and Alarms & reminders only while missing
     * (Alarms is always granted before Android 14), home only while NX Redux is not home; past the last, setup ends.
     */
    private fun kidsSetupGo(step: KidsSetupStep) {
        if (!kidsParentOnly()) return
        val k = kids ?: return
        val skip = when (step) {
            KidsSetupStep.PIN -> return kidsAskNewPin(null)
            KidsSetupStep.USAGE -> k.hasUsageAccess()
            KidsSetupStep.ALARMS -> k.canExactAlarms()
            KidsSetupStep.HOME -> !kidsNeedsHome()
            else -> false
        }
        if (skip) return kidsSetupAfter(step)
        if (step == KidsSetupStep.APPS) return kidsOpenApps(setup = true)
        kidsShowSetup(Screen.KidsSetup(step, schedule = KidsSchedule.decode(settings.kidsSchedule)), 0)
    }

    private fun kidsSetupAfter(step: KidsSetupStep) {
        val next = KidsSetupStep.entries.getOrNull(step.ordinal + 1) ?: return kidsSetupFinish()
        kidsSetupGo(next)
    }

    /** Setup's end: the PIN stored, Kids Mode on with its flag ([KidsController.turnOn]), straight into the schedule's mode. */
    private fun kidsSetupFinish() {
        if (!kidsParentOnly()) return
        val k = kids ?: return
        if (kidsNewPin == null && !settings.kidsPinSet) return kidsAskNewPin(null)   // never on without a PIN
        kidsNewPin?.let(settings::setKidsPin)
        kidsNewPin = null
        k.turnOn()
        kidsSetupExit(done = true)
    }

    private fun kidsShowSetup(page: Screen.KidsSetup, selected: Int) {
        screen.value = page
        val rows = kidsSetupRows(page).size
        selection.value = ListSelection(selected.coerceIn(0, (rows - 1).coerceAtLeast(0)), rows)
    }

    /** The allowed-apps picker: setup's APPS step, or Settings' Allowed apps; the installed apps listed off the main thread. */
    fun kidsOpenApps(setup: Boolean) {
        if (!kidsParentOnly()) return
        val first = Screen.KidsSetup(KidsSetupStep.APPS, setup = setup, allowed = settings.kidsAllowedApps)
        kidsShowSetup(first, 0)
        val l = launcher ?: return
        scope.launch {
            val apps = tryOr({ return@launch }) { l.installedApps() }
            val now = screen.value as? Screen.KidsSetup ?: return@launch
            if (now.step != KidsSetupStep.APPS || now.setup != setup) return@launch
            kidsShowSetup(now.copy(apps = apps), selection.value.index)
        }
    }

    /** [app] on or off the kid's list, at once. */
    private fun kidsToggleApp(s: Screen.KidsSetup, app: InstalledApp) {
        val allowed = if (app.id in s.allowed) s.allowed - app.id else s.allowed + app.id
        settings.kidsAllowedApps = allowed
        screen.value = s.copy(allowed = allowed)
    }

    /** A on (or a tap on) setup row [index]. */
    fun kidsSetupRow(index: Int) {
        val s = screen.value as? Screen.KidsSetup ?: return
        if (!kidsParentOnly()) return
        val k = kids ?: return
        host.select(index)
        when (val row = kidsSetupRows(s).getOrNull(index) ?: return) {
            is KidsSetupRow.App -> kidsToggleApp(s, row.app)
            is KidsSetupRow.Day -> kidsOpenDay(row.day, setup = true)
            KidsSetupRow.Next -> when {
                !s.setup -> kidsShowPage(SettingsKind.KIDS_APPS)
                s.step == KidsSetupStep.APPS && s.allowed.isEmpty() -> host.showNotice(KIDS_PICK_ONE_APP)   // the tiles would be empty
                else -> kidsSetupAfter(s.step)
            }
            KidsSetupRow.Grant -> if (s.step == KidsSetupStep.USAGE) k.openUsageAccess() else k.openExactAlarms()   // on with the step on resume
            KidsSetupRow.Skip -> kidsSetupAfter(s.step)
            KidsSetupRow.UseHome -> kidsUseHome()   // on once home ([kidsHomeCheck])
            KidsSetupRow.Cancel -> kidsSetupLeave(s)
        }
    }

    /** A setup row's switch (an app's) set to [index] (0 off, 1 on), as a tap on it. */
    fun kidsSetupSetting(event: NxSettingEvent) {
        val s = screen.value as? Screen.KidsSetup ?: return
        val e = event as? NxSettingEvent.Choose ?: return
        val row = kidsSetupRows(s).getOrNull(e.row) as? KidsSetupRow.App ?: return
        host.select(e.row)
        if (row.allowed != (e.index == 1)) kidsToggleApp(s, row.app)
    }

    /**
     * The pad on a setup step: UP / DOWN along its rows, A or a side step on an app's switch, B out. B leaves setup for
     * the Kids Mode page (Kids Mode stays off, nothing stored but the apps and times chosen); Allowed apps' B is its Done.
     */
    fun onKidsSetupButton(s: Screen.KidsSetup, button: NxButton) {
        val sel = selection.value
        val rows = kidsSetupRows(s)
        when (button) {
            NxButton.UP, NxButton.DOWN -> if (rows.isNotEmpty()) selection.value = sel.copy(index = (sel.index + if (button == NxButton.UP) -1 else 1).mod(rows.size))
            // An app's switch flips; the two buttons side by side (Grant / Skip, Use as home screen / Skip): LEFT the first, RIGHT the second.
            NxButton.LEFT, NxButton.RIGHT -> when (val row = rows.getOrNull(sel.index)) {
                is KidsSetupRow.App -> kidsToggleApp(s, row.app)
                is KidsSetupRow.Day, KidsSetupRow.Next, null -> Unit
                else -> selection.value = sel.copy(index = if (button == NxButton.LEFT) 0 else 1)
            }
            NxButton.A -> kidsSetupRow(sel.index)
            NxButton.B -> host.back()
            else -> Unit
        }
    }

    fun kidsSetupLeave(s: Screen.KidsSetup) {
        kidsNewPin = null
        if (s.setup) kidsSetupExit(done = false) else kidsShowPage(SettingsKind.KIDS_APPS)
    }

    /** Day [day]'s play time page, from setup's TIMES step ([setup]) or the Kids Mode page. */
    fun kidsOpenDay(day: Int, setup: Boolean) {
        if (!kidsParentOnly()) return
        val w = KidsSchedule.decode(settings.kidsSchedule).days[day]
        screen.value = Screen.KidsDayEdit(day, w, w ?: KidsSchedule.DEFAULT.days[5]!!, setup)
        selection.value = ListSelection(0, kidsDayRows(screen.value as Screen.KidsDayEdit).size)
    }

    /** The day's window set to [w] (null: locked), stored at once; the selection stays on [row]. */
    private fun kidsDaySet(s: Screen.KidsDayEdit, w: PlayWindow?, row: KidsDayRow) {
        if (!kidsParentOnly()) return
        val week = KidsSchedule.decode(settings.kidsSchedule)
        settings.kidsSchedule = week.withDay(s.day, w).encode()
        val next = s.copy(window = w, kept = w ?: s.kept)
        screen.value = next
        val rows = kidsDayRows(next)
        selection.value = ListSelection(rows.indexOf(row).coerceAtLeast(0), rows.size)
    }

    /** Locked on or off: off brings back the window the day had. */
    private fun kidsDayLocked(s: Screen.KidsDayEdit, locked: Boolean) {
        if (locked == (s.window == null)) return
        kidsDaySet(s, if (locked) null else s.kept, KidsDayRow.LOCKED)
    }

    /** From or To [by] half hours, From kept before To ([kidsDayStep]). */
    private fun kidsDayStep(s: Screen.KidsDayEdit, row: KidsDayRow, by: Int) {
        val w = s.window ?: return
        val next = kidsDayStep(w, row == KidsDayRow.FROM, by)
        if (next != w) kidsDaySet(s, next, row)
    }

    /** A on (or a tap on) day row [index]: Locked flips, From / To open their picker, Done goes back. */
    fun kidsDayRow(index: Int) {
        val s = screen.value as? Screen.KidsDayEdit ?: return
        host.select(index)
        when (val row = kidsDayRows(s).getOrNull(index) ?: return) {
            KidsDayRow.LOCKED -> kidsDayLocked(s, s.window != null)
            KidsDayRow.FROM, KidsDayRow.TO -> kidsDayPicker(s, row)
            KidsDayRow.DONE -> kidsDayLeave(s)
        }
    }

    /** A touch on a day row's control: the Locked switch, or From / To's picker. */
    fun kidsDaySetting(event: NxSettingEvent) {
        val s = screen.value as? Screen.KidsDayEdit ?: return
        val rows = kidsDayRows(s)
        when (event) {
            is NxSettingEvent.Choose -> if (rows.getOrNull(event.row) == KidsDayRow.LOCKED) { host.select(event.row); kidsDayLocked(s, event.index == 1) }
            is NxSettingEvent.OpenPicker -> rows.getOrNull(event.row)?.takeIf { it == KidsDayRow.FROM || it == KidsDayRow.TO }?.let { host.select(event.row); kidsDayPicker(s, it) }
            is NxSettingEvent.Info -> Unit
        }
    }

    private fun kidsDayPicker(s: Screen.KidsDayEdit, row: KidsDayRow) {
        val w = s.window ?: return
        val from = row == KidsDayRow.FROM
        val times = kidsDayTimes(w, from)
        host.openPicker(if (from) "From" else "To", times.map(::kidsTimeLabel), times.indexOf(if (from) w.fromMin else w.toMin)) { i ->
            val now = screen.value as? Screen.KidsDayEdit ?: return@openPicker
            val cur = now.window ?: return@openPicker
            val t = times.getOrNull(i) ?: return@openPicker
            kidsDaySet(now, if (from) cur.copy(fromMin = t) else cur.copy(toMin = t), row)
        }
    }

    /** The pad on a day: UP / DOWN along the rows, LEFT / RIGHT half an hour on From / To (or flip Locked), A as a tap, B back. */
    fun onKidsDayButton(s: Screen.KidsDayEdit, button: NxButton) {
        val sel = selection.value
        val rows = kidsDayRows(s)
        when (button) {
            NxButton.UP, NxButton.DOWN -> selection.value = sel.copy(index = (sel.index + if (button == NxButton.UP) -1 else 1).mod(rows.size))
            NxButton.LEFT, NxButton.RIGHT -> when (val row = rows.getOrNull(sel.index)) {
                KidsDayRow.FROM, KidsDayRow.TO -> kidsDayStep(s, row, if (button == NxButton.LEFT) -1 else 1)
                KidsDayRow.LOCKED -> kidsDayLocked(s, s.window != null)
                else -> Unit
            }
            NxButton.A -> kidsDayRow(sel.index)
            NxButton.B -> host.back()
            else -> Unit
        }
    }

    /** Back to where the day was opened, on its row: setup's TIMES step or the Kids Mode page. */
    fun kidsDayLeave(s: Screen.KidsDayEdit) {
        if (!s.setup) return kidsShowPage(SettingsKind.KIDS_DAY, s.day)
        val times = Screen.KidsSetup(KidsSetupStep.TIMES, schedule = KidsSchedule.decode(settings.kidsSchedule))
        kidsShowSetup(times, kidsSetupRows(times).indexOfFirst { it is KidsSetupRow.Day && it.day == s.day })
    }

    /**
     * Back in front ([AppState.onForeground]): a permission screen a setup step or a warning row opened has closed ([KidsController.granting]
     * cleared); the step moves on once granted, and the Kids Mode page re-reads its warnings.
     */
    fun kidsReturned() {
        val k = kids ?: return
        val granting = k.granting
        k.returned()
        when (val s = screen.value) {
            is Screen.KidsSetup -> if (granting) {
                val granted = when (s.step) {
                    KidsSetupStep.USAGE -> k.hasUsageAccess()
                    KidsSetupStep.ALARMS -> k.canExactAlarms()
                    else -> false
                }
                if (granted) kidsSetupAfter(s.step)
            }
            is Screen.Settings -> if (s.section == SettingsSection.KIDS) host.refreshSettings()
            else -> Unit
        }
    }
}

/** Tools' first row while a parent has the full menu: Lock Kids Mode ([KidsScreensController.withKidsLock]). */
private val KIDS_LOCK_ROW = MainRow.Tool(ToolRow(ToolKind.KIDS_LOCK, checkNotNull(ToolKind.KIDS_LOCK.toolName), null))

/** Setup's line under the PIN dots when the second PIN was not the first. */
internal const val KIDS_PIN_MISMATCH = "PINs didn't match"

/** Setup's home question declined: Kids Mode stays off. */
internal const val KIDS_HOME_REFUSED = "Kids Mode is off: NX Redux isn't the home app"

/** The flag file's name, as Forgot PIN? names it where the home has no plain path. */
internal const val KIDS_FLAG_NAME = "kids-mode.flag"

/** Forgot PIN?'s line when Kids Mode is on but its flag file could not be written. */
internal const val KIDS_FLAG_FAILED = "Flag file couldn't be created — check All files access"

/** Setup's Apps step with none picked: its line, and the notice when Continue is refused. */
internal const val KIDS_PICK_ONE_APP = "Pick at least one app"
