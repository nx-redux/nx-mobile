package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The button groups of the built-in NX Carbon layouts. */
class SkinGroupsTest {
    private val dir = File("../androidApp/src/main/assets/skins/nx-carbon")
    private val pack = assertNotNull(SkinPackManifest.parse(File(dir, "skin.json").readText()))
    private fun layout(id: String) = pack.layouts.first { it.id == id }

    @Test
    fun every_control_is_in_exactly_one_group() {
        for (spec in pack.layouts.filter { it.pieces.isNotEmpty() }) {
            val groups = SkinGroups.of(spec)
            assertEquals(groups.size, groups.map { it.id }.distinct().size, "${spec.id} unique ids ${groups.map { it.id }}")
            for (id in spec.controls.map { it.id }.distinct()) {
                // an id drawn in two places (the Wii sideways pad's B, face and shoulder) is in the group of each
                val places = spec.controls.filter { it.id == id }.map { it.x to it.y }.distinct().size
                assertEquals(places, groups.count { id in it.controls }, "${spec.id} $id")
            }
        }
    }

    @Test
    fun face_buttons_and_select_start_group_together() {
        val groups = SkinGroups.of(layout("stacked_menu"))
        assertTrue(groups.any { it.controls.toSet() == setOf("A", "B", "X", "Y") }, "${groups.map { it.controls }}")
        assertTrue(groups.any { it.controls.toSet() == setOf("SELECT", "START") }, "${groups.map { it.controls }}")
    }

    @Test
    fun alt_pieces_join_their_group() {
        val spec = layout("stacked_dc")
        val dpad = SkinGroups.of(spec).single { "DPAD" in it.controls }
        val alts = dpad.pieces.mapNotNull { spec.pieces[it].alt }.toSet()
        assertTrue("dpad" in alts && "stick" in alts, "$alts")
        assertTrue("STICK" in dpad.controls)
    }

    @Test
    fun ds_strip_is_one_free_group() {
        val strip = SkinGroups.of(layout("stacked_ds")).single { it.id == SkinGroups.STRIP }
        assertTrue(strip.free)
        assertEquals(listOf("TOUCH_1", "TOUCH_2", "TOUCH_3", "TOUCH_4"), strip.controls)
        assertTrue(strip.pieces.isEmpty())
    }

    @Test
    fun group_ids_are_stable() {
        val ids = SkinGroups.of(layout("stacked_menu")).map { it.id }
        assertTrue("A+B+X+Y" in ids && "SELECT+START" in ids, "$ids")
    }
}
