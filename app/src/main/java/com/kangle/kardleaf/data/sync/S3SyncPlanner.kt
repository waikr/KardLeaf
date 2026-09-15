package com.kangle.kardleaf.data.sync

import java.security.MessageDigest

internal data class S3FileState(
    val size: Long,
    val modifiedMs: Long,
    val hash: String = "",
    val etag: String = "",
    val directory: Boolean = false,
) {
    fun localVersion(): String = if (directory) "directory" else "$size:$hash"
    fun remoteVersion(): String = if (directory) "directory" else "$size:$modifiedMs:$etag"
}

internal data class S3Baseline(val local: String, val remote: String)

enum class S3SyncAction(val label: String) {
    UPLOAD("上传"), DOWNLOAD("下载"), DELETE_LOCAL("删除本地"), DELETE_REMOTE("删除远端"),
    MATCH("已一致"), CONFLICT("冲突"),
}

enum class S3ConflictChoice(val label: String) { KEEP_LOCAL("保留本地"), KEEP_REMOTE("保留远端"), SKIP("跳过") }

data class S3SyncItem(val path: String, val action: S3SyncAction, val reason: String = "")

internal object S3SyncPlanner {
    fun plan(
        local: Map<String, S3FileState>,
        remote: Map<String, S3FileState>,
        baseline: Map<String, S3Baseline>,
    ): List<S3SyncItem> {
        val paths = (local.keys + remote.keys).distinct()
        require(paths.groupBy { it.trimEnd('/').lowercase(java.util.Locale.ROOT) }.values.none { it.size > 1 }) {
            "两端有大小写或文件与目录同名冲突，请先重命名"
        }
        val items = (local.keys + remote.keys + baseline.keys).sorted().map { path ->
            val l = local[path]
            val r = remote[path]
            val b = baseline[path]
            val localChanged = l?.localVersion() != b?.local
            val remoteChanged = r?.remoteVersion() != b?.remote
            val action = when {
                l == null && r == null -> S3SyncAction.MATCH
                l != null && r != null && l.directory && r.directory -> S3SyncAction.MATCH
                l != null && r != null && l.hash.isNotEmpty() && l.hash == r.hash && l.size == r.size -> S3SyncAction.MATCH
                b == null && l == null -> S3SyncAction.DOWNLOAD
                b == null && r == null -> S3SyncAction.UPLOAD
                b == null -> S3SyncAction.CONFLICT
                !localChanged && !remoteChanged -> S3SyncAction.MATCH
                l == null && !remoteChanged -> S3SyncAction.DELETE_REMOTE
                r == null && !localChanged -> S3SyncAction.DELETE_LOCAL
                l == null || r == null -> S3SyncAction.CONFLICT
                localChanged && remoteChanged -> S3SyncAction.CONFLICT
                localChanged -> S3SyncAction.UPLOAD
                else -> S3SyncAction.DOWNLOAD
            }
            S3SyncItem(path, action, if (action == S3SyncAction.CONFLICT) {
                if (b == null) "首次同步同名文件不同" else "两侧均变化，或删除与修改冲突"
            } else "")
        }
        // Ciphertext filenames change on every save. Compare the whole privacy group without decrypting it.
        val vaultPaths = (local.keys + remote.keys + baseline.keys).filter { it.startsWith(".KardLeaf/Vault/") && !it.endsWith('/') }
        val localVaultChanged = vaultPaths.any { local[it]?.localVersion() != baseline[it]?.local }
        val remoteVaultChanged = vaultPaths.any { remote[it]?.remoteVersion() != baseline[it]?.remote }
        val vaultDiffers = items.any { it.path in vaultPaths && it.action != S3SyncAction.MATCH }
        val bothHaveVault = vaultPaths.any { local[it] != null } && vaultPaths.any { remote[it] != null }
        return if (bothHaveVault && localVaultChanged && remoteVaultChanged && vaultDiffers) {
            items.map { if (it.path in vaultPaths && it.action != S3SyncAction.MATCH) {
                it.copy(action = S3SyncAction.CONFLICT, reason = "隐私库两侧均变化；需对整个隐私库选择同一侧")
            } else it }
        } else items
    }

    fun resolve(item: S3SyncItem, choice: S3ConflictChoice, localExists: Boolean, remoteExists: Boolean): S3SyncAction? =
        when (choice) {
            S3ConflictChoice.SKIP -> null
            S3ConflictChoice.KEEP_LOCAL -> if (localExists) S3SyncAction.UPLOAD else if (remoteExists) S3SyncAction.DELETE_REMOTE else S3SyncAction.MATCH
            S3ConflictChoice.KEEP_REMOTE -> if (remoteExists) S3SyncAction.DOWNLOAD else if (localExists) S3SyncAction.DELETE_LOCAL else S3SyncAction.MATCH
        }
}

internal object S3Paths {
    fun validate(path: String): String {
        require(path.isNotEmpty() && !path.startsWith('/') && !path.contains('\\')) { "文件路径无效" }
        require(path.none { it.code < 32 || it.code == 127 }) { "文件路径含控制字符" }
        require(path.removeSuffix("/").split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "文件路径越界" }
        return path
    }

    fun prefix(value: String): String = if (value.isEmpty()) "" else validate(value).trimEnd('/') + "/"

    fun included(path: String, underscore: Boolean): Boolean {
        validate(path)
        val parts = path.removeSuffix("/").split('/')
        if (parts.any { it.endsWith(".kardleaf-sync-tmp") || it.endsWith(".kardleaf-record-bak") || it.endsWith(".kardleaf-s3-tmp") }) return false
        if (parts.first() == ".KardLeaf") {
            if (parts.any { it.endsWith(".bak", ignoreCase = true) }) return false
            if (parts.getOrNull(1) == "Vault" && parts.last().endsWith(".tmp", ignoreCase = true)) return false
            return true
        }
        return parts.none { it.startsWith('.') || (!underscore && it.startsWith('_')) }
    }

    fun key(prefix: String, path: String): String = (prefix(prefix) + validate(path)).also {
        require(it.toByteArray(Charsets.UTF_8).size <= 1024) { "对象路径过长" }
    }
}

internal fun ByteArray.s3Hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun s3Hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).s3Hex()
