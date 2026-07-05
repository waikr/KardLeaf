/**
 * 选择格式化状态查询
 * 
 * **功能：**
 * 检测当前选区或光标位置的 Markdown 格式状态，用于 UI 按钮的高亮显示。
 */
import { ensureSyntaxTree } from '@codemirror/language';
import type { EditorState } from '@codemirror/state';
import { DEFAULT_SELECTION_FORMATTING, type SelectionFormatting } from '../types';

/**
 * Walk the syntax tree at the cursor to determine active formatting.
 * Resolves the deepest node at the cursor, then walks up through parents.
 * 
 * **工作流程：**
 * 1. 获取光标位置的语法树节点
 * 2. 从最内层节点开始向上遍历父节点
 * 3. 根据节点类型设置对应的格式化标志（加粗、斜体、代码等）
 * 4. 返回完整的格式化状态对象
 * 
 * @param state - 编辑器状态
 * @returns 格式化状态对象
 */
export function computeSelectionFormatting(state: EditorState): SelectionFormatting {
  const result = { ...DEFAULT_SELECTION_FORMATTING };
  const pos = state.selection.main.from;

  const tree = ensureSyntaxTree(state, pos);
  if (!tree) return result;

  let cursor: ReturnType<typeof tree.resolveInner> | null = tree.resolveInner(pos, -1);

  while (cursor) {
    switch (cursor.name) {
      case 'StrongEmphasis':
        result.bold = true;
        break;
      case 'Emphasis':
        result.italic = true;
        break;
      case 'InlineCode':
        result.code = true;
        break;
      case 'Strikethrough':
        result.strikethrough = true;
        break;
      case 'Highlight':
        result.highlight = true;
        break;
      case 'ATXHeading1':
      case 'SetextHeading1':
        result.heading = 1;
        break;
      case 'ATXHeading2':
      case 'SetextHeading2':
        result.heading = 2;
        break;
      case 'ATXHeading3':
        result.heading = 3;
        break;
      case 'ATXHeading4':
        result.heading = 4;
        break;
      case 'ATXHeading5':
        result.heading = 5;
        break;
      case 'ATXHeading6':
        result.heading = 6;
        break;
      case 'BulletList':
        if (!result.listType) {
          result.listType = 'unordered';
        }
        result.listLevel++;
        break;
      case 'OrderedList':
        if (!result.listType) {
          result.listType = 'ordered';
        }
        result.listLevel++;
        break;
      case 'Task':
        result.listType = 'check';
        break;
      case 'Blockquote':
        result.inBlockquote = true;
        break;
      case 'FencedCode':
      case 'CodeBlock':
        result.inCodeBlock = true;
        break;
    }

    cursor = cursor.parent;
  }

  return result;
}
