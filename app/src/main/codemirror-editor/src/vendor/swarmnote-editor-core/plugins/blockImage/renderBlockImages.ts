/**
 * 块级图片渲染扩展 - Obsidian 风格交互
 *
 * **功能：**
 * 将独占一行的 `![alt](url)` 渲染为实际的 <img> 元素。
 *
 * **交互模型（对齐 Obsidian）：**
 * 
 * 1. **默认状态**：`Decoration.replace`，源码行被图片 widget 替换，仅渲染图片
 * 
 * 2. **点击图片**：选中状态（边框 + 右上角 `</>` 图标），不移动光标
 * 
 * 3. **点击图标**：把光标送进图片所在行 → 切到「源 + 图共显」模式
 *    - 使用 `Decoration.widget`(side:1) 把图片追加到行尾
 *    - 源码行同时可见可编辑
 * 
 * 4. **退出选中**：光标离开 / 点击别处 / Esc → 自动退出选中态
 *
 * **技术限制：**
 * CM6 要求 block 级 decoration 必须来自 StateField（不能来自 ViewPlugin）。
 */
import { ensureSyntaxTree } from '@codemirror/language';
import {
  type EditorState,
  RangeSetBuilder,
  StateEffect,
  StateField,
  type Extension,
} from '@codemirror/state';
import {
  Decoration,
  type DecorationSet,
  EditorView,
  WidgetType,
  keymap,
} from '@codemirror/view';
import type { SyntaxNode } from '@lezer/common';
import { editorEventCallback, EditorEventType } from '../../events';

/** 图片元素的 CSS 类名常量 */
const imageClassName = 'cm-md-image';

/**
 * 冻结的 CSS 类名表 —— 所有选择器的单一事实来源
 * 
 * **设计目的：**
 * 同时被 `EditorView.theme()` 和 widget 中的 DOM 操作引用。
 * 这里的拼写错误会在编译时报错；而原始字符串中的拼写错误是静默的样式 bug。
 * 
 * **类名说明：**
 * - block/frame/selected/sourceVisible - 块级图片相关
 * - sourceBtn - 源码切换按钮
 * - linked/linkBtn - 链接图片相关
 * - fallback - 加载失败回退
 * - inline* - 内联图片相关
 */
const CLS = {
  /** 块级图片根类 */
  block: imageClassName,
  /** 图片容器框架 */
  frame: `${imageClassName}-frame`,
  /** 选中状态 */
  selected: `${imageClassName}-selected`,
  /** 源码可见状态 */
  sourceVisible: `${imageClassName}-source-visible`,
  /** 源码切换按钮 */
  sourceBtn: `${imageClassName}-source-btn`,
  /** 链接图片标记 */
  linked: `${imageClassName}-linked`,
  /** 链接打开按钮 */
  linkBtn: `${imageClassName}-link-btn`,
  /** 加载失败回退 */
  fallback: `${imageClassName}-fallback`,
  /** 内联图片 */
  inline: `${imageClassName}-inline`,
  /** 内联图片 img 标签 */
  inlineImg: `${imageClassName}-inline-img`,
  /** 内联图片回退 */
  inlineFallback: `${imageClassName}-inline-fallback`,
  /** 内联链接图片 */
  inlineLinked: `${imageClassName}-inline-linked`,
  /** 内联链接徽章 */
  inlineLinkBadge: `${imageClassName}-inline-link-badge`,
} as const;


/**
 * 剥离 CommonMark 标题分隔符（`"..."` / `'...'` / `(...)`）
 * 
 * @param raw - 预处理后的字符串
 * @returns 剥离后的标题，如果输入未被包裹则返回 null
 */
function stripWrappingQuotes(raw: string): string | null {
  if (raw.length < 2) return null;
  const first = raw[0];
  const last = raw[raw.length - 1];
  // 双引号或单引号包裹
  if ((first === '"' && last === '"') || (first === "'" && last === "'")) {
    return raw.slice(1, -1);
  }
  // 圆括号包裹
  if (first === '(' && last === ')') {
    return raw.slice(1, -1);
  }
  return null;
}

/** 图片 URL 解析器类型 */
export type ImageResolver = (src: string) => string | Promise<string>;

/** 块级图片选项 */
export interface BlockImageOptions {
  /**
   * 可选的解析器，接收 `![alt](src)` 中的原始 `src`。
   * 返回应该赋值给 `<img src>` 的 URL。
   * 适用于将工作区相对路径映射到平台特定协议（如 Tauri `asset://`）。
   * 可以返回 Promise —— widget 在解析完成前渲染占位高度。
   */
  resolver?: ImageResolver;
  /** 图片加载失败后的最大重试次数。默认为 3。 */
  maxLoadAttempts?: number;
}

/**
 * 刷新所有块级图片装饰的 Effect
 * 
 * 派发此 effect 会强制重建所有块级图片装饰（重新调用解析器）。
 * 当上游资源已刷新时使用（例如 P2P 同步二进制媒体后）。
 */
export const refreshBlockImagesEffect = StateEffect.define<null>();

/**
 * 内部 Effect —— 设置当前选中的块级图片为指定的 line.from 偏移量，
 * 或传 null 清除选中。
 * 
 * Widget 点击时派发带 line.from 的 effect；
 * 选区移出图片行时自动清除。
 */
const setSelectedImageEffect = StateEffect.define<number | null>();

/**
 * 选中图片 StateField
 * 
 * 追踪当前哪个图片处于选中状态。
 * 
 * **更新逻辑：**
 * 1. 处理 setSelectedImageEffect → 直接设置新值
 * 2. 文档变化 → 清除选中（避免位置失效）
 * 3. 选区变化 → 如果光标不在图片行，清除选中
 */
const selectedImageField = StateField.define<number | null>({
  /** 初始状态：无选中 */
  create() {
    return null;
  },
  /**
   * 状态更新
   * 
   * @param value - 当前选中的图片行 from 位置
   * @param tr - 事务对象
   * @returns 新的选中状态
   */
  update(value, tr) {
    // 优先处理 effect
    for (const effect of tr.effects) {
      if (effect.is(setSelectedImageEffect)) return effect.value;
    }
    // 文档变化 → 清除选中
    if (tr.docChanged) return null;
    // 选区变化 → 检查是否在图片行
    if (tr.selection && value !== null) {
      const head = tr.state.selection.main.head;
      const headLineFrom = tr.state.doc.lineAt(head).from;
      if (headLineFrom !== value) return null;  // 不在图片行，清除
    }
    return value;
  },
});

/**
 * 图片高度缓存
 * 
 * **目的：**
 * 避免图片加载时的布局抖动（layout shift）。
 * 首次加载后缓存图片高度，下次渲染时直接使用缓存值设置占位高度。
 * 
 * **实现：**
 * - LRU 缓存策略（最近使用移到末尾）
 * - 最大 500 条记录
 */
class ImageHeightCache {
  private readonly cache = new Map<string, number>();
  private readonly maxEntries = 500;

  /**
   * 获取缓存的高度
   * 
   * @param key - 缓存键（通常是图片 URL）
   * @returns 缓存的高度，未命中返回 undefined
   */
  get(key: string): number | undefined {
    const value = this.cache.get(key);
    if (value !== undefined) {
      // LRU：删除后重新插入，移到末尾
      this.cache.delete(key);
      this.cache.set(key, value);
    }
    return value;
  }

  /**
   * 设置缓存
   * 
   * @param key - 缓存键
   * @param height - 图片高度
   */
  set(key: string, height: number): void {
    if (this.cache.has(key)) {
      // 已存在：删除后重新插入（更新 LRU 顺序）
      this.cache.delete(key);
    } else if (this.cache.size >= this.maxEntries) {
      // 达到上限：删除最旧的条目（Map 的第一个 key）
      const firstKey = this.cache.keys().next().value;
      if (firstKey) this.cache.delete(firstKey);
    }
    this.cache.set(key, height);
  }
}

/** 全局高度缓存实例 */
const heightCache = new ImageHeightCache();

/** Markdown 源码图标 SVG */
const SOURCE_ICON_SVG = `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>`;

/** 外部链接图标 SVG —— Lucide 风格 */
const EXTLINK_ICON_SVG = `<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 3h6v6"></path><path d="M10 14L21 3"></path><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path></svg>`;

/**
 * 解析 lezer-markdown `Image` 节点的文本为 `{ alt, src, title }`
 *
 * **支持的格式：**
 *   - `![alt](url)`
 *   - `![alt](url "title")` / `'title'` / `(title)`
 *   - `![alt](<url with spaces>)`（尖括号包裹的 src）
 *   - `![alt](<url> "title")`
 *
 * **返回值：**
 * 如果文本看起来不是有效的图片表达式则返回 null
 * （lezer 应该已经验证过，但我们防御性地再次检查）。
 * 
 * @param text - 图片节点的文本内容
 * @returns 解析结果或 null
 */
function parseImageMarkdown(
  text: string,
): { alt: string; src: string; title: string } | null {
  if (!text.startsWith('![') || !text.endsWith(')')) return null;
  const altEnd = text.indexOf('](', 2);
  if (altEnd === -1) return null;

  // 提取 alt 文本
  const alt = text.slice(2, altEnd);
  // 提取括号内的内容并去首尾空格
  const inside = text.slice(altEnd + 2, -1).trim();

  let src: string;
  let rest: string;

  // 处理尖括号包裹的 URL（允许空格）
  if (inside.startsWith('<')) {
    const close = inside.indexOf('>');
    if (close === -1) return null;
    src = inside.slice(1, close);
    rest = inside.slice(close + 1).trimStart();
  } else {
    // 普通 URL（遇到空格截止）
    const wsIdx = inside.search(/\s/);
    if (wsIdx === -1) {
      src = inside;
      rest = '';
    } else {
      src = inside.slice(0, wsIdx);
      rest = inside.slice(wsIdx + 1).trimStart();
    }
  }

  // 静默忽略格式错误的尾部内容 —— lezer 已将节点标记为 Image，
  // 所以即使标题不可提取，src 仍然会被渲染。
  const title = rest ? stripWrappingQuotes(rest) ?? '' : '';
  return { alt, src, title };
}


/**
 * 检测 `[![alt](img)](link)` 模式 —— Markdown 的“图片作为链接”
 *
 * **Lezer 树结构：**
 * ```
 * Link
 *   LinkMark `[`
 *   Image          ← 遍历到此节点时调用
 *   LinkMark `]`
 *   LinkMark `(`
 *   URL            ← 链接的 href
 *   LinkTitle?
 *   LinkMark `)`
 * ```
 *
 * **返回条件：**
 * 仅当 Image 是 Link 的 `[...]` 括号内的直接（且唯一）内容时才返回非 null
 * —— 即经典的 Markdown 链接图片语法。
 * 
 * @param imageNode - 图片语法节点
 * @param state - 编辑器状态
 * @returns 链接信息或 null
 */
function getLinkedImageInfo(
  imageNode: SyntaxNode,
  state: EditorState,
): { linkFrom: number; linkTo: number; linkUrl: string; linkTitle: string } | null {
  const parent = imageNode.parent;
  if (!parent || parent.name !== 'Link') return null;
  // Image 必须紧接在 Link 的 `[` 之后
  if (imageNode.from !== parent.from + 1) return null;

  // 提取 URL 和标题
  const urlNode = parent.getChild('URL');
  const url = urlNode ? state.sliceDoc(urlNode.from, urlNode.to) : null;
  const titleNode = parent.getChild('LinkTitle');
  const title = titleNode
    ? stripWrappingQuotes(state.sliceDoc(titleNode.from, titleNode.to).trim()) ?? ''
    : '';
  if (!url) return null;
  return { linkFrom: parent.from, linkTo: parent.to, linkUrl: url, linkTitle: title };
}


/**
 * 通过编辑器的事件回调 Facet 派发 `LinkOpen` 事件
 * 
 * **设计原因：**
 * 与 `createCtrlClickLinksExtension` 使用相同的通道。
 * 宿主（NoteEditor.tsx）负责通过 `@tauri-apps/plugin-opener` 实际打开 URL。
 * 在 Tauri webview 中直接调用 `window.open` 不可靠，所以我们通过宿主处理。
 * 
 * @param view - 编辑器视图
 * @param url - 要打开的 URL
 */
function dispatchLinkOpen(view: EditorView, url: string): void {
  const callback = view.state.facet(editorEventCallback);
  callback?.({ kind: EditorEventType.LinkOpen, url });
}


/**
 * 图片加载选项接口
 * 
 * 用于配置 `<img>` 的异步加载 + 指数退避重试 + 永久失败回退。
 * 块级和内联变体仅在回退 DOM（追加 vs. 替换）和可选的成功钩子
 * （仅块级需要高度缓存）上有所不同，因此由调用者提供。
 */
interface ImageLoadingOpts {
  /** img 元素 */
  img: HTMLImageElement;
  /** 原始 src */
  rawSrc: string;
  /** URL 解析器 */
  resolver: ImageResolver | undefined;
  /** 最大重试次数 */
  maxAttempts: number;
  /** Widget DOM 是否仍然挂载；每次重试时检查 */
  isAlive: () => boolean;
  /** 成功加载钩子（例如记录高度到缓存） */
  onLoad?: () => void;
  /** 重试耗尽后调用；widget 应该切换为回退内容 */
  onPermanentFail: () => void;
}

/**
 * 附加图片加载逻辑
 * 
 * **功能：**
 * 1. 异步解析 URL（如果提供了 resolver）
 * 2. 设置 img.src
 * 3. 加载成功 → 重置尝试计数，调用 onLoad
 * 4. 加载失败 → 指数退避重试，直到达到 maxAttempts
 * 5. 重试耗尽 → 调用 onPermanentFail
 * 
 * @param opts - 加载选项
 */
function attachImageLoading(opts: ImageLoadingOpts): void {
  const { img, rawSrc, resolver, maxAttempts, isAlive, onLoad, onPermanentFail } = opts;
  let attempt = 0;

  /** 解析 URL 并设置 src */
  const resolve = async () => {
    try {
      // 使用解析器（如果有）
      const resolved = resolver ? await resolver(rawSrc) : rawSrc;
      if (!isAlive()) return;  // Widget 已销毁，中止
      img.src = resolved;
    } catch {
      // 解析失败，使用原始 src
      if (!isAlive()) return;
      img.src = rawSrc;
    }
  };

  if (onLoad) {
    img.onload = () => {
      attempt = 0;
      onLoad();
    };
  }

  img.onerror = () => {
    if (!isAlive()) return;
    attempt += 1;
    if (attempt <= maxAttempts) {
      // 500ms, 1s, 2s, 4s, ...
      setTimeout(() => {
        if (isAlive()) resolve();
      }, 2 ** (attempt - 1) * 500);
      return;
    }
    onPermanentFail();
  };

  resolve();
}


/**
 * 将 DOM 元素转换回其所在的行（限制在文档末尾内）
 * 
 * @param view - 编辑器视图
 * @param el - DOM 元素
 * @returns 对应的行对象，失败返回 null
 */
function lineAtDOM(view: EditorView, el: HTMLElement) {
  const pos = view.posAtDOM(el);
  if (pos < 0) return null;
  return view.state.doc.lineAt(Math.min(pos, view.state.doc.length));
}

/**
 * 创建外部链接徽章按钮
 * 
 * **功能：**
 * 覆盖在链接图片上的外链图标按钮。
 * 块级（图片框架右上角的图标按钮）和内联（内联 `<img>` 右上角的小徽章）
 * 共享相同的点击语义 —— 仅在包装类名和标签上有所不同。
 * 
 * @param view - 编辑器视图
 * @param linkUrl - 链接 URL
 * @param linkTitle - 链接标题
 * @param className - CSS 类名
 * @returns 链接按钮元素
 */
function makeLinkBadge(
  view: EditorView,
  linkUrl: string,
  linkTitle: string | undefined,
  className: string,
): HTMLButtonElement {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = className;
  // 提示文本：标题 + URL
  btn.title = linkTitle ? `${linkTitle}\n${linkUrl}` : linkUrl;
  btn.setAttribute('aria-label', `Open link: ${linkUrl}`);
  btn.innerHTML = EXTLINK_ICON_SVG;  // 外链图标
  btn.addEventListener('mousedown', (e) => {
    e.preventDefault();
    e.stopPropagation();
    dispatchLinkOpen(view, linkUrl);  // 派发打开链接事件
  });
  return btn;
}


/**
 * 块级图片 Widget
 * 
 * **功能：**
 * 渲染独占一行的 `![alt](url)` 为实际的 <img> 元素。
 * 
 * **状态管理：**
 * - tick: 刷新代次，不同值产生不相等的 widget（强制重建）
 * - selected: 是否为用户选中的图片
 * - sourceVisible: 源码行是否可见（光标在行上 / 显示源码模式）
 * - linkUrl/linkTitle: 如果图片被包裹在链接中 `[![](img)](href)`
 */
class ImageWidget extends WidgetType {
  constructor(
    /** 原始 src */
    private readonly rawSrc: string,
    /** alt 文本 */
    private readonly alt: string,
    /** 标题 */
    private readonly title: string,
    /** URL 解析器 */
    private readonly resolver: ImageResolver | undefined,
    /** 最大加载尝试次数 */
    private readonly maxLoadAttempts: number,
    /** 刷新代次 —— 不同值产生不相等的 widget */
    private readonly tick: number,
    /** 此图片是否为当前用户选中 */
    private readonly selected: boolean,
    /** 源码行是否也可见（光标在行上 / 显示源码模式） */
    private readonly sourceVisible: boolean,
    /** 如果图片被包裹在 Markdown 链接中 `[![](img)](href)` */
    private readonly linkUrl: string | undefined,
    private readonly linkTitle: string | undefined,
  ) {
    super();
  }

  /**
   * 判断两个 widget 是否相等
   * 
   * 只有所有属性都相同时才返回 true，否则 CodeMirror 会重建 DOM。
   * tick 的变化会强制重建（用于刷新图片）。
   */
  eq(other: ImageWidget) {
    return (
      this.rawSrc === other.rawSrc &&
      this.alt === other.alt &&
      this.title === other.title &&
      this.resolver === other.resolver &&
      this.tick === other.tick &&
      this.selected === other.selected &&
      this.sourceVisible === other.sourceVisible &&
      this.linkUrl === other.linkUrl &&
      this.linkTitle === other.linkTitle
    );
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 层级：**
   * ```
   * div.cm-md-image [.selected] [.source-visible] [.linked]
   *   span.cm-md-image-frame [min-height: cached]
   *     img[src, alt, title, draggable=false]
   *     button.cm-md-image-source-btn (</> 图标)
   *     button.cm-md-image-link-btn (外链图标，仅链接图片)
   * ```
   * 
   * @param view - 编辑器视图
   * @returns 容器元素
   */
  toDOM(view: EditorView) {
    // 创建根容器
    const container = document.createElement('div');
    container.classList.add(CLS.block);
    if (this.selected) container.classList.add(CLS.selected);  // 选中状态
    if (this.sourceVisible) container.classList.add(CLS.sourceVisible);  // 源码可见
    if (this.linkUrl) container.classList.add(CLS.linked);  // 链接图片

    // 创建图片框架
    const frame = document.createElement('span');
    frame.className = CLS.frame;

    // 使用缓存的高度避免布局抖动
    const cached = heightCache.get(this.rawSrc);
    if (cached) frame.style.minHeight = `${cached}px`;

    // 创建 img 元素
    const img = document.createElement('img');
    img.alt = this.alt;
    if (this.title) img.title = this.title;
    img.draggable = false;  // 禁止拖拽

    // 附加加载逻辑（异步 + 重试 + 回退）
    attachImageLoading({
      img,
      rawSrc: this.rawSrc,
      resolver: this.resolver,
      maxAttempts: this.maxLoadAttempts,
      isAlive: () => container.isConnected,  // 检查 widget 是否仍挂载
      onLoad: () => {
        // 加载成功：缓存高度，清除最小高度限制
        if (frame.isConnected) heightCache.set(this.rawSrc, frame.offsetHeight);
        frame.style.minHeight = '';
      },
      onPermanentFail: () => {
        // 永久失败：隐藏 img，显示回退文本
        frame.style.minHeight = '';
        img.style.display = 'none';
        const fallback = document.createElement('span');
        fallback.className = CLS.fallback;
        fallback.textContent = this.alt || 'Image failed to load';
        frame.appendChild(fallback);
      },
    });
    frame.appendChild(img);

    // 创建源码切换按钮（</> 图标）
    const sourceBtn = document.createElement('button');
    sourceBtn.type = 'button';
    sourceBtn.className = CLS.sourceBtn;
    sourceBtn.title = 'Show markdown source';
    sourceBtn.setAttribute('aria-label', 'Show markdown source');
    sourceBtn.innerHTML = SOURCE_ICON_SVG;
    sourceBtn.addEventListener('mousedown', (e) => {
      e.preventDefault();
      e.stopPropagation();
      // 点击按钮：将光标移动到图片行，清除选中状态
      const line = lineAtDOM(view, container);
      if (!line) return;
      view.dispatch({
        selection: { anchor: line.from },
        effects: setSelectedImageEffect.of(null),  // 清除选中
        scrollIntoView: false,
      });
      view.focus();
    });
    frame.appendChild(sourceBtn);

    // 如果是链接图片，添加外链徽章
    if (this.linkUrl) {
      frame.appendChild(makeLinkBadge(view, this.linkUrl, this.linkTitle, CLS.linkBtn));
    }

    // 点击图片框架的事件处理
    frame.addEventListener('mousedown', (e) => {
      if (e.button !== 0) return;  // 只处理左键
      
      // Ctrl/Cmd + 点击：打开链接（如果存在）
      if (this.linkUrl && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        e.stopPropagation();
        dispatchLinkOpen(view, this.linkUrl);
        return;
      }
      
      // 如果源码已可见，不处理（允许正常选择文本）
      if (this.sourceVisible) return;
      
      // 普通点击：选中图片
      e.preventDefault();
      const line = lineAtDOM(view, container);
      if (!line) return;
      view.dispatch({ effects: setSelectedImageEffect.of(line.from) });
    });

    container.appendChild(frame);
    return container;
  }

  /**
   * 估算高度（用于 CodeMirror 的虚拟滚动优化）
   * 
   * @returns 缓存的高度，未命中返回 -1（让 CM 自动测量）
   */
  get estimatedHeight() {
    return heightCache.get(this.rawSrc) ?? -1;
  }

  /**
   * 是否忽略事件
   * 
   * 返回 true 表示让 CodeMirror 处理事件（如选区、滚动等）。
   * 我们自己在 mousedown 中处理了特定交互。
   */
  ignoreEvent() {
    return true;
  }
}


/**
 * 内联图片 Widget
 * 
 * **使用场景：**
 * 用于任何**不独占一行**的 `![alt](url)`，例如：
 * - 文本中的图片
 * - 标题内的图片
 * - 强调文本中的图片
 * - 引用块 / 列表 / 表格单元格中的图片
 * - 同一行上的多个图片
 *
 * **与块级 Widget 的区别：**
 * - 无选中状态（小图片上显示图标会是噪音）
 * - 无源码切换按钮
 * - 点击时将光标定位到 [node.from, node.to] → buildDecorations 跳过渲染
 *   → 用户看到原始 Markdown（标准的内联实时预览显示逻辑）
 */
class InlineImageWidget extends WidgetType {
  constructor(
    /** 原始 src */
    private readonly rawSrc: string,
    /** alt 文本 */
    private readonly alt: string,
    /** 标题 */
    private readonly title: string,
    /** URL 解析器 */
    private readonly resolver: ImageResolver | undefined,
    /** 最大加载尝试次数 */
    private readonly maxLoadAttempts: number,
    /** 刷新代次 */
    private readonly tick: number,
    /** 链接 URL（如果存在） */
    private readonly linkUrl: string | undefined,
    private readonly linkTitle: string | undefined,
  ) {
    super();
  }

  /**
   * 判断相等性
   */
  eq(other: InlineImageWidget) {
    return (
      this.rawSrc === other.rawSrc &&
      this.alt === other.alt &&
      this.title === other.title &&
      this.resolver === other.resolver &&
      this.tick === other.tick &&
      this.linkUrl === other.linkUrl &&
      this.linkTitle === other.linkTitle
    );
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 层级：**
   * ```
   * span.cm-md-image-inline [.cm-md-image-inline-linked]
   *   img.cm-md-image-inline-img [alt, title, draggable=false]
   *   span.cm-md-image-inline-link-badge (外链徽章，仅链接图片)
   * ```
   * 
   * @param view - 编辑器视图
   * @returns 包装元素
   */
  toDOM(view: EditorView) {
    // 创建包装容器
    const wrap = document.createElement('span');
    wrap.className = CLS.inline;
    if (this.linkUrl) wrap.classList.add(CLS.inlineLinked);  // 链接图片标记

    // 创建 img 元素
    const img = document.createElement('img');
    img.className = CLS.inlineImg;
    img.alt = this.alt;
    if (this.title) img.title = this.title;
    img.draggable = false;

    // 附加加载逻辑（无 onLoad 钩子，内联不需要高度缓存）
    attachImageLoading({
      img,
      rawSrc: this.rawSrc,
      resolver: this.resolver,
      maxAttempts: this.maxLoadAttempts,
      isAlive: () => wrap.isConnected,
      onPermanentFail: () => {
        // 永久失败：用回退文本替换 img
        const fb = document.createElement('span');
        fb.className = CLS.inlineFallback;
        fb.textContent = this.alt || 'image';
        if (img.parentNode === wrap) wrap.replaceChild(fb, img);
      },
    });
    wrap.appendChild(img);

    // 如果是链接图片，添加外链徽章
    if (this.linkUrl) {
      wrap.appendChild(makeLinkBadge(view, this.linkUrl, this.linkTitle, CLS.inlineLinkBadge));
    }

    return wrap;
  }

  /**
   * 是否忽略事件
   * 
   * 返回 false 表示让 CM6 正常处理点击 —— 点击内联图片会将光标定位到
   * widget 边界，这会触发显示逻辑（reveal）。
   */
  ignoreEvent() {
    return false;
  }
}


const blockImageTheme = EditorView.theme({
  // Default selection-border color tokens — overridable via createEditorTheme
  '&': {
    '--cm-image-selection-border': 'hsl(40, 72%, 46%)',
    '--cm-image-icon-fg': 'hsl(28, 10%, 14%)',
    '--cm-image-icon-bg': 'rgba(255, 255, 255, 0.92)',
    '--cm-image-icon-bg-hover': 'rgba(255, 255, 255, 1)',
    '--cm-image-icon-shadow': '0 1px 3px rgba(0, 0, 0, 0.18)',
  },
  [`& .${CLS.block}`]: {
    padding: '4px 0',
    display: 'flex',
    justifyContent: 'center',
  },
  [`& .${CLS.frame}`]: {
    position: 'relative',
    display: 'inline-block',
    maxWidth: '100%',
    borderRadius: '4px',
  },
  [`& .${CLS.frame} > img`]: {
    maxWidth: '100%',
    display: 'block',
    borderRadius: '4px',
    cursor: 'pointer',
    userSelect: 'none',
  },
  [`& .${CLS.selected} .${CLS.frame}`]: {
    outline: '2px solid var(--cm-image-selection-border)',
    outlineOffset: '2px',
  },
  [`& .${CLS.sourceBtn}`]: {
    position: 'absolute',
    top: '6px',
    right: '6px',
    display: 'none',
    alignItems: 'center',
    justifyContent: 'center',
    width: '26px',
    height: '26px',
    padding: '0',
    margin: '0',
    background: 'var(--cm-image-icon-bg)',
    color: 'var(--cm-image-icon-fg)',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    boxShadow: 'var(--cm-image-icon-shadow)',
    backdropFilter: 'blur(4px)',
    WebkitBackdropFilter: 'blur(4px)',
  },
  [`& .${CLS.sourceBtn}:hover`]: {
    background: 'var(--cm-image-icon-bg-hover)',
  },
  [`& .${CLS.selected} .${CLS.sourceBtn}`]: {
    display: 'inline-flex',
  },
  // External-link button — bottom-right of a linked image. Always visible
  // (independent of selection), faded out a bit at rest, full-strength on
  // hover. Sized slightly smaller than the source-code button so the two
  // can coexist when an image is selected AND linked.
  [`& .${CLS.linkBtn}`]: {
    position: 'absolute',
    bottom: '6px',
    right: '6px',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '24px',
    height: '24px',
    padding: '0',
    margin: '0',
    background: 'var(--cm-image-icon-bg)',
    color: 'var(--cm-image-icon-fg)',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    boxShadow: 'var(--cm-image-icon-shadow)',
    backdropFilter: 'blur(4px)',
    WebkitBackdropFilter: 'blur(4px)',
    opacity: '0.85',
    transition: 'opacity 120ms ease',
  },
  [`& .${CLS.linkBtn}:hover`]: {
    opacity: '1',
    background: 'var(--cm-image-icon-bg-hover)',
  },
  [`& .${CLS.inlineLinked}`]: {
    position: 'relative',
  },
  [`& .${CLS.inlineLinkBadge}`]: {
    position: 'absolute',
    top: '2px',
    right: '2px',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '18px',
    height: '18px',
    background: 'var(--cm-image-icon-bg)',
    color: 'var(--cm-image-icon-fg)',
    borderRadius: '3px',
    boxShadow: 'var(--cm-image-icon-shadow)',
    cursor: 'pointer',
    pointerEvents: 'auto',
    opacity: '0.85',
  },
  [`& .${CLS.inlineLinkBadge}:hover`]: {
    opacity: '1',
    background: 'var(--cm-image-icon-bg-hover)',
  },
  [`& .${CLS.fallback}`]: {
    display: 'block',
    padding: '8px 12px',
    color: 'rgba(127, 127, 127, 0.8)',
    fontStyle: 'italic',
    fontSize: '0.9em',
  },
  [`& .${CLS.inline}`]: {
    display: 'inline-block',
    verticalAlign: 'middle',
    maxWidth: '100%',
    lineHeight: '0',
  },
  [`& .${CLS.inlineImg}`]: {
    maxWidth: '100%',
    maxHeight: 'var(--cm-image-inline-max-height, 320px)',
    display: 'inline-block',
    verticalAlign: 'middle',
    borderRadius: '3px',
  },
  [`& .${CLS.inlineFallback}`]: {
    display: 'inline-block',
    padding: '0 4px',
    color: 'rgba(127, 127, 127, 0.8)',
    fontStyle: 'italic',
    fontSize: '0.9em',
    verticalAlign: 'middle',
  },
  '&.cm-editor.cm-dark': {
    '--cm-image-selection-border': 'hsl(40, 72%, 52%)',
    '--cm-image-icon-fg': 'hsl(36, 10%, 93%)',
    '--cm-image-icon-bg': 'rgba(36, 36, 36, 0.92)',
    '--cm-image-icon-bg-hover': 'rgba(48, 48, 48, 1)',
    '--cm-image-icon-shadow': '0 1px 3px rgba(0, 0, 0, 0.4)',
  },
});


/**
 * 装饰条目接口
 * 
 * 用于在 buildDecorations 中收集待创建的装饰。
 */
interface DecorationEntry {
  /** 起始位置 */
  from: number;
  /** 结束位置 */
  to: number;
  /** 装饰对象 */
  decoration: Decoration;
}

/**
 * 构建图片装饰集
 * 
 * **工作流程：**
 * 
 * 1. 遍历语法树中的所有 Image 节点
 * 2. 检测是否为链接图片 `[![](img)](href)`
 * 3. 判断是块级独占还是内联模式
 * 4. 根据光标位置和选中状态决定渲染策略
 * 5. 创建对应的 Widget 并添加到装饰集
 * 
 * @param state - 编辑器状态
 * @param resolver - URL 解析器
 * @param maxAttempts - 最大加载尝试次数
 * @param tick - 刷新代次（用于强制重建）
 * @returns 装饰集合
 */
function buildDecorations(
  state: EditorState,
  resolver: ImageResolver | undefined,
  maxAttempts: number,
  tick: number,
): DecorationSet {
  const entries: DecorationEntry[] = [];
  // 获取主选区的光标位置
  const head = state.selection.main.head;
  // 光标所在的行号
  const cursorLine = state.doc.lineAt(head).number;
  // 当前选中的图片行起始位置（如果有的话）
  const selectedLineFrom = state.field(selectedImageField, false) ?? null;

  // 确保语法树已构建（最多等待 500ms）
  const tree = ensureSyntaxTree(state, state.doc.length, 500);
  if (!tree) return Decoration.none;

  // 遍历语法树，查找所有 Image 节点
  tree.iterate({
    enter(node) {
      if (node.name !== 'Image') return;

      // 检测图片是否被包裹在 Markdown 链接中 `[![](img)](href)`
      // 如果是，扩展有效范围以覆盖整个链接，并将链接 URL 传递给 widget
      const linkInfo = getLinkedImageInfo(node.node, state);
      const effFrom = linkInfo ? linkInfo.linkFrom : node.from;  // 有效起始位置
      const effTo = linkInfo ? linkInfo.linkTo : node.to;        // 有效结束位置
      const linkUrl = linkInfo?.linkUrl;                          // 链接 URL
      const linkTitle = linkInfo?.linkTitle;                      // 链接标题

      // 获取有效范围所在的行
      const lineFrom = state.doc.lineAt(effFrom);
      const lineTo = state.doc.lineAt(effTo);

      // 解析 ![alt](src "title?") —— 支持括号内的 src 和所有 CommonMark 标题样式
      const nodeText = state.sliceDoc(node.from, node.to);
      const parsed = parseImageMarkdown(nodeText);
      if (!parsed) return;

      // "Block-solo" = 该行（或多行）上除了链接/图片本身外没有其他内容。
      // 块级独占图片获得居中/大尺寸渲染。其他情况（标题、强调、引用前缀、
      // 列表前缀、表格单元格、一行多个图片）→ 内联渲染。
      const textBefore = state.sliceDoc(lineFrom.from, effFrom);
      const textAfter = state.sliceDoc(effTo, lineTo.to);
      const isBlockSolo = textBefore.trim() === '' && textAfter.trim() === '';

      // 块级独占模式：居中显示大图
      if (isBlockSolo) {
        // 判断源码是否可见（光标是否在图片所在行范围内）
        const sourceVisible =
          cursorLine >= lineFrom.number && cursorLine <= lineTo.number;
        // 判断此图片是否为用户选中的图片
        const selected = selectedLineFrom === lineFrom.from;

        // 创建块级图片 Widget
        const widget = new ImageWidget(
          parsed.src,
          parsed.alt,
          parsed.title,
          resolver,
          maxAttempts,
          tick,
          selected,
          sourceVisible,
          linkUrl,
          linkTitle,
        );

        if (sourceVisible) {
          // 源码可见模式：图片渲染在源码行之后 —— 两者都可见，行可编辑
          entries.push({
            from: lineTo.to,
            to: lineTo.to,
            decoration: Decoration.widget({ widget, block: true, side: 1 }),
          });
        } else {
          // 替换模式：用 widget 替换整个源码行范围
          entries.push({
            from: lineFrom.from,
            to: lineTo.to,
            decoration: Decoration.replace({ widget, block: true }),
          });
        }
        return;
      }

      // 内联模式 —— 当光标在有效范围（链接或图片）内时跳过渲染，
      // 以便用户看到原始 Markdown 源码（标准的内联实时预览显示逻辑）
      if (head >= effFrom && head <= effTo) return;

      // 创建内联图片 Widget
      const inlineWidget = new InlineImageWidget(
        parsed.src,
        parsed.alt,
        parsed.title,
        resolver,
        maxAttempts,
        tick,
        linkUrl,
        linkTitle,
      );
      entries.push({
        from: effFrom,
        to: effTo,
        decoration: Decoration.replace({ widget: inlineWidget }),
      });
    },
  });

  // 按起始位置排序装饰条目
  entries.sort((a, b) => a.from - b.from);
  // 使用 RangeSetBuilder 构建最终的装饰集
  const builder = new RangeSetBuilder<Decoration>();
  for (const e of entries) {
    builder.add(e.from, e.to, e.decoration);
  }
  return builder.finish();
}

/**
 * 创建块级图片渲染扩展
 * 
 * **功能概述：**
 * 这个扩展将独占一行的 `![alt](url)` 渲染为实际的 \u003cimg\u003e 元素，
 * 并提供 Obsidian 风格的交互体验（点击图片选中、点击图标切换源码/渲染模式）。
 * 
 * **核心组件：**
 * 1. `decorationField` - StateField，管理图片装饰的生命周期
 * 2. `escapeKeymap` - ESC 键映射，用于清除图片选中状态
 * 3. `blockImageTheme` - 图片相关 CSS 主题
 * 4. `selectedImageField` - 追踪当前选中图片的状态
 * 
 * **工作流程：**
 * 1. 语法树遍历找到所有 Image 节点
 * 2. 判断是块级独占还是内联模式
 * 3. 根据光标位置决定是否显示源码
 * 4. 创建对应的 Widget（ImageWidget 或 InlineImageWidget）
 * 5. 通过 Decoration.replace 或 Decoration.widget 添加到视图
 * 
 * @param options - 配置选项
 * @returns CodeMirror Extension 数组
 */
export function createBlockImageExtension(options: BlockImageOptions = {}): Extension {
  const resolver = options.resolver;  // URL 解析器
  const maxAttempts = options.maxLoadAttempts ?? 3;  // 最大加载尝试次数

  // 可变刷新计数器 —— 在 StateField 事务间共享，
  // 每当 dispatch `refreshBlockImagesEffect` 时递增。
  // 将其 stamp 到 widget 身份中，即使文档本身未改变也能强制重新渲染。
  let tick = 0;

  const decorationField = StateField.define<DecorationSet>({
    create(state) {
      // 初始化时构建装饰
      return buildDecorations(state, resolver, maxAttempts, tick);
    },
    update(deco, tr) {
      // 检查是否有刷新效果
      const hasRefresh = tr.effects.some((e) => e.is(refreshBlockImagesEffect));
      if (hasRefresh) {
        tick += 1;  // 递增刷新计数器以强制重建
        return buildDecorations(tr.state, resolver, maxAttempts, tick);
      }
      // 检查是否有选中状态变化
      const selectionChanged = tr.effects.some((e) => e.is(setSelectedImageEffect));
      if (tr.docChanged || tr.selection || selectionChanged) {
        // 文档变化、选区变化或选中状态变化时重建装饰
        return buildDecorations(tr.state, resolver, maxAttempts, tick);
      }
      return deco;  // 无变化时返回原装饰
    },
    provide: (f) => EditorView.decorations.from(f),
  });

  // ESC 键清除当前激活的图片选中状态
  const escapeKeymap = keymap.of([
    {
      key: 'Escape',
      run: (view) => {
        // 如果没有选中图片，返回 false（不处理）
        if (view.state.field(selectedImageField, false) === null) return false;
        // 分发清除选中状态的效果
        view.dispatch({ effects: setSelectedImageEffect.of(null) });
        return true;  // 表示已处理
      },
    },
  ]);

  return [blockImageTheme, selectedImageField, decorationField, escapeKeymap];
}
