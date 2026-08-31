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
  type EditorTableContextMenuEvent,
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
import {
  mouseSelectingField,
  setMouseSelecting,
} from './vendor/swarmnote-editor-core/core';

type AndroidBridge = Record<string, (...args: unknown[]) => unknown>;

declare global {
  interface Window {
    KardLeafAndroid?: AndroidBridge;
    KardLeafEditor?: Record<string, unknown>;
  }
}

const VERSION = 'kardleaf-swarmnote-core-2026-06-27';
const SEARCH_ACTIVE_CLASS = 'kl-search-active';
const root = document.getElementById('editorRoot');
const statusEl = document.getElementById('status');
const imageDataUris = new Map<string, string>();
const refreshWikiImagesEffect = StateEffect.define<null>();

let editor: EditorControl | null = null;
let suppressBridgeDepth = 0;
const initialLivePreviewEnabled =
  new URLSearchParams(window.location.search).get('livePreview') !== 'false';
let livePreviewEnabled = initialLivePreviewEnabled;
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
let scrollMetricFrame = 0;
let selectionRevision = 0;
let pointerSelectionTrace: {
  startedAt: number;
  endedAt: number;
  x: number;
  y: number;
  position: number | null;
  maxMove: number;
  target: string;
  revisionAtStart: number;
} | null = null;
let titleHeader: HTMLDivElement | null = null;
let titleInput: HTMLInputElement | null = null;
let currentTitle = '';
let currentTitleHint = '';
let titleVisible = true;
let currentTitleFontSize = 22;
let suppressTitleBridge = false;
let tableToolbar: HTMLDivElement | null = null;

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

function domNodeTrace(node: Node | null) {
  if (!node) return 'none';
  if (node.nodeType === Node.TEXT_NODE) return `text(${node.parentElement?.tagName.toLowerCase() ?? 'none'})`;
  if (!(node instanceof Element)) return node.nodeName.toLowerCase();
  const row = node.closest<HTMLElement>('tr[data-row-idx]')?.dataset.rowIdx;
  const cell = node.closest<HTMLElement>('th[contenteditable],td[contenteditable]');
  return `${node.tagName.toLowerCase()}.${Array.from(node.classList).join('.') || '-'}${
    cell ? ` cell=${cell.tagName.toLowerCase()}:${row ?? 'header'}:${cell.dataset.colIdx ?? '?'}` : ''
  }`;
}

function domSelectionTrace() {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return 'dom=none';
  const caret = selection.getRangeAt(0).cloneRange();
  caret.collapse(false);
  const rect = caret.getBoundingClientRect();
  return `dom=${domNodeTrace(selection.anchorNode)}:${selection.anchorOffset}->${
    domNodeTrace(selection.focusNode)
  }:${selection.focusOffset} caret=${rect.left.toFixed(1)},${rect.top.toFixed(1)},${rect.bottom.toFixed(1)}`;
}

function scrollTrace(stage: string) {
  const view = editor?.view;
  if (!view) return;
  const scroller = view.scrollDOM;
  const active = document.activeElement;
  const activeRect = active instanceof Element ? active.getBoundingClientRect() : null;
  const head = view.state.selection.main.head;
  const cmRect = view.coordsAtPos(head);
  log(
    'KardLeafCM6Scroll',
    `${stage} top=${scroller.scrollTop.toFixed(1)} height=${scroller.clientHeight} ` +
      `active=${domNodeTrace(active)} activeRect=${activeRect ? `${activeRect.top.toFixed(1)}:${activeRect.bottom.toFixed(1)}` : 'none'} ` +
      `cmHead=${head} cmRect=${cmRect ? `${cmRect.top.toFixed(1)}:${cmRect.bottom.toFixed(1)}` : 'none'} ${domSelectionTrace()}`,
  );
}

function revealActiveEditorCaret() {
  const control = editor;
  if (!control) return 'missing';
  const active = document.activeElement;
  if (active instanceof HTMLElement && active !== control.view.contentDOM && active.isContentEditable) {
    active.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    return 'contenteditable';
  }
  if (!control.view.hasFocus) return 'ignored';
  control.view.dispatch({ scrollIntoView: true });
  return 'codemirror';
}

function prepareImeReveal(imeInsetPx: unknown) {
  const insetPx = Math.max(0, Number(imeInsetPx) || 0);
  if (insetPx <= 0) return 'hidden';
  const result = revealActiveEditorCaret();
  log(
    'KardLeafCM6Scroll',
    `ime viewport insetPx=${insetPx} result=${result} head=${editor?.view.state.selection.main.head ?? -1}`,
  );
  return result;
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

function notifyLocalImageClicked(rawSrc: string, from: number, to: number) {
  const normalized = normalizeImageReference(rawSrc);
  if (!normalized || isSafeExternalImageSrc(normalized)) return;
  log('KardLeafCM6Image', `image clicked src=${normalized} range=${from}..${to}`);
  callBridge('onDrawingImageClicked', [normalized, from, to]);
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

function setSearchActiveClass(active: boolean) {
  editor?.view.dom.classList.toggle(SEARCH_ACTIVE_CLASS, active);
}

function setTouchSelecting(view: EditorView, selecting: boolean) {
  if (view.state.field(mouseSelectingField, false) === selecting) return;
  view.dispatch({
    effects: setMouseSelecting.of(selecting),
    annotations: Transaction.addToHistory.of(false),
  });
}

function syncNativeSelectionState(view: EditorView) {
  const selection = window.getSelection();
  const selecting = !!selection &&
    !selection.isCollapsed &&
    view.contentDOM.contains(selection.anchorNode) &&
    view.contentDOM.contains(selection.focusNode);
  setTouchSelecting(view, selecting);
}

function editorPositionAtPoint(view: EditorView, x: number, y: number) {
  const range = document.caretRangeFromPoint(x, y);
  const rangeElement = range?.startContainer.nodeType === Node.TEXT_NODE
    ? range.startContainer.parentElement
    : range?.startContainer instanceof Element
      ? range.startContainer
      : null;
  if (range && rangeElement?.closest('.cm-line') && view.contentDOM.contains(range.startContainer)) {
    try {
      return view.posAtDOM(range.startContainer, range.startOffset);
    } catch {
      // Fall back to CodeMirror's geometry mapping for non-document widgets.
    }
  }
  return view.posAtCoords({ x, y }, false);
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

function scheduleScrollMetrics() {
  if (scrollMetricFrame) return;
  scrollMetricFrame = requestAnimationFrame(() => {
    scrollMetricFrame = 0;
    emitScrollMetrics('scroll');
  });
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
    scrollTrace('start');
  } else {
    const delta = timestamp - scrollSession.lastFrame;
    scrollSession.lastFrame = timestamp;
    scrollSession.frames += 1;
    if (delta >= 24) scrollSession.slowFrames += 1;
    if (delta > scrollSession.maxFrameMs) scrollSession.maxFrameMs = delta;
  }

  scheduleScrollMetrics();

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
    scrollTrace('settled');
    scrollSession = null;
  }, 180);
}

class WikiImageWidget extends WidgetType {
  constructor(
    private readonly rawSrc: string,
    private readonly alt: string,
    private readonly from: number,
    private readonly to: number,
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
      this.to === other.to &&
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
      if (!isSafeExternalImageSrc(this.rawSrc)) {
        event.preventDefault();
        event.stopPropagation();
        notifyLocalImageClicked(this.rawSrc, this.from, this.to);
        return;
      }
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
        line.to,
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


function onContentApplied() {
  const appliedLength = editor?.view.state.doc.length ?? fallbackText.length;
  log('KardLeafCM6Bridge', `content applied len=${appliedLength}`);
  callBridge('onContentApplied', [appliedLength]);
}

function createKardLeafBridgePlugin(): EditorPlugin {
  return {
    id: 'kardleaf.androidBridge',
    setup(ctx) {
      const handleNativeSelectionChange = () => {
        if (editor?.view) syncNativeSelectionState(editor.view);
      };
      document.addEventListener('selectionchange', handleNativeSelectionChange);
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
            selectionRevision += 1;
            const selection = update.state.selection.main;
            const pointer = pointerSelectionTrace;
            const pointerAge = pointer ? Math.round(nowMs() - pointer.startedAt) : -1;
            const pointerPos = pointer && pointerAge <= 1000
              ? pointer.position
              : null;
            const pointerSelection = pointerPos !== null && selection.head === pointerPos;
            log(
              'KardLeafCM6Input',
              `selection revision=${selectionRevision} from=${selection.from} to=${selection.to} head=${selection.head} ` +
                `pointer=${pointerSelection} pointerPos=${pointerPos ?? -1} pointerAge=${pointerAge}ms ` +
                `active=${domNodeTrace(document.activeElement)} ` +
                `${pointerSelection ? domSelectionTrace() : 'dom=not-sampled'}`,
            );
            if (pointerSelection) {
              pointerSelectionTrace = null;
            }
            notifySelection();
          }
          if (update.docChanged) notifyHistoryState();
        }),
        EditorView.domEventHandlers({
          touchstart(event) {
            if (editor?.view) setTouchSelecting(editor.view, true);
            callBridge('onUserInteraction');
            const touch = event.touches[0];
            const view = editor?.view;
            const selection = view?.state.selection.main;
            const target = event.target instanceof Element ? event.target : null;
            const editableTarget = target?.closest('[contenteditable]');
            const ignoredTarget = target?.closest('button,a,input,textarea,select') ||
              editableTarget && editableTarget !== editor?.view.contentDOM;
            pointerSelectionTrace = touch && !ignoredTarget
              ? {
                  startedAt: nowMs(),
                  endedAt: 0,
                  x: touch.clientX,
                  y: touch.clientY,
                  position: view ? editorPositionAtPoint(view, touch.clientX, touch.clientY) : null,
                  maxMove: 0,
                  target: domNodeTrace(event.target as Node | null),
                  revisionAtStart: selectionRevision,
                }
              : null;
            if (pointerSelectionTrace) {
              log(
                'KardLeafCM6Input',
                `touch start x=${pointerSelectionTrace.x.toFixed(1)} y=${pointerSelectionTrace.y.toFixed(1)} ` +
                  `target=${pointerSelectionTrace.target} active=${domNodeTrace(document.activeElement)} ` +
                  `cm=${selection?.from ?? -1}:${selection?.to ?? -1}:${selection?.head ?? -1} ` +
                  `revision=${selectionRevision} ${domSelectionTrace()}`,
              );
            }
            return false;
          },
          touchmove(event) {
            const pointer = pointerSelectionTrace;
            const touch = event.touches[0];
            if (pointer && touch) {
              pointer.maxMove = Math.max(
                pointer.maxMove,
                Math.abs(touch.clientX - pointer.x),
                Math.abs(touch.clientY - pointer.y),
              );
            }
            return false;
          },
          touchend() {
            const view = editor?.view;
            if (view) syncNativeSelectionState(view);
            const pointer = pointerSelectionTrace;
            if (pointer) {
              pointer.endedAt = nowMs();
              pointer.position = view
                ? editorPositionAtPoint(view, pointer.x, pointer.y)
                : pointer.position;
              const selection = view?.state.selection.main;
              log(
                'KardLeafCM6Input',
                `touch end elapsed=${Math.round(pointer.endedAt - pointer.startedAt)}ms target=${pointer.target} ` +
                  `active=${domNodeTrace(document.activeElement)} cm=${selection?.from ?? -1}:${selection?.to ?? -1}:${
                    selection?.head ?? -1
                  } revision=${selectionRevision} revisionAtStart=${pointer.revisionAtStart} ${domSelectionTrace()}`,
              );
              if (pointer.endedAt - pointer.startedAt <= 280 && pointer.maxMove <= 8) {
                window.setTimeout(() => {
                  const view = editor?.view;
                  if (!view || pointerSelectionTrace !== pointer || selectionRevision !== pointer.revisionAtStart) return;
                  const pos = pointer.position;
                  if (typeof pos !== 'number') return;
                  view.dispatch({
                    selection: EditorSelection.cursor(pos),
                    annotations: Transaction.addToHistory.of(false),
                  });
                  view.focus();
                  pointerSelectionTrace = null;
                  log(
                    'KardLeafCM6Input',
                    `selection recovered head=${pos} elapsed=${Math.round(nowMs() - pointer.startedAt)}ms target=${pointer.target}`,
                  );
                }, 80);
              }
            }
            return false;
          },
          touchcancel() {
            if (editor?.view) syncNativeSelectionState(editor.view);
            pointerSelectionTrace = null;
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

      return {
        dispose() {
          document.removeEventListener('selectionchange', handleNativeSelectionChange);
        },
      };
    },
  };
}

function buildPlugins(): EditorPlugin[] {
  return [
    ...(livePreviewEnabled
      ? [
          mathPlugin(),
          tablePlugin(),
          mermaidPlugin(),
          admonitionPlugin(),
          codeBlockPlugin({ mode: 'inline' }),
          blockImagePlugin({
            maxLoadAttempts: 1,
            onImageClick: notifyLocalImageClicked,
          }),
          rawHtmlPlugin(),
          createKardLeafWikiImagePlugin(),
        ]
      : []),
    smartPastePlugin(),
    slashCommandPlugin(),
    wikilinkPlugin(),
    selectionToolbarPlugin(),
    createKardLeafBridgePlugin(),
  ];
}

function hideTableToolbar() {
  tableToolbar?.remove();
  tableToolbar = null;
}

function tableToolbarButton(
  label: string,
  enabled: boolean,
  action: () => void,
): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = label;
  button.disabled = !enabled;
  button.addEventListener('pointerdown', (event) => {
    event.preventDefault();
    event.stopPropagation();
  });
  button.addEventListener('click', (event) => {
    event.preventDefault();
    event.stopPropagation();
    if (button.disabled) return;
    hideTableToolbar();
    action();
  });
  return button;
}

function showTableToolbar(event: EditorTableContextMenuEvent) {
  if (readOnly) {
    hideTableToolbar();
    return;
  }

  injectStyle(
    'kardleaf-table-toolbar-style',
    `
      .kl-table-toolbar {
        position: fixed;
        left: max(10px, env(safe-area-inset-left));
        right: max(10px, env(safe-area-inset-right));
        bottom: 8px;
        z-index: 2147483000;
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        gap: 5px;
        align-items: center;
        padding: 6px;
        border: 1px solid var(--kl-shell-border);
        border-radius: 12px;
        background: var(--kl-shell-bg);
        color: var(--kl-shell-fg);
        box-shadow: 0 6px 24px rgba(0, 0, 0, 0.18);
      }
      .kl-table-toolbar button {
        flex: 1 1 auto;
        min-width: 0;
        min-height: 36px;
        padding: 6px 6px;
        border: 1px solid var(--kl-shell-border);
        border-radius: 8px;
        background: var(--kl-shell-soft);
        color: var(--kl-shell-fg);
        font: inherit;
        font-size: 13px;
        white-space: nowrap;
        touch-action: manipulation;
      }
      .kl-table-toolbar button:disabled {
        opacity: 0.4;
      }
    `,
  );

  hideTableToolbar();
  const toolbar = document.createElement('div');
  toolbar.className = 'kl-table-toolbar';
  toolbar.setAttribute('role', 'toolbar');
  toolbar.setAttribute('aria-label', '表格编辑');

  const nextAlignment =
    event.alignment === null
      ? 'left'
      : event.alignment === 'left'
        ? 'center'
        : event.alignment === 'center'
          ? 'right'
          : null;
  const alignmentLabel =
    event.alignment === 'left'
      ? '左对齐'
      : event.alignment === 'center'
        ? '居中'
        : event.alignment === 'right'
          ? '右对齐'
          : '默认对齐';

  toolbar.append(
    tableToolbarButton('+ 行', true, () => event.actions.addRowAt(event.rowIdx, 'below')),
    tableToolbarButton('+ 列', true, () => event.actions.addColumnAt(event.colIdx, 'right')),
    tableToolbarButton('- 行', event.rowIdx >= 0, () => event.actions.deleteRow(event.rowIdx)),
    tableToolbarButton('- 列', event.colCount > 1, () => event.actions.deleteColumn(event.colIdx)),
    tableToolbarButton(alignmentLabel, true, () =>
      event.actions.setAlignment(event.colIdx, nextAlignment),
    ),
    tableToolbarButton('源码', true, () => event.actions.toggleSource()),
  );

  document.body.appendChild(toolbar);
  tableToolbar = toolbar;
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
      showTableToolbar(event);
      break;
    case EditorEventType.MermaidZoomRequest:
      log('KardLeafCM6', `mermaid zoom requested id=${event.id}`);
      break;
    default:
      break;
  }
}

document.addEventListener('focusin', (event) => {
  const target = event.target;
  if (
    !(target instanceof Element) ||
    !target.closest('.cm-table-widget, .kl-table-toolbar')
  ) {
    hideTableToolbar();
  }
});

document.addEventListener('touchmove', hideTableToolbar, { passive: true });

function titleHeaderHeightPx() {
  return Math.ceil(currentTitleFontSize * 1.5 + 8);
}

function applyTitleHeaderState() {
  document.documentElement.style.setProperty(
    '--kl-title-header-height',
    `${titleVisible ? titleHeaderHeightPx() : 0}px`,
  );
  document.documentElement.style.setProperty(
    '--kl-title-font-size',
    `${currentTitleFontSize}px`,
  );

  if (!titleHeader || !titleInput) return;
  titleHeader.hidden = !titleVisible;
  titleInput.placeholder = currentTitleHint;
  titleInput.readOnly = readOnly;
  if (titleInput.value !== currentTitle) {
    suppressTitleBridge = true;
    titleInput.value = currentTitle;
    suppressTitleBridge = false;
  }
}

function installTitleHeader() {
  const scroller = editor?.view.scrollDOM;
  if (!scroller) return;

  titleHeader?.remove();
  titleHeader = document.createElement('div');
  titleHeader.className = 'kl-editor-title-header';

  titleInput = document.createElement('input');
  titleInput.className = 'kl-editor-title-input';
  titleInput.type = 'text';
  titleInput.autocomplete = 'off';
  titleInput.spellcheck = false;
  titleInput.setAttribute('autocapitalize', 'sentences');
  titleInput.setAttribute('enterkeyhint', 'done');
  titleInput.addEventListener('input', () => {
    if (suppressTitleBridge || !titleInput) return;
    currentTitle = titleInput.value;
    callBridge('onTitleChanged', [currentTitle]);
  });
  titleInput.addEventListener('focus', () => callBridge('onUserInteraction'));
  titleInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      titleInput?.blur();
    }
  });

  titleHeader.appendChild(titleInput);
  scroller.insertBefore(titleHeader, scroller.firstChild);
  applyTitleHeaderState();
}

function setTitleState(
  title: unknown,
  hint: unknown,
  visible: unknown,
  fontSize: unknown,
) {
  currentTitle = String(title ?? '');
  currentTitleHint = String(hint ?? '');
  titleVisible = !!visible;
  if (Number.isFinite(Number(fontSize))) {
    currentTitleFontSize = Math.max(16, Math.min(34, Number(fontSize)));
  }
  applyTitleHeaderState();
  return 'ok';
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
      getWikilinkItems: async (query) => {
        const payload = callBridge('getWikilinkItems', [query]);
        if (typeof payload !== 'string' || payload.length === 0) return [];
        try {
          const parsed = JSON.parse(payload);
          return Array.isArray(parsed) ? parsed : [];
        } catch (error) {
          log('KardLeafWikiLinkTrace', `candidate payload parse failed queryLen=${query.length}`);
          return [];
        }
      },
      getSelectionToolbarActions: () => [],
    },
    plugins: buildPlugins(),
    onEvent: handleEditorEvent,
  });
  editor.view.scrollDOM.addEventListener('scroll', handleScroll, { passive: true });
  installTitleHeader();
  notifyHistoryState(true);
  notifySelection();
  setStatus('');
  log(
    'KardLeafCM6',
    `editor ready version=${VERSION} livePreview=${livePreviewEnabled} renderPlugins=${livePreviewEnabled}`,
  );
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
    reportError(`setContent failed len=${text.length}`, error);
    log(
      'KardLeafCM6Perf',
      `setContent failed len=${text.length} elapsed=${(nowMs() - start).toFixed(1)}ms`,
    );
    return 'error';
  }
}

function replaceRangeFromAndroid(
  from: unknown,
  to: unknown,
  replacement: unknown,
  selectionStart: unknown,
  selectionEnd: unknown,
) {
  const view = editor?.view;
  if (!view) return 'missing';
  const length = view.state.doc.length;
  const start = Math.max(0, Math.min(length, Number(from) || 0));
  const end = Math.max(start, Math.min(length, Number(to) || start));
  const text = String(replacement ?? '');
  const selection = clampSelection(selectionStart, selectionEnd, length - (end - start) + text.length);
  view.dispatch({
    changes: { from: start, to: end, insert: text },
    selection: EditorSelection.single(selection.anchor, selection.head),
    annotations: Transaction.addToHistory.of(true),
  });
  notifyHistoryState(true);
  notifySelection();
  log('KardLeafEditorUndo', `action=replace kernel=CodeMirror range=${start}..${end} insertLen=${text.length} canUndo=${undoDepth(view.state) > 0} canRedo=${redoDepth(view.state) > 0}`);
  return 'ok';
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

function clearAndroidSearchState(source: unknown = 'android') {
  if (!editor) return 'missing';
  const reason = String(source ?? 'android');
  try {
    const view = editor.view;
    const stateBefore = editor.getSearchState();
    const selectionBefore = view.state.selection.main;
    const collapseSelection =
      Boolean(stateBefore?.query) &&
      selectionBefore.from !== selectionBefore.to &&
      stateBefore?.activeMatchIndex != null;
    editor.clearSearch(reason);
    setSearchActiveClass(false);
    if (collapseSelection) {
      view.dispatch({ selection: EditorSelection.cursor(selectionBefore.head) });
      notifySelection();
    }
    return 'ok';
  } catch (error) {
    reportError(`clear search failed source=${reason}`, error);
    return 'error';
  }
}

function setAndroidSearchState(
  query: unknown,
  useRegex: unknown,
  matchCase: unknown,
  activeMatchIndex: unknown,
  totalMatches: unknown,
  source: unknown = 'android',
) {
  if (!editor) return 'missing';
  const queryText = String(query ?? '');
  const reason = String(source ?? 'android');
  if (queryText.length === 0) return clearAndroidSearchState(reason);
  const parsedActive = Number(activeMatchIndex);
  const parsedTotal = Number(totalMatches);
  try {
    editor.setSearchState(
      {
        query: queryText,
        replaceQuery: '',
        caseSensitive: !!matchCase,
        wholeWord: false,
        regexp: !!useRegex,
        // The Compose search bar owns the visible UI. CodeMirror's panel is
        // kept open internally because its search highlighter requires it.
        isOpen: true,
        activeMatchIndex: Number.isFinite(parsedActive) ? Math.max(0, Math.floor(parsedActive)) : null,
        totalMatches: Number.isFinite(parsedTotal) ? Math.max(0, Math.floor(parsedTotal)) : 0,
      },
      reason,
    );
    setSearchActiveClass(true);
    return 'ok';
  } catch (error) {
    reportError(`set search failed source=${reason} queryLen=${queryText.length}`, error);
    return 'error';
  }
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
    prepareImeReveal() {
      return 'missing';
    },
    setTitleState(title: unknown, hint: unknown, visible: unknown, fontSize: unknown) {
      return setTitleState(title, hint, visible, fontSize);
    },
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
    replaceRangeFromAndroid(_from: unknown, _to: unknown, replacement: unknown, selectionStart: unknown, selectionEnd: unknown) {
      return (this as Record<string, (...args: unknown[]) => unknown>).setContentFromAndroid(replacement, selectionStart, selectionEnd);
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
      if (!livePreviewEnabled) hideTableToolbar();
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
      if (readOnly) hideTableToolbar();
      if (fallbackTextArea) fallbackTextArea.readOnly = readOnly;
      applyTitleHeaderState();
      return 'fallback';
    },
    setImageDataUris() {
      return 'fallback';
    },
    setImageMap() {
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
    setSearchState() {
      return 'fallback';
    },
    clearSearchState() {
      return 'fallback';
    },
    scrollToOffset(offset: unknown) {
      return (this as Record<string, (...args: unknown[]) => unknown>).selectRange(offset, offset);
    },
    getScrollMetrics() {
      return { scrollTop: 0, scrollHeight: 0, clientHeight: 0 };
    },
    getViewportAnchor() {
      return {
        offset: fallbackTextArea?.selectionStart ?? 0,
        viewportFraction: 0.5,
        edge: 'CENTER',
      };
    },
    getViewportAnchorOffset() {
      return fallbackTextArea?.selectionStart ?? 0;
    },
    scrollViewportToAnchor(anchor: unknown) {
      const data = anchor && typeof anchor === 'object' ? anchor as Record<string, unknown> : {};
      return (this as Record<string, (...args: unknown[]) => unknown>).scrollToOffset(data.offset);
    },
    scrollViewportToOffset(offset: unknown) {
      return (this as Record<string, (...args: unknown[]) => unknown>).scrollToOffset(offset);
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
    prepareImeReveal(imeInsetPx: unknown) {
      return prepareImeReveal(imeInsetPx);
    },
    setTitleState(title: unknown, hint: unknown, visible: unknown, fontSize: unknown) {
      return setTitleState(title, hint, visible, fontSize);
    },
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
    replaceRangeFromAndroid(from: unknown, to: unknown, replacement: unknown, selectionStart: unknown, selectionEnd: unknown) {
      return replaceRangeFromAndroid(from, to, replacement, selectionStart, selectionEnd);
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
      const requested = !!enabled;
      if (requested !== initialLivePreviewEnabled) {
        log(
          'KardLeafCM6',
          `live preview requested=${requested} initial=${initialLivePreviewEnabled} action=reload_required`,
        );
        return 'reload_required';
      }
      livePreviewEnabled = requested;
      if (!livePreviewEnabled) hideTableToolbar();
      updateRuntimeSettings();
      log(
        'KardLeafCM6',
        `live preview requested=${requested} initial=${initialLivePreviewEnabled} effective=${livePreviewEnabled}`,
      );
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
      if (readOnly) hideTableToolbar();
      updateRuntimeSettings();
      applyTitleHeaderState();
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
    getViewportAnchor() {
      const view = editor?.view;
      if (!view) return null;
      const scroller = view.scrollDOM;
      const maxScroll = Math.max(0, scroller.scrollHeight - scroller.clientHeight);
      const edge = scroller.scrollTop <= 1
        ? 'START'
        : scroller.scrollTop >= maxScroll - 1
          ? 'END'
          : 'CENTER';
      const rect = scroller.getBoundingClientRect();
      const centerY = rect.top + rect.height / 2;
      const offset = edge === 'START'
        ? 0
        : edge === 'END'
          ? view.state.doc.length
          : view.lineBlockAtHeight(centerY - view.documentTop).from;
      const anchor = {
        offset: Math.max(0, Math.min(view.state.doc.length, offset)),
        viewportFraction: 0.5,
        edge,
        scrollTop: Math.round(scroller.scrollTop),
        maxScroll: Math.round(maxScroll),
      };
      log(
        'KardLeafCM6Scroll',
        `viewport anchor offset=${anchor.offset} edge=${edge} scrollTop=${anchor.scrollTop} maxScroll=${anchor.maxScroll}`,
      );
      return anchor;
    },
    getViewportAnchorOffset() {
      const anchor = (window.KardLeafEditor?.getViewportAnchor as (() => { offset?: number } | null) | undefined)?.();
      return anchor?.offset ?? -1;
    },
    scrollViewportToAnchor(rawAnchor: unknown) {
      const view = editor?.view;
      if (!view) return 'missing';
      const anchor = rawAnchor && typeof rawAnchor === 'object'
        ? rawAnchor as Record<string, unknown>
        : {};
      const scroller = view.scrollDOM;
      const edge = String(anchor.edge ?? 'CENTER').toUpperCase();
      const target = Math.max(0, Math.min(view.state.doc.length, Number(anchor.offset) || 0));
      const maxScroll = Math.max(0, scroller.scrollHeight - scroller.clientHeight);
      if (edge === 'START') {
        scroller.scrollTop = 0;
      } else if (edge === 'END') {
        scroller.scrollTop = maxScroll;
      } else {
        const fraction = Math.max(0, Math.min(1, Number(anchor.viewportFraction) || 0.5));
        const block = view.lineBlockAt(target);
        const desiredY = scroller.getBoundingClientRect().top + scroller.clientHeight * fraction;
        scroller.scrollTop = Math.max(
          0,
          Math.min(maxScroll, scroller.scrollTop + view.documentTop + block.top - desiredY),
        );
      }
      window.requestAnimationFrame(() => emitScrollMetrics('modeSwitchAnchor'));
      const result = `ok:${edge}:${target}:${Math.round(scroller.scrollTop)}`;
      log('KardLeafCM6Scroll', `viewport anchor apply ${result}`);
      return result;
    },
    scrollViewportToOffset(offset: unknown) {
      const view = editor?.view;
      if (!view) return 'missing';
      const target = Math.max(0, Math.min(view.state.doc.length, Number(offset) || 0));
      return (window.KardLeafEditor?.scrollViewportToAnchor as ((anchor: unknown) => unknown) | undefined)?.({
        offset: target,
        viewportFraction: 0.5,
        edge: 'CENTER',
      }) ?? 'missing';
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
    setSearchState(
      query: unknown,
      useRegex: unknown,
      matchCase: unknown,
      activeMatchIndex: unknown,
      totalMatches: unknown,
    ) {
      return setAndroidSearchState(
        query,
        useRegex,
        matchCase,
        activeMatchIndex,
        totalMatches,
        'android-api',
      );
    },
    clearSearchState(source?: unknown) {
      return clearAndroidSearchState(source ?? 'android-api');
    },
    scrollToOffset(offset: unknown) {
      return selectEditorRangeAndReveal(offset, offset);
    },
    execCommand(name: unknown, ...args: unknown[]) {
      if (!editor || typeof name !== 'string') return 'missing';
      if (name === 'selectRange') return selectEditorRangeAndReveal(args[0], args[1]);
      if (name === 'scrollToOffset') return selectEditorRangeAndReveal(args[0], args[0]);
      if (name === 'setSearchState') {
        return setAndroidSearchState(args[0], args[1], args[2], args[3], args[4], 'android-execCommand');
      }
      if (name === 'clearSearchState') return clearAndroidSearchState(args[0] ?? 'android-execCommand');
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
  window.addEventListener('kardleaf-user-caret', revealActiveEditorCaret);
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
