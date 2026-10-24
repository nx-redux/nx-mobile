package com.nxredux.mobile.ui.nx

import androidx.compose.ui.geometry.Rect
import com.nxredux.mobile.runner.SegaPad
import com.nxredux.mobile.runner.WiiPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where the pad's default layout puts the touch shortcuts and the pills they displaced: the portrait top row (STYLUS /
 * SWAP and MOTION beside SELECT / START, START alone and centred on the GameCube) and, over the game, the shortcuts under
 * the toggle and SELECT, clear of the shoulders on every pad.
 */
class PadShortcutPlacementTest {
    private val wiiSideways = PadProfile.forTag("WII", wii = WiiPad.SIDEWAYS)
    private val wiiNunchuk = PadProfile.forTag("WII", wii = WiiPad.NUNCHUK)
    private val wiiClassic = PadProfile.forTag("WII", wii = WiiPad.CLASSIC)
    private val sega6 = PadProfile.forTag("MD", SegaPad.SIX)
    private val sega3 = PadProfile.forTag("MD", SegaPad.THREE)
    private val nds = PadProfile.forTag("NDS")

    @Test fun the_portrait_top_row_takes_the_pills_that_shared_menus_row() {
        assertEquals(listOf(PortraitTopPill.SELECT, PortraitTopPill.START), portraitTopPills(PadProfile.STANDARD, tools = false, motion = false))
        val dsRow = listOf(PortraitTopPill.STYLUS, PortraitTopPill.SELECT, PortraitTopPill.START, PortraitTopPill.SWAP)
        assertEquals(dsRow, portraitTopPills(nds, tools = true, motion = false))
        assertEquals(dsRow, portraitTopPills(PadProfile.N3DS_NEW, tools = true, motion = false))
        assertEquals(
            listOf(PortraitTopPill.SELECT, PortraitTopPill.HOME, PortraitTopPill.START, PortraitTopPill.MOTION),
            portraitTopPills(wiiSideways, tools = false, motion = true),
        )
        // The Classic Controller has no MOTION; without a host's motion there is none either.
        assertEquals(listOf(PortraitTopPill.SELECT, PortraitTopPill.HOME, PortraitTopPill.START), portraitTopPills(wiiClassic, tools = false, motion = true))
        assertEquals(listOf(PortraitTopPill.SELECT, PortraitTopPill.HOME, PortraitTopPill.START), portraitTopPills(wiiSideways, tools = false, motion = false))
        // SELECT hidden: START alone (centred between the shoulder halves).
        assertEquals(listOf(PortraitTopPill.START), portraitTopPills(PadProfile.GC, tools = false, motion = false))
        assertEquals(listOf(PortraitTopPill.START), portraitTopPills(sega3, tools = false, motion = false))
    }

    // The Fold's cover in portrait: 443 dp inside the padding, 9 units of 49.2 dp; MENU's pill 1.4 units.
    private val u = 443f / 9f
    private val pill = 1.4f * u
    private val g = PORTRAIT_TOP_GAP

    /** The widest shoulder group of [profile] on the portrait top row, dp (L1 / R1 1 unit, the triggers 0.72, 8 dp apart). */
    private fun side(profile: PadProfile): Float = listOf(PadSide.LEFT, PadSide.RIGHT).maxOf { s ->
        val b = padShoulders(s, profile)
        b.sumOf { (if (it == NxButton.L1 || it == NxButton.R1) u else 0.72f * u).toDouble() }.toFloat() + 8f * (b.size - 1).coerceAtLeast(0)
    }

    @Test fun the_portrait_top_pills_keep_menus_size_unless_four_sit_between_all_four_shoulders() {
        fun width(profile: PadProfile, tools: Boolean, motion: Boolean) =
            portraitTopPillWidth(pill, 443f, side(profile), portraitTopPills(profile, tools, motion).size, g)
        // Unchanged pads: MENU's size.
        assertEquals(pill, width(PadProfile.STANDARD, false, false))
        assertEquals(pill, width(wiiClassic, false, false))
        assertEquals(pill, width(PadProfile.GC, false, false))
        // The DS (L and R only) and the sideways Wiimote (Shake alone) fit four full pills.
        assertEquals(pill, width(nds, true, false))
        assertEquals(pill, width(wiiSideways, false, true))
        // The New 3DS and the Nunchuk Wii: four pills between L ZL and ZR R narrow to ~1.1 units, never into the shoulders.
        for ((profile, tools, motion) in listOf(Triple(PadProfile.N3DS_NEW, true, false), Triple(wiiNunchuk, false, true))) {
            val w = width(profile, tools, motion)
            assertTrue(w < pill && w > 1.05f * u, "$profile: $w")
            val n = portraitTopPills(profile, tools, motion).size
            assertEquals(443f, n * w + (n - 1) * g + 2 * (side(profile) + g), 0.01f)
        }
        assertEquals(pill, portraitTopPillWidth(pill, 443f, 100f, pills = 0, gap = g))
    }

    // The GameCube: START alone, centred in the row (the halves either side hold L and R + Z), at full size, clear of R + Z.
    @Test fun the_gamecube_start_is_centred_at_full_size_clear_of_its_wider_right_shoulders() {
        val w = portraitTopPillWidth(pill, 443f, side(PadProfile.GC), 1, g)
        assertEquals(pill, w)
        val startLeft = (443f - w) / 2f
        val rightShouldersLeft = 443f - side(PadProfile.GC)
        assertTrue(startLeft + w + g <= rightShouldersLeft)
        assertTrue(side(PadProfile.GC) > padShoulders(PadSide.LEFT, PadProfile.GC).size * 0.72f * u, "the right group is the wider one")
    }

    @Test fun over_the_game_the_shortcuts_sit_under_the_toggle_and_select() {
        assertEquals(OverlayShortcutPlace(1, under = true), overlayShortcutPlace(PadSide.LEFT, padSystemRow(PadSide.LEFT)))
        assertEquals(OverlayShortcutPlace(1, under = true), overlayShortcutPlace(PadSide.LEFT, padSystemRow(PadSide.LEFT, wiiSideways, motion = true)))
        assertEquals(OverlayShortcutPlace(1, under = true), overlayShortcutPlace(PadSide.RIGHT, padSystemRow(PadSide.RIGHT)))
        assertEquals(OverlayShortcutPlace(1, under = true), overlayShortcutPlace(PadSide.RIGHT, padSystemRow(PadSide.RIGHT, nds)))
        // The Wii's SELECT ( − ) is the third pill in from the edge: − HOME +.
        assertEquals(OverlayShortcutPlace(2, under = true), overlayShortcutPlace(PadSide.RIGHT, padSystemRow(PadSide.RIGHT, wiiClassic)))
        // No SELECT: in its place beside START, on the row's line.
        for (profile in listOf(PadProfile.GC, PadProfile.N64, sega3)) {
            assertEquals(OverlayShortcutPlace(1, under = false), overlayShortcutPlace(PadSide.RIGHT, padSystemRow(PadSide.RIGHT, profile)), "$profile")
        }
        assertEquals(0f, overlayShortcutTop(OverlayShortcutPlace(1, under = false), tools = false))
        assertEquals(0.72f + OVERLAY_SHORTCUT_GAP, overlayShortcutTop(OverlayShortcutPlace(1, under = true), tools = false))
        assertEquals(0.72f + 0.35f, overlayShortcutTop(OverlayShortcutPlace(1, under = true), tools = true))   // STYLUS / SWAP's line
    }

    /**
     * The over-the-game cluster's default layout, height-bound (6 units, a phone in landscape at 56 dp a unit), in units
     * from the cluster's top and its screen edge (x grows inward): the system row on top (the DS / 3DS's STYLUS or SWAP
     * 0.35 under it), the free height, the shoulders (1 tall, outside in, 8 dp apart; the triggers 0.72 centred in the
     * row; the right ones 0.41 lower beside a C-stick), 0.35, the d-pad (3) or face cluster at the bottom. A column too
     * tall for 6 (the DS / 3DS with STYLUS or SWAP) leaves no free height: the shoulders then start under the row.
     */
    private fun shoulderRects(side: PadSide, profile: PadProfile, tools: Boolean): List<Rect> {
        val cStick = side == PadSide.RIGHT && profile.cStick
        val cluster = when {
            side == PadSide.LEFT -> 3f
            cStick -> cStickLayout(C_STICK_LANDSCAPE_DROP).height
            else -> faceHeightUnits(profile)
        }
        val system = if (tools) 0.72f + 0.35f + 0.72f else 0.72f
        val top = maxOf(system, 6f - cluster - 0.35f - 1f) + if (cStick) 0.41f else 0f
        val dp8 = 8f / 56f
        var x = OVERLAY_EDGE_NUDGE_TEST
        // Outside in: L1 then L2 on the left; R1 then R2 on the right.
        val outsideIn = padShoulders(side, profile).let { if (side == PadSide.RIGHT) it.reversed() else it }
        return outsideIn.map { b ->
            val size = if (b == NxButton.L1 || b == NxButton.R1) 1f else 0.72f
            Rect(x, top + (1f - size) / 2f, x + size, top + (1f + size) / 2f).also { x += size + dp8 }
        }
    }

    private fun shortcutRect(side: PadSide, profile: PadProfile, tools: Boolean, motion: Boolean): Rect {
        val place = overlayShortcutPlace(side, padSystemRow(side, profile, motion))
        val left = OVERLAY_EDGE_NUDGE_TEST + place.fromEdge * (1.4f + 12f / 56f)
        val top = overlayShortcutTop(place, tools)
        return Rect(left, top, left + 1.4f, top + 0.72f)
    }

    private fun overlaps(a: Rect, b: Rect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test fun over_the_game_the_shortcuts_clear_the_shoulders_and_clusters_on_every_pad() {
        val pads = listOf(
            "GB" to PadProfile.forTag("GB"), "DS" to nds, "3DS" to PadProfile.N3DS, "New 3DS" to PadProfile.N3DS_NEW,
            "GC" to PadProfile.GC, "N64" to PadProfile.N64, "Sega 6" to sega6, "Sega 3" to sega3,
            "Wii sideways" to wiiSideways, "Wii Nunchuk" to wiiNunchuk, "Wii Classic" to wiiClassic,
        )
        for ((name, profile) in pads) {
            val tools = name.endsWith("DS")
            val motion = profile.motionButton
            for (side in PadSide.entries) {
                val touch = shortcutRect(side, profile, tools, motion)
                for (s in shoulderRects(side, profile, tools)) assertFalse(overlaps(touch, s), "$name $side: $touch over shoulder $s")
                // Above the d-pad / face cluster's box (bottom-anchored), and inside the 6 units.
                val cluster = if (side == PadSide.LEFT) 3f else if (profile.cStick) cStickLayout(C_STICK_LANDSCAPE_DROP).height else faceHeightUnits(profile)
                assertTrue(touch.bottom <= 6f - cluster, "$name $side: over the cluster")
                // On the row's line it sits after the row's last pill; under it, below the row's pills (and beside STYLUS / SWAP).
                if (touch.top == 0f) assertTrue(touch.left >= OVERLAY_EDGE_NUDGE_TEST + padSystemRow(side, profile, motion).size * 1.4f, "$name $side")
                else assertTrue(touch.top >= 0.72f, "$name $side")
            }
        }
    }

    private companion object {
        const val OVERLAY_EDGE_NUDGE_TEST = 0.2f   // the clusters' edge nudge over the game
    }
}
