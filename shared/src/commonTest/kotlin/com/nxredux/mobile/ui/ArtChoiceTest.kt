package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.BackdropArt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtChoiceTest {
    // The preferred kind (Layouts → Backdrop art) first, then 3D → 2D → wheel; nothing, the placeholder.
    @Test fun boxFollowsPreferThenChain() {
        assertEquals(BoxShown.Cover("2d"), boxShown(BackdropArt.BOX2D, "3d", "2d", "w"))
        assertEquals(BoxShown.Art3d("3d"), boxShown(BackdropArt.WHEEL, "3d", null, null))
        assertEquals(BoxShown.Wheel("w"), boxShown(BackdropArt.BOX3D, null, null, "w"))
        assertEquals(BoxShown.Placeholder, boxShown(BackdropArt.BOX3D, null, null, null))
        assertEquals(BoxShown.Art3d("3d"), boxShown(BackdropArt.BOX2D, "3d", "2d", null, failed = setOf("2d")))
        // The plain chain without a preference met.
        assertEquals(BoxShown.Art3d("3d"), boxShown(BackdropArt.BOX3D, "3d", "2d", "w"))
        assertEquals(BoxShown.Wheel("w"), boxShown(BackdropArt.WHEEL, "3d", "2d", "w"))
        assertEquals(BoxShown.Cover("2d"), boxShown(BackdropArt.WHEEL, "3d", "2d", "w", failed = setOf("w", "3d")))
        assertEquals(BoxShown.Placeholder, boxShown(BackdropArt.BOX3D, "3d", "2d", "w", failed = setOf("3d", "2d", "w")))
    }

    // The header: the same chain; the screenshot only for an Android game (androidShot), last; null, the console logo.
    @Test fun panelArtNeverScreenshot() {
        assertNull(panelArt(BackdropArt.BOX3D, null, null, null))
        assertEquals("s", panelArt(BackdropArt.BOX3D, null, null, null, androidShot = "s"))
        assertEquals("w", panelArt(BackdropArt.WHEEL, "3d", "2d", "w"))
        assertEquals("3d", panelArt(BackdropArt.BOX3D, "3d", "2d", "w"))
        assertEquals("2d", panelArt(BackdropArt.BOX2D, "3d", "2d", "w", androidShot = "s"))
        assertEquals("2d", panelArt(BackdropArt.BOX3D, null, "2d", "w", androidShot = "s"))
    }

    // A hero screenshot is never drawn on a portrait background or in a tall tile; wide tiles draw it as a hero.
    @Test fun heroShotNotInPortrait() {
        assertNull(backgroundArt("h", heroShot = true, portrait = true))
        assertEquals("h", backgroundArt("h", heroShot = true, portrait = false))
        assertEquals("s", backgroundArt("s", heroShot = false, portrait = true))
        assertNull(backgroundArt(null, heroShot = false, portrait = false))
        assertEquals(TileArt("abs", TileFit.CROP), tileArt(140f, 154f, "h", true, "abs"))
        assertEquals(TileArt("h", TileFit.HERO), tileArt(340f, 240f, "h", true))
        assertEquals(TileArt("s", TileFit.CROP), tileArt(340f, 240f, "s", false))
    }

    // Tiles: the screenshot, else the abstract picture; an unmeasured tile takes only a plain screenshot.
    @Test fun tileTakesScreenshotThenAbstract() {
        assertEquals(TileArt("s", TileFit.CROP), tileArt(140f, 154f, "s", false, "a"))
        assertEquals(TileArt("a", TileFit.CROP), tileArt(140f, 154f, null, false, "a"))
        assertEquals(TileArt("a", TileFit.CROP), tileArt(340f, 240f, null, false, "a"))
        assertNull(tileArt(340f, 240f, null, false))
        assertEquals(TileArt("s", TileFit.CROP), tileArt(0f, 0f, "s", false, "a"))
        assertNull(tileArt(0f, 0f, null, false, "a"))
        assertNull(tileArt(0f, 0f, "h", true, "a"))   // a hero waits for the tile's shape
    }
}
