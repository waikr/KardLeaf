import assert from 'node:assert/strict';
import test from 'node:test';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, access } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

// Uses an installed desktop browser, not an Android emulator. Override the
// executable with KARDLEAF_TEST_BROWSER on other platforms.
test('WebView selection rendering and table/link gestures', { timeout: 60000 }, async () => {
  const root = fileURLToPath(new URL('../', import.meta.url));
  const candidates = [process.env.KARDLEAF_TEST_BROWSER,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].filter(Boolean);
  let browser;
  for (const candidate of candidates) {
    try { await access(candidate); browser = candidate; break; } catch {}
  }
  assert.ok(browser, 'Set KARDLEAF_TEST_BROWSER to an installed Chromium executable');
  const bundle = await build({ absWorkingDir: root, entryPoints: ['test/selection.browser.ts'],
    bundle: true, format: 'iife', target: 'chrome96', write: false, loader: { '.css': 'text' },
    logLevel: 'silent' });
  const css = await readFile(path.join(root, '../assets/codemirror-editor/editor.css'));
  let complete;
  const result = new Promise((resolve) => { complete = resolve; });
  const server = createServer(async (req, res) => {
    if (req.url === '/result') {
      let body = '';
      for await (const chunk of req) body += chunk;
      res.end('ok');
      complete(JSON.parse(body));
    } else if (req.url === '/test.js') {
      res.setHeader('Content-Type', 'text/javascript; charset=utf-8');
      res.end(bundle.outputFiles[0].contents);
    } else if (req.url === '/editor.css') {
      res.setHeader('Content-Type', 'text/css');
      res.end(css);
    } else {
      res.setHeader('Content-Type', 'text/html');
      res.end('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' +
        '<link rel="stylesheet" href="/editor.css"><div id="app"><div id="status"></div><div id="editorRoot"></div></div>' +
        '<script>window.testLinks=[];window.KardLeafAndroid={' +
        'consumeDocumentPayload:token=>token==="test"?JSON.stringify({documentToken:"body",selectionStart:0,selectionEnd:0}):"",' +
        'openExternalUrl:url=>testLinks.push(url),' +
        'selectionToolbarRequest:payload=>{const {id,op}=JSON.parse(payload);' +
        '(window.testSelectionRequests??=[]).push(op);' +
        'queueMicrotask(()=>window.KardLeafSelection.response(id,{ok:true,rows:1,commands:["toggleBold"]}));}};' +
        'window.addEventListener("error",e=>fetch("/result",{method:"POST",body:JSON.stringify({failures:[e.message+" "+e.error?.stack]})}));</script>' +
        '<script src="/test.js"></script>');
    }
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const profile = await mkdtemp(path.join(tmpdir(), 'kardleaf-selection-test-'));
  const child = spawn(browser, ['--headless', '--no-first-run', '--no-default-browser-check',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
    '--user-agent=Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Mobile Safari/537.36',
    '--window-size=480,800', `--user-data-dir=${profile}`,
    `http://127.0.0.1:${server.address().port}/?livePreview=true&bootstrap=test`], { windowsHide: true, stdio: 'ignore' });
  let timeout;
  try {
    const report = await Promise.race([result,
      new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error('Browser checks timed out')), 45000); }),
      new Promise((_, reject) => child.once('error', reject)),
    ]);
    for (const check of report.checks ?? []) process.stdout.write(`${check}\n`);
    assert.deepEqual(report.failures, [], JSON.stringify(report.failures));
  } finally {
    clearTimeout(timeout);
    child.kill();
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
    // Only remove the dedicated profile created above.
    assert.equal(path.dirname(profile), path.resolve(tmpdir()));
    assert.ok(path.basename(profile).startsWith('kardleaf-selection-test-'));
    await rm(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
  }
});
