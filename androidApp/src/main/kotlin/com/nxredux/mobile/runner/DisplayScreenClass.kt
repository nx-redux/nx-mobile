package com.nxredux.mobile.runner

import android.content.res.Resources
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.screenClass

/** The window's [ScreenClass] from its pixel size and smallest width (the class does not change on rotation). */
internal fun Resources.screenClass(): ScreenClass =
    screenClass(displayMetrics.widthPixels, displayMetrics.heightPixels, configuration.smallestScreenWidthDp)
