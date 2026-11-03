package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.ANDROID_SOURCE
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameDetailsPaneTest {
    private fun entry(box2d: String? = null, screenshot: String? = null, label: String? = null): Listed {
        val g = GameEntry(
            ContentRef("Home/Roms/GBA/Mario.gba"), "Mario.gba", "Mario", box2dRef = box2d?.let(::ContentRef), screenshotRef = screenshot?.let(::ContentRef),
            folder = ContentRef("Home/Roms/GBA"), sourceId = GamePath.HOME, relativeDir = "Roms/GBA",
        )
        return Listed(SystemFolder("Game Boy Advance", "GBA", CoreRegistry.forTag("GBA"), emptyList()), g, label)
    }

    private fun achievement(id: Int, state: RaAchievementState) = RaAchievement(id, "A$id", "", 5, "", state, null, null, 0f, 0f, null)

    private fun panel(content: RaPanelContent) = RaPanel("path", "Mario", content)

    @Test fun the_header_has_the_name_art_console_and_play_time_and_no_clock() {
        val h = detailsHeader(entry(box2d = "box.png", screenshot = "shot.png", label = "Mario (USA)"), GameInfo(null, 3_600_000L, null), null)
        assertEquals("Mario (USA)", h.title)
        assertEquals("box.png", h.artPath)   // the launch panel's art choice ([panelArt]): a box before a screenshot
        assertEquals("GBA", h.consoleTag)
        assertEquals(3_600_000L, h.totalMs)
        assertEquals("", h.clockText)
        assertNull(h.batteryPercent)
        assertNull(h.ra)
    }

    // The header follows Backdrop art, then 3D → 2D → wheel; a ROM's screenshot is never its header, an Android game's is.
    @Test fun the_header_follows_backdrop_art_and_never_a_roms_screenshot() {
        val wheeled = entry(box2d = "box.png").let { it.copy(game = it.game.copy(wheelRef = ContentRef("w.png"))) }
        assertEquals("w.png", detailsHeader(wheeled, null, null, BackdropArt.WHEEL).artPath)
        assertEquals("box.png", detailsHeader(wheeled, null, null, BackdropArt.BOX3D).artPath)
        assertNull(detailsHeader(entry(screenshot = "shot.png"), null, null).artPath)
        val app = entry(screenshot = "file:///art/a.png").let { it.copy(game = it.game.copy(sourceId = ANDROID_SOURCE)) }
        assertEquals("file:///art/a.png", detailsHeader(app, null, null).artPath)
    }

    @Test fun no_art_and_no_info_draw_the_console_logo_and_no_time() {
        val h = detailsHeader(entry(), null, null)
        assertNull(h.artPath)
        assertEquals(0L, h.totalMs)
    }

    @Test fun the_count_comes_from_the_panes_list_pending_as_unlocked_else_the_cached_count() {
        val list = listOf(achievement(1, RaAchievementState.UNLOCKED), achievement(2, RaAchievementState.PENDING), achievement(3, RaAchievementState.LOCKED))
        val info = GameInfo(null, null, RaProgress(1, 10, null))
        assertEquals(2 to 3, detailsHeader(entry(), info, panel(RaPanelContent.Game("h", list, emptyMap()))).ra)
        assertEquals(1 to 10, detailsHeader(entry(), info, panel(RaPanelContent.Downloading)).ra)
        assertEquals(1 to 10, detailsHeader(entry(), info, panel(RaPanelContent.Game("h", emptyList(), emptyMap()))).ra)
        assertEquals(1 to 10, detailsHeader(entry(), info, null).ra)
    }

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
