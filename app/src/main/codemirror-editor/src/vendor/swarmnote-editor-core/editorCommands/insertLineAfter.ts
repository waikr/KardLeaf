/**
 * 在当前行后插入新行
 * 
 * **功能：**
 * 智能地在当前行之后插入新行，并根据上下文自动处理列表和引用块的延续。
 */
import { insertNewlineAndIndent } from '@codemirror/commands';
import { EditorSelection, type SelectionRange } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';
import { insertNewlineContinueMarkup } from './insertNewlineContinueMarkup';

/**
 * 在当前行后插入新行
 * 
 * **行为：**
 * 1. 将光标移动到当前行末尾
 * 2. 优先使用 Markdown 感知的换行（保持列表、引用块等格式）
 * 3. 如果失败则回退到普通换行
 * 
 * @param view - 编辑器视图
 * @returns 总是返回 true
 */
export function insertLineAfter(view: EditorView): boolean {
  const { state } = view;

  // Move cursor to end of current line
  view.dispatch(
    state.changeByRange((sel: SelectionRange) => {
      const line = state.doc.lineAt(sel.anchor);
      return { range: EditorSelection.cursor(line.to) };
    }),
  );

  // Try markdown-aware newline first, fall back to plain
  if (!insertNewlineContinueMarkup(view)) {
    insertNewlineAndIndent(view);
  }

  return true;
}
