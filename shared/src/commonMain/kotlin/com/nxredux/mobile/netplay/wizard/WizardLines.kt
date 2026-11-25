package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.GbaWifiLink
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayWire
import com.nxredux.mobile.netplay.RejectReason
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.PlayerName
import com.nxredux.mobile.runner.SegaPadType

/**
 * The handheld's capability token (nx-redux wiz_caps.c): the HELLO field `caps=<token>` after the four fixed
 * fields, `[A-Za-z0-9._=,-]`, 1..63 chars, comma-separated `key=value` elements. This device always sends
 * `core=<core id>,tag=<TAG>`; gpSP and mGBA add the game's link mode `,wl=<token>` (netplay-prelaunch.sh), a joiner on
 * Auto adds `,auto=1`, and a side that trades names (the NAME line after START, [NameLine]) `,nm=1`. A Cable Link
 * over Wi-Fi offer sends `core=mgba,tag=<TAG>,cl=1` instead (no `wl` or `auto`: that session always runs mGBA's own
 * cable inside each phone). Its ROM and BIOS hashes would not fit in [MAX] chars, so
 * they follow the HELLO in a `CABLE` line ([CableLinkExchange]). A Mega Drive / Sega CD / 32X game adds `,pad=<auto|3|6>`,
 * the side's Controller Type: a joiner's says it follows the host's, the host's (sent only to such a joiner) is the session's.
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
     * `,auto=1` when [auto] (a joiner whose Emulator row is Auto and could switch), then `,pad=<pad>` when [pad] (the
     * Sega Controller Type), then `,nm=1` when [names]; "" when that would break the grammar. A Dreamcast game adds
     * `,dcbios=<fingerprint>` ([dcBios]) after the tag. The `nm` element, then `pad`, then `auto`, then `wl`, is dropped
     * when it would not fit (a side that sent no `pad` plays the old fixed pad, as its peer then does). The handheld reads
     * caps by key (wiz_caps.c WizCaps_value), so elements it does not know are safe there.
     */
    fun of(
        core: String, tag: String, auto: Boolean = false, wl: String? = null, cableLink: Boolean = false,
        names: Boolean = false, dcBios: String? = null, pad: String? = null,
    ): String {
        val token = gates(core, tag, auto, wl, cableLink, dcBios, pad)
        // Names are a nicety: the first thing left out when the token would not fit.
        if (names && token.isNotEmpty()) "$token,nm=1".takeIf { isValid(it) }?.let { return it }
        return token
    }

    private fun gates(core: String, tag: String, auto: Boolean, wl: String?, cableLink: Boolean, dcBios: String?, pad: String?): String {
        val named = "core=$core,tag=${CoreRegistry.handheldTag(tag)}"
        if (cableLink) return "$named,cl=1".takeIf { isValid(it) } ?: ""
        // A Dreamcast game's BIOS fingerprint (the handheld's `--caps dcbios=`): both sides boot the real BIOS only when theirs match.
        val base = dcBios?.let { "$named,dcbios=$it" }?.takeIf { isValid(it) } ?: named
        val withWl = if (wl != null && NetplayCores.link(core) == NetplayLinkKind.NETPACKET) "$base,wl=$wl" else base
        val withAuto = if (auto) "$withWl,auto=1" else withWl
        if (pad != null) "$withAuto,pad=$pad".takeIf { isValid(it) }?.let { return it }
        if (auto) withAuto.takeIf { isValid(it) }?.let { return it }
        withWl.takeIf { isValid(it) }?.let { return it }
        // Only a core or tag with characters outside the grammar gets here; gpSP and mGBA with a GBA tag always fit.
        return base.takeIf { isValid(it) } ?: named.takeIf { isValid(it) } ?: ""
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

    /** A phone that picked Cable Link over Wi-Fi (`cl=1`); older builds and the handheld never send it. */
    val cableLink: Boolean get() = Caps.value(caps, "cl") == "1"

    /** A phone that trades names after START (`nm=1`, [NameLine]); older builds and the handheld never send it. */
    val names: Boolean get() = Caps.value(caps, "nm") == "1"
    /** The side's Sega Controller Type (`pad=`, [SegaPadType]); null when it sent none or an unknown value. */
    val pad: String? get() = Caps.value(caps, "pad")?.takeIf { it in SegaPadType.SETTINGS }
    /** A Dreamcast game's BIOS fingerprint (`dcbios=`): 12 hex digits of its dc_boot.bin's MD5, or `none`; null when not sent. */
    val dcBios: String? get() = Caps.value(caps, "dcbios")

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

/**
 * `REJECT <reason>`; a core refusal names the refusing side's core and folder (`core-<core>.<tag>`, WizCaps_coreReason).
 * A link-mode refusal ([RejectReason.LINK], WizCaps_linkReason) names the refusing side's core, its game's mode [wl] and
 * the other side's [peerWl]: `link-<core>.<wl>.<peerWl>`.
 */
data class Reject(
    val reason: RejectReason, val core: String? = null, val tag: String? = null, val wl: String? = null, val peerWl: String? = null,
) {
    fun format(): String = when (reason) {
        RejectReason.CORE -> "REJECT ${coreReason(core, tag)}"
        RejectReason.LINK -> "REJECT link-$core.$wl.$peerWl"
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

        /**
         * WizCaps_linkReason: gpSP and mGBA on the two sides ([senderCore] refusing, [receiverCore] refused), both games
         * in a Wi-Fi mode, yet the modes differ or the transport turns them into the real cable ([GbaWifiLink.mixedOk]).
         * Only builds that send `wl=` get here, so the refused side knows the reason. null when the refusal is about
         * something else (a side in no Wi-Fi mode runs the real cable, which the plain core refusal covers).
         */
        fun link(senderCore: String, senderWl: String?, receiverCore: String?, receiverWl: String?, usb: Boolean): Reject? {
            if (receiverCore == null || receiverCore == senderCore || '.' in senderCore) return null
            val a = senderWl?.takeIf { it in GbaWifiLink.WIFI_MODES } ?: return null
            val b = receiverWl?.takeIf { it in GbaWifiLink.WIFI_MODES } ?: return null
            if (GbaWifiLink.mixedOk(a, b, usb) || "link-$senderCore.$a.$b".length > REASON_MAX) return null
            return Reject(RejectReason.LINK, senderCore, wl = a, peerWl = b)
        }

        /** null for anything that is not a REJECT line; an unknown reason is [RejectReason.OTHER]. */
        fun parse(line: String): Reject? {
            val f = line.substringBefore('\n').substringBefore('\r').split(' ').filter { it.isNotEmpty() }
            if (f.isEmpty() || f[0] != "REJECT") return null
            val token = f.getOrNull(1)?.take(REASON_MAX) ?: return Reject(RejectReason.OTHER)
            if (token == RejectReason.CORE.wire) return Reject(RejectReason.CORE)
            if (token.startsWith("link-")) {
                val parts = token.substring(5).split('.')
                if (parts.size != 3 || parts[0].isEmpty() || parts[1] !in GbaWifiLink.WIFI_MODES || parts[2] !in GbaWifiLink.WIFI_MODES)
                    return Reject(RejectReason.OTHER)
                return Reject(RejectReason.LINK, parts[0], wl = parts[1], peerWl = parts[2])
            }
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

/**
 * `NAME <escaped name>`: each side's player name, traded after START only when both HELLOs carried `nm=1`, so older
 * builds and the handheld never see it. The name read is cleaned as a Player name ([PlayerName.clean]); a missing or
 * malformed line is no name, never a failed session.
 */
object NameLine {
    fun format(name: String): String = "NAME ${NetplayWire.escape(PlayerName.clean(name))}"

    fun parse(line: String?): String? {
        val f = line?.substringBefore('\n')?.substringBefore('\r')?.split(' ')?.filter { it.isNotEmpty() } ?: return null
        if (f.size != 2 || f[0] != "NAME") return null
        return PlayerName.clean(NetplayWire.unescape(f[1])).ifEmpty { null }
    }
}
