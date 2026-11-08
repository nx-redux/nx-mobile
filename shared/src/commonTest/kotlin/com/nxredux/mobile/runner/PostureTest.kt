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

    // A game launched while half-folded starts from the menu's reading, so its first layout already has the hinge.
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
