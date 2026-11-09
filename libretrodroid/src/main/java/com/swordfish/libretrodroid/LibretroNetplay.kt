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
    /** Pair mode: the drift check failed and the host's state is replacing the client's ("Syncing…"). */
    const val STATE_RESYNCING = 8

    const val REASON_NONE = 0
    const val REASON_PEER_LEFT = 1
    const val REASON_TIMEOUT = 2
    const val REASON_STATE_MISMATCH = 3
    const val REASON_CONNECT = 4
    const val REASON_IO = 5
    /** Pair mode: the third drift-check mismatch within 60 s. */
    const val REASON_DRIFT = 6
    /** Pair mode: a phone missed the frame budget for more than 10 s while its peer waited (either side's). */
    const val REASON_TOO_SLOW = 7

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
    /** Frames each side runs on its own before the state sync (a core that takes no state until it ran); call before [start]. */
    @JvmStatic external fun setPresyncFrames(frames: Int)
    /**
     * Pair mode (Cable Link over Wi-Fi: both sides must set it) for the next [start], which consumes it: drift checks
     * with resync, adaptive input delay and the too-slow end. Call after the core has loaded, before [start] ([shutdown]
     * in between keeps it). False when [on] and the loaded core does not export `nx_pair_hash` (pair mode stays off).
     */
    @JvmStatic external fun setPairMode(on: Boolean): Boolean
    /** 0 = adaptive; 1..8 = pinned input delay in frames (Developer setting). Any time; kept across sessions. */
    @JvmStatic external fun setDelayPin(frames: Int)
    /** This device's input delay in frames (2 outside pair mode). */
    @JvmStatic external fun currentDelay(): Int
    /** Frames between pair-mode drift checks. */
    const val PAIR_CHECK_INTERVAL = 120
    /**
     * Frames the session has run (-1 when not started). Read on the GL thread right after a lockstep frame: a non-zero
     * multiple of [PAIR_CHECK_INTERVAL] is a drift-check frame, so snapshot the local save there keyed by it; once
     * [lastVerifiedFrame] equals that frame the snapshot is verified. A snapshot it passes over is never verified.
     */
    @JvmStatic external fun runFrame(): Long
    /**
     * The last frame both phones' drift hashes matched (-1 none): the save checkpoint. Poll it; it stays readable after
     * the session ended until [shutdown].
     */
    @JvmStatic external fun lastVerifiedFrame(): Long
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
