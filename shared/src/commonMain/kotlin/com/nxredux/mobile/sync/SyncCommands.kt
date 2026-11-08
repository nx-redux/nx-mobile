package com.nxredux.mobile.sync

/**
 * The client's rsync steps: 1-4 saves and states (the handheld's phases 1-4), 5-6 BIOS and 7 collections (every sync,
 * when this phone and the peer can), 8-9 ROMs (only with ROMs on).
 */
enum class SyncStep(val number: Int, val label: String, val push: Boolean, val module: String) {
    SEND_STATES(1, "Sending save states", push = true, module = "shared"),
    SEND_SAVES(2, "Sending save files", push = true, module = "saves"),
    RECEIVE_STATES(3, "Receiving save states", push = false, module = "shared"),
    RECEIVE_SAVES(4, "Receiving save files", push = false, module = "saves"),
    SEND_BIOS(5, "Sending BIOS", push = true, module = "bios"),
    RECEIVE_BIOS(6, "Receiving BIOS", push = false, module = "bios"),
    /** Pulls the peer's collections, merges, pushes back the changed ones (both ways in one step). */
    SYNC_COLLECTIONS(7, "Syncing collections", push = true, module = "collections"),
    SEND_ROMS(8, "Pushing ROMs", push = true, module = "roms"),
    RECEIVE_ROMS(9, "Pulling ROMs", push = false, module = "roms"),
}

/**
 * Device Sync's rsync command lines and daemon config (the handheld's `run_rsync_phase` / `write_rsync_config`). Pure;
 * every value that reaches a URL or a config line is checked, since a newline would start a directive nobody wrote.
 */
object SyncCommands {
    /** Dreamcast shared memory cards: saves (`Saves/DC`), so never carried by BIOS sync. */
    private const val VMU_CARDS = "vmu_save_*.bin"
    /** `map.txt` (a collections folder's alias map, not a collection) in any case, as an rsync pattern. */
    private const val MAP_TXT = "[mM][aA][pP].[tT][xX][tT]"

    /**
     * The handheld's options minus `--inplace`, plus timeouts so a vanished peer fails a step instead of hanging it. The
     * receiver writes a temp file and renames it, so a transfer cut midway never leaves a truncated file with a fresh
     * time that the next sync's `--update` would spread back over the good original.
     */
    val OPTIONS: List<String> = listOf(
        "-rtv", "--update", "--no-perms", "--omit-dir-times", "--modify-window=1", "--info=progress2",
        "--timeout=30", "--contimeout=10",
    )

    /**
     * [root]: the data root (`Saves/` and `.userdata/shared` live under it). No shell: each element is one argv entry. A
     * saves step may run against [savesDir] (a staging tree) instead of `<root>/Saves/`, and with [onlyTags] moves only
     * the `Saves/<T>/` folders named there (each tag escaped as an rsync pattern).
     */
    fun clientArgs(
        binary: String, step: SyncStep, peerIp: String, root: String, savesDir: String? = null, onlyTags: List<String>? = null,
    ): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        val base = root.trimEnd('/')
        val shared = step.module == "shared"
        require(savesDir == null || noBreak(savesDir)) { "a path with a line break" }
        require(onlyTags.orEmpty().none { it.isEmpty() || '/' in it || !noBreak(it) }) { "not a plain tag" }
        val local = if (shared) "$base/.userdata/shared/" else savesDir?.let { it.trimEnd('/') + "/" } ?: "$base/Saves/"
        val remote = "rsync://$peerIp:${SyncWire.RSYNC_PORT}/${step.module}/"
        val filters = if (shared) SyncAreas.sharedFilterArgs() else SyncAreas.savesExcludeArgs() + onlyTags?.let { tags ->
            tags.flatMap { val t = SyncRoms.escape(it); listOf("--include=/$t/", "--include=/$t/**") } + "--exclude=*"
        }.orEmpty()
        return listOf(binary) + OPTIONS + filters + if (step.push) listOf(local, remote) else listOf(remote, local)
    }

    /**
     * One ROM run's rsync call. [homeRoms]: this phone's `Roms/`; [extraRoots]: extra folder index → its path. With a
     * [filesFromFile] or [filterFile] it ignores names missing at the source (a stale index entry is not a failure). An
     * art or BIOS run ([RomRun.ignoreExisting]) never replaces a file the receiver has, and a missing source is not a
     * failure. [homeBios]: this phone's `Bios/`, which a [RomEnd.Bios] end needs. Every end is a folder (ends with `/`),
     * so rsync copies its contents.
     */
    fun romArgs(
        binary: String, run: RomRun, peerIp: String, homeRoms: String, extraRoots: Map<Int, String>,
        filesFromFile: String?, excludeFile: String?, filterFile: String? = null, homeBios: String? = null,
    ): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        require(listOf(run.from, run.to).all { noBreak(endPath(it)) }) { "a path with a line break" }
        fun resolve(e: RomEnd): String = when (e) {
            is RomEnd.Home -> dir(homeRoms.trimEnd('/'), e.path)
            is RomEnd.Extra -> dir((extraRoots[e.extra] ?: error("no extra folder ${e.extra}")).trimEnd('/'), e.path)
            is RomEnd.Peer -> dir("rsync://$peerIp:${SyncWire.RSYNC_PORT}/${e.module}", e.path)
            is RomEnd.Bios -> dir((homeBios ?: error("no home Bios folder")).trimEnd('/'), e.path)
        }
        val limited = filesFromFile != null || filterFile != null || run.ignoreExisting
        return listOf(binary) + OPTIONS +
            listOfNotNull("--ignore-existing".takeIf { run.ignoreExisting }, "--ignore-missing-args".takeIf { limited }) +
            listOf("--exclude=._*", "--exclude=.DS_Store") +
            // Dreamcast memory cards are saves (Saves/DC), never BIOS: an old copy in a BIOS folder must not travel.
            listOf("--exclude=$VMU_CARDS").filter { run.from is RomEnd.Bios || run.to is RomEnd.Bios } +
            listOfNotNull(filesFromFile?.let { "--files-from=$it" }, excludeFile?.let { "--exclude-from=$it" },
                filterFile?.let { "--filter=merge $it" }) +
            resolve(run.from) + resolve(run.to)
    }

    /**
     * The lines of a `--files-from` file for [names]: each is `./`-prefixed, since rsync skips a line that starts with
     * `#` or `;` as a comment.
     */
    fun filesFromLines(names: List<String>): List<String> = names.map { "./$it" }

    /**
     * [code] of a ROM run, with a pull of named entries ([RomRun.only]) or an art run ([RomRun.ignoreExisting]) whose
     * source folder is gone counted as done: `--ignore-missing-args` does not cover a missing folder, which rsync reports
     * as exit 23 with a `change_dir ... No such file` line. Any other error line keeps the code.
     */
    fun romResult(run: RomRun, code: Int, lines: List<String>): Int {
        if (code != 23 || (run.only == null && !run.ignoreExisting)) return code
        val errors = lines.filter { it.startsWith("rsync:") || it.startsWith("rsync error:") }
        val gone = errors.filter { "change_dir" in it && "No such file" in it }
        val rest = errors - gone.toSet()
        val noise = rest.all { it.startsWith("rsync error: some files/attrs were not transferred") || "read error: Connection reset" in it }
        return if (gone.isNotEmpty() && noise) 0 else code
    }

    /**
     * Filter rules (one per line, for [romArgs]' `filterFile`) that keep only [names], relative to the source: each name
     * and what is below it (plus the parent folders of an `a/b` name), then `- *`.
     */
    fun onlyRules(names: List<String>): List<String> {
        val dirs = LinkedHashSet<String>()
        val rules = ArrayList<String>()
        for (name in names) {
            val parts = name.split('/')
            for (i in 1 until parts.size) dirs += "+ /" + parts.take(i).joinToString("/") { SyncRoms.escape(it) } + "/"
            val path = parts.joinToString("/") { SyncRoms.escape(it) }
            rules += "+ /$path"
            rules += "+ /$path/**"
        }
        return dirs.toList() + rules + "- *"
    }

    private fun endPath(e: RomEnd): String = when (e) {
        is RomEnd.Home -> e.path
        is RomEnd.Extra -> e.path
        is RomEnd.Peer -> e.path
        is RomEnd.Bios -> e.path
    }

    private fun noBreak(s: String) = '\n' !in s && '\r' !in s

    /** `base/path/`, or `base/` when [path] is empty. */
    private fun dir(base: String, path: String) = if (path.isEmpty()) "$base/" else "$base/${path.trimEnd('/')}/"

    /** Lists the top level of the peer's [module]: `roms` (its console folders) or `bios` (its tag folders). */
    fun listArgs(binary: String, peerIp: String, module: String = "roms"): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        require(module == "roms" || module == "bios") { "not a listable module: $module" }
        // --8-bit-output: names raw, not with each non-ASCII byte as `\#ooo` (`Pokémon mini (PKM)` must pair by name).
        return listOf(binary, "--list-only", "--8-bit-output", "--timeout=30", "--contimeout=10", "rsync://$peerIp:${SyncWire.RSYNC_PORT}/$module/")
    }

    /**
     * Lists every console folder's `.retroachievements/hashes.json` on the peer in one call (`-R`: each path printed with its
     * folder). Exit 23 with nothing matching is a peer with none.
     */
    fun listHashesArgs(binary: String, peerIp: String): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        return listOf(binary, "--list-only", "--8-bit-output", "-R", "--timeout=30", "--contimeout=10",
            "rsync://$peerIp:${SyncWire.RSYNC_PORT}/roms/./*/.retroachievements/hashes.json")
    }

    /** Lists the top-level entries of the peer's `roms/<folder>/` (which games a sibling console folder holds). */
    fun listFolderArgs(binary: String, peerIp: String, folder: String): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        require(noBreak(folder)) { "a path with a line break" }
        return listOf(binary, "--list-only", "--8-bit-output", "--timeout=30", "--contimeout=10",
            dir("rsync://$peerIp:${SyncWire.RSYNC_PORT}/roms", folder))
    }

    /**
     * Collections sync's pull (the peer's `collections/` into [localDir]) or push ([localDir] into it): the top-level
     * `*.txt` files only, the extension in any case (`Faves.TXT` too), never `map.txt` (the alias map, any case).
     * [OPTIONS] without `--update`: a peer clock ahead would make rsync skip the merged push, so whatever is written is
     * what lands.
     */
    fun collectionsArgs(binary: String, peerIp: String, localDir: String, push: Boolean): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        require(noBreak(localDir)) { "a path with a line break" }
        val local = localDir.trimEnd('/') + "/"
        val remote = "rsync://$peerIp:${SyncWire.RSYNC_PORT}/collections/"
        return listOf(binary) + (OPTIONS - "--update") +
            listOf("--exclude=._*", "--exclude=.DS_Store", "--exclude=/$MAP_TXT", "--include=/*.[tT][xX][tT]", "--exclude=*") +
            if (push) listOf(local, remote) else listOf(remote, local)
    }

    /** Copies one file [path] of the peer's [module] to [localFile]. */
    fun fetchArgs(binary: String, peerIp: String, module: String, path: String, localFile: String): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        return listOf(binary, "-t", "--timeout=30", "--contimeout=10", "rsync://$peerIp:${SyncWire.RSYNC_PORT}/$module/$path", localFile)
    }

    /** Copies [localFile] into the peer's [module] folder [remoteDir]. */
    fun putArgs(binary: String, peerIp: String, module: String, remoteDir: String, localFile: String): List<String> {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        return listOf(binary, "-t", "--no-perms", "--timeout=30", "--contimeout=10", localFile,
            "rsync://$peerIp:${SyncWire.RSYNC_PORT}/$module/$remoteDir/")
    }

    /**
     * The phone's daemon: `[shared]` serves [shared] (the real `.userdata/shared`) with the area filter, `[saves]` the
     * real `Saves/` without macOS junk; both writable, unlisted, for [peerIp] only. With [roms] (ROMs on) it also
     * serves `[roms]` (writable), one read-only `[extra-n]` per extra folder, the read-only `[nx-art]`
     * (`<roms>/.sources`, where extras' art lives and `[roms]` hides it) and the read-only `[nx-extras]` manifest
     * folder. With [bios] (this phone's home `Bios/`, whenever it has All files access, ROMs on or off) it serves
     * `[bios]` (writable; the peer never replaces a file). With [collections] (the home `Collections/`, likewise) it
     * serves `[collections]` (writable; `map.txt` never moves).
     */
    fun daemonConf(
        peerIp: String, shared: String, saves: String, pidFile: String, logFile: String, roms: ServerRoms? = null, bios: String? = null,
        collections: String? = null,
    ): String {
        require(SyncWire.ipv4(peerIp) == peerIp) { "not a dotted IPv4 address: $peerIp" }
        val paths = listOf(shared, saves, pidFile, logFile) + (roms?.let { listOf(it.roms, it.manifestDir) + it.extras }.orEmpty()) +
            listOfNotNull(bios, collections)
        require(paths.none { '\n' in it || '\r' in it }) { "a path with a line break" }
        return buildString {
            append("pid file = ").append(pidFile).append('\n')
            append("port = ").append(SyncWire.RSYNC_PORT).append('\n')
            append("use chroot = no\n")
            append("reverse lookup = no\n")
            append("hosts allow = ").append(peerIp).append('\n')
            append("log file = ").append(logFile).append('\n')
            append("log format = %m %o %f\n")
            append("transfer logging = yes\n")
            append('\n')
            append("[shared]\n")
            append("  path = ").append(shared).append('\n')
            append("  read only = false\n")
            append("  list = false\n")
            append("  filter = ").append(SyncAreas.daemonSharedFilter()).append('\n')
            append("[saves]\n")
            append("  path = ").append(saves).append('\n')
            append("  read only = false\n")
            append("  list = false\n")
            append("  exclude = ").append(SyncAreas.daemonSavesExclude()).append('\n')
            if (bios != null) {
                append("[bios]\n")
                append("  path = ").append(bios).append('\n')
                append("  read only = false\n")
                append("  list = false\n")
                append("  exclude = ._* .DS_Store ").append(VMU_CARDS).append('\n')
            }
            if (collections != null) {
                append("[collections]\n")
                append("  path = ").append(collections).append('\n')
                append("  read only = false\n")
                append("  list = false\n")
                append("  exclude = ._* .DS_Store /").append(MAP_TXT).append('\n')
            }
            if (roms != null) {
                append("[roms]\n")
                append("  path = ").append(roms.roms).append('\n')
                append("  read only = false\n")
                append("  list = false\n")
                append("  exclude = ._* .DS_Store /.sources/\n")
                roms.extras.forEachIndexed { n, path ->
                    append("[extra-").append(n).append("]\n")
                    append("  path = ").append(path).append('\n')
                    append("  read only = true\n")
                    append("  list = false\n")
                    append("  exclude = ._* .DS_Store\n")
                }
                append("[nx-art]\n")
                append("  path = ").append(roms.roms.trimEnd('/')).append("/.sources\n")
                append("  read only = true\n")
                append("  list = false\n")
                append("  exclude = ._* .DS_Store\n")
                append("[nx-extras]\n")
                append("  path = ").append(roms.manifestDir).append('\n')
                append("  read only = true\n")
                append("  list = false\n")
            }
        }
    }
}
