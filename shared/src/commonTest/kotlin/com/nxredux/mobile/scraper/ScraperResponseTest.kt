package com.nxredux.mobile.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScraperResponseTest {
    @Test fun parsesMedias() {
        val g = assertIs<GameLookup.Found>(ScraperResponse.parseGame(200, ScraperFixtures.GAME_FOUND))
        assertEquals(7, g.medias.size); assertEquals(SsMedia("ss", "eu", "https://m/ss-eu.png"), g.medias[2])
        assertNull(g.medias[6].region)
    }
    @Test fun devLoginErrorIsToldFromTheUserOne() {
        assertTrue(ScraperResponse.isDevLoginError(ScraperFixtures.DEV_LOGIN_REFUSED))
        assertTrue(ScraperResponse.isDevLoginError("Erreur de login : Verifier vos identifiants developpeur !"))
        assertTrue(!ScraperResponse.isDevLoginError(ScraperFixtures.USER_LOGIN_REFUSED))
        assertTrue(!ScraperResponse.isDevLoginError(null))
    }
    @Test fun noJeuIsNotFound() { assertIs<GameLookup.NotFound>(ScraperResponse.parseGame(200, ScraperFixtures.GAME_NO_JEU)) }
    @Test fun garbageBodyIsNotFound() {
        assertIs<GameLookup.NotFound>(ScraperResponse.parseGame(200, ScraperFixtures.GARBAGE))
        assertIs<GameLookup.NotFound>(ScraperResponse.parseGame(200, ScraperFixtures.TRUNCATED))
        assertIs<GameLookup.NotFound>(ScraperResponse.parseGame(200, null))
    }
    @Test fun non200IsNotFound() { assertIs<GameLookup.NotFound>(ScraperResponse.parseGame(404, ScraperFixtures.GAME_FOUND)) }
    @Test fun quotaAsStringsOrNumbers() {
        assertEquals(SsQuota(1234, 20000), ScraperResponse.parseQuota(200, ScraperFixtures.QUOTA_STRINGS))
        assertEquals(SsQuota(5, 10000), ScraperResponse.parseQuota(200, ScraperFixtures.QUOTA_NUMBERS))
        assertNull(ScraperResponse.parseQuota(200, ScraperFixtures.GARBAGE))
    }
    @Test fun keepsTheGameId() {
        assertEquals("1", assertIs<GameLookup.Found>(ScraperResponse.parseGame(200, ScraperFixtures.GAME_FOUND)).id)
        assertNull(assertIs<GameLookup.Found>(ScraperResponse.parseGame(200, """{"response":{"jeu":{"medias":[]}}}""")).id)
    }
    @Test fun ranksBox3dThenBox2dSeparately() {
        val medias = (ScraperResponse.parseGame(200, ScraperFixtures.GAME_FOUND) as GameLookup.Found).medias
        val c = ScraperMedia.choose(medias)
        assertEquals("https://m/box3d-wor.png", c[ArtSlot.BOX_3D]?.url)
        assertEquals("https://m/box2d-us.png", c[ArtSlot.BOX_2D]?.url)
        assertEquals("https://m/ss-eu.png", c[ArtSlot.SCREENSHOT]?.url)   // ss in any region beats sstitle
        assertEquals(setOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.WHEEL), c.keys)
        assertEquals(listOf("https://m/box2d-us.png"), ScraperMedia.rank(medias, ArtSlot.BOX_2D).map { it.url })
        assertTrue(ScraperMedia.rank(medias, ArtSlot.HERO).isEmpty())
    }
    @Test fun screenshotIsSsOnly() {
        val c = ScraperMedia.choose(listOf(SsMedia("sstitle", "us", "t"), SsMedia("box-2D", "us", "b")))
        assertNull(c[ArtSlot.SCREENSHOT])
        assertEquals("b", c[ArtSlot.BOX_2D]?.url)
        assertNull(ScraperMedia.screenshotOnly(listOf(SsMedia("sstitle", "us", "t"))))
    }
    @Test fun wheelHdBeforeWheelThenRegion() {
        // NR scraper_api.c:251-253: wheel-hd, then wheel; within each type us, wor, eu, the rest.
        val m = listOf(SsMedia("wheel", "us", "w-us"), SsMedia("wheel-hd", "jp", "hd-jp"), SsMedia("wheel-hd", "eu", "hd-eu"), SsMedia("wheel", "wor", "w-wor"))
        assertEquals("hd-eu", ScraperMedia.choose(m)[ArtSlot.WHEEL]?.url)
        assertEquals(listOf("hd-eu", "hd-jp", "w-us", "w-wor"), ScraperMedia.rank(m, ArtSlot.WHEEL).map { it.url })
        assertEquals("w-us", ScraperMedia.choose(listOf(SsMedia("wheel", "jp", "w-jp"), SsMedia("wheel", "us", "w-us")))[ArtSlot.WHEEL]?.url)
        // The best of each type, in type order: a failed wheel-hd download falls back to the plain wheel.
        assertEquals(listOf("hd-eu", "w-us"), ScraperMedia.alternates(m)[ArtSlot.WHEEL]?.map { it.url })
        assertEquals(listOf("w-us"), ScraperMedia.alternates(m.filter { it.type == "wheel" })[ArtSlot.WHEEL]?.map { it.url })
        assertTrue(ScraperMedia.rank(m, ArtSlot.MIX).isEmpty())
    }
    @Test fun regionOrderWithinAType() {
        val m = listOf(SsMedia("ss", "jp", "jp"), SsMedia("ss", "eu", "eu"), SsMedia("ss", "wor", "wor"), SsMedia("ss", null, "none"), SsMedia("ss", "us", "us"))
        assertEquals("us", ScraperMedia.choose(m)[ArtSlot.SCREENSHOT]?.url)
        assertEquals(listOf("us", "wor", "eu", "jp", "none"), ScraperMedia.rank(m, ArtSlot.SCREENSHOT).map { it.url })   // then API order
        assertEquals("jp", ScraperMedia.choose(listOf(SsMedia("ss", "jp", "jp")))[ArtSlot.SCREENSHOT]?.url)
    }
    @Test fun jsonNullIsAbsent() {
        val body = """{"response":{"jeu":{"medias":[
            {"type":"ss","region":"us","url":null},
            {"type":"ss","region":null,"url":"https://m/ss-ok.png"},
            {"type":null,"url":"https://m/x.png"}
        ]}}}"""
        val g = assertIs<GameLookup.Found>(ScraperResponse.parseGame(200, body))
        assertEquals(listOf(SsMedia("ss", null, "https://m/ss-ok.png")), g.medias)
        assertEquals("https://m/ss-ok.png", ScraperMedia.choose(g.medias)[ArtSlot.SCREENSHOT]?.url)
    }
    @Test fun nothingUseful() = assertTrue(ScraperMedia.choose(listOf(SsMedia("fanart", null, "x"))).isEmpty())
    @Test fun alternates_keeps_every_screenshot_region() {
        val m = listOf(SsMedia("ss", "eu", "ss-eu"), SsMedia("ss", "us", "ss-us"), SsMedia("ss", "jp", "ss-jp"), SsMedia("box-2D", "us", "box-us"))
        val alt = ScraperMedia.alternates(m)
        assertEquals(listOf("us", "eu", "jp"), alt[ArtSlot.SCREENSHOT]!!.map { it.region })
        assertEquals(1, alt[ArtSlot.BOX_2D]!!.size)
    }
}
