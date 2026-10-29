package com.nxredux.mobile.settings

import kotlin.math.roundToInt

/**
 * The selection colour (Appearance -> Accent): every selection mark (list pills, lit tiles, lit tool squares,
 * game rings, the tab-focus plate) draws in it. Every preset is light, so the text on it is always black
 * ([com.nxredux.mobile.ui.nx.NxColors.onAccent]). Tailwind CSS palette colours (zinc-200; Orange, Violet, Pink -300,
 * the rest -400), in the picker's order; [WHITE] is the default. Every preset but Red has a relative luminance of 0.43
 * or more, so a selected name stays clearly brighter than an unselected one (ink 60%) and the hint grey. Red stays
 * red-400, since a brighter red reads as pink; each layout still marks the selection by more than the name's
 * brightness. [id] is what is
 * stored (a shade change keeps it, so a stored accent takes the new shade); [rgb] is 0xRRGGBB.
 */
enum class AccentPreset(val id: String, val label: String, val rgb: Int) {
    WHITE("white", "White", 0xE4E4E7),     // zinc-200, black text 16.6:1
    RED("red", "Red", 0xF87171),           // red-400, 7.6:1. The one exception to the luminance floor (0.33): a red bright enough turns pink
    ORANGE("orange", "Orange", 0xFDBA74),  // orange-300, 12.5:1
    AMBER("amber", "Amber", 0xFBBF24),     // amber-400, 12.6:1
    LIME("lime", "Lime", 0xA3E635),        // lime-400, 13.9:1
    TEAL("teal", "Teal", 0x2DD4BF),        // teal-400, 11.3:1
    SKY("sky", "Sky", 0x38BDF8),           // sky-400, 9.8:1
    VIOLET("violet", "Violet", 0xC4B5FD),  // violet-300, 11.4:1
    PINK("pink", "Pink", 0xF9A8D4);        // pink-300, 11.6:1

    /** 0xFFRRGGBB. */
    val argb: Int get() = (0xFF shl 24) or rgb

    /** [rgb] as six lower-case hex digits, no '#'. */
    val hex: String get() = rgb.toString(16).padStart(6, '0')

    /** The preset [step] places after this one, wrapping (Settings' LEFT is -1, RIGHT and A +1). */
    fun step(step: Int): AccentPreset = entries.stepFrom(this, step)

    companion object {
        val DEFAULT = WHITE

        /** The preset stored as [id]; missing or unknown: [DEFAULT]. */
        fun fromId(id: String?): AccentPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * The Secondary accent's own colours: every one dark, its text [SECONDARY_TEXT] (#DCDCDC). NX teal and NX red are the NX
 * Redux handheld's; the rest Tailwind CSS -900 tokens (Graphite zinc-700). [id] is what is stored; [rgb] is 0xRRGGBB.
 */
enum class SecondaryPreset(val id: String, val label: String, val rgb: Int) {
    TEAL("teal", "Teal", 0x006666),        // NX teal (NX Redux THEME_COLOR2), text 5.0:1
    RED("red", "Red", 0x660000),           // NX red, 9.8:1
    RUST("rust", "Rust", 0x7C2D12),        // orange-900, 6.8:1
    BRONZE("bronze", "Bronze", 0x78350F),  // amber-900, 6.6:1
    MOSS("moss", "Moss", 0x365314),        // lime-900, 6.4:1
    NAVY("navy", "Navy", 0x0C4A6E),        // sky-900, 6.9:1
    PLUM("plum", "Plum", 0x4C1D95),        // violet-900, 8.0:1
    WINE("wine", "Wine", 0x831843),        // pink-900, 7.0:1
    GRAPHITE("graphite", "Graphite", 0x3F3F46);   // zinc-700, 7.6:1

    /** 0xFFRRGGBB. */
    val argb: Int get() = (0xFF shl 24) or rgb
}

/** The text on every Secondary accent (each one dark). */
const val SECONDARY_TEXT = 0xDCDCDC

/**
 * The bar behind a selected option's value (Appearance -> Secondary accent), on the Settings value pages, the Game
 * Settings modal and the in-game menu; never on the main menu. [Auto] (the default) darkens the accent, so any accent
 * gets a matched dark pair, White NX teal; [Preset] is one of the [SecondaryPreset]s, for a player who picks their own. Every one is dark, its text [SECONDARY_TEXT].
 * [entries] is the picker's order; [id] is what is stored. [rgb] is 0xRRGGBB for the current accent.
 */
sealed class SecondaryAccent(val id: String, val label: String) {
    abstract fun rgb(accent: AccentPreset): Int
    fun onRgb(): Int = SECONDARY_TEXT

    /** The accent at [AUTO_BRIGHTNESS] (each channel, rounded to the nearest): always dark, so light text. White gives NX teal. */
    data object Auto : SecondaryAccent("auto", "Auto") {
        override fun rgb(accent: AccentPreset): Int =
            if (accent == AccentPreset.WHITE) SecondaryPreset.TEAL.rgb
            else (0..2).fold(0) { acc, i -> acc or (((accent.rgb shr (8 * i)) and 0xFF) * AUTO_BRIGHTNESS).roundToInt().shl(8 * i) }
    }

    /** [preset]'s own colour, whatever the accent. */
    data class Preset(val preset: SecondaryPreset) : SecondaryAccent(preset.id, preset.label) {
        override fun rgb(accent: AccentPreset): Int = preset.rgb
    }

    /** The value [step] places after this one in [entries], wrapping (Settings' LEFT is -1, RIGHT and A +1). */
    fun step(step: Int): SecondaryAccent = entries.stepFrom(this, step)

    companion object {
        private const val AUTO_BRIGHTNESS = 0.45

        /** Auto, then the [SecondaryPreset]s in their order. */
        val entries: List<SecondaryAccent> by lazy { listOf(Auto) + SecondaryPreset.entries.map(::Preset) }

        val DEFAULT: SecondaryAccent get() = Auto

        /**
         * Before the Secondary accent had its own colours it borrowed the Accent presets: those ids read as the dark
         * colour of the same hue (White as Graphite); Teal and Red kept theirs.
         */
        private val LEGACY_IDS = mapOf(
            "orange" to SecondaryPreset.RUST, "amber" to SecondaryPreset.BRONZE, "lime" to SecondaryPreset.MOSS,
            "sky" to SecondaryPreset.NAVY, "violet" to SecondaryPreset.PLUM, "pink" to SecondaryPreset.WINE,
            "white" to SecondaryPreset.GRAPHITE,
        )

        /** The value stored as [id] (an old Accent preset id by [LEGACY_IDS]); [DEFAULT] when missing or unknown. */
        fun fromId(id: String?): SecondaryAccent =
            entries.firstOrNull { it.id == id } ?: LEGACY_IDS[id]?.let(::Preset) ?: DEFAULT
    }
}
