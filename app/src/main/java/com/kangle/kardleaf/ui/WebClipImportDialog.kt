package com.kangle.kardleaf.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val MAX_WEB_CLIP_HTML_BYTES = 8L * 1024L * 1024L
private const val MAX_WEB_CLIP_HTML_CHARS = 8_000_000
private const val MAX_WEB_CLIP_TITLE_CHARS = 160
private const val MAX_WEB_CLIP_IMAGE_COUNT = 120
private const val MIN_COMPLETE_WEB_CLIP_TEXT_CHARS = 180
internal const val WEB_CLIP_LOG_TAG = "KardLeafWebClip"
private const val WEB_CLIP_PREFS_NAME = "kardleaf_web_clip"
private const val PREF_DOWNLOAD_IMAGES_BY_DEFAULT = "download_images_by_default"
private val REMOTE_MARKDOWN_IMAGE_REGEX =
    Regex("""!\[[^]]*]\(\s*<?(https?://[^\s)>]+)>?[^)]*\)""", RegexOption.IGNORE_CASE)
private val WEB_CLIP_SOURCE_REGEX = Regex("""(?m)^>\s*来源：<([^>]+)>""")

private open class WebClipImportException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

private class WebClipBrowserRequiredException(
    val pageUrl: String,
    message: String,
) : WebClipImportException(message)

internal data class WebClipImageProgress(
    val completed: Int,
    val total: Int,
    val saved: Int,
    val failed: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
}

private data class LocalizedArticle(
    val html: String,
    val savedImages: Int,
    val failedImages: Int,
    val remoteImages: Int,
)

internal data class WebClipMarkdownLocalizationResult(
    val markdown: String,
    val totalImages: Int,
    val savedImages: Int,
    val failedImages: Int,
)

private data class ImportedWebClipImage(
    val localReference: String,
    val markdown: String,
)

typealias WebClipImageImporter = suspend (Uri, String) -> String

private class WebPageMarkdownImporter(
    private val context: Context,
) {
    private val webViewUserAgent = WebSettings.getDefaultUserAgent(context)

    private val browserUserAgent =
        webViewUserAgent
            .replace("; wv", "")
            .replace(Regex("\\sVersion/4\\.0"), "")
            .trim()

    private val acceptLanguage =
        Locale.getDefault().toLanguageTag().let { languageTag ->
            if (languageTag.equals("en-US", ignoreCase = true)) {
                "en-US,en;q=0.9"
            } else {
                "$languageTag,en-US;q=0.8,en;q=0.7"
            }
        }

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private val htmlConverter = FlexmarkHtmlConverter.builder().build()

    suspend fun importFromUrl(
        input: String,
        targetFolder: String,
        downloadImages: Boolean,
        importImage: WebClipImageImporter,
        onImageProgress: suspend (WebClipImageProgress?) -> Unit,
    ): KardLeafCustomFeatures.ExternalNoteDraft {
        val url = parseWebUrl(input)
        val html = downloadHtml(url)
        return importHtmlAsDraft(
            pageUrl = url,
            html = html,
            targetFolder = targetFolder,
            downloadImages = downloadImages,
            importImage = importImage,
            allowShortContent = false,
            onImageProgress = onImageProgress,
        )
    }

    suspend fun importHtmlAsDraft(
        pageUrl: HttpUrl,
        html: String,
        targetFolder: String,
        downloadImages: Boolean,
        importImage: WebClipImageImporter,
        allowShortContent: Boolean,
        onImageProgress: suspend (WebClipImageProgress?) -> Unit,
    ): KardLeafCustomFeatures.ExternalNoteDraft =
        withContext(Dispatchers.IO) {
            if (html.isBlank()) {
                throw WebClipImportException("网页内容为空")
            }
            if (html.length > MAX_WEB_CLIP_HTML_CHARS) {
                throw WebClipImportException("网页内容过大，暂不支持导入")
            }

            val article =
                try {
                    Readability4JExtended(pageUrl.toString(), html).parse()
                } catch (error: Exception) {
                    throw WebClipImportException("未能从网页中提取正文", error)
                }

            val articleHtml = article.content?.trim().orEmpty()
            if (articleHtml.isBlank()) {
                if (allowShortContent) {
                    throw WebClipImportException("当前页面未识别到可保存的正文")
                }
                throw WebClipBrowserRequiredException(
                    pageUrl = pageUrl.toString(),
                    message = "快速提取没有识别到正文，请使用网页登录导入",
                )
            }

            val preliminaryMarkdown =
                try {
                    htmlConverter.convert(articleHtml).trim()
                } catch (error: Exception) {
                    throw WebClipImportException("网页正文转换为 Markdown 失败", error)
                }
            if (preliminaryMarkdown.isBlank()) {
                throw WebClipImportException("网页正文转换后为空")
            }
            if (!allowShortContent && isLikelyIncomplete(preliminaryMarkdown, html)) {
                throw WebClipBrowserRequiredException(
                    pageUrl = pageUrl.toString(),
                    message = "快速提取到的正文过短，网站可能需要登录，请使用网页登录导入",
                )
            }

            val localized =
                localizeArticleImages(
                    articleHtml = articleHtml,
                    pageUrl = pageUrl,
                    targetFolder = targetFolder,
                    downloadImages = downloadImages,
                    importImage = importImage,
                    onImageProgress = onImageProgress,
                )

            val markdown =
                try {
                    htmlConverter.convert(localized.html).trim()
                } catch (error: Exception) {
                    throw WebClipImportException("网页正文转换为 Markdown 失败", error)
                }
            if (markdown.isBlank()) {
                throw WebClipImportException("网页正文转换后为空")
            }

            val title =
                normalizeSingleLine(article.title)
                    .ifBlank { pageUrl.host }
                    .take(MAX_WEB_CLIP_TITLE_CHARS)
            val byline = normalizeSingleLine(article.byline)
            val content =
                buildString {
                    append("> 来源：<")
                    append(pageUrl)
                    append('>')
                    if (byline.isNotBlank()) {
                        append("\n> 作者：")
                        append(byline)
                    }
                    if (localized.savedImages > 0 || localized.failedImages > 0 || localized.remoteImages > 0) {
                        append("\n> 图片：")
                        if (localized.savedImages > 0) {
                            append("已本地保存 ")
                            append(localized.savedImages)
                            append(" 张")
                        }
                        val remoteCount = localized.failedImages + localized.remoteImages
                        if (remoteCount > 0) {
                            if (localized.savedImages > 0) append("，")
                            append(remoteCount)
                            append(" 张保留网络链接，可稍后在笔记更多选项中下载")
                        }
                    }
                    append("\n\n---\n\n")
                    append(markdown)
                }

            onImageProgress(null)
            KardLeafCustomFeatures.ExternalNoteDraft(
                title = title,
                content = content,
                folder = targetFolder,
                forceRootFolder = targetFolder.isBlank(),
                sourceType = NoteFormatUtils.SOURCE_TYPE_WEB_ONLINE,
                sourceUrl = pageUrl.toString(),
            )
        }

    private suspend fun downloadHtml(url: HttpUrl): String {
        val requestBuilder =
            Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9," +
                        "image/avif,image/webp,*/*;q=0.8",
                )
                .header("Accept-Language", acceptLanguage)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Upgrade-Insecure-Requests", "1")
        val cookie = readWebViewCookie(url)
        if (cookie.isNotBlank()) {
            requestBuilder.header("Cookie", cookie)
        }

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 401 || response.code == 403) {
                        KardLeafLog.w(
                            WEB_CLIP_LOG_TAG,
                            "direct fetch requires browser host=${url.host} status=${response.code}",
                        )
                        throw WebClipBrowserRequiredException(
                            pageUrl = url.toString(),
                            message = "网站返回 HTTP ${response.code}，请在网页登录后保存当前页面",
                        )
                    }
                    if (!response.isSuccessful) {
                        throw WebClipImportException("网页请求失败（HTTP ${response.code}）")
                    }
                    readLimitedBody(response.body)
                }
            } catch (error: WebClipImportException) {
                throw error
            } catch (error: Exception) {
                throw WebClipImportException("网页下载失败，请检查链接或网络连接", error)
            }
        }
    }

    private fun readLimitedBody(body: okhttp3.ResponseBody?): String {
        val responseBody = body ?: throw WebClipImportException("网页没有可读取的内容")
        val contentLength = responseBody.contentLength()
        if (contentLength > MAX_WEB_CLIP_HTML_BYTES) {
            throw WebClipImportException("网页内容超过 8 MB，暂不支持导入")
        }
        return responseBody.string().also { bodyText ->
            if (bodyText.length > MAX_WEB_CLIP_HTML_CHARS) {
                throw WebClipImportException("网页内容过大，暂不支持导入")
            }
        }
    }

    private suspend fun localizeArticleImages(
        articleHtml: String,
        pageUrl: HttpUrl,
        targetFolder: String,
        downloadImages: Boolean,
        importImage: WebClipImageImporter,
        onImageProgress: suspend (WebClipImageProgress?) -> Unit,
    ): LocalizedArticle {
        val document = Jsoup.parseBodyFragment(articleHtml, pageUrl.toString())
        document.outputSettings().prettyPrint(false)
        document.select("a[href]").forEach { link ->
            link.absUrl("href").toHttpUrlOrNull()?.let { link.attr("href", it.toString()) }
        }

        val imagesByUrl = LinkedHashMap<HttpUrl, MutableList<Element>>()
        document.select("img").forEach { image ->
            val remoteUrl = resolveImageUrl(image, pageUrl) ?: return@forEach
            if (remoteUrl.scheme != "http" && remoteUrl.scheme != "https") return@forEach
            imagesByUrl.getOrPut(remoteUrl) { mutableListOf() }.add(image)
        }

        val entries = imagesByUrl.entries.take(MAX_WEB_CLIP_IMAGE_COUNT)
        if (entries.isEmpty()) {
            return LocalizedArticle(document.body().html(), savedImages = 0, failedImages = 0, remoteImages = 0)
        }

        if (!downloadImages) {
            imagesByUrl.forEach { (remoteUrl, images) ->
                applyArticleImageSource(images, remoteUrl.toString())
            }
            KardLeafLog.i(
                WEB_CLIP_LOG_TAG,
                "image backup skipped host=${pageUrl.host} remote=${imagesByUrl.size}",
            )
            return LocalizedArticle(
                html = document.body().html(),
                savedImages = 0,
                failedImages = 0,
                remoteImages = imagesByUrl.size,
            )
        }

        var saved = 0
        var failed = 0
        onImageProgress(WebClipImageProgress(0, entries.size, saved, failed))
        entries.forEachIndexed { index, entry ->
            val importedImage =
                try {
                    downloadAndImportImage(
                        imageUrl = entry.key,
                        pageUrl = pageUrl,
                        targetFolder = targetFolder,
                        ordinal = index,
                        importImage = importImage,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    KardLeafLog.w(
                        WEB_CLIP_LOG_TAG,
                        "image backup failed host=${entry.key.host} index=${index + 1}/${entries.size}",
                        error,
                    )
                    null
                }

            if (importedImage == null) {
                failed += 1
                applyArticleImageSource(entry.value, entry.key.toString())
            } else {
                saved += 1
                applyArticleImageSource(entry.value, importedImage.localReference)
            }

            onImageProgress(
                WebClipImageProgress(
                    completed = index + 1,
                    total = entries.size,
                    saved = saved,
                    failed = failed,
                ),
            )
        }

        val skipped = imagesByUrl.size - entries.size
        if (skipped > 0) {
            failed += skipped
            imagesByUrl.entries.drop(entries.size).forEach { entry ->
                applyArticleImageSource(entry.value, entry.key.toString())
            }
            KardLeafLog.w(
                WEB_CLIP_LOG_TAG,
                "image backup count limited total=${imagesByUrl.size} limit=$MAX_WEB_CLIP_IMAGE_COUNT",
            )
        }
        KardLeafLog.i(
            WEB_CLIP_LOG_TAG,
            "image backup done host=${pageUrl.host} total=${imagesByUrl.size} saved=$saved failed=$failed",
        )
        return LocalizedArticle(
            html = document.body().html(),
            savedImages = saved,
            failedImages = failed,
            remoteImages = 0,
        )
    }

    private suspend fun downloadAndImportImage(
        imageUrl: HttpUrl,
        pageUrl: HttpUrl,
        targetFolder: String,
        ordinal: Int,
        importImage: WebClipImageImporter,
    ): ImportedWebClipImage? {
        val requestBuilder =
            Request.Builder()
                .url(imageUrl)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Accept-Language", acceptLanguage)
                .header("Referer", pageUrl.toString())
        val cookie = readWebViewCookie(imageUrl)
        if (cookie.isNotBlank()) {
            requestBuilder.header("Cookie", cookie)
        }

        return httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val responseBody = response.body ?: return@use null
            val contentLength = responseBody.contentLength()
            if (contentLength > KardLeafContentLimits.IMAGE_IMPORT_MAX_BYTES) return@use null

            val extension = imageExtension(imageUrl, response.header("Content-Type")) ?: return@use null
            val tempDirectory = File(context.cacheDir, "shared_notes/webclip").apply { mkdirs() }
            val tempFile = File.createTempFile("webclip_${ordinal + 1}_", ".$extension", tempDirectory)
            try {
                val copied = copyBodyWithinLimit(responseBody, tempFile, KardLeafContentLimits.IMAGE_IMPORT_MAX_BYTES)
                if (!copied) return@use null
                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile,
                    )
                val importedMarkdown = importImage(uri, targetFolder).trim()
                val localReference = extractLocalImageReference(importedMarkdown) ?: return@use null
                ImportedWebClipImage(
                    localReference = localReference,
                    markdown = importedMarkdown,
                )
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun applyArticleImageSource(images: List<Element>, source: String) {
        images.forEach { image ->
            image.attr("src", source)
            image.removeAttr("srcset")
            image.removeAttr("data-src")
            image.removeAttr("data-original")
            image.removeAttr("data-lazy-src")
            image.removeAttr("data-srcset")
            var parent = image.parent()
            while (parent != null && parent.tagName() != "picture") {
                parent = parent.parent()
            }
            parent?.select("source")?.remove()
        }
    }

    suspend fun localizeMarkdownImages(
        markdown: String,
        targetFolder: String,
        importImage: WebClipImageImporter,
        onImageProgress: suspend (WebClipImageProgress?) -> Unit,
    ): WebClipMarkdownLocalizationResult = withContext(Dispatchers.IO) {
        val matches = REMOTE_MARKDOWN_IMAGE_REGEX.findAll(markdown).toList()
        val uniqueUrls = LinkedHashMap<String, MutableList<IntRange>>()
        matches.forEach { match ->
            val urlGroup = match.groups[1] ?: return@forEach
            uniqueUrls.getOrPut(urlGroup.value) { mutableListOf() }.add(match.range)
        }
        val entries = uniqueUrls.entries.take(MAX_WEB_CLIP_IMAGE_COUNT)
        if (entries.isEmpty()) {
            return@withContext WebClipMarkdownLocalizationResult(markdown, 0, 0, 0)
        }

        val sourcePage = extractSourcePageUrl(markdown)
        val replacements = mutableListOf<Pair<IntRange, String>>()
        var saved = 0
        var failed = 0
        onImageProgress(WebClipImageProgress(0, entries.size, saved, failed))
        entries.forEachIndexed { index, entry ->
            val imageUrl = entry.key.toHttpUrlOrNull()
            val imported = if (imageUrl == null) {
                null
            } else {
                try {
                    downloadAndImportImage(
                        imageUrl = imageUrl,
                        pageUrl = sourcePage ?: imageUrl,
                        targetFolder = targetFolder,
                        ordinal = index,
                        importImage = importImage,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    KardLeafLog.w(
                        WEB_CLIP_LOG_TAG,
                        "late image backup failed host=${imageUrl.host} index=${index + 1}/${entries.size}",
                        error,
                    )
                    null
                }
            }
            if (imported == null) {
                failed += 1
            } else {
                saved += 1
                entry.value.forEach { range -> replacements += range to imported.markdown }
            }
            onImageProgress(WebClipImageProgress(index + 1, entries.size, saved, failed))
        }

        val skipped = uniqueUrls.size - entries.size
        if (skipped > 0) failed += skipped
        val updated = StringBuilder(markdown)
        replacements.sortedByDescending { it.first.first }.forEach { (range, replacement) ->
            updated.replace(range.first, range.last + 1, replacement)
        }
        onImageProgress(null)
        KardLeafLog.i(
            WEB_CLIP_LOG_TAG,
            "late image backup done total=${uniqueUrls.size} saved=$saved failed=$failed",
        )
        WebClipMarkdownLocalizationResult(
            markdown = updated.toString(),
            totalImages = uniqueUrls.size,
            savedImages = saved,
            failedImages = failed,
        )
    }

    private fun extractSourcePageUrl(markdown: String): HttpUrl? =
        WEB_CLIP_SOURCE_REGEX.find(markdown)?.groupValues?.getOrNull(1)?.toHttpUrlOrNull()

    private fun copyBodyWithinLimit(
        body: okhttp3.ResponseBody,
        target: File,
        maxBytes: Long,
    ): Boolean {
        var total = 0L
        body.byteStream().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return false
                    output.write(buffer, 0, read)
                }
            }
        }
        return total > 0L
    }

    private suspend fun readWebViewCookie(url: HttpUrl): String =
        withContext(Dispatchers.Main.immediate) {
            CookieManager.getInstance().getCookie(url.toString()).orEmpty()
        }

    private fun resolveImageUrl(image: Element, pageUrl: HttpUrl): HttpUrl? {
        val candidate =
            sequenceOf(
                image.attr("data-original"),
                image.attr("data-lazy-src"),
                image.attr("data-src"),
                largestSrcSetCandidate(image.attr("data-srcset")),
                largestSrcSetCandidate(image.attr("srcset")),
                image.attr("src"),
            ).firstOrNull { value -> value.isNotBlank() }?.trim().orEmpty()
        if (candidate.isBlank() || candidate.startsWith("data:") || candidate.startsWith("blob:")) {
            return null
        }
        return candidate.toHttpUrlOrNull() ?: pageUrl.resolve(candidate)
    }

    private fun largestSrcSetCandidate(srcSet: String): String =
        srcSet
            .split(',')
            .map { item -> item.trim().substringBefore(' ').trim() }
            .lastOrNull { it.isNotBlank() }
            .orEmpty()

    private fun imageExtension(url: HttpUrl, contentType: String?): String? {
        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        val fromMime =
            when (mime) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png", "image/apng" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/svg+xml" -> "svg"
                "image/avif" -> "avif"
                else -> null
            }
        if (fromMime != null) return fromMime
        if (mime.isNotBlank() && !mime.startsWith("image/")) return null
        return url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "avif") }
            ?.let { if (it == "jpeg") "jpg" else it }
            ?: "png"
    }

    private fun extractLocalImageReference(markdown: String): String? {
        val trimmed = markdown.trim()
        if (trimmed.startsWith("![[") && trimmed.endsWith("]]")) {
            return trimmed.removePrefix("![[").removeSuffix("]]").trim().takeIf { it.isNotBlank() }
        }
        val normal = Regex("!\\[[^]]*]\\(([^)]+)\\)").find(trimmed)?.groupValues?.getOrNull(1)
        return normal?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
    }

    fun parseWebUrl(input: String): HttpUrl {
        val normalized = input.trim()
        if (normalized.isBlank()) {
            throw WebClipImportException("请输入网页链接")
        }
        val url = normalized.toHttpUrlOrNull()
            ?: throw WebClipImportException("链接格式不正确，请输入完整的 http 或 https 地址")
        if (url.scheme != "http" && url.scheme != "https") {
            throw WebClipImportException("仅支持 http 或 https 网页链接")
        }
        return url
    }

    private fun normalizeSingleLine(value: String?): String =
        value
            .orEmpty()
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()

    private fun isLikelyIncomplete(markdown: String, sourceHtml: String): Boolean {
        val textLength = visibleTextLength(markdown)
        if (textLength < MIN_COMPLETE_WEB_CLIP_TEXT_CHARS) return true
        if (textLength >= 1200) return false
        val lowerHtml = sourceHtml.lowercase(Locale.ROOT)
        return listOf(
            "登录后阅读全文",
            "登录后查看全文",
            "登录后继续阅读",
            "sign in to continue",
            "log in to continue",
            "subscribe to continue",
            "paywall",
        ).any(lowerHtml::contains)
    }

    private fun visibleTextLength(markdown: String): Int =
        markdown
            .replace(Regex("""!\[\[[^]]+]]"""), "")
            .replace(Regex("""!\[[^]]*]\([^)]*\)"""), "")
            .replace(Regex("""[>#*_`~\[\]()-]"""), "")
            .count { !it.isWhitespace() }
}

@Composable
fun WebClipImportDialog(
    onDismiss: () -> Unit,
    onImported: (KardLeafCustomFeatures.ExternalNoteDraft) -> Unit,
    targetFolder: String,
    importImage: WebClipImageImporter,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var downloadImages by rememberSaveable {
        mutableStateOf(webClipDownloadImagesByDefault(context))
    }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var imageProgress by remember { mutableStateOf<WebClipImageProgress?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var browserInitialUrl by remember { mutableStateOf("") }
    var browserReason by remember { mutableStateOf<String?>(null) }
    val importer = remember(context) { WebPageMarkdownImporter(context) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun updateImageProgress(progress: WebClipImageProgress?) {
        withContext(Dispatchers.Main.immediate) {
            imageProgress = progress
        }
    }

    if (showBrowser) {
        WebClipBrowserDialog(
            initialUrl = browserInitialUrl,
            reason = browserReason,
            importer = importer,
            targetFolder = targetFolder,
            downloadImages = downloadImages,
            onDownloadImagesChanged = { enabled ->
                downloadImages = enabled
                saveWebClipDownloadImagesDefault(context, enabled)
            },
            importImage = importImage,
            onDismiss = {
                if (!isImporting) {
                    showBrowser = false
                    imageProgress = null
                }
            },
            onImported = onImported,
        )
        return
    }

    AlertDialog(
        onDismissRequest = {
            if (!isImporting) onDismiss()
        },
        title = { Text("在线阅读网页") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "提取网页正文后会直接在笔记 Preview 中只读显示；此时不下载图片，需要编辑时再离线保存。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("离线时下载网页图片")
                        Text(
                            text = if (downloadImages) {
                                "点击“离线并编辑”时保存到本地图片目录"
                            } else {
                                "离线时保留网络图片链接"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = downloadImages,
                        enabled = !isImporting,
                        onCheckedChange = { enabled ->
                            downloadImages = enabled
                            saveWebClipDownloadImagesDefault(context, enabled)
                        },
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("网页链接") },
                    placeholder = { Text("https://example.com/article") },
                    singleLine = true,
                    enabled = !isImporting,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { message ->
                        {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                WebClipImageProgressContent(imageProgress)
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !isImporting,
                onClick = {
                    isImporting = true
                    errorMessage = null
                    imageProgress = null
                    coroutineScope.launch {
                        try {
                            val draft =
                                importer.importFromUrl(
                                    input = url,
                                    targetFolder = targetFolder,
                                    downloadImages = false,
                                    importImage = importImage,
                                    onImageProgress = ::updateImageProgress,
                                )
                            onImported(draft)
                        } catch (error: WebClipBrowserRequiredException) {
                            browserInitialUrl = error.pageUrl
                            browserReason = error.message
                            showBrowser = true
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "网页导入失败"
                        } finally {
                            isImporting = false
                            imageProgress = null
                        }
                    }
                },
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("在线阅读")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = url.isNotBlank() && !isImporting,
                    onClick = {
                        try {
                            browserInitialUrl = importer.parseWebUrl(url).toString()
                            browserReason = "请在网页中完成登录并打开要保存的文章"
                            showBrowser = true
                            errorMessage = null
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "链接格式不正确"
                        }
                    },
                ) {
                    Text("网页登录")
                }
                TextButton(
                    enabled = !isImporting,
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
            }
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebClipBrowserDialog(
    initialUrl: String,
    reason: String?,
    importer: WebPageMarkdownImporter,
    targetFolder: String,
    downloadImages: Boolean,
    onDownloadImagesChanged: (Boolean) -> Unit,
    importImage: WebClipImageImporter,
    onDismiss: () -> Unit,
    onImported: (KardLeafCustomFeatures.ExternalNoteDraft) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var currentUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var pageProgress by remember { mutableStateOf(0) }
    var isPageLoading by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var imageProgress by remember { mutableStateOf<WebClipImageProgress?>(null) }

    fun navigate() {
        try {
            val parsed = importer.parseWebUrl(address)
            errorMessage = null
            webView?.loadUrl(parsed.toString())
        } catch (error: Exception) {
            errorMessage = error.message ?: "链接格式不正确"
        }
    }

    suspend fun updateImageProgress(progress: WebClipImageProgress?) {
        withContext(Dispatchers.Main.immediate) {
            imageProgress = progress
        }
    }

    fun saveCurrentPage() {
        val view = webView ?: return
        val parsedUrl = currentUrl.toHttpUrlOrNull()
        if (parsedUrl == null) {
            errorMessage = "当前页面不是可保存的 http/https 网页"
            return
        }
        isImporting = true
        errorMessage = null
        imageProgress = null
        CookieManager.getInstance().flush()
        KardLeafLog.i(
            WEB_CLIP_LOG_TAG,
            "browser save start host=${currentUrl.webClipHostForLog()}",
        )
        view.evaluateJavascript(
            """
            (() => {
              document.querySelectorAll('img').forEach((img) => {
                if (img.currentSrc) img.setAttribute('src', img.currentSrc);
              });
              return document.documentElement.outerHTML;
            })()
            """.trimIndent(),
        ) { encodedHtml ->
            val html = decodeJavascriptString(encodedHtml)
            if (html.isBlank()) {
                isImporting = false
                errorMessage = "未读取到当前网页内容"
                return@evaluateJavascript
            }
            coroutineScope.launch {
                try {
                    val draft =
                        importer.importHtmlAsDraft(
                            pageUrl = parsedUrl,
                            html = html,
                            targetFolder = targetFolder,
                            downloadImages = false,
                            importImage = importImage,
                            allowShortContent = true,
                            onImageProgress = ::updateImageProgress,
                        )
                    onImported(draft)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage = error.message ?: "当前网页保存失败"
                } finally {
                    isImporting = false
                    imageProgress = null
                }
            }
        }
    }

    BackHandler(enabled = true) {
        if (isImporting) return@BackHandler
        val view = webView
        if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isImporting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = !isImporting,
                        onClick = {
                            val view = webView
                            if (view?.canGoBack() == true) view.goBack() else onDismiss()
                        },
                    ) {
                        Text("返回")
                    }
                    TextButton(
                        enabled = !isImporting && webView?.canGoForward() == true,
                        onClick = { webView?.goForward() },
                    ) {
                        Text("前进")
                    }
                    TextButton(
                        enabled = !isImporting,
                        onClick = { webView?.reload() },
                    ) {
                        Text("刷新")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = !isImporting && !isPageLoading,
                        onClick = ::saveCurrentPage,
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("在线阅读")
                        }
                    }
                    TextButton(
                        enabled = !isImporting,
                        onClick = onDismiss,
                    ) {
                        Text("关闭")
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        errorMessage = null
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    singleLine = true,
                    label = { Text(pageTitle.ifBlank { "网页地址" }) },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions = KeyboardActions(onGo = { navigate() }),
                    trailingIcon = {
                        TextButton(onClick = { navigate() }) {
                            Text("打开")
                        }
                    },
                )

                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = (pageProgress.coerceIn(0, 100) / 100f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                val browserMessage = errorMessage ?: reason
                if (!browserMessage.isNullOrBlank()) {
                    Text(
                        text = browserMessage,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = if (errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                WebClipImageProgressContent(imageProgress)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "离线时下载网页图片",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(
                        checked = downloadImages,
                        enabled = !isImporting,
                        onCheckedChange = onDownloadImagesChanged,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { webContext ->
                            WebView(webContext).apply webViewApply@ {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadsImagesAutomatically = true
                                    blockNetworkImage = false
                                    userAgentString = WebSettings.getDefaultUserAgent(webContext)
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(false)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    settings.safeBrowsingEnabled = true
                                }
                                CookieManager.getInstance().apply {
                                    setAcceptCookie(true)
                                    setAcceptThirdPartyCookies(this@webViewApply, true)
                                }
                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            pageProgress = newProgress
                                            isPageLoading = newProgress < 100
                                        }
                                    }
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView,
                                            url: String?,
                                            favicon: Bitmap?,
                                        ) {
                                            super.onPageStarted(view, url, favicon)
                                            isPageLoading = true
                                            pageProgress = 0
                                            errorMessage = null
                                            url?.let {
                                                currentUrl = it
                                                address = it
                                            }
                                        }

                                        override fun onPageFinished(view: WebView, url: String?) {
                                            super.onPageFinished(view, url)
                                            isPageLoading = false
                                            pageProgress = 100
                                            pageTitle = view.title.orEmpty()
                                            url?.let {
                                                currentUrl = it
                                                address = it
                                            }
                                            CookieManager.getInstance().flush()
                                            KardLeafLog.d(
                                                WEB_CLIP_LOG_TAG,
                                                "browser page finished host=${url.webClipHostForLog()}",
                                            )
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: WebResourceError,
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request.isForMainFrame) {
                                                isPageLoading = false
                                                errorMessage =
                                                    "网页加载失败（${error.errorCode}：${error.description}）"
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            val scheme = request.url.scheme.orEmpty().lowercase(Locale.ROOT)
                                            if (scheme == "http" || scheme == "https") return false
                                            errorMessage = "已阻止打开外部应用链接：$scheme"
                                            return true
                                        }
                                    }
                                webView = this
                                loadUrl(initialUrl)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun WebClipImageProgressContent(progress: WebClipImageProgress?) {
    if (progress == null) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = progress.fraction,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text =
                "正在下载网页图片 ${progress.completed}/${progress.total}" +
                    "（成功 ${progress.saved}，失败 ${progress.failed}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal suspend fun localizeRemoteMarkdownImages(
    context: Context,
    markdown: String,
    targetFolder: String,
    importImage: WebClipImageImporter,
    onImageProgress: suspend (WebClipImageProgress?) -> Unit,
): WebClipMarkdownLocalizationResult =
    WebPageMarkdownImporter(context).localizeMarkdownImages(
        markdown = markdown,
        targetFolder = targetFolder,
        importImage = importImage,
        onImageProgress = onImageProgress,
    )

internal fun webClipDownloadImagesByDefault(context: Context): Boolean =
    context.getSharedPreferences(WEB_CLIP_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(PREF_DOWNLOAD_IMAGES_BY_DEFAULT, true)

private fun saveWebClipDownloadImagesDefault(context: Context, enabled: Boolean) {
    context.getSharedPreferences(WEB_CLIP_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_DOWNLOAD_IMAGES_BY_DEFAULT, enabled)
        .apply()
}

private fun decodeJavascriptString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return try {
        org.json.JSONTokener(value).nextValue() as? String ?: ""
    } catch (_: Exception) {
        ""
    }
}

private fun String?.webClipHostForLog(): String =
    this?.toHttpUrlOrNull()?.host.orEmpty().ifBlank { "unknown" }
