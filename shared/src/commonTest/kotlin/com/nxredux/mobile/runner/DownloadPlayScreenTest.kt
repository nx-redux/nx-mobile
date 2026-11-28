package com.nxredux.mobile.runner

import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DownloadPlayScreenTest {
    @Test fun the_ds_client_shows_the_top_screen_alone_in_every_orientation() {
        val v = DownloadPlayScreen.single(mapOf(NdsOptions.LAYOUT_PORTRAIT to "stacked", NdsOptions.BIG_SCREEN to "bottom"), n3ds = false)
        for (key in NdsOptions.LAYOUT_KEYS) assertEquals("single", v[key])
        assertEquals("top", v[NdsOptions.BIG_SCREEN])
    }

    @Test fun the_3ds_client_shows_the_top_screen_alone_in_every_orientation() {
        val v = DownloadPlayScreen.single(mapOf(N3dsOptions.LAYOUT_LANDSCAPE to "large"), n3ds = true)
        for (key in DownloadPlayScreen.N3DS_LAYOUT_KEYS) assertEquals("single_screen", v[key])
    }

    @Test fun the_received_game_gets_the_players_layout_back() {
        val stored = mapOf(NdsOptions.LAYOUT_PORTRAIT to "stacked", NdsOptions.BIG_SCREEN to "bottom", "nx_other" to "a")
        val shown = DownloadPlayScreen.single(stored, n3ds = false) + ("nx_other" to "b")
        val back = DownloadPlayScreen.restored(shown, stored, n3ds = false)
        assertEquals("stacked", back[NdsOptions.LAYOUT_PORTRAIT])
        assertEquals("bottom", back[NdsOptions.BIG_SCREEN])
        assertFalse(NdsOptions.LAYOUT_LANDSCAPE in back)   // no pick before: the screen's default again
        assertEquals("b", back["nx_other"])                // a menu change meanwhile is kept
    }

    @Test fun a_save_while_the_client_shows_keeps_the_players_layout() {
        val stored = mapOf(NdsOptions.LAYOUT_PORTRAIT to "stacked")
        val shown = DownloadPlayScreen.single(stored, n3ds = false) + ("nx_other" to "b")
        val saved = DownloadPlayScreen.toSave(shown, stored, n3ds = false)
        assertEquals("stacked", saved[NdsOptions.LAYOUT_PORTRAIT])
        assertFalse(NdsOptions.LAYOUT_LANDSCAPE in saved)
        assertFalse(NdsOptions.BIG_SCREEN in saved)
        assertEquals("b", saved["nx_other"])
        assertEquals(shown, DownloadPlayScreen.toSave(shown, null, n3ds = false))   // not swapped: saved as shown
    }

    @Test fun the_rows_forced_core_options_are_never_saved() {
        assertEquals(DsMenu.FORCED_OPTIONS.keys, DownloadPlayScreen.unsavedOptions(dsMenu = true, n3ds = false))
        assertEquals(N3dsDownloadPlay.FORCED_OPTIONS.keys, DownloadPlayScreen.unsavedOptions(dsMenu = false, n3ds = true))
        assertEquals(emptySet(), DownloadPlayScreen.unsavedOptions(dsMenu = false, n3ds = false))
    }
}
