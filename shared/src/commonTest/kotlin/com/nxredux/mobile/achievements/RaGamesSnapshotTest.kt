package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaGamesSnapshotTest {
    private val games = listOf(
        RaGameView("a".repeat(32), 1, "Alpha", "art/a.png", 3, 10, 1, "Game Boy Advance", "GBA"),
        RaGameView("b".repeat(32), 2, "Beta", null, 0, 4, 0, RaConsoleGroups.OTHER, null),
    )

    @Test fun a_snapshot_decodes_to_the_games_it_was_made_from() {
        assertEquals(games, RaGamesSnapshotCodec.decode(RaGamesSnapshotCodec.encode("me", games), "me"))
        assertEquals(emptyList(), RaGamesSnapshotCodec.decode(RaGamesSnapshotCodec.encode("me", emptyList()), "me"))
    }

    @Test fun another_version_another_user_or_junk_is_no_snapshot() {
        val text = RaGamesSnapshotCodec.encode("me", games)
        assertNull(RaGamesSnapshotCodec.decode(text.replace("\"version\":${RaGamesSnapshotCodec.VERSION}", "\"version\":${RaGamesSnapshotCodec.VERSION + 1}"), "me"))
        assertNull(RaGamesSnapshotCodec.decode(text, "someone else"))
        assertNull(RaGamesSnapshotCodec.decode("{not json", "me"))
        assertNull(RaGamesSnapshotCodec.decode("", "me"))
        assertNull(RaGamesSnapshotCodec.decode(null, "me"))
    }

    @Test fun a_refreshed_list_keeps_the_selection_on_the_same_key() {
        val old = listOf("a", "b", "c")
        assertEquals(2, RaConsoleGroups.keptIndex(old, 1, listOf("new", "a", "b", "c")) { it })   // moved down by one added above
        assertEquals(0, RaConsoleGroups.keptIndex(old, 2, listOf("c")) { it })
        assertEquals(1, RaConsoleGroups.keptIndex(old, 2, listOf("a", "b")) { it })                // gone: the row clamped
        assertEquals(0, RaConsoleGroups.keptIndex(old, 0, emptyList<String>()) { it })
    }
}
