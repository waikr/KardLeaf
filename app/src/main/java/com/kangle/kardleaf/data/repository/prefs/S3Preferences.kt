package com.kangle.kardleaf.data.repository.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.kangle.kardleaf.data.sync.S3Paths
import com.kangle.kardleaf.data.sync.s3Hash
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class S3Settings(
    val endpoint: String = "",
    val region: String = "",
    val bucket: String = "",
    val prefix: String = "",
    val forcePathStyle: Boolean = false,
    val syncUnderscore: Boolean = false,
    val realtime: Boolean = false,
    val pollSeconds: Int = 30,
) {
    fun validate() {
        val url = endpoint.toHttpUrlOrNull() ?: error("S3 Endpoint 无效")
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.encodedPath == "/" && url.query == null && url.fragment == null) {
            "Endpoint 必须是 HTTPS 服务地址，不含路径、账号或查询参数"
        }
        require(region.matches(Regex("[a-zA-Z0-9-]+"))) { "请填写有效 Region" }
        require(bucket.matches(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) && !bucket.contains("..")) { "Bucket 名称无效" }
        require(!forcePathStyle || !url.host.endsWith("aliyuncs.com")) { "阿里云 OSS 需要关闭 Force Path Style" }
        S3Paths.prefix(prefix)
        require(pollSeconds in 5..3600) { "轮询间隔应为 5–3600 秒" }
    }
}

// Deliberately not a data class: generated toString/copy must not expose credentials.
internal class S3Credentials(val accessKey: String, val secretKey: String)

class S3Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("kardleaf_prefs", Context.MODE_PRIVATE)
    private val secure = context.getSharedPreferences("kardleaf_secure_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private fun rootKey(root: String) = "s3_${s3Hash(root)}"

    fun load(root: String): S3Settings = prefs.getString(rootKey(root), null)?.let {
        runCatching { gson.fromJson(it, S3Settings::class.java) }.getOrNull()
    } ?: S3Settings()

    internal fun credentials(root: String): S3Credentials {
        val text = secure.getString(rootKey(root), null) ?: error("请先保存 S3 密钥")
        return try {
            val bytes = Base64.decode(text, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val values = gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), Array<String>::class.java)
            require(values.size == 2 && values.all { it.isNotBlank() })
            S3Credentials(values[0], values[1])
        } catch (_: Exception) {
            error("S3 密钥无法解密，请重新输入并保存")
        }
    }

    fun hasCredentials(root: String): Boolean = secure.contains(rootKey(root))

    internal fun needsRefresh(root: String): Boolean = prefs.getBoolean("${rootKey(root)}_refresh", false)
    internal fun markRefresh(root: String, pending: Boolean) {
        check(prefs.edit().putBoolean("${rootKey(root)}_refresh", pending).commit()) { "无法保存 S3 刷新状态" }
    }
    internal fun dirtyMs(root: String): Long = prefs.getLong("${rootKey(root)}_dirty", 0L)
    internal fun markDirty(root: String) {
        if (load(root).realtime) prefs.edit().putLong("${rootKey(root)}_dirty", System.currentTimeMillis()).apply()
    }

    fun save(root: String, settings: S3Settings, accessKey: String, secretKey: String) {
        require(root.isNotEmpty()) { "请先选择笔记库" }
        settings.validate()
        if (accessKey.isNotEmpty() || secretKey.isNotEmpty()) {
            require(accessKey.isNotBlank() && secretKey.isNotBlank()) { "请同时输入 Access Key ID 和 Secret Access Key" }
            val encrypted = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                cipher.iv + cipher.doFinal(gson.toJson(arrayOf(accessKey.trim(), secretKey.trim())).toByteArray(Charsets.UTF_8))
            } catch (_: Exception) {
                error("S3 密钥安全保存失败")
            }
            check(secure.edit().putString(rootKey(root), Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "S3 密钥保存失败" }
        }
        credentials(root)
        check(prefs.edit().putString(rootKey(root), gson.toJson(settings.copy(prefix = S3Paths.prefix(settings.prefix)))).commit()) { "S3 设置保存失败" }
    }

    internal fun identity(root: String, settings: S3Settings, credentials: S3Credentials): String = s3Hash(
        gson.toJson(listOf(root, settings.endpoint.toHttpUrlOrNull().toString(), settings.region, settings.bucket,
            S3Paths.prefix(settings.prefix), settings.forcePathStyle.toString(), settings.syncUnderscore.toString(),
            s3Hash(credentials.accessKey), "s3-files-v1")),
    )

    fun logs(root: String): String = prefs.getString("${rootKey(root)}_logs", "").orEmpty()
    fun log(root: String, message: String) {
        // Callers supply fixed summaries only; never log exception messages, URLs or headers.
        val line = "${java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())}  $message"
        prefs.edit().putString("${rootKey(root)}_logs", (listOf(line) + logs(root).lines()).take(50).joinToString("\n")).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("kardleaf_s3_credentials", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("kardleaf_s3_credentials", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
