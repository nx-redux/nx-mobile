package com.swordfish.libretrodroid

/**
 * What the native rewind did on the last frame ([GLRetroView.rewindStatus]). [OFF]: rewind is off, or no buffer is
 * built yet (the core can't serialize yet: a step back then does nothing). [EMPTY]: rewinding with nothing older left;
 * a toggled rewind should turn itself off, a held one keeps alternating a played frame and a step back. [UNAVAILABLE]:
 * rewind is on but the engine gave up on this game (no states, too big for the RAM, a state size that changes,
 * serializes refused): no buffer will come until the core is loaded again.
 */
enum class RewindStatus {
    OFF,
    READY,
    REWINDING,
    EMPTY,
    UNAVAILABLE;

    companion object {
        /** The native status code (0..4); anything else reads as [OFF]. */
        fun fromNative(code: Int): RewindStatus = entries.getOrElse(code) { OFF }
    }
}
