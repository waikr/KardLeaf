/**
 * 排序选中的行
 * 
 * **功能：**
 * 对当前选区内的所有行按字母顺序进行排序（升序）。
 */
import { EditorSelection } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';

/**
 * 排序选中的行
 * 
 * **行为：**
 * 1. 提取选区内的所有行文本
 * 2. 按字母顺序排序（localeCompare，支持中文）
 * 3. 将排序后的文本写回编辑器
 * 
 * @param view - 编辑器视图
 * @returns 总是返回 true
 */
export function sortSelectedLines(view: EditorView): boolean {
  const { state } = view;
  const { doc } = state;

  const transaction = state.changeByRange((range) => {
    const startLine = doc.lineAt(range.from);
    const endLine = doc.lineAt(range.to);

    const lines: string[] = [];
    for (let j = startLine.number; j <= endLine.number; j++) {
      lines.push(doc.line(j).text);
    }

    const sortedText = lines.sort().join('\n');

    return {
      range: EditorSelection.cursor(startLine.from + sortedText.length),
      changes: [{ from: startLine.from, to: endLine.to, insert: sortedText }],
    };
  });

  view.dispatch(transaction);
  return true;
}
