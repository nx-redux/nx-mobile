package com.nxredux.mobile.runner

/**
 * Whether the game view keeps drawing black ([com.swordfish.libretrodroid.GLRetroView.pictureHeld]) after a layout.
 * Before the first frame the core's frame size is unknown and a layout only guesses the picture's place (the whole
 * area), where the first frames would show for a frame or two and then jump; the layout that knows the frame places it.
 * Once the game runs a layout always releases it, so a core reporting no geometry still shows, fitted as before.
 */
internal object PictureHold {
    fun held(frameKnown: Boolean, gameReady: Boolean): Boolean = !frameKnown && !gameReady
}
