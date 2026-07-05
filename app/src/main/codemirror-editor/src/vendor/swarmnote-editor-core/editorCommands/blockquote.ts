/**
 * 引用块命令
 * 
 * **功能：**
 * 切换选中文本或当前行的引用块前缀（`> `）。
 */
import type { EditorView } from '@codemirror/view';

/** 引用块正则表达式：匹配行首的缩进 + `>` + 可选空格 */
const blockquoteRegex = /^(\s*)>\s?/;

/** 检测到的引用块信息 */
interface DetectedBlockquote {
  /** 缩进字符串 */
  indent: string;
  /** 完整匹配的文本（包括缩进和 `>`） */
  fullMatch: string;
}

/**
 * 检测行是否为引用块
 * 
 * @param lineText - 行文本
 * @returns 检测结果或 null
 */
function detectBlockquote(lineText: string): DetectedBlockquote | null {
  const m = lineText.match(blockquoteRegex);
  if (!m) return null;
  return { indent: m[1], fullMatch: m[0] };
}

/**
 * 切换引用块前缀
 * 
 * **工作流程：**
 * 1. 获取选区覆盖的所有行
 * 2. 检测每行是否为引用块
 * 3. 如果所有行都是引用块，移除所有 `> ` 前缀
 * 4. 否则，为所有非引用块行添加 `> ` 前缀（保留现有缩进）
 * 
 * **单光标情况：**
 * 作用于光标所在的行。
 * 
 * @param view - 编辑器视图
 */
export function toggleBlockquote(view: EditorView): void {
  // 获取选区的起始和结束位置
  const { from, to } = view.state.selection.main;
  const fromLine = view.state.doc.lineAt(from);
  const toLine = view.state.doc.lineAt(to);

  // 收集选区覆盖的所有行及其引用块检测结果
  const lines: { line: ReturnType<typeof view.state.doc.lineAt>; detected: DetectedBlockquote | null }[] = [];
  for (let pos = fromLine.from; pos <= toLine.to; ) {
    const line = view.state.doc.lineAt(pos);
    lines.push({ line, detected: detectBlockquote(line.text) });
    if (line.to >= toLine.to) break;
    pos = line.to + 1;
  }

  // 检查是否所有行都是引用块
  const allBlockquoted = lines.every((entry) => entry.detected !== null);

  // 构建变更列表
  const changes: { from: number; to: number; insert: string }[] = [];
  if (allBlockquoted) {
    // 所有行都是引用块：移除 `> ` 前缀
    for (const { line, detected } of lines) {
      if (!detected) continue;
      changes.push({
        from: line.from + detected.indent.length,
        to: line.from + detected.fullMatch.length,
        insert: '',
      });
    }
  } else {
    // 部分行不是引用块：为非引用块行添加 `> ` 前缀
    for (const { line, detected } of lines) {
      if (detected) continue;
      // 提取行首缩进
      const indentMatch = line.text.match(/^(\s*)/);
      const indentLen = indentMatch ? indentMatch[1].length : 0;
      changes.push({
        from: line.from + indentLen,
        to: line.from + indentLen,
        insert: '> ',
      });
    }
  }

  // 如果有变更，执行 dispatch
  if (changes.length === 0) return;
  view.dispatch({ changes });
}
