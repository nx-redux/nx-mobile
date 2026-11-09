package com.swordfish.libretrodroid

/**
 * The lockstep netplay engine and the GBA link (`cpp/netplay/`). While a lockstep session is active the runner runs one
 * frame per step on the session's P1/P2 joypad words; in a link session input is left alone (frames run at speed 1 and
 * hold while either side pauses); otherwise nothing changes.
 */
object LibretroNetplay {
    const val ROLE_HOST = 0
    const val ROLE_CLIENT = 1

    const val STATE_IDLE = 0
    const val STATE_CONNECTING = 1
    const val STATE_SYNCING = 2
    const val STATE_RUNNING = 3
    const val STATE_STALLED = 4
    const val STATE_PEER_PAUSED = 5
    const val STATE_ENDED = 6
    const val STATE_FAILED = 7

    const val REASON_NONE = 0
    const val REASON_PEER_LEFT = 1
    const val REASON_TIMEOUT = 2
    const val REASON_STATE_MISMATCH = 3
    const val REASON_CONNECT = 4
    const val REASON_IO = 5

    /** The GBA link (`cpp/netplay/np_link.h`): gpSP and mGBA packets over TCP 55437; no input or state exchange. */
    const val LINK_IDLE = 0
    const val LINK_CONNECTING = 1
    const val LINK_LINKED = 2
    const val LINK_PEER_PAUSED = 3
    const val LINK_ENDED = 4
    const val LINK_FAILED = 5

    /**
     * [peerIp] is the other device's dotted IPv4 for both roles. Host: listen on [port] and accept only [peerIp]. Client:
     * connect to [peerIp]:[port]. False when already started (until [shutdown]) or on a missing or bad address.
     */
    @JvmStatic external fun start(role: Int, peerIp: String, port: Int): Boolean
    @JvmStatic external fun pause()
    @JvmStatic external fun resume()
    @JvmStatic external fun leave()
    /**
     * Ends the session and resets to idle. May block ~0.75 s: avoid the main thread (the native destroy also calls it on
     * the GL thread at teardown).
     */
    @JvmStatic external fun shutdown()
    /** [state, secondsLeft, reason] */
    @JvmStatic external fun status(): IntArray

    /**
     * Starts the GBA link before the core loads. [peerIp]: the other device (the host accepts only it). [mode]: the host's
     * READY payload (gpSP's raw gpsp_serial, "" for mGBA); a client passes "". False when already started (until
     * [linkShutdown]) or on a bad address.
     */
    @JvmStatic external fun linkStart(role: Int, peerIp: String, port: Int, mode: String): Boolean
    /** Client: the host's mode from its READY ("" when it sent none); null until then, and always for a host. */
    @JvmStatic external fun linkHostMode(): String?
    /** The link mode token (gwl_mode_token) of the GBA ROM at [path], which the mGBA core's link options take. */
    @JvmStatic external fun gbaLinkMode(path: String): String
    @JvmStatic external fun linkPause()
    @JvmStatic external fun linkResume()
    @JvmStatic external fun linkLeave()
    /**
     * Never blocks (sets a flag); any thread. This device must not stay held by the peer's real-cable HOLD: the core's
     * next mid-frame poll gets a HOLD release and leaves retro_run within about 0.5 s, so pausing or destroying the view
     * does not wait on the GL thread for up to 30 s. Nothing without real-cable traffic or a session.
     */
    @JvmStatic external fun linkUnblock()
    /** Ends the link and resets it to idle. May block ~0.5 s: never on the main thread. */
    @JvmStatic external fun linkShutdown()
    /** [state, secondsLeft, reason]: LINK_* and REASON_*. */
    @JvmStatic external fun linkStatus(): IntArray

    init {
        System.loadLibrary("libretrodroid")
    }
}
