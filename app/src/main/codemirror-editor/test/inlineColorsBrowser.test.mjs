// npm run build first; uses the existing desktop Playwright runtime, never an Android device.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { after, before, test } from 'node:test';
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || 'playwright');
let browser;
before(async () => { browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined }); });
after(async () => { await browser?.close(); });

async function open(body, rows = 1, livePreview = true) {
  const page = await browser.newPage({ viewport: { width: 360, height: 800 }, hasTouch: true });
  page.setDefaultTimeout(4000);
  await page.addInitScript(({ body, rows }) => {
    window.clipboard = '粘贴'; window.errors = []; window.ready = false;
    window.KardLeafAndroid = {
      onContextToolbarChanged(kind, canDeleteRow, canDeleteColumn) { window.contextToolbar = { kind, canDeleteRow, canDeleteColumn }; },
      consumeDocumentPayload(token) { return token === 'test' ? JSON.stringify({ documentToken: 'body', selectionStart: 0, selectionEnd: 0 }) : body; },
      onContentApplied() { queueMicrotask(() => window.KardLeafEditor.prepareInitialRender('1', null)); },
      onInitialRenderReady() { window.ready = true; },
      onEditorError(message) { window.errors.push(message); },
      selectionToolbarRequest(payload) {
        const { id, op, text } = JSON.parse(payload);
        if (op === 'write') window.clipboard = text;
        queueMicrotask(() => window.KardLeafSelection.response(id, op === 'settings' ? { ok: true, rows, commands: ['toggleBold'] }
          : op === 'read' ? { ok: true, text: window.clipboard } : { ok: op !== 'selectAll' }));
      },
    };
  }, { body, rows });
  const url = new URL('../../assets/codemirror-editor/index.html', import.meta.url);
  url.search = `?bootstrap=test&livePreview=${livePreview}`;
  await page.goto(url.href);
  await page.waitForFunction(() => window.ready);
  return page;
}
const text = page => page.evaluate(() => window.KardLeafEditor.getText());

test('bottom context actions: styled caret, table commands, selection and source mode', async () => {
  const body = '<span style="color:#e53935">甲乙</span> 普通\n\n| A | B |\n| --- | --- |\n| C | D |\n\n末尾';
  const page = await open(body);
  const command = (name, ...args) => page.evaluate(([name, ...args]) => window.KardLeafEditor.execCommand(name, ...args), [name, ...args]);
  try {
    await select(page, body.indexOf('甲') + 1, body.indexOf('甲') + 1);
    await page.waitForFunction(() => window.contextToolbar?.kind === 'style');
    for (const property of ['color', 'backgroundColor', 'fontSize']) {
      const before = await text(page);
      await command('cycleInlineStyleAtCursor', property);
      assert.notEqual(await text(page), before);
      assert.equal(await page.evaluate(() => window.getSelection().isCollapsed), true);
      await page.evaluate(() => window.KardLeafEditor.undo());
      assert.equal(await text(page), before);
    }
    await select(page, body.indexOf('甲'), body.indexOf('乙') + 1);
    await page.waitForFunction(() => window.contextToolbar?.kind === '');
    await select(page, body.length, body.length);
    const cell = () => page.locator('.cm-table-widget td').first();
    await cell().tap();
    await page.waitForFunction(() => window.contextToolbar?.kind === 'table');
    assert.equal(await page.locator('.kl-table-toolbar').count(), 0);
    await page.evaluate(() => document.dispatchEvent(new Event('touchmove')));
    await page.waitForFunction(() => window.contextToolbar?.kind === '');
    for (const action of ['addRow', 'addColumn', 'alignment', 'deleteRow', 'deleteColumn']) {
      await cell().tap();
      await page.waitForFunction(() => window.contextToolbar?.kind === 'table');
      const before = await text(page);
      assert.equal(await command('tableToolbarAction', action), 'ok');
      assert.notEqual(await text(page), before);
    }
    await page.locator('.cm-table-widget th').first().tap();
    await page.waitForFunction(() => window.contextToolbar?.kind === 'table' && !window.contextToolbar.canDeleteRow);
    assert.equal(await command('tableToolbarAction', 'deleteRow'), 'disabled');
    assert.equal(await command('tableToolbarAction', 'sourcePreview'), 'ok');
    await page.waitForFunction(() => window.contextToolbar?.kind === '');
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
  const sourcePage = await open(body, 1, false);
  try {
    await select(sourcePage, body.indexOf('甲') + 1, body.indexOf('甲') + 1);
    assert.notEqual(await sourcePage.evaluate(() => window.contextToolbar?.kind), 'style');
  } finally { await sourcePage.close(); }
});
async function select(page, from, to) {
  await page.evaluate(([a, b]) => window.KardLeafEditor.selectRange(a, b), [from, to]);
  await page.waitForTimeout(70);
}
async function click(page, id) {
  await page.locator('.mobile-selection-toolbar').waitFor({ state: 'visible' });
  const button = page.locator(`[data-command-id="${id}"]`);
  for (let i = 0; i < 12 && !(await button.isVisible()) && await page.locator('[data-command-id="back"]').isVisible(); i++) {
    await page.locator('[data-command-id="back"]').click();
    await page.waitForTimeout(35);
  }
  for (let i = 0; i < 12 && !(await button.isVisible()); i++) {
    await page.locator('[data-command-id="next"]').click();
    await page.waitForTimeout(35);
  }
  await button.click();
  await page.waitForTimeout(70);
}

for (const rows of [1, 2]) test(`toolbar ${rows} rows: colors, conceal, clipboard, undo and collapsed caret`, async () => {
  const page = await open('甲乙丙\n末尾', rows);
  try {
    await select(page, 1, 1);
    assert.equal(await page.locator('.mobile-selection-toolbar').isVisible(), false);
    await page.evaluate(() => window.KardLeafSelection.event('context'));
    await page.waitForTimeout(70);
    assert.equal(await page.locator('[data-command-id="color"]').count(), 0);
    await select(page, 2, 0); // Reverse selection remains reversed after the transaction.
    await click(page, 'color');
    assert.equal(await page.locator('[data-command-id="color:#212121"]').count(), 1);
    await click(page, 'color:#e53935');
    const colored = '<span style="color:#e53935">甲乙</span>丙\n末尾';
    assert.equal(await text(page), colored);
    assert.equal(await page.locator('.cm-content').innerText().then(value => value.includes('<span')), false);
    assert.equal(await page.locator('.kl-inline-color').first().evaluate(node => getComputedStyle(node).color), 'rgb(229, 57, 53)');
    assert.equal(await page.locator('.kl-inline-color').first().evaluate(node => node.closest('[contenteditable]')?.classList.contains('cm-content')), true);
    await click(page, 'color:#e53935'); // No duplicate span/undo step.
    assert.equal(await text(page), colored);
    await click(page, 'backgroundColor');
    assert.equal(await page.locator('[data-command-id="color:#ffffff"]').count(), 1);
    await click(page, 'color:#fdd835');
    assert.equal(await text(page), '<span style="color:#e53935;background-color:#fdd835">甲乙</span>丙\n末尾');
    await page.evaluate(() => window.KardLeafEditor.undo());
    assert.equal(await text(page), colored);
    await page.evaluate(() => window.KardLeafEditor.undo());
    assert.equal(await text(page), '甲乙丙\n末尾');
    await page.evaluate(() => window.KardLeafEditor.redo());
    assert.equal(await text(page), colored);
    await click(page, 'copy');
    assert.equal(await page.evaluate(() => window.clipboard.replace(/<[^>]*>/g, '')), '甲乙');
    await select(page, colored.indexOf('甲'), colored.indexOf('乙') + 1);
    await page.evaluate(() => window.KardLeafSelection.event('context'));
    await page.waitForTimeout(70);
    await click(page, 'cut');
    assert.equal((await text(page)).includes('甲乙'), false);
    await page.evaluate(() => window.KardLeafEditor.undo());
    assert.equal(await text(page), colored);
    await select(page, colored.indexOf('甲'), colored.indexOf('乙') + 1);
    await page.evaluate(() => { window.clipboard = '替换'; window.KardLeafSelection.event('context'); });
    await page.waitForTimeout(70); await click(page, 'paste');
    assert.equal(await text(page), colored.replace('甲乙', '替换'));
    await page.evaluate(() => window.KardLeafEditor.undo());
    assert.equal(await text(page), colored);
    await select(page, colored.length, colored.length);
    assert.equal(await page.locator('.mobile-selection-toolbar').isVisible(), false);
    await page.keyboard.insertText('中文'); await page.keyboard.press('Enter'); await page.keyboard.insertText('输入'); await page.keyboard.press('Backspace');
    assert.equal(await text(page), colored + '中文\n输');
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('cross-span multiline selection stays editable and the color page stays compact', async () => {
  const body = '<span style="color:#112233">甲乙</span>\n<span style="background-color:#445566">丙丁</span>';
  const page = await open(body);
  try {
    await select(page, body.indexOf('乙'), body.indexOf('丁'));
    await click(page, 'color'); await click(page, 'color:#1e88e5');
    const result = await text(page);
    assert.equal(result, '<span style="color:#112233">甲</span><span style="color:#1e88e5">乙</span>\n<span style="color:#1e88e5;background-color:#445566">丙</span><span style="background-color:#445566">丁</span>');
    assert.equal(await page.locator('.cm-content').innerText().then(value => value.includes('<span')), false);
    assert.equal(await page.locator('[data-command-id="color:clear"]').count(), 0);
    assert.equal(await page.locator('[data-command-id="colorsBack"]').count(), 0);
    assert.equal(await page.locator('[data-command-id="customColor"]').count(), 1);
    await select(page, result.indexOf('乙'), result.indexOf('乙'));
    await page.keyboard.insertText('可编辑');
    assert.equal(await text(page), result.replace('乙', '可编辑乙'));
  } finally { await page.close(); }
});

test('code and math reject color operations; source mode still supports colors', async () => {
  for (const body of ['```\n代码\n```', '$$\nx+y\n$$', '\\[\nx+y\n\\]']) {
    const page = await open(body);
    try {
      await select(page, body.indexOf('\n') + 1, body.lastIndexOf('\n'));
      await click(page, 'color'); await click(page, 'color:#e53935');
      assert.equal(await text(page), body);
    } finally { await page.close(); }
  }
  const page = await open('正文', 1, false);
  try {
    await select(page, 0, 2); await click(page, 'color'); await click(page, 'color:#e53935');
    assert.equal(await text(page), '<span style="color:#e53935">正文</span>');
    assert.ok((await page.locator('.cm-content').innerText()).includes('<span'));
  } finally { await page.close(); }
});

test('text beside inline code remains formatable', async () => {
  const page = await open('正文 `root`');
  try {
    await select(page, 0, 2); await click(page, 'color'); await click(page, 'color:#e53935');
    assert.equal(await text(page), '<span style="color:#e53935">正文</span> `root`');
  } finally { await page.close(); }
});

test('standalone multiline color span keeps real editable text', async () => {
  const body = '<span style="color:#112233">\n甲乙\n</span>';
  const page = await open(body);
  try {
    assert.equal(await page.locator('.cm-md-html-block, .cm-md-html-inline').count(), 0);
    assert.equal((await page.locator('.cm-content').innerText()).includes('<span'), false);
    await select(page, body.indexOf('甲'), body.indexOf('乙') + 1);
    await click(page, 'backgroundColor'); await click(page, 'color:#fdd835');
    assert.ok((await text(page)).includes('color:#112233;background-color:#fdd835'));
  } finally { await page.close(); }
});

test('native selection keeps colored text nodes and the existing palette edits its final range', async () => {
  const body = '<span style="color:#112233">甲乙丙丁</span>尾';
  const page = await open(body);
  try {
    await page.evaluate(() => {
      window.KardLeafEditor.focus();
      window.selectedColorNode = document.querySelector('.kl-inline-color').firstChild;
      window.getSelection().setBaseAndExtent(window.selectedColorNode, 1, window.selectedColorNode, 2);
    });
    await page.waitForTimeout(100);
    assert.equal(await page.evaluate(() => window.getSelection().toString()), '乙');
    await page.evaluate(() => window.getSelection().setBaseAndExtent(window.selectedColorNode, 1, window.selectedColorNode, 3));
    await page.waitForTimeout(150);
    assert.equal(await page.evaluate(() => window.selectedColorNode.isConnected), true);
    assert.equal(await page.evaluate(() => window.getSelection().toString()), '乙丙');
    assert.equal(await text(page), body);
    await page.evaluate(() => window.KardLeafSelection.event('context'));
    await click(page, 'backgroundColor'); await click(page, 'color:#fdd835');
    assert.equal(await text(page), '<span style="color:#112233">甲</span><span style="color:#112233;background-color:#fdd835">乙丙</span><span style="color:#112233">丁</span>尾');
    await page.evaluate(() => window.KardLeafEditor.undo());
    assert.equal(await text(page), body);
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('font size palette writes and renders the requested em size', async () => {
  const page = await open('大字小字');
  try {
    await select(page, 0, 2);
    await click(page, 'fontSize');
    assert.equal(await page.locator('[data-command-id="fontSize:default"]').count(), 1);
    await click(page, 'fontSize:1.5em');
    assert.equal(await text(page), '<span style="font-size:1.5em">大字</span>小字');
    assert.equal(await page.locator('.kl-inline-color').first().evaluate(node => getComputedStyle(node).fontSize), '30px');
    await click(page, 'fontSize'); await click(page, 'fontSize:0.8em');
    assert.equal(await text(page), '<span style="font-size:0.8em">大字</span>小字');
    assert.equal(await page.locator('[data-command-id="fontSize:clear"]').count(), 0);
  } finally { await page.close(); }
});

test('custom color picker applies the selected color and the format back arrow returns to the previous page', async () => {
  const page = await open('甲乙');
  try {
    await select(page, 0, 2);
    assert.equal(await page.locator('[data-command-id="color"] .selection-toolbar-icon').evaluate(icon => getComputedStyle(icon).width), '28px');
    assert.equal(await page.locator('[data-command-id="copy"] .selection-toolbar-icon').evaluate(icon => getComputedStyle(icon).width), '20px');
    assert.equal(await page.locator('[data-command-id="next"] svg').getAttribute('viewBox'), '30 32 66 64');
    await click(page, 'color');
    await page.waitForTimeout(70);
    assert.equal(await page.locator('[data-command-id="back"] svg').getAttribute('viewBox'), '30 32 66 64');
    const picker = page.locator('[data-command-id="customColor"] input[type="color"]');
    assert.equal(await picker.count(), 1);
    await picker.evaluate(input => {
      input.value = '#123456';
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.waitForTimeout(70);
    assert.equal(await text(page), '<span style="color:#123456">甲乙</span>');
    await page.locator('[data-command-id="back"]').click();
    await page.waitForTimeout(70);
    assert.equal(await page.locator('[data-command-id="color"]').isVisible(), true);
    assert.equal(await page.locator('[data-command-id="customColor"]').count(), 0);
  } finally { await page.close(); }
});

test('collapsed caret and document replacement invalidate the old palette', async () => {
  const page = await open('甲乙');
  try {
    await select(page, 0, 2); await click(page, 'color');
    await page.evaluate(() => { window.oldColorButton = document.querySelector('[data-command-id="color:#e53935"]'); });
    await select(page, 1, 1);
    await page.evaluate(() => window.oldColorButton.click());
    await page.waitForTimeout(70);
    assert.equal(await text(page), '甲乙');
    assert.equal(await page.locator('.mobile-selection-toolbar').isVisible(), false);
    await select(page, 0, 2); await click(page, 'color');
    await page.evaluate(() => {
      window.oldColorButton = document.querySelector('[data-command-id="color:#e53935"]');
      window.KardLeafEditor.replaceRangeFromAndroid(0, 2, '新文', 2, 2);
      window.oldColorButton.click();
    });
    assert.equal(await text(page), '新文');
    await page.waitForTimeout(70);
    assert.equal(await page.locator('.mobile-selection-toolbar').isVisible(), false);
  } finally { await page.close(); }
});

test('browser table tap retains its cell caret through typing, blur and undo', async () => {
  const body = 'Before\n\n| A | B |\n| --- | --- |\n| cell | other |\n\nAfter';
  const page = await open(body);
  try {
    for (const selector of ['th', 'td']) {
      const cell = page.locator(`.cm-table-widget ${selector}[contenteditable]`).first();
      await cell.tap();
      await page.waitForTimeout(180);
      assert.equal(await cell.evaluate(el => document.activeElement === el && el.contains(window.getSelection().focusNode)), true);
      assert.equal(await page.evaluate(() => window.KardLeafEditor.prepareImeReveal(300)), 'contenteditable');
      await page.keyboard.insertText('X');
      await page.locator('.cm-line').last().tap();
      await page.waitForTimeout(180);
      assert.match(await text(page), /X/);
      await page.evaluate(() => window.KardLeafEditor.undo());
      assert.equal(await text(page), body);
    }
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('Enter, typing, then tapping empty cells in the new row keeps the tapped cell focused', async () => {
  const page = await open('| A | B | C |\n| --- | --- | --- |\n| one | two | three |\n\nAfter');
  try {
    await page.locator('.cm-table-widget tbody td').first().tap();
    await page.keyboard.press('Enter');
    await page.waitForTimeout(100);
    assert.equal(await page.locator('.cm-table-widget tbody tr').count(), 2);
    for (let column = 1; column < 3; column++) {
      await page.keyboard.insertText(`typed${column}`);
      const target = page.locator('.cm-table-widget tbody tr').nth(1).locator('td').nth(column);
      await target.tap();
      await page.waitForTimeout(150);
      assert.equal(await target.evaluate(el => document.activeElement === el), true, `new row column ${column} lost focus`);
      assert.match(await text(page), new RegExp(`typed${column}`));
    }
    await page.keyboard.insertText('last');
    await page.locator('.cm-line').last().tap();
    await page.waitForTimeout(150);
    assert.match(await text(page), /typed1 \| typed2 \| last/);
    assert.deepEqual(await page.evaluate(() => window.errors), []);
  } finally { await page.close(); }
});

test('browser body tap collapses selection without the toolbar rewriting the caret', async () => {
  const page = await open('alpha beta\n\n\n\ngamma delta');
  try {
    await select(page, 0, 5);
    const point = await page.locator('.cm-line').last().evaluate(line => {
      const range = document.createRange();
      range.setStart(line.firstChild, 3); range.setEnd(line.firstChild, 4);
      const rect = range.getBoundingClientRect();
      return { x: rect.left + 1, y: (rect.top + rect.bottom) / 2 };
    });
    await page.touchscreen.tap(point.x, point.y);
    await page.waitForTimeout(150);
    const selected = await page.evaluate(() => ({ collapsed: window.getSelection().isCollapsed,
      line: window.getSelection().focusNode?.textContent }));
    assert.equal(selected.collapsed, true);
    assert.equal(selected.line, 'gamma delta');
    assert.equal(await page.locator('.mobile-selection-toolbar').isVisible(), false);
    await page.keyboard.insertText('X');
    assert.match(await text(page), /^alpha beta\n\n\n\ngamXma delta$/);
    await page.evaluate(() => window.KardLeafEditor.undo());
    assert.equal(await text(page), 'alpha beta\n\n\n\ngamma delta');
  } finally { await page.close(); }
});
