import { Facet } from '@codemirror/state';
import type {
  EditorSelectionRange,
  SearchState,
  SelectionFormatting,
  SelectionToolbarAction,
  SlashItem,
  WikilinkItem,
} from './types';

/**
 * 编辑器事件类型常量
 *
 * 事件按稳定性分为三层：
 *
 * - **Core**（stable since v0.1）：`Change` / `SelectionChange` /
 *   `SelectionFormattingChange` / `Focus` / `Blur` / `SearchStateChange` /
 *   `CollaborationUpdate` / `LinkOpen`。payload 跨平台中立。
 * - **Interaction**（`SlashTriggerChange` stable since v0.3；其余 wikilink /
 *   selectionToolbar 在 Phase B/C 内提升 stable）。Payload DOM-agnostic（含
 *   可选 `screenRect`，由 web plugin 填）。
 * - **Platform**（platform-coupled）：`TableContextMenu` /
 *   `MermaidZoomRequest` / `Remove`。包含 DOM 坐标 / HTML 字符串等 Web
 *   假设，非 DOM 宿主（如 React Native）下语义可能不同。
 */
export const EditorEventType = {
  /** 文档内容变化（Core） */
  Change: 'change',
  /** 选区变化（Core） */
  SelectionChange: 'selectionChange',
  /** 选区格式状态变化（Core） */
  SelectionFormattingChange: 'selectionFormattingChange',
  /** 获取焦点（Core） */
  Focus: 'focus',
  /** 失去焦点（Core） */
  Blur: 'blur',
  /** 搜索状态变化（Core） */
  SearchStateChange: 'searchStateChange',
  /** 协作更新（Core） */
  CollaborationUpdate: 'collaborationUpdate',
  /** 链接打开（Core） */
  LinkOpen: 'linkOpen',
  /** Slash 触发器状态变化（Interaction, stable since v0.3）。 */
  SlashTriggerChange: 'slashTriggerChange',
  /** Wikilink 触发器状态变化（Interaction, stable since v0.3 phase B）。 */
  WikilinkTriggerChange: 'wikilinkTriggerChange',
  /** 选区工具栏状态变化（Interaction, stable since v0.3 phase C）。 */
  SelectionToolbarChange: 'selectionToolbarChange',
  /** 表格右键菜单（Platform-coupled，Web/DOM 假设） */
  TableContextMenu: 'tableContextMenu',
  /** Mermaid 图表放大查看请求（Platform-coupled，含 SVG HTML 字符串） */
  MermaidZoomRequest: 'mermaidZoomRequest',
  /** 编辑器移除（Platform Convenience；非 DOM 宿主语义可能不同） */
  Remove: 'remove',
} as const;

/** 编辑器事件类型联合类型 */
export type EditorEventType = (typeof EditorEventType)[keyof typeof EditorEventType];

// ---------------------------------------------------------------------------
// Core 事件（v0.x 稳定，跨平台中立）
// ---------------------------------------------------------------------------

/** 文档内容变化事件 */
export interface EditorChangeEvent {
  kind: typeof EditorEventType.Change;
}

/** 选区变化事件 */
export interface EditorSelectionChangeEvent {
  kind: typeof EditorEventType.SelectionChange;
  /** 新的选区位置 */
  selection: EditorSelectionRange;
}

/** 获取焦点事件 */
export interface EditorFocusEvent {
  kind: typeof EditorEventType.Focus;
}

/** 失去焦点事件 */
export interface EditorBlurEvent {
  kind: typeof EditorEventType.Blur;
}

/** 搜索状态变化事件 */
export interface EditorSearchStateChangeEvent {
  kind: typeof EditorEventType.SearchStateChange;
  /** 新的搜索状态 */
  search: SearchState | null;
  /** 触发来源标识 */
  source?: string;
}

/** 协作更新事件 */
export interface EditorCollaborationUpdateEvent {
  kind: typeof EditorEventType.CollaborationUpdate;
  /** Yjs 更新数据 */
  update: Uint8Array;
}

/** 选区格式状态变化事件 */
export interface EditorSelectionFormattingChangeEvent {
  kind: typeof EditorEventType.SelectionFormattingChange;
  /** 新的格式状态 */
  formatting: SelectionFormatting;
}

/** 链接打开事件 */
export interface EditorLinkOpenEvent {
  kind: typeof EditorEventType.LinkOpen;
  /** 链接 URL */
  url: string;
}

// ---------------------------------------------------------------------------
// Interaction 事件（SlashTriggerChange stable since v0.3；wikilink/selectionToolbar
// 在 Phase B/C 提升 stable）
// ---------------------------------------------------------------------------

/**
 * Slash 触发匹配。DOM-agnostic。
 *
 * `range.from` / `range.to` 为 CodeMirror 文档 offset（zero-based 字符索引）；
 * `screenRect` 由 web 平台 plugin 用 `view.coordsAtPos()` 填，RN 等非 DOM
 * 宿主下为 undefined。
 *
 * Stable since v0.3。Shape 在 v0.x 内只可新增 optional 字段。
 */
export interface SlashTriggerMatch {
  /** 触发是否激活；inactive 时其余字段为空值（query: '', items: [], etc.） */
  active: boolean;
  /** 当前查询字符串（trigger char 之后到光标的内容） */
  query: string;
  /** 文档中触发范围；inactive 时 `from === to` */
  range: { from: number; to: number };
  /** 当前候选项（已合并 plugin provider + host.getSlashItems、已排序） */
  items: SlashItem[];
  /** 高亮项 index（`active: false` 时为 0；`items` 为空时无意义） */
  activeIndex: number;
  /** Web 端 anchor 屏幕坐标；非 DOM 宿主下 undefined */
  screenRect?: { x: number; y: number; width: number; height: number };
}

/**
 * Wikilink 触发匹配。DOM-agnostic，与 `SlashTriggerMatch` 同 shape 但 items
 * 类型为 `WikilinkItem`。
 *
 * Stable since v0.3 (phase B)。
 */
export interface WikilinkTriggerMatch {
  active: boolean;
  query: string;
  range: { from: number; to: number };
  items: WikilinkItem[];
  activeIndex: number;
  screenRect?: { x: number; y: number; width: number; height: number };
}

/**
 * SelectionToolbar 匹配。DOM-agnostic。
 *
 * `selection.from === selection.to` 时 `active` 必为 false（光标态不触发）。
 *
 * Stable since v0.3 (phase C)。
 */
export interface SelectionToolbarMatch {
  active: boolean;
  /** 选区范围（CM 文档 offset）；inactive 时 `from === to` */
  selection: { from: number; to: number };
  /** 当前可用的 toolbar actions（已合并 built-in + plugin + host） */
  actions: SelectionToolbarAction[];
  /** 锚定 rect；典型由 web 端用选区第一行的 union rect */
  screenRect?: { x: number; y: number; width: number; height: number };
}

/**
 * Slash 触发器状态变化。当 slash 触发被识别 / query 切换 / activeIndex 切换 /
 * 触发取消时 dispatch。
 *
 * `match.active: false` 表示 trigger 取消；不再使用 `match: null`。
 *
 * Stable since v0.3。
 */
export interface EditorSlashTriggerChangeEvent {
  kind: typeof EditorEventType.SlashTriggerChange;
  /** 当前匹配；`match.active: false` 时其余字段为空值（query: '', items: []） */
  match: SlashTriggerMatch;
}

/**
 * Wikilink 触发器状态变化。当 wikilink 触发被识别 / query 切换 /
 * activeIndex 切换 / 触发取消时 dispatch。
 *
 * `match.active: false` 表示 trigger 取消；不使用 `match: null`。
 *
 * Stable since v0.3 (phase B)。
 */
export interface EditorWikilinkTriggerChangeEvent {
  kind: typeof EditorEventType.WikilinkTriggerChange;
  match: WikilinkTriggerMatch;
}

/**
 * 选区工具栏状态变化。selection 切换 / focus 变化 / 命令 dismiss 时 dispatch。
 *
 * `match.active: false` 表示 toolbar 应隐藏。
 *
 * Stable since v0.3 (phase C)。
 */
export interface EditorSelectionToolbarChangeEvent {
  kind: typeof EditorEventType.SelectionToolbarChange;
  match: SelectionToolbarMatch;
}

// ---------------------------------------------------------------------------
// Platform 事件（platform-coupled，Web/DOM 假设）
// ---------------------------------------------------------------------------

/** 表格对齐方式 */
export type TableAlignment = 'left' | 'center' | 'right' | null;

/**
 * 表格上下文菜单操作接口
 *
 * 当表格 widget 触发右键菜单时，向宿主 React 层暴露的命令式操作。
 * 每个调用都会通过单次 CodeMirror dispatch 修改 Markdown 文档，
 * 让 React 菜单可以关闭而无需进一步耦合。
 */
export interface TableContextMenuActions {
  /** 在指定位置添加行 */
  addRowAt(rowIdx: number, position: 'above' | 'below'): void;
  /** 删除指定行 */
  deleteRow(rowIdx: number): void;
  /** 在指定位置添加列 */
  addColumnAt(colIdx: number, position: 'left' | 'right'): void;
  /** 删除指定列 */
  deleteColumn(colIdx: number): void;
  /** 设置列对齐方式 */
  setAlignment(colIdx: number, alignment: TableAlignment): void;
  /** 切换源码/渲染视图 */
  toggleSource(): void;
  /** 复制表格为 Markdown */
  copyMarkdown(): void;
  /** 删除整个表格 */
  deleteTable(): void;
}

/**
 * 表格右键菜单事件
 *
 * **Platform-coupled**：载荷含 DOM 像素坐标 `clientX` / `clientY`，
 * 用于 Web 宿主把菜单定位到鼠标位置。非 DOM 宿主（React Native 等）
 * 收到该事件时坐标语义未定义。
 */
export interface EditorTableContextMenuEvent {
  kind: typeof EditorEventType.TableContextMenu;
  /** 鼠标 X 坐标（DOM 像素） */
  clientX: number;
  /** 鼠标 Y 坐标（DOM 像素） */
  clientY: number;
  /** 行索引（-1 表示表头，否则为 tbody 行索引） */
  rowIdx: number;
  /** 列索引 */
  colIdx: number;
  /** 当前对齐方式 */
  alignment: TableAlignment;
  /** 总行数 */
  rowCount: number;
  /** 总列数 */
  colCount: number;
  /** 可执行的操作集合 */
  actions: TableContextMenuActions;
}

/**
 * Mermaid 图表放大查看请求事件
 *
 * **Platform-coupled**：`renderedSvg` 为已渲染的 SVG HTML 字符串，前提
 * 宿主能直接渲染 HTML 字符串到 DOM。非 DOM 宿主需要把 `source` 自行
 * 渲染为目标格式。
 */
export interface EditorMermaidZoomRequestEvent {
  kind: typeof EditorEventType.MermaidZoomRequest;
  /** Mermaid 源码字符串 */
  source: string;
  /** 已渲染的 SVG HTML 字符串（DOM 假设） */
  renderedSvg: string;
  /** 图表唯一标识符 */
  id: string;
}

/**
 * 编辑器移除事件
 *
 * **Platform-coupled**（Platform Convenience）：在 `EditorControl.destroy()`
 * 内由内核派发，主要为 Web 宿主提供「编辑器即将销毁」的便利钩子。非 DOM
 * 宿主可能不依赖此事件。
 */
export interface EditorRemoveEvent {
  kind: typeof EditorEventType.Remove;
}

// ---------------------------------------------------------------------------
// 三层 union + 聚合 union
// ---------------------------------------------------------------------------

/**
 * Core 事件 union。v0.x 稳定，载荷跨平台中立（不含 DOM 坐标 / HTML）。
 */
export type EditorCoreEvent =
  | EditorChangeEvent
  | EditorSelectionChangeEvent
  | EditorSelectionFormattingChangeEvent
  | EditorFocusEvent
  | EditorBlurEvent
  | EditorSearchStateChangeEvent
  | EditorCollaborationUpdateEvent
  | EditorLinkOpenEvent;

/**
 * Interaction 事件 union（@unstable v0.1，runtime 延后到 v0.2）。
 */
export type EditorInteractionEvent =
  | EditorSlashTriggerChangeEvent
  | EditorWikilinkTriggerChangeEvent
  | EditorSelectionToolbarChangeEvent;

/**
 * Platform 事件 union（platform-coupled，Web/DOM 假设）。
 */
export type EditorPlatformEvent =
  | EditorTableContextMenuEvent
  | EditorMermaidZoomRequestEvent
  | EditorRemoveEvent;

/**
 * 编辑器事件聚合 union
 *
 * 等价于 `EditorCoreEvent | EditorInteractionEvent | EditorPlatformEvent`，
 * 保持与 v0.0.x 一致的整体类型形态（已存在事件 shape 不变）。
 */
export type EditorEvent =
  | EditorCoreEvent
  | EditorInteractionEvent
  | EditorPlatformEvent;

/**
 * 编辑器事件回调 Facet
 *
 * 把宿主的 `onEvent` 回调注入到 CodeMirror 状态树，供 widget / 内部扩展
 * 派发高层事件使用。`combine` 取第一个非 undefined 的回调。
 */
export const editorEventCallback = Facet.define<
  ((event: EditorEvent) => void) | undefined,
  ((event: EditorEvent) => void) | undefined
>({
  combine: (values) => values.find((v): v is (event: EditorEvent) => void => Boolean(v)),
});
