import type { EditorState } from '@codemirror/state';
import { collapseOnSelectionFacet } from './facets';
import { mouseSelectingField } from './mouseSelecting';

/**
 * 判断是否应该显示指定范围的 Markdown 源码而非渲染为 widget
 * 
 * **核心逻辑：**
 * 只有当选区范围与 `[from, to]` 相交时，才返回 true（显示源码）。
 * 
 * **短路条件（强制返回 false = 保持 widget 渲染）：**
 * 
 * 1. **实时预览被禁用**：通过 `collapseOnSelectionFacet` 控制
 *    - 当 Facet 值为 false 时，widget 始终显示，不显示源码
 * 
 * 2. **用户正在拖拽选择**：通过 `mouseSelectingField` 检测
 *    - 防止拖拽选区扫过 widget 时产生闪烁
 * 
 * **边界处理：**
 * 边界交集是包含性的：当光标在 `range.from === to` 时也会显示源码，
 * 这样用户可以导航到 widget 的右边缘。
 * 
 * **使用场景：**
 * Widget 扩展在决定如何渲染时调用此函数：
 * - 返回 true → 显示原始 Markdown 标记（如 `**bold**`）
 * - 返回 false → 渲染为格式化后的 widget（如粗体文本）
 * 
 * @param state - 编辑器状态
 * @param from - 要检查的范围起始位置
 * @param to - 要检查的范围结束位置
 * @returns 是否应该显示源码
 */
export function shouldShowSource(state: EditorState, from: number, to: number): boolean {
  // 检查 1：实时预览是否启用
  if (!state.facet(collapseOnSelectionFacet)) {
    return false;  // 未启用，始终显示 widget
  }

  // 检查 2：是否正在拖拽选择
  if (state.field(mouseSelectingField, false)) {
    return false;  // 正在拖拽，保持 widget 以避免闪烁
  }

  // 检查 3：选区是否与目标范围相交
  for (const range of state.selection.ranges) {
    // 边界包含性检查：range.from <= to && range.to >= from
    if (range.from <= to && range.to >= from) {
      return true;  // 相交，显示源码
    }
  }

  // 无交集，显示 widget
  return false;
}
