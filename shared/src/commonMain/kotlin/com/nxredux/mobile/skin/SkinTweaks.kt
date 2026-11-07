package com.nxredux.mobile.skin

/** A player's nudge of one button group: [dx], [dy] in picture px, and a [scale] about the group's centre. */
data class SkinTweak(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f)

/** One console's tweaks: layout id, then group id. Keyed by layout, so every pack sharing the layout shares the tweak. */
typealias ConsoleTweaks = Map<String, Map<String, SkinTweak>>

object SkinTweaks {
    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.25f

    /** How far a group may move from where its layout puts it, as a fraction of its larger side. */
    const val MOVE_FRACTION = 0.5f

    /** [t] held to the limits for [g]: scale within [MIN_SCALE]..[MAX_SCALE], a move within [MOVE_FRACTION] of its size (a free group is not capped). */
    fun limit(t: SkinTweak, g: SkinGroup): SkinTweak {
        val cap = MOVE_FRACTION * maxOf(g.ref.width, g.ref.height)
        return SkinTweak(
            dx = if (g.free) t.dx else t.dx.coerceIn(-cap, cap),
            dy = if (g.free) t.dy else t.dy.coerceIn(-cap, cap),
            scale = t.scale.coerceIn(MIN_SCALE, MAX_SCALE),
        )
    }

    /** The tweaks as text: one line per group, `console layout group dx dy scale`. */
    fun encode(m: Map<String, ConsoleTweaks>): String = buildString {
        for ((console, layouts) in m) for ((layout, groups) in layouts) for ((group, t) in groups) {
            if (isNotEmpty()) append('\n')
            append("$console $layout $group ${t.dx} ${t.dy} ${t.scale}")
        }
    }

    /** What [encode] wrote. Lines that do not read (wrong shape, a bad number) are skipped. */
    fun decode(s: String): Map<String, ConsoleTweaks> {
        val out = linkedMapOf<String, LinkedHashMap<String, LinkedHashMap<String, SkinTweak>>>()
        for (line in s.lines()) {
            val p = line.trim().split(' ')
            if (p.size != 6) continue
            val dx = p[3].toFloatOrNull() ?: continue
            val dy = p[4].toFloatOrNull() ?: continue
            val sc = p[5].toFloatOrNull() ?: continue
            if (!dx.isFinite() || !dy.isFinite() || !sc.isFinite()) continue
            out.getOrPut(p[0]) { linkedMapOf() }.getOrPut(p[1]) { linkedMapOf() }[p[2]] = SkinTweak(dx, dy, sc)
        }
        return out
    }
}
