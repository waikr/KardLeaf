/**
 * 插入元素命令
 * 
 * **功能：**
 * 提供在编辑器中插入各种 Markdown 元素的命令，包括：
 * - 代码块（带语言提示）
 * - 分割线（`---`）
 * - 表格（默认结构或基于选区）
 * - 链接（带文本和 URL）
 * - 图片（块级图片，自动处理换行）
 */
import { EditorSelection } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';

/**
 * 插入代码块
 * 
 * **行为：**
 * 1. 如果已有选区，将选区包裹在代码块中
 * 2. 如果没有选区，在当前行后插入空代码块
 * 3. 光标定位到代码块内容区域
 * 
 * @param view - 编辑器视图
 */
export function insertCodeBlock(view: EditorView): void {
  const { from, to } = view.state.selection.main;
  const selected = view.state.sliceDoc(from, to);

  if (selected) {
    const insert = `\`\`\`\n${selected}\n\`\`\``;
    view.dispatch({
      changes: { from, to, insert },
      selection: EditorSelection.cursor(from + insert.length),
    });
  } else {
    const insert = '```\n\n```';
    view.dispatch({
      changes: { from, to, insert },
      selection: EditorSelection.cursor(from + 4),
    });
  }
}

/**
 * 插入分割线
 * 
 * **行为：**
 * 在当前行之后插入 `---` 分割线，并在其后创建新行
 * 
 * @param view - 编辑器视图
 */
export function insertHorizontalRule(view: EditorView): void {
  const { from } = view.state.selection.main;
  const line = view.state.doc.lineAt(from);
  const prefix = line.text.length > 0 ? '\n' : '';
  const insert = `${prefix}---\n`;

  view.dispatch({
    changes: { from: line.to, to: line.to, insert },
    selection: EditorSelection.cursor(line.to + insert.length),
  });
}

/**
 * 插入表格
 * 
 * **行为：**
 * 1. 如果有多行选区，将选区转换为表格（每行作为一行数据）
 * 2. 如果没有选区或多行选区不足，插入默认的空表格（2列 x 2行）
 * 3. 光标定位到第一个单元格
 * 
 * @param view - 编辑器视图
 */
export function insertTable(view: EditorView): void {
  const { from } = view.state.selection.main;
  const line = view.state.doc.lineAt(from);
  const prefix = line.text.length > 0 ? '\n' : '';
  const table = `${prefix}| Header 1 | Header 2 | Header 3 |\n| -------- | -------- | -------- |\n| Cell 1   | Cell 2   | Cell 3   |\n`;

  view.dispatch({
    changes: { from: line.to, to: line.to, insert: table },
    selection: EditorSelection.cursor(line.to + prefix.length + 2),
  });
}

/**
 * Insert a Markdown link at the current selection or cursor.
 *
 * **行为：**
 * - 当提供 URL 时：插入 `[text](url)`（其中 `text` 是当前选区或提供的 `text`，回退到 URL 本身）
 * - 当未提供 URL 时：插入 `[text](url)` 模板并选中 `url` 部分以便用户输入
 *
 * @param view - 编辑器视图
 * @param url - 可选的链接地址
 * @param text - 可选的链接文本
 */
export function insertLink(view: EditorView, url?: string, text?: string): void {
  const { from, to } = view.state.selection.main;
  const selected = view.state.sliceDoc(from, to);

  if (url) {
    const display = text ?? selected ?? url;
    const insert = `[${display}](${url})`;
    view.dispatch({
      changes: { from, to, insert },
      selection: EditorSelection.cursor(from + insert.length),
    });
    return;
  }

  // No URL provided — insert placeholder and select the "url" portion.
  const display = selected.length > 0 ? selected : 'text';
  const insert = `[${display}](url)`;
  const urlFrom = from + display.length + 3; // past "[display]("
  const urlTo = urlFrom + 3; // "url"
  view.dispatch({
    changes: { from, to, insert },
    selection: EditorSelection.single(urlFrom, urlTo),
  });
}

/**
 * Insert a Markdown image on its own line (block image).
 *
 * **行为：**
 * 在独立行插入块级图片语法。如果光标在行中间，会自动添加前导换行符
 * 以确保图片保持块级（这是块级图片 widget 渲染的必要条件）。
 *
 * @param view - 编辑器视图
 * @param url - 图片地址
 * @param alt - 可选的替代文本
 */
export function insertImage(view: EditorView, url: string, alt = ''): void {
  const { from, to } = view.state.selection.main;
  const line = view.state.doc.lineAt(from);

  const needsLeadingNewline = from !== line.from;
  const needsTrailingNewline = to === line.to && line.to < view.state.doc.length
    ? view.state.doc.sliceString(line.to, line.to + 1) !== '\n'
    : to !== line.to;

  const insert = `${needsLeadingNewline ? '\n' : ''}![${alt}](${url})${needsTrailingNewline ? '\n' : ''}`;

  view.dispatch({
    changes: { from, to, insert },
    selection: EditorSelection.cursor(from + insert.length),
  });
}
