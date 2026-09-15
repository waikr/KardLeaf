package com.kangle.kardleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val svgPathPattern = Regex(
    """<path\b[^>]*\bd\s*=\s*[\"']([^\"']+)[\"'][^>]*>""",
    RegexOption.IGNORE_CASE,
)
private val svgViewBoxPattern = Regex(
    """viewBox\s*=\s*[\"']([^\"']+)[\"']""",
    RegexOption.IGNORE_CASE,
)
private val svgNumberPattern = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")

internal val highlightToolbarIcon: ImageVector = ImageVector.Builder(
    name = "KardLeafHighlightToolbarItem",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    listOf(
        "m9 11-6 6v3h9l3-3",
        "m22 12-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4",
    ).forEach { path ->
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

internal fun customToolbarSvgImageVector(svg: String): ImageVector? {
    val pathData = svgPathPattern.findAll(svg).map { it.groupValues[1] }.toList()
    if (pathData.isEmpty()) return null
    val viewBoxNumbers = svgViewBoxPattern
        .find(svg)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { value -> svgNumberPattern.findAll(value).mapNotNull { it.value.toFloatOrNull() }.toList() }
    val viewportWidth = viewBoxNumbers?.getOrNull(2)?.takeIf { it > 0f } ?: 24f
    val viewportHeight = viewBoxNumbers?.getOrNull(3)?.takeIf { it > 0f } ?: 24f
    val builder = ImageVector.Builder(
        name = "KardLeafCustomToolbarItem",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    var parsedPathCount = 0
    pathData.forEach { data ->
        runCatching {
            builder.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Black),
            )
            parsedPathCount++
        }
    }
    return builder.build().takeIf { parsedPathCount > 0 }
}

internal fun customToolbarFallbackText(item: KardLeafCustomFeatures.CustomFunctionItem): String {
    val svg = item.svg.trim()
    if (svg.contains("<svg", ignoreCase = true) || svg.contains("<path", ignoreCase = true)) {
        return item.name.take(4)
    }
    return svg.ifBlank { item.name }.replace('\n', ' ').take(4)
}

@Composable
internal fun CustomToolbarItemGlyph(
    item: KardLeafCustomFeatures.CustomFunctionItem,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val imageVector = remember(item.svg) { customToolbarSvgImageVector(item.svg) }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = item.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size - 6.dp),
            )
        } else {
            Text(
                text = customToolbarFallbackText(item),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun QuickTextsSettingsPage(onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf(KardLeafCustomFeatures.getQuickTexts(context)) }
    var showEditor by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun openEditor(index: Int?) {
        val item = index?.let(items::getOrNull)
        editingIndex = index
        name = item?.name.orEmpty()
        content = item?.content.orEmpty()
        errorMessage = null
        showEditor = true
    }

    fun saveEditor() {
        val item = KardLeafCustomFeatures.normalizeQuickText(name, content)
        if (item == null) {
            errorMessage = "内容不能为空"
            return
        }
        val next = items.toMutableList().apply {
            editingIndex?.let { set(it, item) } ?: add(item)
        }
        items = KardLeafCustomFeatures.normalizeQuickTexts(next)
        KardLeafCustomFeatures.saveQuickTexts(context, items)
        showEditor = false
        onChanged()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsPageText("点击项目编辑，内容为空时不能保存")
        if (items.size < KardLeafCustomFeatures.MaxQuickTextItems) {
            SettingsActionRow(
                icon = Icons.Outlined.Add,
                title = "新增快捷文本",
                subtitle = "设置名称和插入内容",
                onClick = { openEditor(null) },
            )
        } else {
            SettingsPageText("最多 ${KardLeafCustomFeatures.MaxQuickTextItems} 项")
        }
        items.forEachIndexed { index, item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openEditor(index) },
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                    ) {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.content.replace('\n', ' '),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { openEditor(index) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${item.name}")
                    }
                    IconButton(onClick = { deleteIndex = index }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除 ${item.name}")
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingIndex == null) "新增快捷文本" else "编辑快捷文本") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("名称（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it; errorMessage = null },
                        label = { Text("内容") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = ::saveEditor) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("取消") } },
        )
    }

    deleteIndex?.let { index ->
        val item = items.getOrNull(index)
        if (item != null) {
            AlertDialog(
                onDismissRequest = { deleteIndex = null },
                title = { Text("删除快捷文本") },
                text = { Text("确定删除“${item.name}”？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            items = items.toMutableList().also { it.removeAt(index) }
                            KardLeafCustomFeatures.saveQuickTexts(context, items)
                            deleteIndex = null
                            onChanged()
                        },
                    ) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("取消") } },
            )
        }
    }
}

@Composable
internal fun CustomFunctionItemsSettingsPage(onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf(KardLeafCustomFeatures.getCustomFunctionItems(context)) }
    var showEditor by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var svg by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun openEditor(index: Int?) {
        val item = index?.let(items::getOrNull)
        editingIndex = index
        name = item?.name.orEmpty()
        svg = item?.svg.orEmpty()
        content = item?.content.orEmpty()
        errorMessage = null
        showEditor = true
    }

    fun saveEditor() {
        val item = KardLeafCustomFeatures.normalizeCustomFunctionItem(
            name = name,
            svg = svg,
            content = content,
            id = editingIndex?.let { items.getOrNull(it)?.id }.orEmpty(),
        )
        if (item == null) {
            errorMessage = "内容不能为空"
            return
        }
        val next = items.toMutableList().apply {
            editingIndex?.let { set(it, item) } ?: add(item)
        }
        items = KardLeafCustomFeatures.normalizeCustomFunctionItems(next)
        KardLeafCustomFeatures.saveCustomFunctionItems(context, items)
        showEditor = false
        onChanged()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsPageText("自定义功能项会直接显示为底部工具栏按钮，内容为空时不能保存")
        if (items.size < KardLeafCustomFeatures.MaxCustomFunctionItems) {
            SettingsActionRow(
                icon = Icons.Outlined.Add,
                title = "新增自定义功能项",
                subtitle = "设置名称、SVG 图片和插入内容",
                onClick = { openEditor(null) },
            )
        } else {
            SettingsPageText("最多 ${KardLeafCustomFeatures.MaxCustomFunctionItems} 项")
        }
        items.forEachIndexed { index, item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openEditor(index) },
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CustomToolbarItemGlyph(item)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.content.replace('\n', ' '),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { openEditor(index) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${item.name}")
                    }
                    IconButton(onClick = { deleteIndex = index }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除 ${item.name}")
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingIndex == null) "新增自定义功能项" else "编辑自定义功能项") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("名称（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = svg,
                        onValueChange = { svg = it },
                        label = { Text("SVG 图片（可选）") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it; errorMessage = null },
                        label = { Text("内容") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsPageText("名称为空时使用内容；SVG 图片为空时使用名称作为按钮显示内容")
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = ::saveEditor) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("取消") } },
        )
    }

    deleteIndex?.let { index ->
        val item = items.getOrNull(index)
        if (item != null) {
            AlertDialog(
                onDismissRequest = { deleteIndex = null },
                title = { Text("删除自定义功能项") },
                text = { Text("确定删除“${item.name}”？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            items = items.toMutableList().also { it.removeAt(index) }
                            KardLeafCustomFeatures.saveCustomFunctionItems(context, items)
                            deleteIndex = null
                            onChanged()
                        },
                    ) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("取消") } },
            )
        }
    }
}
