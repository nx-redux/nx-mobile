package com.nxredux.mobile.netplay

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** Two connected in-memory channels for handshake tests: bytes written on one end are read on the other. */
class PipeChannel private constructor(
    private val inbox: Channel<Byte>, private val outbox: Channel<Byte>, override val remoteAddress: String,
) : NetplayChannel {
    var closed = false; private set
    override suspend fun readLine(timeoutMs: Long): String {
        val sb = StringBuilder()
        while (true) {
            val b = withTimeoutOrNull(timeoutMs) { inbox.receive() } ?: throw NetplayIoException("timeout")
            if (b == '\n'.code.toByte()) return sb.toString().removeSuffix("\r")
            sb.append(b.toInt().toChar())   // fixtures are ASCII + 0x1F
        }
    }
    override suspend fun writeLine(line: String) {
        if (closed) throw NetplayIoException("closed")
        (line + "\n").encodeToByteArray().forEach { outbox.send(it) }
    }
    override suspend fun readBytes(into: ByteArray, offset: Int, length: Int, timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { for (i in 0 until length) into[offset + i] = inbox.receive() } ?: throw NetplayIoException("timeout")
    }
    override suspend fun writeBytes(bytes: ByteArray, offset: Int, length: Int) {
        if (closed) throw NetplayIoException("closed")
        for (i in offset until offset + length) outbox.send(bytes[i])
    }
    override fun close() { closed = true }
    companion object {
        /** [hostSees]: the host end's remoteAddress (the client); [clientSees]: the client end's (the host). */
        fun pair(hostSees: String = "10.0.0.2", clientSees: String = "10.0.0.1"): Pair<PipeChannel, PipeChannel> {
            val a = Channel<Byte>(Channel.UNLIMITED); val b = Channel<Byte>(Channel.UNLIMITED)
            return PipeChannel(a, b, hostSees) to PipeChannel(b, a, clientSees)
        }
    }
}
