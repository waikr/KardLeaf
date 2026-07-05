/**
 * 块级 Mermaid 图表渲染扩展
 *
 * **功能：**
 * 将 Markdown 文档中的 ```mermaid 代码块渲染为 SVG 图表。
 * 
 * **为什么需要这个扩展？**
 * 
 * Mermaid 代码块是块级元素，需要 block 级别的装饰。
 * CodeMirror 只接受来自 StateField 的 block decoration，
 * 所以 Mermaid 渲染通过这个专用扩展处理。
 *
 * **架构设计：**
 * - editor-core 负责：识别代码块、渲染图表、发出事件
 * - 宿主层负责：放大查看 Modal、编辑界面、主题适配
 *
 * **交互模式：**
 * - 预览模式（光标在代码块外）：显示渲染后的 SVG 图表卡片
 *   - 点击图片区域或放大图标 → 发出 MermaidZoomRequest 事件
 *   - 点击源码图标 → 选中源码区域，CodeMirror 显示原始 Markdown
 * - 编辑模式（光标在代码块内）：不显示 widget，直接编辑源码
 * - 预览和编辑模式互斥，不会同时存在
 */
import { ensureSyntaxTree } from '@codemirror/language';
import {
  type EditorState,
  type Extension,
  RangeSetBuilder,
  StateEffect,
  StateField,
} from '@codemirror/state';
import {
  Decoration,
  type DecorationSet,
  EditorView,
  WidgetType,
} from '@codemirror/view';
import { editorEventCallback, EditorEventType } from '../../events';

/** Mermaid 模块缓存 */
let mermaidModule: typeof import('mermaid') | null = null;

type MermaidTheme = 'default' | 'dark';

/** Mermaid 当前初始化主题 */
let mermaidInitializedTheme: MermaidTheme | null = null;

/** 渲染缓存：主题 + Mermaid 源码 -> SVG。 */
const mermaidRenderCache = new Map<string, string>();

/**
 * 清除 Mermaid 渲染缓存的 Effect
 * 
 * 当主题变化或其他需要重新渲染的情况时，可以 dispatch 这个 effect。
 */
export const clearMermaidCacheEffect = StateEffect.define<void>();

/**
 * 缓存代次 StateField
 *
 * clearMermaidCacheEffect 会清空模块级缓存并递增代次。代次被写入 widget，
 * 让 CodeMirror 在清缓存后不要把旧 DOM 判定为 eq 并继续复用。
 */
const mermaidCacheVersionField = StateField.define<number>({
  create: () => 0,
  update(version, tr) {
    const shouldClear = tr.effects.some((effect) => effect.is(clearMermaidCacheEffect));
    if (!shouldClear) return version;
    mermaidRenderCache.clear();
    return version + 1;
  },
});

/**
 * 懒加载 Mermaid 模块
 * 
 * @param theme - 主题类型（'default' 或 'dark'）
 * @returns Mermaid 模块
 */
async function loadMermaid(theme: MermaidTheme = 'default') {
  if (!mermaidModule) {
    mermaidModule = await import('mermaid');
  }
  
  if (mermaidInitializedTheme !== theme) {
    const mermaid = mermaidModule.default ?? mermaidModule;
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'loose',
      theme,
    });
    mermaidInitializedTheme = theme;
  }
  
  return mermaidModule.default ?? mermaidModule;
}

function getMermaidTheme(): MermaidTheme {
  const isDark = document.body.classList.contains('cm-dark') ||
    document.documentElement.classList.contains('dark');
  return isDark ? 'dark' : 'default';
}

function getRenderCacheKey(theme: MermaidTheme, source: string): string {
  return `${theme}\0${source}`;
}

/**
 * 生成唯一的 Mermaid ID
 * 
 * 每次调用都生成一个全新的唯一 ID，避免 Mermaid 内部状态冲突。
 * Mermaid 要求每个渲染的图表 ID 在 DOM 中必须是唯一的。
 * 
 * @returns 唯一标识符
 */
function generateMermaidId(): string {
  // 使用时间戳 + 随机数确保唯一性
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).substring(2, 8);
  return `mermaid-${timestamp}-${random}`;
}

function createIconButton(
  className: string,
  title: string,
  svg: string,
): HTMLButtonElement {
  const button = document.createElement('button');
  button.className = className;
  button.type = 'button';
  button.title = title;
  button.innerHTML = svg;
  return button;
}

const SOURCE_ICON_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m18 16 4-4-4-4"/><path d="m6 8-4 4 4 4"/><path d="m14.5 4-5 16"/></svg>';

const ZOOM_ICON_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/><path d="M11 8v6"/><path d="M8 11h6"/></svg>';

/**
 * 块级 Mermaid 图表 Widget
 * 
 * **功能：**
 * 渲染 Mermaid 图表卡片，支持点击放大和编辑。
 * 
 * **交互设计：**
 * - 预览模式：显示 SVG 图表，悬停显示源码图标和放大图标
 * - 点击图片区域或放大图标 → 发出 EditorMermaidZoomRequestEvent（宿主层打开放大 Modal）
 * - 点击源码图标 → 选中源码区域，CodeMirror 显示原始 ```mermaid 代码块
 * 
 * **contentFrom / contentTo：**
 * 描述 Mermaid 源码范围（不包括周围的 ```mermaid 和 ``` 分隔符）。
 */
class BlockMermaidWidget extends WidgetType {
  constructor(
    /** Mermaid 源码 */
    private readonly source: string,
    /** 内容起始位置 */
    private readonly contentFrom: number,
    /** 内容结束位置 */
    private readonly contentTo: number,
    /** 图表唯一 ID */
    private readonly chartId: string,
    /** 缓存/渲染代次 */
    private readonly renderVersion: number,
  ) {
    super();
  }

  /**
   * 判断两个 widget 是否相等
   * 
   * chartId 不参与比较，否则每次重建 decoration 都会强制重绘。
   * 源码范围必须参与比较，因为事件闭包会使用这些位置切回源码。
   * 
   * @param other - 另一个 BlockMermaidWidget
   * @returns 如果源码相同则返回 true
   */
  eq(other: BlockMermaidWidget) {
    return (
      other.source === this.source &&
      other.contentFrom === this.contentFrom &&
      other.contentTo === this.contentTo &&
      other.renderVersion === this.renderVersion
    );
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 层级：**
   * ```
   * div.cm-mermaid-block-card
   *   div.cm-mermaid-block（图表渲染区域）
   *   button.cm-mermaid-block-source（源码图标，code-2 图标）
   *   button.cm-mermaid-block-zoom（放大图标，zoom-in 图标）
   * ```
   * 
   * **交互逻辑：**
   * - 点击图片区域或放大图标 → 发出放大事件
   * - 点击源码图标 → 选中源码区域，进入编辑模式
   * 
   * @param view - 编辑器视图
   * @returns 卡片容器元素
   */
  toDOM(view: EditorView) {
    // 创建卡片容器
    const card = document.createElement('div');
    card.className = 'cm-mermaid-block-card';
    card.dataset.mermaidId = this.chartId;

    // 创建图表渲染区域
    const diagram = document.createElement('div');
    diagram.className = 'cm-mermaid-block';
    diagram.textContent = 'Loading...';

    const sourceBtn = createIconButton(
      'cm-mermaid-block-source',
      '查看/编辑源码',
      SOURCE_ICON_SVG,
    );
    const zoomBtn = createIconButton(
      'cm-mermaid-block-zoom',
      '放大查看',
      ZOOM_ICON_SVG,
    );

    card.appendChild(diagram);
    card.appendChild(sourceBtn);
    card.appendChild(zoomBtn);

    // 异步加载 Mermaid 并渲染图表
    void this.renderDiagram(diagram, view);

    // 点击源码图标：选中源码区域，进入编辑模式
    const enterSource = (event: Event) => {
      event.preventDefault();
      event.stopPropagation();
      // 选中 Mermaid 代码内容区域（不包括 ``` 分隔符）
      view.dispatch({
        selection: { anchor: this.contentFrom, head: this.contentTo },
        scrollIntoView: true,
      });
      view.focus();
    };

    // 点击放大按钮或图片区域：打开放大 Modal
    const openZoom = (event: Event) => {
      event.preventDefault();
      event.stopPropagation();
      
      // 获取当前渲染的 SVG
      const renderedSvg = diagram.innerHTML;
      
      // 发出放大查看事件
      const callback = view.state.facet(editorEventCallback);
      if (callback) {
        callback({
          kind: EditorEventType.MermaidZoomRequest,
          source: this.source,
          renderedSvg,
          id: this.chartId,
        });
      }
    };

    // 绑定按钮事件
    sourceBtn.addEventListener('mousedown', enterSource);
    zoomBtn.addEventListener('mousedown', openZoom);
    
    // 点击图片区域：直接打开放大 Modal
    card.addEventListener('mousedown', (e) => {
      const target = e.target as HTMLElement;
      // 如果点击的是按钮，不触发
      if (target === sourceBtn || target.closest('.cm-mermaid-block-source') ||
          target === zoomBtn || target.closest('.cm-mermaid-block-zoom')) {
        return;
      }
      openZoom(e);
    });

    return card;
  }

  /**
   * 渲染 Mermaid 图表
   * 
   * @param container - 图表容器元素
   * @param view - 编辑器视图
   */
  private async renderDiagram(container: HTMLElement, view: EditorView) {
    try {
      const theme = getMermaidTheme();
      const cacheKey = getRenderCacheKey(theme, this.source);

      // 检查缓存
      let svg = mermaidRenderCache.get(cacheKey);
      
      if (!svg) {
        // 缓存未命中，调用 Mermaid 渲染
        const mermaid = await loadMermaid(theme);

        // 使用 mermaid.render 生成 SVG
        const { svg: renderedSvg } = await mermaid.render(this.chartId, this.source);
        svg = renderedSvg;
        
        // 存入缓存
        mermaidRenderCache.set(cacheKey, svg);
      }

      // 渲染 SVG
      container.innerHTML = svg;
      
    } catch (error) {
      // 渲染失败：显示错误信息
      console.error('Mermaid render error:', error);
      container.innerHTML = `
        <div class="cm-mermaid-error">
          <div style="color: #c83c3c; font-size: 0.9em; margin-bottom: 4px;">
            ⚠️ Mermaid 渲染失败
          </div>
          <pre style="color: #666; font-size: 0.85em; white-space: pre-wrap; word-break: break-word;">${this.escapeHtml(this.source)}</pre>
        </div>
      `;
      container.classList.add('cm-mermaid-error');
    }
  }

  /**
   * HTML 转义
   * 
   * @param text - 原始文本
   * @returns 转义后的文本
   */
  private escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  /**
   * 是否忽略事件
   * 
   * Mermaid 卡片的交互都在 DOM 事件里处理，返回 true 防止 CodeMirror
   * 把点击卡片主体解释为“把光标放进被替换的源码范围”。
   * 
   * @returns true（让 CodeMirror 忽略 widget 内部事件）
   */
  ignoreEvent() {
    return true;
  }

  /**
   * 估计高度
   * 
   * 用于虚拟滚动优化。返回一个合理的默认高度。
   * 
   * @returns 估计的高度（像素）
   */
  get estimatedHeight() {
    return 200;  // Mermaid 图表的合理默认高度
  }
}

/**
 * 构建块级 Mermaid 图表装饰集
 * 
 * **工作流程：**
 * 1. 遍历语法树，找到所有 FencedCode 节点
 * 2. 检查语言是否为 "mermaid"
 * 3. 判断光标是否在代码块内
 * 4. 提取 Mermaid 源码（去除 ```mermaid 和 ``` 分隔符）
 * 5. 根据光标位置决定渲染策略：
 *    - 光标在块内：源码可见，图表卡片显示在块后
 *    - 光标在块外：用图表卡片替换整个块
 * 
 * @param state - 编辑器状态
 * @returns 装饰集合
 */
function buildBlockMermaidDecorations(state: EditorState): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  // 确保语法树已构建（最多等待 100ms）
  const tree = ensureSyntaxTree(state, state.doc.length, 100);
  if (!tree) return builder.finish();

  const sel = state.selection.main;
  const cursorLine = state.doc.lineAt(sel.head).number;
  const renderVersion = state.field(mermaidCacheVersionField);

  // 遍历语法树
  tree.iterate({
    enter(node) {
      if (node.name !== 'FencedCode') return;
      
      // 获取代码块的文本
      const codeText = state.sliceDoc(node.from, node.to);
      
      // 检查是否是 mermaid 代码块
      const langMatch = codeText.match(/^```(\w+)\s*\n/);
      if (!langMatch || langMatch[1].toLowerCase() !== 'mermaid') return;
      
      // 获取代码块的行范围
      const fromLine = state.doc.lineAt(node.from).number;
      const toLine = state.doc.lineAt(node.to).number;
      const selFromLine = state.doc.lineAt(sel.from).number;
      const selToLine = state.doc.lineAt(sel.to).number;
      
      // 判断选区是否与代码块相交
      const intersects =
        (cursorLine >= fromLine && cursorLine <= toLine) ||
        (selFromLine <= toLine && selToLine >= fromLine);

      // 提取 Mermaid 源码（去除开头和结尾的 ```）
      const lines = codeText.split('\n');
      if (lines.length < 3) return;  // 至少需要 ```mermaid\n...\n```
      
      const mermaidSource = lines.slice(1, -1).join('\n').trim();
      if (!mermaidSource) return;  // 如果内容为空，跳过
      
      // 计算内容范围（不包括 ``` 分隔符）
      const firstLineEnd = node.from + lines[0].length + 1;  // +1 for \n
      const lastLineStart = node.to - lines[lines.length - 1].length;
      const contentFrom = firstLineEnd;
      const contentTo = lastLineStart;

      // 生成唯一 ID
      const chartId = generateMermaidId();
      
      // 创建 Mermaid Widget
      const widget = new BlockMermaidWidget(
        mermaidSource,
        contentFrom,
        contentTo,
        chartId,
        renderVersion,
      );

      if (intersects) {
        // 光标在块内 → 不渲染 widget，让用户直接编辑源码
        // （不做任何操作，跳过这个代码块）
      } else {
        // 光标在块外 → 用渲染后的卡片替换整个块
        builder.add(node.from, node.to, Decoration.replace({ widget, block: true }));
      }
    },
  });

  return builder.finish();
}

/**
 * 块级 Mermaid 图表 StateField
 * 
 * 管理块级 Mermaid 图表的装饰集。
 * 
 * **更新条件：**
 * - 文档内容变化
 * - 选区变化（影响光标是否在代码块内的判断）
 * - 收到 clearMermaidCacheEffect 时清除缓存并重新构建
 */
const blockMermaidField = StateField.define<DecorationSet>({
  create: (state) => buildBlockMermaidDecorations(state),
  update(prev, tr) {
    const hasCacheClear = tr.effects.some((effect) => effect.is(clearMermaidCacheEffect));
    if (tr.docChanged || tr.selection || hasCacheClear) {
      return buildBlockMermaidDecorations(tr.state);
    }
    return prev;
  },
  provide: (f) => EditorView.decorations.from(f),
});

/**
 * 块级 Mermaid 图表主题样式
 * 
 * **样式说明：**
 * - `.cm-mermaid-block-card`: 图表卡片容器，悬停时显示边框和背景
 * - `.cm-mermaid-block`: 图表渲染区域，居中对齐
 * - `.cm-mermaid-block-source`: 源码图标按钮，默认隐藏，悬停卡片时显示
 * - `.cm-mermaid-block-zoom`: 放大按钮，默认隐藏，悬停卡片时显示
 * - `.cm-mermaid-error`: 渲染错误时的回退样式
 */
const blockMermaidTheme = EditorView.theme({
  '.cm-mermaid-block-card': {
    position: 'relative',
    border: '1px solid transparent',
    borderRadius: '6px',
    margin: '8px 0',
    padding: '10px 12px',
    cursor: 'pointer',
    transition: 'border-color 0.12s ease, background-color 0.12s ease',
  },
  '.cm-mermaid-block-card:hover': {
    borderColor: 'rgba(127, 127, 127, 0.25)',
    backgroundColor: 'rgba(127, 127, 127, 0.04)',
  },
  '.cm-mermaid-block': {
    textAlign: 'center',
    overflow: 'auto',
  },
  '.cm-mermaid-block svg': {
    maxWidth: '100%',
    height: 'auto',
  },
  '.cm-mermaid-block-source, .cm-mermaid-block-zoom': {
    position: 'absolute',
    top: '6px',
    border: 'none',
    background: 'transparent',
    cursor: 'pointer',
    color: 'rgba(127, 127, 127, 0.7)',
    padding: '4px',
    borderRadius: '4px',
    opacity: '0',
    transition: 'opacity 0.12s ease, background-color 0.12s ease',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    lineHeight: '0',
  },
  '.cm-mermaid-block-source': {
    right: '36px',
  },
  '.cm-mermaid-block-zoom': {
    right: '8px',
  },
  '.cm-mermaid-block-card:hover .cm-mermaid-block-source, .cm-mermaid-block-card:hover .cm-mermaid-block-zoom': {
    opacity: '1',
  },
  '.cm-mermaid-block-source:hover, .cm-mermaid-block-zoom:hover': {
    backgroundColor: 'rgba(127, 127, 127, 0.15)',
  },
  '.cm-mermaid-error': {
    padding: '12px',
    backgroundColor: 'rgba(200, 60, 60, 0.05)',
    borderRadius: '4px',
    border: '1px solid rgba(200, 60, 60, 0.2)',
  },
});

/**
 * Mermaid 扩展配置选项
 */
export interface BlockMermaidOptions {
  /** 是否启用 Mermaid 渲染，默认为 true */
  enabled?: boolean;
}

/**
 * 创建块级 Mermaid 图表扩展
 * 
 * **功能：**
 * 将 ```mermaid 代码块渲染为 Mermaid 图表卡片。
 * 
 * **交互模式：**
 * - 光标在代码块外：显示渲染后的图表卡片
 * - 光标在代码块内：同时显示源码和渲染结果
 * - 点击图片区域或放大图标：发出 EditorMermaidZoomRequestEvent（宿主层打开放大 Modal）
 * - 点击源码图标：选中源码区域，CodeMirror 显示原始 ```mermaid 代码块进行编辑
 * 
 * @param options - 配置选项
 * @returns CodeMirror 扩展数组
 */
export function createBlockMermaidExtension(options: BlockMermaidOptions = {}): Extension {
  const { enabled = true } = options;
  
  if (!enabled) {
    return [];  // 如果禁用，返回空扩展
  }
  
  return [mermaidCacheVersionField, blockMermaidField, blockMermaidTheme];
}
