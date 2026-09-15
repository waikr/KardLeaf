package com.kangle.kardleaf.data.sync

import com.kangle.kardleaf.data.repository.prefs.S3Credentials
import com.kangle.kardleaf.data.repository.prefs.S3Settings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.resumeWithException

internal class S3HttpException(
    val status: Int,
    val code: String? = null,
    val path: String? = null,
) : IOException("S3 HTTP $status")

internal class S3Client(
    private val settings: S3Settings,
    private val credentials: S3Credentials,
    private val http: OkHttpClient = sharedHttp,
) {
    private companion object {
        val sharedHttp = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS).writeTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
    private fun url(path: String? = null, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = settings.endpoint.toHttpUrl().newBuilder()
        if (settings.forcePathStyle) builder.addPathSegment(settings.bucket)
        else builder.host("${settings.bucket}.${settings.endpoint.toHttpUrl().host}")
        if (path != null) builder.addEncodedPathSegments(S3Signing.encode(S3Paths.key(settings.prefix, path), keepSlash = true))
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private suspend fun request(method: String, url: HttpUrl, file: File? = null, headers: Map<String, String> = emptyMap(), path: String? = null): Response {
        val payloadHash = if (file == null) s3Hash("") else file.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().s3Hex()
        }
        // Alibaba OSS switches to its Amazon S3-compatible API only when this marker is present.
        // It is deliberately outside SignedHeaders, matching OSS's documented S3 requests.
        val signed = S3Signing.headers(method, url, settings.region, credentials, payloadHash, headers)
        val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .header("x-oss-s3-compat", "true")
            .method(method, file?.asRequestBody("application/octet-stream".toMediaType()))
        signed.forEach { (key, value) -> builder.header(key, value) }
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = http.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(IOException("S3 网络连接失败")) }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response) { response.close() } }
            })
        }
        if (!response.isSuccessful) {
            val code = response.code
            val errorCode = response.body?.byteStream()?.use { stream ->
                stream.readBytesBounded(64 * 1024).toString(Charsets.UTF_8)
                    .let { Regex("<Code>\\s*([A-Za-z0-9._-]{1,80})\\s*</Code>", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            }
            response.close()
            throw S3HttpException(code, errorCode, path)
        }
        return response
    }

    suspend fun list(): Map<String, S3FileState> {
        val files = linkedMapOf<String, S3FileState>()
        var token: String? = null
        val seenTokens = hashSetOf<String>()
        do {
            val query = linkedMapOf("list-type" to "2", "max-keys" to "1000", "encoding-type" to "url", "prefix" to S3Paths.prefix(settings.prefix))
            token?.let { query["continuation-token"] = it }
            val page = request("GET", url(query = query)).use { response ->
                val body = response.body ?: error("S3 列表响应为空")
                require(body.contentLength() <= 8 * 1024 * 1024) { "S3 列表响应过大" }
                val bytes = body.byteStream().use { it.readBytesBounded(8 * 1024 * 1024) }
                parseS3Page(bytes, S3Paths.prefix(settings.prefix))
            }
            page.files.forEach { (path, state) ->
                require(!files.containsKey(path)) { "S3 分页包含重复对象，请重试" }
                if (S3Paths.included(path, settings.syncUnderscore)) files[path] = state
            }
            token = page.nextToken
            if (token != null) require(seenTokens.add(token!!)) { "S3 分页游标重复" }
        } while (token != null)
        files.keys.toList().forEach { path ->
            var parent = path.removeSuffix("/").substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                require(!files.containsKey(parent)) { "远端文件与目录同名" }
                files.putIfAbsent("$parent/", S3FileState(0, 0, directory = true))
                parent = parent.substringBeforeLast('/', "")
            }
        }
        return files
    }

    suspend fun head(path: String): S3FileState? = try {
        request("HEAD", url(path), path = path).use { response -> state(response, path.endsWith('/')) }
    } catch (error: S3HttpException) {
        if (error.status == 404) null else throw error
    }

    suspend fun download(path: String, target: File, expected: S3FileState): S3FileState {
        val conditions = if (expected.etag.isEmpty()) emptyMap() else mapOf("If-Match" to expected.etag)
        return request("GET", url(path), headers = conditions, path = path).use { response ->
            val actual = state(response, false)
            require(actual.remoteVersion() == expected.remoteVersion()) { "远端文件已变化，请重新预览" }
            val digest = MessageDigest.getInstance("SHA-256")
            var length = 0L
            (response.body ?: error("S3 下载响应为空")).byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        length += count
                    }
                }
            }
            require(length == expected.size) { "S3 下载长度不完整" }
            actual.copy(hash = digest.digest().s3Hex())
        }
    }

    suspend fun upload(path: String, file: File, modifiedMs: Long, expected: S3FileState?): S3FileState {
        // Fixed-length RequestBody: no aws-chunked encoding, including large binary attachments.
        require(file.length() <= 5L * 1024 * 1024 * 1024) { "文件超过单次 PUT 的 5 GiB 上限" }
        // OSS PutObject rejects If-Match/If-None-Match with HTTP 400 NotImplemented.
        // The manager performs a fresh HeadObject check before this call and keeps a
        // recovery copy, so the PUT itself must remain unconditional for OSS.
        val headers = mapOf("x-amz-meta-mtime" to "${modifiedMs / 1000.0}", "x-amz-meta-ctime" to "${modifiedMs / 1000.0}")
        request("PUT", url(path), file, headers, path).close()
        return head(path) ?: error("上传后对象不存在")
    }

    suspend fun delete(path: String, expected: S3FileState) {
        if (expected.directory && expected.etag.isEmpty()) return // synthesized directory, no object to delete
        // DeleteObject has no OSS conditional request header. verifyRemote() runs
        // immediately before this operation to detect a stale preview.
        request("DELETE", url(path), path = path).close()
        require(head(path) == null) { "远端删除后对象再次变化" }
    }

    private fun state(response: Response, directory: Boolean): S3FileState = S3FileState(
        size = response.header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 } ?: error("S3 缺少文件大小"),
        modifiedMs = parseS3Date(response.header("Last-Modified").orEmpty()),
        etag = response.header("ETag")?.takeIf { it.isNotBlank() } ?: error("S3 缺少 ETag"),
        directory = directory,
    )
}

internal object S3Signing {
    fun encode(value: String, keepSlash: Boolean = false): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt() and 255
            if (c in 65..90 || c in 97..122 || c in 48..57 || c in listOf(45, 46, 95, 126) || (keepSlash && c == 47)) append(c.toChar())
            else append("%%%02X".format(c))
        }
    }

    fun headers(method: String, url: HttpUrl, region: String, credentials: S3Credentials, payload: String,
        extra: Map<String, String> = emptyMap(), now: Date = Date()): Map<String, String> {
        val timestamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)
        val date = timestamp.take(8)
        val headers = sortedMapOf("host" to (url.host + if (url.port != if (url.isHttps) 443 else 80) ":${url.port}" else ""),
            "x-amz-content-sha256" to payload, "x-amz-date" to timestamp)
        extra.forEach { (key, value) -> headers[key.lowercase(Locale.ROOT)] = value.trim().replace(Regex("\\s+"), " ") }
        val query = (0 until url.querySize).map { encode(url.queryParameterName(it)) to encode(url.queryParameterValue(it).orEmpty()) }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).joinToString("&") { "${it.first}=${it.second}" }
        val names = headers.keys.joinToString(";")
        val canonical = listOf(method, url.encodedPath, query, headers.entries.joinToString("") { "${it.key}:${it.value}\n" }, names, payload).joinToString("\n")
        val scope = "$date/$region/s3/aws4_request"
        fun hmac(key: ByteArray, text: String): ByteArray = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(text.toByteArray(Charsets.UTF_8))
        val signingKey = hmac(hmac(hmac(hmac(("AWS4" + credentials.secretKey).toByteArray(Charsets.UTF_8), date), region), "s3"), "aws4_request")
        val signature = hmac(signingKey, "AWS4-HMAC-SHA256\n$timestamp\n$scope\n${s3Hash(canonical)}").s3Hex()
        headers["Authorization"] = "AWS4-HMAC-SHA256 Credential=${credentials.accessKey}/$scope,SignedHeaders=$names,Signature=$signature"
        return headers
    }
}

internal data class S3ListPage(val files: Map<String, S3FileState>, val nextToken: String?)

internal fun parseS3Page(bytes: ByteArray, prefix: String): S3ListPage {
    val xml = bytes.toString(Charsets.UTF_8)
    require(xml.toByteArray(Charsets.UTF_8).contentEquals(bytes) && !xml.contains('\u0000') &&
        !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "S3 XML 不允许 DTD 或实体声明" }
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Android providers do not all implement these features. Reject declarations above on every platform.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }
    val root = factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement
    require(root.localName == "ListBucketResult" || root.nodeName == "ListBucketResult") { "S3 列表格式无效" }
    fun Element.value(name: String) = getElementsByTagNameNS("*", name).item(0)?.textContent.orEmpty()
    val encoded = root.value("EncodingType") == "url"
    val files = linkedMapOf<String, S3FileState>()
    val nodes = root.getElementsByTagNameNS("*", "Contents")
    for (index in 0 until nodes.length) {
        val node = nodes.item(index) as Element
        // OSS returns form-style '+' for spaces in URL-encoded list keys; URLDecoder
        // also decodes %2B back to a literal plus, so both cases round-trip correctly.
        val key = node.value("Key").let { if (encoded) URLDecoder.decode(it, "UTF-8") else it }
        require(key.startsWith(prefix)) { "S3 返回了 Prefix 之外的对象" }
        val path = key.removePrefix(prefix)
        if (path.isEmpty()) continue
        S3Paths.validate(path)
        val size = node.value("Size").toLongOrNull()?.takeIf { it >= 0 } ?: error("S3 列表缺少大小")
        require(!path.endsWith('/') || size == 0L) { "目录标记必须为零字节" }
        val etag = node.value("ETag").takeIf { it.isNotEmpty() } ?: error("S3 列表缺少 ETag")
        require(files.put(path, S3FileState(size, parseS3Date(node.value("LastModified")), etag = etag, directory = path.endsWith('/'))) == null) { "S3 列表对象重复" }
    }
    val truncated = root.value("IsTruncated")
    require(truncated == "true" || truncated == "false") { "S3 列表缺少分页完成标记" }
    return S3ListPage(files, if (truncated == "true") root.value("NextContinuationToken").takeIf { it.isNotEmpty() } ?: error("S3 分页未完成但无游标") else null)
}

internal fun parseS3Date(value: String): Long {
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "EEE, dd MMM yyyy HH:mm:ss zzz")) {
        val date = runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(value) }.getOrNull()
        if (date != null) return date.time
    }
    error("S3 修改时间无效")
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "响应超出大小限制" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
