package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** A main menu console's accent ([slotLitness]): full at the centre, easing out over one item's step. */
class SlotLitnessTest {
    @Test fun the_centred_item_is_lit_and_its_neighbours_are_not() {
        assertEquals(1f, slotLitness(0f))
        assertEquals(0.5f, slotLitness(-0.5f))
        assertEquals(0.5f, slotLitness(0.5f))
        assertEquals(0f, slotLitness(1f))
        assertEquals(0f, slotLitness(-2.5f))
    }
}
