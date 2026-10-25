package com.nxredux.mobile.ui

import coil3.SingletonImageLoader

/**
 * Installs [nxImageLoader] as the process's image loader. Called when the process starts: a game launched from another
 * launcher starts at the game screen, which never composes [App], so its menu would otherwise get Coil's default loader
 * (no file mapper: a save state's screenshot named with '#' or '?' would not load).
 */
object NxImages {
    fun install() = SingletonImageLoader.setSafe(::nxImageLoader)
}
