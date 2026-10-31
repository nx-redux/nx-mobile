package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** A Grid tile's name ([markNameLines]): broken only between words, at most two lines. */
class MarkNameLinesTest {
    private val fits = { s: String -> s.length <= 10 }   // a line of ten letters

    @Test fun a_name_that_fits_is_one_line() {
        assertEquals(listOf("Fighting"), markNameLines("Fighting", 2, fits))
    }

    @Test fun a_longer_name_wraps_between_words() {
        assertEquals(listOf("Beat 'em", "Ups"), markNameLines("Beat 'em Ups", 2, fits))
    }

    @Test fun a_word_wider_than_the_tile_stays_whole_on_its_line() {
        assertEquals(listOf("Metroidvanias"), markNameLines("Metroidvanias", 2, fits))
        assertEquals(listOf("Metroidvanias", "and more"), markNameLines("Metroidvanias and more", 2, fits))
    }

    @Test fun past_two_lines_the_rest_joins_the_last() {
        assertEquals(listOf("Games for", "the whole family"), markNameLines("Games for the whole family", 2, fits))
    }
}
