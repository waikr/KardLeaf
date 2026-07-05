/**
 * 水平分割线 Widget
 * 
 * **功能：**
 * 渲染水平分割线（`<hr>` 元素），用于可视化 Markdown 的 `---`、`***`、`___`。
 */
import { WidgetType } from '@codemirror/view';

/**
 * 分割线 Widget 类
 */
export class DividerWidget extends WidgetType {
  /**
   * 相等性判断
   * 
   * @returns true（所有分割线 widget 都相同）
   */
  eq(): boolean {
    return true;
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 元素：**
   * `<hr class="cm-divider-widget">`
   * 
   * @returns hr 元素
   */
  toDOM(): HTMLElement {
    // 创建水平分割线元素
    const hr = document.createElement('hr');
    hr.className = 'cm-divider-widget';
    return hr;
  }
}
