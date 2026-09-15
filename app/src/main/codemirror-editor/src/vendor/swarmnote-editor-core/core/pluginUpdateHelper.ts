import type { ViewUpdate } from '@codemirror/view';
import { setSourceRevealEnabled } from './facets';
import type { Transaction } from '@codemirror/state';
import { syntaxTree } from '@codemirror/language';
import { renderingSelection, selectionGestureActive, selectionRenderingFrozen } from './mouseSelecting';

// Keep block widgets stable while native handles move. Content edits still
// rebuild immediately; selection-only transitions rebuild once at the edge of
// the gesture, and viewport changes still mount the newly visible widgets.
export function shouldRebuildBlockDecorations(tr: Transaction): boolean {
  if (tr.docChanged || tr.reconfigured ||
    tr.effects.some((effect) => effect.is(setSourceRevealEnabled))) return true;
  if (!renderingSelection(tr.startState).eq(renderingSelection(tr.state))) return true;
  if (selectionGestureActive(tr.startState) !== selectionGestureActive(tr.state)) return true;
  // DOMObserver can process Android's selectionchange before the bridge's
  // state effect reaches this transaction. A non-empty native select event
  // still needs one rebuild to enter the rendered surface, then the gesture
  // field keeps later handle moves stable.
  const nativeSelection = tr.selection && tr.isUserEvent('select') &&
    tr.state.selection.ranges.some((range) => !range.empty);
  if (nativeSelection && !selectionRenderingFrozen(tr.startState)) return true;
  const selectionStarted = selectionRenderingFrozen(tr.state) && !selectionRenderingFrozen(tr.startState);
  const selectionEnded = !selectionRenderingFrozen(tr.state) && selectionRenderingFrozen(tr.startState);
  if (selectionStarted || selectionEnded) return true;
  if (selectionRenderingFrozen(tr.state)) return false;
  return selectionRenderingFrozen(tr.startState) || !!tr.selection ||
    syntaxTree(tr.startState) !== syntaxTree(tr.state);
}

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
 * 1. **强制重建**：文档内容、显式选区或配置变化时，必须重建
 *    - `docChanged` - 文档内容改变
 *    - `renderingSelection` - 搜索/宿主显式选区改变
 *    - `reconfigured` - 扩展配置重新加载
 * 
 * 2. **拖拽结束重建**：从拖拽状态退出时，需要重建以显示最终选区
 *    - `wasDragging && !isDragging` - 拖拽刚结束
 * 
 * 3. **视口变化重建**：视口变化始终需要重建，以挂载新进入的 widget
 *    - `viewportChanged` - 可见区域改变
 *
 * 4. **拖拽中跳过选区重建**：没有文档或视口变化时，手柄移动期间跳过
 *    会替换节点的选区装饰更新
 *    - `isDragging` - 正在拖拽
 *
 * 5. **选区变化重建**：非拖拽情况下的选区变化需要重建
 *    - `selectionSet` - 选区设置（如点击、键盘移动光标）
 * 
 * 6. **其他情况无操作**：没有相关变化时无需处理
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
  const renderingChanged = !renderingSelection(update.startState).eq(renderingSelection(update.state));
  const nativeGestureChanged = selectionGestureActive(update.startState) !== selectionGestureActive(update.state);
  const nativeSelection = update.transactions.some((transaction) =>
    transaction.selection && transaction.isUserEvent('select') &&
      transaction.state.selection.ranges.some((range) => !range.empty));

  // Document/configuration changes and explicit host selections must rebuild.
  if (
    update.docChanged ||
    renderingChanged ||
    nativeGestureChanged ||
    update.transactions.some((t) => t.reconfigured) ||
    update.transactions.some((t) => t.effects.some((effect) => effect.is(setSourceRevealEnabled)))
  ) {
    return 'rebuild';
  }

  // 获取当前和之前的拖拽状态
  const isDragging = selectionRenderingFrozen(update.state);
  const wasDragging = selectionRenderingFrozen(update.startState);

  if (nativeSelection && !wasDragging && !isDragging) return 'rebuild';

  // Entering the native selection gesture may need one rebuild to replace
  // source-revealed markup with the stable rendered widgets. Keep that DOM
  // fixed for subsequent handle moves, then rebuild once on release.
  if (isDragging !== wasDragging) return 'rebuild';

  // A handle drag may auto-scroll into a new viewport. Rebuild that viewport
  // so its widgets exist; WidgetType.eq keeps already selected DOM nodes when
  // the visible range is reconciled.
  if (update.viewportChanged) {
    return 'rebuild';
  }

  // Selection-only updates during the gesture must not replace DOM nodes.
  if (isDragging) {
    return 'skip';
  }

  // 第二优先级：拖拽刚结束 → 重建以显示最终状态
  if (wasDragging && !isDragging) {
    return 'rebuild';
  }

  // 第四优先级：选区变化 → 重建（非拖拽情况）
  if (update.selectionSet || syntaxTree(update.startState) !== syntaxTree(update.state)) {
    return 'rebuild';
  }

  // 默认：无操作
  return 'none';
}
