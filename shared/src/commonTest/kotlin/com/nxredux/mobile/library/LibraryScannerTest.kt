package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.MemorySidecarStore
import com.nxredux.mobile.storage.Recents
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.test.runTest

class LibraryScannerTest {
    private val home = ContentRef("Home")
    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms", "Bios", "Saves"),
            "Home/Roms" to listOf("Nintendo DS (NDS)", "Commodore 64 (C64)", ".media", "Ports", "Game Boy (GB)", "Game Boy Advance (GBA)", "Game Boy Advance (MGBA)"),
            "Home/Roms/.media" to listOf("Nintendo DS (NDS).png"),
            "Home/Roms/Nintendo DS (NDS)" to listOf("Mario Kart DS.nds", "map.txt", ".media", "Contra 4.nds", "notes.txt", "Old.nds.disabled", "Extras"),
            "Home/Roms/Nintendo DS (NDS)/.media" to listOf("Mario Kart DS.png", "bg.png", "boxart", "screenshot"),
            "Home/Roms/Nintendo DS (NDS)/.media/boxart" to listOf("Mario Kart DS.png"),
            "Home/Roms/Nintendo DS (NDS)/.media/screenshot" to listOf("Mario Kart DS.png"),
            "Home/Roms/Nintendo DS (NDS)/Extras" to listOf("Hidden.nds"),
            "Home/Roms/Commodore 64 (C64)" to listOf("Boulder Dash 2.chd", "readme.txt"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Tetris DX.gbc"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba", "Zelda.gba", "systems.txt"),
            "Home/Roms/Game Boy Advance (MGBA)" to emptyList(),
            "Home/Ports" to emptyList(),
            // Not the home's Tetris.gb (an extra's copy of a home file is hidden): a revision, shown as "Tetris" too.
            "sd/Loose" to listOf("Tetris (Rev 1).gb", "b", "Kirby.gba"),
            "sd/Loose/b" to listOf("Tetris (Rev 1).gb", ".media"),
            "sd/Loose/b/.media" to listOf("Tetris (Rev 1).png", "boxart"),
            "sd/Loose/b/.media/boxart" to listOf("Tetris (Rev 1).png"),
            "sd/Mixed" to listOf("thing.chd"),
        ),
        texts = mapOf(
            "Home/Roms/Nintendo DS (NDS)/map.txt" to "Contra 4.nds\tContra IV\n",
            "Home/Roms/Game Boy Advance (GBA)/systems.txt" to "Zelda.gba\tMGBA\n",
        ),
    )
    private val scanner = LibraryScanner(source, Sidecars(source, MemorySidecarStore()) { home })
    private val loose = ExtraFolder("e1", ContentRef("sd/Loose"))
    private val mixed = ExtraFolder("e2", ContentRef("sd/Mixed"))

    private suspend fun systems(extras: List<ExtraFolder> = emptyList()) = scanner.scan(home, extras, emptyMap()).toSystems()

    @Test fun leftover_part_files_are_neither_games_nor_sidecars() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Commodore 64 (C64)", "Game Boy (GB)"),
                "Home/Roms/Commodore 64 (C64)" to listOf("Boulder Dash.chd", "Boulder Dash.chd.part", "map.txt.part"),   // no core: every file lists
                "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", "map.txt.part", "systems.txt.part"),
            ),
            texts = mapOf(
                "Home/Roms/Game Boy (GB)/map.txt.part" to "Tetris.gb\tHalf Written\n",
                "Home/Roms/Game Boy (GB)/systems.txt.part" to "Tetris.gb\tGBA\n",
            ),
        )
        val systems = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, emptyList(), emptyMap()).toSystems()
        assertEquals(listOf("C64", "GB"), systems.map { it.tag })
        assertEquals(listOf(1, 1), systems.map { it.gameCount })
        val tetris = systems[1].games.single()
        assertEquals("Tetris", tetris.shownName)   // the part's alias is not read
    }

    @Test fun home_systems_are_tagged_folders_sorted_by_display_name_with_counts_and_art() = runTest {
        val systems = systems()
        assertEquals(listOf("Commodore 64", "Game Boy", "Game Boy Advance", "Game Boy Advance", "Nintendo DS"), systems.map { it.displayName })
        assertEquals(listOf("C64", "GB", "GBA", "MGBA", "NDS"), systems.map { it.tag })
        assertEquals(listOf(2, 2, 1, 1, 2), systems.map { it.gameCount })   // C64: no core -> all files; GB lists Tetris DX.gbc; Zelda moved to MGBA by systems.txt
        assertNull(systems[0].core)
        assertEquals("melondsds", systems[4].core!!.id)
        assertEquals(ContentRef("Home/Roms/.media/Nintendo DS (NDS).png"), systems[4].artRef)
        assertEquals(ContentRef("Home/Roms/Nintendo DS (NDS)/.media/bg.png"), systems[4].bgRef)
        assertNull(systems[1].bgRef)
        assertEquals("Nintendo DS (NDS)", systems[4].folderName)
    }

    @Test fun systems_sharing_a_display_name_sort_by_tag_whatever_the_listing_order() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy Advance (MGBA)", "Game Boy Advance (GBA)"),
            "Home/Roms/Game Boy Advance (MGBA)" to listOf("Zelda.gba", "systems.txt"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
        ), texts = mapOf("Home/Roms/Game Boy Advance (MGBA)/systems.txt" to "Zelda.gba\tMGBA\n"))
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, emptyList(), emptyMap())
        assertEquals(listOf("GBA", "MGBA"), index.systems.map { it.tag })
    }

    @Test fun scan_fills_typed_art_and_map_names_per_game() = runTest {
        val nds = scanner.scan(home, emptyList(), emptyMap()).systems.first { it.tag == "NDS" }
        val dir = "Roms/Nintendo DS (NDS)"
        assertEquals(
            listOf(
                IndexedGame(Recents.HOME, dir, "Contra 4.nds", "Home/$dir/Contra 4.nds", "Home/$dir", "Contra IV"),
                IndexedGame(
                    Recents.HOME, dir, "Mario Kart DS.nds", "Home/$dir/Mario Kart DS.nds", "Home/$dir", "Mario Kart DS",
                    boxart = "Home/$dir/.media/boxart/Mario Kart DS.png",
                    screenshot = "Home/$dir/.media/screenshot/Mario Kart DS.png",
                ),
            ),
            nds.games,
        )
    }

    @Test fun ignoresUntypedMediaPng() = runTest {
        // NX Redux's mix `.media/<name>.png` is not art for a game: only the typed folders are read.
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", ".media"),
            "H/Roms/Game Boy (GB)/.media" to listOf("Tetris.png"),
        ))
        assertEquals(IndexedGame(Recents.HOME, "Roms/Game Boy (GB)", "Tetris.gb", "H/Roms/Game Boy (GB)/Tetris.gb", "H/Roms/Game Boy (GB)", "Tetris"), index.systems.single().games.single())
    }

    @Test fun boxart2dWinsOverTheLegacyBox2dAndTheWheelAndMixAreKnown() = runTest {
        val gb = "H/Roms/Game Boy (GB)/.media"
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb", ".media"),
            gb to listOf("box2d", "boxart2d", "wheel", "mix"),
            "$gb/box2d" to listOf("Tetris.jpg", "Zelda.png"),
            "$gb/boxart2d" to listOf("Tetris.png"),
            "$gb/wheel" to listOf("Tetris.png"),
            "$gb/mix" to listOf("Zelda.png"),
        ))
        val games = index.systems.single().games.associateBy { it.file }
        assertEquals("$gb/boxart2d/Tetris.png", games.getValue("Tetris.gb").box2d)   // NX Redux's folder first
        assertEquals("$gb/box2d/Zelda.png", games.getValue("Zelda.gb").box2d)        // the legacy folder still read
        assertEquals("$gb/wheel/Tetris.png" to null, games.getValue("Tetris.gb").let { it.wheel to it.mix })
        assertEquals(null to "$gb/mix/Zelda.png", games.getValue("Zelda.gb").let { it.wheel to it.mix })
        val zelda = index.toSystems().single().games.single { it.fileName == "Zelda.gb" }
        assertEquals("$gb/mix/Zelda.png", zelda.mixRef?.value)
        val tetris = index.toSystems().single().games.single { it.fileName == "Tetris.gb" }
        assertEquals("$gb/wheel/Tetris.png", tetris.wheelRef?.value)
    }

    // Art deleted or added outside the app after the scan: readArt reads each game's art again from its folder, and the
    // index takes it (withArt); an extra folder's game too. Nothing changed: the same index back.
    @Test fun read_art_follows_files_deleted_and_added_since_the_scan() = runTest {
        val gb = "H/Roms/Game Boy (GB)/.media"
        val src = FakeRomsSource(dirs = mapOf(
            "H" to listOf("Roms"),
            "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb", ".media"),
            gb to listOf("boxart", "screenshot"),
            "$gb/boxart" to listOf("Tetris.png"),
            "$gb/screenshot" to listOf("Tetris.png"),
            "sd/Loose" to listOf("Kirby.gb", ".media"),
            "sd/Loose/.media" to listOf("boxart"),
            "sd/Loose/.media/boxart" to listOf("Kirby.png"),
        ))
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
        val index = scanner.scan(ContentRef("H"), listOf(ExtraFolder("e1", ContentRef("sd/Loose"))), emptyMap())
        assertTrue(index.withArt(scanner.readArt(index)) === index)   // nothing changed on disk

        src.delete("$gb/boxart/Tetris.png")
        src.delete("$gb/screenshot/Tetris.png")
        src.delete("sd/Loose/.media/boxart/Kirby.png")
        assertTrue(src.writeText(ContentRef("$gb/boxart"), "Zelda.png", ""))   // added by a computer meanwhile
        val fresh = index.withArt(scanner.readArt(index))
        val games = fresh.systems.single().games.associateBy { it.file }
        assertEquals(null to null, games.getValue("Tetris.gb").let { it.boxart to it.screenshot })
        assertEquals("$gb/boxart/Zelda.png", games.getValue("Zelda.gb").boxart)
        assertNull(games.getValue("Kirby.gb").boxart)
        assertEquals(index.systems.single().games.map { it.file }, fresh.systems.single().games.map { it.file })   // order kept
    }

    // Coming back to the app (reuse): a typed art folder whose modified time is what the scan saw is not listed again;
    // one whose time moved (a file added or deleted bumps it) is, and so is one whose provider reports no time. Without
    // reuse (opening Artwork) every folder is listed. The art read is the same either way.
    @Test fun read_art_with_reuse_lists_only_the_art_folders_that_changed() = runTest {
        val gb = "H/Roms/Game Boy (GB)/.media"
        val fake = FakeRomsSource(dirs = mapOf(
            "H" to listOf("Roms"),
            "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb", ".media"),
            gb to listOf("boxart", "screenshot", "wheel"),
            "$gb/boxart" to listOf("Tetris.png"),
            "$gb/screenshot" to listOf("Tetris.png"),
            "$gb/wheel" to listOf("Tetris.png"),
        )).apply { stamps["$gb/boxart"] = 1_000L; stamps["$gb/screenshot"] = 1_000L }   // wheel: no time
        val src = CountingRomsSource(fake)
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
        val index = scanner.scan(ContentRef("H"), emptyList(), emptyMap())
        fun subListings() = src.calls.filter { it.startsWith("listChildren:$gb/") }.toSet()

        src.calls.clear()
        assertTrue(index.withArt(scanner.readArt(index, reuse = true)) === index)
        assertEquals(setOf("listChildren:$gb/wheel"), subListings())   // only the folder with no time

        assertTrue(fake.writeText(ContentRef("$gb/boxart"), "Zelda.png", ""))
        fake.stamps["$gb/boxart"] = 2_000L
        src.calls.clear()
        val fresh = index.withArt(scanner.readArt(index, reuse = true))
        assertEquals(setOf("listChildren:$gb/boxart", "listChildren:$gb/wheel"), subListings())
        assertEquals("$gb/boxart/Zelda.png", fresh.systems.single().games.single { it.file == "Zelda.gb" }.boxart)

        src.calls.clear()
        assertTrue(fresh.withArt(scanner.readArt(fresh, reuse = true)) === fresh)   // the changed folder's new map is kept
        assertEquals(setOf("listChildren:$gb/wheel"), subListings())

        src.calls.clear()
        assertTrue(fresh.withArt(scanner.readArt(fresh)) === fresh)
        assertEquals(setOf("listChildren:$gb/boxart", "listChildren:$gb/screenshot", "listChildren:$gb/wheel"), subListings())
    }

    @Test fun readsEveryTypedFolder() = runTest {
        val gb = "H/Roms/Game Boy (GB)/.media"
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb", ".media"),
            gb to listOf("Tetris.png", "boxart", "box2d", "screenshot", "grid", "hero"),
            "$gb/boxart" to listOf("Tetris.png"),
            "$gb/box2d" to listOf("Tetris.jpg", "Zelda.png"),
            "$gb/screenshot" to listOf("Tetris.png"),
            "$gb/grid" to listOf("Tetris.jpg"),
            "$gb/hero" to listOf("Tetris.jpg", "Zelda.jpeg"),
        ))
        val games = index.systems.single().games.associateBy { it.file }
        val tetris = games.getValue("Tetris.gb")
        assertEquals("$gb/boxart/Tetris.png", tetris.boxart)
        assertEquals("$gb/box2d/Tetris.jpg", tetris.box2d)   // a .jpg is read
        assertEquals("$gb/screenshot/Tetris.png", tetris.screenshot)
        val zelda = games.getValue("Zelda.gb")
        assertEquals(listOf(null, "$gb/box2d/Zelda.png", null), listOf(zelda.boxart, zelda.box2d, zelda.screenshot))   // its hero is not read
        val entry = index.toSystems().single().games.single { it.fileName == "Tetris.gb" }   // the lists get every slot
        assertEquals(
            listOf("$gb/boxart/Tetris.png", "$gb/box2d/Tetris.jpg", "$gb/screenshot/Tetris.png"),
            listOf(entry.boxartRef, entry.box2dRef, entry.screenshotRef).map { it?.value },
        )
    }

    @Test fun gridAndHeroNotRead() = runTest {
        val gb = "H/Roms/Game Boy (GB)/.media"
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("G.gb", ".media"),
            gb to listOf("grid", "hero"),
            "$gb/grid" to listOf("G.png"),
            "$gb/hero" to listOf("G.png"),
        ))
        assertEquals(ArtRefs(), index.systems.single().games.single().artRefs)
    }

    @Test fun systemArtAndBgStillIndexed() = runTest {
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Game Boy (GB)", ".media"),
            "H/Roms/.media" to listOf("Game Boy (GB).png"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", ".media"),
            "H/Roms/Game Boy (GB)/.media" to listOf("bg.png"),
        ))
        val gb = index.systems.single()
        assertEquals("H/Roms/.media/Game Boy (GB).png", gb.art)
        assertEquals("H/Roms/Game Boy (GB)/.media/bg.png", gb.bg)
    }

    @Test fun games_convert_to_entries_that_carry_their_folder() = runTest {
        val games = systems().first { it.tag == "NDS" }.games
        assertEquals(listOf("Contra IV", "Mario Kart DS"), games.map { it.shownName })
        val mk = games[1]
        assertEquals(ContentRef("Home/Roms/Nintendo DS (NDS)/Mario Kart DS.nds"), mk.ref)
        assertEquals(ContentRef("Home/Roms/Nintendo DS (NDS)/.media/boxart/Mario Kart DS.png"), mk.boxartRef)
        assertEquals(ContentRef("Home/Roms/Nintendo DS (NDS)/.media/screenshot/Mario Kart DS.png"), mk.screenshotRef)
        assertEquals(ContentRef("Home/Roms/Nintendo DS (NDS)"), mk.folder)
        assertEquals(Recents.HOME, mk.sourceId)
        assertEquals("Roms/Nintendo DS (NDS)", mk.relativeDir)
        assertNull(mk.subtitle)
    }

    @Test fun a_home_sidecar_moves_a_file_to_another_tag_of_the_same_console() = runTest {
        val systems = systems()
        assertEquals(listOf("Mario"), systems.first { it.tag == "GBA" }.games.map { it.shownName })
        val mgba = systems.first { it.tag == "MGBA" }.games.single()
        assertEquals("Zelda.gba", mgba.fileName)
        assertEquals(ContentRef("Home/Roms/Game Boy Advance (GBA)"), mgba.folder)
    }

    private suspend fun gbaFolderScan(folder: String, systems: String?, defaults: Map<String, String>): List<IndexedSystem> {
        val src = FakeRomsSource(
            dirs = mapOf("H" to listOf("Roms"), "H/Roms" to listOf(folder), "H/Roms/$folder" to listOfNotNull("Game.gba", systems)),
            texts = systems?.let { mapOf("H/Roms/$folder/systems.txt" to "Game.gba\tGBA\n") } ?: emptyMap(),
        )
        return LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }).scan(ContentRef("H"), emptyList(), defaults).systems
    }

    @Test fun a_console_folder_game_runs_under_the_default_emulator() = runTest {
        val system = gbaFolderScan("Game Boy Advance (GBA)", null, mapOf("gba" to "MGBA")).single { it.games.isNotEmpty() }
        assertEquals("MGBA", system.tag)
        assertEquals("Game Boy Advance (GBA)", system.folderName)
        assertEquals(listOf("Game.gba"), system.games.map { it.file })
    }

    @Test fun a_sidecar_tag_beats_the_default_emulator() = runTest {
        val system = gbaFolderScan("Game Boy Advance (GBA)", "systems.txt", mapOf("gba" to "MGBA")).single { it.games.isNotEmpty() }
        assertEquals("GBA", system.tag)
    }

    @Test fun an_emulator_tagged_folder_runs_the_console_default_not_its_tag() = runTest {
        // A folder's tag names only its console: "Game Boy Advance (MGBA)" games run the Default emulators pick.
        val system = gbaFolderScan("Game Boy Advance (MGBA)", null, emptyMap()).single { it.games.isNotEmpty() }
        assertEquals("GBA", system.tag)
        assertEquals("Game Boy Advance (MGBA)", system.folderName)   // the console's only folder still names the system
        assertEquals("MGBA", gbaFolderScan("Game Boy Advance (MGBA)", null, mapOf("gba" to "MGBA")).single { it.games.isNotEmpty() }.tag)
    }

    @Test fun no_default_means_the_console_tag() = runTest {
        val system = gbaFolderScan("Game Boy Advance (GBA)", null, emptyMap()).single { it.games.isNotEmpty() }
        assertEquals("GBA", system.tag)
    }

    @Test fun extras_merge_by_tag_and_keep_per_folder_art() = runTest {
        val systems = systems(listOf(loose))
        val gb = systems.first { it.tag == "GB" }
        assertEquals(4, gb.gameCount)   // Tetris.gb + Tetris DX.gbc (home) + Loose/Tetris.gb + Loose/b/Tetris.gb
        assertEquals(listOf("Tetris", "Tetris", "Tetris", "Tetris DX"), gb.games.map { it.shownName })
        val inB = gb.games.first { it.relativeDir == "b" }
        assertEquals("e1", inB.sourceId)
        assertEquals(ContentRef("sd/Loose/b/.media/boxart/Tetris (Rev 1).png"), inB.boxartRef)
        assertNull(gb.games.first { it.relativeDir == "" && it.sourceId == "e1" }.boxartRef)
        assertEquals(2, systems.first { it.tag == "GBA" }.gameCount)   // Mario + Kirby
    }

    @Test fun unassigned_files_form_a_last_pseudo_console_with_paths_as_subtitles() = runTest {
        val systems = systems(listOf(loose, mixed))
        val last = systems.last()
        assertTrue(last.unassigned)
        assertEquals(LibraryScanner.UNASSIGNED, last.displayName)
        assertNull(last.core)
        val g = last.games.single()
        assertEquals("thing", g.shownName)
        assertEquals("/", g.subtitle)
        assertTrue(systems.dropLast(1).none { it.unassigned })
    }

    @Test fun a_failing_extra_is_reported_skipped_and_left_out_of_the_recorded_extras() = runTest {
        val bad = ExtraFolder("e9", ContentRef("sd/Gone"))
        val throwing = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = if (dir.value == "sd/Gone") throw IllegalStateException("revoked") else source.listChildren(dir)
        }
        val failed = ArrayList<String>()
        val index = LibraryScanner(throwing, Sidecars(throwing, MemorySidecarStore()) { home }).scan(home, listOf(bad, loose), emptyMap()) { e, t -> failed += "${e.id}:${t.message}" }
        assertEquals(listOf("e9:revoked"), failed)
        assertEquals(4, index.toSystems().first { it.tag == "GB" }.gameCount)
        assertEquals(listOf(IndexedExtra("e1", "sd/Loose")), index.extras)
    }

    @Test fun a_failing_extra_on_an_offline_volume_is_kept_in_the_extras_and_recorded_offline() = runTest {
        val card = ExtraFolder("e9", ContentRef("sd/Card"))
        val unmounted = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = if (dir.value == "sd/Card") throw IllegalArgumentException("Folder no longer exists") else source.listChildren(dir)
            override suspend fun isOffline(tree: ContentRef): Boolean = tree.value == "sd/Card"
        }
        val failed = ArrayList<String>()
        val index = LibraryScanner(unmounted, Sidecars(unmounted, MemorySidecarStore()) { home }).scan(home, listOf(card, loose), emptyMap()) { e, _ -> failed += e.id }
        assertEquals(emptyList(), failed)                                                            // not a failure: the caller keeps it
        assertEquals(setOf(IndexedExtra("e9", "sd/Card"), IndexedExtra("e1", "sd/Loose")), index.extras.toSet())
        assertEquals(listOf("e9"), index.offline)
        assertTrue(index.fits(home, listOf(card, loose), emptyMap()))
    }

    /** "sd/Loose" mounted again (not offline) while the provider still has no root for it: the first [misses] listings fail as gone. */
    private class Remounting(private val src: RomsSource, var misses: Int) : RomsSource by src {
        override suspend fun listChildren(dir: ContentRef): List<Entry> =
            if (dir.value == "sd/Loose" && misses > 0) { misses--; throw IllegalArgumentException("Folder no longer exists") } else src.listChildren(dir)
    }

    private fun wasOffline(vararg ids: String, lateMount: List<String> = emptyList()) =
        LibraryIndex(LibraryIndex.VERSION, home.value, emptyList(), emptyMap(), emptyList(), ids.toList(), lateMount)

    @Test fun a_remounted_extra_whose_root_is_not_back_yet_lists_after_one_retry() = runTest {
        val src = Remounting(source, misses = 1)
        val failed = ArrayList<String>()
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, listOf(loose), emptyMap(), wasOffline("e1")) { e, _ -> failed += e.id }
        assertEquals(emptyList(), failed)
        assertEquals(emptyList(), index.offline)
        assertEquals(listOf(IndexedExtra("e1", "sd/Loose")), index.extras)
        assertTrue(index.systems.flatMap { it.games }.any { it.source == "e1" })
    }

    @Test fun a_remounted_extra_that_still_fails_is_kept_for_one_more_scan_then_dropped() = runTest {
        val src = Remounting(source, misses = Int.MAX_VALUE)
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home })
        val failed = ArrayList<String>()
        val first = scanner.scan(home, listOf(loose), emptyMap(), wasOffline("e1")) { e, _ -> failed += e.id }
        assertEquals(emptyList(), failed)                                                            // not yet back: kept, still offline
        assertEquals(listOf("e1"), first.offline)
        assertEquals(listOf("e1"), first.lateMount)
        assertEquals(listOf(IndexedExtra("e1", "sd/Loose")), first.extras)
        val second = scanner.scan(home, listOf(loose), emptyMap(), first) { e, _ -> failed += e.id }
        assertEquals(listOf("e1"), failed)                                                           // mounted twice and still gone: dropped
        assertEquals(emptyList(), second.extras)
        assertEquals(emptyList(), second.offline)
    }

    @Test fun an_extra_that_was_online_gets_no_retry() = runTest {
        val src = Remounting(source, misses = 1)
        val failed = ArrayList<String>()
        LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, listOf(loose), emptyMap(), wasOffline()) { e, _ -> failed += e.id }
        assertEquals(listOf("e1"), failed)
    }

    @Test fun an_extras_lookup_failure_leaves_its_games_indexed_without_art_and_never_fails_the_scan() = runTest {
        val throwing = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> =
                if (dir.value == "sd/Loose/b/.media") throw IllegalArgumentException("revoked") else source.listChildren(dir)
        }
        val failed = ArrayList<String>()
        val index = LibraryScanner(throwing, Sidecars(throwing, MemorySidecarStore()) { home }).scan(home, listOf(loose), emptyMap()) { e, _ -> failed += e.id }
        assertEquals("Home", index.home)
        assertEquals(emptyList(), failed)
        assertEquals(listOf(IndexedExtra("e1", "sd/Loose")), index.extras)
        val gb = index.toSystems().first { it.tag == "GB" }
        assertEquals(4, gb.gameCount)
        assertNull(gb.games.first { it.relativeDir == "b" }.boxartRef)
        assertEquals(ContentRef("Home/Roms/Nintendo DS (NDS)/.media/boxart/Mario Kart DS.png"), index.toSystems().first { it.tag == "NDS" }.games[1].boxartRef)
    }

    @Test fun an_unavailable_extra_listing_fails_the_scan_instead_of_dropping_the_extra() = runTest {
        val hiding = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = if (dir.value == "sd/Loose/b") throw ListingUnavailable(dir.value) else source.listChildren(dir)
        }
        val failed = ArrayList<String>()
        assertFailsWith<ListingUnavailable> {
            LibraryScanner(hiding, Sidecars(hiding, MemorySidecarStore()) { home }).scan(home, listOf(loose), emptyMap()) { e, _ -> failed += e.id }
        }
        assertEquals(emptyList(), failed)
    }

    @Test fun an_extra_root_that_suddenly_lists_empty_while_its_games_are_there_fails_the_scan() = runTest {
        val x = ExtraFolder("x", ContentRef("sd/X"))
        val src = FakeRomsSource(mapOf("Home" to listOf("Roms"), "Home/Roms" to emptyList(), "sd/X" to listOf("Tetris.gb")))
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home })
        val previous = scanner.scan(home, listOf(x), emptyMap())
        assertEquals(listOf(""), previous.systems.flatMap { it.games }.filter { it.source == "x" && it.file == "Tetris.gb" }.map { it.dir })
        src.listingFault = ListingFault.EMPTY
        assertFailsWith<ListingUnavailable> { scanner.scan(home, listOf(x), emptyMap(), previous) }
    }

    @Test fun an_extra_root_that_suddenly_lists_empty_while_its_subfolder_games_are_there_fails_the_scan() = runTest {
        val x = ExtraFolder("x", ContentRef("sd/X"))
        val src = FakeRomsSource(mapOf("Home" to listOf("Roms"), "Home/Roms" to emptyList(), "sd/X" to listOf("GBA"), "sd/X/GBA" to listOf("Kirby.gba")))
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home })
        val previous = scanner.scan(home, listOf(x), emptyMap())
        assertTrue(previous.systems.flatMap { it.games }.any { it.source == "x" && it.file == "Kirby.gba" })
        src.listingFault = ListingFault.EMPTY
        assertFailsWith<ListingUnavailable> { scanner.scan(home, listOf(x), emptyMap(), previous) }
    }

    @Test fun an_extra_whose_games_moved_away_but_whose_folders_stay_scans_as_empty() = runTest {
        // Move into Roms carries the files off and leaves GBA/ and SNES/ behind: the root lists, so the provider hides nothing.
        val x = ExtraFolder("x", ContentRef("sd/X"))
        val before = FakeRomsSource(mapOf(
            "Home" to listOf("Roms"), "Home/Roms" to emptyList(),
            "sd/X" to listOf("GBA", "SNES"), "sd/X/GBA" to listOf("Kirby.gba"), "sd/X/SNES" to listOf("Mario.sfc"),
        ))
        val previous = LibraryScanner(before, Sidecars(before, MemorySidecarStore()) { home }).scan(home, listOf(x), emptyMap())
        assertEquals(2, previous.systems.flatMap { it.games }.count { it.source == "x" })
        val after = FakeRomsSource(mapOf("Home" to listOf("Roms"), "Home/Roms" to emptyList(), "sd/X" to listOf("GBA", "SNES"), "sd/X/GBA" to emptyList(), "sd/X/SNES" to emptyList()))
        val index = LibraryScanner(after, Sidecars(after, MemorySidecarStore()) { home }).scan(home, listOf(x), emptyMap(), previous)
        assertEquals(listOf(IndexedExtra("x", "sd/X")), index.extras)
        assertTrue(index.systems.flatMap { it.games }.none { it.source == "x" })
    }

    @Test fun a_really_empty_extra_stays_empty() = runTest {
        val x = ExtraFolder("x", ContentRef("sd/X"))
        val before = FakeRomsSource(mapOf("Home" to listOf("Roms"), "Home/Roms" to emptyList(), "sd/X" to listOf("Tetris.gb")))
        val previous = LibraryScanner(before, Sidecars(before, MemorySidecarStore()) { home }).scan(home, listOf(x), emptyMap())
        assertTrue(previous.systems.flatMap { it.games }.any { it.source == "x" })
        val after = FakeRomsSource(mapOf("Home" to listOf("Roms"), "Home/Roms" to emptyList(), "sd/X" to emptyList()))   // Tetris.gb deleted
        val index = LibraryScanner(after, Sidecars(after, MemorySidecarStore()) { home }).scan(home, listOf(x), emptyMap(), previous)
        assertEquals(listOf(IndexedExtra("x", "sd/X")), index.extras)
        assertTrue(index.systems.flatMap { it.games }.none { it.source == "x" })
    }

    @Test fun an_unavailable_lookup_fails_the_scan_instead_of_dropping_art() = runTest {
        val hiding = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> =
                if (dir.value == "sd/Loose/b/.media") throw ListingUnavailable(dir.value) else source.listChildren(dir)
        }
        assertFailsWith<ListingUnavailable> {
            LibraryScanner(hiding, Sidecars(hiding, MemorySidecarStore()) { home }).scan(home, listOf(loose), emptyMap())
        }
    }

    @Test fun the_index_records_the_settings_and_default_tags_steer_extras() = runTest {
        val index = scanner.scan(home, listOf(loose), mapOf("gba" to "MGBA"))
        assertEquals(LibraryIndex.VERSION, index.version)
        assertEquals("Home", index.home)
        assertEquals(listOf(IndexedExtra("e1", "sd/Loose")), index.extras)
        assertEquals(mapOf("gba" to "MGBA"), index.defaultTags)
        val mgba = index.systems.first { it.tag == "MGBA" }
        assertEquals("Game Boy Advance (GBA)", mgba.folderName)   // the console-tag folder names the console's system
        assertEquals(listOf("Kirby.gba", "Mario.gba", "Zelda.gba"), mgba.games.map { it.file })   // Kirby and Mario by the default tag, Zelda by the home sidecar
        assertTrue(index.systems.none { it.tag == "GBA" })
    }

    @Test fun playstation_folders_form_a_playable_system() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "H" to listOf("Roms"),
            "H/Roms" to listOf("Sony PlayStation (PS)", "Game Boy (GB)"),
            "H/Roms/Sony PlayStation (PS)" to listOf("Crash.chd"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "sd/Games" to listOf("My Sony PlayStation"),
            "sd/Games/My Sony PlayStation" to listOf("Tekken 3.PBP"),
        ))
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
            .scan(ContentRef("H"), listOf(ExtraFolder("e1", ContentRef("sd/Games"))), emptyMap())
        val ps = index.toSystems().single { it.tag == "PS" }
        assertEquals("Sony PlayStation", ps.displayName)
        assertEquals("pcsx_rearmed", ps.core!!.id)
        assertEquals(setOf("Crash.chd", "Tekken 3.PBP"), ps.games.map { it.fileName }.toSet())
        assertEquals(listOf("GB", "PS"), index.playableSystems().map { it.tag })   // no PSP system, no Unassigned
    }
    @Test fun empty_skeleton_systems_are_in_the_index() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Game Boy (GB)"), "Home/Roms/Game Boy (GB)" to emptyList()))
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, emptyList(), emptyMap())
        assertEquals(listOf(IndexedSystem("GB", "Game Boy", "Game Boy (GB)", games = emptyList())), index.systems)
    }

    @Test fun retroachievements_folder_stays_out_of_the_library() = runTest {
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", ".retroachievements"),
            "H/Roms/Game Boy (GB)/.retroachievements" to listOf("hashes.json", "badges"),
            "H/Roms/Game Boy (GB)/.retroachievements/badges" to listOf("1"),
            "H/Roms/Game Boy (GB)/.retroachievements/badges/1" to listOf("1.png"),
        ))
        assertEquals(listOf("GB"), index.systems.map { it.tag })
        assertEquals(listOf("Tetris.gb"), index.systems.single().games.map { it.file })
    }

    @Test fun game_entries_match_recent_refs() = runTest {
        val gb = systems(listOf(loose)).first { it.tag == "GB" }
        assertEquals(1, gb.games.count { it.matches(GameRef("e1", "b", "Tetris (Rev 1).gb")) })
        assertEquals(1, gb.games.count { it.matches(GameRef(Recents.HOME, "Roms/Game Boy (GB)", "Tetris.gb")) })
        assertEquals(0, gb.games.count { it.matches(GameRef("e1", "c", "Tetris (Rev 1).gb")) })
    }

    @Test fun a_sources_map_txt_beats_the_one_in_the_extra() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"), "Home/Roms" to listOf(".sources"),
                "Home/Roms/.sources" to listOf("e1"), "Home/Roms/.sources/e1" to listOf("map.txt"),
                "sd/Loose" to listOf("Tetris.gb", "Kirby.gba", "map.txt"),
            ),
            texts = mapOf(
                "Home/Roms/.sources/e1/map.txt" to "Tetris.gb\tTetris (home copy)\n",
                "sd/Loose/map.txt" to "Tetris.gb\tTetris (in extra)\nKirby.gba\tKirby (in extra)\n",
            ),
        )
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, listOf(loose), emptyMap())
        assertEquals(mapOf("Tetris.gb" to "Tetris (home copy)", "Kirby.gba" to "Kirby (in extra)"), index.systems.flatMap { it.games }.associate { it.file to it.name })
        assertEquals(listOf("GB", "GBA"), index.systems.map { it.tag })   // .sources never becomes a system
    }

    private suspend fun scanHome(dirs: Map<String, List<String>>, texts: Map<String, String> = emptyMap()): LibraryIndex {
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms")) + dirs, texts = texts)
        return LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }).scan(ContentRef("H"), emptyList(), emptyMap())
    }

    @Test fun a_home_bin_cue_set_is_one_game_and_a_disc_folder_is_one_game_with_the_systems_art() = runTest {
        val ps = "H/Roms/Sony PlayStation (PS)"
        val index = scanHome(
            mapOf(
                "H/Roms" to listOf("Sony PlayStation (PS)"),
                ps to listOf(".media", "Final Fantasy VII", "Crash.cue", "Crash.bin", "Extras", "map.txt"),
                "$ps/.media" to listOf("boxart"),
                "$ps/.media/boxart" to listOf("Final Fantasy VII.png"),
                "$ps/Final Fantasy VII" to listOf("Final Fantasy VII.m3u", "FF7 (Disc 1).chd", "FF7 (Disc 2).chd"),
                "$ps/Extras" to listOf("manual.pdf"),
            ),
            mapOf("$ps/Crash.cue" to "FILE \"Crash.bin\" BINARY\n", "$ps/map.txt" to "Final Fantasy VII\tFinal Fantasy VII (USA)\n"),
        )
        val games = index.systems.single { it.tag == "PS" }.games
        assertEquals(listOf("Crash.cue", "Final Fantasy VII.m3u"), games.map { it.file })
        val ff7 = games[1]
        assertEquals("Roms/Sony PlayStation (PS)/Final Fantasy VII", ff7.dir)
        assertEquals("Roms/Sony PlayStation (PS)", ff7.lookupDir)      // its alias lives in the system folder's map.txt
        assertEquals(games[0].dir, games[0].lookupDir)              // a loose game's lookup dir is its own
        assertEquals("$ps/Final Fantasy VII", ff7.folder)            // the launch finds the discs here
        assertEquals("$ps/.media/boxart/Final Fantasy VII.png", ff7.boxart)   // art from the system folder's .media
        assertEquals("Final Fantasy VII (USA)", ff7.name)            // map.txt by folder name
    }
    @Test fun only_disc_systems_list_their_subfolders() = runTest {
        val src = CountingRomsSource(FakeRomsSource(dirs = mapOf(
            "H" to listOf("Roms"), "H/Roms" to listOf("Game Boy (GB)"),
            "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Tetris"), "H/Roms/Game Boy (GB)/Tetris" to listOf("Tetris.cue"),
        )))
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }).scan(ContentRef("H"), emptyList(), emptyMap())
        assertEquals(listOf("Tetris.gb"), index.systems.single { it.tag == "GB" }.games.map { it.file })
        assertTrue("listChildren:H/Roms/Game Boy (GB)/Tetris" !in src.calls)
    }
    @Test fun an_archive_lists_under_its_folders_tag_or_its_sidecars() = runTest {
        val index = scanHome(
            mapOf(
                "H/Roms" to listOf("Game Boy Advance (GBA)", "Super Famicom (SFC)"),
                "H/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba", "Kirby.zip", "Wario.7z", "Metroid.7z", "notes.txt", "systems.txt"),
                "H/Roms/Super Famicom (SFC)" to listOf("Chrono.7z"),
            ),
            texts = mapOf("H/Roms/Game Boy Advance (GBA)/systems.txt" to "Wario.7z\tMGBA\n"),
        )
        val gba = index.systems.single { it.tag == "GBA" }
        assertEquals(setOf("Mario.gba", "Kirby.zip", "Metroid.7z"), gba.games.map { it.file }.toSet())   // no longer pushed to MGBA: unpacked at launch; a plain 7z stays too
        assertEquals("Roms/Game Boy Advance (GBA)", gba.games.single { it.file == "Kirby.zip" }.dir)
        assertEquals(listOf("Wario.7z"), index.systems.single { it.tag == "MGBA" }.games.map { it.file })   // the sidecar still wins
        assertEquals(listOf("Chrono.7z"), index.systems.single { it.tag == "SFC" }.games.map { it.file })
    }
    @Test fun a_console_with_several_home_folders_takes_its_name_from_the_one_named_like_the_console() = runTest {
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Mega Drive (GPGX)", "Sega Genesis (GPGX)", "Sega Genesis (MD)"),
            "H/Roms/Mega Drive (GPGX)" to listOf("Shinobi.md"),
            "H/Roms/Sega Genesis (GPGX)" to listOf("Sonic.md"),
            "H/Roms/Sega Genesis (MD)" to listOf("Sonic 2.md"),
        ))
        val md = index.systems.single()
        assertEquals("MD", md.tag)
        assertEquals("Sega Genesis", md.displayName)
        assertEquals("Sega Genesis (MD)", md.folderName)   // named like the console and by its console tag
        assertEquals(listOf("Shinobi.md", "Sonic.md", "Sonic 2.md"), md.games.map { it.file })
        val other = scanHome(mapOf("H/Roms" to listOf("Mega Drive (GPGX)"), "H/Roms/Mega Drive (GPGX)" to listOf("Sonic.md")))
        assertEquals("Mega Drive", other.systems.single().displayName)   // no folder named like the console: the first found
    }
    @Test fun a_sidecar_tag_of_another_console_is_ignored_and_gpgx_is_the_consoles_gpgx() = runTest {
        val sms = "Sega Master System (SMS)"
        val index = scanHome(
            mapOf("H/Roms" to listOf(sms), "H/Roms/$sms" to listOf("A.sms", "B.sms", "C.sms", "systems.txt")),
            mapOf("H/Roms/$sms/systems.txt" to "A.sms\tGPGX\nB.sms\tMD\nC.sms\tGPGX_SMS\n"),
        )
        assertEquals(listOf("A.sms", "C.sms"), index.systems.single { it.tag == "GPGX_SMS" }.games.map { it.file })
        assertEquals(listOf("B.sms"), index.systems.single { it.tag == "SMS" }.games.map { it.file })   // Genesis's MD: ignored
        assertTrue(index.systems.none { it.tag == "MD" || it.tag == "GPGX" })
    }
    @Test fun a_gpgx_genesis_folder_merges_with_md_under_the_default_emulator() = runTest {
        val dirs = mapOf(
            "H/Roms" to listOf("Sega Genesis (MD)", "Sega Genesis (GPGX)"),
            "H/Roms/Sega Genesis (MD)" to listOf("Sonic.md"),
            "H/Roms/Sega Genesis (GPGX)" to listOf("Streets of Rage.md"),
        )
        val md = scanHome(dirs).toSystems().single()
        assertEquals("MD", md.tag)   // Genesis's default emulator is PicoDrive, whichever folder holds the game
        assertEquals("picodrive", md.core?.id)
        assertEquals(listOf("Sonic.md", "Streets of Rage.md"), md.games.map { it.fileName })
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms")) + dirs)
        val gpgx = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
            .scan(ContentRef("H"), emptyList(), mapOf("md" to "GPGX")).toSystems().single()
        assertEquals("GPGX", gpgx.tag)   // Default emulators → Genesis Plus GX
        assertEquals("genesis_plus_gx", gpgx.core?.id)
        assertEquals(2, gpgx.gameCount)
    }
    @Test fun a_decomposed_pokemon_mini_folder_is_pkm() = runTest {
        val folder = "Pokémon mini (PKM)"
        val index = scanHome(mapOf("H/Roms" to listOf(folder), "H/Roms/$folder" to listOf("Pichu Bros.min")))
        val pkm = index.systems.single()
        assertEquals("PKM", pkm.tag)
        assertEquals(listOf("Pichu Bros.min"), pkm.games.map { it.file })
        assertEquals("pokemini", index.toSystems().single().core!!.id)
    }
    @Test fun an_extra_disc_folder_takes_art_and_name_from_its_parent() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"), "Home/Roms" to emptyList(),
                "sd/PSX" to listOf(".media", "map.txt", "Mega Man X4"),
                "sd/PSX/.media" to listOf("screenshot"),
                "sd/PSX/.media/screenshot" to listOf("Mega Man X4.png"),
                "sd/PSX/Mega Man X4" to listOf("Mega Man X4.cue", "Mega Man X4.bin"),
            ),
            texts = mapOf("sd/PSX/map.txt" to "Mega Man X4\tMega Man X4 (USA)\n", "sd/PSX/Mega Man X4/Mega Man X4.cue" to "FILE \"Mega Man X4.bin\" BINARY\n"),
        )
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { home }).scan(home, listOf(ExtraFolder("e2", ContentRef("sd/PSX"))), emptyMap())
        val game = index.systems.single { it.tag == "PS" }.games.single()
        assertEquals("Mega Man X4.cue", game.file)
        assertEquals("Mega Man X4", game.dir)
        assertEquals("sd/PSX/Mega Man X4", game.folder)
        assertEquals("sd/PSX/.media/screenshot/Mega Man X4.png", game.screenshot)
        assertEquals("Mega Man X4 (USA)", game.name)
    }

    @Test fun a_dreamcast_game_folder_with_its_gdi_named_like_it_is_one_game_named_after_the_folder() = runTest {
        val dc = "H/Roms/Sega Dreamcast (DC)"
        val gdi = "3\n1 0 4 2352 track01.bin 0\n2 756 0 2352 track02.raw 0\n3 45000 4 2352 track03.bin 0\n"
        val index = scanHome(
            mapOf(
                "H/Roms" to listOf("Sega Dreamcast (DC)"),
                dc to listOf(".media", "Crazy Taxi", "Shenmue (USA)", "Shenmue (Europe)", "Tosec", "Two"),
                "$dc/.media" to listOf("boxart"),
                "$dc/.media/boxart" to listOf("Crazy Taxi.png", "Shenmue (USA).png", "disc.png"),
                "$dc/Crazy Taxi" to listOf("Crazy Taxi.gdi", "track01.bin", "track02.raw", "track03.bin"),
                "$dc/Shenmue (USA)" to listOf("Shenmue (USA).gdi", "track01.bin", "track02.raw", "track03.bin"),
                "$dc/Shenmue (Europe)" to listOf("SHENMUE (EUROPE).GDI", "track01.bin", "track02.raw", "track03.bin"),
                "$dc/Tosec" to listOf("disc.gdi", "track01.bin", "track02.raw", "track03.bin"),
                "$dc/Two" to listOf("Other.gdi", "Two.gdi", "track01.bin"),
            ),
            mapOf("$dc/Crazy Taxi/Crazy Taxi.gdi" to gdi, "$dc/Shenmue (USA)/Shenmue (USA).gdi" to gdi, "$dc/Shenmue (Europe)/SHENMUE (EUROPE).GDI" to gdi),
        )
        val games = index.systems.single { it.tag == "DC" }.games.associateBy { it.dir.substringAfterLast('/') }
        assertEquals(setOf("Crazy Taxi", "Shenmue (USA)", "Shenmue (Europe)", "Two"), games.keys)   // disc.gdi is not named like its folder
        val taxi = games.getValue("Crazy Taxi")
        assertEquals("Crazy Taxi.gdi", taxi.file)
        assertEquals("Crazy Taxi", taxi.name)
        assertEquals("$dc/Crazy Taxi", taxi.folder)
        assertEquals("Roms/Sega Dreamcast (DC)", taxi.lookupDir)
        assertEquals("$dc/.media/boxart/Crazy Taxi.png", taxi.boxart)
        assertEquals("Two.gdi", games.getValue("Two").file)                  // the one named like the folder, not the first
        assertEquals("$dc/.media/boxart/Shenmue (USA).png", games.getValue("Shenmue (USA)").boxart)
        assertEquals("Shenmue (USA)", games.getValue("Shenmue (USA)").name)  // both plain names are "Shenmue": each shows its folder's name
        val eu = games.getValue("Shenmue (Europe)")
        assertEquals("SHENMUE (EUROPE).GDI", eu.file)
        assertEquals("Shenmue (Europe)", eu.name)                            // the folder's spelling, not the sheet's
    }
    @Test fun a_lone_gdi_folder_under_a_system_whose_core_takes_no_gdi_is_not_a_game() = runTest {
        val index = scanHome(mapOf(
            "H/Roms" to listOf("Sony PlayStation (PS)"),
            "H/Roms/Sony PlayStation (PS)" to listOf("Game"),
            "H/Roms/Sony PlayStation (PS)/Game" to listOf("Game.gdi", "track01.bin"),
        ))
        assertEquals(emptyList(), index.systems.single { it.tag == "PS" }.games)
    }

    @Test fun a_scan_lists_each_folder_once_and_reads_sidecars_from_the_listings() = runTest {
        val src = CountingRomsSource(FakeRomsSource(
            dirs = mapOf(
                "H" to listOf("Roms"), "H/Roms" to listOf(".media", "Game Boy (GB)", "Sony PlayStation (PS)"), "H/Roms/.media" to listOf("Game Boy (GB).png"),
                "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", "map.txt", "systems.txt", ".media"), "H/Roms/Game Boy (GB)/.media" to listOf("boxart"),
                "H/Roms/Game Boy (GB)/.media/boxart" to listOf("Tetris.png"),
                "H/Roms/Sony PlayStation (PS)" to listOf("FF7"), "H/Roms/Sony PlayStation (PS)/FF7" to listOf("FF7.m3u", "FF7.chd"),
                "sd/Ext" to listOf("Game Boy", "a.gb"), "sd/Ext/Game Boy" to listOf("b.gb", "map.txt"),
            ),
            texts = mapOf("H/Roms/Game Boy (GB)/map.txt" to "Tetris.gb\tTetris DX\n", "H/Roms/Game Boy (GB)/systems.txt" to "", "H/Roms/Sony PlayStation (PS)/FF7/FF7.m3u" to "FF7.chd\n", "sd/Ext/Game Boy/map.txt" to "b.gb\tB\n"),
        ))
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
            .scan(ContentRef("H"), listOf(ExtraFolder("x1", ContentRef("sd/Ext"))), emptyMap())
        assertEquals(setOf("Tetris DX", "B", "a", "FF7"), index.systems.flatMap { s -> s.games.map { it.name } }.toSet())
        val lists = src.calls.filter { it.startsWith("listChildren:") }
        assertEquals(lists.distinct(), lists)
        // Names inside a listed folder come from its listing; only the home's own path and the .sources roots are looked up.
        val lookups = src.calls.filter { it.startsWith("child:") }
        assertTrue(lookups.none { it.endsWith("/map.txt") || it.endsWith("/systems.txt") }, lookups.toString())
        assertEquals(lookups.distinct(), lookups)
    }
    @Test fun the_index_is_built_on_the_compute_context() = runTest {
        var dispatched = 0
        val compute = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { dispatched++; Dispatchers.Default.dispatch(context, block) }
        }
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms"), "H/Roms" to listOf("Game Boy (GB)"), "H/Roms/Game Boy (GB)" to listOf("Tetris.gb")))
        val index = LibraryScanner(src, compute = compute).scan(ContentRef("H"), emptyList(), emptyMap())
        assertEquals(listOf("Tetris.gb"), index.systems.single().games.map { it.file })
        assertTrue(dispatched > 0)
    }

    // The whole walk runs on the compute context, not only the list building: every tree read of a scan and of readArt
    // is asked from it (a platform source then hops to its I/O context and comes back there), so the work between reads
    // (resolving files, parsing sheets and sidecars, the art maps) never lands on the caller's thread.
    @Test fun the_walk_and_the_art_read_run_on_the_compute_context() = runTest {
        val compute = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = Dispatchers.Default.dispatch(context, block)
        }
        val elsewhere = ArrayList<String>()
        val fake = FakeRomsSource(
            dirs = mapOf(
                "H" to listOf("Roms"), "H/Roms" to listOf("Game Boy (GB)", "Sony PlayStation (PS)"),
                "H/Roms/Game Boy (GB)" to listOf("Tetris.gb", "map.txt", ".media"), "H/Roms/Game Boy (GB)/.media" to listOf("boxart"),
                "H/Roms/Game Boy (GB)/.media/boxart" to listOf("Tetris.png"),
                "H/Roms/Sony PlayStation (PS)" to listOf("Crash.cue", "Crash.bin"), "sd/x" to listOf("w.gba"),
            ),
            texts = mapOf("H/Roms/Game Boy (GB)/map.txt" to "Tetris.gb\tTetris DX\n", "H/Roms/Sony PlayStation (PS)/Crash.cue" to "FILE \"Crash.bin\" BINARY\n"),
        )
        val src = object : RomsSource by fake {
            suspend fun check(call: String) {
                if (currentCoroutineContext()[ContinuationInterceptor] !== compute) elsewhere += call
            }
            override suspend fun listChildren(dir: ContentRef): List<Entry> { check("list ${dir.value}"); return fake.listChildren(dir) }
            override suspend fun child(dir: ContentRef, name: String): Entry? { check("child $name"); return fake.child(dir, name) }
            override suspend fun readText(file: ContentRef): String? { check("text ${file.value}"); return fake.readText(file) }
            override suspend fun readHead(file: ContentRef, max: Int): ByteArray? { check("head ${file.value}"); return fake.readHead(file, max) }
        }
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }, compute = compute)
        val index = scanner.scan(ContentRef("H"), listOf(ExtraFolder("x1", ContentRef("sd/x"))), emptyMap())
        assertEquals(setOf("Tetris DX", "Crash", "w"), index.systems.flatMap { s -> s.games.map { it.name } }.toSet())
        scanner.readArt(index)
        scanner.scanOutsideRoms(ContentRef("H"), emptyMap())
        assertEquals(emptyList(), elsewhere)
    }
}
