import assert from 'node:assert/strict';
import test from 'node:test';

import { renderedTableCellOffsetToSource } from '../src/vendor/swarmnote-editor-core/plugins/table/tableCaret.ts';

test('maps rendered table caret offsets back to Markdown source', () => {
  assert.equal(renderedTableCellOffsetToSource('abc', 2), 2);
  assert.equal(renderedTableCellOffsetToSource('a\\|b', 2), 3);
  assert.equal(renderedTableCellOffsetToSource('a\\|b', 3), 4);
  assert.equal(renderedTableCellOffsetToSource('abc', 99), 3);
  assert.equal(renderedTableCellOffsetToSource('**bold**', 0), 2);
  assert.equal(renderedTableCellOffsetToSource('**bold**', 2), 4);
  assert.equal(renderedTableCellOffsetToSource('**bold**', 4), 8);
  assert.equal(renderedTableCellOffsetToSource('`code`', 2), 3);
  assert.equal(renderedTableCellOffsetToSource('[link](https://example.com)', 2), 3);
  assert.equal(renderedTableCellOffsetToSource('[link](https://example.com)', 4), '[link](https://example.com)'.length);
});
