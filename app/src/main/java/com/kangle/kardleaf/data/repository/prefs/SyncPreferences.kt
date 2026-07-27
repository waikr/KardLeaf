package com.kangle.kardleaf.data.repository.prefs

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SyncPreferences(
    context: Context,
    private val prefs: SharedPreferences,
) {
    private val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): PrefsManager.WebDavSettings {
        val scopeName = prefs.getString(KEY_SYNC_SCOPE, PrefsManager.WebDavSyncScope.DATABASE_AND_VAULT.name)
        val scope = runCatching {
            PrefsManager.WebDavSyncScope.valueOf(scopeName ?: PrefsManager.WebDavSyncScope.DATABASE_AND_VAULT.name)
        }.getOrDefault(PrefsManager.WebDavSyncScope.DATABASE_AND_VAULT)
            .takeIf { it == PrefsManager.WebDavSyncScope.DATABASE_AND_VAULT }
            ?: PrefsManager.WebDavSyncScope.DATABASE_AND_VAULT
        val modeName = prefs.getString(KEY_SYNC_MODE, PrefsManager.WebDavSyncMode.INCREMENTAL.name)
        val mode = runCatching {
            PrefsManager.WebDavSyncMode.valueOf(modeName ?: PrefsManager.WebDavSyncMode.INCREMENTAL.name)
        }.getOrDefault(PrefsManager.WebDavSyncMode.INCREMENTAL)
            .takeIf { it == PrefsManager.WebDavSyncMode.INCREMENTAL }
            ?: PrefsManager.WebDavSyncMode.INCREMENTAL
        return PrefsManager.WebDavSettings(
            serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = getPassword(),
            remoteFolder = prefs.getString(KEY_REMOTE_FOLDER, "KardLeaf").orEmpty(),
            scope = scope,
            mode = mode,
        )
    }

    fun saveSettings(settings: PrefsManager.WebDavSettings): Boolean {
        if (!savePassword(settings.password)) {
            KardLeafLog.e(SECURITY_TAG, "Failed to save WebDAV password securely; settings were not updated")
            return false
        }
        val saved = prefs.edit()
            .putString(KEY_SERVER_URL, settings.serverUrl.trim())
            .putString(KEY_USERNAME, settings.username.trim())
            .remove(KEY_LEGACY_PASSWORD)
            .putString(KEY_REMOTE_FOLDER, normalizeNotePath(settings.remoteFolder))
            .putString(KEY_SYNC_SCOPE, settings.scope.name)
            .putString(KEY_SYNC_MODE, settings.mode.name)
            .commit()
        if (!saved) KardLeafLog.e(SECURITY_TAG, "Failed to save WebDAV settings")
        return saved
    }

    private fun getPassword(): String {
        securePrefs.getString(KEY_ENCRYPTED_PASSWORD, null)
            ?.let(::decryptPassword)
            ?.let { return it }
        val legacy = prefs.getString(KEY_LEGACY_PASSWORD, "").orEmpty()
        if (legacy.isNotBlank() && savePassword(legacy)) {
            if (!prefs.edit().remove(KEY_LEGACY_PASSWORD).commit()) {
                KardLeafLog.e(SECURITY_TAG, "Migrated WebDAV password but failed to remove legacy field")
            }
        } else if (legacy.isNotBlank()) {
            KardLeafLog.e(SECURITY_TAG, "Failed to migrate legacy WebDAV password; legacy field kept")
        }
        return legacy
    }

    private fun savePassword(password: String): Boolean {
        if (password.isBlank()) return securePrefs.edit().remove(KEY_ENCRYPTED_PASSWORD).commit()
        val encrypted = encryptPassword(password) ?: return false
        return securePrefs.edit().putString(KEY_ENCRYPTED_PASSWORD, encrypted).commit()
    }

    private fun encryptPassword(password: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreatePasswordKey())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }.onFailure {
        KardLeafLog.e(SECURITY_TAG, "Failed to encrypt WebDAV password", it)
    }.getOrNull()

    private fun decryptPassword(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        if (bytes.size <= 12) return@runCatching null
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreatePasswordKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.onFailure {
        KardLeafLog.e(SECURITY_TAG, "Failed to decrypt WebDAV password", it)
    }.getOrNull()

    private fun getOrCreatePasswordKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(PASSWORD_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                PASSWORD_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    fun getIncrementalLastUploadMs(): Long = prefs.getLong(KEY_INCREMENTAL_LAST_UPLOAD_MS, 0L)

    fun saveIncrementalLastUploadMs(value: Long) {
        prefs.edit().putLong(KEY_INCREMENTAL_LAST_UPLOAD_MS, value.coerceAtLeast(0L)).apply()
    }

    fun isRealtimeSyncEnabled(): Boolean = prefs.getBoolean(KEY_REALTIME_SYNC_ENABLED, false)

    fun saveRealtimeSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REALTIME_SYNC_ENABLED, enabled).apply()
    }

    fun getRealtimePollIntervalMs(): Long =
        prefs.getLong(KEY_REALTIME_POLL_INTERVAL_MS, PrefsManager.DEFAULT_WEBDAV_REALTIME_POLL_INTERVAL_MS)
            .coerceIn(1_000L, 60_000L)

    fun saveRealtimePollIntervalMs(intervalMs: Long) {
        prefs.edit().putLong(KEY_REALTIME_POLL_INTERVAL_MS, intervalMs.coerceIn(1_000L, 60_000L)).apply()
    }

    fun markRealtimeLocalDirty() {
        if (!isRealtimeSyncEnabled()) return
        prefs.edit().putLong(KEY_REALTIME_LOCAL_DIRTY_MS, System.currentTimeMillis()).apply()
    }

    fun getRealtimeLocalDirtyMs(): Long = prefs.getLong(KEY_REALTIME_LOCAL_DIRTY_MS, 0L)

    fun clearRealtimeLocalDirtyIfUnchanged(dirtyMs: Long) {
        if (dirtyMs <= 0L) return
        if (getRealtimeLocalDirtyMs() <= dirtyMs) prefs.edit().remove(KEY_REALTIME_LOCAL_DIRTY_MS).apply()
    }

    fun getRealtimeKnownRemoteMarker(): String = prefs.getString(KEY_REALTIME_KNOWN_REMOTE_MARKER, "").orEmpty()

    fun saveRealtimeKnownRemoteMarker(marker: String) {
        prefs.edit().putString(KEY_REALTIME_KNOWN_REMOTE_MARKER, marker).apply()
    }

    fun getRealtimeLastUploadRemoteMarker(): String =
        prefs.getString(KEY_REALTIME_LAST_UPLOAD_REMOTE_MARKER, "").orEmpty()

    fun saveRealtimeLastUploadRemoteMarker(marker: String) {
        prefs.edit().putString(KEY_REALTIME_LAST_UPLOAD_REMOTE_MARKER, marker).apply()
    }

    fun getFileSyncSnapshot(): String = prefs.getString(KEY_FILE_SYNC_SNAPSHOT, "").orEmpty()

    fun saveFileSyncSnapshot(snapshot: String) {
        prefs.edit().putString(KEY_FILE_SYNC_SNAPSHOT, snapshot).apply()
    }

    fun getPendingConflicts(): List<String> =
        prefs.getString(KEY_PENDING_CONFLICTS, "").orEmpty().lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    fun savePendingConflicts(value: String) {
        prefs.edit().putString(KEY_PENDING_CONFLICTS, value).apply()
    }

    fun clearPendingConflicts() {
        prefs.edit().remove(KEY_PENDING_CONFLICTS).apply()
    }

    fun getSyncLogs(): List<String> =
        prefs.getString(KEY_SYNC_LOGS, "").orEmpty().lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    fun appendSyncLog(message: String) {
        val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val cleanMessage = message.replace('\n', ' ').replace('\r', ' ').trim().take(240)
        val logs = (listOf("$time  $cleanMessage") + getSyncLogs()).take(MAX_SYNC_LOG_COUNT)
        prefs.edit().putString(KEY_SYNC_LOGS, logs.joinToString("\n")).apply()
    }

    fun clearSyncLogs() {
        prefs.edit().remove(KEY_SYNC_LOGS).apply()
    }

    private fun normalizeNotePath(path: String): String = path.trim().replace("\\", "/").trim('/')

    private companion object {
        const val SECURE_PREFS_NAME = "kardleaf_secure_prefs"
        const val KEY_SERVER_URL = "webdav_server_url"
        const val KEY_USERNAME = "webdav_username"
        const val KEY_LEGACY_PASSWORD = "webdav_password"
        const val KEY_REMOTE_FOLDER = "webdav_remote_folder"
        const val KEY_SYNC_SCOPE = "webdav_sync_scope"
        const val KEY_SYNC_MODE = "webdav_sync_mode"
        const val KEY_INCREMENTAL_LAST_UPLOAD_MS = "webdav_incremental_last_upload_ms"
        const val KEY_REALTIME_SYNC_ENABLED = "webdav_realtime_sync_enabled"
        const val KEY_REALTIME_LOCAL_DIRTY_MS = "webdav_realtime_local_dirty_ms"
        const val KEY_REALTIME_KNOWN_REMOTE_MARKER = "webdav_realtime_known_remote_marker"
        const val KEY_REALTIME_LAST_UPLOAD_REMOTE_MARKER = "webdav_realtime_last_upload_remote_marker"
        const val KEY_REALTIME_POLL_INTERVAL_MS = "webdav_realtime_poll_interval_ms"
        const val KEY_FILE_SYNC_SNAPSHOT = "webdav_file_sync_snapshot_v1"
        const val KEY_PENDING_CONFLICTS = "webdav_pending_conflicts_v1"
        const val KEY_SYNC_LOGS = "webdav_sync_logs"
        const val KEY_ENCRYPTED_PASSWORD = "webdav_password_encrypted"
        const val PASSWORD_KEY_ALIAS = "kardleaf_webdav_password"
        const val SECURITY_TAG = "KardLeafWebDavSecurity"
        const val MAX_SYNC_LOG_COUNT = 80
    }
}
