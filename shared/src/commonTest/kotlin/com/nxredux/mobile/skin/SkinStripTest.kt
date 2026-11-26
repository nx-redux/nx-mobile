package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkinStripTest {
    /** Four slots across 50..450, an even share each, as the solver places a strip's TOUCH_1..4. */
    private val slots = (1..4).map { i -> PlacedControl(ControlSpec("TOUCH_$i", ControlShape.RECT, 0f, 0f, hw = 50f, hh = 30f, labelDy = -22f), 100f * i, 900f) }

    private fun at(cs: List<PlacedControl>) = cs.map { it.spec.id to it.x }

    @Test fun swap_alone_takes_the_whole_strip_centred() {
        val (swap) = arrangeStrip(slots, emptyList(), STRIP_SWAP)
        assertEquals(STRIP_SWAP, swap.spec.id); assertEquals(250f, swap.x, 0.01f); assertEquals(200f, swap.spec.hw, 0.01f)
        assertEquals(900f, swap.y); assertEquals(30f, swap.spec.hh); assertEquals(-22f, swap.spec.labelDy)
    }

    @Test fun save_swap_load() {
        val cs = arrangeStrip(slots, listOf(slots[0], slots[2]), STRIP_SWAP)
        assertEquals(listOf("TOUCH_1", STRIP_SWAP, "TOUCH_3"), cs.map { it.spec.id })
        for ((c, x) in cs.zip(listOf(50f + 400f / 6f, 250f, 450f - 400f / 6f))) { assertEquals(x, c.x, 0.01f); assertEquals(400f / 6f, c.spec.hw, 0.01f) }
    }

    @Test fun four_shortcuts_two_each_side_of_swap() {
        val cs = arrangeStrip(slots, slots, STRIP_SWAP)
        assertEquals(listOf("TOUCH_1", "TOUCH_2", STRIP_SWAP, "TOUCH_3", "TOUCH_4"), cs.map { it.spec.id })
        for ((c, x) in cs.zip(listOf(90f, 170f, 250f, 330f, 410f))) assertEquals(x, c.x, 0.01f)
    }

    @Test fun an_odd_count_keeps_swap_centred() {
        val one = arrangeStrip(slots, listOf(slots[1]), STRIP_SWAP)
        assertEquals(250f, one.single { it.spec.id == STRIP_SWAP }.x, 0.01f)
        assertTrue(one.single { it.spec.id == "TOUCH_2" }.x < 250f)
        val three = arrangeStrip(slots, slots.take(3), STRIP_SWAP)
        assertEquals(250f, three.single { it.spec.id == STRIP_SWAP }.x, 0.01f)
        assertEquals(listOf("TOUCH_1", "TOUCH_2", STRIP_SWAP, "TOUCH_3"), three.map { it.spec.id })
    }

    @Test fun without_a_centre_the_filled_slots_share_the_strip() {
        assertEquals(listOf("TOUCH_2" to 150f, "TOUCH_4" to 350f), at(arrangeStrip(slots, listOf(slots[1], slots[3]))))
        assertEquals(emptyList(), arrangeStrip(emptyList(), emptyList(), STRIP_SWAP))
    }
}
