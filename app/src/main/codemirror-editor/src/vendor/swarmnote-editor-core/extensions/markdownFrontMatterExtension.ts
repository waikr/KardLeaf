/**
 * Markdown Front Matter 扩展 - YAML 元数据解析
 *
 * **功能：**
 * Lezer 语法扩展，识别文档开头的 YAML front matter：
 * ```yaml
 * ---
 * title: My Document
 * tags: [note, important]
 * ---
 * ```
 *
 * **特性：**
 * - 仅在文档开头（第 0 行）识别 `---` 分隔符
 * - 使用 @codemirror/legacy-modes/mode/yaml 对内容区域做 YAML 语法高亮
 * - 支持缺少闭合分隔符的情况（容错处理）
 * 
 * 参考 Joplin markdownFrontMatterExtension.ts。
 */
import { Tag } from '@lezer/highlight';
import { parseMixed, type SyntaxNodeRef, type Input, type NestedParse, type ParseWrapper } from '@lezer/common';
import type {
  MarkdownConfig,
  BlockContext,
  Line,
  LeafBlock,
  MarkdownExtension,
} from '@lezer/markdown';
import { StreamLanguage } from '@codemirror/language';
import { yaml } from '@codemirror/legacy-modes/mode/yaml';

/** Front Matter 节点名称 */
export const frontMatterTagName = 'FrontMatter';
/** Front Matter 内容节点名称 */
export const frontMatterContentTagName = 'FrontMatterContent';
/** Front Matter 标记符节点名称（--- 分隔符） */
export const frontMatterMarkerTagName = 'FrontMatterMarker';

/** Front Matter 标签定义 —— 用于语法树着色 */
export const frontMatterTag = Tag.define();

/** YAML 语言定义（用于嵌套解析） */
const yamlLanguage = StreamLanguage.define(yaml);

/**
 * 创建 YAML 嵌套解析器包装器
 * 
 * **工作原理：**
 * 当 Lezer 遇到指定标签的节点时，使用 YAML 解析器对其进行二次解析，
 * 从而实现 YAML 内容的语法高亮。
 * 
 * @param nodeTag - 要包裹的节点标签名
 * @returns 解析器包装函数
 */
const wrappedYamlParser = (nodeTag: string): ParseWrapper => {
  return parseMixed((node: SyntaxNodeRef, _input: Input): NestedParse | null => {
    if (node.name !== nodeTag) return null;
    return { parser: yamlLanguage.parser };
  });
};

/** Front Matter 分隔符正则表达式：匹配 `---`（允许尾部空格） */
const frontMatterDelimiterRegex = /^---\s*$/;

/**
 * Front Matter 配置对象
 * 
 * **包含：**
 * 1. defineNodes：定义三种节点类型（FrontMatter、FrontMatterContent、FrontMatterMarker）
 * 2. parseBlock：块级解析器，识别文档开头的 YAML front matter
 * 3. wrap：嵌套解析器包装，对 FrontMatterContent 应用 YAML 语法高亮
 */
const frontMatterConfig: MarkdownConfig = {
  // 定义节点类型
  defineNodes: [
    { name: frontMatterTagName, style: frontMatterTag },
    { name: frontMatterContentTagName },
    { name: frontMatterMarkerTagName, style: frontMatterTag },
  ],
  // 块级解析规则
  parseBlock: [
    {
      name: frontMatterTagName,
      before: 'HorizontalRule',  // 在 HorizontalRule 之前尝试匹配
      /**
       * 解析函数
       * 
       * @param cx - 块级解析上下文
       * @param line - 当前行
       * @returns 是否成功解析
       */
      parse(cx: BlockContext, line: Line): boolean {
        // 仅在文档开头（第 0 行）识别
        if (cx.lineStart !== 0) return false;
        // 检查是否为 `---` 分隔符
        if (!frontMatterDelimiterRegex.test(line.text)) return false;

        // 记录开标记的位置
        const openingMarkerStart = cx.lineStart;
        const openingMarkerEnd = cx.lineStart + line.text.length;
        const contentStart = openingMarkerEnd + 1;  // 内容从下一行开始

        // 查找闭合分隔符
        let foundEnd = false;
        while (cx.nextLine()) {
          if (frontMatterDelimiterRegex.test(line.text)) {
            foundEnd = true;
            break;
          }
        }

        const contentEnd = cx.lineStart;  // 内容结束位置
        // 创建开标记元素
        const openingMarkerElem = cx.elt(frontMatterMarkerTagName, openingMarkerStart, openingMarkerEnd);
        // 创建内容元素
        const contentElem = cx.elt(frontMatterContentTagName, contentStart, contentEnd);

        if (foundEnd) {
          // 找到闭合分隔符：创建完整的 Front Matter 节点（开标记 + 内容 + 闭标记）
          const closingMarkerEnd = cx.lineStart + line.text.length;
          const closingMarkerElem = cx.elt(frontMatterMarkerTagName, cx.lineStart, closingMarkerEnd);
          cx.addElement(
            cx.elt(frontMatterTagName, 0, closingMarkerEnd, [
              openingMarkerElem,
              contentElem,
              closingMarkerElem,
            ]),
          );
          cx.nextLine();  // 跳过闭标记行
        } else {
          // 未找到闭合分隔符：容错处理，只创建开标记 + 内容
          cx.addElement(
            cx.elt(frontMatterTagName, 0, contentEnd, [openingMarkerElem, contentElem]),
          );
        }

        return true;
      },
      /**
       * 叶子块结束判断
       * 
       * 当遇到 `---` 时结束当前叶子块。
       * 
       * @param _cx - 块级解析上下文
       * @param line - 当前行
       * @param _leaf - 当前叶子块
       * @returns 是否应该结束
       */
      endLeaf(_cx: BlockContext, line: Line, _leaf: LeafBlock): boolean {
        return frontMatterDelimiterRegex.test(line.text);
      },
    },
  ],
  // 嵌套解析：对 FrontMatterContent 应用 YAML 语法高亮
  wrap: wrappedYamlParser(frontMatterContentTagName),
};

/**
 * Markdown Front Matter 扩展导出
 * 
 * **用法：**
 * 将此扩展添加到 CodeMirror 的 Markdown 解析器配置中，
 * 即可支持 YAML front matter 的识别和语法高亮。
 */
export const markdownFrontMatterExtension: MarkdownExtension = [frontMatterConfig];
