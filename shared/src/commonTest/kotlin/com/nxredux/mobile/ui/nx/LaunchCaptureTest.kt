package com.nxredux.mobile.ui.nx

import androidx.compose.ui.geometry.Offset
import com.nxredux.mobile.runner.LaunchLine
import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchCaptureTest {
    /**
     * On screen the Continue row reads "Metroid - Zero Mi… [Continue]" (cut short before its tag's placeholder); the copy
     * is the whole name from its laid-out start x on its baseline, grown by the selection's 1.04 from the text's top left.
     */
    @Test fun launch_line_is_the_full_name_at_its_baseline_in_screen_pixels() {
        val lines = launchLines(listOf(CopyLine("Metroid - Zero Mission", 0f, 30f)), originInScreen = Offset(10f, 200f), scale = 1.04f)
        assertEquals(listOf(LaunchLine("Metroid - Zero Mission", 10f, 200f + 30f * 1.04f)), lines)
    }

    /** A long name wrapped onto two lines: both go to the game screen, the second under the first, trailing spaces gone. */
    @Test fun a_wrapped_name_keeps_every_line() {
        val lines = launchLines(
            listOf(CopyLine("Dragon Ball Z - Budokai ", 0f, 30f), CopyLine("Tenkaichi 3 (USA)", 0f, 70f)),
            originInScreen = Offset(10f, 200f), scale = 2f,
        )
        assertEquals(listOf(LaunchLine("Dragon Ball Z - Budokai", 10f, 260f), LaunchLine("Tenkaichi 3 (USA)", 10f, 340f)), lines)
    }

    @Test fun a_stray_tag_placeholder_never_reaches_the_copy() {
        val lines = launchLines(listOf(CopyLine("Metroid - Zero Mission \uFFFD", 4f, 30f)), originInScreen = Offset(10f, 200f), scale = 2f)
        assertEquals(listOf(LaunchLine("Metroid - Zero Mission", 18f, 260f)), lines)
    }

    @Test fun a_blank_name_has_no_line() {
        assertEquals(emptyList<LaunchLine>(), launchLines(listOf(CopyLine("\uFFFD", 0f, 30f)), Offset.Zero, 1f))
    }

    /** The copy wraps as far in from the right as it starts from the left, in its own (unscaled) px. */
    @Test fun the_wrap_width_mirrors_the_left_margin() {
        assertEquals(1000f, launchWrapWidth(windowWidth = 1080f, startX = 40f, scale = 1f))
        assertEquals(500f, launchWrapWidth(windowWidth = 1080f, startX = 40f, scale = 2f))
        assertEquals(1f, launchWrapWidth(windowWidth = 100f, startX = 80f, scale = 1f))
    }
}
