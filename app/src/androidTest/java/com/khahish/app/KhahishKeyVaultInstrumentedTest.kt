package com.khahish.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.AEADBadTagException

@RunWith(AndroidJUnit4::class)
class KhahishKeyVaultInstrumentedTest {
    private lateinit var vault: KhahishKeyVault
    private val alias = "instrumentation-test-key"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        vault = KhahishKeyVault(context)
        vault.deleteKey(alias)
    }

    @After
    fun tearDown() {
        vault.deleteKey(alias)
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val plaintext = "Khahish private message".toByteArray()
        val aad = "chat:conversation-123".toByteArray()

        val encrypted = vault.encrypt(alias, plaintext, aad)

        assertTrue(encrypted.iv.size == 12)
        assertTrue(encrypted.ciphertext.size > plaintext.size)
        assertArrayEquals(plaintext, vault.decrypt(alias, encrypted, aad))
    }

    @Test
    fun keyPersistsAcrossVaultInstances() {
        val plaintext = "persist across app object recreation".toByteArray()
        val aad = "key-vault-test".toByteArray()
        val encrypted = vault.encrypt(alias, plaintext, aad)

        val secondVault = KhahishKeyVault(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        assertArrayEquals(plaintext, secondVault.decrypt(alias, encrypted, aad))
    }

    @Test
    fun wrongAssociatedDataIsRejected() {
        val encrypted = vault.encrypt(
            alias,
            "secret".toByteArray(),
            "correct-context".toByteArray()
        )

        try {
            vault.decrypt(alias, encrypted, "wrong-context".toByteArray())
            throw AssertionError("Decryption unexpectedly accepted incorrect AAD")
        } catch (_: AEADBadTagException) {
            // Expected: GCM authentication must reject altered context.
        }
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val encrypted = vault.encrypt(
            alias,
            "secret payload".toByteArray(),
            "message-context".toByteArray()
        )
        val tampered = encrypted.copy(ciphertext = encrypted.ciphertext.clone().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 0x01).toByte()
        })

        try {
            vault.decrypt(alias, tampered, "message-context".toByteArray())
            throw AssertionError("Decryption unexpectedly accepted modified ciphertext")
        } catch (_: AEADBadTagException) {
            // Expected: GCM authentication detects ciphertext tampering.
        }
    }

    @Test
    fun deletingKeyMakesOldCiphertextUndecryptable() {
        val encrypted = vault.encrypt(alias, "secret".toByteArray(), byteArrayOf(1))
        vault.deleteKey(alias)

        assertFalse(vault.hasKey(alias))
        try {
            vault.decrypt(alias, encrypted, byteArrayOf(1))
            throw AssertionError("Decryption unexpectedly succeeded after key deletion")
        } catch (_: IllegalStateException) {
            // Expected: local key was deliberately deleted.
        }
    }
}
