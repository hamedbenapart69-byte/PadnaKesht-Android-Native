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
        if (!validPin(pin) || !pin.contentEquals(confirmation)) {
            pin.fill('\u0000')
            confirmation.fill('\u0000')
            return false
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        val saved = prefs.edit()
            .putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
            .remove(FAILURE_COUNT_KEY)
            .remove(LOCKED_UNTIL_KEY)
            .commit()
        pin.fill('\u0000')
        confirmation.fill('\u0000')
        return saved
    }

    @Synchronized
    fun verify(pin: CharArray): Boolean {
        val now = System.currentTimeMillis()
        if (prefs.getLong(LOCKED_UNTIL_KEY, 0L) > now) {
            pin.fill('\u0000')
            return false
        }
        if (!validPin(pin)) {
            pin.fill('\u0000')
            recordFailure(now)
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
        val matched = MessageDigest.isEqual(expected, actual)
        if (matched) {
            prefs.edit().remove(FAILURE_COUNT_KEY).remove(LOCKED_UNTIL_KEY).commit()
        } else {
            recordFailure(now)
        }
        return matched
    }

    private fun recordFailure(now: Long) {
        val failures = prefs.getInt(FAILURE_COUNT_KEY, 0) + 1
        val lockoutMillis = if (failures < 5) 0L else
            (30_000L * (1L shl (failures - 5).coerceAtMost(5))).coerceAtMost(15 * 60_000L)
        prefs.edit()
            .putInt(FAILURE_COUNT_KEY, failures)
            .putLong(LOCKED_UNTIL_KEY, if (lockoutMillis == 0L) 0L else now + lockoutMillis)
            .commit()
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

    private companion object {
        const val SALT_KEY = "salt"
        const val HASH_KEY = "hash"
        const val FAILURE_COUNT_KEY = "failure_count"
        const val LOCKED_UNTIL_KEY = "locked_until"
        const val SALT_BYTES = 16
        const val ITERATIONS = 210_000
        const val KEY_BITS = 256
        const val MIN_PIN_LENGTH = 6
        const val MAX_PIN_LENGTH = 12
    }
}
