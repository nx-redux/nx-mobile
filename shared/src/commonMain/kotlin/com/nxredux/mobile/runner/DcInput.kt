package com.nxredux.mobile.runner

/**
 * The Dreamcast's input tag test (DC, NAOMI/Atomiswave sets included: they run under DC), for its analog-trigger routing
 * and its on-screen face letters ([com.nxredux.mobile.ui.nx.PadProfile]). Its physical face buttons go by printed letter
 * ([PhysicalKeys], [FaceMode.LETTERS_SWAPPED]): the pad's A is the Dreamcast's A.
 */
object DcInput {
    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "DC"
}

/**
 * How a console's face buttons take a pad's A/B/X/Y (Controller Layout): [LETTERS] by printed letter, the
 * core's RetroPad A being the console's A (SNES, NES, Game Boy, GBA, DS, 3DS, Virtual Boy, Lynx, Pokemon mini,
 * WonderSwan; N64 too, whose [N64Input] then reads that letter by name); [LETTERS_SWAPPED] by printed letter on a core
 * that reads the console's A from JOYPAD_B, B from JOYPAD_A, X from JOYPAD_Y and Y from JOYPAD_X (Dreamcast with
 * NAOMI/Atomiswave, Neo Geo Pocket); [POSITIONS] by position, for buttons with no matching letters or a layout that is
 * positional by nature (PlayStation symbols, Sega A B C / X Y Z rows, 1/2, I/II, arcade and the rest): the console's
 * bottom button is the pad's bottom button, as the SNES-shaped RetroPad has it.
 */
enum class FaceMode { LETTERS, LETTERS_SWAPPED, POSITIONS }

/**
 * The keycode a physical key is sent to the runner as. The runner hands each core the key's own letter (BUTTON_B as
 * JOYPAD_B). A pad's keycodes sit where its detected [PadFaceLayout] says (an Xbox-layout pad's bottom key is BUTTON_A,
 * a Nintendo-layout pad's BUTTON_B); Controls → Controller Layout names the letters printed on it (Auto: the
 * detected layout). A key is first turned into its printed letter ([printed]: swapped when the two differ), then sent
 * per the console's [FaceMode]. An unknown device (null detected layout: an injected or virtual-device event) is sent
 * as is.
 */
object PhysicalKeys {
    /** Android's KeyEvent.KEYCODE_BUTTON_A/B/X/Y (no Android imports in shared). */
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100

    private val LETTER_TAGS = setOf("SFC", "SUPA", "FC", "FDS", "GB", "GBC", "GBA", "MGBA", "SGB", "NDS", "3DS", "VB", "LYNX",
        "PKM", "WSC", "N64", "WII", "GC")
    private val SWAPPED_TAGS = setOf("DC", "NGP", "NGPC")

    fun modeFor(tag: String): FaceMode = when (tag.trim().uppercase()) {
        in LETTER_TAGS -> FaceMode.LETTERS
        in SWAPPED_TAGS -> FaceMode.LETTERS_SWAPPED
        else -> FaceMode.POSITIONS
    }

    /** A with B and X with Y; every other key as is. */
    fun swap(keyCode: Int): Int = when (keyCode) {
        BUTTON_A -> BUTTON_B
        BUTTON_B -> BUTTON_A
        BUTTON_X -> BUTTON_Y
        BUTTON_Y -> BUTTON_X
        else -> keyCode
    }

    /**
     * The letter printed on the key [keyCode] of a pad whose keys sit as [detected] and whose labels read as [label]:
     * the key itself when they match (or either is unknown), else its A/B, X/Y partner. Also what the in-game menus act on.
     */
    fun printed(keyCode: Int, detected: PadFaceLayout?, label: PadFaceLayout?): Int =
        if (detected == null || label == null || detected == label) keyCode else swap(keyCode)

    fun toRunner(tag: String, keyCode: Int, detected: PadFaceLayout?, label: PadFaceLayout?): Int {
        if (detected == null) return keyCode
        return when (modeFor(tag)) {
            FaceMode.LETTERS -> printed(keyCode, detected, label)
            FaceMode.LETTERS_SWAPPED -> swap(printed(keyCode, detected, label))
            // The bottom key goes to JOYPAD_B whatever is printed on it.
            FaceMode.POSITIONS -> if (detected == PadFaceLayout.XBOX) swap(keyCode) else keyCode
        }
    }
}
