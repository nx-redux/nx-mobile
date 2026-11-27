package com.nxredux.mobile.runner

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
         * letters carries over as the A/B and X/Y swap.
         */
        fun fromConfig(values: Map<String, String>, tag: String): ButtonMap {
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

        fun toConfig(m: ButtonMap): List<Pair<String, String>> =
            MapKey.entries.filter { m.keyFor(it) != it }.map { PREFIX + it.name to m.keyFor(it).name }

        /**
         * For a config save writing [m] ([toConfig]): drops every `nx_map_*` line it doesn't write, since the cfg render
         * keeps any line it isn't given, and the retired layout key so it can't migrate again.
         */
        fun droppedOnSave(m: ButtonMap): (String) -> Boolean {
            val written = toConfig(m).map { it.first }.toSet()
            return { key -> key == LEGACY_LAYOUT || (key.startsWith(PREFIX) && key !in written) }
        }
    }
}
