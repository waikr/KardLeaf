export {
  createCollaborationExtension,
} from './collaborationExtension';
export {
  createInlineRenderingExtension,
} from './inlineRendering';
export type {
  InlineRenderingSpec,
  ReplacementExtension,
  RevealStrategy,
} from './inlineRendering';
export {
  createMarkdownDecorationExtension,
} from './markdownDecorationExtension';
export {
  markdownHighlightExtension,
} from './markdownHighlightExtension';
export {
  createEditorSettingsExtension,
  getEditorSettings,
  getEditorSettingsEffects,
  setEditorSettingsEffect,
} from './editorSettingsExtension';
export type { EditorSettingsExtensionRuntime } from './editorSettingsExtension';
export {
  clearSearch,
  createSearchExtension,
  getSearchState,
  searchChangeSourceEffect,
  setSearchState,
} from './searchExtension';
// 已迁到 subpath plugin 的扩展（math / table / mermaid / admonition /
// codeBlock / blockImage / rawHtml / smartPaste）不在此 barrel 导出。
// Plugin wrapper 直接从对应 ./renderXxx 文件 import 内部实现。
