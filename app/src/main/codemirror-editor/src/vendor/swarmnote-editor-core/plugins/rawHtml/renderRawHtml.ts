/**
 * 原生 HTML 渲染扩展 - Obsidian/Typora 风格
 *
 * **功能：**
 * 将 Markdown 中的原生 HTML（lezer `HTMLBlock` 和自闭合 `HTMLTag`）渲染为实际 DOM。
 * 
 * **安全机制：**
 * 所有 HTML 都先经过 DOMPurify 清洗，剥离危险内容：
 * - `<script>` 标签
 * - `on*` 事件处理器
 * - `javascript:` URL
 * - 其他危险属性和标签
 * 
 * **渲染行为：**
 * - **HTMLBlock**：渲染为 block widget，用 Decoration.replace 替换整段
 * - **自闭合 HTMLTag**（`<img/>`, `<br/>`, `<hr/>` 等 void 元素）：渲染为 inline widget
 * - **配对 HTMLTag**（`<span>...</span>`）：暂不接管，留给现有的 inlineRendering 系统
 * - **光标进入节点范围**：跳过渲染，源码可见可编辑
 *
 * `<img>` 与 `<source>` 的相对路径 `src` / `srcset` 会通过同一个 imageResolver
 * 异步解析（与 `![]()` 保持一致），保证 workspace-relative 路径在 Tauri 下能加载。
 */
import { ensureSyntaxTree } from '@codemirror/language';
import {
  type EditorState,
  RangeSetBuilder,
  StateEffect,
  StateField,
  type Extension,
} from '@codemirror/state';
import { Decoration, type DecorationSet, EditorView, WidgetType } from '@codemirror/view';
import DOMPurify, { type Config as DOMPurifyConfig } from 'dompurify';
import type { SyntaxNode } from '@lezer/common';
import { editorEventCallback, EditorEventType } from '../../events';
import type { ImageResolver } from '../blockImage/renderBlockImages';

/** CSS 类名前缀 */
const rawHtmlClassName = 'cm-md-html';

/** 原生 HTML 选项 */
export interface RawHtmlOptions {
  /** 图片 URL 解析器 */
  resolver?: ImageResolver;
}

/**
 * 强制重建 HTML Widget 的 Effect
 * 
 * **使用场景：**
 * 例如媒体同步后带来新的资源字节时，需要重新渲染 HTML。
 * 镜像 `refreshBlockImagesEffect` —— 不同的 tick 值使 widget 的 `eq` 返回 false，
 * 从而触发 `toDOM` 重新执行。
 */
export const refreshRawHtmlEffect = StateEffect.define<null>();

/** HTML void（自闭合）元素集合 —— 渲染为 inline widget */
const VOID_ELEMENTS = new Set([
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr',
]);

/**
 * DOMPurify 配置
 * 
 * **安全策略：**
 * - 允许 HTML、SVG 和 SVG Filters
 * - 禁止危险标签：script、iframe、form、input、button、object、embed
 * - 禁止事件处理器属性：onclick、onload、onerror 等
 * - 禁止高风险属性：formaction、srcdoc
 * - 不允许 data URI 属性
 */
const purifyConfig: DOMPurifyConfig = {
  USE_PROFILES: { html: true, svg: true, svgFilters: true },
  // 禁止某些在某些配置下 DOMPurify 会保留的危险标签
  FORBID_TAGS: ['script', 'iframe', 'form', 'input', 'button', 'object', 'embed'],
  // 丢弃事件处理器和高风险属性
  FORBID_ATTR: [
    'formaction',
    'srcdoc',
    'onclick', 'onload', 'onerror', 'onmouseover', 'onmouseout',
    'onkeydown', 'onkeyup', 'onfocus', 'onblur', 'onchange',
    'onsubmit', 'oninput', 'onauxclick', 'oncontextmenu',
  ],
  ALLOW_DATA_ATTR: false,
};

/**
 * 清洗 HTML 字符串
 * 
 * @param html - 原始 HTML
 * @returns 清洗后的安全 HTML
 */
function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html, purifyConfig) as unknown as string;
}

/**
 * 遍历渲染的子树并异步解析 `<img>` 和 `<source>` 元素的 `src` / `srcset`
 * 
 * **工作流程：**
 * 1. 查找所有 `<img>` 元素，解析其 `src` 属性
 * 2. 查找所有 `<source>` 元素，解析其 `srcset` 属性
 * 3. 跳过已使用已知协议的 URL（http/https/data/blob/asset/tauri/file）
 * 4. 通过提供的 resolver 异步解析相对路径
 * 5. 检查元素是否仍然连接到 DOM，然后设置解析后的 URL
 * 
 * @param root - 根节点
 * @param resolver - 图片 URL 解析器
 */
function resolveMediaSources(root: ParentNode, resolver: ImageResolver): void {
  // 处理 <img> 元素
  const imgs = root.querySelectorAll('img');
  imgs.forEach(async (img) => {
    const src = img.getAttribute('src');
    // 跳过空值或已有协议的 URL
    if (!src || /^(https?|data|blob|asset|tauri|file):/i.test(src)) return;
    try {
      const resolved = await resolver(src);
      // 检查元素是否仍在 DOM 中
      if (img.isConnected) img.setAttribute('src', resolved);
    } catch {
      // 解析失败，保留原始值
    }
  });
  
  // 处理 <source> 元素
  const sources = root.querySelectorAll('source');
  sources.forEach(async (source) => {
    const srcset = source.getAttribute('srcset');
    // 跳过空值或已有协议的 URL
    if (!srcset || /^(https?|data|blob|asset|tauri|file):/i.test(srcset)) return;
    try {
      const resolved = await resolver(srcset);
      // 检查元素是否仍在 DOM 中
      if (source.isConnected) source.setAttribute('srcset', resolved);
    } catch {
      // 解析失败，保留原始值
    }
  });
}


/**
 * 拦截 Widget 内 `<a>` 标签的点击事件
 * 
 * **设计原因：**
 * - 普通点击被吞掉，防止意外点击导致整个 Tauri webview 导航离开
 * - Ctrl/Cmd + 点击会派发 `LinkOpen` 事件（与 `ctrlClickLinksExtension` 相同的通道）
 * - 宿主应用通过 plugin-opener 打开链接
 * 
 * @param root - 根元素
 * @param view - 编辑器视图
 */
function attachLinkInterceptor(root: HTMLElement, view: EditorView): void {
  root.addEventListener('click', (e) => {
    // 查找最近的 <a> 标签
    const target = (e.target as HTMLElement | null)?.closest?.('a') as
      | HTMLAnchorElement
      | null;
    if (!target) return;
    
    e.preventDefault();  // 阻止默认导航行为
    if (!(e.ctrlKey || e.metaKey)) return;  // 仅响应 Ctrl/Cmd + 点击
    
    const href = target.getAttribute('href');
    if (!href) return;
    
    // 通过 Facet 派发 LinkOpen 事件
    const callback = view.state.facet(editorEventCallback);
    callback?.({ kind: EditorEventType.LinkOpen, url: href });
  });
}


/**
 * HTML Widget - 同时支持块级和内联原生 HTML 渲染
 * 
 * **设计说明：**
 * 单个 Widget 类同时处理两种情况：
 * - 块级（HTMLBlock → `<div>`）
 * - 自闭合内联（HTMLTag void 元素 → `<span>`）
 * 
 * 两者仅在标签名和类名后缀上有所不同；合并为一个类避免了创建
 * `BaseHtmlWidget` 基类和两个几乎相同的子类的样板代码。
 */
class HtmlWidget extends WidgetType {
  constructor(
    /** 原始 HTML 字符串 */
    private readonly html: string,
    /** 图片 URL 解析器 */
    private readonly resolver: ImageResolver | undefined,
    /** 刷新代次 */
    private readonly tick: number,
    /** 类型：'block' 或 'inline' */
    private readonly kind: 'block' | 'inline',
  ) {
    super();
  }

  /**
   * 判断两个 widget 是否相等
   * 
   * @param other - 另一个 HtmlWidget
   * @returns 如果所有属性都相同则返回 true
   */
  eq(other: HtmlWidget) {
    return (
      this.html === other.html &&
      this.resolver === other.resolver &&
      this.tick === other.tick &&
      this.kind === other.kind
    );
  }

  /**
   * 创建 DOM 结构
   * 
   * **工作流程：**
   * 1. 根据 kind 创建 div（块级）或 span（内联）
   * 2. 设置 CSS 类名
   * 3. 清洗 HTML 并注入到 innerHTML
   * 4. 异步解析媒体资源的相对路径
   * 5. 附加链接拦截器
   * 
   * @param view - 编辑器视图
   * @returns 根元素
   */
  toDOM(view: EditorView) {
    const isBlock = this.kind === 'block';
    // 创建根元素：块级用 div，内联用 span
    const root = document.createElement(isBlock ? 'div' : 'span');
    root.className = `${rawHtmlClassName} ${rawHtmlClassName}-${this.kind}`;
    
    // 清洗 HTML 并注入
    root.innerHTML = sanitizeHtml(this.html);
    
    // 异步解析媒体资源路径
    if (this.resolver) resolveMediaSources(root, this.resolver);
    
    // 附加链接拦截器
    attachLinkInterceptor(root, view);
    
    return root;
  }

  /**
   * 是否忽略事件
   * 
   * 返回 false 表示让所有事件通过，允许用户与渲染的 HTML 交互。
   * 
   * @returns false（不忽略任何事件）
   */
  ignoreEvent() {
    return false;
  }
}


const rawHtmlTheme = EditorView.theme({
  [`& .${rawHtmlClassName}-block`]: {
    padding: '4px 0',
  },
  [`& .${rawHtmlClassName}-block img, & .${rawHtmlClassName}-block video`]: {
    maxWidth: '100%',
  },
  [`& .${rawHtmlClassName}-block figure`]: {
    margin: '0',
    textAlign: 'center',
  },
  [`& .${rawHtmlClassName}-block figcaption`]: {
    fontSize: '0.875em',
    color: 'rgba(127, 127, 127, 0.85)',
    marginTop: '4px',
  },
  // The wrapper itself stays plain `display: inline` so that flow-affecting
  // void elements like `<br>` propagate their line-break to the outer line.
  // (display: inline-block would trap the break inside the wrapper.)
  [`& .${rawHtmlClassName}-inline`]: {
    display: 'inline',
  },
  [`& .${rawHtmlClassName}-inline img`]: {
    maxHeight: 'var(--cm-image-inline-max-height, 320px)',
    maxWidth: '100%',
    verticalAlign: 'middle',
  },
});


/** 解析后的 HTML 标签信息 */
interface ParsedTag {
  /** 标签名（小写） */
  name: string;
  /** 是否为闭合标签（`</tag>`） */
  isClosing: boolean;
  /** 是否为 XHTML 风格的自闭合（`<br/>`, `<img />`） */
  isXhtmlSelfClose: boolean;
}

/**
 * 解析 lezer `HTMLTag` 节点的文本为标签信息
 * 
 * **特点：**
 * 使用宽松的正则表达式 —— 接受带属性的标签（包括引号包裹的值等），
 * 与 `replaceInlineHtml.ts` 中简单的 `<name>` 匹配器不同。
 * 
 * @param text - 标签文本
 * @returns 解析结果或 null
 */
function parseHtmlTag(text: string): ParsedTag | null {
  const trimmed = text.trim();
  const m = trimmed.match(/^<(\/?)\s*([a-zA-Z][a-zA-Z0-9-]*)/);
  if (!m) return null;
  return {
    name: m[2].toLowerCase(),  // 标签名转为小写
    isClosing: m[1] === '/',   // 是否为闭合标签
    isXhtmlSelfClose: m[1] === '' && /\/\s*>$/.test(trimmed),  // 是否为 XHTML 自闭合
  };
}

/**
 * 判断是否为自闭合 void 标签
 * 
 * **判断条件：**
 * - 不是闭合标签（`</tag>`）
 * - 是 XHTML 风格的自闭合（`<br/>`）或者是 HTML void 元素（`<br>`）
 * 
 * @param tag - 解析后的标签信息
 * @returns 是否为自闭合 void 标签
 */
function isSelfClosingVoidTag(tag: ParsedTag): boolean {
  if (tag.isClosing) return false;
  return tag.isXhtmlSelfClose || VOID_ELEMENTS.has(tag.name);
}

/**
 * 由现有的基于类的系统处理的配对标签
 * 
 * **说明：**
 * 这些标签在 `inlineRendering/replaceInlineHtml.ts` 中处理。
 * 我们在这里不能再次拦截它们，否则会导致双重渲染。
 */
const PAIRED_TAGS_HANDLED_ELSEWHERE = new Set(['mark', 'kbd', 'sup', 'sub']);

/**
 * 向前遍历兄弟节点，找到开标签的匹配闭标签
 * 
 * **工作原理：**
 * 使用深度计数器处理嵌套的同名标签对。
 * 例如：`<div><div></div></div>` 会正确匹配外层的 div。
 * 
 * @param openNode - 开标签节点
 * @param tagName - 标签名
 * @param state - 编辑器状态
 * @returns 匹配的闭标签节点，未找到返回 null
 */
function findMatchingClose(
  openNode: SyntaxNode,
  tagName: string,
  state: EditorState,
): SyntaxNode | null {
  let depth = 1;  // 深度计数器，初始为 1（当前开标签）
  let cur: SyntaxNode | null = openNode.nextSibling;
  
  while (cur) {
    if (cur.name === 'HTMLTag') {
      const parsed = parseHtmlTag(state.sliceDoc(cur.from, cur.to));
      if (parsed && parsed.name === tagName) {
        if (parsed.isClosing) {
          // 遇到闭标签，深度减 1
          depth -= 1;
          if (depth === 0) return cur;  // 找到匹配的闭标签
        } else if (!isSelfClosingVoidTag(parsed)) {
          // 遇到新的开标签（非自闭合），深度加 1
          depth += 1;
        }
      }
    }
    cur = cur.nextSibling;
  }
  
  return null;  // 未找到匹配的闭标签
}


/** 装饰条目接口 */
interface DecorationEntry {
  /** 起始位置 */
  from: number;
  /** 结束位置 */
  to: number;
  /** 装饰对象 */
  decoration: Decoration;
}

/**
 * 构建 HTML 装饰集
 * 
 * **工作流程：**
 * 1. 遍历语法树，找到所有 HTMLBlock 和 HTMLTag 节点
 * 2. 检查光标是否在节点范围内（是则跳过渲染，显示源码）
 * 3. 对于 HTMLBlock：创建块级 Widget，替换整段
 * 4. 对于 HTMLTag：
 *    - 自闭合 void 标签：创建内联 Widget
 *    - 配对标签：找到匹配的闭标签，创建内联 Widget
 *    - 跳过已由其他系统处理的标签（避免双重渲染）
 * 5. 按位置排序并构建最终的装饰集
 * 
 * @param state - 编辑器状态
 * @param resolver - 图片 URL 解析器
 * @param tick - 刷新代次
 * @returns 装饰集合
 */
function buildDecorations(
  state: EditorState,
  resolver: ImageResolver | undefined,
  tick: number,
): DecorationSet {
  const entries: DecorationEntry[] = [];
  const head = state.selection.main.head;  // 光标位置
  
  // 确保语法树已构建（最多等待 500ms）
  const tree = ensureSyntaxTree(state, state.doc.length, 500);
  if (!tree) return Decoration.none;

  // 遍历语法树
  tree.iterate({
    enter(node) {
      // 处理 HTMLBlock（块级 HTML）
      if (node.name === 'HTMLBlock') {
        const lineFrom = state.doc.lineAt(node.from);
        const lineTo = state.doc.lineAt(node.to);
        const blockFrom = lineFrom.from;
        const blockTo = lineTo.to;
        
        // 如果光标在块的任意行上，显示源码（跳过渲染）
        if (head >= blockFrom && head <= blockTo) return;

        const html = state.sliceDoc(node.from, node.to);
        entries.push({
          from: blockFrom,
          to: blockTo,
          decoration: Decoration.replace({
            widget: new HtmlWidget(html, resolver, tick, 'block'),
            block: true,
          }),
        });
        return;
      }
      
      // 处理 HTMLTag（内联 HTML 标签）
      if (node.name === 'HTMLTag') {
        const text = state.sliceDoc(node.from, node.to);
        const parsed = parseHtmlTag(text);
        if (!parsed) return;

        // 自闭合 void 标签（如 <br>, <img/>）
        if (isSelfClosingVoidTag(parsed)) {
          // 如果光标在标签内，显示源码
          if (head >= node.from && head <= node.to) return;
          entries.push({
            from: node.from,
            to: node.to,
            decoration: Decoration.replace({
              widget: new HtmlWidget(text, resolver, tick, 'inline'),
            }),
          });
          return;
        }

        // 跳过闭合标签（由开标签处理）
        if (parsed.isClosing) return;

        // 跳过已由其他系统处理的配对标签，避免双重渲染
        if (PAIRED_TAGS_HANDLED_ELSEWHERE.has(parsed.name)) return;

        // 查找匹配的闭标签
        const closeNode = findMatchingClose(node.node, parsed.name, state);
        if (!closeNode) return;  // 未找到匹配的闭标签，跳过
        
        // 如果光标在标签对范围内，显示源码
        if (head >= node.from && head <= closeNode.to) return;

        // 提取完整的 HTML（包括开标签、内容和闭标签）
        const fullHtml = state.sliceDoc(node.from, closeNode.to);
        entries.push({
          from: node.from,
          to: closeNode.to,
          decoration: Decoration.replace({
            widget: new HtmlWidget(fullHtml, resolver, tick, 'inline'),
          }),
        });
      }
    },
  });

  // 按起始位置排序
  entries.sort((a, b) => a.from - b.from);
  
  // 构建最终的装饰集
  const builder = new RangeSetBuilder<Decoration>();
  for (const e of entries) {
    builder.add(e.from, e.to, e.decoration);
  }
  return builder.finish();
}


/**
 * 创建原生 HTML 渲染扩展
 * 
 * **功能：**
 * 将 Markdown 中的原生 HTML 渲染为实际 DOM，支持块级和内联两种模式。
 * 
 * **核心组件：**
 * 1. `field` - StateField，管理 HTML 装饰的生命周期
 * 2. `rawHtmlTheme` - HTML 相关的 CSS 主题
 * 
 * **工作流程：**
 * 1. 语法树遍历找到所有 HTMLBlock 和 HTMLTag 节点
 * 2. 根据光标位置决定是否显示源码
 * 3. 创建对应的 HtmlWidget（block 或 inline）
 * 4. 通过 Decoration.replace 添加到视图
 * 
 * @param options - 配置选项
 * @returns CodeMirror 扩展数组
 */
export function createRawHtmlExtension(options: RawHtmlOptions = {}): Extension {
  const resolver = options.resolver;  // 图片 URL 解析器
  let tick = 0;  // 刷新代次计数器

  const field = StateField.define<DecorationSet>({
    create(state) {
      // 初始化时构建装饰
      return buildDecorations(state, resolver, tick);
    },
    update(deco, tr) {
      // 检查是否有刷新效果
      const hasRefresh = tr.effects.some((e) => e.is(refreshRawHtmlEffect));
      if (hasRefresh) {
        tick += 1;  // 递增刷新计数器以强制重建
        return buildDecorations(tr.state, resolver, tick);
      }
      // 文档变化或选区变化时重建装饰
      if (tr.docChanged || tr.selection) {
        return buildDecorations(tr.state, resolver, tick);
      }
      return deco;  // 无变化时返回原装饰
    },
    provide: (f) => EditorView.decorations.from(f),
  });

  return [rawHtmlTheme, field];
}
