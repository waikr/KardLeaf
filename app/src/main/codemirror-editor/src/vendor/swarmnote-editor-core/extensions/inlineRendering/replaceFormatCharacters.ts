/**
 * 格式化字符替换扩展
 * 
 * **功能：**
 * 隐藏 Markdown 格式化标记符（如 `#`、`**`、`_`、`~~`、`` ` `` 等），
 * 仅显示格式化后的文本内容。
 * 
 * **支持的节点类型：**
 * - HeaderMark：标题标记（`#`）
 * - CodeMark：代码标记（`` ` ``）
 * - EmphasisMark：强调标记（`*` 或 `_`）
 * - StrikethroughMark：删除线标记（`~~`）
 * - HighlightMarker：高亮标记（`==`）
 * - QuoteMark：引用标记（`>`）
 * - LinkMark：链接标记（`[` 和 `](`）
 * - URL：URL 部分
 */
import { Decoration } from '@codemirror/view';
import type { InlineRenderingSpec, RevealStrategy } from './types';

// HeaderMark 使用 'line' —— 整行都是标题，所以光标在行的任何位置都会显示 `#`
// （匹配 Obsidian 行为）。
// QuoteMark 使用 'active' + prefix reveal —— 引用块内的正文不属于 `>` 标记本身，
// 所以光标在正文中时 `>` 保持隐藏。
const lineRevealNodes = new Set(['HeaderMark']);
const activeRevealNodes = new Set([
  'EmphasisMark',
  'StrikethroughMark',
  'HighlightMarker',
  'LinkMark',
  'URL',
  'QuoteMark',
  'CodeMark',
]);

/**
 * 需要将显示判断范围扩展到父节点的标记类型
 * 
 * **设计原因：**
 * 成对的内联标记（如 `**bold**`）：当光标在父节点的任何位置时，
 * 两端的标记都应该同时显示。例如：光标在 `**bold**` 的 "bold" 文本中时，
 * 两个 `**` 应该一起显示出来。
 */
const expandRevealToParent = new Set([
  'EmphasisMark',
  'StrikethroughMark',
  'HighlightMarker',
  'LinkMark',
  'URL',
  'CodeMark',
]);

/** 隐藏装饰（空替换，用于完全隐藏标记符） */
const hiddenDecoration = Decoration.replace({});

/**
 * 格式化字符替换规格
 * 
 * **核心逻辑：**
 * 1. createDecoration：返回空替换装饰，完全隐藏标记符
 * 2. getDecorationRange：对于 HeaderMark，包含尾随空格
 * 3. getRevealStrategy：根据节点类型选择策略
 * 4. getRevealRange：扩展某些节点的显示范围到父节点
 */
export const replaceFormatCharacters: InlineRenderingSpec = {
  nodeNames: [
    'HeaderMark',        // 标题标记
    'CodeMark',          // 代码标记
    'EmphasisMark',      // 强调标记
    'StrikethroughMark', // 删除线标记
    'HighlightMarker',   // 高亮标记
    'QuoteMark',         // 引用标记
    'LinkMark',          // 链接标记
    'URL',               // URL
  ],
  extension: {
    /**
     * 创建装饰
     * 
     * @returns 空替换装饰（隐藏标记符）
     */
    createDecoration() {
      return hiddenDecoration;
    },
    /**
     * 获取装饰范围
     * 
     * **特殊处理：**
     * 对于 HeaderMark，如果后面有空格，包含该空格以提供更好的视觉体验。
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns 装饰范围或 null
     */
    getDecorationRange(node, state) {
      // 对于 HeaderMark，如果后面有空格，包含该空格
      if (node.name === 'HeaderMark') {
        const afterMark = state.sliceDoc(node.to, node.to + 1);
        if (afterMark === ' ') {
          return [node.from, node.to + 1];
        }
      }
      return null;
    },
    /**
     * 获取显示策略
     * 
     * @param node - 语法节点
     * @returns 显示策略
     */
    getRevealStrategy(node): RevealStrategy {
      if (lineRevealNodes.has(node.name)) return 'line';
      if (activeRevealNodes.has(node.name)) return 'active';
      return 'line';
    },
    /**
     * 获取显示范围
     * 
     * **特殊处理：**
     * 1. 成对内联标记：扩展到父节点范围（光标在任何位置都显示两端标记）
     * 2. QuoteMark：扩展到整行的所有 `>` 前缀（处理嵌套引用）
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns 显示范围或 null
     */
    getRevealRange(node, state) {
      // 成对内联标记：当光标在父节点的任何位置时显示标记
      // （例如：光标在 `**bold**` 正文中 → 两个 `**` 一起显示）
      if (expandRevealToParent.has(node.name)) {
        const parent = node.node.parent;
        if (!parent) return null;
        return [parent.from, parent.to];
      }
      // QuoteMark `>`（统一处理嵌套的 `> > > ` 前缀）
      if (node.name === 'QuoteMark') {
        const line = state.doc.lineAt(node.from);
        const match = line.text.match(/^(\s*(?:>\s*)+)/);
        if (match) return [line.from, line.from + match[0].length];
      }
      return null;
    },
  },
};
