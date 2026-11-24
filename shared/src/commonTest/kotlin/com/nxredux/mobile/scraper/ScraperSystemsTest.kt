package com.nxredux.mobile.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScraperSystemsTest {
    @Test fun baseIdsFromTheNxReduxTable() {
        assertEquals(4, ScraperSystems.baseIdFor("SFC")); assertEquals(4, ScraperSystems.baseIdFor("supa"))
        assertEquals(12, ScraperSystems.baseIdFor("MGBA")); assertEquals(15, ScraperSystems.baseIdFor("NDS"))
        assertEquals(23, ScraperSystems.baseIdFor("DC")); assertEquals(234, ScraperSystems.baseIdFor("P8"))
        assertEquals(135, ScraperSystems.baseIdFor("PRBOOM")); assertEquals(48, ScraperSystems.baseIdFor("COLECO"))
        assertEquals(19, ScraperSystems.baseIdFor("32X")); assertEquals(82, ScraperSystems.baseIdFor("NGPC"))
        assertNull(ScraperSystems.baseIdFor("NOPE"))
        // ScreenScraper's Wii and GameCube (Dolphin's WII and GC tags).
        assertEquals(16, ScraperSystems.baseIdFor("WII")); assertEquals(13, ScraperSystems.baseIdFor("gc"))
        assertEquals(16, ScraperSystems.systemIdFor("WII", "Super Mario Galaxy 2 (USA).wbfs"))
        assertEquals(17, ScraperSystems.baseIdFor("3DS"))
    }
    @Test fun dreamcastArchivesAreArcade() {
        assertEquals(75, ScraperSystems.systemIdFor("DC", "mvsc2.zip")); assertEquals(75, ScraperSystems.systemIdFor("DC", "x.7Z"))
        assertEquals(23, ScraperSystems.systemIdFor("DC", "Crazy Taxi.chd"))
    }
    @Test fun megaDriveFamilyRefinesByExtension() {
        assertEquals(20, ScraperSystems.systemIdFor("GPGX", "Sonic CD.cue")); assertEquals(20, ScraperSystems.systemIdFor("MD", "x.ISO"))
        assertEquals(2, ScraperSystems.systemIdFor("GPGX", "Alex Kidd.sms")); assertEquals(21, ScraperSystems.systemIdFor("GPGX", "Sonic.gg"))
        assertEquals(109, ScraperSystems.systemIdFor("GPGX", "Flicky.sg")); assertEquals(1, ScraperSystems.systemIdFor("GPGX", "Sonic.md"))
    }
    @Test fun lastExtensionOnlyAndDotfilesHaveNone() {
        assertEquals(1, ScraperSystems.systemIdFor("MD", "game.sms.bin")); assertEquals(1, ScraperSystems.systemIdFor("MD", ".gg"))
    }
    // Every console the app registers can be scraped (the Android games console aside, which has its own system).
    @Test fun everyRegisteredConsoleTagHasASystem() {
        for (console in com.nxredux.mobile.runner.CoreRegistry.consoles) for (tag in console.tags) {
            assertEquals(true, ScraperSystems.baseIdFor(tag) != null, "tag $tag has no scraper system")
        }
    }
    @Test fun newConsolesHaveScreenScraperIds() {
        for (tag in listOf("SWITCH", "WIIU", "PS3", "PSVITA", "PS4", "SATURN", "XBOX", "XBOX360", "JAGUAR", "NEOCD", "CDI", "N64DD", "SGX")) {
            assertEquals(true, ScraperSystems.baseIdFor(tag) != null, "tag $tag has no scraper system")
        }
    }
    @Test fun excludedTags() = assertEquals(setOf("PORTS", "CUSTOM", "EXTRAS"), ScraperSystems.EXCLUDED_TAGS)
}
