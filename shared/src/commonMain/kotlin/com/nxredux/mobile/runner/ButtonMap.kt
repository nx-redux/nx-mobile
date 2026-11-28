package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.FrontendOptions

/** The ten remappable pad keys, with their Android keycodes (no Android imports in shared). */
enum class MapKey(val keyCode: Int, val label: String) {
    A(96, "A"), B(97, "B"), X(99, "X"), Y(100, "Y"),
    L1(102, "L1"), R1(103, "R1"), L2(104, "L2"), R2(105, "R2"), L3(106, "L3"), R3(107, "R3");

    companion object {
        fun ofKeyCode(code: Int): MapKey? = entries.firstOrNull { it.keyCode == code }
    }
}

/**
 * A console's button mapping: which key's job each physical key does. Always a permutation of [MapKey], so every job
 * stays reachable on exactly one key. Rows are jobs; [bind] sets the physical key a job sits on.
 */
class ButtonMap(private val jobOf: Map<MapKey, MapKey>) {
    private val keyOf: Map<MapKey, MapKey> = jobOf.entries.associate { (physical, job) -> job to physical }

    fun jobFor(physical: MapKey): MapKey = jobOf[physical] ?: physical

    fun keyFor(job: MapKey): MapKey = keyOf[job] ?: job

    /** [physical] feeds [job]; the key that fed [job] takes the job [physical] had, so the map stays a permutation. */
    fun bind(job: MapKey, physical: MapKey): ButtonMap {
        val previous = keyFor(job)
        if (previous == physical) return this
        val next = MapKey.entries.associateWith { jobFor(it) }.toMutableMap()
        next[previous] = jobFor(physical)
        next[physical] = job
        return ButtonMap(next)
    }

    /** The keycode a physical key does the job of; keys outside [MapKey] pass through. */
    fun keyCodeFor(physicalKeyCode: Int): Int =
        MapKey.ofKeyCode(physicalKeyCode)?.let { jobFor(it).keyCode } ?: physicalKeyCode

    /** This map with each of [keys] on its own job ([locked]); the keys that fed or took their jobs swap among the rest. */
    fun keeping(keys: Set<MapKey>): ButtonMap = keys.fold(this) { m, k -> m.bind(k, k) }

    /** This map with L2 and R2 on their own keys ([analogTriggers]). */
    fun keepingTriggers(): ButtonMap = keeping(TRIGGERS)

    val isDefault: Boolean get() = MapKey.entries.all { jobFor(it) == it }

    override fun equals(other: Any?): Boolean =
        other is ButtonMap && MapKey.entries.all { jobFor(it) == other.jobFor(it) }

    override fun hashCode(): Int = MapKey.entries.fold(1) { h, k -> 31 * h + jobFor(k).ordinal }

    override fun toString(): String = "ButtonMap(${toConfig(this)})"

    companion object {
        val DEFAULT = ButtonMap(emptyMap())

        const val PREFIX = "nx_map_"

        /** The retired Controller Layout key. */
        const val LEGACY_LAYOUT = "nx_pad_layout"

        private const val LEGACY_NINTENDO = "nintendo"

        /**
         * Reads the `nx_map_<JOB>=<PHYSICAL>` lines of a cfg over the identity; anything that isn't a permutation of
         * [MapKey] is the default. Without any such line, a Nintendo Controller Layout on a console that took the pad's
         * letters carries over as the A/B and X/Y swap. The keys every game of the file keeps ([fileLocked]) stay on
         * their own jobs.
         */
        fun fromConfig(values: Map<String, String>, tag: String): ButtonMap = read(values, tag).keeping(fileLocked(tag))

        /**
         * The [locked] keys of every game a [tag] file serves. A Wii file also serves GameCube discs, whose L3 and R3 go to
         * the core and may be bound; which a game is shows only once its disc is read, so the input applies the rest.
         */
        private fun fileLocked(tag: String): Set<MapKey> =
            if (tag.trim().uppercase() == "WII") locked("WII") intersect locked("GC") else locked(tag)

        /**
         * [edited], the map a game played as [playedAs] (a GameCube disc under the Wii tag: "GC") ran with, as its [tag]
         * file saves it: the jobs only [playedAs] holds on their own keys go back on the keys [stored] (the file's mapping)
         * has them on, so saving from a GameCube disc keeps the Wii's L2 and R2, and from a Wii disc a GameCube's L3 and R3.
         */
        fun toSave(edited: ButtonMap, stored: ButtonMap, tag: String, playedAs: String): ButtonMap =
            (locked(playedAs) - fileLocked(tag)).fold(edited) { m, job -> m.bind(job, stored.keyFor(job)) }

        private fun read(values: Map<String, String>, tag: String): ButtonMap {
            val lines = values.filterKeys { it.startsWith(PREFIX) }
            if (lines.isEmpty()) return legacy(values[LEGACY_LAYOUT], tag)
            val jobOf = MapKey.entries.associateWith { it }.toMutableMap()
            for ((k, v) in lines) {
                val job = MapKey.entries.firstOrNull { it.name == k.removePrefix(PREFIX) } ?: return DEFAULT
                val physical = MapKey.entries.firstOrNull { it.name == v.trim() } ?: return DEFAULT
                jobOf[physical] = job
            }
            if (jobOf.values.toSet().size != MapKey.entries.size) return DEFAULT
            return ButtonMap(jobOf)
        }

        private fun legacy(layout: String?, tag: String): ButtonMap {
            if (layout?.trim()?.lowercase() != LEGACY_NINTENDO) return DEFAULT
            if (PhysicalKeys.modeFor(tag) == FaceMode.POSITIONS) return DEFAULT
            return DEFAULT.bind(MapKey.A, MapKey.B).bind(MapKey.X, MapKey.Y)
        }

        /**
         * The consoles whose L2 and R2 are analog triggers the core reads straight from the pad's axes (the Dreamcast's,
         * the GameCube's): a remapped trigger key would press as well as the pull, so those two keys keep their own jobs.
         * A GameCube disc under the Wii tag goes as "GC".
         */
        fun analogTriggers(tag: String): Boolean = DcInput.appliesTo(tag) || tag.trim().uppercase() == "GC"

        private val TRIGGERS = setOf(MapKey.L2, MapKey.R2)

        /**
         * The keys [tag]'s console keeps for itself: its single-key hotkeys ([ButtonMapRows.hotkeys]) and its [analogTriggers].
         * Their rows show what they do but take no key, and no other row takes them.
         */
        fun locked(tag: String): Set<MapKey> =
            ButtonMapRows.hotkeys(tag).keys + if (analogTriggers(tag)) TRIGGERS else emptySet()

        /** Whether [physical] may take a job on [tag]'s Button Mapping page ([locked]). */
        fun binds(tag: String, physical: MapKey): Boolean = physical !in locked(tag)

        /**
         * The mapping a game plays with: the game file's ([gameValues]) when it has `nx_map_*` lines or a retired layout
         * that carries over, else the console file's ([consoleValues]), so a game saved with its own options still
         * follows the console's Button Mapping. Both are a file's values with the old keys migrated.
         */
        fun resolve(gameValues: Map<String, String>?, consoleValues: Map<String, String>?, tag: String): ButtonMap {
            if (gameValues != null) {
                val game = fromConfig(gameValues, tag)
                if (game != DEFAULT || gameValues.keys.any { it.startsWith(PREFIX) }) return game
            }
            return fromConfig(consoleValues.orEmpty(), tag)
        }

        /** [resolve] for the game's and the console's files as read, their old keys migrated ([FrontendOptions.migrateKeys]). */
        fun fromFiles(game: ConfigFile?, console: ConfigFile?, tag: String): ButtonMap =
            resolve(game?.values?.let(FrontendOptions::migrateKeys), console?.values?.let(FrontendOptions::migrateKeys), tag)

        /**
         * The lines a game's save writes for [m] over the console file's mapping ([console]): none while they match, so
         * the game keeps following the console; else every key's, so even the default stays the game's own ([resolve]).
         */
        fun gameConfig(m: ButtonMap, console: ButtonMap): List<Pair<String, String>> =
            if (m == console) emptyList() else MapKey.entries.map { PREFIX + it.name to m.keyFor(it).name }

        fun toConfig(m: ButtonMap): List<Pair<String, String>> =
            MapKey.entries.filter { m.keyFor(it) != it }.map { PREFIX + it.name to m.keyFor(it).name }

        /**
         * For a config save writing [m] ([toConfig]): drops every `nx_map_*` line it doesn't write, since the cfg render
         * keeps any line it isn't given, and the retired layout key so it can't migrate again.
         */
        fun droppedOnSave(m: ButtonMap): (String) -> Boolean = droppedOnSave(toConfig(m))

        /** As [droppedOnSave] for a save writing the `nx_map_*` [lines] ([toConfig] or [gameConfig]). */
        fun droppedOnSave(lines: List<Pair<String, String>>): (String) -> Boolean {
            val written = lines.map { it.first }.toSet()
            return { key -> key == LEGACY_LAYOUT || (key.startsWith(PREFIX) && key !in written) }
        }
    }
}
