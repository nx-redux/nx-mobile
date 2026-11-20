package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A hidden button closed out of its row ([withoutControl]): its mates close up, the well shortens, the row stays centred. */
class SkinWithoutTest {
    private fun cap(id: String, x: Float, y: Float = 72f) = ControlSpec(id, ControlShape.CIRCLE, x, y, r = 58f, capR = 40f, label = id)
    private fun capPiece(x: Int, group: String, y: Int = 32) = PieceSpec(sx = 0, sy = 0, w = 80, h = 80, x = x - 40, y = y, anchor = PieceAnchor.CENTRE, group = group)

    // the Fold's 3DS row: MIC MENU SWAP SELECT START on a well of caps and stretched middles, 116 px apart
    private val group = "MENU+MIC+SELECT+START+SWAP"
    private val row = BoxSize(
        w = 608, h = 145,
        pieces = listOf(
            PieceSpec(sx = 1533, sy = 701, w = 72, h = 144, x = 0, y = 0, anchor = PieceAnchor.CENTRE, group = group),
            PieceSpec(sx = 1604, sy = 701, w = 2, h = 144, x = 72, y = 0, anchor = PieceAnchor.CENTRE, group = group, scaleX = 232f),
            PieceSpec(sx = 1871, sy = 701, w = 72, h = 144, x = 536, y = 0, anchor = PieceAnchor.CENTRE, group = group),
            PieceSpec(sx = 1604, sy = 701, w = 2, h = 144, x = 304, y = 0, anchor = PieceAnchor.CENTRE, group = group, scaleX = 116f),
            capPiece(72, group), capPiece(188, group), capPiece(304, group), capPiece(420, group), capPiece(536, group),
        ),
        controls = listOf(cap("MIC", 72f), cap("MENU", 188f), cap("SWAP", 304f), cap("SELECT", 420f), cap("START", 536f)),
    )

    private fun BoxSize.ends(): Pair<Float, Float> {
        val well = pieces.filter { it.w == 2 || it.h == 144 }
        return well.minOf { it.x.toFloat() } to well.maxOf { it.x + it.w * it.acrossScale }
    }

    @Test fun the_end_button_goes_and_its_mates_close_up_round_the_middle() {
        val out = row.withoutControl("MIC")
        assertNull(out.controls.firstOrNull { it.id == "MIC" })
        assertEquals(listOf(130f, 246f, 362f, 478f), out.controls.map { it.x })
        assertEquals(58f to 550f, out.ends())   // 116 px shorter, centred on 304 still
        assertEquals(4, out.pieces.count { it.w == 80 })   // the MIC cap gone, the others kept
        assertEquals(listOf(130, 246, 362, 478), out.pieces.filter { it.w == 80 }.map { it.x + 40 })
    }

    @Test fun a_middle_button_goes_from_a_one_piece_well() {
        val g = "MENU+MIC+SWAP"
        val one = BoxSize(
            w = 410, h = 152,
            pieces = listOf(PieceSpec(sx = 1533, sy = 701, w = 410, h = 144, x = 0, y = 0, anchor = PieceAnchor.CENTRE, group = g)),
            controls = listOf(cap("SWAP", 65f), cap("MIC", 205f), cap("MENU", 345f)),
        )
        val out = one.withoutControl("MIC")
        assertEquals(listOf(135f, 275f), out.controls.map { it.x })
        // the well cut at its middle: its two ends kept from the art, 140 px shorter
        assertEquals(listOf(70 to 135, 205 to 135), out.pieces.map { it.x to it.w })
        assertEquals(listOf(1533, 1533 + 275), out.pieces.map { it.sx })
    }

    @Test fun a_column_closes_up_downward() {
        val g = "MENU+MIC"
        val col = BoxSize(
            w = 144, h = 300,
            pieces = listOf(PieceSpec(sx = 0, sy = 0, w = 144, h = 300, x = 0, y = 0, anchor = PieceAnchor.LEFT, group = g)),
            controls = listOf(cap("MENU", 72f, 80f), cap("MIC", 72f, 220f)),
        )
        val out = col.withoutControl("MIC")
        assertEquals(listOf(72f to 150f), out.controls.map { it.x to it.y })
        assertEquals(listOf(70 to 80, 150 to 80), out.pieces.map { it.y to it.h })   // the well's two ends, 140 px shorter
        assertEquals(listOf(0, 220), out.pieces.map { it.sy })
    }

    @Test fun a_slice_cut_away_whole_is_dropped() {
        // a 2 px slice stretched over exactly the strip that goes: drawn at 0 it would fall back to its own width
        val g = "MENU+MIC"
        val pair = BoxSize(
            w = 432, h = 144,
            pieces = listOf(
                PieceSpec(sx = 0, sy = 0, w = 146, h = 144, x = 0, y = 0, anchor = PieceAnchor.CENTRE, group = g),
                PieceSpec(sx = 146, sy = 0, w = 2, h = 144, x = 146, y = 0, anchor = PieceAnchor.CENTRE, group = g, scaleX = 70f),
                PieceSpec(sx = 286, sy = 0, w = 146, h = 144, x = 286, y = 0, anchor = PieceAnchor.CENTRE, group = g),
            ),
            controls = listOf(cap("MENU", 146f), cap("MIC", 286f)),
        )
        val out = pair.withoutControl("MIC")
        assertTrue(out.pieces.none { it.scaleX > 0f || it.acrossScale <= 0f }, "${out.pieces}")
        assertEquals(2, out.pieces.size)
    }

    @Test fun a_layout_without_boxes_loses_the_cap_too() {
        val spec = SkinLayoutSpec(
            id = "wide", family = SkinFamily.entries.first(), variant = SkinVariant.entries.first(), image = "x", width = 100, height = 100,
            tree = SliceNode(), glasses = emptyList(), scales = emptyList(),
            controls = listOf(cap("MIC", 484f, 1808f).copy(anchor = PieceAnchor.CENTRE), cap("MENU", 584f, 1808f).copy(anchor = PieceAnchor.CENTRE)),
            pieces = listOf(
                PieceSpec(sx = 0, sy = 0, w = 100, h = 100, x = 434, y = 1758, anchor = PieceAnchor.CENTRE),   // MIC's cap
                PieceSpec(sx = 0, sy = 0, w = 100, h = 100, x = 534, y = 1758, anchor = PieceAnchor.CENTRE),   // MENU's
                PieceSpec(sx = 0, sy = 0, w = 300, h = 144, x = 400, y = 1736, anchor = PieceAnchor.CENTRE),   // the well
            ),
        )
        val out = SkinPackManifest(format = 2, id = "p", name = "p", layouts = listOf(spec)).withoutControl("MIC").layouts.single()
        assertEquals(listOf("MENU"), out.controls.map { it.id })
        assertEquals(listOf(534, 400), out.pieces.map { it.x })
    }

    @Test fun a_box_without_the_button_is_left_as_it_is() {
        val plain = BoxSize(w = 10, h = 10, controls = listOf(cap("A", 5f)))
        assertTrue(plain.withoutControl("MIC") === plain)
    }
}
