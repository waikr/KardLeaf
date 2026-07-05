/**
 * 块级数学公式渲染扩展 - 使用 KaTeX
 *
 * **功能：**
 * 将多行的 `$$...$$` 块级数学公式渲染为实际的数学公式显示。
 * 
 * **为什么需要这个扩展？**
 * 
 * BlockMath 节点（跨越多行的 `$$...$$`）需要 **block** 级别的装饰，
 * 而 CodeMirror 只接受来自 StateField 的 block 装饰 —— 内联的
 * `replaceMathFormulas` 规范运行在 ViewPlugin 内部，对于跨行 widget 会静默失败，
 * 所以块级数学公式通过这个专用扩展处理。
 *
 * 内联数学公式（`$...$`）仍然由 `replaceMathFormulas` 处理，因为它保持在单行上。
 */
import { ensureSyntaxTree } from '@codemirror/language';
import {
  type EditorState,
  type Extension,
  RangeSetBuilder,
  StateField,
} from '@codemirror/state';
import {
  Decoration,
  type DecorationSet,
  EditorView,
  WidgetType,
} from '@codemirror/view';
// KaTeX 自带样式表；没有它的话，MathML <annotation> 会脱离其
// `position: absolute; clip` 隐藏规则，与可见的 KaTeX 输出一起渲染
// （在同一行上同时显示渲染后的公式和原始 LaTeX 源码）。
import 'katex/dist/katex.css';

/** KaTeX 模块缓存 */
let katexModule: typeof import('katex') | null = null;

/**
 * 懒加载 KaTeX 模块
 * 
 * @returns KaTeX 模块
 */
async function loadKaTeX() {
  if (!katexModule) {
    katexModule = await import('katex');
  }
  return katexModule.default ?? katexModule;
}

/**
 * 块级数学公式 Widget
 * 
 * **功能：**
 * 渲染块级数学公式卡片，支持点击编辑。
 * 
 * **contentFrom / contentTo：**
 * 描述 LaTeX 内容范围（不包括周围的 `$$` 分隔符行）。
 * 点击卡片时选中此区域 —— 匹配 Obsidian 的“仅选中公式主体”行为。
 */
class BlockMathWidget extends WidgetType {
  constructor(
    /** LaTeX 源码 */
    private readonly tex: string,
    /** 内容起始位置 */
    private readonly contentFrom: number,
    /** 内容结束位置 */
    private readonly contentTo: number,
  ) {
    super();
  }

  /**
   * 判断两个 widget 是否相等
   * 
   * @param other - 另一个 BlockMathWidget
   * @returns 如果所有属性都相同则返回 true
   */
  eq(other: BlockMathWidget) {
    return (
      other.tex === this.tex &&
      other.contentFrom === this.contentFrom &&
      other.contentTo === this.contentTo
    );
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 层级：**
   * ```
   * div.cm-math-block-card
   *   div.cm-math-block（公式渲染区域）
   *   button.cm-math-block-edit（编辑按钮，Lucide code-2 图标）
   * ```
   * 
   * @param view - 编辑器视图
   * @returns 卡片容器元素
   */
  toDOM(view: EditorView) {
    // 创建卡片容器
    const card = document.createElement('div');
    card.className = 'cm-math-block-card';

    // 创建公式渲染区域
    const formula = document.createElement('div');
    formula.className = 'cm-math-block';
    formula.textContent = this.tex;  // 初始显示原始 LaTeX

    // 创建编辑按钮
    const editBtn = document.createElement('button');
    editBtn.className = 'cm-math-block-edit';
    editBtn.type = 'button';
    editBtn.title = '编辑源码';
    // Lucide `code-2` 图标 —— 保持内联以避免将 lucide-react 拉入编辑器子模块
    editBtn.innerHTML =
      '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m18 16 4-4-4-4"/><path d="m6 8-4 4 4 4"/><path d="m14.5 4-5 16"/></svg>';

    card.appendChild(formula);
    card.appendChild(editBtn);

    // 异步加载 KaTeX 并渲染公式
    void loadKaTeX().then((katex) => {
      // 检查元素是否仍然连接到 DOM
      if (!formula.isConnected) return;
      try {
        formula.textContent = '';  // 清空占位文本
        // 使用 KaTeX 渲染公式（displayMode: true 表示块级模式）
        katex.render(this.tex, formula, { displayMode: true, throwOnError: false });
      } catch {
        // 渲染失败：回退到显示原始 LaTeX 并添加错误样式
        formula.textContent = this.tex;
        formula.classList.add('cm-math-error');
      }
    });

    // 进入源码模式的处理函数
    const enterSource = (event: Event) => {
      event.preventDefault();
      event.stopPropagation();
      // 选中公式内容区域（不包括 $$ 分隔符）
      view.dispatch({
        selection: { anchor: this.contentFrom, head: this.contentTo },
        scrollIntoView: true,  // 滚动到可见区域
      });
      view.focus();  // 聚焦编辑器
    };

    // 点击编辑按钮或卡片本身都会进入源码模式
    editBtn.addEventListener('mousedown', enterSource);
    card.addEventListener('mousedown', enterSource);

    return card;
  }

  /**
   * 是否忽略事件
   * 
   * 返回 false 表示让 mousedown 事件通过我们的处理器运行；
   * 忽略其他所有事件，这样 CodeMirror 就不会根据 widget 点击重新定位光标。
   * 
   * @param event - 事件对象
   * @returns 如果不是 mousedown 则返回 true（忽略该事件）
   */
  ignoreEvent(event: Event) {
    return event.type !== 'mousedown';
  }
}

/**
 * 构建块级数学公式装饰集
 * 
 * **工作流程：**
 * 1. 遍历语法树，找到所有 BlockMath 节点
 * 2. 判断光标是否在公式块内
 * 3. 提取 LaTeX 内容（去除 $$ 分隔符）
 * 4. 根据光标位置决定渲染策略：
 *    - 光标在块内：源码可见，公式卡片显示在块后
 *    - 光标在块外：用公式卡片替换整个块
 * 
 * @param state - 编辑器状态
 * @returns 装饰集合
 */
function buildBlockMathDecorations(state: EditorState): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  // 确保语法树已构建（最多等待 100ms）
  const tree = ensureSyntaxTree(state, state.doc.length, 100);
  if (!tree) return builder.finish();

  const sel = state.selection.main;
  const cursorLine = state.doc.lineAt(sel.head).number;  // 光标所在行号

  // 遍历语法树
  tree.iterate({
    enter(node) {
      if (node.name !== 'BlockMath') return;
      
      // 获取公式块的行范围
      const fromLine = state.doc.lineAt(node.from).number;
      const toLine = state.doc.lineAt(node.to).number;
      const selFromLine = state.doc.lineAt(sel.from).number;
      const selToLine = state.doc.lineAt(sel.to).number;
      
      // 判断选区是否与公式块相交
      const intersects =
        (cursorLine >= fromLine && cursorLine <= toLine) ||  // 光标在块内
        (selFromLine <= toLine && selToLine >= fromLine);     // 选区与块重叠

      // 提取文本并解析 $$ 分隔符
      const text = state.sliceDoc(node.from, node.to);
      const startMatch = text.match(/^\$\$\s*/);  // 开头的 $$
      const endMatch = text.match(/\s*\$\$$/);    // 结尾的 $$
      const startLen = startMatch?.[0].length ?? 2;
      const endLen = endMatch?.[0].length ?? 2;
      
      // 计算内容范围（不包括 $$ 分隔符）
      const contentFrom = node.from + startLen;
      const contentTo = Math.max(contentFrom, node.to - endLen);
      
      // 提取内部的 LaTeX 代码
      const inner = text.slice(startLen, text.length - endLen).trim();
      if (!inner) return;  // 如果内容为空，跳过

      // 创建数学公式 Widget
      const widget = new BlockMathWidget(inner, contentFrom, contentTo);

      if (intersects) {
        // 光标在块内 → 保持源码可见，并在闭合 $$ 行后立即显示渲染后的预览
        // side:1 将 widget 放置在后面
        builder.add(node.to, node.to, Decoration.widget({ widget, block: true, side: 1 }));
      } else {
        // 光标在块外 → 用渲染后的卡片替换整个块
        builder.add(node.from, node.to, Decoration.replace({ widget, block: true }));
      }
    },
  });

  return builder.finish();
}

/**
 * 块级数学公式 StateField
 * 
 * 管理块级数学公式的装饰集。
 * 
 * **更新条件：**
 * - 文档内容变化
 * - 选区变化（影响光标是否在公式块内的判断）
 */
const blockMathField = StateField.define<DecorationSet>({
  create: (state) => buildBlockMathDecorations(state),
  update(prev, tr) {
    if (tr.docChanged || tr.selection) {
      return buildBlockMathDecorations(tr.state);
    }
    return prev;
  },
  provide: (f) => EditorView.decorations.from(f),
});

/**
 * 块级数学公式主题样式
 * 
 * **样式说明：**
 * - `.cm-math-block-card`: 公式卡片容器，悬停时显示边框和背景
 * - `.cm-math-block`: 公式渲染区域，居中对齐
 * - `.cm-math-block-edit`: 编辑按钮，默认隐藏，悬停卡片时显示
 * - `.cm-math-error`: 渲染错误时的回退样式，红色斜体等宽字体
 */
const blockMathTheme = EditorView.theme({
  '.cm-math-block-card': {
    position: 'relative',
    border: '1px solid transparent',
    borderRadius: '6px',
    margin: '8px 0',
    padding: '10px 12px',
    cursor: 'pointer',
    transition: 'border-color 0.12s ease, background-color 0.12s ease',
  },
  '.cm-math-block-card:hover': {
    borderColor: 'rgba(127, 127, 127, 0.25)',
    backgroundColor: 'rgba(127, 127, 127, 0.04)',
  },
  '.cm-math-block': {
    textAlign: 'center',
  },
  '.cm-math-block-edit': {
    position: 'absolute',
    top: '6px',
    right: '8px',
    border: 'none',
    background: 'transparent',
    cursor: 'pointer',
    color: 'rgba(127, 127, 127, 0.7)',
    padding: '4px',
    borderRadius: '4px',
    opacity: '0',  // 默认隐藏
    transition: 'opacity 0.12s ease, background-color 0.12s ease',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    lineHeight: '0',
  },
  '.cm-math-block-card:hover .cm-math-block-edit': {
    opacity: '1',  // 悬停卡片时显示
  },
  '.cm-math-block-edit:hover': {
    backgroundColor: 'rgba(127, 127, 127, 0.15)',
  },
  '.cm-math-error': {
    color: 'rgba(200, 60, 60, 0.85)',  // 红色
    fontStyle: 'italic',
    fontFamily: 'monospace',
    fontSize: '0.9em',
  },
});

/**
 * 创建块级数学公式扩展
 * 
 * **功能：**
 * 将 `$$...$$` 块级数学公式渲染为 KaTeX 渲染的公式卡片。
 * 
 * **交互：**
 * - 光标在公式外：显示渲染后的公式卡片
 * - 光标在公式内：同时显示源码和渲染结果
 * - 点击卡片或编辑按钮：选中公式内容进入编辑模式
 * 
 * @returns CodeMirror 扩展数组
 */
export function createBlockMathExtension(): Extension {
  return [blockMathField, blockMathTheme];
}
