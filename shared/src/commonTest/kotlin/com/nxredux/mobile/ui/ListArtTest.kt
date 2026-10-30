package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.GameListArt
import kotlin.test.Test
import kotlin.test.assertEquals

class ListArtTest {
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
    private fun game(vararg refs: Pair<String, String>): Listed {
        val m = refs.toMap()
        return Listed(gba, GameEntry(
            ContentRef("r"), "Zelda.gba", "Zelda", boxartRef = m["box"]?.let(::ContentRef), screenshotRef = m["shot"]?.let(::ContentRef),
            box2dRef = m["box2d"]?.let(::ContentRef), wheelRef = m["wheel"]?.let(::ContentRef), mixRef = m["mix"]?.let(::ContentRef),
            folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)",
        ))
    }
    private val full = game("shot" to "s.png", "box" to "b.png", "box2d" to "b2.png", "wheel" to "w.png", "mix" to "m.png")

    @Test fun screenshot_is_todays_full_screen_picture() {
        assertEquals(ListArtShown.Full("s.png"), listArtShown(full, GameListArt.SCREENSHOT, portrait = false))
    }

    @Test fun each_boxed_value_shows_its_own_picture_with_the_screenshot_as_fallback() {
        assertEquals(ListArtShown.Boxed("m.png", "s.png"), listArtShown(full, GameListArt.MIX, portrait = false))
        assertEquals(ListArtShown.Boxed("b.png", "s.png"), listArtShown(full, GameListArt.BOX3D, portrait = false))
        assertEquals(ListArtShown.Boxed("b2.png", "s.png"), listArtShown(full, GameListArt.BOX2D, portrait = false))
        assertEquals(ListArtShown.Boxed("w.png", "s.png"), listArtShown(full, GameListArt.WHEEL, portrait = false))
    }

    @Test fun a_game_without_that_picture_shows_its_screenshot_full_screen() {
        val shotOnly = game("shot" to "s.png")
        for (art in listOf(GameListArt.MIX, GameListArt.BOX3D, GameListArt.BOX2D, GameListArt.WHEEL))
            assertEquals(ListArtShown.Full("s.png"), listArtShown(shotOnly, art, portrait = true), "$art")
        // No screenshot either: today's chain (its abstract picture), as Screenshot would show.
        val bare = game()
        assertEquals(ListArtShown.Full(gameBackground(bare, portrait = false)), listArtShown(bare, GameListArt.WHEEL, portrait = false))
    }

    @Test fun none_is_no_picture_at_all_and_an_empty_list_keeps_todays_mark() {
        assertEquals(ListArtShown.None, listArtShown(full, GameListArt.NONE, portrait = false))
        assertEquals(ListArtShown.None, listArtShown(null, GameListArt.NONE, portrait = false))
        assertEquals(ListArtShown.Full(null), listArtShown(null, GameListArt.MIX, portrait = false))
    }

    @Test fun the_names_stop_short_of_the_box() {
        assertEquals(800f * LIST_ART_WIDTH_SHARE + 24f + 8f, listArtLabelEnd(800f, insetEnd = 24f))
        assertEquals(400f * LIST_ART_WIDTH_SHARE + 16f + 8f, listArtLabelEnd(400f, insetEnd = 16f))
    }
}
