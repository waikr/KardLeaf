import assert from 'node:assert/strict';
import test from 'node:test';
import { getSelectionToolbarCommands, shouldShowSelectionToolbar, computeSelectionToolbarPlacement,
  computeSelectionToolbarPageCapacity, paginateSelectionCommands } from '../src/marktext/selectionToolbar.ts';
import { normalizeSettings } from '../src/marktext/settings.ts';

test('MarkText command table and long-press-only caret visibility', () => {
  const commands = (hasSelection, canPaste, canWrite) => getSelectionToolbarCommands({hasSelection, canPaste, canWrite}).map(c => c.commandId);
  assert.deepEqual(commands(true, true, true), ['cut', 'copy', 'paste', 'selectAll']);
  assert.deepEqual(commands(false, false, true), ['selectAll']);
  assert.deepEqual(commands(false, true, true), ['paste', 'selectAll']);
  assert.deepEqual(commands(true, true, false), ['copy', 'selectAll']);
  const snapshot = { collapsed: true, withinEditor: true, text: '', rect: {left: 0, top: 20, right: 0, bottom: 40, width: 0, height: 20} };
  assert.equal(shouldShowSelectionToolbar({editorReady: true, suspended: false, snapshot}), false);
  assert.equal(shouldShowSelectionToolbar({editorReady: true, suspended: false, snapshot, caretSession: true}), true);
  assert.equal(shouldShowSelectionToolbar({editorReady: true, suspended: true, snapshot, caretSession: true}), false);
  assert.equal(shouldShowSelectionToolbar({editorReady: true, suspended: false, snapshot: {...snapshot, collapsed: false, text: '   '}}), false);
});

test('MarkText placement, arrow slots, settings and no command loss across pages', () => {
  const rect = {left: 100, right: 200, top: 20, bottom: 40, width: 100, height: 20};
  assert.deepEqual(computeSelectionToolbarPlacement(rect, {width: 194, height: 54}, {width: 400, height: 800}), {left: 53, top: 8, placement: 'above'});
  assert.deepEqual(computeSelectionToolbarPlacement({...rect, top: 700, bottom: 790}, {width: 194, height: 54}, {width: 400, height: 800}), {left: 53, top: 636, placement: 'above'});
  const commands = Array.from({length: 12}, (_, i) => i);
  for (const width of [240, 320, 400, 800]) for (const leadingBackArrow of [true, false]) {
    const capacity = computeSelectionToolbarPageCapacity(width);
    const pages = paginateSelectionCommands(commands, capacity, {leadingBackArrow});
    assert.deepEqual(pages.flat(), commands);
    pages.forEach((page, index) => assert.ok(page.length + Number(leadingBackArrow || index > 0) + Number(index < pages.length - 1) <= capacity));
  }
  assert.deepEqual(normalizeSettings({rows: 3, commands: ['toggleBold', 'unknown', 'toggleBold', 'toggleCode', 'constructor']}), {enabled: true, rows: 1, commands: ['toggleBold', 'toggleCode']});
  assert.equal(normalizeSettings({enabled: false}).enabled, false);
  assert.equal(normalizeSettings({rows: '2'}).rows, 2);
});
