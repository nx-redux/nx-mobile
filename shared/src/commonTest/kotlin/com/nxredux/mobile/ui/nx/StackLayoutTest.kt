package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.settings.VerticalListAlignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StackLayoutTest {
    private fun g(h: Float = 100f, s: Float = 0.5f, gap: Float = 16f, cap: Float = 26f) =
        StackGeometry(330f, h, s, gap, cap, 480f, 159f, captionX = 0f, captionRight = 0f)

    @Test fun stepsAreTheRowsStoodOnEndPlusTheCapBelow() {
        val e = 1e-3f
        assertEquals(117f, stackItem(RowStyle.BACKDROP, 1f, g()).dx, e)     // 50 + 16 + 25, + cap 26
        assertEquals(-91f, stackItem(RowStyle.BACKDROP, -1f, g()).dx, e)    // above: no cap
        assertEquals(183f, stackItem(RowStyle.BACKDROP, 2f, g()).dx, e)     // 91 + 66, + 26
        assertEquals(-157f, stackItem(RowStyle.BACKDROP, -2f, g()).dx, e)
        assertEquals(58.5f, stackItem(RowStyle.BACKDROP, 0.5f, g()).dx, e)  // 45.5 + 13: the cap moves with pos
        assertEquals(0.75f, stackItem(RowStyle.BACKDROP, 0.5f, g()).scale, e)
        assertEquals(0.5f, stackItem(RowStyle.BACKDROP, 3f, g()).scale, e)
    }

    @Test fun fadesAreTheRowsOwn() {
        val e = 1e-3f
        assertEquals(0.5f, stackItem(RowStyle.BACKDROP, 1f, g()).alpha, e)
        assertEquals(0.38f, stackItem(RowStyle.BACKDROP, 2f, g()).alpha, e)
        assertEquals(0.26f, stackItem(RowStyle.BACKDROP, 3f, g()).alpha, e)
        assertEquals(0.13f, stackItem(RowStyle.BACKDROP, 3.5f, g()).alpha, e)
        assertTrue(stackItem(RowStyle.BACKDROP, 3.9f, g()).visible); assertFalse(stackItem(RowStyle.BACKDROP, 4f, g()).visible)
        assertEquals(0.6f, stackItem(RowStyle.CAROUSEL, 1f, g()).dim, e)
        assertEquals(0.75f, stackItem(RowStyle.CAROUSEL, 2f, g()).dim, e)
        assertEquals(0.85f, stackItem(RowStyle.CAROUSEL, 3f, g()).dim, e)
        assertEquals(1f, stackItem(RowStyle.CAROUSEL, 3f, g()).alpha, e)
    }

    @Test fun stackStepWrapsOnAFreshPressAndStopsWhenHeld() {
        assertEquals(2, stackStep(0, 3, NxButton.UP, fresh = true)); assertEquals(0, stackStep(0, 3, NxButton.UP, fresh = false))
        assertEquals(0, stackStep(2, 3, NxButton.DOWN, fresh = true)); assertEquals(2, stackStep(2, 3, NxButton.DOWN, fresh = false))
        assertEquals(1, stackStep(2, 3, NxButton.UP, fresh = false)); assertEquals(1, stackStep(0, 3, NxButton.DOWN, fresh = false))
        assertEquals(0, stackStep(0, 1, NxButton.UP, fresh = true)); assertEquals(0, stackStep(0, 1, NxButton.DOWN, fresh = true))
        assertEquals(1, stackStep(1, 3, NxButton.LEFT, fresh = true)); assertEquals(1, stackStep(1, 3, NxButton.RIGHT, fresh = true))
        assertEquals(0, stackStep(0, 0, NxButton.DOWN, fresh = true))
        assertNull(stackStep(0, 0, NxButton.UP, fresh = true))   // an empty stack is all top: UP finds nothing (the press stays)
    }

    @Test fun mainMenuSlots() {
        val logo = mainMenuStackGeometry(BackdropSlot.LOGO, 960f, 344f, portrait = false, gutter = 24f)
        // The horizontal Carousel's logo slot (264 × 104 landscape).
        assertEquals(listOf(264f, 104f, 0.5f, 16f, 26f, 480f, 159f), listOf(logo.slotW, logo.slotH, logo.scale, logo.gap, logo.cap, logo.centreX, logo.centreY))
        val logoP = mainMenuStackGeometry(BackdropSlot.LOGO, 475f, 291f, portrait = true, gutter = 16f)
        val rowP = backdropSlotGeometry(BackdropSlot.LOGO, 291f, portraitW = 475f)   // portrait: the pad held to 60% of the width
        assertEquals(rowP.itemW to rowP.itemH, logoP.slotW to logoP.slotH); assertEquals(132.5f, logoP.centreY)
        val coll = mainMenuStackGeometry(BackdropSlot.COLLECTION, 960f, 344f, false, 24f)
        assertEquals(listOf(400f, 100f, 0.5f, 14f, 0f, 172f), listOf(coll.slotW, coll.slotH, coll.scale, coll.gap, coll.cap, coll.centreY))
        assertEquals(328f, mainMenuStackGeometry(BackdropSlot.COLLECTION, 360f, 423f, true, 16f).slotW)   // the Flip: 360 − 32
        val tool = mainMenuStackGeometry(BackdropSlot.TOOL, 960f, 344f, false, 24f)
        assertEquals(listOf(150f, 140f, 0.6f, 10f, 0f), listOf(tool.slotW, tool.slotH, tool.scale, tool.gap, tool.cap))
    }

    @Test fun mainMenuSlotsNeverOverflowANarrowBody() {
        assertEquals(232f, mainMenuStackGeometry(BackdropSlot.LOGO, 280f, 400f, portrait = false, gutter = 24f).slotW)   // 280 − 48
        assertTrue(mainMenuStackGeometry(BackdropSlot.LOGO, 280f, 400f, portrait = true, gutter = 24f).slotW <= 280f - 48f)   // portrait: the 60% cap is narrower
        assertEquals(122f, mainMenuStackGeometry(BackdropSlot.TOOL, 154f, 400f, portrait = true, gutter = 16f).slotW)
    }

    // The mockup's game-list Backdrop and Carousel vertical tables. Fold cover and Flip: pad (portrait = true,
    // gutter 16); large portrait 704 × 845: no pad (portrait = false, gutter 24), taller than wide → portrait family.
    private fun row(g: StackGeometry) = listOf(g.slotW, g.slotH, g.centreX, g.centreY, g.captionX, g.captionRight)

    @Test fun backdropStackEveryScreen() {
        // Landscape family: the same margin L = max(gutter, round(0.1·W)) left of the stack and right of the caption.
        assertEquals(listOf(150f, 200f, 171f, 172f, 278f, 96f), row(backdropStackGeometry(960f, 344f, 960f, 432f, false, 24f)))
        assertEquals(listOf(108f, 144f, 138f, 124f, 224f, 84f), row(backdropStackGeometry(840f, 248f, 840f, 336f, false, 24f)))
        assertEquals(listOf(240f, 320f, 213f, 308f, 365f, 93f), row(backdropStackGeometry(933f, 616f, 933f, 704f, false, 24f)))
        assertEquals(listOf(135f, 180f, 114.5f, 145.5f, 198f, 16f), row(backdropStackGeometry(475f, 291f, 475f, 379f, true, 16f)))   // Fold cover
        // The box no wider than 30% of the body (Flip 108 of 360, large portrait 212 of 704, rounded).
        assertEquals(listOf(108f, 144f, 83f, 211.5f, 153f, 16f), row(backdropStackGeometry(360f, 423f, 360f, 511f, true, 16f)))      // Flip: 30%-capped
        assertEquals(listOf(212f, 282f, 169.5f, 422.5f, 300f, 24f), row(backdropStackGeometry(704f, 845f, 704f, 933f, false, 24f)))  // large portrait: 30%-capped
        val a = backdropStackGeometry(960f, 344f, 960f, 432f, false, 24f)
        assertEquals(0f, a.cap); assertEquals(0.5f, a.scale); assertEquals(16f, a.gap)
    }

    @Test fun carouselStackEveryScreen() {
        // Landscape family: the same margin L = max(gutter, round(0.1·W)) left of the stack and right of the caption.
        assertEquals(listOf(268f, 189f, 230f, 172f, 396f, 96f), row(carouselStackGeometry(960f, 344f, 960f, 432f, false, 24f)))
        assertEquals(listOf(193f, 136f, 180.5f, 124f, 309f, 84f), row(carouselStackGeometry(840f, 248f, 840f, 336f, false, 24f)))
        assertEquals(listOf(340f, 240f, 263f, 308f, 465f, 93f), row(carouselStackGeometry(933f, 616f, 933f, 704f, false, 24f)))
        assertEquals(listOf(213f, 146f, 122.5f, 145.5f, 245f, 16f), row(carouselStackGeometry(475f, 291f, 475f, 379f, true, 16f)))   // width-capped
        assertEquals(listOf(150f, 103f, 91f, 211.5f, 182f, 16f), row(carouselStackGeometry(360f, 423f, 360f, 511f, true, 16f)))       // width-capped
        assertEquals(listOf(315f, 222f, 181.5f, 422.5f, 363f, 24f), row(carouselStackGeometry(704f, 845f, 704f, 933f, false, 24f)))  // 340:240 spec
        val land = carouselStackGeometry(960f, 344f, 960f, 432f, false, 24f); assertEquals(0.62f, land.scale); assertEquals(16f, land.gap)
        val flip = carouselStackGeometry(360f, 423f, 360f, 511f, true, 16f); assertEquals(0.6f, flip.scale); assertEquals(12f, flip.gap)
        // neighbour 1 peeks 61.5 of its 117 on 960 × 432: half body 172 − (step − half neighbour)
        val r = stackItem(RowStyle.CAROUSEL, 1f, land)
        assertEquals(61.5f, 172f - (r.dx - land.slotH * land.scale / 2), 0.2f)
    }

    @Test fun landscapeStacksKeepOneMarginBothSides() {
        for (g in listOf(carouselStackGeometry(960f, 344f, 960f, 432f, false, 24f), backdropStackGeometry(960f, 344f, 960f, 432f, false, 24f))) {
            assertEquals(96f, g.centreX - g.slotW / 2)   // round(0.1 · 960)
            assertEquals(96f, g.captionRight)
            assertEquals(g.centreX + g.slotW / 2 + 32f, g.captionX)
        }
        assertEquals(24f, carouselStackGeometry(200f, 200f, 300f, 200f, false, 24f).let { it.centreX - it.slotW / 2 })   // never under the gutter
    }

    // Layouts → Vertical alignment. Left is today's geometry, value for value; Right mirrors it about the body's middle:
    // the stack at W − x, the caption's two insets swapped (its text starts on the margin the stack had).
    private data class Frame(val w: Float, val h: Float, val sw: Float, val sh: Float, val pad: Boolean, val gutter: Float)
    private val alignScreens = listOf(
        Frame(960f, 344f, 960f, 432f, false, 24f),    // landscape
        Frame(840f, 248f, 840f, 336f, false, 24f),
        Frame(933f, 616f, 933f, 704f, false, 24f),
        Frame(475f, 291f, 475f, 379f, true, 16f),     // Fold cover (pad)
        Frame(360f, 423f, 360f, 511f, true, 16f),     // Flip (pad)
        Frame(704f, 845f, 704f, 933f, false, 24f),    // large portrait
    )

    @Test fun leftAlignmentIsTodaysGeometry() {
        for ((w, h, sw, sh, pad, gutter) in alignScreens) {
            assertEquals(carouselStackGeometry(w, h, sw, sh, pad, gutter), carouselStackGeometry(w, h, sw, sh, pad, gutter, VerticalListAlignment.LEFT))
            assertEquals(backdropStackGeometry(w, h, sw, sh, pad, gutter), backdropStackGeometry(w, h, sw, sh, pad, gutter, VerticalListAlignment.LEFT))
        }
    }

    @Test fun rightAlignmentMirrorsTheStackAndTheCaption() {
        val R = VerticalListAlignment.RIGHT
        // Landscape 960 × 432: one margin (96) right of the stack and left of the caption.
        assertEquals(listOf(268f, 189f, 730f, 172f, 96f, 396f), row(carouselStackGeometry(960f, 344f, 960f, 432f, false, 24f, R)))
        fun near(want: List<Float>, got: List<Float>) = want.zip(got).forEach { (a, b) -> assertEquals(a, b, 0.01f, "$want vs $got") }
        near(listOf(150f, 200f, 789f, 172f, 99f, 278f), row(backdropStackGeometry(960f, 344f, 960f, 432f, false, 24f, R)))
        // Portrait family: Fold cover, Flip, large portrait; the Carousel's caption starts on the gutter, the Backdrop's
        // where Left's box is drawn (the 0.72 case: Flip's 15.2 in from the gutter; large portrait's 44 in, both 30%-capped).
        assertEquals(listOf(213f, 146f, 352.5f, 145.5f, 16f, 245f), row(carouselStackGeometry(475f, 291f, 475f, 379f, true, 16f, R)))
        near(listOf(108f, 144f, 277f, 211.5f, 31.16f, 153f), row(backdropStackGeometry(360f, 423f, 360f, 511f, true, 16f, R)))
        near(listOf(212f, 282f, 534.5f, 422.5f, 67.98f, 300f), row(backdropStackGeometry(704f, 845f, 704f, 933f, false, 24f, R)))
        for ((w, h, sw, sh, pad, gutter) in alignScreens) {
            for ((l, r, backdrop) in listOf(
                Triple(carouselStackGeometry(w, h, sw, sh, pad, gutter), carouselStackGeometry(w, h, sw, sh, pad, gutter, R), false),
                Triple(backdropStackGeometry(w, h, sw, sh, pad, gutter), backdropStackGeometry(w, h, sw, sh, pad, gutter, R), true),
            )) {
                assertEquals(w - l.centreX, r.centreX)
                assertEquals(l.captionX, r.captionRight)
                assertEquals(listOf(l.slotW, l.slotH, l.scale, l.gap, l.cap, l.centreY), listOf(r.slotW, r.slotH, r.scale, r.gap, r.cap, r.centreY))
                if (!backdrop) {   // the Backdrop's caption starts on Left's box margin (rightBackdropCaptionStartsOnLeftsBoxMargin)
                    assertEquals(l.captionRight, r.captionX)
                    assertEquals(w - l.captionRight - l.captionX, w - r.captionRight - r.captionX)   // the caption's width
                }
                assertTrue(r.captionX + (w - r.captionRight - r.captionX) <= r.centreX - r.slotW / 2)   // caption left of the stack
            }
        }
    }

    // Right keeps Left's gap between the stack as drawn and the caption: measured from the selection's drawn edge (the
    // Carousel tile's 3 dp ring, the box art none), the widest item (neighbours are scaled down about the same x). The
    // mockup's measured gaps: Carousel 29 / 13 / 21, Backdrop 32 / 16 / 24.5 (landscape / Fold cover, Flip / large portrait).
    @Test fun rightAlignmentKeepsLeftsGapFromTheDrawnStack() {
        val R = VerticalListAlignment.RIGHT
        fun half(g: StackGeometry, room: Float) = maxOf(g.slotW / 2 + room, g.slotW * g.scale / 2)
        fun leftGap(g: StackGeometry, room: Float) = g.captionX - (g.centreX + half(g, room))
        fun rightGap(g: StackGeometry, w: Float, room: Float) = (g.centreX - half(g, room)) - (w - g.captionRight)
        val expected = mapOf(
            960f to (29f to 32f), 840f to (29f to 32f), 933f to (29f to 32f),
            475f to (13f to 16f), 360f to (13f to 16f), 704f to (21f to 24.5f),
        )
        for ((w, h, sw, sh, pad, gutter) in alignScreens) {
            val (carGap, boxGap) = expected.getValue(w)
            for ((pair, room, want) in listOf(
                Triple(carouselStackGeometry(w, h, sw, sh, pad, gutter) to carouselStackGeometry(w, h, sw, sh, pad, gutter, R), NX_TILE_RING, carGap),
                Triple(backdropStackGeometry(w, h, sw, sh, pad, gutter) to backdropStackGeometry(w, h, sw, sh, pad, gutter, R), 0f, boxGap),
            )) {
                val (l, r) = pair
                assertEquals(want, leftGap(l, room), 0.5f, "left gap at $w")
                assertEquals(leftGap(l, room), rightGap(r, w, room), 0.5f, "right gap at $w")
                assertTrue((w - r.captionRight) + leftGap(l, room) <= r.centreX - half(r, room) + 0.01f, "caption ends a gap before the stack at $w")
                if (room > 0f) assertEquals(l.captionRight, r.captionX)   // the Carousel's caption starts on the margin
            }
        }
    }

    // Backdrop Right: the caption starts where Left's selected box is drawn (selected at scale 1: the 0.72 case centred in
    // the 0.75 slot, not the slot's edge), every family; the mockup's measured case edges.
    @Test fun rightBackdropCaptionStartsOnLeftsBoxMargin() {
        val R = VerticalListAlignment.RIGHT
        val expected = mapOf(960f to 99f, 840f to 86.2f, 933f to 97.8f, 475f to 49.7f, 360f to 31.2f, 704f to 68f)   // Flip and large portrait 30%-capped
        for ((w, h, sw, sh, pad, gutter) in alignScreens) {
            val l = backdropStackGeometry(w, h, sw, sh, pad, gutter)
            val r = backdropStackGeometry(w, h, sw, sh, pad, gutter, R)
            val boxLeft = l.centreX - placeholderBoxSize(l.slotW, l.slotH).first * 1f / 2
            assertEquals(expected.getValue(w), boxLeft, 0.5f, "Left's box margin at $w")
            assertEquals(boxLeft, r.captionX, 0.5f, "Right's caption start at $w")
            assertEquals(l.captionX, r.captionRight, "Right's caption end unchanged at $w")
            assertTrue(w - r.captionRight - r.captionX >= 0.4f * (w - l.captionRight - l.captionX), "caption keeps 40% at $w")
        }
    }

    // The stack and its caption sit still for the whole list: the geometry comes from the body alone (no item, no hint
    // bar), each item only moves along y, and each box is drawn centred in this fixed slot.
    @Test fun theStackSitsStillWhateverIsSelected() {
        val g = backdropStackGeometry(960f, 344f, 960f, 432f, false, 24f)
        assertEquals(g, backdropStackGeometry(960f, 344f, 960f, 432f, false, 24f))
        assertEquals(0f, stackItem(RowStyle.BACKDROP, 0f, g).dx)   // whichever is selected sits on the stack's centre
        assertEquals(listOf(171f, 278f, 96f), listOf(g.centreX, g.captionX, g.captionRight))
    }

    @Test fun portraitCaptionColumnIsAtLeast45PercentAndTheItemKeepsAGutter() {
        for ((body, screen, pad) in listOf(
            Triple(475f to 291f, 475f to 379f, true), Triple(360f to 423f, 360f to 511f, true), Triple(704f to 845f, 704f to 933f, false),
        )) {
            val (w, h) = body
            val gutter = if (pad) 16f else 24f
            for (g in listOf(
                backdropStackGeometry(w, h, screen.first, screen.second, pad, gutter),
                carouselStackGeometry(w, h, screen.first, screen.second, pad, gutter),
            )) {
                assertTrue(g.centreX - g.slotW / 2 >= gutter)
                assertTrue(w - g.captionRight - g.captionX >= 0.45f * w - 0.5f)
            }
        }
    }

    @Test fun portraitFamilyIsThePadOrAScreenNotWiderThanTall() {
        assertTrue(stackPortraitFamily(475f, 379f, portrait = true))
        assertTrue(stackPortraitFamily(704f, 933f, portrait = false))
        assertFalse(stackPortraitFamily(960f, 432f, portrait = false))
        assertTrue(stackPortraitFamily(400f, 468f, portrait = false))   // body 400 × 380 is wider than tall; the screen is not
    }

    // Near-square windows: the item never starts off-screen and the caption keeps a usable column (96 dp).
    private fun assertOnScreen(g: StackGeometry, bodyW: Float, gutter: Float) {
        assertTrue(g.centreX - g.slotW / 2 >= gutter, "left edge ${g.centreX - g.slotW / 2}")
        assertTrue(g.centreX + g.slotW / 2 <= g.captionX, "item overlaps the caption")
        assertTrue(bodyW - g.captionRight - g.captionX >= 96f - 0.5f, "caption ${bodyW - g.captionRight - g.captionX}")
    }

    @Test fun nearSquareWindowKeepsTheItemOnScreen() {
        assertOnScreen(carouselStackGeometry(400f, 380f, 400f, 468f, false, 24f), 400f, 24f)
        assertOnScreen(backdropStackGeometry(400f, 380f, 400f, 468f, false, 24f), 400f, 24f)
        // a landscape-family window barely wider than tall: x = 0.32·W would put the 296-wide tile off the left edge
        assertOnScreen(carouselStackGeometry(500f, 380f, 500f, 468f, false, 24f), 500f, 24f)
        assertOnScreen(backdropStackGeometry(500f, 380f, 500f, 468f, false, 24f), 500f, 24f)
        val g = carouselStackGeometry(500f, 380f, 500f, 468f, false, 24f)
        assertEquals(340f / 240f, g.slotW / g.slotH, 0.02f)   // a width cap keeps the tile's aspect
    }

    // A narrow landscape-family body (the Flip's cover, 474 dp wide) keeps the tile the height allows; the caption takes
    // the width left (116 dp).
    @Test fun theCoverKeepsTheTileTheHeightAllows() {
        val g = carouselStackGeometry(474f, 298f, 474f, 430f, false, 24f)
        assertEquals(listOf(232f, 164f, 163f, 149f, 311f, 47f), row(g))
        assertEquals(116f, 474f - g.captionRight - g.captionX)
        val b = backdropStackGeometry(474f, 298f, 474f, 430f, false, 24f)
        assertEquals(listOf(130f, 173f), listOf(b.slotW, b.slotH))   // the box art is not clamped there
    }

    // A body too narrow for the tile and a 96 dp caption shrinks the tile, keeping its aspect.
    @Test fun aTooNarrowBodyKeepsA96DpCaption() {
        val g = carouselStackGeometry(500f, 380f, 500f, 468f, false, 24f)
        assertEquals(listOf(272f, 192f), listOf(g.slotW, g.slotH))
        assertEquals(96f, 500f - g.captionRight - g.captionX)
    }

    // Phones in landscape are never clamped: 800 × 242.
    @Test fun aLandscapePhoneIsNeverClamped() {
        assertEquals(listOf(188f, 133f, 174f, 121f, 300f, 80f), row(carouselStackGeometry(800f, 242f, 800f, 330f, false, 24f)))
    }

    @Test fun roundingIsHalfUpAsTheMockup() { assertEquals(197f, jsRound(196.5f)); assertEquals(103f, jsRound(103.125f)) }

    @Test fun mainMenuSlotContentIsTheSpecs() {
        for (slot in listOf(BackdropSlot.COLLECTION, BackdropSlot.TOOL))
            assertEquals(1f, backdropContentScale(mainMenuStackGeometry(slot, 960f, 344f, false, 24f).asItemRow(), slot, portrait = false))
        // A console's slot is the row's, so its content scales as the row's does (264 of the 330 spec).
        assertEquals(0.8f, backdropContentScale(mainMenuStackGeometry(BackdropSlot.LOGO, 960f, 344f, false, 24f).asItemRow(), BackdropSlot.LOGO, portrait = false), 1e-4f)
    }

    // Portrait, the selected console is shrunk (slot, pad, gaps) so its pad is at most 60% of the width;
    // landscape and a wide portrait body keep the spec. The vertical stack's console is the row's, pad included.
    @Test fun aPortraitConsoleIsHeldToSixtyPercentOfTheWidthInBothDirections() {
        val spec = backdropSlotGeometry(BackdropSlot.LOGO, 423f)
        val flip = backdropSlotGeometry(BackdropSlot.LOGO, 423f, portraitW = 411f)
        assertEquals(0.6f * 411f, rowPadBox(flip.itemH).w, 1e-3f)
        assertEquals(spec.itemW / spec.itemH, flip.itemW / flip.itemH, 1e-4f)   // the whole console, its shape kept
        assertEquals(spec.gap * flip.itemH / spec.itemH, flip.gap, 1e-3f)
        assertEquals(spec, backdropSlotGeometry(BackdropSlot.LOGO, 423f, portraitW = 900f))   // never grown
        val stack = mainMenuStackGeometry(BackdropSlot.LOGO, 411f, 423f, portrait = true, gutter = 16f)
        assertEquals(flip.itemW to flip.itemH, stack.slotW to stack.slotH)
        val r = StackReach(30f, 30f)
        assertEquals(rowPadBox(flip.itemH).w to rowPadBox(flip.itemH).h, stackPadBox(stack.slotW, stack.slotH, r).let { it.w to it.h })
    }

    // Every console's footprint is its slot plus its count (8 dp under the slot and the count's line, both as drawn), and
    // the gap from one footprint's bottom to the next one's top is the same for every neighbour, selected or not.
    @Test fun consoleGapsAreEvenCountsIncluded() {
        val e = 1e-3f
        for ((portrait, line) in listOf(false to 18f, true to 18f, true to 22f, false to 15f)) {
            val g = mainMenuStackGeometry(BackdropSlot.LOGO, 960f, 344f, portrait, 24f, countLine = line)
            fun top(d: Int): Float { val r = stackItem(RowStyle.BACKDROP, d.toFloat(), g); return r.dx - g.slotH * r.scale / 2 }
            fun bottom(d: Int): Float {
                val r = stackItem(RowStyle.BACKDROP, d.toFloat(), g)
                return r.dx + g.slotH * r.scale / 2 + countRoom(r.scale, line)
            }
            for (d in 0..2) assertEquals(g.gap, top(d + 1) - bottom(d), e, "below $d → ${d + 1}")
            for (d in 0 downTo -2) assertEquals(g.gap, top(d) - bottom(d - 1), e, "above $d → ${d - 1}")
            assertTrue(g.gap >= 10f)
            // the selected logo and its count are centred as one block
            assertEquals(344f / 2, g.centreY + (top(0) + bottom(0)) / 2, e)
        }
        // a side count: 8·0.5 + 18·(11/14) on screen (the 11 sp minimum)
        assertEquals(4f + 18f * 11f / 14f, countRoom(0.5f, 18f), e)
        assertEquals(26f, countRoom(1f, 18f), e)
        assertEquals(0f, mainMenuStackGeometry(BackdropSlot.COLLECTION, 960f, 344f, false, 24f).sideCap)
        assertEquals(0f, mainMenuStackGeometry(BackdropSlot.TOOL, 960f, 344f, false, 24f).sideCap)
    }

    // Placed by what they draw: logos of any drawn height (a wide one short in its slot, a tall one filling it) and
    // their counts; the gap from one count to the next logo is the same everywhere, whichever is selected.
    @Test fun consolesPlacedByWhatTheyDrawHaveEvenGaps() {
        val e = 1e-3f
        val line = 18f
        val g = mainMenuStackGeometry(BackdropSlot.LOGO, 360f, 423f, portrait = true, gutter = 16f, countLine = line)
        val reaches = listOf(40f, 33f, 70f, 80f, 55f, 20f, 80f).map { logoReach(it, line) } + StackReach(40f, 40f)   // the last: a name, no count
        val r = { i: Int -> reaches[i] }
        for (k in reaches.indices) {
            fun s(j: Int) = if (j == k) 1f else g.scale
            fun top(j: Int) = stackCentreAt(j, k, g, r) - reaches[j].upAt(s(j))
            fun bottom(j: Int) = stackCentreAt(j, k, g, r) + reaches[j].downAt(s(j))
            for (j in (k - 3).coerceAtLeast(0) until (k + 3).coerceAtMost(reaches.lastIndex))
                assertEquals(g.gap, top(j + 1) - bottom(j), e, "selected $k: $j → ${j + 1}")
            assertEquals(0f, (top(k) + bottom(k)) / 2, e)   // the selected logo and its count centred as one block
            // settled: the placed offset is the selection's layout, the selected block on the body's centre
            for (j in reaches.indices) assertEquals(g.cap / 2 + stackCentreAt(j, k, g, r), stackReachDy(j, k.toFloat(), reaches.size, g, r), e)
        }
        // mid-slide it is between the two layouts
        val mid = stackReachDy(2, 1.5f, reaches.size, g, r) - g.cap / 2
        assertEquals((stackCentreAt(2, 1, g, r) + stackCentreAt(2, 2, g, r)) / 2, mid, e)
    }

    @Test fun asRowSwipesOnTheSlotHeight() {
        assertEquals(91f, rowSwipeStep(g().asRow()), 1e-3f)
        assertEquals(66f, g().asRow().let { it.restDx(2f) - it.restDx(1f) }, 1e-3f)
    }

    @Test fun aSelectedPadPushesTheNeighboursOutButKeepsTheBlockCentred() {
        val g = mainMenuStackGeometry(BackdropSlot.LOGO, 960f, 344f, portrait = false, gutter = 24f)
        val plain = StackReach(30f, 30f, countLine = 18f)
        val padded = plain.copy(padUp = 81f, padDown = 107f)
        val reaches = { pad: Boolean -> { i: Int -> if (pad && i == 1) padded else plain } }
        // Item 1 selected: its own place is the same with or without its pad (the logo and count stay centred) ...
        assertEquals(stackCentreAt(1, 1, g, reaches(false)), stackCentreAt(1, 1, g, reaches(true)), 1e-4f)
        // ... while the items either side stand off the pad: 81 above instead of 30, 107 below instead of the count's reach.
        assertEquals(stackCentreAt(0, 1, g, reaches(false)) - (81f - plain.upAt(1f)), stackCentreAt(0, 1, g, reaches(true)), 1e-4f)
        assertEquals(stackCentreAt(2, 1, g, reaches(false)) + (107f - plain.downAt(1f)), stackCentreAt(2, 1, g, reaches(true)), 1e-4f)
    }

    @Test fun aPadOnlyCountsWhileSelected() {
        val g = mainMenuStackGeometry(BackdropSlot.LOGO, 960f, 344f, portrait = false, gutter = 24f)
        val plain = StackReach(30f, 30f, countLine = 18f)
        val padded = plain.copy(padUp = 81f, padDown = 107f)
        // Item 0 selected; item 1 has a pad but is a side item: the spacing is as if it had none.
        assertEquals(stackCentreAt(2, 0, g) { plain }, stackCentreAt(2, 0, g) { if (it == 1) padded else plain }, 1e-4f)
    }

    @Test fun slidingFromAPaddedConsoleToAPlainOneEases() {
        val g = mainMenuStackGeometry(BackdropSlot.LOGO, 960f, 344f, portrait = false, gutter = 24f)
        val plain = StackReach(30f, 30f, countLine = 18f)
        val reach = { i: Int -> if (i == 0) plain.copy(padUp = 81f, padDown = 107f) else plain }
        val start = stackReachDy(1, 0f, 3, g, reach)
        val mid = stackReachDy(1, 0.5f, 3, g, reach)
        val end = stackReachDy(1, 1f, 3, g, reach)
        assertEquals((start + end) / 2, mid, 1e-3f)   // linear between the two selections: no jump at the half
    }

    // Page title hidden: a stack takes the title's 44 dp room into its box. Sized from the box less the room (the shown
    // title's body), re-centred in the whole box: only centreY moves, the selection and its caption room centred as one
    // block in the taller box.
    @Test fun aHiddenTitleKeepsTheStacksAndCentresThemInTheTallerBox() {
        val e = 0.01f
        val room = 44f
        fun check(shown: StackGeometry, hidden: StackGeometry, body: Float) {
            val box = body + room
            assertEquals(shown, shown.centredIn(body))                               // the title shown: unchanged
            assertEquals(shown.copy(centreY = 0f), hidden.copy(centreY = 0f))       // slots, scale, gaps, caption column
            assertEquals(shown.asRow(), hidden.asRow())                              // so the swipe and the steps too
            assertEquals(room + shown.centreY - room / 2, hidden.centreY, e)        // half the room higher
            val top = hidden.centreY - hidden.slotH / 2
            val bottom = hidden.centreY + hidden.slotH / 2 + hidden.cap
            assertEquals(top, box - bottom, e)                                       // centred in the whole box
            assertTrue(top >= -e)
        }
        for (body in listOf(291f, 344f, 423f, 616f)) {
            val box = body + room
            for (slot in listOf(BackdropSlot.LOGO, BackdropSlot.COLLECTION, BackdropSlot.TOOL)) for (portrait in listOf(true, false)) {
                val w = if (portrait) 475f else 933f
                check(
                    mainMenuStackGeometry(slot, w, body, portrait, 16f),
                    mainMenuStackGeometry(slot, w, titleRoomSizingBody(box, room), portrait, 16f).centredIn(box), body,
                )
            }
            for (alignment in VerticalListAlignment.entries) for ((win, portrait) in listOf((475f to 751f) to true, (960f to 432f) to false)) {
                val (sw, sh) = win
                check(
                    carouselStackGeometry(sw, body, sw, sh, portrait, 16f, alignment),
                    carouselStackGeometry(sw, titleRoomSizingBody(box, room), sw, sh, portrait, 16f, alignment).centredIn(box), body,
                )
                check(
                    backdropStackGeometry(sw, body, sw, sh, portrait, 16f, alignment),
                    backdropStackGeometry(sw, titleRoomSizingBody(box, room), sw, sh, portrait, 16f, alignment).centredIn(box), body,
                )
            }
        }
    }

    // LEFT / RIGHT page a game list's stack by the items it shows: the selection and its neighbours whose centre is in the body.
    @Test fun a_stacks_page_is_the_items_shown_in_its_body() {
        for (bodyH in listOf(344f, 616f, 900f)) for (style in listOf(RowStyle.CAROUSEL, RowStyle.BACKDROP)) {
            val g = carouselStackGeometry(960f, bodyH, 960f, bodyH + 88f, false, 24f).centredIn(bodyH)
            val n = stackPageItems(style, g, bodyH)
            assertTrue(n in 1..7, "$style $bodyH: $n")
            val inside = (-3..3).count { d -> (g.centreY + stackItem(style, d.toFloat(), g).dx) in 0f..bodyH }
            assertEquals(inside, n, "$style $bodyH")
        }
        val tall = carouselStackGeometry(960f, 900f, 960f, 988f, false, 24f).centredIn(900f)
        assertTrue(stackPageItems(RowStyle.CAROUSEL, tall, 900f) >= stackPageItems(RowStyle.CAROUSEL, tall, 300f))
    }
}
