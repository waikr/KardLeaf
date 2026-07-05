/**
 * Formatting Classes
 *
 * 给 Emphasis / StrongEmphasis / Strikethrough / Link/URL 节点添加 CSS class，
 * 实现斜体、加粗、删除线和链接的视觉样式。
 * 参考 Joplin addFormattingClasses.ts。
 */
import { Decoration, EditorView } from '@codemirror/view';
import type { InlineRenderingSpec, RevealStrategy } from './types';

const linkDecoration = Decoration.mark({ class: 'cm-ext-link' });
const strikethroughDecoration = Decoration.mark({ class: 'cm-ext-strikethrough' });
const strongDecoration = Decoration.mark({ class: 'cm-ext-strong' });
const emphasisDecoration = Decoration.mark({ class: 'cm-ext-em' });

export const formattingClassesTheme = EditorView.theme({
  '.cm-ext-link, .cm-ext-link span': {
    textDecoration: 'underline',
    cursor: 'pointer',
  },
  '.cm-ext-strikethrough, .cm-ext-strikethrough span': {
    textDecoration: 'line-through',
  },
  '.cm-ext-strong, .cm-ext-strong span': {
    fontWeight: '700',
  },
  '.cm-ext-em, .cm-ext-em span': {
    fontStyle: 'italic',
  },
});

export const addFormattingClasses: InlineRenderingSpec = {
  // URL 节点专门交给 replaceFormatCharacters 处理 conceal（光标外完全隐藏）。
  // 这里只装饰 Link 整段，让 link text（中文/英文）继承 .cm-ext-link 的 link 颜色 + 下划线。
  nodeNames: ['Link', 'Strikethrough', 'Emphasis', 'StrongEmphasis'],
  extension: {
    createDecoration(node) {
      if (node.name === 'Link') {
        return linkDecoration;
      }
      if (node.name === 'Strikethrough') {
        return strikethroughDecoration;
      }
      if (node.name === 'StrongEmphasis') {
        return strongDecoration;
      }
      if (node.name === 'Emphasis') {
        return emphasisDecoration;
      }
      return null;
    },
    getRevealStrategy(node): RevealStrategy {
      if (node.name === 'Link') return 'select';
      return 'active';
    },
  },
};
