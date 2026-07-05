/**
 * Admonition 预设配置
 * 
 * **功能：**
 * 定义 GFM 和 Obsidian 风格的提示框类型预设，包括图标、颜色、标签等。
 * 
 * **包含的预设：**
 * - GFM_TYPES：GitHub Flavored Markdown 标准类型（5种）
 * - OBSIDIAN_TYPES：Obsidian 扩展类型（13种，包含 GFM + 8种额外类型）
 * - DEFAULT_ADMONITION_TYPE：未知类型的回退配置
 */
import type { AdmonitionTypeConfig, AdmonitionTypesMap } from './types';

/**
 * Lucide 图标 SVG 标记
 *
 * **存储方式：**
 * 原始 SVG 字符串，可通过 `innerHTML` 注入到标题 widget 中；
 * `currentColor` 让每个图标通过 CSS 继承类型的强调色。
 * 
 * **来源：**
 * lucide.dev（ISC 许可证）
 * 
 * **支持的图标：**
 * note, tip, important, warning, caution, info, success, question,
 * failure, danger, bug, example, quote, pin
 */
const ICON = {
  note: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>`,
  tip: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5"/></svg>`,
  important: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/></svg>`,
  warning: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>`,
  caution: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="7.86 2 16.14 2 22 7.86 22 16.14 16.14 22 7.86 22 2 16.14 2 7.86 7.86 2"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/></svg>`,
  info: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>`,
  success: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
  question: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" x2="12.01" y1="17" y2="17"/></svg>`,
  failure: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/></svg>`,
  danger: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>`,
  bug: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6"/><path d="M12 20v-9"/><path d="m8 2 1.88 1.88"/><path d="M14.12 3.88 16 2"/><path d="M9 7.13v-1a3 3 0 1 1 6 0v1"/><path d="M6 13H2"/><path d="M22 13h-4"/></svg>`,
  example: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="8" x2="21" y1="6" y2="6"/><line x1="8" x2="21" y1="12" y2="12"/><line x1="8" x2="21" y1="18" y2="18"/><line x1="3" x2="3.01" y1="6" y2="6"/><line x1="3" x2="3.01" y1="12" y2="12"/><line x1="3" x2="3.01" y1="18" y2="18"/></svg>`,
  quote: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>`,
  pin: `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="12" x2="12" y1="17" y2="22"/><path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"/></svg>`,
};

/**
 * GFM 标准 Admonition 类型 — note / tip / important / warning / caution
 * 
 * **用途：**
 * 当调用 `admonitionExtension()` 无参数时的默认注册类型。
 * 
 * **配置说明：**
 * - `icon`：原始 SVG 标记（通过 innerHTML 注入）
 * - `color`：强调色（用于边框、图标等）
 * - `label`：人类可读的标签文本
 * - `className`：CSS 类后缀
 */
export const GFM_TYPES: AdmonitionTypesMap = {
  note: { icon: ICON.note, color: '#1e88e5', label: 'Note', className: 'note' },
  tip: { icon: ICON.tip, color: '#43a047', label: 'Tip', className: 'tip' },
  important: { icon: ICON.important, color: '#7b1fa2', label: 'Important', className: 'important' },
  warning: { icon: ICON.warning, color: '#fb8c00', label: 'Warning', className: 'warning' },
  caution: { icon: ICON.caution, color: '#e53935', label: 'Caution', className: 'caution' },
};

/**
 * Obsidian 社区扩展类型集 — GFM 类型加上最常见的 8 种额外 callout
 * 
 * **使用方式：**
 * 从 Obsidian 迁移的用户可以通过以下方式启用：
 * ```ts
 * admonitionExtension({ types: { ...GFM_TYPES, ...OBSIDIAN_TYPES } })
 * ```
 * 
 * **包含的类型：**
 * GFM 的 5 种 + info, success, question, failure, danger, bug, example, quote
 */
export const OBSIDIAN_TYPES: AdmonitionTypesMap = {
  ...GFM_TYPES,
  info: { icon: ICON.info, color: '#039be5', label: 'Info', className: 'info' },
  success: { icon: ICON.success, color: '#43a047', label: 'Success', className: 'success' },
  question: { icon: ICON.question, color: '#fb8c00', label: 'Question', className: 'question' },
  failure: { icon: ICON.failure, color: '#e53935', label: 'Failure', className: 'failure' },
  danger: { icon: ICON.danger, color: '#e53935', label: 'Danger', className: 'danger' },
  bug: { icon: ICON.bug, color: '#e53935', label: 'Bug', className: 'bug' },
  example: { icon: ICON.example, color: '#7e57c2', label: 'Example', className: 'example' },
  quote: { icon: ICON.quote, color: '#757575', label: 'Quote', className: 'quote' },
};

/**
 * 未知类型字符串的回退配置
 * 
 * **用途：**
 * 当遇到未识别的类型名称时，保留 admonition 样式但使用中性的图钉图标
 * 和字面类型名作为标签，确保跨库笔记不会渲染失败。
 * 
 * **特点：**
 * - 使用灰色（#757575）作为中性色
 * - 使用 pin 图标作为通用标识
 * - label 为空，实际显示时会使用原始类型名
 */
export const DEFAULT_ADMONITION_TYPE: AdmonitionTypeConfig = {
  icon: ICON.pin,
  color: '#757575',
  label: '',
  className: 'default',
};
