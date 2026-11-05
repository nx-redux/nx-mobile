package com.nxredux.mobile.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScraperRequestTest {
    private val auth = ScraperAuth("DEVID", "DEVPW")
    @Test fun encodesLikeNxRedux() {
        assertEquals("Pok%C3%A9mon+-+Emerald+%28USA%2C+Europe%29.gba", ssEncode("Pokémon - Emerald (USA, Europe).gba"))
        assertEquals("Tom+%26+Jerry%27s_v1.0~a.zip", ssEncode("Tom & Jerry's_v1.0~a.zip"))
    }
    @Test fun tryOneUrlInNxReduxOrder() = assertEquals(
        "https://api.screenscraper.fr/api2/jeuInfos.php?devid=DEVID&devpassword=DEVPW&softname=nx-mobile&romnom=Sonic+The+Hedgehog.md&systemeid=1&output=json",
        ScraperRequest.gameInfo(auth, "Sonic The Hedgehog.md", 1),
    )
    @Test fun userLoginGoesAfterOutput() = assertEquals(
        "https://api.screenscraper.fr/api2/jeuInfos.php?devid=DEVID&devpassword=DEVPW&softname=nx-mobile&romnom=a.gb&systemeid=9&output=json&ssid=me&sspassword=p+w%21",
        ScraperRequest.gameInfo(auth.copy(user = "me", password = "p w!"), "a.gb", 9),
    )
    @Test fun tryTwoAddsLowercaseCrcAndSize() = assertTrue(
        ScraperRequest.gameInfo(auth, "a.gb", 9, crc32 = 0x0ABCDEF1L, size = 32768).endsWith("&crc=0abcdef1&romtaille=32768"),
    )
    @Test fun signExtendedCrcIsMaskedTo32Bits() = assertTrue(
        ScraperRequest.gameInfo(auth, "a.gb", 9, crc32 = 0xABCDEF01.toInt().toLong(), size = 1).endsWith("&crc=abcdef01&romtaille=1"),
    )
    @Test fun userInfoNeedsLogin() = assertEquals(
        "https://api.screenscraper.fr/api2/ssuserInfos.php?devid=DEVID&devpassword=DEVPW&softname=nx-mobile&ssid=me&sspassword=pw&output=json",
        ScraperRequest.userInfo(auth.copy(user = "me", password = "pw")),
    )
    @Test fun developerCredentialsAreEncoded() = assertTrue(
        ScraperRequest.gameInfo(ScraperAuth("my dev", "p&w=1", softname = "nx mobile/2"), "a.gb", 9)
            .contains("?devid=my+dev&devpassword=p%26w%3D1&softname=nx+mobile%2F2&romnom="),
    )
    @Test fun availability() { assertTrue(auth.available); assertFalse(ScraperAuth("", "").available) }
    @Test fun searchOnOneSystem() = assertEquals(
        "https://api.screenscraper.fr/api2/jeuRecherche.php?devid=DEVID&devpassword=DEVPW&softname=nx-mobile&recherche=Pok%C3%A9mon+GO%3A+Go&systemeid=63&output=json",
        ScraperRequest.search(auth, "Pokémon GO: Go", 63),
    )
    @Test fun searchOnEverySystem() = assertEquals(
        "https://api.screenscraper.fr/api2/jeuRecherche.php?devid=DEVID&devpassword=DEVPW&softname=nx-mobile&recherche=Temple+Run&output=json",
        ScraperRequest.search(auth, "Temple Run", null),
    )
    @Test fun searchCarriesTheUserLogin() = assertTrue(
        ScraperRequest.search(auth.copy(user = "me", password = "pw"), "a", null).endsWith("&output=json&ssid=me&sspassword=pw"),
    )

    private val signed = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=D&devpassword=P&softname=nx&ssid=U&sspassword=W&systemeid=1&jeuid=42&media=ss(us)"
    private val stripped = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?softname=nx&systemeid=1&jeuid=42&media=ss(us)"
    @Test fun strip_removes_only_credential_params() = assertEquals(stripped, ScraperRequest.stripAuth(signed))
    @Test fun strip_leaves_other_hosts() {
        val u = "https://cdn2.steamgriddb.com/grid/x.png?devid=1"
        assertEquals(u, ScraperRequest.stripAuth(u))
        assertEquals(u, ScraperRequest.withAuth(u, ScraperAuth("D", "P")))
    }
    @Test fun with_auth_restores_signed_in() {
        val u = ScraperRequest.withAuth(stripped, ScraperAuth("D", "P", user = "U", password = "W"))
        for (part in listOf("devid=D", "devpassword=P", "ssid=U", "sspassword=W", "jeuid=42")) assertTrue(u.contains(part), part)
    }
    @Test fun with_auth_signed_out_has_no_ssid() {
        val u = ScraperRequest.withAuth(stripped, ScraperAuth("D", "P"))
        assertTrue(u.contains("devid=D")); assertFalse(u.contains("ssid="))
    }
}
