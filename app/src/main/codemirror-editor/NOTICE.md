KardLeaf CodeMirror editor
==========================

This editor is a KardLeaf WebView adapter for the SwarmNote editor core.

Architecture reference:
- @swarmnote/editor-core, MIT license, https://github.com/swarm-apps/swarmnote-editor

KardLeaf keeps Android/Room/sync/history behavior outside the editor and
exposes a stable WebView bridge through window.KardLeafEditor. The live preview
pipeline is provided by the vendored SwarmNote editor-core extensions/plugins;
Markdown remains the single source of truth.


MarkText Android text selection toolbar
--------------------------------------
Source: user-provided local reference marktext-android-main (selection toolbar files).
Copyright (c) 2026 Renakoni, MIT. Full license: src/marktext/LICENSE; also embedded
in generated editor.js.LEGAL.txt and shipped with the editor assets.

selectionToolbar.ts preserves the original state table, placement and paging
functions. toolbar.ts replaces Vue/Muya/Capacitor bindings with KardLeaf DOM,
CodeMirror and asynchronous Android bridge adapters. selectionToolbar.css derives
from MobileSelectionToolbar.vue with theme variable substitutions. Android
SelectionActionMode and BetaSelectionToolbar are corresponding native ports.
Cut writes to Android's clipboard before deleting text. Beta range block commands
match existing SwarmNote command semantics without changing either editor core.

The existing SwarmNote selection toolbar plugin remains unmodified and is not
installed by KardLeaf. The rawHtml renderer adds one optional handlesEditableHtml
host callback so editable color/size spans keep real editable text instead of
whole-content widgets. Other vendor plugins, commands and IME paths are intact.

KardLeaf color and font-size operations use the existing selection toolbar pages.
inlineStyles.ts and SelectionInlineStyles.kt implement matching validated range
transformations; inlineStyleSpans.ts conceals tags and marks editable content.
No new dependency.
