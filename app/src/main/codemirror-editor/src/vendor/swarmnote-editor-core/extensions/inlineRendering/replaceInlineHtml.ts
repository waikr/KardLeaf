/**
 * 内联 HTML 渲染扩展
 *
 * **功能：**
 * 隐藏简单 HTML 标签（`<mark>`, `<kbd>`, `<sup>`, `<sub>`），
 * 用 Decoration.mark 给内容应用对应样式。
 * 
 * **支持的标签：**
 * - `<mark>`：高亮文本（黄色背景）
 * - `<kbd>`：键盘按键（灰色背景、边框、等宽字体）
 * - `<sup>`：上标（较小字体、上对齐）
 * - `<sub>`：下标（较小字体、下对齐）
 * 
 * **实现策略：**
 * 1. replaceInlineHtml：隐藏开标签和闭标签
 * 2. styleInlineHtmlContent：对标签间的内容应用样式
 */
import type { EditorState } from '@codemirror/state';
import { Decoration, EditorView } from '@codemirror/view';
import type { SyntaxNodeRef } from '@lezer/common';
import type { InlineRenderingSpec, RevealStrategy } from './types';

/** 隐藏装饰（用于隐藏 HTML 标签） */
const hiddenDecoration = Decoration.replace({});

/**
 * 支持的 HTML 标签映射表
 * 
 * **键：** 标签名（小写）
 * **值：** 包含 class 属性的配置对象
 */
const supportedTags: Record<
  string,
  { attributes: Record<string, string> }
> = {
  mark: {
    attributes: { class: 'cm-html-mark' },
  },
  kbd: {
    attributes: { class: 'cm-html-kbd' },
  },
  sup: {
    attributes: { class: 'cm-html-sup' },
  },
  sub: {
    attributes: { class: 'cm-html-sub' },
  },
};

/**
 * 解析 HTML 标签
 * 
 * **匹配规则：**
 * - 开标签：`<tag>` 或 `<tag />`
 * - 闭标签：`</tag>`
 * 
 * @param text - 标签文本
 * @returns [标签名, 是否为闭标签] 或 null（不支持的标签）
 */
function parseHtmlTag(
  text: string,
): [string, boolean] | null {
  // 正则匹配：<tag>、</tag>、<tag />
  const match = text.match(/^<(\/?)([a-zA-Z]+)\s*\/?>$/);
  if (!match) return null;
  // 提取标签名并转为小写
  const tagName = match[2].toLowerCase();
  // 检查是否为支持的标签
  if (!(tagName in supportedTags)) return null;
  return [tagName, match[1] === '/'];
}

/**
 * 内联 HTML 主题样式
 * 
 * **包含的样式：**
 * - .cm-html-mark：高亮背景（黄色半透明）
 * - .cm-html-kbd：键盘按键样式（灰色背景、边框、等宽字体）
 * - .cm-html-sup：上标（较小字体、上对齐）
 * - .cm-html-sub：下标（较小字体、下对齐）
 */
export const inlineHtmlTheme = EditorView.theme({
  '.cm-html-mark': {
    backgroundColor: 'rgba(255, 214, 10, 0.35)',
    borderRadius: '2px',
    padding: '0 2px',
  },
  '.cm-html-kbd': {
    backgroundColor: 'rgba(127, 127, 127, 0.15)',
    border: '1px solid rgba(127, 127, 127, 0.3)',
    borderRadius: '3px',
    padding: '1px 4px',
    fontFamily: 'monospace',
    fontSize: '0.9em',
  },
  '.cm-html-sup': {
    verticalAlign: 'super',
    fontSize: '0.75em',
  },
  '.cm-html-sub': {
    verticalAlign: 'sub',
    fontSize: '0.75em',
  },
});

/**
 * 内联 HTML 替换规格 —— 处理 HTMLTag 节点
 *
 * **策略：**
 * 隐藏开标签和闭标签，内容样式由 styleInlineHtmlContent 单独处理。
 */
export const replaceInlineHtml: InlineRenderingSpec = {
  nodeNames: ['HTMLTag'],  // HTML 标签节点
  extension: {
    /**
     * 创建装饰
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns 隐藏装饰或 null
     */
    createDecoration(node: SyntaxNodeRef, state: EditorState) {
      // 提取标签文本并解析
      const text = state.sliceDoc(node.from, node.to);
      const parsed = parseHtmlTag(text);
      if (!parsed) return null;  // 不支持的标签，跳过
      return hiddenDecoration;  // 隐藏标签
    },
    /**
     * 获取显示策略
     * 
     * @returns 'active' — 仅光标在标签上时显示源码
     */
    getRevealStrategy(): RevealStrategy {
      return 'active';
    },
  },
};

/**
 * 查找匹配的闭标签
 * 
 * **工作原理：**
 * 从给定节点开始，向后遍历兄弟节点，查找匹配的闭标签。
 * 
 * @param node - 开标签节点
 * @param tagName - 标签名
 * @param state - 编辑器状态
 * @returns 闭标签的位置信息或 null
 */
function findClosingTag(
  node: SyntaxNodeRef,
  tagName: string,
  state: EditorState,
): { from: number; to: number } | null {
  // 遍历后续兄弟节点
  let sibling = node.node.nextSibling;
  while (sibling) {
    // 检查是否为 HTMLTag 节点
    if (sibling.name === 'HTMLTag') {
      const sibText = state.sliceDoc(sibling.from, sibling.to);
      const sibParsed = parseHtmlTag(sibText);
      // 找到匹配的闭标签（同名且为闭标签）
      if (sibParsed && sibParsed[0] === tagName && sibParsed[1]) {
        return { from: sibling.from, to: sibling.to };
      }
    }
    sibling = sibling.nextSibling;
  }
  return null;  // 未找到匹配的闭标签
}

/**
 * 内容样式规格 —— 对开标签后到闭标签前的内容应用样式
 * 
 * **工作流程：**
 * 1. 解析开标签，获取标签名
 * 2. 查找匹配的闭标签
 * 3. 对中间的内容范围应用 Decoration.mark
 */
export const styleInlineHtmlContent: InlineRenderingSpec = {
  nodeNames: ['HTMLTag'],  // HTML 标签节点
  extension: {
    /**
     * 创建装饰
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns mark 装饰或 null
     */
    createDecoration(node: SyntaxNodeRef, state: EditorState) {
      // 提取标签文本并解析
      const text = state.sliceDoc(node.from, node.to);
      const parsed = parseHtmlTag(text);
      if (!parsed) return null;  // 不支持的标签

      const [tagName, isClosing] = parsed;
      if (isClosing) return null;  // 跳过闭标签

      // 查找匹配的闭标签
      const closer = findClosingTag(node, tagName, state);
      if (!closer || node.to >= closer.from) return null;  // 无效范围

      // 返回 mark 装饰，应用对应标签的样式
      return Decoration.mark(supportedTags[tagName]);
    },
    /**
     * 获取装饰范围
     * 
     * **范围定义：**
     * 从开标签结束位置到闭标签开始位置（不包括标签本身）
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns [from, to] 范围或 null
     */
    getDecorationRange(node: SyntaxNodeRef, state: EditorState) {
      // 提取标签文本并解析
      const text = state.sliceDoc(node.from, node.to);
      const parsed = parseHtmlTag(text);
      if (!parsed) return null;

      const [tagName, isClosing] = parsed;
      if (isClosing) return null;  // 跳过闭标签

      // 查找匹配的闭标签
      const closer = findClosingTag(node, tagName, state);
      if (!closer) return null;  // 未找到闭标签

      // 返回内容范围（开标签后到闭标签前）
      return [node.to, closer.from];
    },
    /**
     * 获取显示策略
     * 
     * @returns 'active' — 仅光标在内容上时显示源码
     */
    getRevealStrategy(): RevealStrategy {
      return 'active';
    },
    /** 当选区包含时隐藏装饰 */
    hideWhenContainsSelection: true,
  },
};
