package com.swordfish.libretrodroid

/**
 * What the native rewind did on the last frame ([GLRetroView.rewindStatus]). [OFF]: rewind is off, or this core/game
 * can't rewind (no states, too big for the RAM, a state size that changes). [EMPTY]: rewinding with nothing older left;
 * a toggled rewind should turn itself off, a held one keeps alternating a played frame and a step back.
 */
enum class RewindStatus {
    OFF,
    READY,
    REWINDING,
    EMPTY;

    companion object {
        /** The native status code (0..3); anything else reads as [OFF]. */
        fun fromNative(code: Int): RewindStatus = entries.getOrElse(code) { OFF }
    }
}
