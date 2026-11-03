package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals

class OptionGroupTest {
    private fun labelsIn(group: OptionGroup) =
        (ConsoleSettings.all + FrontendOptions.table).filter { OptionGroup.of(it.key) == group }.map { it.label }.toSet()

    @Test fun every_app_setting_is_in_the_group_it_is_for() {
        assertEquals(
            setOf(
                "Controller Layout", "Controller Type", "Portrait Pad", "Pad Opacity (landscape)", "New 3DS Controls",
                "Wii Controller", "Motion Controls", "Pointer Width", "Pointer Height", "Pointer Offset",
            ),
            labelsIn(OptionGroup.CONTROLS),
        )
        assertEquals(setOf("Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio", "PS2 BIOS"), labelsIn(OptionGroup.GAMEPLAY))
        assertEquals(setOf("Audio Resampling Quality"), labelsIn(OptionGroup.AUDIO))
        // The rest is the picture: a new row lands here unless it is put in a group above.
        val display = labelsIn(OptionGroup.DISPLAY)
        for (label in listOf("Screen Scaling", "Overlay", "Debug HUD", "Wii Screen", "Graphics API", "Bottom screen", "3D Resolution", "Layout (portrait)")) {
            assertEquals(true, label in display, label)
        }
    }

    @Test fun grouped_keeps_group_order_and_row_order_and_drops_empty_groups() {
        val keys = listOf(AudioOptions.RESAMPLING, FrontendOptions.SCALING, SpeedOptions.REWIND, FrontendOptions.DEBUG_HUD)
        assertEquals(
            listOf(
                OptionGroup.DISPLAY to listOf(FrontendOptions.SCALING, FrontendOptions.DEBUG_HUD),
                OptionGroup.GAMEPLAY to listOf(SpeedOptions.REWIND),
                OptionGroup.AUDIO to listOf(AudioOptions.RESAMPLING),
            ),
            OptionGroup.grouped(keys) { it },
        )
    }
}
