// Kept in parity with SelectionInlineStyles.kt; both run the same fixture corpus.
export type Colors = { color?: string; backgroundColor?: string; fontSize?: string };
export type ColorProperty = 'color' | 'backgroundColor';
export type InlineStyleProperty = ColorProperty | 'fontSize';
export const colorPalette = ['#e53935', '#fb8c00', '#fdd835', '#43a047', '#1e88e5', '#8e24aa', '#212121', '#ffffff'];
export const colorNames = ['红色', '橙色', '黄色', '绿色', '蓝色', '紫色', '黑色', '白色'];
export const fontSizePalette = ['0.8em', '1.5em'];
export const fontSizeNames = ['默认', '小字', '大字'];
export function safeColor(value: string): string | null {
  const color = value.trim().toLowerCase();
  if (!/^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/.test(color)) return null;
  return color.length === 4 ? '#' + [...color.slice(1)].map(c => c + c).join('') : color;
}
export function safeFontSize(value: string): string | null {
  const size = value.trim().toLowerCase();
  return fontSizePalette.includes(size) ? size : null;
}
export function parseColorTag(tag: string): Colors | null {
  const match = /^<span\s+style\s*=\s*(["'])([^"'<>]*)\1\s*>$/i.exec(tag);
  if (!match) return null;
  const result: Colors = {};
  for (const part of match[2].split(';')) {
    if (!part.trim()) continue;
    const [key, value, extra] = part.split(':');
    if (!value || extra !== undefined) return null;
    const name = key.trim().toLowerCase();
    const normalized = name === 'font-size' ? safeFontSize(value) : safeColor(value);
    if (!normalized) return null;
    if (name === 'color') result.color = normalized;
    else if (name === 'background-color') result.backgroundColor = normalized;
    else if (name === 'font-size') result.fontSize = normalized;
    else return null; // Preserve unknown attributes/declarations by declining the whole span.
  }
  return result;
}
export function colorStyle(colors: Colors): string {
  return [colors.color && `color:${colors.color}`, colors.backgroundColor && `background-color:${colors.backgroundColor}`, colors.fontSize && `font-size:${colors.fontSize}`].filter(Boolean).join(';');
}
type Interval = { from: number; to: number };
export type ColorSpan = Interval & { openTo: number; closeFrom: number; colors: Colors };
const intersects = (a: Interval, b: Interval) => a.from < b.to && b.from < a.to;

export function scanColorDocument(text: string): { spans: ColorSpan[]; blocked: Interval[] } {
  const blocked: Interval[] = [];
  let offset = 0, fence = '', math = false, yaml = false;
  for (const line of text.split('\n')) {
    const content = line.replace(/^\s*(?:>\s*)+/, '').trim();
    const marker = /^(?:[-+*]\s+|\d+[.)]\s+)?(`{3,}|~{3,})/.exec(content)?.[1];
    const wasBlocked = !!fence || math || yaml;
    if (offset === 0 && content === '---') yaml = true;
    else if (yaml && /^(---|\.\.\.)$/.test(content)) yaml = false;
    if (!yaml && marker) {
      if (!fence) fence = marker;
      else if (marker[0] === fence[0] && marker.length >= fence.length && content === marker) fence = '';
    }
    if (!fence && content.startsWith('$$') && (content.match(/\$\$/g)?.length ?? 0) === 1) math = !math;
    // ponytail: tables/inline math/link syntax still use whole-line exclusion; refine with parser ranges if partial selection is needed.
    if (wasBlocked || fence || math || yaml || marker || /[$|]|\\[()[\]]|^( {4}|\t)|!\[|\]\(/.test(line)) {
      blocked.push({ from: offset, to: offset + line.length + 1 });
    }
    const prefix = /^[ \t]{0,3}(?:#{1,6}(?:[ \t]+|$)|(?:[-+*]|\d+[.)])[ \t]+(?:\[[ xX]\][ \t]+)?|(?:>[ \t]*)+)/.exec(line)?.[0];
    if (prefix) blocked.push({ from: offset, to: offset + prefix.length });
    if (/^(?:[=-]{2,}|(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$/.test(content)) blocked.push({ from: offset, to: offset + line.length + 1 });
    offset += line.length + 1;
  }
  for (const pattern of [/(`+)[\s\S]*?\1/g, /\\\[[\s\S]*?(?:\\\]|$)/g, /\\\([\s\S]*?(?:\\\)|$)/g]) {
    for (const match of text.matchAll(pattern)) blocked.push({ from: match.index!, to: match.index! + match[0].length });
  }
  const spans: ColorSpan[] = [];
  const stack: { from: number; openTo: number; name: string; colors: Colors | null }[] = [];
  for (const match of text.matchAll(/<[^>\n]*>/g)) {
    const from = match.index!, to = from + match[0].length;
    if (blocked.some(range => intersects(range, { from, to }))) continue;
    const escaped = /(?:^|[^\\])(?:\\\\)*\\$/.test(text.slice(0, from));
    const openName = /^<([a-z][\w:-]*)\b/i.exec(match[0])?.[1].toLowerCase();
    const closeName = /^<\/([a-z][\w:-]*)\s*>$/i.exec(match[0])?.[1].toLowerCase();
    if (!escaped && openName && !/\/\s*>$/.test(match[0]) && !['br', 'img', 'hr', 'input', 'wbr', 'source', 'meta', 'link', 'area', 'base', 'embed', 'param', 'track', 'col'].includes(openName)) {
      stack.push({ from, openTo: to, name: openName, colors: parseColorTag(match[0]) });
    } else if (!escaped && closeName && stack.at(-1)?.name === closeName) {
      const open = stack.pop()!;
      if (open.colors) spans.push({ ...open, to, closeFrom: from, colors: open.colors });
      else blocked.push({ from: open.from, to });
    } else blocked.push({ from, to });
  }
  for (const open of stack) blocked.push({ from: open.from, to: text.length });
  // A span crossing a code/math/unknown-HTML region is never partially rewritten or concealed.
  return { spans: spans.filter(span => !blocked.some(range => intersects(span, range))), blocked };
}

export function colorSpanAtCursor(text: string, cursor: number): ColorSpan | null {
  if (!Number.isInteger(cursor) || cursor < 0 || cursor > text.length) return null;
  return scanColorDocument(text).spans
    .filter(span => Object.keys(span.colors).length > 0 && span.openTo < span.closeFrom && cursor >= span.openTo && cursor <= span.closeFrom)
    .sort((a, b) => (a.to - a.from) - (b.to - b.from))[0] ?? null;
}

export function colorSpanStyleChangeAtCursor(
  text: string,
  cursor: number,
  property: InlineStyleProperty,
  value: string | null,
) {
  const normalized = value === null ? null : property === 'fontSize' ? safeFontSize(value) : safeColor(value);
  if (value !== null && !normalized) return null;
  const span = colorSpanAtCursor(text, cursor);
  if (!span) return null;
  const colors = { ...span.colors };
  if (normalized === null) delete colors[property];
  else colors[property] = normalized;
  if (Object.keys(colors).length === 0) {
    return { from: span.from, to: span.to, insert: text.slice(span.openTo, span.closeFrom) };
  }
  const openingTag = text.slice(span.from, span.openTo);
  const styleAttribute = /(\sstyle\s*=\s*)(["'])([^"']*)\2/i.exec(openingTag);
  if (!styleAttribute || styleAttribute.index === undefined) return null;
  const replacement = `${styleAttribute[1]}${styleAttribute[2]}${colorStyle(colors)}${styleAttribute[2]}`;
  const nextOpeningTag = openingTag.slice(0, styleAttribute.index) + replacement + openingTag.slice(styleAttribute.index + styleAttribute[0].length);
  return { from: span.from, to: span.openTo, insert: nextOpeningTag };
}

export function isColorHtml(html: string): boolean {
  const text = html.trim();
  if (!/^<span\b/i.test(text)) return false;
  if (parseColorTag(text) !== null) return true;
  return scanColorDocument(text).spans.some(span => span.from === 0 && span.to === text.length);
}

export function colorSelectionChange(text: string, anchor: number, head: number, property: InlineStyleProperty, value: string | null) {
  if (!['color', 'backgroundColor', 'fontSize'].includes(property)) return null;
  const normalized = value === null ? null : property === 'fontSize' ? safeFontSize(value) : safeColor(value);
  if (value !== null && !normalized) return null;
  const from = Math.min(anchor, head), to = Math.max(anchor, head);
  if (from < 0 || to > text.length || from === to) return null;
  const scan = scanColorDocument(text);
  let window = { from, to };
  // Expand to whole enclosing/intersecting spans, then flatten only that edit window.
  for (const span of [...scan.spans].sort((a, b) => a.from - b.from)) {
    if (intersects(span, window)) window = { from: Math.min(window.from, span.from), to: Math.max(window.to, span.to) };
  }
  if (scan.blocked.some(range => intersects(range, window))) return null;
  const spans = scan.spans.filter(span => intersects(span, window));
  const tags = spans.flatMap(span => [{ from: span.from, to: span.openTo }, { from: span.closeFrom, to: span.to }]);
  if (tags.some(tag => (from > tag.from && from < tag.to) || (to > tag.from && to < tag.to))) return null;
  const points = [...new Set([window.from, window.to, from, to, ...tags.flatMap(tag => [tag.from, tag.to])])].sort((a, b) => a - b);
  const runs: { text: string; colors: Colors; selected: boolean }[] = [];
  for (let i = 0; i < points.length - 1; i++) {
    const start = points[i], end = points[i + 1];
    if (tags.some(tag => start >= tag.from && end <= tag.to)) continue;
    const colors: Colors = {};
    for (const span of spans.filter(span => start >= span.openTo && end <= span.closeFrom).sort((a, b) => a.from - b.from)) Object.assign(colors, span.colors);
    const selected = start >= from && end <= to;
    if (selected) { if (normalized === null) delete colors[property]; else colors[property] = normalized; }
    for (const part of text.slice(start, end).split(/(\r?\n)/)) {
      if (part) runs.push({ text: part, colors: /\n/.test(part) ? {} : colors, selected });
    }
  }
  if (!runs.some(run => run.selected && run.text.trim())) return null;
  let insert = '', active = '', start = -1, end = -1;
  for (const run of runs) {
    const style = colorStyle(run.colors);
    if (style !== active) {
      if (active) insert += '</span>';
      if (style) insert += `<span style="${style}">`;
      active = style;
    }
    if (run.selected && start < 0) start = insert.length;
    insert += run.text;
    if (run.selected) end = insert.length;
  }
  if (active) insert += '</span>';
  start += window.from; end += window.from;
  return { from: window.from, to: window.to, insert, anchor: anchor <= head ? start : end, head: anchor <= head ? end : start };
}
