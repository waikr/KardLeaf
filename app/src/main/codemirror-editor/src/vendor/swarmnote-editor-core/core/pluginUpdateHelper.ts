import type { ViewUpdate } from '@codemirror/view';
import { mouseSelectingField } from './mouseSelecting';

/**
 * Widget 更新动作类型
 * 
 * - `rebuild` - 需要完全重建 widget
 * - `skip` - 跳过本次更新（通常在拖拽过程中）
 * - `none` - 无需任何操作
 */
export type UpdateAction = 'rebuild' | 'skip' | 'none';

/**
 * 决定 widget ViewPlugin 在 ViewUpdate 时应该采取的行动
 * 
 * **核心目标：**
 * 集中管理所有实时预览插件的重建/跳过决策，确保行为一致性。
 * 
 * **决策逻辑（优先级从高到低）：**
 * 
 * 1. **强制重建**：文档内容、视口或配置发生变化时，必须重建
 *    - `docChanged` - 文档内容改变
 *    - `viewportChanged` - 可见区域改变
 *    - `reconfigured` - 扩展配置重新加载
 * 
 * 2. **拖拽结束重建**：从拖拽状态退出时，需要重建以显示最终选区
 *    - `wasDragging && !isDragging` - 拖拽刚结束
 * 
 * 3. **拖拽中跳过**：正在拖拽时跳过纯选区更新，避免闪烁
 *    - `isDragging` - 正在拖拽
 * 
 * 4. **选区变化重建**：非拖拽情况下的选区变化需要重建
 *    - `selectionSet` - 选区设置（如点击、键盘移动光标）
 * 
 * 5. **其他情况无操作**：没有相关变化时无需处理
 * 
 * **使用示例：**
 * ```typescript
 * const action = checkUpdateAction(update);
 * if (action === 'rebuild') {
 *   // 重建 decorations
 * } else if (action === 'skip') {
 *   // 跳过本次更新
 * }
 * ```
 * 
 * @param update - CodeMirror 视图更新对象
 * @returns 建议的更新动作
 */
export function checkUpdateAction(update: ViewUpdate): UpdateAction {
  // 第一优先级：文档/视口/配置变化 → 必须重建
  if (
    update.docChanged ||
    update.viewportChanged ||
    update.transactions.some((t) => t.reconfigured)
  ) {
    return 'rebuild';
  }

  // 获取当前和之前的拖拽状态
  const isDragging = update.state.field(mouseSelectingField, false);
  const wasDragging = update.startState.field(mouseSelectingField, false);

  // 第二优先级：拖拽刚结束 → 重建以显示最终状态
  if (wasDragging && !isDragging) {
    return 'rebuild';
  }

  // 第三优先级：正在拖拽 → 跳过以避免闪烁
  if (isDragging) {
    return 'skip';
  }

  // 第四优先级：选区变化 → 重建（非拖拽情况）
  if (update.selectionSet) {
    return 'rebuild';
  }

  // 默认：无操作
  return 'none';
}
