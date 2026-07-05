/**
 * 链接 Tooltip 扩展
 *
 * **功能：**
 * 当鼠标悬停在 URL 节点上时，显示包含完整链接地址的 tooltip。
 * 
 * **使用场景：**
 * - Markdown 链接 `[text](url)` 中的 URL 部分
 * - 裸链接（直接输入的 URL）
 * 
 * **技术细节：**
 * - 使用 CodeMirror 的 hoverTooltip API
 * - 通过 findLinkAtPosition 查找光标位置的链接信息
 * - 创建 monospace 字体的 tooltip DOM 元素
 */
import type { Extension } from '@codemirror/state';
import { EditorView, hoverTooltip, type Tooltip } from '@codemirror/view';
import { findLinkAtPosition } from './linkUtils';

/**
 * 链接 Tooltip 主题样式
 * 
 * **样式特点：**
 * - 紧凑的内边距（4px 8px）
 * - 圆角边框（4px）
 * - 较小字体（0.85em）和等宽字体
 * - 最大宽度 400px，超出部分省略号显示
 */
const linkTooltipTheme = EditorView.theme({
  '.cm-link-tooltip': {
    padding: '4px 8px',
    borderRadius: '4px',
    fontSize: '0.85em',
    maxWidth: '400px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    fontFamily: 'monospace',
  },
});

/**
 * 获取链接 Tooltip
 * 
 * **工作流程：**
 * 1. 在指定位置查找链接信息
 * 2. 如果找到链接，创建 tooltip 对象
 * 3. Tooltip 包含完整的 URL 文本，显示在链接上方
 * 
 * @param view - 编辑器视图
 * @param pos - 文档位置
 * @returns Tooltip 对象，未找到返回 null
 */
function getLinkTooltip(
  view: EditorView,
  pos: number,
): Tooltip | null {
  const link = findLinkAtPosition(pos, view.state);
  if (!link) return null;

  return {
    pos: link.from,      // Tooltip 锚定位置
    end: link.to,        // Tooltip 结束位置
    above: true,         // 显示在链接上方
    /**
     * 创建 Tooltip DOM 元素
     * 
     * @returns 包含 dom 属性的对象
     */
    create() {
      const dom = document.createElement('div');
      dom.className = 'cm-link-tooltip';
      dom.textContent = link.url;  // 显示完整 URL
      return { dom };
    },
  };
}

/**
 * 创建链接 Tooltip 扩展
 * 
 * **功能：**
 * 注册 hoverTooltip 插件，在用户悬停到链接上时显示 URL。
 * 
 * **配置选项：**
 * - hideOnChange: true — 当文档内容变化时自动隐藏 tooltip
 * 
 * @returns CodeMirror 扩展数组
 */
export function createLinkTooltipExtension(): Extension {
  return [
    linkTooltipTheme,
    hoverTooltip((view, pos) => getLinkTooltip(view, pos), {
      hideOnChange: true,
    }),
  ];
}
