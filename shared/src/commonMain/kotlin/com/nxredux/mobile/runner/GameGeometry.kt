package com.nxredux.mobile.runner

import kotlin.math.abs

/**
 * The running game's geometry as the view reports it (`[baseWidth, baseHeight, aspectRatio, rotation]`, zeros before it
 * loads). A core can change it mid-game (SET_GEOMETRY): Azahar does when its layout option changes, as the dual-screen
 * modes switch it between the stacked and the side-by-side frame.
 */
data class GameGeometry(val width: Float, val height: Float, val aspect: Float, val rotation: Float) {
    /** The core turns its picture (SET_ROTATION, radians). */
    val rotated: Boolean get() = abs(rotation) > ROTATED

    companion object {
        private const val ROTATED = 0.01f

        val NONE = GameGeometry(0f, 0f, 0f, 0f)

        /** From the view's array; missing fields are 0. */
        fun of(g: FloatArray?): GameGeometry =
            if (g == null) NONE else GameGeometry(g.getOrElse(0) { 0f }, g.getOrElse(1) { 0f }, g.getOrElse(2) { 0f }, g.getOrElse(3) { 0f })
    }
}
