package com.nxredux.mobile.skin

import com.nxredux.mobile.library.NaomiSets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which Dreamcast-tag games are NAOMI / Atomiswave sets (the arcade skin), and which start on the stick. */
class DcGamesTest {
    // name, parent, title, bios: one game set and one BIOS (no title)
    private val sets = NaomiSets.parse("mvsc2\t\tMarvel vs. Capcom 2\tnaomi\nnaomi\t\t\tnaomi\n")

    @Test
    fun aKnownSetArchiveIsAnArcadeSet() {
        assertTrue(DcGames.isArcadeSet("DC", "mvsc2.zip", sets))
        assertTrue(DcGames.isArcadeSet("DC", "MVSC2.7z", sets))
        assertFalse(DcGames.isArcadeSet("DC", "Sonic Adventure (USA).zip", sets))   // a zipped disc
        assertFalse(DcGames.isArcadeSet("DC", "mvsc2.chd", sets))                   // not an archive
        assertFalse(DcGames.isArcadeSet("FBN", "mvsc2.zip", sets))
        assertFalse(DcGames.isArcadeSet("DC", "mvsc2.zip", NaomiSets.EMPTY))        // no table: not known as a set
    }

    @Test
    fun discsStartOnTheStick() {
        assertTrue(DcGames.stickFirst("DC", arcadeSet = false))
        assertFalse(DcGames.stickFirst("DC", arcadeSet = true))
        assertFalse(DcGames.stickFirst("SFC", arcadeSet = false))
    }

    @Test
    fun n64GamesStartOnTheStick() {   // nearly every N64 game steers with it
        assertTrue(DcGames.stickFirst("N64", arcadeSet = false))
    }
}
