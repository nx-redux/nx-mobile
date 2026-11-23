package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.math.PI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test fun aDsStateKeepsTheTopScreenWhenBothAreTheSameSize() {   // Stacked, Side by side
        val top = PxRect(0f, 0f, 512f, 384f)
        val bottom = PxRect(0f, 384f, 512f, 384f)
        assertEquals(top, listOf(region(NdsScreen.BOTTOM, bottom), region(NdsScreen.TOP, top)).thumbnailPicture())
        // a pixel of rounding is still the same size
        val roundedTop = top.copy(height = 383f)
        assertEquals(roundedTop, listOf(region(NdsScreen.BOTTOM, bottom), region(NdsScreen.TOP, roundedTop)).thumbnailPicture())
    }

    @Test fun largeScreenKeepsTheBigOneWhereverItIs() {
        val big = PxRect(0f, 0f, 768f, 576f)
        val small = PxRect(768f, 0f, 256f, 192f)
        assertEquals(big, listOf(region(NdsScreen.TOP, big), region(NdsScreen.BOTTOM, small)).thumbnailPicture())
        assertEquals(big, listOf(region(NdsScreen.TOP, small), region(NdsScreen.BOTTOM, big)).thumbnailPicture())   // swapped
    }

    @Test fun anInsetIsNeverThePicture() {
        val big = PxRect(0f, 0f, 1024f, 768f)
        val inset = PxRect(700f, 500f, 256f, 192f)
        assertEquals(big, listOf(region(NdsScreen.BOTTOM, big), region(NdsScreen.TOP, inset, inset = true)).thumbnailPicture())
    }

    @Test fun a3dsTopScreenIsBiggerThanItsBottomOne() {   // 400 x 240 over 320 x 240
        val top = PxRect(0f, 0f, 800f, 480f)
        val bottom = PxRect(80f, 480f, 640f, 480f)
        assertEquals(top, listOf(region(NdsScreen.TOP, top), region(NdsScreen.BOTTOM, bottom)).thumbnailPicture())
    }

    @Test fun aDsShowingOnlyItsBottomScreenKeepsThat() {
        val bottom = PxRect(0f, 0f, 512f, 384f)
        assertEquals(bottom, listOf(region(NdsScreen.BOTTOM, bottom)).thumbnailPicture())
        assertNull(emptyList<NdsRegion>().thumbnailPicture())
    }

    @Test fun screensInOnePictureArePlacedWhereThePictureIs() {
        // a 3DS frame composed as Large screen: the top screen on the left two thirds, the bottom one small on the right
        val topSrc = FrameRect(0f, 0f, 1f, 0.5f)
        val bottomSrc = FrameRect(0.1f, 0.5f, 0.9f, 1f)
        val screens = listOf(
            PictureScreen(NdsScreen.TOP, topSrc, FrameRect(0f, 0f, 0.75f, 1f)),
            PictureScreen(NdsScreen.BOTTOM, bottomSrc, FrameRect(0.75f, 0.5f, 1f, 1f)),
        )
        val placed = screens.placedIn(PxRect(100f, 50f, 800f, 400f))
        assertEquals(PxRect(100f, 50f, 600f, 400f), placed[0].dst)
        assertEquals(PxRect(700f, 250f, 200f, 200f), placed[1].dst)
        assertEquals(topSrc, placed.thumbnailRegion()?.src)
    }

    @Test fun aMappedFrameDrawnWholeKeepsItsTopScreen() {   // the core's own layout, from its video views
        val map = ScreenMap.N3DS_START   // also where a 3DS resume cover goes before the first frame
        val placed = map.pictureScreens().placedIn(PxRect(0f, 0f, 400f, 480f))
        assertEquals(PxRect(0f, 0f, 400f, 240f), placed.thumbnailPicture())
        assertEquals(map.top, placed.thumbnailRegion()?.src)
    }

    private val gbaOnMain = PxRect(160f, 0f, 960f, 640f)
    private val gbaOnSecond = PxRect(0f, 20f, 1200f, 800f)

    @Test fun oneScreenKeepsTheMainPicture() {
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain),
            thumbnailSource(emptyList(), gbaOnMain, second = emptyList(), secondWhole = null, pictureOnSecond = false),
        )
    }

    @Test fun thePictureOnTheSecondDisplayIsKeptFromThere() {   // the panel on main
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.SECOND, gbaOnSecond),
            thumbnailSource(emptyList(), gbaOnMain, second = null, secondWhole = gbaOnSecond, pictureOnSecond = true),
        )
    }

    @Test fun thePanelOnTheSecondDisplayKeepsMain() {   // the picture on main: the second display is the panel
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain),
            thumbnailSource(emptyList(), gbaOnMain, second = emptyList(), secondWhole = null, pictureOnSecond = false),
        )
    }

    @Test fun noSecondSurfaceYetKeepsMainsLayout() {   // the presentation's surface not there (yet): main's own layout
        val source = thumbnailSource(emptyList(), gbaOnMain, second = emptyList(), secondWhole = null, pictureOnSecond = true)
        assertEquals(ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain), source)
        // main draws black under the panel then: not copied from there, only read from the core's frame
        assertFalse(source.copyable(mainBlack = true, secondDrawing = false))
        assertTrue(source.copyable(mainBlack = false, secondDrawing = false))
    }

    @Test fun theSecondDisplayIsCopiedOnlyWhileDrawn() {
        val source = ThumbnailSource(ThumbnailDisplay.SECOND, gbaOnSecond)
        assertTrue(source.copyable(mainBlack = true, secondDrawing = true))
        assertFalse(source.copyable(mainBlack = true, secondDrawing = false))
    }

    @Test fun aDsSplitKeepsThePictureDisplaysScreen() {
        val top = PxRect(100f, 0f, 1000f, 750f)
        val bottom = PxRect(0f, 0f, 512f, 384f)
        // the picture on the second display: the top screen there, the bottom one on main
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.SECOND, top, NdsLayout.TOP_SRC),
            thumbnailSource(listOf(region(NdsScreen.BOTTOM, bottom)), null, listOf(region(NdsScreen.TOP, top)), null, pictureOnSecond = true),
        )
        // the picture on main: the top screen there
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, top, NdsLayout.TOP_SRC),
            thumbnailSource(listOf(region(NdsScreen.TOP, top)), null, listOf(region(NdsScreen.BOTTOM, bottom)), null, pictureOnSecond = false),
        )
    }

    @Test fun bothDsScreensOnTheSecondDisplayKeepOne() {   // panel up with the picture there
        val top = PxRect(0f, 0f, 1000f, 750f)
        val bottom = PxRect(1000f, 0f, 1000f, 750f)
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.SECOND, top, NdsLayout.TOP_SRC),
            thumbnailSource(emptyList(), gbaOnMain, listOf(region(NdsScreen.BOTTOM, bottom), region(NdsScreen.TOP, top)), null, pictureOnSecond = true),
        )
    }

    @Test fun aSecondDisplayDrawingNothingKeepsMain() {   // split with the DS frame not the plain stack: main draws it whole
        assertEquals(
            ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain),
            thumbnailSource(emptyList(), gbaOnMain, emptyList(), null, pictureOnSecond = true),
        )
    }

    private fun size(w: Int, h: Int) = (w / 2) to (h / 2)

    @Test fun aFrameReadIsThePictureAtTheThumbnailSize() {
        assertEquals(
            FrameRead(FrameRect(0f, 0f, 1f, 1f), 480, 320, 0),
            frameRead(ThumbnailSource(ThumbnailDisplay.MAIN, gbaOnMain), 0f, ::size),
        )
        assertEquals(
            FrameRead(NdsLayout.TOP_SRC, 500, 375, 0),
            frameRead(ThumbnailSource(ThumbnailDisplay.SECOND, PxRect(100f, 0f, 1000f, 750f), NdsLayout.TOP_SRC), 0f, ::size),
        )
    }

    @Test fun aTurnedFrameIsReadInItsOwnOrientation() {   // a vertical arcade game: the core turns its frame a quarter
        val shown = PxRect(0f, 0f, 480f, 640f)   // tall on screen
        assertEquals(
            FrameRead(FrameRect(0f, 0f, 1f, 1f), 320, 240, 1),
            frameRead(ThumbnailSource(ThumbnailDisplay.MAIN, shown), (-PI / 2).toFloat(), ::size),
        )
        assertEquals(
            FrameRead(FrameRect(0f, 0f, 1f, 1f), 320, 240, 3),
            frameRead(ThumbnailSource(ThumbnailDisplay.MAIN, shown), (-3 * PI / 2).toFloat(), ::size),
        )
        assertEquals(2, frameRead(ThumbnailSource(ThumbnailDisplay.MAIN, shown), (-PI).toFloat(), ::size)?.quarterTurns)
    }

    @Test fun noPictureSizeNoFrameRead() {
        assertNull(frameRead(ThumbnailSource(ThumbnailDisplay.MAIN, null), 0f, ::size))
        assertNull(frameRead(ThumbnailSource(ThumbnailDisplay.MAIN, PxRect(0f, 0f, 0f, 10f)), 0f, ::size))
    }
}
