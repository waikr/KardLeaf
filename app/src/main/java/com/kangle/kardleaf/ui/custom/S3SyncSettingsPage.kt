package com.kangle.kardleaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.sync.S3CloudSyncManager
import com.kangle.kardleaf.data.sync.S3ConflictChoice
import com.kangle.kardleaf.data.sync.S3SyncAction
import com.kangle.kardleaf.data.sync.S3SyncPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun S3SyncSettingsPage(prefs: PrefsManager, manager: S3CloudSyncManager?) {
    val root = prefs.getRootUri().orEmpty()
    val preferences = prefs.s3Preferences
    var settings by remember(root) { mutableStateOf(preferences.load(root)) }
    var interval by remember(root) { mutableStateOf(settings.pollSeconds.toString()) }
    var accessKey by remember(root) { mutableStateOf("") }
    var secretKey by remember(root) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<S3SyncPreview?>(null) }
    var choices by remember { mutableStateOf<Map<String, S3ConflictChoice>>(emptyMap()) }
    var confirmDeletes by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(preferences.logs(root)) }
    val scope = rememberCoroutineScope()
    var runningJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) runningJob?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { runningJob?.cancel(); lifecycle.removeObserver(observer) }
    }

    suspend fun save() {
        val seconds = interval.toIntOrNull() ?: error("请输入有效的轮询秒数")
        check(!settings.realtime || !prefs.isWebDavRealtimeSyncEnabled()) { "请先关闭 WebDAV 实时同步，再启用 S3 前台同步" }
        val config = settings.copy(pollSeconds = seconds)
        withContext(Dispatchers.IO) { preferences.save(root, config, accessKey, secretKey) }
        settings = preferences.load(root)
        accessKey = ""
        secretKey = ""
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        runningJob = scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = S3CloudSyncManager.readableError(error); preview = null }
            finally { busy = false; logs = preferences.logs(root) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("S3 / S3-compatible", style = MaterialTheme.typography.titleLarge)
        Text("普通笔记和附件与 Obsidian Remotely Save 互通。手机同步 .KardLeaf；其中的 .bak 和临时文件不参与同步。Room 数据库、.obsidian 和其他点号目录不上传。")
        OutlinedTextField(settings.endpoint, { settings = settings.copy(endpoint = it.trim()); preview = null }, label = { Text("Endpoint（HTTPS）") },
            placeholder = { Text("https://s3.oss-cn-guangzhou.aliyuncs.com") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(settings.region, { settings = settings.copy(region = it.trim()); preview = null }, label = { Text("Region") },
            placeholder = { Text("cn-guangzhou") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(settings.bucket, { settings = settings.copy(bucket = it.trim()); preview = null }, label = { Text("Bucket") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(settings.prefix, { settings = settings.copy(prefix = it); preview = null }, label = { Text("Remote Prefix（默认留空）") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text("对象路径：${settings.prefix.ifEmpty { "（空前缀）" }} + 笔记相对路径。两端须一致，不会自动添加 vault/。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(accessKey, { accessKey = it; preview = null }, label = { Text("Access Key ID") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(secretKey, { secretKey = it; preview = null }, label = { Text("Secret Access Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text(if (preferences.hasCredentials(root)) "密钥已加密保存；两项留空保留原密钥。" else "请在此设备输入密钥；不会显示已保存的密钥。", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.forcePathStyle, { settings = settings.copy(forcePathStyle = it); preview = null }, enabled = !busy)
            Text("Force Path Style（OSS 必须关闭）", Modifier.padding(start = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.syncUnderscore, { settings = settings.copy(syncUnderscore = it); preview = null }, enabled = !busy)
            Text("同步下划线文件及目录（与电脑设置一致）", Modifier.padding(start = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.realtime, { settings = settings.copy(realtime = it) }, enabled = !busy)
            Text("前台自动同步", Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(interval, { interval = it }, label = { Text("远端轮询间隔（秒，5–3600）") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text("首次先生成预览并手动同步。自动同步只处理无冲突的新增和修改，删除需在预览确认；当前编辑器、任务编辑或隐私库打开时会延后接收文件。")
        Text("单文件支持流式传输，最大 5 GiB。兼容服务的条件写入能力可能不同；请避免两端同时修改同一文件。", style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy && root.isNotEmpty(), onClick = { run { save(); preview = null; message = "S3 设置已保存" } }) { Text("保存设置") }
        Button(enabled = !busy && manager != null, onClick = { run { save(); message = "连接成功，发现 ${manager!!.testConnection()} 个文件（仅验证读取权限）" } }) { Text("测试连接（只读）") }
        Button(enabled = !busy && manager != null, onClick = { run {
            save()
            preview = manager!!.preview()
            choices = emptyMap()
            confirmDeletes = false
            message = "预览完成：${preview!!.changes.size} 项变化"
        } }) { Text("生成同步预览") }
        if (busy) CircularProgressIndicator()
        if (message.isNotEmpty()) Text(message)
        preview?.let { plan ->
            if (plan.changes.isEmpty()) Text("两端文件内容一致。点击开始同步建立基线。")
            // Render a bounded page; never silently hide operations from the confirmation flow.
            var page by remember(plan) { mutableStateOf(0) }
            val pageCount = ((plan.changes.size + 49) / 50).coerceAtLeast(1)
            Text("预览 ${page + 1} / $pageCount 页，共 ${plan.changes.size} 项")
            plan.changes.drop(page * 50).take(50).forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${item.action.label} · ${item.path}")
                        if (item.reason.isNotEmpty()) Text(item.reason, style = MaterialTheme.typography.bodySmall)
                        if (item.action == S3SyncAction.CONFLICT) {
                            val resolved = com.kangle.kardleaf.data.sync.S3SyncPlanner.resolve(
                                item, choices[item.path] ?: S3ConflictChoice.SKIP,
                                plan.local.containsKey(item.path), plan.remote.containsKey(item.path),
                            )
                            Text("选择后的操作：${resolved?.label ?: "跳过"}；本地${if (plan.local.containsKey(item.path)) "存在" else "缺失"}，远端${if (plan.remote.containsKey(item.path)) "存在" else "缺失"}")
                            Text("当前：${(choices[item.path] ?: S3ConflictChoice.SKIP).label}；保留缺失的一侧意味着删除另一侧。", style = MaterialTheme.typography.bodySmall)
                            Row {
                                S3ConflictChoice.values().forEach { choice ->
                                    TextButton(enabled = !busy, onClick = {
                                        choices = if (item.reason.startsWith("隐私库")) choices + plan.changes.filter { it.reason.startsWith("隐私库") }.associate { it.path to choice }
                                        else choices + (item.path to choice)
                                    }) { Text(choice.label) }
                                }
                            }
                        }
                    }
                }
            }
            Row {
                TextButton(enabled = !busy && page > 0, onClick = { page-- }) { Text("上一页") }
                TextButton(enabled = !busy && page + 1 < pageCount, onClick = { page++ }) { Text("下一页") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(confirmDeletes, { confirmDeletes = it }, enabled = !busy)
                Text("确认执行预览中的删除（包括冲突选择导致的删除）")
            }
            Button(enabled = !busy && manager != null, onClick = { run {
                val result = manager!!.sync(plan, choices, confirmDeletes)
                message = result.summary
                preview = null
            } }) { Text("开始同步") }
        }
        TextButton(onClick = { logs = preferences.logs(root) }) { Text("刷新同步记录") }
        Text(logs.ifEmpty { "暂无同步记录" }, style = MaterialTheme.typography.bodySmall)
    }
}
