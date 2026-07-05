/**
 * Markdown 装饰扩展 - 语法高亮和样式
 * 
 * **功能：**
 * 通过遍历语法树，为各种 Markdown 元素添加 CSS 类名和装饰。
 * 支持标题、列表、代码块、引用、表格、Front Matter 等元素的视觉增强。
 * 
 * **核心技术：**
 * - ViewPlugin：监听视图变化并重建装饰
 * - Syntax Tree：遍历语法树节点匹配元素类型
 * - Decoration.line/mark：行级和内联装饰
 */
import { RangeSetBuilder, type Extension } from '@codemirror/state';
import { ensureSyntaxTree } from '@codemirror/language';
import {
  Decoration,
  type DecorationSet,
  EditorView,
  ViewPlugin,
  type ViewUpdate,
} from '@codemirror/view';

/** 已完成任务的行装饰（删除线 + 灰色） */
const taskCompletedDecoration = Decoration.line({ attributes: { class: 'cm-taskCompleted' } });
/** 任务完成行的正则表达式：`- [x]` 或 `* [X]` */
const TASK_COMPLETED_LINE = /^\s*[-*]\s\[[xX]\]/;

/**
 * 嵌套引用的行装饰数组（按深度索引）
 * 
 * **设计说明：**
 * Depth 0 = 外层引用，Depth 1 = 嵌套一次，以此类推。
 * 使用多个 CSS 渐变绘制堆叠的垂直条（匹配 Obsidian 的双条外观）。
 */
const blockquoteLineDecorations = [
  Decoration.line({ attributes: { class: 'cm-blockQuote cm-blockQuote-d0' } }),
  Decoration.line({ attributes: { class: 'cm-blockQuote cm-blockQuote-d1' } }),
  Decoration.line({ attributes: { class: 'cm-blockQuote cm-blockQuote-d2' } }),
  Decoration.line({ attributes: { class: 'cm-blockQuote cm-blockQuote-d3' } }),
];

/**
 * 行级装饰映射表
 * 
 * **键：** 语法树节点名称
 * **值：** 对应的行装饰对象
 */
const lineDecorations: Record<string, Decoration> = {
  OrderedList: Decoration.line({ attributes: { class: 'cm-orderedList' } }),
  BulletList: Decoration.line({ attributes: { class: 'cm-unorderedList' } }),
  ListItem: Decoration.line({ attributes: { class: 'cm-listItem' } }),
  FencedCode: Decoration.line({ attributes: { class: 'cm-codeBlock' } }),
  CodeBlock: Decoration.line({ attributes: { class: 'cm-codeBlock' } }),
  SetextHeading1: Decoration.line({ attributes: { class: 'cm-h1 cm-headerLine cm-header' } }),
  ATXHeading1: Decoration.line({ attributes: { class: 'cm-h1 cm-headerLine cm-header' } }),
  SetextHeading2: Decoration.line({ attributes: { class: 'cm-h2 cm-headerLine cm-header' } }),
  ATXHeading2: Decoration.line({ attributes: { class: 'cm-h2 cm-headerLine cm-header' } }),
  ATXHeading3: Decoration.line({ attributes: { class: 'cm-h3 cm-headerLine cm-header' } }),
  ATXHeading4: Decoration.line({ attributes: { class: 'cm-h4 cm-headerLine cm-header' } }),
  ATXHeading5: Decoration.line({ attributes: { class: 'cm-h5 cm-headerLine cm-header' } }),
  ATXHeading6: Decoration.line({ attributes: { class: 'cm-h6 cm-headerLine cm-header' } }),
  TableHeader: Decoration.line({ attributes: { class: 'cm-tableHeader' } }),
  TableDelimiter: Decoration.line({ attributes: { class: 'cm-tableDelimiter' } }),
  TableRow: Decoration.line({ attributes: { class: 'cm-tableRow' } }),
  FrontMatter: Decoration.line({ attributes: { class: 'cm-frontMatter' } }),
  FrontMatterMarker: Decoration.line({ attributes: { class: 'cm-frontMatter cm-frontMatterMarker' } }),
  FrontMatterContent: Decoration.line({ attributes: { class: 'cm-frontMatter cm-frontMatterContent' } }),
};

/**
 * 内联标记装饰映射表
 * 
 * **键：** 语法树节点名称
 * **值：** 对应的内联装饰对象
 */
const markDecorations: Record<string, Decoration> = {
  InlineCode: Decoration.mark({ attributes: { class: 'cm-inlineCode', spellcheck: 'false' } }),
  URL: Decoration.mark({ attributes: { class: 'cm-url', spellcheck: 'false' } }),
  TaskMarker: Decoration.mark({ attributes: { class: 'cm-taskMarker' } }),
  HorizontalRule: Decoration.mark({ attributes: { class: 'cm-hr' } }),
  Highlight: Decoration.mark({ attributes: { class: 'cm-highlighted' } }),
  HeaderMark: Decoration.mark({ attributes: { class: 'cm-headerMark' } }),
  QuoteMark: Decoration.mark({ attributes: { class: 'cm-quoteMark' } }),
};

/**
 * Markdown 主题样式
 * 
 * **包含的样式：**
 * - 标题层级（h1-h6）的字体大小和间距
 * - 行内代码的背景色和边框
 * - 代码块的背景色
 * - 嵌套引用的垂直条（使用渐变实现）
 * - URL 下划线、任务标记、高亮等
 * - 表格和 Front Matter 的样式
 */
const markdownTheme = EditorView.theme({
  '.cm-headerLine': {
    fontWeight: '700',
    lineHeight: '1.3',
  },
  '.cm-h1': {
    fontSize: '1.9em',
    letterSpacing: '-0.03em',
    paddingTop: '12px',
    paddingBottom: '4px',
  },
  '.cm-h2': {
    fontSize: '1.55em',
    letterSpacing: '-0.02em',
    paddingTop: '12px',
  },
  '.cm-h3': {
    fontSize: '1.35em',
    letterSpacing: '-0.01em',
    paddingTop: '12px',
  },
  '.cm-h4, .cm-h5, .cm-h6': {
    fontSize: '1.1em',
    paddingTop: '8px',
  },
  '.cm-inlineCode': {
    backgroundColor: 'rgba(127, 127, 127, 0.12)',
    borderRadius: '4px',
    fontFamily: 'monospace',
    padding: '0.1em 0.4em',
    border: '1px solid rgba(127, 127, 127, 0.18)',
  },
  '.cm-codeBlock': {
    backgroundColor: 'rgba(127, 127, 127, 0.1)',
  },
  // Blockquote bars are drawn via background-image gradients (one per depth bar).
  // Each bar is 2px wide, anchored at fixed x positions: 0, 14px, 28px, 42px.
  '.cm-blockQuote-d0': {
    backgroundImage:
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6))',
    backgroundSize: '2px 100%',
    backgroundPosition: '0 0',
    backgroundRepeat: 'no-repeat',
    paddingLeft: '10px',
  },
  '.cm-blockQuote-d1': {
    backgroundImage:
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6)), ' +
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6))',
    backgroundSize: '2px 100%, 2px 100%',
    backgroundPosition: '0 0, 14px 0',
    backgroundRepeat: 'no-repeat, no-repeat',
    paddingLeft: '24px',
  },
  '.cm-blockQuote-d2': {
    backgroundImage:
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6)), ' +
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6)), ' +
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6))',
    backgroundSize: '2px 100%, 2px 100%, 2px 100%',
    backgroundPosition: '0 0, 14px 0, 28px 0',
    backgroundRepeat: 'no-repeat, no-repeat, no-repeat',
    paddingLeft: '38px',
  },
  '.cm-blockQuote-d3': {
    backgroundImage:
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6)), ' +
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6)), ' +
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6)), ' +
      'linear-gradient(rgba(180, 140, 55, 0.6), rgba(180, 140, 55, 0.6))',
    backgroundSize: '2px 100%, 2px 100%, 2px 100%, 2px 100%',
    backgroundPosition: '0 0, 14px 0, 28px 0, 42px 0',
    backgroundRepeat: 'no-repeat, no-repeat, no-repeat, no-repeat',
    paddingLeft: '52px',
  },
  '.cm-url': {
    textDecoration: 'underline',
  },
  '.cm-headerMark': {
    opacity: '0.35',
    marginRight: '0.25em',
  },
  '.cm-quoteMark': {
    opacity: '0.45',
  },
  '.cm-taskCompleted, .cm-taskCompleted span': {
    textDecoration: 'line-through',
    color: 'rgba(127, 127, 127, 0.75)',
  },
  '.cm-taskMarker': {
    fontWeight: '700',
  },
  '.cm-highlighted': {
    backgroundColor: 'rgba(255, 200, 15, 0.32)',
    borderRadius: '2px',
  },
  '.cm-tableHeader, .cm-tableRow, .cm-tableDelimiter': {
    fontFamily: 'monospace',
    fontSize: '0.95em',
  },
  '.cm-tableHeader': {
    fontWeight: '700',
  },
  '.cm-tableDelimiter': {
    color: 'rgba(127, 127, 127, 0.5)',
  },
  '.cm-frontMatter': {
    color: 'rgba(127, 127, 127, 0.65)',
    fontFamily: 'monospace',
    fontSize: '0.88em',
    borderLeft: '2px solid rgba(127, 127, 127, 0.2)',
    paddingLeft: '8px',
  },
  '.cm-frontMatterMarker': {
    color: 'rgba(127, 127, 127, 0.45)',
  },
});

/** 装饰描述接口 */
type DecorationDescription = {
  /** 起始位置 */
  from: number;
  /** 结束位置 */
  to: number;
  /** 装饰对象 */
  decoration: Decoration;
};

/**
 * 为指定范围内的每一行添加行级装饰
 * 
 * **工作原理：**
 * 从 from 到 to 遍历每一行，在每行的起始位置添加装饰。
 * 
 * @param decorations - 装饰数组（会被修改）
 * @param view - 编辑器视图
 * @param from - 起始位置
 * @param to - 结束位置
 * @param decoration - 要应用的行装饰
 */
function pushLineDecorations(
  decorations: DecorationDescription[],
  view: EditorView,
  from: number,
  to: number,
  decoration: Decoration,
) {
  let position = from;
  while (position <= to) {
    const line = view.state.doc.lineAt(position);
    decorations.push({
      from: line.from,
      to: line.from,
      decoration,
    });
    position = line.to + 1;  // 移动到下一行
  }
}

/**
 * 计算可见区域内的所有装饰
 * 
 * **工作流程：**
 * 1. 遍历可见区域的语法树
 * 2. 对每个节点检查是否有对应的行装饰或内联装饰
 * 3. 特殊处理：
 *    - Blockquote：计算嵌套深度，应用对应的垂直条样式
 *    - ListItem：检测是否为已完成任务，应用删除线
 * 4. 按位置排序并构建最终的装饰集
 * 
 * @param view - 编辑器视图
 * @returns 装饰集合
 */
function computeDecorations(view: EditorView): DecorationSet {
  const decorations: DecorationDescription[] = [];

  // 遍历所有可见区域
  for (const { from, to } of view.visibleRanges) {
    ensureSyntaxTree(view.state, to)?.iterate({
      from,
      to,
      enter(node) {
        // 计算节点在可见区域内的实际范围
        const visibleFrom = Math.max(from, node.from);
        const visibleTo = Math.min(to, node.to);

        // 检查是否有行级装饰
        const lineDecoration = lineDecorations[node.name];
        if (lineDecoration) {
          pushLineDecorations(decorations, view, visibleFrom, visibleTo, lineDecoration);
        }

        // 特殊处理：嵌套引用（带深度感知）
        // 外层 = 单条，嵌套 = 多条垂直条
        if (node.name === 'Blockquote') {
          // 计算嵌套深度（向上遍历父节点）
          let depth = 0;
          let p = node.node.parent;
          while (p) {
            if (p.name === 'Blockquote') depth++;
            p = p.parent;
          }
          // 选择对应深度的装饰（最大到 d3）
          const idx = Math.min(depth, blockquoteLineDecorations.length - 1);
          pushLineDecorations(decorations, view, visibleFrom, visibleTo, blockquoteLineDecorations[idx]);
        }

        // 特殊处理：已完成的任务列表项
        // 检测 ListItem 是否以 `[x]` 开头，如果是则应用删除线和灰色
        // 在 ListItem 上检查（而不是 TaskMarker），这样装饰覆盖整行
        if (node.name === 'ListItem') {
          const lineText = view.state.doc.lineAt(node.from).text;
          if (TASK_COMPLETED_LINE.test(lineText)) {
            pushLineDecorations(decorations, view, visibleFrom, visibleTo, taskCompletedDecoration);
          }
        }

        // 检查是否有内联标记装饰
        const markDecoration = markDecorations[node.name];
        if (markDecoration && visibleFrom < visibleTo) {
          decorations.push({
            from: visibleFrom,
            to: visibleTo,
            decoration: markDecoration,
          });
        }
      },
    });
  }

  // 按位置排序（先按 from，再按 to）
  decorations.sort((left, right) => {
    const fromDiff = left.from - right.from;
    if (fromDiff !== 0) {
      return fromDiff;
    }

    return left.to - right.to;
  });

  // 构建最终的装饰集
  const builder = new RangeSetBuilder<Decoration>();
  for (const decoration of decorations) {
    builder.add(decoration.from, decoration.to, decoration.decoration);
  }

  return builder.finish();
}

/**
 * Markdown 装饰插件
 * 
 * **功能：**
 * 监听视图变化并在需要时重新计算装饰。
 * 
 * **更新条件：**
 * - 文档内容变化（docChanged）
 * - 视口变化（viewportChanged，用户滚动或窗口大小改变）
 */
const markdownDecorationPlugin = ViewPlugin.fromClass(
  class {
    /** 当前装饰集 */
    decorations: DecorationSet;

    /**
     * 初始化时计算装饰
     * 
     * @param view - 编辑器视图
     */
    constructor(view: EditorView) {
      this.decorations = computeDecorations(view);
    }

    /**
     * 视图更新时的处理
     * 
     * @param update - 视图更新对象
     */
    update(update: ViewUpdate) {
      // 仅在文档或视口变化时重新计算
      if (update.docChanged || update.viewportChanged) {
        this.decorations = computeDecorations(update.view);
      }
    }
  },
  {
    // 暴露装饰集给 CodeMirror
    decorations: (value) => value.decorations,
  },
);

/**
 * 创建 Markdown 装饰扩展
 * 
 * **功能：**
 * 为 Markdown 元素添加视觉增强（标题样式、代码高亮、引用条等）。
 * 
 * @returns CodeMirror 扩展数组
 */
export function createMarkdownDecorationExtension(): Extension {
  return [markdownTheme, markdownDecorationPlugin];
}
