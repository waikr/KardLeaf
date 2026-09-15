package com.kangle.kardleaf.data.sync

import com.kangle.kardleaf.data.repository.prefs.S3Credentials
import com.kangle.kardleaf.data.repository.prefs.S3Settings
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Date

class S3ClientTest {
    private fun client(server: MockWebServer) = S3Client(S3Settings(endpoint = server.url("/").toString(), region = "test", bucket = "test-bucket", forcePathStyle = true), S3Credentials("fake-id", "fake-secret"))
    private fun page(content: String = "", more: String = "false", token: String = "") =
        "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><EncodingType>url</EncodingType><IsTruncated>$more</IsTruncated>$token$content</ListBucketResult>"
    private fun entry(path: String, size: Int = 3) = "<Contents><Key>$path</Key><Size>$size</Size><ETag>&quot;not-an-md5-2&quot;</ETag><LastModified>2026-09-15T00:00:00.000Z</LastModified></Contents>"

    @Test fun awsOfficialSignatureVector() {
        // AWS S3 developer guide, GET Object example; public example credentials, never real keys.
        val headers = S3Signing.headers("GET", "https://examplebucket.s3.amazonaws.com/test.txt".toHttpUrl(), "us-east-1",
            S3Credentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"), s3Hash(""), mapOf("Range" to "bytes=0-9"), Date(1369353600000L))
        assertTrue(headers.getValue("Authorization").endsWith("Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"))
        assertEquals("%E7%AC%94%E8%AE%B0/a%20%2B%25%23%3F.md", S3Signing.encode("笔记/a +%#?.md", true))
    }

    @Test fun paginatedListingAndHiddenFileRules() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(page((0 until 1000).joinToString("") { entry("file$it.md") }, "true", "<NextContinuationToken>a+b/%=</NextContinuationToken>")))
            server.enqueue(MockResponse().setBody(page(entry("%E7%AC%94%E8%AE%B0/a%20%2B%25.md") + entry("%E6%9C%AA%E5%91%BD%E5%90%8D+2.md") + entry(".KardLeaf/note.bak") + entry(".KardLeaf/history/note.json"))))
            val result = client(server).list()
            assertEquals(1003, result.count { !it.value.directory })
            assertTrue(result.containsKey("笔记/a +%.md"))
            assertTrue(result.containsKey("未命名 2.md"))
            assertTrue(result.containsKey("笔记/"))
            assertFalse(result.containsKey(".KardLeaf/note.bak"))
            server.takeRequest()
            assertEquals("a+b/%=", server.takeRequest().requestUrl!!.queryParameter("continuation-token"))
        }
    }

    @Test fun incompleteOrUnsafeListsNeverLookEmpty() {
        listOf(page(more = "true"), "<Error><Code>AccessDenied</Code></Error>", "<ListBucketResult/>",
            page(entry("../escape")), "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///not-read'>]><ListBucketResult>&e;</ListBucketResult>").forEach {
            assertThrows(Exception::class.java) { parseS3Page(it.toByteArray(), "") }
        }
    }

    @Test fun pageFailureIsNotReturnedAsPartialSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(page(entry("note.md"), "true", "<NextContinuationToken>next</NextContinuationToken>")))
            server.enqueue(MockResponse().setResponseCode(403).setBody("sensitive server detail must not escape"))
            try { client(server).list(); fail("must fail") } catch (error: S3HttpException) {
                assertEquals(403, error.status)
                assertFalse(error.message.orEmpty().contains("sensitive"))
            }
        }
    }

    @Test fun serverErrorCodeIsParsedWithoutReturningBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("<Error><Code>InvalidArgument</Code><Message>secret detail</Message></Error>"))
            try { client(server).list(); fail("must fail") } catch (error: S3HttpException) {
                assertEquals(400, error.status)
                assertEquals("InvalidArgument", error.code)
                assertFalse(error.message.orEmpty().contains("secret detail"))
            }
        }
    }

    @Test fun binaryDownloadAndOssCompatibleFixedLengthUpload() = runBlocking {
        val bytes = ByteArray(256 * 1024) { it.toByte() }
        val remote = S3FileState(bytes.size.toLong(), parseS3Date("2026-09-15T00:00:00Z"), etag = "\"opaque\"")
        val target = File.createTempFile("s3-test", ".bin")
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setHeader("ETag", remote.etag).setHeader("Last-Modified", "Tue, 15 Sep 2026 00:00:00 GMT").setBody(Buffer().write(bytes)))
                val c = client(server)
                val downloaded = c.download("目录/a +%.bin", target, remote)
                assertArrayEquals(bytes, target.readBytes())
                assertEquals(remote.etag, server.takeRequest().getHeader("If-Match"))
                server.enqueue(MockResponse().setResponseCode(200))
                server.enqueue(MockResponse().setHeader("ETag", remote.etag).setHeader("Last-Modified", "Tue, 15 Sep 2026 00:00:00 GMT").setHeader("Content-Length", bytes.size))
                c.upload("目录/a +%.bin", target, downloaded.modifiedMs, remote)
                val request = server.takeRequest()
                assertEquals("true", request.getHeader("x-oss-s3-compat"))
                assertEquals("PUT", request.method)
                assertEquals(bytes.size.toString(), request.getHeader("Content-Length"))
                assertNull(request.getHeader("Transfer-Encoding"))
                assertNull(request.getHeader("If-Match"))
                assertNull(request.getHeader("If-None-Match"))
                assertArrayEquals(bytes, request.body.readByteArray())
                assertTrue(request.path!!.contains("a%20%2B%25.bin"))
                server.takeRequest()
                Unit
            }
        } finally { target.delete() }
    }
}
