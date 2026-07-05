import { ensureSyntaxTree } from '@codemirror/language';
import type { EditorState } from '@codemirror/state';

export interface HeadingItem {
  /** Heading level 1-6, matching `#` count. */
  level: number;
  /** Heading text with leading `#`s and trailing whitespace stripped. */
  text: string;
  /** Character offset where the heading's line starts (suitable for scrollIntoView / cursor jump). */
  offset: number;
}

const ATX_REGEX = /^ATXHeading([1-6])$/;
const HEADING_TEXT = /^#{1,6}\s+(.*?)\s*#*\s*$/;

/**
 * Extract ATX-style Markdown headings (`#`, `##`, …) from the given editor state.
 *
 * Uses CM6's syntax tree so headings inside fenced code blocks are correctly
 * excluded. `ensureSyntaxTree` runs with a 500ms budget — for very long
 * documents the result may be incomplete; callers should treat the return
 * value as best-effort and re-run after further edits/scrolls.
 */
export function extractHeadings(state: EditorState): HeadingItem[] {
  const headings: HeadingItem[] = [];
  const tree = ensureSyntaxTree(state, state.doc.length, 500);
  if (!tree) return headings;

  tree.iterate({
    enter(node) {
      const match = ATX_REGEX.exec(node.name);
      if (!match) return;
      const level = Number(match[1]);
      const line = state.doc.lineAt(node.from);
      const textMatch = HEADING_TEXT.exec(line.text);
      const text = (textMatch ? textMatch[1] : '').trim();
      if (text.length > 0) {
        headings.push({ level, text, offset: line.from });
      }
    },
  });

  return headings;
}
