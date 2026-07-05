/**
 * Markdown 格式化命令
 * 
 * **功能：**
 * 提供 Markdown 内联格式化的切换命令，包括：
 * - 标题级别切换（cycleHeading, toggleHeading）
 * - 加粗、斜体、代码、删除线、高亮（toggleBold, toggleItalic, toggleCode, toggleStrike, toggleHighlight）
 */
import { EditorSelection } from '@codemirror/state';
import { EditorView } from '@codemirror/view';

/**
 * 在选中文本两边包裹/取消 marker（如 ** 或 * 或 `）。
 * 
 * **行为：**
 * 1. 如果选区前后已有相同的标记，则移除标记（取消格式化）
 * 2. 否则在选区前后添加标记（应用格式化）
 * 3. 更新光标位置以保持选中原始文本
 * 
 * @param view - 编辑器视图
 * @param marker - 标记字符串（如 `**`、`*`、`` ` ``、`~~`）
 */
function toggleInlineMarker(view: EditorView, marker: string): void {
  const { from, to } = view.state.selection.main;
  const selectedText = view.state.sliceDoc(from, to);
  const markerLen = marker.length;

  const before = view.state.sliceDoc(Math.max(0, from - markerLen), from);
  const after = view.state.sliceDoc(to, to + markerLen);

  if (before === marker && after === marker) {
    view.dispatch({
      changes: [
        { from: from - markerLen, to: from, insert: '' },
        { from: to, to: to + markerLen, insert: '' },
      ],
      selection: EditorSelection.single(from - markerLen, to - markerLen),
    });
    return;
  }

  const insert = `${marker}${selectedText}${marker}`;
  view.dispatch({
    changes: { from, to, insert },
    selection: EditorSelection.single(
      from + markerLen,
      from + markerLen + selectedText.length,
    ),
  });
}

/**
 * 切换加粗
 * 
 * @param view - 编辑器视图
 */
export function toggleBold(view: EditorView): void {
  toggleInlineMarker(view, '**');
}

/**
 * 切换斜体
 * 
 * @param view - 编辑器视图
 */
export function toggleItalic(view: EditorView): void {
  toggleInlineMarker(view, '*');
}

/**
 * 切换行内代码
 * 
 * @param view - 编辑器视图
 */
export function toggleCode(view: EditorView): void {
  toggleInlineMarker(view, '`');
}

/**
 * 切换删除线
 * 
 * @param view - 编辑器视图
 */
export function toggleStrike(view: EditorView): void {
  toggleInlineMarker(view, '~~');
}

/**
 * 切换高亮
 * 
 * @param view - 编辑器视图
 */
export function toggleHighlight(view: EditorView): void {
  toggleInlineMarker(view, '==');
}

/**
 * 切换指定级别标题
 * 
 * **行为：**
 * 1. 如果当前行已是指定级别的标题，则移除标题标记（转为普通段落）
 * 2. 如果当前行是其他级别的标题，则替换为指定级别
 * 3. 如果当前行不是标题，则添加指定级别的标题标记
 * 
 * @param view - 编辑器视图
 * @param level - 标题级别（1-6，默认为 2）
 */
export function toggleHeading(view: EditorView, level = 2): void {
  const { from } = view.state.selection.main;
  const line = view.state.doc.lineAt(from);
  const prefix = `${'#'.repeat(level)} `;

  if (line.text.startsWith(prefix)) {
    view.dispatch({
      changes: { from: line.from, to: line.from + prefix.length, insert: '' },
    });
    return;
  }

  const existingMatch = line.text.match(/^#{1,6}\s/);
  const removeLen = existingMatch ? existingMatch[0].length : 0;
  view.dispatch({
    changes: { from: line.from, to: line.from + removeLen, insert: prefix },
  });
}

/**
 * Cycle the current line's heading level: paragraph → H1 → H2 → H3 → paragraph.
 * 
 * **行为：**
 * 循环切换当前行的标题级别：普通段落 → H1 → H2 → H3 → 普通段落
 * 
 * @param view - 编辑器视图
 */
export function cycleHeading(view: EditorView): void {
  const { from } = view.state.selection.main;
  const line = view.state.doc.lineAt(from);
  const match = line.text.match(/^(#{1,6})\s/);
  const currentLevel = match ? match[1].length : 0;
  const nextLevel = currentLevel === 0 ? 1 : currentLevel >= 3 ? 0 : currentLevel + 1;

  if (nextLevel === 0) {
    if (match) {
      view.dispatch({
        changes: { from: line.from, to: line.from + match[0].length, insert: '' },
      });
    }
    return;
  }

  const prefix = `${'#'.repeat(nextLevel)} `;
  const removeLen = match ? match[0].length : 0;
  view.dispatch({
    changes: { from: line.from, to: line.from + removeLen, insert: prefix },
  });
}
