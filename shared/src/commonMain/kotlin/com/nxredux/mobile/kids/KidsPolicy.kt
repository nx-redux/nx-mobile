package com.nxredux.mobile.kids

/** A daily play window in minutes of the day; never crosses midnight (fromMin < toMin). */
data class PlayWindow(val fromMin: Int, val toMin: Int)

/** Per ISO day (1 = Monday .. 7 = Sunday): the play window, or null when that day is locked. */
data class KidsSchedule(val days: Map<Int, PlayWindow?>) {

    fun withDay(day: Int, w: PlayWindow?): KidsSchedule = KidsSchedule(days + (day to w))

    fun encode(): String = (1..7).joinToString(";") { d ->
        val w = days[d]
        if (w == null) "$d=-" else "$d=${w.fromMin}-${w.toMin}"
    }

    companion object {
        val DEFAULT: KidsSchedule = KidsSchedule(
            (1..7).associateWith { d -> if (d >= 5) PlayWindow(540, 1200) else null },
        )

        fun decode(s: String?): KidsSchedule {
            if (s.isNullOrBlank()) return DEFAULT
            val parts = s.split(";")
            if (parts.size != 7) return DEFAULT
            val out = HashMap<Int, PlayWindow?>()
            for (p in parts) {
                val kv = p.split("=")
                if (kv.size != 2) return DEFAULT
                val day = kv[0].toIntOrNull() ?: return DEFAULT
                if (day !in 1..7 || day in out) return DEFAULT
                if (kv[1] == "-") {
                    out[day] = null
                } else {
                    val ft = kv[1].split("-")
                    if (ft.size != 2) return DEFAULT
                    val from = ft[0].toIntOrNull() ?: return DEFAULT
                    val to = ft[1].toIntOrNull() ?: return DEFAULT
                    if (from !in 0..1439 || to !in 1..1440 || from >= to) return DEFAULT
                    out[day] = PlayWindow(from, to)
                }
            }
            return KidsSchedule((1..7).associateWith { out[it] })
        }
    }
}

/** The local clock, supplied by the caller: epoch ms, ISO weekday and ms since local midnight. */
data class LocalNow(val epochMs: Long, val isoDay: Int, val msOfDay: Long)

data class NextOpen(val isoDay: Int, val fromMin: Int, val daysAhead: Int)

sealed interface KidsMode {
    data object Off : KidsMode
    data class Locked(val next: NextOpen?) : KidsMode
    data class Open(val untilMs: Long) : KidsMode
    data object Parent : KidsMode
}

enum class UnlockChip(val label: String, val minutes: Int?) {
    MIN30("30 min", 30),
    HOUR1("1 hour", 60),
    HOUR2("2 hours", 120),
    TODAY("Rest of today", null),
}

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 86_400_000L

fun kidsMode(
    enabled: Boolean,
    parent: Boolean,
    now: LocalNow,
    schedule: KidsSchedule,
    unlockUntilMs: Long,
): KidsMode {
    if (!enabled) return KidsMode.Off
    if (parent) return KidsMode.Parent

    val midnight = now.epochMs - now.msOfDay
    val today = schedule.days[now.isoDay]
    val windowEnd = if (today != null &&
        now.msOfDay >= today.fromMin * MINUTE_MS && now.msOfDay < today.toMin * MINUTE_MS
    ) midnight + today.toMin * MINUTE_MS else null
    val unlockEnd = if (unlockUntilMs > now.epochMs) unlockUntilMs else null

    val end = when {
        windowEnd != null && unlockEnd != null -> maxOf(windowEnd, unlockEnd)
        windowEnd != null -> windowEnd
        unlockEnd != null -> unlockEnd
        else -> null
    }
    if (end != null) return KidsMode.Open(end)
    return KidsMode.Locked(nextOpen(now, schedule))
}

private fun nextOpen(now: LocalNow, schedule: KidsSchedule): NextOpen? {
    val today = schedule.days[now.isoDay]
    if (today != null && today.fromMin * MINUTE_MS > now.msOfDay) {
        return NextOpen(now.isoDay, today.fromMin, 0)
    }
    for (ahead in 1..7) {
        val day = (now.isoDay - 1 + ahead) % 7 + 1
        val w = schedule.days[day] ?: continue
        return NextOpen(day, w.fromMin, ahead)
    }
    return null
}

/** The kid-facing warning, 10 minutes before an end ([warnAtMs]): in a game, on the menu or as a notification. */
const val KIDS_WARNING = "Games close in 10 minutes. Save your game now."

/** The warning shows 10 minutes before an end. */
fun warnAtMs(untilMs: Long): Long = untilMs - 600_000L

fun unlockUntil(now: LocalNow, chip: UnlockChip): Long {
    val minutes = chip.minutes
    return if (minutes != null) now.epochMs + minutes * MINUTE_MS
    else now.epochMs - now.msOfDay + DAY_MS
}

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** [minOfDay] as the kid-facing copy writes a time: "9:00 AM". */
fun timeLabel(minOfDay: Int): String {
    val h24 = minOfDay / 60
    val m = minOfDay % 60
    val h12 = when (val h = h24 % 12) { 0 -> 12; else -> h }
    val suffix = if (h24 < 12) "AM" else "PM"
    return "$h12:${m.toString().padStart(2, '0')} $suffix"
}

fun nextOpenLabel(n: NextOpen): String {
    val whenWord = when (n.daysAhead) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> DAY_NAMES[(n.isoDay - 1).coerceIn(0, 6)]
    }
    return "Games open $whenWord ${timeLabel(n.fromMin)}"
}
