/**
 * Wikilink 装饰扩展（Obsidian 风格）：
 * - `[[` 和 `]]` 在 cursor 远离时 hidden（Decoration.replace），仅显示 title 文本
 * - title 加 `cm-wikilink` class（蓝色下划线，与普通链接视觉一致）
 * - cursor / selection 进入 `[[xxx]]` 范围内时 reveal 原始 markdown
 *
 * 不依赖 lezer markdown 节点（标准 markdown 不识别 wikilink），改用 regex
 * 在每个 visibleRange 内扫描。性能：visibleRange 是 viewport 窗口，doc
 * 增量更新时只重扫此窗口。
 */
import { Prec, RangeSetBuilder, type Extension } from '@codemirror/state';
import {
  Decoration,
  type DecorationSet,
  EditorView,
  ViewPlugin,
  type ViewUpdate,
  WidgetType,
} from '@codemirror/view';

const WIKILINK_REGEX = /\[\[([^\[\]\n]+)\]\]/g;

/**
 * Widget displaying `[[` or `]]` in reveal mode. Required (instead of
 * Decoration.mark) because lezer markdown classifies the outer `[` and `]`
 * of `[[xxx]]` as `LinkMark`, which inline-rendering hides via
 * `Decoration.replace({})`. A mark decoration cannot override that hide;
 * a widget replace decoration with higher precedence does.
 */
class WikilinkBracketWidget extends WidgetType {
  constructor(readonly text: string) {
    super();
  }
  toDOM() {
    const el = document.createElement('span');
    el.className = 'cm-wikilink-bracket';
    el.textContent = this.text;
    return el;
  }
  eq(other: WikilinkBracketWidget) {
    return other.text === this.text;
  }
  ignoreEvent() {
    return false;
  }
}

const hideMark = Decoration.replace({});
const decoratedTitleMark = Decoration.mark({ class: 'cm-wikilink' });
const revealedTitleMark = Decoration.mark({ class: 'cm-wikilink-revealed' });
const leftBracketDeco = Decoration.replace({ widget: new WikilinkBracketWidget('[[') });
const rightBracketDeco = Decoration.replace({ widget: new WikilinkBracketWidget(']]') });

function buildDecorations(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  const sel = view.state.selection.main;
  const selFrom = Math.min(sel.from, sel.to);
  const selTo = Math.max(sel.from, sel.to);

  for (const range of view.visibleRanges) {
    const text = view.state.doc.sliceString(range.from, range.to);
    // 重置 lastIndex 防 stateful regex 漏匹配
    WIKILINK_REGEX.lastIndex = 0;
    let m: RegExpExecArray | null;
    // biome-ignore lint/suspicious/noAssignInExpressions: classic regex iter
    while ((m = WIKILINK_REGEX.exec(text)) !== null) {
      const start = range.from + m.index;
      const titleStart = start + 2;
      const titleEnd = start + 2 + m[1].length;
      const end = start + m[0].length;

      // Reveal: cursor / selection touches the wikilink range → 显示原始 markdown
      // brackets 用 widget replace 显示（覆盖 inline-rendering 对 LinkMark 的 hide）
      const touches = selTo >= start && selFrom <= end;
      if (touches) {
        builder.add(start, titleStart, leftBracketDeco);
        builder.add(titleStart, titleEnd, revealedTitleMark);
        builder.add(titleEnd, end, rightBracketDeco);
        continue;
      }

      builder.add(start, titleStart, hideMark); // hide [[
      builder.add(titleStart, titleEnd, decoratedTitleMark); // mark title (pointer)
      builder.add(titleEnd, end, hideMark); // hide ]]
    }
  }
  return builder.finish();
}

const wikilinkTheme = EditorView.theme({
  // 装饰状态：cursor: pointer 提示可点击跳转
  // 颜色 + 下划线由外层 `.cm-ext-link`（inline-rendering 给 lezer Link 节点加的）
  // 提供，避免我们再叠加一层下划线导致重影
  '.cm-wikilink': {
    cursor: 'pointer',
  },
  // Reveal 状态：cursor: text 编辑模式
  '.cm-wikilink-revealed': {
    cursor: 'text',
  },
  // Reveal 状态下方括号：dim 灰，text cursor
  '.cm-wikilink-bracket': {
    opacity: 0.6,
    cursor: 'text',
  },
});

export function createWikilinkDecorationExtension(): Extension {
  const plugin = ViewPlugin.fromClass(
    class {
      decorations: DecorationSet;
      constructor(view: EditorView) {
        this.decorations = buildDecorations(view);
      }
      update(u: ViewUpdate) {
        if (u.docChanged || u.viewportChanged || u.selectionSet) {
          this.decorations = buildDecorations(u.view);
        }
      }
    },
    { decorations: (v) => v.decorations },
  );

  // Prec.highest 让 wikilink 装饰先 apply，bracket widget replace 才能
  // 覆盖 inline-rendering 对 LinkMark 的 hide replace
  return [Prec.highest(plugin), wikilinkTheme];
}
