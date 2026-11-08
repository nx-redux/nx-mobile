package com.nxredux.mobile.scraper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TheGamesDB bodies in the official spec's shapes (`https://api.thegamesdb.net/spec.yaml`; no key was at
 * hand to record live ones). No key values anywhere.
 */
object TgdbFixtures {
    fun byName(left: Int = 249, extra: Int = 0) = """
    {"code":200,"status":"Success","remaining_monthly_allowance":$left,"extra_allowance":$extra,"allowance_refresh_timer":2592000,
     "data":{"count":3,"games":[
       {"id":432,"game_title":"Sonic the Hedgehog 2","release_date":"1992-11-24","platform":18},
       {"id":53,"game_title":"Sonic the Hedgehog","release_date":"1991-06-23","platform":18},
       {"id":9001,"game_title":"Sonic The Hedgehog","release_date":"1991-06-23","platform":36}]},
     "include":{"boxart":{"base_url":{"original":"https://cdn.thegamesdb.net/images/original/","small":"https://cdn.thegamesdb.net/images/small/"},
       "data":{"53":[
         {"id":17439,"type":"boxart","side":"back","filename":"boxart/back/53-1.jpg","resolution":"1521x2156"},
         {"id":17500,"type":"boxart","side":"front","filename":"boxart/front/53-2.jpg","resolution":"3000x4000"},
         {"id":17438,"type":"boxart","side":"front","filename":"boxart/front/53-1.jpg","resolution":"300x400"}],
        "432":[{"id":1,"type":"boxart","side":"front","filename":"boxart/front/432-1.jpg","resolution":"1x1"}]}}},
     "pages":{"previous":null,"current":"https://api.thegamesdb.net/v1/Games/ByGameName?apikey=APIKEY&name=Sonic+the+Hedgehog&page=1","next":null}}
    """.trimIndent()

    fun images(left: Int = 248) = """
    {"code":200,"status":"Success","remaining_monthly_allowance":$left,"extra_allowance":0,"allowance_refresh_timer":2592000,
     "data":{"count":3,"base_url":{"original":"https://cdn.thegamesdb.net/images/original/","thumb":"https://cdn.thegamesdb.net/images/thumb/"},
       "images":{"53":[
         {"id":4600,"type":"screenshot","side":null,"filename":"screenshots/53-2.jpg","resolution":"320x224"},
         {"id":4521,"type":"screenshot","side":null,"filename":"screenshots/53-1.jpg","resolution":"320x224"},
         {"id":17438,"type":"boxart","side":"front","filename":"boxart/front/53-1.jpg","resolution":"1521x2156"}]}},
     "pages":{"previous":null,"current":"https://api.thegamesdb.net/v1/Games/Images?apikey=APIKEY&games_id=53&page=1","next":null}}
    """.trimIndent()

    /** `Games/Images` with `filter[type]=screenshot,clearlogo`: a screenshot and two clear logos (the spec's `type` enum). */
    fun imagesWithLogos(left: Int = 248) = """
    {"code":200,"status":"Success","remaining_monthly_allowance":$left,"extra_allowance":0,"allowance_refresh_timer":2592000,
     "data":{"count":3,"base_url":{"original":"https://cdn.thegamesdb.net/images/original/","thumb":"https://cdn.thegamesdb.net/images/thumb/"},
       "images":{"53":[
         {"id":4521,"type":"screenshot","side":null,"filename":"screenshots/53-1.jpg","resolution":"320x224"},
         {"id":9102,"type":"clearlogo","side":null,"filename":"clearlogo/53-2.png","resolution":"400x150"},
         {"id":9101,"type":"clearlogo","side":null,"filename":"clearlogo/53.png","resolution":"400x150"}]}},
     "pages":{"previous":null,"current":null,"next":null}}
    """.trimIndent()

    fun limit(left: Int = 1820, extra: Int = 0) = """{"remaining_monthly_allowance":$left,"extra_allowance":$extra,"allowance_refresh_timer":2592000}"""
}

class TheGamesDbTest {
    private val sonic = Scrape.homeGame("Sonic the Hedgehog (USA, Europe).md", "MD").copy(name = "Sonic the Hedgehog")
    private fun ok(s: String) = HttpResult(200, s.encodeToByteArray())

    private class Rig(var key: String = "K") {
        var clock = 0L
        val http = FakeHttp().apply { clock = { this@Rig.clock } }
        val src = TheGamesDbSource(http, { key }, { clock }) { clock += it }
    }

    @Test fun urlsHaveKeyNameAndPlatform() {
        assertEquals(
            "https://api.thegamesdb.net/v1/Games/ByGameName?apikey=K&name=Sonic+the+Hedgehog&filter%5Bplatform%5D=18,36&include=boxart",
            Tgdb.byName("K", "Sonic the Hedgehog", "18,36"),
        )
        assertEquals("https://api.thegamesdb.net/v1/Games/Images?apikey=K&games_id=53&filter%5Btype%5D=screenshot", Tgdb.images("K", 53))
        assertEquals("https://api.thegamesdb.net/v1/API/Limit?apikey=K", Tgdb.limit("K"))
        assertEquals("Tom+%26+Jerry", Tgdb.byName("K", "Tom & Jerry", "6").substringAfter("name=").substringBefore('&'))
    }

    @Test fun mdUsesGenesisAndMegaDrive() {
        assertEquals("18,36", Tgdb.platforms("MD", "Sonic.md"))
        assertEquals("9", Tgdb.platforms("WII", "Super Mario Galaxy 2 (USA).wbfs"))
        assertEquals("2", Tgdb.platforms("GC", "Mario Kart - Double Dash!! (USA).ciso"))
        assertEquals("4912", Tgdb.platforms("3DS", "Pokemon X (USA).3ds"))
        assertEquals("21", Tgdb.platforms("GPGX", "Sonic CD.chd"))
        assertEquals("20", Tgdb.platforms("GPGX", "Sonic.gg"))
        assertEquals("23", Tgdb.platforms("DC", "mvsc2.zip"))
        assertEquals("16", Tgdb.platforms("DC", "Crazy Taxi.chd"))
        assertNull(Tgdb.platforms("TIC80", "x.tic"))
        assertNull(Tgdb.platforms("PORTS", "x.sh"))
    }

    @Test fun everyScraperSystemsIdIsMappedOrListedAsNone() {
        for ((tag, id) in ScraperSystems.table) {
            val mapped = Tgdb.platforms(tag, "x.bin") != null
            assertTrue(mapped != (id in Tgdb.NONE), "tag $tag (id $id): mapped=$mapped, none=${id in Tgdb.NONE}")
        }
        for (file in listOf("x.cue", "x.sms", "x.gg", "x.sg")) assertTrue(Tgdb.platforms("MD", file) != null)
    }

    @Test fun parsesGamesBoxartAndAllowance() {
        val g = Tgdb.parseGames(TgdbFixtures.byName(left = 100, extra = 20))
        assertEquals(listOf(432, 53, 9001), g.games.map { it.id })
        assertEquals(TgdbGame(53, "Sonic the Hedgehog", 18), g.games[1])
        assertEquals(3, g.boxart.getValue(53).size)
        assertEquals("https://cdn.thegamesdb.net/images/original/", g.baseUrl)
        assertEquals(120, g.left)
        val i = Tgdb.parseImages(TgdbFixtures.images(left = 7))
        assertEquals(listOf(4600, 4521, 17438), i.images.getValue(53).map { it.id })
        assertNull(i.images.getValue(53)[0].side)
        assertEquals(7, i.left)
        assertEquals(1820, Tgdb.parseLimit(TgdbFixtures.limit()))
    }

    @Test fun garbageBodyIsEmpty() {
        for (body in listOf(null, "", "<html>", """{"data":{"games":"x"}}""", """{"data":{"games":[{"id":"no"}]}}""")) {
            val g = Tgdb.parseGames(body)
            assertTrue(g.games.isEmpty() && g.boxart.isEmpty() && g.left == null, "$body")
            assertTrue(Tgdb.parseImages(body).images.isEmpty())
            assertNull(Tgdb.parseLimit(body))
        }
    }

    @Test fun matchIsExactAfterNormalising() {
        val games = Tgdb.parseGames(TgdbFixtures.byName()).games
        assertEquals(53, Tgdb.match(games, "Sonic the Hedgehog")?.id)
        assertEquals(53, Tgdb.match(games, "Sonic the Hedgehog (USA, Europe)")?.id)
        assertEquals(432, Tgdb.match(games, "Sonic the Hedgehog 2")?.id)
        assertNull(Tgdb.match(games, "Sonic"))
        assertNull(Tgdb.match(games, "Sonic the Hedgehog 3"))
    }

    @Test fun tieTakesLowestId() {
        val games = listOf(TgdbGame(9001, "Sonic The Hedgehog", 36), TgdbGame(53, "Sonic the Hedgehog", 18))
        assertEquals(53, Tgdb.match(games, "SONIC THE HEDGEHOG")?.id)
    }

    @Test fun anotherPlatformNeverMatches() {
        // The API answered the SNES game (6, lower id) for a Genesis search with filter[platform]=18,36.
        val games = listOf(TgdbGame(28104, "Super Street Fighter II", 18), TgdbGame(2792, "Super Street Fighter II", 6))
        assertEquals(28104, Tgdb.match(games, "Super Street Fighter II", "18,36")?.id)
        assertNull(Tgdb.match(games.drop(1), "Super Street Fighter II", "18,36"))
        assertEquals(2792, Tgdb.match(games, "Super Street Fighter II")?.id)
    }

    @Test fun looserMatchAfterExactKeepsThePlatform() {
        val md = listOf(TgdbGame(30, "Sonic the Hedgehog 2", 6), TgdbGame(31, "Sonic the Hedgehog 2", 18))
        assertEquals(31, Tgdb.match(md, "Sonic The Hedgehog II", "18,36")?.id)
        assertNull(Tgdb.match(md.take(1), "Sonic The Hedgehog II", "18,36"))
        val dc = listOf(TgdbGame(40, "ChuChu Rocket!", 16), TgdbGame(41, "Crazy Taxi 2", 16))
        assertEquals(40, Tgdb.match(dc, "ChuChu Rocket", "16")?.id)
        assertEquals(41, Tgdb.match(dc, "Crazy Taxi 2 v1.004", "16")?.id)
        val mvc2 = listOf(TgdbGame(42, "Marvel vs. Capcom 2: New Age of Heroes", 16), TgdbGame(43, "Marvel vs. Capcom", 16))
        assertEquals(42, Tgdb.match(mvc2, "Marvel vs Capcom 2", "16")?.id)
        // The head only from the first result: a hack listed below the search's best answer never matches.
        assertNull(Tgdb.match(mvc2.reversed(), "Marvel vs Capcom 2", "16"))
        // Live answers: the search for "Pokemon - FireRed" listed hacks only; "[Case Bundle]" is its own entry.
        val fireRed = listOf(TgdbGame(1, "Pokemon - FireRed Extended", 5), TgdbGame(2, "Pokemon FireRed Omega Plus Plus", 5), TgdbGame(3, "Pokémon FireRed: VR Missions", 5))
        assertNull(Tgdb.match(fireRed, "Pokemon - FireRed", "5"))
        assertNull(Tgdb.match(listOf(TgdbGame(4, "Pokemon - Emerald Enhanced", 5), TgdbGame(5, "Pokemon Emerald Version [Case Bundle]", 5)), "Pokemon - Emerald", "5"))
        val gba = listOf(TgdbGame(52, "Pokémon FireRed Version", 5), TgdbGame(51, "Pokémon FireRed Version", 5), TgdbGame(50, "Pokémon LeafGreen Version", 5))
        assertEquals(51, Tgdb.match(gba, "Pokemon FireRed", "5")?.id)
        // Exact before looser even at a higher id.
        assertEquals(61, Tgdb.match(listOf(TgdbGame(60, "Tetris!", 4), TgdbGame(61, "Tetris", 4)), "Tetris", "4")?.id)
        // False positives.
        assertNull(Tgdb.match(listOf(TgdbGame(70, "Tetris 2", 4)), "Tetris", "4"))
        assertNull(Tgdb.match(listOf(TgdbGame(71, "Super Mario Bros. 3", 7)), "Super Mario Bros", "7"))
    }

    @Test fun rankBoxartFrontThenIdIgnoringResolution() {
        val ranked = Tgdb.rankBoxart(Tgdb.parseGames(TgdbFixtures.byName()).boxart.getValue(53))
        assertEquals(listOf(17438, 17500), ranked.map { it.id })   // the 300x400 one first: its id is lower
    }

    @Test fun rankScreensById() {
        assertEquals(listOf(4521, 4600), Tgdb.rankScreens(Tgdb.parseImages(TgdbFixtures.images()).images.getValue(53)).map { it.id })
    }

    @Test fun thumbBase() = runTest {
        val body = """{"data":{"base_url":{"original":"https://cdn.thegamesdb.net/images/original/","thumb":"https://cdn.thegamesdb.net/images/thumb/"},
            "images":{"53":[{"id":1,"type":"screenshot","side":null,"filename":"screenshots/1.jpg"}]}}}"""
        assertEquals("https://cdn.thegamesdb.net/images/thumb/", Tgdb.parseImages(body).thumbBase)
        val r = Rig()
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r.http.on("Games/Images", ok(body))
        val shot = r.src.find(sonic, setOf(ArtSlot.SCREENSHOT)).single()
        assertEquals("https://cdn.thegamesdb.net/images/thumb/screenshots/1.jpg", shot.thumb)
        assertEquals("https://cdn.thegamesdb.net/images/original/screenshots/1.jpg", shot.url)
        // The boxart's answer carries no thumb base here: the documented thumb CDN.
        val r2 = Rig()
        r2.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        val box = r2.src.find(sonic, setOf(ArtSlot.BOX_2D)).first()
        assertEquals("https://cdn.thegamesdb.net/images/thumb/boxart/front/53-1.jpg", box.thumb)
        assertEquals("https://cdn.thegamesdb.net/images/original/boxart/front/53-1.jpg", box.url)
    }

    @Test fun findReturnsOriginalUrlsAndOrigins() = runTest {
        val r = Rig()
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r.http.on("Games/Images", ok(TgdbFixtures.images()))
        val hits = r.src.find(sonic, setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT))
        assertEquals(
            listOf(
                ArtSlot.BOX_2D to "https://cdn.thegamesdb.net/images/original/boxart/front/53-1.jpg",
                ArtSlot.BOX_2D to "https://cdn.thegamesdb.net/images/original/boxart/front/53-2.jpg",
                ArtSlot.SCREENSHOT to "https://cdn.thegamesdb.net/images/original/screenshots/53-1.jpg",
                ArtSlot.SCREENSHOT to "https://cdn.thegamesdb.net/images/original/screenshots/53-2.jpg",
            ),
            hits.map { it.slot to it.url },
        )
        assertEquals(ArtOrigin("thegamesdb", "53", "17438"), hits[0].origin)
        assertTrue(r.http.calls.first().contains("name=Sonic+the+Hedgehog&"))
        assertEquals(248, r.src.left())
        assertTrue(r.http.times[1] - r.http.times[0] >= TheGamesDbSource.RATE_MS)
    }

    @Test fun screenshotCallOnlyWhenWanted() = runTest {
        val r = Rig()
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r.http.on("Games/Images", ok(TgdbFixtures.images()))
        val hits = r.src.find(sonic, setOf(ArtSlot.BOX_2D))
        assertEquals(0, r.http.count("Games/Images"))
        assertTrue(hits.all { it.slot == ArtSlot.BOX_2D })
        // No match: no screenshot call either.
        r.src.find(sonic.copy(name = "Nothing Like It"), setOf(ArtSlot.SCREENSHOT))
        assertEquals(0, r.http.count("Games/Images"))
        r.src.find(sonic, setOf(ArtSlot.SCREENSHOT))
        assertEquals(1, r.http.count("Games/Images"))
    }

    @Test fun wheelIsTheClearLogoInTheSameImagesCallAndTriedLast() = runTest {
        assertEquals(
            "https://api.thegamesdb.net/v1/Games/Images?apikey=K&games_id=53&filter%5Btype%5D=screenshot%2Cclearlogo",
            Tgdb.images("K", 53, listOf("screenshot", "clearlogo")),
        )
        val r = Rig()
        assertTrue(ArtSlot.WHEEL in r.src.slots && r.src.wheelLast)
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r.http.on("Games/Images", ok(TgdbFixtures.imagesWithLogos()))
        val hits = r.src.find(sonic, setOf(ArtSlot.SCREENSHOT, ArtSlot.WHEEL))
        assertEquals(1, r.http.count("Games/Images"))
        assertTrue(r.http.calls.last().endsWith("filter%5Btype%5D=screenshot%2Cclearlogo"))
        assertEquals(
            listOf(
                ArtSlot.SCREENSHOT to "https://cdn.thegamesdb.net/images/original/screenshots/53-1.jpg",
                ArtSlot.WHEEL to "https://cdn.thegamesdb.net/images/original/clearlogo/53.png",
                ArtSlot.WHEEL to "https://cdn.thegamesdb.net/images/original/clearlogo/53-2.png",
            ),
            hits.map { it.slot to it.url },
        )
        assertEquals(ArtOrigin(SourceId.TGDB, "53", "9101"), hits[1].origin)
        // The wheel alone right after (the engine's last-resort ask): the logos already read, no call.
        val again = r.src.find(sonic, setOf(ArtSlot.WHEEL))
        assertEquals(2, r.http.calls.size)
        assertEquals(hits.drop(1).map { it.url }, again.map { it.url })
        // The wheel alone with nothing remembered: the search, then only clear logos asked for.
        val r2 = Rig()
        r2.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r2.http.on("Games/Images", ok(TgdbFixtures.imagesWithLogos()))
        val wheelOnly = r2.src.find(sonic, setOf(ArtSlot.WHEEL))
        assertTrue(r2.http.calls.last().endsWith("filter%5Btype%5D=clearlogo"))
        assertEquals(listOf(ArtSlot.WHEEL, ArtSlot.WHEEL), wheelOnly.map { it.slot })
    }

    @Test fun aWheelBesideOtherSlotsSpendsNoRequestOfItsOwn() = runTest {
        val r = Rig()
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r.http.on("Games/Images", ok(TgdbFixtures.imagesWithLogos()))
        // A 2D box and the wheel: no Images call for the wheel (it rides only on a screenshot call).
        val box = r.src.find(sonic, setOf(ArtSlot.BOX_2D, ArtSlot.WHEEL))
        assertEquals(0, r.http.count("Games/Images"))
        assertTrue(box.isNotEmpty() && box.all { it.slot == ArtSlot.BOX_2D })
        // Then the wheel alone for the same game: the match is kept, one clear-logo call.
        val wheels = r.src.find(sonic, setOf(ArtSlot.WHEEL))
        assertEquals(1, r.http.count("Games/ByGameName"))
        assertEquals(1, r.http.count("Games/Images"))
        assertTrue(r.http.calls.last().endsWith("filter%5Btype%5D=clearlogo"))
        assertEquals(listOf(ArtSlot.WHEEL, ArtSlot.WHEEL), wheels.map { it.slot })
        // A game that matched nothing: its wheel ask costs nothing.
        val other = sonic.copy(gamePath = "Roms/MD/Nothing.md", name = "Nothing Like It")
        assertTrue(r.src.find(other, setOf(ArtSlot.BOX_2D, ArtSlot.WHEEL)).isEmpty())
        assertTrue(r.src.find(other, setOf(ArtSlot.WHEEL)).isEmpty())
        assertEquals(2, r.http.count("Games/ByGameName"))
        assertEquals(1, r.http.count("Games/Images"))
    }

    @Test fun stopsAtOneLeft() = runTest {
        val r = Rig()
        r.http.on("API/Limit", ok(TgdbFixtures.limit(left = 1)))
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r.src.startJob()
        val e = assertFailsWith<SourceStop> { r.src.find(sonic, setOf(ArtSlot.BOX_2D)) }
        assertEquals(TheGamesDbSource.LIMIT_REACHED, e.note)
        assertEquals(0, r.http.count("Games/ByGameName"))
        // Extra allowance counts.
        val r2 = Rig()
        r2.http.on("API/Limit", ok(TgdbFixtures.limit(left = 0, extra = 50)))
        r2.http.on("Games/ByGameName", ok(TgdbFixtures.byName()))
        r2.src.startJob()
        assertTrue(r2.src.find(sonic, setOf(ArtSlot.BOX_2D)).isNotEmpty())
    }

    @Test fun boxKeptWhenTheScreenshotCallHitsTheLimit() = runTest {
        val r = Rig()
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName(left = 1)))
        r.http.on("Games/Images", ok(TgdbFixtures.images()))
        val hits = r.src.find(sonic, setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT))
        assertTrue(hits.isNotEmpty() && hits.all { it.slot == ArtSlot.BOX_2D })
        assertEquals(0, r.http.count("Games/Images"))
        assertFailsWith<SourceStop> { r.src.find(sonic, setOf(ArtSlot.BOX_2D)) }
    }

    @Test fun forbiddenStops() = runTest {
        val r = Rig()
        r.http.on("Games/ByGameName", HttpResult(403, null))
        assertEquals(TheGamesDbSource.LIMIT_REACHED, assertFailsWith<SourceStop> { r.src.find(sonic, setOf(ArtSlot.BOX_2D)) }.note)
        val r2 = Rig()
        r2.http.on("Games/ByGameName", HttpResult(502, null))
        assertEquals(TheGamesDbSource.UNAVAILABLE, assertFailsWith<SourceStop> { r2.src.find(sonic, setOf(ArtSlot.BOX_2D)) }.note)
    }

    @Test fun oneServerErrorIsAskedAgainBeforeTheSourceStops() = runTest {
        for (status in listOf(429, 502)) {
            val r = Rig()
            r.http.on("Games/ByGameName", HttpResult(status, null), ok(TgdbFixtures.byName()))
            assertTrue(r.src.find(sonic, setOf(ArtSlot.BOX_2D)).isNotEmpty())
            assertEquals(2, r.http.count("Games/ByGameName"))
            assertTrue(r.clock >= TheGamesDbSource.BUSY_WAIT_MS)
            val stop = Rig()
            stop.http.on("Games/ByGameName", HttpResult(status, null))
            assertEquals(TheGamesDbSource.UNAVAILABLE, assertFailsWith<SourceStop> { stop.src.find(sonic, setOf(ArtSlot.BOX_2D)) }.note)
            assertEquals(2, stop.http.count("Games/ByGameName"))
        }
    }

    @Test fun offlinePausesTheJob() = runTest {
        val r = Rig()
        r.http.on("Games/ByGameName", HttpResult(-1, null))
        assertEquals(ScrapeEngine.NO_CONNECTION, assertFailsWith<JobPause> { r.src.find(sonic, setOf(ArtSlot.BOX_2D)) }.reason)
        assertEquals(TheGamesDbSource.TRIES, r.http.calls.size)
    }

    @Test fun limitReadOnceAtJobStart() = runTest {
        val r = Rig()
        r.http.on("API/Limit", ok(TgdbFixtures.limit(left = 1820)))
        r.http.on("Games/ByGameName", ok(TgdbFixtures.byName(left = 1819)))
        val fs = Scrape.homeTree()
        val engine = ScrapeEngine(r.http, FakeFitter(), fs, Scrape.home, listOf(r.src), { _, _ -> }, {})
        val games = listOf(Scrape.homeGame("Sonic.md", "MD"), Scrape.homeGame("Tetris.gb"), Scrape.homeGame("Zelda.gb"))
        val states = mutableListOf<ScrapeState>()
        val end = engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> games.firstOrNull { it.gamePath == p } }) { states += it }
        assertEquals(1, r.http.count("API/Limit"))
        assertEquals(1, r.http.calls.indexOfFirst { "ByGameName" in it })   // after the limit read
        assertEquals(1819, end.tgdbLeft)
        assertTrue(states.any { it.tgdbLeft == 1820 })
    }

    @Test fun validateKeyResults() = runTest {
        val unknownKey = """{"remaining_monthly_allowance":0,"allowance_refresh_timer":0}"""   // the API's answer to a made-up key
        val spent = """{"remaining_monthly_allowance":0,"extra_allowance":0,"allowance_refresh_timer":86400}"""
        for ((answer, result) in listOf(
            ok(TgdbFixtures.limit()) to KeyResult.OK, ok(spent) to KeyResult.OK, ok(unknownKey) to KeyResult.BAD_KEY,
            ok("<html>") to KeyResult.UNAVAILABLE, HttpResult(401, null) to KeyResult.BAD_KEY, HttpResult(403, null) to KeyResult.BAD_KEY,
            HttpResult(-1, null) to KeyResult.OFFLINE, HttpResult(500, null) to KeyResult.UNAVAILABLE, HttpResult(429, null) to KeyResult.UNAVAILABLE,
        )) {
            val r = Rig(key = "")
            r.http.on("API/Limit", answer)
            assertEquals(result, r.src.check("NEW"), "${answer.status} ${answer.body?.decodeToString()}")
            assertEquals(listOf("https://api.thegamesdb.net/v1/API/Limit?apikey=NEW"), r.http.calls)
        }
        assertEquals(true, Tgdb.keyAccepted(TgdbFixtures.limit()))
        assertEquals(false, Tgdb.keyAccepted(unknownKey))
        assertEquals(null, Tgdb.keyAccepted("{}"))
    }

    @Test fun allowanceBelongsToTheKeyItWasReadWith() = runTest {
        val r = Rig(key = "BUILD")
        r.http.on("apikey=BUILD", ok(TgdbFixtures.limit(left = 900)))
        r.http.on("apikey=MINE", ok(TgdbFixtures.limit(left = 2000)))
        assertEquals(900, r.src.allowance())
        assertEquals(900, r.src.left())
        r.key = "MINE"
        assertNull(r.src.left())   // unknown until read with the new key
        assertEquals(2000, r.src.allowance())
        r.key = "BUILD"
        assertNull(r.src.left())
    }

    @Test fun disabledWithoutKey() = runTest {
        val r = Rig(key = "")
        assertFalse(r.src.enabled)
        assertTrue(r.src.find(sonic, setOf(ArtSlot.BOX_2D)).isEmpty())
        r.src.startJob()
        assertTrue(r.http.calls.isEmpty())
        assertTrue(Rig().src.enabled)
        assertTrue(Rig().src.knows(sonic))
        assertFalse(Rig().src.knows(Scrape.homeGame("x.tic", "TIC80")))
        assertFalse(Rig().src.knows(Scrape.androidGame("com.x", "X")))
    }
}
