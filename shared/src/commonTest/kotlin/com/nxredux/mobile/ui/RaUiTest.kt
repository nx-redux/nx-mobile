package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.*
import com.nxredux.mobile.ui.nx.NxColors
import com.nxredux.mobile.ui.nx.NxRichRow
import com.nxredux.mobile.ui.nx.NxTone
import com.nxredux.mobile.ui.nx.raRarityPercent
import com.nxredux.mobile.ui.nx.toneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaUiTest {
    private val status = RaStatus("me", 1234, 3, 1_758_800_000_000L, 12)
    // The page's head: the user as the headline, points and the last sync below; the unlocks waiting on Sync now.
    @Test fun details_block() {
        val signedOut = RaState.Idle(RaStatus(null, null, 0, null, 0))
        assertEquals("me", raHeadline(RaState.Idle(status)))
        assertEquals(listOf("1,234 points", "Last sync: ${formatDateTime(1_758_800_000_000L)}"), raDetails(RaState.Idle(status)))
        assertEquals("Not signed in", raHeadline(signedOut))
        assertEquals(listOf("Sign in under Settings", "Last sync: Never"), raDetails(signedOut))
        assertEquals(listOf("1,234 points", "Last sync: Never"), raDetails(RaState.Idle(status.copy(pending = 0, lastSync = null))))
        assertEquals("Sync now (3)", raRowLabel(RaAction.SYNC, null, raPending(RaState.Idle(status))))
        assertEquals("Sync now", raRowLabel(RaAction.SYNC, null, raPending(RaState.Idle(status.copy(pending = 0)))))
        assertEquals("Sync now", raRowLabel(RaAction.SYNC, null, raPending(signedOut)))
    }
    @Test fun progress_lines_and_fractions() {
        fun syncing(step: RaSyncStep, done: Int = 0, total: Int = 0, current: String? = null) = RaState.Syncing(RaSyncProgress(step, done, total, current), status)
        assertEquals("Starting sync…", raProgressLine(syncing(RaSyncStep.STARTING))); assertNull(raProgressFraction(syncing(RaSyncStep.STARTING)))
        val sending = syncing(RaSyncStep.SUBMITTING, 2, 5, "Alleyway")
        assertEquals("Sending unlock 2 of 5", raProgressLine(sending)); assertEquals(0.4f, raProgressFraction(sending)); assertEquals("Alleyway", raProgressDetail(sending))
        assertEquals("Checking your account…", raProgressLine(syncing(RaSyncStep.ACCOUNT))); assertNull(raProgressFraction(syncing(RaSyncStep.ACCOUNT))); assertNull(raProgressDetail(syncing(RaSyncStep.ACCOUNT)))
        assertEquals("Reading game data 3 of 48", raProgressLine(syncing(RaSyncStep.READING, 3, 48)))
        assertEquals("Fetching progress: system 1 of 2", raProgressLine(syncing(RaSyncStep.PROGRESS, 1, 2, "12 games"))); assertEquals("12 games", raProgressDetail(syncing(RaSyncStep.PROGRESS, 1, 2, "12 games")))
        val updating = syncing(RaSyncStep.SESSIONS, 12, 48, "Tetris")
        assertEquals("Updating game 12 of 48", raProgressLine(updating)); assertEquals(0.25f, raProgressFraction(updating)); assertEquals("Tetris", raProgressDetail(updating))
        assertNull(raProgressDetail(RaState.Authenticating))
        assertNull(raProgressLine(RaState.Prefetching(12, 119, "Zelda", status)))   // a download keeps the page's rows (it has its own page)
        assertEquals("Signing in…", raProgressLine(RaState.Authenticating)); assertNull(raProgressLine(RaState.Idle(status)))
    }
    @Test fun tools_row_status_and_browser_strings() {
        assertEquals(RaSubtitle("12/40 unlocked · 2 pending sync", RaTone.PENDING), raGameSubtitle(RaGameView("h", 1, "G", null, 12, 40, 2)))
        assertEquals(RaSubtitle("0/40 unlocked", RaTone.LOCKED), raGameSubtitle(RaGameView("h", 1, "G", null, 0, 40, 0)))
        val g = RaGameView("h", 1, "G", null, 12, 40, 0)
        assertEquals("2 games · 24 of 80 unlocked", raConsoleSubtitle(com.nxredux.mobile.achievements.RaConsoleView("GB", "GB", listOf(g, g.copy(hash = "i")))))
        assertEquals("1 game · 12 of 40 unlocked", raConsoleSubtitle(com.nxredux.mobile.achievements.RaConsoleView("GB", "GB", listOf(g))))
        val a = RaAchievement(1, "A", "d", 5, "b", RaAchievementState.LOCKED, null, null, 0f, 0f, null)
        // No state words: the title, the points as its pill, the description under it (none when blank or worth no points).
        assertEquals(NxRichRow("A", "d", thumbnail = null, titleBadge = "5"), raAchievementRow(a, emptyMap()))
        assertEquals(NxRichRow("A", null, thumbnail = null, titleBadge = null), raAchievementRow(a.copy(description = " ", points = 0), emptyMap()))
        assertEquals(listOf(RaAction.ACHIEVEMENTS, RaAction.SYNC, RaAction.SETTINGS), raRows(RaState.Idle(status)))
        assertEquals(emptyList(), raRows(RaState.Authenticating))
    }
    @Test fun tones_map_to_list_tones_and_colours() {
        assertEquals(listOf(NxTone.SUCCESS, NxTone.WARNING, NxTone.DIM), listOf(RaTone.UNLOCKED, RaTone.PENDING, RaTone.LOCKED).map { it.toNxTone() })
        val c = NxColors()
        assertEquals(listOf(c.hint, c.success, c.warning), NxTone.entries.map { c.toneColor(it, selected = false) })
        assertEquals(listOf(c.secondarySelected, c.successSelected, c.warningSelected), NxTone.entries.map { c.toneColor(it, selected = true) })
    }
    @Test fun rarity_has_exactly_two_decimals() {
        assertEquals("12.50", raRarityPercent(12.5f)); assertEquals("3.14", raRarityPercent(3.14159f)); assertEquals("0.00", raRarityPercent(0f))
        assertEquals("96.42", raRarityPercent(96.42f)); assertEquals("100.00", raRarityPercent(100f)); assertEquals("6.69", raRarityPercent(6.69f))
    }
    @Test fun locked_only_filter() {
        val a = RaAchievement(1, "A", "d", 5, "b", RaAchievementState.LOCKED, null, null, 0f, 0f, null)
        val g = RaGameView("h", 1, "G", null, 1, 3, 0)
        val all = listOf(a, a.copy(id = 2, state = RaAchievementState.UNLOCKED), a.copy(id = 3, state = RaAchievementState.PENDING))
        assertEquals(all, visibleAchievements(Screen.RaGame(g, all)))
        assertEquals(listOf(1), visibleAchievements(Screen.RaGame(g, all, lockedOnly = true)).map { it.id })
    }
    @Test fun badge_model_comes_from_the_listing() {
        val a = RaAchievement(1, "T", "D", 5, "b", RaAchievementState.LOCKED, null, null, 0f, 0f, null)
        val listing = mapOf("b.png" to "content://u/b", "b_lock.png" to "content://u/bl")
        assertEquals("content://u/bl", raBadgeModel(listing, a))
        assertEquals("content://u/b", raBadgeModel(listing, a.copy(state = RaAchievementState.PENDING)))
        assertNull(raBadgeModel(emptyMap(), a))
    }

    @Test fun the_download_header_counts_and_says_the_time_left_or_the_phase() {
        fun d(phase: RaDownloadPhase, left: Long? = null, why: String? = null) = RaDownload(phase, 340, 12, emptyList(), secondsLeft = left, stopReason = why)
        assertEquals("12 / 340 · ~9 min left", raDownloadHeader(d(RaDownloadPhase.RUNNING, left = 540)))
        assertEquals("12 / 340 · ~1 min left", raDownloadHeader(d(RaDownloadPhase.RUNNING, left = 20)))
        assertEquals("12 / 340", raDownloadHeader(d(RaDownloadPhase.RUNNING)))   // the pace not known yet
        assertEquals("12 / 340 · paused", raDownloadHeader(d(RaDownloadPhase.PAUSED, left = 540)))
        assertEquals("12 / 340 · done", raDownloadHeader(d(RaDownloadPhase.DONE)))
        assertEquals("12 / 340 · cancelled", raDownloadHeader(d(RaDownloadPhase.CANCELLED)))
        assertEquals("12 / 340 · stopped: no connection", raDownloadHeader(d(RaDownloadPhase.STOPPED, why = "no connection")))
        assertEquals("Starting download…", raDownloadHeader(RaDownload(RaDownloadPhase.RUNNING, 0, 0, emptyList())))
        assertEquals("3 cached · 1 without achievements · 2 skipped", raDownloadSummary(RaDownload(RaDownloadPhase.DONE, 6, 6, emptyList(), cached = 3, unknown = 1, skipped = 2)))
    }
    @Test fun the_download_page_offers_done_once_it_has_ended() {
        for (p in listOf(RaDownloadPhase.DONE, RaDownloadPhase.CANCELLED, RaDownloadPhase.STOPPED))
            assertEquals(com.nxredux.mobile.ui.nx.Hint(com.nxredux.mobile.ui.nx.NxButton.A, "Done", com.nxredux.mobile.ui.nx.HintTouch.Button(primary = true)), raDownloadHints(p).first())
        assertTrue(raDownloadHints(RaDownloadPhase.RUNNING).none { it.label == "Done" })
    }
    @Test fun a_download_rows_status_names_the_step_or_why() {
        assertEquals("Waiting", raDownloadStatus(RaDownloadItem("Zelda", RaItemStatus.WAITING)))
        assertEquals("Hashing", raDownloadStatus(RaDownloadItem("Zelda", RaItemStatus.HASHING)))
        assertEquals("Downloading", raDownloadStatus(RaDownloadItem("Zelda", RaItemStatus.DOWNLOADING)))
        assertEquals("Skipped: couldn't read the file", raDownloadStatus(RaDownloadItem("Zelda", RaItemStatus.SKIPPED, "couldn't read the file")))
        assertEquals("Error: no connection", raDownloadStatus(RaDownloadItem("Zelda", RaItemStatus.ERROR, "no connection")))
    }
}
