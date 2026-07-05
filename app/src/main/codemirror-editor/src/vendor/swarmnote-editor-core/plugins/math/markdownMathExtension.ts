/**
 * Markdown 数学公式扩展 - LaTeX 解析和高亮
 *
 * **功能：**
 * Lezer 语法扩展，识别两种数学公式：
 * - **内联公式**：`$...$`（单行）
 * - **块级公式**：`$$...$$`（可跨多行）
 * 
 * **特性：**
 * - 生成 InlineMath / BlockMath 语法节点，供渲染层使用
 * - 使用 @codemirror/legacy-modes/mode/stex 对公式内容做 LaTeX 语法高亮
 * - 支持转义字符（\$）
 * - 禁止空格紧邻分隔符（`$ x$` 不匹配）
 *
 * 基于 Joplin markdownMathExtension.ts。
 */
import { Tag, tags } from '@lezer/highlight';
import {
  parseMixed,
  type SyntaxNodeRef,
  type Input,
  type NestedParse,
  type ParseWrapper,
} from '@lezer/common';
import type {
  MarkdownConfig,
  InlineContext,
  BlockContext,
  Line,
  LeafBlock,
  MarkdownExtension,
} from '@lezer/markdown';
import { StreamLanguage } from '@codemirror/language';
import { stexMath } from '@codemirror/legacy-modes/mode/stex';

/** ASCII 码：'$' 的字符码 */
const dollarSignCharcode = 36;
/** ASCII 码：'\\' 的字符码 */
const backslashCharcode = 92;

/** 块级公式开始正则：匹配 `$$`（允许前导空格和引用符号 `>`） */
const mathBlockStartRegex = /^(?:\s*[>]\s*)?\$\$/;
/** 块级公式结束正则：匹配行尾的 `$$` */
const mathBlockEndRegex = /\$\$\s*$/;

/** TeX/LaTeX 语言定义（用于嵌套解析） */
const texLanguage = StreamLanguage.define(stexMath);

/** 内联数学公式节点名称 */
export const inlineMathTagName = 'InlineMath';
/** 内联数学公式内容节点名称 */
export const inlineMathContentTagName = 'InlineMathContent';
/** 块级数学公式节点名称 */
export const blockMathTagName = 'BlockMath';
/** 块级数学公式内容节点名称 */
export const blockMathContentTagName = 'BlockMathContent';
/** 数学公式标签定义 —— 归类为 monospace 标签 */
export const mathTag = Tag.define(tags.monospace);

/**
 * 创建 TeX 嵌套解析器包装器
 * 
 * **工作原理：**
 * 当 Lezer 遇到指定标签的节点时，使用 TeX 解析器对其进行二次解析，
 * 从而实现 LaTeX 内容的语法高亮。
 * 
 * @param nodeTag - 要包裹的节点标签名
 * @returns 解析器包装函数
 */
const wrappedTexParser = (nodeTag: string): ParseWrapper => {
  return parseMixed((node: SyntaxNodeRef, _input: Input): NestedParse | null => {
    if (node.name !== nodeTag) return null;
    return { parser: texLanguage.parser };
  });
};

/**
 * 内联数学公式配置（$...$）
 * 
 * **解析规则：**
 * 1. 当前字符必须是 `$`
 * 2. 前一个字符不能是 `$`（避免匹配 `$$`）
 * 3. 下一个字符不能是 `$`（避免匹配 `$$`）
 * 4. `$` 后不能紧跟空格
 * 5. 支持转义字符（\$）
 * 6. 闭合 `$` 前不能有空格
 */
const inlineMathConfig: MarkdownConfig = {
  // 定义节点类型
  defineNodes: [
    { name: inlineMathTagName, style: mathTag },
    { name: inlineMathContentTagName },
  ],
  // 内联解析规则
  parseInline: [
    {
      name: inlineMathTagName,
      after: 'InlineCode',  // 在 InlineCode 之后尝试匹配

      /**
       * 内联解析函数
       * 
       * @param cx - 内联解析上下文
       * @param current - 当前字符的 Unicode 码点
       * @param pos - 当前位置
       * @returns 消耗的字符数，-1 表示不匹配
       */
      parse(cx: InlineContext, current: number, pos: number): number {
        const nextCharCode = cx.char(pos + 1);
        
        // 验证：必须是单个 $（不是 $$）
        if (
          current !== dollarSignCharcode ||           // 当前字符不是 $
          cx.char(pos - 1) === dollarSignCharcode ||  // 前一个字符是 $
          nextCharCode === dollarSignCharcode         // 下一个字符是 $
        ) {
          return -1;  // 不匹配
        }

        // $ 后不能紧跟空格
        if (/\s/.test(String.fromCharCode(nextCharCode))) return -1;

        const start = pos;  // 记录开始位置
        const end = cx.end; // 结束位置
        let escaped = false; // 转义标志
        pos++;  // 跳过开头的 $

        // 查找闭合的 $（支持转义）
        for (; pos < end && (escaped || cx.char(pos) !== dollarSignCharcode); pos++) {
          escaped = !escaped && cx.char(pos) === backslashCharcode;
        }

        // 闭合 $ 前不能有空格
        if (/\s/.test(String.fromCharCode(cx.char(pos - 1)))) return -1;

        // 必须找到闭合的 $
        if (pos === end) return -1;

        pos++;  // 跳过闭合的 $
        
        // 创建内容元素（不包括 $ 分隔符）
        const contentElem = cx.elt(inlineMathContentTagName, start + 1, pos - 1);
        // 创建完整的 InlineMath 节点
        cx.addElement(cx.elt(inlineMathTagName, start, pos, [contentElem]));
        return pos;
      },
    },
  ],
  // 嵌套解析：对 InlineMathContent 应用 TeX 语法高亮
  wrap: wrappedTexParser(inlineMathContentTagName),
};

/**
 * 块级数学公式配置（$$...$$）
 * 
 * **解析规则：**
 * 1. 行首匹配 `$$`（允许前导空格和引用符号 `>`）
 * 2. 支持单行公式（`$$...$$` 在同一行）
 * 3. 支持多行公式（跨越多行，直到找到闭合的 `$$`）
 * 4. 容错处理：未找到闭合分隔符时，到文档末尾为止
 */
const blockMathConfig: MarkdownConfig = {
  // 定义节点类型
  defineNodes: [
    { name: blockMathTagName, style: mathTag },
    { name: blockMathContentTagName },
  ],
  // 块级解析规则
  parseBlock: [
    {
      name: blockMathTagName,
      before: 'Blockquote',  // 在 Blockquote 之前尝试匹配
      /**
       * 块级解析函数
       * 
       * @param cx - 块级解析上下文
       * @param line - 当前行
       * @returns 是否成功解析
       */
      parse(cx: BlockContext, line: Line): boolean {
        // 检查是否为块级公式开始
        const startMatch = mathBlockStartRegex.exec(line.text);
        if (!startMatch) return false;

        const delimLen = 2;  // 分隔符长度（$$）
        const start = cx.lineStart + startMatch[0].length;  // 内容起始位置

        // 检查是否在同一行结束（单行公式）
        const sameLineEnd = mathBlockEndRegex.exec(
          line.text.substring(startMatch[0].length),
        );

        let stop: number;  // 内容结束位置
        if (sameLineEnd) {
          // 单行公式：计算结束位置
          stop = cx.lineStart + line.text.length - sameLineEnd[0].length;
        } else {
          // 多行公式：查找闭合的 $$
          let hadNextLine = false;
          let endMatch: RegExpExecArray | null = null;
          do {
            hadNextLine = cx.nextLine();
            endMatch = hadNextLine ? mathBlockEndRegex.exec(line.text) : null;
          } while (hadNextLine && !endMatch);

          // 计算结束位置（如果未找到闭合，则到当前行末尾）
          stop =
            hadNextLine && endMatch
              ? cx.lineStart + line.text.length - endMatch[0].length
              : cx.lineStart;
        }

        const lineEnd = cx.lineStart + line.text.length;
        // 创建内容元素
        const contentElem = cx.elt(blockMathContentTagName, start, stop);
        // 创建完整的 BlockMath 节点（包括分隔符）
        cx.addElement(
          cx.elt(blockMathTagName, start - delimLen, Math.min(lineEnd, stop + delimLen), [
            contentElem,
          ]),
        );
        cx.nextLine();  // 跳过下一行
        return true;
      },
      /**
       * 叶子块结束判断
       * 
       * 当遇到新的 `$$` 开始时结束当前叶子块。
       * 
       * @param _cx - 块级解析上下文
       * @param line - 当前行
       * @param _leaf - 当前叶子块
       * @returns 是否应该结束
       */
      endLeaf(_cx: BlockContext, line: Line, _leaf: LeafBlock): boolean {
        return mathBlockStartRegex.test(line.text);
      },
    },
  ],
  // 嵌套解析：对 BlockMathContent 应用 TeX 语法高亮
  wrap: wrappedTexParser(blockMathContentTagName),
};

/**
 * Markdown 数学公式扩展导出
 * 
 * **用法：**
 * 将此扩展添加到 CodeMirror 的 Markdown 解析器配置中，
 * 即可支持内联（$...$）和块级（$$...$$）数学公式的识别和 LaTeX 语法高亮。
 */
export const markdownMathExtension: MarkdownExtension = [
  inlineMathConfig,
  blockMathConfig,
];
