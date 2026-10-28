package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Carousel and List Home space their strip's text evenly from the tab underline. */
class HomeStripSpacingTest {
    private fun near(expected: Float, actual: Float, what: String = "") = assertEquals(expected, actual, 0.01f, what)

    // The strip, the carousel and the dock are one group: the card (centred in its box, as the carousel draws it) 16 dp
    // from the strip's text and from the dock, as much room above the group (from the underline) as below it, the box
    // stopping at the card's full size.
    @Test fun carouselRowsAreOneCentredGroup() {
        val above = 6f
        val max = homeCarouselMaxH(400f, portrait = true)
        val p = homeCarouselPlace(bodyH = 700f, above = above, stripInkH = 30f, dockH = 42f, maxBoxH = max)
        val itemH = max - GRID_RING_ROOM - NX_TILE_RING
        val cardTop = p.boxTop + rowSelectedTop(p.boxH, itemH, 0f, 0f)
        val cardBottom = cardTop + itemH
        near(16f, cardTop - (p.stripInkTop + 30f), "strip to card")
        near(16f, p.dockTop - cardBottom, "card to dock")
        near(p.stripInkTop + above, 700f - (p.dockTop + 42f), "as much room above the group as below")
        near(max, p.boxH, "the card stops at its full size")
    }

    @Test fun aShortBodyShrinksTheCardKeepingTheLeastGap() {
        val p = homeCarouselPlace(bodyH = 200f, above = 0f, stripInkH = 30f, dockH = 42f)
        near(12f, p.stripInkTop)
        val room = (GRID_RING_ROOM + NX_TILE_RING) / 2
        near(200f - 30f - 42f - 2 * (16f - room) - 2 * 12f, p.boxH)
        near(200f - 12f - 42f, p.dockTop)
    }

    @Test fun noStripNoDockLeavesTwoGaps() {
        val p = homeCarouselPlace(bodyH = 200f, above = 0f, stripInkH = 0f, dockH = 0f, fill = true)
        near(12f, p.boxTop)
        near(200f - 24f, p.boxH)
    }

    // No strip (Home stats hidden, or in the header): the carousel and the dock alone are the group.
    @Test fun noStripCentresTheCarouselAndTheDock() {
        val above = 6f
        val p = homeCarouselPlace(bodyH = 400f, above = above, stripInkH = 0f, dockH = 60f)
        near(16f - (GRID_RING_ROOM + NX_TILE_RING) / 2, p.dockTop - (p.boxTop + p.boxH), "box to dock")
        near(p.boxTop + above, 400f - (p.dockTop + 60f), "as much room above the group as below")
    }

    // The box's full size follows the card: the 300 dp cap's height, smaller in portrait on a narrow body (60% wide).
    @Test fun theBoxFollowsTheCardsSize() {
        val ring = GRID_RING_ROOM + NX_TILE_RING
        near(300f * 240f / 340f + ring, homeCarouselMaxH(1200f, portrait = false))
        near(0.6f * 360f * 240f / 340f + ring, homeCarouselMaxH(360f, portrait = true))
    }

    @Test fun listStripCentresBetweenTheUnderlineAndTheFirstPill() {
        val top = homeListStripInkTop(above = 6f, inkH = 30f, firstPill = 70f)
        near(top + 6f, 70f - (top + 30f))
        near(12f - 6f, homeListStripInkTop(above = 6f, inkH = 30f, firstPill = null), "before the list reports, the least gap")
        near(12f - 6f, homeListStripInkTop(above = 6f, inkH = 30f, firstPill = 20f), "never under the least gap")
    }

    @Test fun stripInkSpansItsLines() {
        val f = HomeFrame(400f, 600f, portrait = true)
        val (capTop, one) = homeStripInk(f, 1)
        val (_, two) = homeStripInk(f, 2)
        assertTrue(capTop > 0f && capTop < homeStripLineH(f))
        near(homeStripPitch(f), two - one)
    }

    // Sideways, the card and the dock are centred on the screen's middle: a body 100 dp down, the screen's centre 200 dp down, puts the
    // group's middle at 100 dp into the body, held inside the room the body gives it.
    @Test fun sidewaysCarouselCentresOnTheScreen() {
        val p = HomeCarouselPlace(stripInkTop = 0f, boxTop = 120f, boxH = 100f, dockTop = 236f)
        val c = homeCarouselScreenCentred(p, stripInkH = 0f, dockH = 40f, bodyTop = 100f, centreY = 200f, minTop = 12f, maxBottom = 288f)
        near(100f, (c.boxTop + c.dockTop + 40f) / 2, "group middle")
        near(c.boxTop + 116f, c.dockTop, "dock keeps its gap")
        val low = homeCarouselScreenCentred(p, stripInkH = 0f, dockH = 40f, bodyTop = -200f, centreY = 200f, minTop = 12f, maxBottom = 288f)
        near(288f, low.dockTop + 40f, "held above the body's bottom")
    }
}
