package com.khahish.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-local encrypted storage for future E2EE protocol state.
 *
 * This vault is NOT a messaging protocol and does NOT encrypt Firestore messages.
 * It only protects bytes explicitly passed to it using a non-exportable Android
 * Keystore AES-256-GCM key. Never pass plaintext message logs or export this key.
 */
class KhahishKeyVault(context: Context) {
    private val appContext = context.applicationContext

    data class EncryptedBlob(
        val version: Int,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    fun encrypt(alias: String, plaintext: ByteArray, associatedData: ByteArray): EncryptedBlob {
        require(plaintext.isNotEmpty()) { "Nothing to encrypt" }
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedBlob(FORMAT_VERSION, cipher.iv, ciphertext)
    }

    fun decrypt(alias: String, blob: EncryptedBlob, associatedData: ByteArray): ByteArray {
        require(blob.version == FORMAT_VERSION) { "Unsupported encrypted data version" }
        require(blob.iv.size == GCM_IV_LENGTH_BYTES) { "Invalid encrypted data IV" }
        require(blob.ciphertext.size >= GCM_TAG_LENGTH_BITS / 8) { "Invalid encrypted data payload" }

        val key = getExistingKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, blob.iv)
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(blob.ciphertext)
    }

    fun hasKey(alias: String): Boolean {
        validateAlias(alias)
        val keyStore = loadKeyStore()
        return keyStore.containsAlias(fullAlias(alias))
    }

    fun deleteKey(alias: String) {
        validateAlias(alias)
        loadKeyStore().deleteEntry(fullAlias(alias))
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        validateAlias(alias)
        val keyStore = loadKeyStore()
        val name = fullAlias(alias)
        val existing = keyStore.getKey(name, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                name,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun getExistingKey(alias: String): SecretKey {
        validateAlias(alias)
        val key = loadKeyStore().getKey(fullAlias(alias), null)
        return key as? SecretKey ?: throw IllegalStateException("Local encryption key is unavailable")
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun fullAlias(alias: String): String = KEY_ALIAS_PREFIX + alias

    private fun validateAlias(alias: String) {
        require(alias.matches(Regex("[A-Za-z0-9._-]{1,64}"))) {
            "Invalid local encryption key alias"
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "com.khahish.app.e2ee."
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_IV_LENGTH_BYTES = 12
        const val FORMAT_VERSION = 1
    }
}
