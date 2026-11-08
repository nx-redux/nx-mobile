package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayWire
import com.nxredux.mobile.netplay.RejectReason
import com.nxredux.mobile.runner.CoreRegistry

/**
 * The handheld's capability token (nx-redux wiz_caps.c at 8f5a760b): the HELLO field `caps=<token>` after the four fixed
 * fields, `[A-Za-z0-9._=,-]`, 1..63 chars, comma-separated `key=value` elements. This device always sends
 * `core=<core id>,tag=<TAG>`; gpSP and mGBA add the game's link mode `,wl=<token>` (netplay-prelaunch.sh), a joiner on
 * Auto adds `,auto=1`.
 */
object Caps {
    const val MAX = 63

    /** WizCaps_isValid. */
    fun isValid(token: String?): Boolean =
        token != null && token.length in 1..MAX && token.all {
            it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '=' || it == ',' || it == '-'
        }

    /** WizCaps_find: the first token after the 4th field that starts with `caps=`; an invalid one reads as "". */
    fun find(line: String): String {
        val fields = line.substringBefore('\n').substringBefore('\r').split(' ').filter { it.isNotEmpty() }
        for (t in fields.drop(4)) {
            if (t.length > 5 && t.startsWith("caps=")) return t.substring(5).takeIf { isValid(it) } ?: ""
        }
        return ""
    }

    /** WizCaps_value: [key]'s value in [caps]; null when absent or empty. */
    fun value(caps: String?, key: String): String? {
        if (caps.isNullOrEmpty()) return null
        return caps.split(',')
            .firstOrNull { it.length > key.length + 1 && it.startsWith(key) && it[key.length] == '=' }
            ?.substring(key.length + 1)
    }

    /** WizCaps_coreMismatch: only when both tokens name a core and the ids differ (never the tag, never a version). */
    fun coreMismatch(ours: String?, theirs: String?): Boolean {
        val a = value(ours, "core") ?: return false
        val b = value(theirs, "core") ?: return false
        return a != b
    }

    /**
     * What this device sends: `core=<core>,tag=<TAG>` (the handheld's tag, [CoreRegistry.handheldTag]: GPGX_SMS goes as
     * GPGX, which the handheld knows), then for gpSP and mGBA `,wl=<wl>` (the game's link mode, when known), then
     * `,auto=1` when [auto] (a joiner whose Emulator row is Auto and could switch); "" when that would break the grammar.
     * The `auto` element, then `wl`, is dropped when it would not fit. The handheld reads caps by key (wiz_caps.c
     * WizCaps_value), so elements it does not know are safe there.
     */
    fun of(core: String, tag: String, auto: Boolean = false, wl: String? = null): String {
        val base = "core=$core,tag=${CoreRegistry.handheldTag(tag)}"
        val withWl = if (wl != null && NetplayCores.link(core) == NetplayLinkKind.NETPACKET) "$base,wl=$wl" else base
        if (auto) "$withWl,auto=1".takeIf { isValid(it) }?.let { return it }
        withWl.takeIf { isValid(it) }?.let { return it }
        // Only a core or tag with characters outside the grammar gets here; gpSP and mGBA with a GBA tag always fit.
        return base.takeIf { isValid(it) } ?: ""
    }
}

/**
 * One HELLO line (wizard_net.c wiz_parse_hello): `HELLO <version> <game> <role>[ <fifth>][ caps=<token>]`. [game] is
 * unescaped. The fifth field is `any` on a client and the player number on a host (missing or 0 means 2). [caps] is the
 * validated token, "" when absent.
 */
data class Hello(
    val version: Int,
    val game: String,
    val role: String,
    val any: Boolean = false,
    val player: Int = 2,
    val caps: String = "",
) {
    val core: String? get() = Caps.value(caps, "core")
    val tag: String? get() = Caps.value(caps, "tag")

    /** The game's link mode a gpSP or mGBA side sent (`wl=`); null when it sent none. */
    val wl: String? get() = Caps.value(caps, "wl")

    /** A phone joiner on Auto (`auto=1`): it switches to the host's core, so a phone host on Auto keeps its own. */
    val auto: Boolean get() = Caps.value(caps, "auto") == "1"

    companion object {
        const val CLIENT = "client"
        const val HOST = "host"
        const val ANY = "any"
        fun client(game: String, any: Boolean, caps: String) = Hello(NetplayWire.VERSION, game, CLIENT, any = any, caps = caps)
        fun host(game: String, player: Int, caps: String) = Hello(NetplayWire.VERSION, game, HOST, player = player, caps = caps)
    }
}

object HelloCodec {
    private val LEADING_INT = Regex("^[+-]?[0-9]+")

    /** `HELLO 2 <esc game> client[ any][ caps=..]` or `HELLO 2 <esc game> host <n>[ caps=..]`; an invalid caps is left out. */
    fun format(h: Hello): String {
        val caps = if (Caps.isValid(h.caps)) " caps=${h.caps}" else ""
        val game = NetplayWire.escape(h.game)
        return if (h.role == Hello.HOST) "HELLO ${h.version} $game host ${h.player}$caps"
        else "HELLO ${h.version} $game ${h.role}${if (h.any) " ${Hello.ANY}" else ""}$caps"
    }

    /** null unless at least 4 fields, `HELLO` first and a number second (the handheld's `sscanf("%15s %d %255s %15s %15s")`). */
    fun parse(line: String): Hello? {
        val f = line.substringBefore('\n').substringBefore('\r').split(' ').filter { it.isNotEmpty() }
        if (f.size < 4 || f[0] != "HELLO") return null
        val version = LEADING_INT.find(f[1])?.value?.toIntOrNull() ?: return null
        val fifth = f.getOrNull(4).orEmpty()
        val player = fifth.takeWhile { it in '0'..'9' }.take(9).toIntOrNull()?.takeIf { it >= 1 } ?: 2
        return Hello(version, NetplayWire.unescape(f[2]), f[3], any = fifth == Hello.ANY, player = player, caps = Caps.find(line))
    }

    /** wiz_hello_mismatch: version, then game (unless [any]), then role; null when all three match. The core gate is separate. */
    fun mismatch(h: Hello, ownGame: String, expectRole: String, any: Boolean): RejectReason? = when {
        h.version != NetplayWire.VERSION -> RejectReason.VERSION
        !any && !NetplayWire.equivalent(h.game, ownGame) -> RejectReason.GAME
        h.role != expectRole -> RejectReason.ROLE
        else -> null
    }
}

/** `REJECT <reason>`; a core refusal names the refusing side's core and folder (`core-<core>.<tag>`, WizCaps_coreReason). */
data class Reject(val reason: RejectReason, val core: String? = null, val tag: String? = null) {
    fun format(): String = when (reason) {
        RejectReason.CORE -> "REJECT ${coreReason(core, tag)}"
        RejectReason.OTHER -> "REJECT"
        else -> "REJECT ${reason.wire}"
    }

    companion object {
        /** Both sides read the reason with `%31s`. */
        const val REASON_MAX = 31

        /** `core-<core>.<tag>`, else `core-<core>` when over 31 chars, else `core`. */
        fun coreReason(core: String?, tag: String?): String {
            val c = core.orEmpty()
            val t = tag?.let(CoreRegistry::handheldTag).orEmpty()
            return when {
                c.isNotEmpty() && t.isNotEmpty() && 5 + c.length + 1 + t.length <= REASON_MAX -> "core-$c.$t"
                c.isNotEmpty() && 5 + c.length <= REASON_MAX -> "core-$c"
                else -> "core"
            }
        }

        /** null for anything that is not a REJECT line; an unknown reason is [RejectReason.OTHER]. */
        fun parse(line: String): Reject? {
            val f = line.substringBefore('\n').substringBefore('\r').split(' ').filter { it.isNotEmpty() }
            if (f.isEmpty() || f[0] != "REJECT") return null
            val token = f.getOrNull(1)?.take(REASON_MAX) ?: return Reject(RejectReason.OTHER)
            if (token == RejectReason.CORE.wire) return Reject(RejectReason.CORE)
            if (token.startsWith("core-") && token.length > 5) {
                val rest = token.substring(5)
                val dot = rest.indexOf('.')   // core names carry no '.', so the first one starts the folder tag
                val core = (if (dot < 0) rest else rest.substring(0, dot)).ifEmpty { null } ?: return Reject(RejectReason.OTHER)
                return Reject(RejectReason.CORE, core, if (dot < 0) null else rest.substring(dot + 1).ifEmpty { null })
            }
            return Reject(RejectReason.entries.firstOrNull { it.wire.isNotEmpty() && it.wire == token } ?: RejectReason.OTHER)
        }
    }
}
