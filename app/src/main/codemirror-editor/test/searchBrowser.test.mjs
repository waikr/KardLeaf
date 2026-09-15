// Real desktop Chromium only; no Android device or emulator. Build with npm run build first.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { before, after, test } from 'node:test';

const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || 'playwright');
let browser;
before(async () => { browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined }); });
after(async () => { await browser?.close(); });

async function open(text) {
  const page = await browser.newPage({ viewport: { width: 412, height: 800 } });
  await page.addInitScript(text => {
    window.searchStates = [];
    window.errors = [];
    window.selection = [0, 0];
    window.KardLeafAndroid = {
      consumeDocumentPayload(token) { return token === 'test' ? JSON.stringify({ documentToken: 'body' }) : text; },
      onContentApplied() { queueMicrotask(() => window.KardLeafEditor.prepareInitialRender('1', null)); },
      onInitialRenderReady() { window.ready = true; },
      onSearchStateChanged(state) { window.searchStates.push(JSON.parse(state)); },
      onSelectionChanged(from, to) { window.selection = [from, to]; },
      onEditorError(message) { window.errors.push(message); },
    };
  }, text);
  const url = new URL('../../assets/codemirror-editor/index.html', import.meta.url);
  url.search = '?bootstrap=test&livePreview=true';
  await page.goto(url.href, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => window.ready === true);
  return page;
}

const command = (page, ...args) => page.evaluate(args => window.KardLeafEditor.execCommand(...args), args);
const summary = page => page.evaluate(() => window.searchStates.at(-1));
const text = page => page.evaluate(() => window.KardLeafEditor.getText());
const undo = page => page.evaluate(() => window.KardLeafEditor.undo());

test('real document owns count, current hit, wrapping navigation and replacement undo', async () => {
  const page = await open('😀 foo\nfoo foo');
  try {
    await command(page, 'setSearchState', 'foo', false, true, -1, 0, 7, true);
    assert.equal((await summary(page)).currentStart, 7);
    assert.equal((await summary(page)).count, 3);
    await command(page, 'navigateSearch', -1);
    assert.equal((await summary(page)).currentStart, 3);
    assert.equal(await page.locator('.cm-searchMatch-selected').count(), 1);
    await command(page, 'navigateSearch', -1);
    assert.equal((await summary(page)).currentStart, 11);
    await command(page, 'navigateSearch', 1);
    assert.equal((await summary(page)).currentStart, 3);
    await command(page, 'replaceSearch', false, 'bar');
    assert.equal(await text(page), '😀 bar\nfoo foo');
    assert.equal((await summary(page)).count, 2);
    await command(page, 'replaceSearch', true, 'X');
    assert.equal(await text(page), '😀 bar\nX X');
    assert.equal((await summary(page)).count, 0);
    await undo(page);
    assert.equal(await text(page), '😀 bar\nfoo foo');
    assert.equal((await summary(page)).count, 2);
    await undo(page);
    assert.equal(await text(page), '😀 foo\nfoo foo');
    assert.equal((await summary(page)).count, 3);
    await page.evaluate(() => window.KardLeafEditor.redo());
    assert.equal((await summary(page)).count, 2);
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('query changes, invalid expressions, literal escapes and multiline replacement stay synchronized', async () => {
  const page = await open('foo\r\nbar\nfoo\\nbar');
  try {
    await command(page, 'setSearchState', 'foo\nbar', false, false, -1, 0, 0, true);
    assert.equal((await summary(page)).count, 1);
    await command(page, 'replaceSearch', true, 'X');
    assert.equal(await text(page), 'X\nfoo\\nbar');
    await undo(page);
    await command(page, 'setSearchState', '\\n', false, false);
    assert.equal((await summary(page)).count, 1);
    await command(page, 'setSearchState', '^foo$', true, true);
    assert.equal((await summary(page)).count, 1);
    await command(page, 'setSearchState', '[', true, true);
    assert.ok((await summary(page)).error);
    assert.equal(await page.locator('.cm-searchMatch').count(), 0);
    await command(page, 'setSearchState', 'bar', false, true);
    await page.evaluate(() => window.KardLeafEditor.replaceRangeFromAndroid(4, 7, 'xxx', 7, 7));
    assert.equal((await summary(page)).count, 1);
    await undo(page);
    assert.equal((await summary(page)).count, 2);
    await command(page, 'clearSearchState');
    assert.equal((await summary(page)).query, '');
    assert.equal(await page.locator('.cm-searchMatch').count(), 0);
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('search highlights preserve line breaks and allow a caret inside the hit', async () => {
  const body = 'abcdefghijklmnopqrstuvwxyz '.repeat(16);
  const page = await open(body);
  try {
    const geometry = () => page.locator('.cm-line').first().evaluate(line => {
      const walker = document.createTreeWalker(line, NodeFilter.SHOW_TEXT);
      const rows = [];
      while (walker.nextNode()) {
        const node = walker.currentNode;
        for (let i = 0; i < node.textContent.length; i++) {
          const range = document.createRange();
          range.setStart(node, i); range.setEnd(node, i + 1);
          const rect = range.getBoundingClientRect();
          rows.push(Math.round(rect.top - line.getBoundingClientRect().top));
        }
      }
      return rows;
    });
    const before = await geometry();
    await command(page, 'setSearchState', 'defghijklmnopqrstuv', false, true);
    assert.deepEqual(await geometry(), before);
    const hit = page.locator('.cm-searchMatch').first();
    const point = await hit.evaluate(el => {
      const range = document.createRange();
      range.setStart(el.firstChild, 7); range.setEnd(el.firstChild, 7);
      const rect = range.getBoundingClientRect();
      return { x: rect.x, y: rect.y + rect.height / 2 };
    });
    await page.mouse.click(point.x, point.y);
    await page.waitForTimeout(120); // Longer than the old delayed re-selection.
    const selection = await page.evaluate(() => window.selection);
    assert.equal(selection[0], selection[1]);
    assert.ok(selection[0] > 3 && selection[0] < 22, JSON.stringify(selection));
    assert.equal((await summary(page)).currentStart, 3);
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('regex replacement uses official references and is one undo transaction', async () => {
  const page = await open('foo foo');
  try {
    await command(page, 'setSearchState', '(foo)', true, true);
    await command(page, 'replaceSearch', true, '$& $$ $1 $12 $0 $99 \\n');
    assert.equal(await text(page), 'foo $ foo foo2 $0 $99 \\n foo $ foo foo2 $0 $99 \\n');
    await undo(page);
    assert.equal(await text(page), 'foo foo');
    await command(page, 'setSearchState', '(x)?(foo)', true, true);
    await command(page, 'replaceSearch', true, '$1$2');
    assert.equal(await text(page), 'foo foo');
    await command(page, 'setSearchState', '(?=foo)', true, true);
    await command(page, 'replaceSearch', true, 'x');
    assert.equal(await text(page), 'foo foo');
  } finally { await page.close(); }
});

test('a replacement carries the latest query even before a queued search refresh', async () => {
  const page = await open('foo bar');
  try {
    await command(page, 'setSearchState', 'foo', false, false, -1, 0, 0, true);
    await command(page, 'replaceSearch', true, 'X', 'bar', false, true);
    assert.equal(await text(page), 'foo X');
    await undo(page);
    await command(page, 'replaceSearch', true, 'X', '[', true, true);
    assert.equal(await text(page), 'foo bar');
  } finally { await page.close(); }
});

test('live-preview headings, emphasis, tables and code reveal the selected source match', async () => {
  const body = '# hit\n\n**hit**\n\n| A |\n| --- |\n| hit |\n\n```text\nhit\n```\n';
  const page = await open(body);
  try {
    await command(page, 'setSearchState', 'hit', false, true, -1, 0, 0, true);
    const offsets = [...body.matchAll(/hit/g)].map(m => m.index);
    for (const offset of offsets) {
      assert.equal((await summary(page)).count, 4);
      assert.equal((await summary(page)).currentStart, offset);
      assert.ok((await page.locator('.cm-searchMatch-selected').allTextContents()).some(text => text.includes('hit')),
        JSON.stringify({ offset, html: await page.locator('.cm-content').innerHTML() }));
      await command(page, 'navigateSearch', 1);
    }
    await command(page, 'navigateSearch', 1);
    await command(page, 'navigateSearch', 1);
    assert.equal(await page.locator('.cm-table-widget').count(), 0);
    await command(page, 'clearSearchState');
    assert.equal(await page.locator('.cm-table-widget').count(), 1);
    assert.equal(await text(page), body);
    assert.equal(await undo(page), 'empty');
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('a match beyond the viewport is counted and revealed without changing the document', async () => {
  const body = '普通正文\r\n'.repeat(6000) + '😀 TARGET\r\n尾部';
  const page = await open(body);
  try {
    const normalized = body.replaceAll('\r\n', '\n');
    await command(page, 'setSearchState', 'TARGET', false, true, -1, 0, normalized.indexOf('TARGET'), true);
    assert.equal((await summary(page)).count, 1);
    assert.equal((await summary(page)).currentStart, normalized.indexOf('TARGET'));
    await page.waitForFunction(() => window.KardLeafEditor.getScrollMetrics().scrollTop > 1000);
    assert.equal(await text(page), normalized);
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});
