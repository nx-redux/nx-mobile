package com.nxredux.mobile.sync

/**
 * What Device Sync carries in `.userdata/shared`: save states (`<TAG>-<core>/`) and slot pictures/markers
 * (`.minui/<TAG>/`), by the handheld's own rule list, copied verbatim and in order, since rsync takes the first rule
 * that matches. `Saves/` travels whole, minus macOS metadata and interrupted writes' temp files ([SAVES_EXCLUDES]) and
 * the emulators' files that belong to one device ([SAVES_PER_DEVICE]). Export/Import use the same rule
 * ([com.nxredux.mobile.storage.SaveArchive.areaOf]) but keep the per-device files: an export backs up the whole device.
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

    /**
     * `Saves/` files an emulator writes for the device it runs on, as rsync exclude patterns from the `Saves/` root (the
     * rsync side of [isPerDevice]): settings, identities and caches, never a game's save. The first folder is any tag, so
     * the patterns hold for this phone's console folders and a handheld's emulator folders alike. Only skipped, never
     * deleted: a copy an earlier sync brought stays where it is.
     */
    val SAVES_PER_DEVICE: List<String> = listOf(
        // Dolphin (`<tag>/User/`): graphics, backend, resolution and controller settings are this device's choices; the
        // Wii's SYSCONF and network settings are rewritten from this device's core options at every boot.
        "/*/User/Config/",
        "/*/User/Wii/shared2/sys/SYSCONF",
        "/*/User/Wii/shared2/sys/net/",
        // Dolphin's shader and game-list caches, logs and dumps: built for this device's GPU and driver, or debugging only.
        "/*/User/Cache/",
        "/*/User/ShaderCache/",
        "/*/User/Logs/",
        "/*/User/Dump/",
        // Azahar (`<tag>/Azahar/`): the emulated console's MAC address and the console unique ID in its config savegame
        // must differ per device, or local wireless sees two phones as the same 3DS (a room refuses the second one); the
        // MCU file is that console's clock counter.
        "/*/Azahar/sysdata/mac.txt",
        "/*/Azahar/sysdata/mcu.dat",
        "/*/Azahar/nand/data/*/sysdata/00010017/",
        "/*/Azahar/config/",
        // A Download Play child is stored under a key made on this device (its title, its ticket and the key itself):
        // copied to another device it could not be read there.
        "/*/Azahar/sysdata/nx_dlp_child.key",
        "/*/Azahar/nand/*/title/00040001/",
        "/*/Azahar/nand/dbs/ticket.db/00040001*",
        // 3DS Download Play's own files beside them: which children this device holds, and the hand-off to the app.
        "/*/Azahar/sdmc/nx-redux/",
        // Azahar's shader caches (built for this device's GPU and driver), logs and dumps.
        "/*/Azahar/shaders/",
        "/*/Azahar/cache/",
        "/*/Azahar/log/",
        "/*/Azahar/dump/",
        // PPSSPP's memory stick `SYSTEM/`: its settings, control mappings and shader/texture caches; the saves are in
        // `SAVEDATA/`.
        "/PSP/SYSTEM/",
    )

    /** [SHARED_RULES] as rsync client arguments, one `--filter=` each, in order. */
    fun sharedFilterArgs(): List<String> = SHARED_RULES.map { "--filter=$it" }
    /**
     * [SAVES_EXCLUDES] and [SAVES_PER_DEVICE] as rsync client arguments, one `--exclude=` each. A client's excludes cover
     * both directions: its own list when it sends, and the daemon's list when it receives.
     */
    fun savesExcludeArgs(): List<String> = (SAVES_EXCLUDES + SAVES_PER_DEVICE).map { "--exclude=$it" }
    /** rsyncd.conf `filter =` value: the daemon splits it back into rules on spaces. */
    fun daemonSharedFilter(): String = SHARED_RULES.joinToString(" ")
    /**
     * The daemon's `Saves/` excludes: [SAVES_EXCLUDES] only. A daemon refuses an excluded file a client pushes and fails
     * that client's step (exit 23), so a peer phone on a version that still sends [SAVES_PER_DEVICE] files could not sync.
     */
    fun daemonSavesExclude(): String = SAVES_EXCLUDES.joinToString(" ")

    private class Rule(val include: Boolean, val anchored: Boolean, val dirOnly: Boolean, val regex: Regex)

    /** One rsync pattern [pattern] (no `+ `/`- ` prefix) as a [Rule]. */
    private fun rule(pattern: String, include: Boolean): Rule {
        var p = pattern
        val dirOnly = p.endsWith('/').also { if (it) p = p.dropLast(1) }
        val anchored = p.startsWith('/').also { if (it) p = p.drop(1) }
        require(anchored || '/' !in p) { "an unanchored rule with a slash needs rsync's tail matching: $pattern" }
        return Rule(include, anchored, dirOnly, glob(p))
    }

    private val rules: List<Rule> = SHARED_RULES.map { rule(it.substring(2), include = it[0] == '+') }

    private val perDevice: List<Rule> = SAVES_PER_DEVICE.map { rule(it, include = false) }

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

    /** First of [list] matching [path] (a dir when [dir]): true include, false exclude, null no rule matched. */
    private fun decide(path: String, dir: Boolean, list: List<Rule> = rules): Boolean? {
        val name = path.substringAfterLast('/')
        for (r in list) {
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

    /**
     * The file [path] (relative to a `Saves/` root, `<tag>/...`) belongs to the device it is on ([SAVES_PER_DEVICE]): it
     * or one of its parent folders matches, as rsync skips a whole excluded folder.
     */
    fun isPerDevice(path: String): Boolean {
        val parts = path.split('/')
        return (1..parts.size).any { depth -> decide(parts.take(depth).joinToString("/"), depth < parts.size, perDevice) == false }
    }
}
