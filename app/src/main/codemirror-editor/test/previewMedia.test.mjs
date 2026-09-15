import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || 'playwright');

test('preview attachment MIME dispatch, actual playback, pause, errors and Markdown isolation', { timeout: 30000 }, async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
  try {
    const page = await browser.newPage();
    await page.goto('about:blank');
    // A tiny browser-generated video keeps the check independent of external fixtures/codecs.
    const bytes = await page.evaluate(async () => {
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 32;
      const stream = canvas.captureStream(10);
      const recorder = new MediaRecorder(stream, { mimeType: 'video/webm' });
      const chunks = [];
      recorder.ondataavailable = e => chunks.push(e.data);
      const stopped = new Promise(resolve => { recorder.onstop = resolve; });
      recorder.start();
      const timer = setInterval(() => {
        canvas.getContext('2d').fillStyle = 'blue';
        canvas.getContext('2d').fillRect(0, 0, 32, 32);
      }, 80);
      await new Promise(resolve => setTimeout(resolve, 800));
      recorder.stop();
      await stopped;
      clearInterval(timer);
      stream.getTracks().forEach(track => track.stop());
      return Array.from(new Uint8Array(await new Blob(chunks).arrayBuffer()));
    });
    const videoUrl = 'https://kardleaf-image.invalid/image/test?mime=video%2Fwebm';
    const pdfUrl = 'https://kardleaf-image.invalid/image/pdf?mime=application%2Fpdf';
    await page.route('https://kardleaf-image.invalid/**', route => route.fulfill({ contentType: 'video/webm', body: Buffer.from(bytes) }));
    await page.addInitScript(() => {
      window.resources = {};
      window.controlTouches = 0;
      window.Android = {
        getAttachments: () => JSON.stringify(window.resources),
        onPreviewControlTouched: () => { window.controlTouches++; },
      };
    });
    await page.goto('file:///' + fileURLToPath(new URL('../../assets/preview/preview.html', import.meta.url)).replaceAll('\\', '/'));
    const markdown = '[演示](<attachments/a%20b.webm>)\n\n[文档](attachments/doc.pdf)\n\n`[code](attachments/a%20b.webm)`\n\n<video src="https://evil.invalid/a.mp4" controls></video>\n\n[引用][clip]\n\n[clip]: attachments/a%20b.webm';
    await page.evaluate(markdown => window.updateContent(markdown, false, 1), markdown);
    assert.equal(await page.locator('video').count(), 0);
    await page.evaluate(({ videoUrl, pdfUrl }) => {
      window.resources = { 'attachments/a b.webm': videoUrl, 'attachments/doc.pdf': pdfUrl };
      window.attachPreviewMedia();
      window.attachPreviewMedia();
    }, { videoUrl, pdfUrl });
    assert.equal(await page.locator('video').count(), 2, 'reference links work; repeated resource delivery does not duplicate players');
    assert.equal(await page.locator('a').filter({ hasText: '文档' }).getAttribute('href'), pdfUrl);
    assert.match(await page.locator('code').first().textContent(), /\[code\]/);
    const media = page.locator('video').first();
    assert.equal(await media.getAttribute('controls'), '');
    assert.equal(await media.getAttribute('autoplay'), null);
    await media.evaluate(async video => { await video.play(); });
    await page.waitForFunction(() => document.querySelector('video').currentTime > 0);
    await page.evaluate(() => window.pausePreviewMedia());
    assert.equal(await media.evaluate(video => video.paused), true);
    await media.dispatchEvent('touchstart');
    assert.equal(await page.evaluate(() => window.controlTouches), 1);
    await media.dispatchEvent('error');
    assert.match(await page.locator('.preview-media-status').first().textContent(), /无法播放/);
    await page.evaluate(() => window.updateContent(
      '<span style="color:#e53935">行内颜色</span> 普通\n\n<span style="font-size:1.5em">\n多行大字\n</span>',
      false,
      2,
    ));
    const spans = page.locator('#content span');
    assert.equal(await spans.count(), 2);
    assert.equal(await spans.nth(0).textContent(), '行内颜色');
    assert.equal(await spans.nth(0).evaluate(element => element.style.color), 'rgb(229, 57, 53)');
    assert.match(await spans.nth(1).textContent(), /多行大字/);
    assert.equal(await spans.nth(1).evaluate(element => element.style.fontSize), '1.5em');
    await page.evaluate(() => window.updateContent('[网上视频](https://example.invalid/movie.mp4#t=10,20)\n\n[危险](javascript:alert(1))', false, 3));
    assert.equal(await page.locator('video source').getAttribute('src'), 'https://example.invalid/movie.mp4#t=10,20');
    assert.equal(await page.locator('a[href^="javascript:"]').count(), 0);
  } finally {
    await browser.close();
  }
});
