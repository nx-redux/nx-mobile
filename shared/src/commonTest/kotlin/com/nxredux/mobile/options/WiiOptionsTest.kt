package com.nxredux.mobile.options

import com.nxredux.mobile.runner.WiiController
import com.nxredux.mobile.runner.WiiMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WiiOptionsTest {
    @Test fun wiiGetsTheControllerAndMotionRowsBeforeTheLayoutRow() {
        assertEquals(
            listOf(
                WiiOptions.CONTROLLER, WiiOptions.MOTION, WiiOptions.SCREEN, WiiOptions.POINTER_WIDTH, WiiOptions.POINTER_HEIGHT,
                WiiOptions.POINTER_OFFSET, GraphicsApiOptions.KEY, BottomScreenOptions.KEY, PadOptions.LAYOUT,
            ) + (SpeedOptions.table + AudioOptions.rows + FrameSkipOptions.row).map { it.key },
            ConsoleSettings.tableFor("WII").map { it.key },
        )
    }

    @Test fun gameCubeGetsNeither() {
        assertEquals(listOf(GraphicsApiOptions.KEY, BottomScreenOptions.KEY, PadOptions.LAYOUT) + (SpeedOptions.table + AudioOptions.rows + FrameSkipOptions.row).map { it.key }, ConsoleSettings.tableFor("GC").map { it.key })
        assertFalse(WiiOptions.appliesTo("GC"))
        assertTrue(WiiOptions.appliesTo(" wii "))
    }

    @Test fun valuesAndDefaults() {
        assertEquals(
            listOf(WiiController.AUTO, WiiController.SIDEWAYS, WiiController.UPRIGHT, WiiController.NUNCHUK, WiiController.CLASSIC),
            WiiOptions.controllerRow.values,
        )
        assertEquals(WiiController.AUTO, WiiOptions.controllerRow.default)
        assertEquals(listOf(WiiMotion.AUTO, WiiMotion.ON, WiiMotion.OFF), WiiOptions.motionRow.values)
        assertEquals("Classic Controller", WiiOptions.controllerRow.labelOf(WiiController.CLASSIC))
    }

    // Wii Screen: Auto is 4:3 when the game starts in portrait, 16:9 in landscape on a wide screen; a fixed choice wins.
    @Test fun theWiiScreenFollowsTheOrientationTheGameStartsIn() {
        assertEquals(listOf(WiiScreen.AUTO, WiiScreen.FOUR_THREE, WiiScreen.WIDE), WiiOptions.screenRow.values)
        assertEquals(WiiScreen.AUTO, WiiOptions.screenRow.default)
        assertEquals(listOf("Auto", "4:3", "16:9"), WiiOptions.screenRow.values.map(WiiOptions.screenRow::labelOf))
        assertFalse(WiiScreen.widescreen(WiiScreen.AUTO, portrait = true))
        assertTrue(WiiScreen.widescreen(WiiScreen.AUTO, portrait = false))
        assertFalse(WiiScreen.widescreen(WiiScreen.FOUR_THREE, portrait = false))
        assertTrue(WiiScreen.widescreen(WiiScreen.WIDE, portrait = true))
        assertTrue(WiiScreen.widescreen(null, portrait = false))   // unknown = Auto
        // Auto follows the screen's shape in landscape: a 4:3 handheld (Retroid Pocket Nova, 1280x960) or a 3:2 one is 4:3.
        assertFalse(WiiScreen.widescreen(WiiScreen.AUTO, portrait = false, screenAspect = 1280f / 960f))
        assertFalse(WiiScreen.widescreen(WiiScreen.AUTO, portrait = false, screenAspect = 1.5f))
        assertTrue(WiiScreen.widescreen(WiiScreen.AUTO, portrait = false, screenAspect = 1920f / 1080f))
        assertTrue(WiiScreen.widescreen(WiiScreen.AUTO, portrait = false, screenAspect = 2340f / 1080f))
        assertTrue(WiiScreen.widescreen(WiiScreen.WIDE, portrait = false, screenAspect = 4f / 3f))   // a fixed choice wins
        assertEquals("disabled", WiiScreen.coreValue(widescreen = false))
        assertEquals("enabled", WiiScreen.coreValue(widescreen = true))
    }

    // The touch pointer's geometry follows the shape (calibrated on the Fold in SMG2): 16:9 yaw 18, 4:3 yaw 20.
    @Test fun thePointerGeometryFollowsTheScreenShape() {
        assertEquals(
            mapOf("dolphin_ir_yaw" to "18", "dolphin_ir_pitch" to "20", "dolphin_ir_offset" to "16"),
            WiiScreen.pointerOptions(widescreen = true),
        )
        assertEquals(
            mapOf("dolphin_ir_yaw" to "20", "dolphin_ir_pitch" to "20", "dolphin_ir_offset" to "16"),
            WiiScreen.pointerOptions(widescreen = false),
        )
    }

    // Pointer Width / Height scale the shape's yaw / pitch; Pointer Offset moves the cursor up (+) or down from 16.
    @Test fun thePointerRowsAdjustTheCalibratedGeometry() {
        assertEquals(
            mapOf("dolphin_ir_yaw" to "16", "dolphin_ir_pitch" to "22", "dolphin_ir_offset" to "19"),
            WiiScreen.pointerOptions(widescreen = true, width = "90", height = "110", offset = "3"),
        )
        assertEquals("18", WiiScreen.pointerOptions(widescreen = false, width = "90")["dolphin_ir_yaw"])
        assertEquals("11", WiiScreen.pointerOptions(widescreen = true, offset = "-5")["dolphin_ir_offset"])
        // Unknown values fall back to the calibration.
        assertEquals(WiiScreen.pointerOptions(widescreen = true), WiiScreen.pointerOptions(true, "abc", null, "x"))
    }

    @Test fun thePointerRowsOfferTheirRanges() {
        assertEquals((50..150 step 5).map { it.toString() }, WiiOptions.pointerWidthRow.values)
        assertEquals("100", WiiOptions.pointerWidthRow.default)
        assertEquals("90%", WiiOptions.pointerWidthRow.labelOf("90"))
        assertEquals(WiiOptions.pointerWidthRow.values, WiiOptions.pointerHeightRow.values)
        assertEquals((-20..20).map { it.toString() }, WiiOptions.pointerOffsetRow.values)
        assertEquals("0", WiiOptions.pointerOffsetRow.default)
        assertEquals("+3", WiiOptions.pointerOffsetRow.labelOf("3"))
        assertEquals("-3", WiiOptions.pointerOffsetRow.labelOf("-3"))
    }

    @Test fun everyConsoleRowKeyIsUnique() {
        val keys = ConsoleSettings.all.map { it.key }
        assertEquals(keys.toSet().size, keys.size)
        assertTrue(WiiOptions.CONTROLLER in keys && WiiOptions.MOTION in keys)
    }

    // A save state keeps the shape its game booted with: the core's frame aspect tells it, and a note says why once.
    @Test fun aResumedGameKeepsTheShapeItWasSavedIn() {
        assertTrue(WiiScreen.isWide(16f / 9f))
        assertFalse(WiiScreen.isWide(4f / 3f))
        assertNull(WiiScreen.keptShapeNote(gameWide = true, launchWide = true))
        assertEquals(
            "This save is in 16:9. Wii Screen 4:3 applies when the game starts fresh.",
            WiiScreen.keptShapeNote(gameWide = true, launchWide = false),
        )
        assertEquals(
            "This save is in 4:3. Wii Screen 16:9 applies when the game starts fresh.",
            WiiScreen.keptShapeNote(gameWide = false, launchWide = true),
        )
    }
}
