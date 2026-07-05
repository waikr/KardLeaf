/**
 * Admonition / Callout 类型定义
 * 
 * **功能：**
 * 定义提示框（Admonition）的类型配置接口，支持 Obsidian 风格的彩色信息块。
 */

/**
 * 单个 Admonition / Callout 类型的配置
 *
 * **用途：**
 * `className` 会被附加到 `.cm-admonition-` 后面用于 CSS 选择器；
 * 预设使用小写名称，匹配 GFM / Obsidian 约定。
 * 
 * **示例：**
 * ```typescript
 * {
 *   icon: '<svg>...</svg>',
 *   color: '#1e88e5',
 *   label: 'Note',
 *   className: 'note'
 * }
 * ```
 */
export interface AdmonitionTypeConfig {
  /** 显示图标 — SVG 字符串或短文本，用于标题行 */
  icon: string;
  /** 强调色（边框 / 图标色调），任何有效的 CSS 颜色值 */
  color: string;
  /** 在标题行中显示的人类可读标签 */
  label: string;
  /** CSS 类后缀；最终类名为 `cm-admonition-<className>` */
  className: string;
}

/** Admonition 类型映射表 — 类型名到配置的映射 */
export type AdmonitionTypesMap = Record<string, AdmonitionTypeConfig>;
