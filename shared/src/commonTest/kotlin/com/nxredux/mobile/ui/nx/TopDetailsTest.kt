package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TopDetailsTest {
    private fun entry(): Listed {
        val g = GameEntry(
            ContentRef("Home/Roms/GBA/Mario.gba"), "Mario.gba", "Mario",
            folder = ContentRef("Home/Roms/GBA"), sourceId = GamePath.HOME, relativeDir = "Roms/GBA",
        )
        return Listed(SystemFolder("Game Boy Advance", "GBA", CoreRegistry.forTag("GBA"), emptyList()), g)
    }

    private fun achievement(id: Int, state: RaAchievementState) = RaAchievement(id, "A$id", "", 5, "", state, null, null, 0f, 0f, null)

    private fun panel(content: RaPanelContent) = RaPanel("path", "Mario", content)

    @Test fun only_the_highlighted_games_achievements_count() {
        val e = entry()
        val mine = RaPanel(e.game.path, "Mario", RaPanelContent.Downloading)
        assertEquals(mine, detailsRaFor(e, mine))
        assertNull(detailsRaFor(e, mine.copy(path = "home/Roms/GBA/Zelda.gba")))
        assertNull(detailsRaFor(e, mine.copy(path = null)))
        assertNull(detailsRaFor(e, null))
    }

    @Test fun the_body_lists_a_games_achievements_says_downloading_else_nothing() {
        val list = listOf(achievement(1, RaAchievementState.LOCKED))
        val badges = mapOf("b" to "file://b.png")
        assertEquals(DetailsBody.Rows(list, badges), detailsBody(panel(RaPanelContent.Game("h", list, badges))))
        assertEquals(DetailsBody.Downloading, detailsBody(panel(RaPanelContent.Downloading)))
        assertEquals(DetailsBody.None, detailsBody(null))   // signed out
        assertEquals(DetailsBody.None, detailsBody(panel(RaPanelContent.NoData)))
        assertEquals(DetailsBody.None, detailsBody(panel(RaPanelContent.Loading)))
        assertEquals(DetailsBody.None, detailsBody(panel(RaPanelContent.Failed("no connection"))))
        assertEquals(DetailsBody.None, detailsBody(panel(RaPanelContent.Game("h", emptyList(), emptyMap()))))
    }
}
