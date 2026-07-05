/**
 * 无序列表替换扩展
 * 
 * **功能：**
 * 将无序列表标记（`-` 或 `*`）替换为可视化的 BulletWidget，支持嵌套层级显示。
 * 
 * **特性：**
 * - 根据嵌套深度显示不同的 bullet 样式（•、◦、▪ 等）
 * - 对于任务列表，完全隐藏破折号（仅显示复选框）
 * - 光标在正文中时保持 widget 显示，光标在前缀时显示源码
 */
import { Decoration } from '@codemirror/view';
import type { InlineRenderingSpec } from './types';
import { BulletWidget } from './widgets/BulletWidget';

/** 隐藏装饰（用于任务列表中隐藏破折号） */
const hiddenDecoration = Decoration.replace({});

/** 任务列表行正则表达式（匹配 `- [ ]` 或 `* [x]` 格式） */
const TASK_LINE_PATTERN = /^\s*[-*]\s\[[ xX]\]/;

/**
 * 无序列表替换规格
 * 
 * **工作流程：**
 * 1. createDecoration：根据嵌套深度创建 BulletWidget，任务列表返回隐藏装饰
 * 2. getDecorationRange：对于任务列表，包含尾随空格
 * 3. getRevealStrategy：使用 'active' 策略
 * 4. getRevealRange：扩展到前缀部分（`- ` 或 `- [ ]`）
 */
export const replaceBulletLists: InlineRenderingSpec = {
  nodeNames: ['ListMark'],  // 列表标记节点
  extension: {
    /**
     * 创建装饰
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @param parentTags - 父节点标签计数
     * @returns BulletWidget、隐藏装饰或 null
     */
    createDecoration(node, state, parentTags) {
      // 获取 BulletList 的嵌套深度
      const bulletListDepth = parentTags.get('BulletList') ?? 0;
      if (bulletListDepth === 0) return null;  // 不在列表中，跳过

      // 对于任务列表，完全隐藏破折号（仅显示复选框）。
      // 否则行会渲染为 "- ☐ item"，而 Obsidian 风格是 "☐ item"。
      const lineText = state.doc.lineAt(node.from).text;
      if (TASK_LINE_PATTERN.test(lineText)) {
        return hiddenDecoration;
      }

      // 创建 BulletWidget，传入嵌套深度（减 1 使第一层为 0）
      return new BulletWidget(bulletListDepth - 1);
    },
    /**
     * 获取装饰范围
     * 
     * **特殊处理：**
     * 对于任务列表，包含尾随空格以使行直接从复选框开始。
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns 装饰范围或 null
     */
    getDecorationRange(node, state) {
      // 检查是否为任务列表
      const lineText = state.doc.lineAt(node.from).text;
      if (TASK_LINE_PATTERN.test(lineText)) {
        // 包含尾随空格，使行直接从复选框开始
        const afterMark = state.sliceDoc(node.to, node.to + 1);
        if (afterMark === ' ') {
          return [node.from, node.to + 1];
        }
      }
      return null;
    },
    /**
     * 获取显示策略
     * 
     * **设计原因：**
     * 仅前缀显示：当光标在项目正文中时，bullet widget 保持可见。
     * 光标进入前导 `- `（或 `- [ ]`）前缀时才触发显示源码。
     * 
     * @returns 'active'
     */
    getRevealStrategy() {
      return 'active';
    },
    /**
     * 获取显示范围
     * 
     * **匹配规则：**
     * 1. 任务列表前缀：`- [ ]`（包括缩进，5+ 字符）
     * 2. 普通 bullet 前缀：`- ` 或 `* `（包括缩进）
     * 
     * @param node - 语法节点
     * @param state - 编辑器状态
     * @returns 显示范围或 null
     */
    getRevealRange(node, state) {
      const line = state.doc.lineAt(node.from);
      // 任务列表前缀 `- [ ]`（包括缩进，5+ 字符）
      const taskMatch = line.text.match(/^(\s*[-*]\s\[[ xX]\])/);
      if (taskMatch) {
        return [line.from, line.from + taskMatch[0].length];
      }
      // 普通 bullet 前缀 `- ` 或 `* `
      const bulletMatch = line.text.match(/^(\s*[-*]\s)/);
      if (bulletMatch) {
        return [line.from, line.from + bulletMatch[0].length];
      }
      return null;
    },
  },
};
