package com.kangle.kardleaf.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownMindMapScreen(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
    unavailableTitle: String? = null,
    unavailableMessage: String? = null,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onHeadingClick: (MarkdownHeading) -> Unit,
    onNodeReparent: (movingIndex: Int, parentIndex: Int) -> Unit = { _, _ -> },
    onNodeAddChild: (parentIndex: Int) -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(
                                text = "思维导图",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = when {
                                    unavailableTitle != null -> unavailableTitle
                                    headings.isEmpty() -> "没有检测到 Markdown 标题"
                                    else -> "${headings.size} 个节点 · 双指缩放 · 拖到目标节点下"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭思维导图")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    unavailableTitle != null -> {
                        MindMapUnavailableHint(
                            title = title,
                            reasonTitle = unavailableTitle,
                            message = unavailableMessage.orEmpty(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    headings.isEmpty() -> {
                        EmptyMindMapHint(
                            title = title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        MarkdownMindMapWebView(
                            title = title,
                            headings = headings,
                            isDark = isDark,
                            onHeadingClick = onHeadingClick,
                            onNodeReparent = onNodeReparent,
                            onNodeAddChild = onNodeAddChild,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMindMapHint(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
    ) {
        Text(
            text = title.ifBlank { "未命名笔记" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "思维导图会根据 Markdown 标题生成。你可以在笔记里添加 #、##、### 这类标题后再打开。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MindMapUnavailableHint(
    title: String,
    reasonTitle: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
    ) {
        Text(
            text = title.ifBlank { "未命名笔记" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = reasonTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message.ifBlank { "当前内容不适合直接生成思维导图。" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MarkdownMindMapWebView(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
    onHeadingClick: (MarkdownHeading) -> Unit,
    onNodeReparent: (movingIndex: Int, parentIndex: Int) -> Unit,
    onNodeAddChild: (parentIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnHeadingClick = rememberUpdatedState(onHeadingClick)
    val currentOnNodeReparent = rememberUpdatedState(onNodeReparent)
    val currentOnNodeAddChild = rememberUpdatedState(onNodeAddChild)
    val currentHeadings = rememberUpdatedState(headings)
    val html = rememberMindMapHtml(title, headings, isDark)
    val signature = rememberMindMapSignature(title, headings, isDark)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.textZoom = 100
                settings.allowFileAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onNodeClick(index: Int) {
                            Handler(Looper.getMainLooper()).post {
                                currentHeadings.value.getOrNull(index)?.let { currentOnHeadingClick.value(it) }
                            }
                        }

                        @JavascriptInterface
                        fun onNodeReparent(movingIndex: Int, parentIndex: Int) {
                            Handler(Looper.getMainLooper()).post {
                                currentOnNodeReparent.value(movingIndex, parentIndex)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeAddChild(parentIndex: Int) {
                            Handler(Looper.getMainLooper()).post {
                                currentOnNodeAddChild.value(parentIndex)
                            }
                        }
                    },
                    "KardLeafMindMap",
                )
            }
        },
        update = { webView ->
            if (webView.tag != signature) {
                webView.tag = signature
                webView.loadDataWithBaseURL(
                    "https://kardleaf.local/mindmap/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

@Composable
private fun rememberMindMapHtml(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
): String = androidx.compose.runtime.remember(title, headings, isDark) {
    buildMindMapHtml(title, headings, isDark)
}

@Composable
private fun rememberMindMapSignature(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
): String = androidx.compose.runtime.remember(title, headings, isDark) {
    title.hashCode().toString() + ":" + headings.size + ":" + headings.sumOf { it.text.hashCode() + it.level * 31 + it.lineIndex } + ":" + isDark
}

private fun buildMindMapHtml(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
): String {
    val nodes = JSONArray().apply {
        headings.forEachIndexed { index, heading ->
            put(
                JSONObject()
                    .put("index", index)
                    .put("level", heading.level.coerceIn(1, 6))
                    .put("text", heading.text.take(80))
                    .put("line", heading.lineIndex + 1),
            )
        }
    }.toString()
    val pageTitle = JSONObject.quote(title.ifBlank { "未命名笔记" })
    val dark = if (isDark) "true" else "false"
    return """
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<title>KardLeaf Mind Map</title>
<style>
:root {
  color-scheme: ${if (isDark) "dark" else "light"};
  --bg: ${if (isDark) "#101418" else "#f7fbff"};
  --surface: ${if (isDark) "#18212a" else "#ffffff"};
  --surface2: ${if (isDark) "#1f2b36" else "#eef6ff"};
  --text: ${if (isDark) "#e5edf5" else "#17212b"};
  --muted: ${if (isDark) "#9fb0c2" else "#667789"};
  --line: ${if (isDark) "#55708d" else "#9bc9ff"};
  --primary: ${if (isDark) "#8ec8ff" else "#2f80ed"};
  --primary2: ${if (isDark) "#223b55" else "#dceeff"};
  --float: ${if (isDark) "rgba(24,33,42,.92)" else "rgba(255,255,255,.92)"};
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: var(--bg); font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--text); }
#toolbar { position: fixed; left: 12px; right: 12px; top: 8px; z-index: 4; display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 18px; background: var(--float); box-shadow: 0 8px 28px rgba(20, 80, 140, .16); backdrop-filter: blur(12px); }
#title { min-width: 0; flex: 1; }
#title .main { font-size: 14px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
#title .sub { margin-top: 1px; font-size: 11px; color: var(--muted); }
.toolBtn { width: 30px; height: 30px; border: 0; border-radius: 12px; background: var(--primary2); color: var(--primary); font-size: 17px; font-weight: 700; }
#stage { width: 100%; height: 100%; touch-action: none; }
svg { width: 100%; height: 100%; display: block; }
.link { fill: none; stroke: var(--line); stroke-width: 2.2; stroke-linecap: round; opacity: .72; }
.node rect { fill: var(--surface); stroke: rgba(47,128,237,.26); stroke-width: 1.1; filter: drop-shadow(0 8px 16px rgba(31, 91, 156, .13)); }
.node.root rect { fill: var(--primary); stroke: var(--primary); }
.node { cursor: grab; }
.node.dragging rect { stroke: var(--primary); stroke-width: 2; opacity: .86; }
.node.dropTarget rect { stroke: var(--primary); stroke-width: 2.4; fill: var(--primary2); }
.node.dropTarget.root rect { fill: var(--primary); opacity: .78; }
.node text { fill: var(--text); font-size: 14px; font-weight: 650; pointer-events: none; }
.node .meta { fill: var(--muted); font-size: 11px; font-weight: 500; }
.node.root text { fill: #fff; }
.addBtn { cursor: pointer; }
.addBtn circle { fill: var(--primary2); stroke: var(--primary); stroke-width: 1.1; }
.addBtn text { fill: var(--primary); font-size: 16px; font-weight: 800; pointer-events: none; }
.node.root .addBtn circle { fill: rgba(255,255,255,.2); stroke: rgba(255,255,255,.72); }
.node.root .addBtn text { fill: #fff; }
.hint { position: fixed; left: 14px; right: 14px; bottom: 12px; z-index: 3; padding: 8px 10px; border-radius: 15px; color: var(--muted); background: var(--float); font-size: 11px; text-align: center; box-shadow: 0 6px 20px rgba(20, 80, 140, .12); }
</style>
</head>
<body>
<div id="toolbar">
  <div id="title">
    <div class="main" id="docTitle"></div>
    <div class="sub" id="docSub"></div>
  </div>
  <button class="toolBtn" id="zoomOut" aria-label="缩小">−</button>
  <button class="toolBtn" id="zoomIn" aria-label="放大">+</button>
  <button class="toolBtn" id="reset" aria-label="重置">⌂</button>
</div>
<div id="stage"><svg id="svg"><g id="viewport"><g id="links"></g><g id="nodes"></g></g></svg></div>
<div class="hint">点节点右侧 + 添加子节点；拖动节点到另一个节点上，可改成它的子节点</div>
<script>
const nodesData = $nodes;
const docTitle = $pageTitle;
const isDark = $dark;
document.getElementById('docTitle').textContent = docTitle;
document.getElementById('docSub').textContent = nodesData.length + ' 个标题节点';
const svg = document.getElementById('svg');
const viewport = document.getElementById('viewport');
const linksLayer = document.getElementById('links');
const nodesLayer = document.getElementById('nodes');
const nodeWidth = 150;
const nodeHeight = 46;
const rootWidth = 170;
const levelGap = 168;
const rowGap = 58;
const root = { index: -1, level: 0, text: docTitle || '未命名笔记', line: 1, x: 28, y: 74, parent: null, root: true };
const stack = [root];
const nodes = [root];
nodesData.forEach((item, i) => {
  const level = Math.max(1, Math.min(6, item.level || 1));
  while (stack.length > level) stack.pop();
  const parent = stack[stack.length - 1] || root;
  const node = { index: item.index, level: level, text: item.text, line: item.line, x: 28 + level * levelGap, y: 74 + i * rowGap, parent: parent, root: false };
  nodes.push(node);
  stack[level] = node;
});
let tx = 0, ty = 0, scale = 1;
let drag = null;
let pinch = null;
let dropTarget = null;
let lastTap = { time: 0, nodeIndex: -99 };
let suppressTapUntil = 0;
const activePointers = new Map();
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
function shortText(text, max) { return (text || '').length > max ? text.slice(0, max - 1) + '…' : (text || ''); }
function setTransform() { viewport.setAttribute('transform', 'translate(' + tx + ' ' + ty + ') scale(' + scale + ')'); }
function screenToWorld(clientX, clientY) { return { x: (clientX - tx) / scale, y: (clientY - ty) / scale }; }
function distance(a, b) { const dx = a.x - b.x; const dy = a.y - b.y; return Math.hypot(dx, dy); }
function centerOf(a, b) { return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }; }
function currentTwoPointers() { return Array.from(activePointers.values()).slice(0, 2); }
function nodeVisualWidth(n) { return n.root ? rootWidth : nodeWidth; }
function nodeCenter(n) { return { x: n.x + nodeVisualWidth(n) / 2, y: n.y + nodeHeight / 2 }; }
function isDescendantOf(node, ancestor) {
  let p = node ? node.parent : null;
  while (p) {
    if (p === ancestor) return true;
    p = p.parent;
  }
  return false;
}
function findDropTargetFor(movingNode) {
  if (!movingNode || movingNode.root) return null;
  const c = nodeCenter(movingNode);
  let best = null;
  let bestScore = Infinity;
  nodes.forEach(n => {
    if (n === movingNode || isDescendantOf(n, movingNode)) return;
    if (n === movingNode.parent) return;
    const w = nodeVisualWidth(n);
    const marginX = 34;
    const marginY = 26;
    if (c.x < n.x - marginX || c.x > n.x + w + marginX || c.y < n.y - marginY || c.y > n.y + nodeHeight + marginY) return;
    const nc = nodeCenter(n);
    const score = Math.abs(c.x - nc.x) + Math.abs(c.y - nc.y);
    if (score < bestScore) {
      best = n;
      bestScore = score;
    }
  });
  return best;
}
function mapBounds() {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  nodes.forEach(n => {
    minX = Math.min(minX, n.x);
    minY = Math.min(minY, n.y);
    maxX = Math.max(maxX, n.x + nodeVisualWidth(n));
    maxY = Math.max(maxY, n.y + nodeHeight);
  });
  return { minX, minY, maxX, maxY, width: Math.max(1, maxX - minX), height: Math.max(1, maxY - minY) };
}
function resetView() {
  const rect = svg.getBoundingClientRect();
  const b = mapBounds();
  const availableW = Math.max(1, rect.width - 28);
  const availableH = Math.max(1, rect.height - 120);
  const targetH = Math.min(b.height, 760);
  scale = clamp(Math.min(availableW / (b.width + 48), availableH / (targetH + 48), .95), .42, 1.15);
  tx = 14 - b.minX * scale;
  ty = 68 - b.minY * scale;
  setTransform();
}
function pathBetween(a, b) {
  const ax = a.x + nodeVisualWidth(a);
  const ay = a.y + nodeHeight / 2;
  const bx = b.x;
  const by = b.y + nodeHeight / 2;
  const mx = ax + Math.max(48, (bx - ax) * .45);
  return 'M ' + ax + ' ' + ay + ' C ' + mx + ' ' + ay + ', ' + mx + ' ' + by + ', ' + bx + ' ' + by;
}
function render() {
  linksLayer.innerHTML = '';
  nodesLayer.innerHTML = '';
  nodes.slice(1).forEach(n => {
    const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    p.setAttribute('class', 'link');
    p.setAttribute('d', pathBetween(n.parent || root, n));
    linksLayer.appendChild(p);
  });
  nodes.forEach(n => {
    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    g.setAttribute('class', 'node' + (n.root ? ' root' : '') + (drag && drag.node === n ? ' dragging' : '') + (dropTarget === n ? ' dropTarget' : ''));
    g.setAttribute('transform', 'translate(' + n.x + ' ' + n.y + ')');
    g.dataset.index = String(n.index);
    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    rect.setAttribute('width', nodeVisualWidth(n));
    rect.setAttribute('height', nodeHeight);
    rect.setAttribute('rx', '18');
    rect.setAttribute('ry', '18');
    g.appendChild(rect);
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    text.setAttribute('x', '16');
    text.setAttribute('y', '20');
    text.textContent = shortText(n.text, n.root ? 11 : 9);
    g.appendChild(text);
    const meta = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    meta.setAttribute('class', 'meta');
    meta.setAttribute('x', '16');
    meta.setAttribute('y', '35');
    meta.textContent = n.root ? 'KardLeaf' : 'H' + n.level + ' · 第 ' + n.line + ' 行';
    g.appendChild(meta);
    const add = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    add.setAttribute('class', 'addBtn');
    add.setAttribute('transform', 'translate(' + (nodeVisualWidth(n) - 22) + ' 23)');
    add.dataset.parentIndex = String(n.index);
    const addCircle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    addCircle.setAttribute('r', '11');
    add.appendChild(addCircle);
    const addText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    addText.setAttribute('x', '0');
    addText.setAttribute('y', '5');
    addText.setAttribute('text-anchor', 'middle');
    addText.textContent = '+';
    add.appendChild(addText);
    g.appendChild(add);
    nodesLayer.appendChild(g);
  });
}
function findNodeByIndex(index) { return nodes.find(n => n.index === index); }
function closestNode(target) {
  while (target && target !== svg) {
    if (target.classList && target.classList.contains('node')) return target;
    target = target.parentNode;
  }
  return null;
}
function closestAddButton(target) {
  while (target && target !== svg) {
    if (target.classList && target.classList.contains('addBtn')) return target;
    target = target.parentNode;
  }
  return null;
}
function beginPinchIfNeeded() {
  if (activePointers.size < 2) return;
  const [a, b] = currentTwoPointers();
  const center = centerOf(a, b);
  pinch = {
    startDistance: Math.max(24, distance(a, b)),
    startScale: scale,
    worldCenter: screenToWorld(center.x, center.y),
  };
  drag = null;
  dropTarget = null;
  suppressTapUntil = Date.now() + 260;
  render();
}
function updatePinch() {
  if (!pinch || activePointers.size < 2) return;
  const [a, b] = currentTwoPointers();
  const center = centerOf(a, b);
  scale = clamp(pinch.startScale * distance(a, b) / pinch.startDistance, .42, 2.35);
  tx = center.x - pinch.worldCenter.x * scale;
  ty = center.y - pinch.worldCenter.y * scale;
  setTransform();
}
svg.addEventListener('pointerdown', (e) => {
  e.preventDefault();
  svg.setPointerCapture(e.pointerId);
  activePointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (activePointers.size >= 2) {
    beginPinchIfNeeded();
    return;
  }
  dropTarget = null;
  const addButton = closestAddButton(e.target);
  if (addButton) {
    const parentIndex = Number(addButton.dataset.parentIndex);
    activePointers.delete(e.pointerId);
    suppressTapUntil = Date.now() + 320;
    if (window.KardLeafMindMap && window.KardLeafMindMap.onNodeAddChild) {
      window.KardLeafMindMap.onNodeAddChild(Number.isFinite(parentIndex) ? parentIndex : -1);
    }
    return;
  }
  const nodeGroup = closestNode(e.target);
  const world = screenToWorld(e.clientX, e.clientY);
  const index = nodeGroup ? Number(nodeGroup.dataset.index) : null;
  const node = Number.isFinite(index) ? findNodeByIndex(index) : null;
  drag = {
    id: e.pointerId,
    startX: e.clientX,
    startY: e.clientY,
    lastX: e.clientX,
    lastY: e.clientY,
    moved: false,
    mode: node ? 'node' : 'pan',
    node,
    nodeStartX: node ? node.x : 0,
    nodeStartY: node ? node.y : 0,
    worldStartX: world.x,
    worldStartY: world.y,
  };
  render();
});
svg.addEventListener('pointermove', (e) => {
  if (!activePointers.has(e.pointerId)) return;
  e.preventDefault();
  activePointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (activePointers.size >= 2) {
    updatePinch();
    return;
  }
  if (!drag || drag.id !== e.pointerId) return;
  const dx = e.clientX - drag.startX;
  const dy = e.clientY - drag.startY;
  if (Math.abs(dx) + Math.abs(dy) > 5) drag.moved = true;
  if (drag.mode === 'node' && drag.node) {
    const world = screenToWorld(e.clientX, e.clientY);
    drag.node.x = drag.nodeStartX + (world.x - drag.worldStartX);
    drag.node.y = drag.nodeStartY + (world.y - drag.worldStartY);
    dropTarget = findDropTargetFor(drag.node);
    render();
  } else {
    tx += e.clientX - drag.lastX;
    ty += e.clientY - drag.lastY;
    setTransform();
  }
  drag.lastX = e.clientX;
  drag.lastY = e.clientY;
});
function finishPointer(e) {
  if (activePointers.has(e.pointerId)) activePointers.delete(e.pointerId);
  if (pinch) {
    pinch = null;
    drag = null;
    suppressTapUntil = Date.now() + 260;
    render();
    return;
  }
  if (!drag || drag.id !== e.pointerId) return;
  const item = drag;
  const target = dropTarget;
  drag = null;
  dropTarget = null;
  if (item.moved && item.node && !item.node.root) {
    if (target && window.KardLeafMindMap && window.KardLeafMindMap.onNodeReparent) {
      window.KardLeafMindMap.onNodeReparent(item.node.index, target.index);
      suppressTapUntil = Date.now() + 320;
      render();
      return;
    }
    item.node.x = item.nodeStartX;
    item.node.y = item.nodeStartY;
    render();
    return;
  }
  render();
  if (!item.moved && item.node && !item.node.root && Date.now() > suppressTapUntil) {
    const now = Date.now();
    if (now - lastTap.time > 180 || lastTap.nodeIndex !== item.node.index) {
      if (window.KardLeafMindMap && window.KardLeafMindMap.onNodeClick) {
        window.KardLeafMindMap.onNodeClick(item.node.index);
      }
    }
    lastTap = { time: now, nodeIndex: item.node.index };
  }
}
svg.addEventListener('pointerup', finishPointer);
svg.addEventListener('pointercancel', finishPointer);
function zoomAt(clientX, clientY, factor) {
  const world = screenToWorld(clientX, clientY);
  scale = clamp(scale * factor, .42, 2.35);
  tx = clientX - world.x * scale;
  ty = clientY - world.y * scale;
  setTransform();
}
function zoomBy(factor) {
  const rect = svg.getBoundingClientRect();
  zoomAt(rect.width / 2, rect.height / 2, factor);
}
document.getElementById('zoomOut').onclick = () => zoomBy(.86);
document.getElementById('zoomIn').onclick = () => zoomBy(1.16);
document.getElementById('reset').onclick = resetView;
render();
setTimeout(resetView, 0);
</script>
</body>
</html>
""".trimIndent()
}
