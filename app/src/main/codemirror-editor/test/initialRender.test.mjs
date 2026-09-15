// Build first with npm run build. Uses an existing Playwright installation;
// PLAYWRIGHT_MODULE / CHROMIUM_PATH may point to a shared local runtime.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { after, before, test } from 'node:test';

const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || 'playwright');
let browser;
before(async () => {
  browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
});
after(async () => { await browser?.close(); });

async function openEditor(body, { anchor = null, selection = 0, livePreview = true, slowImage = false, title = '' } = {}) {
  const page = await browser.newPage({ viewport: { width: 412, height: 800 }, hasTouch: true });
  page.renderLogs = [];
  page.on('console', message => { if (message.text().includes('initial render ready')) page.renderLogs.push(message.text()); });
  if (slowImage) await page.route('https://slow.invalid/**', () => {});
  await page.addInitScript(({ body, anchor, selection, title }) => {
    window.renderResults = [];
    window.patches = [];
    window.editorErrors = [];
    window.KardLeafAndroid = {
      consumeDocumentPayload(token) {
        if (token === 'test') return JSON.stringify({ documentToken: 'body', selectionStart: selection, selectionEnd: selection, title, titleVisible: !!title });
        return body;
      },
      onContentApplied() {
        queueMicrotask(() => window.KardLeafEditor.prepareInitialRender('1', anchor));
      },
      onInitialRenderReady(page, content, request, result) {
        window.renderResults.push({ page, content, request, result, elapsed: performance.now() });
      },
      onContentPatches(...args) { window.patches.push(args); },
      onEditorError(message) { window.editorErrors.push(message); },
    };
  }, { body, anchor, selection, title });
  const url = new URL('../../assets/codemirror-editor/index.html', import.meta.url);
  url.search = `?bootstrap=test&livePreview=${livePreview}`;
  await page.goto(url.href, { waitUntil: 'domcontentloaded' });
  return page;
}

async function ready(page) {
  await page.waitForFunction(() => window.renderResults.length > 0, null, { timeout: 5000 });
  const [result] = await page.evaluate(() => window.renderResults);
  assert.match(result.result, /^ok/);
  assert.deepEqual(await page.evaluate(() => window.editorErrors), []);
  assert.deepEqual(await page.evaluate(() => window.patches), []);
  return result;
}

test('first body touch establishes DOM focus before native long-press selection', async () => {
  for (const livePreview of [true, false]) {
    const body = '首次长按选择正文\n\n第二行';
    const page = await openEditor(body, { livePreview });
    try {
      await ready(page);
      assert.equal(await page.locator('.cm-content').evaluate(node => document.activeElement === node), false);
      // Controls and nested editors must retain their own native touch handling.
      const ignored = await page.locator('.cm-line').first().evaluate(line => {
        return ['button', 'a', 'input', 'textarea', 'select', 'div'].map(tag => {
          const target = document.createElement(tag);
          if (tag === 'div') target.contentEditable = 'true';
          line.append(target);
          const touch = new Touch({ identifier: 1, target });
          target.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, touches: [touch] }));
          const focused = document.activeElement === line.closest('.cm-content');
          target.dispatchEvent(new TouchEvent('touchcancel', { bubbles: true }));
          target.remove();
          return focused;
        });
      });
      assert.deepEqual(ignored, [false, false, false, false, false, false]);
      const result = await page.locator('.cm-line').first().evaluate(line => {
        const rect = line.getBoundingClientRect();
        const touch = new Touch({ identifier: 1, target: line, clientX: rect.left + 12, clientY: rect.top + 8 });
        const event = new TouchEvent('touchstart', { bubbles: true, cancelable: true, touches: [touch], targetTouches: [touch], changedTouches: [touch] });
        line.dispatchEvent(event);
        const content = line.closest('.cm-content');
        const selection = document.getSelection();
        const result = { focused: document.activeElement === content, nativeCaret: content.contains(selection.anchorNode), prevented: event.defaultPrevented };
        line.dispatchEvent(new TouchEvent('touchcancel', { bubbles: true, changedTouches: [touch] }));
        return result;
      });
      assert.deepEqual(result, { focused: true, nativeCaret: true, prevented: false });
      await page.evaluate(() => window.KardLeafEditor.selectRange(1, 4));
      const selected = await page.locator('.cm-line').first().evaluate(line => {
        const before = document.getSelection().toString();
        const touch = new Touch({ identifier: 1, target: line });
        line.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, touches: [touch] }));
        const after = document.getSelection().toString();
        line.dispatchEvent(new TouchEvent('touchcancel', { bubbles: true }));
        return { before, after };
      });
      assert.deepEqual(selected, { before: body.slice(1, 4), after: body.slice(1, 4) });
      assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), body);
      assert.equal(await page.evaluate(() => window.KardLeafEditor.undo()), 'empty');
      assert.deepEqual(await page.evaluate(() => window.patches), []);
    } finally { await page.close(); }
  }
});

test('direct open hides initial heading markers and preserves CRLF/emoji text and history', async () => {
  const body = '# 标题\r\n\r\n正文 😀 **强调**\r\n';
  const page = await openEditor(body);
  try {
    await ready(page);
    assert.equal(await page.locator('.cm-content').innerText().then(text => text.includes('# 标题')), false);
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), body.replaceAll('\r\n', '\n'));
    assert.equal(await page.evaluate(() => window.KardLeafEditor.undo()), 'empty');
    await page.evaluate(() => window.KardLeafEditor.replaceRangeFromAndroid(0, 0, '输入', 2, 2));
    assert.equal(await page.evaluate(() => window.KardLeafEditor.undo()), 'ok');
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), body.replaceAll('\r\n', '\n'));
    assert.equal(await page.evaluate(() => window.KardLeafEditor.redo()), 'ok');
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), '输入' + body.replaceAll('\r\n', '\n'));
    await page.evaluate(() => { window.KardLeafEditor.setTypography(20, {}); window.KardLeafEditor.setTypography(20, {}); });
    assert.equal(await page.locator('.cm-content').evaluate(node => getComputedStyle(node).fontSize), '20px');
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), '输入' + body.replaceAll('\r\n', '\n'));
  } finally { await page.close(); }
});

test('opening heading stays concealed through title focus and a body scroll gesture', async () => {
  const page = await openEditor('# First heading\n\nOther paragraph\n\n' + 'More text\n\n'.repeat(80), { title: 'Note title' });
  try {
    await ready(page);
    await page.locator('.kl-editor-title-input').focus();
    assert.equal(await page.locator('.cm-content').innerText().then(s => s.includes('# First heading')), false);
    await page.locator('.cm-line').nth(2).evaluate(line => {
      const rect = line.getBoundingClientRect();
      const send = (type, y) => {
        const point = new Touch({ identifier: 1, target: line, clientX: rect.left + 30, clientY: y });
        line.dispatchEvent(new TouchEvent(type, { bubbles: true, touches: type === 'touchend' ? [] : [point], changedTouches: [point] }));
      };
      send('touchstart', rect.top + 10);
      send('touchmove', rect.top - 30);
      send('touchend', rect.top - 30);
    });
    await page.waitForTimeout(150);
    assert.equal(await page.locator('.cm-content').innerText().then(s => s.includes('# First heading')), false);
    await page.locator('.cm-line').nth(2).tap();
    await page.waitForTimeout(150);
    assert.equal(await page.locator('.cm-content').innerText().then(s => s.includes('# First heading')), false);
    await page.locator('.cm-headerLine').tap();
    await page.waitForTimeout(150);
    assert.equal(await page.locator('.cm-content').innerText().then(s => s.includes('# First heading')), true);
    assert.deepEqual(await page.evaluate(() => window.patches), []);
  } finally { await page.close(); }
});

test('IME reveal does not scroll an old caret while a long press awaits native selection', async () => {
  const body = 'Old caret\n\n' + 'Paragraph text\n\n'.repeat(120);
  const page = await openEditor(body);
  try {
    await ready(page);
    await page.evaluate(() => window.KardLeafEditor.selectRange(0, 0));
    await page.locator('.cm-scroller').evaluate(el => { el.scrollTop = 400; });
    await page.waitForTimeout(100);
    await page.locator('.cm-content').evaluate(content => {
      const line = [...content.querySelectorAll('.cm-line')].find(el => el.getBoundingClientRect().top > 80);
      const rect = line.getBoundingClientRect();
      const point = new Touch({ identifier: 1, target: line, clientX: rect.left + 20, clientY: rect.top + 8 });
      line.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, touches: [point], changedTouches: [point] }));
      window.endPendingPress = () => line.dispatchEvent(new TouchEvent('touchend', { bubbles: true, touches: [], changedTouches: [point] }));
    });
    await page.waitForTimeout(510);
    await page.evaluate(() => window.endPendingPress());
    const before = await page.locator('.cm-scroller').evaluate(el => el.scrollTop);
    assert.equal(await page.evaluate(() => window.KardLeafEditor.prepareImeReveal(300)), 'pointer-pending');
    await page.waitForTimeout(80);
    assert.equal(await page.locator('.cm-scroller').evaluate(el => el.scrollTop), before);
    await page.evaluate(() => window.KardLeafEditor.selectRange(2, 2));
    assert.equal(await page.evaluate(() => window.KardLeafEditor.prepareImeReveal(300)), 'codemirror');
  } finally { await page.close(); }
});

test('the note title scrolls with the body after initial rendering and title updates', async () => {
  const page = await openEditor('正文\n\n'.repeat(500), { title: '切换标题' });
  try {
    await ready(page);
    assert.equal(await page.locator('.kl-editor-title-input').inputValue(), '切换标题');
    const top = await page.locator('.kl-editor-title-header').evaluate(node => node.getBoundingClientRect().top);
    await page.mouse.move(200, 650);
    await page.mouse.wheel(0, 300);
    await page.waitForFunction(() => window.KardLeafEditor.getScrollMetrics().scrollTop >= 300);
    await page.evaluate(() => window.KardLeafEditor.setTitleState('修改标题', '', true, 22));
    const position = await page.evaluate(() => ({
      top: document.querySelector('.kl-editor-title-header').getBoundingClientRect().top,
      scroll: window.KardLeafEditor.getScrollMetrics().scrollTop,
    }));
    assert.ok(Math.abs(top - position.top - position.scroll) <= 1, JSON.stringify(position));
    assert.equal(await page.locator('.kl-editor-title-input').inputValue(), '修改标题');
  } finally { await page.close(); }
});

test('live preview inherits the ordinary preview font, size, spacing and English glyph widths', async () => {
  const body = 'Markdown AV fi 中文';
  const page = await openEditor(body, { title: 'Markdown 标题' });
  const preview = await browser.newPage({ viewport: { width: 412, height: 800 } });
  // Read the real host CSS template so this comparison also follows preview-side changes.
  const host = readFileSync(new URL('../../java/com/kangle/kardleaf/ui/editor/host/EditorPreviewWebView.kt', import.meta.url), 'utf8');
  const template = host.slice(host.indexOf('private fun previewTypographyCss(')).match(/return """([\s\S]*?)"""\.trimIndent/)[1];
  const measure = node => {
    const style = getComputedStyle(node);
    const range = document.createRange();
    range.selectNodeContents(node);
    return {
      fontFamily: style.fontFamily, fontSize: style.fontSize,
      lineHeight: style.lineHeight, letterSpacing: style.letterSpacing,
      width: range.getBoundingClientRect().width,
    };
  };
  try {
    await ready(page);
    await preview.goto(new URL('../../assets/preview/preview.html', import.meta.url).href);
    await preview.evaluate(body => window.updateContent(body, false, 'test'), body);
    for (const [fontFamily, fontSize, lineHeight, letterSpacing] of [
      ['system', 16, 1.55, 0], ['system', 20, 1.9, 1.2], ['serif', 18, 1.7, -0.3],
    ]) {
      const values = { safeFontFamily: fontFamily === 'system' ? 'sans-serif' : `"${fontFamily}"`,
        safeFontSize: fontSize, safeLineHeight: lineHeight, safeLetterSpacing: letterSpacing,
        safeParagraphSpacing: 8, '(safeParagraphSpacing / 2f)': 4 };
      const css = template.replace(/\$\{([^}]+)\}|\$(\w+)/g, (_, expression, name) => {
        assert.ok((expression || name) in values);
        return values[expression || name];
      });
      await preview.evaluate(css => {
        let style = document.getElementById('kl-preview-typography');
        if (!style) { style = document.createElement('style'); style.id = 'kl-preview-typography'; document.head.append(style); }
        style.textContent = css;
      }, css);
      await page.evaluate(({ fontFamily, fontSize, lineHeight, letterSpacing }) => {
        window.KardLeafEditor.setTypography(fontSize, { fontFamily, lineHeight, letterSpacing });
      }, { fontFamily, fontSize, lineHeight, letterSpacing });
      assert.deepEqual(await page.locator('.cm-line').first().evaluate(measure), await preview.locator('#content p').first().evaluate(measure));
    }
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), body);
    assert.equal(await page.evaluate(() => window.KardLeafEditor.undo()), 'empty');
  } finally { await page.close(); await preview.close(); }
});

test('the first live title keeps its position and metrics through host replay and the following 1.5 seconds', async () => {
  const title = 'Markdown 中英混排标题 '.repeat(8);
  const page = await openEditor('# 正文标题\n\n正文 **强调**\n\n'.repeat(500), { title });
  try {
    await ready(page);
    const samples = await page.evaluate(async title => {
      const input = document.querySelector('.kl-editor-title-input');
      const sample = () => {
        const rect = input.getBoundingClientRect();
        const style = getComputedStyle(input);
        return [rect.x, rect.y, rect.width, rect.height, style.fontFamily, style.fontSize,
          style.lineHeight, style.letterSpacing, window.KardLeafEditor.getScrollMetrics().scrollTop];
      };
      const samples = [sample()];
      window.KardLeafEditor.traceInitialSurface();
      window.KardLeafEditor.setTitleState(title, '', true, 22);
      window.KardLeafEditor.setTypography(16, { fontFamily: 'system', lineHeight: 1.55, letterSpacing: 0 });
      window.KardLeafEditor.setReadOnly(false);
      window.KardLeafEditor.setDarkMode(false);
      const start = performance.now();
      while (performance.now() - start < 1550) {
        await new Promise(requestAnimationFrame);
        samples.push(sample());
      }
      return samples;
    }, title);
    assert.ok(samples.length > 10);
    for (const sample of samples) assert.deepEqual(sample, samples[0]);
    assert.equal(await page.locator('.kl-editor-title-input').inputValue(), title);
    assert.equal(await page.evaluate(() => window.KardLeafEditor.undo()), 'empty');
    assert.deepEqual(await page.evaluate(() => window.patches), []);
  } finally { await page.close(); }
});

test('a stalled image does not block the first preview or scrolling', async () => {
  const body = '# 标题\n\n![图片](https://slow.invalid/image.png)\n\n' + '正文 **强调**\n\n'.repeat(300);
  const page = await openEditor(body, { slowImage: true });
  try {
    await ready(page);
    await page.mouse.move(200, 650);
    await page.mouse.wheel(0, 600);
    await page.waitForFunction(() => window.KardLeafEditor.getScrollMetrics().scrollTop > 0);
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), body);
  } finally { await page.close(); }
});

test('long note opens at start without parsing the entire document and can reach rendered end', async () => {
  const body = '# 标题\n\n' + '正文 **强调** 内容\n\n'.repeat(15000) + '$$\nx^2\n$$\n\n| 左 | 右 |\n| --- | --- |\n| 一 | 二 |\n';
  const page = await openEditor(body);
  try {
    const result = await ready(page);
    assert.ok(Number(/parsedTo=(\d+)/.exec(page.renderLogs[0])?.[1]) < body.length, page.renderLogs[0]);
    console.log(`long note first viewport: ${Math.round(result.elapsed)} ms (${body.length} chars)`);
    await page.evaluate(length => window.KardLeafEditor.prepareInitialRender('2', { offset: length, edge: 'END', viewportFraction: 1 }), body.length);
    await page.waitForFunction(() => window.renderResults.some(result => result.request === '2'), null, { timeout: 15000 });
    assert.equal(await page.locator('.cm-table-widget table').count(), 1);
    await page.waitForSelector('.cm-math-block .katex');
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), body);
  } finally { await page.close(); }
});

test('empty note and plain source mode both become ready', async () => {
  for (const [body, livePreview] of [['', true], ['# 源码\n\n**保留标记**', false]]) {
    const page = await openEditor(body, { livePreview });
    try {
      await ready(page);
      assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), body);
    } finally { await page.close(); }
  }
});

test('same-length replacement invalidates an in-flight render; search wins over the initial anchor', async () => {
  const body = '# 旧标题\n\n' + '正文 **强调**\n\n'.repeat(300);
  const next = body.replace('旧标题', '新标题');
  const page = await openEditor(body);
  try {
    await ready(page);
    await page.evaluate(next => {
      window.renderResults = [];
      window.KardLeafAndroid.onContentApplied = () => {};
      window.KardLeafEditor.prepareInitialRender('old', null);
      window.KardLeafEditor.setContentFromAndroid(next, 0, 0, 'next-body');
      window.KardLeafEditor.prepareInitialRender('new', { offset: 0, edge: 'START' });
      window.KardLeafEditor.execCommand('setSearchState', '正文', false, false, 299, 300);
      window.KardLeafEditor.selectRange(next.lastIndexOf('正文'), next.lastIndexOf('正文') + 2);
    }, next);
    await ready(page);
    const results = await page.evaluate(() => window.renderResults);
    assert.equal(results.length, 1);
    assert.equal(results[0].request, 'new');
    assert.equal(results[0].content, 'next-body');
    assert.ok(await page.evaluate(() => window.KardLeafEditor.getScrollMetrics().scrollTop > 0));
    assert.equal(await page.evaluate(() => window.KardLeafEditor.getText()), next);
  } finally { await page.close(); }
});

test('initial selection inside a block keeps its source hidden until explicit editing', async () => {
  for (const [body, selector, sourceMarker] of [
    ['$$\nx^2\n$$\n', '.cm-math-block'],
    ['```mermaid\ngraph LR; A-->B\n```\n', '.cm-mermaid-block'],
    ['<div>预览</div>\n', '.cm-md-html-block'],
    ['> [!note] 提示\n> 正文\n', '.cm-admonition', '!note'],
    ['```text\n代码\n```\n', '.cm-codeblock-header'],
    ['![图片](https://slow.invalid/image.png)\n', 'img'],
  ]) {
    const page = await openEditor(body, { slowImage: true });
    try {
      await ready(page);
      assert.ok(await page.locator(selector).count(), selector);
      const sourceLine = sourceMarker || body.trim().split('\n')[0];
      assert.equal(await page.locator('.cm-content').innerText().then(text => text.includes(sourceLine)), false, body);
      await page.evaluate(() => window.KardLeafEditor.selectRange(0, 0));
      const editingText = await page.locator('.cm-content').innerText();
      assert.equal(editingText.includes(sourceLine), true, `${body}\nRendered while editing: ${editingText}`);
    } finally { await page.close(); }
  }
});
