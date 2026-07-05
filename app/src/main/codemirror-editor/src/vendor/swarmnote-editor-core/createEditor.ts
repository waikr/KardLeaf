import { closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete';
import { Compartment, EditorState, type Extension } from '@codemirror/state';
import { history, historyKeymap, indentWithTab, standardKeymap } from '@codemirror/commands';
import { markdown, markdownLanguage } from '@codemirror/lang-markdown';
import { GFM } from '@lezer/markdown';
import { languages as codeLanguages } from '@codemirror/language-data';
import { syntaxHighlighting } from '@codemirror/language';
import { searchKeymap } from '@codemirror/search';
import { EditorView, drawSelection, dropCursor, highlightActiveLine, keymap } from '@codemirror/view';
import { classHighlighter } from '@lezer/highlight';

import { collapseOnSelectionFacet, mouseSelectingExtension } from './core';
import { EditorControlImpl } from './EditorControl';
import { editorEventCallback, EditorEventType, type EditorEvent } from './events';
import { execCommandFacet, type ExecCommandRef } from './pluginHost';
import {
  computeSelectionFormatting,
  cycleHeading,
  insertLink,
  toggleBlockquote,
  toggleBold,
  toggleCode,
  toggleHighlight,
  toggleItalic,
  toggleList,
  toggleStrike,
} from './editorCommands';
import {
  createCollaborationExtension,
  createEditorSettingsExtension,
  createInlineRenderingExtension,
  createMarkdownDecorationExtension,
  createSearchExtension,
  markdownHighlightExtension,
} from './extensions';
import { createCtrlClickLinksExtension } from './extensions/links/ctrlClickLinksExtension';
import { createLinkTooltipExtension } from './extensions/links/linkTooltipExtension';
import { createWikilinkClickExtension } from './extensions/links/wikilinkClickExtension';
import { insertNewlineContinueMarkup } from './editorCommands/insertNewlineContinueMarkup';
import { markdownMathExtension } from './plugins/math/markdownMathExtension';
import { markdownFrontMatterExtension } from './extensions/markdownFrontMatterExtension';
import { createLineAwareClipboardExtension } from './extensions/lineAwareClipboardExtension';
import { createPluginHost, mergeHostCapabilities } from './pluginHost';
import type { EditorControl, EditorProps } from './types';
import { createSelectionRange } from './utils/selection';

(EditorView as unknown as { EDIT_CONTEXT: boolean }).EDIT_CONTEXT = false;

/** Wrap a void-returning command into a keymap `run` handler that reports "handled". */
function handled(fn: (view: EditorView) => void) {
  return (view: EditorView) => {
    fn(view);
    return true;
  };
}

/** Markdown formatting shortcuts exposed as a CM6 keymap. */
function buildFormatKeymap() {
  return [
    { key: 'Mod-b', run: handled(toggleBold) },
    { key: 'Mod-i', run: handled(toggleItalic) },
    { key: 'Mod-e', run: handled(toggleCode) },
    { key: 'Mod-Shift-x', run: handled(toggleStrike) },
    { key: 'Mod-Shift-=', run: handled(toggleHighlight) },
    { key: 'Mod-Shift-q', run: handled(toggleBlockquote) },
    { key: 'Mod-k', run: handled((view) => insertLink(view)) },
    { key: 'Mod-Shift-7', run: handled((view) => toggleList(view, 'ordered')) },
    { key: 'Mod-Shift-8', run: handled((view) => toggleList(view, 'unordered')) },
    { key: 'Mod-Shift-9', run: handled((view) => toggleList(view, 'check')) },
    { key: 'Mod-Shift-h', run: handled(cycleHeading) },
  ];
}

export function createEditor(
  parent: HTMLElement,
  props: EditorProps,
): EditorControl {
  const {
    initialText,
    initialSelection,
    settings,
    initialSearchState,
    collaboration,
    onEvent,
    imageResolver,
    uploadFile,
    host,
    plugins,
  } = props;

  // 合并 deprecated 顶层字段到 host 对象，双字段同存时 warn 一次。
  const effectiveHost = mergeHostCapabilities(host, imageResolver, uploadFile);
  // 由 plugin host 收集 register* 调用结果。
  const pluginHost = createPluginHost(effectiveHost, plugins);

  // execCommandFacet：plugin runtime 通过 facet 调 control.execCommand。
  // mutable ref 配合 facet 单例：control 创建后回填 fn。
  const execCommandRef: ExecCommandRef = { fn: null };

  // Plugin probing：通过 plugin id 集合决定 lezer / inline-rendering 等
  // 仍由 createEditor 直接控制的扩展是否启用。Block-level 渲染扩展由
  // plugin 自身经 registerCmExtensions 注入，不在此处重复挂载。
  const pluginIds = new Set((plugins ?? []).map((p) => p.id));
  const mathEnabled = pluginIds.has('math');

  const settingsRuntime = createEditorSettingsExtension(settings);

  // Two compartments so setScrollBottomMargin can swap scrollMargins +
  // content padding-bottom atomically. See EditorControl.setScrollBottomMargin.
  const scrollMarginsCompartment = new Compartment();
  const contentPaddingCompartment = new Compartment();
  const markdownExtensions = [
    ...GFM,
    ...(settings.features.markdownHighlight ? [markdownHighlightExtension] : []),
    ...(mathEnabled ? [markdownMathExtension] : []),
    markdownFrontMatterExtension,
  ];

  const extensions: Extension[] = [
    collapseOnSelectionFacet.of(true),
    mouseSelectingExtension,
    scrollMarginsCompartment.of(EditorView.scrollMargins.of(() => ({ bottom: 0 }))),
    contentPaddingCompartment.of(EditorView.contentAttributes.of({ style: 'padding-bottom: 0px' })),
    history(),
    drawSelection(),
    dropCursor(),
    highlightActiveLine(),
    closeBrackets(),
    markdown({
      base: markdownLanguage,
      extensions: markdownExtensions,
      codeLanguages,
    }),
    syntaxHighlighting(classHighlighter),
    settingsRuntime.extension,
    ...(settings.features.markdownDecorations
      ? [createMarkdownDecorationExtension()]
      : []),
    ...(settings.features.inlineRendering
      ? [createInlineRenderingExtension({ mathRendering: mathEnabled })]
      : []),
    // Block-level rendering（math / table / mermaid / admonition / codeBlock /
    // blockImage / rawHtml / smartPaste）由各自 plugin 通过 registerCmExtensions
    // 注入；createEditor 不再直接条件挂载。
    ...(onEvent
      ? [
          createCtrlClickLinksExtension((url) => {
            onEvent({ kind: EditorEventType.LinkOpen, url });
          }),
          createWikilinkClickExtension((target) => {
            // Wikilink target 复用 LinkOpen channel；host 端按 url 形态路由
            // （wikilink target 不会以 scheme `://` 开头，host 借此识别）
            onEvent({ kind: EditorEventType.LinkOpen, url: target });
          }),
        ]
      : []),
    createLinkTooltipExtension(),
    createLineAwareClipboardExtension(),
    ...(settings.features.collaboration
      ? createCollaborationExtension(collaboration)
      : []),
    ...(settings.features.search
      ? [
          createSearchExtension({
            onSearchStateChange(search, source) {
              onEvent?.({
                kind: EditorEventType.SearchStateChange,
                search,
                source,
              });
            },
          }),
        ]
      : []),
    keymap.of([
      ...closeBracketsKeymap,
      { key: 'Enter', run: insertNewlineContinueMarkup },
      indentWithTab,
      // Format shortcuts — placed before standardKeymap so our Mod-b / Mod-i
      // take precedence over any default single-selection boundary bindings.
      ...buildFormatKeymap(),
      ...standardKeymap,
      ...historyKeymap,
      ...(settings.features.search ? searchKeymap : []),
    ]),
  ];

  // 统一 emit：先调 host onEvent（可能 undefined），再分发给 plugin listener（ctx.on）。
  // Helper / plugin / widget 通过 `view.state.facet(editorEventCallback)` 拿到此 emit。
  const emit = (event: EditorEvent) => {
    onEvent?.(event);
    pluginHost.dispatchEvent(event);
  };

  // editorEventCallback facet 总是注入 emit（即使没有 host onEvent，plugin listener 也要工作）
  extensions.push(editorEventCallback.of(emit));
  extensions.push(execCommandFacet.of(execCommandRef));

  if (onEvent) {
    extensions.push(
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          onEvent({
            kind: EditorEventType.Change,
          });
        }

        if (update.selectionSet) {
          const selection = update.state.selection.main;
          onEvent({
            kind: EditorEventType.SelectionChange,
            selection: createSelectionRange(selection.anchor, selection.head),
          });
          onEvent({
            kind: EditorEventType.SelectionFormattingChange,
            formatting: computeSelectionFormatting(update.state),
          });
        }

        if (update.focusChanged) {
          onEvent({
            kind: update.view.hasFocus ? EditorEventType.Focus : EditorEventType.Blur,
          });
        }
      }),
    );
  }

  // 把 plugin 注册的 CM 扩展追加到末尾，使其相对于内置扩展具备更高优先级
  // (CM resolves duplicate facet values by registration order)。
  if (pluginHost.extensions.length) {
    extensions.push(...pluginHost.extensions);
  }

  const selection = initialSelection
    ? {
        anchor: initialSelection.anchor,
        head: initialSelection.head,
      }
    : undefined;

  // When collaboration is enabled, seed CM6's initial doc with the current Y.Text
  // content. y-codemirror.next's ySync extension only bridges observer events,
  // so any content that was already in the Y.Text at mount time would otherwise
  // be invisible in CM6 (doc='' would desync from a non-empty ytext).
  let initialDoc = initialText;
  if (collaboration) {
    const ydoc = collaboration.ydoc as { getText: (name: string) => { toString(): string } };
    initialDoc = ydoc.getText(collaboration.fragmentName ?? 'document').toString();
  }

  const view = new EditorView({
    state: EditorState.create({
      doc: initialDoc,
      selection,
      extensions,
    }),
    parent,
  });

  const control = new EditorControlImpl(view, {
    settingsRuntime,
    scrollMarginsCompartment,
    contentPaddingCompartment,
    pluginHost,
    onDestroy: onEvent
      ? () => {
          onEvent({ kind: EditorEventType.Remove });
        }
      : undefined,
  });

  // 回填 execCommandRef.fn，让 plugin runtime 通过 execCommandFacet 调到。
  execCommandRef.fn = (id, ...args) => control.execCommand(id, ...args);

  if (initialSearchState && settings.features.search) {
    control.setSearchState(initialSearchState, 'initialSearchState');
  }

  if (settings.autofocus || props.autofocus) {
    view.focus();
  }

  return control;
}

