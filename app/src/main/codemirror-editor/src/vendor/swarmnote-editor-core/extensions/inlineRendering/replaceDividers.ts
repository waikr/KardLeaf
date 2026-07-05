/**
 * 水平分割线替换扩展
 * 
 * **功能：**
 * 将 Markdown 的水平分割线（`---`、`***`、`___`）替换为可视化的 DividerWidget。
 * 
 * **显示策略：**
 * 使用 'line' 策略 —— 光标在整行时显示源码，离开时显示 widget。
 */
import type { InlineRenderingSpec } from './types';
import { DividerWidget } from './widgets/DividerWidget';

/**
 * 水平分割线替换规格
 * 
 * **工作流程：**
 * 1. createDecoration：创建 DividerWidget 实例
 * 2. getRevealStrategy：使用 'line' 策略
 */
export const replaceDividers: InlineRenderingSpec = {
  nodeNames: ['HorizontalRule'],  // 水平分割线节点
  extension: {
    /**
     * 创建装饰
     * 
     * @returns DividerWidget 实例
     */
    createDecoration() {
      return new DividerWidget();
    },
    /**
     * 获取显示策略
     * 
     * @returns 'line' — 光标在整行时显示源码
     */
    getRevealStrategy() {
      return 'line';
    },
  },
};
