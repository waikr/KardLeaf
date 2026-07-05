/**
 * Lightweight inline markdown renderer for table cells.
 *
 * Supports: **bold**, __bold__, *italic*, _italic_, ~~strike~~, ==highlight==,
 * `code`, [text](url), ![alt](url), $latex$ (rendered via KaTeX downstream).
 * All HTML is escaped before pattern replacement so user input cannot inject
 * tags. URLs are filtered to a safe scheme allowlist.
 *
 * Inline math returns a placeholder span (`<span class="cm-table-math"
 * data-tex="...">`) — the consumer (e.g. `renderBlockTables`) is expected to
 * call `katex.render` on each span asynchronously after insertion. Wikilinks
 * `[[note]]` are still NOT supported.
 */

const HTML_ENTITIES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (ch) => HTML_ENTITIES[ch]);
}

const SAFE_URL_RE = /^(https?:\/\/|mailto:|\/|#|[^:]+$)/i;

function isSafeUrl(url: string): boolean {
  return SAFE_URL_RE.test(url.trim());
}

// Use Unicode Private Use Area chars as placeholders so they cannot collide
// with anything in user-escaped HTML.
const CODE_OPEN = '\u{E000}';
const CODE_CLOSE = '\u{E001}';
const MATH_OPEN = '\u{E002}';
const MATH_CLOSE = '\u{E003}';
const BR_TOKEN = '\u{E004}';

function applyEmphasis(html: string, mark: '*' | '_'): string {
  const escaped = mark === '*' ? '\\*' : '_';
  const re = new RegExp(`(${escaped}+)([^${escaped}\\s][^${escaped}]*?)\\1`, 'g');
  return html.replace(re, (match, marks: string, content: string) => {
    if (marks.length === 1) return `<em>${content}</em>`;
    if (marks.length === 2) return `<strong>${content}</strong>`;
    if (marks.length === 3) return `<strong><em>${content}</em></strong>`;
    return match;
  });
}

export function renderInlineMarkdown(raw: string): string {
  // Pull `<br>` / `<br/>` / `<br />` out before HTML-escaping so users can
  // force line breaks inside table cells (markdown tables have no multi-line
  // cell syntax otherwise). Restored at the end below.
  let html = escapeHtml(raw.replace(/<br\s*\/?>/gi, BR_TOKEN));

  // Protect inline code spans before other transforms — `code` content must
  // not be re-parsed for emphasis or link syntax.
  const codeSpans: string[] = [];
  html = html.replace(/`([^`\n]+)`/g, (_, code: string) => {
    codeSpans.push(code);
    return `${CODE_OPEN}${codeSpans.length - 1}${CODE_CLOSE}`;
  });

  // Protect inline math `$...$` likewise — `$x*y$` must not have its `*`
  // chewed by the emphasis pass.
  const mathSpans: string[] = [];
  html = html.replace(/\$([^$\n]+)\$/g, (_, tex: string) => {
    mathSpans.push(tex);
    return `${MATH_OPEN}${mathSpans.length - 1}${MATH_CLOSE}`;
  });

  // Images first so the leading `!` doesn't get consumed by the link regex.
  html = html.replace(/!\[([^\]\n]*)\]\(([^\s)]+)\)/g, (match, alt: string, url: string) => {
    if (!isSafeUrl(url)) return match;
    return `<img src="${url}" alt="${alt}">`;
  });

  // Links — url comes from escaped html so `&` already became `&amp;`.
  html = html.replace(/\[([^\]\n]+)\]\(([^\s)]+)\)/g, (match, text: string, url: string) => {
    if (!isSafeUrl(url)) return match;
    return `<a href="${url}">${text}</a>`;
  });

  html = applyEmphasis(html, '*');
  html = applyEmphasis(html, '_');

  html = html.replace(/~~([^~\n]+)~~/g, '<del>$1</del>');
  html = html.replace(/==([^=\n]+)==/g, '<mark>$1</mark>');

  // Restore inline code last.
  const restoreRe = new RegExp(`${CODE_OPEN}(\\d+)${CODE_CLOSE}`, 'gu');
  html = html.replace(restoreRe, (_, idx: string) => `<code>${codeSpans[Number(idx)]}</code>`);

  // Restore inline math as KaTeX-pending placeholders. The consumer hydrates
  // these via `katex.render(tex, span)` after insertion.
  const mathRestoreRe = new RegExp(`${MATH_OPEN}(\\d+)${MATH_CLOSE}`, 'gu');
  html = html.replace(mathRestoreRe, (_, idx: string) => {
    const tex = mathSpans[Number(idx)] ?? '';
    return `<span class="cm-table-math" data-tex="${escapeHtml(tex)}">${escapeHtml(`$${tex}$`)}</span>`;
  });

  // Restore `<br>` line breaks last.
  html = html.split(BR_TOKEN).join('<br>');

  return html;
}
