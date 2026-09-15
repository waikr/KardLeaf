package com.kangle.kardleaf.data.utils

import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Encodes a granted SAF content URI as a signed, same-process HTTPS resource URL for preview WebViews.
 *
 * Image and media bytes remain outside the Markdown/JavaScript bridge. Non-media attachments
 * can only be opened externally after signature validation, not loaded as active WebView content.
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

    /**
     * Media uses the platform content loader so WebView can seek the SAF file itself.
     * Joplin's mobile viewer follows the same principle with file:// resource URLs.
     */
    fun buildMediaUrl(
        sourceUri: Uri,
        mimeType: String,
        lastModified: Long,
        length: Long,
    ): String = sourceUri.buildUpon()
        .appendQueryParameter(MIME_QUERY, mimeType)
        .appendQueryParameter(VERSION_QUERY, "$lastModified-$length")
        .build()
        .toString()

    fun isRequest(uri: Uri): Boolean =
        uri.scheme.equals(SCHEME, ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            uri.pathSegments.firstOrNull() == IMAGE_PATH

    fun isDirectMediaRequest(uri: Uri): Boolean =
        uri.scheme.equals("content", ignoreCase = true) &&
            uri.getQueryParameter(VERSION_QUERY)?.isNotBlank() == true &&
            isMediaMime(mimeType(uri))

    fun decodeDirectMediaUri(uri: Uri): Uri? {
        if (!isDirectMediaRequest(uri)) return null
        return runCatching {
            uri.buildUpon().clearQuery().fragment(null).build()
        }.getOrNull()
    }

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
            ?.takeIf { it.matches(Regex("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+")) }

    fun hasStrongVersion(requestUri: Uri): Boolean =
        requestUri.getQueryParameter(VERSION_QUERY)
            ?.substringBefore('-')
            ?.toLongOrNull()
            ?.let { it > 0L }
            ?: false

    private fun isMediaMime(mimeType: String?): Boolean =
        mimeType?.startsWith("video/", ignoreCase = true) == true ||
            mimeType?.startsWith("audio/", ignoreCase = true) == true

    private fun sign(token: String, version: String, mimeType: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey, "HmacSHA256"))
        return Base64.encodeToString(
            mac.doFinal("$token|$version|$mimeType".toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }
}
