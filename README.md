# KardLeaf / 卡叶笔记

<p align="center">
  <strong>一个以本地 Markdown 文件为核心的 Android 笔记应用</strong>
</p>

<p align="center">
  <a href="https://github.com/waikr/KardLeaf/releases"><img src="https://img.shields.io/github/downloads/waikr/KardLeaf/total?label=Downloads" alt="GitHub Downloads" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="Apache License 2.0" /></a>
  <img src="https://img.shields.io/badge/Android-6.0%2B-brightgreen" alt="Android 6.0+" />
  <img src="https://img.shields.io/badge/Markdown-local_files-blue" alt="Local Markdown files" />
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF" alt="Kotlin Compose" />
</p>

<p align="center">
  <a href="https://github.com/waikr/KardLeaf/releases">下载最新版</a>
  ·
  <a href="#界面预览">界面预览</a>
  ·
  <a href="#功能特性">功能特性</a>
  ·
  <a href="#如何运行">如何运行</a>
</p>

<p align="center">
  <img src="docs/images/home-cards.jpg" width="220" alt="KardLeaf 首页卡片与分类" />
  <img src="docs/images/editor-editing.jpg" width="220" alt="KardLeaf Markdown 编辑器" />
  <img src="docs/images/navigation-drawer.jpg" width="220" alt="KardLeaf 侧边栏" />
</p>

KardLeaf 是一款轻量、简洁、偏本地优先的 Android Markdown 笔记应用。它不会把正文锁进应用私有数据库，而是把笔记保存为真实的 `.md` / `.txt` 文件，方便备份、迁移、同步，也方便继续用 Obsidian、VS Code、Typora 等工具管理同一批文件。

---

## 为什么选择 KardLeaf

<table>
  <tr>
    <td width="42" align="center">📁</td>
    <td><strong>本地文件优先</strong><br />笔记保存在你选择的目录中，正文是普通 Markdown / TXT 文件，迁移和备份更直接。</td>
  </tr>
  <tr>
    <td width="42" align="center">🗂️</td>
    <td><strong>分类清晰</strong><br />支持多级目录、顶部分类、下拉分类面板和侧边栏导航，适合按学习、生活、项目等场景整理。</td>
  </tr>
  <tr>
    <td width="42" align="center">📝</td>
    <td><strong>Markdown 编辑与预览</strong><br />支持标题、列表、任务、图片、代码块、数学公式、目录和常用格式按钮。</td>
  </tr>
  <tr>
    <td width="42" align="center">🔗</td>
    <td><strong>URL 新建笔记</strong><br />支持通过 URL 快速新建笔记，适合收集网页、资料链接和临时想法。</td>
  </tr>
  <tr>
    <td width="42" align="center">🧩</td>
    <td><strong>移动端资料整理</strong><br />首页卡片、搜索、排序、收藏、置顶、回收站、归档、历史版本和备注面板都围绕手机使用优化。</td>
  </tr>
  <tr>
    <td width="42" align="center">🎨</td>
    <td><strong>可调整界面</strong><br />支持主题、强调色、首页底部工具栏、笔记顶部栏、侧边栏方案和编辑器按钮位置等自定义。</td>
  </tr>
  <tr>
    <td width="42" align="center">🔒</td>
    <td><strong>隐私友好</strong><br />不强制登录账号，不强制把笔记上传到云端；正文文件仍由你自己掌控。</td>
  </tr>
</table>

---

## 界面预览

<p align="center">
  <img src="docs/images/home-cards.jpg" width="220" alt="首页卡片、分类和底部工具栏" />
  <img src="docs/images/navigation-drawer.jpg" width="220" alt="侧边栏导航和数据卡片" />
  <img src="docs/images/settings.jpg" width="220" alt="设置页" />
</p>

<p align="center">
  <img src="docs/images/editor-editing.jpg" width="220" alt="Markdown 编辑器" />
  <img src="docs/images/editor-outline-panel.jpg" width="220" alt="Markdown 目录侧滑面板" />
  <img src="docs/images/editor-detail-panel.jpg" width="220" alt="笔记属性和备注面板" />
</p>

<p align="center">
  <img src="docs/images/history.jpg" width="220" alt="历史版本" />
  <img src="docs/images/drawing.jpg" width="220" alt="绘图功能" />
  <img src="docs/images/tasks.jpg" width="220" alt="任务清单" />
</p>

---

## 功能特性

<table>
  <tr>
    <td width="42" align="center">🏠</td>
    <td><strong>首页</strong><br />卡片式笔记列表、分类筛选、搜索、排序、收藏、置顶、图片缩略图和底部快捷工具栏。</td>
  </tr>
  <tr>
    <td width="42" align="center">✍️</td>
    <td><strong>编辑器</strong><br />Markdown 编辑、预览、目录跳转、格式按钮、本地图片引用、数学公式和任务列表。</td>
  </tr>
  <tr>
    <td width="42" align="center">📚</td>
    <td><strong>笔记管理</strong><br />多级文件夹、草稿、归档、回收站、历史版本、备注记录、属性统计和外部文件刷新。</td>
  </tr>
  <tr>
    <td width="42" align="center">✅</td>
    <td><strong>任务</strong><br />支持笔记中的 Markdown 任务识别，也支持独立任务清单和提醒入口。</td>
  </tr>
  <tr>
    <td width="42" align="center">🖼️</td>
    <td><strong>图片与绘图</strong><br />支持本地图片预览、首页首图缩略图，以及内置绘图功能。</td>
  </tr>
  <tr>
    <td width="42" align="center">☁️</td>
    <td><strong>同步思路</strong><br />以本地文件夹为基础，可配合 WebDAV、Syncthing、网盘目录或手动复制进行多端同步。</td>
  </tr>
</table>

---

## 本地 Markdown 工作流

KardLeaf 的核心是 **文件夹 + Markdown 文件**。

你可以把笔记库放在普通目录中，也可以把它作为 Obsidian、VS Code、Typora 等 Markdown 工具的移动端补充：

```text
KardLeaf 笔记库
├── 学习
│   ├── 数学
│   └── 物理
├── 生活
└── 办公
```

- 电脑上整理长文档，手机上快速记录和查看。
- 其他编辑器修改 `.md` / `.txt` 文件后，KardLeaf 可刷新索引。
- Room 数据库主要用于索引、缓存和状态管理，正文仍保存在真实文件里。

KardLeaf 不是 Obsidian 官方客户端，也不依赖 Obsidian 的专有功能；它更适合作为本地 Markdown 工作流的 Android 补充。

---

## 下载

<p align="center">
  <a href="https://github.com/waikr/KardLeaf/releases"><img src="https://img.shields.io/github/downloads/waikr/KardLeaf/total?label=Downloads" alt="GitHub Downloads" /></a>
</p>

请前往 [Releases](https://github.com/waikr/KardLeaf/releases) 下载最新 APK。下载数量以上方 GitHub Releases 统计为准。

---

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Kotlin Coroutines / Flow
- Android Storage Access Framework
- WebView
- markdown-it
- KaTeX
- Gradle Kotlin DSL

CodeMirror编辑器实现参考了 swarmnote-editor，用于改善 Markdown 编辑体验。

---

## 如何运行

环境要求：

- Android Studio
- JDK 17
- Android 设备或模拟器
- minSdk 23
- targetSdk 34

构建方式：

```bash
git clone https://github.com/waikr/KardLeaf.git
cd KardLeaf
./gradlew assembleDebug
```

也可以直接使用 Android Studio 打开项目，然后运行 `app` 模块。

---

## 当前状态

KardLeaf 仍在持续开发和优化中。当前重点是稳定编辑体验、优化长文本性能、完善图片加载、历史版本、外部同步和整体界面细节。

---

## License

本项目基于 Apache License 2.0 开源。

请在使用、修改或分发本项目代码时遵守相应开源协议。
