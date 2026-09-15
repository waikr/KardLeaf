import '../src/editor';
import { EditorState } from '@codemirror/state';
import { Decoration, EditorView, WidgetType } from '@codemirror/view';
import { forceParsing } from '@codemirror/language';

const checks: string[] = [];
const failures: string[] = [];
const wait = (ms = 60) => new Promise((resolve) => setTimeout(resolve, ms));
const assert = (value: unknown, message: string) => { if (!value) throw new Error(message); };
const api = window.KardLeafEditor as Record<string, (...args: unknown[]) => unknown>;
const view = EditorView.findFromDOM(document.querySelector('.cm-editor')!)!;
const fixture = 'Start of document.\n\n**bold text** and [[WikiTarget]] and [external](https://example.com).\n\n' +
  '| Header | Other |\n| --- | --- |\n| abcdefghij | a\\|b **bold** |\n\n' +
  '> [!NOTE] Notice\n> content\n\n$$\nx^2\n$$\n\n```mermaid\ngraph TD; A-->B\n```\n\n' +
  '<div>HTML text</div>\n\n![image](data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7)\n\n' +
  '```js\nconst value = 1;\n```\n\nAfter all blocks.\n\n';

function selectionTrace() {
  const selection = window.getSelection();
  return JSON.stringify({ text: selection?.toString(), active: document.activeElement?.outerHTML.slice(0, 80),
    anchor: selection?.anchorNode?.parentElement?.outerHTML.slice(0, 120),
    cm: view.state.selection.main.toJSON() });
}

async function reset(doc = fixture) {
  document.activeElement instanceof HTMLElement && document.activeElement.blur();
  window.getSelection()?.removeAllRanges();
  api.setContentFromAndroid(doc, 0, 0);
  forceParsing(view, view.state.doc.length, 1000);
  await wait(180);
}

async function check(name: string, run: () => unknown) {
  try { await run(); checks.push(`PASS ${name}`); }
  catch (error) { failures.push(`${name}: ${String(error)}`); }
}

function selectCell(cell: HTMLElement, from: number, to: number) {
  cell.focus();
  const node = cell.firstChild!;
  window.getSelection()!.setBaseAndExtent(node, from, node, to);
  return node;
}

function touch(target: HTMLElement, type: string, x: number, y: number) {
  const point = new Touch({ identifier: 1, target, clientX: x, clientY: y });
  target.dispatchEvent(new TouchEvent(type, { bubbles: true, cancelable: true,
    touches: type === 'touchend' || type === 'touchcancel' ? [] : [point], changedTouches: [point] }));
}

class FocusProbeWidget extends WidgetType {
  toDOM() {
    const widget = document.createElement('span');
    widget.className = 'focus-probe-widget';
    const editableText = document.createElement('span');
    editableText.className = 'focus-probe-text';
    editableText.contentEditable = 'true';
    editableText.textContent = 'widget text';
    widget.append(editableText);
    return widget;
  }
}

function createFocusProbe() {
  const parent = document.createElement('div');
  document.body.append(parent);
  const replacement = Decoration.replace({ widget: new FocusProbeWidget() }).range(6, 7);
  const view = new EditorView({
    state: EditorState.create({
      doc: 'beforeXafter',
      extensions: [EditorView.decorations.of(Decoration.set([replacement]))],
    }),
    parent,
  });
  const text = view.dom.querySelector<HTMLElement>('.focus-probe-text')?.firstChild;
  assert(text?.nodeType === Node.TEXT_NODE, 'focus probe widget text did not render');
  return { parent, view, text: text as Text };
}

function focusDiagnostics() {
  const diagnostics = (window as unknown as {
    __KardLeafCodeMirrorSelectionDiagnostics?: Record<string, number>;
  }).__KardLeafCodeMirrorSelectionDiagnostics;
  assert(diagnostics, 'CodeMirror focus diagnostics were not installed');
  return {
    candidate: diagnostics.cmAndroidFocusWorkaroundCandidateCount ?? 0,
    executed: diagnostics.cmAndroidFocusWorkaroundExecutedCount ?? 0,
    skipped: diagnostics.cmAndroidFocusWorkaroundSkippedForNativeRangeCount ?? 0,
  };
}

function focusDiagnosticsDelta(before: ReturnType<typeof focusDiagnostics>, after: ReturnType<typeof focusDiagnostics>) {
  return {
    candidate: after.candidate - before.candidate,
    executed: after.executed - before.executed,
    skipped: after.skipped - before.skipped,
  };
}

function setNativeSelection(anchor: Node, anchorOffset: number, focus: Node, focusOffset: number) {
  const selection = window.getSelection()!;
  selection.removeAllRanges();
  selection.setBaseAndExtent(anchor, anchorOffset, focus, focusOffset);
  document.dispatchEvent(new Event('selectionchange'));
}

function focusProbeTrace(view: EditorView, label: string) {
  const selection = window.getSelection();
  const observer = (view as unknown as { observer?: { selectionRange?: Selection } }).observer;
  const widget = view.dom.querySelector<HTMLElement>('.focus-probe-widget');
  return `${label} ua=${navigator.userAgent} hasFocus=${view.hasFocus} active=${document.activeElement?.nodeName} ` +
    `widgetEditable=${widget?.contentEditable} selection=${selection?.anchorNode?.parentElement?.className}:` +
    `${selection?.anchorOffset}->${selection?.focusNode?.parentElement?.className}:${selection?.focusOffset} ` +
    `observer=${observer?.selectionRange?.anchorNode?.parentElement?.className}:` +
    `${observer?.selectionRange?.anchorOffset}->${observer?.selectionRange?.focusNode?.parentElement?.className}:` +
    `${observer?.selectionRange?.focusOffset}`;
}

async function run() {
  await check('short touch preserves heading markers until the new caret settles', async () => {
    const body = '#### Heading\n\nOther paragraph';
    await reset(body);
    api.focus();
    view.dispatch({ selection: { anchor: 8 } });
    await wait();
    const markers = () => view.contentDOM.textContent?.includes('####');
    assert(markers(), 'heading source was not revealed');
    const line = view.contentDOM.querySelectorAll<HTMLElement>('.cm-line')[2];
    const rect = line.getBoundingClientRect();
    touch(line, 'touchstart', rect.left + 15, rect.top + 8);
    assert(markers(), 'touchstart hid the old heading before caret placement');
    touch(line, 'touchend', rect.left + 15, rect.top + 8);
    assert(markers(), 'touchend changed heading markers before caret placement');
    view.dispatch({ selection: { anchor: body.indexOf('Other') + 2 }, userEvent: 'select.pointer' });
    await wait(120);
    assert(!markers(), 'heading stayed revealed after the caret left');
    assert(api.getText() === body, 'tap changed the document');
    view.dispatch({ selection: { anchor: 8 } });
    await wait();
    const heading = view.contentDOM.querySelector<HTMLElement>('.cm-headerLine')!;
    const headingRect = heading.getBoundingClientRect();
    touch(heading, 'touchstart', headingRect.left + 20, headingRect.top + 8);
    assert(markers(), 'press hid the heading before a native range existed');
    view.dispatch({ selection: { anchor: 5, head: 9 }, userEvent: 'select.pointer' });
    assert(!markers(), 'a real range formed during the press failed to hide heading syntax');
    touch(view.contentDOM, 'touchend', headingRect.left + 20, headingRect.top + 8);
  });

  await check('collapsed table caret survives pointer selection synchronization and IME reveal', async () => {
    await reset();
    api.focus();
    const cell = view.dom.querySelector<HTMLElement>('td[contenteditable]')!;
    const before = focusDiagnostics();
    selectCell(cell, 3, 3);
    // Android CM may flush a pointer selection before the cell's rAF sync.
    view.dispatch({ selection: { anchor: fixture.indexOf('abcdefghij') + 3 }, userEvent: 'select.pointer' });
    await wait(120);
    assert(document.activeElement === cell, `table lost focus: ${selectionTrace()}`);
    assert(window.getSelection()?.focusNode === cell.firstChild && window.getSelection()?.focusOffset === 3,
      `table caret moved: ${selectionTrace()}`);
    assert(api.prepareImeReveal(300) === 'contenteditable', 'IME reveal targeted the CM root instead of the cell');
    assert(focusDiagnostics().executed === before.executed, 'table tap cycled Android focus');
    assert(api.getText() === fixture, 'table tap changed the document');
    api.selectRange(2, 2);
    await wait();
    assert(document.activeElement === view.contentDOM && view.state.selection.main.head === 2,
      'explicit focus could not leave the table');
  });

  await check('ordered lists and background spans never collapse native handles while normalizing endpoints', async () => {
    const body = 'Anchor text\n\n1. plain item\n2. <span style="background-color:#fdd835">colored item</span> tail\n' +
      '3. **bold** and ==highlight==\n\n> quoted <span style="background-color:#fdd835">colored text</span> tail\n\nAfter';
    await reset(body);
    api.focus();
    let anchor = view.domAtPos(0), head = view.domAtPos(6);
    setNativeSelection(anchor.node, anchor.offset, head.node, head.offset);
    await wait();
    const collapse = Selection.prototype.collapse;
    const setRange = Selection.prototype.setBaseAndExtent;
    let collapsedRepairs = 0;
    let atomicRepairs = 0;
    Selection.prototype.setBaseAndExtent = function(anchorNode, anchorOffset, focusNode, focusOffset) {
      if (new Error().stack?.includes('.updateSelection')) atomicRepairs++;
      return setRange.call(this, anchorNode, anchorOffset, focusNode, focusOffset);
    };
    Selection.prototype.collapse = function(node, offset) {
      if (!this.isCollapsed && this.anchorNode && view.contentDOM.contains(this.anchorNode)) collapsedRepairs++;
      return collapse.call(this, node, offset);
    };
    try {
      const elements = Array.from(view.contentDOM.querySelectorAll('.cm-line, .cm-line span'));
      for (const reverse of [false, true]) {
        for (const element of elements) {
          for (let offset = 0; offset <= element.childNodes.length; offset++) {
            if (!element.isConnected) continue;
            anchor = view.domAtPos(reverse ? body.length : 0);
            setNativeSelection(anchor.node, anchor.offset, element, offset);
            await wait(16);
          }
        }
      }
      checks.push(`MEASURE ordered/background native repairs: collapsed=${collapsedRepairs}, atomic=${atomicRepairs}`);
      assert(collapsedRepairs === 0, `CodeMirror collapsed and recreated the native range ${collapsedRepairs} times`);
      assert(atomicRepairs > 0, 'test did not exercise native endpoint normalization');
      assert(api.getText() === body, 'drag changed list/background source');
      api.selectRange(8, 2);
      await wait();
      assert(view.state.selection.main.anchor === 8 && view.state.selection.main.head === 2, 'explicit reversed selection failed');
      assert(window.getSelection()?.toString() === body.slice(2, 8), 'native reversed selection differs from CM');
    } finally { Selection.prototype.collapse = collapse; Selection.prototype.setBaseAndExtent = setRange; }
  });

  await check('long-text handle movement defers toolbar range layout until selection settles', async () => {
    const longText = Array.from({ length: 1600 }, (_, i) => `Line ${i}: ${'long text '.repeat(6)}`).join('\n');
    await reset(longText);
    api.focus();
    const original = Range.prototype.getBoundingClientRect;
    let toolbarLayouts = 0;
    let expectedHead = 0;
    Range.prototype.getBoundingClientRect = function() {
      if (new Error().stack?.includes('Toolbar.read')) toolbarLayouts++;
      return original.call(this);
    };
    try {
      for (let i = 0; i < 16; i++) {
        const anchor = view.domAtPos(0);
        const focus = Array.from(view.contentDOM.querySelectorAll('.cm-line')).at(-1)!.firstChild!;
        expectedHead = view.posAtDOM(focus, focus.textContent!.length);
        setNativeSelection(anchor.node, anchor.offset, focus, focus.textContent!.length);
        view.scrollDOM.scrollTop += 80;
        await wait(20);
      }
      const duringDrag = toolbarLayouts;
      await wait(220);
      const afterSettled = toolbarLayouts - duringDrag;
      checks.push(`MEASURE long-text toolbar layouts: dragging=${duringDrag}, settled=${afterSettled}`);
      assert(duringDrag === 0, `toolbar measured the growing range ${duringDrag} times during drag`);
      assert(afterSettled > 0 && afterSettled <= 2, 'toolbar did not resume once after selection settled');
      assert(!window.getSelection()?.isCollapsed && view.state.selection.main.to === expectedHead,
        `drag lost the native range: cm=${view.state.selection.main.from}:${view.state.selection.main.to}, expected=${expectedHead}`);
      assert(api.getText() === longText, 'drag changed the text');
    } finally { Range.prototype.getBoundingClientRect = original; }
  });

  for (const signal of ['touchend', 'native-tap']) {
    await check(`toolbar ${signal} does not collapse a new native range or finish its handles`, async () => {
      await reset('alpha beta gamma');
      api.focus();
      const line = view.contentDOM.querySelector<HTMLElement>('.cm-line')!;
      const node = line.firstChild!;
      setNativeSelection(node, 0, node, 5);
      await wait(180);
      const bar = document.querySelector<HTMLElement>('.mobile-selection-toolbar')!;
      assert(bar.style.display !== 'none', 'toolbar was not active before the next gesture');
      const rect = view.coordsAtPos(8)!;
      const x = rect.left, y = (rect.top + rect.bottom) / 2;
      const requests = (window as unknown as { testSelectionRequests: string[] }).testSelectionRequests;
      const before = requests.filter(op => op === 'finish').length;
      touch(line, 'touchstart', x, y);
      setNativeSelection(node, 6, node, 10);
      await wait(350);
      if (signal === 'touchend') touch(line, 'touchend', x, y);
      else {
        touch(line, 'touchcancel', x, y);
        (window as any).KardLeafSelection.event('tap', { x, y });
      }
      await wait(120);
      assert(window.getSelection()?.toString() === 'beta', `native range overwritten: ${selectionTrace()}`);
      assert(view.state.selection.main.from === 6 && view.state.selection.main.to === 10, 'CM range overwritten');
      assert(requests.filter(op => op === 'finish').length === before, 'toolbar finished the native handle session');
      assert(view.state.doc.toString() === 'alpha beta gamma', 'gesture changed the document');
    });
  }

  await check('collapsed widget caret still runs the Android focus workaround', async () => {
    const { parent, view, text } = createFocusProbe();
    try {
      view.focus();
      setNativeSelection(text, 0, text, 0);
      await wait();
      const probeBefore = focusProbeTrace(view, 'before');
      const before = focusDiagnostics();
      view.dispatch({ selection: { anchor: 0, head: 2 }, userEvent: 'select.pointer' });
      const delta = focusDiagnosticsDelta(before, focusDiagnostics());
      assert(delta.candidate > 0, `collapsed probe did not reach workaround: ${JSON.stringify(delta)} ${probeBefore} ${focusProbeTrace(view, 'after')}`);
      assert(delta.executed > 0, `collapsed probe was incorrectly suppressed: ${JSON.stringify(delta)}`);
      assert(delta.skipped === 0, `collapsed probe was classified as native range: ${JSON.stringify(delta)}`);
    } finally {
      view.destroy();
      parent.remove();
      window.getSelection()?.removeAllRanges();
    }
  });

  await check('non-empty native selection suppresses the Android focus workaround even with an empty CM selection', async () => {
    const { parent, view, text } = createFocusProbe();
    try {
      view.focus();
      setNativeSelection(text, 0, text, 6);
      await wait();
      const probeBefore = focusProbeTrace(view, 'before');
      const before = focusDiagnostics();
      view.dispatch({ selection: { anchor: 0 }, userEvent: 'select.pointer' });
      const delta = focusDiagnosticsDelta(before, focusDiagnostics());
      assert(delta.candidate > 0, `native-range probe did not reach workaround: ${JSON.stringify(delta)} ${probeBefore} ${focusProbeTrace(view, 'after')}`);
      assert(delta.executed === 0, `native-range probe executed blur/focus: ${JSON.stringify(delta)}`);
      assert(delta.skipped > 0, `native-range probe was not protected: ${JSON.stringify(delta)}`);
    } finally {
      view.destroy();
      parent.remove();
      window.getSelection()?.removeAllRanges();
    }
  });

  await check('selection outside the editor does not suppress its workaround path', async () => {
    const { parent, view, text } = createFocusProbe();
    const outside = document.createElement('div');
    outside.contentEditable = 'true';
    outside.textContent = 'outside selection';
    document.body.append(outside);
    try {
      view.focus();
      setNativeSelection(outside.firstChild!, 0, outside.firstChild!, 7);
      await wait();
      const before = focusDiagnostics();
      view.dispatch({ selection: { anchor: 0, head: 2 } });
      const delta = focusDiagnosticsDelta(before, focusDiagnostics());
      assert(delta.candidate === 0 && delta.executed === 0 && delta.skipped === 0,
        `outside selection affected editor workaround: ${JSON.stringify(delta)}`);
    } finally {
      view.destroy();
      parent.remove();
      outside.remove();
      window.getSelection()?.removeAllRanges();
    }
  });

  await check('long press keeps the formatted surface instead of revealing Markdown source', async () => {
    await reset();
    // Main now suppresses source reveal until an explicit user editing action.
    api.focus();
    const bold = fixture.indexOf('bold text');
    view.dispatch({ selection: { anchor: bold } });
    await wait();
    assert(view.contentDOM.textContent?.includes('**bold text**'), 'cursor did not reveal the editable Markdown source');
    view.dispatch({ selection: { anchor: bold, head: bold + 4 }, userEvent: 'select.pointer' });
    await wait();
    assert(!view.contentDOM.textContent?.includes('**bold text**'),
      `long press revealed Markdown source: ${selectionTrace()}`);
    const line = view.domAtPos(bold).node;
    for (let head = bold + 1; head < bold + 9; head += 2) {
      view.dispatch({ selection: { anchor: bold, head }, userEvent: 'select.pointer' });
      assert(line.isConnected, `selection replaced the rendered line at ${head}`);
    }

    await reset();
    const nativeFrom = view.domAtPos(bold);
    const nativeTo = view.domAtPos(bold + 4);
    view.focus();
    setNativeSelection(nativeFrom.node, nativeFrom.offset, nativeTo.node, nativeTo.offset);
    await wait();
    assert(window.getSelection()?.toString() === 'bold',
      `native selection did not stay on rendered text: ${selectionTrace()}`);
    assert(!view.contentDOM.textContent?.includes('**bold text**'),
      `native selection switched to Markdown source: ${selectionTrace()}`);
  });

  await check('viewport changes keep revealed inline text nodes alive during selection', async () => {
    await reset(fixture + ('**more text** and [[MoreTarget]]\n\n').repeat(650));
    const bold = view.state.doc.toString().indexOf('bold text');
    view.dispatch({ selection: { anchor: bold } });
    await wait();
    view.dispatch({ selection: { anchor: bold, head: bold + 3 }, userEvent: 'select.pointer' });
    await wait();
    const line = view.domAtPos(bold).node;
    view.scrollDOM.scrollTop = 150;
    await wait();
    assert(line.isConnected, 'scroll/reveal replaced the selected inline node');
    view.scrollDOM.scrollTop = 1000;
    await wait();
    assert(view.viewport.to > 1000, 'viewport failed to extend during selection');
  });

  await check('asynchronous image refresh waits until the native selection collapses', async () => {
    await reset();
    view.dispatch({ selection: { anchor: 1, head: 20 }, userEvent: 'select' });
    const before = view.state.facet(EditorView.decorations).filter((value) => typeof value !== 'function');
    api.setImageMap({});
    const after = view.state.facet(EditorView.decorations).filter((value) => typeof value !== 'function');
    assert(after.every((value, i) => value === before[i]), 'image refresh rebuilt a widget during selection');
    window.getSelection()?.removeAllRanges();
    view.dispatch({ selection: { anchor: 0 }, userEvent: 'select' });
    await wait();
    const resumed = view.state.facet(EditorView.decorations).filter((value) => typeof value !== 'function');
    assert(resumed.some((value, i) => value !== before[i]), 'image refresh remained frozen after selection ended');
  });

  await check('table pointerup preserves a non-empty native selection and source range', async () => {
    await reset();
    const cell = view.dom.querySelector<HTMLElement>('td[contenteditable]')!;
    assert(cell, 'table did not render');
    const widget = cell.closest<HTMLElement>('.cm-table-widget');
    assert(widget?.contentEditable === 'false', 'table cells lost their independent editing boundary');
    assert(cell.contentEditable === 'true' && cell.tabIndex === 0, 'table cell is not a focusable edit host');
    const node = selectCell(cell, 1, 6);
    const rect = cell.getBoundingClientRect();
    cell.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerType: 'touch',
      clientX: rect.left + 4, clientY: rect.top + 4 }));
    await wait();
    assert(window.getSelection()?.toString() === 'bcdef', `pointerup changed the native selection: ${selectionTrace()}`);
    assert(node.isConnected, 'selected table text node was replaced');
    const sel = view.state.selection.main;
    assert(view.state.sliceDoc(sel.from, sel.to) === 'bcdef', 'table selection mapped to the wrong source');
  });

  await check('native selection crosses a rendered table in both directions', async () => {
    await reset();
    const table = view.dom.querySelector('.cm-table-widget')!;
    const from = view.domAtPos(2);
    const to = view.domAtPos(fixture.indexOf('After all') + 5);
    view.focus();
    const selection = window.getSelection()!;
    selection.setBaseAndExtent(from.node, from.offset, to.node, to.offset);
    await wait();
    assert(!selection.isCollapsed && selection.toString().includes('Header'), 'forward drag lost the table');
    assert(table.isConnected, 'forward drag rebuilt the table');
    selection.setBaseAndExtent(to.node, to.offset, from.node, from.offset);
    await wait();
    assert(!selection.isCollapsed && selection.toString().includes('Header'), 'reverse drag lost the table');
    assert(table.isConnected, 'reverse drag rebuilt the table');
  });

  await check('table selection maps unescaped pipe text to Markdown source', async () => {
    await reset();
    const cell = view.dom.querySelectorAll<HTMLElement>('td[contenteditable]')[1];
    // parseMarkdownTable already decodes the pipe escape, even in the focused
    // raw cell. Compare the actual native text as well as the Markdown slice.
    selectCell(cell, 1, 3);
    await wait();
    const sel = view.state.selection.main;
    assert(window.getSelection()?.toString() === '|b', 'unexpected native cell selection');
    assert(view.state.sliceDoc(sel.from, sel.to) === '\\|b', 'pipe escape shifted the source selection');
  });

  await check('table editing preserves rendered inline Markdown', async () => {
    await reset();
    const cell = view.dom.querySelectorAll<HTMLElement>('td[contenteditable]')[1];
    const bold = cell.querySelector('strong');
    assert(bold, 'formatted table cell did not render');
    cell.focus();
    bold.textContent = 'bold+';
    cell.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: '+' }));
    cell.blur();
    await wait();
    assert(view.state.doc.toString().includes('a\\|b **bold+**'), 'table edit discarded inline Markdown');
  });

  await check('table blur preserves selected nodes until selection is cancelled', async () => {
    await reset();
    const cell = view.dom.querySelector<HTMLElement>('td[contenteditable]')!;
    const node = selectCell(cell, 1, 6);
    await wait();
    cell.blur();
    await wait();
    assert(node.isConnected && window.getSelection()?.toString() === 'bcdef', `blur changed selection: connected=${node.isConnected} ${selectionTrace()}`);
    window.getSelection()?.removeAllRanges();
    await wait();
    assert(cell.textContent === 'abcdefghij', 'preview restoration changed cell content');
  });

  await check('explicit search selection reveals its target and edits still undo/redo', async () => {
    await reset();
    const pos = fixture.indexOf('x^2');
    api.selectRange(pos, pos + 3);
    await wait();
    assert(view.contentDOM.textContent?.includes('x^2'), 'search target remained hidden');
    view.dispatch({ changes: { from: pos, to: pos + 3, insert: 'y^3' },
      selection: { anchor: pos + 3 }, userEvent: 'input.type' });
    assert(view.state.sliceDoc(pos, pos + 3) === 'y^3', 'edit was blocked while selecting');
    api.undo();
    assert(view.state.sliceDoc(pos, pos + 3) === 'x^2', 'undo failed');
    api.redo();
    assert(view.state.sliceDoc(pos, pos + 3) === 'y^3', 'redo failed');
  });

  await check('table Enter adds a row and restores the target cell focus', async () => {
    await reset();
    const cell = view.dom.querySelector<HTMLElement>('td[contenteditable]')!;
    selectCell(cell, 10, 10);
    await wait();
    cell.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    await wait();
    const rows = view.dom.querySelectorAll('tbody tr');
    assert(rows.length === 2, 'Enter did not append a table row');
    assert(rows[1].contains(document.activeElement), 'focus did not move to the new row');
    assert(view.state.doc.toString().includes('abcdefghij'), 'row insertion changed existing cell text');
  });

  await check('reused table DOM releases its document selection listener on removal', async () => {
    const listeners = new Set<EventListenerOrEventListenerObject>();
    const add = document.addEventListener, remove = document.removeEventListener;
    document.addEventListener = function(type, listener, options) {
      if (type === 'selectionchange' && listener) listeners.add(listener);
      return add.call(this, type, listener, options);
    };
    document.removeEventListener = function(type, listener, options) {
      if (type === 'selectionchange' && listener) listeners.delete(listener);
      return remove.call(this, type, listener, options);
    };
    try {
      await reset();
      const table = view.dom.querySelector('.cm-table-widget')!;
      assert(listeners.size > 0, 'table did not register its document selection listener');
      const baseline = listeners.size;
      view.dispatch({ changes: { from: view.state.doc.length, insert: 'appended text' } });
      assert(table === view.dom.querySelector('.cm-table-widget'), 'unchanged table DOM was not reused');
      api.setContentFromAndroid('replacement document', 0, 0);
      assert(listeners.size === baseline - 1, 'reused widget leaked its document listener');
    } finally {
      document.addEventListener = add;
      document.removeEventListener = remove;
    }
  });

  for (const [label, token] of [['Wikilink', 'WikiTarget'], ['Markdown link', 'external']]) {
    await check(`${label}: long press/cancel never navigate; short tap still opens`, async () => {
      await reset();
      const pos = fixture.indexOf(token) + 2;
      const rect = view.coordsAtPos(pos)!;
      const x = rect.left + 1, y = (rect.top + rect.bottom) / 2;
      const target = document.elementFromPoint(x, y) as HTMLElement;
      const opened = (window as unknown as { testLinks: string[] }).testLinks;
      const before = opened.length;
      touch(target, 'touchstart', x, y);
      await wait(600);
      touch(target, 'touchend', x, y);
      assert(opened.length === before, 'long press opened a link');
      touch(target, 'touchstart', x, y);
      touch(target, 'touchcancel', x, y);
      touch(target, 'touchend', x, y);
      assert(opened.length === before, 'cancelled gesture opened a link');
      touch(target, 'touchstart', x, y);
      touch(target, 'touchend', x, y);
      assert(opened.length === before + 1, 'short tap did not open the link exactly once');
    });
  }
}

run().catch((error) => failures.push(String(error))).finally(async () => {
  api.destroy();
  await fetch('/result', { method: 'POST', body: JSON.stringify({ checks, failures }) });
});
