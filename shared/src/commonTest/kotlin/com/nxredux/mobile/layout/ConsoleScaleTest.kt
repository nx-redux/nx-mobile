package com.nxredux.mobile.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleScaleTest {
    @Test fun arcade_is_whole_height() = assertEquals(ConsoleScale.WHOLE_HEIGHT, consoleScale("FBN"))
    @Test fun genesis_is_whole_height() = assertEquals(ConsoleScale.WHOLE_HEIGHT, consoleScale("GPGX"))
    @Test fun doom_is_whole_height() = assertEquals(ConsoleScale.WHOLE_HEIGHT, consoleScale("PRBOOM"))
    @Test fun gba_is_native() = assertEquals(ConsoleScale.NATIVE, consoleScale("MGBA"))
    @Test fun playstation_is_fill() = assertEquals(ConsoleScale.FILL, consoleScale("PSX"))
    @Test fun ps2_is_fill() = assertEquals(ConsoleScale.FILL, consoleScale("PS2"))
    @Test fun nds_is_ds() = assertEquals(ConsoleScale.DS, consoleScale("NDS"))
    @Test fun n3ds_is_n3ds() = assertEquals(ConsoleScale.N3DS, consoleScale("3DS"))
    @Test fun unknown_tag_is_whole_height() = assertEquals(ConsoleScale.WHOLE_HEIGHT, consoleScale("NOPE"))
}
