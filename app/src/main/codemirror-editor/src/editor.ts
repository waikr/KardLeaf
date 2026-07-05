import katexCss from 'katex/dist/katex.css';
import { redoDepth, undoDepth } from '@codemirror/commands';
import {
  type EditorState,
  EditorSelection,
  RangeSetBuilder,
  StateEffect,
  StateField,
  Transaction,
  type Extension,
} from '@codemirror/state';
import {
  Decoration,
  type DecorationSet,
  EditorView,
  WidgetType,
} from '@codemirror/view';
import {
  createEditor as createSwarmEditor,
  DEFAULT_SETTINGS,
  EditorEventType,
  type EditorControl,
  type EditorEvent,
  type EditorPlugin,
  type EditorSettings,
} from './vendor/swarmnote-editor-core';
import { admonitionPlugin } from './vendor/swarmnote-editor-core/plugins/admonition';
import {
  blockImagePlugin,
  refreshBlockImagesEffect,
} from './vendor/swarmnote-editor-core/plugins/blockImage';
import { codeBlockPlugin } from './vendor/swarmnote-editor-core/plugins/codeBlock';
import { mathPlugin } from './vendor/swarmnote-editor-core/plugins/math';
import { mermaidPlugin } from './vendor/swarmnote-editor-core/plugins/mermaid';
import { rawHtmlPlugin } from './vendor/swarmnote-editor-core/plugins/rawHtml';
import { smartPastePlugin } from './vendor/swarmnote-editor-core/plugins/smartPaste';
import { tablePlugin } from './vendor/swarmnote-editor-core/plugins/table';
import { selectionToolbarPlugin } from './vendor/swarmnote-editor-core/plugins/interactions/selectionToolbar';
import { slashCommandPlugin } from './vendor/swarmnote-editor-core/plugins/interactions/slash';
import { wikilinkPlugin } from './vendor/swarmnote-editor-core/plugins/interactions/wikilink';

type AndroidBridge = Record<string, (...args: unknown[]) => unknown>;

declare global {
  interface Window {
    KardLeafAndroid?: AndroidBridge;
    KardLeafEditor?: Record<string, unknown>;
  }
}

const VERSION = 'kardleaf-swarmnote-core-2026-06-27';
const SCROLL_METRIC_INTERVAL_MS = 48;
const root = document.getElementById('editorRoot');
const statusEl = document.getElementById('status');
const imageDataUris = new Map<string, string>();
const refreshWikiImagesEffect = StateEffect.define<null>();

let editor: EditorControl | null = null;
let suppressBridgeDepth = 0;
let livePreviewEnabled = true;
let readOnly = false;
let currentFontSize = 16;
let currentLineHeight = 1.55;
let currentLetterSpacing = 0;
let currentParagraphSpacing = 8;
let currentFontFamily =
  'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
let darkMode = false;
let appThemeColors: Record<string, string> = {};
let fallbackText = '';
let fallbackTextArea: HTMLTextAreaElement | null = null;
let lastHistoryState = '';
let scrollSession: {
  start: number;
  lastFrame: number;
  frames: number;
  slowFrames: number;
  maxFrameMs: number;
} | null = null;
let scrollSettleTimer = 0;
let lastScrollMetricAt = 0;
let cursorEnsureTimer = 0;
let viewportEnsureTimer = 0;
let lastViewportKeyboardInsetPx = -1;
let viewportKeyboardSyncInstalled = false;
let contentApplied = false;
let contentApplying = false;
let contentAppliedAt = 0;
let pendingTap:
  | {
      x: number;
      y: number;
      startAt: number;
      maxMove: number;
      selectionSetAtStart: number;
    }
  | null = null;
let pendingTapBeforeContentApplied:
  | {
      x: number;
      y: number;
      at: number;
      selectionSetAtStart: number;
      reason: string;
    }
  | null = null;
let lastSelectionSetAt = 0;

function nowMs() {
  return typeof performance !== 'undefined' && performance.now
    ? performance.now()
    : Date.now();
}

function bridge(): AndroidBridge | null {
  return window.KardLeafAndroid ?? null;
}

function log(tag: string, message: string) {
  try {
    console.log(`[${tag}] ${message}`);
  } catch {
    // Console may be unavailable in older WebView startup failure paths.
  }
}

function reportError(message: string, error?: unknown) {
  const detail =
    error instanceof Error
      ? error.stack || error.message
      : error == null
        ? ''
        : String(error);
  log('KardLeafCM6Bridge', `error message=${message} detail=${detail}`);
  try {
    const target = bridge();
    if (target && typeof target.onEditorError === 'function') {
      target.onEditorError(String(message || ''), detail);
    }
  } catch {
    // Reporting must never break editor startup.
  }
}

function setStatus(message: string, isError = false) {
  if (!statusEl) return;
  statusEl.textContent = message;
  statusEl.classList.toggle('visible', !!message);
  statusEl.classList.toggle('error', !!isError);
}

function callBridge(name: string, args: unknown[] = []) {
  try {
    const target = bridge();
    if (!target) return undefined;
    // Android WebView JavaScriptInterface methods must be invoked on the
    // injected object itself. Extracting the method first can throw:
    // "Java bridge method can't be invoked on a non-injected object".
    return target[name]?.(...args);
  } catch (error) {
    reportError(`bridge call failed: ${name}`, error);
  }
  return undefined;
}

function withSuppressedBridge<T>(fn: () => T): T {
  suppressBridgeDepth += 1;
  try {
    return fn();
  } finally {
    suppressBridgeDepth -= 1;
  }
}

function injectStyle(id: string, cssText: string) {
  if (!cssText || document.getElementById(id)) return;
  const style = document.createElement('style');
  style.id = id;
  style.textContent = cssText;
  document.head.appendChild(style);
}

function clampSelection(start: unknown, end: unknown, length: number) {
  const parsedStart = Number(start);
  const parsedEnd = Number(end);
  const anchor = Number.isFinite(parsedStart) ? parsedStart : length;
  const head = Number.isFinite(parsedEnd) ? parsedEnd : anchor;
  return {
    anchor: Math.max(0, Math.min(length, anchor)),
    head: Math.max(0, Math.min(length, head)),
  };
}

function setDocumentTheme(enabled: boolean) {
  darkMode = !!enabled;
  document.documentElement.dataset.theme = darkMode ? 'dark' : 'light';
}

function applyThemeColors(colors: unknown) {
  if (!colors || typeof colors !== 'object') return 'ignored';
  const next = colors as Record<string, unknown>;
  const accepted = [
    'background',
    'foreground',
    'muted',
    'border',
    'soft',
    'selection',
    'codeBackground',
    'heading',
    'link',
  ];
  accepted.forEach((key) => {
    const value = String(next[key] ?? '').trim();
    if (value) appThemeColors[key] = value;
  });
  const rootStyle = document.documentElement.style;
  if (appThemeColors.background) rootStyle.setProperty('--kl-shell-bg', appThemeColors.background);
  if (appThemeColors.foreground) rootStyle.setProperty('--kl-shell-fg', appThemeColors.foreground);
  if (appThemeColors.muted) rootStyle.setProperty('--kl-shell-muted', appThemeColors.muted);
  if (appThemeColors.border) rootStyle.setProperty('--kl-shell-border', appThemeColors.border);
  if (appThemeColors.soft) rootStyle.setProperty('--kl-shell-soft', appThemeColors.soft);
  updateRuntimeSettings();
  return 'ok';
}

function editorThemeColors() {
  return {
    background: appThemeColors.background || 'var(--kl-shell-bg)',
    foreground: appThemeColors.foreground || 'var(--kl-shell-fg)',
    selection: appThemeColors.selection || 'rgba(37, 99, 235, 0.22)',
    border: appThemeColors.border || 'var(--kl-shell-border)',
    codeBackground: appThemeColors.codeBackground || 'var(--kl-shell-soft)',
    heading: appThemeColors.heading || appThemeColors.foreground || 'var(--kl-shell-fg)',
    link: appThemeColors.link || '#2563eb',
  };
}

function normalizeImageReference(raw: unknown) {
  let ref = String(raw ?? '').trim();
  if (!ref) return '';
  ref = ref.replace(/^<|>$/g, '').trim().replace(/^['"]|['"]$/g, '').trim();
  const titleSplit = /^([^\s]+)\s+["'][^"']*["']$/.exec(ref);
  if (titleSplit) ref = titleSplit[1];
  ref = ref.replace(/\\/g, '/');
  while (ref.startsWith('./')) ref = ref.slice(2);
  return ref.trim();
}

function isSafeExternalImageSrc(src: string) {
  return /^(data:image\/|https?:\/\/|file:\/\/|content:\/\/|blob:)/i.test(src);
}

function resolveImageSource(rawSrc: string) {
  const raw = String(rawSrc || '').trim();
  const normalized = normalizeImageReference(raw);
  return (
    imageDataUris.get(raw) ||
    imageDataUris.get(normalized) ||
    imageDataUris.get(decodeURIComponentSafe(normalized)) ||
    (isSafeExternalImageSrc(raw) ? raw : '')
  );
}

function decodeURIComponentSafe(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function normalizeFontFamily(fontFamily: string) {
  const value = String(fontFamily || '').trim();
  if (!value || value === 'system') {
    return 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  }
  return value;
}

function applyTypographyStyle(style: unknown) {
  if (!style || typeof style !== 'object') return;
  const data = style as Record<string, unknown>;
  const lineHeight = Number(data.lineHeight);
  const letterSpacing = Number(data.letterSpacing);
  const paragraphSpacing = Number(data.paragraphSpacing);
  if (Number.isFinite(lineHeight)) currentLineHeight = Math.max(1, Math.min(2.5, lineHeight));
  if (Number.isFinite(letterSpacing)) currentLetterSpacing = Math.max(-1, Math.min(3, letterSpacing));
  if (Number.isFinite(paragraphSpacing)) currentParagraphSpacing = Math.max(0, Math.min(32, paragraphSpacing));
  if (typeof data.fontFamily === 'string' && data.fontFamily.trim()) currentFontFamily = data.fontFamily.trim();
}

function buildSettings(): EditorSettings {
  return {
    ...DEFAULT_SETTINGS,
    readonly: readOnly,
    editable: !readOnly,
    lineWrapping: true,
    indentWithTabs: false,
    tabSize: 2,
    autofocus: false,
    spellcheck: false,
    showLineNumbers: false,
    features: {
      ...DEFAULT_SETTINGS.features,
      markdownHighlight: true,
      markdownDecorations: livePreviewEnabled,
      inlineRendering: livePreviewEnabled,
      search: true,
      collaboration: false,
    },
    theme: {
      ...DEFAULT_SETTINGS.theme,
      appearance: darkMode ? 'dark' : 'light',
      fontFamily: normalizeFontFamily(currentFontFamily),
      fontSize: currentFontSize,
      lineHeight: currentLineHeight,
      letterSpacing: currentLetterSpacing,
      paragraphSpacing: currentParagraphSpacing,
      colors: editorThemeColors(),
    },
  };
}

function notifyHistoryState(force = false) {
  const view = editor?.view;
  if (!view) return;
  const canUndo = undoDepth(view.state) > 0;
  const canRedo = redoDepth(view.state) > 0;
  const key = `${canUndo}:${canRedo}`;
  if (!force && key === lastHistoryState) return;
  lastHistoryState = key;
  callBridge('onHistoryStateChanged', [canUndo, canRedo]);
}

function notifySelection() {
  const selection = editor?.view.state.selection.main;
  if (!selection) return;
  callBridge('onSelectionChanged', [selection.from, selection.to]);
}

function emitScrollMetrics(
  eventName: string,
  elapsedMs = 0,
  frames = 0,
  slowFrames = 0,
  maxFrameMs = 0,
  avgFrameMs = 0,
  smooth = true,
) {
  const scroller = editor?.view.scrollDOM;
  if (!scroller) return;
  callBridge('onEditorScrollPerf', [
    eventName,
    elapsedMs,
    frames,
    slowFrames,
    maxFrameMs,
    avgFrameMs,
    smooth,
    Math.round(scroller.scrollTop || 0),
    Math.round(scroller.scrollHeight || 0),
    Math.round(scroller.clientHeight || 0),
  ]);
}

function handleScroll() {
  const timestamp = nowMs();
  if (!scrollSession) {
    scrollSession = {
      start: timestamp,
      lastFrame: timestamp,
      frames: 0,
      slowFrames: 0,
      maxFrameMs: 0,
    };
    emitScrollMetrics('start');
  } else {
    const delta = timestamp - scrollSession.lastFrame;
    scrollSession.lastFrame = timestamp;
    scrollSession.frames += 1;
    if (delta >= 24) scrollSession.slowFrames += 1;
    if (delta > scrollSession.maxFrameMs) scrollSession.maxFrameMs = delta;
  }

  if (timestamp - lastScrollMetricAt > SCROLL_METRIC_INTERVAL_MS) {
    lastScrollMetricAt = timestamp;
    emitScrollMetrics('scroll');
  }

  clearTimeout(scrollSettleTimer);
  scrollSettleTimer = window.setTimeout(() => {
    if (!scrollSession) return;
    const elapsed = nowMs() - scrollSession.start;
    const avg = scrollSession.frames > 0 ? elapsed / scrollSession.frames : 0;
    emitScrollMetrics(
      'settled',
      elapsed,
      scrollSession.frames,
      scrollSession.slowFrames,
      scrollSession.maxFrameMs,
      avg,
      scrollSession.slowFrames <= Math.max(2, scrollSession.frames * 0.2),
    );
    scrollSession = null;
  }, 180);
}

class WikiImageWidget extends WidgetType {
  constructor(
    private readonly rawSrc: string,
    private readonly alt: string,
    private readonly from: number,
    private readonly sourceVisible: boolean,
    private readonly resolver: (src: string) => string | Promise<string>,
    private readonly tick: number,
  ) {
    super();
  }

  eq(other: WikiImageWidget) {
    return (
      this.rawSrc === other.rawSrc &&
      this.alt === other.alt &&
      this.from === other.from &&
      this.sourceVisible === other.sourceVisible &&
      this.resolver === other.resolver &&
      this.tick === other.tick
    );
  }

  toDOM(view: EditorView) {
    const container = document.createElement('div');
    container.className = 'kl-wiki-image';
    if (this.sourceVisible) container.classList.add('kl-wiki-image-source-visible');

    const frame = document.createElement('span');
    frame.className = 'kl-wiki-image-frame';

    const setFallback = () => {
      frame.textContent = '';
      const fallback = document.createElement('span');
      fallback.className = 'kl-wiki-image-fallback';
      fallback.textContent = `Image unavailable: ${this.rawSrc}`;
      frame.appendChild(fallback);
    };

    const img = document.createElement('img');
    img.alt = this.alt || this.rawSrc;
    img.decoding = 'async';
    img.loading = 'lazy';
    img.draggable = false;
    img.onerror = () => {
      log('KardLeafCM6Image', `wiki image load failed src=${this.rawSrc}`);
      setFallback();
    };

    Promise.resolve(this.resolver(this.rawSrc))
      .then((resolved) => {
        if (!frame.isConnected) return;
        if (resolved) img.src = resolved;
        else setFallback();
      })
      .catch(() => {
        if (frame.isConnected) setFallback();
      });

    frame.appendChild(img);
    container.appendChild(frame);

    container.addEventListener('mousedown', (event) => {
      if (this.sourceVisible) return;
      event.preventDefault();
      event.stopPropagation();
      view.dispatch({
        selection: EditorSelection.cursor(this.from),
        scrollIntoView: true,
      });
      view.focus();
    });

    return container;
  }

  ignoreEvent(event: Event) {
    return event.type !== 'mousedown';
  }
}

function parseWikiImageLine(text: string) {
  const match = /^\s*!\[\[([^\]\n]+)]]\s*$/.exec(text);
  if (!match) return null;
  const body = match[1].trim();
  const pipe = body.indexOf('|');
  const rawSrc = (pipe >= 0 ? body.slice(0, pipe) : body).trim();
  const alt = (pipe >= 0 ? body.slice(pipe + 1) : '').trim();
  if (!rawSrc) return null;
  return { rawSrc, alt };
}

function createWikiImageExtension(
  resolver: (src: string) => string | Promise<string>,
): Extension {
  let tick = 0;

  function buildDecorations(state: EditorState): DecorationSet {
    const builder = new RangeSetBuilder<Decoration>();
    const head = state.selection.main.head;
    const cursorLine = state.doc.lineAt(head).number;

    for (let lineNumber = 1; lineNumber <= state.doc.lines; lineNumber += 1) {
      const line = state.doc.line(lineNumber);
      const parsed = parseWikiImageLine(line.text);
      if (!parsed) continue;

      const sourceVisible = cursorLine === line.number;
      const widget = new WikiImageWidget(
        parsed.rawSrc,
        parsed.alt,
        line.from,
        sourceVisible,
        resolver,
        tick,
      );

      if (sourceVisible) {
        builder.add(
          line.to,
          line.to,
          Decoration.widget({ widget, block: true, side: 1 }),
        );
      } else {
        builder.add(
          line.from,
          line.to,
          Decoration.replace({ widget, block: true }),
        );
      }
    }

    return builder.finish();
  }

  const field = StateField.define<DecorationSet>({
    create: buildDecorations,
    update(value, tr) {
      const hasRefresh = tr.effects.some((effect) => effect.is(refreshWikiImagesEffect));
      if (hasRefresh) tick += 1;
      if (tr.docChanged || tr.selection || hasRefresh) {
        return buildDecorations(tr.state);
      }
      return value;
    },
    provide: (f) => EditorView.decorations.from(f),
  });

  return field;
}

function createKardLeafWikiImagePlugin(): EditorPlugin {
  return {
    id: 'kardleaf.wikiImage',
    setup(ctx) {
      ctx.registerCmExtensions([
        createWikiImageExtension((src) => ctx.host.resolveImage?.(src) ?? src),
      ]);
    },
  };
}


function computeViewportKeyboardInsetPx() {
  const viewport = window.visualViewport;
  const layoutHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0);
  if (!viewport || layoutHeight <= 0) return 0;

  const visibleBottom = viewport.height + viewport.offsetTop;
  const rawInset = Math.max(0, Math.round(layoutHeight - visibleBottom));
  // Safety cap: if Android/WebView already resized the page, or a device reports
  // a full root-window IME height, using the raw value can push short notes to a
  // large blank area. Keep only a reasonable visual-viewport overlap.
  const cap = Math.round(layoutHeight * 0.62);
  return Math.min(rawInset, cap);
}

function computeScrollBottomMarginPx(keyboardInsetPx: number) {
  const layoutHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0);
  // Match Swarm's RN WebView behavior more closely: always keep enough bottom
  // space so the last lines can scroll above the keyboard, even on Android
  // WebView builds where visualViewport does not report the IME overlap.
  const baseMargin = layoutHeight > 0 ? Math.round(layoutHeight * 0.5) : 240;
  return Math.max(96, baseMargin, Math.max(0, keyboardInsetPx) + 32);
}

function editorHasFocus() {
  const view = editor?.view;
  if (!view) return false;
  return view.hasFocus || Boolean(document.activeElement && root?.contains(document.activeElement));
}

function applyViewportKeyboardInset(reason: string) {
  const inset = computeViewportKeyboardInsetPx();
  if (inset === lastViewportKeyboardInsetPx) return inset;
  lastViewportKeyboardInsetPx = inset;
  const margin = computeScrollBottomMarginPx(inset);
  document.documentElement.style.setProperty('--kardleaf-ime-safe-bottom', `${inset}px`);
  editor?.setScrollBottomMargin(margin);
  log('KardLeafCM6Input', `viewport keyboard inset=${inset} margin=${margin} reason=${reason}`);
  return inset;
}

function manuallyNudgeCursorIntoView(reason: string) {
  const view = editor?.view;
  if (!view) return;

  const scroller = view.scrollDOM;
  const selection = view.state.selection.main;
  const cursorRect =
    view.coordsAtPos(selection.head, 1) ||
    view.coordsAtPos(selection.head);
  const scrollerRect = scroller.getBoundingClientRect();
  if (!cursorRect || !scrollerRect) return;

  const viewport = window.visualViewport;
  const viewportBottom = viewport
    ? viewport.offsetTop + viewport.height
    : window.innerHeight;
  const keyboardInset = Math.max(0, lastViewportKeyboardInsetPx || 0);
  const safeBottom = Math.min(scrollerRect.bottom, viewportBottom) - keyboardInset - 32;
  const topMargin = 24;
  const safeTop = scrollerRect.top + topMargin;

  if (cursorRect.bottom > safeBottom) {
    scroller.scrollTop += Math.ceil(cursorRect.bottom - safeBottom);
    emitScrollMetrics(`cursor-nudge:${reason}`);
  } else if (cursorRect.top < safeTop) {
    scroller.scrollTop -= Math.ceil(safeTop - cursorRect.top);
    emitScrollMetrics(`cursor-nudge:${reason}`);
  }
}

function ensureEditorCursorVisible(reason?: unknown) {
  const view = editor?.view;
  if (!view) return 'missing';
  const inset = applyViewportKeyboardInset(String(reason || 'ensure'));
  const selection = view.state.selection.main;
  view.dispatch({
    effects: EditorView.scrollIntoView(selection.head, {
      y: 'nearest',
      yMargin: inset > 0 ? 144 : 96,
    }),
  });
  window.requestAnimationFrame(() => manuallyNudgeCursorIntoView(String(reason || 'cursor')));
  log('KardLeafCM6Input', `ensure cursor visible reason=${String(reason || 'unknown')} inset=${inset}`);
  return 'ok';
}

function scheduleEnsureCursorVisible(reason: string, delayMs = 90) {
  if (!editorHasFocus()) return;
  if (cursorEnsureTimer) window.clearTimeout(cursorEnsureTimer);
  cursorEnsureTimer = window.setTimeout(() => {
    cursorEnsureTimer = 0;
    if (editorHasFocus()) ensureEditorCursorVisible(reason);
  }, delayMs);
}

function scheduleDelayedCursorEnsures(reason: string) {
  scheduleEnsureCursorVisible(reason, 120);
  window.setTimeout(() => scheduleEnsureCursorVisible(`${reason}-delayed`, 320), 320);
}

function installViewportKeyboardSync() {
  if (viewportKeyboardSyncInstalled) return;
  viewportKeyboardSyncInstalled = true;
  const sync = (reason: string) => {
    applyViewportKeyboardInset(reason);
    if (!editorHasFocus()) return;
    if (reason === 'focusin') {
      scheduleDelayedCursorEnsures('focusin');
      return;
    }
    if (reason.startsWith('visualViewport.')) {
      if (viewportEnsureTimer) window.clearTimeout(viewportEnsureTimer);
      viewportEnsureTimer = window.setTimeout(() => {
        viewportEnsureTimer = 0;
        scheduleDelayedCursorEnsures(reason);
      }, 80);
      return;
    }
    scheduleEnsureCursorVisible(reason, 120);
  };
  window.visualViewport?.addEventListener('resize', () => sync('visualViewport.resize'), { passive: true });
  window.visualViewport?.addEventListener('scroll', () => sync('visualViewport.scroll'), { passive: true });
  window.addEventListener('resize', () => sync('window.resize'), { passive: true });
  document.addEventListener('focusin', () => sync('focusin'));
}

function isTapIgnoredTarget(target: EventTarget | null) {
  if (!(target instanceof Element)) return false;
  return Boolean(target.closest('button,a,input,textarea,select'));
}

function scheduleTapEnsures(reason: string) {
  scheduleEnsureCursorVisible(`${reason}:tap`, 120);
  window.setTimeout(() => {
    scheduleEnsureCursorVisible(`${reason}:tap-delayed`, 320);
  }, 320);
}

function scheduleTapCaretSync(
  x: number,
  y: number,
  tapAt: number,
  selectionSetAtStart: number,
  reason: string,
) {
  const view = editor?.view;
  if (!view) return;

  window.requestAnimationFrame(() => {
    window.setTimeout(() => {
      const currentView = editor?.view;
      if (!currentView) return;

      const pos = currentView.posAtCoords({ x, y });
      if (typeof pos !== 'number') return;

      const safePos = Math.max(0, Math.min(pos, currentView.state.doc.length));
      const currentHead = currentView.state.selection.main.head;
      if (
        lastSelectionSetAt > tapAt &&
        lastSelectionSetAt !== selectionSetAtStart &&
        Math.abs(currentHead - safePos) <= 2
      ) {
        scheduleTapEnsures(`${reason}:native-selection`);
        return;
      }

      currentView.dispatch({
        selection: EditorSelection.cursor(safePos),
        effects: EditorView.scrollIntoView(safePos, {
          y: 'nearest',
          yMargin: 144,
        }),
        annotations: Transaction.addToHistory.of(false),
      });

      currentView.focus();
      notifySelection();
      scheduleTapEnsures(`${reason}:posAtCoords`);
    }, 0);
  });
}

function handleTapCaret(
  x: number,
  y: number,
  tapAt: number,
  selectionSetAtStart: number,
  reason: string,
) {
  if (!contentApplied || contentApplying) {
    pendingTapBeforeContentApplied = { x, y, at: tapAt, selectionSetAtStart, reason };
    return;
  }
  scheduleTapCaretSync(x, y, tapAt, selectionSetAtStart, reason);
}

function onContentApplied() {
  contentApplying = false;
  contentApplied = true;
  contentAppliedAt = nowMs();
  const appliedLength = editor?.view.state.doc.length ?? fallbackText.length;
  log('KardLeafCM6Bridge', `content applied len=${appliedLength}`);
  callBridge('onContentApplied', [appliedLength]);

  const tap = pendingTapBeforeContentApplied;
  pendingTapBeforeContentApplied = null;
  if (tap && contentAppliedAt - tap.at < 800) {
    window.requestAnimationFrame(() => {
      scheduleTapCaretSync(
        tap.x,
        tap.y,
        tap.at,
        tap.selectionSetAtStart,
        `${tap.reason}:after-content-applied`,
      );
    });
  }
}

function createKardLeafBridgePlugin(): EditorPlugin {
  return {
    id: 'kardleaf.androidBridge',
    setup(ctx) {
      ctx.registerCmExtensions([
        EditorView.updateListener.of((update) => {
          if (suppressBridgeDepth <= 0 && update.docChanged) {
            const patches: Array<{
              start: number;
              deleteCount: number;
              inserted: string;
            }> = [];
            update.changes.iterChanges((fromA, toA, _fromB, _toB, inserted) => {
              patches.push({
                start: fromA,
                deleteCount: toA - fromA,
                inserted: inserted.toString(),
              });
            });
            const selection = update.state.selection.main;
            callBridge('onContentPatches', [
              JSON.stringify(patches),
              selection.from,
              selection.to,
            ]);
            log(
              'KardLeafCM6Bridge',
              `content changed patches=${patches.length} len=${update.state.doc.length}`,
            );
          }

          if (update.selectionSet) {
            lastSelectionSetAt = nowMs();
            notifySelection();
          }
          if (update.docChanged && update.view.hasFocus) scheduleEnsureCursorVisible('docChanged');
          if (update.docChanged) notifyHistoryState();
        }),
        EditorView.domEventHandlers({
          touchstart(event) {
            callBridge('onUserInteraction');
            const touch = event.touches && event.touches[0];
            if (!touch || isTapIgnoredTarget(event.target)) {
              pendingTap = null;
              return false;
            }
            pendingTap = {
              x: touch.clientX,
              y: touch.clientY,
              startAt: nowMs(),
              maxMove: 0,
              selectionSetAtStart: lastSelectionSetAt,
            };
            // Keep focus/keyboard behavior inside CodeMirror/WebView itself.
            // SwarmNote does not ask the native host to show the keyboard from
            // touch events; doing so makes normal vertical scrolling look like
            // a tap and causes IME pop-up/jump bugs on Android WebView.
            return false;
          },
          touchmove(event) {
            if (!pendingTap) return false;
            const touch = event.touches && event.touches[0];
            if (!touch) return false;
            pendingTap.maxMove = Math.max(
              pendingTap.maxMove,
              Math.abs(touch.clientX - pendingTap.x),
              Math.abs(touch.clientY - pendingTap.y),
            );
            return false;
          },
          touchend() {
            const tap = pendingTap;
            pendingTap = null;
            if (!tap) return false;
            const duration = nowMs() - tap.startAt;
            if (duration <= 280 && tap.maxMove <= 8) {
              handleTapCaret(
                tap.x,
                tap.y,
                tap.startAt,
                tap.selectionSetAtStart,
                'touchend',
              );
            }
            return false;
          },
          touchcancel() {
            pendingTap = null;
            return false;
          },
          mousedown() {
            callBridge('onUserInteraction');
          },
        }),
      ]);

      ctx.registerCommands([
        {
          id: 'kardleaf.refreshImages',
          run({ view }) {
            view.dispatch({
              effects: [
                refreshBlockImagesEffect.of(null),
                refreshWikiImagesEffect.of(null),
              ],
              annotations: Transaction.addToHistory.of(false),
            });
          },
        },
      ]);
    },
  };
}

function buildPlugins(): EditorPlugin[] {
  return [
    mathPlugin(),
    tablePlugin(),
    mermaidPlugin(),
    admonitionPlugin(),
    codeBlockPlugin({ mode: 'inline' }),
    blockImagePlugin({ maxLoadAttempts: 1 }),
    rawHtmlPlugin(),
    smartPastePlugin(),
    slashCommandPlugin(),
    wikilinkPlugin(),
    selectionToolbarPlugin(),
    createKardLeafWikiImagePlugin(),
    createKardLeafBridgePlugin(),
  ];
}

function handleEditorEvent(event: EditorEvent) {
  switch (event.kind) {
    case EditorEventType.LinkOpen:
      callBridge('openExternalUrl', [event.url]);
      break;
    case EditorEventType.TableContextMenu:
      log(
        'KardLeafCM6TableTrace',
        `table context row=${event.rowIdx} col=${event.colIdx} rows=${event.rowCount} cols=${event.colCount}`,
      );
      break;
    case EditorEventType.MermaidZoomRequest:
      log('KardLeafCM6', `mermaid zoom requested id=${event.id}`);
      break;
    default:
      break;
  }
}

function createEditorInstance(initialText = '', initialSelection?: { anchor: number; head: number }) {
  if (!root) throw new Error('Missing #editorRoot');
  root.textContent = '';
  editor = createSwarmEditor(root, {
    initialText,
    initialSelection: initialSelection
      ? {
          anchor: initialSelection.anchor,
          head: initialSelection.head,
          from: Math.min(initialSelection.anchor, initialSelection.head),
          to: Math.max(initialSelection.anchor, initialSelection.head),
        }
      : undefined,
    settings: buildSettings(),
    host: {
      resolveImage(src) {
        return resolveImageSource(src);
      },
      openLink(url) {
        callBridge('openExternalUrl', [url]);
      },
      getSlashItems: async () => [],
      getWikilinkItems: async () => [],
      getSelectionToolbarActions: () => [],
    },
    plugins: buildPlugins(),
    onEvent: handleEditorEvent,
  });
  editor.view.scrollDOM.addEventListener('scroll', handleScroll, { passive: true });
  installViewportKeyboardSync();
  notifyHistoryState(true);
  notifySelection();
  setStatus('');
  log('KardLeafCM6', `editor ready version=${VERSION}`);
  callBridge('onEditorReady', [VERSION, editor.view.state.doc.length]);
}

function updateRuntimeSettings() {
  if (!editor) return;
  editor.updateSettings({
    readonly: readOnly,
    editable: !readOnly,
    theme: {
      appearance: darkMode ? 'dark' : 'light',
      fontSize: currentFontSize,
      fontFamily: normalizeFontFamily(currentFontFamily),
      lineHeight: currentLineHeight,
      letterSpacing: currentLetterSpacing,
      paragraphSpacing: currentParagraphSpacing,
      colors: editorThemeColors(),
    },
    features: {
      markdownDecorations: livePreviewEnabled,
      inlineRendering: livePreviewEnabled,
    },
  });
}

function dispatchFullDocument(
  content: unknown,
  selectionStart?: unknown,
  selectionEnd?: unknown,
  addToHistory = false,
) {
  if (!editor) {
    fallbackText = String(content ?? '');
    if (fallbackTextArea) fallbackTextArea.value = fallbackText;
    return 'fallback';
  }

  const text = String(content ?? '');
  const start = nowMs();
  contentApplying = true;

  try {
    const view = editor.view;
    withSuppressedBridge(() => {
      view.dispatch({
        changes: { from: 0, to: view.state.doc.length, insert: text },
        annotations: Transaction.addToHistory.of(addToHistory),
      });
      const selection = clampSelection(selectionStart, selectionEnd, view.state.doc.length);
      view.dispatch({
        selection: EditorSelection.single(selection.anchor, selection.head),
      });
    });

    notifyHistoryState(true);
    notifySelection();
    log(
      'KardLeafCM6Perf',
      `setContent done len=${text.length} elapsed=${(nowMs() - start).toFixed(1)}ms`,
    );
    window.requestAnimationFrame(() => onContentApplied());
    return 'ok';
  } catch (error) {
    contentApplying = false;
    reportError(`setContent failed len=${text.length}`, error);
    log(
      'KardLeafCM6Perf',
      `setContent failed len=${text.length} elapsed=${(nowMs() - start).toFixed(1)}ms`,
    );
    return 'error';
  }
}

function selectEditorRangeAndReveal(start: unknown, end: unknown) {
  const view = editor?.view;
  if (!view) return 'missing';
  const selection = clampSelection(start, end, view.state.doc.length);
  const target = Math.min(selection.anchor, selection.head);
  view.dispatch({
    selection: EditorSelection.single(selection.anchor, selection.head),
    effects: EditorView.scrollIntoView(target, {
      y: 'center',
      yMargin: 96,
    }),
  });
  if (!readOnly) view.focus();
  notifySelection();
  window.requestAnimationFrame(() => emitScrollMetrics('searchJump'));
  log('KardLeafCM6Scroll', `search jump start=${selection.anchor} end=${selection.head}`);
  return 'ok';
}

function refreshImages() {
  const view = editor?.view;
  if (!view) return;
  view.dispatch({
    effects: [refreshBlockImagesEffect.of(null), refreshWikiImagesEffect.of(null)],
    annotations: Transaction.addToHistory.of(false),
  });
}

function parseImagePayload(payload: unknown) {
  let parsed = payload;
  if (typeof parsed === 'string') parsed = parsed ? JSON.parse(parsed) : [];
  return Array.isArray(parsed) ? parsed : [];
}

function setImageDataUris(payload: unknown) {
  const start = nowMs();
  imageDataUris.clear();

  for (const item of parseImagePayload(payload)) {
    const reference = normalizeImageReference(item?.reference);
    const rawReference = String(item?.reference ?? '').trim();
    const dataUri = String(item?.dataUri ?? '').trim();
    if (!reference || !/^data:image\//i.test(dataUri)) continue;
    imageDataUris.set(reference, dataUri);
    if (rawReference) imageDataUris.set(rawReference, dataUri);
    const decoded = decodeURIComponentSafe(reference);
    if (decoded !== reference) imageDataUris.set(decoded, dataUri);
  }

  refreshImages();
  log(
    'KardLeafCM6Image',
    `image map updated count=${imageDataUris.size} elapsed=${(nowMs() - start).toFixed(1)}ms`,
  );
  return `ok:${imageDataUris.size}`;
}

function installFallbackApi() {
  window.KardLeafEditor = {
    version: VERSION,
    setDocument(content: unknown, selectionStart: unknown, selectionEnd: unknown, fontSize: unknown, nextDarkMode: unknown, typographyStyle?: unknown) {
      fallbackText = String(content ?? '');
      if (Number.isFinite(Number(fontSize))) currentFontSize = Number(fontSize);
      applyTypographyStyle(typographyStyle);
      if (typeof nextDarkMode === 'boolean') setDocumentTheme(nextDarkMode);
      if (fallbackTextArea) fallbackTextArea.value = fallbackText;
      return 'fallback';
    },
    setContent(content: unknown) {
      fallbackText = String(content ?? '');
      if (fallbackTextArea) fallbackTextArea.value = fallbackText;
      return 'fallback';
    },
    setContentFromAndroid(content: unknown, selectionStart: unknown, selectionEnd: unknown) {
      return (this as Record<string, (...args: unknown[]) => unknown>).setDocument(
        content,
        selectionStart,
        selectionEnd,
        currentFontSize,
        darkMode,
      );
    },
    getText() {
      return fallbackTextArea ? fallbackTextArea.value : fallbackText;
    },
    getContent() {
      return (this as Record<string, () => unknown>).getText();
    },
    focusEditor() {
      fallbackTextArea?.focus();
      return 'fallback';
    },
    focus() {
      fallbackTextArea?.focus();
      return 'fallback';
    },
    setLivePreviewEnabled(enabled: unknown) {
      livePreviewEnabled = !!enabled;
      return 'fallback';
    },
    setDarkMode(enabled: unknown) {
      setDocumentTheme(!!enabled);
      return 'fallback';
    },
    setThemeColors(colors: unknown) {
      return applyThemeColors(colors);
    },
    setReadOnly(enabled: unknown) {
      readOnly = !!enabled;
      if (fallbackTextArea) fallbackTextArea.readOnly = readOnly;
      return 'fallback';
    },
    setImageDataUris() {
      return 'fallback';
    },
    setImageMap() {
      return 'fallback';
    },
    ensureCursorVisible() {
      return 'fallback';
    },
    setKeyboardInsetPx(px: unknown) {
      document.documentElement.style.setProperty(
        '--kardleaf-ime-safe-bottom',
        `${Math.max(0, Number(px) || 0)}px`,
      );
      return 'fallback';
    },
    fastScrollToRatio() {
      return 'fallback';
    },
    scrollToRatio() {
      return 'fallback';
    },
    selectRange(start: unknown, end: unknown) {
      if (fallbackTextArea) {
        const selection = clampSelection(start, end, fallbackTextArea.value.length);
        fallbackTextArea.selectionStart = selection.anchor;
        fallbackTextArea.selectionEnd = selection.head;
        fallbackTextArea.focus();
      }
      return 'fallback';
    },
    scrollToOffset(offset: unknown) {
      return (this as Record<string, (...args: unknown[]) => unknown>).selectRange(offset, offset);
    },
    getScrollMetrics() {
      return { scrollTop: 0, scrollHeight: 0, clientHeight: 0 };
    },
    getScrollInfo() {
      return { scrollTop: 0, scrollHeight: 0, clientHeight: 0 };
    },
    undo() {
      return 'fallback';
    },
    redo() {
      return 'fallback';
    },
    destroy() {
      return 'fallback';
    },
  };
}

function installEditorApi() {
  window.KardLeafEditor = {
    version: VERSION,
    setDocument(content: unknown, selectionStart: unknown, selectionEnd: unknown, fontSize: unknown, nextDarkMode: unknown, typographyStyle?: unknown) {
      if (Number.isFinite(Number(fontSize))) {
        currentFontSize = Math.max(12, Math.min(30, Number(fontSize)));
      }
      applyTypographyStyle(typographyStyle);
      if (typeof nextDarkMode === 'boolean') setDocumentTheme(nextDarkMode);
      updateRuntimeSettings();
      return dispatchFullDocument(content, selectionStart, selectionEnd, false);
    },
    setContent(content: unknown) {
      const length = String(content ?? '').length;
      return dispatchFullDocument(content, length, length, false);
    },
    setContentFromAndroid(content: unknown, selectionStart: unknown, selectionEnd: unknown) {
      return dispatchFullDocument(content, selectionStart, selectionEnd, false);
    },
    getText() {
      return editor ? editor.getText() : fallbackText;
    },
    getContent() {
      return editor ? editor.getText() : fallbackText;
    },
    focusEditor() {
      if (!editor) return 'missing';
      editor.focus();
      return 'ok';
    },
    focus() {
      if (!editor) return 'missing';
      editor.focus();
      return 'ok';
    },
    setLivePreviewEnabled(enabled: unknown) {
      livePreviewEnabled = !!enabled;
      updateRuntimeSettings();
      log('KardLeafCM6', `live preview enabled=${livePreviewEnabled}`);
      return 'ok';
    },
    setDarkMode(enabled: unknown) {
      setDocumentTheme(!!enabled);
      updateRuntimeSettings();
      return 'ok';
    },
    setThemeColors(colors: unknown) {
      return applyThemeColors(colors);
    },
    setReadOnly(enabled: unknown) {
      readOnly = !!enabled;
      updateRuntimeSettings();
      return 'ok';
    },
    setImageDataUris(payload: unknown) {
      return setImageDataUris(payload);
    },
    setImageMap(map: unknown) {
      imageDataUris.clear();
      if (Array.isArray(map)) {
        return setImageDataUris(map);
      }
      if (map && typeof map === 'object') {
        for (const [key, value] of Object.entries(map as Record<string, unknown>)) {
          const dataUri = String(value ?? '').trim();
          if (/^data:image\//i.test(dataUri)) {
            imageDataUris.set(normalizeImageReference(key), dataUri);
          }
        }
        refreshImages();
      }
      return `ok:${imageDataUris.size}`;
    },
    ensureCursorVisible(reason?: unknown) {
      return ensureEditorCursorVisible(reason);
    },
    setKeyboardInsetPx(px: unknown) {
      const layoutHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0);
      const cap = layoutHeight > 0 ? Math.round(layoutHeight * 0.62) : 0;
      const safePx = Math.min(Math.max(0, Number(px) || 0), cap);
      lastViewportKeyboardInsetPx = safePx;
      document.documentElement.style.setProperty(
        '--kardleaf-ime-safe-bottom',
        `${safePx}px`,
      );
      editor?.setScrollBottomMargin(computeScrollBottomMarginPx(safePx));
      return 'ok';
    },
    fastScrollToRatio(ratio: unknown) {
      const scroller = editor?.view.scrollDOM;
      if (!scroller) return 'missing';
      const safeRatio = Math.max(0, Math.min(1, Number(ratio) || 0));
      scroller.scrollTop = (scroller.scrollHeight - scroller.clientHeight) * safeRatio;
      emitScrollMetrics('fastScroll');
      log('KardLeafCM6Scroll', `fast scroll ratio=${safeRatio.toFixed(4)}`);
      return 'ok';
    },
    scrollToRatio(ratio: unknown) {
      return (window.KardLeafEditor?.fastScrollToRatio as (value: unknown) => unknown)?.(ratio);
    },
    getScrollMetrics() {
      const scroller = editor?.view.scrollDOM;
      if (!scroller) return { scrollTop: 0, scrollHeight: 0, clientHeight: 0 };
      return {
        scrollTop: Math.round(scroller.scrollTop || 0),
        scrollHeight: Math.round(scroller.scrollHeight || 0),
        clientHeight: Math.round(scroller.clientHeight || 0),
      };
    },
    getScrollInfo() {
      const getMetrics = window.KardLeafEditor?.getScrollMetrics as (() => unknown) | undefined;
      return getMetrics ? getMetrics() : { scrollTop: 0, scrollHeight: 0, clientHeight: 0 };
    },
    undo() {
      return editor?.execCommand('undo') ? 'ok' : 'empty';
    },
    redo() {
      return editor?.execCommand('redo') ? 'ok' : 'empty';
    },
    selectRange(start: unknown, end: unknown) {
      return selectEditorRangeAndReveal(start, end);
    },
    scrollToOffset(offset: unknown) {
      return selectEditorRangeAndReveal(offset, offset);
    },
    execCommand(name: unknown, ...args: unknown[]) {
      if (!editor || typeof name !== 'string') return 'missing';
      if (name === 'selectRange') return selectEditorRangeAndReveal(args[0], args[1]);
      if (name === 'scrollToOffset') return selectEditorRangeAndReveal(args[0], args[0]);
      return editor.execCommand(name, ...args) ?? 'ok';
    },
    destroy() {
      if (editor) {
        editor.destroy();
        editor = null;
      }
      return 'ok';
    },
  };
}

function installGlobalErrorHandlers() {
  window.addEventListener('error', (event) => {
    reportError(event.message || 'window.error', event.error || '');
  });
  window.addEventListener('unhandledrejection', (event) => {
    reportError('unhandledrejection', event.reason || '');
  });
}

function createFallbackTextArea(reason: string) {
  if (!root) return;
  root.textContent = '';
  fallbackTextArea = document.createElement('textarea');
  fallbackTextArea.className = 'kl-fallback';
  fallbackTextArea.value = fallbackText;
  fallbackTextArea.placeholder = 'CodeMirror failed to start. Plain text fallback is active.';
  fallbackTextArea.addEventListener('input', () => {
    fallbackText = fallbackTextArea?.value ?? '';
    callBridge('onContentPatch', [0, 0, '', fallbackText.length, fallbackText.length]);
  });
  root.appendChild(fallbackTextArea);
  setStatus(`CodeMirror failed to start: ${reason}`, true);
}

function main() {
  installGlobalErrorHandlers();
  installFallbackApi();
  installViewportKeyboardSync();
  injectStyle('kardleaf-katex-css', katexCss);
  setDocumentTheme(window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false);

  try {
    const start = nowMs();
    createEditorInstance();
    installEditorApi();
    log('KardLeafCM6Perf', `startup elapsed=${(nowMs() - start).toFixed(1)}ms`);
  } catch (error) {
    reportError('startup failed', error);
    createFallbackTextArea(error instanceof Error ? error.message : String(error));
  }
}

main();
