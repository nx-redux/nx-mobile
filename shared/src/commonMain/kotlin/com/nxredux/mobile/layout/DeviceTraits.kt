package com.nxredux.mobile.layout

/**
 * What the device is, as the platform reports it once at start ([canHalfFold]); read by text and pages that only
 * matter on some devices. Defaults describe a plain phone.
 */
object DeviceTraits {
    /** The device has a hinge it can half-fold at (Android: the hinge angle sensor), so half-folded layouts apply. */
    @kotlin.concurrent.Volatile
    var canHalfFold: Boolean = false

    /**
     * The GPU's name (GL_RENDERER), set once a launch or settings page has read it (null until then): the PSP's Max Scale
     * starts lower on a modest GPU ([com.nxredux.mobile.runner.PspResolution.defaultCap]).
     */
    @kotlin.concurrent.Volatile
    var gpu: String? = null

    fun isMali(gpu: String?): Boolean = gpu?.trim()?.startsWith("Mali", ignoreCase = true) == true
}
