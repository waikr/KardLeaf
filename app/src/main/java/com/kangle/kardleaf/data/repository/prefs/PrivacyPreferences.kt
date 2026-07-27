package com.kangle.kardleaf.data.repository.prefs

import android.content.SharedPreferences

internal class PrivacyPreferences(private val prefs: SharedPreferences) {
    init {
        if (prefs.contains("safety_word_hash")) {
            prefs.edit().remove("safety_word_hash").apply()
        }
    }
    fun getAppPasswordHash(): String? = prefs.getString(KEY_APP_PASSWORD, null)?.takeIf { it.isNotBlank() }

    fun saveAppPasswordHash(hash: String?) {
        prefs.edit().apply {
            if (hash.isNullOrBlank()) {
                remove(KEY_APP_PASSWORD)
                remove(KEY_APP_BIOMETRIC_UNLOCK)
            } else {
                putString(KEY_APP_PASSWORD, hash)
            }
        }.apply()
    }

    fun isAppBiometricUnlockEnabled(): Boolean = prefs.getBoolean(KEY_APP_BIOMETRIC_UNLOCK, false)

    fun saveAppBiometricUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_BIOMETRIC_UNLOCK, enabled).apply()
    }

    fun getPrivacyPasswordHash(): String? =
        prefs.getString(KEY_PRIVACY_PASSWORD, null)?.takeIf { it.isNotBlank() }

    fun savePrivacyPasswordHash(hash: String?) {
        prefs.edit().apply {
            if (hash.isNullOrBlank()) {
                remove(KEY_PRIVACY_PASSWORD)
                remove(KEY_PRIVACY_BIOMETRIC_UNLOCK)
            } else {
                putString(KEY_PRIVACY_PASSWORD, hash)
            }
        }.apply()
    }


    fun isPrivacyBiometricUnlockEnabled(): Boolean = prefs.getBoolean(KEY_PRIVACY_BIOMETRIC_UNLOCK, false)

    fun savePrivacyBiometricUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_BIOMETRIC_UNLOCK, enabled).apply()
    }

    private companion object {
        const val KEY_APP_PASSWORD = "app_password_hash"
        const val KEY_PRIVACY_PASSWORD = "privacy_password_hash"
        const val KEY_APP_BIOMETRIC_UNLOCK = "app_biometric_unlock"
        const val KEY_PRIVACY_BIOMETRIC_UNLOCK = "privacy_biometric_unlock"
    }
}
