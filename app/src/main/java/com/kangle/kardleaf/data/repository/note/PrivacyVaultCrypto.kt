package com.kangle.kardleaf.data.repository.note

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal class PrivacyVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

@OptIn(ExperimentalEncodingApi::class)
internal object PrivacyVaultCrypto {
    const val MASTER_KEY_BYTES = 32
    const val DEFAULT_PBKDF2_ITERATIONS = 600_000

    data class NotePayload(
        @field:SerializedName("id") val id: Long,
        @field:SerializedName("title") val title: String,
        @field:SerializedName("content") val content: String,
        @field:SerializedName("updatedAtMs") val updatedAtMs: Long,
    )

    private data class VaultMeta(
        @field:SerializedName("format") val format: String = META_FORMAT,
        @field:SerializedName("version") val version: Int = FORMAT_VERSION,
        @field:SerializedName("vaultId") val vaultId: String,
        @field:SerializedName("kdf") val kdf: String = PASSWORD_KDF,
        @field:SerializedName("iterations") val iterations: Int,
        @field:SerializedName("salt") val salt: String,
        @field:SerializedName("cipher") val cipher: String = CIPHER,
        @field:SerializedName("nonce") val nonce: String,
        @field:SerializedName("tagBits") val tagBits: Int = GCM_TAG_BITS,
        @field:SerializedName("wrappedKey") val wrappedKey: String,
    )

    private data class NoteEnvelope(
        @field:SerializedName("format") val format: String = NOTE_FORMAT,
        @field:SerializedName("version") val version: Int = FORMAT_VERSION,
        @field:SerializedName("kdf") val kdf: String = NOTE_KDF,
        @field:SerializedName("salt") val salt: String,
        @field:SerializedName("cipher") val cipher: String = CIPHER,
        @field:SerializedName("nonce") val nonce: String,
        @field:SerializedName("tagBits") val tagBits: Int = GCM_TAG_BITS,
        @field:SerializedName("ciphertext") val ciphertext: String,
    )

    data class UnlockedMeta(val vaultId: String, val masterKey: ByteArray)

    private val gson = Gson()
    private val random = SecureRandom()

    fun newMasterKey(): ByteArray = randomBytes(MASTER_KEY_BYTES)

    fun createMeta(
        password: String,
        vaultId: String,
        masterKey: ByteArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    ): ByteArray {
        require(masterKey.size == MASTER_KEY_BYTES)
        require(iterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS)
        val salt = randomBytes(PASSWORD_SALT_BYTES)
        val nonce = randomBytes(GCM_NONCE_BYTES)
        val key = derivePasswordKey(password, salt, iterations)
        return try {
            val header =
                VaultMeta(
                    vaultId = vaultId,
                    iterations = iterations,
                    salt = salt.b64(),
                    nonce = nonce.b64(),
                    wrappedKey = "",
                )
            val wrapped = aesGcm(Cipher.ENCRYPT_MODE, key, nonce, metaAad(header), masterKey)
            gson.toJson(header.copy(wrappedKey = wrapped.b64())).toByteArray(Charsets.UTF_8)
        } finally {
            key.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }

    fun unlockMeta(
        password: String,
        encoded: ByteArray,
    ): UnlockedMeta {
        val meta = parseMeta(encoded)
        val salt = meta.salt.b64Bytes(PASSWORD_SALT_BYTES)
        val nonce = meta.nonce.b64Bytes(GCM_NONCE_BYTES)
        val wrapped = meta.wrappedKey.b64Bytes(maxBytes = MASTER_KEY_BYTES + GCM_TAG_BYTES)
        val key = derivePasswordKey(password, salt, meta.iterations)
        return try {
            val master = aesGcm(Cipher.DECRYPT_MODE, key, nonce, metaAad(meta), wrapped)
            if (master.size != MASTER_KEY_BYTES) {
                master.fill(0)
                throw PrivacyVaultException(DECRYPTION_ERROR)
            }
            UnlockedMeta(meta.vaultId, master)
        } catch (e: PrivacyVaultException) {
            throw e
        } catch (e: Exception) {
            throw PrivacyVaultException(DECRYPTION_ERROR, e)
        } finally {
            key.fill(0)
            salt.fill(0)
            nonce.fill(0)
            wrapped.fill(0)
        }
    }

    fun readVaultId(encoded: ByteArray): String = parseMeta(encoded).vaultId

    fun encryptNote(
        masterKey: ByteArray,
        payload: NotePayload,
    ): ByteArray {
        require(masterKey.size == MASTER_KEY_BYTES)
        val salt = randomBytes(NOTE_SALT_BYTES)
        val nonce = randomBytes(GCM_NONCE_BYTES)
        val key = hkdfSha256(masterKey, salt, NOTE_INFO)
        val plaintext = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        return try {
            val header = NoteEnvelope(salt = salt.b64(), nonce = nonce.b64(), ciphertext = "")
            val ciphertext = aesGcm(Cipher.ENCRYPT_MODE, key, nonce, noteAad(header), plaintext)
            gson.toJson(header.copy(ciphertext = ciphertext.b64())).toByteArray(Charsets.UTF_8)
        } finally {
            key.fill(0)
            salt.fill(0)
            nonce.fill(0)
            plaintext.fill(0)
        }
    }

    fun decryptNote(
        masterKey: ByteArray,
        encoded: ByteArray,
    ): NotePayload {
        require(masterKey.size == MASTER_KEY_BYTES)
        val envelope = parseNoteEnvelope(encoded)
        val salt = envelope.salt.b64Bytes(NOTE_SALT_BYTES)
        val nonce = envelope.nonce.b64Bytes(GCM_NONCE_BYTES)
        val ciphertext = envelope.ciphertext.b64Bytes(maxBytes = MAX_ENCRYPTED_NOTE_BYTES)
        val key = hkdfSha256(masterKey, salt, NOTE_INFO)
        var plaintext: ByteArray? = null
        return try {
            plaintext = aesGcm(Cipher.DECRYPT_MODE, key, nonce, noteAad(envelope), ciphertext)
            gson.fromJson(String(plaintext, Charsets.UTF_8), NotePayload::class.java)
                ?.takeIf { it.id > 0L && it.updatedAtMs >= 0L }
                ?: throw PrivacyVaultException(DECRYPTION_ERROR)
        } catch (e: PrivacyVaultException) {
            throw e
        } catch (e: Exception) {
            throw PrivacyVaultException(DECRYPTION_ERROR, e)
        } finally {
            key.fill(0)
            salt.fill(0)
            nonce.fill(0)
            ciphertext.fill(0)
            plaintext?.fill(0)
        }
    }

    fun legacyPasswordHash(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun parseMeta(encoded: ByteArray): VaultMeta =
        try {
            gson.fromJson(String(encoded, Charsets.UTF_8), VaultMeta::class.java)
                ?.takeIf {
                    it.format == META_FORMAT &&
                        it.version == FORMAT_VERSION &&
                        it.vaultId.matches(VAULT_ID_PATTERN) &&
                        it.kdf == PASSWORD_KDF &&
                        it.iterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS &&
                        it.cipher == CIPHER &&
                        it.tagBits == GCM_TAG_BITS
                } ?: throw PrivacyVaultException(FORMAT_ERROR)
        } catch (e: PrivacyVaultException) {
            throw e
        } catch (e: Exception) {
            throw PrivacyVaultException(FORMAT_ERROR, e)
        }

    private fun parseNoteEnvelope(encoded: ByteArray): NoteEnvelope =
        try {
            gson.fromJson(String(encoded, Charsets.UTF_8), NoteEnvelope::class.java)
                ?.takeIf {
                    it.format == NOTE_FORMAT &&
                        it.version == FORMAT_VERSION &&
                        it.kdf == NOTE_KDF &&
                        it.cipher == CIPHER &&
                        it.tagBits == GCM_TAG_BITS
                } ?: throw PrivacyVaultException(FORMAT_ERROR)
        } catch (e: PrivacyVaultException) {
            throw e
        } catch (e: Exception) {
            throw PrivacyVaultException(FORMAT_ERROR, e)
        }

    internal fun derivePasswordKey(
        password: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        val firstBlockInput = salt + byteArrayOf(0, 0, 0, 1)
        var u = mac.doFinal(firstBlockInput)
        val result = u.copyOf()
        return try {
            repeat(iterations - 1) {
                val next = mac.doFinal(u)
                u.fill(0)
                u = next
                result.indices.forEach { index -> result[index] = (result[index].toInt() xor u[index].toInt()).toByte() }
            }
            result
        } finally {
            passwordBytes.fill(0)
            firstBlockInput.fill(0)
            u.fill(0)
        }
    }

    private fun hkdfSha256(
        inputKey: ByteArray,
        salt: ByteArray,
        info: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(inputKey)
        return try {
            mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            mac.update(info)
            mac.doFinal(byteArrayOf(1)).copyOf(MASTER_KEY_BYTES)
        } finally {
            pseudoRandomKey.fill(0)
        }
    }

    private fun aesGcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        input: ByteArray,
    ): ByteArray =
        Cipher.getInstance(CIPHER).run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            updateAAD(aad)
            doFinal(input)
        }

    private fun metaAad(meta: VaultMeta): ByteArray =
        listOf(meta.format, meta.version, meta.vaultId, meta.kdf, meta.iterations, meta.salt, meta.cipher, meta.nonce, meta.tagBits)
            .joinToString("|")
            .toByteArray(Charsets.UTF_8)

    private fun noteAad(note: NoteEnvelope): ByteArray =
        listOf(note.format, note.version, note.kdf, note.salt, note.cipher, note.nonce, note.tagBits)
            .joinToString("|")
            .toByteArray(Charsets.UTF_8)

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun ByteArray.b64(): String = Base64.Default.encode(this)

    private fun String.b64Bytes(
        expectedBytes: Int? = null,
        maxBytes: Int = expectedBytes ?: Int.MAX_VALUE,
    ): ByteArray =
        try {
            Base64.Default.decode(this).also {
                if ((expectedBytes != null && it.size != expectedBytes) || it.size > maxBytes) {
                    it.fill(0)
                    throw PrivacyVaultException(FORMAT_ERROR)
                }
            }
        } catch (e: PrivacyVaultException) {
            throw e
        } catch (e: Exception) {
            throw PrivacyVaultException(FORMAT_ERROR, e)
        }

    private const val FORMAT_VERSION = 1
    private const val META_FORMAT = "KardLeafPrivacyVault"
    private const val NOTE_FORMAT = "KardLeafPrivacyNote"
    private const val PASSWORD_KDF = "PBKDF2WithHmacSHA256"
    private const val NOTE_KDF = "HKDF-HMAC-SHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    private const val GCM_NONCE_BYTES = 12
    private const val PASSWORD_SALT_BYTES = 32
    private const val NOTE_SALT_BYTES = 32
    private const val MIN_PBKDF2_ITERATIONS = 100_000
    private const val MAX_PBKDF2_ITERATIONS = 5_000_000
    private const val MAX_ENCRYPTED_NOTE_BYTES = 64 * 1024 * 1024
    private val NOTE_INFO = "KardLeaf privacy note key v1".toByteArray(Charsets.UTF_8)
    private val VAULT_ID_PATTERN = Regex("[0-9a-f]{32}")
    private const val FORMAT_ERROR = "隐私仓库文件格式无效"
    private const val DECRYPTION_ERROR = "密码错误或隐私仓库文件已损坏"
}
