import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { EditorState } from '@codemirror/state';
import { search, setSearchQuery } from '@codemirror/search';
import { androidSearchQuery, searchMatches, searchSummary } from '../src/search.ts';

const cases = JSON.parse(readFileSync(new URL('../../../test/resources/search-cases.json', import.meta.url)));
for (const c of cases) test(c.name, () => {
  const query = androidSearchQuery(c.query, !!c.useRegex, !!c.matchCase);
  let state = EditorState.create({ doc: c.text, extensions: [search()] });
  state = state.update({ effects: setSearchQuery.of(query) }).state;
  assert.deepEqual(searchMatches(state).map(m => [m.from, m.to]), c.ranges);
  assert.equal(!!searchSummary(state).error, !!c.error);
});

test('query, same-length edits and caret update the summary from the actual document', () => {
  let state = EditorState.create({ doc: 'foo foo', extensions: [search()] });
  state = state.update({ effects: setSearchQuery.of(androidSearchQuery('foo', false, true)), selection: { anchor: 5 } }).state;
  assert.equal(searchSummary(state).currentOrdinal, 2);
  state = state.update({ changes: { from: 0, to: 3, insert: 'bar' } }).state;
  assert.equal(searchSummary(state).count, 1);
  assert.equal(searchSummary(state).currentStart, 4);
  state = state.update({ effects: setSearchQuery.of(androidSearchQuery('[', true, true)) }).state;
  assert.equal(searchSummary(state).count, 0);
});
