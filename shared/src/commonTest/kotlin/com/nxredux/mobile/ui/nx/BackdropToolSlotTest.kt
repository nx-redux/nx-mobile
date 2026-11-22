package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.ToolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The Backdrop Tools tab's own tool slot, 150 × 160 with gap 14: one size everywhere, shrunk only to fit the body's height.
class BackdropToolSlotTest {
    private val tool = GridTile.Tool(toolIcon(ToolKind.SETTINGS), listOf("Settings"))
    private val achievements = GridTile.Tool(toolIcon(ToolKind.RETRO_ACHIEVEMENTS), listOf("Retro", "Achievements"))

    private fun assertGeometry(w: Float, h: Float, gap: Float, scale: Float, g: RowGeometry) {
        assertEquals(w, g.itemW, 1e-3f); assertEquals(h, g.itemH, 1e-3f); assertEquals(gap, g.gap, 1e-3f); assertEquals(scale, g.scale, 1e-4f)
    }

    @Test fun theToolSlotIsOneSizeShrunkOnlyForHeight() {
        assertGeometry(150f, 160f, 14f, 0.62f, backdropSlotGeometry(BackdropSlot.TOOL, 358f))
        assertGeometry(150f, 160f, 14f, 0.62f, backdropSlotGeometry(BackdropSlot.TOOL, 298f))   // the Flip cover: the spec
        assertGeometry(150f, 160f, 14f, 0.62f, backdropSlotGeometry(BackdropSlot.TOOL, 700f))   // never grows
        val f = 0.5f   // a short body: the same shape, smaller, just fitting
        assertGeometry(150f * f, 160f * f, 14f * f, 0.62f, backdropSlotGeometry(BackdropSlot.TOOL, GRID_RING_ROOM + 80f))
    }

    // The logo slot is rowGeometry's; BOX is the box art spec before the games band grows it (the row uses the band).
    @Test fun theOtherSlotsAreRowGeometrys() {
        for (h in listOf(358f, 298f, 200f, 100f)) {
            assertEquals(rowGeometry(RowStyle.BACKDROP, true, h), backdropSlotGeometry(BackdropSlot.LOGO, h))
            assertEquals(rowGeometry(RowStyle.BACKDROP, false, h, 80f), backdropSlotGeometry(BackdropSlot.BOX, h, 80f))
        }
    }

    // The Consoles row's side logos shrink to 0.45 of the 330 dp slot, so the focused console stands out; the other rows
    // keep theirs. The focused console is 0.8 of the slot, so the sides are 0.45 / 0.8 of it, the same size.
    @Test fun consolesSideLogosShrinkToPoint45() {
        for (h in listOf(358f, 298f)) {
            val logo = backdropSlotGeometry(BackdropSlot.LOGO, h)
            assertEquals(0.45f * 330f, logo.itemW * logo.scale, 1e-2f)
            assertEquals(0.62f, backdropSlotGeometry(BackdropSlot.BOX, h).scale, 1e-4f)
            assertEquals(0.62f, backdropSlotGeometry(BackdropSlot.TOOL, h).scale, 1e-4f)
        }
        // The side items follow the scale: the neighbour's centre is 234 dp out (267 with the full-size selection).
        val g = backdropSlotGeometry(BackdropSlot.LOGO, 358f)
        val side = rowItem(RowStyle.BACKDROP, 1f, g)
        assertEquals(0.45f / LOGO_SELECTED_SCALE, side.scale, 1e-4f)
        assertEquals(234.25f, side.dx, 0.5f)
    }

    // The tool slot's icon-to-name gap is the mockup's 12 dp; a tool among games keeps 6.
    @Test fun theToolSlotGapIs12() {
        assertEquals(12f, backdropToolGap(BackdropSlot.TOOL))
        assertEquals(6f, backdropToolGap(BackdropSlot.BOX))
        assertEquals(6f, backdropToolGap(BackdropSlot.LOGO))
    }

    @Test fun aToolsRowPicksTheToolSlot() {
        assertEquals(BackdropSlot.TOOL, backdropSlot(games = false, tiles = listOf(achievements, tool)))
        assertEquals(BackdropSlot.LOGO, backdropSlot(games = false, tiles = listOf(GridTile.Logo(null, "Game Boy"))))   // Consoles
        assertEquals(BackdropSlot.COLLECTION, backdropSlot(games = false, tiles = listOf(GridTile.Collection("Co-op", 2))))  // Collections
        assertEquals(BackdropSlot.LOGO, backdropSlot(games = false, tiles = emptyList()))
        assertEquals(BackdropSlot.BOX, backdropSlot(games = true, tiles = emptyList()))                                // a game list
        assertEquals(BackdropSlot.BOX, backdropSlot(games = true, tiles = listOf(tool)))                               // All pinned, a tool among games
    }

    // Collections: the tool slot's height 160 and gap 14, but 240 wide, × f, the side items at half size.
    @Test fun collectionsSideItemsAreHalfSize() {
        for (h in listOf(358f, 298f, 120f)) {
            val f = minOf(1f, (h - GRID_RING_ROOM) / 160f)
            assertGeometry(240f * f, 160f * f, 14f * f, 0.5f, backdropSlotGeometry(BackdropSlot.COLLECTION, h))
        }
        assertEquals(0.5f, COLLECTION_SIDE_SCALE)
        assertEquals(240f, COLLECTION_SLOT_W)
    }

    @Test fun theCollectionSlotKeepsFiveInsideTheLandscapeGutters() {
        val g = backdropSlotGeometry(BackdropSlot.COLLECTION, 358f)
        assertGeometry(240f, 160f, 14f, 0.5f, g)
        assertEquals(1f, backdropContentScale(g, BackdropSlot.COLLECTION, portrait = false), 1e-4f)
        // The selected item and two each side sit inside the 24 dp gutters.
        val outer = rowItem(RowStyle.BACKDROP, 2f, g)
        assertTrue(outer.dx + g.itemW * outer.scale / 2 <= 960f / 2 - 24f, "outer edge ${outer.dx + g.itemW * outer.scale / 2}")
    }

    @Test fun aNarrowBodyKeepsTheCollectionSlotAndShowsLessOfItsNeighbours() {
        // The Flip (360 dp wide): the full 240 slot, whole on screen; its neighbours run off the edges.
        val g = backdropSlotGeometry(BackdropSlot.COLLECTION, 423f)
        assertGeometry(240f, 160f, 14f, 0.5f, g)
        assertEquals(1f, backdropContentScale(g, BackdropSlot.COLLECTION, portrait = false), 1e-4f)
        assertTrue(g.itemW <= 360f - 2 * 16f)
        val next = rowItem(RowStyle.BACKDROP, 1f, g)
        assertTrue(next.visible)
        assertTrue(next.dx - g.itemW * next.scale / 2 < 360f / 2, "neighbour starts on screen")
    }

    // Landscape: the focused tool and two each side sit inside the 24 dp gutters.
    @Test fun landscapeShowsFiveToolsInsideTheGutters() {
        val g = backdropSlotGeometry(BackdropSlot.TOOL, 358f)
        val outer = rowItem(RowStyle.BACKDROP, 2f, g)
        assertTrue(outer.visible)
        assertTrue(outer.dx + g.itemW * outer.scale / 2 <= 960f / 2 - 24f, "outer edge ${outer.dx + g.itemW * outer.scale / 2}")
    }

    @Test fun theToolContentFitsTheSlot() {
        assertEquals(2, backdropToolNameLines(BackdropSlot.TOOL))
        val reference = listOf(358f, 298f, 263f)
        for (h in reference + listOf(200f, 140f, 100f)) {
            val g = backdropSlotGeometry(BackdropSlot.TOOL, h)
            val k = backdropContentScale(g, BackdropSlot.TOOL, portrait = false)
            assertEquals(min1(g.itemW / 150f), k, 1e-4f)
            assertTrue(backdropToolHeight(k, backdropToolNameLines(BackdropSlot.TOOL), backdropToolGap(BackdropSlot.TOOL)) <= g.itemH, "tool too tall at $h")
            // "Achievements" fits between the name's padding: no mid-word break, down to a short body's small slot.
            val sp = backdropToolNameSp(g, k, BackdropSlot.TOOL)
            assertTrue(sp <= 17f * k + 1e-4f)
            assertTrue(sp * TOOL_LONGEST_EM <= g.itemW - 2 * backdropToolNamePad(BackdropSlot.TOOL) + 1e-3f, "Achievements too wide at $h: $sp sp")
            if (h in reference) assertEquals(17f * k, sp, 1e-4f)   // the phones: the full 17 sp
        }
    }

    // A narrow body (the Fold cover, 475 wide): five show, the outer two cut by the screen edges (as the mockup does).
    @Test fun aNarrowBodyCutsTheOuterTwoAtTheEdges() {
        val g = backdropSlotGeometry(BackdropSlot.TOOL, 263f)
        val outer = rowItem(RowStyle.BACKDROP, 2f, g)
        val half = g.itemW * outer.scale / 2
        assertTrue(outer.visible)
        assertTrue(outer.dx - half < 475f / 2 && 475f / 2 < outer.dx + half, "outer ${outer.dx - half}..${outer.dx + half}")
    }

    // The main-menu rows are centred on their own at their slot's height (the tool slot is taller than the games band's
    // spec); only a games row uses the band.
    @Test fun theToolsRowIsItsSlotsHeight() {
        val band = rowGeometry(RowStyle.BACKDROP, false, 305f)
        val tools = backdropSlotGeometry(BackdropSlot.TOOL, 305f)
        assertEquals(160f, backdropRowHeight(BackdropSlot.TOOL, band, tools), 1e-3f)
        val logo = backdropSlotGeometry(BackdropSlot.LOGO, 305f)
        assertEquals(logo.itemH, backdropRowHeight(BackdropSlot.LOGO, band, logo), 1e-3f)
        assertEquals(band.itemH, backdropRowHeight(BackdropSlot.BOX, band, band), 1e-3f)
    }

    private fun min1(x: Float) = if (x < 1f) x else 1f

    // Collections (user text) in the 240 dp landscape slot: the name shrinks from 30 sp until its longest word fits
    // between the 8 dp paddings, never below 17 sp, two lines ending in an ellipsis. Word widths here are a stand-in for
    // the measured ones: 0.62 em a character.
    private fun wordW(word: String, sp: Float) = word.length * 0.62f * sp

    @Test fun aCollectionNameShrinksUntilItsLongestWordFits() {
        val g = backdropSlotGeometry(BackdropSlot.COLLECTION, 358f)
        val k = backdropContentScale(g, BackdropSlot.COLLECTION, portrait = false)
        val avail = g.itemW - 2 * BACKDROP_COLLECTION_PAD
        assertEquals(8f, BACKDROP_COLLECTION_PAD)
        assertEquals(224f, avail, 1e-3f)
        fun size(name: String) = backdropCollectionNameSp(wordW(backdropLongestWord(name), 30f * k), avail, k)
        assertEquals(30f, size("Multiplayer"), 1e-3f)            // 204.6 dp at 30 sp: fits whole
        val metro = size("Metroidvanias")                        // 241.8 dp at 30 sp
        assertEquals(30f * 223f / wordW("Metroidvanias", 30f), metro, 1e-3f)   // 1 dp inside: pixel rounding can't break it
        assertTrue(wordW("Metroidvanias", metro) <= avail - 1f + 1e-3f)
        assertEquals(17f, size("Supercalifragilisticexpialidocious"), 1e-3f)   // 34 characters: floored at 17
        val long = "Classic Arcade Fighting Titles"                // 30 characters, several words
        assertEquals(30, long.length)
        assertEquals("Fighting", backdropLongestWord(long))
        assertEquals(30f, size(long), 1e-3f)                     // "Fighting" (148.8 dp at 30) fits
        assertEquals(30f, size("Co-op"), 1e-3f)
        assertEquals(2, BACKDROP_COLLECTION_LINES)
    }

    @Test fun theCollectionSizeFollowsTheSlotOnASmallPhone() {
        val k = 0.674f
        assertEquals(30f * k, backdropCollectionNameSp(80f, 90f, k), 1e-4f)                // fits
        assertEquals(30f * k * 89f / 100f, backdropCollectionNameSp(100f, 90f, k), 1e-4f)  // shrinks to fit, 1 dp inside
        assertEquals(30f * k * 89f / 89.5f, backdropCollectionNameSp(89.5f, 90f, k), 1e-4f) // within 1 dp of the edge: shrinks too
        assertEquals(17f * k, backdropCollectionNameSp(200f, 90f, k), 1e-4f)              // floored
    }

    @Test fun theLongestWordSplitsOnWhitespace() {
        assertEquals("Multiplayer", backdropLongestWord("Multiplayer"))
        assertEquals("Shoot-'em-ups", backdropLongestWord("Best  Shoot-'em-ups\tEver"))
        assertEquals("", backdropLongestWord(""))
    }
}
