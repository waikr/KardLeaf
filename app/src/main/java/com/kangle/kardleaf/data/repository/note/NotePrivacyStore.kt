package com.kangle.kardleaf.data.repository.note

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.kangle.kardleaf.data.database.PrivacyNoteDao
import com.kangle.kardleaf.data.database.PrivacyNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal class NotePrivacyStore(
    private val context: Context,
    private val legacyDao: PrivacyNoteDao,
) {
    data class BiometricUnlockRequest(val vaultId: String, val cipher: Cipher)

    private data class Backup(
        @field:SerializedName(value = "title", alternate = ["a"])
        val title: String = "",
        @field:SerializedName(value = "content", alternate = ["b"])
        val content: String = "",
        @field:SerializedName(value = "updatedAtMs", alternate = ["c"])
        val updatedAtMs: Long = 0L,
    )

    private data class LoadedVault(
        val notes: List<PrivacyNoteEntity>,
        val fileNamesById: Map<Long, Set<String>>,
    )

    private val notes = MutableStateFlow<List<PrivacyNoteEntity>>(emptyList())
    private val mutex = Mutex()
    private val random = SecureRandom()
    private val localKeys = context.getSharedPreferences(LOCAL_KEYS_PREFS, Context.MODE_PRIVATE)
    private var rootDir: DocumentFile? = null
    private var masterKey: ByteArray? = null
    private var fileNamesById: Map<Long, Set<String>> = emptyMap()

    fun getAll(): Flow<List<PrivacyNoteEntity>> = notes

    suspend fun onRootChanged(root: DocumentFile?) =
        mutex.withLock {
            clearUnlockedState()
            rootDir = root
        }

    suspend fun hasVault(): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock { findMetaFile(resolveVaultDir(create = false)) != null }
        }

    suspend fun initialize(password: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(password.isNotBlank()) { "隐私密码不能为空" }
                val vaultDir = resolveVaultDir(create = true) ?: throw PrivacyVaultException("无法创建隐私仓库目录")
                if (findMetaFile(vaultDir) != null) throw PrivacyVaultException("当前笔记库已存在隐私仓库")
                if (legacyDao.getAllOnce().isNotEmpty()) throw PrivacyVaultException("检测到旧隐私数据，请使用原隐私密码解锁迁移")
                unlockNewVault(vaultDir, password, deleteMetaOnFailure = true)
            }
        }

    suspend fun unlock(
        password: String,
        legacyPasswordVerified: Boolean,
    ) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val vaultDir = resolveVaultDir(create = true) ?: throw PrivacyVaultException("无法访问隐私仓库目录")
                val metaFile = findMetaFile(vaultDir)
                if (metaFile == null) {
                    if (!legacyPasswordVerified) throw PrivacyVaultException("隐私密码错误")
                    unlockNewVault(vaultDir, password)
                    return@withLock
                }

                val unlocked = PrivacyVaultCrypto.unlockMeta(password, readBytes(metaFile, MAX_META_BYTES))
                try {
                    val notesDir = resolveNotesDir(vaultDir, create = true) ?: throw PrivacyVaultException("无法访问隐私笔记目录")
                    var loaded = readAllNotes(notesDir, unlocked.masterKey)
                    loaded = migrateLegacyNotes(notesDir, unlocked.masterKey, loaded)
                    setUnlockedState(unlocked.masterKey, loaded)
                    runCatching { cacheBiometricMasterKey(unlocked.vaultId, unlocked.masterKey) }
                } catch (e: Exception) {
                    unlocked.masterKey.fill(0)
                    clearUnlockedState()
                    throw e
                }
            }
        }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        legacyPasswordVerified: Boolean,
    ) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(newPassword.isNotBlank()) { "新隐私密码不能为空" }
                val vaultDir = resolveVaultDir(create = false) ?: throw PrivacyVaultException("未找到隐私仓库")
                if (findMetaFile(vaultDir) == null) {
                    if (!legacyPasswordVerified) throw PrivacyVaultException("当前隐私密码错误")
                    unlockNewVault(vaultDir, currentPassword)
                }
                val currentMeta = findMetaFile(vaultDir) ?: throw PrivacyVaultException("未找到隐私仓库元数据")
                val unlocked = PrivacyVaultCrypto.unlockMeta(currentPassword, readBytes(currentMeta, MAX_META_BYTES))
                try {
                    val replacement = PrivacyVaultCrypto.createMeta(newPassword, unlocked.vaultId, unlocked.masterKey)
                    replaceMeta(vaultDir, replacement) { bytes ->
                        PrivacyVaultCrypto.unlockMeta(newPassword, bytes).masterKey.fill(0)
                    }
                    val notesDir = resolveNotesDir(vaultDir, create = true) ?: throw PrivacyVaultException("无法访问隐私笔记目录")
                    val loaded = readAllNotes(notesDir, unlocked.masterKey)
                    setUnlockedState(unlocked.masterKey, loaded)
                    runCatching { cacheBiometricMasterKey(unlocked.vaultId, unlocked.masterKey) }
                } catch (e: Exception) {
                    unlocked.masterKey.fill(0)
                    throw e
                }
            }
        }

    suspend fun removePassword(
        currentPassword: String,
        legacyPasswordVerified: Boolean,
    ) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val vaultDir =
                    resolveVaultDir(create = false) ?: run {
                        if (legacyDao.getAllOnce().isNotEmpty()) throw PrivacyVaultException("隐私仓库中仍有笔记，不能移除密码")
                        return@withLock
                    }
                val metaFile = findMetaFile(vaultDir)
                if (metaFile == null) {
                    if (!legacyPasswordVerified) throw PrivacyVaultException("当前隐私密码错误")
                    if (legacyDao.getAllOnce().isNotEmpty()) throw PrivacyVaultException("隐私仓库中仍有笔记，不能移除密码")
                    return@withLock
                }
                val unlocked = PrivacyVaultCrypto.unlockMeta(currentPassword, readBytes(metaFile, MAX_META_BYTES))
                try {
                    val notesDir = resolveNotesDir(vaultDir, create = false)
                    if (notesDir != null && readAllNotes(notesDir, unlocked.masterKey).notes.isNotEmpty()) {
                        throw PrivacyVaultException("隐私仓库中仍有笔记，不能移除密码")
                    }
                    if (!metaFile.delete()) throw PrivacyVaultException("无法移除隐私仓库密码")
                    clearBiometricMasterKey(unlocked.vaultId)
                    legacyDao.deleteAll()
                    clearUnlockedState()
                } finally {
                    unlocked.masterKey.fill(0)
                }
            }
        }

    suspend fun prepareBiometricUnlock(): BiometricUnlockRequest? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val vaultDir = resolveVaultDir(create = false) ?: return@withLock null
                val meta = findMetaFile(vaultDir) ?: return@withLock null
                val currentVaultId = PrivacyVaultCrypto.readVaultId(readBytes(meta, MAX_META_BYTES))
                val wrapped =
                    localKeys.getString(localKey(currentVaultId), null)?.let {
                        runCatching { Base64.Default.decode(it) }.getOrNull()
                    } ?: return@withLock null
                if (wrapped.isEmpty()) return@withLock null
                wrapped.fill(0)
                val privateKey = androidKeyStore().getKey(keyAlias(currentVaultId), null) ?: return@withLock null
                val cipher = rsaCipher().apply { init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC) }
                BiometricUnlockRequest(currentVaultId, cipher)
            }
        }

    suspend fun unlockWithBiometric(request: BiometricUnlockRequest) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val vaultDir = resolveVaultDir(create = false) ?: throw PrivacyVaultException("未找到隐私仓库")
                val meta = findMetaFile(vaultDir) ?: throw PrivacyVaultException("未找到隐私仓库元数据")
                val currentVaultId = PrivacyVaultCrypto.readVaultId(readBytes(meta, MAX_META_BYTES))
                if (currentVaultId != request.vaultId || legacyDao.getAllOnce().isNotEmpty()) {
                    throw PrivacyVaultException("首次升级请使用隐私密码解锁并完成迁移")
                }
                val wrapped =
                    localKeys.getString(localKey(currentVaultId), null)?.let { Base64.Default.decode(it) }
                        ?: throw PrivacyVaultException("请先使用隐私密码解锁一次")
                val master =
                    try {
                        request.cipher.doFinal(wrapped)
                    } catch (e: Exception) {
                        throw PrivacyVaultException("指纹快捷密钥已失效，请使用隐私密码解锁", e)
                    } finally {
                        wrapped.fill(0)
                    }
                if (master.size != PrivacyVaultCrypto.MASTER_KEY_BYTES) {
                    master.fill(0)
                    throw PrivacyVaultException("指纹快捷密钥无效")
                }
                try {
                    val notesDir = resolveNotesDir(vaultDir, create = true) ?: throw PrivacyVaultException("无法访问隐私笔记目录")
                    setUnlockedState(master, readAllNotes(notesDir, master))
                } catch (e: Exception) {
                    master.fill(0)
                    clearUnlockedState()
                    throw e
                }
            }
        }

    suspend fun lock() = mutex.withLock { clearUnlockedState() }

    suspend fun save(
        id: Long,
        title: String,
        content: String,
    ): Long =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val key = requireMasterKey()
                val notesDir = requireNotesDir()
                val noteId = if (id > 0L) id else generateNoteId()
                val previousUpdatedAt = notes.value.firstOrNull { it.id == noteId }?.updatedAtMs ?: -1L
                val updatedAt = maxOf(System.currentTimeMillis(), previousUpdatedAt + 1L)
                val payload = PrivacyVaultCrypto.NotePayload(noteId, title, content, updatedAt)
                writeNote(notesDir, key, payload, fileNamesById[noteId].orEmpty())
                reloadUnlockedNotes(notesDir, key)
                noteId
            }
        }

    suspend fun delete(id: Long) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val key = requireMasterKey()
                val notesDir = requireNotesDir()
                val failed = fileNamesById[id].orEmpty().filter { name -> notesDir.findFile(name)?.delete() == false }
                if (failed.isNotEmpty()) throw PrivacyVaultException("隐私笔记删除失败")
                reloadUnlockedNotes(notesDir, key)
            }
        }

    suspend fun export(): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                requireMasterKey()
                Gson().toJson(notes.value.map { Backup(it.title, it.content, it.updatedAtMs) })
            }
        }

    suspend fun import(json: String): Int =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val key = requireMasterKey()
                val type = object : TypeToken<List<Backup>>() {}.type
                val list: List<Backup> =
                    try {
                        Gson().fromJson(json, type) ?: emptyList()
                    } catch (e: Exception) {
                        throw PrivacyVaultException("隐私备份格式无效", e)
                    }
                val notesDir = requireNotesDir()
                list.forEach { item ->
                    val payload =
                        PrivacyVaultCrypto.NotePayload(
                            id = generateNoteId(),
                            title = item.title,
                            content = item.content,
                            updatedAtMs = item.updatedAtMs.coerceAtLeast(0L),
                        )
                    writeNote(notesDir, key, payload, emptySet())
                    reloadUnlockedNotes(notesDir, key)
                }
                list.size
            }
        }

    private suspend fun unlockNewVault(
        vaultDir: DocumentFile,
        password: String,
        deleteMetaOnFailure: Boolean = false,
    ) {
        val master = PrivacyVaultCrypto.newMasterKey()
        val newVaultId = UUID.randomUUID().toString().replace("-", "")
        try {
            val meta = PrivacyVaultCrypto.createMeta(password, newVaultId, master)
            replaceMeta(vaultDir, meta) { bytes -> PrivacyVaultCrypto.unlockMeta(password, bytes).masterKey.fill(0) }
            val notesDir = resolveNotesDir(vaultDir, create = true) ?: throw PrivacyVaultException("无法创建隐私笔记目录")
            var loaded = readAllNotes(notesDir, master)
            loaded = migrateLegacyNotes(notesDir, master, loaded)
            setUnlockedState(master, loaded)
            runCatching { cacheBiometricMasterKey(newVaultId, master) }
        } catch (e: Exception) {
            if (deleteMetaOnFailure) findMetaFile(vaultDir)?.delete()
            master.fill(0)
            clearUnlockedState()
            throw e
        }
    }

    private suspend fun migrateLegacyNotes(
        notesDir: DocumentFile,
        key: ByteArray,
        initial: LoadedVault,
    ): LoadedVault {
        val legacy = legacyDao.getAllOnce()
        if (legacy.isEmpty()) return initial
        var loaded = initial
        legacy.forEach { old ->
            val payload = PrivacyVaultCrypto.NotePayload(old.id, old.title, old.content, old.updatedAtMs)
            val existing = loaded.notes.firstOrNull { it.id == old.id }
            if (existing == null) {
                writeNote(notesDir, key, payload, emptySet())
                loaded = readAllNotes(notesDir, key)
            } else if (existing.title != old.title || existing.content != old.content || existing.updatedAtMs != old.updatedAtMs) {
                throw PrivacyVaultException("旧隐私笔记迁移发现 ID 冲突，已保留 Room 原数据")
            }
        }
        val verified = readAllNotes(notesDir, key)
        val verifiedById = verified.notes.associateBy { it.id }
        if (legacy.any { old ->
                verifiedById[old.id]?.let { it.title == old.title && it.content == old.content && it.updatedAtMs == old.updatedAtMs } != true
            }
        ) {
            throw PrivacyVaultException("旧隐私笔记迁移校验失败，已保留 Room 原数据")
        }
        legacyDao.deleteAll()
        return verified
    }

    private fun writeNote(
        notesDir: DocumentFile,
        key: ByteArray,
        payload: PrivacyVaultCrypto.NotePayload,
        oldFileNames: Set<String>,
    ) {
        val encoded = PrivacyVaultCrypto.encryptNote(key, payload)
        val finalName = UUID.randomUUID().toString().replace("-", "") + NOTE_EXTENSION
        val published = publishNewFile(notesDir, finalName, encoded)
        try {
            if (PrivacyVaultCrypto.decryptNote(key, readBytes(published, MAX_NOTE_FILE_BYTES)) != payload) {
                throw PrivacyVaultException("隐私笔记写入校验失败")
            }
        } catch (e: Exception) {
            published.delete()
            throw e
        } finally {
            encoded.fill(0)
        }
        oldFileNames.filter { it != finalName }.forEach { notesDir.findFile(it)?.delete() }
    }

    private fun readAllNotes(
        notesDir: DocumentFile,
        key: ByteArray,
    ): LoadedVault {
        val variants = mutableMapOf<Long, MutableSet<String>>()
        val newest = mutableMapOf<Long, PrivacyNoteEntity>()
        notesDir.listFiles()
            .filter { it.isFile && it.name.orEmpty().endsWith(NOTE_EXTENSION, ignoreCase = true) }
            .forEach { file ->
                val name = file.name ?: throw PrivacyVaultException("隐私笔记文件名无效")
                val payload = PrivacyVaultCrypto.decryptNote(key, readBytes(file, MAX_NOTE_FILE_BYTES))
                variants.getOrPut(payload.id) { linkedSetOf() }.add(name)
                val current = newest[payload.id]
                if (current == null || payload.updatedAtMs > current.updatedAtMs) {
                    newest[payload.id] = PrivacyNoteEntity(payload.id, payload.title, payload.content, payload.updatedAtMs)
                }
            }
        return LoadedVault(newest.values.sortedByDescending { it.updatedAtMs }, variants)
    }

    private fun reloadUnlockedNotes(
        notesDir: DocumentFile,
        key: ByteArray,
    ) {
        val loaded = readAllNotes(notesDir, key)
        notes.value = loaded.notes
        fileNamesById = loaded.fileNamesById
    }

    private fun setUnlockedState(
        key: ByteArray,
        loaded: LoadedVault,
    ) {
        masterKey?.takeUnless { it === key }?.fill(0)
        masterKey = key
        notes.value = loaded.notes
        fileNamesById = loaded.fileNamesById
    }

    private fun clearUnlockedState() {
        masterKey?.fill(0)
        masterKey = null
        fileNamesById = emptyMap()
        notes.value = emptyList()
    }

    private fun requireMasterKey(): ByteArray = masterKey ?: throw PrivacyVaultException("隐私仓库已锁定")

    private fun requireNotesDir(): DocumentFile {
        val vault = resolveVaultDir(create = false) ?: throw PrivacyVaultException("未找到隐私仓库")
        return resolveNotesDir(vault, create = true) ?: throw PrivacyVaultException("无法访问隐私笔记目录")
    }

    private fun generateNoteId(): Long {
        val used = notes.value.mapTo(mutableSetOf()) { it.id }
        while (true) {
            val candidate = random.nextLong() and Long.MAX_VALUE
            if (candidate > 0L && candidate !in used) return candidate
        }
    }

    private fun resolveVaultDir(create: Boolean): DocumentFile? {
        val root = rootDir ?: return null
        val appDir =
            (
                root.findFile(APP_DIR)?.takeIf { it.isDirectory }
                    ?: if (create) root.createDirectory(APP_DIR) else null
            ) ?: return null
        return appDir.findFile(VAULT_DIR)?.takeIf { it.isDirectory }
            ?: if (create) appDir.createDirectory(VAULT_DIR) else null
    }

    private fun resolveNotesDir(
        vault: DocumentFile,
        create: Boolean,
    ): DocumentFile? =
        vault.findFile(NOTES_DIR)?.takeIf { it.isDirectory }
            ?: if (create) vault.createDirectory(NOTES_DIR) else null

    private fun findMetaFile(vault: DocumentFile?): DocumentFile? {
        if (vault == null) return null
        vault.findFile(META_FILE)?.takeIf { it.isFile }?.let { return it }
        val backup = vault.findFile(META_BACKUP_FILE)?.takeIf { it.isFile } ?: return null
        backup.renameTo(META_FILE)
        return vault.findFile(META_FILE)?.takeIf { it.isFile } ?: backup
    }

    private fun replaceMeta(
        vault: DocumentFile,
        bytes: ByteArray,
        verify: (ByteArray) -> Unit,
    ) {
        val temp =
            vault.createFile(BINARY_MIME, ".$META_FILE.${UUID.randomUUID()}.tmp")
                ?: throw IOException("无法创建隐私仓库临时文件")
        try {
            writeBytes(temp, bytes)
            verify(readBytes(temp, MAX_META_BYTES))
            val current = vault.findFile(META_FILE)?.takeIf { it.isFile }
            val staleBackup = vault.findFile(META_BACKUP_FILE)?.takeIf { it.isFile }
            if (staleBackup != null && !staleBackup.delete()) throw IOException("无法清理隐私仓库元数据备份")
            if (current != null && !current.renameTo(META_BACKUP_FILE)) throw IOException("无法备份隐私仓库元数据")
            if (!temp.renameTo(META_FILE)) {
                vault.findFile(META_BACKUP_FILE)?.renameTo(META_FILE)
                throw IOException("无法原子发布隐私仓库元数据")
            }
            vault.findFile(META_BACKUP_FILE)?.delete()
        } catch (e: Exception) {
            temp.delete()
            throw PrivacyVaultException("隐私仓库元数据写入失败", e)
        }
    }

    private fun publishNewFile(
        dir: DocumentFile,
        finalName: String,
        bytes: ByteArray,
    ): DocumentFile {
        val temp =
            dir.createFile(BINARY_MIME, ".$finalName.${UUID.randomUUID()}.tmp")
                ?: throw PrivacyVaultException("无法创建隐私笔记临时文件")
        try {
            writeBytes(temp, bytes)
            if (!readBytes(temp, MAX_NOTE_FILE_BYTES).contentEquals(bytes)) throw IOException("临时文件校验失败")
            if (!temp.renameTo(finalName)) throw IOException("同目录重命名失败")
            return dir.findFile(finalName)?.takeIf { it.isFile } ?: temp
        } catch (e: Exception) {
            temp.delete()
            throw PrivacyVaultException("隐私笔记写入失败", e)
        }
    }

    private fun writeBytes(
        file: DocumentFile,
        bytes: ByteArray,
    ) {
        val resolver = context.contentResolver
        runCatching {
            resolver.openFileDescriptor(file.uri, "rwt")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
            } ?: throw IOException("openFileDescriptor returned null")
        }.getOrElse {
            resolver.openOutputStream(file.uri, "wt")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: throw IOException("openOutputStream returned null")
        }
    }

    private fun readBytes(
        file: DocumentFile,
        limit: Int,
    ): ByteArray {
        if (file.length() > limit) throw PrivacyVaultException("隐私仓库文件过大")
        val input = context.contentResolver.openInputStream(file.uri) ?: throw IOException("openInputStream returned null")
        return input.use { stream ->
            val initialSize = file.length().coerceIn(0L, 64L * 1024L).toInt()
            val output = ByteArrayOutputStream(initialSize)
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw PrivacyVaultException("隐私仓库文件过大")
                output.write(buffer, 0, read)
            }
            buffer.fill(0)
            output.toByteArray()
        }
    }

    private fun cacheBiometricMasterKey(
        id: String,
        key: ByteArray,
    ) {
        val alias = keyAlias(id)
        val keyStore = androidKeyStore()
        if (!keyStore.containsAlias(alias)) generateBiometricKeyPair(alias)
        val wrapped =
            try {
                val publicKey = androidKeyStore().getCertificate(alias).publicKey
                rsaCipher().run {
                    init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC)
                    doFinal(key)
                }
            } catch (_: Exception) {
                keyStore.deleteEntry(alias)
                generateBiometricKeyPair(alias)
                val publicKey = androidKeyStore().getCertificate(alias).publicKey
                rsaCipher().run {
                    init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC)
                    doFinal(key)
                }
            }
        try {
            if (!localKeys.edit().putString(localKey(id), Base64.Default.encode(wrapped)).commit()) {
                throw IOException("无法保存本机指纹快捷密钥")
            }
        } finally {
            wrapped.fill(0)
        }
    }

    private fun clearBiometricMasterKey(id: String) {
        localKeys.edit().remove(localKey(id)).commit()
        runCatching { androidKeyStore().deleteEntry(keyAlias(id)) }
    }

    private fun generateBiometricKeyPair(alias: String) {
        val builder =
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(2048)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setInvalidatedByBiometricEnrollment(true)
        }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE).run {
            initialize(builder.build())
            generateKeyPair()
        }
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun rsaCipher(): Cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")

    private fun keyAlias(id: String): String = "kardleaf_privacy_vault_$id"

    private fun localKey(id: String): String = "wrapped_master_$id"

    private companion object {
        const val APP_DIR = ".KardLeaf"
        const val VAULT_DIR = "Vault"
        const val NOTES_DIR = "notes"
        const val META_FILE = "vault.meta"
        const val META_BACKUP_FILE = "vault.meta.bak"
        const val NOTE_EXTENSION = ".klv"
        const val BINARY_MIME = "application/octet-stream"
        const val MAX_META_BYTES = 64 * 1024
        const val MAX_NOTE_FILE_BYTES = 64 * 1024 * 1024
        const val LOCAL_KEYS_PREFS = "kardleaf_privacy_vault_keys"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        val OAEP_SPEC =
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT,
            )
    }
}
