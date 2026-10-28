package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxButton.*

/** The Sega pad a game gets ([SegaPadType]): 3-button, or 6-button (X, Y, Z and Mode). */
enum class SegaPad { THREE, SIX }

/**
 * One console's buttons as NX Redux binds them (`skeleton/SYSTEM/tg5050/paks/Emus/<TAG>.pak/default.cfg`): what each
 * on-screen/physical position sends to the core ([sends]) and what is drawn on it
 * ([label], null = the button's own glyph), which buttons are left off the pad ([isHidden]) or only off its face
 * cluster ([isHiddenInCluster]), and which button the app's menus act on for an on-screen press ([menuButton]).
 * The Genesis family draws the real Sega 6-button pad ([segaCluster]). DC, N64, 3DS, Wii and GameCube are not here
 * ([com.nxredux.mobile.ui.nx.PadProfile]).
 */
class PadMap private constructor(
    private val routes: Map<NxButton, NxButton> = emptyMap(),
    private val labels: Map<NxButton, String> = emptyMap(),
    private val hidden: Set<NxButton> = emptySet(),
    /** On-screen button to the button the app's menus act on, so a drawn "A" (PS/PSP: ×) confirms and a drawn "B" (○) goes back. */
    private val menu: Map<NxButton, NxButton> = emptyMap(),
    /** Buttons left out of the face cluster only (Sega 3-button: the X Y Z slots); L1/R1 pills stay drawn. */
    private val clusterHidden: Set<NxButton> = emptySet(),
    /** The face cluster is the Sega 6-button pad (A B C / X Y Z) instead of the diamond. */
    val segaCluster: Boolean = false,
) {
    fun sends(button: NxButton): NxButton = routes[button] ?: button
    fun label(button: NxButton): String? = labels[button]
    fun isHidden(button: NxButton): Boolean = button in hidden
    fun isHiddenInCluster(button: NxButton): Boolean = button in clusterHidden

    /** The button the app's menus act on for an on-screen [button]; the game still receives the raw button. */
    fun menuButton(button: NxButton): NxButton = menu[button] ?: button

    /** The keycode a pressed [keyCode] reaches the core as; keys that are not face/shoulder/START/SELECT pass through. */
    fun sendKey(keyCode: Int): Int {
        val button = BY_KEY[keyCode] ?: return keyCode
        return KEY_OF.getValue(sends(button))
    }

    companion object {
        // Android's KeyEvent.KEYCODE_BUTTON_* (no Android imports in shared).
        const val KEY_A = 96
        const val KEY_B = 97
        const val KEY_X = 99
        const val KEY_Y = 100
        const val KEY_L1 = 102
        const val KEY_R1 = 103
        const val KEY_L2 = 104
        const val KEY_R2 = 105
        const val KEY_START = 108
        const val KEY_SELECT = 109
        private val KEY_OF = mapOf(A to KEY_A, B to KEY_B, X to KEY_X, Y to KEY_Y, L1 to KEY_L1, R1 to KEY_R1,
            L2 to KEY_L2, R2 to KEY_R2, START to KEY_START, SELECT to KEY_SELECT)
        private val BY_KEY = KEY_OF.entries.associate { (b, k) -> k to b }

        val IDENTITY = PadMap()

        private val SEGA_TAGS = setOf("MD", "GPGX", "SEGACD", "32X")

        /** The tags with the Sega Controller Type row ([SegaPadType]); a GPGX_<X> tag as <X> ([CoreRegistry.baseTag]), here and in [forTag]. */
        fun isSegaPadTag(tag: String): Boolean = CoreRegistry.baseTag(tag) in SEGA_TAGS

        // NX Redux MD.pak: A=Y, B=X:B, C=A, X=B:L1, Y=L1:X, Z=R1, Mode=SELECT.
        private val SEGA_ROUTES = mapOf(X to B, B to L1, L1 to X)
        // In the menus the drawn A (Y) confirms and the drawn B (X) goes back; the drawn C and X act as menu X and Y.
        private val SEGA_MENU = mapOf(Y to A, X to B, A to X, B to Y)
        private val SEGA_SIX = PadMap(SEGA_ROUTES,
            mapOf(Y to "A", X to "B", A to "C", B to "X", L1 to "Y", R1 to "Z", SELECT to "MODE"),
            menu = SEGA_MENU, segaCluster = true)
        // 3-button: the cluster shows only A B C, Mode is gone, and the L1/R1 pills keep their own glyphs.
        private val SEGA_THREE = PadMap(SEGA_ROUTES, mapOf(Y to "A", X to "B", A to "C"), hidden = setOf(SELECT),
            menu = SEGA_MENU, clusterHidden = setOf(B, L1, R1), segaCluster = true)
        private val SEGA_8BIT = PadMap(labels = mapOf(B to "1", A to "2"))
        private val TURBO_AB = mapOf(X to "TA", Y to "TB")

        fun forTag(tag: String, sega: SegaPad? = null): PadMap = when (CoreRegistry.baseTag(tag)) {
            in SEGA_TAGS -> if (sega == SegaPad.THREE) SEGA_THREE else SEGA_SIX
            "SMS", "GG", "SG1000", "A7800" -> SEGA_8BIT
            "NGP", "NGPC" -> PadMap(labels = mapOf(B to "A", A to "B", START to "OPTION"), menu = mapOf(B to A, A to B, Y to X, X to Y))
            "PCE" -> PadMap(labels = mapOf(A to "I", B to "II", Y to "III", X to "IV", L1 to "V", R1 to "VI", L2 to "Mode", START to "RUN"))
            // PPSSPP reads the RetroPad like beetle-psx: B = Cross, A = Circle, X = Triangle, Y = Square.
            // In the menus × (bottom) confirms and ○ (right) goes back, the Western PlayStation convention.
            "PS", "PSX", "PS2", "PSP" -> PadMap(labels = mapOf(A to "○", B to "×", X to "△", Y to "□"), menu = mapOf(B to A, A to B))
            "FBN" -> PadMap(labels = mapOf(SELECT to "COIN"))
            "P8" -> PadMap(labels = mapOf(B to "O", A to "X"))
            "A2600" -> PadMap(labels = mapOf(B to "Fire"))
            "LYNX" -> PadMap(labels = mapOf(L1 to "Opt 1", R1 to "Opt 2"))
            "PKM" -> PadMap(labels = mapOf(R1 to "C", L1 to "Shake", SELECT to "POWER", X to "TA"))
            "GB", "GBC", "GBA" -> PadMap(labels = TURBO_AB)
            "MGBA", "SGB" -> PadMap(labels = TURBO_AB + mapOf(L2 to "TL", R2 to "TR"))
            "VB" -> PadMap(labels = mapOf(X to "Batt", L2 to "R↑", R2 to "R←"))
            "FC", "FDS" -> PadMap(labels = mapOf(L1 to "Side", R1 to "Insert", R2 to "Coin"))
            // The DS has only L and R. L2 and R2 are NX Redux's stylus and swap-screens hotkeys (NdsHotkeys): a controller's
            // still work, and the on-screen pad has STYLUS and SWAP pills for them instead (PadTools).
            "NDS" -> PadMap(labels = mapOf(L1 to "L", R1 to "R"), hidden = setOf(L2, R2))
            else -> IDENTITY
        }
    }
}
