import { StateEffect, StateField, type Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';

/**
 * 设置鼠标拖拽选择状态的 StateEffect
 * 
 * Effect 是 CodeMirror 中用于在事务中传递一次性信息的机制。
 * 这里用于通知状态系统用户是否正在拖拽选择文本。
 */
export const setMouseSelecting = StateEffect.define<boolean>();

/**
 * 追踪用户是否正在拖拽选择的 StateField
 * 
 * **为什么需要这个字段？**
 * 
 * Widget 扩展（如实时预览的加粗、斜体等）会监听选区变化来隐藏/显示 Markdown 标记。
 * 但在用户拖拽选择的过程中，如果频繁重建 widget 装饰会导致闪烁。
 * 
 * **解决方案：**
 * 1. 通过 DOM 事件监听 mousedown/mouseup 来追踪拖拽状态
 * 2. Widget 在更新时检查此字段，如果正在拖拽则跳过重建
 * 3. 拖拽结束后再重建，避免视觉闪烁
 */
export const mouseSelectingField = StateField.define<boolean>({
  /** 初始状态：未拖拽 */
  create: () => false,
  /**
   * 状态更新逻辑
   * 
   * @param value - 当前状态
   * @param tr - 事务对象
   * @returns 新的状态值
   */
  update(value, tr) {
    // 遍历事务中的所有 effects
    for (const effect of tr.effects) {
      // 如果找到 setMouseSelecting effect，使用其值
      if (effect.is(setMouseSelecting)) {
        return effect.value;
      }
    }
    // 如果没有相关 effect，保持原值
    return value;
  },
});

/**
 * DOM 事件处理器，桥接原生鼠标事件到 CodeMirror 状态系统
 * 
 * **工作流程：**
 * 1. 用户按下鼠标 → dispatch setMouseSelecting(true)
 * 2. 用户释放鼠标 → dispatch setMouseSelecting(false)
 * 3. mouseSelectingField 接收 effect 并更新状态
 * 4. Widget 扩展读取状态决定是否需要重建
 * 
 * **返回值说明：**
 * 返回 `false` 表示不阻止默认行为，让 CodeMirror 正常处理鼠标事件。
 */
const mouseSelectingHandlers = EditorView.domEventHandlers({
  mousedown(_event, view) {
    view.dispatch({ effects: setMouseSelecting.of(true) });
    return false;
  },
  mouseup(_event, view) {
    view.dispatch({ effects: setMouseSelecting.of(false) });
    return false;
  },
});

/**
 * 完整的鼠标选择追踪扩展
 * 
 * 组合了 StateField 和 DOM 事件处理器，提供开箱即用的拖拽状态追踪功能。
 * 在 createEditor 中被添加到编辑器扩展列表中。
 */
export const mouseSelectingExtension: Extension = [
  mouseSelectingField,
  mouseSelectingHandlers,
];
