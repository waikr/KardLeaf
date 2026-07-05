import type { Extension } from '@codemirror/state';
import { makeInlineReplaceExtension } from './makeInlineReplaceExtension';
import { replaceCheckboxes } from './replaceCheckboxes';
import { replaceBulletLists } from './replaceBulletLists';
import { replaceDividers } from './replaceDividers';
import { replaceFormatCharacters } from './replaceFormatCharacters';
import { replaceBackslashEscapes } from './replaceBackslashEscapes';
import { inlineHtmlTheme, replaceInlineHtml, styleInlineHtmlContent } from './replaceInlineHtml';
import { addFormattingClasses, formattingClassesTheme } from './addFormattingClasses';
import { mathTheme, replaceMathFormulas } from './replaceMathFormulas';
import { inlineRenderingTheme } from './inlineRenderingTheme';

/**
 * 内联渲染扩展模块
 * 
 * **功能概述：**
 * 实现 Markdown 实时预览（Live Preview）的核心功能。
 * 将 Markdown 语法标记（如 `**bold**`、`*italic*`）替换为格式化的 widget，
 * 同时允许用户通过选区查看和编辑原始源码。
 * 
 * **支持的元素：**
 * - 复选框（任务列表）
 * - 无序列表标记
 * - 水平分割线
 * - 格式化字符（加粗、斜体、删除线、高亮等）
 * - 转义字符
 * - 内联 HTML
 * - 数学公式（可选）
 * 
 * **架构设计：**
 * 采用插件化设计，每个元素类型都是一个独立的 ReplacementExtension，
 * 通过 makeInlineReplaceExtension 统一管理和渲染。
 */

/** 内联渲染选项 */
export interface InlineRenderingOptions {
  /** 是否启用数学公式渲染 */
  mathRendering?: boolean;
}

/**
 * 创建内联渲染扩展
 * 
 * 组装所有内联替换规则，并应用相关主题。
 * 
 * @param options - 配置选项
 * @returns CodeMirror 扩展集合
 */
export function createInlineRenderingExtension(
  options: InlineRenderingOptions = {},
): Extension {
  // 定义所有替换规格
  const specs = [
    replaceCheckboxes,        // 复选框
    replaceBulletLists,       // 无序列表
    replaceDividers,          // 水平分割线
    replaceFormatCharacters,  // 格式化字符（加粗、斜体等）
    replaceBackslashEscapes,  // 转义字符
    replaceInlineHtml,        // 内联 HTML
    styleInlineHtmlContent,   // HTML 内容样式
    addFormattingClasses,     // 添加格式化类名
    ...(options.mathRendering ? [replaceMathFormulas] : []),  // 数学公式（条件）
  ];

  // 返回完整的扩展集合（包括主题和核心插件）
  return [
    inlineRenderingTheme,     // 内联渲染基础主题
    inlineHtmlTheme,          // HTML 主题
    formattingClassesTheme,   // 格式化类名主题
    ...(options.mathRendering ? [mathTheme] : []),  // 数学公式主题（条件）
    makeInlineReplaceExtension(specs),  // 核心替换插件
  ];
}

export { makeInlineReplaceExtension } from './makeInlineReplaceExtension';
/** 导出类型定义，供外部扩展使用 */
export type { InlineRenderingSpec, ReplacementExtension, RevealStrategy } from './types';
