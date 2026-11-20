package com.nxredux.mobile.options

import com.nxredux.mobile.runner.FrameRect
import com.nxredux.mobile.runner.NdsRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Shader Screens: the shader on the top screen alone (the default) or on the touch screen too, per console and per game. */
class ShaderScreensTest {
    private fun region(screen: NdsScreen, shaded: Boolean, inset: Boolean = false) =
        NdsRegion(screen, FrameRect(0f, 0f, 1f, 1f), PxRect(0f, 0f, 10f, 10f), shaded = shaded, touch = screen == NdsScreen.BOTTOM, inset = inset)

    @Test fun theDsAndThe3dsListTheRowTopScreenFirst() {
        for (tag in listOf("NDS", "3DS")) {
            val row = ConsoleSettings.tableFor(tag).single { it.key == NdsOptions.SHADER_SCREENS }
            assertEquals(NdsOptions.SHADER_TOP, row.default)
            assertEquals(listOf("Top screen", "Both screens"), row.valueLabels)
            assertEquals(OptionGroup.DISPLAY, OptionGroup.of(row.key))
        }
        assertTrue(ConsoleSettings.tableFor("GBA").none { it.key == NdsOptions.SHADER_SCREENS })
    }

    @Test fun bothShadesTheTouchScreenTooButNotTheInset() {
        val both = mapOf(NdsOptions.SHADER_SCREENS to NdsOptions.SHADER_BOTH)
        assertTrue(NdsOptions.shaded(region(NdsScreen.BOTTOM, shaded = false), both).shaded)
        assertFalse(NdsOptions.shaded(region(NdsScreen.BOTTOM, shaded = false, inset = true), both).shaded)
        assertFalse(NdsOptions.shaded(region(NdsScreen.BOTTOM, shaded = false), emptyMap()).shaded)
        assertTrue(NdsOptions.shaded(region(NdsScreen.TOP, shaded = true), emptyMap()).shaded)
    }
}
