/**
 * 复选框 Widget
 * 
 * **功能：**
 * 渲染可交互的复选框，支持点击切换勾选状态。
 * 
 * **交互逻辑：**
 * - 点击复选框时，阻止默认行为
 * - 根据当前状态切换 `[ ]` ↔ `[x]`
 * - 通过 CodeMirror dispatch 更新文档内容
 */
import { EditorView, WidgetType } from '@codemirror/view';

/**
 * 复选框 Widget 类
 * 
 * @param checked - 是否已勾选
 * @param pos - 文档位置（用于更新）
 */
export class CheckboxWidget extends WidgetType {
  constructor(
    /** 是否已勾选 */
    private readonly checked: boolean,
    /** 文档位置 */
    private readonly pos: number,
  ) {
    super();
  }

  /**
   * 相等性判断
   * 
   * @param other - 另一个 widget 实例
   * @returns 是否相等
   */
  eq(other: CheckboxWidget): boolean {
    return this.checked === other.checked && this.pos === other.pos;
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 元素：**
   * `<input type="checkbox" class="cm-checkbox-widget">`
   * 
   * **事件处理：**
   * mousedown：阻止默认行为，切换勾选状态并更新文档
   * 
   * @param view - 编辑器视图
   * @returns input 元素
   */
  toDOM(view: EditorView): HTMLElement {
    // 创建复选框输入元素
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = this.checked;
    input.className = 'cm-checkbox-widget';
    input.setAttribute('aria-label', this.checked ? 'checked' : 'unchecked');

    // 监听鼠标按下事件
    input.addEventListener('mousedown', (e) => {
      e.preventDefault();  // 阻止默认行为
      // 切换文本：[ ] ↔ [x]
      const newText = this.checked ? '[ ]' : '[x]';
      view.dispatch({
        changes: { from: this.pos, to: this.pos + 3, insert: newText },
      });
    });

    return input;
  }

  /**
   * 事件处理策略
   * 
   * **返回值：**
   * true — 忽略所有事件（由 toDOM 中的 mousedown 处理器处理）
   * 
   * @returns true
   */
  ignoreEvent(): boolean {
    return true;
  }
}
