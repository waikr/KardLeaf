import katexCss from 'katex/dist/katex.css';
import { isolateHistory, redoDepth, undoDepth } from '@codemirror/commands';
import { getSearchQuery, openSearchPanel, replaceAll as replaceAllSearch, replaceNext, setSearchQuery } from '@codemirror/search';
import { androidSearchQuery, currentSearchHighlight, regexSearchChanges, searchMatches, searchSummary } from './search';
import { forceParsing, syntaxTree } from '@codemirror/language';
import {
  EditorState,
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
  type ViewUpdate,
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
import { markTextToolbarPlugin } from './marktext/toolbar';
import { cycleInlineStyleAtCursor, inlineColorPlugin, setInlineStyleAtCursor } from './marktext/inlineStyleSpans';
import { isColorHtml, scanColorDocument, type ColorSpan, type InlineStyleProperty } from './marktext/inlineStyles';
import { slashCommandPlugin } from './vendor/swarmnote-editor-core/plugins/interactions/slash';
import { wikilinkPlugin } from './vendor/swarmnote-editor-core/plugins/interactions/wikilink';
import {
  mouseSelectingField,
  hasNonEmptyNativeSelection,
  nativeSelectionSettledEvent,
  nativeSelectionField,
  runWhenNativeSelectionSettled,
  setNativeSelectionActive,
  setMouseSelecting,
  setSourceRevealEnabled,
} from './vendor/swarmnote-editor-core/core';
import { renderingSelection, selectionRenderingFrozen } from './vendor/swarmnote-editor-core/core/mouseSelecting';
import {
  checkUpdateAction,
  shouldRebuildBlockDecorations,
} from './vendor/swarmnote-editor-core/core/pluginUpdateHelper';

type AndroidBridge = Record<string, (...args: unknown[]) => unknown>;
type CodeMirrorFocusDiagnostics = {
  cmAndroidFocusWorkaroundCandidateCount: number;
  cmAndroidFocusWorkaroundExecutedCount: number;
  cmAndroidFocusWorkaroundSkippedForNativeRangeCount: number;
};

declare global {
  interface Window {
    KardLeafAndroid?: AndroidBridge;
    KardLeafEditor?: Record<string, unknown>;
    __KardLeafCodeMirrorSelectionDiagnostics?: CodeMirrorFocusDiagnostics;
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
const bootstrapToken = new URLSearchParams(window.location.search).get('bootstrap') ?? '';
let documentToken = '';
let initialRenderRequest = '';
let initialSelectionRequested = false;
let sourceRevealEnabled = false;
let lastRuntimeSettings = '';
let livePreviewEnabled = initialLivePreviewEnabled;
let readOnly = false;
let currentFontSize = 16;
let currentLineHeight = 1.55;
let currentLetterSpacing = 0;
let currentParagraphSpacing = 8;
let currentFontFamily = initialLivePreviewEnabled
  ? 'system'
  : 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
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
let lastSelectionViewTraceAt = 0;
let lastNativeSelectionTraceAt = 0;
let lastSelectionGeometryTraceAt = 0;
let nativeSelectionGestureSerial = 0;
let nativeSelectionGestureActive = false;
let nativeSelectionDiagnosticsStart: CodeMirrorFocusDiagnostics | null = null;
let lastNativeSelectionGestureTraceAt = 0;
let lastNativeSelectionGestureScrollTraceAt = 0;
let lastNativeSelectionGestureScrollTop = -1;
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
let tableToolbar: EditorTableContextMenuEvent | null = null;
let styleDocument: EditorState['doc'] | null = null;
let styleSpans: ColorSpan[] = [];
let lastContextToolbarState = '';
let contextToolbarHideTimer: ReturnType<typeof setTimeout> | undefined;
let inlineColorPicker: HTMLInputElement | null = null;

function notifyContextToolbar() {
  const view = editor?.view;
  let kind = '';
  if (view && livePreviewEnabled && !readOnly) {
    if (tableToolbar) kind = 'table';
    else if (view.hasFocus && view.state.selection.main.empty) {
      if (styleDocument !== view.state.doc) {
        styleDocument = view.state.doc;
        styleSpans = scanColorDocument(view.state.doc.toString()).spans;
      }
      const cursor = view.state.selection.main.head;
      if (styleSpans.some(span => Object.keys(span.colors).length > 0 &&
          span.openTo < span.closeFrom && cursor >= span.openTo && cursor <= span.closeFrom)) kind = 'style';
    }
  }
  const canDeleteRow = kind === 'table' && tableToolbar!.rowIdx >= 0;
  const canDeleteColumn = kind === 'table' && tableToolbar!.colCount > 1;
  const state = `${kind}:${canDeleteRow}:${canDeleteColumn}`;
  clearTimeout(contextToolbarHideTimer);
  if (state === lastContextToolbarState) return;
  const publish = () => {
    lastContextToolbarState = state;
    callBridge('onContextToolbarChanged', [kind, canDeleteRow, canDeleteColumn]);
  };
  // Widget replacement restores its cell on the next frame; do not animate that brief gap.
  if (!kind) contextToolbarHideTimer = setTimeout(publish, 80);
  else publish();
}

function openInlineColorPicker(property: string) {
  if (property !== 'color' && property !== 'backgroundColor' || !editor?.view || readOnly) return 'missing';
  inlineColorPicker?.remove();
  const picker = document.createElement('input');
  picker.type = 'color';
  picker.value = property === 'backgroundColor' ? '#ffffff' : '#212121';
  picker.style.position = 'fixed';
  picker.style.width = '1px';
  picker.style.height = '1px';
  picker.style.opacity = '0';
  picker.style.pointerEvents = 'none';
  picker.addEventListener('change', () => {
    setInlineStyleAtCursor(editor!.view, property as InlineStyleProperty, picker.value);
    picker.remove();
    if (inlineColorPicker === picker) inlineColorPicker = null;
  }, { once: true });
  document.body.appendChild(picker);
  inlineColorPicker = picker;
  try {
    if (picker.showPicker) picker.showPicker();
    else picker.click();
  } catch {
    picker.click();
  }
  return 'ok';
}

function nowMs() {
  return typeof performance !== 'undefined' && performance.now
    ? performance.now()
    : Date.now();
}

function bridge(): AndroidBridge | null {
  return window.KardLeafAndroid ?? null;
}

function enableSourceReveal(reason: string) {
  pointerSelectionTrace = null;
  if (sourceRevealEnabled) return;
  const view = editor?.view;
  if (!view) return;
  sourceRevealEnabled = true;
  view.dispatch({
    effects: setSourceRevealEnabled.of(true),
    annotations: Transaction.addToHistory.of(false),
  });
  log(
    'KardLeafCM6Input',
    `source reveal enabled reason=${reason} selection=${view.state.selection.main.from}:${view.state.selection.main.to}`,
  );
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

function domSelectionRangeTrace() {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return 'dom=none';
  return `dom=${domNodeTrace(selection.anchorNode)}:${selection.anchorOffset}->${
    domNodeTrace(selection.focusNode)
  }:${selection.focusOffset}`;
}

function readCodeMirrorFocusDiagnostics(): CodeMirrorFocusDiagnostics | null {
  const diagnostics = window.__KardLeafCodeMirrorSelectionDiagnostics;
  if (!diagnostics) return null;
  return {
    cmAndroidFocusWorkaroundCandidateCount: Number(diagnostics.cmAndroidFocusWorkaroundCandidateCount) || 0,
    cmAndroidFocusWorkaroundExecutedCount: Number(diagnostics.cmAndroidFocusWorkaroundExecutedCount) || 0,
    cmAndroidFocusWorkaroundSkippedForNativeRangeCount:
      Number(diagnostics.cmAndroidFocusWorkaroundSkippedForNativeRangeCount) || 0,
  };
}

function traceNativeSelectionGesture(view: EditorView, source: string) {
  const active = hasNonEmptyNativeSelection(view);
  const now = nowMs();
  if (!active) {
    if (nativeSelectionGestureActive) {
      nativeSelectionGestureActive = false;
      lastNativeSelectionGestureScrollTop = -1;
      const diagnostics = readCodeMirrorFocusDiagnostics();
      if (diagnostics) {
        const start = nativeSelectionDiagnosticsStart;
        log(
          'KardLeafCM6Gesture',
          `focusWorkaround ` +
            `cmAndroidFocusWorkaroundCandidateCount=${diagnostics.cmAndroidFocusWorkaroundCandidateCount -
              (start?.cmAndroidFocusWorkaroundCandidateCount ?? 0)} ` +
            `cmAndroidFocusWorkaroundExecutedCount=${diagnostics.cmAndroidFocusWorkaroundExecutedCount -
              (start?.cmAndroidFocusWorkaroundExecutedCount ?? 0)} ` +
            `cmAndroidFocusWorkaroundSkippedForNativeRangeCount=${diagnostics.cmAndroidFocusWorkaroundSkippedForNativeRangeCount -
              (start?.cmAndroidFocusWorkaroundSkippedForNativeRangeCount ?? 0)}`,
        );
      }
      nativeSelectionDiagnosticsStart = null;
      log(
        'KardLeafCM6Gesture',
        `selectionGesture end source=${source} serial=${nativeSelectionGestureSerial} ` +
          `cm=${view.state.selection.main.from}-${view.state.selection.main.to} scrollTop=${view.scrollDOM.scrollTop.toFixed(1)}`,
      );
    }
    return false;
  }

  if (!nativeSelectionGestureActive) {
    nativeSelectionGestureActive = true;
    nativeSelectionGestureSerial += 1;
    nativeSelectionDiagnosticsStart = readCodeMirrorFocusDiagnostics();
    lastNativeSelectionGestureTraceAt = 0;
    log(
      'KardLeafCM6Gesture',
      `selectionGesture start source=${source} serial=${nativeSelectionGestureSerial} ` +
        `cm=${view.state.selection.main.from}-${view.state.selection.main.to} ` +
        `scrollTop=${view.scrollDOM.scrollTop.toFixed(1)} ${domSelectionRangeTrace()}`,
    );
  }

  if (now - lastNativeSelectionGestureTraceAt >= 48) {
    lastNativeSelectionGestureTraceAt = now;
    log(
      'KardLeafCM6Gesture',
      `selectionGesture move source=${source} serial=${nativeSelectionGestureSerial} ` +
        `cm=${view.state.selection.main.from}-${view.state.selection.main.to} ` +
        `scrollTop=${view.scrollDOM.scrollTop.toFixed(1)} ${domSelectionRangeTrace()}`,
    );
  }
  return true;
}

function traceNativeSelectionGestureScroll(view: EditorView | null, now: number) {
  if (!view || !hasNonEmptyNativeSelection(view)) {
    lastNativeSelectionGestureScrollTop = -1;
    return;
  }
  if (!nativeSelectionGestureActive) traceNativeSelectionGesture(view, 'scroll');
  if (now - lastNativeSelectionGestureScrollTraceAt < 48) return;
  lastNativeSelectionGestureScrollTraceAt = now;
  const top = view.scrollDOM.scrollTop;
  const delta = lastNativeSelectionGestureScrollTop < 0 ? 0 : top - lastNativeSelectionGestureScrollTop;
  lastNativeSelectionGestureScrollTop = top;
  log(
    'KardLeafCM6Gesture',
    `selectionGesture scroll serial=${nativeSelectionGestureSerial} top=${top.toFixed(1)} ` +
      `delta=${delta.toFixed(1)} cmHead=${view.state.selection.main.head}`,
  );
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
      `scrollHeight=${scroller.scrollHeight} revision=${selectionRevision} ` +
      `frozen=${selectionRenderingFrozen(view.state)} native=${view.state.field(nativeSelectionField, false)} ` +
      `renderHead=${renderingSelection(view.state).main.head} ` +
      `focusResets=${readCodeMirrorFocusDiagnostics()?.cmAndroidFocusWorkaroundExecutedCount ?? 0} ` +
      `active=${domNodeTrace(active)} activeRect=${activeRect ? `${activeRect.top.toFixed(1)}:${activeRect.bottom.toFixed(1)}` : 'none'} ` +
      `cmHead=${head} cmRect=${cmRect ? `${cmRect.top.toFixed(1)}:${cmRect.bottom.toFixed(1)}` : 'none'} ${domSelectionTrace()}`,
  );
}

function revealActiveEditorCaret() {
  const control = editor;
  if (!control) return 'missing';
  if (hasNonEmptyNativeSelection(control.view)) return 'native-selection';
  const active = document.activeElement;
  if (active instanceof HTMLElement && active !== control.view.contentDOM && active.isContentEditable) {
    active.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    return 'contenteditable';
  }
  if (!control.view.hasFocus) return 'ignored';
  // A long press may release before Chromium publishes its new selection.
  // Never reveal the old caret just because the IME viewport has changed.
  if (pointerSelectionTrace && selectionRevision === pointerSelectionTrace.revisionAtStart) return 'pointer-pending';
  control.view.dispatch({ scrollIntoView: true });
  return 'codemirror';
}

function prepareImeReveal(imeInsetPx: unknown) {
  const insetPx = Math.max(0, Number(imeInsetPx) || 0);
  if (insetPx <= 0) return 'hidden';
  const view = editor?.view;
  scrollTrace(`ime reveal before insetPx=${insetPx}`);
  const result = revealActiveEditorCaret();
  log(
    'KardLeafCM6Scroll',
    `ime viewport insetPx=${insetPx} result=${result} head=${editor?.view.state.selection.main.head ?? -1} ` +
      `revision=${selectionRevision} pointerRevision=${pointerSelectionTrace?.revisionAtStart ?? -1} ` +
      `pointerAge=${pointerSelectionTrace ? Math.round(nowMs() - pointerSelectionTrace.startedAt) : -1}ms`,
  );
  // Diagnostic only: observe the existing native/CM scroll on the next frame.
  requestAnimationFrame(() => {
    if (view && editor?.view === view) scrollTrace(`ime reveal frame result=${result}`);
  });
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
  // Match PreviewWebView's single family name (including escaping), not a CSS font list.
  if (livePreviewEnabled) {
    return !value || value.toLowerCase() === 'system' ? 'sans-serif' : `"${value.replace(/"/g, '\\"')}"`;
  }
  if (!value || value === 'system') {
    return 'sans-serif';
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

// While a native selection gesture is active, Chromium fires selectionSet for
// every handle move. Streaming each one over JNI only adds main-thread hops on
// the Kotlin side, so coalesce to one trailing call per 120ms window. The
// trailing timer guarantees the final range still reaches the Kotlin side.
let selectionNotifyTimer: ReturnType<typeof setTimeout> | null = null;

function flushSelectionNotification() {
  if (selectionNotifyTimer !== null) {
    clearTimeout(selectionNotifyTimer);
    selectionNotifyTimer = null;
  }
  const selection = editor?.view.state.selection.main;
  if (selection) callBridge('onSelectionChanged', [selection.from, selection.to]);
}

function notifySelection() {
  const view = editor?.view;
  if (!view) return;
  if (selectionRenderingFrozen(view.state)) {
    if (selectionNotifyTimer === null) {
      selectionNotifyTimer = setTimeout(() => {
        selectionNotifyTimer = null;
        const latest = editor?.view.state.selection.main;
        if (latest) callBridge('onSelectionChanged', [latest.from, latest.to]);
      }, 120);
    }
    return;
  }
  flushSelectionNotification();
}

function setSearchActiveClass(active: boolean) {
  editor?.view.dom.classList.toggle(SEARCH_ACTIVE_CLASS, active);
}

function setTouchSelecting(view: EditorView, selecting: boolean) {
  const native = hasNonEmptyNativeSelection(view);
  const mouseSelecting = view.state.field(mouseSelectingField, false);
  const nativeSelecting = view.state.field(nativeSelectionField, false);
  if (mouseSelecting === selecting && nativeSelecting === native) return;
  const effects = [] as ReturnType<typeof setMouseSelecting.of>[];
  if (mouseSelecting !== selecting) effects.push(setMouseSelecting.of(selecting));
  if (nativeSelecting !== native) effects.push(setNativeSelectionActive.of(native));
  view.dispatch({
    effects,
    annotations: Transaction.addToHistory.of(false),
  });
}

function syncNativeSelectionState(view: EditorView, touching = false) {
  const native = hasNonEmptyNativeSelection(view);
  const selecting = touching || native;
  setTouchSelecting(view, selecting);
}

function traceSelectionViewUpdate(update: ViewUpdate) {
  const frozen = selectionRenderingFrozen(update.state);
  const gestureChanged = frozen !== selectionRenderingFrozen(update.startState) ||
    update.state.field(nativeSelectionField, false) !== update.startState.field(nativeSelectionField, false);
  if (!gestureChanged && (!frozen || (!update.selectionSet && !update.viewportChanged))) return;
  const now = nowMs();
  // Viewport updates can arrive for every auto-scroll tick while a native
  // handle is moving. Keep this diagnostic off the layout-critical path.
  if (!gestureChanged && now - lastSelectionViewTraceAt < 120) return;
  lastSelectionViewTraceAt = now;
  const selection = update.state.selection.main;
  const renderingChanged = !renderingSelection(update.startState).eq(renderingSelection(update.state));
  const geometry = now - lastSelectionGeometryTraceAt >= 250;
  if (geometry) lastSelectionGeometryTraceAt = now;
  log(
    'KardLeafCM6SelectionTrace',
    `viewUpdate action=${checkUpdateAction(update)} selection=${selection.from}-${selection.to} head=${selection.head} ` +
      `frozenStart=${selectionRenderingFrozen(update.startState)} frozenEnd=${frozen} ` +
      `native=${update.state.field(nativeSelectionField, false)} renderHead=${renderingSelection(update.state).main.head} ` +
      `scrollTop=${update.view.scrollDOM.scrollTop.toFixed(1)} ` +
      `selectionSet=${update.selectionSet} viewportChanged=${update.viewportChanged} ` +
      `docChanged=${update.docChanged} focusChanged=${update.focusChanged} renderingChanged=${renderingChanged} ` +
      `transactions=${update.transactions.length} active=${domNodeTrace(document.activeElement)} ` +
      `${geometry ? domSelectionTrace() : 'dom=not-sampled'}`,
  );
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
  traceNativeSelectionGestureScroll(editor?.view ?? null, timestamp);
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
    // These are scroll-event intervals, not rendered frames. Exclude the
    // 180ms settle timer from their average and label them accordingly in Android.
    const activeElapsed = scrollSession.lastFrame - scrollSession.start;
    const avg = scrollSession.frames > 0 ? activeElapsed / scrollSession.frames : 0;
    emitScrollMetrics(
      'settled',
      elapsed,
      scrollSession.frames,
      scrollSession.slowFrames,
      scrollSession.maxFrameMs,
      avg,
      scrollSession.frames > 0 && scrollSession.maxFrameMs < 50 &&
        scrollSession.slowFrames <= Math.floor(scrollSession.frames * 0.2),
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
    let disposed = false;
    let selectionCleanup = () => {};

    const deferDomUpdate = (action: () => void) => {
      selectionCleanup();
      if (disposed || !frame.isConnected) return;
      selectionCleanup = runWhenNativeSelectionSettled(view, container, () => {
        if (!disposed && frame.isConnected) action();
      });
    };

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
      deferDomUpdate(setFallback);
    };

    Promise.resolve(this.resolver(this.rawSrc))
      .then((resolved) => {
        deferDomUpdate(() => {
          if (resolved) img.src = resolved;
          else setFallback();
        });
      })
      .catch(() => {
        deferDomUpdate(setFallback);
    });

    frame.appendChild(img);
    container.appendChild(frame);
    wikiImageCleanups.set(container, () => {
      disposed = true;
      selectionCleanup();
      img.onerror = null;
    });

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

  destroy(dom: HTMLElement) {
    wikiImageCleanups.get(dom)?.();
    wikiImageCleanups.delete(dom);
  }

  ignoreEvent(event: Event) {
    return event.type !== 'mousedown';
  }
}

const wikiImageCleanups = new WeakMap<HTMLElement, () => void>();

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
    const head = renderingSelection(state).main.head;
    const cursorLine = state.doc.lineAt(head).number;

    for (let lineNumber = 1; lineNumber <= state.doc.lines; lineNumber += 1) {
      const line = state.doc.line(lineNumber);
      const parsed = parseWikiImageLine(line.text);
      if (!parsed) continue;

      const sourceVisible = !selectionRenderingFrozen(state) && cursorLine === line.number;
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
      if (shouldRebuildBlockDecorations(tr) || hasRefresh && !selectionRenderingFrozen(tr.state)) {
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


function onContentApplied(appliedToken = documentToken) {
  const appliedLength = editor?.view.state.doc.length ?? fallbackText.length;
  log('KardLeafCM6Bridge', `content applied len=${appliedLength}`);
  callBridge('onContentApplied', [bootstrapToken, appliedToken, appliedLength]);
}

async function prepareInitialRender(request: string, anchor: unknown) {
  const view = editor?.view;
  if (!view) return;
  initialRenderRequest = request;
  const doc = view.state.doc;
  const contentToken = documentToken;
  const start = nowMs();
  let anchorResult = 'ok';
  log(
    'KardLeafCM6Perf',
    `initial render start request=${request} len=${doc.length} anchor=${anchor ? 'yes' : 'no'} sourceReveal=${sourceRevealEnabled}`,
  );
  try {
    while (initialRenderRequest === request && view.state.doc === doc && documentToken === contentToken) {
      // Let CodeMirror settle its viewport/height map, including hidden but laid-out WebViews.
      await new Promise<void>((resolve) => view.requestMeasure({ read: () => {}, write: () => resolve() }));
      if (initialRenderRequest !== request || view.state.doc !== doc || documentToken !== contentToken) return;
      if (nowMs() - start > 10_000) throw new Error('Initial preview did not finish rendering');
      if (view.scrollDOM.clientWidth <= 0 || view.scrollDOM.clientHeight <= 0) continue;
      // Position first so only the destination viewport needs synchronous parsing.
      // Search navigation has priority over a saved mode-switch anchor.
      if (anchor && !initialSelectionRequested) {
        const before = view.scrollDOM.scrollTop;
        anchorResult = String((window.KardLeafEditor?.scrollViewportToAnchor as (anchor: unknown) => unknown)(anchor));
        if (!anchorResult.startsWith('ok:')) throw new Error(anchorResult);
        if (Math.abs(view.scrollDOM.scrollTop - before) > 1) continue;
      }
      const treeBefore = syntaxTree(view.state);
      if (livePreviewEnabled && (!forceParsing(view, view.viewport.to, 8) || treeBefore !== syntaxTree(view.state))) continue;
      // Images, fonts and asynchronous diagrams keep their own loading/error widgets.
      // Their network/decoding time must not hold the whole note invisible or disable scrolling.
      log(
        'KardLeafCM6Perf',
        `initial render ready request=${request} len=${doc.length} viewportTo=${view.viewport.to} parsedTo=${syntaxTree(view.state).length} ` +
          `elapsed=${(nowMs() - start).toFixed(1)}ms anchor=${anchorResult} sourceReveal=${sourceRevealEnabled}`,
      );
      callBridge('onInitialRenderReady', [bootstrapToken, contentToken, request, anchorResult]);
      if (livePreviewEnabled) traceTitleStyle('render-ready');
      return;
    }
  } catch (error) {
    if (initialRenderRequest !== request) return;
    log(
      'KardLeafCM6Perf',
      `initial render failed request=${request} elapsed=${(nowMs() - start).toFixed(1)}ms: ${String(error)}`,
    );
    callBridge('onInitialRenderReady', [bootstrapToken, contentToken, request, 'error']);
  }
}

function createKardLeafBridgePlugin(): EditorPlugin {
  return {
    id: 'kardleaf.androidBridge',
    setup(ctx) {
      let touching = false;
      let lastSelectionLogAt = 0;
      const longTasks = typeof PerformanceObserver !== 'undefined' &&
        PerformanceObserver.supportedEntryTypes?.includes('longtask')
        ? new PerformanceObserver((entries) => {
          const view = editor?.view;
          const selection = view?.state.selection.main;
          if (!view || !selection || selection.empty) return;
          for (const entry of entries.getEntries()) {
            log('KardLeafCM6Perf', `selection longTask start=${entry.startTime.toFixed(1)}ms duration=${entry.duration.toFixed(1)}ms ` +
              `from=${selection.from} to=${selection.to} head=${selection.head} docLen=${view.state.doc.length} ` +
              `domFocus=${domNodeTrace(window.getSelection()?.focusNode ?? null)}`);
          }
        })
        : null;
      longTasks?.observe({ entryTypes: ['longtask'] });
      const handleNativeSelectionChange = () => {
        const view = editor?.view;
        if (!view) return;
        syncNativeSelectionState(view, touching);
        const active = traceNativeSelectionGesture(view, 'selectionchange');
        const selection = window.getSelection();
        const now = nowMs();
        if (selection && !selection.isCollapsed && now - lastNativeSelectionTraceAt >= 120) {
          lastNativeSelectionTraceAt = now;
          log(
            'KardLeafCM6SelectionTrace',
            `nativeSelection change touching=${touching} cm=${view.state.selection.main.from}-${view.state.selection.main.to} ` +
              `active=${domNodeTrace(document.activeElement)} ${domSelectionTrace()}`,
          );
        }
        if (!active) {
          flushSelectionNotification();
          document.dispatchEvent(new Event(nativeSelectionSettledEvent));
        }
      };
      // Capture also sees independent contenteditable widgets, whose events
      // are deliberately ignored by CodeMirror's DOM event handlers.
      const handleNativeTouchStart = (event: TouchEvent) => {
        const view = editor?.view;
        if (!view || !(event.target instanceof Node) || !view.contentDOM.contains(event.target)) return;
        touching = true;
        setTouchSelecting(view, true);
      };
      const handleNativeTouchEnd = () => {
        touching = false;
        handleNativeSelectionChange();
      };
      // Capture before CodeMirror's DOMObserver. Android emits the native
      // range first; if CM flushes it before this listener, a source-reveal
      // rebuild can win the same frame as the long-press.
      document.addEventListener('selectionchange', handleNativeSelectionChange, true);
      document.addEventListener('touchstart', handleNativeTouchStart, { capture: true, passive: true });
      document.addEventListener('touchend', handleNativeTouchEnd, { capture: true, passive: true });
      document.addEventListener('touchcancel', handleNativeTouchEnd, { capture: true, passive: true });
      ctx.registerCmExtensions([
        currentSearchHighlight,
        EditorState.transactionExtender.of(tr => tr.isUserEvent('input.replace')
          ? { annotations: isolateHistory.of('full') } : null),
        EditorState.transactionExtender.of(tr => !sourceRevealEnabled && tr.selection && tr.isUserEvent('select')
          ? { effects: setSourceRevealEnabled.of(true) } : null),
        EditorView.updateListener.of((update) => {
          if (!sourceRevealEnabled && update.transactions.some(tr =>
            tr.effects.some(effect => effect.is(setSourceRevealEnabled) && effect.value))) {
            sourceRevealEnabled = true;
            log('KardLeafCM6Input', `source reveal enabled reason=selection selection=${update.state.selection.main.from}:${update.state.selection.main.to}`);
          }
          traceSelectionViewUpdate(update);
          if (update.docChanged || update.selectionSet || update.focusChanged) notifyContextToolbar();
          if ((getSearchQuery(update.state).search || getSearchQuery(update.startState).search) &&
              (update.docChanged || update.selectionSet || update.transactions.some(tr => tr.effects.some(e => e.is(setSearchQuery))))) {
            queueSearchSummary();
          }
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
            if (selection.empty || pointerSelection || nowMs() - lastSelectionLogAt >= 250) {
              lastSelectionLogAt = nowMs();
              log(
                'KardLeafCM6Input',
                `selection revision=${selectionRevision} from=${selection.from} to=${selection.to} head=${selection.head} ` +
                  `pointer=${pointerSelection} pointerPos=${pointerPos ?? -1} pointerAge=${pointerAge}ms ` +
                  `active=${domNodeTrace(document.activeElement)} ` +
                  `${pointerSelection ? domSelectionTrace() : 'dom=not-sampled'}`,
              );
            }
            if (pointerSelection) {
              pointerSelectionTrace = null;
            }
            notifySelection();
          }
          if (update.docChanged) notifyHistoryState();
        }),
        EditorView.domEventHandlers({
          touchstart(event) {
            callBridge('onUserInteraction');
            const touch = event.touches[0];
            const view = editor?.view;
            const selection = view?.state.selection.main;
            const target = event.target instanceof Element ? event.target : null;
            const editableTarget = target?.closest('[contenteditable]');
            const ignoredTarget = target?.closest('button,a,input,textarea,select') ||
              editableTarget && editableTarget !== editor?.view.contentDOM;
            // Establish the DOM caret before Chromium starts its first long-press
            // selection session. Focusing afterwards can lose the native handles.
            if (view && !view.hasFocus && event.touches.length === 1 && !ignoredTarget &&
                editableTarget === view.contentDOM && target?.closest('.cm-line')) {
              view.focus();
            }
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
              pointer.position = view && pointer.endedAt - pointer.startedAt <= 280 && pointer.maxMove <= 8
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
                  if (!view.state.selection.main.empty || window.getSelection()?.isCollapsed === false) return;
                  const pos = pointer.position;
                  if (typeof pos !== 'number') return;
                  view.dispatch({
                    selection: EditorSelection.cursor(pos),
                    userEvent: 'select.pointer',
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
          keydown() {
            enableSourceReveal('keydown');
            callBridge('onUserInteraction');
            return false;
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
        {
          id: 'cycleInlineStyleAtCursor',
          run({ view }, property) {
            if (property === 'color' || property === 'backgroundColor' || property === 'fontSize') {
              return cycleInlineStyleAtCursor(view, property);
            }
            return false;
          },
        },
        {
          id: 'setInlineStyleAtCursor',
          run({ view }, property, value) {
            if (property === 'color' || property === 'backgroundColor' || property === 'fontSize') {
              return setInlineStyleAtCursor(view, property, typeof value === 'string' && value ? value : null);
            }
            return false;
          },
        },
        {
          id: 'openInlineColorPicker',
          run(_context, property) {
            return openInlineColorPicker(String(property ?? '')) === 'ok';
          },
        },
      ]);

      return {
        dispose() {
          longTasks?.disconnect();
          document.removeEventListener('selectionchange', handleNativeSelectionChange, true);
          document.removeEventListener('touchstart', handleNativeTouchStart, true);
          document.removeEventListener('touchend', handleNativeTouchEnd, true);
          document.removeEventListener('touchcancel', handleNativeTouchEnd, true);
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
          rawHtmlPlugin({ handlesEditableHtml: isColorHtml }),
          inlineColorPlugin(),
          createKardLeafWikiImagePlugin(),
        ]
      : []),
    smartPastePlugin(),
    slashCommandPlugin(),
    wikilinkPlugin(),
    markTextToolbarPlugin((id) => { editor?.execCommand(id); }),
    createKardLeafBridgePlugin(),
  ];
}

function hideTableToolbar() {
  const wasVisible = tableToolbar !== null;
  tableToolbar = null;
  if (wasVisible) notifyContextToolbar();
}

function showTableToolbar(event: EditorTableContextMenuEvent) {
  if (readOnly) {
    hideTableToolbar();
    return;
  }

  tableToolbar = event;
  notifyContextToolbar();
}

function runTableToolbarAction(action: string) {
  const event = tableToolbar;
  if (!event || readOnly || !livePreviewEnabled) return 'missing';

  switch (action) {
    case 'addRow':
      hideTableToolbar();
      event.actions.addRowAt(event.rowIdx, 'below');
      return 'ok';
    case 'addColumn':
      hideTableToolbar();
      event.actions.addColumnAt(event.colIdx, 'right');
      return 'ok';
    case 'deleteRow':
      if (event.rowIdx < 0) return 'disabled';
      hideTableToolbar();
      event.actions.deleteRow(event.rowIdx);
      return 'ok';
    case 'deleteColumn':
      if (event.colCount <= 1) return 'disabled';
      hideTableToolbar();
      event.actions.deleteColumn(event.colIdx);
      return 'ok';
    case 'alignment': {
      const nextAlignment =
        event.alignment === null
          ? 'left'
          : event.alignment === 'left'
            ? 'center'
            : event.alignment === 'center'
              ? 'right'
              : null;
      hideTableToolbar();
      event.actions.setAlignment(event.colIdx, nextAlignment);
      return 'ok';
    }
    case 'sourcePreview':
      hideTableToolbar();
      event.actions.toggleSource();
      return 'ok';
    default:
      return 'missing';
  }
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
  // Table selection sync briefly focuses the CM root; it is not a new user target.
  if (target === editor?.view.contentDOM) return;
  if (
    !(target instanceof Element) ||
    !target.closest('.cm-table-widget')
  ) {
    hideTableToolbar();
  }
});

document.addEventListener('pointerdown', (event) => {
  if (!(event.target instanceof Element) || !event.target.closest('.cm-table-widget')) hideTableToolbar();
}, true);

document.addEventListener('keydown', (event) => {
  if (event.target === editor?.view.contentDOM) hideTableToolbar();
});

document.addEventListener('touchmove', hideTableToolbar, { passive: true });

function titleHeaderHeightPx() {
  return Math.ceil(currentTitleFontSize * 1.272727 + 8);
}

function traceTitleStyle(stage: string) {
  if (!titleHeader || !titleInput) {
    log(
      'KardLeafCM6Trace',
      `title style stage=${stage} input=missing titleLen=${currentTitle.length} ` +
        `titleVisible=${titleVisible} titleFontSize=${currentTitleFontSize}px`,
    );
    return;
  }
  const inputStyle = window.getComputedStyle(titleInput);
  const inputRect = titleInput.getBoundingClientRect();
  const headerRect = titleHeader.getBoundingClientRect();
  const rootStyle = window.getComputedStyle(document.documentElement);
  const scroller = editor?.view.scrollDOM;
  const content = editor?.view.contentDOM;
  const contentStyle = content ? getComputedStyle(content) : null;
  const scrollerStyle = scroller ? getComputedStyle(scroller) : null;
  const contentRect = content?.getBoundingClientRect();
  log(
    'KardLeafCM6Trace',
    `title style stage=${stage} page=${bootstrapToken} request=${initialRenderRequest} t=${nowMs().toFixed(1)} ` +
      `titleLen=${currentTitle.length} titleVisible=${titleVisible} ` +
      `hidden=${titleHeader.hidden} readOnly=${titleInput.readOnly} ` +
      `fontFamily=${inputStyle.fontFamily} fontSize=${inputStyle.fontSize} ` +
      `fontWeight=${inputStyle.fontWeight} letterSpacing=${inputStyle.letterSpacing} ` +
      `lineHeight=${inputStyle.lineHeight} input=${inputRect.width.toFixed(1)}x${inputRect.height.toFixed(1)} ` +
      `header=${headerRect.width.toFixed(1)}x${headerRect.height.toFixed(1)} ` +
      `inputXY=${inputRect.x.toFixed(2)},${inputRect.y.toFixed(2)} headerXY=${headerRect.x.toFixed(2)},${headerRect.y.toFixed(2)} ` +
      `bodyXY=${contentRect?.x.toFixed(2)},${contentRect?.y.toFixed(2)} scroll=${scroller?.scrollLeft},${scroller?.scrollTop} ` +
      `viewport=${scroller?.clientWidth}x${scroller?.clientHeight} dpr=${devicePixelRatio} scale=${window.visualViewport?.scale} ` +
      `bodyFont=${contentStyle?.fontFamily} bodySize=${contentStyle?.fontSize} bodyLine=${contentStyle?.lineHeight} ` +
      `bodySpacing=${contentStyle?.letterSpacing} bodyWeight=${contentStyle?.fontWeight} ` +
      `scrollerFont=${scrollerStyle?.fontFamily} scrollerLine=${scrollerStyle?.lineHeight} ` +
      `requestedFont=${normalizeFontFamily(currentFontFamily)} requestedSize=${currentFontSize} ` +
      `requestedLine=${currentLineHeight} requestedSpacing=${currentLetterSpacing} sourceReveal=${sourceRevealEnabled} ` +
      `cssHeaderHeight=${rootStyle.getPropertyValue('--kl-title-header-height').trim()} ` +
      `fonts=${document.fonts?.status ?? 'unknown'}`,
  );
}

function traceInitialSurface() {
  if (!livePreviewEnabled) return;
  const token = documentToken;
  const request = initialRenderRequest;
  // Bounded observation only: never move the title/scroll position or gate rendering on a timer.
  for (const delay of [0, 100, 300, 700, 1500]) {
    window.setTimeout(() => {
      if (documentToken === token && initialRenderRequest === request) traceTitleStyle(`visible+${delay}ms`);
    }, delay);
  }
}

function scheduleTitleStyleTrace(stage: string) {
  requestAnimationFrame(() => {
    traceTitleStyle(`${stage}:raf1`);
    requestAnimationFrame(() => traceTitleStyle(`${stage}:raf2`));
  });
}

function applyTitleHeaderState(stage = 'apply') {
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
  traceTitleStyle(stage);
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
  titleInput.addEventListener('focus', () => {
    callBridge('onUserInteraction');
  });
  titleInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      titleInput?.blur();
    }
  });

  titleHeader.appendChild(titleInput);
  scroller.insertBefore(titleHeader, scroller.firstChild);
  applyTitleHeaderState('install');
  scheduleTitleStyleTrace('install');
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
  applyTitleHeaderState('setState');
  scheduleTitleStyleTrace('setState');
  return 'ok';
}

function createEditorInstance(initialText = '', initialSelection?: { anchor: number; head: number }) {
  if (!root) throw new Error('Missing #editorRoot');
  root.textContent = '';
  root.classList.toggle('kl-live-preview', livePreviewEnabled);
  const settings = buildSettings();
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
    settings,
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
  lastRuntimeSettings = JSON.stringify(settings);
  editor.view.scrollDOM.addEventListener('scroll', handleScroll, { passive: true });
  installTitleHeader();
  notifyHistoryState(true);
  notifySelection();
  setStatus('');
  log(
    'KardLeafCM6',
    `editor ready version=${VERSION} livePreview=${livePreviewEnabled} renderPlugins=${livePreviewEnabled} ` +
      `docLen=${editor.view.state.doc.length} selection=${editor.view.state.selection.main.from}:${editor.view.state.selection.main.to} ` +
      `sourceReveal=${sourceRevealEnabled}`,
  );
}

function updateRuntimeSettings() {
  if (!editor) return;
  const settings = buildSettings();
  const signature = JSON.stringify(settings);
  if (signature === lastRuntimeSettings) return;
  editor.updateSettings(settings);
  lastRuntimeSettings = signature;
}

function dispatchFullDocument(
  content: unknown,
  selectionStart?: unknown,
  selectionEnd?: unknown,
  addToHistory = false,
  contentToken = documentToken,
) {
  if (!editor) {
    fallbackText = String(content ?? '');
    if (fallbackTextArea) fallbackTextArea.value = fallbackText;
    return 'fallback';
  }

  const text = String(content ?? '');
  const start = nowMs();
  documentToken = contentToken;

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
    window.requestAnimationFrame(() => onContentApplied(contentToken));
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
  enableSourceReveal('replace-range');
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
  enableSourceReveal('select-range');
  initialSelectionRequested = true;
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
  enableSourceReveal('search');
  try {
    const view = editor.view;
    const next = androidSearchQuery(queryText, !!useRegex, !!matchCase);
    if (!getSearchQuery(view.state).eq(next)) view.dispatch({ effects: setSearchQuery.of(next) });
    // SwarmNote supplies the existing invisible panel required by the official highlighter.
    openSearchPanel(view);
    setSearchActiveClass(true);
    queueSearchSummary();
    return 'ok';
  } catch (error) {
    reportError(`set search failed source=${reason} queryLen=${queryText.length}`, error);
    return 'error';
  }
}

let searchSummaryQueued = false;
function queueSearchSummary() {
  if (searchSummaryQueued) return;
  searchSummaryQueued = true;
  queueMicrotask(() => {
    searchSummaryQueued = false;
    if (editor) callBridge('onSearchStateChanged', [JSON.stringify(searchSummary(editor.view.state))]);
  });
}

function navigateAndroidSearch(direction: number, preferredStart = -1) {
  const view = editor?.view;
  if (!view) return 'missing';
  const matches = searchMatches(view.state);
  if (!matches.length) { queueSearchSummary(); return 'empty'; }
  const selection = view.state.selection.main;
  const current = matches.findIndex(m => selection.from >= m.from && selection.to <= m.to && selection.from < m.to);
  let index: number;
  if (direction === 0) {
    const start = preferredStart >= 0 ? preferredStart : selection.from;
    index = matches.findIndex(m => m.to > start);
    if (index < 0) index = 0;
  } else if (current >= 0) {
    index = (current + direction + matches.length) % matches.length;
  } else if (direction > 0) {
    index = matches.findIndex(m => m.from >= selection.to);
    if (index < 0) index = 0;
  } else {
    index = -1;
    for (let i = matches.length - 1; i >= 0; i--) {
      if (matches[i].to <= selection.from) { index = i; break; }
    }
    if (index < 0) index = matches.length - 1;
  }
  const match = matches[index];
  initialSelectionRequested = true;
  view.dispatch({ selection: EditorSelection.single(match.from, match.to),
    effects: EditorView.scrollIntoView(match.from, { y: 'center', yMargin: 96 }),
    annotations: Transaction.addToHistory.of(false) });
  return 'ok';
}

function replaceAndroidSearch(all: boolean, replacement: unknown) {
  const view = editor?.view;
  if (!view || readOnly) return 'missing';
  const query = getSearchQuery(view.state);
  if (!query.valid) return 'invalid';
  view.dispatch({ effects: setSearchQuery.of(androidSearchQuery(query.search, query.regexp, query.caseSensitive, String(replacement ?? ''))) });
  if (query.regexp) {
    const changes = regexSearchChanges(view.state);
    const selection = view.state.selection.main;
    const current = changes.find(m => selection.from >= m.from && selection.to <= m.to && selection.from < m.to)
      ?? changes.find(m => m.from >= selection.from) ?? changes[0];
    if (!current) return 'empty';
    const after = current.from + current.insert.length;
    view.dispatch({ changes: all ? changes : [current],
      selection: all ? undefined : EditorSelection.cursor(after), userEvent: all ? 'input.replace.all' : 'input.replace' });
    if (!all) {
      const remaining = searchMatches(view.state);
      const next = remaining.find(m => m.from >= after) ?? remaining[0];
      if (next) navigateAndroidSearch(0, next.from);
    }
  } else if (all) replaceAllSearch(view);
  else {
    const summary = searchSummary(view.state);
    // The search field retains focus; select the actual match before the official replacement transaction.
    navigateAndroidSearch(0, summary.currentStart);
    replaceNext(view);
  }
  notifyHistoryState(true);
  queueSearchSummary();
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
    prepareInitialRender(request: string, anchor: unknown) {
      void prepareInitialRender(request, anchor);
      return 'pending';
    },
    setTypography(fontSize: unknown, style: unknown) {
      if (Number.isFinite(Number(fontSize))) currentFontSize = Math.max(12, Math.min(30, Number(fontSize)));
      applyTypographyStyle(style);
      updateRuntimeSettings();
      if (livePreviewEnabled) scheduleTitleStyleTrace('typography');
      return 'ok';
    },
    traceInitialSurface,
    prepareImeReveal(imeInsetPx: unknown) {
      return prepareImeReveal(imeInsetPx);
    },
    setTitleState(title: unknown, hint: unknown, visible: unknown, fontSize: unknown) {
      return setTitleState(title, hint, visible, fontSize);
    },
    setDocument(content: unknown, selectionStart: unknown, selectionEnd: unknown, fontSize: unknown, nextDarkMode: unknown, typographyStyle?: unknown, contentToken?: string) {
      if (Number.isFinite(Number(fontSize))) {
        currentFontSize = Math.max(12, Math.min(30, Number(fontSize)));
      }
      applyTypographyStyle(typographyStyle);
      if (typeof nextDarkMode === 'boolean') setDocumentTheme(nextDarkMode);
      updateRuntimeSettings();
      return dispatchFullDocument(content, selectionStart, selectionEnd, false, contentToken);
    },
    setContent(content: unknown) {
      const length = String(content ?? '').length;
      return dispatchFullDocument(content, length, length, false);
    },
    setContentFromAndroid(content: unknown, selectionStart: unknown, selectionEnd: unknown, contentToken?: string) {
      return dispatchFullDocument(content, selectionStart, selectionEnd, false, contentToken);
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
      enableSourceReveal('focus-editor');
      editor.focus();
      return 'ok';
    },
    focus() {
      if (!editor) return 'missing';
      enableSourceReveal('focus');
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
      enableSourceReveal('undo');
      return editor?.execCommand('undo') ? 'ok' : 'empty';
    },
    redo() {
      enableSourceReveal('redo');
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
      if (name === 'tableToolbarAction') return runTableToolbarAction(String(args[0] ?? ''));
      if (name === 'scrollToOffset') return selectEditorRangeAndReveal(args[0], args[0]);
      if (name === 'setSearchState') {
        const result = setAndroidSearchState(args[0], args[1], args[2], args[3], args[4], 'android-execCommand');
        if (result === 'ok' && args[6]) navigateAndroidSearch(0, Number(args[5] ?? -1));
        return result;
      }
      if (name === 'clearSearchState') return clearAndroidSearchState(args[0] ?? 'android-execCommand');
      if (name === 'navigateSearch') {
        if (args.length > 2) setAndroidSearchState(args[2], args[3], args[4], -1, 0);
        return navigateAndroidSearch(Number(args[0]) || 0, Number(args[1] ?? -1));
      }
      if (name === 'replaceSearch') {
        if (args.length > 2) setAndroidSearchState(args[2], args[3], args[4], -1, 0);
        return replaceAndroidSearch(!!args[0], args[1]);
      }
      enableSourceReveal(`command:${name}`);
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
    let initialText = '';
    let initialSelection: { anchor: number; head: number } | undefined;
    if (bootstrapToken) {
      const payload = callBridge('consumeDocumentPayload', [bootstrapToken]);
      if (typeof payload !== 'string') throw new Error('Missing bootstrap configuration');
      const config = JSON.parse(payload);
      documentToken = config.documentToken;
      const content = callBridge('consumeDocumentPayload', [documentToken]);
      if (typeof content !== 'string') throw new Error('Missing initial document');
      initialText = content;
      // Match the existing setDocument path: CodeMirror folds CRLF before clamping the supplied UTF-16 selection.
      initialSelection = clampSelection(config.selectionStart, config.selectionEnd, initialText.replace(/\r\n/g, '\n').length);
      setDocumentTheme(!!config.darkMode);
      applyThemeColors(config.themeColors);
      currentFontSize = Math.max(12, Math.min(30, Number(config.fontSize) || 16));
      applyTypographyStyle(config.typography);
      setTitleState(config.title, config.titleHint, config.titleVisible, config.titleFontSize);
    } else if (bridge()) {
      throw new Error('Missing bootstrap token');
    }
    createEditorInstance(initialText, initialSelection);
    installEditorApi();
    callBridge('onEditorReady', [VERSION, editor!.view.state.doc.length, bootstrapToken]);
    onContentApplied();
    log(
      'KardLeafCM6Perf',
      `startup elapsed=${(nowMs() - start).toFixed(1)}ms docLen=${initialText.length} ` +
        `selection=${initialSelection?.anchor ?? 0}:${initialSelection?.head ?? 0} sourceReveal=${sourceRevealEnabled}`,
    );
  } catch (error) {
    reportError('startup failed', error);
    createFallbackTextArea(error instanceof Error ? error.message : String(error));
  }
}

main();
