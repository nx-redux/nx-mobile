package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.options.PxRect

/** A console keypad's keys, in reading order: 1 2 3 / 4 5 6 / 7 8 9 / * 0 #. */
enum class KeypadKey(val label: String) {
    ONE("1"), TWO("2"), THREE("3"), FOUR("4"), FIVE("5"), SIX("6"), SEVEN("7"), EIGHT("8"), NINE("9"), STAR("*"), ZERO("0"), HASH("#"),
}

/** What a keypad key sends the core: a pad button, or the left stick pushed this far (x right, y down, -1..1). */
sealed interface KeypadPress {
    data class Button(val button: NxButton) : KeypadPress
    data class Stick(val x: Float, val y: Float) : KeypadPress
}

/** The biggest key, dp: a thumb's width; a bigger picture leaves more room round the grid instead. */
const val KEYPAD_KEY_MAX_DP = 64f

/** The gap between keys, a share of a key. */
private const val KEYPAD_GAP = 0.18f

/**
 * The keypad's keys over [area] (the picture, window px), [dp] px to the dp: a 3 x 4 grid of square keys, as big as
 * the area allows up to [KEYPAD_KEY_MAX_DP], centred in it.
 */
fun keypadKeys(area: PxRect, dp: Float): List<Pair<KeypadKey, PxRect>> {
    val fit = minOf(area.width / (3f + 2f * KEYPAD_GAP), area.height / (4f + 3f * KEYPAD_GAP))
    val key = minOf(fit, KEYPAD_KEY_MAX_DP * dp)
    val gap = key * KEYPAD_GAP
    val left = area.x + (area.width - (3f * key + 2f * gap)) / 2f
    val top = area.y + (area.height - (4f * key + 3f * gap)) / 2f
    return KeypadKey.entries.mapIndexed { i, k ->
        k to PxRect(left + (i % 3) * (key + gap), top + (i / 3) * (key + gap), key, key)
    }
}

/** The key under [x], [y] in [keys] ([keypadKeys]); null in a gap or off the grid. */
fun keypadKeyAt(keys: List<Pair<KeypadKey, PxRect>>, x: Float, y: Float): KeypadKey? =
    keys.firstOrNull { (_, r) -> x >= r.x && x <= r.x + r.width && y >= r.y && y <= r.y + r.height }?.first

/**
 * Gearcoleco's keypad: 1 to 8, * and # are pad buttons; 9 and 0 are the left stick's Y and X pushed past its
 * threshold (it reads no button for them).
 */
fun colecoKeypad(key: KeypadKey): KeypadPress = when (key) {
    KeypadKey.ONE -> KeypadPress.Button(NxButton.Y)
    KeypadKey.TWO -> KeypadPress.Button(NxButton.X)
    KeypadKey.THREE -> KeypadPress.Button(NxButton.L1)
    KeypadKey.FOUR -> KeypadPress.Button(NxButton.R1)
    KeypadKey.FIVE -> KeypadPress.Button(NxButton.L2)
    KeypadKey.SIX -> KeypadPress.Button(NxButton.R2)
    KeypadKey.SEVEN -> KeypadPress.Button(NxButton.L3)
    KeypadKey.EIGHT -> KeypadPress.Button(NxButton.R3)
    KeypadKey.STAR -> KeypadPress.Button(NxButton.START)
    KeypadKey.HASH -> KeypadPress.Button(NxButton.SELECT)
    KeypadKey.NINE -> KeypadPress.Stick(0f, 1f)
    KeypadKey.ZERO -> KeypadPress.Stick(1f, 0f)
}
