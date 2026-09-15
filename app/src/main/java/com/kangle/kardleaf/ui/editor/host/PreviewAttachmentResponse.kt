package com.kangle.kardleaf.ui.editor.host

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.LocalPreviewImageResource
import com.kangle.kardleaf.data.utils.previewByteRange
import java.io.ByteArrayInputStream
import java.io.FilterInputStream

/** Streams signed SAF media without putting video bytes into the JS bridge or heap. */
internal const val PREVIEW_MEDIA_TRACE_TAG = "KardLeafPreviewMedia"

internal fun isPreviewMediaRequest(request: Uri): Boolean {
    if (!LocalPreviewImageResource.isRequest(request) && !LocalPreviewImageResource.isDirectMediaRequest(request)) return false
    val mime = LocalPreviewImageResource.mimeType(request) ?: return false
    return mime.startsWith("video/") || mime.startsWith("audio/")
}

internal fun previewMediaRequestSummary(request: Uri): String =
    "scheme=${request.scheme ?: "<none>"} " +
        "mime=${LocalPreviewImageResource.mimeType(request) ?: "<invalid>"} " +
        "pathSegments=${request.pathSegments.size} " +
        "version=${request.getQueryParameter("v")?.isNotBlank() == true} " +
        "signature=${request.getQueryParameter("s")?.isNotBlank() == true}"

internal fun previewAttachmentResponse(context: Context, request: Uri, rangeHeader: String?): WebResourceResponse? {
    if (!LocalPreviewImageResource.isRequest(request)) return null
    val summary = previewMediaRequestSummary(request)
    val mime = LocalPreviewImageResource.mimeType(request) ?: run {
        KardLeafLog.w(PREVIEW_MEDIA_TRACE_TAG, "response reject reason=invalid_mime $summary")
        return null
    }
    if (mime.startsWith("image/")) return null
    fun error(code: Int, reason: String, headers: Map<String, String> = emptyMap()): WebResourceResponse {
        KardLeafLog.w(
            PREVIEW_MEDIA_TRACE_TAG,
            "response status=$code reason=$reason $summary range=${rangeHeader ?: "none"}",
        )
        return WebResourceResponse("text/plain", "utf-8", code, reason, headers, ByteArrayInputStream(ByteArray(0)))
    }
    if (!mime.startsWith("video/") && !mime.startsWith("audio/")) return error(403, "Forbidden")
    KardLeafLog.d(
        PREVIEW_MEDIA_TRACE_TAG,
        "response start $summary range=${rangeHeader ?: "none"}",
    )
    val source = LocalPreviewImageResource.decodeSourceUri(request) ?: return error(403, "Forbidden")
    return try {
        val descriptor = context.contentResolver.openAssetFileDescriptor(source, "r") ?: return error(404, "Not Found")
        val assetLength = descriptor.length
        val statSize = descriptor.parcelFileDescriptor.statSize
        val length = assetLength.takeIf { it >= 0 } ?: statSize
        KardLeafLog.d(
            PREVIEW_MEDIA_TRACE_TAG,
            "source opened $summary assetLength=$assetLength statSize=$statSize effectiveLength=$length",
        )
        val headers = mutableMapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store", "Content-Type" to mime)
        val range = if (rangeHeader != null && length >= 0) previewByteRange(rangeHeader, length) else null
        if (rangeHeader != null && length >= 0 && range == null) {
            descriptor.close()
            return error(416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$length"))
        }
        val input = try { descriptor.createInputStream() } catch (error: Exception) { descriptor.close(); throw error }
        try {
            var skip = range?.first ?: 0L
            while (skip > 0) {
                val skipped = input.skip(skip)
                if (skipped > 0) skip -= skipped else {
                    check(input.read() != -1) { "Unexpected end of attachment" }
                    skip--
                }
            }
            if (length >= 0) {
                headers["Accept-Ranges"] = "bytes"
                headers["Content-Length"] = (range?.let { it.last - it.first + 1 } ?: length).toString()
            }
            if (range != null) headers["Content-Range"] = "bytes ${range.first}-${range.last}/$length"
            val statusCode = if (range == null) 200 else 206
            KardLeafLog.d(
                PREVIEW_MEDIA_TRACE_TAG,
                "response ready status=$statusCode $summary range=${range ?: "none"} " +
                    "contentLength=${headers["Content-Length"] ?: "unknown"}",
            )
            val stream = object : FilterInputStream(input) {
                var remaining = range?.let { it.last - it.first + 1 } ?: if (length >= 0) length else Long.MAX_VALUE
                var totalRead = 0L
                var firstReadLogged = false
                var eofLogged = false

                private fun traceRead(count: Int) {
                    if (count > 0) {
                        if (!firstReadLogged) {
                            firstReadLogged = true
                            KardLeafLog.d(PREVIEW_MEDIA_TRACE_TAG, "stream firstRead bytes=$count $summary")
                        }
                        totalRead += count
                    } else if (count < 0 && !eofLogged) {
                        eofLogged = true
                        KardLeafLog.d(PREVIEW_MEDIA_TRACE_TAG, "stream eof totalRead=$totalRead $summary")
                    }
                }

                override fun read(): Int {
                    if (remaining == 0L) {
                        traceRead(-1)
                        return -1
                    }
                    return super.read().also {
                        if (it >= 0) remaining--
                        traceRead(if (it >= 0) 1 else -1)
                    }
                }
                override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                    if (count == 0) return 0
                    if (remaining == 0L) {
                        traceRead(-1)
                        return -1
                    }
                    return `in`.read(buffer, offset, count.toLong().coerceAtMost(remaining).toInt())
                        .also {
                            if (it > 0) remaining -= it
                            traceRead(it)
                        }
                }
            }
            WebResourceResponse(mime, null, statusCode, if (range == null) "OK" else "Partial Content", headers, stream)
        } catch (failure: Exception) {
            input.close()
            throw failure
        }
    } catch (failure: Exception) {
        KardLeafLog.w(
            PREVIEW_MEDIA_TRACE_TAG,
            "response failed $summary range=${rangeHeader ?: "none"}",
            failure,
        )
        error(404, "Not Found")
    }
}
