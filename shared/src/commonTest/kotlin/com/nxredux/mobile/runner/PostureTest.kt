package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostureTest {
    // The Flip half-folded upright, measured on the device: 1080 x 2520 window, FoldingFeature bounds (0, 1260 - 1080, 1260),
    // a zero-height hinge line.
    private val flip = Posture.Tabletop(hingeTop = 1260, hingeBottom = 1260)

    @Test fun halfOpenedHorizontalIsTabletop() =
        assertEquals(flip, postureFrom(halfOpened = true, horizontal = true, top = 1260, bottom = 1260, windowH = 2520))

    @Test fun zeroHeightHingeIsTabletop() =
        assertEquals(Posture.Tabletop(1260, 1260), postureFrom(halfOpened = true, horizontal = true, top = 1260, bottom = 1260, windowH = 2520))

    @Test fun flatIsFlat() =
        assertEquals(Posture.Flat, postureFrom(halfOpened = false, horizontal = true, top = 1260, bottom = 1260, windowH = 2520))

    @Test fun verticalHingeIsFlat() =
        assertEquals(Posture.Flat, postureFrom(halfOpened = true, horizontal = false, top = 0, bottom = 1080, windowH = 1080))

    @Test fun hingeOutsideWindowIsFlat() =
        assertEquals(Posture.Flat, postureFrom(halfOpened = true, horizontal = true, top = 2600, bottom = 2640, windowH = 2520))

    @Test fun hingeNearAnEdgeIsFlat() {
        // the upper half would be 20% of the window: under the 25% minimum
        assertEquals(Posture.Flat, postureFrom(halfOpened = true, horizontal = true, top = 504, bottom = 540, windowH = 2520))
    }

    @Test fun flipGetsGameAboveAndPortraitPadBelow() =
        assertEquals(
            TabletopLayout(gameTop = 90, gameBottom = 1260, padTop = 1260, pad = TabletopPad.PORTRAIT),
            tabletopLayout(flip, windowW = 1080, windowH = 2520, cutoutTop = 90, controllerConnected = false, isDs = false),
        )

    @Test fun wideWindowGetsClusters() {
        // a wide window with a horizontal hinge (not the Fold's laptop pose, which is portrait-shaped: see foldLaptop below)
        val fold = Posture.Tabletop(hingeTop = 900, hingeBottom = 948)
        assertEquals(
            TabletopLayout(gameTop = 0, gameBottom = 900, padTop = 948, pad = TabletopPad.CLUSTERS),
            tabletopLayout(fold, windowW = 2448, windowH = 1848, cutoutTop = 0, controllerConnected = false, isDs = false),
        )
    }

    @Test fun controllerHidesPad() =
        assertEquals(
            TabletopPad.NONE,
            tabletopLayout(flip, windowW = 1080, windowH = 2520, cutoutTop = 90, controllerConnected = true, isDs = false)?.pad,
        )

    @Test fun dsGameStaysFlat() =
        assertNull(tabletopLayout(flip, windowW = 1080, windowH = 2520, cutoutTop = 90, controllerConnected = false, isDs = true))

    @Test fun flatHasNoTabletopLayout() =
        assertNull(tabletopLayout(Posture.Flat, windowW = 1080, windowH = 2520, cutoutTop = 90, controllerConnected = false, isDs = false))

    // ---- the main app's shell: the posture (window px) seen from its content area, which starts below the status bar

    @Test fun shellOnFlipGetsContentAboveAndPadBelow() =
        // Flip: status bar 90 px, so the shell is 1080 x 2430 from window y 90; the hinge line at 1260 is 1170 in it
        assertEquals(
            TabletopLayout(gameTop = 0, gameBottom = 1170, padTop = 1170, pad = TabletopPad.PORTRAIT),
            tabletopInArea(flip, areaTop = 90, areaW = 1080, areaH = 2430, controllerConnected = false),
        )

    @Test fun shellOnFoldLandscapeGetsClusters() =
        assertEquals(
            TabletopLayout(gameTop = 0, gameBottom = 900, padTop = 948, pad = TabletopPad.CLUSTERS),
            tabletopInArea(Posture.Tabletop(900, 948), areaTop = 0, areaW = 2448, areaH = 1848, controllerConnected = false),
        )

    @Test fun shellWithControllerHasNoPad() =
        assertEquals(
            TabletopPad.NONE,
            tabletopInArea(flip, areaTop = 90, areaW = 1080, areaH = 2430, controllerConnected = true)?.pad,
        )

    @Test fun shellFlatHasNoTabletop() =
        assertNull(tabletopInArea(Posture.Flat, areaTop = 90, areaW = 1080, areaH = 2430, controllerConnected = false))

    @Test fun shellHingeAboveTheAreaIsFlat() =
        assertNull(tabletopInArea(Posture.Tabletop(40, 40), areaTop = 90, areaW = 1080, areaH = 2430, controllerConnected = false))

    @Test fun shellHingeNearTheAreaBottomIsFlat() =
        // 2000 in the window is 1910 in a 2430 area: the lower part would be about 21%, under the 25% minimum
        assertNull(tabletopInArea(Posture.Tabletop(2000, 2000), areaTop = 90, areaW = 1080, areaH = 2430, controllerConnected = false))

    // ---- the hinge angle fallback: the Fold reports its feature FLAT at a laptop angle (HALF_OPENED is not app-visible)
    // Measured: the Fold's laptop window is 1848 x 2448 with the hinge line at 1224.
    private val foldLaptop = Posture.Tabletop(hingeTop = 1224, hingeBottom = 1224)

    private fun hinge(halfOpened: Boolean = false, horizontal: Boolean = true, angle: Float?, previous: Posture = Posture.Flat) =
        postureFromHinge(halfOpened, horizontal, top = 1224, bottom = 1224, windowH = 2448, hingeAngle = angle, previous = previous)

    @Test fun reportedHalfOpenedWinsWithoutASensor() = assertEquals(foldLaptop, hinge(halfOpened = true, angle = null))

    @Test fun flatFeatureAtLaptopAngleIsTabletop() = assertEquals(foldLaptop, hinge(angle = 90f))

    @Test fun flatFeatureNearlyOpenIsFlat() = assertEquals(Posture.Flat, hinge(angle = 175f))

    @Test fun flatFeatureNearlyClosedIsFlat() = assertEquals(Posture.Flat, hinge(angle = 28f))

    @Test fun tabletopStaysTabletopInsideTheHysteresis() {
        assertEquals(foldLaptop, hinge(angle = 27f, previous = foldLaptop))
        assertEquals(foldLaptop, hinge(angle = 153f, previous = foldLaptop))
    }

    @Test fun tabletopLeavesPastTheHysteresis() {
        assertEquals(Posture.Flat, hinge(angle = 24f, previous = foldLaptop))
        assertEquals(Posture.Flat, hinge(angle = 156f, previous = foldLaptop))
    }

    @Test fun verticalFeatureAtLaptopAngleIsFlat() = assertEquals(Posture.Flat, hinge(horizontal = false, angle = 90f))

    @Test fun flatFeatureWithoutASensorIsFlat() = assertEquals(Posture.Flat, hinge(angle = null))

    @Test fun foldLaptopGetsThePortraitPad() =
        assertEquals(
            TabletopLayout(gameTop = 0, gameBottom = 1224, padTop = 1224, pad = TabletopPad.PORTRAIT),
            tabletopLayout(foldLaptop, windowW = 1848, windowH = 2448, cutoutTop = 0, controllerConnected = false, isDs = false),
        )

    // A game launched while half-folded starts from the menu's reading, so its first layout is already tabletop.
    @Test fun aSameSizeWindowKeepsTheMenusTabletop() =
        assertEquals(flip, seededPosture(flip, hintW = 1080, hintH = 2520, windowW = 1080, windowH = 2520))

    @Test fun noHintOrAFlatHintStartsFlat() {
        assertEquals(Posture.Flat, seededPosture(null, hintW = 0, hintH = 0, windowW = 1080, windowH = 2520))
        assertEquals(Posture.Flat, seededPosture(Posture.Flat, hintW = 1080, hintH = 2520, windowW = 1080, windowH = 2520))
    }

    @Test fun aWindowOfAnotherSizeIgnoresTheHint() {
        // rotated, or the game on another display: the hinge is somewhere else there, so wait for this window's own reading
        assertEquals(Posture.Flat, seededPosture(flip, hintW = 1080, hintH = 2520, windowW = 2520, windowH = 1080))
        assertEquals(Posture.Flat, seededPosture(flip, hintW = 1080, hintH = 2520, windowW = 1080, windowH = 2400))
    }
}
