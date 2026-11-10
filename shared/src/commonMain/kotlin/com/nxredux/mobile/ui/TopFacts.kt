package com.nxredux.mobile.ui

/** [REQUIRED]: the console cannot start without it; [OPTIONAL]: only an optional BIOS file is missing. */
enum class BiosMissing { REQUIRED, OPTIONAL }

/** The platform's reads for the stats page; never throws, a failed read is a null answer. */
interface TopFactsPlatform {
    /** Null when nothing is missing (or it cannot be told). */
    suspend fun biosMissing(tag: String): BiosMissing?
}
