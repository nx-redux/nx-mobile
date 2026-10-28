package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxButton.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PadMapTest {
    /** NX Redux MD/32X/SEGACD/GPGX: A=Y, B=X:B, C=A, X=B:L1, Y=L1:X, Z=R1, Mode=SELECT, Start=START. */
    @Test fun genesis_family_routes_like_nx_redux() {
        for (tag in listOf("MD", "GPGX", "SEGACD", "32X", " md ")) for (sega in listOf(null, SegaPad.THREE, SegaPad.SIX)) {
            val m = PadMap.forTag(tag, sega)
            assertEquals(Y, m.sends(Y), tag); assertEquals(B, m.sends(X), tag); assertEquals(A, m.sends(A), tag)
            assertEquals(L1, m.sends(B), tag); assertEquals(X, m.sends(L1), tag); assertEquals(R1, m.sends(R1), tag)
            assertEquals(SELECT, m.sends(SELECT), tag); assertEquals(START, m.sends(START), tag)
            assertEquals(L2, m.sends(L2), tag); assertEquals(R2, m.sends(R2), tag)
        }
    }

    /** Drift guard: every registered tag is covered, so a new tag must be either Genesis-routed or identity. */
    @Test fun every_registered_tag_routes_like_nx_redux() {
        val genesis = setOf("MD", "GPGX", "SEGACD", "32X")
        val ownRouting = setOf("DC", "NDS", "N64")   // their own input paths ([com.nxredux.mobile.ui.nx.PadProfile]), not PadMap
        assertTrue(CoreRegistry.tags.containsAll(genesis + ownRouting))
        for (tag in CoreRegistry.tags - ownRouting) for (sega in listOf(null, SegaPad.THREE, SegaPad.SIX)) {
            val m = PadMap.forTag(tag, sega)
            val expected = if (CoreRegistry.baseTag(tag) in genesis) mapOf(X to B, B to L1, L1 to X) else emptyMap()
            for (b in NxButton.entries) assertEquals(expected[b] ?: b, m.sends(b), "$tag $sega $b")
        }
    }

    @Test fun every_other_tag_routes_each_button_to_itself() {
        val tags = listOf("SMS", "GG", "SG1000", "NGP", "NGPC", "PCE", "PS", "P8", "A7800", "A2600", "A5200", "COLECO", "LYNX",
            "PKM", "GB", "GBC", "GBA", "MGBA", "SGB", "VB", "FC", "FDS", "SFC", "SUPA", "FBN", "PRBOOM", "PSP", "WSC", "UNKNOWN", "")
        for (tag in tags) for (b in NxButton.entries) assertEquals(b, PadMap.forTag(tag).sends(b), "$tag $b")
    }

    @Test fun genesis_labels_six_button_and_unknown() {
        for (sega in listOf(null, SegaPad.SIX)) {
            val m = PadMap.forTag("MD", sega)
            assertEquals("A", m.label(Y)); assertEquals("B", m.label(X)); assertEquals("C", m.label(A))
            assertEquals("X", m.label(B)); assertEquals("Y", m.label(L1)); assertEquals("Z", m.label(R1))
            assertEquals("MODE", m.label(SELECT)); assertNull(m.label(L2)); assertNull(m.label(R2))
            for (b in NxButton.entries) assertFalse(m.isHidden(b), "$sega $b")
        }
    }

    @Test fun genesis_three_button_hides_mode_on_the_pills_and_xyz_in_the_cluster() {
        val m = PadMap.forTag("32X", SegaPad.THREE)
        assertEquals(setOf(SELECT), NxButton.entries.filter { m.isHidden(it) }.toSet())
        assertEquals(setOf(B, L1, R1), NxButton.entries.filter { m.isHiddenInCluster(it) }.toSet())
        assertEquals("A", m.label(Y)); assertEquals("B", m.label(X)); assertEquals("C", m.label(A))
        assertNull(m.label(L1)); assertNull(m.label(R1))   // own "L1"/"R1" glyphs: a 3-button game ignores them
        for (sega in listOf(null, SegaPad.SIX)) for (b in NxButton.entries) assertFalse(PadMap.forTag("MD", sega).isHiddenInCluster(b))
    }

    @Test fun label_only_consoles() {
        fun l(tag: String, b: NxButton) = PadMap.forTag(tag).label(b)
        for (t in listOf("SMS", "GG", "SG1000", "A7800")) { assertEquals("1", l(t, B)); assertEquals("2", l(t, A)) }
        for (t in listOf("NGP", "NGPC")) { assertEquals("A", l(t, B)); assertEquals("B", l(t, A)); assertEquals("OPTION", l(t, START)) }
        assertEquals(listOf("I", "II", "III", "IV", "V", "VI", "Mode", "RUN"), listOf(A, B, Y, X, L1, R1, L2, START).map { l("PCE", it) })
        for (t in listOf("PS", "PSP")) assertEquals(listOf("○", "×", "△", "□"), listOf(A, B, X, Y).map { l(t, it) }, t)
        assertEquals("O", l("P8", B)); assertEquals("X", l("P8", A))
        assertEquals("Fire", l("A2600", B))
        assertEquals("Opt 1", l("LYNX", L1)); assertEquals("Opt 2", l("LYNX", R1))
        assertEquals(listOf("C", "Shake", "POWER", "TA"), listOf(R1, L1, SELECT, X).map { l("PKM", it) })
        for (t in listOf("GB", "GBC", "GBA", "MGBA", "SGB")) { assertEquals("TA", l(t, X)); assertEquals("TB", l(t, Y)) }
        for (t in listOf("MGBA", "SGB")) { assertEquals("TL", l(t, L2)); assertEquals("TR", l(t, R2)) }
        assertNull(l("GBA", L2))
        assertEquals("Batt", l("VB", X)); assertEquals("R↑", l("VB", L2)); assertEquals("R←", l("VB", R2))
        for (t in listOf("FC", "FDS")) { assertEquals("Side", l(t, L1)); assertEquals("Insert", l(t, R1)); assertEquals("Coin", l(t, R2)) }
    }

    @Test fun identity_consoles_have_no_labels_and_hide_nothing() {
        for (tag in listOf("SFC", "SUPA", "A5200", "COLECO", "PRBOOM", "WSC", "UNKNOWN")) for (b in NxButton.entries) {
            assertNull(PadMap.forTag(tag).label(b), "$tag $b"); assertFalse(PadMap.forTag(tag).isHidden(b), "$tag $b")
        }
    }

    @Test fun menu_buttons_per_console() {
        for (tag in listOf("MD", "GPGX", "SEGACD", "32X")) for (sega in listOf(null, SegaPad.THREE, SegaPad.SIX)) {
            val m = PadMap.forTag(tag, sega)
            assertEquals(A, m.menuButton(Y)); assertEquals(B, m.menuButton(X)); assertEquals(X, m.menuButton(A)); assertEquals(Y, m.menuButton(B))
            for (b in NxButton.entries - setOf(A, B, X, Y)) assertEquals(b, m.menuButton(b), "$tag $b")
        }
        for (tag in listOf("NGP", "NGPC")) {
            val m = PadMap.forTag(tag)
            assertEquals(A, m.menuButton(B)); assertEquals(B, m.menuButton(A)); assertEquals(X, m.menuButton(Y)); assertEquals(Y, m.menuButton(X))
        }
        for (tag in listOf("PS", "PSP")) {
            val m = PadMap.forTag(tag)
            assertEquals(A, m.menuButton(B), tag); assertEquals(B, m.menuButton(A), tag)
            for (b in NxButton.entries - setOf(A, B)) assertEquals(b, m.menuButton(b), "$tag $b")
        }
        for (tag in listOf("SMS", "PCE", "SFC", "")) for (b in NxButton.entries) assertEquals(b, PadMap.forTag(tag).menuButton(b), "$tag $b")
    }

    @Test fun only_the_genesis_family_uses_the_sega_cluster() {
        for (tag in listOf("MD", "GPGX", "SEGACD", "32X")) assertTrue(PadMap.forTag(tag).segaCluster, tag)
        for (tag in listOf("SMS", "GG", "NGP", "SFC", "")) assertFalse(PadMap.forTag(tag).segaCluster, tag)
    }

    @Test fun send_key_translates_keycodes_and_passes_others_through() {
        val md = PadMap.forTag("MD")
        assertEquals(PadMap.KEY_B, md.sendKey(PadMap.KEY_X))
        assertEquals(PadMap.KEY_L1, md.sendKey(PadMap.KEY_B))
        assertEquals(PadMap.KEY_X, md.sendKey(PadMap.KEY_L1))
        assertEquals(PadMap.KEY_A, md.sendKey(PadMap.KEY_A))
        for (code in listOf(-1, 0, 19, 20, 21, 22, 110, 400)) assertEquals(code, md.sendKey(code), "keycode $code")
        for (code in -1..400) assertEquals(code, PadMap.forTag("SFC").sendKey(code), "keycode $code")
    }

    @Test fun sega_pad_tags() {
        for (t in listOf("MD", "GPGX", "SEGACD", "32X", " 32x ")) assertTrue(PadMap.isSegaPadTag(t), t)
        for (t in listOf("SMS", "GG", "SG1000", "DC", "")) assertFalse(PadMap.isSegaPadTag(t), t)
    }

    @Test fun ps2UsesPsLabels() {
        assertEquals(listOf("○", "×", "△", "□"), listOf(A, B, X, Y).map { PadMap.forTag("PS2").label(it) })
    }
}
