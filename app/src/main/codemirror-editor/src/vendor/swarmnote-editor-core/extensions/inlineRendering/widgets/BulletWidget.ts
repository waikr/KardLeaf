/**
 * 无序列表 Bullet Widget
 * 
 * **功能：**
 * 根据嵌套深度显示不同的 bullet 符号（•、◦、▪）。
 * 
 * **Bullet 符号序列：**
 * - 第 0 层：● (U+25CF)
 * - 第 1 层：○ (U+25CB)
 * - 第 2 层：■ (U+25A0)
 * - 第 3 层及以上：循环使用上述符号
 */
import { WidgetType } from '@codemirror/view';

/** Bullet 符号数组，按嵌套深度循环使用 */
const BULLET_CHARS = ['\u25CF', '\u25CB', '\u25A0'];

/**
 * Bullet Widget 类
 * 
 * @param depth - 嵌套深度（0 表示第一层）
 */
export class BulletWidget extends WidgetType {
  constructor(
    /** 嵌套深度 */
    private readonly depth: number,
  ) {
    super();
  }

  /**
   * 相等性判断
   * 
   * @param other - 另一个 widget 实例
   * @returns 是否相等
   */
  eq(other: BulletWidget): boolean {
    return this.depth === other.depth;
  }

  /**
   * 创建 DOM 结构
   * 
   * **DOM 元素：**
   * `<span class="cm-bullet-widget">{bullet}</span>`
   * 
   * **特性：**
   * - 根据深度选择 bullet 符号（循环使用）
   * - 设置 aria-hidden="true"（辅助技术忽略）
   * 
   * @returns span 元素
   */
  toDOM(): HTMLElement {
    // 创建 span 元素
    const span = document.createElement('span');
    span.className = 'cm-bullet-widget';
    // 根据深度选择 bullet 符号（取模循环）
    span.textContent = BULLET_CHARS[this.depth % BULLET_CHARS.length];
    // 辅助技术忽略此元素
    span.setAttribute('aria-hidden', 'true');
    return span;
  }
}
