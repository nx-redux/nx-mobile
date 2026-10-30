package com.nxredux.mobile.options

import com.nxredux.mobile.runner.PadFaceLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PadOptionsTest {
    @Test fun layout_row_for_every_tag_n64_included() {
        for (tag in listOf("DC", "MD", "SFC", "NDS", "PS", "GBA", "N64", "3DS", "UNKNOWN"))
            assertTrue(PadOptions.layoutRow in ConsoleSettings.tableFor(tag), tag)
        assertEquals(listOf(PadOptions.AUTO, PadOptions.XBOX, PadOptions.NINTENDO), PadOptions.layoutRow.values)
        assertEquals(PadOptions.AUTO, PadOptions.layoutRow.default)
        assertEquals(PadOptions.LAYOUT, PadOptions.layoutRow.key)
    }

    @Test fun sega_pad_row_only_for_sega_pad_tags() {
        for (tag in listOf("MD", "GPGX", "SEGACD", "32X")) assertTrue(PadOptions.segaPadRow in ConsoleSettings.tableFor(tag), tag)
        for (tag in listOf("SMS", "GG", "DC", "SFC")) assertFalse(PadOptions.segaPadRow in ConsoleSettings.tableFor(tag), tag)
        assertEquals(listOf("auto", "3", "6"), PadOptions.segaPadRow.values)
        assertEquals("auto", PadOptions.segaPadRow.default)
    }

    @Test fun nds_keeps_its_rows_and_gains_the_portrait_pad_and_layout_rows() {
        assertEquals(NdsOptions.table + BottomScreenOptions.row + PadOptions.portraitPadRow + PadOptions.layoutRow + (SpeedOptions.table + AudioOptions.row), ConsoleSettings.tableFor("NDS"))
    }

    @Test fun portrait_pad_row_only_for_ds_and_3ds() {
        for (tag in listOf("NDS", "3DS", " nds ")) assertTrue(PadOptions.portraitPadRow in ConsoleSettings.tableFor(tag), tag)
        for (tag in listOf("GBA", "MD", "PS", "WII", "")) assertFalse(PadOptions.portraitPadRow in ConsoleSettings.tableFor(tag), tag)
        val row = PadOptions.portraitPadRow
        assertEquals("nx_portrait_pad", row.key)
        assertEquals("Portrait Pad", row.label)
        assertEquals(listOf(PadOptions.SHOWN, PadOptions.HIDDEN), row.values)
        assertEquals(PadOptions.SHOWN, row.default)
        assertEquals(listOf("Shown", "Hidden"), row.valueLabels)
        assertFalse(row.visual)
        assertFalse(row.atLaunch)   // a change applies live in the running game's Console Settings
        assertTrue(row.info.lines().all { it.length <= 48 }, row.info)
        assertEquals(row, FrontendOptions.option(PadOptions.PORTRAIT_PAD))
    }

    @Test fun portrait_pad_hidden_only_when_set_on_a_two_screen_console() {
        val hidden = mapOf(PadOptions.PORTRAIT_PAD to PadOptions.HIDDEN)
        assertTrue(PadOptions.portraitPadHidden("NDS", hidden))
        assertTrue(PadOptions.portraitPadHidden("3DS", hidden))
        assertFalse(PadOptions.portraitPadHidden("GBA", hidden))   // a stray key on another console does nothing
        assertFalse(PadOptions.portraitPadHidden("NDS", emptyMap()))
        assertFalse(PadOptions.portraitPadHidden("NDS", mapOf(PadOptions.PORTRAIT_PAD to PadOptions.SHOWN)))
        assertFalse(PadOptions.portraitPadHidden("NDS", mapOf(PadOptions.PORTRAIT_PAD to "junk")))
    }

    // Arcade offers it for its tall boards (the game decides per picture); its text says so.
    @Test fun arcade_offers_portrait_pad_for_tall_games() {
        val hidden = mapOf(PadOptions.PORTRAIT_PAD to PadOptions.HIDDEN)
        assertTrue(PadOptions.portraitPadHidden("FBN", hidden))
        assertTrue(PadOptions.portraitPadTallRow in PadOptions.tableFor("FBN"))
        assertFalse(PadOptions.portraitPadRow in PadOptions.tableFor("FBN"))
        assertTrue(PadOptions.portraitPadRow in PadOptions.tableFor("NDS"))
        assertFalse(PadOptions.tableFor("GBA").any { it.key == PadOptions.PORTRAIT_PAD })
        assertTrue(PadOptions.portraitPadTallRow.info.lines().all { it.length <= 48 }, PadOptions.portraitPadTallRow.info)
    }

    // The handhelds with upright games (WonderSwan, both tags; Lynx) offer it too; other handhelds do not.
    @Test fun upright_handhelds_offer_portrait_pad_for_tall_games() {
        val hidden = mapOf(PadOptions.PORTRAIT_PAD to PadOptions.HIDDEN)
        for (tag in listOf("WSC", "LYNX")) {
            assertTrue(PadOptions.hasPortraitPadRow(tag), tag)
            assertTrue(PadOptions.portraitPadHidden(tag, hidden), tag)
            assertTrue(PadOptions.portraitPadTallRow in PadOptions.tableFor(tag), tag)
        }
        for (tag in listOf("GBA", "GB", "GG", "NGPC")) assertFalse(PadOptions.hasPortraitPadRow(tag), tag)
    }

    @Test fun keys_are_unique_and_findable() {
        val keys = ConsoleSettings.all.map { it.key }
        assertEquals(keys.toSet().size, keys.size)
        assertEquals(PadOptions.layoutRow, FrontendOptions.option(PadOptions.LAYOUT))
        assertEquals(PadOptions.segaPadRow, FrontendOptions.option(PadOptions.SEGA_PAD))
    }

    @Test fun printed_layout_forced_or_detected() {
        val auto = mapOf(PadOptions.LAYOUT to "auto")
        assertEquals(PadFaceLayout.XBOX, PadOptions.printedLayout(auto, PadFaceLayout.XBOX))
        assertEquals(PadFaceLayout.NINTENDO, PadOptions.printedLayout(emptyMap(), PadFaceLayout.NINTENDO))
        assertEquals(PadFaceLayout.XBOX, PadOptions.printedLayout(mapOf(PadOptions.LAYOUT to "xbox"), PadFaceLayout.NINTENDO))
        assertEquals(PadFaceLayout.NINTENDO, PadOptions.printedLayout(mapOf(PadOptions.LAYOUT to "nintendo"), PadFaceLayout.XBOX))
        assertNull(PadOptions.printedLayout(mapOf(PadOptions.LAYOUT to "xbox"), null))   // an injected key is sent as is
    }

    // Carried over from the retired DcOptionsTest.
    @Test fun the_layout_row_reads_as_before() {
        val row = PadOptions.layoutRow
        assertEquals("nx_pad_layout", row.key)
        assertEquals("Controller Layout", row.label)
        assertEquals(listOf("Auto-detect", "Xbox (A at the bottom)", "Nintendo (A on the right)"), row.valueLabels)
        assertFalse(row.visual)
        assertTrue("work as printed" in row.info)
        assertTrue(row.info.lines().all { it.length <= 48 }, row.info)
        assertEquals(listOf("Auto", "3 buttons", "6 buttons"), PadOptions.segaPadRow.valueLabels)
        assertFalse(PadOptions.segaPadRow.visual)
    }

    @Test fun an_unknown_value_follows_detection_and_a_forced_one_wins_for_every_detection() {
        assertEquals(PadFaceLayout.XBOX, PadOptions.printedLayout(mapOf(PadOptions.LAYOUT to "junk"), PadFaceLayout.XBOX))
        for (detected in PadFaceLayout.entries) {
            assertEquals(PadFaceLayout.XBOX, PadOptions.printedLayout(mapOf(PadOptions.LAYOUT to "xbox"), detected))
            assertEquals(PadFaceLayout.NINTENDO, PadOptions.printedLayout(mapOf(PadOptions.LAYOUT to "nintendo"), detected))
        }
        for (v in listOf("auto", "xbox", "nintendo")) assertNull(PadOptions.printedLayout(mapOf(PadOptions.LAYOUT to v), null))
    }

    @Test fun defaults_include_the_layout_row_for_every_console() {
        assertEquals("auto", FrontendOptions.defaults("DC")[PadOptions.LAYOUT])
        assertEquals("auto", FrontendOptions.defaults("GBA")[PadOptions.LAYOUT])
        assertEquals("auto", FrontendOptions.defaults("N64")[PadOptions.LAYOUT])
        assertEquals("auto", FrontendOptions.defaults("MD")[PadOptions.SEGA_PAD])
        assertFalse(PadOptions.SEGA_PAD in FrontendOptions.defaults("GBA"))
    }
}
