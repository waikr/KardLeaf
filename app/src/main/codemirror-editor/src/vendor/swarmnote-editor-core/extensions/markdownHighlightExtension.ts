/**
 * Markdown 高亮扩展（==文本== 语法）
 * 
 * **功能：**
 * 解析和渲染 Markdown 中的高亮语法 `==高亮文本==`。
 * 
 * **语法规则：**
 * - 使用双等号 `==` 作为标记符
 * - 前后不能有空格或空内容
 * - 支持嵌套在其他内联格式中
 */
import { tags, Tag } from '@lezer/highlight';
import type { MarkdownConfig, InlineContext, MarkdownExtension } from '@lezer/markdown';

/** ASCII 码：'=' 的字符码 */
const equalsSignCharcode = 61;

/** 高亮节点名称 */
export const highlightTagName = 'Highlight';
/** 高亮标记符节点名称 */
export const highlightMarkerTagName = 'HighlightMarker';

/** 高亮标签定义 —— 用于语法树着色 */
export const highlightTag = Tag.define();
/** 高亮标记符标签定义 —— 归类为 meta 标签 */
export const highlightMarkerTag = Tag.define(tags.meta);

/** 高亮分隔符配置 */
const highlightDelimiter = {
  resolve: highlightTagName,    // 解析为 Highlight 节点
  mark: highlightMarkerTagName, // 标记符解析为 HighlightMarker 节点
};

/**
 * 检查文本是否为空白或空
 * 
 * @param text - 待检查的文本
 * @returns 如果只包含空白字符或为空则返回 true
 */
function isWhitespaceOrEmpty(text: string) {
  return /^\s*$/.test(text);
}

/**
 * 创建双字符内联配置（== 高亮语法）
 * 
 * **工作原理：**
 * 1. 定义两种节点类型：Highlight（高亮内容）和 HighlightMarker（标记符 ==）
 * 2. 注册内联解析器，检测连续的三个 '=' 字符
 * 3. 验证前后是否有非空白内容（避免匹配到无效的高亮）
 * 4. 将 '==' 注册为分隔符，可以开启或关闭高亮区域
 * 
 * @returns Lezer Markdown 配置对象
 */
function createDoubleCharInlineConfig(): MarkdownConfig {
  return {
    // 定义节点类型及其样式标签
    defineNodes: [
      {
        name: highlightTagName,
        style: highlightTag,
      },
      {
        name: highlightMarkerTagName,
        style: highlightMarkerTag,
      },
    ],
    // 注册内联解析器
    parseInline: [
      {
        name: highlightTagName,
        /**
         * 内联解析函数
         * 
         * @param cx - 解析上下文
         * @param current - 当前字符的 Unicode 码点
         * @param pos - 当前位置
         * @returns 消耗的字符数，-1 表示不匹配
         */
        parse(cx: InlineContext, current: number, pos: number): number {
          // 获取下一个和下下个字符的码点
          const nextCharCode = cx.char(pos + 1);
          const nextNextCharCode = cx.char(pos + 2);
          
          // 验证：必须是 '==' 且后面不是第三个 '='
          if (
            current !== equalsSignCharcode ||       // 当前字符不是 '='
            nextCharCode !== equalsSignCharcode ||  // 下一个字符不是 '='
            nextNextCharCode === equalsSignCharcode // 下下个字符是 '='（排除 '==='）
          ) {
            return -1;  // 不匹配
          }

          // 提取前后的字符
          const before = cx.slice(pos - 1, pos);  // 前一个字符
          const after = cx.slice(pos + 2, pos + 3);  // 后一个字符

          // 判断是否可以开始/结束高亮区域
          const canStart = !isWhitespaceOrEmpty(after);  // 后面有内容才能开始
          const canEnd = !isWhitespaceOrEmpty(before);   // 前面有内容才能结束

          // 如果既不能开始也不能结束，拒绝匹配
          if (!canStart && !canEnd) {
            return -1;
          }

          // 注册分隔符（占据 pos 到 pos+2 的范围）
          return cx.addDelimiter(highlightDelimiter, pos, pos + 2, canStart, canEnd);
        },
      },
    ],
  };
}

/**
 * Markdown 高亮扩展导出
 * 
 * **用法：**
 * 将此扩展添加到 CodeMirror 的 Markdown 解析器配置中，即可支持 `==高亮文本==` 语法。
 */
export const markdownHighlightExtension: MarkdownExtension = [
  createDoubleCharInlineConfig(),
];
