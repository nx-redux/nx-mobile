package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SkinBoxesTest {
    private val full = BoxSize(420, 900)
    private val narrow = BoxSize(360, 900)
    private val small = BoxSize(280, 700)
    private val tagged = BoxSize(400, 880, `when` = BoxWhen(orientation = "portrait", minWdp = 400f, maxWdp = 460f))
    private val box = BoxSpec(BoxSlot.RIGHT, listOf(full, narrow, small, tagged))

    // The size drawn for this device shape wins even where the full one would fit.
    @Test
    fun aTaggedSizeWinsOnItsDeviceShape() {
        assertSame(tagged, SkinBoxes.pick(box, DeviceShape(411f, 891f, halfFolded = false)) { true })
    }

    // A device nobody tuned for: the size drawn for the nearest device shape (the same orientation), so every layout
    // of the pack falls back alike; with none of that orientation, the full size.
    @Test
    fun untaggedDeviceTakesTheNearestTunedSize() {
        assertSame(tagged, SkinBoxes.pick(box, DeviceShape(380f, 800f, halfFolded = false)) { true })
        val wide = BoxSize(500, 900, `when` = BoxWhen(orientation = "portrait", minWdp = 600f, maxWdp = 700f))
        val two = BoxSpec(BoxSlot.RIGHT, listOf(full, tagged, wide))
        assertSame(wide, SkinBoxes.pick(two, DeviceShape(620f, 1000f, halfFolded = false)) { true })
        // the tag's orientation counts: a landscape window falls to the full size
        assertSame(full, SkinBoxes.pick(box, DeviceShape(800f, 380f, halfFolded = false)) { true })
    }

    // Where the full size does not fit: the biggest compact one that does.
    @Test
    fun compactSizeWhenTheFullOneDoesNotFit() {
        assertSame(small, SkinBoxes.pick(box, DeviceShape(800f, 380f, halfFolded = false)) { it.w <= 300 })
        assertSame(narrow, SkinBoxes.pick(box, DeviceShape(800f, 380f, halfFolded = false)) { it.w <= 370 })
    }

    // Nothing drawn for this orientation and nothing fits: no size (a drawing for a near shape would shrink to fit)
    @Test
    fun noSizeFitsGivesNull() {
        assertNull(SkinBoxes.pick(box, DeviceShape(800f, 380f, halfFolded = false)) { false })
        assertSame(tagged, SkinBoxes.pick(box, DeviceShape(380f, 800f, halfFolded = false)) { false })
    }

    // A half-folded tag needs the device half-folded.
    @Test
    fun halfFoldedTagNeedsTheFold() {
        val half = BoxSize(300, 500, `when` = BoxWhen(halfFolded = true))
        val b = BoxSpec(BoxSlot.LEFT, listOf(full, half))
        assertSame(half, SkinBoxes.pick(b, DeviceShape(411f, 891f, halfFolded = true)) { true })
        assertSame(full, SkinBoxes.pick(b, DeviceShape(411f, 891f, halfFolded = false)) { true })
    }

    private val pack = skinPack()

    /**
     * [id]'s pieces and controls as boxes, as drawn: the left-anchored in the left box, the right-anchored in the right,
     * the centred under the screen in the bottom row; each box as big as what it holds.
     */
    private fun boxed(id: String): SkinLayoutSpec {
        val spec = pack.layouts.first { it.id == id }
        fun box(slot: BoxSlot, a: PieceAnchor): BoxSpec? {
            val ps = spec.pieces.filter { it.glass == null && it.anchor == a }
            val cs = spec.controls.filter { (it.anchor ?: PieceAnchor.LEFT) == a }
            if (ps.isEmpty() && cs.isEmpty()) return null
            val x0 = ps.minOf { it.x }; val y0 = ps.minOf { it.y }
            val x1 = ps.maxOf { it.x + it.w * it.scale }; val y1 = ps.maxOf { it.y + it.h * it.scale }
            return BoxSpec(slot, listOf(BoxSize((x1 - x0).toInt(), (y1 - y0).toInt(),
                ps.map { it.copy(x = it.x - x0, y = it.y - y0) }, cs.map { it.copy(x = it.x - x0, y = it.y - y0) })))
        }
        return spec.copy(
            pieces = spec.pieces.filter { it.glass != null },
            controls = emptyList(),
            boxes = listOfNotNull(box(BoxSlot.LEFT, PieceAnchor.LEFT), box(BoxSlot.RIGHT, PieceAnchor.RIGHT), box(BoxSlot.BOTTOM_ROW, PieceAnchor.CENTRE)),
        )
    }

    private fun place(spec: SkinLayoutSpec, w: Int, h: Int, dpi: Int): SkinPlacement? {
        val k = dpi / 420f
        return SkinBoxes.place(spec, w, h, k, dpi, hingeY = null) { s, room ->
            SkinSolver.placePieces(s, w.toFloat(), h.toFloat(), k, roomLeft = room.left, roomRight = room.right, roomBottom = room.bottom)
        }
    }

    private fun SkinPlacement.side(a: PieceAnchor) = pieces.filter { it.piece >= 0 && spec.pieces[it.piece].glass == null && spec.pieces[it.piece].anchor == a }.map { it.dst }
    private fun SkinPlacement.frame() = pieces.filter { it.piece < 0 && !it.strip }.map { it.dst }

    // Landscape: the side boxes 32 dp from the edges, the screen 16 dp from each, their main pads centred in the height.
    @Test
    fun landscapeBoxesSitAtTheEdgesAndTheScreenBetween() {
        val p = assertNotNull(place(boxed("grip_menu_tall"), 2448, 1848, 420))
        val dp = 420 / 160f
        val l = p.side(PieceAnchor.LEFT); val r = p.side(PieceAnchor.RIGHT); val f = p.frame()
        assertEquals(32f * dp, l.minOf { it.x }, 1f, "left box from the edge")
        assertEquals(2448f - 32f * dp, r.maxOf { it.x + it.width }, 1f, "right box from the edge")
        assertEquals(l.maxOf { it.x + it.width } + 16f * dp, f.minOf { it.x }, 1f, "the screen 16 dp from the left box")
        assertEquals(r.minOf { it.x } - 16f * dp, f.maxOf { it.x + it.width }, 1f, "the screen 16 dp from the right box")
        // sideways the main pads centred in the window's height: the d-pad, the face buttons
        assertEquals(1848f / 2f, p.controls.first { it.spec.id == "DPAD" }.y, 1f, "the d-pad centred")
        assertEquals(1848f / 2f, listOf("X", "Y", "A", "B").map { id -> p.controls.first { it.spec.id == id }.y }.average().toFloat(), 1f, "the face buttons centred")
        // the box layout is a plain placement: its groups as the controls editor sees them
        assertTrue(SkinGroups.of(p.spec).isNotEmpty())
        assertTrue(p.controls.any { it.spec.id == "A" } && p.controls.any { it.spec.id == "DPAD" })
    }

    // Portrait: the bottom row 32 dp over the window's bottom, the side boxes 16 dp over it, the screen 16 dp over them;
    // a taller window keeps the boxes at the bottom and gives the height to the screen.
    @Test
    fun portraitBoxesHoldTheBottomAndTheScreenTakesTheRest() {
        val dp = 480 / 160f
        for (h in listOf(2520, 2800)) {
            val p = assertNotNull(place(boxed("stacked_menu"), 1080, h, 480), "$h")
            val row = p.side(PieceAnchor.CENTRE); val l = p.side(PieceAnchor.LEFT); val f = p.frame()
            assertEquals(h - 32f * dp, row.maxOf { it.y + it.height }, 1f, "$h the bottom row from the bottom")
            assertEquals(row.minOf { it.y } - 16f * dp, l.maxOf { it.y + it.height }, 1f, "$h the side boxes over the row")
            val top = minOf(l.minOf { it.y }, p.side(PieceAnchor.RIGHT).minOf { it.y })
            assertEquals(top - 16f * dp, f.maxOf { it.y + it.height }, 1f, "$h the screen over the boxes")
        }
    }

    // Too narrow for the boxes at their size (the background still fits): both shrink by one factor, no less than
    // SHRINK_FLOOR, before the layout gives up.
    @Test
    fun tooNarrowShrinksEvenly() {
        val spec = boxed("grip_menu")
        val full = assertNotNull(place(spec, 2520, 1080, 480))
        fun width(q: SkinPlacement, a: PieceAnchor) = q.side(a).let { it.maxOf { r -> r.x + r.width } - it.minOf { r -> r.x } }
        val shrunk = (2520 downTo 1600 step 20).mapNotNull { w -> place(spec, w, 1080, 480) }
            .firstOrNull { width(it, PieceAnchor.LEFT) < width(full, PieceAnchor.LEFT) - 1f }
        val p = assertNotNull(shrunk, "some narrower window shrinks the boxes")
        val fl = width(p, PieceAnchor.LEFT) / width(full, PieceAnchor.LEFT)
        val fr = width(p, PieceAnchor.RIGHT) / width(full, PieceAnchor.RIGHT)
        assertTrue(fl < 1f && fl >= SkinBoxes.SHRINK_FLOOR - 0.01f, "shrunk $fl")
        assertEquals(fl, fr, 0.01f, "both alike")
    }

    @Test
    fun tooSmallReturnsNull() {
        assertNull(place(boxed("grip_menu_tall"), 900, 600, 420))
    }

    // The stick / d-pad switch's alternates inside a box stay on one spot: either shown, both at the same middle.
    @Test
    fun theSwitchKeepsBothAlternatesOnOneSpot() {
        val p = assertNotNull(place(boxed("grip_n64"), 2520, 1080, 480))
        for (stick in listOf(true, false)) {
            val q = p.forStick(stick)
            assertEquals(1, q.controls.count { it.spec.id == "STICK_SWAP" }, "one switch")
        }
        val stick = p.controls.first { it.spec.id == "STICK" }; val pad = p.controls.first { it.spec.id == "DPAD" }
        assertEquals(stick.x, pad.x, 1f); assertEquals(stick.y, pad.y, 1f)
    }

    // A layout with boxes goes to the box solver from SkinChoice: none of the console's rules move what the box holds
    // (the N64's stick stays where its box has it, not on the main menu's d-pad).
    @Test
    fun aLayoutWithBoxesSkipsTheConsoleRules() {
        val legacy = pack.layouts.first { it.id == "grip_n64_tall" }
        // the left box's contents drawn 50 px further in than the legacy layout has them
        val drawn = boxed("grip_n64_tall")
        val withBoxes = drawn.copy(pieces = legacy.pieces, controls = legacy.controls, boxes = drawn.boxes.map { box ->
            if (box.slot != BoxSlot.LEFT) box else box.copy(sizes = box.sizes.map { z -> z.copy(w = z.w + 50,
                pieces = z.pieces.map { it.copy(x = it.x + 50) }, controls = z.controls.map { it.copy(x = it.x + 50) }) })
        })
        val packed = pack.copy(layouts = pack.layouts.map { if (it.id == legacy.id) withBoxes else it })
        val p = assertNotNull(SkinChoice.place(packed, 2448, 1848, 420, tablet = true, ds = false, picture = 320f to 240f, foldable = true, variant = SkinVariant.N64))
        assertEquals("grip_n64_tall", p.spec.id)
        val left = withBoxes.boxes.first { it.slot == BoxSlot.LEFT }.sizes[0]
        val stick = left.controls.first { it.id == "STICK" }
        val dp = 420 / 160f
        assertEquals(32f * dp + stick.x, p.controls.first { it.spec.id == "STICK" }.x, 1f, "the stick where its box has it")
    }
}
