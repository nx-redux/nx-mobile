package com.nxredux.mobile.ui.nx

/**
 * A finger the skin owns but no cap takes, passed on to the game's touch screen. Android gives a whole gesture to the
 * view its first finger landed on: with a cap held first (A while driving), a later finger on the touch glass reaches
 * the skin view, not the game view under it. The first such finger drives the game's pointer through [send] (view px,
 * down = pressed or moved, else lifted) until it lifts or the gesture ends; a finger on a cap is never passed.
 */
class SkinGlassPass(private val send: (x: Float, y: Float, down: Boolean) -> Unit) {
    private var finger: Long? = null

    fun down(id: Long, x: Float, y: Float, onCap: Boolean) {
        if (onCap || finger != null) return
        finger = id
        send(x, y, true)
    }

    fun move(id: Long, x: Float, y: Float) {
        if (id == finger) send(x, y, true)
    }

    fun up(id: Long) {
        if (id != finger) return
        finger = null
        send(0f, 0f, false)
    }

    /** The gesture ended (the last finger up, a cancel, the view gone): a passed finger lifts. */
    fun cancel() { finger?.let(::up) }
}
