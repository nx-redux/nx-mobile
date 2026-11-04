package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The analog stick in a skin: its control and knob piece, the stick / d-pad alternates, and the stick under the thumb. */
class SkinStickTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun spec() = SkinLayoutSpec(
        id = "s", family = SkinFamily.STACKED, variant = SkinVariant.DC, image = "s.png", width = 1000, height = 1000,
        tree = SliceNode(SliceSplit.ROWS, listOf(SlicePart(1000))), glasses = emptyList(), scales = listOf(emptyList()), controls = emptyList(),
    )

    private fun ctl(id: String, shape: ControlShape, x: Float, y: Float, r: Float, alt: String? = null, travel: Float = 0f) =
        PlacedControl(ControlSpec(id, shape, x, y, r = r, travel = travel, alt = alt), x, y)

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
}
