package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals

class GameInfoTextTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test fun whenIsRelative() {
        assertEquals("Today", whenText(now, now - 3_600_000))
        assertEquals("Yesterday", whenText(now, now - day))
        assertEquals("2 days ago", whenText(now, now - 2 * day))
        assertEquals("6 days ago", whenText(now, now - 6 * day))
        assertEquals("Last week", whenText(now, now - 7 * day))
        assertEquals("2 weeks ago", whenText(now, now - 14 * day))
        assertEquals("Last month", whenText(now, now - 30 * day))
        assertEquals("3 months ago", whenText(now, now - 95 * day))
        assertEquals("Last year", whenText(now, now - 365 * day))
        assertEquals("2 years ago", whenText(now, now - 800 * day))
        assertEquals("Today", whenText(now, now + day))   // a clock step back is not "in the future"
    }

    @Test fun theNextLabelChangeIsTheEarliestWholeDaySincePlay() {
        assertEquals(now - 3_600_000 + day, nextWhenChange(now, listOf(now - 3_600_000)))                 // "Today" turns "Yesterday"
        assertEquals(now - 3 * day + 4 * day, nextWhenChange(now, listOf(now - 3 * day)))                 // exactly on a boundary: the next one
        assertEquals(now - 2 * day + 3 * day - 1, nextWhenChange(now, listOf(now - 10 * day, now - 2 * day - 1)))   // the soonest game
        assertEquals(now + day + day, nextWhenChange(now, listOf(now + day)))                             // a future time stays "Today" a day past it
        assertEquals(null, nextWhenChange(now, emptyList()))
    }

    @Test fun timeUsesTheGameTrackerFormatSoShortSessionsShow() {
        // 1 min 31 s must not round to "0.0 h": the menu shows what Game Tracker shows.
        assertEquals(listOf("Today - 1m 31s"), gameInfoLines(GameInfo(now, 91_000, null), now, full = true).map { it.text })
        assertEquals(listOf("Today - 12h 24m"), gameInfoLines(GameInfo(now, (12.4 * 3_600_000).toLong(), null), now, full = true).map { it.text })
    }

    @Test fun segmentsAreWhatIsKnownInOrder() {
        val ra = RaProgress(0, 35, "That Was Easy")
        val both = GameInfo(now, 91_000, ra)
        // Shown as "Today - 1m 31s · 0 of 35 · Next: That Was Easy" (the trophy before "0 of 35").
        assertEquals(
            listOf(InfoLine(InfoKind.TIME, "Today - 1m 31s"), InfoLine(InfoKind.ACHIEVEMENTS, "0 of 35"), InfoLine(InfoKind.NEXT, "Next: That Was Easy")),
            gameInfoLines(both, now, full = true),
        )
        assertEquals("Today - 1m 31s · 0 of 35 · Next: That Was Easy", gameInfoLines(both, now, full = true).joinToString(INFO_SEPARATOR) { it.text })
        assertEquals(listOf("Today - 1m 31s", "0 of 35"), gameInfoLines(both, now, full = false).map { it.text })   // a grid tile: no next goal
        assertEquals(listOf("0 of 35", "Next: That Was Easy"), gameInfoLines(GameInfo(null, null, ra), now, full = true).map { it.text })
        assertEquals(listOf("2 days ago"), gameInfoLines(GameInfo(now - 2 * day, null, null), now, full = true).map { it.text })
        assertEquals(listOf("39 of 39"), gameInfoLines(GameInfo(null, null, RaProgress(39, 39, null)), now, full = true).map { it.text })
        assertEquals(emptyList(), gameInfoLines(null, now, full = true))
        assertEquals(emptyList(), gameInfoLines(GameInfo(null, null, null), now, full = true))
    }

    // A genre segment is drawn as pills: its genres apart, as many as fit the line (the first always), one where nothing is fitted.
    @Test fun a_genre_segment_splits_into_the_genres_that_fit() {
        assertEquals(listOf("Sports", "Soccer"), genreParts("Sports / Soccer"))
        assertEquals(listOf("Action"), genreParts(" Action "))
        assertEquals(listOf("Platform", "Shooter"), genreParts("Platform, Shooter,"))
        assertEquals(2, genresThatFit(listOf(60f, 50f, 40f), gap = 4f, room = 120f))   // 60 + 4 + 50 fits, the third does not
        assertEquals(3, genresThatFit(listOf(60f, 50f, 40f), gap = 4f, room = 158f))
        assertEquals(1, genresThatFit(listOf(60f, 50f), gap = 4f, room = 10f))        // the first stays, cut short by the line
        assertEquals(0, genresThatFit(emptyList(), gap = 4f, room = 100f))
        val time = InfoLine(InfoKind.TIME, "Today - 1m")
        assertEquals(listOf(time, InfoLine(InfoKind.GENRE, "Sports")), firstGenre(listOf(time, InfoLine(InfoKind.GENRE, "Sports / Soccer"))))
    }

    // No achievements to show: the stored genre and release take their place; Details shows them for every game.
    @Test fun a_game_without_achievements_shows_its_genre_and_release() {
        val now = 1_000_000_000_000L
        val meta = com.nxredux.mobile.scraper.StoredGameInfo(synopsis = null, genre = "Action", year = "1989", publisher = "Nintendo", lang = null, ssId = null, fetchedAt = 0)
        val ra = RaProgress(0, 35, "That Was Easy")
        assertEquals(listOf("Today - 1m 31s", "Action", "1989 · Nintendo"), gameInfoLines(GameInfo(now, 91_000, null), now, full = true, meta).map { it.text })
        assertEquals(listOf(InfoKind.GENRE, InfoKind.RELEASE), gameInfoLines(null, now, full = true, meta).map { it.kind })   // never played
        assertEquals(listOf("Game", "1989"), gameInfoLines(null, now, full = true, meta.copy(genre = " ", publisher = null), noGenre = NO_GENRE).map { it.text })   // no genre: the fixed word
        assertEquals(listOf("0 of 35", "Next: That Was Easy"), gameInfoLines(GameInfo(null, null, ra), now, full = true, meta).map { it.text })
        assertEquals(listOf("Action", "1989 · Nintendo"), gameInfoLines(GameInfo(null, null, ra), now, full = true, meta, metaAlways = true).map { it.text })
        assertEquals(listOf("Game"), gameInfoLines(GameInfo(null, null, ra), now, full = true, null, metaAlways = true, noGenre = NO_GENRE).map { it.text })   // nothing stored yet
        assertEquals(emptyList(), gameInfoLines(null, now, full = true, null))   // not shown (Extra info hidden): no pill
        assertEquals(listOf("0 of 35"), gameInfoLines(GameInfo(null, null, ra), now, full = false, meta, metaAlways = true).map { it.text })   // a grid tile keeps its count
        assertEquals(emptyList(), gameInfoLines(null, now, full = false, meta))
    }

    @Test fun switcherSubtitleNamesTheSystemAndThePosition() {
        assertEquals("Super Nintendo ES   2 / 6", switcherSubtitle("Super Nintendo ES", 1, 6))
    }

    private fun system(tag: String, games: Int) = SystemFolder("Super Nintendo ES", tag, null,
        List(games) { GameEntry(ContentRef("$tag/$it"), "$it.sfc", "$it", folder = ContentRef(tag), sourceId = "home", relativeDir = "") })

    @Test fun aConsoleRowCountsTheGamesOfEveryTag() {
        // SFC and SUPA are one row; opening it lists both tags' games.
        assertEquals(listOf(InfoLine(InfoKind.COUNT, "24 games")), consoleInfoLines(MainRow.System(listOf(system("SFC", 20), system("SUPA", 4)))))
        assertEquals(listOf(InfoLine(InfoKind.COUNT, "1 game")), consoleInfoLines(MainRow.System(system("SFC", 1))))
    }

    @Test fun aCollectionRowShowsItsCountAndNothingWhenUnread() {
        assertEquals(listOf(InfoLine(InfoKind.COUNT, "3 games")), collectionInfoLines(MainRow.Collection("RPGs", count = 3)))
        assertEquals(listOf(InfoLine(InfoKind.COUNT, "1 game")), collectionInfoLines(MainRow.Collection("Co-op", count = 1)))
        assertEquals(emptyList(), collectionInfoLines(MainRow.Collection("Unread", count = null)))   // an unreadable file: no count, not 0
    }

    // Layouts → Extra info hidden: the launcher menus' info map is empty, so every game's info lines (the
    // time, the achievements, the next goal; a tile's and a caption's alike) are none, while a console's count stays.
    @Test fun extraInfoHiddenLeavesNoGameInfoButKeepsCounts() {
        val g = system("SFC", 2).games[0]
        val info = mapOf(g.path to GameInfo(now - day, 3_600_000L, RaProgress(3, 10, "First")))
        assertEquals(3, gameInfoFor(launcherGameInfo(info, extraInfo = true), now, g, full = true).size)
        assertEquals(info, launcherGameInfo(info, extraInfo = true))
        assertEquals(emptyMap(), launcherGameInfo(info, extraInfo = false))
        assertEquals(emptyList(), gameInfoFor(launcherGameInfo(info, extraInfo = false), now, g, full = true))
        assertEquals(emptyList(), gameInfoFor(launcherGameInfo(info, extraInfo = false), now, g, full = false))
        val rows = listOf<MainRow>(MainRow.Pin(Listed(system("SFC", 2), g)))
        assertEquals(emptyList(), rowTileInfo(rows, launcherGameInfo(info, extraInfo = false), now)(0))
        assertEquals(listOf(InfoLine(InfoKind.COUNT, "2 games")), consoleInfoLines(MainRow.System(system("SFC", 2))))
    }
}
