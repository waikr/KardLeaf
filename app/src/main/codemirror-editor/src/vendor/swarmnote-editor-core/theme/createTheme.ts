/**
 * Editor Theme Factory
 *
 * 根据 EditorThemeConfig 生成 CM6 EditorView.theme()。
 * 深色/浅色各有一套默认配色，可被宿主传入的 colors 覆盖。
 */
import type { Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import type { EditorThemeConfig } from '../types';

const lightDefaults = {
  background: '#f4faff',
  foreground: '#172033',
  selection: 'rgba(59, 130, 246, 0.24)',
  activeLine: 'transparent',
  border: '#d8e6fb',
  codeBackground: '#e4f1ff',
  heading: '#172033',
  link: '#3b82f6',
  // Code syntax — VS Code Light+ style
  comment: '#008000',
  keyword: '#0000FF',
  string: '#A31515',
  number: '#098658',
  bool: '#0000FF',
  variableName: '#001080',
  definition: '#795E26',
  typeName: '#267F99',
  className: '#267F99',
  propertyName: '#001080',
  operator: '#000000',
  punctuation: '#000000',
  meta: '#795E26',
  atom: '#0000FF',
  namespace: '#267F99',
};

const darkDefaults = {
  background: '#101923',
  foreground: '#e5eef8',
  selection: 'rgba(147, 197, 253, 0.28)',
  activeLine: 'transparent',
  border: '#2b3b4d',
  codeBackground: '#17212d',
  heading: '#e5eef8',
  link: '#93c5fd',
  // Code syntax — VS Code Dark+ style
  comment: '#6A9955',
  keyword: '#569CD6',
  string: '#CE9178',
  number: '#B5CEA8',
  bool: '#569CD6',
  variableName: '#9CDCFE',
  definition: '#DCDCAA',
  typeName: '#4EC9B0',
  className: '#4EC9B0',
  propertyName: '#9CDCFE',
  operator: '#D4D4D4',
  punctuation: '#D4D4D4',
  meta: '#DCDCAA',
  atom: '#569CD6',
  namespace: '#4EC9B0',
};

// Table widget tokens — values referenced by `renderBlockTables.ts` via
// `var(--cm-table-*)`. Defined here so theme switching auto-updates the
// widget colors without a CM transaction.
const lightTableTokens = {
  '--cm-table-border': '#d8e6fb',
  '--cm-table-header-bg': '#e4f1ff',
  '--cm-table-selection-border': '#3b82f6',
  '--cm-table-selection-bg': 'rgba(59, 130, 246, 0.10)',
  '--cm-table-affordance-fg': 'rgba(23, 32, 51, 0.6)',
  '--cm-table-affordance-bg-hover': '#d8ecff',
};

const darkTableTokens = {
  '--cm-table-border': '#2b3b4d',
  '--cm-table-header-bg': '#17212d',
  '--cm-table-selection-border': '#93c5fd',
  '--cm-table-selection-bg': 'rgba(147, 197, 253, 0.16)',
  '--cm-table-affordance-fg': 'rgba(229, 238, 248, 0.6)',
  '--cm-table-affordance-bg-hover': '#243447',
};

export function createEditorTheme(config: EditorThemeConfig): Extension {
  const isDark = config.appearance === 'dark';
  const defaults = isDark ? darkDefaults : lightDefaults;
  const c = { ...defaults, ...config.colors };
  const tableTokens = {
    ...(isDark ? darkTableTokens : lightTableTokens),
    '--cm-table-border': c.border,
    '--cm-table-header-bg': c.codeBackground,
    '--cm-table-selection-border': c.link,
    '--cm-table-selection-bg': c.selection,
  };
  const fontFamily = config.fontFamily ?? 'system-ui, sans-serif';
  const fontSize = config.fontSize ?? 16;
  const lineHeight = config.lineHeight ?? 1.55;
  const letterSpacing = config.letterSpacing ?? 0;
  const paragraphSpacing = config.paragraphSpacing ?? 8;

  return EditorView.theme(
    {
      '&': {
        color: c.foreground,
        backgroundColor: c.background,
        fontFamily,
        fontSize: `${fontSize}px`,
        lineHeight: `${lineHeight}`,
        letterSpacing: `${letterSpacing}px`,
        ...tableTokens,
      },
      '&.cm-focused': {
        outline: 'none',
      },
      '.cm-content': {
        caretColor: c.foreground,
        paddingBottom: `${paragraphSpacing}px`,
      },
      '.cm-line': {
        paddingBottom: `${paragraphSpacing}px`,
      },
      '.cm-cursor, .cm-dropCursor': {
        borderLeftColor: c.foreground,
      },
      '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection': {
        backgroundColor: c.selection,
      },
      '.cm-activeLine': {
        backgroundColor: c.activeLine,
      },
      '.cm-gutters': {
        backgroundColor: c.background,
        color: c.comment,
        borderRight: `1px solid ${c.border}`,
      },
      '.cm-activeLineGutter': {
        backgroundColor: 'transparent',
      },
      // Markdown decorations
      '.cm-headerLine': {
        color: c.heading,
      },
      '.cm-inlineCode': {
        backgroundColor: c.codeBackground,
      },
      '.cm-codeBlock': {
        backgroundColor: c.codeBackground,
      },
      '.cm-url': {
        color: c.link,
      },
      '.cm-ext-link': {
        color: c.link,
      },
      '.cm-wikilink, .cm-wikilink-revealed': {
        color: c.link,
      },
      '.cm-blockQuote': {
        borderLeftColor: c.border,
      },
      // Syntax highlighting overrides
      '.tok-keyword': { color: c.keyword },
      '.tok-string, .tok-string2': { color: c.string },
      '.tok-comment': { color: c.comment },
      '.tok-link': { color: c.link },
      '.tok-number': { color: c.number },
      '.tok-bool': { color: c.bool },
      '.tok-variableName': { color: c.variableName },
      '.tok-definition': { color: c.definition },
      '.tok-typeName': { color: c.typeName },
      '.tok-className': { color: c.className },
      '.tok-propertyName': { color: c.propertyName },
      '.tok-operator': { color: c.operator },
      '.tok-punctuation': { color: c.punctuation },
      '.tok-meta': { color: c.meta },
      '.tok-atom': { color: c.atom },
      '.tok-namespace': { color: c.namespace },
    },
    { dark: isDark },
  );
}
