package com.nxredux.mobile.runner

import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.GraphicsApiOptions
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.SpeedOptions
import com.nxredux.mobile.options.PadOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class N3dsNewControlsTest {
    @Test fun listed_title_ids_in_every_region_use_the_new_controls() {
        for (id in listOf(
            0x0004000000126300L,   // Monster Hunter 4 Ultimate (USA)
            0x000400000011D700L,   // Monster Hunter 4G (JPN)
            0x0004000000153200L,   // Monster Hunter 4G (KOR)
            0x000400000F700100L,   // Xenoblade Chronicles 3D (USA)
            0x000400000F700000L,   // Xenoblade (JPN)
            0x0004000000125600L,   // Majora's Mask 3D (EUR)
            0x00040000000EDF00L,   // Smash Bros. for 3DS (USA)
            0x000400000F70CD00L,   // Fire Emblem Warriors (EUR)
        )) assertTrue(N3dsNewControls.listed(N3dsTitle(id, null)), id.toString(16))
    }

    @Test fun hyrule_warriors_legends_and_monster_hunter_xx_are_listed_by_id_and_code() {
        // Hyrule Warriors Legends: C Stick camera on the New 3DS (Nintendo UK). MHXX: Circle Pad Pro camera and ZL/ZR (Capcom FAQ).
        for (id in listOf(0x000400000017D500L, 0x000400000017EA00L, 0x000400000017EB00L, 0x0004000000189E00L, 0x0004000000197100L))
            assertTrue(N3dsNewControls.listed(N3dsTitle(id, null)), id.toString(16))
        assertTrue(N3dsNewControls.listed(N3dsTitle(null, "CTR-N-BZHE")))
        assertTrue(N3dsNewControls.listed(N3dsTitle(null, "CTR-N-AGQJ")))
    }

    @Test fun a_listed_game_code_matches_a_region_without_a_listed_id() {
        // Metal Gear Solid Snake Eater 3D's US cart: retail only, known by its product code.
        assertTrue(N3dsNewControls.listed(N3dsTitle(0x0004000000099999L, "CTR-P-AMGE")))
        assertTrue(N3dsNewControls.listed(N3dsTitle(null, "KTR-P-CFMJ")))
    }

    @Test fun other_games_and_unknown_files_do_not() {
        assertFalse(N3dsNewControls.listed(N3dsTitle(0x0004000000030800L, "CTR-P-AMKE")))   // Mario Kart 7
        assertFalse(N3dsNewControls.listed(N3dsTitle(null, null)))
        assertFalse(N3dsNewControls.listed(null))
    }

    @Test fun every_listed_id_is_an_application_title_and_every_code_three_letters() {
        assertTrue(N3dsNewControls.TITLE_IDS.size >= 50)
        assertTrue(N3dsNewControls.TITLE_IDS.all { (it ushr 32) == 0x00040000L })
        assertTrue(N3dsNewControls.GAME_CODES.all { it.length == 3 && it == it.uppercase() })
    }

    @Test fun auto_follows_the_list_on_and_off_force() {
        val mh4u = N3dsTitle(0x0004000000126300L, "CTR-P-BFGE")
        val mk7 = N3dsTitle(0x0004000000030800L, "CTR-P-AMKE")
        assertTrue(N3dsNewControls.resolve(N3dsNewControls.AUTO, mh4u))
        assertFalse(N3dsNewControls.resolve(N3dsNewControls.AUTO, mk7))
        assertFalse(N3dsNewControls.resolve(N3dsNewControls.AUTO, null))     // .3dsx, compressed: unknown
        assertFalse(N3dsNewControls.resolve(null, mk7))                      // a missing value is Auto
        assertTrue(N3dsNewControls.resolve(N3dsNewControls.ON, null))
        assertFalse(N3dsNewControls.resolve(N3dsNewControls.OFF, mh4u))
        assertTrue(N3dsNewControls.resolve("bogus", mh4u))                   // unknown counts as Auto
    }

    @Test fun the_3ds_console_settings_offer_the_row_before_the_layout_row() {
        assertEquals(N3dsOptions.layoutRows + N3dsOptions.resolutionRows(null) + listOf(N3dsOptions.newControlsRow, GraphicsApiOptions.row, BottomScreenOptions.row, PadOptions.portraitPadRow, PadOptions.layoutRow) + SpeedOptions.table, ConsoleSettings.tableFor("3DS"))
        val row = N3dsOptions.newControlsRow
        assertEquals(N3dsOptions.NEW_CONTROLS, row.key)
        assertEquals("New 3DS Controls", row.label)
        assertEquals(listOf("auto", "on", "off"), row.values)
        assertEquals("auto", row.default)
        assertTrue(ConsoleSettings.all.contains(row))
        assertTrue(N3dsOptions.NEW_CONTROLS.startsWith("nx_"))
    }
}
