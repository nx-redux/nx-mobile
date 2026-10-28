package com.nxredux.mobile.cheats.db

/** The newest commit that touched `cht` in libretro-database: its [sha] and [committedAt] (epoch ms; null when GitHub's date did not parse). */
data class CheatDbCommit(val sha: String, val committedAt: Long?)

/** The two date formats the update check compares, parsed without a date library (common code): HTTP's and GitHub's. */
object CheatDbDates {
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val HTTP = Regex("""^(?:[A-Za-z]{3},\s*)?(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+(?:GMT|UTC|Z)$""")
    private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?Z$""")

    /** A Last-Modified header (`Wed, 21 Aug 2024 10:00:00 GMT`) in epoch ms, or null. */
    fun parseHttp(text: String): Long? {
        val m = HTTP.find(text.trim()) ?: return null
        val month = MONTHS.indexOf(m.groupValues[2].lowercase()) + 1
        if (month == 0) return null
        return epoch(m.groupValues[3].toInt(), month, m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt())
    }

    /** A GitHub commit date (`2026-10-20T10:00:00Z`) in epoch ms, or null. */
    fun parseIso(text: String): Long? {
        val g = ISO.find(text.trim())?.groupValues ?: return null
        return epoch(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
    }

    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        // Days from civil (Howard Hinnant): proleptic Gregorian, no leap-second handling.
        val yy = if (mo <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (mo + (if (mo > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era.toLong() * 146097 + doe - 719468
        return ((days * 24 + h) * 60 + mi) * 60_000 + s * 1000L
    }

    /**
     * Whether a cheats.zip with Last-Modified [stamp] was built at or after [commit], so it holds its cheats. Null when
     * either date can't be read: the caller then can't tell.
     */
    fun builtAfter(stamp: String?, commit: CheatDbCommit): Boolean? {
        val built = stamp?.let(::parseHttp) ?: return null
        val committed = commit.committedAt ?: return null
        return built >= committed
    }
}
