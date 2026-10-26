package com.nxredux.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ListJumpsTest {
    private val names = listOf("1942", "3 Ninjas", "Advance Wars", "Aero", "banjo", "Castlevania", "Contra", "Éclair", "Zelda")

    @Test fun letter_groups_are_a_to_z_with_digits_and_symbols_before_and_accents_after() {
        assertEquals(0, ListJumps.group("1942"))
        assertEquals(0, ListJumps.group("[BIOS]"))
        assertEquals(1, ListJumps.group("Advance Wars"))
        assertEquals(1, ListJumps.group("aero"))   // case does not split a group
        assertEquals(26, ListJumps.group("Zelda"))
        assertEquals(27, ListJumps.group("Éclair"))
        assertEquals(0, ListJumps.group(""))
    }

    @Test fun r1_goes_to_the_next_groups_first_entry_and_stops_at_the_last_group() {
        assertEquals(2, ListJumps.letter(names, 0, 1))   // # -> A
        assertEquals(2, ListJumps.letter(names, 1, 1))   // from inside #
        assertEquals(4, ListJumps.letter(names, 2, 1))   // A -> B
        assertEquals(5, ListJumps.letter(names, 4, 1))   // B -> C
        assertEquals(7, ListJumps.letter(names, 6, 1))   // C -> É
        assertEquals(8, ListJumps.letter(names, 7, 1))   // É -> Z (runs in list order)
        assertEquals(8, ListJumps.letter(names, 8, 1))   // the last group: stays
    }

    // As NX Redux's game lists: the previous group's start, even from inside a group (not the current group's start).
    @Test fun l1_goes_to_the_previous_groups_first_entry_from_anywhere_in_a_group() {
        assertEquals(7, ListJumps.letter(names, 8, -1))  // Z -> É
        assertEquals(4, ListJumps.letter(names, 6, -1))  // Contra (inside C) -> B
        assertEquals(4, ListJumps.letter(names, 5, -1))  // C -> B
        assertEquals(0, ListJumps.letter(names, 3, -1))  // Aero (inside A) -> #
        assertEquals(1, ListJumps.letter(names, 1, -1))  // the first group: stays
        assertEquals(0, ListJumps.letter(names, 0, -1))
    }

    @Test fun an_empty_list_or_a_row_out_of_range_stays() {
        assertEquals(0, ListJumps.letter(emptyList(), 0, 1))
        assertEquals(12, ListJumps.letter(names, 12, 1))
    }

    @Test fun a_page_moves_by_the_rows_shown_and_stops_at_either_end() {
        assertEquals(7, ListJumps.page(count = 20, rows = 5, index = 2, dir = 1))
        assertEquals(19, ListJumps.page(count = 20, rows = 5, index = 17, dir = 1))
        assertEquals(19, ListJumps.page(count = 20, rows = 5, index = 19, dir = 1))
        assertEquals(10, ListJumps.page(count = 20, rows = 5, index = 15, dir = -1))
        assertEquals(0, ListJumps.page(count = 20, rows = 5, index = 3, dir = -1))
        assertEquals(1, ListJumps.page(count = 20, rows = 0, index = 0, dir = 1))   // no rows reported yet: one row
        assertEquals(0, ListJumps.page(count = 0, rows = 5, index = 0, dir = 1))
    }
}
