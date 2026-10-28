package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.PadMap
import com.nxredux.mobile.runner.SegaPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PadProfileTest {
    @Test fun tags_pick_their_profile() {
        assertEquals(PadProfile.DREAMCAST, PadProfile.forTag("DC"))
        assertEquals(PadProfile.DREAMCAST, PadProfile.forTag(" dc "))
        assertEquals(PadProfile.N64, PadProfile.forTag("N64"))
        assertEquals(PadProfile.N64, PadProfile.forTag(" n64 "))
    }

    @Test fun identity_tags_are_standard() {
        for (tag in listOf("SFC", "UNKNOWN", ""))
            assertEquals(PadProfile.STANDARD, PadProfile.forTag(tag), tag)
    }

    @Test fun arcade_select_reads_coin() {
        assertEquals("COIN", PadProfile.forTag("FBN").label(NxButton.SELECT))
        assertEquals(NxButton.SELECT, PadMap.forTag("FBN").sends(NxButton.SELECT))   // label only
    }

    @Test fun the_ds_pad_has_l_and_r_only() {
        val ds = PadProfile.forTag("NDS")
        assertEquals(listOf("L", "R"), listOf(NxButton.L1, NxButton.R1).map(ds::label))
        assertEquals(setOf(NxButton.L2, NxButton.R2), NxButton.entries.filter(ds::isHidden).toSet())   // STYLUS / SWAP pills instead
        for (b in NxButton.entries) assertEquals(b, PadMap.forTag("NDS").sends(b), "$b")   // labels only: routing unchanged
    }

    @Test fun table_tags_take_their_labels() {
        val md = PadProfile.forTag("MD")
        assertEquals("C", md.label(NxButton.A)); assertEquals("A", md.label(NxButton.Y)); assertEquals("MODE", md.label(NxButton.SELECT))
        assertEquals("L2", md.label(NxButton.L2))               // no table label: the glyph
        assertEquals("1", PadProfile.forTag("SMS").label(NxButton.B))
        assertEquals("×", PadProfile.forTag("PS").label(NxButton.B))
    }

    @Test fun sega_tags_draw_the_sega_cluster() {
        for (tag in listOf("MD", "GPGX", "SEGACD", "32X")) for (sega in listOf(null, SegaPad.THREE, SegaPad.SIX))
            assertEquals(FaceCluster.SEGA, PadProfile.forTag(tag, sega).cluster, "$tag $sega")
        assertEquals(FaceCluster.DIAMOND, PadProfile.forTag("SMS").cluster)
    }

    @Test fun sega_three_button_cluster_shows_only_abc_and_pills_keep_shoulders() {
        val p = PadProfile.forTag("32X", SegaPad.THREE)
        assertEquals(setOf(NxButton.B, NxButton.L1, NxButton.R1, NxButton.SELECT), NxButton.entries.filter { p.isHiddenInCluster(it) }.toSet())
        assertEquals(setOf(NxButton.SELECT), NxButton.entries.filter { p.isHidden(it) }.toSet())
        assertEquals("L1", p.label(NxButton.L1)); assertEquals("R1", p.label(NxButton.R1))
        for (b in NxButton.entries) assertFalse(PadProfile.forTag("32X", SegaPad.SIX).isHiddenInCluster(b), b.name)
    }

    @Test fun sega_menu_follows_the_drawn_a_and_b() {
        val md = PadProfile.forTag("MD")
        assertEquals(NxButton.A, md.menuButton(NxButton.Y)); assertEquals(NxButton.B, md.menuButton(NxButton.X))
        assertEquals(NxButton.X, md.menuButton(NxButton.A)); assertEquals(NxButton.Y, md.menuButton(NxButton.B))
    }

    @Test fun ngp_menu_follows_the_drawn_letters_like_dc() {
        val ngp = PadProfile.forTag("NGP")
        assertEquals(NxButton.A, ngp.menuButton(NxButton.B)); assertEquals(NxButton.B, ngp.menuButton(NxButton.A))
        assertEquals(NxButton.X, ngp.menuButton(NxButton.Y)); assertEquals(NxButton.Y, ngp.menuButton(NxButton.X))
    }

    @Test fun ps_and_psp_menus_confirm_with_cross() {
        for (tag in listOf("PS", "PSP")) {
            val p = PadProfile.forTag(tag)
            assertEquals(NxButton.A, p.menuButton(NxButton.B), tag); assertEquals(NxButton.B, p.menuButton(NxButton.A), tag)
            assertEquals(NxButton.X, p.menuButton(NxButton.X), tag); assertEquals(NxButton.Y, p.menuButton(NxButton.Y), tag)
        }
    }

    @Test fun other_table_menus_go_by_position() {
        for (tag in listOf("SMS", "PCE", "GBA"))
            for (b in NxButton.entries) assertEquals(b, PadProfile.forTag(tag).menuButton(b), "$tag ${b.name}")
    }

    @Test fun long_labels_shrink_short_ones_do_not() {
        // Room is 0.7 of the button's diameter, the text line across its middle.
        assertEquals(1f, faceLabelScale(textWidth = 30f, diameter = 100f))     // "II", "TA"
        assertEquals(1f, faceLabelScale(textWidth = 70f, diameter = 100f))     // exactly fits: a word like "Fire" at full size, as a single letter is
        assertEquals(0.7f, faceLabelScale(textWidth = 100f, diameter = 100f), 0.001f)
        assertEquals(0.3f, faceLabelScale(textWidth = 1000f, diameter = 100f), 0.001f)   // floor
    }

    @Test fun only_shoulder_and_system_buttons_are_pills() {
        for (b in listOf(NxButton.L1, NxButton.L2, NxButton.R1, NxButton.R2, NxButton.START, NxButton.SELECT, NxButton.MENU))
            assertTrue(isPill(b), b.name)
        for (b in listOf(NxButton.A, NxButton.B, NxButton.X, NxButton.Y, NxButton.C_UP, NxButton.C_DOWN, NxButton.C_LEFT, NxButton.C_RIGHT))
            assertFalse(isPill(b), b.name)
    }

    @Test fun pill_labels_get_the_pill_width_as_room() {
        // Room is the pill's width less its rounded ends (0.3 of its height); a wider pill fits a longer label.
        assertEquals(1f, pillLabelScale(textWidth = 50f, pillWidth = 72f, pillHeight = 72f))     // "Coin" on a trigger
        assertEquals(1f, pillLabelScale(textWidth = 70f, pillWidth = 100f, pillHeight = 100f))   // "Insert" on a bumper
        assertEquals(1f, pillLabelScale(textWidth = 80f, pillWidth = 140f, pillHeight = 72f))    // "Option" on START
        assertEquals(1f, pillLabelScale(textWidth = 50.4f, pillWidth = 72f, pillHeight = 72f))   // exactly fits
        assertEquals(0.8f, pillLabelScale(textWidth = 63f, pillWidth = 72f, pillHeight = 72f), 0.001f)
        assertEquals(0.3f, pillLabelScale(textWidth = 1000f, pillWidth = 72f, pillHeight = 72f), 0.001f)   // floor
    }

    @Test fun standard_uses_the_button_glyphs_and_hides_nothing() {
        assertEquals(FaceCluster.DIAMOND, PadProfile.STANDARD.cluster)
        for (b in NxButton.entries) {
            assertEquals(b.glyph(), PadProfile.STANDARD.label(b), b.name)
            assertFalse(PadProfile.STANDARD.isHidden(b), b.name)
        }
    }

    @Test fun dreamcast_letters_unchanged() {
        // The pad's bottom button is NxButton.B, right A, left Y, top X (the old FaceLabels.DREAMCAST).
        assertEquals(FaceCluster.DIAMOND, PadProfile.DREAMCAST.cluster)
        assertEquals("A", PadProfile.DREAMCAST.label(NxButton.B))
        assertEquals("B", PadProfile.DREAMCAST.label(NxButton.A))
        assertEquals("X", PadProfile.DREAMCAST.label(NxButton.Y))
        assertEquals("Y", PadProfile.DREAMCAST.label(NxButton.X))
        val face = setOf(NxButton.A, NxButton.B, NxButton.X, NxButton.Y)
        for (b in NxButton.entries - face) assertEquals(b.glyph(), PadProfile.DREAMCAST.label(b), b.name)
        for (b in NxButton.entries) assertFalse(PadProfile.DREAMCAST.isHidden(b), b.name)
    }

    @Test fun n64_six_buttons_z_and_hidden_select() {
        assertEquals(FaceCluster.N64, PadProfile.N64.cluster)
        assertEquals("Z", PadProfile.N64.label(NxButton.L2))
        assertEquals("A", PadProfile.N64.label(NxButton.A))
        assertEquals("B", PadProfile.N64.label(NxButton.B))
        assertEquals("▲", PadProfile.N64.label(NxButton.C_UP))
        // L1/L2/R1/R2 are drawn on every console: R2 is drawn though it sends nothing on N64. SELECT is not.
        assertEquals("R2", PadProfile.N64.label(NxButton.R2))
        assertEquals(setOf(NxButton.SELECT), NxButton.entries.filter { PadProfile.N64.isHidden(it) }.toSet())
    }

    @Test fun dreamcast_menu_follows_the_drawn_letters() {
        assertEquals(NxButton.A, PadProfile.DREAMCAST.menuButton(NxButton.B))
        assertEquals(NxButton.B, PadProfile.DREAMCAST.menuButton(NxButton.A))
        assertEquals(NxButton.X, PadProfile.DREAMCAST.menuButton(NxButton.Y))
        assertEquals(NxButton.Y, PadProfile.DREAMCAST.menuButton(NxButton.X))
        val face = setOf(NxButton.A, NxButton.B, NxButton.X, NxButton.Y)
        for (b in NxButton.entries - face) assertEquals(b, PadProfile.DREAMCAST.menuButton(b), b.name)
    }

    @Test fun standard_and_n64_menu_buttons_unchanged() {
        for (p in listOf(PadProfile.STANDARD, PadProfile.N64))
            for (b in NxButton.entries) assertEquals(b, p.menuButton(b), "$p ${b.name}")
    }

    @Test fun pad_event_map_keeps_down_and_up() {
        assertEquals(PadEvent.Down(NxButton.A), PadEvent.Down(NxButton.B).map { PadProfile.DREAMCAST.menuButton(it) })
        assertEquals(PadEvent.Up(NxButton.A), PadEvent.Up(NxButton.B).map { PadProfile.DREAMCAST.menuButton(it) })
    }

    @Test fun ps_and_psp_draw_the_playstation_symbols() {
        for (tag in listOf("PS", "PSP", " psp ")) {
            val p = PadProfile.forTag(tag)
            assertEquals(PadIcon.PS_CIRCLE, p.icon(NxButton.A), tag); assertEquals(PadIcon.PS_CROSS, p.icon(NxButton.B), tag)
            assertEquals(PadIcon.PS_TRIANGLE, p.icon(NxButton.X), tag); assertEquals(PadIcon.PS_SQUARE, p.icon(NxButton.Y), tag)
            for (b in NxButton.entries - setOf(NxButton.A, NxButton.B, NxButton.X, NxButton.Y)) assertNull(p.icon(b), "$tag $b")
        }
    }

    @Test fun other_consoles_draw_text() {
        for (tag in listOf("SFC", "MD", "SMS", "P8", "PCE", "DC", "N64", "NDS", "")) for (b in NxButton.entries)
            assertNull(PadProfile.forTag(tag).icon(b), "$tag $b")
    }

    @Test fun ps2HasDualSticksNoToggle() {
        val ps2 = PadProfile.forTag("PS2")
        assertEquals(PadProfile.Kind.PS2, ps2.kind)
        assertTrue(ps2.dualSticks)
        assertFalse(ps2.cStick)
        assertFalse(ps2.stickByDefault)   // no toggle to start on
        assertEquals(listOf(PadSystemKey.MENU), padSystemRow(PadSide.LEFT, ps2, motion = false))
        assertTrue(PadProfile.forLayoutEditor("PS2").dualSticks)
    }

    @Test fun ps2UsesPsLabels() {
        val ps2 = PadProfile.forTag("PS2")
        assertEquals("△", ps2.label(NxButton.X))
        assertEquals("○", ps2.label(NxButton.A))
        assertEquals("×", ps2.label(NxButton.B))
        assertEquals("□", ps2.label(NxButton.Y))
        assertEquals(PadIcon.PS_CROSS, ps2.icon(NxButton.B))
        assertEquals(NxButton.A, ps2.menuButton(NxButton.B))   // × confirms, as on PS1
        assertEquals("L3", ps2.label(NxButton.L3))
        assertEquals("R3", ps2.label(NxButton.R3))
        for (b in NxButton.entries) assertFalse(ps2.isHidden(b), b.name)
    }

    @Test fun otherConsolesHaveNoDualSticks() {
        for (p in listOf(PadProfile.forTag("PS"), PadProfile.forTag("3DS"), PadProfile.forTag("3DS", n3dsNew = true), PadProfile.forTag("GC"), PadProfile.forTag("N64"), PadProfile.STANDARD))
            assertFalse(p.dualSticks, p.toString())
    }
}
