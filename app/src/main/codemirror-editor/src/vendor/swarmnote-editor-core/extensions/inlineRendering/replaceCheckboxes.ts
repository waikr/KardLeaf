/**
 * 复选框替换扩展
 * 
 * **功能：**
 * 将任务列表标记 `[ ]`、`[x]`、`[X]` 替换为可视化的 CheckboxWidget。
 * 
 * **特性：**
 * - 支持已勾选（[x]）和未勾选（[ ]）状态
 * - 与 replaceBulletLists 共享显示范围，确保 `- [ ]` 前缀一起显示/隐藏
 * - 光标在正文中时保持 widget 显示，光标在前缀时显示源码
 */
import type { InlineRenderingSpec } from './types';
import { CheckboxWidget } from './widgets/CheckboxWidget';

/**
 * 复选框替换规格
 * 
 * **工作流程：**
 * 1. createDecoration：根据标记文本判断是否勾选，创建对应的 CheckboxWidget
 * 2. getRevealStrategy：使用 'active' 策略（仅光标在前缀时显示源码）
 * 3. getRevealRange：扩展到整行的 `- [ ]` 前缀部分
 */
export const replaceCheckboxes: InlineRenderingSpec = {
  nodeNames: ['TaskMarker'],  // 任务标记节点
  extension: {
    /**
     * 创建装饰
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns CheckboxWidget 实例
     */
    createDecoration(node, state) {
      // 提取标记文本（如 `[ ]` 或 `[x]`）
      const text = state.sliceDoc(node.from, node.to);
      // 判断是否已勾选（支持小写 x 和大写 X）
      const checked = /\[[xX]\]/.test(text);
      return new CheckboxWidget(checked, node.from);
    },
    /**
     * 获取显示策略
     * 
     * @returns 'active' — 仅光标在标记上时显示源码
     */
    getRevealStrategy() {
      return 'active';
    },
    /**
     * 获取显示范围
     * 
     * **设计原因：**
     * 与 replaceBulletLists 共享显示范围，确保破折号和复选框一起显示/隐藏：
     * - 光标在 `- [ ]` 前缀的任何位置 → 两者都显示源码
     * - 光标在正文中 → 两者都保持 widget 显示
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns 显示范围或 null
     */
    getRevealRange(node, state) {
      // 匹配任务列表前缀（如 `- [ ]` 或 `* [x]`）
      const line = state.doc.lineAt(node.from);
      const taskMatch = line.text.match(/^(\s*[-*]\s\[[ xX]\])/);
      if (taskMatch) {
        return [line.from, line.from + taskMatch[0].length];
      }
      return null;
    },
  },
};
