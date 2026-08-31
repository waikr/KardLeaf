package com.kangle.kardleaf.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

const val DEFAULT_VAULT_DATABASE_NAME = "kardleaf_database"
private const val VAULT_DATABASE_PREFIX = "${DEFAULT_VAULT_DATABASE_NAME}_"
private val vaultDatabaseNamePattern = Regex("${VAULT_DATABASE_PREFIX}[0-9a-f]{16}")

data class VaultInfo(
    @field:SerializedName(value = "uri", alternate = ["a"])
    val uri: String,
    @field:SerializedName(value = "displayName", alternate = ["b"])
    val displayName: String,
    @field:SerializedName(value = "databaseName", alternate = ["c"])
    val databaseName: String,
)

internal fun databaseNameForVault(uri: String): String {
    val suffix = MessageDigest.getInstance("SHA-256")
        .digest(uri.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    return "$VAULT_DATABASE_PREFIX$suffix"
}

internal fun isVaultDatabaseName(name: String): Boolean =
    name == DEFAULT_VAULT_DATABASE_NAME || vaultDatabaseNamePattern.matches(name)

internal object VaultRegistryCodec {
    private val gson = Gson()
    private val listType = object : TypeToken<List<VaultInfo>>() {}.type

    fun decode(raw: String?): List<VaultInfo> =
        runCatching { gson.fromJson<List<VaultInfo>>(raw, listType).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.uri.isNotBlank() && it.displayName.isNotBlank() && isVaultDatabaseName(it.databaseName) }
            .distinctBy(VaultInfo::uri)

    fun encode(vaults: List<VaultInfo>): String = gson.toJson(vaults)
}
