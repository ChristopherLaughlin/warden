package com.warden.blocker.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 hashing so a stored PIN can't be trivially recovered from device storage. */
object PinHasher {
    // OWASP-recommended baseline for PBKDF2-HMAC-SHA256 (API 26+). Slows brute force of the
    // small PIN space; paired with rate-limiting/lockout in SettingsStore.
    private const val ITERATIONS = 310_000
    private const val KEY_LENGTH = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, saltB64: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            return Base64.encodeToString(hash, Base64.NO_WRAP)
        } finally {
            spec.clearPassword() // wipe the PIN chars from the spec asap
        }
    }

    fun verify(pin: String, saltB64: String, expectedHashB64: String): Boolean =
        constantTimeEquals(hash(pin, saltB64), expectedHashB64)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
