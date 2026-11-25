package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A device no box size was drawn for still gets each console's boxed layout over a sibling built from pieces: the
 * pieces' layout puts the pads off the menu's spots, so the pad jumps between the menu and the game.
 */
class SkinUntunedTest {
    private val pack = skinPack()
    private val boxed = pack.layouts.filter { it.boxes.isNotEmpty() }.map { it.id }.toSet()

    @Test
    fun anUntunedDeviceGetsTheBoxedLayouts() {
        val devices = listOf(
            DeviceClass("big_tablet", 1848, 2960, 320, true, false), DeviceClass("big_tablet_land", 2960, 1848, 320, true, false),
            DeviceClass("small_tablet", 1200, 1920, 240, true, false), DeviceClass("square_fold_half", 2076, 2152, 420, true, true, 1076f))
        val off = devices.flatMap { d ->
            BOXED.keys.mapNotNull { v -> placeFor(pack, v, d)?.spec?.id?.takeIf { it !in boxed }?.let { "${d.name} ${BOXED.getValue(v)}: $it" } }
        }
        assertTrue(off.isEmpty(), "unboxed layouts chosen: $off")
    }
}
