package com.kangle.kardleaf

import com.kangle.kardleaf.data.repository.PrefsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val publishedDate: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

sealed class AppUpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : AppUpdateCheckResult()
    data class UpToDate(val latestTag: String) : AppUpdateCheckResult()
    data class Failed(val message: String) : AppUpdateCheckResult()
}

object AppUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/waikr/KardLeaf/releases/latest"
    private const val MAX_RELEASE_NOTES_CHARS = 4_000

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    suspend fun checkLatestRelease(prefsManager: PrefsManager): AppUpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                requestLatestRelease(prefsManager)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                AppUpdateCheckResult.Failed(error.message ?: "无法连接 GitHub")
            }
        }

    private fun requestLatestRelease(prefsManager: PrefsManager): AppUpdateCheckResult {
        val cachedEtag = prefsManager.getUpdateCheckEtag()
        executeRequest(cachedEtag).use { response ->
            if (response.code == 304) {
                val cachedBody = prefsManager.getUpdateCheckCache()
                if (!cachedBody.isNullOrBlank()) return parseResult(cachedBody)
            } else {
                if (response.code == 404) {
                    return AppUpdateCheckResult.Failed("GitHub 尚未发布正式 Release")
                }
                if (!response.isSuccessful) {
                    return AppUpdateCheckResult.Failed("GitHub 请求失败（HTTP ${response.code}）")
                }
                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return AppUpdateCheckResult.Failed("GitHub 返回了空的版本信息")
                }
                prefsManager.saveUpdateCheckCache(response.header("ETag"), responseBody)
                return parseResult(responseBody)
            }
        }

        executeRequest(etag = null).use { response ->
            if (!response.isSuccessful) {
                return AppUpdateCheckResult.Failed("GitHub 请求失败（HTTP ${response.code}）")
            }
            val responseBody = response.body?.string().orEmpty()
            if (responseBody.isBlank()) {
                return AppUpdateCheckResult.Failed("GitHub 返回了空的版本信息")
            }
            prefsManager.saveUpdateCheckCache(response.header("ETag"), responseBody)
            return parseResult(responseBody)
        }
    }

    private fun executeRequest(etag: String?) =
        client.newCall(
            Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "KardLeaf/${BuildConfig.VERSION_NAME}")
                .apply {
                    if (!etag.isNullOrBlank()) header("If-None-Match", etag)
                }
                .build(),
        ).execute()

    private fun parseResult(responseBody: String): AppUpdateCheckResult {
        val release = parseRelease(responseBody)
            ?: return AppUpdateCheckResult.Failed("无法识别 GitHub Release 版本信息")
        return if (isNewerVersion(release.tagName, BuildConfig.VERSION_NAME)) {
            AppUpdateCheckResult.UpdateAvailable(release)
        } else {
            AppUpdateCheckResult.UpToDate(release.tagName)
        }
    }

    private fun parseRelease(responseBody: String): AppReleaseInfo? {
        val json = JSONObject(responseBody)
        val tagName = json.optString("tag_name").trim()
        if (tagName.isBlank() || parseVersionParts(tagName) == null) return null

        val assets = json.optJSONArray("assets")
        var selectedApkUrl: String? = null
        var selectedApkScore = Int.MIN_VALUE
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name").lowercase(Locale.ROOT)
                if (!name.endsWith(".apk")) continue
                val score =
                    (if ("release" in name) 2 else 0) +
                        (if ("debug" !in name && "dev" !in name) 1 else 0)
                val url = asset.optString("browser_download_url").trim()
                if (url.isNotBlank() && score > selectedApkScore) {
                    selectedApkScore = score
                    selectedApkUrl = url
                }
            }
        }

        val releasePageUrl = json.optString("html_url").trim()
        val downloadUrl = selectedApkUrl ?: releasePageUrl
        if (downloadUrl.isBlank()) return null

        return AppReleaseInfo(
            tagName = tagName,
            publishedDate = json.optString("published_at").take(10),
            releaseNotes = json.optString("body").trim().take(MAX_RELEASE_NOTES_CHARS),
            downloadUrl = downloadUrl,
        )
    }

    internal fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        val latestParts = parseVersionParts(latestVersion) ?: return false
        val currentParts = parseVersionParts(currentVersion) ?: return false
        val size = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until size) {
            val latest = latestParts.getOrElse(index) { 0 }
            val current = currentParts.getOrElse(index) { 0 }
            if (latest != current) return latest > current
        }
        return false
    }

    private fun parseVersionParts(value: String): List<Int>? {
        val match = Regex("(?i)^\\s*v?(\\d+(?:\\.\\d+){1,3})").find(value) ?: return null
        return match.groupValues[1].split('.').mapNotNull { it.toIntOrNull() }
            .takeIf { it.size >= 2 }
    }
}
