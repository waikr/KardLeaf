# KardLeaf / 卡叶笔记

![GitHub Downloads](https://img.shields.io/github/downloads/waikr/KardLeaf/total?label=Downloads)

KardLeaf 是一款轻量、简洁、以本地文件为核心的 Android Markdown 笔记应用。

它的目标是让用户把笔记保存在自己选择的本地目录中，而不是锁定在应用私有数据库里。笔记内容以 `.md` / `.txt` 文件形式保存，应用内部使用 Room 数据库建立索引和缓存，从而兼顾数据可迁移性与移动端加载性能。

---

## 功能特性

* 本地 Markdown / TXT 笔记管理
* 通过 Android SAF 选择和访问本地笔记目录
* 卡片式首页，支持笔记预览
* 支持文件夹分类和多级目录
* 支持 Markdown 编辑与预览
* 支持常用 Markdown 工具栏
* 支持 KaTeX 数学公式预览
* 支持本地图片引用预览
* 支持搜索、排序、收藏、置顶
* 支持归档与回收站
* 支持笔记提醒和系统通知
* 支持历史版本记录与恢复
* 支持外部文件变更同步
* 支持数据导入与导出

---

## 项目定位

KardLeaf 适合希望在 Android 手机上管理本地 Markdown 笔记的用户。

它更偏向以下使用方式：

* 笔记以真实文件形式保存，方便备份和迁移
* 可以和文件管理器、同步盘、桌面 Markdown 编辑器一起使用
* 不依赖账号登录或云端服务
* 首页以卡片方式快速浏览笔记
* 编辑时保持轻量，预览时再进行完整 Markdown 渲染

---

## 技术栈

* Kotlin
* Jetpack Compose
* Material 3
* Room
* Kotlin Coroutines / Flow
* Android Storage Access Framework
* WebView
* markdown-it
* KaTeX
* Gradle Kotlin DSL

---

## 项目结构

```text
app/src/main/java/com/kangle/kardleaf
├── MainActivity.kt
├── data
│   ├── database        # Room 数据库、Dao、Entity、Migration
│   ├── model           # 数据模型
│   ├── receiver        # 通知、提醒、目录变化监听
│   ├── repository      # 笔记仓库、配置管理、元数据管理
│   └── utils           # 工具类
└── ui
    ├── DashboardScreen.kt
    ├── EditorScreen.kt
    ├── AppDrawer.kt
    ├── MarkwonMarkdownEditText.kt
    ├── EditorUtils.kt
    └── theme
```

---

## 本地文件与数据库设计

KardLeaf 使用“本地文件 + 数据库索引”的方式管理笔记。

笔记正文保存在用户选择的本地目录中，格式为 `.md` 或 `.txt` 文件。Room 数据库主要用于保存索引、缓存和应用状态，例如标题、预览内容、修改时间、收藏、置顶、归档、回收站、历史版本等信息。

这样做的好处是：

* 用户可以直接拿到自己的 Markdown 文件
* 笔记可以被其他 Markdown 编辑器打开
* 应用首页不需要每次都重新读取所有文件全文
* 大量笔记场景下加载更快
* 数据迁移和备份更简单

---

## Markdown 编辑与预览

KardLeaf 支持编辑模式和预览模式切换。

编辑器提供常用 Markdown 输入辅助，包括标题、加粗、斜体、删除线、链接、代码、引用、列表、待办列表、公式等。

预览部分使用 WebView 渲染 Markdown，支持较复杂的 Markdown 内容，包括任务列表、数学公式和本地图片引用。

---

## 外部文件同步

KardLeaf 会监听用户选择的笔记目录。

当用户通过文件管理器、同步工具或其他 Markdown 编辑器修改笔记文件时，应用可以检测外部变化并刷新对应的笔记索引。

这让 KardLeaf 可以和其他本地文件工具配合使用，而不是把数据封闭在应用内部。

---

## 权限说明

应用可能会使用以下权限：

* 文件夹访问权限：用于读取和保存用户选择目录中的 Markdown 文件
* 通知权限：用于笔记提醒
* 精确闹钟权限：用于按指定时间触发提醒
* 开机广播权限：用于设备重启后恢复提醒

KardLeaf 不依赖账号登录，也不需要云端服务。笔记内容保存在用户选择的本地目录中。

---

## 如何运行

### 环境要求

* Android Studio
* JDK 17
* Android 设备或模拟器
* minSdk 23
* targetSdk 34

### 构建方式

```bash
git clone https://github.com/waikr/KardLeaf.git
cd KardLeaf
./gradlew assembleDebug
```

也可以直接使用 Android Studio 打开项目，然后运行 `app` 模块。

---

## 当前状态

KardLeaf 目前仍处于持续开发和优化阶段，核心功能已经可用，但仍可能存在未完善的细节。

后续计划包括：

* 继续优化长文本编辑性能
* 改进 Markdown 编辑体验
* 完善历史版本管理
* 优化大型笔记库扫描速度
* 增强同步冲突处理
* 补充更多测试和文档

---

## License

本项目基于 Apache License 2.0 开源。

请在使用、修改或分发本项目代码时遵守相应开源协议。
