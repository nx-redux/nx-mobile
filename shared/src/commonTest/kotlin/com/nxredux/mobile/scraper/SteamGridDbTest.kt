package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.ensurePath
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.scraper.Scrape.OK_PNG
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SteamGridDB bodies in the shapes of the official OpenAPI file (`https://www.steamgriddb.com/static/openapi.yml`,
 * 2.10.0) plus the asset fields the live API returns (width, height, mime, tags as booleans); no key was at
 * hand to record live ones. No key values anywhere.
 */
object SgdbFixtures {
    val autocomplete = """
    {"success":true,"data":[
      {"id":5246,"name":"Tetris","release_date":454982400,"types":["steam"],"verified":false},
      {"id":5247,"name":"TETRIS","release_date":454982400,"types":[],"verified":true},
      {"id":17,"name":"Tetris Effect","release_date":1541462400,"types":["steam"],"verified":true}]}
    """.trimIndent()

    private fun asset(id: Int, w: Int, h: Int, score: Int, style: String = "alternate", mime: String = "image/png", url: String = "https://cdn2.steamgriddb.com/grid/$id.png", tags: String = "") =
        """{"id":$id,"score":$score,"style":"$style","width":$w,"height":$h,"nsfw":${"nsfw" in tags},"humor":${"humor" in tags},"epilepsy":${"epilepsy" in tags},"notes":null,"mime":"$mime","language":"en","url":"$url","thumb":"https://cdn2.steamgriddb.com/thumb/$id.png","lock":false,"upvotes":0,"downvotes":0,"author":{"name":"a","steam64":"1","avatar":"https://x/a.jpg"}}"""

    val grids = """{"success":true,"page":0,"total":7,"limit":50,"data":[
      ${asset(10, 600, 900, 3)},
      ${asset(11, 920, 430, 9)},
      ${asset(12, 342, 482, 3)},
      ${asset(13, 460, 215, 8)},
      ${asset(14, 660, 930, 5, style = "blurred")},
      ${asset(15, 1024, 1024, 7)},
      ${asset(9, 1200, 1800, 3, mime = "image/jpeg", url = "https://cdn2.steamgriddb.com/grid/9.jpg")}]}"""

    val heroes = """{"success":true,"page":0,"total":5,"limit":50,"data":[
      ${asset(30, 1920, 620, 2, url = "https://cdn2.steamgriddb.com/hero/30.png")},
      ${asset(31, 3840, 1240, 4, style = "blurred", url = "https://cdn2.steamgriddb.com/hero/31.png")},
      ${asset(29, 1600, 650, 2, mime = "image/jpeg", url = "https://cdn2.steamgriddb.com/hero/29.jpg")},
      ${asset(32, 1920, 1080, 9, url = "https://cdn2.steamgriddb.com/hero/32.png")},
      ${asset(33, 1920, 620, 9, mime = "image/webp", url = "https://cdn2.steamgriddb.com/hero/33.webp")}]}"""

    /** `logos/game`: the same asset shape (styles official, white, black, custom). */
    val logos = """{"success":true,"page":0,"total":4,"limit":50,"data":[
      ${asset(50, 800, 310, 1, style = "official", url = "https://cdn2.steamgriddb.com/logo/50.png")},
      ${asset(51, 1200, 400, 6, style = "white", url = "https://cdn2.steamgriddb.com/logo/51.png")},
      ${asset(52, 600, 600, 6, style = "custom", url = "https://cdn2.steamgriddb.com/logo/52.png", tags = "humor")},
      ${asset(49, 900, 300, 1, style = "black", url = "https://cdn2.steamgriddb.com/logo/49.png")}]}"""
}

class SteamGridDbTest {
    private val tetris = Scrape.homeGame("Tetris (World) (Rev 1).gb").copy(name = "Tetris (World) (Rev 1)")
    private fun ok(s: String) = HttpResult(200, s.encodeToByteArray())

    private class Rig(key: String? = "K") {
        var clock = 0L
        val http = FakeHttp().apply { clock = { this@Rig.clock } }
        val src = SteamGridDbSource(http, { key }, { clock }) { clock += it }
        fun answerAll() {
            http.on("search/autocomplete/", HttpResult(200, SgdbFixtures.autocomplete.encodeToByteArray()))
            http.on("grids/game/", HttpResult(200, SgdbFixtures.grids.encodeToByteArray()))
            http.on("heroes/game/", HttpResult(200, SgdbFixtures.heroes.encodeToByteArray()))
        }
    }

    @Test fun urlsAndBearerHeader() = runTest {
        assertEquals("https://www.steamgriddb.com/api/v2/search/autocomplete/Tom%20%26%20Jerry%3A%20War", Sgdb.search("Tom & Jerry: War"))
        assertEquals(
            "https://www.steamgriddb.com/api/v2/heroes/game/5247?types=static&mimes=image/png,image/jpeg&nsfw=false&humor=false&epilepsy=false",
            Sgdb.heroes(5247),
        )
        val r = Rig()
        r.answerAll()
        r.src.find(tetris, setOf(ArtSlot.GRID, ArtSlot.HERO))
        assertEquals(
            listOf("search/autocomplete/Tetris", "grids/game/5247", "heroes/game/5247"),
            r.http.calls.map { it.removePrefix(Sgdb.API + "/").substringBefore('?') },
        )
        assertTrue(r.http.headers.all { it == mapOf("Authorization" to "Bearer K") })
        assertTrue(r.http.calls.none { "K" in it.removePrefix(Sgdb.API) })   // the key is only ever a header
        assertTrue(r.http.times.zipWithNext().all { (a, b) -> b - a >= SteamGridDbSource.RATE_MS })
    }

    @Test fun wheelIsTheLogosByScoreThenId() = runTest {
        assertEquals(
            "https://www.steamgriddb.com/api/v2/logos/game/5247?types=static&mimes=image/png&nsfw=false&humor=false&epilepsy=false",
            Sgdb.logos(5247),
        )
        val r = Rig()
        r.answerAll()
        r.http.on("logos/game/", HttpResult(200, SgdbFixtures.logos.encodeToByteArray()))
        val hits = r.src.find(tetris, setOf(ArtSlot.WHEEL))
        assertEquals(listOf("search/autocomplete/Tetris", "logos/game/5247"), r.http.calls.map { it.removePrefix(Sgdb.API + "/").substringBefore('?') })
        assertEquals(
            listOf("https://cdn2.steamgriddb.com/logo/51.png", "https://cdn2.steamgriddb.com/logo/49.png", "https://cdn2.steamgriddb.com/logo/50.png"),
            hits.map { it.url },
        )   // the humor one dropped; any style or shape
        assertTrue(hits.all { it.slot == ArtSlot.WHEEL })
        assertEquals(ArtOrigin(SourceId.SGDB, "5247", "51"), hits[0].origin)
    }

    @Test fun gridQueryHasAlternateAndNoDimensions() {
        val url = Sgdb.grids(5247)
        assertEquals(
            "https://www.steamgriddb.com/api/v2/grids/game/5247?styles=alternate&types=static&mimes=image/png,image/jpeg&nsfw=false&humor=false&epilepsy=false",
            url,
        )
        assertFalse("dimensions" in url)
    }

    @Test fun matchPrefersVerified() {
        val games = Sgdb.parseGames(SgdbFixtures.autocomplete)
        assertEquals(listOf(5246, 5247, 17), games.map { it.id })
        assertEquals(5247, Sgdb.match(games, "Tetris (World) (Rev 1)")?.id)
        assertEquals(17, Sgdb.match(games, "Tetris Effect")?.id)
        assertNull(Sgdb.match(games, "Tetris 2"))
        // No verified one among the same names: API order.
        assertEquals(5246, Sgdb.match(games.filter { it.id != 5247 }, "tetris")?.id)
    }

    @Test fun looserMatchAfterExact() {
        fun g(id: Int, name: String, verified: Boolean = false) = SgdbGame(id, name, verified)
        assertEquals(1, Sgdb.match(listOf(g(1, "ChuChu Rocket!"), g(2, "ChuChu Rocket! 2")), "ChuChu Rocket")?.id)
        assertEquals(3, Sgdb.match(listOf(g(3, "Marvel vs. Capcom 2: New Age of Heroes"), g(4, "Marvel vs. Capcom")), "Marvel vs Capcom 2")?.id)
        assertEquals(5, Sgdb.match(listOf(g(5, "Crazy Taxi 2")), "Crazy Taxi 2 v1.004")?.id)
        assertEquals(6, Sgdb.match(listOf(g(6, "Sonic the Hedgehog 2")), "Sonic The Hedgehog II")?.id)
        assertEquals(8, Sgdb.match(listOf(g(7, "Pokémon Ruby Version"), g(8, "Pokémon Emerald Version")), "Pokemon Emerald")?.id)
        // Exact first even when a looser one is verified; within the looser tier verified first.
        assertEquals(9, Sgdb.match(listOf(g(10, "ChuChu Rocket!", true), g(9, "ChuChu Rocket")), "ChuChu Rocket")?.id)
        assertEquals(12, Sgdb.match(listOf(g(11, "Chuchu Rocket!"), g(12, "ChuChu Rocket!", true)), "ChuChu Rocket")?.id)
        // False positives.
        assertNull(Sgdb.match(listOf(g(13, "Super Mario Bros. 3")), "Super Mario Bros"))
        assertNull(Sgdb.match(listOf(g(14, "Street Fighter II Turbo")), "Street Fighter II"))
        assertNull(Sgdb.match(listOf(g(15, "Street Fighter II: The World Warrior"), g(16, "Street Fighter II: Champion Edition")), "Street Fighter II"))
        // Live answers: a title's groups tell games apart; fan games below the first result never match.
        assertNull(Sgdb.match(listOf(g(17, "Tetris Ultimate"), g(18, "Tetris 2"), g(19, "Tetris (IBM DOS)")), "Tetris"))
        assertEquals(21, Sgdb.match(listOf(g(20, "Sonic The Hedgehog 4: Episode II"), g(21, "Sonic the Hedgehog 2"), g(22, "Sonic the Hedgehog 2 (Sega Master System)"), g(23, "Sonic the Hedgehog 2: Build a Burger")), "Sonic The Hedgehog II")?.id)
        assertNull(Sgdb.match(listOf(g(20, "Sonic The Hedgehog 4: Episode II"), g(23, "Sonic the Hedgehog 2: Build a Burger")), "Sonic The Hedgehog II"))
        assertNull(Sgdb.match(listOf(g(24, "Sonic the Hedgehog 4: Episode I"), g(25, "Sonic the Hedgehog: Back in Time"), g(26, "Sonic The Hedgehog: Lost Island")), "Sonic The Hedgehog I"))
    }

    @Test fun searchDropsAVersionNumber() = runTest {
        val r = Rig()
        r.src.find(Scrape.homeGame("Crazy Taxi 2 v1.004.cdi", "DC"), setOf(ArtSlot.GRID))
        assertTrue(r.http.calls.first().endsWith("search/autocomplete/Crazy%20Taxi%202"), r.http.calls.first())
    }

    @Test fun rankGridsAlternatePortraitShapeByScore() {
        // 10 (600x900, 3) and 9 (1200x1800 JPEG, 3): same score, id ascending; 12 (342x482, h/w 1.41, 3) by id after 10.
        assertEquals(listOf(9, 10, 12), Sgdb.rankGrids(Sgdb.parseAssets(SgdbFixtures.grids)).map { it.id })
        val scored = listOf(SgdbAsset(1, "https://x/1.png", 600, 900, "image/png", 1, style = "alternate"), SgdbAsset(2, "https://x/2.png", 600, 900, "image/png", 5, style = "alternate"))
        assertEquals(listOf(2, 1), Sgdb.rankGrids(scored).map { it.id })
    }

    @Test fun horizontalGridsDropped() {
        val ranked = Sgdb.rankGrids(Sgdb.parseAssets(SgdbFixtures.grids)).map { it.id }
        assertFalse(11 in ranked || 13 in ranked)   // 920x430, 460x215
        assertFalse(15 in ranked)                   // square
        assertFalse(14 in ranked)                   // blurred style
        assertTrue(Sgdb.portrait(SgdbAsset(1, "u", 100, 130)) && Sgdb.portrait(SgdbAsset(1, "u", 100, 170)))
        assertFalse(Sgdb.portrait(SgdbAsset(1, "u", 100, 129)) || Sgdb.portrait(SgdbAsset(1, "u", 100, 171)) || Sgdb.portrait(SgdbAsset(1, "u", 0, 0)))
    }

    @Test fun largeAndSmallGridsBothEligible() {
        val ids = Sgdb.rankGrids(Sgdb.parseAssets(SgdbFixtures.grids)).map { it.id }
        assertTrue(9 in ids && 12 in ids)   // 1200x1800 and 342x482: size never filters
    }

    @Test fun rankHeroesSortsByScoreThenId() {
        // 31 (blurred, 4) first; 29 and 30 tie on 2: id ascending. 32 is 16:9 (not wide), 33 WebP.
        assertEquals(listOf(31, 29, 30), Sgdb.rankHeroes(Sgdb.parseAssets(SgdbFixtures.heroes)).map { it.id })
    }

    @Test fun animatedAndNsfwDropped() {
        fun a(id: Int, url: String = "https://x/$id.png", mime: String? = "image/png", nsfw: Boolean = false, humor: Boolean = false, epilepsy: Boolean = false) =
            SgdbAsset(id, url, 600, 900, mime, 1, nsfw, humor, epilepsy, "alternate")
        val all = listOf(
            a(1), a(2, url = "https://x/2.webm", mime = "video/webm"), a(3, url = "https://x/3.gif", mime = "image/gif"),
            a(4, nsfw = true), a(5, humor = true), a(6, epilepsy = true), a(7, mime = "image/webp"), a(8, url = "https://x/8.jpg", mime = null),
            a(9, url = "https://x/9.webm", mime = null),
        )
        assertEquals(listOf(1, 8), Sgdb.rankGrids(all).map { it.id })
    }

    @Test fun garbageBodiesAreEmpty() {
        for (body in listOf(null, "", "<html>", """{"success":false,"errors":["Game not found"]}""", """{"data":"x"}""", """{"success":true,"data":[{"id":"no"}]}""")) {
            assertTrue(Sgdb.parseGames(body).isEmpty(), "$body")
            assertTrue(Sgdb.parseAssets(body).isEmpty(), "$body")
        }
        assertEquals(SgdbAsset(1, "https://x/1.png"), Sgdb.parseAssets("""{"success":true,"data":[{"id":1,"url":"https://x/1.png"}]}""").single())
    }

    @Test fun thumbFromAsset() = runTest {
        val r = Rig()
        r.http.on("search/autocomplete/", ok(SgdbFixtures.autocomplete))
        r.http.on("grids/game/", ok("""{"success":true,"data":[{"id":7,"score":1,"style":"alternate","width":600,"height":900,"mime":"image/jpeg","url":"https://cdn2.steamgriddb.com/grid/x.jpg","thumb":"https://cdn2.steamgriddb.com/thumb/x.jpg"}]}"""))
        val hit = r.src.find(tetris, setOf(ArtSlot.GRID)).single()
        assertEquals("https://cdn2.steamgriddb.com/grid/x.jpg", hit.url)
        assertEquals("https://cdn2.steamgriddb.com/thumb/x.jpg", hit.thumb)
        assertNull(Sgdb.parseAssets("""{"success":true,"data":[{"id":1,"url":"https://x/1.png","thumb":""}]}""").single().thumb)
    }

    @Test fun findReturnsGridsThenHeroesWithOrigins() = runTest {
        val r = Rig()
        r.answerAll()
        val hits = r.src.find(tetris, setOf(ArtSlot.GRID, ArtSlot.HERO))
        assertEquals(
            listOf(ArtSlot.GRID to "https://cdn2.steamgriddb.com/grid/9.jpg", ArtSlot.GRID to "https://cdn2.steamgriddb.com/grid/10.png", ArtSlot.GRID to "https://cdn2.steamgriddb.com/grid/12.png",
                ArtSlot.HERO to "https://cdn2.steamgriddb.com/hero/31.png", ArtSlot.HERO to "https://cdn2.steamgriddb.com/hero/29.jpg", ArtSlot.HERO to "https://cdn2.steamgriddb.com/hero/30.png"),
            hits.map { it.slot to it.url },
        )
        assertEquals(ArtOrigin("steamgriddb", "5247", "9"), hits[0].origin)
        // Only what is wanted is asked for; no match asks nothing more.
        val r2 = Rig(); r2.answerAll()
        assertTrue(r2.src.find(tetris, setOf(ArtSlot.HERO)).all { it.slot == ArtSlot.HERO })
        assertEquals(0, r2.http.count("grids/game/"))
        r2.src.find(tetris.copy(name = "Nothing Like It"), setOf(ArtSlot.GRID, ArtSlot.HERO))
        assertEquals(1, r2.http.count("heroes/game/"))
    }

    @Test fun badKeyStops() = runTest {
        for ((status, note) in listOf(401 to SteamGridDbSource.KEY_REFUSED, 403 to SteamGridDbSource.KEY_REFUSED, 429 to SteamGridDbSource.TOO_MANY, 503 to SteamGridDbSource.UNAVAILABLE)) {
            val r = Rig()
            r.http.on("search/autocomplete/", HttpResult(status, null))
            assertEquals(note, assertFailsWith<SourceStop> { r.src.find(tetris, setOf(ArtSlot.GRID)) }.note)
        }
        val off = Rig()
        off.http.on("search/autocomplete/", HttpResult(-1, null))
        assertEquals(ScrapeEngine.NO_CONNECTION, assertFailsWith<JobPause> { off.src.find(tetris, setOf(ArtSlot.GRID)) }.reason)
        assertEquals(SteamGridDbSource.TRIES, off.http.calls.size)
        // Not found is not a stop.
        val nf = Rig()
        nf.http.on("search/autocomplete/", HttpResult(404, """{"success":false}""".encodeToByteArray()))
        assertTrue(nf.src.find(tetris, setOf(ArtSlot.GRID)).isEmpty())
    }

    @Test fun oneServerErrorIsAskedAgainBeforeTheSourceStops() = runTest {
        for (status in listOf(429, 503)) {
            val r = Rig()
            r.answerAll()
            r.http.set("search/autocomplete/", HttpResult(status, null), ok(SgdbFixtures.autocomplete))
            assertTrue(r.src.find(tetris, setOf(ArtSlot.GRID)).isNotEmpty())
            assertEquals(2, r.http.count("search/autocomplete/"))
            assertTrue(r.clock >= SteamGridDbSource.BUSY_WAIT_MS)
        }
        val r = Rig()
        r.http.on("search/autocomplete/", HttpResult(503, null))
        assertFailsWith<SourceStop> { r.src.find(tetris, setOf(ArtSlot.GRID)) }
        assertEquals(2, r.http.count("search/autocomplete/"))   // twice, never more
    }

    @Test fun gridsKeptWhenTheHeroCallStops() = runTest {
        val r = Rig()
        r.http.on("search/autocomplete/", ok(SgdbFixtures.autocomplete))
        r.http.on("grids/game/", ok(SgdbFixtures.grids))
        r.http.on("heroes/game/", HttpResult(429, null))
        val hits = r.src.find(tetris, setOf(ArtSlot.GRID, ArtSlot.HERO))
        assertTrue(hits.isNotEmpty() && hits.all { it.slot == ArtSlot.GRID })
        assertEquals(SteamGridDbSource.TOO_MANY, assertFailsWith<SourceStop> { r.src.find(tetris, setOf(ArtSlot.GRID)) }.note)
        r.src.startJob()   // a new run asks again
        r.http.set("heroes/game/", ok(SgdbFixtures.heroes))
        assertTrue(r.src.find(tetris, setOf(ArtSlot.HERO)).isNotEmpty())
    }

    @Test fun validateKeyResults() = runTest {
        for ((status, result) in listOf(200 to KeyResult.OK, 401 to KeyResult.BAD_KEY, 403 to KeyResult.BAD_KEY, -1 to KeyResult.OFFLINE, 500 to KeyResult.UNAVAILABLE, 429 to KeyResult.UNAVAILABLE)) {
            val r = Rig(key = null)
            r.http.on("search/autocomplete/tetris", HttpResult(status, null))
            assertEquals(result, r.src.check("NEW"), "$status")
            assertEquals(listOf(mapOf("Authorization" to "Bearer NEW")), r.http.headers)
        }
    }

    @Test fun disabledWithoutKey() = runTest {
        for (key in listOf(null, "")) {
            val r = Rig(key = key)
            assertFalse(r.src.enabled)
            assertTrue(r.src.find(tetris, setOf(ArtSlot.GRID, ArtSlot.HERO)).isEmpty())
            assertTrue(r.http.calls.isEmpty())
        }
        val on = Rig().src
        assertTrue(on.enabled)
        assertTrue(on.knows(tetris))
        assertTrue(on.knows(Scrape.homeGame("x.tic", "TIC80")))   // no platform filter
        assertFalse(on.knows(Scrape.homeGame("x.sh", "PORTS")))
        assertFalse(on.knows(Scrape.androidGame("com.x", "X")))
    }

    // --- The engine's want rule, with fake earlier sources and a fake SteamGridDB ---

    private class EngineRig(vararg val sources: FakeSource, var extras: ArtExtras = ArtExtras(), pixels: PixelCodec? = null) {
        val fs = Scrape.homeTree()
        val http = FakeHttp().apply { on("https://", OK_PNG) }
        val written = mutableListOf<String>()
        val arts = mutableListOf<ArtRefs>()
        val log = FakeLoggedArt(mutableSetOf())
        val engine = ScrapeEngine(
            http, FakeFitter(), fs, Scrape.home, sources.toList(), onArt = { _, a -> arts += a },
            onWritten = { w -> written += w.map { it.first }; log.record(w) }, logged = log, extras = { extras }, pixels = pixels,
        )
        suspend fun run(vararg games: LibraryGameRef, replace: Boolean = false) =
            engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> games.firstOrNull { it.gamePath == p } }, replace) {}
    }

    private fun sgdb() = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID, ArtSlot.HERO)).apply {
        for (f in listOf("Tetris.gb", "Zelda.gb")) { offer(f, ArtSlot.GRID, "https://sg/grid-$f"); offer(f, ArtSlot.HERO, "https://sg/hero-$f") }
    }

    @Test fun gridWritesBoxart2d() = runTest {
        val sg = sgdb()
        val rig = EngineRig(FakeSource("ss"), sg)
        val end = rig.run(Scrape.homeGame("Tetris.gb"))
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.GRID, ArtSlot.HERO)), sg.asked)
        val media = "Roms/GB/.media"
        assertEquals(listOf("$media/boxart2d/Tetris.jpg", "$media/screenshot/Tetris.jpg"), rig.written)   // a grid's and a hero's cap: JPEG
        assertNotNull(rig.fs.bytesAt("Home/$media/boxart2d/Tetris.jpg"))
        assertNotNull(rig.fs.bytesAt("Home/$media/screenshot/Tetris.jpg"))
        assertTrue(rig.fs.listChildren(ContentRef("Home/$media")).none { it.name == "grid" || it.name == "hero" })
        assertEquals(listOf(SourceId.SGDB, SourceId.SGDB), rig.written.map { rig.log.origins.getValue(it).source })
        val art = rig.arts.single()
        assertEquals("Home/$media/boxart2d/Tetris.jpg", art.box2d)
        assertEquals("Home/$media/screenshot/Tetris.jpg", art.screenshot)
        assertTrue(art.heroShot)
        assertTrue(ArtHave.of(art).complete)
        val item = end.items.single()
        assertEquals(ItemStatus.DONE, item.status)
        assertTrue(item.reason!!.contains(ScrapeEngine.NO_SCREENSHOT), item.reason)   // the hero stand-in's note
    }

    @Test fun heroOnlyWhenNoScreenshot() = runTest {
        // An earlier source finds the screenshot: SteamGridDB's hero is never written over it.
        val ss = FakeSource("ss").apply { offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://ss/shot") }
        val sg = sgdb()
        val rig = EngineRig(ss, sg)
        val item = rig.run(Scrape.homeGame("Tetris.gb")).items.single()
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.GRID)), sg.asked)   // only the box
        assertEquals(ItemStatus.DONE to "ss, steamgriddb", item.status to item.reason)   // the sources that wrote
        val shot = rig.written.single { "/screenshot/" in it }
        assertEquals("ss", rig.log.origins.getValue(shot).source)
        assertFalse(rig.arts.single().heroShot)
        // A screenshot already (no box): not asked for a hero, only the grid (the box).
        val sg2 = sgdb()
        EngineRig(sg2).run(Scrape.homeGame("Tetris.gb", have = ArtHave(screenshot = true)))
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.GRID)), sg2.asked)
        // Under Replace a hero is fetched again.
        val sg3 = sgdb()
        EngineRig(sg3).run(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART), replace = true)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.GRID, ArtSlot.HERO)), sg3.asked)
    }

    @Test fun want2dTakesGrid() = runTest {
        val ss = FakeSource("ss", slots = setOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.SCREENSHOT)).apply {
            offer("Tetris.gb", ArtSlot.BOX_3D, "https://ss/3d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://ss/shot")
        }
        val sg = sgdb()
        val rig = EngineRig(ss, sg, extras = ArtExtras(box2d = true))
        assertEquals(ItemStatus.DONE, rig.run(Scrape.homeGame("Tetris.gb")).items.single().status)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.GRID)), sg.asked)   // the 2D box only
        assertEquals("ss", rig.log.origins.getValue("Roms/GB/.media/boxart/Tetris.png").source)
        assertEquals(SourceId.SGDB, rig.log.origins.getValue("Roms/GB/.media/boxart2d/Tetris.jpg").source)
    }

    @Test fun notAskedWhenEarlierSourcesFilledEverything() = runTest {
        val ss = FakeSource("ss").apply { offer("Tetris.gb", ArtSlot.BOX_2D, "https://ss/box"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://ss/shot") }
        val sg = sgdb()
        val rig = EngineRig(ss, sg)
        assertEquals(ItemStatus.DONE, rig.run(Scrape.homeGame("Tetris.gb")).items.single().status)
        assertTrue(sg.asked.isEmpty())
    }

    @Test fun aGridInTheOldFolderIsNotABox() = runTest {
        val sg = sgdb()
        val rig = EngineRig(sg)
        rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "grid"))?.let { rig.fs.writeBytes(it, "Tetris.jpg", byteArrayOf(1)) }
        rig.run(Scrape.homeGame("Tetris.gb", have = ArtHave(screenshot = true)))
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.GRID)), sg.asked)
        assertEquals(listOf("Roms/GB/.media/boxart2d/Tetris.jpg"), rig.written)
    }

    @Test fun replaceWithAGridRemovesTheAppsStale3dBox() = runTest {
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID)).apply { offer("Tetris.gb", ArtSlot.GRID, "https://sg/grid") }
        val rig = EngineRig(sg)
        val dir = rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!
        rig.fs.writeBytes(dir, "Tetris.png", byteArrayOf(1))
        val logged = FakeLoggedArt(mutableSetOf("Roms/GB/.media/boxart/Tetris.png"))
        val engine = ScrapeEngine(rig.http, FakeFitter(), rig.fs, Scrape.home, listOf(sg), onArt = { _, _ -> }, onWritten = {}, logged = logged)
        val game = Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART)
        engine.run(ScrapeState(items = listOf(Scrape.item(game))), { game }, replace = true) {}
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))   // the app's own 3D box would win over the new 2D box
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.jpg"))
    }

    private suspend fun EngineRig.putShot(name: String, bytes: ByteArray, origin: ArtOrigin?) {
        fs.writeBytes(fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!, name, bytes)
        if (origin != null) log.origins["Roms/GB/.media/screenshot/$name"] = origin
    }

    @Test fun replaceNeverPutsAHeroOverTheAppsScreenshot() = runTest {
        for (stopped in listOf(false, true)) {
            val ss = FakeSource("screenscraper").apply { if (stopped) fail = { SourceStop("Quota") } }
            val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.HERO, "https://sg/hero") }
            val rig = EngineRig(ss, sg)
            rig.putShot("Tetris.png", byteArrayOf(7), ArtOrigin("screenscraper"))
            rig.run(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART), replace = true)
            assertContentEquals(byteArrayOf(7), rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"), "stopped=$stopped")
            assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpg"))
            assertEquals("screenscraper", rig.log.origins.getValue("Roms/GB/.media/screenshot/Tetris.png").source)
            assertTrue(rig.written.none { "/screenshot/" in it }, rig.written.toString())
        }
    }

    @Test fun replaceNeverPutsAHeroOverAHandPlacedScreenshot() = runTest {
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.HERO, "https://sg/hero") }
        val rig = EngineRig(sg)
        rig.putShot("Tetris.png", byteArrayOf(8), origin = null)
        rig.run(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART), replace = true)
        assertContentEquals(byteArrayOf(8), rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertTrue(rig.written.isEmpty(), rig.written.toString())
    }

    @Test fun replaceKeepingARealScreenshotOverAHeroIsNotMissingOne() = runTest {
        // Only a hero offered for the screenshot: the real one stays, and the game is DONE once its box came.
        val ss = FakeSource("ss", slots = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT)).apply { offer("Tetris.gb", ArtSlot.BOX_2D, "https://ss/box") }
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.HERO, "https://sg/hero") }
        val rig = EngineRig(ss, sg)
        rig.putShot("Tetris.png", byteArrayOf(7), ArtOrigin("screenscraper"))
        val item = rig.run(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART), replace = true).items.single()
        assertEquals(ItemStatus.DONE to "ss", item.status to item.reason)
        assertContentEquals(byteArrayOf(7), rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        // No box either: only the box is missing.
        val ss2 = FakeSource("ss", slots = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT))
        val sg2 = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.HERO, "https://sg/hero") }
        val rig2 = EngineRig(ss2, sg2)
        rig2.putShot("Tetris.png", byteArrayOf(7), origin = null)
        val item2 = rig2.run(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART), replace = true).items.single()
        assertEquals(ScrapeEngine.NO_BOX, item2.reason)
    }

    @Test fun replaceMayPutANewHeroOverAHero() = runTest {
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.HERO, "https://sg/hero2") }
        val rig = EngineRig(sg)
        rig.putShot("Tetris.jpg", byteArrayOf(9), ArtOrigin(SourceId.SGDB, "1", "old"))
        rig.run(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART), replace = true)
        assertContentEquals(OK_PNG.body, rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpg"))
        assertEquals("https://sg/hero2", rig.log.origins.getValue("Roms/GB/.media/screenshot/Tetris.jpg").assetId)
        assertTrue(rig.arts.single().heroShot)
    }

    @Test fun aLaterRealScreenshotReplacesThisRunsHero() = runTest {
        // SteamGridDB asked first: its hero stands in, then the next source's screenshot replaces it in the same run.
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.HERO, "https://sg/hero") }
        val ss = FakeSource("ss").apply { offer("Tetris.gb", ArtSlot.BOX_2D, "https://ss/box"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://ss/shot") }
        val rig = EngineRig(sg, ss)
        val item = rig.run(Scrape.homeGame("Tetris.gb")).items.single()
        assertEquals(ItemStatus.DONE to "steamgriddb, ss", item.status to item.reason)   // no "no screenshot": a real one won
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpg"))         // the hero's file is gone
        val art = rig.arts.single()
        assertEquals("Home/Roms/GB/.media/screenshot/Tetris.png", art.screenshot)
        assertFalse(art.heroShot)
    }

    @Test fun want2dWithNo3dBoxTakesTheGridAsTheBox() = runTest {
        val ss = FakeSource("ss", slots = setOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.SCREENSHOT)).apply { offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://ss/shot") }
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID)).apply { offer("Tetris.gb", ArtSlot.GRID, "https://sg/grid") }
        val rig = EngineRig(ss, sg, extras = ArtExtras(box2d = true))
        val item = rig.run(Scrape.homeGame("Tetris.gb")).items.single()
        assertEquals(ItemStatus.DONE to "ss, steamgriddb", item.status to item.reason)   // the box is no longer missing
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.jpg"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertTrue(ArtHave.of(rig.arts.single()).box)
    }
    @Test fun regionalVariantsShareOneSearchAndAssetReadPerJob() = runTest {
        val r = Rig()
        r.answerAll()
        val usa = Scrape.homeGame("Tetris (USA).gb").copy(name = "Tetris (USA)")
        val jpn = Scrape.homeGame("Tetris (Japan).gb").copy(name = "Tetris (Japan)")
        r.src.startJob()
        val first = r.src.find(usa, setOf(ArtSlot.GRID, ArtSlot.HERO))
        val second = r.src.find(jpn, setOf(ArtSlot.GRID, ArtSlot.HERO))
        assertEquals(first.map { it.url }, second.map { it.url })
        assertEquals(1, r.http.count("search/autocomplete/"))
        assertEquals(1, r.http.count("grids/game/"))
        assertEquals(1, r.http.count("heroes/game/"))
        r.src.startJob()
        r.src.find(jpn, setOf(ArtSlot.GRID))
        assertEquals(2, r.http.count("search/autocomplete/"))
    }
}
