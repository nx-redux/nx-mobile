package com.nxredux.mobile.ui.nx

import androidx.compose.ui.geometry.Offset
import com.nxredux.mobile.runner.LaunchLine
import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchCaptureTest {
    /**
     * On screen the Continue row reads "Metroid - Zero Mi… [Continue]" (cut short before its tag's placeholder); the copy
     * is the whole name, one line, from the laid-out start x on the laid-out first baseline, both grown by the selection's
     * 1.04 from the text's top left.
     */
    @Test fun launch_line_is_the_full_name_at_its_baseline_in_screen_pixels() {
        val lines = launchLines("Metroid - Zero Mission", lineLeft = 0f, baseline = 30f, originInScreen = Offset(10f, 200f), scale = 1.04f)
        assertEquals(listOf(LaunchLine("Metroid - Zero Mission", 10f, 200f + 30f * 1.04f)), lines)
    }

    @Test fun a_stray_tag_placeholder_never_reaches_the_copy() {
        val lines = launchLines("Metroid - Zero Mission \uFFFD", lineLeft = 4f, baseline = 30f, originInScreen = Offset(10f, 200f), scale = 2f)
        assertEquals(listOf(LaunchLine("Metroid - Zero Mission", 18f, 260f)), lines)
    }

    @Test fun a_blank_name_has_no_line() {
        assertEquals(emptyList<LaunchLine>(), launchLines("\uFFFD", 0f, 30f, Offset.Zero, 1f))
    }
}
