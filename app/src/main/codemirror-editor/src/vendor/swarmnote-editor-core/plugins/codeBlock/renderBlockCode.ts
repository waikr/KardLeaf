/**
 * 块级代码渲染扩展 - 支持四种交互模式
 *
 * **四种模式详解：**
 *
 * 1. `off` - 关闭特殊渲染，仅显示原始 Markdown 源码
 * 
 * 2. `inline`（默认）- 内联模式
 *    - fence 标记行（```lang / ```）折叠为 header/footer widget
 *    - 代码内容行保持在 CM 文档流中
 *    - 支持完整的语法高亮和直接编辑
 *    - 最佳编程体验
 *
 * 3. `auto` - 自动卡片模式
 *    - 光标在代码块外时：整个块折叠为只读“卡片”widget
 *    - 光标进入时：显示原始 Markdown，可编辑
 *    - 适合阅读为主的场景
 *
 * 4. `toggle` - 手动切换模式
 *    - 始终渲染为卡片
 *    - 每个块有 "Code" / "Render" 按钮
 *    - 源码可见性通过 state field 追踪
 *    - 用户完全控制何时查看源码
 *
 * **卡片渲染说明：**
 * `auto`/`toggle` 模式下的卡片使用等宽纯文本渲染代码体，
 * widget 内部无语法高亮。需要完整高亮的用户可以：
 * - auto 模式：点击进入代码块
 * - toggle 模式：点击 "Code" 按钮
 */
import { syntaxTree } from '@codemirror/language';
import {
  type EditorState,
  type Extension,
  type Range,
  StateEffect,
  StateField,
} from '@codemirror/state';
import { Decoration, type DecorationSet, EditorView, WidgetType } from '@codemirror/view';
import { shouldShowSource } from '../../core';
import type { CodeBlockMode } from '../../types';

// ─── 辅助函数 ────────────────────────────────────────────────────

/**
 * 从 fence 行提取编程语言标识
 * 
 * @param state - 编辑器状态
 * @param fenceLineFrom - fence 行起始位置
 * @param fenceLineTo - fence 行结束位置
 * @returns 语言标识（如 'javascript'、'python'），无则返回空字符串
 */
function extractLanguage(state: EditorState, fenceLineFrom: number, fenceLineTo: number): string {
  const text = state.sliceDoc(fenceLineFrom, fenceLineTo);
  // 匹配 ``` 后的第一个非空白字符序列
  const match = text.match(/^`{3,}\s*(\S+)?/);
  return match?.[1] ?? '';
}

/**
 * 提取代码块的实际内容（去除 fence 标记行）
 * 
 * @param state - 编辑器状态
 * @param codeBlockFrom - 代码块起始位置（包含开头的 ```）
 * @param codeBlockTo - 代码块结束位置（包含结尾的 ```）
 * @returns 纯代码内容字符串
 */
function extractCodeContent(state: EditorState, codeBlockFrom: number, codeBlockTo: number): string {
  const fullText = state.sliceDoc(codeBlockFrom, codeBlockTo);
  const lines = fullText.split('\n');
  if (lines.length >= 2) {
    // 去掉第一行（```lang）和最后一行（```）
    return lines.slice(1, -1).join('\n');
  }
  return '';
}

// ─── Toggle 模式的源码可见性追踪 ────────────────────────────────

/** 代码块源码范围接口 */
interface CodeBlockSourceRange {
  /** 起始位置 */
  from: number;
  /** 结束位置 */
  to: number;
}

/**
 * 设置代码块源码显示状态的 Effect
 * 
 * 用于 toggle 模式下手动切换某个代码块的源码可见性。
 */
export const setCodeBlockSourceMode = StateEffect.define<{
  from: number;
  to: number;
  showSource: boolean;
}>();

/**
 * 判断两个范围是否重叠
 * 
 * @param a - 范围 A
 * @param b - 范围 B
 * @returns 是否重叠
 */
function codeRangesOverlap(a: CodeBlockSourceRange, b: CodeBlockSourceRange): boolean {
  return a.from <= b.to && a.to >= b.from;
}

/**
 * 代码块源码模式 StateField
 * 
 * 维护一个数组，记录所有处于“显示源码”状态的代码块范围。
 * 
 * **更新逻辑：**
 * 1. 映射现有范围到新的文档位置（处理文档变化）
 * 2. 处理 setCodeBlockSourceMode effects：
 *    - showSource=true → 添加到数组（如果不存在）
 *    - showSource=false → 从数组中移除
 */
const codeBlockSourceModeField = StateField.define<CodeBlockSourceRange[]>({
  /** 初始状态：空数组（无代码块显示源码） */
  create: () => [],
  /**
   * 状态更新函数
   * 
   * @param ranges - 当前显示源码的代码块范围列表
   * @param tr - 事务对象
   * @returns 更新后的范围列表
   */
  update(ranges, tr) {
    // 步骤 1：映射现有范围到新位置（考虑文档变化）
    let next = ranges.map((range) => ({
      from: tr.changes.mapPos(range.from, 1),   // 向前映射
      to: tr.changes.mapPos(range.to, -1),      // 向后映射
    }));

    // 步骤 2：处理 effects
    for (const effect of tr.effects) {
      if (!effect.is(setCodeBlockSourceMode)) continue;
      const { from, to, showSource } = effect.value;
      
      // 映射 effect 中的范围
      const mapped: CodeBlockSourceRange = {
        from: tr.changes.mapPos(from, 1),
        to: tr.changes.mapPos(to, -1),
      };
      
      if (showSource) {
        // 添加到列表（避免重复）
        if (!next.some((r) => codeRangesOverlap(r, mapped))) {
          next = [...next, mapped];
        }
      } else {
        // 从列表中移除
        next = next.filter((r) => !codeRangesOverlap(r, mapped));
      }
    }

    return next;
  },
});

/**
 * 检查指定范围的代码块是否处于源码显示模式
 * 
 * @param ranges - 显示源码的范围列表
 * @param from - 要检查的范围起始位置
 * @param to - 要检查的范围结束位置
 * @returns 是否在源码模式
 */
function isCodeBlockInSourceMode(
  ranges: CodeBlockSourceRange[],
  from: number,
  to: number,
): boolean {
  return ranges.some((r) => r.from <= to && r.to >= from);
}

// ─── Inline 模式共享的 Widgets（header/footer） ──────────────

/**
 * 为复制按钮附加事件处理器
 * 
 * @param button - 复制按钮元素
 * @param view - 编辑器视图
 * @param codeFrom - 代码块起始位置
 * @param codeTo - 代码块结束位置
 */
function attachCopyHandler(button: HTMLButtonElement, view: EditorView, codeFrom: number, codeTo: number) {
  button.addEventListener('mousedown', (e) => {
    e.preventDefault();
    e.stopPropagation();
    // 提取代码内容并复制到剪贴板
    const code = extractCodeContent(view.state, codeFrom, codeTo);
    void navigator.clipboard.writeText(code).then(() => {
      // 显示“已复制”反馈
      button.textContent = 'Copied!';
      setTimeout(() => {
        button.textContent = 'Copy';
      }, 1500);
    });
  });
}

/**
 * 代码块头部 Widget
 * 
 * **功能：**
 * - 显示编程语言标签
 * - 提供“Copy”按钮
 * - 点击空白区域将光标移动到代码第一行
 * 
 * **使用场景：**
 * inline 模式下，替换开头的 ```lang 行
 */
class CodeBlockHeaderWidget extends WidgetType {
  constructor(
    /** 编程语言标识 */
    private readonly language: string,
    /** 代码块起始位置 */
    private readonly codeFrom: number,
    /** 代码块结束位置 */
    private readonly codeTo: number,
  ) {
    super();
  }

  /**
   * 判断两个 widget 是否相等（用于优化渲染）
   */
  eq(other: CodeBlockHeaderWidget): boolean {
    return (
      this.language === other.language &&
      this.codeFrom === other.codeFrom &&
      this.codeTo === other.codeTo
    );
  }

  /**
   * 创建 DOM 元素
   * 
   * @param view - 编辑器视图
   * @returns 头部容器元素
   */
  toDOM(view: EditorView): HTMLElement {
    const container = document.createElement('div');
    container.className = 'cm-codeblock-header';

    // 添加语言标签
    if (this.language) {
      const label = document.createElement('span');
      label.className = 'cm-codeblock-lang';
      label.textContent = this.language;
      container.appendChild(label);
    }

    // 添加弹性 spacer，将按钮推到右侧
    const spacer = document.createElement('span');
    spacer.style.flex = '1';
    container.appendChild(spacer);

    // 添加复制按钮
    const copyBtn = document.createElement('button');
    copyBtn.className = 'cm-codeblock-copy';
    copyBtn.textContent = 'Copy';
    copyBtn.type = 'button';
    attachCopyHandler(copyBtn, view, this.codeFrom, this.codeTo);
    container.appendChild(copyBtn);

    // 点击头部空白区域时，将光标移动到代码第一行
    container.addEventListener('mousedown', (e) => {
      if (e.target === copyBtn) return;  // 点击按钮不处理
      e.preventDefault();
      const firstCodeLine = view.state.doc.lineAt(this.codeFrom);
      const nextLine =
        firstCodeLine.number < view.state.doc.lines
          ? view.state.doc.line(firstCodeLine.number + 1)  // 下一行是代码内容
          : firstCodeLine;
      view.dispatch({
        selection: { anchor: nextLine.from },
        scrollIntoView: true,
      });
      view.focus();
    });

    return container;
  }

  /**
   * 是否忽略事件（返回 false 表示不忽略，允许交互）
   */
  ignoreEvent(): boolean {
    return false;
  }
}

/**
 * 代码块底部 Widget
 * 
 * **功能：**
 * 在 inline 模式下，替换结尾的 ``` 行。
 * 目前只是一个视觉分隔符，无交互功能。
 */
class CodeBlockFooterWidget extends WidgetType {
  eq(): boolean {
    return true;  // 所有 footer widget 都相同
  }

  toDOM(): HTMLElement {
    const container = document.createElement('div');
    container.className = 'cm-codeblock-footer';
    return container;
  }

  ignoreEvent(): boolean {
    return false;
  }
}

// ─── Auto / Toggle 模式的卡片 Widget ────────────────────────

/**
 * 代码块卡片 Widget
 * 
 * **功能：**
 * 在 auto/toggle 模式下，将整个代码块折叠为一个只读卡片。
 * 卡片包含：
 * - 头部：语言标签 + 操作按钮（Copy / Code）
 * - 主体：等宽纯文本显示代码内容（无高亮）
 * 
 * **交互模式：**
 * - auto 模式：点击卡片主体将光标移入，卡片消失，显示源码
 * - toggle 模式：点击 "Code" 按钮切换到源码视图
 */
class CodeBlockCardWidget extends WidgetType {
  constructor(
    /** 编程语言标识 */
    private readonly language: string,
    /** 纯代码内容 */
    private readonly code: string,
    /** 代码内容起始位置 */
    private readonly codeFrom: number,
    /** 代码内容结束位置 */
    private readonly codeTo: number,
    /** 渲染模式 */
    private readonly mode: 'auto' | 'toggle',
    /** 整个代码块起始位置（包含 fence） */
    private readonly blockFrom: number,
    /** 整个代码块结束位置（包含 fence） */
    private readonly blockTo: number,
  ) {
    super();
  }

  /**
   * 判断两个 widget 是否相等
   */
  eq(other: CodeBlockCardWidget): boolean {
    return (
      this.language === other.language &&
      this.code === other.code &&
      this.codeFrom === other.codeFrom &&
      this.codeTo === other.codeTo &&
      this.mode === other.mode
    );
  }

  /**
   * 创建卡片 DOM 结构
   * 
   * @param view - 编辑器视图
   * @returns 卡片容器元素
   */
  toDOM(view: EditorView): HTMLElement {
    const container = document.createElement('div');
    container.className = 'cm-codeblock-card';

    // 构建头部
    const header = document.createElement('div');
    header.className = 'cm-codeblock-header';

    // 添加语言标签
    if (this.language) {
      const label = document.createElement('span');
      label.className = 'cm-codeblock-lang';
      label.textContent = this.language;
      header.appendChild(label);
    }

    // 弹性 spacer
    const spacer = document.createElement('span');
    spacer.style.flex = '1';
    header.appendChild(spacer);

    // Toggle 模式：添加 "Code" 按钮
    if (this.mode === 'toggle') {
      const codeBtn = document.createElement('button');
      codeBtn.className = 'cm-codeblock-toggle';
      codeBtn.textContent = 'Code';
      codeBtn.type = 'button';
      codeBtn.title = 'Show source';
      codeBtn.addEventListener('mousedown', (e) => {
        e.preventDefault();
        e.stopPropagation();
        // 派发 effect，切换到源码模式
        view.dispatch({
          effects: setCodeBlockSourceMode.of({
            from: this.blockFrom,
            to: this.blockTo,
            showSource: true,
          }),
        });
      });
      header.appendChild(codeBtn);
    }

    // 添加复制按钮
    const copyBtn = document.createElement('button');
    copyBtn.className = 'cm-codeblock-copy';
    copyBtn.textContent = 'Copy';
    copyBtn.type = 'button';
    attachCopyHandler(copyBtn, view, this.codeFrom, this.codeTo);
    header.appendChild(copyBtn);

    container.appendChild(header);

    // 构建代码主体（纯文本，无高亮）
    const body = document.createElement('pre');
    body.className = 'cm-codeblock-card-body';
    body.textContent = this.code;
    container.appendChild(body);

    // auto mode: clicking body moves cursor in (so the card collapses).
    if (this.mode === 'auto') {
      body.addEventListener('mousedown', (e) => {
        if (e.target === copyBtn) return;
        e.preventDefault();
        const firstCodeLine = view.state.doc.lineAt(this.codeFrom);
        const nextLine =
          firstCodeLine.number < view.state.doc.lines
            ? view.state.doc.line(firstCodeLine.number + 1)
            : firstCodeLine;
        view.dispatch({
          selection: { anchor: nextLine.from },
          scrollIntoView: true,
        });
        view.focus();
      });
    }

    return container;
  }

  ignoreEvent(): boolean {
    return false;
  }
}

class CodeBlockRenderToggleWidget extends WidgetType {
  constructor(
    private readonly blockFrom: number,
    private readonly blockTo: number,
  ) {
    super();
  }

  eq(other: CodeBlockRenderToggleWidget): boolean {
    return this.blockFrom === other.blockFrom && this.blockTo === other.blockTo;
  }

  toDOM(view: EditorView): HTMLElement {
    const container = document.createElement('div');
    container.className = 'cm-codeblock-render-toggle';

    const button = document.createElement('button');
    button.className = 'cm-codeblock-toggle';
    button.textContent = 'Render';
    button.type = 'button';
    button.title = 'Render as card';
    button.addEventListener('mousedown', (e) => {
      e.preventDefault();
      e.stopPropagation();
      view.dispatch({
        effects: setCodeBlockSourceMode.of({
          from: this.blockFrom,
          to: this.blockTo,
          showSource: false,
        }),
      });
    });
    container.appendChild(button);

    return container;
  }

  ignoreEvent(): boolean {
    return false;
  }
}

// ─── 各模式的装饰构建器 ──────────────────────────────

/**
 * 为代码块的所有行添加背景类名
 * 
 * @param decorations - 装饰数组（会被修改）
 * @param state - 编辑器状态
 * @param fromLineFrom - 起始行的 from 位置
 * @param blockTo - 代码块结束位置
 */
function pushBlockBackgroundLines(
  decorations: Range<Decoration>[],
  state: EditorState,
  fromLineFrom: number,
  blockTo: number,
) {
  // 遍历代码块内的每一行
  for (let pos = fromLineFrom; pos <= blockTo; ) {
    const line = state.doc.lineAt(pos);
    // 为每行添加 cm-codeblock-line 类名（用于背景色等样式）
    decorations.push(Decoration.line({ class: 'cm-codeblock-line' }).range(line.from));
    pos = line.to + 1;
  }
}

/**
 * 构建 Inline 模式的装饰
 * 
 * **逻辑：**
 * 1. 遍历语法树，查找 FencedCode 节点
 * 2. 检查光标是否在代码块内（shouldShowSource）
 * 3. 如果光标不在内部：
 *    - 替换第一行（```lang）为 HeaderWidget
 *    - 替换最后一行（```）为 FooterWidget
 * 4. 为所有代码行添加背景类名
 * 
 * @param state - 编辑器状态
 * @returns 装饰范围数组
 */
function buildInlineDecorations(state: EditorState): Range<Decoration>[] {
  const decorations: Range<Decoration>[] = [];

  syntaxTree(state).iterate({
    enter: (node) => {
      if (node.name !== 'FencedCode') return;  // 只处理代码块

      // 检查是否应该显示源码（光标在范围内）
      const cursorInside = shouldShowSource(state, node.from, node.to);
      const firstLine = state.doc.lineAt(node.from);
      const lastLine = state.doc.lineAt(node.to);

      if (!cursorInside) {
        // 光标不在内部，折叠 fence 标记
        const language = extractLanguage(state, firstLine.from, firstLine.to);
        
        // 替换开头 ```lang 行为 HeaderWidget
        decorations.push(
          Decoration.replace({
            widget: new CodeBlockHeaderWidget(language, node.from, node.to),
            block: true,  // 块级装饰
          }).range(firstLine.from, firstLine.to),
        );

        // 替换结尾 ``` 行为 FooterWidget（如果存在）
        if (lastLine.number > firstLine.number) {
          const lastLineText = state.sliceDoc(lastLine.from, lastLine.to);
          if (/^`{3,}\s*$/.test(lastLineText)) {  // 匹配纯 ``` 行
            decorations.push(
              Decoration.replace({
                widget: new CodeBlockFooterWidget(),
                block: true,
              }).range(lastLine.from, lastLine.to),
            );
          }
        }
      }

      // 为所有行添加背景样式
      pushBlockBackgroundLines(decorations, state, firstLine.from, node.to);
    },
  });

  return decorations;
}

/**
 * 构建 Auto 模式的装饰
 * 
 * **逻辑：**
 * 1. 遍历语法树，查找 FencedCode 节点
 * 2. 检查是否应该显示源码（光标在范围内）
 * 3. 如果显示源码：仅添加背景类名（显示原始 Markdown）
 * 4. 如果隐藏源码：替换整个块为 CardWidget
 * 
 * @param state - 编辑器状态
 * @returns 装饰范围数组
 */
function buildAutoDecorations(state: EditorState): Range<Decoration>[] {
  const decorations: Range<Decoration>[] = [];

  syntaxTree(state).iterate({
    enter: (node) => {
      if (node.name !== 'FencedCode') return;

      // 检查是否应该显示源码
      const showSource = shouldShowSource(state, node.from, node.to);
      const firstLine = state.doc.lineAt(node.from);

      if (showSource) {
        // 显示原始 Markdown，仅添加背景
        pushBlockBackgroundLines(decorations, state, firstLine.from, node.to);
        return;
      }

      // 隐藏源码，渲染为卡片
      const language = extractLanguage(state, firstLine.from, firstLine.to);
      const code = extractCodeContent(state, node.from, node.to);
      decorations.push(
        Decoration.replace({
          widget: new CodeBlockCardWidget(
            language,
            code,
            node.from,
            node.to,
            'auto',  // auto 模式
            node.from,
            node.to,
          ),
          block: true,
        }).range(node.from, node.to),
      );
    },
  });

  return decorations;
}

/**
 * 构建 Toggle 模式的装饰
 * 
 * **逻辑：**
 * 1. 遍历语法树，查找 FencedCode 节点
 * 2. 检查该代码块是否处于源码模式（通过 sourceRanges）
 * 3. 如果是源码模式：
 *    - 在顶部插入 "Render" 切换按钮 widget
 *    - 显示原始 Markdown + 背景
 * 4. 如果不是源码模式：
 *    - 替换整个块为 CardWidget（带 "Code" 按钮）
 * 
 * @param state - 编辑器状态
 * @param sourceRanges - 处于源码模式的代码块范围列表
 * @returns 装饰范围数组
 */
function buildToggleDecorations(
  state: EditorState,
  sourceRanges: CodeBlockSourceRange[],
): Range<Decoration>[] {
  const decorations: Range<Decoration>[] = [];

  syntaxTree(state).iterate({
    enter: (node) => {
      if (node.name !== 'FencedCode') return;

      // 检查是否在源码模式
      const showSource = isCodeBlockInSourceMode(sourceRanges, node.from, node.to);
      const firstLine = state.doc.lineAt(node.from);

      if (showSource) {
        // 源码模式：显示原始 Markdown + "Render" 切换按钮
        decorations.push(
          Decoration.widget({
            widget: new CodeBlockRenderToggleWidget(node.from, node.to),
            block: true,
            side: -1,  // 放在节点之前
          }).range(node.from),
        );
        pushBlockBackgroundLines(decorations, state, firstLine.from, node.to);
        return;
      }

      // 卡片模式：渲染为只读卡片
      const language = extractLanguage(state, firstLine.from, firstLine.to);
      const code = extractCodeContent(state, node.from, node.to);
      decorations.push(
        Decoration.replace({
          widget: new CodeBlockCardWidget(
            language,
            code,
            node.from,
            node.to,
            'toggle',  // toggle 模式
            node.from,
            node.to,
          ),
          block: true,
        }).range(node.from, node.to),
      );
    },
  });

  return decorations;
}

// ─── StateField 工厂 + 主题 + 扩展导出 ──────────────────────────

/**
 * 根据模式构建装饰集合
 * 
 * @param state - 编辑器状态
 * @param mode - 代码块渲染模式
 * @returns 装饰集合
 */
function buildDecorations(state: EditorState, mode: CodeBlockMode): DecorationSet {
  let entries: Range<Decoration>[];
  switch (mode) {
    case 'inline':
      entries = buildInlineDecorations(state);
      break;
    case 'auto':
      entries = buildAutoDecorations(state);
      break;
    case 'toggle':
      // Toggle 模式需要传入源码范围列表
      entries = buildToggleDecorations(state, state.field(codeBlockSourceModeField));
      break;
    case 'off':
      entries = [];  // 关闭模式，无装饰
      break;
  }
  // 排序并创建 DecorationSet
  return Decoration.set(
    entries.sort((a, b) => a.from - b.from),
    true,
  );
}

const codeBlockTheme = EditorView.theme({
  '.cm-codeblock-line': {
    backgroundColor: 'rgba(127, 127, 127, 0.09)',
  },
  '.cm-codeblock-header': {
    display: 'flex',
    alignItems: 'center',
    gap: '4px',
    padding: '5px 14px',
    backgroundColor: 'rgba(127, 127, 127, 0.1)',
    borderRadius: '6px 6px 0 0',
    borderBottom: '1px solid rgba(127, 127, 127, 0.14)',
    fontSize: '0.82em',
    fontFamily: 'monospace',
  },
  '.cm-codeblock-lang': {
    color: 'rgba(127, 127, 127, 0.75)',
    fontWeight: '600',
    textTransform: 'lowercase',
    letterSpacing: '0.04em',
  },
  '.cm-codeblock-copy, .cm-codeblock-toggle': {
    border: '1px solid rgba(127, 127, 127, 0.22)',
    background: 'transparent',
    color: 'rgba(127, 127, 127, 0.6)',
    cursor: 'pointer',
    padding: '2px 10px',
    borderRadius: '4px',
    fontSize: '0.8em',
    fontFamily: 'inherit',
  },
  '.cm-codeblock-copy:hover, .cm-codeblock-toggle:hover': {
    backgroundColor: 'rgba(127, 127, 127, 0.12)',
    color: 'rgba(127, 127, 127, 0.9)',
  },
  '.cm-codeblock-footer': {
    height: '5px',
    backgroundColor: 'rgba(127, 127, 127, 0.1)',
    borderRadius: '0 0 6px 6px',
    borderTop: '1px solid rgba(127, 127, 127, 0.12)',
  },
  '.cm-codeblock-card': {
    border: '1px solid rgba(127, 127, 127, 0.2)',
    borderRadius: '6px',
    overflow: 'hidden',
    margin: '4px 0',
  },
  '.cm-codeblock-card-body': {
    margin: '0',
    padding: '8px 12px',
    backgroundColor: 'rgba(127, 127, 127, 0.04)',
    fontFamily: 'monospace',
    fontSize: '0.9em',
    whiteSpace: 'pre-wrap',
    overflowX: 'auto',
    cursor: 'text',
  },
  '.cm-codeblock-render-toggle': {
    display: 'flex',
    justifyContent: 'flex-end',
    padding: '4px 12px',
  },
});

/** 代码块扩展选项 */
export interface BlockCodeOptions {
  /** 渲染模式，默认为 'inline' */
  mode?: CodeBlockMode;
}

/**
 * 创建代码块渲染扩展
 * 
 * **工作流程：**
 * 1. 根据 mode 参数决定渲染策略
 * 2. 如果 mode 为 'off'，返回空数组（无装饰）
 * 3. 否则创建 StateField 管理装饰
 * 4. StateField 在以下情况重建装饰：
 *    - 文档内容变化 (docChanged)
 *    - 配置重新加载 (reconfigured)
 *    - 选区变化 (selection)
 *    - Toggle 模式的源码切换 (hasModeToggle)
 * 
 * @param options - 配置选项
 * @returns CodeMirror 扩展集合
 */
export function createBlockCodeExtension(options: BlockCodeOptions = {}): Extension {
  const mode: CodeBlockMode = options.mode ?? 'inline';  // 默认 inline 模式

  if (mode === 'off') return [];  // 关闭模式，不添加任何扩展

  // 创建装饰 StateField
  const field = StateField.define<DecorationSet>({
    /** 初始化：构建初始装饰 */
    create(state) {
      return buildDecorations(state, mode);
    },
    /**
     * 更新装饰
     * 
     * @param deco - 当前装饰集合
     * @param tr - 事务对象
     * @returns 新的装饰集合
     */
    update(deco, tr) {
      // 检查是否有 toggle 模式的源码切换
      const hasModeToggle = tr.effects.some((e) => e.is(setCodeBlockSourceMode));
      
      // 如果文档、配置、选区或源码模式发生变化，重建装饰
      if (tr.docChanged || tr.reconfigured || tr.selection || hasModeToggle) {
        return buildDecorations(tr.state, mode);
      }
      // 否则保持原装饰
      return deco;
    },
    /** 将 StateField 暴露为 EditorView.decorations */
    provide: (f) => EditorView.decorations.from(f),
  });

  // 返回完整的扩展集合：
  // 1. codeBlockSourceModeField - Toggle 模式的源码状态追踪
  // 2. codeBlockTheme - 样式主题
  // 3. field - 装饰 StateField
  return [codeBlockSourceModeField, codeBlockTheme, field];
}
