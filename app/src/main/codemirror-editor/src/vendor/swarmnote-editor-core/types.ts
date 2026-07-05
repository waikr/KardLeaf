import type { Extension } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';
import type { EditorEvent, EditorEventType } from './events';

/**
 * 编辑器命令类型枚举
 * 
 * 定义了所有可通过 execCommand 执行的编辑操作。
 * 这些命令涵盖了文本格式化、插入元素、行操作、搜索等功能。
 */
export enum EditorCommandType {
  /** 撤销 */
  Undo = 'undo',
  /** 重做 */
  Redo = 'redo',
  /** 切换加粗 */
  ToggleBold = 'toggleBold',
  /** 切换斜体 */
  ToggleItalic = 'toggleItalic',
  /** 切换行内代码 */
  ToggleCode = 'toggleCode',
  /** 切换删除线 */
  ToggleStrike = 'toggleStrike',
  /** 切换高亮 */
  ToggleHighlight = 'toggleHighlight',
  /** 切换引用块 */
  ToggleBlockquote = 'toggleBlockquote',
  /** 设置标题级别 */
  ToggleHeading = 'toggleHeading',
  /** 循环切换标题级别 */
  CycleHeading = 'cycleHeading',
  /** 切换有序列表 */
  ToggleOrderedList = 'toggleOrderedList',
  /** 切换无序列表 */
  ToggleUnorderedList = 'toggleUnorderedList',
  /** 切换任务列表 */
  ToggleCheckList = 'toggleCheckList',
  /** 插入代码块 */
  InsertCodeBlock = 'insertCodeBlock',
  /** 插入水平分割线 */
  InsertHorizontalRule = 'insertHorizontalRule',
  /** 插入表格 */
  InsertTable = 'insertTable',
  /** 插入链接 */
  InsertLink = 'insertLink',
  /** 插入图片 */
  InsertImage = 'insertImage',
  /** 全选 */
  SelectAll = 'selectAll',
  /** 复制当前行 */
  DuplicateLine = 'duplicateLine',
  /** 删除当前行 */
  DeleteLine = 'deleteLine',
  /** 增加缩进 */
  IndentMore = 'indentMore',
  /** 减少缩进 */
  IndentLess = 'indentLess',
  /** 在当前行后插入新行 */
  InsertLineAfter = 'insertLineAfter',
  /** 排序选中行 */
  SortSelectedLines = 'sortSelectedLines',
  /** 跳转到哈希锚点 */
  JumpToHash = 'jumpToHash',
  /** 查找下一个 */
  FindNext = 'findNext',
  /** 查找上一个 */
  FindPrevious = 'findPrevious',
  /** 获取焦点 */
  Focus = 'focus',
  /** 失去焦点 */
  Blur = 'blur',
  /** 滚动使选区可见 */
  ScrollSelectionIntoView = 'scrollSelectionIntoView',
}

/**
 * 编辑器选区范围
 * 
 * 表示 CodeMirror 中的一个文本选区，包含起始和结束位置。
 * anchor 和 head 是 CodeMirror 的术语：
 * - anchor: 选区的固定端（用户开始选择的位置）
 * - head: 选区的活动端（用户拖动到的位置）
 * - from/to: 规范化后的起止位置（from <= to）
 */
export interface EditorSelectionRange {
  /** 选区锚点位置 */
  anchor: number;
  /** 选区头部位置 */
  head: number;
  /** 选区起始位置（较小值） */
  from: number;
  /** 选区结束位置（较大值） */
  to: number;
}

/**
 * 代码块渲染模式
 *
 * 控制代码块在编辑器中的显示和交互方式：
 *
 * - `off` - 关闭特殊渲染，仅显示原始 Markdown 语法
 * - `inline`（默认）-  fence 标记折叠为头部/尾部 widget，代码内容保持在 CM 文档流中，
 *   支持完整的语法高亮和直接编辑
 * - `auto` - 当光标不在代码块内时，整个块折叠为只读卡片；
 *   光标进入时显示原始 Markdown
 * - `toggle` - 始终显示为只读卡片；通过 "Code" / "Render" 按钮手动切换每个块的源码可见性
 */
export type CodeBlockMode = 'off' | 'inline' | 'auto' | 'toggle';

/**
 * 编辑器功能开关配置
 *
 * 控制编辑器内核（in-core）功能的启用/禁用。v0.1 起以下 7 个字段已迁移
 * 到 plugin 形态、不再受 features 控制：
 *
 * - `mathRendering` → `@swarmnote/editor-core/plugins/math`
 * - `mermaidRendering` → `@swarmnote/editor-core/plugins/mermaid`
 * - `blockImageRendering` → `@swarmnote/editor-core/plugins/blockImage`（含 table）
 * - `rawHtmlRendering` → `@swarmnote/editor-core/plugins/rawHtml`
 * - `codeBlockMode` → `@swarmnote/editor-core/plugins/codeBlock`（`mode` 选项传入）
 * - `smartPaste` → `@swarmnote/editor-core/plugins/smartPaste`
 * - `admonition` → `@swarmnote/editor-core/plugins/admonition`
 *
 * 保留的 5 个字段语义不变。`table` 由 blockImage plugin 同时注入（v0.1 行为）。
 */
export interface EditorFeatureToggles {
  /** Markdown 语法高亮增强 */
  markdownHighlight: boolean;
  /** Markdown 装饰（如隐藏标记符号） */
  markdownDecorations: boolean;
  /** 内联渲染（实时预览 Markdown 格式） */
  inlineRendering: boolean;
  /** 搜索功能 */
  search: boolean;
  /** 协作编辑（Yjs） */
  collaboration: boolean;
}

/** 编辑器外观主题 */
export type EditorAppearance = 'light' | 'dark';

/**
 * 编辑器主题配置
 * 
 * 定义编辑器的视觉样式，包括颜色、字体等。
 */
export interface EditorThemeConfig {
  /** 外观（亮色/暗色） */
  appearance: EditorAppearance;
  /** 字体族 */
  fontFamily?: string;
  /** 字体大小（像素） */
  fontSize?: number;
  lineHeight?: number;
  letterSpacing?: number;
  paragraphSpacing?: number;
  /** 颜色配置 */
  colors?: {
    /** 背景色 */
    background?: string;
    /** 前景色（文本颜色） */
    foreground?: string;
    /** 选区背景色 */
    selection?: string;
    /** 当前行高亮色 */
    activeLine?: string;
    /** 边框颜色 */
    border?: string;
    /** 代码块背景色 */
    codeBackground?: string;
    /** 标题颜色 */
    heading?: string;
    /** 链接颜色 */
    link?: string;
    /** 注释颜色 */
    comment?: string;
    /** 关键字颜色 */
    keyword?: string;
    /** 字符串颜色 */
    string?: string;
  };
}

/**
 * 编辑器设置
 * 
 * 包含编辑器的所有配置选项，分为基础设置、功能开关和主题配置。
 */
export interface EditorSettings {
  /** 只读模式 */
  readonly: boolean;
  /** 自动换行 */
  lineWrapping: boolean;
  /** 使用 Tab 字符缩进（而非空格） */
  indentWithTabs: boolean;
  /** Tab 宽度（空格数） */
  tabSize: number;
  /** 自动获取焦点 */
  autofocus: boolean;
  /** 拼写检查 */
  spellcheck: boolean;
  /** 可编辑状态 */
  editable: boolean;
  /** 显示行号 */
  showLineNumbers: boolean;
  /** 功能开关 */
  features: EditorFeatureToggles;
  /** 主题配置 */
  theme: EditorThemeConfig;
}

/**
 * 编辑器设置更新接口
 * 
 * 用于部分更新编辑器设置。features 和 theme 也可以部分更新。
 * 这种设计允许宿主应用只修改需要改变的配置项。
 */
export interface EditorSettingsUpdate
  extends Partial<Omit<EditorSettings, 'features' | 'theme'>> {
  /** 部分功能开关更新 */
  features?: Partial<EditorFeatureToggles>;
  /** 部分主题配置更新 */
  theme?: Partial<EditorThemeConfig>;
}

/**
 * 搜索状态
 * 
 * 表示搜索面板的当前状态，包括搜索关键词、选项和匹配结果信息。
 */
export interface SearchState {
  /** 搜索关键词 */
  query: string;
  /** 替换关键词 */
  replaceQuery: string;
  /** 区分大小写 */
  caseSensitive: boolean;
  /** 全字匹配 */
  wholeWord: boolean;
  /** 使用正则表达式 */
  regexp: boolean;
  /** 搜索面板是否打开 */
  isOpen: boolean;
  /** 当前激活的匹配项索引（从 0 开始） */
  activeMatchIndex: number | null;
  /** 总匹配数 */
  totalMatches: number;
}

/**
 * 编辑器协作配置
 * 
 * 配置 Yjs CRDT 协作编辑所需的参数。
 */
export interface EditorCollaborationConfig {
  /** Yjs 文档实例 */
  ydoc: unknown;
  /** 片段名称（默认为 'document'） */
  fragmentName?: string;
  /** 本地操作来源标识 */
  localOrigin?: string;
  /** 远程操作来源标识 */
  remoteOrigin?: string;
  /**
   * 可选的 `Awareness` 实例（来自 `y-protocols/awareness`），绑定到与
   * `ydoc` 相同的 Y.Doc。提供时，编辑器会通过 `yCollab(ytext, awareness)`
   * 连接它，使 `y-codemirror.next` 能够渲染远程光标和用户名标签。
   * 
   * 生命周期管理（创建、`setLocalState`、销毁、网络推送/应用）由调用者负责——
   * 编辑器只读取它。使用 `unknown` 类型以避免在此处硬依赖 `y-protocols`。
   */
  awareness?: unknown;
}

/**
 * 编辑器属性（创建编辑器时的配置）
 * 
 * 传递给 createEditor 函数的完整配置对象。
 */
export interface EditorProps {
  /** 初始文本内容 */
  initialText: string;
  /** 初始选区位置 */
  initialSelection?: EditorSelectionRange;
  /** 编辑器设置 */
  settings: EditorSettings;
  /** 初始搜索状态 */
  initialSearchState?: SearchState | null;
  /** 是否自动获取焦点 */
  autofocus?: boolean;
  /** 协作编辑配置 */
  collaboration?: EditorCollaborationConfig;
  /** 事件回调函数 */
  onEvent?: (event: EditorEvent) => void;
  /**
   * 可选的图片 URL 解析器。接收 Markdown 源中的原始 `src` 字符串，
   * 返回实际赋值给 `<img src>` 的 URL。适用于将工作区相对路径映射到
   * 平台特定协议（如 Tauri 的 `asset://`）。
   *
   * 解析器可以返回 Promise。在解析完成前，widget 会渲染为无 src 状态
   * （仅占位高度）。如果拒绝或加载错误，widget 会使用退避策略重试最多 3 次，
   * 然后显示回退内容。
   *
   * @deprecated v0.1 改用 `host.resolveImage`。该字段在 v0.1 仍会被透明桥接到
   * `host.resolveImage`，但会在 v1.0 移除。同时提供 `host.resolveImage` 时，
   * `host.*` 优先，并触发一次 `console.warn`。
   */
  imageResolver?: (src: string) => string | Promise<string>;
  /**
   * 可选的文件上传处理函数，当用户拖拽文件到编辑器时调用。
   * 接收一个 `File` 对象，返回 URL 和可选的 alt 文本，
   * 这些内容将以 `![alt](url)` Markdown 格式插入到拖拽位置。
   * 如果省略，文件拖拽会被 preventDefault 并静默忽略。
   *
   * @deprecated v0.1 改用 `host.uploadFile`。该字段在 v0.1 仍会被透明桥接到
   * `host.uploadFile`，但会在 v1.0 移除。同时提供 `host.uploadFile` 时，
   * `host.*` 优先，并触发一次 `console.warn`。
   */
  uploadFile?: (file: File) => Promise<{ url: string; alt?: string }>;
  /**
   * 宿主提供的能力聚合体。Plugin 通过 `ctx.host` 访问这些能力，避免每个
   * 字段都单独传参。
   *
   * v0.1 起为推荐 API，deprecated 的顶层 `imageResolver` / `uploadFile`
   * 仍可工作但会触发桥接逻辑。详见 `EditorHostCapabilities`。
   */
  host?: EditorHostCapabilities;
  /**
   * 显式声明的 plugin 列表。`createEditor` 会按数组顺序调用每个 plugin 的
   * `setup(ctx)`，收集它们注册的命令 / CM6 扩展 / Markdown 渲染规则。
   *
   * 内置功能 plugin (math / table / mermaid / admonition / codeBlock /
   * blockImage / rawHtml / smartPaste) 默认不启用，需要显式传入。
   */
  plugins?: EditorPlugin[];
}

/**
 * 编辑器控制器接口
 * 
 * 这是外部宿主应用与编辑器交互的主要接口。
 * 提供了文本操作、选区管理、设置更新、搜索控制等功能。
 */
export interface EditorControl {
  /** CodeMirror 6 编辑器视图（只读） */
  readonly view: EditorView;

  /** 检查是否支持指定命令 */
  supportsCommand(name: EditorCommandType | string): boolean;
  /** 执行指定命令 */
  execCommand(name: EditorCommandType | string, ...args: unknown[]): unknown;

  /** 获取全文 */
  getText(): string;
  /** 设置全文 */
  setText(text: string): void;
  /** 插入文本 */
  insertText(text: string): void;
  /** 替换选区 */
  replaceSelection(text: string): void;

  /** 获取当前选区 */
  getSelection(): EditorSelectionRange;
  /** 设置选区 */
  select(anchor: number, head?: number): void;

  /** 获取编辑器设置 */
  getSettings(): EditorSettings;
  /** 更新编辑器设置 */
  updateSettings(settings: EditorSettingsUpdate): void;

  /** 获取搜索状态 */
  getSearchState(): SearchState | null;
  /** 设置搜索状态 */
  setSearchState(state: SearchState | null, source?: string): void;
  /** 清除搜索 */
  clearSearch(source?: string): void;

  /** 获取选区格式状态 */
  getSelectionFormatting(): SelectionFormatting;

  /**
   * 设置覆盖编辑器底部的 UI 元素的高度（像素）。
   * 
   * 原子性地驱动两个配置：
   * - `scrollMargins.bottom` - 自动 scrollIntoView 时光标会停留在遮挡物上方
   * - `.cm-content` padding-bottom - 用户可以手动滚动最后几行经过遮挡物
   * 
   * 传入 0 禁用此功能。当遮挡物高度变化时调用。
   */
  setScrollBottomMargin(px: number): void;

  /** 获取焦点 */
  focus(): void;
  /** 失去焦点 */
  blur(): void;
  /** 销毁编辑器 */
  destroy(): void;
}

/** 列表类型 */
export type ListType = 'ordered' | 'unordered' | 'check';

/**
 * 选区格式状态
 * 
 * 描述当前选区或光标位置的格式状态，用于工具栏按钮的状态显示。
 * 例如：如果 bold 为 true，则加粗按钮应该显示为激活状态。
 */
export interface SelectionFormatting {
  /** 是否加粗 */
  bold: boolean;
  /** 是否斜体 */
  italic: boolean;
  /** 是否行内代码 */
  code: boolean;
  /** 是否删除线 */
  strikethrough: boolean;
  /** 是否高亮 */
  highlight: boolean;
  /** 标题级别（0 表示非标题） */
  heading: number;
  /** 列表类型 */
  listType: ListType | null;
  /** 列表层级 */
  listLevel: number;
  /** 是否在引用块中 */
  inBlockquote: boolean;
  /** 是否在代码块中 */
  inCodeBlock: boolean;
}

export const DEFAULT_SELECTION_FORMATTING: SelectionFormatting = {
  bold: false,
  italic: false,
  code: false,
  strikethrough: false,
  highlight: false,
  heading: 0,
  listType: null,
  listLevel: 0,
  inBlockquote: false,
  inCodeBlock: false,
};

export const DEFAULT_SEARCH_STATE: SearchState = {
  query: '',
  replaceQuery: '',
  caseSensitive: false,
  wholeWord: false,
  regexp: false,
  isOpen: false,
  activeMatchIndex: null,
  totalMatches: 0,
};

export const DEFAULT_THEME: EditorThemeConfig = {
  appearance: 'light',
};

// ---------------------------------------------------------------------------
// Plugin SDK (v0.1)
// ---------------------------------------------------------------------------

/**
 * 可释放资源接口。`register*` 系列方法返回的对象，调用 `dispose()` 会撤销
 * 该次注册。所有未被 plugin 显式 dispose 的 disposable 会在 editor 销毁时
 * 自动按反向顺序 dispose。
 */
export interface Disposable {
  dispose(): void;
}

/**
 * 命令执行时传入的上下文。Plugin 通过它访问 CM6 view / state 与当前选区。
 * v0.1 字段封闭，不允许扩充——如需新增能力，走 v0.2 minor bump。
 */
export interface EditorCommandContext {
  /** CodeMirror 6 EditorView */
  view: EditorView;
  /** 当前选区（规范化后） */
  selection: EditorSelectionRange;
}

/**
 * 命令注册规约。Plugin 通过 `ctx.registerCommands([...])` 注册命令，由
 * `EditorControl.execCommand(id)` 调度执行。
 *
 * v0.1 字段封闭。`group` / `category` / `keybinding` / `argSchema` 等
 * 额外元数据延至 v0.2+。
 */
export interface EditorCommandSpec {
  /** 命令唯一 id。冲突时 last-wins + console.warn */
  id: string;
  /** 命令显示标题（命令面板用） */
  title?: string;
  /** 详细描述 */
  description?: string;
  /** 图标标识（语义化字符串，宿主决定如何渲染） */
  icon?: string;
  /** 可选门控：返回 false 时 `execCommand` 直接 no-op */
  when?: (ctx: EditorCommandContext) => boolean;
  /** 命令执行体 */
  /** 命令执行体。`args` 是 `EditorControl.execCommand(id, ...args)` 透传的可变参数。 */
  run: (ctx: EditorCommandContext, ...args: unknown[]) => void | Promise<void>;
}

/**
 * 宿主能力聚合。`host: EditorHostCapabilities` 通过 `EditorProps.host`
 * 传入，并通过 `EditorPluginContext.host` 暴露给 plugin。
 *
 * 稳定字段（stable since v0.1）：`resolveImage` / `uploadFile` / `openLink`。
 * 稳定字段（stable since v0.3）：`getSlashItems`。
 *
 * v0.3 移除 v0.1 `searchNotes` 占位字段（被 `getWikilinkItems` 替代，Phase B 加入）。
 */
export interface EditorHostCapabilities {
  /**
   * 解析 Markdown 中的相对图片 src → 实际可加载 URL。
   * 用例：Tauri 的 workspace 相对路径 → `asset://` URL。
   */
  resolveImage?: (src: string) => string | Promise<string>;
  /**
   * 处理粘贴 / 拖放的文件，返回可插入 Markdown 的 url + alt。
   */
  uploadFile?: (file: File) => Promise<{ url: string; alt?: string }>;
  /**
   * 打开外部链接。Tauri 等限制 `window.open` 的环境必须实现。
   */
  openLink?: (url: string) => void | Promise<void>;
  /**
   * 返回 slash 菜单的业务候选项列表。
   *
   * 在 slash trigger active 期间，`slashCommandPlugin` 在 query 切换时
   * debounce 150ms 后调用本函数，并把结果与 plugin provider 的结果合并。
   *
   * `signal` 在 trigger 失活或 query 切换时 abort——host 实现 SHOULD
   * 检查 `signal.aborted` 并在 abort 后尽早返回（fetch / DB 查询均接受
   * `signal`）。即便 host 仍 resolve，runtime 也会丢弃 stale 结果。
   *
   * Stable since v0.3。
   */
  getSlashItems?: (query: string, signal: AbortSignal) => Promise<SlashItem[]>;
  /**
   * 返回 wikilink 菜单的业务候选项（典型：笔记标题）。
   *
   * 与 `getSlashItems` 同样 debounce + AbortSignal 语义；由 `wikilinkPlugin`
   * 调用并与 plugin provider 结果合并。
   *
   * Stable since v0.3 (phase B)。
   */
  getWikilinkItems?: (query: string, signal: AbortSignal) => Promise<WikilinkItem[]>;
  /**
   * 返回 selection toolbar 业务 actions（同步签名）。
   *
   * 在每次 selection 变化、toolbar 即将激活时调用。host 可基于当前选区
   * 内容返回业务 actions（如「跳转 wikilink」）；返回 [] 表示无追加。
   *
   * Stable since v0.3 (phase C)。
   */
  getSelectionToolbarActions?: (selection: {
    from: number;
    to: number;
  }) => SelectionToolbarAction[];
}

/**
 * Markdown 渲染规则。Plugin 通过 `ctx.registerMarkdownRenderer(rule)`
 * 声明对某个 lezer markdown 节点的渲染贡献。
 *
 * v0.1 形态为「按节点类型注入 CM6 扩展」，与现有 `createBlockXxxExtension`
 * 内部实现对齐。冲突策略：同 `nodeType` 多次注册时 last-wins + console.warn。
 */
export interface MarkdownRenderRule {
  /** lezer markdown 节点类型名，例如 'CodeBlock' / 'Image' / 'Table' */
  nodeType: string;
  /** 对应该节点类型的 CM6 扩展 */
  extension: Extension | readonly Extension[];
}

/**
 * Slash 菜单候选项。
 *
 * Plugin 通过 `ctx.registerSlashItems(provider)` 注册的 provider 在被
 * `slashCommandPlugin` 调用时返回这些项；host 通过 `host.getSlashItems`
 * 也返回这种 shape。
 *
 * **Comlink 序列化约束**：当 host 是 React Native 走 WebView Comlink RPC 时，
 * items 跨进程序列化。**必须使用 `commandId` + `commandArgs` 路径**，
 * 不可用 `run` closure —— 函数不可跨 Comlink 序列化。桌面 host（同进程）
 * 可以用 `run` closure 实现自定义动作。建议第三方 plugin 全部走 commandId
 * 路径以保证跨平台兼容。
 *
 * Stable since v0.3。Shape 在 v0.x 内只可新增 optional 字段。
 */
export interface SlashItem {
  /** 全局唯一 id（plugin-id 前缀推荐：'math.insertBlock'） */
  id: string;
  /** 显示标题 */
  title: string;
  /** 描述（可选，副标题渲染） */
  description?: string;
  /** 图标语义化标识（host 决定如何渲染） */
  icon?: string;
  /** 额外 fuzzy 匹配关键词，与 title 一起参与匹配 */
  keywords?: string[];
  /** 分组（host 端可分组渲染，如 "插入" / "跳转"） */
  section?: string;
  /**
   * 排序权重（可选）。若设置，slash plugin runtime 在排序时使用本字段
   * 覆盖 provider 默认 priority。Host 可用此字段把 MRU items lift 到顶部
   * （典型：host 给 MRU items 赋 priority 300+）。
   */
  priority?: number;
  /** 引用已注册的 EditorCommandSpec.id；选中时优先走 execCommand 路径 */
  commandId?: string;
  /**
   * 传给 commandId 的参数（typical 用例：`toggleHeading` 需要 level 参数）。
   * 仅在 `commandId` 也设置时生效；slash plugin 内部 `exec.fn(commandId, ...args)`。
   *
   * 必须是 JSON-serializable —— host 通过 Comlink 跨 WebView 时，函数 / Symbol /
   * EditorView 等都不可序列化。
   */
  commandArgs?: unknown[];
  /**
   * 直接 commit 函数；优先级低于 commandId（两者都填时 commandId 优先）。
   *
   * **不可跨 Comlink** —— RN host 通过 WebView 序列化 items 时，函数会丢失
   * 为 `undefined`。RN host items 必须用 `commandId + commandArgs` 路径。
   * 仅桌面 host（同进程）可用 `run` 闭包。
   */
  run?: (ctx: { view: EditorView; range: { from: number; to: number } }) => void | Promise<void>;
}

/**
 * Slash 菜单候选项提供方。Plugin 通过
 * `ctx.registerSlashItems(provider)` 声明对 slash 菜单的贡献。
 *
 * Stable since v0.3。
 */
export interface SlashItemProvider {
  /** Provider 唯一 id，用于排序去重 */
  id: string;
  /** 排序优先级，默认 100；host 注入的 provider 默认 200 */
  priority?: number;
  /**
   * 在 `query` 上下文下返回候选项（同步或异步）。
   *
   * `signal` 在 trigger 失活或 query 切换时 abort——async 实现应监听
   * `signal.aborted` 提前结束工作。
   */
  provide(query: string, signal: AbortSignal): SlashItem[] | Promise<SlashItem[]>;
}

/**
 * Wikilink 候选项。用户输入 `[[query` 触发 wikilink trigger，候选项
 * 通常是笔记标题；选中后插入 `[[note-title]]` 文本。
 *
 * Stable since v0.3 (phase B)。
 */
export interface WikilinkItem {
  /** 全局唯一 id（典型：note path 或 uuid） */
  id: string;
  /** 显示标题（即将插入的 `[[<title>]]` 中的 title） */
  title: string;
  /** 描述（如 path / breadcrumb，副标题渲染） */
  description?: string;
  /** 图标语义化标识 */
  icon?: string;
  /** 排序权重（可选），用法同 SlashItem.priority */
  priority?: number;
  /**
   * 选中后的 commit 语义：
   * - `replaceWithLink`（默认）：替换 `[[query` 为 `[[<title>]]`
   * - `jumpToNote`：替换为 `[[<title>]]` 后并触发 host 跳转（host 实现 onItemConfirmed）
   */
  commit?: 'replaceWithLink' | 'jumpToNote';
}

/**
 * Wikilink 候选项提供方。Plugin 通过
 * `ctx.registerWikilinkItems(provider)` 声明对 wikilink 菜单的贡献。
 *
 * Stable since v0.3 (phase B)。
 */
export interface WikilinkItemProvider {
  id: string;
  priority?: number;
  provide(query: string, signal: AbortSignal): WikilinkItem[] | Promise<WikilinkItem[]>;
}

/**
 * SelectionToolbar action：浮动 toolbar 上的按钮。
 *
 * 与 SlashItem 不同，selection toolbar action 永远绑定到已注册的
 * EditorCommandSpec.id —— toolbar 按钮天然是命令的别名。
 *
 * Stable since v0.3 (phase C)。
 */
export interface SelectionToolbarAction {
  /** 全局唯一 id（如 'bold', 'italic', 'strike'） */
  id: string;
  /** 鼠标 hover tooltip 文本 */
  title: string;
  /** 图标语义化标识 */
  icon: string;
  /** 必填：引用 EditorControl.execCommand 的命令名 */
  commandId: string;
  /**
   * 可选：返回当前 selection 上该 action 是否处于「激活」状态。
   * 用于按钮高亮（如选区已经 bold，bold 按钮显示 pressed 状态）。
   */
  isActive?: (state: import('@codemirror/state').EditorState) => boolean;
  /** 排序权重；同 SlashItem.priority */
  priority?: number;
}

/** 编辑器事件监听器（与 onEvent 同 shape） */
export type EditorEventListener = (event: EditorEvent) => void;

/**
 * Plugin 的运行时上下文。`setup(ctx)` 接收的唯一参数。
 *
 * Stable 表面（v0.x 不变 shape，只可新增 optional 字段）：
 * - `registerCommands` / `registerCmExtensions` / `registerMarkdownRenderer`（stable since v0.1）
 * - `host`（stable since v0.1）
 * - `registerSlashItems` / `on`（stable since v0.3）
 *
 * v0.3 移除 v0.1 `registerTrigger?` 占位（被 `registerSlashItems` /
 * `registerWikilinkItems` / `registerSelectionToolbarActions` 替代）。
 *
 * 所有 `register*` 都返回 `Disposable`，未显式持有也会由 host 在 destroy
 * 时自动 dispose（反向顺序）。
 */
export interface EditorPluginContext {
  /** 注册命令。冲突 last-wins。 */
  registerCommands(specs: EditorCommandSpec[]): Disposable;
  /** 注册 CM6 扩展。editor 创建期间收集，一次性挂载。 */
  registerCmExtensions(extensions: readonly Extension[]): Disposable;
  /** 注册 Markdown 渲染规则。冲突 last-wins。 */
  registerMarkdownRenderer(rule: MarkdownRenderRule): Disposable;
  /** 宿主能力聚合体。已合并 deprecated 顶层字段桥接。 */
  host: EditorHostCapabilities;

  /**
   * 注册 slash 菜单候选项提供方。在 `slashCommandPlugin` 加载时，
   * 这些 provider 会被收集；trigger active 期间被并行调用。
   *
   * 即便 `slashCommandPlugin` 未在 `plugins[]` 中，本方法也接受注册
   * 但 provider 永远不会被调用（不报错）。
   *
   * Stable since v0.3。
   */
  registerSlashItems(provider: SlashItemProvider): Disposable;

  /**
   * 注册 wikilink 菜单候选项提供方。语义对称 `registerSlashItems`。
   *
   * Stable since v0.3 (phase B)。
   */
  registerWikilinkItems(provider: WikilinkItemProvider): Disposable;

  /**
   * 注册 selection toolbar actions。在 `selectionToolbarPlugin` 加载时
   * 收集；toolbar 激活期间作为额外 actions 合并到内置 default 之后。
   *
   * Stable since v0.3 (phase C)。
   */
  registerSelectionToolbarActions(actions: SelectionToolbarAction[]): Disposable;

  /**
   * 订阅 editor 事件。Plugin 监听内核事件做反应式工作时使用。
   *
   * 返回的 Disposable 在 dispose 时会真实移除 listener；editor destroy
   * 时也会自动 dispose 所有未释放的 listener。
   *
   * Stable since v0.3。
   */
  on(event: EditorEventType, listener: EditorEventListener): Disposable;
}

/**
 * Plugin 返回的实例。v0.1 仅支持 `dispose`（与 register 返回 disposable
 * 不同的、plugin 自身的清理钩子）。
 */
export interface EditorPluginInstance {
  /** Plugin 自身的销毁钩子，在 editor.destroy() 时调用一次 */
  dispose?(): void;
}

/**
 * Plugin 接口。v0.1 metadata 锁定为 `id` (required) + `version` (optional)。
 *
 * 其他元数据（`dependencies` / `settings` / `permission` / `manifest`）
 * 延至 v1.0+ runtime marketplace 时再加。
 */
export interface EditorPlugin {
  /** 全局唯一 id，例如 'math' / 'org.swarmnote.math' */
  id: string;
  /** Plugin 自身版本号（语义化版本字符串） */
  version?: string;
  /** 安装钩子。可同步执行，可返回实例供后续 dispose。 */
  setup(ctx: EditorPluginContext): EditorPluginInstance | void;
}

export const DEFAULT_SETTINGS: EditorSettings = {
  readonly: false,
  lineWrapping: true,
  indentWithTabs: false,
  tabSize: 2,
  autofocus: false,
  spellcheck: false,
  editable: true,
  showLineNumbers: false,
  theme: DEFAULT_THEME,
  features: {
    markdownHighlight: true,
    markdownDecorations: true,
    inlineRendering: true,
    search: true,
    collaboration: true,
  },
};
