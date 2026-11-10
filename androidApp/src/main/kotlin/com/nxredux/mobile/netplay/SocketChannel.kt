package com.nxredux.mobile.netplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * A [NetplayChannel] over one connected TCP socket. Reads go through one 512-byte buffer, so the next line's bytes
 * that arrive in the same segment as a line are kept. All I/O runs on [Dispatchers.IO]; a blocking read is bounded by
 * its timeout (SO_TIMEOUT), and [close] from another thread ends it at once with "closed".
 */
class SocketChannel(private val socket: Socket) : NetplayChannel {
    /** Dotted IPv4 (an IPv4-mapped peer normalised, [NetplayAddress]); "" for anything else, which fails the session. */
    override val remoteAddress: String = NetplayAddress.ipv4(socket.inetAddress?.address).orEmpty()

    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private val buf = ByteArray(LINE_MAX)
    private var start = 0
    private var end = 0
    private val readLock = Mutex()
    private val writeLock = Mutex()

    init {
        runCatching {
            socket.tcpNoDelay = true
            socket.sendBufferSize = SOCKET_BUFFER
            socket.receiveBufferSize = SOCKET_BUFFER
        }
    }

    override suspend fun readLine(timeoutMs: Long): String = readLock.withLock {
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                for (i in start until end) {
                    if (buf[i] == NL) {
                        var lineEnd = i
                        if (lineEnd > start && buf[lineEnd - 1] == CR) lineEnd--
                        val line = buf.decodeToString(start, lineEnd)
                        start = i + 1
                        if (start == end) { start = 0; end = 0 }
                        return@withContext line
                    }
                }
                if (end - start >= LINE_MAX) throw NetplayIoException("line too long")
                if (start > 0) { buf.copyInto(buf, 0, start, end); end -= start; start = 0 }
                end += fill(buf, end, LINE_MAX - end, deadline)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    /** One blocking read of up to [len] bytes into [into] at [at], within [deadline]; at least one byte or a throw. */
    private fun fill(into: ByteArray, at: Int, len: Int, deadline: Long): Int {
        val left = deadline - System.currentTimeMillis()
        if (left <= 0) throw NetplayIoException(NetplayIoException.TIMEOUT)
        val n = try {
            socket.soTimeout = left.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            input.read(into, at, len)
        } catch (_: SocketTimeoutException) {
            throw NetplayIoException(NetplayIoException.TIMEOUT)
        } catch (_: IOException) {
            throw NetplayIoException(NetplayIoException.CLOSED)
        }
        if (n < 0) throw NetplayIoException(NetplayIoException.CLOSED)
        return n
    }

    override suspend fun readBytes(into: ByteArray, offset: Int, length: Int, timeoutMs: Long) = readLock.withLock {
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffered = minOf(length, end - start)
            buf.copyInto(into, offset, start, start + buffered)
            start += buffered
            if (start == end) { start = 0; end = 0 }
            var got = buffered
            while (got < length) got += fill(into, offset + got, length - got, deadline)
        }
    }

    override suspend fun writeLine(line: String) = write("$line\n".encodeToByteArray())

    override suspend fun writeBytes(bytes: ByteArray, offset: Int, length: Int) = write(bytes, offset, length)

    private suspend fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) = writeLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                output.write(bytes, offset, length)
                output.flush()
            } catch (_: IOException) {
                throw NetplayIoException(NetplayIoException.CLOSED)
            }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private companion object {
        const val LINE_MAX = 512
        const val SOCKET_BUFFER = 64 * 1024
        const val NL = '\n'.code.toByte()
        const val CR = '\r'.code.toByte()
    }
}
