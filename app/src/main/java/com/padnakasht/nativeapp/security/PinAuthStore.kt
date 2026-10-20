package com.padnakasht.nativeapp.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN verifier only: the PIN itself is never persisted. */
class PinAuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_pin_verifier", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = prefs.contains(SALT_KEY) && prefs.contains(HASH_KEY)

    @Synchronized
    fun configure(pin: CharArray, confirmation: CharArray): Boolean {
        if (!validPin(pin) || !MessageDigest.isEqual(charsToBytes(pin), charsToBytes(confirmation))) return false
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        val saved = prefs.edit()
            .putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
            .commit()
        pin.fill('\u0000')
        confirmation.fill('\u0000')
        return saved
    }

    @Synchronized
    fun verify(pin: CharArray): Boolean {
        if (!validPin(pin)) {
            pin.fill('\u0000')
            return false
        }
        val encodedSalt = prefs.getString(SALT_KEY, null)
        val encodedHash = prefs.getString(HASH_KEY, null)
        if (encodedSalt == null || encodedHash == null) {
            pin.fill('\u0000')
            return false
        }
        val expected = Base64.decode(encodedHash, Base64.NO_WRAP)
        val actual = derive(pin, Base64.decode(encodedSalt, Base64.NO_WRAP))
        pin.fill('\u0000')
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun validPin(pin: CharArray): Boolean =
        pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    private fun charsToBytes(chars: CharArray): ByteArray =
        String(chars).toByteArray(Charsets.UTF_8)

    private companion object {
        const val SALT_KEY = "salt"
        const val HASH_KEY = "hash"
        const val SALT_BYTES = 16
        const val ITERATIONS = 210_000
        const val KEY_BITS = 256
        const val MIN_PIN_LENGTH = 6
        const val MAX_PIN_LENGTH = 12
    }
}
