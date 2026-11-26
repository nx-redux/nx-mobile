package com.nxredux.mobile.skin

/** The Wii U's screen swap: a fixed button in the middle of the touch shortcut strip, never a player's shortcut. */
const val STRIP_SWAP = "SWAP"

/**
 * The touch shortcut strip's buttons over the span of its [slots] (TOUCH_1..4 as placed, an even share each, window
 * px). The [filled] slots (in slot order) an even share each of the whole span; with a [centre] button (the Wii U's
 * [STRIP_SWAP]) that one in the middle and the filled ones split evenly either side of it (the left one more where they
 * are odd, its share on the right left empty), so it stays centred: Save · SWAP · Load. Alone (or with one), the
 * strip is split in three all the same, so SWAP is one normal button in the middle, the rest of the strip untouched
 * (no stray press beside its word). Empty: no slots, or nothing to show.
 */
fun arrangeStrip(slots: List<PlacedControl>, filled: List<PlacedControl>, centre: String? = null): List<PlacedControl> {
    if (slots.isEmpty()) return emptyList()
    val start = slots.minOf { it.x - it.spec.hw }
    val span = slots.maxOf { it.x + it.spec.hw } - start
    fun at(i: Int, n: Int, c: PlacedControl): PlacedControl {
        val share = span / n
        return c.copy(spec = c.spec.copy(hw = share / 2f), x = start + share * (i + 0.5f))
    }
    if (centre == null) return filled.mapIndexed { i, c -> at(i, filled.size, c) }
    val left = (filled.size + 1) / 2   // (the left side has as many as the right, or one more)
    val side = left
    val n = maxOf(3, 2 * side + 1)   // (never wider than one share of three)
    val half = n / 2
    val first = slots.first()
    val mid = at(half, n, first.copy(spec = first.spec.copy(id = centre, label = null)))
    return filled.take(left).mapIndexed { i, c -> at(half - left + i, n, c) } + mid +
        filled.drop(left).mapIndexed { i, c -> at(half + 1 + i, n, c) }
}

/**
 * The Button layout editor's dummy strip as the game draws it with every slot filled: TOUCH_1..4 an even share each;
 * on the Wii U's layouts with its SWAP in the middle ([arrangeStrip]).
 */
fun SkinPlacement.stripDummies(): List<PlacedControl> {
    val slots = controls.filter { it.spec.id.startsWith("TOUCH_") }
    return if (spec.variant == SkinVariant.WIIU) arrangeStrip(slots, slots, STRIP_SWAP) else slots
}
