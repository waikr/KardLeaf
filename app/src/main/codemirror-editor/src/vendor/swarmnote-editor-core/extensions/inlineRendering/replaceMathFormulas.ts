/**
 * 数学公式渲染扩展
 *
 * **功能：**
 * 使用 KaTeX 渲染 InlineMath（`$...$`）节点。
 * 
 * **特性：**
 * - 异步加载 KaTeX 模块（懒加载）
 * - 渲染失败时回退到显示原始 LaTeX
 * - 点击公式选中内容，触发源码显示
 * 
 * **注意：**
 * BlockMath（`$$...$$`）由 `createBlockMathExtension` 单独处理
 * （块级装饰需要 StateField，ViewPlugin 无法提供）。
 */
import type { EditorState } from '@codemirror/state';
import { EditorView, WidgetType } from '@codemirror/view';
import type { SyntaxNodeRef } from '@lezer/common';
import type { InlineRenderingSpec, RevealStrategy } from './types';

/** KaTeX 模块缓存（懒加载） */
let katexModule: typeof import('katex') | null = null;

/**
 * 异步加载 KaTeX 模块
 * 
 * @returns KaTeX 模块实例
 */
async function loadKaTeX() {
  // 首次调用时加载模块
  if (!katexModule) {
    katexModule = await import('katex');
  }
  return katexModule.default ?? katexModule;
}

/**
 * 数学公式 Widget
 * 
 * **功能：**
 * 渲染内联数学公式，支持点击编辑。
 * 
 * @param tex - LaTeX 源码
 * @param nodeFrom - 节点起始位置
 * @param nodeTo - 节点结束位置
 */
class MathWidget extends WidgetType {
  constructor(
    /** LaTeX 源码 */
    private readonly tex: string,
    /** 节点起始位置 */
    private readonly nodeFrom: number,
    /** 节点结束位置 */
    private readonly nodeTo: number,
  ) {
    super();
  }

  /**
   * 相等性判断
   * 
   * @param other - 另一个 widget 实例
   * @returns 是否相等
   */
  eq(other: MathWidget) {
    return (
      this.tex === other.tex &&
      this.nodeFrom === other.nodeFrom &&
      this.nodeTo === other.nodeTo
    );
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 元素：**
   * `<span class="cm-math-inline">{formula}</span>`
   * 
   * **工作流程：**
   * 1. 创建容器 span 元素
   * 2. 异步加载 KaTeX 并渲染公式
   * 3. 渲染失败时回退到显示原始 LaTeX
   * 4. 添加 mousedown 事件处理器（点击选中内容）
   * 
   * @param view - 编辑器视图
   * @returns span 元素
   */
  toDOM(view: EditorView) {
    // 创建容器元素
    const container = document.createElement('span');
    container.className = 'cm-math-inline';

    // 异步加载 KaTeX 并渲染公式
    void loadKaTeX().then((katex) => {
      if (!container.isConnected) return;  // 元素已断开连接，跳过
      try {
        // 渲染公式（displayMode: false 表示内联模式）
        katex.render(this.tex, container, { displayMode: false, throwOnError: false });
      } catch {
        // 渲染失败：回退到显示原始 LaTeX 并添加错误样式
        container.textContent = this.tex;
        container.classList.add('cm-math-error');
      }
    });

    // 加载中显示占位文本（原始 LaTeX）
    container.textContent = this.tex;

    // 点击渲染的公式任意位置 → 将光标放在内部（`$` 分隔符之间）
    // 以触发源码显示。
    container.addEventListener('mousedown', (event) => {
      event.preventDefault();
      event.stopPropagation();
      // 计算内容范围（去掉前后的 `$`）
      const contentFrom = this.nodeFrom + 1;
      const contentTo = Math.max(contentFrom, this.nodeTo - 1);
      view.dispatch({
        selection: { anchor: contentFrom, head: contentTo },
        scrollIntoView: true,
      });
      view.focus();
    });

    return container;
  }

  /**
   * 事件处理策略
   * 
   * **返回值：**
   * 对于 mousedown 事件返回 false（允许处理），其他事件返回 true（忽略）
   * 
   * @param event - 事件对象
   * @returns 是否忽略事件
   */
  ignoreEvent(event: Event) {
    return event.type !== 'mousedown';
  }
}

/**
 * 提取内联数学公式内容
 * 
 * **工作原理：**
 * 从 InlineMath 节点中提取 LaTeX 源码，去掉前后的 `$` 分隔符。
 * 
 * @param node - 语法节点
 * @param state - 编辑器状态
 * @returns LaTeX 源码或 null（非 InlineMath 节点）
 */
function extractInlineMath(node: SyntaxNodeRef, state: EditorState): string | null {
  // 仅处理 InlineMath 节点
  if (node.name !== 'InlineMath') return null;
  // BlockMath 由 `createBlockMathExtension` 单独处理（块级装饰需要 StateField；
  // ViewPlugin 路径无法提供它们）。
  const text = state.sliceDoc(node.from, node.to);
  return text.slice(1, -1); // 去掉前后的 `$`
}

/**
 * 数学公式主题样式
 * 
 * **包含的样式：**
 * - .cm-math-inline：内联公式内边距
 * - .cm-math-block：块级公式内边距和居中对齐
 * - .cm-math-error：错误样式（红色斜体、等宽字体）
 */
export const mathTheme = EditorView.theme({
  '.cm-math-inline': {
    padding: '0 2px',
  },
  '.cm-math-block': {
    padding: '8px 0',
    textAlign: 'center',
  },
  '.cm-math-error': {
    color: 'rgba(200, 60, 60, 0.8)',
    fontStyle: 'italic',
    fontFamily: 'monospace',
    fontSize: '0.9em',
  },
});

/**
 * 数学公式替换规格
 * 
 * **工作流程：**
 * 1. extractInlineMath：提取 LaTeX 源码
 * 2. createDecoration：创建 MathWidget 实例
 * 3. getRevealStrategy：使用 'active' 策略
 */
export const replaceMathFormulas: InlineRenderingSpec = {
  nodeNames: ['InlineMath'],  // 内联数学公式节点
  extension: {
    /**
     * 创建装饰
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns MathWidget 实例或 null
     */
    createDecoration(node: SyntaxNodeRef, state: EditorState) {
      // 提取 LaTeX 源码
      const tex = extractInlineMath(node, state);
      if (!tex || !tex.trim()) return null;  // 空公式，跳过
      return new MathWidget(tex, node.from, node.to);
    },
    /**
     * 获取显示策略
     * 
     * @returns 'active' — 仅光标在公式上时显示源码
     */
    getRevealStrategy(): RevealStrategy {
      return 'active';
    },
  },
};
