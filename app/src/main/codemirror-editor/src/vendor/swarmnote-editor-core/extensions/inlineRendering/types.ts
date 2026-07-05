import type { EditorState } from '@codemirror/state';
import type { Decoration, WidgetType } from '@codemirror/view';
import type { SyntaxNodeRef } from '@lezer/common';

/**
 * 显示策略类型
 * 
 * 控制当选区与 widget 相交时如何显示源码：
 * - `line` - 当光标在整行时显示源码
 * - `select` - 当选中具体内容时显示源码
 * - `active` - 仅当光标在特定位置时显示源码
 */
export type RevealStrategy = 'line' | 'select' | 'active';

/**
 * 替换扩展接口
 * 
 * 定义了如何将特定的语法节点替换为装饰或 widget。
 * 每个 Markdown 元素（如加粗、斜体、列表等）都实现此接口。
 * 
 * **工作流程：**
 * 1. CodeMirror 解析 Markdown 语法树
 * 2. 遍历语法节点，匹配 nodeNames
 * 3. 调用 createDecoration 创建装饰
 * 4. 根据选区状态和 reveal strategy 决定是否显示
 */
export interface ReplacementExtension {
  /**
   * 创建装饰或 widget
   * 
   * @param node - 语法节点引用
   * @param state - 编辑器状态
   * @param parentTags - 父节点标签计数（用于嵌套检测）
   * @returns Decoration、WidgetType 或 null（不渲染）
   */
  createDecoration(
    node: SyntaxNodeRef,
    state: EditorState,
    parentTags: ReadonlyMap<string, number>,
  ): Decoration | WidgetType | null;

  /**
   * 获取装饰范围（可选）
   * 
   * 默认使用节点的 from/to 作为装饰范围。
   * 如果需要自定义范围（如只装饰标记符而非整个内容），可以重写此方法。
   * 
   * @returns [from] 或 [from, to] 或 null（使用默认范围）
   */
  getDecorationRange?(
    node: SyntaxNodeRef,
    state: EditorState,
  ): [number] | [number, number] | null;

  /** 
   * 当选区包含时是否隐藏装饰
   * 
   * 默认为 true：当选区与装饰范围相交时，隐藏 widget 并显示源码。
   * 设为 false：即使有选区也保持 widget 显示。
   */
  hideWhenContainsSelection?: boolean;

  /**
   * 获取显示策略（可选）
   * 
   * 默认为 'line'：光标在整行时显示源码。
   * 
   * @returns RevealStrategy 或 boolean（false 表示永不显示源码）
   */
  getRevealStrategy?(
    node: SyntaxNodeRef,
    state: EditorState,
  ): RevealStrategy | boolean;

  /**
   * 覆盖显示策略使用的范围（可选）
   * 
   * 默认使用装饰范围来判断是否显示源码。
   * 此方法允许扩大判断范围到父节点。
   * 
   * **使用场景示例：**
   * EmphasisMark（`**` 标记）的隐藏应该在光标位于 StrongEmphasis 父节点
   * 的任何位置时都显示源码，而不仅仅是在 `**` 字符上。
   * 
   * @returns [from, to] 范围或 null（使用默认范围）
   */
  getRevealRange?(
    node: SyntaxNodeRef,
    state: EditorState,
  ): [number, number] | null;
}

/**
 * 内联渲染规格
 * 
 * 将一个或多个语法节点名称映射到替换扩展。
 * 用于注册新的内联渲染规则。
 * 
 * **使用示例：**
 * ```typescript
 * const spec: InlineRenderingSpec = {
 *   nodeNames: ['StrongEmphasis', 'EmphasisMark'],
 *   extension: replaceFormatCharacters
 * };
 * ```
 */
export interface InlineRenderingSpec {
  /** 要匹配的语法节点名称列表 */
  nodeNames: string[];
  /** 对应的替换扩展 */
  extension: ReplacementExtension;
}
