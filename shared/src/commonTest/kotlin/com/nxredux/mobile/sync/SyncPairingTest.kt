package com.nxredux.mobile.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SyncPairingTest {
    private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test fun sha256_matches_the_standard_vectors() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(Sha256.digest("abc".encodeToByteArray())))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(Sha256.digest(ByteArray(0))))
        // 56 bytes: the padding spills into a second block.
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex(Sha256.digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", hex(Sha256.digest(ByteArray(1_000_000) { 'a'.code.toByte() })))
    }

    @Test fun the_spec_vectors_give_the_same_code_and_password_in_either_order() {
        val vectors = listOf(
            listOf("192.168.1.20", "0123456789abcdef", "192.168.1.31", "fedcba9876543210", "2995", "f55d7cdf6e74ca135fdcb442ca1a924e"),
            listOf("10.0.0.2", "00000000000000ff", "10.0.0.10", "a1b2c3d4e5f60718", "3032", "ac7f22bee829d36307216f2e87f80c2c"),
            listOf("192.168.42.129", "1111111111111111", "192.168.42.1", "2222222222222222", "7669", "80b95a44278f8880c299a57f1a96cd4b"),
        )
        for (v in vectors) {
            val (ownIp, own, peerIp, peer) = v
            val want = SyncPairing.Secret(code = v[4], password = v[5])
            assertEquals(want, SyncPairing.derive(ownIp, own, peerIp, peer), "$ownIp/$peerIp")
            assertEquals(want, SyncPairing.derive(peerIp, peer, ownIp, own), "$peerIp/$ownIp")
        }
    }

    @Test fun a_nonce_is_sixteen_lowercase_hex_characters() {
        assertEquals("0123456789abcdef", SyncPairing.nonce(byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xab.toByte(), 0xcd.toByte(), 0xef.toByte())))
        assertEquals("00000000000000ff", SyncPairing.nonce(byteArrayOf(0, 0, 0, 0, 0, 0, 0, -1)))
        assertFailsWith<IllegalArgumentException> { SyncPairing.nonce(ByteArray(7)) }
        assertEquals("0123456789abcdef", SyncPairing.checkedNonce("0123456789abcdef"))
        listOf("", "0123456789ABCDEF", "0123456789abcde", "0123456789abcdef0", "0123456789abcdeg", " 123456789abcdef").forEach {
            assertNull(SyncPairing.checkedNonce(it), it)
        }
    }

    @Test fun the_secrets_file_holds_the_user_and_password() {
        assertEquals("nxsync:f55d7cdf6e74ca135fdcb442ca1a924e\n", SyncPairing.secrets("f55d7cdf6e74ca135fdcb442ca1a924e"))
        assertFailsWith<IllegalArgumentException> { SyncPairing.secrets("f55d\n[evil]") }
    }
}
