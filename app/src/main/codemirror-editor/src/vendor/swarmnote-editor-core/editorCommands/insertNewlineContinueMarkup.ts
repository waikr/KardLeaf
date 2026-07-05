/**
 * Insert Newline Continue Markup
 *
 * 按 Enter 时自动续列表标记、blockquote 前缀。
 * 空列表项按 Enter 则删除标记、回退缩进。
 *
 * **功能：**
 * Markdown 感知的换行命令，在换行时自动保持格式连续性：
 * - 列表项的延续（自动添加 `- ` 或 `1. `）
 * - 引用块的延续（自动添加 `> `）
 * - 任务列表的延续（自动添加 `- [ ] `）
 * - 空列表项的处理（删除标记并回缩）
 *
 * 基于 CodeMirror 的 insertNewlineContinueMarkup，参考 Joplin 的 fork 版本。
 *
 * Copyright (C) 2018-2021 by Marijn Haverbeke <marijn@haverbeke.berlin> and others
 * MIT License
 */
import { markdownLanguage } from '@codemirror/lang-markdown';
import { indentUnit, syntaxTree } from '@codemirror/language';
import {
  type ChangeSpec,
  countColumn,
  EditorSelection,
  type EditorState,
  type StateCommand,
  type Text,
} from '@codemirror/state';
import type { SyntaxNode } from '@lezer/common';

/**
 * 上下文类
 * 
 * **功能：**
 * 封装当前行的 Markdown 结构信息，用于生成正确的换行前缀。
 */
class Context {
  constructor(
    public readonly node: SyntaxNode,
    public readonly from: number,
    public readonly to: number,
    public readonly spaceBefore: string,
    public readonly spaceAfter: string,
    public readonly type: string,
    public readonly item: SyntaxNode | null,
  ) {}

  blank(maxWidth: number | null, trailing = true) {
    let result = this.spaceBefore + (this.node.name === 'Blockquote' ? '>' : '');
    if (maxWidth !== null) {
      while (result.length < maxWidth) result += ' ';
      return result;
    }
    for (let i = this.to - this.from - result.length - this.spaceAfter.length; i > 0; i--)
      result += ' ';
    return result + (trailing ? this.spaceAfter : '');
  }

  marker(doc: Text, add: number) {
    const number =
      this.node.name === 'OrderedList' ? String(+itemNumber(this.item!, doc)[2] + add) : '';
    return this.spaceBefore + number + this.type + this.spaceAfter;
  }
}

function getContext(node: SyntaxNode, doc: Text) {
  const nodes: SyntaxNode[] = [];
  for (let cur: SyntaxNode | null = node; cur && cur.name !== 'Document'; cur = cur.parent) {
    if (
      cur.name === 'ListItem' ||
      cur.name === 'Blockquote' ||
      cur.name === 'FencedCode'
    ) {
      nodes.push(cur);
    }
  }

  const context: Context[] = [];
  for (let i = nodes.length - 1; i >= 0; i--) {
    const node = nodes[i];
    const line = doc.lineAt(node.from);
    const startPos = node.from - line.from;

    if (node.name === 'FencedCode') {
      context.push(new Context(node, startPos, startPos, '', '', '', null));
    } else if (node.name === 'Blockquote') {
      const match = /^ *>( ?)/.exec(line.text.slice(startPos));
      if (match) {
        context.push(
          new Context(node, startPos, startPos + match[0].length, '', match[1], '>', null),
        );
      }
    } else if (node.name === 'ListItem' && node.parent!.name === 'OrderedList') {
      const match = /^( *)\d+([.)])( *)/.exec(line.text.slice(startPos));
      if (match) {
        let after = match[3];
        let len = match[0].length;
        if (after.length >= 4) {
          after = after.slice(0, after.length - 4);
          len -= 4;
        }
        context.push(
          new Context(node.parent!, startPos, startPos + len, match[1], after, match[2], node),
        );
      }
    } else if (node.name === 'ListItem' && node.parent!.name === 'BulletList') {
      const match = /^( *)([-+*])( {1,4}\[[ xX]\])?( +)/.exec(line.text.slice(startPos));
      if (match) {
        let after = match[4];
        let len = match[0].length;
        if (after.length > 4) {
          after = after.slice(0, after.length - 4);
          len -= 4;
        }
        let type = match[2];
        if (match[3]) type += match[3].replace(/[xX]/, ' ');
        context.push(
          new Context(node.parent!, startPos, startPos + len, match[1], after, type, node),
        );
      }
    }
  }
  return context;
}

function itemNumber(item: SyntaxNode, doc: Text) {
  return /^(\s*)(\d+)(?=[.)])/.exec(doc.sliceString(item.from, item.from + 10))!;
}

function normalizeIndent(content: string, state: EditorState) {
  const blank = /^[ \t]*/.exec(content)![0].length;
  if (!blank || state.facet(indentUnit) !== '\t') return content;
  const col = countColumn(content, 4, blank);
  let space = '';
  for (let i = col; i > 0; ) {
    if (i >= 4) {
      space += '\t';
      i -= 4;
    } else {
      space += ' ';
      i--;
    }
  }
  return space + content.slice(blank);
}

function renumberList(after: SyntaxNode, doc: Text, changes: ChangeSpec[], offset = 0) {
  let prev = -1;
  let current: SyntaxNode | null = after;
  while (current) {
    if (current.name === 'ListItem') {
      const m = itemNumber(current, doc);
      const number = +m[2];
      if (prev >= 0) {
        if (number !== prev + 1) return;
        changes.push({
          from: current.from + m[1].length,
          to: current.from + m[0].length,
          insert: String(prev + 2 + offset),
        });
      }
      prev = number;
    }
    current = current.nextSibling;
  }
}

export const insertNewlineContinueMarkup: StateCommand = ({ state, dispatch }) => {
  const tree = syntaxTree(state);
  const { doc } = state;
  let dont = null;

  const changes = state.changeByRange((range) => {
    if (!range.empty || !markdownLanguage.isActiveAt(state, range.from))
      return (dont = { range });

    const pos = range.from;
    const line = doc.lineAt(pos);
    const context = getContext(tree.resolveInner(pos, -1), doc);

    while (context.length && context[context.length - 1].from > pos - line.from) context.pop();
    if (!context.length) return (dont = { range });

    const inner = context[context.length - 1];
    if (inner.to - inner.spaceAfter.length > pos - line.from) return (dont = { range });

    const emptyLine = pos >= inner.to - inner.spaceAfter.length && !/\S/.test(line.text.slice(inner.to));

    // Empty line in list → delete a level of markup
    if (inner.item && emptyLine) {
      if (
        inner.node.firstChild!.to >= pos ||
        (line.from > 0 && !/[^\s>]/.test(doc.lineAt(line.from - 1).text))
      ) {
        const next = context.length > 1 ? context[context.length - 2] : null;
        let delTo: number;
        let insert = '';
        if (next && next.item) {
          delTo = line.from + next.from;
          insert = next.marker(doc, 1);
        } else {
          delTo = line.from + (next ? next.to : 0);
        }
        const changes: ChangeSpec[] = [{ from: delTo, to: pos, insert }];
        if (inner.node.name === 'OrderedList') renumberList(inner.item!, doc, changes, -2);
        if (next && next.node.name === 'OrderedList') renumberList(next.item!, doc, changes);
        return { range: EditorSelection.cursor(delTo + insert.length), changes };
      }
      // Move this line down
      let insert = '';
      for (let i = 0, e = context.length - 2; i <= e; i++) {
        insert += context[i].blank(
          i < e ? countColumn(line.text, 4, context[i + 1].from) - insert.length : null,
          i < e,
        );
      }
      insert = normalizeIndent(insert, state);
      return {
        range: EditorSelection.cursor(pos + insert.length + 1),
        changes: { from: line.from, insert: insert + state.lineBreak },
      };
    }

    // Empty blockquote line
    if (inner.node.name === 'Blockquote' && emptyLine && line.from) {
      const prevLine = doc.lineAt(line.from - 1);
      const quoted = />\s*$/.exec(prevLine.text);
      if (quoted && quoted.index === inner.from) {
        const changes = state.changes([
          { from: prevLine.from + quoted.index, to: prevLine.to },
          { from: line.from + inner.from, to: line.to },
        ]);
        return { range: range.map(changes), changes };
      }
    }

    // Normal case: continue markup
    const changeList: ChangeSpec[] = [];
    if (inner.node.name === 'OrderedList') renumberList(inner.item!, doc, changeList);

    const continued = inner.item && inner.item.from < line.from;
    let insert = '';
    if (!continued || /^[\s\d.)\-+*>]*/.exec(line.text)![0].length >= inner.to) {
      for (let i = 0, e = context.length - 1; i <= e; i++) {
        insert +=
          i === e && !continued
            ? context[i].marker(doc, 1)
            : context[i].blank(
                i < e
                  ? countColumn(line.text, 4, context[i + 1].from) - insert.length
                  : null,
              );
      }
    }

    let from = pos;
    while (from > line.from && /\s/.test(line.text.charAt(from - line.from - 1))) from--;

    insert = normalizeIndent(insert, state);
    changeList.push({ from, to: pos, insert: state.lineBreak + insert });
    return { range: EditorSelection.cursor(from + insert.length + 1), changes: changeList };
  });

  if (dont) return false;
  dispatch(state.update(changes, { scrollIntoView: true, userEvent: 'input' }));
  return true;
};
