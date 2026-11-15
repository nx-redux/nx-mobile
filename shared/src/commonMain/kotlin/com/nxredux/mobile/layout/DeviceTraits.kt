package com.nxredux.mobile.layout

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the device is, as the platform reports it once at start ([canHalfFold]); read by text and pages that only
 * matter on some devices. Defaults describe a plain phone.
 */
object DeviceTraits {
    /** The device has a hinge it can half-fold at (Android: the hinge angle sensor), so half-folded layouts apply. */
    @kotlin.concurrent.Volatile
    var canHalfFold: Boolean = false

    /**
     * Whether the fold runs across a portrait window (true: a wide-screen Fold upright) or down it (false: a book
     * foldable's near-square inner screen upright); null until a fold is first seen. Kept across starts, as it never
     * changes on a device and a window lays out before its own fold reading arrives.
     */
    val foldAcrossPortrait = MutableStateFlow<Boolean?>(null)

    /**
     * A real hinge runs across a [w] x [h] window, so a fold board draws its hinge there; else the board is one slab
     * ([com.nxredux.mobile.skin.SkinSeam]). Before the fold's direction is known, a foldable is taken to have one.
     */
    fun hingeAcross(w: Int, h: Int, acrossPortrait: Boolean? = foldAcrossPortrait.value): Boolean =
        canHalfFold && (acrossPortrait?.let { it == (h > w) } ?: true)
}
