package com.nxredux.mobile.ui

import com.nxredux.mobile.kids.KidsController
import com.nxredux.mobile.scraper.SignInResult
import com.nxredux.mobile.scraper.KeyResult
import com.nxredux.mobile.achievements.RaStatus
import com.nxredux.mobile.sync.DeviceSyncController
import com.nxredux.mobile.sync.DeviceSyncPage
import com.nxredux.mobile.sync.DeviceSyncText
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.BottomScreen
import com.nxredux.mobile.settings.ImportResult
import com.nxredux.mobile.settings.ImportSource
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxPadA
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.nxredux.mobile.settings.stepFrom
import com.nxredux.mobile.ui.nx.padA

/** Onboarding's layout pages: a layout row above one picture of it. */
internal val ONBOARDING_LAYOUT_STEPS = setOf(
    OnboardingStep.TAB_HOME, OnboardingStep.TAB_CONSOLES, OnboardingStep.TAB_COLLECTIONS, OnboardingStep.TAB_TOOLS, OnboardingStep.GAME_LISTS,
)
/** Onboarding's tab layout steps and the tab each sets. */
internal val ONBOARDING_TAB_STEPS = mapOf(
    OnboardingStep.TAB_HOME to MenuCategory.HOME, OnboardingStep.TAB_CONSOLES to MenuCategory.CONSOLES,
    OnboardingStep.TAB_COLLECTIONS to MenuCategory.COLLECTIONS, OnboardingStep.TAB_TOOLS to MenuCategory.TOOLS,
)
/** The artwork step's rows: the sources' accounts, then its two buttons. */
private const val ART_SCREENSCRAPER = "ScreenScraper"
private const val ART_SGDB_KEY = "SteamGridDB key"
private const val ART_TGDB_KEY = "TheGamesDB key"
private const val ART_FETCH_NOW = "Fetch now"
private const val ART_SKIP = "Skip"

/** Onboarding's value pages and the Settings value each row steps, top to bottom (Continue below them). */
private val ONBOARDING_VALUE_KINDS = mapOf(
    OnboardingStep.TEXT_SIZE to listOf(SettingsKind.TEXT_SIZE),
    OnboardingStep.SAVE_FORMATS to listOf(SettingsKind.SAVE_FORMAT, SettingsKind.STATE_FORMAT, SettingsKind.EXTRACTED_NAME),
    OnboardingStep.PAD to listOf(SettingsKind.PAD_SKIN, SettingsKind.PAD_THEME),
    OnboardingStep.TITLE to listOf(SettingsKind.PAGE_TITLE, SettingsKind.BUTTON_HINTS),
    OnboardingStep.ACCENT to listOf(SettingsKind.ACCENT, SettingsKind.SECONDARY_ACCENT),
)

/**
 * What onboarding's steps need from [AppState]: the folder steps' screens before them, Device Sync's page for the sync
 * step, the library as loaded, a save import, and the main menu to build and show when they end.
 */
internal interface OnboardingHost {
    fun select(index: Int)
    fun showNotice(text: String)
    /** First setup's home pick (or the step before it that still applies). */
    fun showPickFolder()
    fun showAddFirstExtra()
    fun showPermissions()
    /** The home pick is a step here (not the media home taken without asking). */
    fun homePickShown(): Boolean
    /** First setup's home page was shown: it counts in the step numbers. */
    fun homePageShown(): Boolean
    fun resetFolderSummaries()
    fun showDeviceSync(sync: DeviceSyncController)
    /** Device Sync's page state goes: it is not showing. */
    fun clearSyncSetup()
    /** ROMs arrived while Device Sync was open: they are scanned (the load then shows the step). False: none did. */
    fun rescanArrivedRoms(): Boolean
    fun rebuildHomeTab()
    suspend fun rebuildMainRows()
    fun showSystemsNow(category: MenuCategory)
    fun gameListLayout(): Pair<MenuStyle, MenuOrientation>
    fun gameListTurns(style: MenuStyle): Boolean
    fun systems(): List<SystemFolder>
    /** The artwork screens; null on a platform without artwork. */
    fun artwork(): ArtworkScreenController?
    fun importSaves(source: ImportSource, progress: (String) -> Unit, done: (ImportResult) -> Unit)
}

/**
 * Onboarding's steps as [Screen.Onboarding] pages: each step's title, lines and rows, a row's choice and a value row
 * stepped in place, the sign-in and artwork prompts, the way on and back, first setup's one step count across the
 * folder steps and these, and the run's flags. Where the flow is lives in [OnboardingController].
 */
internal class OnboardingScreensController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val deps: AppDeps,
    private val onboarding: OnboardingController,
    private val kids: KidsController?,
    private val loader: ScreenLoader,
    private val settingsContent: SettingsRows,
    private val settingsUi: SettingsController,
    private val kidsUi: KidsScreensController,
    private val host: OnboardingHost,
) {
    var allFilesStepShown = false   // this run showed the All files step: it counts in the step numbers
    var androidStepShown = false   // likewise the Android games step
    var addedExtraId: String? = null   // the ROMs folder first setup added: its summary step counts in the step numbers
    var welcomeDone = false   // the welcome was answered this run: a load without a home goes to the folder steps
    var mediaHomeTaken = false   // first setup went on with the media home (no All files access): no pick to wait for
    var onboardingSync = false   // Device Sync was opened from the sync offer: leaving it returns there
    private var onboardingSigningIn = false   // onboarding's RetroAchievements sign-in is running: its end moves the step on
    var onboardingSynced: String? = null   // the sync offer's last result ("done", "failed", "stopped"; "" left before one ended)
    private var onboardingImporting: String? = null   // the save import step's running import: its progress text
    private var onboardingImported: ImportResult? = null   // the save import step's last result, cleared when the step is left
    var firstSetup = false   // a first home pick is on its folder steps: the menu's dialog waits (the setup asks as a step)

    /**
     * A load landing while onboarding runs (a sync's rescan) or is still to do (first setup's folder steps just ended):
     * the flow's step shows instead of the menu, resuming at the saved step.
     */
    fun onboardingTakesOver(): Boolean {
        if (onboarding.step != null) return true
        kidsUi.kidsInOnboarding = false   // a setup from onboarding's Who's this for? never outlives the screen it was on
        if (deps.settings.onboardingDone != false) return false
        if (firstSetup) return false   // the folder steps' scans: their own page follows, onboarding starts after them
        onboarding.context = onboarding.context.copy(signedIn = deps.settings.raToken != null, kidsOffered = kids != null && deps.launcher != null)
        refreshPadContext()   // the customise pages a resume can land on depend on it
        onboarding.startMain(resume = deps.settings.onboardingStep?.let { n -> OnboardingStep.entries.firstOrNull { it.name == n } })
        deps.settings.onboardingStep = onboarding.step?.name
        return true
    }

    /** The current onboarding step's page, on [selected] (by default the row holding the current value, else the first). */
    fun showOnboarding(selected: Int = onboardingCurrentRow(), error: String? = null) {
        refreshPadContext()
        val step = onboarding.step ?: return
        val (title, details, rows) = onboardingPage(step, error)
        // The step count is the page's small title; the step's own question or name is its headline, its lines under it.
        // The sync step is Device Sync itself, in place under this page title.
        if (step == OnboardingStep.SYNC_OFFER) deps.deviceSync?.let { sync ->
            onboardingSync = true
            host.showDeviceSync(sync)
            return
        }
        val pageTitle = onboardingPageTitle()
        val canGoBack = step != OnboardingStep.WELCOME && !kidsUi.kidsOnAtDone()
        screen.value = Screen.Onboarding(
            step, pageTitle, listOf(title) + details, rows, accent = deps.settings.accent, padTheme = deps.settings.padTheme,
            canGoBack = canGoBack, shot = onboardingShot(step), touchRows = onboardingTouchRows(step, rows),
        )
        selection.value = ListSelection(selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0)), rows.size)
    }

    /**
     * Whether the main menu's pad skin shows now: Appearance → Pad skin, except during first setup and onboarding, which show it
     * on the PAD page only (its value applies at once there) and give every other page, the folder steps and their
     * pickers too, the full height. The menu after setup follows the choice.
     */
    fun menuPadShown(): Boolean {
        when (screen.value) {
            is Screen.PermissionsStep, Screen.PickFolder, Screen.AddFirstExtra, is Screen.AndroidGamesStep, is Screen.FolderSummary, is Screen.StorageUpgrade -> return false
            else -> Unit
        }
        // First setup between the welcome and HANDHELD (the folder steps and their pickers): not asked yet.
        val step = onboarding.step ?: return deps.settings.padSkin && deps.settings.onboardingDone != false
        // Setup shows it on its own page only (the choice, live); every other page keeps the full height.
        return step == OnboardingStep.PAD && deps.settings.padSkin
    }

    /**
     * The page title onboarding's current step shows: its place in first setup's one count ([setupCounter]): Text size,
     * System buttons with a controller, Bottom screen where asked, the folder steps, then the rest. "Welcome" on the welcome (before setup).
     */
    fun onboardingPageTitle(): String {
        val pre = setupPreSteps()
        return when (onboarding.step) {
            null, OnboardingStep.WELCOME -> "Welcome"
            OnboardingStep.TEXT_SIZE -> setupCounter(1)
            OnboardingStep.BOTTOM_SCREEN -> setupCounter(2)
            else -> setupCounter(onboarding.position().first + pre + setupFolderSteps())
        }
    }

    /**
     * First setup's steps before the folder steps: Text size, System buttons with a controller connected, and Bottom
     * screen on a device with a built-in second screen.
     */
    fun setupPreSteps(): Int =
        if (!inFirstSetup()) 0 else 1 + (if (deps.builtInSecondScreen()) 1 else 0)

    /**
     * "Setup · Step [at]/m": m the steps as the answers so far make them (Text size, Bottom screen where asked, the folder
     * steps, then the rest); a yes still to come (a handheld, customising) adds its pages and m grows.
     */
    fun setupCounter(at: Int): String {
        val main = onboarding.position().second.takeIf { it > 0 } ?: onboardingSteps(onboarding.context).size
        return "Setup · Step $at/${setupPreSteps() + setupFolderSteps() + main}"
    }

    /** The row a step opens on: the first (a layout page's layout row, a value page's first value). */
    private fun onboardingCurrentRow(): Int =
        // The artwork step opens on Fetch now (else Skip), past its accounts' rows.
        if (onboarding.step == OnboardingStep.ARTWORK) onboardingArtRows().size else 0

    /** The artwork step's account rows: ScreenScraper's sign-in, the SteamGridDB and TheGamesDB keys, those this build offers. */
    private fun onboardingArtRows(): List<NxRow> {
        val art = host.artwork() ?: return emptyList()
        return listOfNotNull(
            NxRow(ART_SCREENSCRAPER, secondary = art.ssUser ?: "Sign in").takeIf { art.ssOffered },
            NxRow(ART_SGDB_KEY, secondary = if (art.sgdbKeySet) "Set" else "Not set").takeIf { art.sgdbOffered },
            NxRow(ART_TGDB_KEY, secondary = if (art.tgdbKeySet) "Set" else "Not set").takeIf { art.tgdbOffered },
        )
    }

    /** The artwork step's prompt confirmed: the sign-in or key checked with its source (kept only when it answers OK), the step again on its row. */
    private fun submitOnboardingArt(s: Screen.Onboarding) {
        val art = host.artwork() ?: return
        val key = s.artKey
        val row = s.rows.indexOfFirst { it.label == if (key == null) ART_SCREENSCRAPER else if (key == ArtKeyFor.SGDB) ART_SGDB_KEY else ART_TGDB_KEY }
        if (key == null && (s.draftUser.isBlank() || s.draftPassword.isBlank())) { error.value = "Enter a username and password"; return }
        if (key != null && s.draftKey.isBlank()) { error.value = "Enter a ${key.label} API key"; return }
        screen.value = s.copy(artSignIn = false, artKey = null, draftPassword = "", draftKey = "")
        loader.start(message = if (key == null) "Signing in…" else "Checking key…", cancellable = false) {
            guardArtwork(error) {
                if (key == null) {
                    val r = art.signIn(s.draftUser.trim(), s.draftPassword)
                    if (r == SignInResult.OK) host.showNotice(signInText(r)) else error.value = signInText(r)
                } else {
                    val r = art.setKey(key, s.draftKey.trim())
                    if (r == KeyResult.OK) host.showNotice(keyResultText(r, key)) else error.value = keyResultText(r, key)
                }
            }
            if ((screen.value as? Screen.Onboarding)?.step == OnboardingStep.ARTWORK) showOnboarding(row.coerceAtLeast(0))
        }
    }

    fun onboardingDraftKey(text: String) { (screen.value as? Screen.Onboarding)?.takeIf { it.artKey != null }?.let { screen.value = it.copy(draftKey = text) } }

    /**
     * [rows] of a step as Settings' touch rows: a value row as its Settings row (name, one-line description, control; no
     * fuller text, as setup has no info popup), a choice as a plain row. WELCOME draws its own page: null.
     */
    private fun onboardingTouchRows(step: OnboardingStep, rows: List<NxRow>): List<NxSettingRow>? {
        if (step == OnboardingStep.WELCOME) return null
        val kinds = onboardingValueKinds(step)
        return rows.mapIndexed { i, row ->
            val kind = kinds.getOrNull(i)?.takeIf { row.cycleable }
            val value = kind?.let { settingsContent.settingValue(SettingsRow(it, "", category = onboardingKindCategory(step, it))) }
            val labels = value?.labels
            if (kind == null || labels == null) NxSettingRow(row.label, value = row.secondary, enabled = row.enabled)   // a status (System buttons' Found) on the right
            else settingsTouchRow(SettingsRow(kind, row.label, row.secondary, category = onboardingKindCategory(step, kind)), SettingChoices(labels, value.index))
                .copy(detail = "")
        }
    }

    /** The tab a value row of [step] belongs to: the step's own, or the Games tab for the Game lists step's Games tab row. */
    private fun onboardingKindCategory(step: OnboardingStep?, kind: SettingsKind): MenuCategory? =
        if (kind == SettingsKind.TAB_SHOWN) MenuCategory.GAMES else ONBOARDING_TAB_STEPS[step]

    /** The Settings value each of [step]'s rows steps, top to bottom: a value page's, or a layout page's ([onboardingLayoutKinds]). */
    private fun onboardingValueKinds(step: OnboardingStep): List<SettingsKind> =
        if (step in ONBOARDING_LAYOUT_STEPS) onboardingLayoutKinds(step) else ONBOARDING_VALUE_KINDS[step].orEmpty()

    /**
     * A touch on an onboarding page's row ([Screen.Onboarding.touchRows]): a segment or a swatch sets that value in place
     * (a layout through [stepOnboardingLayout], resetting its options), a picker value opens its picker.
     */
    fun onboardingSettingEvent(event: NxSettingEvent) {
        val s = screen.value as? Screen.Onboarding ?: return
        val row = when (event) {
            is NxSettingEvent.Choose -> event.row
            is NxSettingEvent.OpenPicker -> event.row
            is NxSettingEvent.Info -> return
        }
        val kind = onboardingValueKinds(s.step).getOrNull(row)?.takeIf { s.rows.getOrNull(row)?.cycleable == true } ?: return
        val value = settingsContent.settingValue(SettingsRow(kind, "", category = onboardingKindCategory(s.step, kind))) ?: return
        val choose = { i: Int ->
            when {
                kind == SettingsKind.MENU_STYLE || kind == SettingsKind.GAME_LIST_STYLE -> if (i != value.index) stepOnboardingLayout(i - value.index) else host.select(row)
                value.setTo(i) -> showOnboarding(row)
                else -> host.select(row)
            }
        }
        when (event) {
            is NxSettingEvent.Choose -> choose(event.index)
            else -> { host.select(row); settingsUi.openPicker(s.rows[row].label, value.labels.orEmpty(), value.index, choose) }
        }
    }

    /**
     * A value page's row [row] ([ONBOARDING_VALUE_KINDS]) stepped by [by] in place: the same settings Settings steps
     * ([SettingsRows.settingValue]), applied at once and the page republished on that row ([App] reads them per screen). A row past
     * the page's values, or another [step], does nothing.
     */
    private fun stepOnboardingValue(step: OnboardingStep?, row: Int, by: Int) {
        val kind = ONBOARDING_VALUE_KINDS[step]?.getOrNull(row) ?: return
        settingsContent.settingValue(SettingsRow(kind, ""))?.step(by)   // a bare row: none of these needs a tab, console or hint
        showOnboarding(row)
    }

    private var padPortrait = true
    private var padController = false

    /** The device as App sees it: portrait or not, a controller or not; onboarding's customise pages follow it. */
    fun onPadContext(portrait: Boolean, controller: Boolean) {
        if (portrait == padPortrait && controller == padController) return
        padPortrait = portrait
        padController = controller
        // The page republishes for its step count; a sign-in prompt keeps its typing (the count catches up on the next page).
        val s = screen.value as? Screen.Onboarding ?: return
        if (onboarding.step != null && !s.prompt) showOnboarding(selection.value.index)
    }

    /**
     * Which customise pages apply here: the pad is a choice on a phone without a controller (built in, connected or
     * clip-on: the menu shows no skin with any, [com.nxredux.mobile.ui.nx.menuSkinShown]), in either orientation, as it
     * shows when the phone is held upright; title and hints hide only where buttons exist.
     */
    fun refreshPadContext() {
        val padPage = !padController
        onboarding.context = onboarding.context.copy(padPage = padPage, buttons = padController || (padPage && deps.settings.padSkin))
    }

    /** A step's title, details and rows. */
    private fun onboardingPage(step: OnboardingStep, error: String?): Triple<String, List<String>, List<NxRow>> = when (step) {
        OnboardingStep.WELCOME -> Triple("NX Redux", listOf("Ready when you are."), listOf(NxRow("Get started")))
        OnboardingStep.BOTTOM_SCREEN -> Triple(
            "Which is the bottom screen?",
            listOf(
                "Tap the button on the bottom screen. It becomes the touch screen, and games show their picture on the other.",
                "Change it later in Settings → Second screen, or for one console in its settings.",
            ),
            listOf(NxRow("This is the bottom screen"), NxRow("Skip")),
        )
        OnboardingStep.HANDHELD -> Triple(
            "Do you also play on an NX Redux handheld?", listOf("Your games, BIOS, saves, save states and collections can then carry over between the two."),
            listOf(NxRow("Yes, I have one"), NxRow("No")),
        )
        OnboardingStep.SAVE_FORMATS -> Triple(
            "Match your handheld", listOf("Set these as on your handheld's Settings → System, so each loads the other's saves."),
            listOf(
                NxRow("Save format", secondary = deps.settings.saveFormat.label, cycleable = true),
                NxRow("Save state format", secondary = deps.settings.stateFormat.label, cycleable = true),
                NxRow("Use extracted file name", secondary = onOff(deps.settings.useExtractedFileName), cycleable = true),
                NxRow("Continue"),
            ),
        )
        OnboardingStep.SYNC_OFFER -> Triple(
            "Sync with your handheld",
            listOf("Copies games, BIOS, saves, save states and collections both ways. Open Device Sync on the handheld too."),
            listOf(NxRow("Sync now"), NxRow("Later")),
        )
        // The three imports, then (in place) a running one's progress with the rows waiting, or its summary and skipped files.
        OnboardingStep.IMPORT_SAVES -> when (val r = onboardingImported) {
            is ImportResult.Done -> Triple(
                "Bring your saves", r.summary.lines() + r.summary.statesNote(), r.summary.skipped.map { NxRow(it.text(), enabled = false) } + NxRow("Continue"),   // a skipped file: a line to read, never an action
            )
            else -> Triple(
                "Bring your saves",
                listOf(onboardingImporting ?: (r as? ImportResult.Message)?.text ?: "Copy battery saves and save states from another app. Nothing is moved or deleted."),
                listOf("From a folder", "From a zip file", "From RetroArch", "Skip").map { NxRow(it, enabled = onboardingImporting == null) },
            )
        }
        // The sources' accounts as rows (each opens its prompt), then Fetch now and Skip; Skip alone with nothing to fetch from.
        OnboardingStep.ARTWORK -> Triple(
            "Fetch artwork",
            listOfNotNull(
                if (host.artwork()?.available == true) "Box art and screenshots for your ${gamesLabel(onboardingGameCount())}, fetched in the background."
                else "Sign in or add a key to fetch box art and screenshots for your ${gamesLabel(onboardingGameCount())}; or later in Tools → Artwork.",
                // Not signed in, ScreenScraper's anonymous quota still fetches, more slowly: signing in is optional.
                "Signing in is optional: without an account art still downloads, just more slowly. You can sign in or add a key later in Tools → Artwork."
                    .takeIf { host.artwork()?.let { it.ssOffered && it.ssUser == null } == true },
            ),
            onboardingArtRows() + listOfNotNull(NxRow(ART_FETCH_NOW).takeIf { host.artwork()?.available == true }, NxRow(ART_SKIP)),
        )
        OnboardingStep.RA_SIGN_IN -> Triple(
            "RetroAchievements",
            listOfNotNull("Sign in to earn achievements and see your progress.", if (onboardingSigningIn) "Signing in…" else error),
            listOf(NxRow("Sign in"), NxRow("Skip")),
        )
        OnboardingStep.RA_DOWNLOAD -> Triple(
            "Achievement data", listOf("Achievement lists and badges for your ${gamesLabel(onboardingGameCount())}, downloaded in the background. Its progress shows as a notification."),
            listOf(NxRow("Download now"), NxRow("Skip")),
        )
        OnboardingStep.CUSTOMISE -> Triple(
            "Customise the layout?", listOf("Pick the menu's look page by page, or keep the defaults. Each can change later in Settings → Layouts."),
            listOf(NxRow("Yes, customise"), NxRow("Use the defaults")),
        )
        // Asked first, after the welcome: the lists', titles' and hints' text (setup's own pages too), a value row that flips
        // in place (the page reads at the new size), then Continue.
        OnboardingStep.TEXT_SIZE -> Triple(
            "Text size", listOf("How big the text reads in setup and the app. Change it later in Settings → Appearance."),
            listOf(NxRow("Size", secondary = deps.settings.optionTextSize.label, cycleable = true), NxRow("Continue")),   // the title says Text size
        )
        // The pad and its theme (only while the pad shows): value rows that flip in place (the real pad follows, to
        // compare), then Continue.
        OnboardingStep.PAD -> Triple(
            "On-screen pad in the menu",
            // Landscape shows no menu pad: the choice still applies, seen once the phone is upright. Nor does a controller
            // (one connected on this page, or the step resumed with one): seen once it is disconnected.
            listOfNotNull(
                "Show the pad below the menu in portrait, and how it looks. Games keep their own pad, and tools always hide it, so their pages keep the whole screen.",
                if (padPortrait) null else "Turn your phone upright to see the pad.",
                if (padController) "Disconnect your controller to see the pad." else null,
            ),
            listOfNotNull(
                NxRow("Pad skin", secondary = onOff(deps.settings.padSkin), cycleable = true),
                if (deps.settings.padSkin) NxRow("Pad theme", secondary = settingsContent.padThemeLabel(deps.settings.padTheme), cycleable = true) else null,
                NxRow("Continue"),
            ),
        )
        // The page title and the button hints: value rows that flip in place (the picture above shows the pair), then Continue.
        OnboardingStep.TITLE -> Triple(
            "Page title and button hints", listOf("The tab row at the top of the menu (L1 / R1 switch tabs either way) and the hint bar at the bottom."),
            listOf(
                NxRow("Page title", secondary = showHide(deps.settings.menuPageTitle), cycleable = true),
                NxRow("Button hints", secondary = showHide(deps.settings.menuButtonHints), cycleable = true),
                NxRow("Continue"),
            ),
        )
        // The layout and the options its picture shows as value rows (they step in place, applied at once, the picture
        // under them following), then Continue.
        OnboardingStep.TAB_HOME, OnboardingStep.TAB_CONSOLES, OnboardingStep.TAB_COLLECTIONS, OnboardingStep.TAB_TOOLS -> Triple(
            "${ONBOARDING_TAB_STEPS.getValue(step).label} layout",
            listOf(
                if (step == OnboardingStep.TAB_HOME) "Home is where your last played game waits, next to the games and tools you pin, with a few suggestions of what to play next."
                else "How the ${ONBOARDING_TAB_STEPS.getValue(step).label} tab lays out.",
            ),
            onboardingLayoutRows(step),
        )
        OnboardingStep.GAME_LISTS -> Triple(
            "Game lists layout",
            listOf("How a console's or a collection's games lay out.", "A tab that lists every game from every console."),
            onboardingLayoutRows(step),
        )
        // The accent and the secondary accent: value rows that step in place (the page recolours), then Continue.
        OnboardingStep.ACCENT -> Triple(
            "Pick your accents", listOf("The accent colours the selection everywhere; the secondary accent, the lit details beside it."),
            listOf(
                NxRow("Accent", secondary = deps.settings.accent.label, cycleable = true),
                NxRow("Secondary accent", secondary = deps.settings.secondaryAccent.label, cycleable = true),
                NxRow("Continue"),
            ),
        )
        // Who's this for: My kids runs Kids Mode's setup here, then Done ([KidsScreensController.kidsStartInOnboarding]); either way the line says where it is.
        OnboardingStep.KIDS -> Triple(
            "Who's this for?", listOf(KIDS_ONBOARDING_LINE),
            listOf(NxRow("Just me"), NxRow("My kids")),
        )
        OnboardingStep.DONE -> Triple("Ready, player one", listOf(
            "Your library is set up and the cartridges are blown clean.",
            "Every feature is explained at nxredux.com.",
        ), listOf(NxRow("Press Start")))
    }

    /** A or a tap on onboarding row [index]: that step's choice. */
    fun onboardingRow(index: Int) {
        val s = screen.value as? Screen.Onboarding ?: return
        when (s.step) {
            OnboardingStep.WELCOME -> { onboarding.startTextSize(); showOnboarding() }
            OnboardingStep.TEXT_SIZE -> if (index < s.rows.lastIndex) stepOnboardingValue(onboarding.step, index, 1)
                else afterTextSize()
            OnboardingStep.BOTTOM_SCREEN -> {
                if (index == 0) deps.settings.bottomScreen = BottomScreen.MAIN   // Skip keeps Auto
                startFolderSteps()
            }
            OnboardingStep.HANDHELD -> {
                onboarding.context = onboarding.context.copy(handheld = index == 0)
                onboardingNext()
            }
            OnboardingStep.SAVE_FORMATS -> if (index < 3) stepOnboardingValue(OnboardingStep.SAVE_FORMATS, index, 1) else onboardingNext()
            OnboardingStep.IMPORT_SAVES -> when {
                onboardingImporting != null -> Unit   // the rows wait for the running import
                onboardingImported is ImportResult.Done -> if (index == s.rows.lastIndex) onboardingNext()   // a skipped file: inert
                index < 3 -> onboardingImport(listOf(ImportSource.FOLDER, ImportSource.ZIP, ImportSource.RETROARCH)[index])
                else -> onboardingNext()
            }
            OnboardingStep.CUSTOMISE -> { onboarding.context = onboarding.context.copy(customise = index == 0); onboardingNext() }
            OnboardingStep.KIDS -> if (index == 1) kidsUi.kidsStartInOnboarding() else onboardingNext()
            OnboardingStep.PAD, OnboardingStep.TITLE, OnboardingStep.ACCENT -> if (index < s.rows.lastIndex) stepOnboardingValue(onboarding.step, index, 1) else onboardingNext()
            OnboardingStep.TAB_HOME, OnboardingStep.TAB_CONSOLES, OnboardingStep.TAB_COLLECTIONS, OnboardingStep.TAB_TOOLS,
            OnboardingStep.GAME_LISTS -> if (index < s.rows.lastIndex) stepOnboardingLayoutRow(index, 1) else onboardingNext()
            OnboardingStep.ARTWORK -> when (s.rows.getOrNull(index)?.label) {
                ART_SCREENSCRAPER -> screen.value = s.copy(artSignIn = true, draftUser = deps.settings.ssUsername, draftPassword = "")
                ART_SGDB_KEY -> screen.value = s.copy(artKey = ArtKeyFor.SGDB, draftKey = "")
                ART_TGDB_KEY -> screen.value = s.copy(artKey = ArtKeyFor.TGDB, draftKey = "")
                ART_FETCH_NOW -> { host.artwork()?.fetchInBackground(); onboardingNext() }
                else -> onboardingNext()   // Skip
            }
            OnboardingStep.RA_SIGN_IN -> when {
                s.prompt -> submitOnboardingSignIn(s)
                index == 0 && deps.achievements != null && !onboardingSigningIn ->
                    screen.value = s.copy(prompt = true, draftUser = deps.settings.raUsername, draftPassword = "")
                index == 0 -> Unit   // a sign-in is still running
                else -> onboardingNext()
            }
            OnboardingStep.RA_DOWNLOAD -> { if (index == 0) deps.achievements?.prefetchAll(); onboardingNext() }
            else -> onboardingNext()
        }
    }

    /** The layout a layout page sets, as it is now. */
    private fun onboardingLayout(step: OnboardingStep): MenuStyle =
        ONBOARDING_TAB_STEPS[step]?.let { MenuStyles.styleOf(deps.settings.menuStyles, it) } ?: host.gameListLayout().first

    /**
     * A layout page's value rows, the options its picture shows: the layout, then Home's stats, a tab Carousel's
     * orientation, or a game list Carousel's or Backdrop's orientation and, standing on end, its alignment.
     */
    private fun onboardingLayoutKinds(step: OnboardingStep): List<SettingsKind> {
        val s = deps.settings
        val category = ONBOARDING_TAB_STEPS[step]
        return listOfNotNull(
            if (category == null) SettingsKind.GAME_LIST_STYLE else SettingsKind.MENU_STYLE,
            SettingsKind.HOME_STATS.takeIf { category == MenuCategory.HOME },
            SettingsKind.MENU_ORIENTATION.takeIf { category != null && category != MenuCategory.HOME && MenuStyles.styleOf(s.menuStyles, category) == MenuStyle.CAROUSEL },
            SettingsKind.GAME_LIST_ORIENTATION.takeIf { category == null && host.gameListTurns(host.gameListLayout().first) },
            SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT.takeIf { category == null && settingsContent.gameListsShot().vertical },
            SettingsKind.TAB_SHOWN.takeIf { category == null },   // the Games tab, on the Game lists step
        )
    }

    /** A layout page's rows: its value rows ([onboardingLayoutKinds]), named without the tab (the page's title says it), then Continue. */
    private fun onboardingLayoutRows(step: OnboardingStep): List<NxRow> {
        val s = deps.settings
        val category = ONBOARDING_TAB_STEPS[step]
        return onboardingLayoutKinds(step).map { kind ->
            when (kind) {
                SettingsKind.HOME_STATS -> NxRow("Stats", secondary = showHide(s.menuHomeStats), cycleable = true)
                SettingsKind.MENU_ORIENTATION -> NxRow("Orientation", secondary = orientationLabel(MenuStyles.orientationOf(s.menuOrientations, category!!)), cycleable = true)
                SettingsKind.GAME_LIST_ORIENTATION -> NxRow("Orientation", secondary = orientationLabel(s.gameListOrientation), cycleable = true)
                SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT -> NxRow("Alignment", secondary = verticalAlignmentLabel(s.gameListVerticalAlignment), cycleable = true)
                SettingsKind.TAB_SHOWN -> NxRow("Games tab", secondary = showHide(s.showGamesTab), cycleable = true)
                else -> NxRow("Layout", secondary = menuStyleLabel(onboardingLayout(step)), cycleable = true)
            }
        } + NxRow("Continue")
    }

    /** A layout page's picture: the tab or the game lists as their rows set them; null on the other steps. */
    private fun onboardingShot(step: OnboardingStep): LayoutShot? {
        if (step == OnboardingStep.GAME_LISTS) return settingsContent.gameListsShot()
        val c = ONBOARDING_TAB_STEPS[step] ?: return null
        return settingsContent.tabShot(c)
    }

    /**
     * A layout page's value row [row] stepped by [by]: the layout ([stepOnboardingLayout]), or the option on that row in
     * place, the same setting Settings steps ([SettingsRows.settingValue], through a bare row carrying the page's tab), so the
     * picture follows.
     */
    private fun stepOnboardingLayoutRow(row: Int, by: Int) {
        val step = onboarding.step ?: return
        when (val kind = onboardingLayoutKinds(step).getOrNull(row) ?: return) {
            // The layout is onboarding's own: it resets the orientation (and alignment) with it, from its own list.
            SettingsKind.MENU_STYLE, SettingsKind.GAME_LIST_STYLE -> return stepOnboardingLayout(by)
            else -> settingsContent.settingValue(SettingsRow(kind, "", category = onboardingKindCategory(step, kind)))?.step(by)
        }
        showOnboarding(row)
    }

    /** A layout page's layout stepped by [by] (-1: the one before): applied at once (its orientation reset), the page on its row. */
    private fun stepOnboardingLayout(by: Int) {
        val step = onboarding.step ?: return
        val category = ONBOARDING_TAB_STEPS[step]
        if (category == null) {
            deps.settings.gameListStyle = MenuStyles.GAME_LIST_STYLES.stepFrom(host.gameListLayout().first, by)
            deps.settings.gameListOrientation = MenuStyles.DEFAULT_ORIENTATION
            deps.settings.gameListVerticalAlignment = VerticalListAlignment.DEFAULT
        } else {
            deps.settings.menuStyles = deps.settings.menuStyles + (category to MenuStyles.cycleMainMenu(MenuStyles.styleOf(deps.settings.menuStyles, category), by))
            deps.settings.menuOrientations = deps.settings.menuOrientations - category
            if (category == MenuCategory.HOME) host.rebuildHomeTab()
        }
        showOnboarding(0)
    }

    /** The library's game count, for the artwork and achievement steps' lines. */
    private fun onboardingGameCount(): Int = host.systems().sumOf { it.gameCount }

    /** The sign-in prompt's typing (as the RetroAchievements settings' prompt). */
    fun onboardingDraftUser(text: String) { (screen.value as? Screen.Onboarding)?.takeIf { it.prompt || it.artSignIn }?.let { screen.value = it.copy(draftUser = text) } }
    fun onboardingDraftPassword(text: String) { (screen.value as? Screen.Onboarding)?.takeIf { it.prompt || it.artSignIn }?.let { screen.value = it.copy(draftPassword = text) } }

    /** The prompt's confirm: both filled, the sign-in runs ([onRaIdleOnboarding] takes its end); else it stays. */
    private fun submitOnboardingSignIn(s: Screen.Onboarding) {
        val c = deps.achievements ?: return
        if (s.draftUser.isBlank() || s.draftPassword.isBlank() || c.busy) return
        onboardingSigningIn = true
        showOnboarding(0)
        c.login(s.draftUser.trim(), s.draftPassword)
    }

    /** A sign-in from onboarding ended: signed in, on to the download offer; else the step again with the error. */
    fun onRaIdleOnboarding(status: RaStatus) {
        if (!onboardingSigningIn) return
        onboardingSigningIn = false
        // Judged by the sign-in's own result: a token kept from before stays through a failed one.
        val signedIn = status.lastError == null && deps.settings.raToken != null
        // Even when the user skipped on meanwhile: the download offer becomes a step (behind them, B reaches it).
        if (signedIn) onboarding.context = onboarding.context.copy(signedIn = true)
        if (onboarding.step != OnboardingStep.RA_SIGN_IN) return
        if (signedIn) onboardingNext() else showOnboarding(0, error = status.lastError ?: "Sign-in failed")
    }

    /** On to the next step, saved for a resume; past DONE onboarding ends ([finishOnboarding]). */
    fun onboardingNext() {
        refreshPadContext()
        onboardingImported = null
        val next = onboarding.next() ?: return finishOnboarding()
        deps.settings.onboardingStep = next.name
        showOnboarding()
    }

    /**
     * The Bottom screen step's button on the second screen was tapped: the second screen is the bottom one. Only while the
     * step shows.
     */
    fun onboardingBottomScreenOnSecond() {
        if (onboarding.step != OnboardingStep.BOTTOM_SCREEN || (screen.value as? Screen.Onboarding)?.step != OnboardingStep.BOTTOM_SCREEN) return
        deps.settings.bottomScreen = BottomScreen.SECOND
        startFolderSteps()
    }

    /** The welcome pages are answered: on to first setup's folder steps. */
    private fun startFolderSteps() {
        welcomeDone = true
        onboarding.finish()
        host.showPickFolder()
    }

    /** B: the step before; WELCOME stays, the first post-folder step goes back to the ROMs folder step. */
    fun onboardingBack() {
        if (kidsUi.kidsOnAtDone()) return   // Kids Mode on: the earlier steps (Device Sync, the folders) are a parent's, Press Start alone goes on
        // A first run's first step after the folders: back to the ROMs folder step (going on again comes back here, the saved step kept).
        if (onboarding.isFirstMain()) { onboarding.finish(); return host.showAddFirstExtra() }
        val step = onboarding.back() ?: return
        onboardingImported = null
        deps.settings.onboardingStep = step.takeIf { it > OnboardingStep.BOTTOM_SCREEN }?.name   // the steps before the folders are never resumed
        showOnboarding()
    }

    /** B on first setup's home pick: the All files step while access is declared and not granted, else the text size question. */
    fun backFromHomePick() {
        val access = deps.allFilesAccess
        if (access != null && access.declared && !access.granted()) {
            host.showPermissions()
        } else backToWelcome()
    }

    /** First setup's first folder step (All files access or the home pick), back: the bottom screen question where asked, else the text size question. */
    fun backToWelcome() {
        welcomeDone = false
        when {
            deps.builtInSecondScreen() -> onboarding.startBottomScreen()
            else -> onboarding.startTextSize()
        }
        showOnboarding()
    }

    /** After the text size: the bottom screen question where there is one, else the folder steps. */
    private fun afterTextSize() {
        if (deps.builtInSecondScreen()) { onboarding.startBottomScreen(); showOnboarding() } else startFolderSteps()
    }

    /** First setup is on: the welcome was not finished by an earlier run. */
    fun inFirstSetup(): Boolean = deps.settings.onboardingDone == false

    /** How many folder steps first setup has before HANDHELD: All files (where shown or still to ask), home, ROMs, Android games (once shown). */
    private fun setupFolderSteps(): Int {
        if (!inFirstSetup()) return 0
        return allFilesSteps() + 1 + homePickSteps() + (if (host.homePageShown()) 1 else 0) + (if (addedExtraId != null) 1 else 0) + (if (androidStepShown) 1 else 0)
    }

    /** The All files step in the count: 1 where it is shown or still to ask. */
    private fun allFilesSteps(): Int {
        val access = deps.allFilesAccess
        val counts = allFilesStepShown || (access != null && access.declared && !access.granted() && !deps.settings.allFilesAsked)
        return if (counts) 1 else 0
    }

    /** The home pick in the count: none on the media home. */
    private fun homePickSteps(): Int = if (mediaHomeTaken || !host.homePickShown()) 0 else 1

    /**
     * A folder step's page title and whether B goes back from it: "Setup · Step n/m" (and back) in first setup; after
     * it (a lost home's pick) just the page's name, and B has nowhere to go.
     */
    fun setupChrome(): Pair<String, Boolean> {
        val screen = screen.value
        if (!inFirstSetup()) return (if (screen is Screen.AddFirstExtra) "ROM folders" else "Home folder") to (screen is Screen.AddFirstExtra)
        val home = if (host.homePageShown()) 1 else 0   // the home page, between the pick and the ROMs folder offer
        val pick = homePickSteps()
        val at = setupPreSteps() + allFilesSteps() + when (screen) {
            is Screen.PermissionsStep -> 0
            Screen.PickFolder -> 1
            Screen.AddFirstExtra -> 1 + pick + home
            is Screen.FolderSummary -> if (screen.extraId == Recents.HOME) 1 + pick else 2 + pick + home
            else -> 2 + pick + home + (if (addedExtraId != null) 1 else 0)
        }
        return setupCounter(at) to true
    }

    /** Every onboarding flag back to a run's start (a new run, or onboarding ended or left), so none leaks into normal use. */
    fun resetOnboardingRun() {
        onboardingSync = false
        host.clearSyncSetup()
        onboardingSynced = null
        onboardingSigningIn = false
        allFilesStepShown = false
        androidStepShown = false
        addedExtraId = null
        host.resetFolderSummaries()
    }

    /** Onboarding done: the menu on Home, its tabs rebuilt for the layout and options picked. */
    fun finishOnboarding() {
        kidsUi.kidsInOnboarding = false
        onboarding.finish()
        resetOnboardingRun()
        deps.settings.onboardingDone = true
        deps.settings.onboardingStep = null
        // A new install has nothing to upgrade, unless data waits to move. Before any load can ask the gate (a new install's
        // private root is a few files to walk).
        deps.storageUpgrade?.markIfEmpty()
        scope.launch {
            host.rebuildMainRows()
            host.showSystemsNow(MenuCategory.HOME)
        }
    }

    /** A pad press on an onboarding step: UP / DOWN move (wrapping), A chooses, B goes back. */
    fun onOnboardingButton(s: Screen.Onboarding, button: NxButton) {
        if (s.artSignIn || s.artKey != null) {   // the artwork step's prompt: A checks it, B closes it
            when (button) {
                NxButton.A -> submitOnboardingArt(s)
                NxButton.B -> screen.value = s.copy(artSignIn = false, artKey = null, draftPassword = "", draftKey = "")
                else -> Unit
            }
            return
        }
        if (s.prompt) {   // the sign-in prompt: A signs in, B closes it
            when (button) {
                NxButton.A -> submitOnboardingSignIn(s)
                NxButton.B -> screen.value = s.copy(prompt = false, draftPassword = "")
                else -> Unit
            }
            return
        }
        // The last page: START goes as Press Start does.
        if (s.step == OnboardingStep.DONE && button == NxButton.START) return onboardingRow(0)
        // A value row changes as Settings' do: A first, then LEFT / RIGHT, until A or B ([SettingsController.onRowEditButton]).
        val heard = settingsUi.onRowEditButton(s, button) ?: return
        when (heard) {
            NxButton.UP -> selection.value = selection.value.up(wrap = true)
            NxButton.DOWN -> selection.value = selection.value.down(wrap = true)
            NxButton.LEFT, NxButton.RIGHT -> when (s.step) {
                OnboardingStep.SAVE_FORMATS -> stepOnboardingValue(OnboardingStep.SAVE_FORMATS, selection.value.index, if (heard == NxButton.LEFT) -1 else 1)
                OnboardingStep.PAD, OnboardingStep.TITLE, OnboardingStep.ACCENT, OnboardingStep.TEXT_SIZE -> if (selection.value.index < s.rows.lastIndex) stepOnboardingValue(onboarding.step, selection.value.index, if (heard == NxButton.LEFT) -1 else 1)
                in ONBOARDING_LAYOUT_STEPS -> if (selection.value.index < s.rows.lastIndex) stepOnboardingLayoutRow(selection.value.index, if (heard == NxButton.LEFT) -1 else 1)
                else -> Unit
            }
            // A picker's A opens it, as a tap on its value does; a switch's flips (the row's own A).
            NxButton.A -> if (s.touchRows?.getOrNull(selection.value.index)?.padA == NxPadA.PICK) onboardingSettingEvent(NxSettingEvent.OpenPicker(selection.value.index))
                else onboardingRow(selection.value.index)
            NxButton.B -> onboardingBack()
            else -> Unit
        }
    }

    /**
     * Onboarding's import from [source]: its progress and then its result show on the step itself (re-drawn), never a
     * Settings page or sticky notice. Ended after the step was left, the result is a notice.
     */
    private fun onboardingImport(source: ImportSource) {
        fun onStep() = onboarding.step == OnboardingStep.IMPORT_SAVES && (screen.value as? Screen.Onboarding)?.step == OnboardingStep.IMPORT_SAVES
        onboardingImported = null
        host.importSaves(source, progress = { text ->
            onboardingImporting = text
            if (onStep()) showOnboarding(selection.value.index)
        }) { result ->
            onboardingImporting = null
            when {
                onStep() -> {
                    onboardingImported = result
                    // A summary lands on Continue; a message keeps the row picked.
                    showOnboarding(if (result is ImportResult.Done) result.summary.skipped.size else selection.value.index)
                }
                result is ImportResult.Done -> host.showNotice(result.summary.lines().joinToString("; "))
                result is ImportResult.Message -> host.showNotice(result.text)
            }
        }
    }

    /**
     * Leaves setup's sync step: on to the next step ([forward]) or back to the one before. ROMs that arrived are scanned
     * first; the load then shows the step onboarding is on.
     */
    fun leaveOnboardingSync(forward: Boolean) {
        onboardingSync = false
        host.clearSyncSetup()
        if (forward) {
            val next = onboarding.next() ?: return finishOnboarding()
            deps.settings.onboardingStep = next.name
        } else {
            onboarding.back()?.let { deps.settings.onboardingStep = it.name }
        }
        if (!host.rescanArrivedRoms()) showOnboarding()
    }

    /** The sync step's result from the page Device Sync shows: an ended sync's outcome. */
    fun noteOnboardingSync(page: DeviceSyncPage?) {
        onboardingSynced = when (page) {
            is DeviceSyncPage.Done -> "done"
            is DeviceSyncPage.Failed -> if (page.reason == DeviceSyncText.STOPPED) "stopped" else "failed"
            else -> onboardingSynced
        }
    }

}
