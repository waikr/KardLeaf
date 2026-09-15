/*! Adapted from MarkText Android. Copyright (c) 2026 Renakoni. MIT; see LICENSE.
 * Vue bindings and Muya operations are replaced by DOM/CodeMirror adapters.
 * Product rules, geometry and paging live in the original selectionToolbar.ts. */
import { EditorSelection, Transaction } from '@codemirror/state';
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view';
import type { EditorPlugin } from '../vendor/swarmnote-editor-core';
import { computeSelectionToolbarPlacement, computeSelectionToolbarPageCapacity,
  getSelectionToolbarCommands, shouldShowSelectionToolbar, paginateSelectionCommands } from './selectionToolbar';
import css from './selectionToolbar.css';

import { commandLabels, normalizeSettings } from './settings';
import { colorNames, colorPalette, fontSizeNames, fontSizePalette, safeColor, type InlineStyleProperty } from './inlineStyles';
import { applySelectionInlineStyle } from './inlineStyleSpans';
import { hasNonEmptyNativeSelection } from '../vendor/swarmnote-editor-core/core';
const quickColorPalette = [colorPalette[6], colorPalette[0], colorPalette[2], colorPalette[4]];
const quickColorNames = quickColorPalette.map(color => colorNames[colorPalette.indexOf(color)]);
const quickBackgroundColorPalette = [colorPalette[7], colorPalette[0], colorPalette[2], colorPalette[4]];
const quickBackgroundColorNames = quickBackgroundColorPalette.map(color => colorNames[colorPalette.indexOf(color)]);
const quickPalette = (property: InlineStyleProperty | null) => property === 'backgroundColor' ? quickBackgroundColorPalette : quickColorPalette;
const quickNames = (property: InlineStyleProperty | null) => property === 'backgroundColor' ? quickBackgroundColorNames : quickColorNames;
const labels: Record<string, string> = { cut: '剪切', copy: '复制', paste: '粘贴', selectAll: '全选', color: '字体颜色', backgroundColor: '字体背景色', fontSize: '字体大小', customColor: '自定义颜色', ...commandLabels };
const glyphs: Record<string, string> = {
  back: '<path d="M90 40L70 64L90 88M60 40L40 64L60 88" fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>',
  copy: '<rect x="9" y="8" width="10" height="12" rx="2"/><path d="M5 16V6a2 2 0 0 1 2-2h8"/>',
  cut: '<circle cx="6" cy="7" r="2.3"/><circle cx="6" cy="17" r="2.3"/><path d="M8.1 8.1 19 19M8.1 15.9 19 5"/>',
  next: '<path d="M38 40L58 64L38 88M68 40L88 64L68 88" fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>',
  paste: '<path d="M9 5h6M9 4.8A2.8 2.8 0 0 1 11.8 2h.4A2.8 2.8 0 0 1 15 4.8V6H9ZM8 5H6.8A2.8 2.8 0 0 0 4 7.8v10.4A2.8 2.8 0 0 0 6.8 21h10.4a2.8 2.8 0 0 0 2.8-2.8V7.8A2.8 2.8 0 0 0 17.2 5H16"/>',
  selectAll: '<rect x="4" y="4" width="16" height="16" rx="2" stroke-dasharray="2.4 2.4"/>',
  customColor: '<path d="M12 5v14M5 12h14"/>',
  color: '<g transform="translate(-32.644 -42.966) scale(1.255)" fill="currentColor"><path d="m152.58 152-8.1758-20.922h-32.602l-8.2266 20.922h-10.055l29.199-71.551h11.02l28.742 71.551zm-24.477-64.238-0.45703 1.4219q-1.2695 4.2148-3.7578 10.816l-9.1406 23.512h26.762l-9.1914-23.613q-1.4219-3.5039-2.8438-7.9219z"/><path d="m78 188h100" stroke="currentColor" stroke-linecap="round" stroke-width="8"/></g>',
  backgroundColor: '<g transform="translate(-12.787 -3.0861) scale(1.0487)" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="8"><path d="m104 70 54 54"/><path d="m120 86-46 46q-5 5 0 11l32 32q5 5 10 0l46-46q5-5 0-10l-42-42"/><path d="m185 142s-12 17-12 26a12 12 0 0 0 24 0c0-9-12-26-12-26z"/></g>',
  fontSize: '<g transform="translate(-15.018 -11.102) scale(1.1631)" fill="currentColor" font-family="Arial, Helvetica, sans-serif" font-weight="400" text-anchor="middle"><path d="m99.125 154-5.0312-12.875h-20.062l-5.0625 12.875h-6.1875l17.969-44.031h6.7812l17.688 44.031zm-15.062-39.531-0.28125 0.875q-0.78125 2.5938-2.3125 6.6562l-5.625 14.469h16.469l-5.6562-14.531q-0.875-2.1562-1.75-4.875z"/><path d="m173.63 154-7.8613-20.117h-31.348l-7.9102 20.117h-9.668l28.076-68.799h10.596l27.637 68.799zm-23.535-61.768-0.43946 1.3672q-1.2207 4.0527-3.6133 10.4l-8.7891 22.607h25.732l-8.8379-22.705q-1.3672-3.3691-2.7344-7.6172z"/></g>',
};
const iconViewBoxes: Record<string, string> = { back: '30 32 66 64', next: '30 32 66 64', color: '0 0 256 256', backgroundColor: '0 0 256 256', fontSize: '0 0 256 256' };
const short: Record<string, string> = { toggleBold: 'B', toggleItalic: 'I', toggleUnderline: 'U',
  toggleStrike: 'S', toggleCode: '`', toggleBlockquote: '❯', toggleOrderedList: '1.', toggleUnorderedList: '•', toggleCheckList: '☑', color: '字色', backgroundColor: '底色', fontSize: '字号' };
type Snapshot = { selection: EditorSelection; doc: unknown; range: Range | null; cell: HTMLElement | null;
  collapsed: boolean; withinEditor: boolean; text: string; rect: DOMRect | null };
type Native = { event: (name: string, data?: any) => void; response: (id: number, data: any) => void; suspend: (value: boolean) => void };
declare global { interface Window { KardLeafSelection?: Native } }

export function markTextToolbarPlugin(execute: (id: string) => void): EditorPlugin {
  return { id: 'kardleaf.marktextSelection', setup(ctx) {
    let owner: Toolbar | null = null;
    ctx.registerCmExtensions([ViewPlugin.fromClass(class {
      constructor(view: EditorView) { owner = new Toolbar(view, execute); }
      update(update: ViewUpdate) { owner?.update(update); }
      destroy() { owner?.dispose(); owner = null; }
    })]);
    ctx.registerCommands([{ id: 'selectionToolbar.dismiss', run() { owner?.dismiss(); } }]);
  } };
}

class Toolbar {
  private bar = document.createElement('div');
  private style = document.createElement('style');
  private settings = normalizeSettings(null);
  private page = 0;
  private styleReturnPage = 0;
  private styleProperty: InlineStyleProperty | null = null;
  private generation = 0;
  private caret: EditorSelection | null = null;
  private canPaste = false;
  private suspended = false;
  private failed = false;
  private disposed = false;
  private busy = false;
  private frame = 0;
  private selectionTimer = 0;
  private sequence = 0;
  private signature = '';
  private snapshot: Snapshot | null = null;
  private touch: { x: number; y: number; time: number; travel: number } | null = null;
  private pending = new Map<number, { resolve: (data: any) => void; timer: number }>();
  private native: Native;
  private abort = new AbortController();
  private toolbarTouchAt = 0;
  private toolbarTouchFlipped = false;
  constructor(private view: EditorView, private execute: (id: string) => void) {
    this.style.textContent = css;
    document.head.append(this.style);
    this.bar.className = 'mobile-selection-toolbar';
    this.bar.dataset.testid = 'mobile-selection-toolbar';
    this.bar.setAttribute('role', 'toolbar');
    this.bar.setAttribute('aria-label', '文本选择工具栏');
    this.bar.hidden = true;
    this.bar.style.display = 'none';
    document.body.append(this.bar);
    this.native = { response: (id, data) => {
      const request = this.pending.get(id);
      if (request) { clearTimeout(request.timer); this.pending.delete(id); request.resolve(data); }
    }, event: (name, data) => {
      if (name === 'context') void this.context();
      if (name === 'tap' && data) {
        if (this.toolbarTouchAt && Date.now() - this.toolbarTouchAt <= 1000) {
          const flipped = this.toolbarTouchFlipped;
          this.toolbarTouchAt = 0;
          this.toolbarTouchFlipped = false;
          if (flipped) this.restoreSnapshotSelection();
          this.schedule();
          return;
        }
        void this.outside(data.x, data.y);
      }
      if (name === 'settings') void this.loadSettings();
      if (name === 'hideFailed') { this.failed = true; this.dismiss(); }
    }, suspend: (value) => {
      this.suspended = value;
      if (value) this.dismiss();
      this.syncNativeSelectionMode();
      this.schedule();
    } };
    window.KardLeafSelection = this.native;
    const signal = this.abort.signal;
    this.bar.addEventListener('touchstart', event => {
      this.toolbarTouchAt = Date.now();
      this.toolbarTouchFlipped = false;
      event.preventDefault();
    }, { passive: false, signal });
    this.bar.addEventListener('touchcancel', () => {
      this.toolbarTouchAt = 0;
      this.toolbarTouchFlipped = false;
    }, { signal });
    this.bar.addEventListener('mousedown', event => event.preventDefault(), { signal });
    document.addEventListener('selectionchange', () => this.schedule(), { signal });
    document.addEventListener('scroll', () => {
      if (this.inEditorFocus() && (!this.view.state.selection.main.empty || hasNonEmptyNativeSelection(this.view)))
        this.deferSelectionRender();
      else this.schedule();
    }, { capture: true, passive: true, signal });
    window.addEventListener('resize', () => this.schedule(), { signal });
    document.addEventListener('focusin', () => {
      if (!this.inEditorFocus()) this.dismiss();
      this.syncNativeSelectionMode();
      this.schedule();
    }, { signal });
    document.addEventListener('touchstart', event => {
      if (this.bar.contains(event.target as Node)) return;
      this.toolbarTouchAt = 0;
      this.toolbarTouchFlipped = false;
      this.invalidateCaret();
      const point = event.touches.length === 1 ? event.touches[0] : null;
      this.touch = point && this.bar.style.display !== 'none' ? { x: point.clientX, y: point.clientY, time: Date.now(), travel: 0 } : null;
    }, { capture: true, passive: true, signal });
    document.addEventListener('touchmove', event => {
      if (this.touch && event.touches[0]) this.touch.travel = Math.max(this.touch.travel,
        Math.hypot(event.touches[0].clientX - this.touch.x, event.touches[0].clientY - this.touch.y));
    }, { capture: true, passive: true, signal });
    document.addEventListener('touchcancel', () => { this.touch = null; }, { signal });
    document.addEventListener('touchend', event => {
      const start = this.touch; this.touch = null;
      const point = event.changedTouches[0];
      if (start && point && Date.now() - start.time <= 500 && start.travel <= 24 &&
        Math.hypot(point.clientX - start.x, point.clientY - start.y) <= 24)
        void this.outside(point.clientX, point.clientY);
    }, { capture: true, passive: true, signal });
    // Browser development fallback only; Android owns the native long-press signal.
    if (!window.KardLeafAndroid) this.view.contentDOM.addEventListener('contextmenu', event => {
      event.preventDefault(); void this.context();
    }, { signal });
    void this.loadSettings();
  }
  private request(op: string, data: any = {}): Promise<any> {
    if (this.disposed) return Promise.resolve({ ok: false });
    const id = ++this.sequence;
    return new Promise(resolve => {
      const timer = window.setTimeout(() => { this.pending.delete(id); resolve({ ok: false }); }, 2500);
      this.pending.set(id, { resolve, timer });
      try {
        const target = window.KardLeafAndroid;
        if (!target?.selectionToolbarRequest) { this.native.response(id, { ok: false }); return; }
        target.selectionToolbarRequest(JSON.stringify({ id, op, ...data }));
      } catch { this.native.response(id, { ok: false }); }
    });
  }
  private async loadSettings() {
    const result = await this.request('settings');
    if (this.disposed || !result.ok) return;
    this.settings = normalizeSettings(result);
    if (!this.settings.enabled) this.dismiss();
    this.page = 0;
    this.syncNativeSelectionMode();
    this.schedule();
  }
  private syncNativeSelectionMode() {
    void this.request('suppress', { enabled: this.settings.enabled && !this.suspended && !this.failed && this.inEditorFocus() });
  }
  private inEditorFocus() { return this.view.dom.contains(document.activeElement) || this.bar.contains(document.activeElement); }
  private read(): Snapshot {
    const selection = this.view.state.selection;
    const dom = document.getSelection();
    const range = dom?.rangeCount ? dom.getRangeAt(0) : null;
    const withinEditor = !!range && this.view.contentDOM.contains(range.startContainer) && this.view.contentDOM.contains(range.endContainer);
    const element = range?.startContainer instanceof Element ? range.startContainer : range?.startContainer.parentElement;
    const editable = element?.closest<HTMLElement>('[contenteditable="true"]');
    const cell = editable && editable !== this.view.contentDOM ? editable : null;
    const collapsed = cell ? !!range?.collapsed : selection.main.empty;
    let rect = withinEditor ? range!.getBoundingClientRect() : null;
    if (!rect || (!rect.width && !rect.height)) {
      const start = this.view.coordsAtPos(selection.main.from), end = this.view.coordsAtPos(selection.main.to);
      if (start && end) rect = new DOMRect(Math.min(start.left, end.left), Math.min(start.top, end.top),
        Math.max(start.right, end.right) - Math.min(start.left, end.left), Math.max(start.bottom, end.bottom) - Math.min(start.top, end.top));
    }
    return { selection, doc: this.view.state.doc, range: withinEditor ? range!.cloneRange() : null, cell,
      collapsed, withinEditor: withinEditor || this.view.hasFocus,
      text: cell ? range?.toString() ?? '' : this.view.state.sliceDoc(selection.main.from, selection.main.to), rect };
  }
  private invalidateCaret() { this.generation++; this.caret = null; }
  update(update: ViewUpdate) {
    if (update.docChanged || update.selectionSet) {
      this.invalidateCaret();
      clearTimeout(this.selectionTimer);
      this.selectionTimer = 0;
      if (!update.docChanged && !update.state.selection.main.empty &&
        update.transactions.some(tr => tr.isUserEvent('select'))) {
        this.deferSelectionRender();
      }
    }
    this.schedule();
  }
  private deferSelectionRender() {
    // Native handles can move without DOM touch events or a new CM range (tables).
    // Keep range text/layout reads out of drag frames and resume after a pause.
    clearTimeout(this.selectionTimer);
    cancelAnimationFrame(this.frame);
    this.frame = 0;
    this.bar.style.display = 'none';
    this.selectionTimer = window.setTimeout(() => {
      this.selectionTimer = 0;
      this.schedule();
    }, 120);
  }
  private async context() {
    if (!this.settings.enabled || this.suspended || this.failed || !this.inEditorFocus()) return;
    const before = this.read();
    const generation = ++this.generation;
    const result = await this.request('read');
    if (this.disposed || this.suspended || generation !== this.generation || before.doc !== this.view.state.doc ||
      !before.selection.eq(this.view.state.selection)) return;
    this.canPaste = !!result.text;
    this.caret = before.collapsed ? before.selection : null;
    this.schedule();
  }
  private schedule() {
    if (this.frame || this.selectionTimer || this.disposed) return;
    this.frame = requestAnimationFrame(() => { this.frame = 0; this.render(); });
  }
  dismiss() { this.invalidateCaret(); this.snapshot = null; this.page = 0; this.styleProperty = null; this.bar.style.display = 'none'; }
  private render() {
    if (this.busy) return;
    if (this.disposed || !this.settings.enabled || this.suspended || this.failed || this.view.composing || !this.inEditorFocus()) {
      this.bar.style.display = 'none'; this.page = 0; return;
    }
    const snap = this.read();
    if (snap.collapsed || snap.cell || !snap.text.trim()) this.styleProperty = null;
    if (!shouldShowSelectionToolbar({ editorReady: !this.disposed, suspended: this.suspended || this.failed || this.view.composing || !this.inEditorFocus(),
      snapshot: snap, caretSession: !!this.caret })) { this.bar.style.display = 'none'; this.page = 0; return; }
    this.snapshot = snap;
    const canWrite = !this.view.state.readOnly;
    const basic = getSelectionToolbarCommands({ hasSelection: !snap.collapsed, canPaste: this.canPaste, canWrite }).map(c => c.commandId);
    const custom = !snap.collapsed && snap.text.trim() && canWrite && !snap.cell ? (this.styleProperty
      ? this.styleProperty === 'fontSize'
        ? ['fontSize:default', ...fontSizePalette.map(size => `fontSize:${size}`)]
        : [...quickPalette(this.styleProperty).map(color => `color:${color}`), 'customColor']
      : [...this.settings.commands, 'color', 'backgroundColor', 'fontSize']) : [];
    const pages = paginateSelectionCommands(custom, computeSelectionToolbarPageCapacity(innerWidth), { leadingBackArrow: this.settings.rows === 1 || !!this.styleProperty });
    const total = this.settings.rows === 2 ? Math.max(1, pages.length) : pages.length + 1;
    this.page = Math.min(this.page, total - 1);
    const signature = JSON.stringify([basic, pages, this.page, this.settings.rows, this.styleProperty]);
    if (signature !== this.signature) {
      const oldFocus = this.bar.contains(document.activeElement);
      this.signature = signature;
      this.bar.replaceChildren();
      this.bar.classList.toggle('is-two-row', this.settings.rows === 2 && custom.length > 0);
      const row = () => { const r = document.createElement('div'); r.className = 'selection-toolbar-row'; this.bar.append(r); return r; };
      if (this.settings.rows === 2 || this.page === 0) {
        const r = row(); basic.forEach(id => this.button(r, id, () => void this.run(id)));
        if (this.settings.rows === 1 && total > 1) this.button(r, 'next', () => this.flip(1));
      }
      if (custom.length && (this.settings.rows === 2 || this.page > 0)) {
        const r = row();
        if (this.page > 0 || this.styleProperty) this.button(r, 'back', () => this.flip(-1));
        (pages[this.settings.rows === 2 ? this.page : this.page - 1] ?? []).forEach(id => this.button(r, id, () => void this.run(id)));
        if (this.page < total - 1) this.button(r, 'next', () => this.flip(1));
      }
      const status = document.createElement('span'); status.className = 'selection-page-status';
      status.setAttribute('aria-live', 'polite'); status.textContent = `${this.styleProperty ? labels[this.styleProperty] : ''} 第 ${this.page + 1} 页，共 ${total} 页`; this.bar.append(status);
      if (oldFocus) this.bar.querySelector('button')?.focus({ preventScroll: true });
    }
    this.bar.hidden = false; this.bar.style.display = 'flex';
    const placed = computeSelectionToolbarPlacement(snap.rect!, { width: this.bar.offsetWidth, height: this.bar.offsetHeight }, { width: innerWidth, height: innerHeight });
    this.bar.style.left = `${placed.left}px`; this.bar.style.top = `${placed.top}px`;
  }
  private flip(delta: number) {
    this.toolbarTouchFlipped = true;
    this.restoreSnapshotSelection();
    if (delta < 0 && this.styleProperty) {
      this.styleProperty = null;
      this.page = this.styleReturnPage;
    } else this.page += delta;
    this.schedule();
  }
  private restoreSnapshotSelection() {
    const snap = this.snapshot;
    if (!snap || snap.collapsed || snap.cell || snap.doc !== this.view.state.doc) return;
    if (this.view.state.selection.main.empty) {
      this.view.dispatch({ selection: snap.selection, annotations: Transaction.addToHistory.of(false) });
    }
  }
  private button(row: HTMLElement, id: string, run: () => void) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'selection-toolbar-button';
    button.title = labels[id] ?? (id === 'back' ? (this.styleProperty ? '返回上一个状态' : '上一页') : '展开更多工具'); button.setAttribute('aria-label', button.title);
    const palette = quickPalette(this.styleProperty);
    const names = quickNames(this.styleProperty);
    const colorIndex = id.startsWith('color:') ? palette.indexOf(id.slice(6)) : -1;
    const fontSizeIndex = id.startsWith('fontSize:') ? fontSizePalette.indexOf(id.slice(9)) : -1;
    const isDefaultFontSize = id === 'fontSize:default';
    if (id.startsWith('color:') && colorIndex >= 0) {
      button.title = `${labels[this.styleProperty!]}：${names[colorIndex]}`;
      button.setAttribute('aria-label', button.title);
    } else if (isDefaultFontSize) {
      button.title = `${labels.fontSize}：默认`;
      button.setAttribute('aria-label', button.title);
    } else if (fontSizeIndex >= 0) {
      button.title = `${labels.fontSize}：${fontSizeNames[fontSizeIndex + 1]}`;
      button.setAttribute('aria-label', button.title);
    }
    button.dataset.commandId = id;
    if (glyphs[id]) {
      const iconStyle = ['color', 'backgroundColor', 'fontSize'].includes(id) ? ' style="stroke:none"' : '';
      const iconClass = id === 'back' || id === 'next' ? ' selection-toolbar-icon-pager' :
        id === 'color' || id === 'backgroundColor' || id === 'fontSize' ? ' selection-toolbar-icon-format' : '';
      button.innerHTML = `<svg class="selection-toolbar-icon${iconClass}" viewBox="${iconViewBoxes[id] ?? '0 0 24 24'}" aria-hidden="true"${iconStyle}>${glyphs[id]}</svg>`;
    }
    else if (colorIndex >= 0) {
      button.textContent = '●'; button.style.color = palette[colorIndex]; button.style.fontSize = '28px';
      button.style.webkitTextStroke = '1px var(--kl-shell-muted, #888)';
    } else if (isDefaultFontSize) button.textContent = fontSizeNames[0];
    else if (fontSizeIndex >= 0) button.textContent = fontSizeNames[fontSizeIndex + 1];
    else if (id !== 'customColor') button.textContent = short[id] ?? (id === 'back' ? '‹' : '›');
    if (id === 'customColor') {
      const picker = document.createElement('input');
      picker.type = 'color'; picker.value = palette[0]; picker.className = 'selection-toolbar-color-input';
      picker.setAttribute('aria-label', labels.customColor);
      picker.addEventListener('change', () => void this.run(`customColor:${picker.value}`));
      button.append(picker);
      let touchHandledAt = 0;
      const openPicker = () => {
        if (picker.showPicker) {
          try { picker.showPicker(); return; } catch { /* Fall through to WebView's native click path. */ }
        }
        picker.click();
      };
      button.addEventListener('touchstart', () => button.classList.add('is-pressed'));
      button.addEventListener('touchcancel', () => button.classList.remove('is-pressed'));
      button.addEventListener('touchend', event => {
        event.preventDefault(); button.classList.remove('is-pressed'); touchHandledAt = Date.now();
        openPicker();
      });
      button.addEventListener('click', () => { if (Date.now() - touchHandledAt > 500) openPicker(); });
    } else {
      let touchHandledAt = 0;
      button.addEventListener('touchstart', () => button.classList.add('is-pressed'));
      button.addEventListener('touchcancel', () => button.classList.remove('is-pressed'));
      button.addEventListener('touchend', event => {
        event.preventDefault(); button.classList.remove('is-pressed'); touchHandledAt = Date.now();
        const point = event.changedTouches[0], rect = button.getBoundingClientRect();
        if (point && point.clientX >= rect.left && point.clientX <= rect.right && point.clientY >= rect.top && point.clientY <= rect.bottom) run();
      });
      button.addEventListener('click', () => { if (Date.now() - touchHandledAt > 500) run(); });
    }
    row.append(button);
  }
  private outside(x: number, y: number) {
    if (this.busy || this.bar.style.display === 'none') return;
    const target = document.elementFromPoint(x, y);
    if (!target || this.bar.contains(target)) return;
    // Chromium owns body taps and selection handles. A touchend/native tap can
    // arrive after a new long-press range; rewriting it here destroys that range.
    if (!this.view.contentDOM.contains(target) || this.read().collapsed) this.dismiss();
    else this.schedule();
  }
  private async run(id: string) {
    if ((id === 'color' || id === 'backgroundColor' || id === 'fontSize' || id === 'fontSize:default' || id.startsWith('color:') || id.startsWith('fontSize:') || id.startsWith('customColor:')) && this.bar.style.display === 'none') return;
    this.restoreSnapshotSelection();
    const snap = this.snapshot;
    if (!snap || this.busy || !this.settings.enabled || this.suspended || snap.doc !== this.view.state.doc) return;
    this.busy = true;
    const generation = ++this.generation;
    const current = () => !this.disposed && !this.suspended && this.generation === generation && snap.doc === this.view.state.doc &&
      snap.selection.eq(this.view.state.selection) && (!snap.cell || (snap.cell.isConnected && snap.range?.toString() === snap.text));
    const customColor = id.startsWith('customColor:') ? safeColor(id.slice('customColor:'.length)) : null;
    try {
      if (id === 'color' || id === 'backgroundColor' || id === 'fontSize' || id === 'fontSize:default' || id.startsWith('color:') || id.startsWith('fontSize:') || customColor !== null) {
        if (!current() || snap.collapsed || !snap.text.trim() || snap.cell || this.view.state.readOnly || this.view.composing) return;
        if (id === 'color' || id === 'backgroundColor' || id === 'fontSize') {
          this.toolbarTouchFlipped = true;
          this.styleReturnPage = this.page;
          this.styleProperty = id as InlineStyleProperty;
          this.page = this.settings.rows === 2 ? 0 : 1;
        } else if (customColor !== null && this.styleProperty) {
          applySelectionInlineStyle(this.view, this.styleProperty, customColor);
        } else if (this.styleProperty) {
          const value = id === 'fontSize:default' ? null : id.slice(id.startsWith('fontSize:') ? 9 : 6);
          applySelectionInlineStyle(this.view, this.styleProperty, value);
        }
      } else if (id === 'selectAll') {
        const result = await this.request('selectAll');
        if (!result.ok && current()) this.execute('selectAll');
      } else if (id === 'copy' || id === 'cut' || id === 'paste') {
        if (id !== 'copy' && this.view.state.readOnly) return;
        const result = await this.request(id === 'paste' ? 'read' : 'write', { text: snap.text });
        if (!result.ok || !current() || (id === 'paste' && !result.text)) return;
        if (snap.cell && snap.range) {
          if (!snap.cell.isConnected) return;
          const dom = document.getSelection(); dom?.removeAllRanges(); dom?.addRange(snap.range);
          if (id !== 'copy') document.execCommand('insertText', false, id === 'cut' ? '' : result.text);
          else dom?.collapseToEnd();
        } else if (id === 'copy') {
          this.view.dispatch({ selection: EditorSelection.cursor(snap.selection.main.to), annotations: Transaction.addToHistory.of(false) });
        } else {
          this.view.dispatch({ changes: { from: snap.selection.main.from, to: snap.selection.main.to, insert: id === 'cut' ? '' : String(result.text).replace(/\r\n?/g, '\n') },
            selection: EditorSelection.cursor(snap.selection.main.from + (id === 'cut' ? 0 : String(result.text).replace(/\r\n?/g, '\n').length)), userEvent: id === 'cut' ? 'delete.cut' : 'input.paste' });
        }
        // Finishing a real Chromium ActionMode can clear the DOM caret asynchronously.
        // Restore only if this operation still owns the post-command selection.
        const after = this.read(), finishGeneration = this.generation;
        let lostOwnership = false;
        const watch = () => {
          const live = document.getSelection();
          if (live?.rangeCount && !live.isCollapsed) lostOwnership = true;
        };
        document.addEventListener('selectionchange', watch);
        try {
          const finished = await this.request('finish');
          await new Promise(requestAnimationFrame);
          if (finished.ok && !lostOwnership && !this.disposed && !this.suspended &&
            finishGeneration === this.generation && after.doc === this.view.state.doc && after.selection.eq(this.view.state.selection)) {
            if (after.cell && after.range?.collapsed && after.cell.isConnected) {
              const dom = document.getSelection(); dom?.removeAllRanges(); dom?.addRange(after.range);
            } else if (!after.cell) {
              this.view.dispatch({ selection: after.selection, annotations: Transaction.addToHistory.of(false) });
            }
          }
        } finally { document.removeEventListener('selectionchange', watch); }
      } else if (Object.prototype.hasOwnProperty.call(commandLabels, id) && !this.view.state.readOnly && !snap.cell) {
        this.view.dispatch({ selection: snap.selection, annotations: Transaction.addToHistory.of(false) }); this.execute(id);
      }
    } finally { this.busy = false; this.caret = null; this.schedule(); }
  }
  dispose() {
    this.dismiss(); this.disposed = true; this.abort.abort(); cancelAnimationFrame(this.frame); clearTimeout(this.selectionTimer);
    this.bar.remove(); this.style.remove();
    for (const request of this.pending.values()) { clearTimeout(request.timer); request.resolve({ ok: false }); }
    this.pending.clear(); if (window.KardLeafSelection === this.native) delete window.KardLeafSelection;
  }
}
