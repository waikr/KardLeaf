/**
 * Core 模块导出
 * 
 * 包含编辑器核心基础设施：
 * - Facets：全局配置开关
 * - StateFields：状态追踪
 * - 工具函数：渲染决策、更新控制
 */

/** 实时预览主开关 Facet */
export {
  collapseOnSelectionFacet,
  setSourceRevealEnabled,
  sourceRevealEnabledField,
} from './facets';

/** 鼠标拖拽选择追踪相关 */
export {
  /** 完整的鼠标选择扩展（StateField + DOM 事件处理器） */
  mouseSelectingExtension,
  /** 拖拽状态 StateField */
  mouseSelectingField,
  /** Android/浏览器原生选区 StateField */
  nativeSelectionField,
  /** 检查编辑器内的非空原生 DOM 选区 */
  hasNonEmptyNativeSelection,
  /** 原生选区结束事件名 */
  nativeSelectionSettledEvent,
  /** 在原生选区结束后执行一次异步 DOM 更新 */
  runWhenNativeSelectionSettled,
  /** 清理原生选区状态的 Effect */
  setNativeSelectionActive,
  /** 设置拖拽状态的 Effect */
  setMouseSelecting,
  /** 用户选区手势是否活跃 */
  selectionGestureActive,
} from './mouseSelecting';

/** 判断是否显示源码的工具函数 */
export { shouldShowSource } from './shouldShowSource';

/** Widget 更新决策工具 */
export { checkUpdateAction, type UpdateAction } from './pluginUpdateHelper';
