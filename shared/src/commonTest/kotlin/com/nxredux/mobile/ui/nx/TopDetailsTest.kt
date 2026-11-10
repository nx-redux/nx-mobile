package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.scraper.StoredGameInfo
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test fun no_placeholder_text_exists() {
        assertNull(synopsisOf(null))
        assertNull(synopsisOf(stored(synopsis = "  ")))
        assertEquals("A hero.", synopsisOf(stored(synopsis = " A hero. ")))
    }

    private fun stored(synopsis: String? = null, genre: String? = null, year: String? = null, publisher: String? = null) =
        StoredGameInfo(synopsis, genre, year, publisher, null, null, 0L)

    private val day = 86_400_000L
    private val now = 10 * day

    @Test fun the_play_line_leaves_out_what_has_no_data() {
        assertEquals("41 m played · 25 sessions · last played today", playLine(GameInfo(now, 41 * 60_000L, null, 25L), now))
        assertEquals("3 h played · 1 session · last played yesterday", playLine(GameInfo(now - day, 3 * 3_600_000L, null, 1L), now))
        assertEquals("< 1 m played · 1 session", playLine(GameInfo(null, 20_000L, null, 1L), now))
        assertEquals("Last played today", playLine(GameInfo(now, null, null, null), now))   // alone: it starts the line
    }

    @Test fun a_game_never_played_says_so() {
        assertEquals("Never played", playLine(null, now))
        assertEquals("Never played", playLine(GameInfo(null, 0L, null, 0L), now))
        assertEquals("Never played", playLine(GameInfo(null, null, RaProgress(0, 10, "A"), null), now))
    }

    @Test fun genre_line_includes_publisher_and_omits_missing_parts() {
        assertEquals("Game Boy Advance", genreLine(null, "Game Boy Advance"))
        assertEquals("RPG · 2004 · Nintendo · Game Boy Advance", genreLine(stored(genre = "RPG", year = "2004", publisher = "Nintendo"), "Game Boy Advance"))
        assertEquals("2004 · Game Boy Advance", genreLine(stored(year = "2004", genre = " "), "Game Boy Advance"))
        assertEquals("Nintendo · Game Boy Advance", genreLine(stored(publisher = "Nintendo"), "Game Boy Advance"))
    }

    @Test fun achievements_count_unlocked_and_name_the_first_locked() {
        val list = listOf(achievement(1, RaAchievementState.UNLOCKED), achievement(2, RaAchievementState.PENDING), achievement(3, RaAchievementState.LOCKED), achievement(4, RaAchievementState.LOCKED))
        assertEquals(DetailsAchievements(2, 4, "A3"), detailsAchievements(DetailsBody.Rows(list, emptyMap()), null))
        val done = list.take(2)
        assertEquals(DetailsAchievements(2, 2, null), detailsAchievements(DetailsBody.Rows(done, emptyMap()), null))
    }

    // The rows still downloading (or never read): the menus' cached progress stands in; no set, no block.
    @Test fun achievements_fall_back_to_the_cached_progress_and_need_a_set() {
        assertEquals(DetailsAchievements(13, 61, "I Choose You!"), detailsAchievements(DetailsBody.Downloading, RaProgress(13, 61, "I Choose You!")))
        assertNull(detailsAchievements(DetailsBody.None, RaProgress(0, 0, null)))
        assertNull(detailsAchievements(DetailsBody.None, null))
    }

    @Test fun the_picture_is_the_resume_then_the_screenshot_then_the_box() {
        assertEquals(ShelfImage.Shot("resume"), detailsImage("resume", "shot", "box"))
        assertEquals(ShelfImage.Shot("shot"), detailsImage(null, "shot", "box"))
        assertEquals(ShelfImage.Box("box"), detailsImage(null, null, "box"))
        assertEquals(ShelfImage.Placeholder, detailsImage(null, null, null))
    }

    @Test fun only_a_resume_picture_has_a_caption() {
        assertEquals("Continue · yesterday", resumeCaption(ShelfImage.Shot("resume"), "resume", now - day, now))
        assertEquals("Continue", resumeCaption(ShelfImage.Shot("resume"), "resume", null, now))
        assertNull(resumeCaption(ShelfImage.Shot("shot"), null, null, now))
        assertNull(resumeCaption(ShelfImage.Box("box"), null, null, now))
    }

    // A small top screen (inside its padding) gives the text the width first, then drops the next goal.
    @Test fun a_small_top_screen_drops_the_picture_then_the_next_goal() {
        assertEquals(DetailsFit(picture = true, next = true, synopsis = true), detailsFit(560.dp, 320.dp))
        assertEquals(DetailsFit(picture = true, next = true, synopsis = false), detailsFit(400.dp, 220.dp))
        assertEquals(DetailsFit(picture = false, next = true, synopsis = true), detailsFit(399.dp, 320.dp))   // narrow: the text takes the width
        assertEquals(DetailsFit(picture = false, next = true, synopsis = false), detailsFit(560.dp, 219.dp))
        assertEquals(DetailsFit(picture = false, next = true, synopsis = false), detailsFit(560.dp, 176.dp))
        assertEquals(DetailsFit(picture = false, next = false, synopsis = false), detailsFit(560.dp, 175.dp))
    }

    @Test fun synopsis_is_dropped_first_on_short_screens() {
        assertEquals(DetailsFit(picture = true, next = true, synopsis = false), detailsFit(560.dp, 319.dp))
        assertEquals(DetailsFit(picture = true, next = true, synopsis = false), detailsFit(560.dp, 220.dp))
    }
}
