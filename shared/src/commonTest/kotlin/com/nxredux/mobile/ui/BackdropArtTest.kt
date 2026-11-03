package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.BackdropArt
import kotlin.test.Test
import kotlin.test.assertEquals

class BackdropArtTest {
    private fun game(box: String? = "b.png", box2d: String? = "b2.png", wheel: String? = "w.png", art: BackdropArt) =
        BackdropGame(box, "s.png", "Zelda", box2dUrl = box2d, wheelUrl = wheel, art = art)

    @Test fun box3d_is_todays_chain() {
        assertEquals(BoxShown.Art3d("b.png"), backdropBoxShown(game(art = BackdropArt.BOX3D)))
        assertEquals(BoxShown.Cover("b2.png"), backdropBoxShown(game(box = null, art = BackdropArt.BOX3D)))
    }

    @Test fun box2d_and_wheel_show_their_own_picture() {
        assertEquals(BoxShown.Cover("b2.png"), backdropBoxShown(game(art = BackdropArt.BOX2D)))
        assertEquals(BoxShown.Wheel("w.png"), backdropBoxShown(game(art = BackdropArt.WHEEL)))
    }

    @Test fun without_it_the_chain_3d_then_2d_then_wheel() {
        assertEquals(BoxShown.Art3d("b.png"), backdropBoxShown(game(box2d = null, art = BackdropArt.BOX2D)))
        assertEquals(BoxShown.Art3d("b.png"), backdropBoxShown(game(wheel = null, art = BackdropArt.WHEEL)))
        assertEquals(BoxShown.Cover("b2.png"), backdropBoxShown(game(box = null, wheel = null, art = BackdropArt.WHEEL)))
        assertEquals(BoxShown.Wheel("w.png"), backdropBoxShown(game(box = null, box2d = null, art = BackdropArt.BOX2D)))
        assertEquals(BoxShown.Wheel("w.png"), backdropBoxShown(game(box = null, box2d = null, art = BackdropArt.BOX3D)))
        assertEquals(BoxShown.Placeholder, backdropBoxShown(game(box = null, box2d = null, wheel = null, art = BackdropArt.WHEEL)))   // never the screenshot
        assertEquals(BoxShown.Art3d("b.png"), backdropBoxShown(game(art = BackdropArt.WHEEL), failed = setOf("w.png")))   // a broken wheel
    }

    @Test fun backdrop_games_carry_the_wheel_and_the_setting() {
        val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
        val e = Listed(gba, GameEntry(ContentRef("r"), "Zelda.gba", "Zelda", wheelRef = ContentRef("w.png"), folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)"))
        val g = backdropGames(listOf(e), BackdropArt.WHEEL).single()
        assertEquals("w.png", g.wheelUrl)
        assertEquals(BackdropArt.WHEEL, g.art)
        assertEquals(BackdropArt.BOX3D, backdropGames(listOf(e)).single().art)
    }
}
