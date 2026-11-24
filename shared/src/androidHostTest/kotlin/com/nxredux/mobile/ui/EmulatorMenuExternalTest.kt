package com.nxredux.mobile.ui

import com.nxredux.mobile.external.ExternalApps
import com.nxredux.mobile.external.ExternalEmulator
import com.nxredux.mobile.external.ExternalLaunch
import com.nxredux.mobile.external.ExternalLaunchResult
import com.nxredux.mobile.external.GamePass
import com.nxredux.mobile.external.SetRule
import com.nxredux.mobile.external.externalPickFor
import com.nxredux.mobile.external.installedExternal
import com.nxredux.mobile.external.offeredFor
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.Collections
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.ExternalPicksCodec
import com.nxredux.mobile.settings.FakeAppSettings
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.MemorySidecarStore
import com.nxredux.mobile.storage.NoUserData
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Installed standalone emulators in a game's Emulator menu, and the per-game pick that menu stores. */
@OptIn(ExperimentalCoroutinesApi::class)
class EmulatorMenuExternalTest {
    // Test entries: the menu and the pick never depend on which apps the shipped table lists.
    private fun app(id: String, name: String, tags: Set<String>, pass: GamePass = GamePass.VIEW_DATA) = ExternalEmulator(
        id = id, name = name, packages = listOf("test.$id"), activity = "test.$id.Main", pass = pass,
        extraKey = if (pass == GamePass.VIEW_DATA) null else "path", tags = tags,
    )
    private val duckstation = app("duckstation", "DuckStation", setOf("PS"))
    private val epsxe = app("epsxe", "ePSXe", setOf("PS"))
    private val ppsspp = app("ppsspp", "PPSSPP", setOf("PSP"))
    private val pathOnly = app("pathonly", "Path Only", setOf("PS"), GamePass.PATH_EXTRA)
    private val saturnA = app("saturn-a", "Saturn A", setOf("SATURN"))
    private val saturnB = app("saturn-b", "Saturn B", setOf("SATURN"))
    private val table = listOf(duckstation, epsxe, ppsspp, pathOnly, saturnA, saturnB)

    /** [installed]: the apps the device has, each labelled by its own name. */
    private class Apps(var installed: Set<ExternalEmulator>) : ExternalApps {
        override fun installedPackage(e: ExternalEmulator): String? = e.packages.first().takeIf { e in installed }
        override fun label(packageId: String): String? = installed.firstOrNull { it.packages.first() == packageId }?.name
        override suspend fun launch(request: ExternalLaunch) = ExternalLaunchResult.FAILED
    }

    private class Settings : FakeAppSettings(ContentRef("Home")) {
        override var externalPicks: Map<String, String> = emptyMap()
    }

    private fun system(tag: String, games: List<GameEntry> = emptyList()) = SystemFolder(tag, tag, CoreRegistry.forTag(tag), games)

    private fun game(file: String, ref: ContentRef = ContentRef("Home/Roms/PS/$file")) = GameEntry(
        ref = ref, fileName = file, shownName = file.substringBeforeLast('.'),
        folder = ContentRef("Home/Roms/PS"), sourceId = Recents.HOME, relativeDir = "PS",
    )

    private fun everyOffered(@Suppress("UNUSED_PARAMETER") e: ExternalEmulator) = true

    @Test fun menuListsCoresThenInstalledApps() {
        val apps = installedExternal("PS", Apps(setOf(duckstation)), ::everyOffered, table)
        val m = assertNotNull(ContextMenus.emulatorMenu(system("PS"), apps, pick = null))
        assertEquals(listOf("PS (pcsx_rearmed)", "PSX (swanstation)", "DuckStation (app)"), m.labels)
        assertEquals(listOf("PS", "PSX", "app:duckstation"), m.values)
        assertEquals(0, m.current)
        val picked = assertNotNull(ContextMenus.emulatorMenu(system("PS"), apps, pick = "duckstation"))
        assertEquals(2, picked.current)
        assertEquals(2, picked.index)
    }

    @Test fun uninstalledAppsNeverAppear() {
        val apps = installedExternal("PS", Apps(setOf(duckstation)), ::everyOffered, table)
        assertEquals(listOf(duckstation), apps.map { it.first })
        val labels = ContextMenus.emulatorMenu(system("PS"), apps, pick = null)!!.labels
        assertTrue(labels.none { "ePSXe" in it }, "$labels")
        // A stored pick of an app that has gone marks our tag, never a stale entry.
        assertEquals(0, ContextMenus.emulatorMenu(system("PS"), apps, pick = "epsxe")!!.current)
    }

    @Test fun singleCoreConsoleGetsMenuWhenAnAppIsInstalled() {
        val apps = installedExternal("PSP", Apps(setOf(ppsspp)), ::everyOffered, table)
        val m = assertNotNull(ContextMenus.emulatorMenu(system("PSP"), apps, pick = null))
        assertEquals(listOf("PSP (ppsspp)", "PPSSPP (app)"), m.labels)
        assertNull(ContextMenus.emulatorMenu(system("PSP"), installedExternal("PSP", Apps(emptySet()), ::everyOffered, table), pick = null))
        val psp = Listed(system("PSP"), game("Daxter.iso"))
        assertTrue(ContextKind.EMULATOR in ContextMenus.itemsFor(ListSource.Console(psp.system), psp, false, externalApps = true).map { it.kind })
        assertTrue(ContextKind.EMULATOR !in ContextMenus.itemsFor(ListSource.Console(psp.system), psp, false).map { it.kind })
        // A console without a core lists only its apps; the first is the one that opens the game without a pick.
        val saturn = assertNotNull(ContextMenus.emulatorMenu(system("SATURN"), installedExternal("SATURN", Apps(setOf(saturnA, saturnB)), ::everyOffered, table), pick = null))
        assertEquals(listOf("Saturn A (app)", "Saturn B (app)"), saturn.labels)
        assertEquals(0, saturn.current)
    }

    @Test fun picksSurviveTheStoredString() {
        val picks = mapOf("Home/Roms/PS/Crash\t\"1\".cue" to "duckstation", "ext/Loose/a\nb.iso" to "epsxe")
        assertEquals(picks, ExternalPicksCodec.decode(ExternalPicksCodec.encode(picks)))
        assertEquals(emptyMap(), ExternalPicksCodec.decode(null))
        assertEquals(emptyMap(), ExternalPicksCodec.decode("not json"))
    }

    @Test fun pickingAnAppStoresIt_pickingACoreClearsIt() = runTest {
        val crash = game("Crash.cue")
        val ps = system("PS", listOf(crash))
        val entry = Listed(ps, crash)
        val source = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("PS"), "Home/Roms/PS" to listOf("Crash.cue")))
        val sidecarStore = MemorySidecarStore()
        val settings = Settings()
        val switched = ArrayList<String>()
        val screen = MutableStateFlow<Screen>(Screen.Games(ListSource.Console(ps), listOf(entry)))
        val menus = controller(screen, settings, source, sidecarStore, switched) { installedExternal(it.system.tag, Apps(setOf(duckstation)), ::everyOffered, table) }
        fun pick(value: String) {
            menus.openContextMenu(0); advanceUntilIdle()
            val m = assertNotNull(screen.value.contextMenu)
            menus.menuSelect(m.items.indexOfFirst { it.kind == ContextKind.EMULATOR }); menus.menuOpen(); advanceUntilIdle()
            val sub = assertNotNull(screen.value.contextMenu?.sub)
            menus.menuSelect(sub.values.indexOf(value)); menus.menuOpen(); advanceUntilIdle()
        }

        pick("app:duckstation")
        assertEquals(mapOf(crash.path to "duckstation"), settings.externalPicks)
        assertNull(source.textAt("Home/Roms/PS/systems.txt"))   // the sidecar the handheld shares is untouched
        assertTrue(sidecarStore.map.isEmpty())
        assertTrue(switched.isEmpty())
        assertNull(assertIs<Screen.Games>(screen.value).menu)

        pick("PSX")
        assertEquals(emptyMap(), settings.externalPicks)
        assertEquals("Crash.cue\tPSX\n", source.textAt("Home/Roms/PS/systems.txt"))
        assertEquals(listOf("PSX"), switched)

        // Back on the game's own tag: the pick goes and nothing is rewritten.
        settings.externalPicks = mapOf(crash.path to "duckstation")
        screen.value = Screen.Games(ListSource.Console(ps), listOf(entry))
        pick("PS")
        assertEquals(emptyMap(), settings.externalPicks)
        assertEquals(listOf("PSX"), switched)
    }

    /** The menu controller over [screen] with a host that records [switched] tags and answers [externalApps] with [lookup]. */
    private fun TestScope.controller(
        screen: MutableStateFlow<Screen>, settings: Settings, source: FakeRomsSource, sidecarStore: MemorySidecarStore,
        switched: MutableList<String>, saved: Boolean = false, elsewhere: (Listed) -> Boolean = { false },
        lookup: (Listed) -> List<Pair<ExternalEmulator, String>>,
    ): ContextMenuController {
        val host = object : ContextMenuHost {
            override val pins: List<Shortcut> = emptyList()
            override val toolPins: List<String> = emptyList()
            override suspend fun writeToolPins(keys: List<String>) = true
            override fun select(index: Int) {}
            override fun showNotice(text: String) {}
            override suspend fun rebuildMainRows() {}
            override fun collectionChanged(name: String) {}
            override fun selectCollection(name: String) {}
            override fun menuClosed() {}
            override suspend fun refreshOpenList(select: String?) {}
            override fun rescan() {}
            override suspend fun applyHidden(entry: Listed, hidden: Boolean, closeMenu: Boolean) {}
            override suspend fun renameInIndex(game: GameEntry, name: String) = true
            override fun openGameSettings(entry: Listed) {}
            override fun finishSwitch(game: GameEntry, from: SystemFolder, tag: String) { switched += tag }
            override suspend fun externalApps(entry: Listed) = lookup(entry)
            override suspend fun canResume(entry: Listed) = saved
            override suspend fun opensElsewhere(entry: Listed) = elsewhere(entry)
        }
        val home = { ContentRef("Home") as ContentRef? }
        return ContextMenuController(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)), screen, MutableStateFlow(ListSelection(0, 1)), MutableStateFlow(null),
            NoUserData, settings, Sidecars(source, sidecarStore, home), Collections(source, sidecarStore, home), Mutex(), host,
        )
    }

    @Test fun aFailedAppLookupStillOpensOurCores() = runTest {
        val crash = game("Crash.cue")
        val ps = system("PS", listOf(crash))
        val source = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("PS"), "Home/Roms/PS" to listOf("Crash.cue")))
        val screen = MutableStateFlow<Screen>(Screen.Games(ListSource.Console(ps), listOf(Listed(ps, crash))))
        val menus = controller(screen, Settings(), source, MemorySidecarStore(), ArrayList()) { throw IllegalStateException("package manager died") }
        menus.openContextMenu(0); advanceUntilIdle()
        val m = assertNotNull(screen.value.contextMenu)                  // the game menu opens without the apps
        menus.menuSelect(m.items.indexOfFirst { it.kind == ContextKind.EMULATOR }); menus.menuOpen(); advanceUntilIdle()
        val open = assertNotNull(screen.value.contextMenu)
        assertEquals(false, open.busy)
        assertEquals(listOf("PS", "PSX"), assertNotNull(open.sub).values)
    }

    @Test fun stalePickFallsBack() = runTest {
        val crash = game("Crash.cue")
        val settings = Settings().apply { externalPicks = mapOf(crash.path to "epsxe", "Home/Roms/SATURN/Nights.cue" to "saturn-a") }
        val apps = Apps(setOf(duckstation))
        // A cored console: our core again.
        assertNull(externalPickFor(crash.path, "PS", settings, apps, ::everyOffered, table))
        apps.installed = setOf(epsxe)
        assertEquals(epsxe, externalPickFor(crash.path, "PS", settings, apps, ::everyOffered, table))
        // A console with no core: the next installed app in the table's order.
        apps.installed = setOf(saturnB)
        assertEquals(saturnB, externalPickFor("Home/Roms/SATURN/Nights.cue", "SATURN", settings, apps, ::everyOffered, table))
        apps.installed = emptySet()
        assertNull(externalPickFor("Home/Roms/SATURN/Nights.cue", "SATURN", settings, apps, ::everyOffered, table))
    }

    @Test fun pathOnlyPickIgnoredWithoutRealFile() = runTest {
        val saf = game("Crash.cue")                                            // a SAF document: no real path
        val real = game("Crash.cue", FileRefs.of("/storage/emulated/0/Roms/PS/Crash.cue"))
        val zipped = game("Crash.zip", FileRefs.of("/storage/emulated/0/Roms/PS/Crash.zip"))
        val apps = Apps(setOf(pathOnly))
        val settings = Settings().apply { externalPicks = mapOf(saf.path to "pathonly") }
        assertNull(externalPickFor(saf.path, "PS", settings, apps, offeredFor(saf, NaomiSets.EMPTY, realPath = null), table))
        assertEquals(pathOnly, externalPickFor(saf.path, "PS", settings, apps, offeredFor(real, NaomiSets.EMPTY, realPath = FileRefs.path(real.ref)), table))
        assertNull(externalPickFor(saf.path, "PS", settings, apps, offeredFor(zipped, NaomiSets.EMPTY, realPath = FileRefs.path(zipped.ref)), table))
        assertTrue(installedExternal("PS", apps, offeredFor(saf, NaomiSets.EMPTY, realPath = null), table).isEmpty())
    }

    @Test fun model3AppOfferedOnlyForModel3Sets() {
        val super3 = ExternalEmulator(
            id = "super3", name = "SUPER3", packages = listOf("test.super3"), activity = "test.super3.Main",
            pass = GamePass.VIEW_DATA, tags = setOf("FBN"), sets = SetRule.MODEL3,
        )
        assertTrue(offeredFor(game("scud.zip"), NaomiSets.EMPTY, realPath = null)(super3))
        assertTrue(offeredFor(game("Vf3.7z"), NaomiSets.EMPTY, realPath = null)(super3))
        assertFalse(offeredFor(game("sf2.zip"), NaomiSets.EMPTY, realPath = null)(super3))
        assertTrue(offeredFor(game("sf2.zip"), NaomiSets.EMPTY, realPath = null)(app("all", "All", setOf("FBN"))))
    }

    // The Play build (no All files access) and a file in the app's own storage: a real file, but no path to give.
    @Test fun pathOnlyPickNeedsAPathToGive() = runTest {
        val real = game("Crash.cue", FileRefs.of("/storage/emulated/0/Roms/PS/Crash.cue"))
        val apps = Apps(setOf(pathOnly, duckstation))
        val settings = Settings().apply { externalPicks = mapOf(real.path to "pathonly") }
        val noPath = offeredFor(real, NaomiSets.EMPTY, realPath = null)
        assertNull(externalPickFor(real.path, "PS", settings, apps, noPath, table))
        assertEquals(listOf(duckstation), installedExternal("PS", apps, noPath, table).map { it.first })
    }

    // Our save state is never what the other app opens: no Play from start for a game picked to open there.
    @Test fun aGameThatOpensElsewhereHasNoSaveStateItems() = runTest {
        val crash = game("Crash.cue")
        val ps = system("PS", listOf(crash))
        val source = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("PS"), "Home/Roms/PS" to listOf("Crash.cue")))
        var elsewhere = false
        val screen = MutableStateFlow<Screen>(Screen.Games(ListSource.Console(ps), listOf(Listed(ps, crash))))
        val menus = controller(screen, Settings(), source, MemorySidecarStore(), ArrayList(), saved = true, elsewhere = { elsewhere }) { emptyList() }
        fun kinds(): List<ContextKind> {
            menus.closeContextMenu()
            menus.openContextMenu(0)
            return assertNotNull(screen.value.contextMenu).items.map { it.kind }
        }
        assertEquals(ContextKind.PLAY_FROM_START, kinds().first())
        elsewhere = true
        val kinds = kinds()
        assertTrue(ContextKind.PLAY_FROM_START !in kinds && ContextKind.RESUME !in kinds, "$kinds")
        assertTrue(listOf(ContextKind.PIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION).all { it in kinds }, "$kinds")
    }

    // A console only other apps run still has Game Settings: the page that says where the game opens.
    @Test fun aConsoleWithoutACoreOffersGameSettings() {
        val zelda = Listed(system("SWITCH"), game("Zelda.nsp"))
        assertTrue(ContextKind.GAME_SETTINGS in ContextMenus.itemsFor(ListSource.Console(zelda.system), zelda, false).map { it.kind })
    }
}
