package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.InfoLine
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RowLayoutTest {
    @Test fun stepsOneItemAndSignalsTheEnds() {
        assertEquals(1, rowStep(0, 3, NxButton.RIGHT))
        assertEquals(1, rowStep(2, 3, NxButton.LEFT))
        assertNull(rowStep(0, 3, NxButton.LEFT))
        assertNull(rowStep(2, 3, NxButton.RIGHT))
        assertEquals(1, rowStep(1, 3, NxButton.UP))      // UP/DOWN do nothing
        assertEquals(1, rowStep(1, 3, NxButton.DOWN))
        assertNull(rowStep(0, 1, NxButton.RIGHT))        // one item: both ends at once
        assertEquals(0, rowStep(0, 0, NxButton.RIGHT))   // empty: stays
    }

    // One size, the landscape spec, in every orientation; shrunk only so the tile and its caption fit the body's height.
    private val room = 83f   // about the Carousel's caption room at text scale 1: the 16 dp gap, two 18 sp name lines, a 14 sp info line

    @Test fun geometryIsTheSpecWhenTheHeightFits() {
        val g = rowGeometry(RowStyle.CAROUSEL, false, 358f, room)
        assertEquals(340f, g.itemW); assertEquals(240f, g.itemH)
        assertEquals(0.62f, g.scale); assertEquals(16f, g.gap); assertEquals(20f, g.top)
        // A portrait-pad body and a landscape one of ample height: the same row (no width, no orientation in it).
        assertEquals(g, rowGeometry(RowStyle.CAROUSEL, false, 330f, room))
        assertEquals(g, rowGeometry(RowStyle.CAROUSEL, false, 700f, room))   // never grows
        // Exactly fitting: ring room, tile and caption fill the body.
        assertEquals(240f, rowGeometry(RowStyle.CAROUSEL, false, GRID_RING_ROOM + 240f + room, room).itemH, 1e-3f)
    }

    @Test fun theFlipCoverGetsAHeightFitTile() {
        // The Flip's cover, landscape: a 399 × 298 body. The width is ignored; the tile and caption fit the 298.
        val g = rowGeometry(RowStyle.CAROUSEL, false, 298f, room)
        val f = (298f - GRID_RING_ROOM - room) / 240f
        assertEquals(240f * f, g.itemH, 0.01f); assertEquals(340f * f, g.itemW, 0.01f); assertEquals(16f * f, g.gap, 0.01f)
        assertEquals(0.62f, g.scale)
        assertTrue(g.itemH in 200f..215f, "cover tile ${g.itemH}")
        assertEquals(298f, GRID_RING_ROOM + g.itemH + room, 0.01f)
        assertEquals(GRID_RING_ROOM, rowBlockTop(298f, g.itemH, room), 0.01f)   // nothing clipped: the block fills the body
    }

    @Test fun aShortBodyOrALargeCaptionScalesTheRowDown() {
        val short = rowGeometry(RowStyle.CAROUSEL, false, 200f, room)
        assertEquals(200f - GRID_RING_ROOM - room, short.itemH, 0.01f)
        assertEquals(short.itemH * 340f / 240f, short.itemW, 0.01f)                    // the same shape
        val big = rowGeometry(RowStyle.CAROUSEL, false, 298f, 1.6f * room)             // a larger font scale
        assertTrue(GRID_RING_ROOM + big.itemH + 1.6f * room <= 298f + 1e-3f)
        assertTrue(big.itemH < rowGeometry(RowStyle.CAROUSEL, false, 298f, room).itemH)
        assertEquals(0f, rowGeometry(RowStyle.CAROUSEL, false, 50f, room).itemH)       // no room: nothing, never negative
    }

    @Test fun backdropSlots() {
        val box = rowGeometry(RowStyle.BACKDROP, logoSlot = false, 358f)
        assertEquals(170f, box.itemW); assertEquals(150f, box.itemH); assertEquals(28f, box.gap); assertEquals(92f, box.top)
        val logo = rowGeometry(RowStyle.BACKDROP, logoSlot = true, 298f)   // the Flip cover: the full spec, the selection at 0.8
        assertEquals(264f, logo.itemW, 0.01f); assertEquals(104f, logo.itemH, 0.01f); assertEquals(28f, logo.gap); assertEquals(92f, logo.top)
        val short = rowGeometry(RowStyle.BACKDROP, logoSlot = true, 100f)
        assertEquals(100f - GRID_RING_ROOM, short.itemH, 0.01f); assertEquals(330f * short.itemH / 130f, short.itemW, 0.01f)
    }

    @Test fun carouselNeighboursShrinkDarkenAndFadeOutBeyondThree() {
        val g = rowGeometry(RowStyle.CAROUSEL, false, 358f)
        val centre = rowItem(RowStyle.CAROUSEL, 0f, g)
        assertEquals(0f, centre.dx); assertEquals(1f, centre.scale); assertEquals(0f, centre.dim); assertTrue(centre.visible)
        val one = rowItem(RowStyle.CAROUSEL, 1f, g)
        assertEquals(0.62f, one.scale, 0.001f)
        assertEquals(g.itemW / 2 + g.gap + g.itemW * g.scale / 2, one.dx, 0.01f)
        assertEquals(0.60f, one.dim, 0.001f)                                      // 0.45 + 0.15
        val two = rowItem(RowStyle.CAROUSEL, -2f, g)
        assertEquals(-(one.dx + g.itemW * g.scale + g.gap), two.dx, 0.01f)
        assertEquals(0.75f, two.dim, 0.001f)
        assertEquals(0.85f, rowItem(RowStyle.CAROUSEL, 3f, g).dim, 0.001f)        // capped
        assertEquals(1f, rowItem(RowStyle.CAROUSEL, 3f, g).alpha)
        val fading = rowItem(RowStyle.CAROUSEL, 3.5f, g)                          // three to four: fades out, no pop
        assertTrue(fading.visible); assertEquals(0.5f, fading.alpha, 0.001f); assertEquals(0.85f, fading.dim, 0.001f)
        assertFalse(rowItem(RowStyle.CAROUSEL, 4f, g).visible)
        val half = rowItem(RowStyle.CAROUSEL, 0.5f, g)                            // mid-slide: halfway everything
        assertEquals(0.81f, half.scale, 0.001f)
        assertEquals(one.dx / 2, half.dx, 0.01f)
    }

    @Test fun backdropOpacitySteps() {
        val g = rowGeometry(RowStyle.BACKDROP, false, 358f)
        assertEquals(1f, rowItem(RowStyle.BACKDROP, 0f, g).alpha)
        assertEquals(0.5f, rowItem(RowStyle.BACKDROP, 1f, g).alpha, 0.001f)
        assertEquals(0.38f, rowItem(RowStyle.BACKDROP, -2f, g).alpha, 0.001f)
        assertEquals(0.26f, rowItem(RowStyle.BACKDROP, 3f, g).alpha, 0.001f)
        assertEquals(0.13f, rowItem(RowStyle.BACKDROP, 3.5f, g).alpha, 0.001f)
        assertFalse(rowItem(RowStyle.BACKDROP, 4f, g).visible)
        assertEquals(0f, rowItem(RowStyle.BACKDROP, 1f, g).dim)                  // Backdrop fades, it doesn't darken
    }

    @Test fun windowIsTheIndicesUnderFourStepsAway() {
        assertEquals(0..3, rowWindow(0f, 10))
        assertEquals(2..8, rowWindow(5f, 10))
        assertEquals(2..9, rowWindow(5.5f, 10))         // mid-slide: 1.5 and 9.5 are out, 2 and 9 in
        assertEquals(6..9, rowWindow(9f, 10))
        assertEquals(0..0, rowWindow(0f, 1))
        assertTrue(rowWindow(0f, 0).isEmpty())
    }

    @Test fun carouselLogoInsetScalesWithTheRowNeverGrows() {
        val land = rowGeometry(RowStyle.CAROUSEL, false, 358f)
        assertEquals(Pair(22f, 30f), rowLogoInset(land))
        assertEquals(Pair(22f, 30f), rowLogoInset(land.copy(itemW = 500f)))   // never grown
        val cover = rowGeometry(RowStyle.CAROUSEL, false, 298f, room)
        val (side, vertical) = rowLogoInset(cover)
        assertEquals(22f * cover.itemW / 340f, side, 0.01f); assertEquals(30f * cover.itemW / 340f, vertical, 0.01f)
    }

    @Test fun rowCentresTheTileAndCaptionRoomBetweenHeaderAndHintBar() {
        val r = GRID_RING_ROOM
        // Block = ring room + 240 tile + 90 caption room, centred in the body 400 (no info band under it).
        val block = r + 240f + 90f
        assertEquals((400f - block) / 2 + r, rowBlockTop(400f, 240f, 90f), 0.001f)
        // The block's middle is the body's middle.
        val top = rowBlockTop(400f, 240f, 90f)
        assertEquals(400f / 2, (top - r + top + 240f + 90f) / 2, 0.001f)
    }

    @Test fun rowTooShortKeepsTheTileUnderTheHeaderWithRingRoom() {
        assertEquals(GRID_RING_ROOM, rowBlockTop(200f, 240f, 90f))
        assertEquals(GRID_RING_ROOM, rowBlockTop(0f, 240f, 90f))
        // Exactly fitting: the ring room above, nothing more.
        assertEquals(GRID_RING_ROOM, rowBlockTop(GRID_RING_ROOM + 330f, 240f, 90f), 0.001f)
    }

    @Test fun carouselToolIconAndNameScaleWithTheTile() {
        // Full size, every orientation: a 72 dp icon and a 20 sp name.
        val full = carouselToolSize(rowGeometry(RowStyle.CAROUSEL, false, 500f))
        assertEquals(72f, full.iconDp, 0.01f); assertEquals(20f, full.nameSp, 0.01f); assertEquals(9f, full.gapDp, 0.01f)
        // The Flip cover's height-fit tile: both shrink with it, in step, the icon still well over 55 dp.
        val cover = rowGeometry(RowStyle.CAROUSEL, false, 298f, room)
        val c = carouselToolSize(cover)
        assertEquals(0.3f * cover.itemH, c.iconDp, 0.01f)
        assertEquals(20f * cover.itemH / 240f, c.nameSp, 0.01f)
        assertTrue(c.iconDp > 55f, "cover icon ${c.iconDp}")
        // The group fits the tile: icon, gap and two name lines (1.15 line height) under its height.
        for ((t, h) in listOf(full to 240f, c to cover.itemH)) assertTrue(t.iconDp + t.gapDp + 2 * 1.15f * t.nameSp < h)
        // Capped so "Achievements" (about 7.4 em) fits a narrow tile's width less its 8 dp sides.
        val narrow = RowGeometry(itemW = 120f, itemH = 240f, scale = 0.62f, gap = 16f, top = 20f)
        val n = carouselToolSize(narrow)
        assertTrue(n.nameSp < 20f)
        assertTrue(n.nameSp * 12 * 0.62f <= 120f - 16f + 0.01f)
    }

    @Test fun gridToolSizeIsUnchanged() {
        assertEquals(ToolTileSize(46f, 13f, 6f), gridToolSize(portrait = false))
        assertEquals(ToolTileSize(38f, 13f, 6f), gridToolSize(portrait = true))
    }

    // Page title hidden: the rows take the title's 44 dp room into their box, sized from the box less the room (the
    // shown title's body), the block centred in the whole box.
    @Test fun aHiddenTitleSizesFromTheShownBodyOnly() {
        val e = 0.01f
        assertEquals(291f, titleRoomSizingBody(291f, 0f), e)        // the title shown: the box itself
        assertEquals(291f, titleRoomSizingBody(291f + 44f, 44f), e)
        assertEquals(0f, titleRoomSizingBody(20f, 44f), e)          // never negative
    }

    @Test fun aHiddenTitleKeepsTheCarouselRowAndCentresItInTheTallerBox() {
        val e = 0.01f
        val room = 44f
        for (body in listOf(242f, 291f, 344f, 423f, 616f)) for (caption in listOf(0f, 60f, 74f)) {
            val shown = carouselRowPlace(body, 0f, caption)
            val raw = rowGeometry(RowStyle.CAROUSEL, false, body, caption)               // the title shown: as before,
            assertEquals(min(raw.itemW, CAROUSEL_MAX_W), shown.g.itemW, e)              // held to the Carousel's cap
            assertEquals(raw.itemW / raw.itemH, shown.g.itemW / shown.g.itemH, 1e-4f)
            assertEquals(rowBlockTop(body, shown.g.itemH, caption), shown.top, e)
            val box = body + room
            val hidden = carouselRowPlace(box, room, caption)
            assertEquals(shown.g, hidden.g)                                  // the same tiles, neighbours and gap
            val block = GRID_RING_ROOM + hidden.g.itemH + caption            // the ring room, the tile, the caption room
            val above = hidden.top - GRID_RING_ROOM
            assertEquals(above, box - (above + block), e)                    // as much above as below, in the whole box
            assertEquals(room + shown.top - room / 2, hidden.top, e)         // half the room higher than under the spacer
            assertTrue(above >= -e)                                          // the ring inside the box's top
        }
    }

    @Test fun aHiddenTitleKeepsTheBackdropRowAndCentresItInTheTallerBox() {
        val e = 0.01f
        val room = 44f
        for (slot in BackdropSlot.entries) for (body in listOf(291f, 344f, 423f, 616f)) for (captionH in listOf(58f, 66f)) {
            val captioned = slot == BackdropSlot.BOX
            val shown = backdropRowPlace(slot, captioned, body, 0f, captionH)
            val box = body + room
            val hidden = backdropRowPlace(slot, captioned, box, room, captionH)
            assertEquals(shown.g, hidden.g)                                  // the same items, neighbours and gap
            assertEquals(shown.rowH, hidden.rowH, e)
            assertEquals(shown.f, hidden.f, e)
            val gap = if (captioned) BACKDROP_ROW_CAPTION_GAP * hidden.f else 0f
            val block = hidden.rowH + GRID_RING_ROOM + gap + (if (captioned) captionH else 0f)
            val p = hidden.place
            assertEquals(p.rowTop, box - (p.rowTop + block), e)              // centred in the whole box
            assertEquals(room + shown.place.rowTop - room / 2, p.rowTop, e)  // half the room higher than under the spacer
            assertEquals(room + shown.place.captionTop - room / 2, p.captionTop, e)   // the caption with it
            assertEquals(p.captionTop - p.rowTop, shown.place.captionTop - shown.place.rowTop, e)
            assertTrue(p.rowTop >= GRID_RING_ROOM - e)                      // the shadow room inside the box's top
        }
    }

    // Game-list rows placed by what is drawn: a neighbour centred alone in the area, the selection and its
    // caption as drawn centred together, a tile between the two by its distance, with the scale's weight.
    @Test fun aNeighbourIsCentredByItself() {
        val e = 0.01f
        for (area in listOf(291f, 335f, 616f)) for (itemH in listOf(120f, 203f, 240f)) {
            val top = rowNeighbourTop(area, itemH)
            assertEquals(top, area - (top + itemH), e)
        }
    }

    @Test fun theSelectionAndItsDrawnCaptionAreCentredTogether() {
        val e = 0.01f
        val gap = 16f
        for (area in listOf(335f, 420f, 616f)) for (itemH in listOf(120f, 203f)) for (drawn in listOf(24f, 43f, 67f)) {
            val top = rowSelectedTop(area, itemH, gap, drawn)
            assertEquals(top, area - (top + itemH + gap + drawn), e)          // as much above as below the block
            assertEquals((gap + drawn) / 2, rowNeighbourTop(area, itemH) - top, e)   // half the caption above the neighbours
        }
        assertEquals(rowNeighbourTop(335f, 203f), rowSelectedTop(335f, 203f, 16f, 0f), e)   // no caption: as a neighbour
        assertEquals(GRID_RING_ROOM, rowSelectedTop(250f, 203f, 16f, 67f), e)               // too short: the ring's room kept
    }

    @Test fun aSecondNameLineMovesTheSelectionUpHalfALine() {
        val e = 0.01f
        val line = 24.5f
        val info = 18.5f
        for (area in listOf(335f, 616f)) {
            val one = rowSelectedTop(area, 203f, 16f, line + info)
            val two = rowSelectedTop(area, 203f, 16f, 2 * line + info)
            assertEquals(line / 2, one - two, e)
        }
    }

    @Test fun aTileGlidesBetweenTheTwoTopsWithTheSlide() {
        val e = 0.01f
        val nb = 66f
        val sel = 34f
        assertEquals(sel, rowItemTop(nb, sel, 0f), e)       // progress 1: selected
        assertEquals(50f, rowItemTop(nb, sel, 0.5f), e)     // half way
        assertEquals(50f, rowItemTop(nb, sel, -0.5f), e)
        assertEquals(nb, rowItemTop(nb, sel, 1f), e)        // progress 0: a neighbour
        assertEquals(nb, rowItemTop(nb, sel, -2.5f), e)
        // The same weight as the scale's: the top and the scale are at the same fraction between their two ends.
        val g = rowGeometry(RowStyle.CAROUSEL, false, 291f, room)
        for (d in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val scaleW = (1f - rowItem(RowStyle.CAROUSEL, d, g).scale) / (1f - g.scale)
            assertEquals(scaleW, (rowItemTop(nb, sel, d) - sel) / (nb - sel), e)
        }
    }

    // Layouts → Extra info hidden: the selected game's info is empty, so the Carousel's and Backdrop's
    // captions have no info rows (none fitted, none measured): the drawn caption is the name alone, and the selected
    // tile sits half the info lines lower than with them, centred on what is drawn. Home's captions (Continue, a pin)
    // likewise keep no info room, so a name may take the second line.
    @Test fun noExtraInfoLeavesTheCaptionTheNameAlone() {
        val info = listOf(InfoLine(InfoKind.TIME, "Today - 1h"), InfoLine(InfoKind.ACHIEVEMENTS, "3 of 10"), InfoLine(InfoKind.NEXT, "Next: First"))
        // The time and trophies, then Next; an empty row left out.
        assertEquals(listOf(info.take(2), info.drop(2)), carouselCaptionRows("Name", info))
        assertEquals(listOf(info.take(1)), carouselCaptionRows("Name", info.take(1)))
        assertEquals(listOf(info.drop(2)), carouselCaptionRows("Name", info.drop(2)))
        assertEquals(emptyList(), carouselCaptionRows("Name", emptyList()))
        assertEquals(emptyList(), carouselCaptionRows(null, info))
        assertEquals(2, backdropCaptionRows(info).size)
        assertEquals(emptyList(), backdropCaptionRows(emptyList()))
        assertEquals(emptyList(), fitCaptionRows(emptyList(), emptyList(), 300f, spareLines = 3))
        val e = 0.01f
        val name = 21f; val infoLine = 18f
        val withInfo = rowSelectedTop(335f, 150f, 16f, name + infoLine)
        val nameOnly = rowSelectedTop(335f, 150f, 16f, name)
        assertEquals(withInfo + infoLine / 2, nameOnly, e)
        // Home: a 114 dp portrait pin's caption (17 dp lines, 8.5 bottom) holds 2 name lines with no info line too.
        assertEquals(2, homeCaptionTitleLines(114f, 17f, 8.5f))
    }

    // As the handheld's Row_itemDxVar: Tools and Collections take their content's width, one gap between edges.
    @Test fun contentSizedItemsKeepOneGapBetweenEdges() {
        val g = RowGeometry(itemW = 150f, itemH = 160f, scale = 0.5f, gap = 28f, top = 0f)
        val w = floatArrayOf(60f, 150f, 100f, 80f)
        // At rest on 1: centred; the next item's left edge one gap past the selection's right edge.
        assertEquals(0f, rowItemDxVar(g, 1, 1f, w), 0.001f)
        assertEquals(75f + 28f, rowItemDxVar(g, 2, 1f, w) - 100f * 0.5f / 2, 0.001f)
        assertEquals(75f + 28f + 50f + 28f, rowItemDxVar(g, 3, 1f, w) - 80f * 0.5f / 2, 0.001f)
        assertEquals(-(75f + 28f), rowItemDxVar(g, 0, 1f, w) + 60f * 0.5f / 2, 0.001f)
        // Halfway between two rests: linear.
        assertEquals((rowItemDxVar(g, 2, 1f, w) + rowItemDxVar(g, 2, 2f, w)) / 2, rowItemDxVar(g, 2, 1.5f, w), 0.001f)
        // Equal widths: rowItem's offsets exactly, at rest and between.
        val same = FloatArray(6) { 150f }
        for (pos in listOf(0f, 1f, 2.3f, 4.5f)) for (i in 0 until 6) {
            assertEquals(rowItem(RowStyle.BACKDROP, i - pos, g).dx, rowItemDxVar(g, i, pos, same), 0.01f, "item $i at $pos")
        }
        // One item follows the position.
        assertEquals(-(60f * 0.5f + 28f) * 0.5f, rowItemDxVar(g, 0, 0.5f, floatArrayOf(60f)), 0.001f)
    }

    // A portrait game list Carousel's selected tile is at most 60% of the width, its shape and gaps scaled
    // with it, so a neighbour shows either side; landscape (no width) and a wide body keep the spec.
    @Test fun aPortraitCarouselTileIsHeldToSixtyPercentOfTheWidth() {
        val spec = carouselRowPlace(500f, 0f, 60f).g
        val flip = carouselRowPlace(500f, 0f, 60f, portraitW = 411f).g
        assertEquals(0.6f * 411f, flip.itemW, 1e-3f)
        assertEquals(spec.itemW / spec.itemH, flip.itemW / flip.itemH, 1e-4f)
        assertEquals(spec.gap * flip.itemW / spec.itemW, flip.gap, 1e-3f)
        // A neighbour's inner edge (at its scale) is inside the screen: its centre less half its width, under half the width.
        val neighbourInner = flip.itemW / 2 + flip.gap
        assertTrue(neighbourInner < 411f / 2 - 40f)
        assertEquals(spec, carouselRowPlace(500f, 0f, 60f, portraitW = 900f).g)
    }

    // A Carousel (Home's and a game list's): the first neighbour at 0.75 of the selection, each further one 0.8 of the
    // one before (0.6, 0.48), held from there; the gaps edge to edge stay the row's.
    @Test fun aCarouselTapersItsNeighboursOutwards() {
        val g = carouselRowPlace(500f, 0f, 60f, portraitW = 411f).g
        val scales = (0..4).map { rowItem(RowStyle.CAROUSEL, it.toFloat(), g).scale }
        listOf(1f, 0.75f, 0.6f, 0.48f, 0.48f).zip(scales).forEach { (want, got) -> assertEquals(want, got, 1e-5f) }
        val mid = rowItem(RowStyle.CAROUSEL, 1.5f, g).scale
        assertTrue(mid < scales[1] && mid > scales[2])                                              // mid-slide between the two
        for (k in 1..3) {
            val inner = rowItem(RowStyle.CAROUSEL, k.toFloat(), g)
            val outer = rowItem(RowStyle.CAROUSEL, k + 1f, g)
            assertEquals(g.gap, (outer.dx - g.itemW * outer.scale / 2) - (inner.dx + g.itemW * inner.scale / 2), 1f)   // within a dp
        }
        // No jump in a sliding tile's size or speed past the first step: a hair either side of each step agrees.
        for (k in listOf(2f, 3f)) {
            val e = 1e-3f
            assertEquals(rowItem(RowStyle.CAROUSEL, k - e, g).scale, rowItem(RowStyle.CAROUSEL, k + e, g).scale, 1e-3f)
            val before = (g.restDx(k) - g.restDx(k - e)) / e
            val after = (g.restDx(k + e) - g.restDx(k)) / e
            assertEquals(before, after, 0.01f * before)
        }
        assertEquals(-rowItem(RowStyle.CAROUSEL, 2f, g).dx, rowItem(RowStyle.CAROUSEL, -2f, g).dx, 1e-4f)   // both sides
    }

    // A Carousel's tile (Home's and a game list's) is never wider than 300 dp, its height in proportion, whatever room it has.
    @Test fun aCarouselTileIsCappedAt300Dp() {
        for ((h, w) in listOf(900f to null, 900f to 2000f, 400f to null)) {
            val g = carouselRowPlace(h, 0f, 30f, portraitW = w).g
            assertTrue(g.itemW <= 300f + 1e-3f, "$h: ${g.itemW}")
            assertEquals(340f / 240f, g.itemW / g.itemH, 1e-4f)
        }
        assertEquals(300f, carouselRowPlace(900f, 0f, 30f).g.itemW, 1e-3f)
        assertEquals(300f * 240f / 340f, carouselRowPlace(900f, 0f, 30f).g.itemH, 1e-3f)
    }

    // A game list Carousel's neighbours centre on the screen's centre line, wherever their box starts; unmeasured, none.
    // A game list's selection and its caption (gap 8 + 40 drawn) centred on the screen's line, 300 dp down its 600 dp box;
    // kept inside the box when the line is near an edge.
    @Test fun rowSelectedTopOnCentresTheTileAndCaptionOnTheLine() {
        assertEquals(300f - (200f + 48f) / 2, rowSelectedTopOn(300f, 600f, 200f, 8f, 40f), 1e-4f)
        assertEquals(600f - 248f, rowSelectedTopOn(560f, 600f, 200f, 8f, 40f), 1e-4f)
    }

    @Test fun screenCentreLineTopCentresTheTileOnTheScreen() {
        // A screen 1000 px tall from y 100 (centre 600), the box from y 250, 2.5 px per dp: the centre 140 dp down the box,
        // so a 120 dp tile's top at 80. A box starting higher puts the tile lower in it.
        assertEquals(80f, screenCentreLineTop(600f, 250f, 2.5f, 120f)!!, 1e-4f)
        assertEquals(130f, screenCentreLineTop(600f, 150f, 2.5f, 100f)!!, 1e-4f)
        assertEquals(null, screenCentreLineTop(null, 150f, 2.5f, 100f))
        assertEquals(null, screenCentreLineTop(600f, null, 2.5f, 100f))
    }

    // A game list Backdrop's box art slot is never wider than 30% of the body, row or stack, any orientation.
    @Test fun backdropBoxArtIsHeldToThirtyPercentOfTheWidth() {
        for ((w, h) in listOf(411f to 620f, 360f to 500f, 900f to 330f, 1200f to 700f)) {
            val row = backdropRowPlace(BackdropSlot.BOX, true, h, 0f, 60f, bodyW = w).g
            assertTrue(row.itemW <= 0.3f * w + 1e-3f, "row $w x $h: ${row.itemW}")
            assertTrue(row.itemH <= row.itemW / PLACEHOLDER_ASPECT + 1e-3f)   // a portrait case that wide fits
            for (portrait in listOf(true, false)) {
                val stack = backdropStackGeometry(w, h, w, h, portrait, if (portrait) 16f else 24f)
                assertTrue(stack.slotW <= 0.3f * w + 1f, "stack $w x $h: ${stack.slotW}")   // rounded to whole dp
            }
        }
        // No width (as before): the band as grown; a body wide enough keeps it.
        val free = backdropRowPlace(BackdropSlot.BOX, true, 620f, 0f, 60f).g
        assertEquals(free, backdropRowPlace(BackdropSlot.BOX, true, 620f, 0f, 60f, bodyW = 10_000f).g)
    }
}
