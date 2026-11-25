package com.nxredux.mobile.options

import com.nxredux.mobile.runner.Res3dConsole

/** Saturn settings the app owns on YabaSanshiro: the 3D Resolution rows. */
object SaturnOptions {
    fun appliesTo(tag: String): Boolean = Res3dConsole.forTag(tag) == Res3dConsole.SATURN

    /** The 3D Resolution rows (mode, Max Scale, Scale), as the other consoles' ones; the scales are 1x, 2x and 4x. */
    fun resolutionRows(displayMax: Int?): List<FrontendOption> = resolutionRows(Res3dConsole.SATURN, displayMax)
}
