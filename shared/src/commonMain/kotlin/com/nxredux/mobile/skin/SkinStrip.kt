package com.nxredux.mobile.skin

/** The Wii U's screen swap: a fixed button in the middle of the touch shortcut strip, never a player's shortcut. */
const val STRIP_SWAP = "SWAP"

/**
 * Touch strip buttons evenly across the span of [slots] (window px); with a [centre] button it sits in the middle and
 * the [filled] ones split either side (Save · SWAP · Load). A lone centre button is one share of three.
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
    val left = (filled.size + 1) / 2
    val n = maxOf(3, 2 * left + 1)
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
