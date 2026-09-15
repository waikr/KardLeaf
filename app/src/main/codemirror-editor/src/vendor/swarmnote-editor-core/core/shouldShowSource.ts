import type { EditorState } from '@codemirror/state';
import { collapseOnSelectionFacet, sourceRevealEnabledField } from './facets';
import { renderingSelection, selectionGestureActive } from './mouseSelecting';

/**
 * 判断是否应该显示指定范围的 Markdown 源码而非渲染为 widget
 * 
 * **核心逻辑：**
 * 只有当选区范围与 `[from, to]` 相交时，才返回 true（显示源码）。
 * 原生长按/拖选期间保持实时预览，不因选区本身切回源码。
 * 
 * **短路条件（强制返回 false = 保持 widget 渲染）：**
 * 
 * 1. **实时预览被禁用**：通过 `collapseOnSelectionFacet` 控制
 *    - 当 Facet 值为 false 时，widget 始终显示，不显示源码
 * 
 * 拖选期间读取手势开始前的渲染选区，保留原有的源码显隐状态。
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
  // MarkText-style selection: keep rendered blocks visible while a native
  // selection gesture is active. Explicit host/search selections still use
  // the source-reveal behavior below.
  if (selectionGestureActive(state)) return false;

  // 检查 1：实时预览是否启用
  if (!state.facet(collapseOnSelectionFacet)) {
    return false;  // 未启用，始终显示 widget
  }

  if (!state.field(sourceRevealEnabledField, false)) return false;
  // 拖选时沿用手势开始前的源码显隐状态，而不是强制切回 widget。
  for (const range of renderingSelection(state).ranges) {
    // 边界包含性检查：range.from <= to && range.to >= from
    if (range.from <= to && range.to >= from) {
      return true;  // 相交，显示源码
    }
  }

  // 无交集，显示 widget
  return false;
}
