package com.nxredux.mobile.ui.nx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The unit [nxTopCentreMaxWidth] measures the notifications' side rows in: height-bound, only a narrow screen fits the width. */
class ClusterUnitTest {
    private fun near(a: Float, b: Float) = assertTrue(abs(a - b) < 0.01f, "expected $b, got $a")

    @Test fun aCutoutOnTheClusterSideLeavesAPhoneHeightBound() {
        near(landscapeClusterUnit(800f, 360f, inset = 30f), 328f / 6f)
    }

    @Test fun theTallerCoverWindowFitsHalfTheWidth() {
        near(landscapeClusterUnit(474f, 470f), (237f - 32f) / 4.5f)
    }

    @Test fun theUnitInsideABoxIsTheHeightBoundOneOrTheWidthFit() {
        assertEquals(50f, clusterUnit(400f, 300f))
        assertEquals(20f, clusterUnit(90f, 300f))
    }
}
