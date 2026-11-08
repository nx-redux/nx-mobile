package com.nxredux.mobile.ui.nx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PadMetricsTest {
    private fun near(a: Float, b: Float) = assertTrue(abs(a - b) < 0.5f, "expected $b, got $a")

    @Test fun usesRealDpiWhenSane() {
        // 5.8 cm at 420 dpi = 5.8/2.54*420 ≈ 959 px; availablePx large so no cap.
        near(padBandPx(5.8f, 420f, 2.625f, 4000f), 5.8f / 2.54f * 420f)
    }

    @Test fun fallsBackToDensityBucketWhenDpiInsane() {
        // ydpi 0 -> density*160 = 420 dpi bucket.
        near(padBandPx(5.8f, 0f, 2.625f, 4000f), 5.8f / 2.54f * (2.625f * 160f))
    }

    @Test fun cappedAtSixtyPercent() {
        // Tiny screen: 5.8 cm would exceed 60% of 500 px -> capped at 300.
        near(padBandPx(5.8f, 420f, 2.625f, 500f), 300f)
    }
}
