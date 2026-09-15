import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { colorSelectionChange, colorSpanStyleChangeAtCursor, scanColorDocument, safeColor, safeFontSize } from '../src/marktext/inlineStyles.ts';

const fixtures = JSON.parse(readFileSync(new URL('../../../test/resources/selection-inline-colors.json', import.meta.url)));
for (const fixture of fixtures) test(fixture.name, () => {
  const from = fixture.marked.indexOf('«'), to = fixture.marked.indexOf('»') - 1;
  const text = fixture.marked.replace('«', '').replace('»', '');
  const change = colorSelectionChange(text, fixture.reverse ? to : from, fixture.reverse ? from : to, fixture.property, fixture.value);
  if (fixture.expected === null) { assert.equal(change, null); return; }
  assert.ok(change);
  const result = text.slice(0, change.from) + change.insert + text.slice(change.to);
  assert.equal(result, fixture.expected);
  const selected = result.slice(Math.min(change.anchor, change.head), Math.max(change.anchor, change.head)).replace(/<[^>]*>/g, '');
  assert.equal(selected, text.slice(from, to).replace(/<[^>]*>/g, ''));
  assert.equal(change.anchor > change.head, !!fixture.reverse);
  assert.ok(!result.includes('style=""'));
});
test('cursor style change preserves the body and other styles at both content edges', () => {
  const text = 'x<span style="color:#e53935">abc</span>y';
  for (const cursor of [text.indexOf('>') + 1, text.indexOf('</span>')]) {
    const change = colorSpanStyleChangeAtCursor(text, cursor, 'backgroundColor', '#fdd835');
    assert.ok(change);
    assert.equal(text.slice(0, change.from) + change.insert + text.slice(change.to),
      'x<span style="color:#e53935;background-color:#fdd835">abc</span>y');
  }
  const clearText = '<span style="font-size:1.5em">abc</span>';
  const clear = colorSpanStyleChangeAtCursor(clearText, clearText.indexOf('abc'), 'fontSize', null);
  assert.ok(clear);
  assert.equal(clearText.slice(0, clear.from) + clear.insert + clearText.slice(clear.to), 'abc');
  assert.equal(colorSpanStyleChangeAtCursor(text, 0, 'fontSize', '1.5em'), null);
  assert.equal(colorSpanStyleChangeAtCursor(text, text.indexOf('abc'), 'color', 'url(x)'), null);
});
test('render scan does not conceal code/math or unsupported HTML; inline style validators are strict', () => {
  const span = '<span style="color:#123">正文</span>';
  for (const text of ['```\n' + span + '\n```', '$$\n' + span + '\n$$', '<span onclick="x()">' + span + '</span>']) assert.equal(scanColorDocument(text).spans.length, 0);
  assert.equal(safeColor(' #AbC '), '#aabbcc');
  for (const value of ['red', '#12', '#1234', '#12345678', 'url(x)', '#fff;opacity:0']) assert.equal(safeColor(value), null);
  assert.equal(safeFontSize(' 1.5EM '), '1.5em');
  for (const value of ['20px', '3em', 'calc(1em + 1px)']) assert.equal(safeFontSize(value), null);
});
