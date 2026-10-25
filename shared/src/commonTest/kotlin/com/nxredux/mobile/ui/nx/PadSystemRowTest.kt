package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.SegaPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The landscape clusters drawn over the game put their system row at the top; the main menu's and the
 * flex-mode clusters keep it lowest. The left cluster's row is always MENU + toggle.
 */
class PadSystemRowTest {
    private val sega3 = PadProfile.forTag("32X", SegaPad.THREE)

    @Test fun theLeftClusterHoldsMenuBeforeTheToggle() {
        assertEquals(listOf(PadSystemKey.MENU, PadSystemKey.TOGGLE), padSystemRow(PadSide.LEFT))
        assertEquals(listOf(PadSystemKey.MENU, PadSystemKey.TOGGLE), padSystemRow(PadSide.LEFT, sega3))
    }

    @Test fun theRightClusterHoldsSelectAndStartWithTheHiddenSelectRule() {
        assertEquals(listOf(PadSystemKey.SELECT, PadSystemKey.START), padSystemRow(PadSide.RIGHT))
        assertEquals(listOf(PadSystemKey.START), padSystemRow(PadSide.RIGHT, sega3))
    }

    // Over the game the system row is on top and the spacer under it takes the free height (the rest sits low); elsewhere
    // the row is lowest and every spacer is the gap.
    @Test fun overTheGameTheSpaceUnderTheTopSystemRowStretches() {
        val over = clusterRows(overGame = true)
        assertEquals(PadRow.SYSTEM, over.first())
        assertEquals(1, flexSpacerRow(over, overGame = true))
        val under = clusterRows(overGame = false)
        assertEquals(PadRow.SYSTEM, under.last())
        assertNull(flexSpacerRow(under, overGame = false))
    }

    // Beside the picture the pad has its own column: shoulders, d-pad / face cluster, then the system row, with or without overGame.
    @Test fun besidePictureRowsOrder() {
        val expected = listOf(PadRow.SHOULDERS, PadRow.CLUSTER, PadRow.SYSTEM)
        assertEquals(expected, clusterRows(overGame = false, besidePicture = true))
        assertEquals(expected, clusterRows(overGame = true, besidePicture = true))
        assertNull(flexSpacerRow(clusterRows(overGame = true, besidePicture = true), overGame = false))
    }

    // The stretching spacer follows the system row, wherever it is in the list (not a fixed index).
    @Test fun theStretchingSpacerFollowsTheSystemRow() {
        assertEquals(2, flexSpacerRow(listOf(PadRow.SHOULDERS, PadRow.SYSTEM, PadRow.CLUSTER), overGame = true))
        assertNull(flexSpacerRow(listOf(PadRow.SHOULDERS, PadRow.CLUSTER, PadRow.SYSTEM), overGame = true))   // nothing under it
    }

    @Test fun theMotionButtonJoinsTheLeftClusterWhenShown() {
        val wii = PadProfile.forTag("WII", wii = com.nxredux.mobile.runner.WiiPad.SIDEWAYS)
        assertEquals(listOf(PadSystemKey.MENU, PadSystemKey.TOGGLE, PadSystemKey.MOTION), padSystemRow(PadSide.LEFT, wii, motion = true))
        assertEquals(listOf(PadSystemKey.MENU, PadSystemKey.TOGGLE), padSystemRow(PadSide.LEFT, wii, motion = false))
    }

    // Only the sideways and upright Wiimotes and the GameCube hide shoulders; every other pad keeps L1 / R1 (and the triggers it shows).
    @Test fun theShoulderRowsLeaveOutOnlyHiddenShoulders() {
        val tags = listOf("SNES", "MD", "32X", "SMS", "PCE", "PS", "PSP", "GBA", "VB", "FC", "LYNX", "PKM", "DC", "N64", "NDS")
        for (profile in tags.map { PadProfile.forTag(it) } + listOf(sega3, PadProfile.N3DS, PadProfile.N3DS_NEW, PadProfile.STANDARD)) {
            assertEquals(NxButton.L1, padShoulders(PadSide.LEFT, profile).first(), "$profile")
            assertEquals(NxButton.R1, padShoulders(PadSide.RIGHT, profile).last(), "$profile")
        }
        // The GameCube pad hides L1 and keeps Z on R1.
        assertEquals(listOf(NxButton.L2), padShoulders(PadSide.LEFT, PadProfile.GC))
        assertEquals(listOf(NxButton.R2, NxButton.R1), padShoulders(PadSide.RIGHT, PadProfile.GC))
        assertEquals(listOf(NxButton.L1, NxButton.L2), padShoulders(PadSide.LEFT))
        assertEquals(listOf(NxButton.R2, NxButton.R1), padShoulders(PadSide.RIGHT))
        assertEquals(listOf(NxButton.L1), padShoulders(PadSide.LEFT, PadProfile.N3DS))
        assertEquals(listOf(NxButton.R1), padShoulders(PadSide.RIGHT, PadProfile.N3DS))
    }

    @Test fun theSidewaysAndUprightWiimotesKeepOnlyShake() {
        for (pad in listOf(com.nxredux.mobile.runner.WiiPad.SIDEWAYS, com.nxredux.mobile.runner.WiiPad.UPRIGHT)) {
            val wii = PadProfile.forTag("WII", wii = pad)
            assertEquals(emptyList(), padShoulders(PadSide.LEFT, wii), "$pad")
            assertEquals(listOf(NxButton.R2), padShoulders(PadSide.RIGHT, wii), "$pad")
        }
        for (pad in listOf(com.nxredux.mobile.runner.WiiPad.NUNCHUK, com.nxredux.mobile.runner.WiiPad.CLASSIC)) {
            val wii = PadProfile.forTag("WII", wii = pad)
            assertEquals(listOf(NxButton.L1, NxButton.L2), padShoulders(PadSide.LEFT, wii), "$pad")
            assertEquals(listOf(NxButton.R2, NxButton.R1), padShoulders(PadSide.RIGHT, wii), "$pad")
        }
    }

    // Every Wii pad puts HOME between SELECT and START (− ⌂ + on a Wiimote, 2 ⌂ 1 on the Nunchuk pad); nothing else has it.
    @Test fun wiiPadsPutHomeBetweenSelectAndStart() {
        for (pad in com.nxredux.mobile.runner.WiiPad.entries) {
            val wii = PadProfile.forTag("WII", wii = pad)
            assertEquals(listOf(PadSystemKey.SELECT, PadSystemKey.HOME, PadSystemKey.START), padSystemRow(PadSide.RIGHT, wii), "$pad")
            assertEquals(true, wii.homeButton, "$pad")
            assertEquals("HOME", wii.label(NxButton.HOME), "$pad")
        }
        assertEquals(false, PadProfile.GC.homeButton)
        assertEquals(listOf(PadSystemKey.START), padSystemRow(PadSide.RIGHT, PadProfile.GC))
        assertEquals(false, PadProfile.STANDARD.homeButton)
        assertEquals(listOf(PadSystemKey.SELECT, PadSystemKey.START), padSystemRow(PadSide.RIGHT, PadProfile.forTag("SNES")))
    }

    // The layout editor arranges the Wii's fullest pad (Classic: C-stick, every shoulder, HOME); other tags as in game.
    @Test fun theLayoutEditorGetsTheFullestWiiPad() {
        val wii = PadProfile.forLayoutEditor("WII")
        assertEquals(com.nxredux.mobile.runner.WiiPad.CLASSIC, wii.wiiPad)
        assertEquals(true, wii.cStick)
        assertEquals(true, wii.homeButton)
        assertEquals(listOf(NxButton.L1, NxButton.L2), padShoulders(PadSide.LEFT, wii))
        assertEquals(PadProfile.GC.toString(), PadProfile.forLayoutEditor("GC").toString())
        assertEquals(PadProfile.N3DS_NEW.toString(), PadProfile.forLayoutEditor("3DS").toString())
    }
}
