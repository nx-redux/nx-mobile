package com.nxredux.mobile.ui

import com.nxredux.mobile.scraper.FetchResult
import com.nxredux.mobile.scraper.ItemStatus
import com.nxredux.mobile.scraper.KeyResult
import com.nxredux.mobile.scraper.JobPhase
import com.nxredux.mobile.scraper.ResetResult
import com.nxredux.mobile.scraper.ScrapeEngine
import com.nxredux.mobile.scraper.ScrapeItem
import com.nxredux.mobile.scraper.inFlightOrWaiting
import com.nxredux.mobile.scraper.ScrapeState
import com.nxredux.mobile.scraper.ScreenScraperSource
import com.nxredux.mobile.scraper.SignInResult
import com.nxredux.mobile.scraper.SsQuota

/** The Artwork page's rows. */
enum class ArtworkAction { RESUME, FETCH_MISSING, FETCH_BY_CONSOLE, PROGRESS, SETTINGS }

data class ArtworkRow(val action: ArtworkAction, val label: String)

/** A main-menu console group on Fetch by console / Reset artwork: its name, its tags and how many of its games lack art. */
data class ArtworkConsole(val name: String, val tags: Set<String>, val missing: Int = 0)

/** Whose API key the Settings key prompt edits. */
enum class ArtKeyFor(val label: String) { SGDB("SteamGridDB"), TGDB("TheGamesDB") }

/**
 * Artwork → Settings' rows: Replace existing art, the optional art (2D box, wheel, mix), the art sources' accounts, Reset
 * artwork; [SECTION] a group's label and [GAP] the space before one, never selected.
 */
enum class ArtworkSettingKind {
    SECTION, GAP, REPLACE, BOX2D, WHEEL, MIX, SS_LOGIN, SS_LOGOUT, SGDB_KEY, SGDB_REMOVE, TGDB_KEY, TGDB_REMOVE, TGDB_LEFT, RESET;

    /** The On/Off rows that step on A / LEFT / RIGHT (when enabled). */
    val toggle: Boolean get() = this == REPLACE || this == BOX2D || this == WHEEL || this == MIX
}

/** [enabled] false: dimmed and A / LEFT / RIGHT do nothing. */
data class ArtworkSettingRow(val kind: ArtworkSettingKind, val label: String, val secondary: String? = null, val enabled: Boolean = true) {
    val selectable: Boolean get() = kind != ArtworkSettingKind.SECTION && kind != ArtworkSettingKind.GAP
}

/** What Reset artwork deletes: every logged file ([tags] null) or a console's. */
data class ResetScope(val label: String, val tags: Set<String>?)

/**
 * Which art sources this build and the user's keys turn on, for the Artwork page and Settings: [ss] the build's
 * credentials, [tgdb] TheGamesDB has the user's key and [tgdbBuilt] the engine has the source, [sgdb] the
 * build has SteamGridDB and [sgdbKey] the user's key is set; [labels] the enabled sources' names in fetch order (the
 * credit line).
 */
data class ArtSources(
    val ss: Boolean = false, val tgdb: Boolean = false, val sgdb: Boolean = false, val sgdbKey: Boolean = false,
    val labels: List<String> = emptyList(), val tgdbBuilt: Boolean = false,
)

/** The Artwork page's detail lines: counts, the job, stopped sources' notes, the credit. */
internal fun artworkDetails(
    available: Boolean, games: Int, withArt: Int, missing: Int, state: ScrapeState, sources: ArtSources,
): List<String> = buildList {
    add("${gamesLabel(games)} · $withArt with art · $missing missing")
    if (!available) { add("Artwork fetching isn't available in this build."); return@buildList }
    // No per-source account lines (sign-in, quota, keys): the Account page has them, and here they cost rows on a narrow screen.
    when (state.phase) {
        JobPhase.RUNNING -> add("Fetching ${state.done} / ${state.total} …")
        JobPhase.PAUSED -> add("Paused at ${state.done} / ${state.total}")
        else -> Unit
    }
    state.notes.values.forEach(::add)   // each stopped source's note
    artCredit(sources.labels)?.let(::add)
}

/** The credit line: "Source: a - b - c" over the enabled sources, short for a narrow screen; null with none. */
internal fun artCredit(sources: List<String>): String? =
    sources.takeIf { it.isNotEmpty() }?.joinToString(" - ", prefix = "Source: ")

internal fun quotaText(q: SsQuota): String = "${groupThousands(q.today)} / ${groupThousands(q.max)}"


/** The Artwork page's rows: [available] false leaves Settings only; Progress once there is a job, or with [progress] (beside a starting one). */
internal fun artworkRows(available: Boolean, state: ScrapeState, replace: Boolean, progress: Boolean = false): List<ArtworkRow> = buildList {
    if (!available) { add(ArtworkRow(ArtworkAction.SETTINGS, "Settings")); return@buildList }
    if (state.phase == JobPhase.PAUSED && state.items.any { it.status == ItemStatus.WAITING }) {
        add(ArtworkRow(ArtworkAction.RESUME, "Resume art fetch (${state.done}/${state.total})"))
    }
    add(ArtworkRow(ArtworkAction.FETCH_MISSING, if (replace) "Fetch all art" else "Fetch missing art"))
    add(ArtworkRow(ArtworkAction.FETCH_BY_CONSOLE, "Fetch by console"))
    if (state.phase != JobPhase.IDLE || progress) add(ArtworkRow(ArtworkAction.PROGRESS, "Progress"))
    add(ArtworkRow(ArtworkAction.SETTINGS, "Settings"))
}

/** Seconds per game for the time-left estimate (the 1.2 s pacing plus the downloads). */
private const val SECONDS_PER_GAME = 1.5

/** Progress's header: "d / t · ~M min left", or the phase when the job is not running; [starting]: its job is not there yet. */
internal fun progressHeader(state: ScrapeState, starting: Boolean = false): String {
    if (starting) return "Starting art fetch…"
    val count = "${state.done} / ${state.total}"
    return when (state.phase) {
        JobPhase.RUNNING -> {
            val left = state.total - state.done
            val minutes = ((left * SECONDS_PER_GAME + 59) / 60).toInt().coerceAtLeast(1)
            "$count · ~$minutes min left"
        }
        JobPhase.PAUSED -> "$count · paused" + (state.pauseReason?.let { ": $it" } ?: "")
        JobPhase.DONE -> "$count · done"
        JobPhase.CANCELLED -> "$count · cancelled"
        JobPhase.IDLE -> "No art fetch"
    }
}

/**
 * The progress page's rows: while the job runs or is paused, every game not yet done (a done game leaves the list, the
 * summary counts it); once it has ended only those to look at (not found, errors).
 */
internal fun artworkProgressRows(state: ScrapeState): List<ScrapeItem> =
    if (state.phase == JobPhase.DONE || state.phase == JobPhase.CANCELLED) state.items.filter { it.status == ItemStatus.NOT_FOUND || it.status == ItemStatus.ERROR }
    else state.items.filter { it.status != ItemStatus.DONE }

/** The progress page's second line: what the games came to, "12 fetched · 2 partial · 3 not found · 20 not fetched" (none left out). */
internal fun artworkSummary(state: ScrapeState): String {
    fun n(st: ItemStatus) = state.items.count { it.status == st }
    return listOf(
        n(ItemStatus.DONE) to "fetched", n(ItemStatus.PARTIAL) to "partial", n(ItemStatus.NOT_FOUND) to "not found",
        n(ItemStatus.ERROR) to (if (n(ItemStatus.ERROR) == 1) "error" else "errors"), n(ItemStatus.SKIPPED) to "skipped",
        state.items.count { it.status.inFlightOrWaiting && state.phase == JobPhase.CANCELLED } to "not fetched",
    ).filter { it.first > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }
}

internal fun itemStatusText(status: ItemStatus, reason: String?): String = when (status) {
    ItemStatus.WAITING -> "Waiting"
    ItemStatus.SEARCHING -> "Searching"
    ItemStatus.DOWNLOADING -> "Downloading"
    ItemStatus.DONE -> "Done" + (reason?.let { " · $it" } ?: "")       // the sources, "· no screenshot" for a hero
    ItemStatus.PARTIAL -> "Partial" + (reason?.let { " · $it" } ?: "")  // what is missing
    ItemStatus.NOT_FOUND -> "Not found"
    ItemStatus.ERROR -> "Error" + (reason?.let { ": $it" } ?: "")
    ItemStatus.SKIPPED -> "Skipped" + (reason?.let { ": $it" } ?: "")
}

/** The notice after the context menu's Fetch art. */
internal fun fetchResultText(r: FetchResult): String = when (r.status) {
    ItemStatus.DONE -> "Art fetched"
    ItemStatus.PARTIAL -> "Art fetched" + (r.reason?.let { " ($it)" } ?: "")
    ItemStatus.NOT_FOUND -> "No art found" + (r.reason?.let { " ($it)" } ?: "")
    ItemStatus.SKIPPED -> if (r.reason == ScrapeEngine.HAS_ART) "Already has art (Replace existing art is off)" else r.reason ?: "Couldn't fetch art"
    else -> r.reason ?: "Couldn't fetch art"
}

internal fun signInText(r: SignInResult): String = when (r) {
    SignInResult.OK -> "Signed in"
    SignInResult.BAD_LOGIN -> "ScreenScraper sign-in failed"
    SignInResult.OFFLINE -> "No connection"
    SignInResult.UNAVAILABLE -> ScreenScraperSource.UNAVAILABLE
    SignInResult.BUILD_REFUSED -> ScreenScraperSource.BUILD_REFUSED
}

/** The notice (OK) or error after a [key] check (SteamGridDB by default); the key is saved only on OK. */
internal fun keyResultText(r: KeyResult, key: ArtKeyFor = ArtKeyFor.SGDB): String = when (r) {
    KeyResult.OK -> "${key.label} key saved"
    KeyResult.BAD_KEY -> "${key.label} rejected this key"
    KeyResult.OFFLINE -> "No connection · key not saved"
    KeyResult.UNAVAILABLE -> "${key.label} is unavailable right now · key not saved"
}

/** The notice after Reset artwork, noting when it paused a running fetch of the same games ([ResetResult.pausedJob]). */
internal fun resetText(r: ResetResult): String =
    (if (r.deleted == 1) "Deleted 1 file" else "Deleted ${r.deleted} files") + (if (r.pausedJob) " · art fetch paused" else "")
