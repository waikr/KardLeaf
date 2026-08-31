package com.kangle.kardleaf.data.repository.note

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivacyVaultCryptoTest {
    @Test
    fun usesStandardPbkdf2Sha256Vector() {
        val derived = PrivacyVaultCrypto.derivePasswordKey("password", "salt".toByteArray(), 1)

        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            derived.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun encryptsRoundTripsAndRejectsWrongPasswordOrTampering() {
        val password = "correct horse battery staple"
        val master = PrivacyVaultCrypto.newMasterKey()
        val meta = PrivacyVaultCrypto.createMeta(password, "0123456789abcdef0123456789abcdef", master, 100_000)
        val unlocked = PrivacyVaultCrypto.unlockMeta(password, meta)
        val payload = PrivacyVaultCrypto.NotePayload(42L, "Private title", "Private body", 1234L)
        val encrypted = PrivacyVaultCrypto.encryptNote(unlocked.masterKey, payload)

        assertArrayEquals(master, unlocked.masterKey)
        assertFalse(String(encrypted).contains(payload.title))
        assertFalse(String(encrypted).contains(payload.content))
        assertEquals(payload, PrivacyVaultCrypto.decryptNote(unlocked.masterKey, encrypted))
        assertThrows(PrivacyVaultException::class.java) {
            PrivacyVaultCrypto.unlockMeta("wrong password", meta)
        }

        val tampered =
            encrypted.copyOf().also { bytes ->
                val marker = "\"ciphertext\":\"".toByteArray()
                val start =
                    bytes.indices.first { index ->
                        index + marker.size < bytes.size && bytes.copyOfRange(index, index + marker.size).contentEquals(marker)
                    } + marker.size
                bytes[start] = if (bytes[start].toInt() == 'A'.code) 'B'.code.toByte() else 'A'.code.toByte()
            }
        assertThrows(PrivacyVaultException::class.java) {
            PrivacyVaultCrypto.decryptNote(unlocked.masterKey, tampered)
        }
    }
}
