package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A save state's picture is the game's own picture only ([thumbnailCrop]): no bars or anything else on the surface. */
class ThumbnailCropTest {
    @Test fun cropsToThePictureRounded() {   // a GBA picture letterboxed in a taller surface
        assertEquals(ThumbnailCrop(10, 40, 480, 320), thumbnailCrop(PxRect(10.4f, 39.6f, 479.8f, 320.2f), 500, 400))
    }

    @Test fun clampsToTheSurface() {   // a picture offset partly off the surface
        assertEquals(ThumbnailCrop(0, 0, 500, 380), thumbnailCrop(PxRect(-20f, -10f, 540f, 390f), 500, 400))
    }

    @Test fun wholeSurfaceWithoutOneRectOrWhenItIsTheSurface() {
        assertNull(thumbnailCrop(null, 500, 400))                         // DS / 3DS screens: no one rect
        assertNull(thumbnailCrop(PxRect(0f, 0f, 500f, 400f), 500, 400))   // nothing to cut
        assertNull(thumbnailCrop(PxRect(600f, 0f, 50f, 50f), 500, 400))   // off the surface: keep the whole frame
    }

    private fun region(screen: NdsScreen, dst: PxRect, inset: Boolean = false) =
        NdsRegion(screen, FrameRect(0f, 0f, 1f, 0.5f), dst, shaded = false, touch = screen == NdsScreen.BOTTOM, inset = inset)

    @Test fun aDsStateKeepsTheTopScreen() {   // the top screen only, wherever it is drawn
        val top = PxRect(0f, 0f, 512f, 384f)
        val bottom = PxRect(128f, 400f, 256f, 192f)
        assertEquals(top, listOf(region(NdsScreen.TOP, top), region(NdsScreen.BOTTOM, bottom)).thumbnailPicture())
        assertEquals(top, listOf(region(NdsScreen.BOTTOM, bottom), region(NdsScreen.TOP, top, inset = true)).thumbnailPicture())
    }

    @Test fun aDsShowingOnlyItsBottomScreenKeepsThat() {
        val bottom = PxRect(0f, 0f, 512f, 384f)
        assertEquals(bottom, listOf(region(NdsScreen.BOTTOM, bottom)).thumbnailPicture())
        assertNull(emptyList<NdsRegion>().thumbnailPicture())
    }

    private val gbaOnMain = PxRect(160f, 0f, 960f, 640f)
    private val gbaOnSecond = PxRect(0f, 20f, 1200f, 800f)

    @Test fun oneScreenCopiesTheMainPicture() {
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain),
            thumbnailSource(emptyList(), gbaOnMain, second = null, secondWhole = null, mainBlack = false, secondDrawing = false),
        )
    }

    @Test fun thePictureOnTheSecondDisplayIsCopiedFromThere() {   // the panel on main: main draws only black under it
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.SECOND, gbaOnSecond),
            thumbnailSource(emptyList(), gbaOnMain, second = null, secondWhole = gbaOnSecond, mainBlack = true, secondDrawing = true),
        )
    }

    @Test fun noSecondSurfaceFallsBackToMain() {   // the presentation's surface not there (yet)
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain),
            thumbnailSource(emptyList(), gbaOnMain, second = null, secondWhole = null, mainBlack = true, secondDrawing = false),
        )
    }

    @Test fun aDsWithTheSecondDisplayAsItsPictureKeepsTheTopScreenThere() {
        val top = PxRect(100f, 0f, 1000f, 750f)
        val bottom = PxRect(0f, 0f, 512f, 384f)
        // the panel on main, both DS screens side by side on the second display (panel up)
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.SECOND, top),
            thumbnailSource(emptyList(), gbaOnMain, listOf(region(NdsScreen.BOTTOM, bottom), region(NdsScreen.TOP, top)), null, mainBlack = true, secondDrawing = true),
        )
        // split: the bottom screen on main, the top one on the second display
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.SECOND, top),
            thumbnailSource(listOf(region(NdsScreen.BOTTOM, bottom)), null, listOf(region(NdsScreen.TOP, top)), null, mainBlack = false, secondDrawing = true),
        )
    }

    @Test fun splitWithTheTopScreenOnMainCopiesMain() {
        val top = PxRect(0f, 0f, 1000f, 750f)
        val bottom = PxRect(0f, 0f, 512f, 384f)
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, top),
            thumbnailSource(listOf(region(NdsScreen.TOP, top)), null, listOf(region(NdsScreen.BOTTOM, bottom)), null, mainBlack = false, secondDrawing = true),
        )
    }

    @Test fun aSecondDisplayDrawingNothingStaysOnMain() {   // split with the DS frame not the plain stack: main draws it whole
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain),
            thumbnailSource(emptyList(), gbaOnMain, emptyList(), null, mainBlack = false, secondDrawing = true),
        )
    }
}
