package com.nxredux.mobile.runner

/** A key typed into a core through its libretro keyboard callback: the RETROK_ [keycode] and the [character] it types (0: none). */
data class CoreKey(val keycode: Int, val character: Int)

/**
 * A core's open text entry (the Wii U software keyboard), answered from the app's own text box: what goes in, and the
 * keys that put it there.
 */
object CoreTextInput {
    const val RETROK_BACKSPACE = 8
    const val RETROK_RETURN = 13

    /**
     * The text for the box's OK: [typed] on one line, without control characters or characters beyond 16 bits (the
     * Wii U's keyboard holds UTF-16 units one at a time), at most [maxLength] long (0: no limit given). Nothing typed
     * answers with the account's [name], as the core's own Start does.
     */
    fun answer(typed: String, name: String, maxLength: Int): String {
        val clean = { s: String -> s.filter { it.code >= 32 && it.code != 127 && !it.isSurrogate() }.trim() }
        val text = clean(typed).ifEmpty { clean(name) }
        return if (maxLength > 0) text.take(maxLength) else text
    }

    /**
     * The keys that turn the entry's [initial] text into [answer] and accept it: a backspace each, the text, Enter. A
     * character goes with no key code (RETROK_UNKNOWN): its number could be a key's (U+010F is keypad Enter's).
     */
    fun keys(initial: String, answer: String): List<CoreKey> =
        List(initial.length) { CoreKey(RETROK_BACKSPACE, 0) } + answer.map { CoreKey(0, it.code) } + CoreKey(RETROK_RETURN, 0)
}
