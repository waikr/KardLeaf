KardLeaf CodeMirror editor
==========================

This editor is a KardLeaf WebView adapter for the SwarmNote editor core.

Architecture reference:
- @swarmnote/editor-core, MIT license, https://github.com/swarm-apps/swarmnote-editor

KardLeaf keeps Android/Room/sync/history behavior outside the editor and
exposes a stable WebView bridge through window.KardLeafEditor. The live preview
pipeline is provided by the vendored SwarmNote editor-core extensions/plugins;
Markdown remains the single source of truth.
