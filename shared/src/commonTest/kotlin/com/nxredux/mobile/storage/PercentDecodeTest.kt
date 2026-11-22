package com.nxredux.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class PercentDecodeTest {
    @Test fun escapes_decode_as_utf8() {
        assertEquals("primary:NXRedux/Roms", percentDecode("primary%3ANXRedux%2FRoms"))
        assertEquals("Pokémon", percentDecode("Pok%C3%A9mon"))
        assertEquals("🎮", percentDecode("%F0%9F%8E%AE"))
    }

    @Test fun text_without_an_escape_is_returned_as_is() {
        assertEquals("plain text", percentDecode("plain text"))
    }

    // An unescaped character outside the basic plane is two chars: it must come through whole beside an escape.
    @Test fun an_unescaped_character_outside_the_basic_plane_survives() {
        assertEquals("primary:🎮 Games", percentDecode("primary%3A🎮 Games"))
        assertEquals("日本語:🎮", percentDecode("日本語%3A🎮"))
    }

    @Test fun a_malformed_escape_is_kept_as_written() {
        assertEquals("100%", percentDecode("100%"))
        assertEquals("50%zz off:", percentDecode("50%zz off%3A"))
        assertEquals("a%+1b:", percentDecode("a%+1b%3A"))
        assertEquals("%4", percentDecode("%4"))
    }
}
