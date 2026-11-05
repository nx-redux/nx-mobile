package com.nxredux.mobile.scraper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LibretroThumbnailsTest {
    private val png = HttpResult(200, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 7))

    private fun game(file: String, tag: String = "GB", datTitle: String? = null) = Scrape.homeGame(file, tag).copy(datTitle = datTitle)
    private fun folderGame(folder: String, sheet: String, tag: String = "PS") =
        Scrape.homeGame(sheet, tag, dir = "Roms/$tag/$folder").copy(folderGame = true)

    @Test fun encodesLikeRetroArch() {
        fun enc(s: String) = Libretro.pathEncode(Libretro.clean(s))
        assertEquals("Tom%20_%20Jerry_%20Fists%20of%20Furry%20%28USA%29", enc("Tom & Jerry: Fists of Furry (USA)"))
        assertEquals("Pok%C3%A9mon%20-%20Emerald%20Version%20%28USA%2C%20Europe%29", enc("Pokémon - Emerald Version (USA, Europe)"))
        assertEquals("What%27s%20_this__", enc("What's \"this\"?"))
        assertEquals("Bang%21%20a_b_c_d_e_f_g", enc("Bang! a*b/c<d>e\\f|g"))
    }

    @Test fun urlForGameBoy() {
        assertEquals(listOf("Nintendo - Game Boy"), Libretro.systems("GB", "Tetris (World).gb"))
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy/Named_Boxarts/Tetris%20%28World%29%20%28Rev%201%29.png",
            Libretro.url("Nintendo - Game Boy", Libretro.Folder.BOX, "Tetris (World) (Rev 1)"),
        )
    }

    @Test fun mdFamilyRefinesByExtension() {
        assertEquals(listOf("Sega - Game Gear"), Libretro.systems("GPGX", "Sonic.gg"))
        assertEquals(listOf("Sega - Mega-CD - Sega CD"), Libretro.systems("MD", "Sonic CD.chd"))
        assertEquals(listOf("Sega - Mega Drive - Genesis"), Libretro.systems("md", "Sonic.md"))
    }

    @Test fun dcZipIsNaomiThenAtomiswave() {
        assertEquals(listOf("Sega - Naomi", "Sega - Naomi 2", "Atomiswave"), Libretro.systems("DC", "mvsc2.zip"))
        assertEquals(listOf("Sega - Dreamcast"), Libretro.systems("DC", "Crazy Taxi.chd"))
    }

    @Test fun arcadeUsesDatTitleAndFbneoThenMame() = runTest {
        assertEquals(listOf("FBNeo - Arcade Games", "MAME"), Libretro.systems("FBN", "sf2.zip"))
        assertEquals(listOf("FBNeo - Arcade Games", "MAME"), Libretro.systems("CPS2", "ssf2.zip"))
        val g = game("sf2.zip", "FBN", datTitle = "Street Fighter II: The World Warrior (World 910522)")
        assertEquals("Street Fighter II: The World Warrior (World 910522)", Libretro.candidates(g).first())
        val http = FakeHttp()
        http.on("MAME/Named_Boxarts/Street%20Fighter%20II_", png)
        val hits = LibretroThumbnails(http, { 0L }) {}.find(g, setOf(ArtSlot.BOX_2D))
        assertEquals("https://thumbnails.libretro.com/MAME/Named_Boxarts/Street%20Fighter%20II_%20The%20World%20Warrior%20%28World%20910522%29.png", hits.single().url)
        assertTrue(http.calls.first().contains("FBNeo%20-%20Arcade%20Games"))
    }

    @Test fun doomTagsUseDoom() {
        assertEquals(listOf("DOOM"), Libretro.systems("PRBOOM", "doom.wad"))
        assertEquals(listOf("DOOM"), Libretro.systems("DOOM", "doom2.wad"))
        assertEquals(listOf("DOS"), Libretro.systems("DOS", "keen.zip"))
    }

    @Test fun unmappedSystemsAreUnknown() {
        assertEquals(emptyList(), Libretro.systems("PICO8", "celeste.p8.png"))
        assertEquals(emptyList(), Libretro.systems("MEGADUCK", "x.bin"))
        assertEquals(emptyList(), Libretro.systems("PORTS", "x.sh"))
        val src = LibretroThumbnails(FakeHttp(), { 0L }) {}
        assertTrue(!src.knows(game("x.p8", "PICO8")))
        assertTrue(!src.knows(Scrape.androidGame("com.x", "X")))
        assertTrue(src.knows(game("Tetris.gb")))
    }

    @Test fun wiiAndGameCubeUseTheirRepositories() {
        assertEquals(listOf("Nintendo - Wii"), Libretro.systems("WII", "Super Mario Galaxy 2 (USA).wbfs"))
        assertEquals(listOf("Nintendo - GameCube"), Libretro.systems("GC", "Mario Kart - Double Dash!! (USA).ciso"))
        assertEquals(listOf("Nintendo - Nintendo 3DS"), Libretro.systems("3DS", "Pokemon X (USA).3ds"))
    }

    @Test fun everyScraperSystemsIdIsMappedOrListedAsNone() {
        for ((tag, id) in ScraperSystems.table) {
            val mapped = Libretro.systems(tag, "x.bin").isNotEmpty()
            assertTrue(mapped != (id in Libretro.NONE), "tag $tag (id $id): mapped=$mapped, none=${id in Libretro.NONE}")
        }
        // The refined ids too (the MD family and DC archives).
        for (file in listOf("x.cue", "x.sms", "x.gg", "x.sg")) assertTrue(Libretro.systems("MD", file).isNotEmpty())
        assertTrue(Libretro.systems("DC", "x.zip").isNotEmpty())
    }

    @Test fun candidatesStripSquareBrackets() {
        assertEquals(listOf("Sonic (USA) [!]", "Sonic (USA)"), Libretro.candidates(game("Sonic (USA) [!].md", "MD")))
        assertEquals(listOf("Tetris (World)"), Libretro.candidates(game("Tetris (World).gb")))
    }

    @Test fun candidatesAddDisc1ForM3uAndFolders() {
        assertEquals(listOf("Final Fantasy VII (USA)", "Final Fantasy VII (USA) (Disc 1)"), Libretro.candidates(game("Final Fantasy VII (USA).m3u", "PS")))
        assertEquals(listOf("Riven (USA)", "Riven (USA) (Disc 1)"), Libretro.candidates(folderGame("Riven (USA)", "Riven (USA).cue")))
        assertEquals(listOf("Riven (USA) (Disc 2)"), Libretro.candidates(game("Riven (USA) (Disc 2).m3u", "PS")))
        assertEquals(listOf("Crash (USA)"), Libretro.candidates(game("Crash (USA).cue", "PS")))
    }

    @Test fun candidatesCapAtThree() {
        val c = Libretro.candidates(game("Myst (USA) [b].m3u", "PS"))
        assertEquals(listOf("Myst (USA) [b]", "Myst (USA)", "Myst (USA) (Disc 1)"), c)
    }

    @Test fun notFoundTriesNextCandidate() = runTest {
        val http = FakeHttp()
        http.on("Named_Boxarts/Sonic%20%28USA%29.png", png)
        http.on("Named_Snaps/Sonic%20%28USA%29%20%5B%21%5D.png", HttpResult(200, "<html>".encodeToByteArray()))   // not an image
        val src = LibretroThumbnails(http, { 0L }) {}
        val hits = src.find(game("Sonic (USA) [!].md", "MD"), setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT))
        val box = hits.single()
        assertEquals(ArtSlot.BOX_2D, box.slot)
        assertEquals(ArtOrigin("libretro", "Sega - Mega Drive - Genesis", "Named_Boxarts/Sonic (USA)"), box.origin)
        assertContentEquals(png.body, box.bytes)
        assertEquals(5, http.calls.size)   // box: [!] 404, bare 200; snaps: [!] not an image, bare 404, name list 404
    }

    @Test fun onlyWantedFolders() = runTest {
        val http = FakeHttp()
        LibretroThumbnails(http, { 0L }) {}.find(game("Tetris.gb"), setOf(ArtSlot.SCREENSHOT))
        assertTrue(http.calls.all { "Named_Snaps" in it })
    }

    @Test fun serverErrorStopsTheSource() = runTest {
        for (status in listOf(429, 503)) {
            val http = FakeHttp().apply { on("libretro", HttpResult(status, null)) }
            val waits = mutableListOf<Long>()
            val e = assertFailsWith<SourceStop> { LibretroThumbnails(http, { 0L }) { waits += it }.find(game("Tetris.gb"), setOf(ArtSlot.BOX_2D)) }
            assertEquals(LibretroThumbnails.UNAVAILABLE, e.note)
            assertEquals(2, http.calls.size)   // asked again once, after the wait
            assertEquals(LibretroThumbnails.BUSY_WAIT_MS, waits.first())   // then the pacer's gap
        }
    }

    @Test fun oneServerErrorIsAskedAgainAndTheSourceGoesOn() = runTest {
        for (status in listOf(429, 503)) {
            val http = FakeHttp().apply { on("Named_Boxarts/Tetris.png", HttpResult(status, null), png) }
            val hits = LibretroThumbnails(http, { 0L }) {}.find(game("Tetris.gb"), setOf(ArtSlot.BOX_2D))
            assertEquals(listOf(ArtSlot.BOX_2D), hits.map { it.slot })
            assertEquals(2, http.calls.size)
        }
    }

    @Test fun offlinePausesAfterThreeTries() = runTest {
        val http = FakeHttp().apply { on("libretro", HttpResult(-1, null)) }
        val e = assertFailsWith<JobPause> { LibretroThumbnails(http, { 0L }) {}.find(game("Tetris.gb"), setOf(ArtSlot.BOX_2D)) }
        assertEquals(ScrapeEngine.NO_CONNECTION, e.reason)
        assertEquals(3, http.calls.size)
    }

    @Test fun pacesQuarterSecond() = runTest {
        var clock = 0L
        val http = FakeHttp().apply { this.clock = { clock } }
        LibretroThumbnails(http, { clock }) { clock += it }.find(game("Tetris (World) [a].gb"), setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT))
        assertEquals(6, http.times.size)   // per folder: [a] 404, bare 404, name list 404
        http.times.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= LibretroThumbnails.RATE_MS, "${http.times}") }
    }

    @Test fun engineWritesTheProbedBytesWithoutASecondDownload() = runTest {
        val fs = Scrape.homeTree()
        val http = FakeHttp().apply { on("Named_Boxarts", png) }
        val written = mutableListOf<Pair<String, ArtOrigin?>>()
        val engine = ScrapeEngine(http, FakeFitter(), fs, Scrape.home, listOf(LibretroThumbnails(http, { 0L }) {}), { _, _ -> }, { written += it })
        val tetris = Scrape.homeGame("Tetris.gb")
        engine.run(ScrapeState(items = listOf(Scrape.item(tetris))), { tetris }) {}
        assertContentEquals(png.body, fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertEquals(1, http.count("Named_Boxarts"))
        assertEquals("libretro", written.single().second?.source)
    }
    // ---- name-list matching (an untagged "Advance Wars.gba" against libretro's "Advance Wars (USA)") ----

    private val gbaIndex = """
        <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
        <html><head><title>Index of /Nintendo - Game Boy Advance/Named_Boxarts</title></head><body>
        <h1>Index of /Nintendo - Game Boy Advance/Named_Boxarts</h1>
        <ul><li><a href="/Nintendo%20-%20Game%20Boy%20Advance/"> Parent Directory</a></li>
        <li><a href="Advance%20Wars%20(Europe)%20(En,Fr,De,Es).png"> Advance Wars (Europe) (En,Fr,De,Es).png</a></li>
        <li><a href="Advance%20Wars%20(USA)%20(Rev%201).png"> Advance Wars (USA) (Rev 1).png</a></li>
        <li><a href="Advance%20Wars%20(USA)%20(Virtual%20Console).png"> Advance Wars (USA) (Virtual Console).png</a></li>
        <li><a href="Advance%20Wars%20(USA).png"> Advance Wars (USA).png</a></li>
        <li><a href="Advance%20Wars%202%20-%20Black%20Hole%20Rising%20(USA).png"> Advance Wars 2 - Black Hole Rising (USA).png</a></li>
        <li><a href="2%20Games%20in%201!%20-%20Buu's%20Fury%20+%20GT%20(USA).png"> 2 Games in 1! - Buu's Fury + GT (USA).png</a></li>
        <li><a href="Pok%C3%A9mon%20-%20Emerald%20Version%20(USA,%20Europe).png"> Pokémon - Emerald Version (USA, Europe).png</a></li>
        <li><a href="Legend%20of%20Zelda,%20The%20-%20The%20Minish%20Cap%20(USA).png"> Legend of Zelda, The - The Minish Cap (USA).png</a></li>
        <li><a href="Tom%20_%20Jerry%20(USA).png"> Tom _ Jerry (USA).png</a></li>
        <li><a href="notes.txt"> notes.txt</a></li>
        </ul></body></html>
    """.trimIndent()

    @Test fun listingNamesParseTheDirectoryIndex() {
        assertEquals(
            listOf(
                "Advance Wars (Europe) (En,Fr,De,Es)", "Advance Wars (USA) (Rev 1)", "Advance Wars (USA) (Virtual Console)", "Advance Wars (USA)",
                "Advance Wars 2 - Black Hole Rising (USA)", "2 Games in 1! - Buu's Fury + GT (USA)", "Pokémon - Emerald Version (USA, Europe)",
                "Legend of Zelda, The - The Minish Cap (USA)", "Tom _ Jerry (USA)",
            ),
            Libretro.listingNames(gbaIndex),
        )
        // The fancy table format too, and sort links / absolute links are not names.
        val table = """<tr><td><a href="?C=N;O=D">Name</a></td></tr><tr><td><a href="Sonic%20%26%20Co%20(Japan).png">x</a></td></tr>""" +
            """<a href="https://example.com/x.png">y</a><a HREF="It&#39;s%20(USA).png">z</a>"""
        assertEquals(listOf("Sonic & Co (Japan)", "It's (USA)"), Libretro.listingNames(table))
        assertEquals(emptyList(), Libretro.listingNames(""))
    }

    @Test fun matchKeyIgnoresTagsPunctuationCaseAndArticles() {
        val k = Libretro.matchKey("Advance Wars")
        assertEquals("advance wars", k)
        assertEquals(k, Libretro.matchKey("Advance Wars (USA) (Rev 1)"))
        assertEquals(k, Libretro.matchKey("ADVANCE  WARS [!] (Europe) (En,Fr,De,Es)"))
        assertEquals(Libretro.matchKey("Street Fighter II: The World Warrior"), Libretro.matchKey("Street Fighter II - The World Warrior (World 910522)"))
        assertEquals(Libretro.matchKey("Street Fighter II_ The World Warrior"), Libretro.matchKey("Street Fighter II: The World Warrior"))
        assertEquals(Libretro.matchKey("The Legend of Zelda - The Minish Cap"), Libretro.matchKey("Legend of Zelda, The - The Minish Cap (USA)"))
        assertEquals(Libretro.matchKey("Legend of Zelda, The"), Libretro.matchKey("The Legend of Zelda"))
        assertEquals(Libretro.matchKey("Pokemon - Emerald Version"), Libretro.matchKey("Pokémon - Emerald Version (USA, Europe)"))
        assertEquals(Libretro.matchKey("Tom & Jerry"), Libretro.matchKey("Tom _ Jerry (USA)"))
        assertTrue(Libretro.matchKey("Advance Wars 2") != k)
        assertEquals(Libretro.matchKey("Donkey Kong Country 2 - Diddys Kong Quest"), Libretro.matchKey("Donkey Kong Country 2 - Diddy's Kong Quest (USA)"))
        assertEquals(Libretro.matchKey("Mortal Kombat 2"), Libretro.matchKey("Mortal Kombat II (USA)"))
        assertEquals("sonic the hedgehog 3", Libretro.matchKey("Sonic The Hedgehog III"))
        assertEquals("mega man x", Libretro.matchKey("Mega Man X (USA)"))
        assertEquals("final fantasy v", Libretro.matchKey("Final Fantasy V"))
        assertEquals("i robot", Libretro.matchKey("I, Robot"))
        assertEquals("", Libretro.matchKey("(USA) [!]"))
    }

    @Test fun rankingPrefersRegionsThenCleanThenShortest() {
        assertEquals(0, Libretro.regionRank("X (USA, Europe)"))
        assertEquals(1, Libretro.regionRank("X (World)"))
        assertEquals(2, Libretro.regionRank("X (Europe) (En,Fr,De)"))
        assertEquals(3, Libretro.regionRank("X (Japan)"))
        assertEquals(4, Libretro.regionRank("X (Germany)"))
        assertEquals(4, Libretro.regionRank("X"))
        assertTrue(Libretro.unwanted("X (USA) (Beta)"))
        assertTrue(Libretro.unwanted("X (USA) (Proto 2)"))
        assertTrue(Libretro.unwanted("X (Unl)"))
        assertTrue(Libretro.unwanted("X [Hack by Y]"))
        assertTrue(Libretro.unwanted("X (USA) [b]"))
        assertTrue(!Libretro.unwanted("X (USA) [!]"))
        assertTrue(!Libretro.unwanted("Demolition Man (USA)"))
        assertTrue(!Libretro.unwanted("X (USA) (Rev 1)"))
        fun best(vararg names: String, rom: String = "X") = Libretro.bestListed(rom, Libretro.index(names.toList()))
        assertEquals("X (USA)", best("X (Japan)", "X (Europe)", "X (USA) (Rev 1)", "X (USA)", "X (World)"))
        assertEquals("X (World)", best("X (Japan)", "X (Europe)", "X (World)"))
        assertEquals("X (Europe)", best("X (Japan)", "X (Europe)", "X (Brazil)"))
        assertEquals("X (Japan)", best("X (USA) (Beta)", "X (Japan)", "X (USA) (Demo)"))
        assertEquals("X (USA) (Proto)", best("X (USA) (Proto)", "X (USA) (Proto 2)"))
        assertEquals("X (USA) (Rev A)", best("X (USA) (Rev B)", "X (USA) (Rev A)"))
        assertEquals("Advance Wars (USA)", Libretro.bestListed("Advance Wars", Libretro.index(Libretro.listingNames(gbaIndex))))
        assertEquals(null, best("Y (USA)"))
        assertEquals(null, best("X (USA)", rom = "(USA)"))
    }

    @Test fun nameListTriesTheLooserTierAfterTheKey() {
        val gba = Libretro.index(listOf(
            "Pokemon - Emerald Version (USA, Europe)", "Pokemon - Ruby Version (USA, Europe) (Rev 2)", "Pokemon - Sapphire Version (USA, Europe)",
            "Pokemon - FireRed Version (USA, Europe) (Rev 1)", "Pokemon - LeafGreen Version (USA, Europe)", "Pokemon - Emerald Version (Japan)",
        ))
        assertEquals("Pokemon - Emerald Version (USA, Europe)", Libretro.bestListed("Pokémon Emerald", gba))
        assertEquals("Pokemon - Ruby Version (USA, Europe) (Rev 2)", Libretro.bestListed("Pokemon Ruby", gba))
        assertEquals("Pokemon - LeafGreen Version (USA, Europe)", Libretro.bestListed("Pokemon LeafGreen", gba))
        val md = Libretro.index(listOf("Sonic The Hedgehog (USA, Europe)", "Sonic The Hedgehog 2 (World)", "Sonic The Hedgehog 3 (USA)"))
        assertEquals("Sonic The Hedgehog (USA, Europe)", Libretro.bestListed("Sonic The Hedgehog I", md))
        val dc = Libretro.index(listOf("Marvel vs. Capcom 2 (USA)", "Marvel vs. Capcom 2_ New Age of Heroes (Japan)", "ChuChu Rocket! (USA)", "Crazy Taxi 2 (USA)"))
        // The key first: "Marvel vs. Capcom 2 (USA)" over the subtitled one.
        assertEquals("Marvel vs. Capcom 2 (USA)", Libretro.bestListed("Marvel vs Capcom 2", dc))
        assertEquals("Crazy Taxi 2 (USA)", Libretro.bestListed("Crazy Taxi 2 v1.004", dc))
        val dc2 = Libretro.index(listOf("Marvel vs. Capcom 2_ New Age of Heroes (Japan)", "Marvel vs. Capcom 2 - New Age of Heroes (USA)"))
        assertEquals("Marvel vs. Capcom 2 - New Age of Heroes (USA)", Libretro.bestListed("Marvel vs Capcom 2", dc2))
        // False positives.
        val nes = Libretro.index(listOf("Super Mario Bros. 3 (USA)", "Tetris 2 (USA)", "Street Fighter II Turbo (USA)"))
        assertEquals(null, Libretro.bestListed("Super Mario Bros", nes))
        assertEquals(null, Libretro.bestListed("Tetris", nes))
        assertEquals(null, Libretro.bestListed("Street Fighter II", nes))
        val snes = Libretro.index(listOf("Street Fighter II - The World Warrior (USA)", "Street Fighter II - Champion Edition (Japan)", "Street Fighter II (USA)"))
        assertEquals("Street Fighter II (USA)", Libretro.bestListed("Street Fighter II", snes))
        assertEquals(null, Libretro.bestListed("Street Fighter II", Libretro.index(snes.exact.values.flatten().dropLast(1))))
    }

    @Test fun untaggedNameMatchesThroughTheNameList() = runTest {
        val http = FakeHttp()
        http.on("Named_Boxarts/?F=0", HttpResult(200, gbaIndex.encodeToByteArray()))
        http.on("Named_Boxarts/Advance%20Wars%20%28USA%29.png", png)
        http.on("Named_Boxarts/Legend%20of%20Zelda%2C%20The%20-%20The%20Minish%20Cap%20%28USA%29.png", png)
        val src = LibretroThumbnails(http, { 0L }) {}
        src.startJob()
        val hit = src.find(game("Advance Wars.gba", "GBA"), setOf(ArtSlot.BOX_2D)).single()
        assertEquals("https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/Advance%20Wars%20%28USA%29.png", hit.url)
        assertEquals(ArtOrigin("libretro", "Nintendo - Game Boy Advance", "Named_Boxarts/Advance Wars (USA)"), hit.origin)
        assertContentEquals(png.body, hit.bytes)
        assertEquals(
            listOf(
                "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/Advance%20Wars.png",
                "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/?F=0",
                hit.url,
            ),
            http.calls,
        )
        // A second game in the same job reads the kept list.
        val zelda = src.find(game("The Legend of Zelda - The Minish Cap [!].gba", "GBA"), setOf(ArtSlot.BOX_2D)).single()
        assertEquals("Named_Boxarts/Legend of Zelda, The - The Minish Cap (USA)", zelda.origin.assetId)
        assertEquals(1, http.count("?F=0"))
        // Not in the list: no hit, no further list read.
        assertEquals(emptyList(), src.find(game("Unknown Game.gba", "GBA"), setOf(ArtSlot.BOX_2D)))
        assertEquals(1, http.count("?F=0"))
        // A new job reads it again.
        src.startJob()
        src.find(game("Advance Wars.gba", "GBA"), setOf(ArtSlot.BOX_2D))
        assertEquals(2, http.count("?F=0"))
    }

    @Test fun eachFolderHasItsOwnList() = runTest {
        val http = FakeHttp()
        http.on("Named_Boxarts/?F=0", HttpResult(200, gbaIndex.encodeToByteArray()))
        http.on("Named_Snaps/?F=0", HttpResult(200, """<a href="Advance%20Wars%20(Europe).png">x</a>""".encodeToByteArray()))
        http.on("Advance%20Wars%20%28", png)
        val hits = LibretroThumbnails(http, { 0L }) {}.find(game("Advance Wars.gba", "GBA"), setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT))
        assertEquals(listOf("Named_Boxarts/Advance Wars (USA)", "Named_Snaps/Advance Wars (Europe)"), hits.map { it.origin.assetId })
    }

    @Test fun arcadeTriesDatTitleExactlyBeforeTheList() = runTest {
        val http = FakeHttp()
        http.on("FBNeo%20-%20Arcade%20Games/Named_Boxarts/?F=0", HttpResult(200, """<a href="Metal%20Slug%20-%20Super%20Vehicle-001%20(Japan).png">x</a>""".encodeToByteArray()))
        http.on("Named_Boxarts/Metal%20Slug%20-%20Super%20Vehicle-001%20%28Japan%29.png", png)
        val g = game("mslug.zip", "FBN", datTitle = "Metal Slug: Super Vehicle-001")
        val hit = LibretroThumbnails(http, { 0L }) {}.find(g, setOf(ArtSlot.BOX_2D)).single()
        assertEquals("FBNeo - Arcade Games", hit.origin.gameId)
        assertEquals("Named_Boxarts/Metal Slug - Super Vehicle-001 (Japan)", hit.origin.assetId)
        // Exact DAT title on FBNeo, then MAME, before any list.
        assertTrue(http.calls[0].contains("FBNeo") && http.calls[0].endsWith(".png"))
        assertTrue(http.calls[1].contains("/MAME/") && http.calls[1].endsWith(".png"))
        assertTrue(http.calls[2].endsWith("?F=0"))
    }

    @Test fun anUnreadableListIsKeptEmptyForTheJob() = runTest {
        val http = FakeHttp().apply { on("?F=0", HttpResult(HttpResult.TOO_LARGE, null)) }
        val src = LibretroThumbnails(http, { 0L }) {}
        assertEquals(emptyList(), src.find(game("Advance Wars.gba", "GBA"), setOf(ArtSlot.BOX_2D)))
        assertEquals(emptyList(), src.find(game("Golden Sun.gba", "GBA"), setOf(ArtSlot.BOX_2D)))
        assertEquals(1, http.count("?F=0"))
    }

    @Test fun listServerErrorStopsAndOfflinePauses() = runTest {
        for (status in listOf(429, 502)) {
            val http = FakeHttp().apply { on("?F=0", HttpResult(status, null)) }
            val e = assertFailsWith<SourceStop> { LibretroThumbnails(http, { 0L }) {}.find(game("Advance Wars.gba", "GBA"), setOf(ArtSlot.BOX_2D)) }
            assertEquals(LibretroThumbnails.UNAVAILABLE, e.note)
        }
        val http = FakeHttp().apply { on("?F=0", HttpResult(-1, null)) }
        val e = assertFailsWith<JobPause> { LibretroThumbnails(http, { 0L }) {}.find(game("Advance Wars.gba", "GBA"), setOf(ArtSlot.BOX_2D)) }
        assertEquals(ScrapeEngine.NO_CONNECTION, e.reason)
        assertEquals(3, http.count("?F=0"))
    }

    @Test fun extra_regional_snaps_when_listing_loaded() = runTest {
        val http = FakeHttp()
        http.on("Named_Snaps/?F=0", HttpResult(200, """<a href="Tetris%20(USA).png">x</a><a href="Tetris%20(Europe).png">x</a>""".encodeToByteArray()))
        http.on("Named_Snaps/Tetris%20%28USA%29.png", png)
        val src = LibretroThumbnails(http, { 0L }) {}
        // Without the listing: only the probed snap, and no listing request.
        val plain = FakeHttp().apply { on("Named_Snaps/Tetris.png", png) }
        val alone = LibretroThumbnails(plain, { 0L }) {}.find(game("Tetris.gb"), setOf(ArtSlot.SCREENSHOT))
        assertEquals(1, alone.size)
        assertEquals(0, plain.count("?F=0"))
        // Another game missed, so the snap listing is loaded for this run.
        src.find(game("Zzz.gb"), setOf(ArtSlot.SCREENSHOT))
        assertEquals(1, http.count("Named_Snaps/?F=0"))
        val hits = src.find(game("Tetris.gb"), setOf(ArtSlot.SCREENSHOT))
        assertEquals(1, http.count("Named_Snaps/?F=0"))
        assertEquals(2, hits.size)
        assertTrue(hits[0].url.endsWith("Tetris%20%28USA%29.png"))
        assertEquals("USA", hits[0].note)
        assertTrue(hits[1].url.endsWith("Tetris%20%28Europe%29.png"))
        assertEquals("Europe", hits[1].note)
        assertEquals(null, hits[1].bytes)
    }
}
