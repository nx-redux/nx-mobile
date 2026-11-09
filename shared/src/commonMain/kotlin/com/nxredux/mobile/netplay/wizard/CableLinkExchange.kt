package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.NetplayChannel
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.cablelink.LinkRomHashMismatch
import com.nxredux.mobile.netplay.cablelink.RomHash
import kotlinx.coroutines.CancellationException

/**
 * The Cable Link over Wi-Fi part of the handshake, after both HELLOs carried `cl=1`. Both sides run it; the host leads
 * every step that moves bytes, so the two never stream at once:
 *
 * 1. each side: `CABLE <rom sha1> <rom size> <bios sha1|none>`;
 * 2. each side: `NEED <the other's rom sha1>` when it has no file with that hash, else `NEED none`;
 * 3. a needed ROM, host's first: `ROM <size>`, the raw bytes, then the receiver's `ROM-OK` or `ROM-BAD`;
 * 4. saves, host's first: `SAVE <size>` and the raw bytes, or `SAVE none`;
 * 5. host: `CLOCK <epoch seconds>`.
 *
 * Any failure ends the exchange; the caller closes the channel, which also ends the other side's wait.
 */
internal object CableLinkExchange {
    /** A reply may wait on the other side's library lookup or a cache copy, so steps get longer than [Wizard.RECV_MS]. */
    const val STEP_MS = 30_000L
    const val CHUNK_MS = 15_000L
    const val CHUNK = 64 * 1024

    /** The largest GBA cartridge ROM. */
    const val ROM_MAX = 32L * 1024 * 1024

    /** Flash 128 KiB plus an RTC trailer fits with room to spare. */
    const val SAVE_MAX = 1024 * 1024

    private const val NONE = "none"

    sealed interface Result {
        data class Done(val setup: CableLinkSetup) : Result
        /** [beforeSaves]: it failed on the hashes or a ROM, before any save moved. */
        data class Failed(val message: String, val beforeSaves: Boolean) : Result
    }

    private class Fail(val text: String) : Exception(text)

    private data class Peer(val romHash: String, val romSize: Long, val biosHash: String?)

    suspend fun run(ch: NetplayChannel, offer: CableLinkOffer, host: Boolean, onProgress: (CableLinkProgress) -> Unit): Result {
        val step = Step()
        return try {
            Result.Done(exchange(ch, offer, host, onProgress, step))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {   // Fail, NetplayIoException, or the platform's file I/O
            offer.files.log("Cable Link exchange failed (${if (host) "host" else "guest"}, ${if (step.saves) "saves" else "game"}): ${e::class.simpleName}: ${e.message}")
            Result.Failed((e as? Fail)?.text ?: NetplayText.CABLE_LINK_COPY_FAILED, beforeSaves = !step.saves)
        }
    }

    private class Step { var saves = false }

    private suspend fun exchange(
        ch: NetplayChannel, offer: CableLinkOffer, host: Boolean, onProgress: (CableLinkProgress) -> Unit, step: Step,
    ): CableLinkSetup {
        val files = offer.files
        ch.writeLine("CABLE ${offer.romHash} ${offer.romSize} ${offer.biosHash ?: NONE}")
        val peer = parseCable(readLine(ch))

        var otherRom = files.findRom(peer.romHash, peer.romSize)
        ch.writeLine("NEED ${if (otherRom == null) peer.romHash else NONE}")
        val peerNeed = fields(readLine(ch), "NEED", 2)[1]
        val peerNeedsMine = when (peerNeed) {
            NONE -> false
            offer.romHash -> true
            else -> throw Fail(NetplayText.UNEXPECTED)
        }

        suspend fun receive() { otherRom = receiveRom(ch, peer, files, onProgress) }
        if (host) {
            if (peerNeedsMine) sendRom(ch, offer, onProgress)
            if (otherRom == null) receive()
        } else {
            if (otherRom == null) receive()
            if (peerNeedsMine) sendRom(ch, offer, onProgress)
        }

        step.saves = true
        val otherSave: String?
        if (host) {
            sendSave(ch, files.ownSave())
            otherSave = receiveSave(ch, files)
        } else {
            otherSave = receiveSave(ch, files)
            sendSave(ch, files.ownSave())
        }

        val epoch = if (host) {
            offer.clock().also { ch.writeLine("CLOCK $it") }
        } else {
            fields(readLine(ch), "CLOCK", 2)[1].toLongOrNull()?.takeIf { it >= 0 } ?: throw Fail(NetplayText.UNEXPECTED)
        }

        val sameBios = offer.biosHash != null && offer.biosHash == peer.biosHash
        return CableLinkSetup(
            otherRomPath = otherRom ?: throw Fail(NetplayText.CABLE_LINK_COPY_FAILED),
            otherSavePath = otherSave,
            localSlot = if (host) 1 else 2,
            biosMode = if (sameBios) CableLinkSetup.BIOS_REAL else CableLinkSetup.BIOS_HLE,
            rtcEpoch = epoch,
        )
    }

    /** Every hash off the wire is checked before it names a file. */
    private fun parseCable(line: String): Peer {
        val f = fields(line, "CABLE", 4)
        val size = f[2].toLongOrNull()?.takeIf { it in 1..ROM_MAX }
        val bios = f[3].takeIf { it == NONE || RomHash.isHash(it) }
        if (!RomHash.isHash(f[1]) || size == null || bios == null) throw Fail(NetplayText.UNEXPECTED)
        return Peer(f[1], size, bios.takeIf { it != NONE })
    }

    private suspend fun sendRom(ch: NetplayChannel, offer: CableLinkOffer, onProgress: (CableLinkProgress) -> Unit) {
        ch.writeLine("ROM ${offer.romSize}")
        var sent = 0L
        onProgress(CableLinkProgress(sending = true, done = 0, total = offer.romSize))
        offer.files.readOwnRom { buf, n ->
            // A file that grew since it was hashed: the other side counts on exactly the announced size.
            if (sent + n > offer.romSize) throw Fail(NetplayText.CABLE_LINK_COPY_FAILED)
            ch.writeBytes(buf, 0, n)
            sent += n
            onProgress(CableLinkProgress(sending = true, done = sent, total = offer.romSize))
        }
        if (sent != offer.romSize) throw Fail(NetplayText.CABLE_LINK_COPY_FAILED)
        when (readLine(ch)) {
            "ROM-OK" -> Unit
            "ROM-BAD" -> throw Fail(NetplayText.CABLE_LINK_COPY_FAILED)
            else -> throw Fail(NetplayText.UNEXPECTED)
        }
    }

    private suspend fun receiveRom(ch: NetplayChannel, peer: Peer, files: CableLinkFiles, onProgress: (CableLinkProgress) -> Unit): String {
        val size = fields(readLine(ch), "ROM", 2)[1].toLongOrNull()
        if (size != peer.romSize) throw Fail(NetplayText.UNEXPECTED)
        onProgress(CableLinkProgress(sending = false, done = 0, total = size))
        val path = try {
            files.cache.put(peer.romHash) { sink ->
                val buf = ByteArray(CHUNK)
                var got = 0L
                while (got < size) {
                    val n = minOf(CHUNK.toLong(), size - got).toInt()
                    ch.readBytes(buf, 0, n, CHUNK_MS)
                    sink.write(buf, 0, n)
                    got += n
                    onProgress(CableLinkProgress(sending = false, done = got, total = size))
                }
            }
        } catch (e: LinkRomHashMismatch) {
            // Every byte was read, so the line can still go out in step.
            ch.writeLine("ROM-BAD")
            throw Fail(NetplayText.GAME_DAMAGED)
        }
        ch.writeLine("ROM-OK")
        return path
    }

    private suspend fun sendSave(ch: NetplayChannel, save: ByteArray?) {
        if (save == null || save.isEmpty()) {
            ch.writeLine("SAVE $NONE")
            return
        }
        if (save.size > SAVE_MAX) throw Fail(NetplayText.CABLE_LINK_COPY_FAILED)
        ch.writeLine("SAVE ${save.size}")
        ch.writeBytes(save, 0, save.size)
    }

    private suspend fun receiveSave(ch: NetplayChannel, files: CableLinkFiles): String? {
        val token = fields(readLine(ch), "SAVE", 2)[1]
        if (token == NONE) return null
        val size = token.toIntOrNull()?.takeIf { it in 1..SAVE_MAX } ?: throw Fail(NetplayText.UNEXPECTED)
        val bytes = ByteArray(size)
        ch.readBytes(bytes, 0, size, STEP_MS)
        return files.writeOtherSave(bytes)
    }

    /** The next line; a REJECT from the other side becomes its text. */
    private suspend fun readLine(ch: NetplayChannel): String {
        val line = ch.readLine(STEP_MS)
        Reject.parse(line)?.let { throw Fail(NetplayText.reject(it.reason)) }
        return line
    }

    private fun fields(line: String, head: String, count: Int): List<String> {
        val f = line.split(' ').filter { it.isNotEmpty() }
        if (f.size != count || f[0] != head) throw Fail(NetplayText.UNEXPECTED)
        return f
    }
}
