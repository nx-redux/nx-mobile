package com.nxredux.mobile.runner

/**
 * The phone's sensor axes to the Wii U GamePad's as Cemu's libretro core reads them: Android's own convention (+X right,
 * +Y up, +Z out toward you; the accelerometer's reaction to gravity in g, the gyroscope in rad/s counter-clockwise), but
 * of the screen as it is held rather than of the phone's natural orientation, so a phone held in landscape tilts and
 * turns the GamePad the way it is held ([rotation] is Surface.ROTATION_*). Not [WiimoteFrame.display], which comes out
 * turned 180° about the screen's normal in landscape (see [N3dsFrame]). No level: the GamePad reads its attitude as it is.
 */
object GamePadFrame {
    private fun screen(p: Vec3, rotation: Int) = when (rotation and 3) {
        1 -> Vec3(-p.y, p.x, p.z)
        2 -> Vec3(-p.x, -p.y, p.z)
        3 -> Vec3(p.y, -p.x, p.z)
        else -> p
    }

    fun accel(phoneG: Vec3, rotation: Int): Vec3 = screen(phoneG, rotation)

    fun gyro(phone: Vec3, rotation: Int): Vec3 = screen(phone, rotation)
}
