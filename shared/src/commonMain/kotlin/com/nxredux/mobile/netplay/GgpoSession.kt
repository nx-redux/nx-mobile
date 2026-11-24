package com.nxredux.mobile.netplay

/**
 * A Dreamcast session in flycast's GGPO rollback, as the handheld runs it (nx-redux core_netplay.h, DC.pak): the core reads
 * the session from its process environment at load, waits for the other device inside the load, then plays it. The
 * frontend only keeps the rules of a netplay game (no states, fast-forward, rewind, reset or cheats) and the goodbye.
 */
object GgpoSession {
    /** How long a player may stay paused (menu open, app in the background) before leaving; the other game waits meanwhile. */
    const val LEAVE_GRACE_MS = 20_000L

    /** Peer silence after which the core ends the session; longer than [LEAVE_GRACE_MS], so the pause ends first. */
    const val DISCONNECT_MS = 25_000

    /** How long the core's load waits for the other device. */
    const val CONNECT_TIMEOUT_S = 60

    /** A player who leaves says so at once (UDP, sent a few times), so the other game ends now, not at the timeout. */
    const val BYE_PORT = 55442
    const val BYE_MESSAGE = "NXREDUX-NETPLAY-BYE"
    const val BYE_REPEATS = 3

    private val KEYS = listOf("NX_GGPO", "NX_GGPO_HOST", "NX_GGPO_SERVER", "NX_GGPO_HLE", "NX_GGPO_DISCONNECT_MS", "NX_GGPO_TIMEOUT")

    /**
     * The process environment for a launch of [core]: the session's for a Dreamcast session, every key unset for any other
     * flycast launch (a game process outlives no launch, but nothing may start a session it was not given); empty for the
     * other cores.
     */
    fun env(core: String?, session: NetplaySession?): Map<String, String?> = when {
        core == null || !NetplayCores.ggpo(core) -> emptyMap()
        session == null || !session.ggpo -> KEYS.associateWith { null }
        else -> mapOf(
            "NX_GGPO" to "1",
            "NX_GGPO_HOST" to if (session.role == NetplayRole.HOST) "1" else "0",
            "NX_GGPO_SERVER" to session.peerIp,
            "NX_GGPO_HLE" to if (session.dcHle) "1" else "0",
            "NX_GGPO_DISCONNECT_MS" to DISCONNECT_MS.toString(),
            "NX_GGPO_TIMEOUT" to CONNECT_TIMEOUT_S.toString(),
        )
    }
}
