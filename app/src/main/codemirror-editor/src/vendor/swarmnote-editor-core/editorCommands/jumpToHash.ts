/**
 * 跳转到文档中匹配 hash 的标题。
 * hash 是标题文本经 slug 化后的结果。
 * 
 * **功能：**
 * 根据 URL hash（如 `#heading-title`）滚动到对应的 Markdown 标题位置。
 */
import { ensureSyntaxTree } from '@codemirror/language';
import { EditorSelection } from '@codemirror/state';
import { EditorView } from '@codemirror/view';

/**
 * 跳转到文档中匹配 hash 的标题。
 * hash 是标题文本经 slug 化后的结果。
 */
/**
 * 跳转到指定的 hash 锚点
 * 
 * **行为：**
 * 1. 解析 hash 字符串（移除前导 `#`）
 * 2. 在文档中搜索匹配的标题
 * 3. 滚动到该标题位置并高亮显示
 * 
 * @param view - 编辑器视图
 * @param hash - 目标锚点（可以带或不带 `#` 前缀）
 * @returns 是否成功找到并跳转
 */
export function jumpToHash(view: EditorView, hash: string): boolean {
  const { state } = view;
  let targetPos: number | undefined;

  const tree = ensureSyntaxTree(state, state.doc.length, 1000);
  if (!tree) return false;

  tree.iterate({
    enter(node) {
      if (targetPos !== undefined) return false;

      if (node.name.startsWith('SetextHeading') || node.name.startsWith('ATXHeading')) {
        const text = state
          .sliceDoc(node.from, node.to)
          .replace(/^#+\s/, '')
          .replace(/\n-+$/, '');

        if (hash === slugify(text)) {
          targetPos = node.to;
          return false;
        }
      }
    },
  });

  if (targetPos !== undefined) {
    view.dispatch({
      selection: EditorSelection.cursor(targetPos),
      effects: [EditorView.scrollIntoView(targetPos, { y: 'start' })],
    });
    return true;
  }

  return false;
}

/**
 * 将文本转换为 URL 友好的 slug
 * 
 * **转换规则：**
 * 1. 转为小写并去除首尾空格
 * 2. 移除非字母数字、空格和连字符的字符
 * 3. 将空格和下划线替换为连字符
 * 4. 移除首尾的连字符
 * 
 * @param text - 原始文本
 * @returns slug 化的字符串
 */
function slugify(text: string): string {
  return text
    .toLowerCase()
    .trim()
    .replace(/[^\w\s-]/g, '')
    .replace(/[\s_]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
