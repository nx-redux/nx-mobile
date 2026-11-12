package com.nxredux.mobile.achievements

/** The unsigned dorequest.php forms, pinned to rcheevos 12.2.1's rc_api_*.c. Only the award is built by rcheevos (RaSigner). */
object RaRequests {
    const val URL = "https://retroachievements.org/dorequest.php"
    const val CONTENT_TYPE = "application/x-www-form-urlencoded"
    const val MEDIA_BADGE = "https://media.retroachievements.org/Badge/"
    /** RCHEEVOS_VERSION_STRING at the pin; `l=` on startsession. */
    const val RCHEEVOS_VERSION = "12.2.1"

    /**
     * How every request names this client to RetroAchievements, in the form its server reads: the client and its
     * [version], the [os] ("Android 14"), then the rcheevos the requests are built to.
     */
    fun userAgent(version: String, os: String) = "NXRedux/$version ($os) rcheevos/$RCHEEVOS_VERSION"

    fun login2(user: String, token: String) = "r=login2&u=${encode(user)}&t=${encode(token)}"
    fun loginWithPassword(user: String, password: String) = "r=login2&u=${encode(user)}&p=${encode(password)}"
    fun gameId(hash: String) = "r=gameid&m=${encode(hash)}"
    fun achievementSets(user: String, token: String, hash: String) = "r=achievementsets&u=${encode(user)}&t=${encode(token)}&m=${encode(hash)}"
    fun startSession(user: String, token: String, gameId: Int, hash: String) =
        "r=startsession&u=${encode(user)}&t=${encode(token)}&g=$gameId&h=0&m=${encode(hash)}&l=$RCHEEVOS_VERSION"
    /** Unlock ids only (softcore and hardcore: h=0). Unlike startsession it opens no player session, so the game is not marked played. */
    fun unlocks(user: String, token: String, gameId: Int) = "r=unlocks&u=${encode(user)}&t=${encode(token)}&g=$gameId&h=0"
    fun allProgress(user: String, token: String, consoleId: Int) = "r=allprogress&u=${encode(user)}&t=${encode(token)}&c=$consoleId"

    /** RFC 3986 unreserved characters pass; everything else is %XX of its UTF-8 bytes (rc_url_builder does the same). */
    fun encode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c in 0x30..0x39 || c in 0x41..0x5A || c in 0x61..0x7A || c == 0x2D || c == 0x2E || c == 0x5F || c == 0x7E) append(c.toChar())
            else { append('%'); append("0123456789ABCDEF"[c shr 4]); append("0123456789ABCDEF"[c and 15]) }
        }
    }
}

/** The parts of a form body the shim keys on. */
data class RaRequestKind(val r: String, val m: String?, val g: Int?, val u: String?, val a: Int?) {
    companion object {
        fun of(post: String): RaRequestKind {
            val fields = post.split('&').filter { it.isNotEmpty() }.associate { kv -> kv.substringBefore('=') to decode(kv.substringAfter('=', "")) }
            return RaRequestKind(fields["r"] ?: "", fields["m"], fields["g"]?.toIntOrNull(), fields["u"], fields["a"]?.toIntOrNull())
        }
        private fun decode(s: String): String {
            val out = ArrayList<Byte>(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) { out += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3 }
                else if (c == '+') { out += ' '.code.toByte(); i++ }
                else { out += c.code.toByte(); i++ }
            }
            return out.toByteArray().decodeToString()
        }
    }
}
