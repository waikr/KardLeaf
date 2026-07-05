/**
 * 编辑器命令模块导出
 * 
 * **功能：**
 * 提供 Markdown 编辑器的各种格式化命令，包括：
 * - 内联格式化（加粗、斜体、代码、删除线、高亮）
 * - 块级格式化（标题、引用、列表）
 * - 插入元素（代码块、分割线、图片、链接、表格）
 * - 选择格式化状态查询
 */
export {
  cycleHeading,      // 循环切换标题级别
  toggleBold,        // 切换加粗
  toggleCode,        // 切换行内代码
  toggleHeading,     // 切换指定级别标题
  toggleHighlight,   // 切换高亮
  toggleItalic,      // 切换斜体
  toggleStrike,      // 切换删除线
} from './markdown';
export { toggleBlockquote } from './blockquote';  // 切换引用块
export { toggleList } from './list';              // 切换列表类型
export {
  insertCodeBlock,        // 插入代码块
  insertHorizontalRule,   // 插入分割线
  insertImage,            // 插入图片
  insertLink,             // 插入链接
  insertTable,            // 插入表格
} from './insert';
export { computeSelectionFormatting } from './selectionFormatting';  // 计算选择格式化状态
