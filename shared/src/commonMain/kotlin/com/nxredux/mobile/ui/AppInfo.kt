package com.nxredux.mobile.ui

import com.nxredux.mobile.update.UpdateState

/**
 * What Settings -> System -> About shows that never changes while the app runs (the host fills it in). [commit]: the build's short
 * git commit, `-dirty` when its tree had uncommitted changes; [commitDate]: that commit's ISO-8601 committer date ("" when
 * unknown), the Released row; [pageSizeKb]: the memory page size, 0 when unknown. The GPU comes later ([AppDeps.deviceGpu]).
 */
data class AppInfo(
    val version: String,
    val versionCode: Long = 0,
    val commit: String = UNKNOWN_INFO,
    val commitDate: String = "",
    val platform: String = UNKNOWN_INFO,
    val device: String = UNKNOWN_INFO,
    val chip: String = UNKNOWN_INFO,
    val pageSizeKb: Int = 0,
)

/** A value the host could not read. */
internal const val UNKNOWN_INFO = "Unknown"

/** The GPU row before its query answers. */
internal const val PENDING_INFO = "…"

/** The About page's first row: the app's name, its value the version. */
internal const val APP_NAME = "NX Redux"

/**
 * The About rows, label to value, in order. [gpu]: the queried renderer, null while the query runs. Released is the
 * build commit's date in the device locale ([formatInstallDate]), not the build time.
 */
internal fun aboutRows(info: AppInfo, gpu: String?): List<Pair<String, String>> = listOf(
    APP_NAME to "${info.version} (${info.versionCode})",
    "Build" to info.commit,
    "Released" to (isoEpochMillis(info.commitDate)?.let(::formatInstallDate) ?: UNKNOWN_INFO),
    "Platform" to info.platform,
    "Device" to info.device,
    "Chip" to info.chip,
    "GPU" to (gpu ?: PENDING_INFO),
    "Page size" to if (info.pageSizeKb > 0) "${info.pageSizeKb} KB" else UNKNOWN_INFO,
)

/**
 * Copy device info's text: `NX Redux <version> (<code>) <commit>` on the first line, then one `Label: value` line
 * per other About row (Build is already on the first line).
 */
internal fun aboutCopyText(info: AppInfo, gpu: String?): String {
    val rows = aboutRows(info, gpu)
    val first = "$APP_NAME ${rows[0].second} ${info.commit}"
    return (listOf(first) + rows.drop(2).map { (label, value) -> "$label: $value" }).joinToString("\n")
}

/** About's last row, the updater's state: what A does, or what it waits on (a Waiting install shows its reason). */
internal fun appUpdateLabel(state: UpdateState): String = when (state) {
    UpdateState.Idle -> CHECK_LABEL
    UpdateState.Checking -> "Checking for updates…"
    is UpdateState.UpToDate -> "Up to date (${state.version})"
    is UpdateState.Available -> "Update to ${state.release.version}"
    is UpdateState.Downloading -> "Downloading ${if (state.total > 0) (state.done * 100 / state.total).coerceIn(0, 100) else 0}%"
    is UpdateState.Waiting -> state.reason
    is UpdateState.Installing -> "Installing…"
    is UpdateState.Failed -> state.text
}

/**
 * The A hint for About's update row, from its [label] ([appUpdateLabel]): Check before a check found a release (a failed
 * check included), Update once one did (a failed download included); null while a check, download or install runs.
 */
internal fun appUpdateHint(label: String): String? = when {
    label == CHECK_LABEL || label.startsWith("Up to date (") || label == "Couldn't reach GitHub" -> "Check"
    label.startsWith("Update to ") || label == "Update failed" -> "Update"
    else -> null
}

private const val CHECK_LABEL = "Check for updates"

/**
 * Epoch ms of an ISO-8601 date-time with an offset (`2026-10-01T12:34:56+08:00`, `…Z`), as `git log --format=%cI` prints
 * it; fractional seconds are ignored. Null when [iso] is not one.
 */
internal fun isoEpochMillis(iso: String): Long? {
    val m = ISO_DATE_TIME.matchEntire(iso.trim()) ?: return null
    val (y, mo, d, h, mi, s) = m.destructured
    val month = mo.toInt(); val day = d.toInt()
    if (month !in 1..12 || day !in 1..31 || h.toInt() > 23 || mi.toInt() > 59 || s.toInt() > 60) return null
    val offset = m.groupValues[8].let { z ->
        if (z == "Z") 0 else (if (z[0] == '-') -1 else 1) * (z.substring(1, 3).toInt() * 60 + z.substring(4, 6).toInt())
    }
    val seconds = daysFromCivil(y.toInt(), month, day) * 86_400L + h.toInt() * 3_600L + mi.toInt() * 60L + s.toInt() - offset * 60L
    return seconds * 1_000L
}

private val ISO_DATE_TIME = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(\.\d+)?(Z|[+-]\d{2}:\d{2})""")

/** Days from 1970-01-01 to the proleptic Gregorian [y]-[m]-[d] (Howard Hinnant's days_from_civil). */
private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    val yy = if (m <= 2) y - 1 else y
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}
