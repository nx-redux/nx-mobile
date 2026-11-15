package com.nxredux.mobile.runner

import android.app.Activity
import android.content.Context
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowMetricsCalculator
import com.nxredux.mobile.layout.DeviceTraits

/**
 * Which way the fold runs on this device ([DeviceTraits.foldAcrossPortrait]): learned from the first folding feature
 * a window sees and kept, so the next window (the game's process too) knows before its own reading arrives.
 */
object FoldDirection {
    private const val PREFS = "device-traits"
    private const val KEY = "fold_across_portrait"

    /** The kept direction, at start. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY)) DeviceTraits.foldAcrossPortrait.value = prefs.getBoolean(KEY, true)
    }

    /** [fold] as [activity]'s window sees it: across or down the window, and the window upright or sideways. */
    fun learn(activity: Activity, fold: FoldingFeature) {
        val b = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        if (b.width() == b.height()) return
        val across = (fold.orientation == FoldingFeature.Orientation.HORIZONTAL) == (b.height() > b.width())
        if (DeviceTraits.foldAcrossPortrait.value == across) return
        DeviceTraits.foldAcrossPortrait.value = across
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, across).apply()
    }
}
