import { EditorSelection } from '@codemirror/state';
/**
 * 列表命令
 * 
 * **功能：**
 * 切换选中文本或当前行的列表类型（无序列表、有序列表、任务列表）。
 */
import type { EditorView } from '@codemirror/view';
import type { ListType } from '../types';

/** 无序列表正则：匹配行首缩进 + `-` 或 `*` + 空格（排除任务列表） */
const bulletedRegex = /^(\s*)([-*])\s(?!\[[ xX]+\]\s)/;
/** 任务列表正则：匹配行首缩进 + `-` 或 `*` + `[ ]` 或 `[x]` + 空格 */
const checklistRegex = /^(\s*)([-*])\s\[[ xX]+\]\s/;
/** 有序列表正则：匹配行首缩进 + 数字 + `.` + 空格 */
const numberedRegex = /^(\s*)(\d+)\.\s/;

/** 任意列表匹配结果类型 */
type AnyListMatch = RegExpMatchArray & { index: number };

/** 检测到的列表信息 */
interface DetectedList {
  /** 列表类型 */
  type: ListType;
  /** 缩进字符串 */
  indent: string;
  /** 完整匹配的文本（包括缩进和标记） */
  fullMatch: string;
}

/**
 * 检测行是否为列表项
 * 
 * @param lineText - 行文本
 * @returns 检测结果或 null
 */
function detectListType(lineText: string): DetectedList | null {
  let m: AnyListMatch | null;

  m = lineText.match(checklistRegex) as AnyListMatch | null;
  if (m) return { type: 'check', indent: m[1], fullMatch: m[0] };

  m = lineText.match(bulletedRegex) as AnyListMatch | null;
  if (m) return { type: 'unordered', indent: m[1], fullMatch: m[0] };

  m = lineText.match(numberedRegex) as AnyListMatch | null;
  if (m) return { type: 'ordered', indent: m[1], fullMatch: m[0] };

  return null;
}

/**
 * 生成列表前缀
 * 
 * @param type - 列表类型
 * @param lineIndex - 行索引（用于有序列表编号）
 * @returns 列表前缀字符串
 */
function makePrefix(type: ListType, lineIndex: number): string {
  switch (type) {
    case 'unordered':
      return '- ';
    case 'check':
      return '- [ ] ';
    case 'ordered':
      return `${lineIndex + 1}. `;
  }
}

/**
 * 切换列表类型
 * 
 * **行为：**
 * 1. 遍历选区内的所有行
 * 2. 如果行是目标类型的列表，则移除列表标记（取消列表）
 * 3. 如果行是其他类型的列表，则切换为指定类型
 * 4. 如果行不是列表，则添加指定类型的列表标记
 * 
 * @param view - 编辑器视图
 * @param targetType - 目标列表类型
 */
export function toggleList(view: EditorView, targetType: ListType): void {
  const { from, to } = view.state.selection.main;
  const fromLine = view.state.doc.lineAt(from);
  const toLine = view.state.doc.lineAt(to);

  const changes: { from: number; to: number; insert: string }[] = [];
  let lineIndex = 0;
  let totalDelta = 0;

  for (let pos = fromLine.from; pos <= toLine.to; ) {
    const line = view.state.doc.lineAt(pos);
    const detected = detectListType(line.text);

    if (detected && detected.type === targetType) {
      // Same type: remove list marker
      changes.push({
        from: line.from + detected.indent.length,
        to: line.from + detected.fullMatch.length,
        insert: '',
      });
      totalDelta -= detected.fullMatch.length - detected.indent.length;
    } else if (detected) {
      // Different list type: switch
      const prefix = makePrefix(targetType, lineIndex);
      changes.push({
        from: line.from + detected.indent.length,
        to: line.from + detected.fullMatch.length,
        insert: prefix,
      });
      totalDelta += prefix.length - (detected.fullMatch.length - detected.indent.length);
    } else {
      // Not a list: add marker
      const indentMatch = line.text.match(/^(\s*)/);
      const indentLen = indentMatch ? indentMatch[1].length : 0;
      const prefix = makePrefix(targetType, lineIndex);
      changes.push({
        from: line.from + indentLen,
        to: line.from + indentLen,
        insert: prefix,
      });
      totalDelta += prefix.length;
    }

    lineIndex++;
    pos = line.to + 1;
  }

  view.dispatch({
    changes,
    selection: EditorSelection.cursor(Math.min(from + totalDelta, view.state.doc.length + totalDelta)),
  });
}
