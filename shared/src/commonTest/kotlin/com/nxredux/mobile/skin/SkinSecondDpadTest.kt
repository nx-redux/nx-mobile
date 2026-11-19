package com.nxredux.mobile.skin

import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.SkinPadTouch
import com.nxredux.mobile.ui.nx.SkinPressShape
import com.nxredux.mobile.ui.nx.VB_SECOND_DPAD
import com.nxredux.mobile.ui.nx.WS_SECOND_DPAD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A console's second d-pad (the Virtual Boy's right one, the WonderSwan's Y cursor): its directions as the core's buttons. */
class SkinSecondDpadTest {
    private fun spec() = SkinLayoutSpec(
        id = "s", family = SkinFamily.STACKED, variant = SkinVariant.VB, image = "s.png", width = 1000, height = 1000,
        tree = SliceNode(SliceSplit.ROWS, listOf(SlicePart(1000))), glasses = emptyList(), scales = listOf(emptyList()), controls = emptyList(),
    )

    private fun pad(id: String, x: Float) = PlacedControl(ControlSpec(id, ControlShape.DPAD, x, 500f, r = 150f, arm = 50f), x, 500f)

    // the d-pad at (200, 500), the second at (800, 500), each 150 each way
    private fun touch(events: MutableList<PadEvent>, second: Map<NxButton, NxButton> = VB_SECOND_DPAD) =
        SkinPadTouch(onPad = { events += it }, onAction = { _, _ -> }, tick = {}).apply {
            placement = SkinPlacement(spec(), 1f, emptyList(), emptyList(), listOf(pad("DPAD", 200f), pad("DPAD2", 800f)))
            secondDpad = second
        }

    @Test
    fun theSecondDpadSendsTheCoresButtonsForItsDirections() {
        val ev = mutableListOf<PadEvent>()
        val t = touch(ev)
        t.press(1, 800f, 400f)   // up
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.L2)), ev)
        t.move(1, 900f, 500f)    // slid to right
        assertEquals(listOf(PadEvent.Down(NxButton.L2), PadEvent.Up(NxButton.L2), PadEvent.Down(NxButton.R3)), ev)
        t.release(1)
        assertEquals(PadEvent.Up(NxButton.R3), ev.last())
    }

    @Test
    fun bothDpadsTakeAThumbEach() {
        val ev = mutableListOf<PadEvent>()
        val t = touch(ev)
        t.press(1, 100f, 500f)   // the d-pad's left
        t.press(2, 800f, 600f)   // the second's down
        assertEquals(setOf<PadEvent>(PadEvent.Down(NxButton.LEFT), PadEvent.Down(NxButton.L3)), ev.toSet())
        t.move(1, 90f, 510f)     // the first thumb steering on its own pad presses nothing of the second's
        assertEquals(2, ev.size)
    }

    @Test
    fun aThumbSlidOntoTheOtherDpadKeepsSteeringItsOwn() {
        val ev = mutableListOf<PadEvent>()
        val t = touch(ev)
        t.press(1, 200f, 380f)   // the d-pad's up
        t.move(1, 800f, 500f)    // slid straight onto the second: still the first's, now its right (not up held)
        assertEquals(listOf(PadEvent.Down(NxButton.UP), PadEvent.Up(NxButton.UP), PadEvent.Down(NxButton.RIGHT)), ev)
        assertTrue(ev.none { it == PadEvent.Down(NxButton.L1) || it == PadEvent.Down(NxButton.R2) || it == PadEvent.Down(NxButton.L2) }, "nothing of the second's: $ev")
    }

    @Test
    fun theWonderSwansYCursorIsItsCoresR2L2LAndR() {
        val ev = mutableListOf<PadEvent>()
        val t = touch(ev, WS_SECOND_DPAD)
        for ((x, y, b) in listOf(Triple(800f, 400f, NxButton.R2), Triple(800f, 600f, NxButton.L2), Triple(700f, 500f, NxButton.L1), Triple(900f, 500f, NxButton.R1))) {
            ev.clear(); t.press(1, x, y); t.release(1)
            assertEquals(listOf(PadEvent.Down(b), PadEvent.Up(b)), ev, "$b")
        }
    }

    @Test
    fun theSecondDpadsHeldArmIsShaded() {
        val t = touch(mutableListOf())
        t.press(1, 800f, 400f)   // up
        val arm = t.pressedShapes().single() as SkinPressShape.Box
        assertTrue(arm.left > 700f && arm.bottom <= 500f, "the up arm of the second d-pad: $arm")
    }

    @Test
    fun withoutAMapTheSecondDpadIsDead() {
        val ev = mutableListOf<PadEvent>()
        val t = touch(ev, emptyMap())
        assertEquals(false, t.press(1, 800f, 400f))
        assertTrue(ev.isEmpty())
    }
}
