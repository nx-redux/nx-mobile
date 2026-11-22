package com.nxredux.mobile.storage

/**
 * `%XX` escapes decoded as UTF-8 bytes ("%3A" -> ':', "%C3%A9" -> 'é'); a malformed escape is kept as written. The text
 * between escapes is encoded a run at a time, never a char at a time: a character outside the basic plane is two chars.
 */
internal fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val out = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val hex = if (text[i] == '%' && i + 3 <= text.length) hexByte(text[i + 1], text[i + 2]) else null
        if (hex != null) { out += hex; i += 3; continue }
        var j = i + 1
        while (j < text.length && text[j] != '%') j++
        for (b in text.substring(i, j).encodeToByteArray()) out += b
        i = j
    }
    return out.toByteArray().decodeToString()
}

/** Two hex digits as a byte; null for anything else (a sign is not a digit, though `toInt(16)` takes one). */
private fun hexByte(high: Char, low: Char): Byte? {
    val h = high.digitToIntOrNull(16) ?: return null
    val l = low.digitToIntOrNull(16) ?: return null
    return (h * 16 + l).toByte()
}
