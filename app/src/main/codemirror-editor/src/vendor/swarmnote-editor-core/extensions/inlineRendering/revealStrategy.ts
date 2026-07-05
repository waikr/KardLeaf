/**
 * 显示策略判断工具
 * 
 * **功能：**
 * 根据选区状态和显示策略，决定是否应该隐藏 widget 并显示源码。
 * 
 * **三种策略：**
 * - `line`：光标在整行时显示源码（用于标题等）
 * - `select`：选中具体内容时显示源码（用于链接等）
 * - `active`：仅当光标在特定位置时显示源码（用于标记符等）
 */
import type { EditorState } from '@codemirror/state';
import type { RevealStrategy } from './types';

/**
 * 判断是否应该显示源码（隐藏装饰）
 * 
 * **工作流程：**
 * 1. 如果策略是布尔值，直接返回
 * 2. 获取主选区信息
 * 3. 根据策略类型判断：
 *    - line：检查光标行号是否在范围内
 *    - select：检查选区是否与范围相交
 *    - active：检查光标位置是否在范围内
 * 
 * @param state - 编辑器状态
 * @param from - 起始位置
 * @param to - 结束位置
 * @param strategy - 显示策略
 * @returns true 表示应该显示源码（隐藏 widget）
 */
export function shouldReveal(
  state: EditorState,
  from: number,
  to: number,
  strategy: RevealStrategy | boolean,
): boolean {
  // 布尔值策略直接返回
  if (typeof strategy === 'boolean') return strategy;

  const selection = state.selection.main;

  switch (strategy) {
    case 'line': {
      // 行策略：检查光标所在行是否在范围内
      const cursorLine = state.doc.lineAt(selection.head).number;
      const fromLine = state.doc.lineAt(from).number;
      const toLine = state.doc.lineAt(to).number;
      return cursorLine >= fromLine && cursorLine <= toLine;
    }
    case 'select':
      // 选择策略：检查选区是否与范围相交
      return selection.from < to && selection.to > from;
    case 'active':
      // 激活策略：检查光标位置是否在范围内
      return selection.head >= from && selection.head <= to;
  }
}
