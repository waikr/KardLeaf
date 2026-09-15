package com.kangle.kardleaf.data.sync

import android.content.Context
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.prefs.S3Preferences
import com.kangle.kardleaf.data.repository.prefs.S3Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class S3SyncPreview internal constructor(
    val items: List<S3SyncItem>,
    internal val identity: String,
    internal val local: Map<String, S3FileState>,
    internal val remote: Map<String, S3FileState>,
) {
    val changes: List<S3SyncItem> get() = items.filter { it.action != S3SyncAction.MATCH }
    val hasDeletes: Boolean get() = changes.any { it.action == S3SyncAction.DELETE_LOCAL || it.action == S3SyncAction.DELETE_REMOTE }
}

data class S3SyncResult(val applied: Int, val pending: Int) {
    val summary: String get() = "完成 $applied 项，待处理 $pending 项"
}

class S3CloudSyncManager(
    context: Context,
    private val prefs: PrefsManager,
    private val localAccess: suspend (applying: Boolean, block: suspend () -> Unit) -> Unit,
    private val refresh: suspend () -> Unit,
) {
    private val context = context.applicationContext
    private val preferences = S3Preferences(context)
    private data class Session(val root: String, val settings: S3Settings, val identity: String,
        val client: S3Client, val vault: S3VaultFiles, val store: S3SyncStateStore)

    private fun session(): Session {
        val root = prefs.getRootUri() ?: error("请先选择笔记库")
        val settings = preferences.load(root)
        settings.validate()
        val credentials = preferences.credentials(root)
        val identity = preferences.identity(root, settings, credentials)
        return Session(root, settings, identity, S3Client(settings, credentials), S3VaultFiles(context, root, settings.syncUnderscore), S3SyncStateStore(context, identity))
    }

    private fun checkSession(session: Session) {
        check(prefs.getRootUri() == session.root && preferences.load(session.root) == session.settings &&
            preferences.identity(session.root, session.settings, preferences.credentials(session.root)) == session.identity) {
            "仓库或 S3 配置已变化，请重新预览"
        }
    }

    suspend fun testConnection(): Int = withContext(Dispatchers.IO) { session().client.list().count { !it.value.directory } }

    suspend fun preview(): S3SyncPreview = mutex.withLock { withContext(Dispatchers.IO) {
        val session = session()
        flushRefresh(session)
        buildPreview(session)
    } }

    private suspend fun flushRefresh(session: Session) {
        if (session.store.read().pendingRefresh || preferences.needsRefresh(session.root)) {
            checkSession(session)
            localAccess(true) { checkSession(session); session.vault.recover() }
            refresh()
            session.store.write(session.store.read().copy(pendingRefresh = false))
            preferences.markRefresh(session.root, false)
        }
    }

    private suspend fun buildPreview(session: Session): S3SyncPreview {
        checkSession(session)
        var local = emptyMap<String, S3FileState>()
        localAccess(false) { checkSession(session); local = session.vault.scan() }
        val remote = session.client.list().toMutableMap()
        val baseline = session.store.read().files.filterKeys { S3Paths.included(it, session.settings.syncUnderscore) }
        // Only ambiguous or remotely changed common files need GET for byte equality; ETag is never treated as MD5.
        for ((path, l) in local) {
            val r = remote[path] ?: continue
            if (!l.directory && (baseline[path] == null || baseline[path]?.remote != r.remoteVersion())) {
                val temporary = temporary()
                try { remote[path] = session.client.download(path, temporary, r) } finally { temporary.delete() }
            }
        }
        checkSession(session)
        return S3SyncPreview(S3SyncPlanner.plan(local, remote, baseline), session.identity, local, remote)
    }

    suspend fun sync(
        preview: S3SyncPreview? = null,
        choices: Map<String, S3ConflictChoice> = emptyMap(),
        confirmDeletes: Boolean = false,
        automatic: Boolean = false,
        progress: (String) -> Unit = {},
    ): S3SyncResult = mutex.withLock { withContext(Dispatchers.IO) {
        val session = session()
        S3SyncGate.activeRoot = session.root
        try {
        flushRefresh(session)
        var stored = session.store.read()
        if (automatic && !stored.initialized) return@withContext S3SyncResult(0, 1)
        val actual = buildPreview(session)
        if (preview != null) {
            check(preview.identity == actual.identity && preview.local.mapValues { it.value.localVersion() } == actual.local.mapValues { it.value.localVersion() } &&
                preview.remote.mapValues { it.value.remoteVersion() } == actual.remote.mapValues { it.value.remoteVersion() }) { "同步预览已过期，请重新生成" }
        }
        val vaultChoices = actual.changes.filter { it.reason.startsWith("隐私库") }.map { choices[it.path] ?: S3ConflictChoice.SKIP }.distinct()
        require(vaultChoices.size <= 1) { "隐私库冲突必须全部选择同一侧，或全部跳过" }
        val actions = actual.items.mapNotNull { item ->
            val action = if (item.action == S3SyncAction.CONFLICT) S3SyncPlanner.resolve(item, choices[item.path] ?: S3ConflictChoice.SKIP,
                actual.local.containsKey(item.path), actual.remote.containsKey(item.path)) else item.action
            if (action == null || ((action == S3SyncAction.DELETE_LOCAL || action == S3SyncAction.DELETE_REMOTE) && (!confirmDeletes || automatic))) null
            else item.copy(action = action)
        }.sortedWith(compareBy<S3SyncItem> {
            when (it.action) { S3SyncAction.MATCH -> 0; S3SyncAction.DELETE_LOCAL, S3SyncAction.DELETE_REMOTE -> 3; else -> if (it.path.endsWith('/')) 1 else 2 }
        }.thenBy { if (it.action == S3SyncAction.DELETE_LOCAL || it.action == S3SyncAction.DELETE_REMOTE) -it.path.length else it.path.length })
        val baselines = stored.files.toMutableMap()
        var completed = 0
        try {
            for (item in actions) {
                currentCoroutineContext().ensureActive()
                checkSession(session)
                val path = item.path
                val local = actual.local[path]
                var remote = actual.remote[path]
                progress("${item.action.label}：$path")
                if (item.action == S3SyncAction.MATCH) {
                    if (local != null && remote != null) baselines[path] = S3Baseline(local.localVersion(), remote.remoteVersion())
                    else if (local == null && remote == null) baselines.remove(path)
                } else {
                    val temporary = temporary()
                    try {
                        when (item.action) {
                            S3SyncAction.UPLOAD -> {
                                checkNotNull(local)
                                localAccess(false) { checkSession(session); session.vault.snapshot(path, temporary, local) }
                                verifyRemote(session, path, remote)
                                retainRemote(session, path, remote)
                                remote = session.client.upload(path, temporary, local.modifiedMs.takeIf { it > 0 } ?: System.currentTimeMillis(), remote)
                                if (!local.directory) {
                                    val verification = temporary()
                                    try { check(session.client.download(path, verification, remote).hash == local.hash) { "上传后远端内容已变化" } }
                                    finally { verification.delete() }
                                }
                                baselines[path] = S3Baseline(local.localVersion(), remote.remoteVersion())
                            }
                            S3SyncAction.DOWNLOAD -> {
                                checkNotNull(remote)
                                val hash = if (remote.directory) { temporary.writeBytes(byteArrayOf()); "" }
                                    else session.client.download(path, temporary, remote).hash
                                preferences.markRefresh(session.root, true)
                                stored = stored.copy(pendingRefresh = true)
                                session.store.write(stored.copy(files = baselines))
                                localAccess(true) {
                                    checkSession(session)
                                    session.vault.apply(path, temporary, local, hash)
                                    val after = session.vault.state(path) ?: error("下载后文件不存在")
                                    baselines[path] = S3Baseline(after.localVersion(), remote.remoteVersion())
                                }
                            }
                            S3SyncAction.DELETE_LOCAL -> {
                                verifyRemote(session, path, null)
                                preferences.markRefresh(session.root, true)
                                stored = stored.copy(pendingRefresh = true)
                                session.store.write(stored.copy(files = baselines))
                                localAccess(true) { checkSession(session); session.vault.apply(path, null, local) }
                                baselines.remove(path)
                            }
                            S3SyncAction.DELETE_REMOTE -> {
                                checkNotNull(remote)
                                localAccess(false) { check(session.vault.state(path) == null) { "本地文件已重新出现" } }
                                verifyRemote(session, path, remote)
                                retainRemote(session, path, remote)
                                session.client.delete(path, remote)
                                baselines.remove(path)
                            }
                            else -> Unit
                        }
                        completed++
                    } finally { temporary.delete() }
                }
                stored = stored.copy(files = baselines.toMap())
                session.store.write(stored)
            }
            // First sync is a user-reviewed operation. Skipped conflicts retain their old/absent baseline.
            stored = stored.copy(initialized = true)
            session.store.write(stored)
            flushRefresh(session)
            val result = S3SyncResult(completed, actual.changes.size - completed)
            if (completed > 0 || result.pending > 0 || !automatic) preferences.log(session.root, result.summary)
            result
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            preferences.log(session.root, "同步未完成，已完成项目保留；请重新预览。${readableError(error)}")
            throw error
        }
        } finally { S3SyncGate.activeRoot = null }
    } }

    private suspend fun verifyRemote(session: Session, path: String, expected: S3FileState?) {
        if (expected?.directory == true && expected.etag.isEmpty()) return
        val current = session.client.head(path)
        check(current?.remoteVersion() == expected?.remoteVersion()) { "远端文件已变化，请重新预览" }
    }

    private suspend fun retainRemote(session: Session, path: String, remote: S3FileState?) {
        if (remote == null || remote.directory) return
        // OSS PutObject/DeleteObject do not support conditional headers. Keep the
        // overwritten bytes locally as a recovery copy after verifyRemote().
        val directory = File(context.noBackupFilesDir, "s3-recovery/${s3Hash(session.root)}")
        check(directory.isDirectory || directory.mkdirs()) { "无法保留远端恢复副本" }
        val target = File(directory, "${s3Hash(path)}-remote-${UUID.randomUUID()}")
        try {
            session.client.download(path, target, remote)
            writeS3Atomic(File(target.path + ".json"), com.google.gson.Gson().toJson(mapOf("path" to path, "time" to System.currentTimeMillis())))
        } catch (error: Exception) { target.delete(); throw error }
    }

    private fun temporary(): File = File.createTempFile("kardleaf-s3-", ".tmp", context.cacheDir)

    companion object {
        // ponytail: serialize S3 sessions; per-vault parallelism only if multiple active vaults are introduced.
        private val mutex = S3SyncGate.cloudMutex
        fun readableError(error: Throwable): String = when (error) {
            is S3HttpException -> {
                val path = error.path?.let { "（Key：$it）" }.orEmpty()
                when (error.code) {
                "PublicEndpointForbidden" -> "OSS 公网 API 被限制，请改用已备案 HTTPS 自定义域名"
                "SecondLevelDomainForbidden" -> "OSS 要求虚拟主机地址，请检查 Endpoint（如 oss-cn-guangzhou.aliyuncs.com）"
                "SignatureDoesNotMatch", "InvalidAccessKeyId", "InvalidSecurityToken" -> "S3 签名或密钥无效，请检查 Access Key、Secret、Region 和手机时间"
                "RequestTimeTooSkewed", "RequestExpired" -> "手机时间与 OSS 相差过大，请开启自动日期和时间"
                "AuthorizationHeaderMalformed" -> "S3 Region 或签名范围不正确，请检查 Region"
                "NotImplemented" -> "OSS 不支持当前请求中的参数或请求头，请更新应用后重试"
                "NoSuchBucket" -> "S3 Bucket 不存在，或 Endpoint/Region 与 Bucket 所在地域不匹配"
                "NoSuchKey", "NoSuchObject", "NoSuchVersion" -> "S3 对象 Key 不匹配或对象已删除$path，请核对 Remote Prefix 后重新生成同步预览"
                "InvalidArgument", "MalformedXML", "InvalidRequest" -> "S3 请求参数不被服务端接受，请检查 Endpoint、Bucket 和 Prefix"
                else -> when (error.status) {
                401, 403 -> "S3 认证或权限失败，请检查密钥、Region、时间和授权"
                404 -> "S3 Bucket 或对象不存在"
                409, 412 -> "远端版本发生变化，请重新预览"
                429 -> "S3 请求过于频繁，请稍后重试"
                else -> "S3 请求失败（HTTP ${error.status}${error.code?.let { "，$it" } ?: ""}）"
                }
                }
            }
            is IllegalArgumentException, is IllegalStateException -> error.message?.takeIf { !it.contains("Credential", true) && !it.contains("Signature", true) && !it.contains("https://") }?.take(160) ?: "S3 配置或文件状态无效"
            else -> "S3 网络或文件读写失败，请检查连接、空间与目录权限"
        }
    }
}
