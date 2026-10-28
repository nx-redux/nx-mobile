package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.DcInput
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.runner.N64Input
import com.nxredux.mobile.runner.PadMap
import com.nxredux.mobile.runner.SegaPad
import com.nxredux.mobile.runner.WiiPad

/** Which face cluster a pad draws: the X/Y/B/A diamond, the Nintendo 64's A, B and C diamond, or the Sega 6-button pad. */
enum class FaceCluster { DIAMOND, N64, SEGA }

/**
 * A face-button symbol drawn as a shape instead of its text label (PlayStation ○ × △ □, whose font glyphs have uneven
 * weights). [label] is the text the symbol stands for, which [PadProfile.label] still returns.
 */
enum class PadIcon(val label: String) {
    PS_CROSS("×"), PS_CIRCLE("○"), PS_TRIANGLE("△"), PS_SQUARE("□");

    companion object {
        private val BY_LABEL = entries.associateBy { it.label }
        fun forLabel(label: String?): PadIcon? = BY_LABEL[label]
    }
}

/**
 * How the on-screen pad looks for the running game's system: its face cluster, the letters it shows and the buttons it
 * leaves out. Most consoles read these from their [PadMap] (NX Redux's binds); DC and N64 keep their own rules. What each
 * button sends is decided elsewhere ([PadMap.sends], [N64Input], [DcInput]).
 */
class PadProfile private constructor(
    val kind: Kind,
    private val map: PadMap,
    private val n3dsNew: Boolean = false,
    val wiiPad: WiiPad? = null,
    /** The layout editor's Wii pad ([forLayoutEditor]): the Classic Controller's groups and the Wiimote's MOTION. */
    private val editorMotion: Boolean = false,
) {
    enum class Kind {
        /** The console's [PadMap] labels (the buttons' own letters where it has none): X top, Y left, A right, B bottom. */
        TABLE,

        /** The Dreamcast's letters by position (flycast reads JOYPAD_B as its A): A bottom, B right, X left, Y top. */
        DREAMCAST,

        /**
         * A, B and C▲◀▶▼ like a real N64 pad, L2 as Z. R2 and SELECT send nothing on N64: R2 is still drawn (every
         * console draws L1/L2/R1/R2), SELECT is not.
         */
        N64,

        /**
         * The Nintendo 3DS: A right, B bottom, X top, Y left like the DS, L and R as the shoulders. ZL and ZR (L2 / R2,
         * which Azahar reads as ZL / ZR) and the C-stick (the core's right analog) only with the New 3DS controls
         * ([com.nxredux.mobile.runner.N3dsNewControls]); without them L2 and R2 are not drawn.
         */
        N3DS,

        /**
         * The Wii pad for the resolved [WiiPad]: the face buttons carry the Wii labels of the core's own binds (X top,
         * Y left, B bottom, A right), SELECT / START are - / + (the Nunchuk pad swaps them to 2 / 1), and R2 / L2 are
         * the Shake buttons. The sideways and upright Wiimote hide the shoulders it does not use; the Classic
         * Controller shows L, R, ZL and ZR and the C-stick (the core's right analog).
         */
        WII,

        /**
         * The GameCube: X top, Y left, B bottom, A right like the diamond, with Z on R1, L / R on L2 / R2 (analog
         * triggers, see [com.nxredux.mobile.runner.TriggerGesture]) and the C-stick (the core's right analog). L1 and
         * SELECT send nothing and are not drawn.
         */
        GC,

        /**
         * The PlayStation 2's DualShock 2: the PlayStation labels of its [PadMap] (○ × △ □, × confirms), both sticks
         * always drawn beside the d-pad and the face cluster ([dualSticks], no d-pad / stick toggle), and L3 / R3 as
         * their own pills.
         */
        PS2,
    }

    /**
     * The pad draws a C-stick, the core's right analog (the New 3DS, the Wii Classic Controller, the GameCube): a small
     * analog stick in the face cluster's top-left corner in landscape ([cStickLayout]), between the clusters in portrait.
     */
    val cStick: Boolean get() = kind == Kind.N3DS && n3dsNew || wiiPad == WiiPad.CLASSIC || kind == Kind.GC

    /**
     * The pad draws the d-pad and the left stick together, and the face cluster with the right stick (the core's right
     * analog), each stick with its click as a pill (L3 / R3): the PS2, whose games switch between d-pad and stick mid-play.
     * No d-pad / stick toggle then ([padSystemRow]).
     */
    val dualSticks: Boolean get() = kind == Kind.PS2

    /**
     * The pad shows the MOTION button (tap toggles motion, long-press recentres): every Wii pad but the Classic
     * Controller; the layout editor's Wii pad too when the phone has motion ([forLayoutEditor]).
     */
    val motionButton: Boolean get() = kind == Kind.WII && (wiiPad != WiiPad.CLASSIC || editorMotion)

    /** The pad shows HOME between SELECT and START, as on a Wiimote: every Wii pad. */
    val homeButton: Boolean get() = kind == Kind.WII

    /**
     * The pad starts on its stick, not the d-pad, until the player chooses ([PadStickModes]): the Nunchuk's stick and the
     * Classic Controller's left stick are what those games move with.
     */
    val stickByDefault: Boolean get() = wiiPad == WiiPad.NUNCHUK || wiiPad == WiiPad.CLASSIC

    val cluster: FaceCluster get() = when {
        kind == Kind.N64 -> FaceCluster.N64
        kind == Kind.TABLE && map.segaCluster -> FaceCluster.SEGA
        else -> FaceCluster.DIAMOND
    }

    fun label(button: NxButton): String = when (kind) {
        Kind.TABLE, Kind.PS2 -> map.label(button) ?: button.glyph()
        Kind.DREAMCAST -> when (button) {
            NxButton.B -> "A"
            NxButton.A -> "B"
            NxButton.Y -> "X"
            NxButton.X -> "Y"
            else -> button.glyph()
        }
        Kind.N64 -> if (button == NxButton.L2) "Z" else button.glyph()
        Kind.N3DS -> when (button) {
            NxButton.L1 -> "L"
            NxButton.R1 -> "R"
            NxButton.L2 -> "ZL"
            NxButton.R2 -> "ZR"
            else -> button.glyph()
        }
        Kind.WII -> wiiLabel(button)
        Kind.GC -> when (button) {
            NxButton.R1 -> "Z"
            NxButton.L2 -> "L"
            NxButton.R2 -> "R"
            else -> button.glyph()
        }
    }

    private fun wiiLabel(button: NxButton): String = when (wiiPad!!) {
        WiiPad.SIDEWAYS -> when (button) { NxButton.X -> "A"; NxButton.Y -> "B"; NxButton.B -> "1"; NxButton.A -> "2"; NxButton.R2 -> "Shake"; NxButton.SELECT -> "−"; NxButton.START -> "+"; else -> button.glyph() }
        WiiPad.UPRIGHT -> when (button) { NxButton.X -> "1"; NxButton.Y -> "2"; NxButton.B -> "B"; NxButton.A -> "A"; NxButton.R2 -> "Shake"; NxButton.SELECT -> "−"; NxButton.START -> "+"; else -> button.glyph() }
        WiiPad.NUNCHUK -> when (button) { NxButton.X -> "C"; NxButton.Y -> "Z"; NxButton.B -> "B"; NxButton.A -> "A"; NxButton.L1 -> "−"; NxButton.R1 -> "+"; NxButton.L2 -> "Shake N"; NxButton.R2 -> "Shake"; NxButton.SELECT -> "2"; NxButton.START -> "1"; else -> button.glyph() }
        WiiPad.CLASSIC -> when (button) { NxButton.X -> "x"; NxButton.Y -> "y"; NxButton.B -> "b"; NxButton.A -> "a"; NxButton.L1 -> "L"; NxButton.R1 -> "R"; NxButton.L2 -> "ZL"; NxButton.R2 -> "ZR"; NxButton.SELECT -> "−"; NxButton.START -> "+"; else -> button.glyph() }
    }

    private fun wiiHidden(button: NxButton): Boolean = when (wiiPad!!) {
        WiiPad.SIDEWAYS, WiiPad.UPRIGHT -> button == NxButton.L1 || button == NxButton.R1 || button == NxButton.L2
        WiiPad.NUNCHUK, WiiPad.CLASSIC -> false
    }

    /** The symbol drawn on [button] in place of its [label] text, or null to draw the text. */
    fun icon(button: NxButton): PadIcon? = if (kind == Kind.TABLE || kind == Kind.PS2) PadIcon.forLabel(map.label(button)) else null

    /**
     * The button the app's menus act on for an on-screen [button]. Where the drawn "A" and "B" are not on the right and
     * bottom buttons the menus follow the drawn letter: DC and NGP (A bottom, B right) and the Sega pad (A and B the
     * first two of its bottom row, [PadMap.menuButton]). PS, PS2 and PSP follow the Western PlayStation convention: × (bottom)
     * confirms and ○ (right) goes back. Elsewhere they go by position (right confirms, bottom goes back). The game still
     * receives the raw button.
     */
    fun menuButton(button: NxButton): NxButton = when (kind) {
        Kind.TABLE, Kind.PS2 -> map.menuButton(button)
        Kind.DREAMCAST -> when (button) {
            NxButton.B -> NxButton.A
            NxButton.A -> NxButton.B
            NxButton.Y -> NxButton.X
            NxButton.X -> NxButton.Y
            else -> button
        }
        Kind.N64, Kind.N3DS, Kind.WII, Kind.GC -> button
    }

    fun isHidden(button: NxButton): Boolean = when (kind) {
        Kind.TABLE, Kind.PS2 -> map.isHidden(button)
        Kind.DREAMCAST -> false
        Kind.N64 -> button == NxButton.SELECT
        Kind.N3DS -> !n3dsNew && (button == NxButton.L2 || button == NxButton.R2)
        Kind.WII -> wiiHidden(button)
        Kind.GC -> button == NxButton.L1 || button == NxButton.SELECT
    }

    /** Whether the face cluster leaves [button] out: [isHidden], or (Sega 3-button) the X Y Z slots. */
    fun isHiddenInCluster(button: NxButton): Boolean = isHidden(button) || (kind == Kind.TABLE && map.isHiddenInCluster(button))

    override fun toString(): String = when {
        this === STANDARD -> "STANDARD"
        this === N3DS_NEW -> "N3DS_NEW"
        kind == Kind.WII -> "WII_$wiiPad"
        else -> kind.name
    }

    companion object {
        val STANDARD = PadProfile(Kind.TABLE, PadMap.IDENTITY)
        val DREAMCAST = PadProfile(Kind.DREAMCAST, PadMap.IDENTITY)
        val N64 = PadProfile(Kind.N64, PadMap.IDENTITY)
        val N3DS = PadProfile(Kind.N3DS, PadMap.IDENTITY)
        val N3DS_NEW = PadProfile(Kind.N3DS, PadMap.IDENTITY, n3dsNew = true)
        val GC = PadProfile(Kind.GC, PadMap.IDENTITY)
        val PS2 = PadProfile(Kind.PS2, PadMap.forTag("PS2"))

        /**
         * The pad the layout editor arranges for [tag]: the console's fullest, so every group it can show is placed (the
         * New 3DS's C-stick; the Wii's Classic Controller, with its C-stick, every shoulder and HOME, plus the Wiimote's
         * MOTION when [motion]: the phone has the sensors, so its Wiimote games draw it, [motionButton]).
         */
        fun forLayoutEditor(tag: String, motion: Boolean = false): PadProfile =
            if (tag.trim().uppercase() == "WII") PadProfile(Kind.WII, PadMap.IDENTITY, wiiPad = WiiPad.CLASSIC, editorMotion = motion)
            else forTag(tag, n3dsNew = true)

        /**
         * [sega]: the resolved Sega pad for the Sega pad tags (null = unknown: all six drawn). [n3dsNew]: the 3DS pad
         * draws the New 3DS controls (resolved New 3DS Controls setting). [wii]: the resolved Wii controller (null = not
         * resolved yet: the plain pad). [gameCube]: a GameCube disc running under the Wii tag.
         */
        fun forTag(tag: String, sega: SegaPad? = null, n3dsNew: Boolean = false, wii: WiiPad? = null, gameCube: Boolean = false): PadProfile = when {
            gameCube || tag.trim().uppercase() == "GC" -> GC
            tag.trim().uppercase() == "PS2" -> PS2
            wii != null && WiiOptions.appliesTo(tag) -> PadProfile(Kind.WII, PadMap.IDENTITY, wiiPad = wii)
            DcInput.appliesTo(tag) -> DREAMCAST
            N64Input.appliesTo(tag) -> N64
            N3dsOptions.appliesTo(tag) -> if (n3dsNew) N3DS_NEW else N3DS
            else -> PadMap.forTag(tag, sega).let { if (it === PadMap.IDENTITY) STANDARD else PadProfile(Kind.TABLE, it) }
        }
    }
}
