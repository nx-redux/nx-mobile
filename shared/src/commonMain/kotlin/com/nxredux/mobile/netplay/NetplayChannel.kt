package com.nxredux.mobile.netplay

/**
 * One connected control-channel stream (TCP on the device, an in-memory pipe in tests) that the wizard drivers
 * ([com.nxredux.mobile.netplay.wizard.Wizard]) drive. Implemented per platform by the socket layer; lines are UTF-8
 * terminated by "\n" (a trailing "\r" is dropped on read). Every failure is reported as a [NetplayIoException].
 */
interface NetplayChannel {
    /** The peer's IP address as text, e.g. "10.0.0.2"; the host hands it to the engine as the client's address. */
    val remoteAddress: String

    /**
     * The next line without its terminator; throws [NetplayIoException] on timeout or close. Bytes that arrived
     * after the line's "\n" must stay buffered in the channel for the next [readLine].
     */
    suspend fun readLine(timeoutMs: Long): String

    /** Writes [line] followed by "\n". */
    suspend fun writeLine(line: String)

    /**
     * Reads exactly [length] raw bytes into [into] at [offset], within [timeoutMs]: bytes already buffered after the last
     * line come first. Only the Cable Link exchange sends raw bytes (a ROM or a save after a line that announces its size),
     * so a channel that never carries one may leave this unimplemented.
     */
    suspend fun readBytes(into: ByteArray, offset: Int, length: Int, timeoutMs: Long) {
        throw NetplayIoException("raw bytes unsupported")
    }

    /** Writes [length] raw bytes of [bytes] from [offset] (see [readBytes]). */
    suspend fun writeBytes(bytes: ByteArray, offset: Int, length: Int) {
        throw NetplayIoException("raw bytes unsupported")
    }

    /** Closes the stream; idempotent. */
    fun close()
}

/**
 * A control-channel I/O failure. The message is a contract every [NetplayChannel] implementation keeps:
 * [TIMEOUT] ("timeout") when a read ran out of time with the stream still open, [CLOSED] ("closed") when the
 * peer closed the stream (EOF) or this side already closed it. The client's 12 × 5 s wait for the host to start
 * keeps waiting on "timeout" and gives up on anything else. Other messages are allowed for other errors.
 */
class NetplayIoException(message: String) : Exception(message) {
    companion object {
        const val TIMEOUT = "timeout"
        const val CLOSED = "closed"
    }
}
