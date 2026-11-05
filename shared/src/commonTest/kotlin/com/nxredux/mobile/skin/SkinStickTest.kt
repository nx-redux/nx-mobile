package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.SkinPadTouch
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The analog stick in a skin: its control and knob piece, the stick / d-pad alternates, and the stick under the thumb. */
class SkinStickTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun spec() = SkinLayoutSpec(
        id = "s", family = SkinFamily.STACKED, variant = SkinVariant.DC, image = "s.png", width = 1000, height = 1000,
        tree = SliceNode(SliceSplit.ROWS, listOf(SlicePart(1000))), glasses = emptyList(), scales = listOf(emptyList()), controls = emptyList(),
    )

    private fun ctl(id: String, shape: ControlShape, x: Float, y: Float, r: Float, alt: String? = null, travel: Float = 0f, knobTravel: Float = 0f) =
        PlacedControl(ControlSpec(id, shape, x, y, r = r, travel = travel, alt = alt, knobTravel = knobTravel), x, y)

    private fun piece(x: Float, alt: String? = null, knob: Boolean = false) =
        SkinPiece(PxRect(0f, 0f, 10f, 10f), PxRect(x, 0f, 10f, 10f), alt = alt, knob = knob)

    @Test
    fun aStickControlParses() {
        val c = json.decodeFromString(ControlSpec.serializer(),
            """{"id":"STICK","shape":"stick","x":240,"y":1900,"r":100,"travel":64,"alt":"stick"}""")
        assertEquals(ControlShape.STICK, c.shape)
        assertEquals(64f, c.travel)
        assertEquals("stick", c.alt)
        val p = json.decodeFromString(PieceSpec.serializer(),
            """{"sx":0,"sy":0,"w":10,"h":10,"x":0,"y":0,"anchor":"left","knob":true,"alt":"stick"}""")
        assertEquals(true, p.knob)
        assertEquals("stick", p.alt)
        assertEquals(SkinVariant.DC, json.decodeFromString(SkinVariant.serializer(), "\"dc\""))
    }

    @Test
    fun forStickKeepsOneSet() {
        val p = SkinPlacement(spec(), 1f, emptyList(), emptyList(),
            listOf(ctl("STICK", ControlShape.STICK, 500f, 500f, 100f, "stick"), ctl("DPAD", ControlShape.DPAD, 500f, 500f, 100f, "dpad"),
                ctl("A", ControlShape.CIRCLE, 800f, 500f, 50f)),
            listOf(piece(1f, "stick"), piece(2f, "stick", knob = true), piece(3f, "dpad"), piece(4f)))
        assertEquals(setOf("STICK", "A"), p.forStick(true).controls.map { it.spec.id }.toSet())
        assertEquals(setOf("DPAD", "A"), p.forStick(false).controls.map { it.spec.id }.toSet())
        assertEquals(listOf(1f, 2f, 4f), p.forStick(true).pieces.map { it.dst.x })
        assertEquals(listOf(3f, 4f), p.forStick(false).pieces.map { it.dst.x })
        val plain = p.copy(controls = p.controls.filter { it.spec.alt == null }, pieces = p.pieces.filter { it.alt == null })
        assertEquals(plain, plain.forStick(true))
        assertEquals(plain, plain.forStick(false))
    }

    // one stick at (500, 500), ring 100, travel 60; the d-pad under it at (500, 760), 110 each way
    private val sticks = mutableListOf<Pair<Float, Float>>()
    private val pads = mutableListOf<PadEvent>()
    private val actions = mutableListOf<Pair<String, Boolean>>()
    private fun touch() = SkinPadTouch({ pads += it }, { id, d -> actions += id to d }, {}, onStick = { x, y -> sticks += x to y }).apply {
        placement = SkinPlacement(spec(), 1f, emptyList(), emptyList(), listOf(
            ctl("STICK", ControlShape.STICK, 500f, 500f, 100f, travel = 60f), ctl("DPAD", ControlShape.DPAD, 500f, 760f, 110f),
            ctl("STICK_SWAP", ControlShape.CIRCLE, 800f, 900f, 40f)))
    }
    private fun near(a: Pair<Float, Float>, x: Float, y: Float) = kotlin.math.abs(a.first - x) < 1e-3f && kotlin.math.abs(a.second - y) < 1e-3f

    @Test
    fun firstTouchCentresTheStick() {
        val t = touch()
        assertTrue(t.press(1, 530f, 480f))
        assertTrue(near(sticks.last(), 0f, 0f), "$sticks")
        t.move(1, 560f, 480f)
        assertTrue(near(sticks.last(), 0.5f, 0f), "$sticks")
        assertTrue(near(t.knobOffset(), 30f, 0f), "${t.knobOffset()}")
    }

    @Test
    fun pastTheRingIsFullTilt() {
        val t = touch()
        t.press(1, 530f, 480f)
        t.move(1, 530f, 380f)
        assertTrue(near(sticks.last(), 0f, -1f), "$sticks")
        assertTrue(near(t.knobOffset(), 0f, -60f), "${t.knobOffset()}")
    }

    @Test
    fun releaseCentresIt() {
        val t = touch()
        t.press(1, 530f, 480f); t.move(1, 560f, 480f); t.release(1)
        assertTrue(near(sticks.last(), 0f, 0f), "$sticks")
        assertTrue(near(t.knobOffset(), 0f, 0f))
        assertTrue(t.pressedShapes().isEmpty())
    }

    @Test
    fun theEdgeOfTheTouchAreaTakesTheStick() {
        val t = touch()
        assertTrue(t.press(1, 500f, 615f))   // past the ring (100), inside 1.25 x; the d-pad's box starts at 650
        assertTrue(t.holds(1))
        assertTrue(pads.isEmpty(), "$pads")
        assertTrue(sticks.isNotEmpty())
        assertEquals(1, t.pressedShapes().size)
    }

    @Test
    fun swapIsAnAction() {
        val t = touch()
        assertTrue(t.press(2, 800f, 900f))
        assertEquals(listOf("STICK_SWAP" to true), actions)
    }

    @Test
    fun theKnobMovesItsOwnShorterWay() {
        // a big knob in its hole moves 20 px at full tilt while the thumb goes 60 for it
        val t = SkinPadTouch({ pads += it }, { _, _ -> }, {}, onStick = { x, y -> sticks += x to y }).apply {
            placement = SkinPlacement(spec(), 1f, emptyList(), emptyList(), listOf(ctl("STICK", ControlShape.STICK, 500f, 500f, 170f, travel = 60f, knobTravel = 20f)))
        }
        t.press(1, 500f, 500f); t.move(1, 530f, 500f)
        assertTrue(near(sticks.last(), 0.5f, 0f), "$sticks")
        assertTrue(near(t.knobOffset(), 10f, 0f), "${t.knobOffset()}")
        t.move(1, 500f, 400f)
        assertTrue(near(t.knobOffset(), 0f, -20f), "${t.knobOffset()}")
    }

    @Test
    fun diskCapPressesL1AndSlidesLikeAButton() {
        // the FDS skin's DISK: fceumm's L (the one-press disk flip), the same press as a controller's L1
        val events = mutableListOf<PadEvent>()
        val t = SkinPadTouch({ events += it }, { _, _ -> }, {}).apply {
            placement = SkinPlacement(spec(), 1f, emptyList(), emptyList(), listOf(
                ctl("DISK", ControlShape.CIRCLE, 500f, 500f, 60f), ctl("B", ControlShape.CIRCLE, 700f, 500f, 60f)))
        }
        assertTrue(t.press(1, 500f, 500f))
        assertEquals(listOf<PadEvent>(PadEvent.Down(NxButton.L1)), events)
        t.move(1, 700f, 500f)
        assertEquals(listOf(PadEvent.Down(NxButton.L1), PadEvent.Up(NxButton.L1), PadEvent.Down(NxButton.B)), events)
        t.release(1)
        assertEquals(PadEvent.Up(NxButton.B), events.last())
    }
}
