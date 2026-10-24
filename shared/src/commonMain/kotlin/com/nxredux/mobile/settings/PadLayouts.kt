package com.nxredux.mobile.settings

/**
 * Controls → Pad layout. The on-screen pad's button groups: each moves and resizes as one (the d-pad and
 * the stick share [DPAD]'s spot). [C_STICK] is the New 3DS C-stick of the portrait pad; in the landscape clusters it is
 * part of [FACE]. [SYSTEM_LEFT] is MENU and the d-pad / stick toggle, [SYSTEM_RIGHT] SELECT and START. [SHORTCUT_1] /
 * [SHORTCUT_2] are the touch shortcut buttons, drawn in game only while their slot has an action (the editor always shows
 * them). Layouts store groups by name: new groups go at the end, so a stored line stays valid.
 */
enum class PadGroup { DPAD, FACE, L_SHOULDERS, R_SHOULDERS, SYSTEM_LEFT, SYSTEM_RIGHT, C_STICK, SHORTCUT_1, SHORTCUT_2 }

/**
 * The pad's two editable arrangements: the portrait band ([PORTRAIT], also flex mode's band and the main menu's pad)
 * and the clusters over the game ([OVERLAY]). Flex mode's clusters below the hinge (wide foldables only) keep the
 * default layout. A stored line for another shape is skipped.
 */
enum class PadShape(val label: String) { PORTRAIT("Portrait band"), OVERLAY("Landscape overlay") }

/**
 * The screen a pad is drawn on, by its smallest side: a Fold's cover screen (and a phone or handheld) is [NARROW], its
 * inner screen [WIDE]. Each keeps its own layouts, so groups spread out on the wide screen do not overlap on the narrow.
 */
enum class PadScreen {
    NARROW, WIDE;

    val other: PadScreen get() = if (this == NARROW) WIDE else NARROW

    companion object {
        /** Android's tablet breakpoint: the Fold's inner screen is well above it, its cover screen well below. */
        const val WIDE_MIN_DP = 600f

        fun of(smallestSideDp: Float): PadScreen = if (smallestSideDp >= WIDE_MIN_DP) WIDE else NARROW
    }
}

/** Where a layout applies: a [shape] on a [screen]. */
data class PadSlot(val shape: PadShape, val screen: PadScreen) {
    /** The codec's name: the shape's alone for [PadScreen.NARROW], else `SHAPE.WIDE`. */
    val code: String get() = if (screen == PadScreen.NARROW) shape.name else "${shape.name}.${screen.name}"

    companion object {
        fun fromCode(code: String): PadSlot? {
            val parts = code.split('.')
            val shape = PadShape.entries.firstOrNull { it.name == parts[0] } ?: return null
            val screen = when (parts.size) {
                1 -> PadScreen.NARROW
                2 -> PadScreen.entries.firstOrNull { it.name == parts[1] && it != PadScreen.NARROW } ?: return null
                else -> return null
            }
            return PadSlot(shape, screen)
        }
    }
}

/** A group moved [dx], [dy] pad units from its default spot and drawn at [scale] (0.7..1.5) of its default size. */
data class GroupTweak(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f) {
    val isIdentity: Boolean get() = dx == 0f && dy == 0f && scale == 1f
}

/** One shape's tweaks; a group without one stays where the default pad puts it. [EMPTY] is the default pad. */
data class PadLayout(val tweaks: Map<PadGroup, GroupTweak> = emptyMap()) {
    /** [group] at [tweak]; an identity tweak removes the group's entry. */
    fun with(group: PadGroup, tweak: GroupTweak): PadLayout =
        PadLayout(if (tweak.isIdentity) tweaks - group else tweaks + (group to tweak))

    fun without(group: PadGroup): PadLayout = PadLayout(tweaks - group)

    companion object { val EMPTY = PadLayout() }
}

/** Every stored layout: console id (or [PadLayouts.SHARED_KEY]) → slot (shape and screen) → layout. */
typealias PadLayoutMap = Map<String, Map<PadSlot, PadLayout>>

object PadLayouts {
    /** The Shared layouts' key: the main menu's, and every console's without its own for a shape. */
    const val SHARED_KEY = "*"
    const val MIN_SCALE = 0.7f
    const val MAX_SCALE = 1.5f

    /** Written for a console's explicit default (the default pad, over a customised Shared) in place of a tweak line. */
    private const val DEFAULT_MARKER = "-"

    /**
     * [key]'s own layout for [slot], or null when it has none (it inherits). A console's own [PadLayout.EMPTY] is an
     * explicit default: the default pad even when Shared is customised.
     */
    fun own(all: PadLayoutMap, key: String, slot: PadSlot): PadLayout? = all[key]?.get(slot)

    private fun ownOrShared(all: PadLayoutMap, consoleKey: String?, slot: PadSlot): PadLayout? =
        consoleKey?.let { own(all, it, slot) } ?: own(all, SHARED_KEY, slot)

    /**
     * The layout a pad of [consoleKey] (null: none known) draws for [slot]: its own (an explicit default included),
     * else Shared's, else [PadLayout.EMPTY]. Each screen stands alone: the other screen's layout is never drawn here.
     */
    fun resolve(all: PadLayoutMap, consoleKey: String?, slot: PadSlot): PadLayout =
        ownOrShared(all, consoleKey, slot) ?: PadLayout.EMPTY

    /**
     * Where the editor starts for [key] and [slot]: as [resolve] (a console's first edit starts from Shared), but a
     * screen with nothing for that shape yet starts from the other screen's layout.
     */
    fun startingLayout(all: PadLayoutMap, key: String, slot: PadSlot): PadLayout =
        ownOrShared(all, key, slot) ?: ownOrShared(all, key, slot.copy(screen = slot.screen.other)) ?: PadLayout.EMPTY

    /**
     * [all] with [key]'s [slot] set to [layout]. A console keeps an empty layout as its explicit default (Reset console,
     * [withoutScreen], makes it inherit again); for Shared an empty layout removes the slot, and a Shared left with none goes.
     */
    fun withLayout(all: PadLayoutMap, key: String, slot: PadSlot, layout: PadLayout): PadLayoutMap {
        val remove = key == SHARED_KEY && layout.tweaks.isEmpty()
        val slots = all[key].orEmpty().let { if (remove) it - slot else it + (slot to layout) }
        return if (slots.isEmpty()) all - key else all + (key to slots)
    }

    /**
     * What the editor's Save writes: [all] with [edited] for [key]'s [slot], or null to write nothing. Unchanged
     * ([dirty] false) it writes only a layout adopted from the other screen ([startingLayout] differs from [resolve]),
     * so an inheriting console's unchanged Save does not copy Shared.
     */
    fun afterSave(all: PadLayoutMap, key: String, slot: PadSlot, edited: PadLayout, dirty: Boolean): PadLayoutMap? {
        val adopted = startingLayout(all, key, slot) != resolve(all, key, slot)
        return if (dirty || adopted) withLayout(all, key, slot, edited) else null
    }

    /** [all] without [key]'s layouts for [screen] (Reset console on that screen); the other screen's stay. */
    fun withoutScreen(all: PadLayoutMap, key: String, screen: PadScreen): PadLayoutMap {
        val slots = all[key].orEmpty().filterKeys { it.screen != screen }
        return if (slots.isEmpty()) all - key else all + (key to slots)
    }

    /** Any tweak stored (an explicit default does not count): Controls' Pad layout row reads "Custom". */
    fun isCustom(all: PadLayoutMap): Boolean = all.values.any { slots -> slots.values.any { it.tweaks.isNotEmpty() } }

    /**
     * One line per tweak: `key TAB SLOT TAB GROUP TAB dx TAB dy TAB scale` (SLOT: [PadSlot.code]); a console's explicit
     * default (an empty layout) is one line `key TAB SLOT TAB -`.
     */
    fun encode(all: PadLayoutMap): String = buildList {
        for ((key, slots) in all) for ((slot, layout) in slots) {
            if (layout.tweaks.isEmpty()) add(listOf(key, slot.code, DEFAULT_MARKER).joinToString("\t"))
            for ((group, t) in layout.tweaks) add(listOf(key, slot.code, group.name, t.dx, t.dy, t.scale).joinToString("\t"))
        }
    }.joinToString("\n")

    /**
     * [encode]'s text back; a line that does not read (unknown names, not finite numbers) is skipped, scale clamped. A
     * tweak line after a default marker for the same key and shape replaces the marker.
     */
    fun decode(text: String?): PadLayoutMap {
        var all: PadLayoutMap = emptyMap()
        for (line in text.orEmpty().lineSequence()) {
            val p = line.split('\t')
            if (p.size == 3 && p[0].isNotEmpty() && p[2] == DEFAULT_MARKER) {
                val slot = PadSlot.fromCode(p[1]) ?: continue
                if (own(all, p[0], slot) == null) all = withLayout(all, p[0], slot, PadLayout.EMPTY)
                continue
            }
            if (p.size != 6 || p[0].isEmpty()) continue
            val slot = PadSlot.fromCode(p[1]) ?: continue
            val group = PadGroup.entries.firstOrNull { it.name == p[2] } ?: continue
            val dx = p[3].toFloatOrNull()?.takeIf { it.isFinite() } ?: continue
            val dy = p[4].toFloatOrNull()?.takeIf { it.isFinite() } ?: continue
            val scale = p[5].toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(MIN_SCALE, MAX_SCALE) ?: continue
            val layout = (own(all, p[0], slot) ?: PadLayout.EMPTY).with(group, GroupTweak(dx, dy, scale))
            all = withLayout(all, p[0], slot, layout)
        }
        return all
    }
}
