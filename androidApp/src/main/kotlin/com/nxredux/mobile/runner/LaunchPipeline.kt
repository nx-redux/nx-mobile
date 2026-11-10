package com.nxredux.mobile.runner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.DebugLogFile
import com.nxredux.mobile.R
import com.nxredux.mobile.cheats.CheatSet
import com.nxredux.mobile.cheats.DolphinCheats
import com.nxredux.mobile.gametime.GameTimeRecorder
import com.nxredux.mobile.gametime.GameTimeService
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.library.ArcadeCompanions
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayLinkOptions
import com.nxredux.mobile.netplay.NetplayPads
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplaySessionDir
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.cablelink.PairSaveCheckpoint
import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.GraphicsApiOptions
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.OverlayResolver
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.options.Ps2Options
import com.nxredux.mobile.options.ShaderOptions
import com.nxredux.mobile.options.VulkanFallback
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_COMPANION_NAMES
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_COMPANION_URIS
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_CONTENT_URI
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_CORE_FILE
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_CORE_ID
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_FILE_NAME
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_FOLDER_URI
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_FORCE_GLES
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_GAME_PATH
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_LOAD_MODE
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_NETPLAY_ROLE
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_OPTIONS
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_SYSTEM_SETS
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.GameRumble
import com.nxredux.mobile.settings.Prefs
import com.nxredux.mobile.shortcuts.Shortcuts
import com.nxredux.mobile.skin.GlassScreen
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.AndroidRefs
import com.nxredux.mobile.storage.AndroidRomsSources
import com.nxredux.mobile.storage.DataRoot
import com.nxredux.mobile.storage.NdsDsvSave
import com.nxredux.mobile.storage.NdsResumeCleanup
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.test.NxTestResults
import com.nxredux.mobile.tools.CheatsRoot
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.VirtualFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What [LaunchPipeline] needs from [GameActivity]: its controllers, what the launch reads, and the state the launch
 * settles. Built by the activity for the pipeline alone, so these setters stay out of every other class's reach. Each
 * stage sets them in place, not at its end: later reads in the same stage, and a menu opened mid-load, rely on them.
 */
internal interface LaunchHost : GameScreenHost {
    val input: GameInputRouter
    val padLayout: PadLayoutController
    val renderer: FrontendRenderer
    val dual: DualScreenController
    val shortcuts: ShortcutsMicController
    val saves: SaveSession
    val wii: WiiDolphinController
    val res3d: Res3dController
    val gameNetplay: GameNetplay
    val speed: SpeedController
    val settings: AndroidAppSettings
    val refs: AndroidRefs
    val gameRumble: GameRumble
    val launchRecords: LaunchRecords
    val launchId: Long
    val intentTag: String?
    val intentTitle: String?
    val netplay: NetplaySession?
    val achievements: AchievementsBridge?
    val focusPaused: Boolean
    val isDolphin: Boolean
    val isDc: Boolean
    val isN3ds: Boolean

    var usingData: Boolean
    var dataRoot: File
    var privateRoot: File
    var shaderRoots: Shaders.Roots
    var paths: NxPaths
    var gameTag: String
    var gameCoreId: String
    var playerCount: Int
    var rumbles: Array<RumbleMotor>
    var canRewind: Boolean
    var padMap: PadMap
    var segaPad: SegaPad?
    var segaHeader: SegaPad?
    var launchedSegaSetting: String?
    var n3dsTitle: N3dsTitle?
    var n3dsNew: Boolean
    var n3dsSwapMode: String
    var n3dsSwapPinnedHalfFolded: Boolean
    var ndsRenderMode: String?
    var ndsFellBack: Boolean
    var launchedOnVulkan: Boolean
    var launchEnv: Map<String, String?>
    var graphicsForcedOptions: Set<String>
    var frontendValues: Map<String, String>
    var configStore: CoreConfigStore?
    var configSource: ConfigSource
    var lockedOptions: Set<String>
    var storedValues: Map<String, String>
    var restartOnlyOptions: Set<String>
    var forcedOptions: Set<String>
    var hiddenValues: Map<String, Set<String>>
    var descriptorDefaults: Map<String, String>
    var cheatsSupported: Boolean
    var cheatsFolder: String
    var cheatSet: CheatSet
    var cheatsLoad: Job?
    var dolphinPlayerCheats: CheatSet?
    var dolphinSavedCheats: List<String>
    var dolphinBuiltInPath: String?

    /** The game's view exists: from here the saves, the quit and every controller act on it. */
    fun onViewCreated(view: GLRetroView)
    /** The first frame rendered: the battery save and the auto state may be written from here. */
    fun onGameReady()
    /** The core's stall watch, from the view's creation to the end of onDestroy. */
    fun startCoreWatchdog(stalledMillis: () -> Long)
    /** Game time's session for this launch, as onStart and onStop keep it from here. */
    fun startGameTime(rec: GameTimeRecorder)

    fun fail(message: String, title: String = GameActivity.START_FAILED)
    fun relaunchOnGles()
    fun quitGame(chosen: Boolean, autoState: Boolean)
    fun isLandscape(): Boolean
    fun screenClass(): ScreenClass
    fun n3dsSkinStack(): Boolean
    fun n3dsHalfFoldedPin(mode: DualMode): Boolean
    fun n3dsProportion(): String
    fun relayoutPad()
    fun dropPadProfile()
    fun applyFrontend(values: Map<String, String>)
    fun sendSegaPad(setting: String)
    fun offerLayoutTip()
    fun ensureNotificationsView()
    fun publishMenuShaders()
    fun withMenu(block: (InGameMenuModel) -> Unit)
    fun publishNds3d(model: InGameMenuModel)
    fun publishCheats()
    fun applyCheats(view: GLRetroView)
    suspend fun readDolphinBuiltIn(): String?
    suspend fun refreshDolphinCheats()
    fun prepareAchievements(tag: String, romFileName: String, content: ContentRef, inner: File?)
    fun onAchievementEvent(e: RaEvent)
}

/**
 * The game's start, from the launch intent to its first frame, in stages run in order on the main thread:
 * [resolveConfig] (the core, the config, the frontend values, the shortcuts, the 3DS layout options, a link session's
 * engine), [prepareContent] (the data root's saves, an archive's unpack, the BIOS checks, the headers read from the
 * content), [buildViewData] (the runner's data, a real-path copy), [attachView] (the view, its flows and observers) and
 * [onFirstFrame]. A refusal shows the could-not-start dialog ([GameActivity.fail]) and ends the run. Holds the RomCache
 * entries the launch took until the screen is destroyed ([onDestroy]). The state it sets is [host]'s.
 */
internal class LaunchPipeline(private val host: LaunchHost) {
    companion object {
        /** Cores that send a video view map (melonds-ds#335, azahar#2718 in our patches). */
        private val VIDEO_VIEW_CORES = setOf("melondsds", "azahar")
        private const val KEY_FCEUMM_TURBO_SINCE = "fceumm_turbo_since"
        private const val SIMULATE_VULKAN_FAIL = "simulate_vulkan_fail"   // debug builds only: see attachView
        private const val MUPEN64PLUS_NEXT_ID = "mupen64plus_next"
        /** Beside a real-path cache copy: what it was copied from ([realPathCopyStamp]). */
        private const val REAL_PATH_STAMP = ".nxstamp"
    }

    /** What [resolveConfig] settled that the later stages read. */
    private class LaunchConfig(
        val coreFile: String,
        val np: NetplaySession?,
        val tag: String,
        val title: String,
        val coreId: String,
        val descriptor: CoreDescriptor?,
        val players: Int,
        val content: ContentRef,
        val romFileName: String,
        val home: ContentRef?,
        val sessionDir: File?,
        val extraction: kotlinx.coroutines.Deferred<Boolean>,
        val shaderExtraction: Job,
        val realPath: Boolean,
        val companions: List<Pair<String, ContentRef>>,
        /** The file Dolphin loads, once known: an archive's unpacked disc names its built-in cheats ([prepareContent]). */
        val dolphinContent: CompletableDeferred<String>,
        val netplayPad: NetplayPads.Pad?,
        val options: List<Variable>,
        val linkOptions: Map<String, String>,
        val frontend: FrontendSettings,
    )

    /** What [prepareContent] found that the view's data and the load-failure message read. */
    private class PreparedContent(
        /** An archive's unpacked file, which the core gets by real path; null for content loaded as is. */
        val inner: File?,
        val systemDir: File,
        val savesDir: File,
        val sram: ByteArray?,
        val rtc: ByteArray?,
        val segaSetting: String?,
        val biosPresent: Set<String>,
        val archiveAsIs: Boolean,
        val naomiSets: NaomiSets,
        val naomiBios: String?,
    )

    // RomCache entries this game uses (its extraction, or its real-path copies): held so no sweep deletes them; released in onDestroy.
    private val cacheHolds = java.util.concurrent.ConcurrentLinkedQueue<String>()
    @Volatile private var cacheHoldsDrained = false   // set in onDestroy; a hold kept later (an unpack/copy that outlived us) is released at once
    private var resumeCover: ResumeCover? = null   // the resume's screenshot over the game until its frame shows (main thread only)

    suspend fun start() {
        val cfg = resolveConfig() ?: return
        val prepared = prepareContent(cfg) ?: return
        val data = buildViewData(cfg, prepared) ?: return
        attachView(cfg, prepared, data)
    }

    private fun refuse(message: String): Nothing? { host.fail(message); return null }

    private fun refuse(message: String, title: String): Nothing? { host.fail(message, title); return null }

    private suspend fun resolveConfig(): LaunchConfig? {
        with(host) {
            val coreFile = activity.intent.getStringExtra(EXTRA_CORE_FILE) ?: return refuse("No core given")
            // netplay: extras that make no session must not start an ordinary game on the real saves; a recreated session is over.
            if (activity.intent.hasExtra(EXTRA_NETPLAY_ROLE) && netplay == null && !gameNetplay.local) return refuse(NetplayText.FAILED, NetplayText.FAILED)
            if (gameNetplay.restored) return refuse(NetplayText.ENDED, NetplayText.FAILED)
            val np = netplay
            val tag = intentTag ?: "UNKNOWN"
            val title = intentTitle ?: "game"
            val coreId = activity.intent.getStringExtra(EXTRA_CORE_ID) ?: coreFile.removePrefix("lib").substringBefore("_libretro")
            // The tag's own descriptor: a tag view (SGB) carries its own extensions and defaults; the id alone would give plain mgba.
            val descriptor = CoreRegistry.forTag(tag)?.takeIf { it.id == coreId } ?: CoreRegistry.forId(coreId)
            DebugLogFile.nameGame(tag, coreId)
            val players = CoreRegistry.playersFor(tag, descriptor)
            playerCount = players
            // netplay: no slots, so every pad and the touch pad drive native port 0, the engine's local word.
            input.slots = if (players > 1 && np == null) PlayerSlots(players) else null
            // onCreate laid out before the slots existed: a pad benched in an earlier game counted there. Lay out again (only
            // when a benched pad is attached: one player, slots null, never re-lays out here).
            if (padLayout.layoutControllerNames() != padLayout.controllerNames()) relayoutPad()
            // Kept for one player: a key held through the load keeps its recorded send.
            if (input.inputs.count != players) input.inputs = PortInputs(players)
            rumbles = Array(players) { RumbleMotor(activity, gameRumble, phoneFallback = it == 0) }
            // A core that ignores codes still takes RAM patches (ramCheats).
            val cheatsOk = descriptor?.let { it.cheats || it.ramCheats } ?: true
            cheatsSupported = np == null && cheatsOk   // netplay: cheats would desync the other device
            // A SAF document URI, or a file ref with All files access: opened only through [refs], never Uri.parse.
            val content = ContentRef(activity.intent.getStringExtra(EXTRA_CONTENT_URI) ?: return refuse("No content given"))
            val romFileName = activity.intent.getStringExtra(EXTRA_FILE_NAME)
                ?: withContext(Dispatchers.IO) { runCatching { refs.displayName(content) }.getOrNull() } ?: title
            // Binder calls to the menu process, so off the main thread; a menu process that cannot answer fails the launch, not the app.
            dataRoot = withContext(Dispatchers.IO) {
                runCatching {
                    if (!usingData) { DataRoot.useBegin(host.activity); usingData = true }   // no move starts under the session
                    DataRoot.settled(host.activity)   // waits for a move still running
                }.onFailure { NxLog.w(TAG_LOG, "data root unavailable", it) }.getOrNull()
            } ?: return refuse("The game's storage could not be reached. Try again.")
            privateRoot = DataRoot.private(activity)
            shaderRoots = Shaders.Roots.of(privateRoot, dataRoot)
            cheatsFolder = Cheats.folder(tag, romFileName)
            // The battery-save, clock and state names follow the handheld's save settings. The save setup waits
            // for the unpack (after the inner-file check below), since Use extracted file name names them after the
            // archive's inner file; the config and marker names never depend on the settings.
            paths = NxPaths(tag, coreId, romFileName, settings.saveNaming)
            showResumeCover()   // the screenshot's name depends on the tag and ROM name only: up long before the core loads
            val home = AndroidHomeLocator(activity).current()   // a SAF tree or a file ref (the media home without access): BiosImport picks the tree
            // netplay: a session on a copy never touches the real saves: its session folder is its own.
            val sessionDir = if (np?.onCopy == true) NetplaySessionDir.dir(activity.filesDir) else null
            gameTag = tag
            gameCoreId = coreId
            // MainActivity does it too, in the menu process; here for a game started before that copy is in place. Not
            // awaited: it is locked across both processes, and a first-run or post-update copy (~10 MB) must not hold up the
            // game. Re-read once it is done.
            val extraction = lifecycleScope.async(Dispatchers.IO) { Overlays.extractBundled(host.activity) }
            val shaderExtraction = lifecycleScope.launch(Dispatchers.IO) { Shaders.extractBundled(host.activity) }
            restartOnlyOptions = descriptor?.restartOnlyOptions ?: emptySet()
            forcedOptions = descriptor?.lockedOptions ?: emptySet()
            hiddenValues = descriptor?.hiddenValues ?: emptyMap()
            val realPath = activity.intent.getStringExtra(EXTRA_LOAD_MODE) == "REAL_PATH"
            val companionNames = activity.intent.getStringArrayExtra(EXTRA_COMPANION_NAMES) ?: emptyArray()
            val companionUris = activity.intent.getStringArrayExtra(EXTRA_COMPANION_URIS) ?: emptyArray()
            val companions = companionNames.zip(companionUris).map { (name, ref) -> name to ContentRef(ref) }
            descriptorDefaults = optionExtras(activity.intent.getStringArrayExtra(EXTRA_OPTIONS) ?: emptyArray())
            // melonDS DS: GPU where the device has GLES 3.2, as a lowest-priority default; a stored value wins, none is written.
            if (coreId == NdsRenderer.CORE_ID_MELONDSDS) {
                val gles = (activity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).deviceConfigurationInfo.reqGlEsVersion
                descriptorDefaults = NdsRenderer.defaults(gles) + descriptorDefaults
            }
            val store = CoreConfigStore(File(dataRoot, paths.consoleConfig), File(dataRoot, paths.gameConfig)).also { configStore = it }
            val resolved = withContext(Dispatchers.IO) {
                // fceumm's turbo default (Player 1): a cfg saved before it holds the old None as if picked ([FceummTurbo])
                if (coreId == "fceumm") store.migrateFceummTurbo(fceummTurboSince())
                val (console, perGame) = store.read()
                CoreConfig.resolve(descriptorDefaults, console, perGame, forcedOptions, hiddenValues)
            }
            configSource = resolved.source
            lockedOptions = resolved.locked
            storedValues = resolved.values
            ndsRenderMode = resolved.values[NdsRenderer.RENDER_MODE]
            input.n3dsCoreValues(resolved.values)
            val savedCheats = CheatSet.savedFrom(resolved.values, resolved.source)
            // The file Dolphin loads, once known: an archive's unpacked disc names its built-in cheats (completed below).
            val dolphinContent = CompletableDeferred<String>()
            // Off the critical path: the menu shows the set once it lands, the first-frame apply waits for it.
            val loadCheats = { lifecycleScope.launch {
                val extensions = descriptor?.extensions.orEmpty()
                val resolvedSet = withContext(Dispatchers.IO) {
                    // PSP cheat files are named by serial ("[ULUS-10466]"), which the disc knows and the file name rarely does.
                    val serial = if (descriptor?.id == "ppsspp" && PspSerial.readable(romFileName)) PspSerialReader.read(refs, content, romFileName) else null
                    Cheats.resolve(CheatsRoot.of(host.activity), tag, romFileName, title, extensions, serial)
                        .let { if (descriptor?.cheats == false) it.ramPatchesOnly() else it }   // its codes would do nothing
                }
                val combined = if (isDolphin) {
                    dolphinBuiltInPath = DolphinCheats.builtInFile(activity.filesDir.absolutePath, CoreRegistry.contentName(tag, dolphinContent.await()))
                    val builtIn = readDolphinBuiltIn()
                    // Dolphin writes its list at boot, only if absent: a first launch has none yet (see the first-frame re-resolve).
                    if (builtIn == null) { dolphinPlayerCheats = resolvedSet; dolphinSavedCheats = savedCheats.orEmpty() }
                    DolphinCheats.combine(builtIn, resolvedSet)
                } else resolvedSet
                cheatSet = savedCheats?.let(combined::withSaved) ?: combined
                publishCheats()
            } }
            cheatsLoad = if (np != null) null else loadCheats()   // netplay: no cheats (a saved nx_cheats set included)
            // netplay link: the cheats come once the session has ended and the game is a local one.
            if (np?.endsAsLocalGame == true) gameNetplay.localCheats = { cheatsSupported = cheatsOk; loadCheats() }
            // Resolved and decoded before the view exists, so the first frame already has its overlay (the configured one;
            // with none stored the default is None).
            // A DS game starts split (every DS layout draws its screens as regions), which shows no overlay: none is
            // preloaded, and it counts as split until its first frame says otherwise, so the configured one never flashes.
            val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val ndsAtStart = NdsOptions.appliesTo(tag)
            val (sets, preloaded) = withContext(Dispatchers.IO) {
                val found = Overlays.sets(privateRoot, tag)
                val overlay = FrontendSettings.parse(FrontendOptions.fromConfig(resolved.values)).overlay
                val file = if (ndsAtStart) null else OverlayResolver.resolve(tag, found, overlay, landscape)
                found to file?.let { f -> renderer.decodeOverlay(f)?.let { f.path to it } }
            }
            renderer.overlaySets = sets
            renderer.overlayBitmap = preloaded
            renderer.ndsSplit = ndsAtStart
            // nx_* (and legacy minarch_*) keys are frontend settings: never passed to the core.
            frontendValues = FrontendOptions.fromConfig(resolved.values, tag)
            // netplay: both devices run the console's default Sega pad (Auto), whatever this device's console or game setting;
            // the core then gets the handheld pak's pad for this tag and core (NetplayPads).
            if (np != null && PadMap.isSegaPadTag(tag)) frontendValues = frontendValues + (PadOptions.SEGA_PAD to SegaPadType.AUTO)
            canRewind = descriptor?.rewind == true
            speed.setSession(netplay = np != null)   // first: the touch buttons [ShortcutsMicController.launch] builds follow the session
            shortcuts.launch(microphone = descriptor?.microphone == true, netplay = np != null, tag, Shortcuts.fromConfig(resolved.values))
            val netplayPad = if (np != null) NetplayPads.forSession(tag, descriptor?.id) else null
            renderer.shaderValues = ShaderOptions.fromConfig(resolved.values)
            renderer.presetNames = withContext(Dispatchers.IO) { Shaders.presets(shaderRoots) }
            // 3DS with a second display: Azahar starts in the split's fixed layout, which replaces the rows' values below; launched
            // half-folded, its swap is pinned likewise ([DualScreen.n3dsForcedOptions]). The core never runs the user's swap
            // values then, so leaving split (or unfolding) puts back the stored ones. Not under netplay: a session changes no
            // core option once it runs, so an unfold could never put them back; it runs the user's swap, as it launched.
            val launchDual = dual.dualModeNow()
            // 3DS under video views (no netplay): Azahar stays at its unswapped Default, which keeps its views on; the app
            // draws the player's layout and does SWAP itself ([FrontendRenderer.n3dsComposition], [N3dsSwap]).
            val n3dsViews = N3dsOptions.appliesTo(tag) && np == null && LibretroDroid.videoViewsOffered()
            renderer.n3dsViews = n3dsViews
            if (n3dsViews) {
                n3dsSwapMode = resolved.values[DualScreen.N3DS_SWAP_MODE_KEY] ?: "Toggle"
                renderer.n3dsSwapped = N3dsSwap.initial(resolved.values)
            }
            val n3dsSplit = if (!N3dsOptions.appliesTo(tag)) null
                else if (n3dsViews) DualScreen.N3DS_VIEWS_CORE
                else DualScreen.n3dsForcedOptions(launchDual, n3dsSkinStack() && np == null)
            if (n3dsSplit != null && !n3dsViews) dual.n3dsSwapBeforeSplit = DualScreen.n3dsSwapRestore(null, resolved.values)
            n3dsSwapPinnedHalfFolded = N3dsOptions.appliesTo(tag) && np == null && n3dsHalfFoldedPin(launchDual)
            val options = FrontendOptions.coreValues(resolved.values)
                .filterNot { (k, _) -> k == N3dsOptions.LAYOUT && N3dsOptions.appliesTo(tag) }   // set from the row below
                .filterNot { (k, _) -> n3dsSplit?.containsKey(k) == true }
                .map { (k, v) -> Variable(k, v) } +
                // 3DS Large screen: the touch screen at half size (onConfigurationChanged follows a rotation); in portrait the
                // first layout makes it fill the height left ([onN3dsPictureSpace]).
                if (N3dsOptions.appliesTo(tag)) listOf(
                    Variable(N3dsOptions.STACKED_SMALL_PROPORTION, n3dsProportion()),
                    Variable(N3dsOptions.TOUCH_MAIN_ONLY, N3dsOptions.TOUCH_MAIN_ONLY_OFF),
                    Variable(N3dsOptions.CURSOR_VISIBLE, N3dsOptions.cursorVisible(false)),   // stylus mode is off at launch
                    Variable(N3dsOptions.ANALOG_FUNCTION, N3dsOptions.analogFunction(false)),   // the C-stick only until stylus mode (it maps the C-stick at load)
                    // The locked core layout ([N3dsOptions.LAYOUT]) from the row in use; the descriptor's default is replaced.
                    Variable(N3dsOptions.LAYOUT, N3dsOptions.coreLayoutFor(frontendValues, isLandscape(), renderer.ndsControllerMode(), screenClass(), n3dsSkinStack())),
                ).withN3dsSplit(n3dsSplit) else emptyList()
            options.firstOrNull { it.key == N3dsOptions.STACKED_SMALL_PROPORTION }?.value?.let { renderer.n3dsProportion = it }
            if (n3dsViews) {
                renderer.n3dsProportion = n3dsProportion()
                renderer.n3dsComposeLayout = N3dsOptions.coreLayoutFor(frontendValues, isLandscape(), renderer.ndsControllerMode(), screenClass(), n3dsSkinStack())
            }
            // netplay link: gambatte and Azahar get their link options; a GBA link engine starts now, before the core loads, so
            // a joiner learns the host's gpsp_serial or mgba_link_mode from its READY and runs with it (the handheld reloads instead).
            // Outside a session mGBA gets the real cable (NetplayLinkOptions.forSolo). Cable Link: the pair core's variables
            // (both GBAs on this phone, NetplayLinkOptions.forPair), session-only like the others.
            val linkKind = np?.linkKind
            val linkOptions: Map<String, String> = if (np == null) NetplayLinkOptions.forSolo(coreId) else when (linkKind) {
                null -> NetplayLinkOptions.forPair(np)
                NetplayLinkKind.GBLINK, NetplayLinkKind.N3DSLINK -> NetplayLinkOptions.forSession(np, null)
                NetplayLinkKind.NETPACKET -> {
                    val own = NetplayLinkOptions.modeKey(np.coreId)?.let { key -> options.firstOrNull { it.key == key }?.value }
                    val mode = gameNetplay.connectLink(np, own) ?: return refuse(NetplayText.FAILED_CONNECT, NetplayText.FAILED)
                    NetplayLinkOptions.forSession(np, mode)
                }
            }
            val frontend = FrontendSettings.parse(frontendValues)
            return LaunchConfig(
                coreFile, np, tag, title, coreId, descriptor, players, content, romFileName, home, sessionDir, extraction,
                shaderExtraction, realPath, companions, dolphinContent, netplayPad, options, linkOptions, frontend,
            )
        }
    }

    private suspend fun prepareContent(cfg: LaunchConfig): PreparedContent? {
        with(host) {
            // An arcade set's BIOS that was not beside it must be in the system dir (Bios/FBN/ is copied there); a missing parent is
            // not refused (a non-merged clone carries its parent's ROMs), so only BIOS sets arrive here.
            val systemSets = activity.intent.getStringArrayExtra(EXTRA_SYSTEM_SETS)?.toList() ?: emptyList()
            // flycast's set table: a DC zip/7z named after one of its NAOMI/Atomiswave sets is loaded as is and needs its BIOS
            // archive (naomi.zip, awbios.zip ...) in <system>/dc/; any other DC zip/7z (a zipped disc) is unpacked below.
            val naomiSets = if (cfg.descriptor?.id == "flycast" && Names.isArchive(cfg.romFileName)) {
                withContext(Dispatchers.IO) { NaomiSetsAsset.get(host.activity) }
            } else NaomiSets.EMPTY
            val archiveAsIs = CoreRegistry.loadsArchive(cfg.tag, cfg.romFileName, naomiSets)
            val naomiBios = if (archiveAsIs) naomiSets.biosFor(Names.stripExtension(cfg.romFileName)) else null
            // The files this launch needs, looked up by name when a Bios folder will not list.
            val biosNames = CoreRegistry.requiredBios(cfg.tag).flatMap { it.anyOf } +
                (systemSets + listOfNotNull(naomiBios)).flatMap { listOf("$it.zip", "$it.7z") }
            // Usually a stamp check: MainActivity pre-warms the copy after an update; if it is still running, this waits on its lock.
            val systemDir = withContext(Dispatchers.IO) { SystemFiles.ensure(host.activity) }
            // Bios/<TAG>/ lands in the core's BIOS folder: the system dir, or a subfolder of it (flycast's <system>/dc/).
            val biosDir = BiosFolders.subdir(cfg.tag).let { if (it.isEmpty()) systemDir else File(systemDir, it) }
            withContext(Dispatchers.IO) { BiosImport.sync(host.activity, cfg.home, BiosFolders.forTag(cfg.tag), biosDir, biosNames) }
            // An archive is unpacked into the cache first; the core gets the inner file by real path, and everything keyed by
            // romFileName (config, cheats, thumbnails; saves and states unless Use extracted file name is on) keeps the archive
            // name, as on the Brick; unless the tag's core loads the archive as is (fbneo's romsets and flycast's known sets:
            // the zip is the game).
            // Only a real unpack outlasts the delay and shows the toast; it is taken down with the unpack, not left over the game.
            val unpacked: Unpacked? = if (!Names.isArchive(cfg.romFileName) || archiveAsIs) null else {
                var unpackToast: android.widget.Toast? = null
                val unpacking = lifecycleScope.launch { delay(400); unpackToast = toast("Unpacking ${cfg.romFileName}…") }
                try {
                    val romExtensions = cfg.descriptor?.extensions ?: CoreRegistry.coreExtensions
                    withContext(Dispatchers.IO) {
                        // flycast names the VMU after the file it is given: unpacked as <archive stem>.<ext>, the VMU is <stem>.A1.bin.
                        Archives.extract(host.activity, cfg.content, cfg.romFileName, romExtensions, cfg.descriptor?.savesNamedAfterContent == true)
                            .also { keepCacheHold(it.file.parentFile!!.name) }
                    }
                } catch (e: ArchiveException) {
                    NxLog.w(TAG_LOG, "unpack failed: ${e.kind}", e)
                    return refuse(e.message ?: "Could not unpack ${cfg.romFileName}")
                } finally {
                    unpacking.cancel()
                    unpackToast?.cancel()
                }
            }
            val inner: File? = unpacked?.file
            val unpackedEntry = unpacked?.entryName
            cfg.dolphinContent.complete(inner?.name ?: cfg.romFileName)   // Dolphin names its cheat file after the inner disc, not the archive
            if (inner != null && cfg.descriptor != null && Names.extensionOf(inner.name) !in cfg.descriptor.extensions) {
                // Name a tag of the same console whose core runs the file only when one exists; otherwise the file is in the wrong folder.
                val alt = CoreRegistry.consoleOf(cfg.tag)?.tags?.firstOrNull { t ->
                    t != cfg.tag && CoreRegistry.forTag(t)?.let { Names.extensionOf(inner.name) in it.extensions } == true
                }
                return refuse(
                    "${cfg.romFileName} holds ${inner.name}, which ${cfg.descriptor.id} does not run. " +
                        if (alt != null) "Pick ${CoreRegistry.handheldTag(alt)} from the game's Emulator menu." else "Move it to the folder of the console it belongs to.",
                )
            }
            // minarch names saves after the unpacked file when Use extracted file name is on (ma_game.c:75-76): known only now.
            paths = paths.withInner(inner?.let { unpackedEntry })
            saves.open(if (cfg.np == null) SaveStates(dataRoot, paths) else null)   // netplay: no save states, no auto-resume
            dropStaleNdsResume(cfg.tag, cfg.coreId, cfg.romFileName)
            saves.refreshSlots()   // warm for the first menu open; after the cleanup, so it never lists a deleted state
            cfg.np?.let { gameNetplay.prepareSaves(it, cfg.tag, cfg.romFileName, dataRoot, paths) }
            // netplay: on a copy the battery save is the session folder's (read only: writeBatterySave skips it), under the
            // default names (the folder is mobile's own), with no clock file.
            val sessionPaths = if (cfg.sessionDir != null) NxPaths(cfg.tag, cfg.coreId, cfg.romFileName) else null
            // melonDS DS: a handheld Saves/NDS/<rom>.dsv that is the game's only save is played in place (not on a netplay copy).
            val dsvStem = if (cfg.coreId == NdsDsvSave.CORE_ID && cfg.sessionDir == null) paths.saveStem else null
            val battery = BatterySaves(cfg.sessionDir ?: File(dataRoot, paths.savesDir), sessionPaths ?: paths, dsvStem)
            val clock = if (cfg.sessionDir != null) null else ClockSave(File(dataRoot, paths.clock))
            saves.attachBattery(battery, clock)
            val pairFiles = if (cfg.sessionDir != null) null else AndroidCheckpointFiles(PairSaveFiles.sessionFile(activity.filesDir, paths), battery)
            if (cfg.np?.savesAtCheckpoints == true && pairFiles != null) gameNetplay.attachCheckpoint(pairFiles)
            val (sram, rtc) = withContext(Dispatchers.IO) {
                // Cable Link: a game process that died mid-session left the last save both phones agreed on; it is the real
                // save from now on, whatever this launch is.
                pairFiles?.let { f ->
                    runCatching { f.promotePending(File(dataRoot, paths.saveRam)) }
                        .onSuccess {
                            when (it) {
                                PairSaveCheckpoint.Commit.Written -> NxLog.i(TAG_LOG, "promoted a Cable Link session's verified save")
                                PairSaveCheckpoint.Commit.NothingVerified -> Unit
                                is PairSaveCheckpoint.Commit.NotWritten -> NxLog.e(TAG_LOG, "could not promote a Cable Link session's save", it.error)
                            }
                        }
                        .onFailure { NxLog.e(TAG_LOG, "could not promote a Cable Link session's save", it) }
                }
                battery.load() to clock?.load()
            }
            NxLog.i(TAG_LOG, "battery save: ${battery.loadedFrom ?: "none"}")
            if (BuildConfig.DEBUG) NxTestResults.write(activity, "battery", "loadedFrom" to battery.loadedFrom, "bytes" to (sram?.size ?: 0))
            // A damaged configured file is set aside as a .bak at the first save, then the game saves again. Not said for a
            // netplay copy: it is never written, so the file stays as it is.
            if (battery.damaged && cfg.sessionDir == null) toast("Save file is damaged — it will be kept as a .bak when the game saves")
            if (!battery.writable) toast(if (battery.loadedFrom == null) "Save file is damaged — not loaded" else "Save file is damaged — loaded ${battery.loadedFrom}, not saving")
            // Sega Controller Type (Console Settings): the cart header's I/O field under Auto, else the forced pad.
            val segaSetting = if (PadMap.isSegaPadTag(cfg.tag)) frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO else null
            if (segaSetting != null) {
                val headerName = inner?.name ?: cfg.romFileName
                val header = if (!SegaHeader.readable(headerName)) null else withContext(Dispatchers.IO) {
                    runCatching {
                        (inner?.inputStream() ?: refs.openInput(cfg.content))?.use { SegaHeader.padType(readUpTo(it, SegaHeader.READ_LENGTH)) }
                    }.getOrNull()
                }
                segaHeader = header
                // The current value, not segaSetting: the menu may have changed it while the header was read.
                segaPad = cfg.netplayPad?.pad ?: SegaPadType.resolve(frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO, header)
                dropPadProfile()   // the profile follows the resolved pad
            }
            // New 3DS Controls (Console Settings): Auto reads the title from the file's NCSD / NCCH header, off the main thread.
            if (N3dsOptions.appliesTo(cfg.tag)) {
                val headerName = inner?.name ?: cfg.romFileName
                n3dsTitle = if (!N3dsTitle.readable(headerName)) null else withContext(Dispatchers.IO) {
                    runCatching { (inner?.inputStream() ?: refs.openInput(cfg.content))?.use(::readN3dsTitle) }
                        .onFailure { NxLog.w(TAG_LOG, "reading the 3DS header failed", it) }
                        .getOrNull()
                }
                NxLog.i(TAG_LOG, "3DS title: ${n3dsTitle?.titleId?.toString(16)} ${n3dsTitle?.productCode}")
                // The current value: the menu may have changed it while the header was read.
                val resolved = N3dsNewControls.resolve(frontendValues[N3dsOptions.NEW_CONTROLS], n3dsTitle)
                if (resolved != n3dsNew) {
                    n3dsNew = resolved
                    dropPadProfile()
                    relayoutPad()   // the pad was composed without them in onCreate
                }
            }
            // Wii Controller and Motion Controls (Console Settings): Auto reads the disc ID and looks it up in GameTDB's table.
            val wiiDisc = if (!WiiOptions.appliesTo(cfg.tag) || !WiiDiscId.readable(inner?.name ?: cfg.romFileName)) null else withContext(Dispatchers.IO) {
                inner?.let(WiiDiscIdReader::read) ?: WiiDiscIdReader.read(refs, cfg.content, cfg.romFileName)
            }
            wii.resolveAtLaunch(cfg.tag, wiiDisc)
            padMap = if (isDc) PadMap.IDENTITY else PadMap.forTag(cfg.tag, segaPad)
            // Only 3-button draws differently from the six the pad was composed with in onCreate: recompose it.
            if (segaPad == SegaPad.THREE) relayoutPad()
            val contentExtension = Names.extensionOf(inner?.name ?: cfg.romFileName)
            // A missing required BIOS: say which file goes where instead of letting the core fail silently.
            val present = withContext(Dispatchers.IO) { systemDir.list()?.toSet() ?: emptySet() }
            val biosPresent = if (biosDir == systemDir) present else withContext(Dispatchers.IO) { biosDir.list()?.toSet() ?: emptySet() }
            BiosCheck.missing(CoreRegistry.requiredBios(cfg.tag), contentExtension, biosPresent).firstOrNull()?.let {
                return refuse(BiosCheck.message(cfg.tag, it))
            }
            // A NAOMI/Atomiswave set's BIOS archive (flycast opens <system>/dc/<bios>.zip or .7z); an unknown set is not checked.
            if (archiveAsIs) naomiSets.missingBios(cfg.romFileName, biosPresent)?.let { return refuse(NaomiSets.biosMessage(cfg.title, it)) }
            // A missing arcade BIOS set (systemSets, read above) is refused here.
            if (systemSets.isNotEmpty()) {
                val fbneoFiles = withContext(Dispatchers.IO) { File(systemDir, "fbneo").list()?.toSet() ?: emptySet() }
                ArcadeCompanions.missing(systemSets, present, fbneoFiles)?.let { set ->
                    return refuse("${cfg.title} needs $set.zip: put it next to the game or in Bios/FBN/")
                }
            }
            val savesDir = (cfg.sessionDir ?: File(dataRoot, paths.savesDir)).apply { mkdirs() }
            // netplay: a link session's game is the player's own, so it gets achievements as a normal launch; lockstep has none.
            if (cfg.np == null || cfg.np.allowsAchievements) prepareAchievements(cfg.tag, cfg.romFileName, cfg.content, inner)
            return PreparedContent(inner, systemDir, savesDir, sram, rtc, segaSetting, biosPresent, archiveAsIs, naomiSets, naomiBios)
        }
    }

    private suspend fun buildViewData(cfg: LaunchConfig, prepared: PreparedContent): GLRetroViewData? {
        with(host) {
            // A REAL_PATH core's copy into the cache gets the unpack's toast: only one that outlasts the delay shows it.
            var copyToast: android.widget.Toast? = null
            val copying = if (cfg.realPath && prepared.inner == null) lifecycleScope.launch { delay(400); copyToast = toast("Copying ${cfg.romFileName}…") } else null
            // What the core is launched with, read now (a change during the header read or the BIOS checks included); a
            // change after this reaches the core at the first frame.
            val launchSega = if (prepared.segaSetting != null) frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO else null
            val launchSegaPad = segaPad
            launchedSegaSetting = launchSega
            val nds3dLaunch = res3d.launchOption()   // read on the main thread: the window's size
            // PS2 BIOS row: the pick while its file is still in the folder, else Auto (the core's own choice).
            val ps2BiosOption = if (Ps2Options.appliesTo(cfg.tag)) {
                Ps2Options.biosOption(frontendValues[Ps2Options.BIOS], prepared.biosPresent.filter { it.endsWith(".bin", ignoreCase = true) })
            } else emptyMap()
            val data = try {
                withContext(Dispatchers.IO) {
                    // The name the core sees: an arcade zip in lowercase (fbneo matches the driver name case-sensitively).
                    val fileName = CoreRegistry.contentName(cfg.tag, cfg.romFileName)
                    // Display → Graphics API: the core options for the API and whether the runner offers Vulkan.
                    // Vulkan that could not start here before, or this is the relaunch after it did: OpenGL ES (VulkanFallback).
                    val vulkanFailed = activity.intent.getBooleanExtra(EXTRA_FORCE_GLES, false) ||
                        cfg.descriptor?.id in settings.vulkanFailedCores || (cfg.np != null && NetplayCores.forcesGles(cfg.descriptor?.id))
                    val graphics = GraphicsApiOptions.resolve(frontendValues[GraphicsApiOptions.KEY], cfg.descriptor?.vulkan, { LibretroDroid.isVulkanSupported() }, vulkanFailed)
                    launchedOnVulkan = graphics.preferVulkan
                    launchEnv = graphics.env
                    // netplay link: the link options are this launch's too (a session's port, mode and peer; a solo mGBA's cable): never saved.
                    graphicsForcedOptions = graphics.forcedOptions.keys + cfg.linkOptions.keys
                    GLRetroViewData(host.activity).apply {
                        coreFilePath = cfg.coreFile
                        systemDirectory = prepared.systemDir.absolutePath
                        savesDirectory = prepared.savesDir.absolutePath
                        // netplay: the handheld pak's pad options (NetplayPads) replace the Controller Type's, and a link
                        // session's (NetplayLinkOptions) the user's, set even when absent.
                        // N64: Max High-Res VRAM Limit's Auto becomes a number for this device's memory (N64HiresPack.vramLimit).
                        val n64Vram = if (cfg.descriptor?.id == MUPEN64PLUS_NEXT_ID) {
                            val total = ActivityManager.MemoryInfo().also { activity.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem
                            val limit = N64HiresPack.vramLimit(cfg.options.firstOrNull { it.key == N64HiresPack.VRAM_KEY }?.value, total)
                            NxLog.i(TAG_LOG, "N64 hi-res VRAM limit: $limit MB (device ${total / (1024 * 1024)} MB)")
                            mapOf(N64HiresPack.VRAM_KEY to limit)
                        } else emptyMap()
                        val forced = cfg.netplayPad?.options.orEmpty() + cfg.linkOptions + wii.screenOption() + nds3dLaunch + ps2BiosOption + n64Vram + graphics.forcedOptions
                        variables = withForced(cfg.options, forced).toTypedArray()
                        rumbleEventsEnabled = gameRumble != GameRumble.OFF   // Game rumble Off: the runner does not even read the core's state
                        preferLowLatencyAudio = true
                        // DS / 3DS: the frontend feeds the core's mic ([ShortcutsMicController.applyMic] picks silence, the phone mic or noise).
                        enableMicrophone = shortcuts.canMic
                        // GL driver messages and a frame probe to tag NXGL, with Settings → System → Debug log on at
                        // launch.
                        glDebugOutput = NxLog.enabled
                        // Display → Graphics API ([graphics]): Vulkan frames are bridged into the GLES presentation.
                        preferVulkan = graphics.preferVulkan
                        vulkanSwapchainFrames = graphics.swapchainFrames
                        // DS / 3DS: the core reports where each screen sits in its frame (video views), the layouts read it.
                        videoViews = gameCoreId in VIDEO_VIEW_CORES
                        // gpsp: never unload the core between games, so its recompiler cache stays mapped near it.
                        keepCoreLoaded = cfg.descriptor?.keepLoaded == true
                        shader = renderer.shaderFor(cfg.frontend)
                        saveRAMState = prepared.sram
                        rtcState = prepared.rtc   // the clock file, applied after the battery save
                        // Set after load and before the first frame: pcsx_rearmed's DualShock (a gamepad's sticks reach the
                        // core), flycast's joypad (its ports start empty, so the BIOS and game would see no controller) with
                        // its unused ports set to none, which completes its device setup (the VMU in slot 1). Player 2+
                        // ports get the same device, as does a forced genesis_plus_gx Controller Type.
                        cfg.descriptor?.let { controllerPorts = it.controllerPortsFor(cfg.players) }
                        if (cfg.descriptor?.id == WiiDolphinController.DOLPHIN_ID) controllerPorts = wii.launchPorts(cfg.players)
                        if (cfg.descriptor?.id == SegaPadType.GPGX) SegaPadType.gpgxPorts(launchSega, cfg.players)?.let { controllerPorts = it }
                        if (cfg.descriptor?.id == SegaPadType.PICODRIVE && launchSega != null) controllerPorts = SegaPadType.picodrivePorts(launchSega, launchSegaPad, cfg.players)
                        cfg.netplayPad?.ports?.takeIf { it.isNotEmpty() }?.let { controllerPorts = controllerPorts + it }   // netplay: the pak's ports 0-1
                        if (prepared.inner != null) {
                            gameFilePath = prepared.inner.absolutePath
                        } else if (cfg.realPath) {
                            // A file ref (All files access) is opened in place when it and its companions are readable there;
                            // a SAF game is copied: a disc set's files go next to its sheet in the cache, under the names the sheet uses.
                            val inPlace = RealPathLaunch.inPlace(cfg.content, fileName, cfg.companions)
                                ?.takeIf { p -> File(p).canRead() && cfg.companions.all { (name, _) -> File(File(p).parentFile, name).canRead() } }
                            if (inPlace != null) {
                                gameFilePath = inPlace
                            } else {
                                for ((name, ref) in cfg.companions) copyToCache(ref, name)
                                gameFilePath = copyToCache(cfg.content, fileName).absolutePath
                            }
                        } else {
                            // With more than one virtual file the runner serves the core through its VFS (libretrodroid.cpp:391),
                            // so the core opens /rom/<track>.bin by the name its cue gives.
                            val opened = ArrayList<VirtualFile>(cfg.companions.size + 1)
                            try {
                                val pfd = refs.openFd(cfg.content, "r") ?: throw IOException("Cannot open file descriptor")
                                opened += VirtualFile("/rom/$fileName", pfd)
                                for ((name, ref) in cfg.companions) {
                                    opened += VirtualFile("/rom/$name", refs.openFd(ref, "r") ?: throw IOException("Cannot open $name"))
                                }
                            } catch (e: Exception) {
                                // The view never gets them: nothing else would close the ones already open.
                                opened.forEach { f -> runCatching { f.fileDescriptor.close() } }
                                throw e
                            }
                            gameVirtualFiles = opened
                        }
                        if (cfg.descriptor?.id == MUPEN64PLUS_NEXT_ID) {
                            val values = variables.mapNotNull { v -> v.key?.let { k -> v.value?.let { k to it } } }.toMap()
                            extraVirtualFiles = n64HiresPack(values, graphics.preferVulkan, cfg.content, prepared.inner, cfg.home)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e   // lifecycleScope cancelled mid-copy (a back-out, a destroy): not a launch failure to report
            } catch (e: Exception) {
                return refuse("Cannot open ${cfg.title}: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                copying?.cancel()
                copyToast?.cancel()
            }
            return data
        }
    }

    private suspend fun attachView(cfg: LaunchConfig, prepared: PreparedContent, data: GLRetroViewData) {
        with(host) {
            // Graphics API's process environment for the core (ParaLLEl-RDP's switches), before it loads; unset otherwise.
            launchEnv.forEach { (k, v) ->
                runCatching { if (v != null) android.system.Os.setenv(k, v, true) else android.system.Os.unsetenv(k) }
                    .onFailure { NxLog.w("GameActivity", "could not set $k", it) }
            }
            if (isN3ds) wii.offerN3dsMotion()
            val view = GLRetroView(activity, data)
            view.pictureHeld = true   // black until the first frame's layout places the picture ([PictureHold])
            onViewCreated(view)
            speed.apply()   // a speed set before the view existed (the launch's rows reach it again in applyFrontend)
            shortcuts.pushMicSource()   // a Mic toggle tapped before the view existed
            // Not in netplay: a link cable's step may wait out the peer's hold (sio_lockstep: up to 30 s), and the session
            // has its own timeouts.
            startCoreWatchdog { if (netplay == null) view.stepStalledMillis() else 0L }
            if (focusPaused) { view.frameSpeed = 0; view.audioEnabled = false }   // focus went to another app while loading
            // Collected before the view is added (its ON_CREATE) and long before the first frame: the runner reports a
            // change only, from an unbuffered flow, so a missed event would leave the motor wrong until the next one.
            if (gameRumble != GameRumble.OFF) lifecycleScope.launch {
                view.getRumbleEvents().collect { e -> if (!quitting) rumbles.getOrNull(e.port)?.onRumble(e.strengthWeak, e.strengthStrong) }
            }
            // The core asked to end (Dolphin: the game's HOME menu → Wii Menu, which is not installed): close as Quit does,
            // without a resume state, since the game has stopped itself by then. The resume point is HOME's ([WiiDolphinController.pressHome]).
            lifecycleScope.launch {
                view.getShutdownRequests().first()
                wii.endedByCore = true   // before the drop suspends: no slot 9 may queue behind it
                // Left without HOME this session (a game's own "Wii Menu", a channel launch): the resume point on disk is an
                // older session's, behind the game's own save on the NAND. Dropped, so the next launch boots fresh.
                if (!wii.homeSaved) saves.dropAutoInOrder()
                quitGame(chosen = true, autoState = false)
            }
            // Subscribed before the login (startSession, after the first frame): events are not replayed.
            achievements?.let { b ->
                b.attach(view)
                b.deliverEvents { onAchievementEvent(it) }
            }
            // The core's on-screen messages: the few worth showing (CoreMessages: the FDS disk side) as a toast on the picture.
            lifecycleScope.launch { view.getCoreMessages().collect { m -> CoreMessages.toast(cfg.coreId, m)?.let(dual::pictureToast) } }
            // The core loads the game on the GL thread once the view is added (SurfaceCreated follows): the waiting pads'
            // timeout counts from then, not through the load.
            lifecycleScope.launch {
                view.getGLRetroEvents().first { it is GLRetroView.GLRetroEvents.SurfaceCreated }
                padLayout.onCoreRunning()
                offerLayoutTip()
            }
            val container = activity.findViewById<FrameLayout>(R.id.gameContainer)
            container.addView(view)
            resumeCover?.follow(view)
            lifecycle.addObserver(view)
            // Added after the view: LifecycleRegistry sends ON_DESTROY newest-first, so this runs before the view's
            // LibretroDroid.destroy(). It waits for a quit's serialize in flight (one call, milliseconds) and stops the next.
            lifecycle.addObserver(LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_DESTROY) saves.onCoreDestroy() })
            renderer.ensureOverlayView()
            ensureNotificationsView()
            // Size changes (first layout, rotation, cutout margin) re-lay out the picture and the overlay box.
            container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) container.post { renderer.relayoutFrontend() }
            }
            applyFrontend(frontendValues)
            // Set before the first chain push: a chain built with the view reports its errors through this.
            view.onShaderError = { msg -> toast("Shader: $msg"); renderer.dropShaderChain() }
            // A stored preset reads the extracted files: on a first run or after an update, wait for the (~0.5 MB)
            // copy and swap rather than fail or read the tree mid-swap.
            if (ShaderOptions.preset(renderer.shaderValues) != null) cfg.shaderExtraction.join()
            renderer.applyShader(renderer.shaderValues)
            // First run or after an update: the bundled sets may still be copying; pick them up once swapped in.
            lifecycleScope.launch {
                val synced = cfg.extraction.await()
                val found = withContext(Dispatchers.IO) { Overlays.sets(privateRoot, cfg.tag) }
                // After a sync, re-apply even with the same names: an overlay decoded mid-swap may have failed.
                if (synced || found != renderer.overlaySets) {
                    renderer.overlaySets = found   // the Overlay value is unchanged (its default is None whatever the files)
                    applyFrontend(frontendValues)
                }
            }
            lifecycleScope.launch {
                cfg.shaderExtraction.join()
                renderer.presetNames = withContext(Dispatchers.IO) { Shaders.presets(shaderRoots) }
                publishMenuShaders()
            }
            lifecycleScope.launch {
                view.getGLRetroEvents().first { it is GLRetroView.GLRetroEvents.FrameRendered }
                onFirstFrame(cfg, view)
            }
            // A refused game: the core's own reason (flycast names the NAOMI/Atomiswave BIOS or ROM file it could not find).
            val naomiParent = if (prepared.archiveAsIs) prepared.naomiSets.parent(Names.stripExtension(cfg.romFileName)) else null
            observeErrors(view, cfg.coreFile) { lines ->
                LoadFailure.message(cfg.title, lines, cfg.romFileName.takeIf { prepared.archiveAsIs }, prepared.naomiBios, naomiParent)
            }
            // Debug builds: a file named simulate_vulkan_fail in the app's files dir makes a Vulkan launch take the fallback
            // as a failed bridge setup would (no phone at hand fails it): `adb shell run-as <package> touch files/simulate_vulkan_fail`.
            if (BuildConfig.DEBUG && launchedOnVulkan && (!activity.intent.hasExtra(EXTRA_NETPLAY_ROLE) || gameNetplay.local) && File(activity.filesDir, SIMULATE_VULKAN_FAIL).exists()) {
                relaunchOnGles()
            }
        }
    }

    /** The first rendered frame: the game runs; what waited for it (game time, a resume, the cheats, netplay) goes now. */
    private suspend fun onFirstFrame(cfg: LaunchConfig, view: GLRetroView) {
        with(host) {
            onGameReady()
            if (BuildConfig.DEBUG) NxTestResults.write(host.activity, "game", "state" to "running", "tag" to gameTag, "core" to gameCoreId)
            activity.intent.getStringExtra(EXTRA_GAME_PATH)?.let { path ->
                val tag = gameTag
                val name = intentTitle ?: path.substringAfterLast('/')
                lifecycleScope.launch {
                    val rec = withContext(Dispatchers.IO) { GameTimeService.recorder(host.activity) }
                    rec.start(path, tag, name)
                    startGameTime(rec)
                }
            }
            // A Controller Type change made while the game loaded was held back (applySegaPad): send it now.
            launchedSegaSetting?.let { launched ->
                val now = frontendValues[PadOptions.SEGA_PAD] ?: SegaPadType.AUTO
                if (now != launched) sendSegaPad(now)
            }
            wii.onFirstFrame(view)
            dual.pushSecondSurface()   // a presentation shown before the view ran
            if (dual.panelShown()) dual.refreshDiscs()   // a panel up before the game ran asked too early
            // A core may change its geometry later (SET_GEOMETRY), as Azahar does a frame or so after a layout switch: the
            // picture and the pad are laid out again from the new size and aspect (both displays in a dual mode, or the
            // second one keeps fitting the new frame at the old aspect). A core turning its picture (a WonderSwan game
            // held upright) moves the picture screen too. Collected before the first layout below, so no change falls
            // between the two.
            lifecycleScope.launch {
                view.getGameGeometryChanges().collect {
                    val g = view.getGameGeometry()
                    renderer.onCoreGeometry(g)
                    wii.followShape(g)
                }
            }
            renderer.relayoutFrontend()   // the core's geometry (a DS game's 256x384) is only known now
            // melonDS DS falls back to software when its GL renderer can't start: say so once (nothing is saved, the
            // next launch tries the GPU again). ~30 frames lets the core's first hardware frames arrive.
            if (NdsOptions.appliesTo(gameTag) && ndsRenderMode == NdsRenderer.OPENGL) {
                lifecycleScope.launch {
                    view.getGLRetroEvents().filter { it is GLRetroView.GLRetroEvents.FrameRendered }.take(30).collect {}
                    if (NdsRenderer.showFallbackNotice(ndsRenderMode, view.isHardwareRendered())) {
                        ndsFellBack = true
                        withMenu { publishNds3d(it) }
                        toast("GPU renderer unavailable — using software")
                    }
                }
            }
            // A frame means the core loaded the game (a load error aborts the view before any): the launcher may record it.
            if (launchRecords.started(launchId)) {
                activity.sendBroadcast(Intent(LaunchRecords.action(activity)).setPackage(activity.packageName))   // the menu records it
            }
            if (cfg.np != null && !gameNetplay.onFirstFrame(cfg.np, cfg.descriptor?.name ?: cfg.coreId)) return
            saves.resumeAtFirstFrame(view, dataRoot, paths)
            dropResumeCover(view)
            // Cheats sit on top of the resumed state, as minarch applies them after load.
            cheatsLoad?.join()
            refreshDolphinCheats()
            if (cheatSet.hasEnabled()) applyCheats(view)
            achievements?.startSession()
        }
    }

    /**
     * NDS moved from melonds to melonDS DS: a resume marker left pointing at an old-core state (which melonDS DS can't
     * load) is deleted with its thumbnails, and this launch boots fresh instead of toasting "Resume failed". Once per
     * game, since the marker is gone afterwards; the old core's states stay untouched.
     */
    private suspend fun dropStaleNdsResume(tag: String, coreId: String, romFileName: String) {
        if (tag != NdsResumeCleanup.TAG || coreId != NdsResumeCleanup.CORE_ID) return
        val dataRoot = host.dataRoot
        val paths = host.paths
        val dropped = withContext(Dispatchers.IO) {
            SaveSession.awaitAutoWrites()   // a Home write of this game's auto state may still be landing
            runCatching {
                val marker = File(dataRoot, paths.lastSlot).takeIf { it.exists() }?.readText()
                val victims = NdsResumeCleanup.victims(tag, coreId, romFileName, marker, paths.naming, paths.innerFileName) { File(dataRoot, it).exists() }
                victims.forEach { File(dataRoot, it).delete() }
                victims.isNotEmpty()
            }.onFailure { NxLog.w("GameActivity", "stale NDS resume cleanup failed", it) }.getOrDefault(false)
        }
        if (dropped) host.saves.dropResume()
    }

    /**
     * The resume slot's screenshot over the game container from as soon as its name is known, while the core loads and
     * boots, until the resumed state's frame ([dropResumeCover]), as minarch shows it from its window's first moment.
     * Decoded off the main thread; nothing when the slot has none, or the resume already landed.
     */
    private fun showResumeCover() {
        val saves = host.saves
        val slot = saves.resumeSlot?.takeIf { saves.resumePending && it != 8 } ?: return   // slot 8 has no screenshot in minarch's scheme
        val file = File(host.dataRoot, host.paths.thumb(slot))
        host.lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { ResumeCover.decode(file) } ?: return@launch
            if (!saves.resumePending || host.activity.isFinishing) return@launch
            // Where the picture is drawn: as last laid out, else (before the game view, or a DS / 3DS before its first
            // frame) the pad skin's glass for the picture the screenshot holds.
            val picture = { host.renderer.thumbnailPicture() ?: host.padLayout.takeIf { it.skinShown }?.skinPlacement()?.let(::coverGlass) }
            resumeCover = ResumeCover(host.activity, host.activity.findViewById(R.id.gameContainer), picture).also { cover ->
                host.renderer.onPictureLaidOut = { resumeCover?.place() }
                cover.show(bmp)
                host.retroView?.let(cover::follow)   // the game view came first
            }
        }
    }

    /**
     * The skin glass a resume screenshot belongs on: a one-screen game's picture glass, else the DS / 3DS top screen
     * (the screenshot is that screen alone): its own glass, or the top half of a stacked one.
     */
    private fun coverGlass(placement: SkinPlacement): PxRect? {
        placement.glass(GlassScreen.PICTURE)?.let { return it.rect }
        placement.glass(GlassScreen.TOP)?.let { return it.rect }
        return placement.glass(GlassScreen.STACK)?.rect?.let { it.copy(height = it.height / 2f) }
    }

    /**
     * The resume is done (or failed): the cover fades once [ResumeCover.SETTLE_FRAMES] of the game's frames are drawn,
     * so its own frame is on screen under it (the surface shows a drawn frame a frame or two later).
     */
    private fun dropResumeCover(view: GLRetroView) {
        val cover = resumeCover ?: return
        host.lifecycleScope.launch {
            view.getGLRetroEvents().filter { it is GLRetroView.GLRetroEvents.FrameRendered }.take(ResumeCover.SETTLE_FRAMES).collect {}
            cover.fadeOut()
            if (resumeCover === cover) resumeCover = null
        }
    }

    private fun observeErrors(view: GLRetroView, coreFile: String, loadFailure: (List<String>) -> String?) {
        host.lifecycleScope.launch {
            view.getGLRetroErrors().flowWithLifecycle(host.lifecycle, Lifecycle.State.STARTED).collect { code ->
                if (code == GLRetroView.ERROR_LOAD_LIBRARY) {
                    val lines = runCatching { view.getRecentCoreErrors() }.getOrDefault(emptyList())
                    host.fail(CoreLoadFailure.message(coreFile, lines))
                    return@collect
                }
                val name = when (code) {
                    GLRetroView.ERROR_LOAD_GAME -> "ERROR_LOAD_GAME"
                    GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "ERROR_GL_NOT_COMPATIBLE"
                    GLRetroView.ERROR_SERIALIZATION -> "ERROR_SERIALIZATION"
                    GLRetroView.ERROR_CHEAT -> "ERROR_CHEAT"
                    else -> "ERROR_GENERIC($code)"
                }
                if (code == GLRetroView.ERROR_LOAD_GAME) {
                    val lines = runCatching { view.getRecentCoreErrors() }.getOrDefault(emptyList())
                    lines.forEach { NxLog.i(TAG_LOG, "core said: $it") }
                    loadFailure(lines)?.let { host.fail(it); return@collect }
                }
                if (code == GLRetroView.ERROR_GL_NOT_COMPATIBLE && VulkanFallback.relaunchOnGles(host.launchedOnVulkan)) {
                    // netplay: a relaunch would start without the session (it is gone), so the dialog says what to do. An
                    // ended link session's relaunch ([GameNetplay.local]) is a plain launch, which relaunches as any other.
                    if (host.activity.intent.hasExtra(GameActivity.EXTRA_NETPLAY_ROLE) && !host.gameNetplay.local) {
                        host.fail("Vulkan could not start for this game on this device. Set Display → Graphics API to OpenGL ES.")
                    } else {
                        host.relaunchOnGles()
                    }
                    return@collect
                }
                host.fail("The core reported $name")
            }
        }
    }

    /** onDestroy: the RomCache holds this game took go, and one taken later (an unpack or copy that outlived the screen) at once. */
    fun onDestroy() {
        cacheHoldsDrained = true
        releaseCacheHolds()
    }

    /**
     * Records a RomCache hold this game took, for onDestroy to release. The blocking unpack/copy that took it runs on
     * after a back-out cancels lifecycleScope, so it may land after onDestroy drained: then it is released here. Either
     * this sees [cacheHoldsDrained] after its add, or onDestroy's drain (after setting it) sees the add; poll releases once.
     */
    private fun keepCacheHold(name: String) {
        cacheHolds += name
        if (cacheHoldsDrained) releaseCacheHolds()
    }

    private fun releaseCacheHolds() {
        while (true) RomCache.release(host.activity, cacheHolds.poll() ?: break)
    }

    /**
     * A top-level RomCache entry, held for this game; a copy whose stamp still matches the source is reused (and touched:
     * its mtime is its last use). The stamp, `<file name>.nxstamp` beside it, is the source's size
     * and last-modified ([realPathCopyStamp]): a same-size patched ROM is copied again. The copy goes to `<file name>.part`
     * and is renamed into place, the old stamp dropped first, so a kill mid-copy leaves no torn copy that passes for whole.
     */
    private fun copyToCache(ref: ContentRef, fileName: String): File {
        val refs = host.refs
        val root = RomCache.root(host.activity).apply { mkdirs() }
        val target = File(root, fileName)
        val stampFile = File(root, cacheSiblingName(fileName, REAL_PATH_STAMP))
        val part = File(root, cacheSiblingName(fileName, ".part"))
        RomCache.withEntryLock(root, target.name) {
            RomCache.hold(host.activity, target.name); keepCacheHold(target.name)
            RomCache.hold(host.activity, stampFile.name); keepCacheHold(stampFile.name)   // a swept stamp would only cost a recopy, but keep it
            val size = refs.size(ref) ?: -1L
            val stamp = realPathCopyStamp(size, refs.lastModified(ref))
            val stored = runCatching { stampFile.takeIf { it.isFile }?.readText() }.getOrNull()
            if (reuseRealPathCopy(target.isFile, target.length(), size, stored, stamp)) {
                RomCache.touch(stampFile)
                return target.also { RomCache.touch(it) }
            }
            // The .part is its own top-level name to the sweep: held while it grows, so a big ISO's is never trimmed mid-copy.
            RomCache.hold(host.activity, part.name)
            try {
                val input = refs.openInput(ref) ?: throw IOException("Cannot open input stream")
                input.use { source -> part.outputStream().use { source.copyTo(it, 1 shl 20) } }
                stampFile.delete()   // describes the copy being replaced; a kill before the new stamp only costs a recopy
                if (!part.renameTo(target)) throw IOException("Cannot move ${part.name} into place")
                // An unknown size never matches (reuseRealPathCopy), so its stamp is not worth writing.
                if (size >= 0) runCatching { stampFile.writeAtomically(stamp.encodeToByteArray()) }
                    .onFailure { NxLog.w("GameActivity", "could not stamp the cache copy of $fileName", it) }
            } finally {
                part.delete()
                RomCache.release(host.activity, part.name)
            }
            return target
        }
    }

    /**
     * The N64 game's hi-res texture pack when Use High-Res textures is on (GLideN64 only): `<NAME>_HIRESTEXTURES.hts`, named
     * by the ROM header, in [home]'s `Textures/N64/` or else in `.cache/` in the game's folder, served to the core as a
     * read-only virtual file ([N64HiresPack]). Empty when there is none; a pack that cannot be found or opened only leaves
     * the original textures.
     */
    private suspend fun n64HiresPack(values: Map<String, String>, onVulkan: Boolean, content: ContentRef, inner: File?, home: ContentRef?): List<VirtualFile> {
        if (!N64HiresPack.wanted(values, onVulkan)) return emptyList()
        val refs = host.refs
        val head = runCatching { (inner?.inputStream() ?: refs.openInput(content))?.use { readUpTo(it, N64HiresPack.HEADER_BYTES) } }.getOrNull()
        val name = head?.let(N64HiresPack::headerName)
            ?: return emptyList<VirtualFile>().also { NxLog.w(TAG_LOG, "N64 hi-res pack: no ROM header name") }
        val source = AndroidRomsSources.of(host.activity)
        val fileName = N64HiresPack.fileName(name)
        suspend fun under(root: ContentRef?, path: List<String>) = runCatching {
            var dir = root ?: return@runCatching null
            for (part in path) dir = source.child(dir, part)?.takeIf { it.isDirectory }?.ref ?: return@runCatching null
            source.child(dir, fileName)?.takeIf { !it.isDirectory }
        }.getOrNull()
        val folder = host.activity.intent.getStringExtra(GameActivity.EXTRA_FOLDER_URI)?.takeIf { it.isNotEmpty() }?.let(::ContentRef)
        val pack = under(home, N64HiresPack.HOME_PATH) ?: under(folder, listOf(N64HiresPack.FOLDER))
            ?: return emptyList<VirtualFile>().also {
                NxLog.i(TAG_LOG, "N64 hi-res pack: no $fileName in ${N64HiresPack.HOME_PATH.joinToString("/")}/ or ${N64HiresPack.FOLDER}/")
            }
        val pfd = runCatching { refs.openFd(pack.ref, "r") }.getOrNull()
            ?: return emptyList<VirtualFile>().also { NxLog.w(TAG_LOG, "N64 hi-res pack: cannot open $fileName") }
        NxLog.i(TAG_LOG, "N64 hi-res pack: ${pack.ref.value}")
        return listOf(VirtualFile("${N64HiresPack.VIRTUAL_DIR}/$fileName", pfd))
    }

    /**
     * When this app first ran with fceumm's turbo on by default: cfg files written before then hold the old default
     * ([CoreConfigStore.migrateFceummTurbo]). Kept in its own prefs file, written once.
     */
    private fun fceummTurboSince(): Long {
        val prefs = Prefs.open(host.activity, "nx-migrations")
        prefs.getLong(KEY_FCEUMM_TURBO_SINCE, 0L).takeIf { it > 0L }?.let { return it }
        return System.currentTimeMillis().also { prefs.edit().putLong(KEY_FCEUMM_TURBO_SINCE, it).commit() }
    }
}

private fun readUpTo(input: java.io.InputStream, max: Int): ByteArray {
    val buf = ByteArray(max)
    var n = 0
    while (n < max) { val r = input.read(buf, n, max - n); if (r < 0) break; n += r }
    return buf.copyOf(n)
}

/**
 * The 3DS title from the start of [s] ([N3dsTitle.parse]): the first header, and for a cart image (NCSD) on to its
 * partition 0's NCCH header (0x4000 on dumped carts), a few KB in all.
 */
private fun readN3dsTitle(s: java.io.InputStream): N3dsTitle? {
    val head = readUpTo(s, N3dsTitle.HEADER)
    if (head.size < N3dsTitle.HEADER) return null
    val offset = N3dsTitle.ncchOffset(head) ?: return N3dsTitle.parse(head)
    // On to the end of the NCCH header at [offset], which starts at or past the NCSD header's end.
    return N3dsTitle.parse(head + readUpTo(s, offset.toInt()))
}

/**
 * What a real-path cache copy was made from ([LaunchPipeline.copyToCache]): the source's size and, when its provider
 * reports one, its last-modified. Without a last-modified it is size only.
 */
internal fun realPathCopyStamp(size: Long, lastModified: Long?): String = "$size:${lastModified ?: "?"}"

/**
 * `<file name><suffix>`, or `<hash of the name><suffix>` when that (plus writeAtomically's `.xxxxxxxx.tmp`) would pass
 * the 255-byte file-name limit: a long ROM name must not fail its copy over a sibling's name.
 */
internal fun cacheSiblingName(fileName: String, suffix: String): String =
    if ((fileName + suffix).encodeToByteArray().size + 14 <= 255) fileName + suffix
    else fileName.hashCode().toUInt().toString(16) + suffix

/** A cache copy is reused only when the source's size is known, the copy has it, and its stored stamp is [stamp]. */
internal fun reuseRealPathCopy(copyExists: Boolean, copyLength: Long, size: Long, storedStamp: String?, stamp: String): Boolean =
    copyExists && size >= 0 && copyLength == size && storedStamp?.trim() == stamp

/** The launch's EXTRA_OPTIONS (`key=value`, the value may hold `=`); an entry without a key is dropped, a later key wins. */
internal fun optionExtras(extras: Array<String>): Map<String, String> =
    extras.mapNotNull { kv -> kv.indexOf('=').takeIf { it > 0 }?.let { kv.substring(0, it) to kv.substring(it + 1) } }.toMap()

/** [options] with [forced]'s values in place of the same keys, in their order, then the forced keys [options] lacks. */
internal fun withForced(options: List<Variable>, forced: Map<String, String>): List<Variable> =
    options.map { v -> forced[v.key]?.let { Variable(v.key, it) } ?: v } +
        forced.filterKeys { k -> options.none { it.key == k } }.map { (k, v) -> Variable(k, v) }
