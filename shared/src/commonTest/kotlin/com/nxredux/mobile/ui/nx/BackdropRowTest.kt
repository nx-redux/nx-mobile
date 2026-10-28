package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import com.nxredux.mobile.ui.BackdropGame
import com.nxredux.mobile.ui.abstractAppArtUrl
import com.nxredux.mobile.ui.abstractArtUrl
import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.InfoLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackdropRowTest {
    @Test fun contentScaleIsTheSlotOverTheSpecNeverGrown() {
        val full = rowGeometry(RowStyle.BACKDROP, true, 2000f)
        assertEquals(LOGO_SELECTED_SCALE, backdropContentScale(full, BackdropSlot.LOGO, portrait = false), 1e-4f)   // the selected console's
        val half = rowGeometry(RowStyle.BACKDROP, true, GRID_RING_ROOM + 52f)   // f = 0.5
        assertEquals(0.5f * LOGO_SELECTED_SCALE, backdropContentScale(half, BackdropSlot.LOGO, portrait = false), 1e-4f)
        val cover = rowGeometry(RowStyle.BACKDROP, false, 298f, BACKDROP_ROW_CAPTION_GAP + text)   // the Flip cover: the spec
        assertEquals(1f, backdropContentScale(cover, BackdropSlot.BOX, portrait = false), 1e-4f)
    }

    // A tool pinned among games sits in the games' box art slot: its icon and name (two lines there) fit the slot.
    @Test fun aToolFitsTheGamesSlot() {
        assertEquals(1, backdropToolNameLines(BackdropSlot.LOGO))
        assertEquals(2, backdropToolNameLines(BackdropSlot.BOX))
        for (h in listOf(358f, 298f, 263f, 200f, 150f)) {
            val g = rowGeometry(RowStyle.BACKDROP, false, h, BACKDROP_ROW_CAPTION_GAP + text)
            val k = backdropContentScale(g, BackdropSlot.BOX, portrait = false)
            assertTrue(backdropToolHeight(k, backdropToolNameLines(BackdropSlot.BOX), backdropToolGap(BackdropSlot.BOX)) <= g.itemH, "tool ${backdropToolHeight(k, 2, 6f)} > slot ${g.itemH} at $h")
        }
    }

    // A tool's name in the games slot shrinks so "Achievements" fits the slot's text width; the logo slot never shrinks it.
    @Test fun aToolNameFitsTheGamesSlotWidth() {
        val small = rowGeometry(RowStyle.BACKDROP, false, GRID_RING_ROOM + 75f)   // f = 0.5: an 85 dp slot
        val k = backdropContentScale(small, BackdropSlot.BOX, portrait = false)
        val sp = backdropToolNameSp(small, k, BackdropSlot.BOX)
        assertTrue(sp * TOOL_LONGEST_EM <= small.itemW - 2 * SLOT_TEXT_PAD + 1e-3f, "$sp sp too wide for ${small.itemW}")
        assertTrue(sp < 17f * k, "no shrink: $sp")
        val land = rowGeometry(RowStyle.BACKDROP, false, 358f)
        val kl = backdropContentScale(land, BackdropSlot.BOX, portrait = false)
        assertEquals(17f * kl, backdropToolNameSp(land, kl, BackdropSlot.BOX), 1e-4f)   // the spec: room to spare
        val logo = rowGeometry(RowStyle.BACKDROP, true, 120f)
        val klogo = backdropContentScale(logo, BackdropSlot.LOGO, portrait = false)
        assertEquals(17f * klogo, backdropToolNameSp(logo, klogo, BackdropSlot.LOGO), 1e-4f)   // the Consoles / Collections logo slot: unchanged
    }

    // The composed window around the animated position is exactly the items rowItem shows.
    @Test fun windowAtPositionIsExactlyTheVisibleItems() {
        val g = rowGeometry(RowStyle.BACKDROP, false, 358f)
        for (pos in listOf(0f, 0.3f, 2f, 4.5f, 5f, 5.99f, 9f)) {
            val visible = (0 until 10).filter { rowItem(RowStyle.BACKDROP, it - pos, g).visible }
            assertEquals(visible, rowWindow(pos, 10).toList(), "pos $pos")
        }
    }

    // The band, an 18 dp × f gap and the caption reserve centred as one block; the slot grows to fill
    // the body less 16 dp above and below, at most 1.3 × the spec's.
    private val text = 62f   // about the caption reserve: one 18 sp name line and two 14 sp info rows

    private fun placed(bodyH: Float): Triple<Float, RowGeometry, BackdropPlacement> {
        val spec = rowGeometry(RowStyle.BACKDROP, false, bodyH, BACKDROP_ROW_CAPTION_GAP + text)
        val f = spec.itemH / BOX_SLOT_H
        val s = backdropSlotScale(bodyH, text, spec.itemH)
        val band = spec.grown(s)
        return Triple(s, band, backdropPlacement(bodyH, band.itemH, text, f))
    }

    private fun assertCentred(bodyH: Float, f: Float, band: RowGeometry, p: BackdropPlacement) {
        assertEquals(text, p.captionBottom - p.captionTop, 0.01f)
        // The band's bottom edge, its shadow room included, 18 dp × f over the caption's top.
        assertEquals(BACKDROP_ROW_CAPTION_GAP * f, p.captionTop - (p.rowTop + band.itemH + GRID_RING_ROOM), 0.01f)
        assertEquals(p.rowTop, bodyH - p.captionBottom, 0.01f)             // as much room above the block as below
    }

    @Test fun flipCoverBodyGrowsTheSlotToFillTheBodyCentred() {
        // The Flip's cover, landscape: a 399 × 298 body. The spec fits its height; the slot grows into the rest.
        val (s, band, p) = placed(298f)
        assertTrue(s > 1f && band.itemH < BACKDROP_SLOT_MAX * BOX_SLOT_H, "grew $s")
        assertCentred(298f, 1f, band, p)
        assertEquals(BACKDROP_SLOT_MARGIN, p.rowTop, 0.01f)                // fills the body less the 16 dp margins
        assertEquals(298f - 2 * BACKDROP_SLOT_MARGIN - (GRID_RING_ROOM + 18f + text), band.itemH, 0.1f)
        assertEquals(170f / 150f, band.itemW / band.itemH, 1e-4f)          // the slot's aspect ratio kept
        assertEquals(28f * s, band.gap, 1e-4f)                             // the neighbours grow with it
    }

    @Test fun foldCoverBodyKeepsTheSpecCentred() {
        // The Fold's cover, portrait with the pad: a body about 263 tall. The spec fits, with no spare to grow into.
        val (s, band, p) = placed(263f)
        assertEquals(1f, s)
        assertEquals(150f, band.itemH, 0.01f); assertEquals(170f, band.itemW, 0.01f)
        assertCentred(263f, 1f, band, p)
    }

    @Test fun tallerBodyStopsAtTheCapCentred() {
        val (s, band, p) = placed(330f)                                    // a Flip-like body, either orientation
        assertEquals(BACKDROP_SLOT_MAX * 150f, band.itemH, 0.01f)
        assertEquals(BACKDROP_SLOT_MAX * 170f, band.itemW, 0.01f)
        assertTrue(s > 1f)
        assertCentred(330f, 1f, band, p)
        assertTrue(p.rowTop > BACKDROP_SLOT_MARGIN)
    }

    @Test fun landscapeBodyStopsAtTheCapCentred() {
        val (s, band, p) = placed(358f)                                    // the mockup body: f = 1
        assertEquals(BACKDROP_SLOT_MAX, s, 1e-4f)
        assertEquals(195f, band.itemH, 0.01f)                              // 1.3 × 150, not a giant box
        assertCentred(358f, 1f, band, p)
        assertEquals((358f - (195f + GRID_RING_ROOM + 18f + text)) / 2, p.rowTop, 0.01f)
    }

    @Test fun slotNeverGoesBelowTheFittedSize() {
        val spec = rowGeometry(RowStyle.BACKDROP, false, 150f, BACKDROP_ROW_CAPTION_GAP + text)
        assertEquals(1f, backdropSlotScale(150f, text, spec.itemH))   // no spare height
        assertEquals(1f, backdropSlotScale(0f, text, spec.itemH))
        assertEquals(1f, backdropSlotScale(263f, 400f, spec.itemH))   // a huge font's caption
        assertEquals(1f, backdropSlotScale(263f, text, 0f))
    }

    @Test fun shortBodyScalesTheBandSoTheBlockFits() {
        for (h in listOf(200f, 150f, 120f)) {
            val (s, band, p) = placed(h)
            assertEquals(1f, s)
            assertTrue(band.itemH < 150f, "$h: ${band.itemH}")
            assertTrue(p.rowTop >= GRID_RING_ROOM - 1e-3f && p.captionBottom <= h + 1e-3f, "$h: ${p.rowTop}..${p.captionBottom}")
            assertEquals(170f / 150f, band.itemW / band.itemH, 1e-4f)
        }
    }

    // Collections and Tools: no caption; the band and its shadow room centred alone.
    @Test fun noCaptionRowIsCentredAlone() {
        for (h in listOf(263f, 298f, 330f, 358f)) {
            val (_, band, captioned) = placed(h)
            val f = rowGeometry(RowStyle.BACKDROP, false, h, BACKDROP_ROW_CAPTION_GAP + text).itemH / BOX_SLOT_H
            val p = backdropPlacement(h, band.itemH, null, f)
            assertEquals((h - band.itemH - GRID_RING_ROOM) / 2, p.rowTop, 0.01f, "$h")
            assertEquals(p.rowTop + band.itemH + GRID_RING_ROOM, p.captionTop, 0.01f)   // no gap, no reserve
            assertEquals(p.captionTop, p.captionBottom, 0.01f)
            assertTrue(p.rowTop > captioned.rowTop)                                      // lower than with a caption
        }
        assertEquals(GRID_RING_ROOM, backdropPlacement(100f, 120f, null, 0.5f).rowTop, 0.01f)   // short body: under the header
    }

    // The main-menu Consoles row (main-menu sync): its 130 dp logo slot centred alone in the body, with its shadow room.
    @Test fun consolesRowIsCentredAlone() {
        assertEquals((344f - 130f - GRID_RING_ROOM) / 2, backdropPlacement(344f, 130f, null, 1f).rowTop, 0.01f)
        val logo = backdropSlotGeometry(BackdropSlot.LOGO, 344f)
        val band = rowGeometry(RowStyle.BACKDROP, false, 344f)
        assertEquals(logo.itemH, backdropRowHeight(BackdropSlot.LOGO, band, logo), 1e-3f)   // not the games band
    }

    // Leaving a Backdrop game list (its page no longer live): the picture crossfades to black as the page slides out.
    @Test fun aPageSlidingOutFadesItsPictureToBlack() {
        assertEquals("u", backdropPictureTarget(true, "u")); assertNull(backdropPictureTarget(false, "u")); assertNull(backdropPictureTarget(true, null))
    }

    // The dim layer (scrimBackdrop, 0xBF ≈ 0.75: the picture at 25%): pinned, and with the shade the picture is at least 60% dark even in the middle.
    @Test fun dimLayerDarkensEveryShadeStop() {
        val dim = NxDefaults.colors.scrimBackdrop.alpha
        assertEquals(0.75f, dim, 0.002f)
        for ((at, alpha) in BACKDROP_SHADE) {
            val dark = 1f - (1f - dim) * (1f - alpha)
            assertTrue(dark >= 0.60f - 1e-4f, "$at: $dark")
        }
    }

    // The handheld's two lines: the time and the trophies, then the next goal alone.
    @Test fun captionRowsAreTheTimeAndTheAchievementsThenTheNextGoal() {
        val time = InfoLine(InfoKind.TIME, "Today - 1m 31s")
        val ach = InfoLine(InfoKind.ACHIEVEMENTS, "0 of 35")
        val next = InfoLine(InfoKind.NEXT, "Next: That Was Easy")
        assertEquals(listOf(listOf(time, ach), listOf(next)), backdropCaptionRows(listOf(time, ach, next)))
        assertEquals(listOf(listOf(ach), listOf(next)), backdropCaptionRows(listOf(ach, next)))   // no time
        assertEquals(listOf(listOf(next)), backdropCaptionRows(listOf(next)))             // the next goal alone
        assertEquals(listOf(listOf(time)), backdropCaptionRows(listOf(time)))             // no achievements
        assertEquals(listOf(listOf(ach)), backdropCaptionRows(listOf(ach)))               // all unlocked: no next goal
        assertEquals(emptyList(), backdropCaptionRows(emptyList()))
        val count = InfoLine(InfoKind.COUNT, "12 games")
        assertEquals(listOf(listOf(count)), backdropCaptionRows(listOf(count)))           // a console: one row
        // Each row is one infoText: the achievements row joins with the separator, after the trophy.
        assertEquals("[trophy]0 of 35 · Next: That Was Easy", infoAnnotatedString(listOf(ach, next)).text)
    }

    @Test fun nextGoalIsASpanInWhiteAndNothingElseIs() {
        val time = InfoLine(InfoKind.TIME, "Today - 1m")
        val ach = InfoLine(InfoKind.ACHIEVEMENTS, "0 of 35")
        val next = InfoLine(InfoKind.NEXT, "Next: That Was Easy")
        val white = Color.White
        val text = infoAnnotatedString(listOf(time, ach, next), next = white)
        val spans = text.spanStyles
        assertEquals(1, spans.size)
        val span = spans.single()
        assertEquals(white, span.item.color)
        assertEquals("Next: That Was Easy", text.text.substring(span.start, span.end))   // not the separator before it
        assertTrue(text.text.substring(0, span.start).endsWith(" · "))
        assertEquals(emptyList(), infoAnnotatedString(listOf(time, ach), next = white).spanStyles)   // no next goal: no span
        assertEquals(emptyList(), infoAnnotatedString(listOf(time, ach, next)).spanStyles)           // no colour: no span
    }

    @Test fun placeholderIsAPortraitCaseTheSlotsHeight() {
        val (w, h) = placeholderBoxSize(130f, 118f)   // a slot wider than the case
        assertEquals(118f, h, 1e-4f)
        assertEquals(118f * PLACEHOLDER_ASPECT, w, 1e-4f)
        val (nw, nh) = placeholderBoxSize(60f, 118f)  // a slot narrower than the case: fits the width
        assertEquals(60f, nw, 1e-4f)
        assertEquals(60f / PLACEHOLDER_ASPECT, nh, 1e-4f)
        assertTrue(nh <= 118f)
    }

    // The placeholder box over its abstract cover has no title (the caption under the row names the game), a ROM's
    // and an Android game's alike; only a bare case without any picture keeps the title.
    @Test fun placeholderCoverHasNoTitle() {
        assertNull(placeholderTitle(BackdropGame(null, null, "Kirby", "GBA", abstractUrl = abstractArtUrl("GBA", "Kirby.gba"))))
        assertNull(placeholderTitle(BackdropGame(null, "s/k.png", "Kirby", "GBA", abstractUrl = abstractArtUrl("GBA", "Kirby.gba"))))
        assertNull(placeholderTitle(BackdropGame(null, null, "Alpha", "ANDROID", appId = "a/A", abstractUrl = abstractAppArtUrl("a/A"))))
        assertEquals("Kirby", placeholderTitle(BackdropGame(null, null, "Kirby", "GBA")))
    }

    @Test fun placeholderTitleScalesWithTheBox() {
        assertEquals(118f * PLACEHOLDER_ASPECT * 0.13f, placeholderTitleSp(118f * PLACEHOLDER_ASPECT), 1e-4f)   // about 11 sp
        assertTrue(placeholderTitleSp(150f) > placeholderTitleSp(100f))
        assertEquals(10f, placeholderTitleSp(20f))
        assertEquals(20f, placeholderTitleSp(400f))
    }

    // A side item's "N games" never reads under 11 sp as drawn; the selected one stays 14.
    @Test fun aSideCountKeepsItsLeastSize() {
        assertEquals(1f, countGrowth(1f), 1e-4f)
        assertEquals(1f, countGrowth(0.9f), 1e-4f)                     // 12.6 sp as drawn: as it is
        assertEquals(MIN_COUNT_SP, 14f * 0.45f * countGrowth(0.45f), 1e-3f)
        assertEquals(MIN_COUNT_SP, 14f * 0.5f * countGrowth(0.5f), 1e-3f)
        assertEquals(1f, countGrowth(0f), 0f)
    }

    // A dimmed side console must not be drawn offscreen: that cuts it to its slot, and its count hangs past the slot.
    @Test fun aDimmedMarkIsNotCutToItsSlot() {
        assertEquals(CompositingStrategy.ModulateAlpha, itemCompositing(marks = true))
        assertEquals(CompositingStrategy.Auto, itemCompositing(marks = false))   // box art: a plate over its shadow
    }
}
