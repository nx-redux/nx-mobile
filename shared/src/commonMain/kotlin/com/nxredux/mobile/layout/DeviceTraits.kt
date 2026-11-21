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
     * starts lower on a modest GPU ([modestGpu], [com.nxredux.mobile.runner.Res3dConsole.defaultCap]).
     */
    @kotlin.concurrent.Volatile
    var gpu: String? = null

    fun isMali(gpu: String?): Boolean = gpu?.trim()?.startsWith("Mali", ignoreCase = true) == true

    fun isAdreno(gpu: String?): Boolean = gpu?.contains("Adreno", ignoreCase = true) == true

    private val ADRENO = Regex("""Adreno\D*(\d{3})""", RegexOption.IGNORE_CASE)

    /**
     * A Mali, a PowerVR, or an Adreno below the flagship ones: 640 / 650 / 660 (Snapdragon 855 to 888), 730 and up
     * (8 Gen 1 on), 830 and up (8 Elite on). Any other GPU, or one not read yet (null), is not.
     */
    fun modestGpu(gpu: String?): Boolean {
        if (isMali(gpu) || gpu?.contains("PowerVR", ignoreCase = true) == true) return true
        val model = gpu?.let { ADRENO.find(it) }?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return when (model / 100) {
            6 -> model !in setOf(640, 650, 660)
            7 -> model < 730
            8 -> model < 830
            else -> model < 600
        }
    }
}
