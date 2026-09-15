import { StateEffect, StateField, type EditorSelection, type EditorState, type Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { sourceRevealEnabledField } from './facets';

/**
 * 设置鼠标拖拽选择状态的 StateEffect
 * 
 * Effect 是 CodeMirror 中用于在事务中传递一次性信息的机制。
 * 这里用于通知状态系统用户是否正在拖拽选择文本。
 */
export const setMouseSelecting = StateEffect.define<boolean>();

/** Event emitted after the browser's native selection has become empty. */
export const nativeSelectionSettledEvent = 'kardleaf-native-selection-settled';

/** State effect used by the DOM bridge to clear a native selection session. */
export const setNativeSelectionActive = StateEffect.define<boolean>();

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

// Android's selection handles live in a native PopupWindow: dragging them need
// not emit DOM touch/mouse events. Track selection transactions with the
// `select` user-event so the preview can distinguish a user gesture from an
// explicit host/search selection.
export const nativeSelectionField = StateField.define<boolean>({
  create: () => false,
  update(value, tr) {
    for (const effect of tr.effects) {
      if (effect.is(setNativeSelectionActive)) return effect.value;
    }
    if (tr.docChanged) return false;
    if (tr.selection) {
      return tr.isUserEvent('select') && tr.state.selection.ranges.some((range) => !range.empty);
    }
    return value;
  },
});

/** A non-empty native selection, including Android PopupWindow handles. */
export function selectionGestureActive(state: EditorState): boolean {
  return state.field(nativeSelectionField, false) ?? false;
}

// Keep this exported name for the existing render/update gates. It now means
// only an active user selection gesture, not every non-empty programmatic
// selection (search/host selection must still reveal its target).
export function selectionRenderingFrozen(state: EditorState): boolean {
  // A press freezes the previous reveal decision; only a real native range
  // switches to the rendered surface. Treating every tap as a range flickers.
  return !!state.field(mouseSelectingField, false) || selectionGestureActive(state);
}

/** True only when both native DOM selection endpoints belong to this editor. */
export function hasNonEmptyNativeSelection(view: EditorView): boolean {
  const selection = view.dom.ownerDocument.getSelection?.() ?? window.getSelection();
  return !!selection && !selection.isCollapsed &&
    !!selection.anchorNode && !!selection.focusNode &&
    view.contentDOM.contains(selection.anchorNode) && view.contentDOM.contains(selection.focusNode);
}

/**
 * Run a DOM mutation now, or after the current native handle selection ends.
 * The caller owns the target DOM node and receives cleanup for widget destroy.
 */
export function runWhenNativeSelectionSettled(
  view: EditorView,
  target: HTMLElement,
  action: () => void,
): () => void {
  let disposed = false;
  let listening = false;

  const cleanup = () => {
    if (!listening) return;
    document.removeEventListener(nativeSelectionSettledEvent, retry);
    listening = false;
  };
  const retry = () => {
    if (disposed || !target.isConnected || !view.dom.contains(target)) {
      cleanup();
      return;
    }
    if (hasNonEmptyNativeSelection(view)) return;
    cleanup();
    action();
  };

  if (hasNonEmptyNativeSelection(view)) {
    listening = true;
    document.addEventListener(nativeSelectionSettledEvent, retry);
  } else {
    action();
  }

  return () => {
    disposed = true;
    cleanup();
  };
}

// Keep the *previous* reveal decisions when a drag scrolls into a new viewport.
// The selection gesture itself keeps the rendered surface visible; only the
// transition into/out of the gesture is allowed to rebuild decorations.
const renderingSelectionField = StateField.define<EditorSelection>({
  create: (state) => state.selection,
  update(selection, tr) {
    // Explicit host/search selections may reveal their target immediately.
    // Chromium's DOM selections use the select/select.pointer user event.
    const firstReveal = !tr.startState.field(sourceRevealEnabledField, false) && tr.state.field(sourceRevealEnabledField, false);
    return firstReveal || tr.docChanged || (tr.selection && !tr.isUserEvent('select')) || !selectionRenderingFrozen(tr.state)
      ? tr.state.selection
      : selection;
  },
});

export function renderingSelection(state: EditorState): EditorSelection {
  return state.field(renderingSelectionField, false) ?? state.selection;
}

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
  nativeSelectionField,
  renderingSelectionField,
  mouseSelectingHandlers,
];
