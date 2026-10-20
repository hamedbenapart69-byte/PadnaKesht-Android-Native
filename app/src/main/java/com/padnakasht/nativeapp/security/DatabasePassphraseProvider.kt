package com.padnakasht.nativeapp.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

/**
 * Persists only an AES-GCM-wrapped random database passphrase.
 * The wrapping key is non-exportable and held by Android Keystore.
 * If the key or wrapped value is lost/corrupt, this fails closed rather than
 * silently creating a new key and making an existing encrypted database unreadable.
 */
object DatabasePassphraseProvider {
    private const val KEY_ALIAS = "padnakasht_room_wrap_key_v1"
    private const val PREFS = "encrypted_database_key"
    private const val IV_KEY = "iv"
    private const val VALUE_KEY = "ciphertext"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    @Synchronized
    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encodedIv = prefs.getString(IV_KEY, null)
        val encodedCiphertext = prefs.getString(VALUE_KEY, null)

        if (encodedIv != null && encodedCiphertext != null) {
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext)
        }
        check(encodedIv == null && encodedCiphertext == null) {
            "Encrypted database key metadata is incomplete; refusing to replace it"
        }

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(passphrase)
        val iv = cipher.iv
        check(prefs.edit()
            .putString(IV_KEY, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(VALUE_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()) { "Unable to persist encrypted database key" }
        return passphrase
    }

    private fun getExistingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return store.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Android Keystore wrapping key is missing; refusing to reset database key")
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}
