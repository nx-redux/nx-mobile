package com.nxredux.mobile.kids

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The Kids Mode PIN as a salted PBKDF2 hash, stored as `v1:<salt>:<hash>` (base64); the PIN itself is never stored. */
object KidsPin {
    private const val ITERATIONS = 10_000
    private const val BITS = 256

    fun hash(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val enc = Base64.getEncoder()
        return "v1:${enc.encodeToString(salt)}:${enc.encodeToString(derive(pin, salt))}"
    }

    fun matches(pin: String, stored: String): Boolean = runCatching {
        val p = stored.split(':')
        if (p.size != 3 || p[0] != "v1") return false
        val dec = Base64.getDecoder()
        java.security.MessageDigest.isEqual(derive(pin, dec.decode(p[1])), dec.decode(p[2]))
    }.getOrDefault(false)

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, BITS)).encoded
}
