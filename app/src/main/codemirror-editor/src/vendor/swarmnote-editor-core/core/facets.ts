import { Facet } from '@codemirror/state';

/**
 * 实时预览行为的主开关 Facet
 *
 * **作用：**
 * 控制是否在选区时折叠 Markdown 源码标记。
 * 
 * - 当值为 `true` 时：选中某段文本时，该段的 Markdown 标记（如 `**`、`*`）会被隐藏
 * - 当值为 `false` 时：所有扩展无条件显示源码，不隐藏任何标记
 *
 * **使用场景：**
 * 用户可以通过这个开关切换“源码模式”和“预览模式”。
 *
 * **消费方式：**
 * 扩展通过 `shouldShowSource` 函数消费此 Facet，当 Facet 值为 false 时，
 * `shouldShowSource` 会短路返回 `false`，强制显示源码。
 */
export const collapseOnSelectionFacet = Facet.define<boolean, boolean>({
  /**
   * 合并策略：取最后一个配置值（允许覆盖）
   * 如果没有配置，默认为 true（启用实时预览）
   */
  combine: (values) => (values.length > 0 ? values[values.length - 1] : true),
});
