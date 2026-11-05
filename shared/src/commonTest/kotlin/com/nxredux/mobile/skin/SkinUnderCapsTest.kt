package com.nxredux.mobile.skin

import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.SkinPadTouch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Caps under the d-pad, the stick or the face buttons (the fold board's and landscape's MENU, DISK, SELECT, START,
 * swaps) press only when no other touch is on the skin: a thumb slipping off the d-pad or the face buttons, or one
 * sliding from DISK, doesn't open the menu or flip the disk. Caps elsewhere (portrait's middle row) are unchanged.
 */
class SkinUnderCapsTest {
    private val spec = SkinLayoutSpec(
        id = "u", family = SkinFamily.STACKED, variant = SkinVariant.FDS, image = "u.png", width = 1000, height = 1000,
        tree = SliceNode(SliceSplit.ROWS, listOf(SlicePart(1000))), glasses = emptyList(), scales = listOf(emptyList()), controls = emptyList(),
    )

    private fun ctl(id: String, shape: ControlShape, x: Float, y: Float, r: Float) = PlacedControl(ControlSpec(id, shape, x, y, r = r), x, y)

    private val pads = mutableListOf<PadEvent>()
    private val actions = mutableListOf<Pair<String, Boolean>>()

    // d-pad at (240, 400), face diamond around (600, 400); DISK + MENU under the d-pad, START under the face buttons,
    // SELECT in the middle between them; a stick at (240, 400) with its swap under it in the second layout
    private fun touch(stick: Boolean = false) = SkinPadTouch({ pads += it }, { id, d -> actions += id to d }, {}).apply {
        placement = SkinPlacement(spec, 1f, emptyList(), emptyList(), if (stick) listOf(
            ctl("STICK", ControlShape.STICK, 240f, 400f, 100f), ctl("STICK_SWAP", ControlShape.CIRCLE, 240f, 700f, 50f),
            ctl("A", ControlShape.CIRCLE, 700f, 400f, 50f),
        ) else listOf(
            ctl("DPAD", ControlShape.DPAD, 240f, 400f, 110f),
            ctl("A", ControlShape.CIRCLE, 700f, 400f, 50f), ctl("B", ControlShape.CIRCLE, 600f, 500f, 50f),
            ctl("X", ControlShape.CIRCLE, 600f, 300f, 50f), ctl("Y", ControlShape.CIRCLE, 500f, 400f, 50f),
            ctl("DISK", ControlShape.CIRCLE, 170f, 700f, 50f), ctl("MENU", ControlShape.CIRCLE, 310f, 700f, 50f),
            ctl("START", ControlShape.CIRCLE, 650f, 700f, 50f), ctl("SELECT", ControlShape.CIRCLE, 400f, 850f, 50f),
        ))
    }

    @Test
    fun anUnderCapAlonePresses() {
        val t = touch()
        assertTrue(t.press(1, 310f, 700f))
        t.release(1)
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.MENU), PadEvent.Up(NxButton.MENU)), pads)
    }

    @Test
    fun anUnderCapIsIgnoredWhileTheDpadIsHeld() {
        val t = touch()
        t.press(1, 240f, 300f)   // up
        pads.clear()
        assertTrue(t.press(2, 310f, 700f), "the touch is still the pad's, not the game's")
        assertTrue(t.holds(2))
        t.move(2, 320f, 700f)
        t.release(2)
        assertEquals(emptyList(), pads)
    }

    @Test
    fun anUnderCapIsIgnoredWhileAFaceButtonIsHeld() {
        val t = touch()
        t.press(1, 700f, 400f)
        pads.clear()
        t.press(2, 650f, 700f)
        t.release(2)
        assertEquals(emptyList(), pads)
    }

    @Test
    fun aThumbSlidingOffTheDpadDoesntPressTheCapUnderIt() {
        val t = touch()
        t.press(1, 240f, 470f)   // down
        pads.clear()
        t.move(1, 170f, 700f)    // onto DISK
        t.move(1, 310f, 700f)    // onto MENU
        assertTrue(pads.none { it == PadEvent.Down(NxButton.L1) || it == PadEvent.Down(NxButton.MENU) }, "$pads")
        t.release(1)
        assertTrue(pads.none { it is PadEvent.Down }, "$pads")
    }

    @Test
    fun slidingFromDiskOntoMenuDoesntOpenTheMenu() {
        val t = touch()
        t.press(1, 170f, 700f)
        t.move(1, 310f, 700f)
        t.release(1)
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.L1), PadEvent.Up(NxButton.L1)), pads)
    }

    @Test
    fun aHeldUnderCapDoesntBlockTheOthers() {
        val t = touch()
        t.press(1, 310f, 700f)
        t.press(2, 700f, 400f)
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.MENU), PadEvent.Down(NxButton.A)), pads)
    }

    @Test
    fun holdingAnUnderCapThenTheDpadWorks() {
        // e.g. DS: hold SELECT, then the d-pad switches the layout
        val t = touch()
        t.press(1, 650f, 700f)   // START, under the face buttons
        t.press(2, 240f, 300f)   // d-pad up
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.START), PadEvent.Down(NxButton.UP)), pads)
    }

    @Test
    fun aCapBetweenTheClustersIsntGuarded() {
        val t = touch()
        t.press(1, 240f, 300f)
        pads.clear()
        t.press(2, 400f, 850f)
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.SELECT)), pads)
    }

    @Test
    fun theSwapUnderTheStickIsIgnoredWhileTheStickIsHeld() {
        val t = touch(stick = true)
        t.press(1, 240f, 400f)
        t.press(2, 240f, 700f)
        t.release(2)
        assertEquals(emptyList(), actions)
        t.release(1)
        t.press(3, 240f, 700f)
        assertEquals(listOf("STICK_SWAP" to true), actions)
    }
}
