package com.kangle.kardleaf.data.utils

import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Encodes a granted SAF content URI as a same-process HTTPS resource URL for preview WebViews.
 *
 * The URL contains only the URI token and source version metadata. The image bytes remain outside
 * the Markdown/JavaScript bridge and are streamed by EditorPreviewWebView.shouldInterceptRequest.
 */
internal object LocalPreviewImageResource {
    private const val SCHEME = "https"
    private const val HOST = "kardleaf-image.invalid"
    private const val IMAGE_PATH = "image"
    private const val MIME_QUERY = "mime"
    private const val VERSION_QUERY = "v"
    private const val SIGNATURE_QUERY = "s"
    private val signingKey = ByteArray(32).also(SecureRandom()::nextBytes)

    fun buildUrl(
        sourceUri: Uri,
        mimeType: String,
        lastModified: Long,
        length: Long,
    ): String {
        val token = Base64.encodeToString(
            sourceUri.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val version = "$lastModified-$length"
        val signature = sign(token, version, mimeType)
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(IMAGE_PATH)
            .appendPath(token)
            .appendQueryParameter(VERSION_QUERY, version)
            .appendQueryParameter(MIME_QUERY, mimeType)
            .appendQueryParameter(SIGNATURE_QUERY, signature)
            .build()
            .toString()
    }

    fun isRequest(uri: Uri): Boolean =
        uri.scheme.equals(SCHEME, ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            uri.pathSegments.firstOrNull() == IMAGE_PATH

    fun decodeSourceUri(requestUri: Uri): Uri? {
        if (!isRequest(requestUri)) return null
        val token = requestUri.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val version = requestUri.getQueryParameter(VERSION_QUERY)?.takeIf { it.isNotBlank() } ?: return null
        val mimeType = mimeType(requestUri) ?: return null
        val signature = requestUri.getQueryParameter(SIGNATURE_QUERY)?.takeIf { it.isNotBlank() } ?: return null
        if (!MessageDigest.isEqual(signature.toByteArray(Charsets.UTF_8), sign(token, version, mimeType).toByteArray(Charsets.UTF_8))) {
            return null
        }
        val decoded = runCatching {
            Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                .toString(Charsets.UTF_8)
        }.getOrNull() ?: return null
        return runCatching { Uri.parse(decoded) }
            .getOrNull()
            ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
    }

    fun mimeType(requestUri: Uri): String? =
        requestUri.getQueryParameter(MIME_QUERY)
            ?.trim()
            ?.takeIf { it.startsWith("image/", ignoreCase = true) }

    fun hasStrongVersion(requestUri: Uri): Boolean =
        requestUri.getQueryParameter(VERSION_QUERY)
            ?.substringBefore('-')
            ?.toLongOrNull()
            ?.let { it > 0L }
            ?: false

    private fun sign(token: String, version: String, mimeType: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey, "HmacSHA256"))
        return Base64.encodeToString(
            mac.doFinal("$token|$version|$mimeType".toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }
}
