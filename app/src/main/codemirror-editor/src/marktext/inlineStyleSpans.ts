import { ensureSyntaxTree, syntaxTree } from '@codemirror/language';
import { EditorSelection, StateField } from '@codemirror/state';
import { isolateHistory } from '@codemirror/commands';
import { Decoration, EditorView } from '@codemirror/view';
import { colorPalette, fontSizePalette, colorSpanAtCursor, colorSpanStyleChangeAtCursor, colorSelectionChange, colorStyle, isColorHtml, scanColorDocument, type InlineStyleProperty } from './inlineStyles';
import { mouseSelectingField } from '../vendor/swarmnote-editor-core/core';
import type { EditorPlugin } from '../vendor/swarmnote-editor-core';

export function applySelectionInlineStyle(view: EditorView, property: InlineStyleProperty, value: string | null): boolean {
  if (view.state.readOnly || view.composing || view.state.selection.ranges.length !== 1) return false;
  const selection = view.state.selection.main;
  if (selection.empty) return false;
  const change = colorSelectionChange(view.state.doc.toString(), selection.anchor, selection.head, property, value);
  if (!change) return false;
  // Fail closed if parsing is incomplete or the edit overlaps a structural widget's source.
  const tree = ensureSyntaxTree(view.state, change.to, 50);
  if (!tree) return false;
  let blocked = false;
  tree.iterate({ from: change.from, to: change.to, enter(node) {
    if (node.from < change.to && node.to > change.from && (/Code|Math|Table|Link|Image/.test(node.name) ||
      (node.name === 'HTMLBlock' && !isColorHtml(view.state.sliceDoc(node.from, node.to))))) blocked = true;
  } });
  if (blocked) return false;
  if (view.state.sliceDoc(change.from, change.to) !== change.insert) view.dispatch({
    changes: { from: change.from, to: change.to, insert: change.insert },
    selection: EditorSelection.range(change.anchor, change.head),
    userEvent: 'input.inlineStyle', annotations: isolateHistory.of('full'),
  });
  return true;
}

export function cycleInlineStyleAtCursor(view: EditorView, property: InlineStyleProperty): boolean {
  if (view.state.readOnly || view.composing || view.state.selection.ranges.length !== 1) return false;
  const selection = view.state.selection.main;
  if (!selection.empty) return false;
  const text = view.state.doc.toString();
  const span = colorSpanAtCursor(text, selection.head);
  if (!span) return false;
  const palette = property === 'fontSize' ? fontSizePalette : colorPalette;
  const currentIndex = span.colors[property] ? palette.indexOf(span.colors[property]!) : -1;
  const value = palette[(currentIndex + 1) % palette.length];
  return setInlineStyleAtCursor(view, property, value);
}

export function setInlineStyleAtCursor(view: EditorView, property: InlineStyleProperty, value: string | null): boolean {
  if (view.state.readOnly || view.composing || view.state.selection.ranges.length !== 1) return false;
  const selection = view.state.selection.main;
  if (!selection.empty) return false;
  const text = view.state.doc.toString();
  const change = colorSpanStyleChangeAtCursor(text, selection.head, property, value);
  if (!change || text.slice(change.from, change.to) === change.insert) return false;
  view.dispatch({
    changes: { from: change.from, to: change.to, insert: change.insert },
    userEvent: 'input.inlineStyle',
    annotations: isolateHistory.of('full'),
  });
  return true;
}

// Conceal only tag ranges. Content remains CodeMirror text, including during selection/IME.
export function inlineColorPlugin(): EditorPlugin {
  return { id: 'kardleaf.inlineColors', setup(ctx) {
    const build = (state: EditorView['state']) => {
      const text = state.doc.toString();
      if (!/<span\b/i.test(text)) return { decorations: Decoration.none, atomic: Decoration.none };
      const spans = scanColorDocument(text).spans;
      const tags = new Set<number>();
      syntaxTree(state).iterate({ enter(node) {
        if (/Code|Math/.test(node.name)) return false;
        if (node.name === 'HTMLBlock') {
          if (isColorHtml(state.sliceDoc(node.from, node.to))) for (const span of spans) {
            if (span.from >= node.from && span.to <= node.to) { tags.add(span.from); tags.add(span.closeFrom); }
          }
          return false;
        }
        if (node.name === 'HTMLTag') tags.add(node.from);
      } });
      const decorations = [], atomic = [];
      for (const span of spans) {
        if (!tags.has(span.from) || !tags.has(span.closeFrom)) continue;
        const hidden = Decoration.replace({});
        atomic.push(hidden.range(span.from, span.openTo), hidden.range(span.closeFrom, span.to));
        const style = colorStyle(span.colors);
        if (style && span.openTo < span.closeFrom) decorations.push(Decoration.mark({ attributes: { style }, class: 'kl-inline-color' }).range(span.openTo, span.closeFrom));
      }
      return { decorations: Decoration.set([...decorations, ...atomic], true), atomic: Decoration.set(atomic, true) };
    };
    ctx.registerCmExtensions([StateField.define({
      create: build,
      update(value, tr) {
        if (!tr.docChanged && tr.state.field(mouseSelectingField, false)) return value;
        return tr.docChanged || syntaxTree(tr.startState) !== syntaxTree(tr.state) || tr.startState.field(mouseSelectingField, false) ? build(tr.state) : value;
      },
      provide: field => [EditorView.decorations.from(field, value => value.decorations), EditorView.atomicRanges.of(view => view.state.field(field).atomic)],
    })]);
  } };
}
