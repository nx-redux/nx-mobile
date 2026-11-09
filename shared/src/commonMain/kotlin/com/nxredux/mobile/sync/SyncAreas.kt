package com.nxredux.mobile.sync

/**
 * What Device Sync carries in `.userdata/shared`: save states (`<TAG>-<core>/`) and slot pictures/markers
 * (`.minui/<TAG>/`), by the handheld's own rule list, copied verbatim and in order, since rsync takes the first rule
 * that matches. `Saves/` travels whole, minus macOS metadata and interrupted writes' temp files. Export/Import use the same rule
 * ([com.nxredux.mobile.storage.SaveArchive.areaOf]).
 */
object SyncAreas {
    /** The `.userdata/shared` filter rules, `+ `/`- ` prefixed, first match wins (see the object's KDoc). */
    val SHARED_RULES: List<String> = listOf(
        "- ._*",
        "- .DS_Store",
        "- /music-player/",
        "- /media-player/",
        "- /PORTS-portmaster/",
        "- /*-*/config/",
        "- /DC-flycast/*/",
        "- /DC-flycast/nx-seeded.txt",
        "- /N64-mupen64plus/",
        "- /.minui/placeholders/",
        "+ /.minui/",
        "+ /.minui/*/",
        "+ /.minui/*/**",
        "- /.*",
        "+ /*-*/",
        "+ /*-*/**",
        "- *",
    )
    /** The `Saves/` exclude patterns, the rsync side of [isJunk]. */
    val SAVES_EXCLUDES: List<String> = listOf("._*", ".DS_Store", "*.tmp", "*.part")

    /** [SHARED_RULES] as rsync client arguments, one `--filter=` each, in order. */
    fun sharedFilterArgs(): List<String> = SHARED_RULES.map { "--filter=$it" }
    /** [SAVES_EXCLUDES] as rsync client arguments, one `--exclude=` each. */
    fun savesExcludeArgs(): List<String> = SAVES_EXCLUDES.map { "--exclude=$it" }
    /** rsyncd.conf `filter =` value: the daemon splits it back into rules on spaces. */
    fun daemonSharedFilter(): String = SHARED_RULES.joinToString(" ")
    fun daemonSavesExclude(): String = SAVES_EXCLUDES.joinToString(" ")

    private class Rule(val include: Boolean, val anchored: Boolean, val dirOnly: Boolean, val regex: Regex)

    private val rules: List<Rule> = SHARED_RULES.map { r ->
        var p = r.substring(2)
        val dirOnly = p.endsWith('/').also { if (it) p = p.dropLast(1) }
        val anchored = p.startsWith('/').also { if (it) p = p.drop(1) }
        require(anchored || '/' !in p) { "an unanchored rule with a slash needs rsync's tail matching: $r" }
        Rule(r[0] == '+', anchored, dirOnly, glob(p))
    }

    /** rsync wildcards: `**` any text, `*` any text but `/`, `?` one character but `/`; everything else literal. */
    private fun glob(p: String): Regex {
        val out = StringBuilder()
        var i = 0
        while (i < p.length) {
            when {
                p.startsWith("**", i) -> { out.append(".*"); i += 2 }
                p[i] == '*' -> { out.append("[^/]*"); i++ }
                p[i] == '?' -> { out.append("[^/]"); i++ }
                else -> { out.append(Regex.escape(p[i].toString())); i++ }
            }
        }
        return Regex(out.toString())
    }

    /** First matching rule for [path] (a dir when [dir]): true include, false exclude, null no rule matched. */
    private fun decide(path: String, dir: Boolean): Boolean? {
        val name = path.substringAfterLast('/')
        for (r in rules) {
            if (r.dirOnly && !dir) continue
            if (r.regex.matches(if (r.anchored) path else name)) return r.include
        }
        return null
    }

    /**
     * The file [path] (relative to `.userdata/shared`) travels: rsync walks down from the top, so each parent folder must
     * not be excluded, and then the file itself must not be.
     */
    fun inShared(path: String): Boolean {
        val parts = path.split('/')
        if (parts.any { it.isEmpty() }) return false
        for (depth in 1..parts.size) {
            val dir = depth < parts.size
            if (decide(parts.take(depth).joinToString("/"), dir) == false) return false
        }
        return true
    }

    /**
     * Never synced, never exported: macOS metadata a card picks up on a Mac (`._*`, `.DS_Store`), and the temp file of a
     * write that was cut short (`<name>.<hex>.tmp`, `<name>.part`), whose bytes are a partial save.
     */
    fun isJunk(path: String): Boolean = path.substringAfterLast('/').let {
        it == ".DS_Store" || it.startsWith("._") || it.endsWith(".tmp") || it.endsWith(".part")
    }
}
