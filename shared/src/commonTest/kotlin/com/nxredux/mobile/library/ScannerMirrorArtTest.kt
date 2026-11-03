package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.scraper.ArtLogStore
import com.nxredux.mobile.scraper.ArtOrigin
import com.nxredux.mobile.scraper.ArtSlot
import com.nxredux.mobile.scraper.ArtworkController
import com.nxredux.mobile.scraper.FakeHost
import com.nxredux.mobile.scraper.LegacyArtMove
import com.nxredux.mobile.scraper.SourceId
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.MemorySidecarStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ScannerMirrorArtTest {
    private val home = ContentRef("Home")
    private val x1 = ExtraFolder("x1", ContentRef("sd/Ext"))
    private val mirror = "Home/Roms/.sources/x1/My Sega Genesis/.media"
    private val baseDirs = mapOf(
        "Home" to listOf("Roms"),
        "Home/Roms" to listOf(".sources"),
        "Home/Roms/.sources" to listOf("x1"),
        "Home/Roms/.sources/x1" to listOf("My Sega Genesis"),
        "Home/Roms/.sources/x1/My Sega Genesis" to listOf(".media"),
        mirror to listOf("Sonic.png", "boxart", "box2d", "grid", "hero"),   // grid/ and hero/: no longer read
        "$mirror/boxart" to listOf("Sonic.png"),
        "$mirror/box2d" to listOf("Sonic.jpg"),
        "$mirror/grid" to listOf("Sonic.jpg"),
        "$mirror/hero" to listOf("Sonic.jpg"),
        "sd/Ext" to listOf("My Sega Genesis"),
    )

    private suspend fun sonic(dirs: Map<String, List<String>>): GameEntry {
        val src = FakeRomsSource(dirs)
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, listOf(x1), emptyMap())
        return index.toSystems().flatMap { it.games }.single { it.fileName == "Sonic.md" }
    }

    @Test fun scannerReadsMirrorArt() = runTest {
        val game = sonic(baseDirs + ("sd/Ext/My Sega Genesis" to listOf("Sonic.md")))
        assertEquals(ContentRef("$mirror/boxart/Sonic.png"), game.boxartRef)
        assertEquals(ContentRef("$mirror/box2d/Sonic.jpg"), game.box2dRef)
        assertNull(game.screenshotRef)   // the mirror's untyped Sonic.png is not art
    }

    @Test fun ownWinsPerTypedSlot() = runTest {
        val own = "sd/Ext/My Sega Genesis/.media"
        val game = sonic(
            baseDirs + mapOf(
                "sd/Ext/My Sega Genesis" to listOf("Sonic.md", ".media"),
                own to listOf("Sonic.png", "screenshot", "box2d", "grid"),
                "$own/screenshot" to listOf("Sonic.png"),
                "$own/box2d" to listOf("Sonic.png"),
                "$own/grid" to listOf("Sonic.png"),
            ),
        )
        assertEquals(ContentRef("$mirror/boxart/Sonic.png"), game.boxartRef)   // mirror fills only what is missing
        assertEquals(ContentRef("$own/box2d/Sonic.png"), game.box2dRef)        // own box2d over the mirror's
        assertEquals(ContentRef("$own/screenshot/Sonic.png"), game.screenshotRef)
    }

    @Test fun aPickedScreenshotInTheMirrorWinsOverTheFoldersOwn() = runTest {
        // Change art writes a pick to the home mirror: for a picked game the mirror's screenshot wins, scan and art re-read.
        val own = "sd/Ext/My Sega Genesis/.media"
        val dirs = baseDirs + mapOf(
            "sd/Ext/My Sega Genesis" to listOf("Sonic.md", "Streets.md", ".media"),
            own to listOf("screenshot"),
            "$own/screenshot" to listOf("Sonic.png", "Streets.png"),
            mirror to listOf("screenshot"),
            "$mirror/screenshot" to listOf("Sonic.png", "Streets.png"),
        )
        val src = FakeRomsSource(dirs)
        val picked = GamePath.of(GameRef("x1", "My Sega Genesis", "Sonic.md"))
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }, picks = { mapOf(picked to setOf(ArtSlot.SCREENSHOT)) })
        val index = scanner.scan(home, listOf(x1), emptyMap())
        val games = index.toSystems().flatMap { it.games }
        assertEquals(ContentRef("$mirror/screenshot/Sonic.png"), games.single { it.fileName == "Sonic.md" }.screenshotRef)
        assertEquals(ContentRef("$own/screenshot/Streets.png"), games.single { it.fileName == "Streets.md" }.screenshotRef)   // not picked: own wins
        val art = scanner.readArt(index)
        assertEquals("$mirror/screenshot/Sonic.png", art.getValue(Triple("x1", "My Sega Genesis", "Sonic.md")).screenshot)
        assertEquals("$own/screenshot/Streets.png", art.getValue(Triple("x1", "My Sega Genesis", "Streets.md")).screenshot)
    }

    @Test fun pickedBoxPrefersMirror() = runTest {
        // A 2D box pick is written to the home mirror: for that slot the mirror's file wins; the others keep the folder's own.
        val own = "sd/Ext/My Sega Genesis/.media"
        val dirs = baseDirs + mapOf(
            "sd/Ext/My Sega Genesis" to listOf("Sonic.md", ".media"),
            own to listOf("boxart2d", "screenshot"),
            "$own/boxart2d" to listOf("Sonic.png"),
            "$own/screenshot" to listOf("Sonic.png"),
            mirror to listOf("boxart2d", "screenshot"),
            "$mirror/boxart2d" to listOf("Sonic.jpg"),
            "$mirror/screenshot" to listOf("Sonic.png"),
        )
        val src = FakeRomsSource(dirs)
        val picked = GamePath.of(GameRef("x1", "My Sega Genesis", "Sonic.md"))
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }, picks = { mapOf(picked to setOf(ArtSlot.BOX_2D)) })
        val index = scanner.scan(home, listOf(x1), emptyMap())
        val game = index.toSystems().flatMap { it.games }.single { it.fileName == "Sonic.md" }
        assertEquals(ContentRef("$mirror/boxart2d/Sonic.jpg"), game.box2dRef)
        assertEquals(ContentRef("$own/screenshot/Sonic.png"), game.screenshotRef)   // not picked: own wins
        val art = scanner.readArt(index).getValue(Triple("x1", "My Sega Genesis", "Sonic.md"))
        assertEquals("$mirror/boxart2d/Sonic.jpg", art.box2d)
        assertEquals("$own/screenshot/Sonic.png", art.screenshot)
    }

    @Test fun noMirrorWalksWhenTheExtraHasNoMirrorRoot() = runTest {
        // No `.sources/x1` yet (nothing scraped): `.sources` is looked up once and no folder of x1 is walked in it.
        val dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf(".sources"),
            "Home/Roms/.sources" to listOf("other"),
            "Home/Roms/.sources/other" to emptyList(),
            "sd/Ext" to listOf("A", "B", "C"),
            "sd/Ext/A" to listOf("Sonic.md"),
            "sd/Ext/B" to listOf("Streets.md"),
            "sd/Ext/C" to listOf("Comix.md"),
        )
        val counting = CountingRomsSource(FakeRomsSource(dirs))
        // Sidecars get no home, so every `.sources` call below is the scanner's art mirror (the sidecar copies have their own).
        val games = LibraryScanner(counting, Sidecars(counting, MemorySidecarStore()) { null }).scan(home, listOf(x1), emptyMap())
            .systems.flatMap { it.games }
        assertEquals(3, games.size)
        val mirrorCalls = counting.calls.filter { ".sources" in it }
        assertEquals(listOf("child:Home/Roms/.sources", "child:Home/Roms/.sources/x1"), mirrorCalls)
    }

    @Test fun homeGamesIgnoreSourcesTree() = runTest {
        // A home game named like a mirror file does not pick up .sources art.
        val src = FakeRomsSource(baseDirs + mapOf("Home/Roms" to listOf(".sources", "Sega Genesis (MD)"), "Home/Roms/Sega Genesis (MD)" to listOf("Sonic.md")))
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, emptyList(), emptyMap())
        assertNull(index.toSystems().flatMap { it.games }.single { it.fileName == "Sonic.md" }.boxartRef)
    }
    @Test fun heroFlagFromLog() = runTest {
        // A home game's screenshot logged as a SteamGridDB hero is a hero; an extra folder's own file of the same name
        // is not (only the mirror's is logged), while the mirror's own hero is.
        val gb = "Home/Roms/Game Boy (GB)"
        val own = "sd/Ext/My Sega Genesis/.media"
        val dirs = baseDirs + mapOf(
            "Home/Roms" to listOf(".sources", "Game Boy (GB)"),
            gb to listOf("G.gb", "H.gb", ".media"),
            "$gb/.media" to listOf("screenshot"),
            "$gb/.media/screenshot" to listOf("G.jpg", "H.png"),
            "sd/Ext/My Sega Genesis" to listOf("Sonic.md", "Streets.md", ".media"),
            own to listOf("screenshot"),
            "$own/screenshot" to listOf("Sonic.png"),
            mirror to listOf("screenshot"),
            "$mirror/screenshot" to listOf("Sonic.jpg", "Streets.jpg"),
        )
        val heroes = setOf(
            "Roms/Game Boy (GB)/.media/screenshot/G.jpg",
            "Roms/.sources/x1/My Sega Genesis/.media/screenshot/Sonic.jpg",
            "Roms/.sources/x1/My Sega Genesis/.media/screenshot/Streets.jpg",
        )
        val src = FakeRomsSource(dirs)
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }, heroShots = { heroes })
        val index = scanner.scan(home, listOf(x1), emptyMap())
        val games = index.toSystems().flatMap { it.games }.associateBy { it.fileName }
        assertTrue(games.getValue("G.gb").heroShot)
        assertFalse(games.getValue("H.gb").heroShot)
        assertEquals(ContentRef("$own/screenshot/Sonic.png"), games.getValue("Sonic.md").screenshotRef)
        assertFalse(games.getValue("Sonic.md").heroShot)       // the folder's own file, not the mirror's hero
        assertTrue(games.getValue("Streets.md").heroShot)      // the mirror's hero
        val art = scanner.readArt(index)
        assertTrue(art.getValue(Triple(GamePath.HOME, "Roms/Game Boy (GB)", "G.gb")).heroShot)
        assertFalse(art.getValue(Triple(GamePath.HOME, "Roms/Game Boy (GB)", "H.gb")).heroShot)
        assertFalse(art.getValue(Triple("x1", "My Sega Genesis", "Sonic.md")).heroShot)
        assertTrue(art.getValue(Triple("x1", "My Sega Genesis", "Streets.md")).heroShot)
        // A picked extra game shows the mirror's screenshot, and so its hero flag.
        val picked = LibraryScanner(
            src, Sidecars(src, MemorySidecarStore()) { home }, heroShots = { heroes },
            picks = { mapOf(GamePath.of(GameRef("x1", "My Sega Genesis", "Sonic.md")) to setOf(ArtSlot.SCREENSHOT)) },
        ).scan(home, listOf(x1), emptyMap()).toSystems().flatMap { it.games }.single { it.fileName == "Sonic.md" }
        assertTrue(picked.heroShot)
    }

    @Test fun moveRunsOnScan() = runTest {
        val gb = "Home/Roms/Game Boy (GB)"
        val src = FakeRomsSource(
            mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Game Boy (GB)"),
                gb to listOf("G.gb", ".media"),
                "$gb/.media" to listOf("grid", "hero"),
                "$gb/.media/grid" to listOf("G.png"),
                "$gb/.media/hero" to listOf("G.jpg"),
            ),
        )
        val host = FakeHost(emptyList())
        val log = ArtLogStore(host, ArtworkController.LOG_FILE)
        log.add(listOf("Roms/Game Boy (GB)/.media/hero/G.jpg" to ArtOrigin(SourceId.SGDB)))
        val move = LegacyArtMove(src, log, host)
        val scanner = LibraryScanner(
            src, Sidecars(src, MemorySidecarStore()) { home }, legacyMove = { move },
            heroShots = { if (log.contains("Roms/Game Boy (GB)/.media/screenshot/G.jpg")) setOf("Roms/Game Boy (GB)/.media/screenshot/G.jpg") else emptySet() },
        )
        val index = scanner.scan(home, emptyList(), emptyMap())
        val g = index.toSystems().flatMap { it.games }.single { it.fileName == "G.gb" }
        assertEquals(ContentRef("$gb/.media/boxart2d/G.png"), g.box2dRef)
        assertEquals(ContentRef("$gb/.media/screenshot/G.jpg"), g.screenshotRef)
        assertTrue(g.heroShot)   // the log as it is after the move
        assertNull(src.childNames("$gb/.media/grid"))
        assertTrue(src.childNames("$gb/.media")!!.none { it == "hero" })
    }
}
