import { RegExpCursor, SearchQuery, getSearchQuery } from '@codemirror/search';
import type { EditorState } from '@codemirror/state';
import { Decoration, ViewPlugin } from '@codemirror/view';

const nonEmptyMatch = (_match: string, _state: EditorState, from: number, to: number) => to > from;

export function androidSearchQuery(search: string, regexp: boolean, caseSensitive: boolean, replace = '') {
  return new SearchQuery({
    search: search.replace(/\r\n?/g, '\n'), replace: replace.replace(/\r\n?/g, '\n'),
    regexp, caseSensitive, literal: true, test: nonEmptyMatch,
  });
}

export function searchMatches(state: EditorState) {
  const query = getSearchQuery(state);
  const matches: Array<{ from: number; to: number }> = [];
  if (query.valid) {
    for (const cursor = query.getCursor(state); !cursor.next().done;) {
      if (cursor.value.to > cursor.value.from) matches.push({ from: cursor.value.from, to: cursor.value.to });
    }
  }
  return matches;
}

export function searchSummary(state: EditorState) {
  const query = getSearchQuery(state);
  const matches = searchMatches(state);
  const selection = state.selection.main;
  const index = matches.findIndex(match =>
    selection.from >= match.from && selection.to <= match.to && selection.from < match.to);
  return {
    query: query.search, useRegex: query.regexp, matchCase: query.caseSensitive,
    count: matches.length, currentStart: matches[index]?.from ?? -1,
    currentEnd: matches[index]?.to ?? -1, currentOrdinal: index + 1,
    error: query.search && !query.valid ? '正则表达式无效' : null,
  };
}

export function regexSearchChanges(state: EditorState) {
  const query = getSearchQuery(state);
  const changes: Array<{ from: number; to: number; insert: string }> = [];
  if (!query.valid) return changes;
  const cursor = new RegExpCursor(state.doc, query.search, { ignoreCase: !query.caseSensitive });
  while (!cursor.next().done) {
    const { from, to, match } = cursor.value;
    if (from === to) continue;
    // CodeMirror 6.6 expands an unmatched optional group to "undefined". Keep Beta's empty-group semantics.
    const insert = query.replace.replace(/\$([$&]|\d+)/g, (token, group: string) => {
      if (group === '$') return '$';
      if (group === '&') return match[0];
      for (let length = group.length; length > 0; length--) {
        const index = Number(group.slice(0, length));
        if (index > 0 && index < match.length) return (match[index] ?? '') + group.slice(length);
      }
      return token;
    });
    changes.push({ from, to, insert });
  }
  return changes;
}

// Only paint the current match; this mark never replaces text or makes a range atomic.
export const currentSearchHighlight = ViewPlugin.define(view => ({
  decorations: Decoration.none,
  update(update) {
    if (!update.docChanged && !update.selectionSet && !update.transactions.some(tr => tr.effects.length)) return;
    const summary = searchSummary(update.state);
    const selection = update.state.selection.main;
    this.decorations = summary.currentStart < 0 || (selection.from === summary.currentStart && selection.to === summary.currentEnd)
      ? Decoration.none : Decoration.set([
      Decoration.mark({ class: 'cm-searchMatch-selected' }).range(summary.currentStart, summary.currentEnd),
    ]);
  },
}), { decorations: plugin => plugin.decorations });
