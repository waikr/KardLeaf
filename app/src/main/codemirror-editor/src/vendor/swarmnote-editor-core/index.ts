// ---------------------------------------------------------------------------
// Public API surface for @swarmnote/editor-core (v0.1)
// ---------------------------------------------------------------------------
//
// 主入口仅暴露：内核构造函数、控制器、in-core 扩展、命令、事件类型、Plugin SDK。
// 已迁移到 subpath 的 8 个功能 plugin（math / table / mermaid / admonition /
// codeBlock / blockImage / rawHtml / smartPaste）以及 3 个 interaction
// 占位 plugin 不在此处再导出 —— 仅可通过对应 subpath
// (`@swarmnote/editor-core/plugins/<name>`) 访问。
// ---------------------------------------------------------------------------

// Core constructor + control
export { createEditor } from './createEditor';
export { EditorControlImpl } from './EditorControl';

// Events
export { EditorEventType } from './events';
export type {
  EditorBlurEvent,
  EditorChangeEvent,
  EditorCollaborationUpdateEvent,
  EditorCoreEvent,
  EditorEvent,
  EditorFocusEvent,
  EditorInteractionEvent,
  EditorLinkOpenEvent,
  EditorMermaidZoomRequestEvent,
  EditorPlatformEvent,
  EditorRemoveEvent,
  EditorSearchStateChangeEvent,
  EditorSelectionChangeEvent,
  EditorSelectionFormattingChangeEvent,
  EditorSelectionToolbarChangeEvent,
  EditorSlashTriggerChangeEvent,
  EditorTableContextMenuEvent,
  EditorWikilinkTriggerChangeEvent,
  SelectionToolbarMatch,
  SlashTriggerMatch,
  TableAlignment,
  TableContextMenuActions,
  WikilinkTriggerMatch,
} from './events';

// Built-in commands & shared command helpers
export * from './editorCommands';

// In-core extensions (NOT migrated to plugin; still gated by features field).
// 注意：本 barrel 仅 re-export in-core extension 工厂。已迁到 plugin 的
// `createBlockMermaidExtension` 等不再从 extensions 桶导出。
export * from './extensions';

// Core / utils
export * from './core';
export * from './utils';

// Plugin SDK types & runtime constants
export {
  DEFAULT_SEARCH_STATE,
  DEFAULT_SELECTION_FORMATTING,
  DEFAULT_SETTINGS,
  DEFAULT_THEME,
  EditorCommandType,
} from './types';
export type {
  Disposable,
  EditorAppearance,
  EditorCollaborationConfig,
  EditorCommandContext,
  EditorCommandSpec,
  EditorControl,
  EditorEventListener,
  EditorFeatureToggles,
  EditorHostCapabilities,
  EditorPlugin,
  EditorPluginContext,
  EditorPluginInstance,
  EditorProps,
  EditorSelectionRange,
  EditorSettings,
  EditorSettingsUpdate,
  EditorThemeConfig,
  ListType,
  MarkdownRenderRule,
  SearchState,
  SelectionFormatting,
  SelectionToolbarAction,
  SlashItem,
  SlashItemProvider,
  WikilinkItem,
  WikilinkItemProvider,
} from './types';
