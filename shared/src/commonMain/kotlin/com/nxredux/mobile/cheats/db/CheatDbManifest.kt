package com.nxredux.mobile.cheats.db

/**
 * What the Cheats & Patches tool installed: the server's Last-Modified stamp (for update checks), when, and
 * every file written (paths relative to the sd root), so Remove deletes exactly those and nothing the user
 * added. [tags] are the table rows it was installed for (null when the file has none): when the app's table changes,
 * an update check reinstalls even though the server's archive is the same. One text file at [PATH]; nx-redux keeps the
 * same information as a manifest + `db_lastmod` pair. [stamp] is libretro's cheats.zip's, [patchesStamp] PCSX2's
 * patches.zip's (null when it was not installed, as in a manifest from before it was a source).
 */
data class CheatDbManifest(
    val stamp: String, val installedAt: Long, val files: List<String>, val tags: List<String>? = null, val patchesStamp: String? = null,
) {
    /** The stamp [source]'s archive was installed with; [UNKNOWN_STAMP] when it wasn't. */
    fun stampOf(source: CheatDbSource): String = when (source) {
        CheatDbSource.LIBRETRO -> stamp
        CheatDbSource.PCSX2 -> patchesStamp ?: UNKNOWN_STAMP
    }

    /** Files per tag (`Cheats/<TAG>/…`), in first-seen order. */
    val countsByTag: Map<String, Int> get() = files.groupingBy { tagOf(it) }.eachCount()
    val systems: Int get() = countsByTag.size
    val fileCount: Int get() = files.size
    /** nx-redux's rule: installed means the manifest exists and lists something. */
    val installed: Boolean get() = files.isNotEmpty()

    /** The on-disk text: [HEADER], the `stamp`, `installed`, `tags` and `patches_stamp` keys, then one file path per line. */
    fun render(): String = buildString {
        append(HEADER).append('\n')
        append("stamp = ").append(stamp).append('\n')
        append("installed = ").append(installedAt).append('\n')
        tags?.let { append("tags = ").append(it.joinToString(",")).append('\n') }
        patchesStamp?.let { append("patches_stamp = ").append(it).append('\n') }
        files.forEach { append(it).append('\n') }
    }

    companion object {
        const val PATH = ".userdata/shared/xtras/cheatdb.manifest"
        const val HEADER = "# nx-mobile cheatdb 1"
        /** Stored when the server sent no Last-Modified: an update check then always downloads. */
        const val UNKNOWN_STAMP = "installed"

        /** Null when the header line is missing or names another version. Blank and `#` lines are skipped. */
        fun parse(text: String): CheatDbManifest? {
            val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            if (lines.firstOrNull()?.trim() != HEADER) return null
            var stamp = UNKNOWN_STAMP
            var installedAt = 0L
            var tags: List<String>? = null
            var patchesStamp: String? = null
            val files = ArrayList<String>()
            for (raw in lines.drop(1)) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val eq = line.indexOf(" = ")
                // Key lines never contain a slash; every file path does (`Cheats/<TAG>/…`).
                if (eq > 0 && !line.contains('/')) {
                    val value = line.substring(eq + 3)
                    when (line.substring(0, eq)) {
                        "stamp" -> stamp = value
                        "installed" -> installedAt = value.toLongOrNull() ?: 0L
                        "tags" -> tags = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        "patches_stamp" -> patchesStamp = value
                    }
                    continue
                }
                files += line
            }
            return CheatDbManifest(stamp, installedAt, files, tags, patchesStamp)
        }

        private fun tagOf(path: String): String = path.split('/').getOrNull(1) ?: ""
    }
}
