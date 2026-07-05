/**
 * Admonition / Callout 块渲染扩展 — Obsidian 风格
 *
 * **功能：**
 * 识别 GFM 风格的 `> [!type] Title` 和 Obsidian pre-callout `> **type** Title`
 * 语法，在 Blockquote 节点内渲染样式化的提示框。
 * 
 * **渲染效果：**
 * - 填充背景，按类型颜色着色
 * - 圆角 + 彩色左侧强调条
 * - 标题行：Lucide SVG 图标 + 粗体标签（自定义标题覆盖默认标签）
 * - 当光标不在标题行时隐藏源码 markdown `> [!type] ...`；
 *   光标进入时显示源码，离开时恢复渲染
 * 
 * **特性：**
 * - 类型查找不区分大小写
 * - 未知类型回退到中性的默认配置 —— 导入的 Obsidian 库中的自定义 callout 不会渲染失败
 */
import { syntaxTree } from '@codemirror/language';
import { type EditorState, type Extension, type Range, StateField } from '@codemirror/state';
import { Decoration, type DecorationSet, EditorView, WidgetType } from '@codemirror/view';
import { DEFAULT_ADMONITION_TYPE, GFM_TYPES } from './presets';
import type { AdmonitionTypeConfig, AdmonitionTypesMap } from './types';

// `[ \t]*` 而不是 `\s*` — `\s` 会匹配 `\n` 并让尾部的 `(.*)`
// 贪婪地捕获下一行作为“自定义标题”（导致例如
// `customTitle = "> body content"` 且 widget 将正文文本渲染为
// 标题标签）。限制为空格和制表符可保持匹配在第 1 行。
const ADMONITION_REGEX =
  /^>[ \t]*(?:\*{2}|\[!)([a-zA-Z][a-zA-Z0-9_-]*)(?:\*{2}|\])[ \t]*(.*)/;

/** Admonition 选项接口 */
export interface AdmonitionOptions {
  /**
   * 类型名到配置的映射。默认：`GFM_TYPES`。要支持完整的
   * Obsidian 集合：`{ types: { ...GFM_TYPES, ...OBSIDIAN_TYPES } }`。
   * 类型查找不区分大小写。
   */
  types?: AdmonitionTypesMap;
}

/**
 * 查找类型配置
 * 
 * **工作流程：**
 * 1. 首先尝试直接匹配（区分大小写）
 * 2. 如果未找到，尝试不区分大小写的匹配
 * 3. 如果仍未找到，返回默认配置（使用原始类型名作为标签）
 * 
 * @param types - 类型映射表
 * @param raw - 原始类型字符串
 * @returns 包含配置和是否已知的对象
 */
function lookupType(types: AdmonitionTypesMap, raw: string): { config: AdmonitionTypeConfig; isKnown: boolean } {
  const direct = types[raw];
  if (direct) return { config: direct, isKnown: true };

  const lower = raw.toLowerCase();
  for (const [key, value] of Object.entries(types)) {
    if (key.toLowerCase() === lower) {
      return { config: value, isKnown: true };
    }
  }

  return {
    config: { ...DEFAULT_ADMONITION_TYPE, label: raw },
    isKnown: false,
  };
}


/**
 * 标题行 Widget — 替换原始的 `> [!TYPE] custom-title?` 源码
 * 
 * **功能：**
 * 用自包含的块级元素替换标题行，该元素带有自己的背景 / 圆角 / 内边距
 * （与正文行相同的 admonition token）。
 * 
 * **设计原因：**
 * 块级替换使该行完全由 widget 拥有 —— 当光标进入和离开块时，
 * 不会与行装饰协调产生脆弱的交互。
 */
class AdmonitionTitleWidget extends WidgetType {
  constructor(
    /** Lucide SVG 图标字符串 */
    private readonly iconSvg: string,
    /** 显示标签文本 */
    private readonly labelText: string,
    /** CSS 类名（类型名） */
    private readonly className: string,
    /** 如果标题行也是最后一行（单行 callout） */
    private readonly isOnly: boolean,
  ) {
    super();
  }

  /**
   * 相等性判断
   * 
   * @param other - 另一个 widget 实例
   * @returns 是否相等
   */
  eq(other: AdmonitionTitleWidget) {
    return (
      this.iconSvg === other.iconSvg &&
      this.labelText === other.labelText &&
      this.className === other.className &&
      this.isOnly === other.isOnly
    );
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 层级：**
   * ```
   * div.cm-admonition.cm-admonition-title.cm-admonition-{className}
   *   div.cm-admonition-title-widget
   *     span.cm-admonition-title-icon（SVG 图标）
   *     span.cm-admonition-title-label（标签文本）
   * ```
   * 
   * @returns 根 DOM 元素
   */
  toDOM() {
    const root = document.createElement('div');
    root.className = `cm-admonition cm-admonition-title cm-admonition-${this.className}`;
    if (this.isOnly) root.classList.add('cm-admonition-only');
    root.setAttribute('data-admonition-type', this.className);

    const inner = document.createElement('div');
    inner.className = 'cm-admonition-title-widget';

    const iconWrap = document.createElement('span');
    iconWrap.className = 'cm-admonition-title-icon';
    iconWrap.innerHTML = this.iconSvg;
    inner.appendChild(iconWrap);

    const labelEl = document.createElement('span');
    labelEl.className = 'cm-admonition-title-label';
    labelEl.textContent = this.labelText;
    inner.appendChild(labelEl);

    root.appendChild(inner);
    return root;
  }

  /**
   * 事件处理策略
   * 
   * **返回值：**
   * false — 允许 CodeMirror 处理点击 → 光标落在标题行上 → 
   * 块切换到源码模式（参见 buildDecorations 中的 `cursorLineNum` 检查）。
   * 
   * @returns false 允许事件传播
   */
  ignoreEvent() {
    return false;
  }
}


/**
 * 构建 Admonition 装饰集
 * 
 * **工作流程：**
 * 1. 遍历语法树，查找 Blockquote 节点
 * 2. 使用正则表达式匹配 Admonition 语法
 * 3. 如果光标在 admonition 块内，不应用任何装饰（显示源码）
 * 4. 否则，创建标题 widget 和正文行装饰
 * 
 * **Obsidian 风格交互：**
 * - 点击任意位置 → 整个块变为源码模式
 * - 点击外部（光标离开块）→ 恢复渲染
 * 
 * @param state - 编辑器状态
 * @param types - 类型映射表
 * @returns 装饰集合
 */
function buildAdmonitionDecorations(
  state: EditorState,
  types: AdmonitionTypesMap,
): DecorationSet {
  const decorations: Range<Decoration>[] = [];
  // 获取光标所在的行号
  const cursorLineNum = state.doc.lineAt(state.selection.main.head).number;

  // 遍历语法树
  syntaxTree(state).iterate({
    /**
     * 进入节点时的处理
     * 
     * @param node - 当前节点
     */
    enter: (node) => {
      // 仅处理 Blockquote 节点
      if (node.name !== 'Blockquote') return;

      // 获取 Blockquote 节点的原始文本
      const rawText = state.sliceDoc(node.from, node.to);
      // 尝试匹配 Admonition 正则
      const match = ADMONITION_REGEX.exec(rawText);
      if (!match) return;

      // 遍历此 Blockquote 节点覆盖的实际文档行 —— 比在 /\n>/ 上分割并重新推导偏移量更简单且无歧义。
      const startLineNum = state.doc.lineAt(node.from).number;
      const endLineNum = state.doc.lineAt(node.to).number;

      // Obsidian 风格的“点击任意位置 → 整个块变为源码”：当
      // 光标坐落在此 admonition 块内的任意位置时，不发出任何
      // 装饰。结果是纯 `> [!type] / > body...` markdown，带有编辑器的默认 blockquote 样式 —— 完全可编辑。
      // 点击外部（光标离开块）→ 恢复渲染。
      if (cursorLineNum >= startLineNum && cursorLineNum <= endLineNum) return;

      // 提取类型名和自定义标题
      const typeRaw = match[1];
      const customTitle = match[2].trim();
      // 查找类型配置
      const { config } = lookupType(types, typeRaw);
      const baseClass = `cm-admonition cm-admonition-${config.className}`;
      const isOnly = startLineNum === endLineNum;  // 是否为单行 callout
      const labelText = customTitle || config.label || typeRaw;

      // 标题 — 块级替换。Widget DOM 带有自己的 admonition
      // 类，因此我们不依赖 Decoration.line + Decoration.replace
      // 重叠协调，这在进入/离开源码模式时被证明是脆弱的。
      const titleLine = state.doc.line(startLineNum);
      decorations.push(
        Decoration.replace({
          widget: new AdmonitionTitleWidget(config.icon, labelText, config.className, isOnly),
          block: true,
        }).range(titleLine.from, titleLine.to),
      );

      // 正文行 — 仅行装饰。
      for (let n = startLineNum + 1; n <= endLineNum; n++) {
        const line = state.doc.line(n);
        const isLast = n === endLineNum;
        const bodyClasses = [
          baseClass,
          'cm-admonition-body',
          isLast ? 'cm-admonition-body-last' : '',
        ]
          .filter(Boolean)
          .join(' ');
        decorations.push(
          Decoration.line({
            class: bodyClasses,
            attributes: { 'data-admonition-type': config.className },
          }).range(line.from),
        );
      }
    },
  });

  return Decoration.set(decorations, true);
}

/**
 * Admonition 主题样式
 * 
 * **包含的样式：**
 * 1. 每种类型的强调色（用于背景、左侧条、图标描边）
 * 2. 每行获得着色的填充 —— 跨块组合形成连续的圆角框
 * 3. 标题 widget — flex 行，带图标 + 粗体标签，强调色
 * 
 * **重要技术细节：**
 * `background-image: none !important` 至关重要：`markdownDecorationExtension`
 * 也添加了 `cm-blockQuote-d0` 类，通过 `background-image: linear-gradient(...)`
 * 绘制 2px 金色垂直条。backgroundColor 和 backgroundImage 是独立的 CSS 属性
 * —— 如果不显式清除图像，blockquote 条仍会通过我们的着色填充渲染。
 */
const admonitionTheme = EditorView.theme({
  // Per-type accent color (used by background, left bar, icon stroke).
  '.cm-admonition-note': { '--admonition-color': '#1e88e5' },
  '.cm-admonition-tip': { '--admonition-color': '#43a047' },
  '.cm-admonition-important': { '--admonition-color': '#7b1fa2' },
  '.cm-admonition-warning': { '--admonition-color': '#fb8c00' },
  '.cm-admonition-caution': { '--admonition-color': '#e53935' },
  '.cm-admonition-info': { '--admonition-color': '#039be5' },
  '.cm-admonition-success': { '--admonition-color': '#43a047' },
  '.cm-admonition-question': { '--admonition-color': '#fb8c00' },
  '.cm-admonition-failure, .cm-admonition-danger, .cm-admonition-bug': {
    '--admonition-color': '#e53935',
  },
  '.cm-admonition-example': { '--admonition-color': '#7e57c2' },
  '.cm-admonition-quote': { '--admonition-color': '#757575' },
  '.cm-admonition-default': { '--admonition-color': '#757575' },

  // Each line gets the tinted fill — combined across the block they form a
  // continuous rounded box (Obsidian-style; no left accent bar). Padding is
  // on the line so cursor positioning behaves naturally; rounding lives on
  // first / last line of the block.
  //
  // `background-image: none !important` is critical: `markdownDecorationExtension`
  // also adds a `cm-blockQuote-d0` class that draws a 2px gold vertical bar
  // via `background-image: linear-gradient(...)`. backgroundColor and
  // backgroundImage are independent CSS properties — without explicitly
  // clearing the image, the blockquote bar would still render through our
  // tinted fill.
  '.cm-admonition': {
    backgroundColor: 'color-mix(in srgb, var(--admonition-color) 10%, transparent)',
    backgroundImage: 'none !important',
    paddingLeft: '14px !important',
    paddingRight: '14px !important',
    paddingTop: '0',
    paddingBottom: '0',
  },
  '.cm-admonition-title': {
    paddingTop: '10px !important',
    paddingBottom: '4px !important',
    borderTopLeftRadius: '6px',
    borderTopRightRadius: '6px',
  },
  '.cm-admonition-only, .cm-admonition-body-last': {
    paddingBottom: '10px !important',
    borderBottomLeftRadius: '6px',
    borderBottomRightRadius: '6px',
  },

  // Header widget — flex row with icon + bold label, accent-colored.
  '.cm-admonition-title-widget': {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '8px',
    color: 'var(--admonition-color)',
    fontWeight: '600',
    fontSize: '0.95em',
    lineHeight: '1.4',
    letterSpacing: '0.01em',
  },
  '.cm-admonition-title-icon': {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: '0',
  },
  '.cm-admonition-title-icon > svg': {
    display: 'block',
  },
  '.cm-admonition-title-label': {
    color: 'var(--admonition-color)',
  },
});

/**
 * 创建 Admonition 扩展
 * 
 * **工作流程：**
 * 1. 合并默认类型和自定义类型
 * 2. 创建 StateField 持久化存储装饰集
 * 3. 在文档变化、重新配置或选区变化时重建装饰
 * 4. 通过 provide 将装饰集提供给 EditorView
 * 
 * @param options - Admonition 选项
 * @returns CodeMirror 扩展数组
 */
export function createAdmonitionExtension(options: AdmonitionOptions = {}): Extension {
  const types = options.types ?? GFM_TYPES;

  const field = StateField.define<DecorationSet>({
    create(state) {
      return buildAdmonitionDecorations(state, types);
    },
    update(deco, tr) {
      if (tr.docChanged || tr.reconfigured || tr.selection) {
        return buildAdmonitionDecorations(tr.state, types);
      }
      return deco;
    },
    provide: (f) => EditorView.decorations.from(f),
  });

  return [admonitionTheme, field];
}
