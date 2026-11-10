package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.StateShot
import com.nxredux.mobile.ui.ANDROID_TAG
import com.nxredux.mobile.ui.FileFacts
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun details(
        entry: Listed = entry(), info: GameInfo? = null, shots: List<StateShot> = emptyList(), facts: FileFacts? = null,
        achievements: DetailsBody = DetailsBody.None, netplay: Boolean = false, raSet: Boolean = false,
    ) = TopDetails(entry, info, ResumeInfo.NONE, shots, facts, achievements, null, netplay, raSet)

    private val shot = StateShot(1, "file:///s1.png", 10L)
    private val rows = DetailsBody.Rows(listOf(achievement(1, RaAchievementState.LOCKED)), emptyMap())

    @Test fun lower_area_is_both_with_states_and_achievements() {
        assertEquals(LowerArea.BOTH, details(shots = listOf(shot), achievements = rows).lowerArea())
    }

    @Test fun lower_area_is_states_only() {
        assertEquals(LowerArea.STATES, details(shots = listOf(shot)).lowerArea())
        assertEquals(LowerArea.STATES, details(shots = listOf(shot), achievements = DetailsBody.Downloading).lowerArea())
    }

    @Test fun lower_area_is_achievements_only() {
        assertEquals(LowerArea.ACHIEVEMENTS, details(achievements = rows).lowerArea())
    }

    @Test fun lower_area_is_none_for_an_android_game() {
        val app = GameEntry(ContentRef("app:a.b"), "a.b", "Some Game", folder = ContentRef("app:"), sourceId = "android", relativeDir = "")
        val android = Listed(SystemFolder("Android", ANDROID_TAG, null, listOf(app), android = true), app)
        val info = GameInfo(1L, 60_000, null, 2L)
        assertEquals(LowerArea.NONE, details(entry = android, info = info, shots = listOf(shot), achievements = rows).lowerArea())
        assertEquals(LowerArea.NONE, details().lowerArea())   // never played, no set
    }

    @Test fun chips_show_play_history_file_facts_and_readiness() {
        val now = 10L * 86_400_000L
        val g = GameEntry(
            ContentRef("Home/Roms/GB/Zelda (USA).zip"), "Zelda (USA).zip", "Zelda",
            folder = ContentRef("Home/Roms/GB"), sourceId = GamePath.HOME, relativeDir = "Roms/GB",
        )
        val e = Listed(SystemFolder("Game Boy", "GB", CoreRegistry.forTag("GB"), emptyList()), g)
        val info = GameInfo(now - 86_400_000L, (12 * 60 + 40) * 60_000L, null, 23L)
        val d = details(entry = e, info = info, facts = FileFacts(512 * 1024, cheats = true, hiresPack = false), netplay = true)
        assertEquals(listOf("12h 40m · 23 sessions · Yesterday", "USA · ZIP 512 KB", "Cheats", "Netplay"), d.chips(now))
        assertEquals(listOf("ZIP"), details(entry = e.copy(game = g.copy(fileName = "Zelda.zip"))).chips(now))   // never played, no facts read
        assertEquals(listOf("1 session · Today", "Hi-res textures", "RetroAchievements"),
            details(entry = e.copy(game = g.copy(fileName = "Zelda")), info = GameInfo(now, null, null, 1L), facts = FileFacts(null, false, true), raSet = true).chips(now))
    }

    @Test fun no_placeholder_meta_in_release() {
        assertNull(gameMeta(placeholderMeta = false))
        val m = assertNotNull(gameMeta(placeholderMeta = true))
        assertTrue(m.synopsis.isNotBlank())
    }

    @Test fun achievements_list_locked_first_keeping_their_order() {
        val list = listOf(achievement(1, RaAchievementState.UNLOCKED), achievement(2, RaAchievementState.LOCKED), achievement(3, RaAchievementState.LOCKED))
        val body = assertIs<DetailsBody.Rows>(topAchievements(panel(RaPanelContent.Game("h", list, emptyMap()))))
        assertEquals(listOf(2, 3, 1), body.achievements.map { it.id })
    }
}
