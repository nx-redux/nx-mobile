package com.nxredux.mobile.layout

/** Coarse screen category that picks the pad layout. Orientation-independent. */
enum class ScreenClass {
    REGULAR_PHONE,
    STUBBY_PHONE,
    WIDE_TABLET,
    NEAR_SQUARE_TABLET;

    /** True for the tablet classes (smallest width at least [LayoutConstants.TABLET_MIN_DP]). */
    val isTablet: Boolean get() = this == WIDE_TABLET || this == NEAR_SQUARE_TABLET

    /** True for the near-square shapes: the stubby phone and the near-square tablet. */
    val isFoldShaped: Boolean get() = this == STUBBY_PHONE || this == NEAR_SQUARE_TABLET
}

/** Shared numbers of the pad layout model. */
object LayoutConstants {
    const val TABLET_MIN_DP = 600
    const val WIDE_TABLET_RATIO = 1.5f
    const val STUBBY_PHONE_RATIO = 1.8f
    const val UNIT_MM = 11f
    const val BAND_CM = 5.8f
    const val BAND_MAX_SHARE = 0.6f
    const val CLUSTER_PADDING_DP = 16f
    const val EDGE_NUDGE_UNITS = 0.2f
    const val REFERENCE_LINES = 224
    const val HIDDEN_BAND_REACH = 0.25f

    /** The half-folded DS pad unit: the Fold cover screen's (1248 - 2 x 16 dp x 2.625) / 9 = 129.3 px at 428 dpi, about 7.67 mm. */
    const val TABLETOP_DS_UNIT_MM = 7.7f

    /**
     * The share of the pad's reach the half-folded touch screen leaves free on each side: it may overlap each half by at
     * most a quarter of its reach.
     */
    const val TABLETOP_DS_SET_ASIDE = 0.75f
}

/**
 * Classifies a screen from its pixel size and smallest width in dp. Ratio is long side over
 * short side, so rotating the device does not change the class.
 */
fun screenClass(widthPx: Int, heightPx: Int, smallestWidthDp: Int): ScreenClass {
    val long = maxOf(widthPx, heightPx).toFloat()
    val short = minOf(widthPx, heightPx).toFloat()
    val ratio = if (short > 0f) long / short else Float.MAX_VALUE
    return if (smallestWidthDp >= LayoutConstants.TABLET_MIN_DP) {
        if (ratio >= LayoutConstants.WIDE_TABLET_RATIO) ScreenClass.WIDE_TABLET else ScreenClass.NEAR_SQUARE_TABLET
    } else {
        if (ratio < LayoutConstants.STUBBY_PHONE_RATIO) ScreenClass.STUBBY_PHONE else ScreenClass.REGULAR_PHONE
    }
}

/** Pixels in one layout unit (11 mm); ydpi outside 40..1200 falls back to density x 160. */
fun unitPx(ydpi: Float, density: Float): Float = mmToPx(LayoutConstants.UNIT_MM, ydpi, density)

/** [mm] in pixels at [ydpi], or at density x 160 when ydpi is outside 40..1200 (an unreported or bogus value). */
private fun mmToPx(mm: Float, ydpi: Float, density: Float): Float {
    val dpi = if (ydpi in 40f..1200f) ydpi else density * 160f
    return mm / 25.4f * dpi
}

/** Pixels in one half-folded DS layout unit ([LayoutConstants.TABLETOP_DS_UNIT_MM]); ydpi as [unitPx]. */
fun tabletopDsUnitPx(ydpi: Float, density: Float): Float = mmToPx(LayoutConstants.TABLETOP_DS_UNIT_MM, ydpi, density)
